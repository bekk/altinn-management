package no.kartverket.altinnmanagement.restserver.accesslist

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import no.kartverket.altinnmanagement.client.ResourceRegistryClient
import no.kartverket.altinnmanagement.restserver.problem.ResourceNotFoundException

@OptIn(ExperimentalKtorApi::class)
internal fun Route.accessLists(client: ResourceRegistryClient) {
    get("/api/accesslists/{id}") {
        val list = client.getAccessList(call.pathParameters["id"]!!) ?: throw ResourceNotFoundException("access list")
        call.respond(AccessList.from(list))
    }.describe(accessListGetOperation)
}
