package no.kartverket.altinnmanagement.restserver

import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.application.plugin
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.routing.openapi.plus
import kotlinx.serialization.json.Json

private val openApiJson = Json { prettyPrint = true }

private val apiInfo = OpenApiInfo(
    title = "Altinn Management REST API",
    version = "v0",
    description = "Manages Kartverket's resources in Altinn's Resource Registry.",
)

fun Application.openApiSpec(): String =
    openApiJson.encodeToString(OpenApiDoc(info = apiInfo) + plugin(RoutingRoot).getAllRoutes())
