package no.kartverket.altinnmanagement.restserver.accesslist

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.utils.io.ExperimentalKtorApi
import no.kartverket.altinnmanagement.client.ResourceRegistryClient
import no.kartverket.altinnmanagement.restserver.clientCertificate
import no.kartverket.altinnmanagement.restserver.filtering.CLIENT_CERTIFICATE_HEADER
import no.kartverket.altinnmanagement.restserver.filtering.ExternalFiltering
import no.kartverket.altinnmanagement.restserver.filtering.allowedBy
import no.kartverket.altinnmanagement.restserver.problem.ResourceNotFoundException

@OptIn(ExperimentalKtorApi::class)
internal fun Route.accessLists(client: ResourceRegistryClient, externalFiltering: ExternalFiltering) {
    get("/api/accesslists") {
        val resource = resource(call.queryParameters.getAll("resource"))
        val certificate = clientCertificate(call.request.headers[CLIENT_CERTIFICATE_HEADER], required = externalFiltering.isOn)
        val filter = externalFiltering.filterFor(certificate)
        val lists = if (resource == null) client.listAccessLists() else client.listAccessLists(resource)
        call.respond(lists.allowedBy(filter) { allows(it) }.map(AccessList::from))
    }.describe(accessListsListOperation)

    post("/api/accesslists") {
        val list = newAccessList(call.newAccessListRequest())
        val created = client.createAccessList(list.id, list.name, list.description)
        call.response.header(HttpHeaders.Location, "/api/accesslists/${list.id}")
        call.respond(HttpStatusCode.Created, AccessList.from(created))
    }.describe(accessListCreateOperation)

    get("/api/accesslists/{id}") {
        val list = client.getAccessList(call.pathParameters["id"]!!) ?: throw ResourceNotFoundException("access list")
        call.respond(AccessList.from(list))
    }.describe(accessListGetOperation)

    put("/api/accesslists/{id}") {
        val id = accessListId("id", call.pathParameters["id"]!!)
        val list = accessListContent(id, call.accessListRequest())
        val changed = client.updateAccessList(list.id, list.name, list.description) ?: throw ResourceNotFoundException("access list")
        call.respond(AccessList.from(changed))
    }.describe(accessListPutOperation)

    delete("/api/accesslists/{id}") {
        val id = accessListId("id", call.pathParameters["id"]!!)
        val deleted = client.deleteAccessList(id) ?: throw ResourceNotFoundException("access list")
        call.respond(AccessList.from(deleted))
    }.describe(accessListDeleteOperation)

    get("/api/accesslists/{id}/members") {
        val members = client.listAccessListMembers(call.pathParameters["id"]!!) ?: throw ResourceNotFoundException("access list")
        call.respond(members.map(AccessListMember::from))
    }.describe(accessListMembersListOperation)

    post("/api/accesslists/{id}/members") {
        val id = accessListId("id", call.pathParameters["id"]!!)
        val organisationNumbers = newAccessListMembers(call.newAccessListMembersRequest())
        if (!client.addAccessListMembers(id, organisationNumbers)) throw ResourceNotFoundException("access list")
        call.respond(HttpStatusCode.NoContent)
    }.describe(accessListMembersAddOperation)

    delete("/api/accesslists/{id}/members/{organisationNumber}") {
        val id = accessListId("id", call.pathParameters["id"]!!)
        val organisationNumber = organisationNumber("organisationNumber", call.pathParameters["organisationNumber"]!!)
        if (!client.removeAccessListMembers(id, listOf(organisationNumber))) throw ResourceNotFoundException("access list")
        call.respond(HttpStatusCode.NoContent)
    }.describe(accessListMemberRemoveOperation)
}
