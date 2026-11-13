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
import no.kartverket.altinnmanagement.restserver.models.GenericAccessResource
import no.kartverket.altinnmanagement.restserver.models.ProblemType
import no.kartverket.altinnmanagement.restserver.models.ValidationCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WithdrawGenericAccessResourceTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private val genericAccessResource = resourceJson("GenericAccessResource")

    private fun altinn(
        resource: String = genericAccessResource,
        getStatus: Int = 200,
    ) = OutgoingHttpClient { request ->
        sent += request
        when (request.method) {
            HttpMethod.GET -> response(getStatus, "application/json; charset=utf-8", resource)
            else -> response(200, "application/json; charset=utf-8", "")
        }
    }

    private suspend fun ApplicationTestBuilder.withdraw(id: String = "kv-matrikkel-brukernavn") =
        client.put("/api/genericaccessresources/$id/withdraw")

    private val puts get() = sent.filter { it.method == HttpMethod.PUT }

    private val sentResource get() = Json.parseToJsonElement(puts.single().body!!.decodeToString()).jsonObject

    private val withdrawnResource = JsonObject(
        Json.parseToJsonElement(genericAccessResource).jsonObject.minus(setOf("versionId", "createdAt", "updatedAt")) + mapOf(
            "delegable" to JsonPrimitive(false),
            "visible" to JsonPrimitive(false),
            "status" to JsonPrimitive("Withdrawn"),
        ),
    )

    private val notFound = ErrorResponse.of(ProblemType.NOT_FOUND, HttpStatusCode.NotFound, "There is no GenericAccessResource with this id")

    @Test
    fun `answers with the withdrawn GenericAccessResource`() = testServer(altinn()) {
        val response = withdraw()

        assertEquals(HttpStatusCode.OK, response.status)
        val resource = Json.decodeFromString(GenericAccessResource.serializer(), response.bodyAsText())
        assertEquals("kv-matrikkel-brukernavn", resource.resourceId)
        assertEquals("Withdrawn", resource.status)
        assertEquals(false, resource.delegable)
        assertEquals(false, resource.visible)
    }

    @Test
    fun `changes delegable, visible and status, and keeps the rest of the resource as Altinn has it`() = testServer(altinn()) {
        withdraw()

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
            withdraw().assertProblem(notFound)

            assertTrue(puts.isEmpty(), "A resource was put: $puts")
        }

    @Test
    fun `gives 404 for a MaskinportenSchema, without changing it`() = testServer(altinn(resource = resourceJson())) {
        withdraw().assertProblem(notFound)

        assertTrue(puts.isEmpty(), "A resource was put: $puts")
    }

    @Test
    fun `gives 404 for another organisation's resource, without changing it`() =
        testServer(altinn(resource = genericAccessResource.replace("971040238", "991825827"))) {
            withdraw().assertProblem(notFound)

            assertTrue(puts.isEmpty(), "A resource was put: $puts")
        }
}
