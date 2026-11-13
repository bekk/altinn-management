package no.kartverket.altinnmanagement.restserver

import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.support.resourceJson
import no.kartverket.altinnmanagement.client.support.response
import no.kartverket.altinnmanagement.restserver.models.ErrorResponse
import no.kartverket.altinnmanagement.restserver.models.FieldError
import no.kartverket.altinnmanagement.restserver.models.MaskinportenSchema
import no.kartverket.altinnmanagement.restserver.models.ProblemType
import no.kartverket.altinnmanagement.restserver.models.ValidationCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WithdrawMaskinportenSchemaTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun altinn(
        resource: String = resourceJson(),
        getStatus: Int = 200,
    ) = OutgoingHttpClient { request ->
        sent += request
        when (request.method) {
            HttpMethod.GET -> response(getStatus, "application/json; charset=utf-8", resource)
            else -> response(200, "application/json; charset=utf-8", "")
        }
    }

    private suspend fun ApplicationTestBuilder.withdraw(id: String = "kv-matrikkel-brukernavn") =
        client.put("/api/maskinportenschemas/$id/withdraw")

    private val puts get() = sent.filter { it.method == HttpMethod.PUT }

    private val sentResource get() = Json.parseToJsonElement(puts.single().body!!.decodeToString()).jsonObject

    private val withdrawnResource = JsonObject(
        Json.parseToJsonElement(resourceJson()).jsonObject.minus(setOf("versionId", "createdAt", "updatedAt")) + mapOf(
            "delegable" to JsonPrimitive(false),
            "visible" to JsonPrimitive(false),
            "status" to JsonPrimitive("Withdrawn"),
        ),
    )

    @Test
    fun `answers with the withdrawn MaskinportenSchema`() = testServer(altinn()) {
        val response = withdraw()

        assertEquals(HttpStatusCode.OK, response.status)
        val schema = Json.decodeFromString(MaskinportenSchema.serializer(), response.bodyAsText())
        assertEquals("Withdrawn", schema.status)
        assertEquals(false, schema.delegable)
        assertEquals(false, schema.visible)
        assertEquals(listOf("kartverk:matrikkel:brukernavn"), schema.scopes)
    }

    @Test
    fun `changes delegable, visible and status, and keeps the rest of the resource as Altinn has it`() = testServer(altinn()) {
        withdraw()

        assertEquals(withdrawnResource, sentResource)
    }

    @Test
    fun `sends the same resource when it is already withdrawn`() = testServer(altinn(resource = withdrawnResource.toString())) {
        val response = withdraw()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(withdrawnResource, sentResource)
    }

    @Test
    fun `gives 400 for an id Altinn would not accept, without asking Altinn`() = testServer(altinn()) {
        withdraw("Kv-matrikkel").assertProblem(
            ErrorResponse.of(
                ProblemType.VALIDATION_ERROR,
                HttpStatusCode.BadRequest,
                errors = listOf(FieldError("id", ValidationCode.INVALID_FORMAT, "id must be 4 or more of a-z, 0-9, _ and -")),
            ),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 404 when Altinn has no resource with the id, without changing anything`() =
        testServer(altinn(getStatus = 404, resource = """{"title":"Not Found","status":404}""")) {
            withdraw().assertProblem(maskinportenSchemaNotFound)

            assertTrue(puts.isEmpty(), "A resource was put: $puts")
        }
}
