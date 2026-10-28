package no.kartverket.altinnmanagement.restserver

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.http.OutgoingResponse
import no.kartverket.altinnmanagement.restserver.models.FieldError
import no.kartverket.altinnmanagement.restserver.models.ValidationCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MaskinportenSchemasTest {

    private val altinnRequests = mutableListOf<OutgoingRequest>()
    private val filteringRequests = mutableListOf<OutgoingRequest>()

    private fun resource(id: String, scope: String, status: String = "Completed") = """
        {"identifier":"$id","resourceType":"MaskinportenSchema","status":"$status","delegable":true,"visible":true,
        "title":{"en":"$id","nb":"$id","nn":"$id"},"description":{"nb":"Om $id"},"rightDescription":{"nb":"Kan bruke $id"},
        "contactPoints":[{"category":"Kundesenter","email":"kundesenter@kartverket.no","telephone":"","contactPage":""}],
        "keywords":null,"homepage":"https://www.kartverket.no/","version":"1","accessListMode":"Disabled",
        "resourceReferences":[{"referenceSource":"Default","reference":"$scope","referenceType":"MaskinportenScope"}]}
    """.trimIndent()

    private val resources = listOf(
        resource("nrl-reporting", "kartverk:nrl.rapportering"),
        resource("nrl-retrieval", "kartverk:nrl.rapportering", status = "Withdrawn"),
        resource("kv-other", "kartverk:nrl.rapportering"),
        resource("nrl-longer", "kartverk:nrl.rapportering.read"),
    ).joinToString(",", "[", "]")

    private fun maskinportenSchemasTest(
        filtering: ((OutgoingRequest) -> OutgoingResponse)? = {
            json(200, """{"result":{"scopes":{"exact":[],"prefix":[]},"resources":{"exact":["kv-matrikkel"],"prefix":["nrl-"]}}}""")
        },
        altinn: (OutgoingRequest) -> OutgoingResponse = { json(200, resources) },
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testServer(recording(altinnRequests, altinn), filtering?.let { recording(filteringRequests, it) }, block)

    private suspend fun ApplicationTestBuilder.list(query: String = "scope=kartverk%3Anrl.rapportering"): HttpResponse =
        client.get("/api/maskinportenschemas?$query") { header(CLIENT_CERTIFICATE_HEADER, CLIENT_CERTIFICATE) }

    private suspend fun HttpResponse.ids(): List<String> =
        Json.parseToJsonElement(bodyAsText()).jsonArray.map { it.jsonObject.getValue("resourceId").jsonPrimitive.content }

    @Test
    fun `returns the resources for exactly the scope that the calling app has access to, withdrawn ones included`() =
        maskinportenSchemasTest {
            val response = list()

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(listOf("nrl-reporting", "nrl-retrieval"), response.ids())
        }

    @Test
    fun `sends the calling app's certificate to the filtering service, and asks Altinn for Kartverket's resources`() =
        maskinportenSchemasTest {
            list()

            assertEquals(CLIENT_CERTIFICATE, filteringRequests.single().certificateSentToFiltering())
            assertEquals(
                "https://altinn.test/resourceregistry/api/v1/resource/search?organizationId=971040238&resourceType=MaskinportenSchema",
                altinnRequests.single().url.toString(),
            )
        }

    @Test
    fun `answers in this API's format, with Altinn's names changed`() = maskinportenSchemasTest {
        val body = Json.parseToJsonElement(list().bodyAsText()).jsonArray.first()

        val expected = """
            {"resourceId":"nrl-reporting","scopes":["kartverk:nrl.rapportering"],
            "title":{"en":"nrl-reporting","nb":"nrl-reporting","nn":"nrl-reporting"},
            "description":{"nb":"Om nrl-reporting"},"delegationDescription":{"nb":"Kan bruke nrl-reporting"},
            "homepage":"https://www.kartverket.no/",
            "contactPoints":[{"category":"Kundesenter","email":"kundesenter@kartverket.no","telephone":"","contactPage":""}],
            "keywords":[],"status":"Completed","delegable":true,"visible":true}
        """.trimIndent()
        assertEquals(Json.parseToJsonElement(expected), body)
    }

    @Test
    fun `no resources for the scope is an empty list`() = maskinportenSchemasTest {
        val response = list("scope=kartverk%3Anothing")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(JsonArray(emptyList()), Json.parseToJsonElement(response.bodyAsText()))
    }

    @Test
    fun `the query must be exactly one scope, without whitespace`() = maskinportenSchemasTest {
        val refused = mapOf(
            "" to FieldError("scope", ValidationCode.MISSING, "scope is required"),
            "scope=" to FieldError("scope", ValidationCode.MISSING, "scope is required"),
            "scope=kartverk%3Aa+b" to FieldError("scope", ValidationCode.INVALID_FORMAT, "scope must not contain whitespace"),
            "scope=kartverk%3Aa&scope=kartverk%3Ab" to FieldError("scope", ValidationCode.NOT_ALLOWED, "scope must be sent once"),
            "scope=kartverk%3Aa&reference=x" to FieldError("reference", ValidationCode.NOT_ALLOWED, "reference is not allowed"),
        )
        for ((query, error) in refused) {
            val response = list(query)

            assertEquals(HttpStatusCode.BadRequest, response.status, query)
            assertEquals(validationFailed(error), response.errorResponse(), query)
        }
        assertTrue(filteringRequests.isEmpty())
        assertTrue(altinnRequests.isEmpty())
    }

    // Ktor's test client rewrites such a query before sending it, so the parser is called directly
    @Test
    fun `a query that is not names and values is refused, since another parser could read it differently`() {
        for (query in listOf("=scope=kartverk:a", "scope=kartverk:a&", "scope")) {
            assertEquals(
                "The query must contain only the parameter scope, sent once",
                assertFailsWith<MalformedRequestException>(query) { RequestInput().queryParameters(query, "scope") }.message,
                query,
            )
        }
    }

    @Test
    fun `a call without the certificate header is refused before anything else is called`() = maskinportenSchemasTest {
        val response = client.get("/api/maskinportenschemas?scope=kartverk%3Aa")

        assertEquals(
            validationFailed(FieldError(CLIENT_CERTIFICATE_HEADER, ValidationCode.MISSING, "$CLIENT_CERTIFICATE_HEADER is required")),
            response.errorResponse(),
        )
        assertTrue(filteringRequests.isEmpty())
        assertTrue(altinnRequests.isEmpty())
    }

    @Test
    fun `gives 502 without calling Altinn when the filtering service fails`() = maskinportenSchemasTest(filtering = { json(500, "{}") }) {
        val response = list()

        assertEquals(upstreamError("The call to the filtering service failed"), response.errorResponse())
        assertTrue(altinnRequests.isEmpty())
    }

    @Test
    fun `an app the filtering service gives no resources gets an empty list`() =
        maskinportenSchemasTest(filtering = { json(200, "{}") }) {
            assertEquals(emptyList(), list().ids())
        }

    @Test
    fun `gives 502 when Altinn fails`() = maskinportenSchemasTest(altinn = { json(503, "{}") }) {
        assertEquals(upstreamError("The call to Altinn failed"), list().errorResponse())
    }

    @Test
    fun `with filtering turned off, returns every resource for the scope and needs no header`() =
        maskinportenSchemasTest(filtering = null) {
            val response = client.get("/api/maskinportenschemas?scope=kartverk%3Anrl.rapportering")

            assertEquals(listOf("nrl-reporting", "nrl-retrieval", "kv-other"), response.ids())
        }
}
