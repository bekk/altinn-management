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
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.json.Json
import no.kartverket.altinnmanagement.client.ResourceRegistryClient
import no.kartverket.altinnmanagement.restserver.config.AppConfig
import no.kartverket.altinnmanagement.restserver.config.externalFiltering
import no.kartverket.altinnmanagement.restserver.config.file
import no.kartverket.altinnmanagement.restserver.config.loadApplicationConfig
import no.kartverket.altinnmanagement.restserver.config.resourceRegistryClient
import no.kartverket.altinnmanagement.restserver.filtering.CLIENT_CERTIFICATE_HEADER
import no.kartverket.altinnmanagement.restserver.filtering.ExternalFiltering
import no.kartverket.altinnmanagement.restserver.filtering.allowedBy
import no.kartverket.altinnmanagement.restserver.models.GenericAccessResource
import no.kartverket.altinnmanagement.restserver.models.GenericAccessResourcePolicy
import no.kartverket.altinnmanagement.restserver.models.MaskinportenSchema
import no.kartverket.altinnmanagement.restserver.observability.installCallLogging
import no.kartverket.altinnmanagement.restserver.problem.PolicyNotFoundException
import no.kartverket.altinnmanagement.restserver.problem.ResourceNotFoundException
import no.kartverket.altinnmanagement.restserver.problem.errorResponses

internal val apiJson = Json(DefaultJson) {
    explicitNulls = false
    // DefaultJson is lenient, and would read 42 as the string "42"
    isLenient = false
}

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
            accessLogHealth = config.accessLogHealth,
        )
    }.start(wait = true)
}

@OptIn(ExperimentalKtorApi::class)
fun Application.module(
    client: ResourceRegistryClient,
    externalFiltering: ExternalFiltering,
    accessLog: Boolean = true,
    accessLogHealth: Boolean = false,
) {
    installCallLogging(accessLog, accessLogHealth)
    install(ContentNegotiation) {
        json(apiJson)
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
            call.respond(matching.allowedBy(filter) { allows(it) }.map(MaskinportenSchema::from))
        }.describe(maskinportenSchemasListOperation)

        post("/api/maskinportenschemas") {
            val schema = newMaskinportenSchema(call.newMaskinportenSchemaRequest())
            val created = client.createMaskinportenSchema(schema)
            call.response.header(HttpHeaders.Location, "/api/maskinportenschemas/${created.resourceId.value}")
            call.respond(HttpStatusCode.Created, MaskinportenSchema.from(created))
        }.describe(maskinportenSchemaCreateOperation)

        get("/api/maskinportenschemas/{id}") {
            val id = resourceId("id", call.pathParameters["id"]!!)
            val schema = client.getMaskinportenSchema(id) ?: throw ResourceNotFoundException("MaskinportenSchema")
            call.respond(MaskinportenSchema.from(schema))
        }.describe(maskinportenSchemaGetOperation)

        put("/api/maskinportenschemas/{id}") {
            val id = resourceId("id", call.pathParameters["id"]!!)
            val content = maskinportenSchemaContent(id, call.maskinportenSchemaRequest())
            val schema = client.updateMaskinportenSchema(id) { content(it.delegable) }
                ?: throw ResourceNotFoundException("MaskinportenSchema")
            call.respond(MaskinportenSchema.from(schema))
        }.describe(maskinportenSchemaPutOperation)

        put("/api/maskinportenschemas/{id}/withdraw") {
            val id = resourceId("id", call.pathParameters["id"]!!)
            val schema = client.withdrawMaskinportenSchema(id) ?: throw ResourceNotFoundException("MaskinportenSchema")
            call.respond(MaskinportenSchema.from(schema))
        }.describe(maskinportenSchemaWithdrawOperation)

        put("/api/maskinportenschemas/{id}/policy") {
            val id = resourceId("id", call.pathParameters["id"]!!)
            if (!client.setMaskinportenSchemaPolicy(id)) throw ResourceNotFoundException("MaskinportenSchema")
            call.respond(HttpStatusCode.NoContent)
        }.describe(maskinportenSchemaPolicyPutOperation)

        get("/api/genericaccessresources") {
            val certificate = clientCertificate(call.request.headers[CLIENT_CERTIFICATE_HEADER], required = externalFiltering.isOn)
            val filter = externalFiltering.filterFor(certificate)
            val resources = client.listGenericAccessResources()
            call.respond(resources.allowedBy(filter) { allows(it) }.map(GenericAccessResource::from))
        }.describe(genericAccessResourcesListOperation)

        post("/api/genericaccessresources") {
            val resource = newGenericAccessResource(call.newGenericAccessResourceRequest())
            val created = client.createGenericAccessResource(resource)
            call.response.header(HttpHeaders.Location, "/api/genericaccessresources/${created.resourceId.value}")
            call.respond(HttpStatusCode.Created, GenericAccessResource.from(created))
        }.describe(genericAccessResourceCreateOperation)

        get("/api/genericaccessresources/{id}") {
            val id = resourceId("id", call.pathParameters["id"]!!)
            val resource = client.getGenericAccessResource(id) ?: throw ResourceNotFoundException("GenericAccessResource")
            call.respond(GenericAccessResource.from(resource))
        }.describe(genericAccessResourceGetOperation)

        put("/api/genericaccessresources/{id}") {
            val id = resourceId("id", call.pathParameters["id"]!!)
            val content = genericAccessResourceContent(id, call.genericAccessResourceRequest())
            val resource = client.updateGenericAccessResource(id) { content(it.delegable) }
                ?: throw ResourceNotFoundException("GenericAccessResource")
            call.respond(GenericAccessResource.from(resource))
        }.describe(genericAccessResourcePutOperation)

        put("/api/genericaccessresources/{id}/withdraw") {
            val id = resourceId("id", call.pathParameters["id"]!!)
            val resource = client.withdrawGenericAccessResource(id) ?: throw ResourceNotFoundException("GenericAccessResource")
            call.respond(GenericAccessResource.from(resource))
        }.describe(genericAccessResourceWithdrawOperation)

        put("/api/genericaccessresources/{id}/policy") {
            val id = resourceId("id", call.pathParameters["id"]!!)
            val policy = genericAccessResourcePolicy(call.genericAccessResourcePolicyRequest())
            if (!client.setGenericAccessResourcePolicy(id, policy)) throw ResourceNotFoundException("GenericAccessResource")
            call.respond(HttpStatusCode.NoContent)
        }.describe(genericAccessResourcePolicyPutOperation)

        get("/api/genericaccessresources/{id}/policy") {
            val id = resourceId("id", call.pathParameters["id"]!!)
            val policy = client.getGenericAccessResourcePolicy(id) ?: throw PolicyNotFoundException()
            call.respond(GenericAccessResourcePolicy.from(policy))
        }.describe(genericAccessResourcePolicyGetOperation)
    }
}
