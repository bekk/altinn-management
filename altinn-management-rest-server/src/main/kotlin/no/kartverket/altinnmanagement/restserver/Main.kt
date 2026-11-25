package no.kartverket.altinnmanagement.restserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.routing
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.json.Json
import no.kartverket.altinnmanagement.client.ResourceRegistryClient
import no.kartverket.altinnmanagement.restserver.config.AppConfig
import no.kartverket.altinnmanagement.restserver.config.externalFiltering
import no.kartverket.altinnmanagement.restserver.config.file
import no.kartverket.altinnmanagement.restserver.config.loadApplicationConfig
import no.kartverket.altinnmanagement.restserver.config.resourceRegistryClient
import no.kartverket.altinnmanagement.restserver.filtering.ExternalFiltering
import no.kartverket.altinnmanagement.restserver.genericaccessresource.genericAccessResources
import no.kartverket.altinnmanagement.restserver.maskinportenschema.maskinportenSchemas
import no.kartverket.altinnmanagement.restserver.observability.installCallLogging
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

        maskinportenSchemas(client, externalFiltering)
        genericAccessResources(client, externalFiltering)
    }
}
