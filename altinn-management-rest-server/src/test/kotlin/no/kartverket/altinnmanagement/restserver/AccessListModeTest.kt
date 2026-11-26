package no.kartverket.altinnmanagement.restserver

import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
import no.kartverket.altinnmanagement.restserver.problem.ErrorResponse
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ProblemType
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccessListModeTest {

    private class ResourceType(val path: String, val resource: String, val notFound: ErrorResponse)

    private val types = listOf(
        ResourceType("maskinportenschemas", resourceJson(), maskinportenSchemaNotFound),
        ResourceType("genericaccessresources", resourceJson("GenericAccessResource"), genericAccessResourceNotFound),
    )

    private val sent = mutableListOf<OutgoingRequest>()

    private fun altinn(type: ResourceType, getStatus: Int = 200) = OutgoingHttpClient { request ->
        sent += request
        when (request.method) {
            HttpMethod.GET -> response(getStatus, "application/json; charset=utf-8", if (getStatus == 200) type.resource else """{"status":404}""")
            else -> response(200, "application/json; charset=utf-8", "")
        }
    }

    private suspend fun ApplicationTestBuilder.setMode(type: ResourceType, body: String) =
        client.put("/api/${type.path}/kv-matrikkel-brukernavn/accesslistmode") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private val puts get() = sent.filter { it.method == HttpMethod.PUT }

    @Test
    fun `turns access lists on, and keeps the rest of the resource as Altinn has it`() {
        for (type in types) {
            sent.clear()
            testServer(altinn(type)) {
                val response = setMode(type, """{"accessListMode":"Enabled"}""")

                assertEquals(HttpStatusCode.OK, response.status, type.path)
                assertEquals(JsonPrimitive("Enabled"), Json.parseToJsonElement(response.bodyAsText()).jsonObject["accessListMode"], type.path)
                val expected = JsonObject(
                    Json.parseToJsonElement(type.resource).jsonObject.minus(setOf("versionId", "createdAt", "updatedAt")) +
                        mapOf("accessListMode" to JsonPrimitive("Enabled")),
                )
                assertEquals(expected, Json.parseToJsonElement(puts.single().body!!.decodeToString()).jsonObject, type.path)
            }
        }
    }

    @Test
    fun `gives 400 for a mode that is missing or not one of Altinn's, without asking Altinn`() {
        val missing = FieldError("accessListMode", ValidationCode.MISSING, "accessListMode is required")
        val invalid = FieldError("accessListMode", ValidationCode.INVALID_FORMAT, "accessListMode must be one of Enabled, Disabled")
        for (type in types) {
            testServer(altinn(type)) {
                for ((body, error) in listOf("{}" to missing, """{"accessListMode":"enabled"}""" to invalid)) {
                    setMode(type, body).assertProblem(ErrorResponse.of(ProblemType.VALIDATION_ERROR, HttpStatusCode.BadRequest, errors = listOf(error)))
                }
            }
        }

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 404 when Altinn has no resource of the type with the id, without changing anything`() {
        for (type in types) {
            testServer(altinn(type, getStatus = 404)) {
                setMode(type, """{"accessListMode":"Enabled"}""").assertProblem(type.notFound)
            }
        }

        assertTrue(puts.isEmpty(), "A resource was put: $puts")
    }
}
