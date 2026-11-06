package no.kartverket.altinnmanagement.restserver

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.routing
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.json.Json
import no.kartverket.altinnmanagement.client.ResourceRegistryClient
import no.kartverket.altinnmanagement.restserver.models.MaskinportenSchema
import org.slf4j.LoggerFactory
import java.util.UUID

fun main() {
    val applicationConfig = loadApplicationConfig()
    // Logback reads this when the first logger is created, so it is set before anything that could log.
    applicationConfig.file("logback.configFile")?.let { System.setProperty("logback.configurationFile", it) }
    val config = AppConfig.load(applicationConfig)

    embeddedServer(Netty, port = config.port) {
        module(
            client = resourceRegistryClient(config),
            externalFiltering = externalFiltering(config),
            accessLog = config.accessLog,
        )
    }.start(wait = true)
}

@OptIn(ExperimentalKtorApi::class)
fun Application.module(
    client: ResourceRegistryClient,
    externalFiltering: ExternalFiltering,
    accessLog: Boolean = true,
) {
    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify(::isSafeRequestId)
    }
    install(CallLogging) {
        mdc("workload") { workloadFrom(it.request.headers[CLIENT_CERTIFICATE_HEADER]) }
        callIdMdc("request_id")

        logger = LoggerFactory.getLogger("access")
        disableDefaultColors()
        filter { accessLog }
    }
    install(ContentNegotiation) {
        json(Json(DefaultJson) { explicitNulls = false })
        accept { _, _ -> emptyList() }
    }
    install(StatusPages) { errorResponses() }

    val spec by lazy { openApiSpec() }

    routing {
        get("/health/live") { call.respond(HttpStatusCode.OK) }.hide()
        get("/openapi") { call.respondText(spec, ContentType.Application.Json) }.hide()

        get("/api/maskinportenschemas") {
            val scope = scope(call.queryParameters.getAll("scope"))
            val certificate = clientCertificate(call.request.headers[CLIENT_CERTIFICATE_HEADER], required = externalFiltering.isOn)
            val filter = externalFiltering.filterFor(certificate)
            val schemas = client.listMaskinportenSchemas()
            val matching = if (scope == null) schemas else schemas.filter { scope in it.scopes.orEmpty() }
            call.respond(matching.allowedBy(filter).map(MaskinportenSchema::from))
        }.describe(maskinportenSchemasListOperation)

        get("/api/maskinportenschemas/{id}") {
            val id = resourceId("id", call.pathParameters["id"]!!)
            val schema = client.getMaskinportenSchema(id) ?: throw MaskinportenSchemaNotFoundException()
            call.respond(MaskinportenSchema.from(schema))
        }.describe(maskinportenSchemaGetOperation)
    }
}
