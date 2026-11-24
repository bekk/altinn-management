package no.kartverket.altinnmanagement.restserver.genericaccessresource

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
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
import no.kartverket.altinnmanagement.restserver.problem.PolicyNotFoundException
import no.kartverket.altinnmanagement.restserver.problem.ResourceNotFoundException
import no.kartverket.altinnmanagement.restserver.resourceId

@OptIn(ExperimentalKtorApi::class)
internal fun Route.genericAccessResources(client: ResourceRegistryClient, externalFiltering: ExternalFiltering) {
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
