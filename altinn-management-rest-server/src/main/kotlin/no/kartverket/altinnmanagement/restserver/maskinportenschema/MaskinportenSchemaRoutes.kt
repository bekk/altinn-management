package no.kartverket.altinnmanagement.restserver.maskinportenschema

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
import no.kartverket.altinnmanagement.restserver.problem.ResourceNotFoundException
import no.kartverket.altinnmanagement.restserver.resourceId

@OptIn(ExperimentalKtorApi::class)
internal fun Route.maskinportenSchemas(client: ResourceRegistryClient, externalFiltering: ExternalFiltering) {
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
}
