package no.kartverket.altinnmanagement.restserver.genericaccessresource

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
import no.kartverket.altinnmanagement.restserver.ContactPoint
import no.kartverket.altinnmanagement.restserver.Keyword
import no.kartverket.altinnmanagement.restserver.MultilingualText
import no.kartverket.altinnmanagement.restserver.TEST_TOKEN
import no.kartverket.altinnmanagement.restserver.assertProblem
import no.kartverket.altinnmanagement.restserver.errorResponse
import no.kartverket.altinnmanagement.restserver.genericAccessResourceNotFound
import no.kartverket.altinnmanagement.restserver.problem.ErrorResponse
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ProblemType
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode
import no.kartverket.altinnmanagement.restserver.testServer
import no.kartverket.altinnmanagement.restserver.upstreamError
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PutGenericAccessResourceTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private val genericAccessResource = resourceJson("GenericAccessResource")

    private fun altinn(
        resource: String = genericAccessResource,
        getStatus: Int = 200,
        putStatus: Int = 200,
        putBody: String = "",
    ) = OutgoingHttpClient { request ->
        sent += request
        when (request.method) {
            HttpMethod.GET -> response(getStatus, "application/json; charset=utf-8", resource)
            else -> response(putStatus, "application/json; charset=utf-8", putBody)
        }
    }

    private val path = "/api/genericaccessresources/kv-matrikkel-brukernavn"

    private val body = """
        {
          "title": { "en": "New Cadastre API", "nb": "Ny Matrikkel API", "nn": "Ny Matrikkel API" },
          "description": { "en": "New description.", "nb": "Ny beskrivelse.", "nn": "Ny skildring." },
          "delegationDescription": { "en": "New rights.", "nb": "Nye rettigheter.", "nn": "Nye rettar." },
          "homepage": "https://kartverket.no/ny",
          "contactPoints": [{ "category": "Kundesenter", "email": "post@kartverket.no" }],
          "keywords": [{ "word": "ny", "language": "nb" }]
        }
    """.trimIndent()

    private val requiredOnly = """
        {
          "title": { "en": "New Cadastre API", "nb": "Ny Matrikkel API", "nn": "Ny Matrikkel API" },
          "description": { "en": "New description.", "nb": "Ny beskrivelse.", "nn": "Ny skildring." },
          "delegationDescription": { "en": "New rights.", "nb": "Nye rettigheter.", "nn": "Nye rettar." }
        }
    """.trimIndent()

    private val withoutDelegationDescription = requiredOnly.replace(Regex(""",\s*"delegationDescription": \{[^}]*\}"""), "")

    private suspend fun ApplicationTestBuilder.put(body: String, path: String = this@PutGenericAccessResourceTest.path) =
        client.put(path) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private val puts get() = sent.filter { it.method == HttpMethod.PUT }

    private val sentResource get() = Json.parseToJsonElement(puts.single().body!!.decodeToString()).jsonObject

    private fun validationError(vararg errors: FieldError) =
        ErrorResponse.of(ProblemType.VALIDATION_ERROR, HttpStatusCode.BadRequest, errors = errors.toList())

    private fun JsonObject.with(vararg fields: Pair<String, String>) =
        JsonObject(this + fields.associate { (key, value) -> key to Json.parseToJsonElement(value) })

    @Test
    fun `answers with the changed GenericAccessResource`() = testServer(altinn()) {
        val response = put(body)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            GenericAccessResource(
                resourceId = "kv-matrikkel-brukernavn",
                title = MultilingualText(en = "New Cadastre API", nb = "Ny Matrikkel API", nn = "Ny Matrikkel API"),
                description = MultilingualText(en = "New description.", nb = "Ny beskrivelse.", nn = "Ny skildring."),
                delegationDescription = MultilingualText(en = "New rights.", nb = "Nye rettigheter.", nn = "Nye rettar."),
                homepage = "https://kartverket.no/ny",
                contactPoints = listOf(ContactPoint(category = "Kundesenter", email = "post@kartverket.no")),
                keywords = listOf(Keyword(word = "ny", language = "nb")),
                status = "Completed",
                delegable = true,
                visible = true,
            ),
            Json.decodeFromString(GenericAccessResource.serializer(), response.bodyAsText()),
        )
    }

    @Test
    fun `gets the resource from Altinn without a token, and puts it back with one`() = testServer(altinn()) {
        put(body)

        val (get, put) = sent
        assertEquals(HttpMethod.GET, get.method)
        assertEquals("https://platform.tt02.altinn.no/resourceregistry/api/v1/resource/kv-matrikkel-brukernavn", get.url.toString())
        assertNull(get.headers["Authorization"])
        assertEquals(HttpMethod.PUT, put.method)
        assertEquals("https://platform.tt02.altinn.no/resourceregistry/api/v1/resource/kv-matrikkel-brukernavn", put.url.toString())
        assertEquals("Bearer $TEST_TOKEN", put.headers["Authorization"])
        assertEquals("application/json", put.headers["Content-Type"])
    }

    @Test
    fun `replaces this API's fields and keeps the rest of the resource as Altinn has it, its references too`() = testServer(altinn()) {
        put(body)

        val expected = Json.parseToJsonElement(genericAccessResource).jsonObject
            .minus(setOf("versionId", "createdAt", "updatedAt"))
            .let(::JsonObject)
            .with(
                "title" to """{ "en": "New Cadastre API", "nb": "Ny Matrikkel API", "nn": "Ny Matrikkel API" }""",
                "description" to """{ "en": "New description.", "nb": "Ny beskrivelse.", "nn": "Ny skildring." }""",
                "rightDescription" to """{ "en": "New rights.", "nb": "Nye rettigheter.", "nn": "Nye rettar." }""",
                "homepage" to "\"https://kartverket.no/ny\"",
                "contactPoints" to """[{ "category": "Kundesenter", "email": "post@kartverket.no" }]""",
                "keywords" to """[{ "word": "ny", "language": "nb" }]""",
            )
        assertEquals(expected, sentResource)
    }

    @Test
    fun `keeps the access list mode, status, delegable and visible as Altinn has them`() {
        val resource = genericAccessResource
            .replace(""""accessListMode": "Disabled"""", """"accessListMode": "Enabled"""")
            .replace(""""status": "Completed"""", """"status": "UnderDevelopment"""")
            .replace(""""delegable": true""", """"delegable": false""")
            .replace(""""visible": true""", """"visible": false""")
        testServer(altinn(resource = resource)) {
            val response = put(body)

            assertEquals(JsonPrimitive("Enabled"), sentResource["accessListMode"])
            assertEquals(JsonPrimitive("UnderDevelopment"), sentResource["status"])
            assertEquals(JsonPrimitive(false), sentResource["delegable"])
            assertEquals(JsonPrimitive(false), sentResource["visible"])
            val answered = Json.decodeFromString(GenericAccessResource.serializer(), response.bodyAsText())
            assertEquals(Triple("UnderDevelopment", false, false), Triple(answered.status, answered.delegable, answered.visible))
        }
    }

    @Test
    fun `removes the homepage, contact points and keywords that are left out`() = testServer(altinn()) {
        val response = put(requiredOnly)

        assertFalse("homepage" in sentResource)
        assertEquals("[]", sentResource["contactPoints"].toString())
        assertEquals("[]", sentResource["keywords"].toString())
        assertEquals(
            setOf("resourceId", "title", "description", "delegationDescription", "contactPoints", "keywords", "status", "delegable", "visible"),
            Json.parseToJsonElement(response.bodyAsText()).jsonObject.keys,
        )
    }

    @Test
    fun `accepts the resourceId in the body when it is the id in the path`() = testServer(altinn()) {
        val response = put(body.replaceFirst("{", """{ "resourceId": "kv-matrikkel-brukernavn","""))

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `gives 400 when the resourceId in the body is not the id in the path, without asking Altinn`() = testServer(altinn()) {
        put(body.replaceFirst("{", """{ "resourceId": "kv-annen-ressurs",""")).assertProblem(
            validationError(FieldError("resourceId", ValidationCode.INVALID_FORMAT, "resourceId must be the id in the path")),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 for every field that is missing whatever the resource is, without asking Altinn`() = testServer(altinn()) {
        put("{}").assertProblem(
            validationError(
                FieldError("title", ValidationCode.MISSING, "title is required"),
                FieldError("description", ValidationCode.MISSING, "description is required"),
            ),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 and not 404 for a body with errors when Altinn has no resource with the id`() =
        testServer(altinn(getStatus = 404, resource = """{"title":"Not Found","status":404}""")) {
            put(withoutDelegationDescription.replace(""""nn": "Ny Matrikkel API"""", """"nn": " """")).assertProblem(
                validationError(FieldError("title.nn", ValidationCode.MISSING, "title.nn is required")),
            )
        }

    @Test
    fun `gives 400 for a title or description that is missing or blank in a language, since Altinn requires all three`() =
        testServer(altinn()) {
            put(requiredOnly.replace(""""nn": "Ny Matrikkel API"""", """"nn": " """").replace(""""en": "New description.", """, ""))
                .assertProblem(
                    validationError(
                        FieldError("title.nn", ValidationCode.MISSING, "title.nn is required"),
                        FieldError("description.en", ValidationCode.MISSING, "description.en is required"),
                    ),
                )
        }

    @Test
    fun `gives 400 for a delegation description that is missing or missing in a language when the resource is delegable`() =
        testServer(altinn()) {
            put(withoutDelegationDescription).assertProblem(
                validationError(FieldError("delegationDescription", ValidationCode.MISSING, "delegationDescription is required")),
            )
            put(requiredOnly.replace(""""en": "New rights.", """, "")).assertProblem(
                validationError(FieldError("delegationDescription.en", ValidationCode.MISSING, "delegationDescription.en is required")),
            )

            assertTrue(puts.isEmpty(), "A resource was put: $puts")
        }

    @Test
    fun `takes a resource without delegable as delegable, as Altinn does`() =
        testServer(altinn(resource = genericAccessResource.replace(""""delegable": true,""", ""))) {
            put(withoutDelegationDescription).assertProblem(
                validationError(FieldError("delegationDescription", ValidationCode.MISSING, "delegationDescription is required")),
            )
        }

    @Test
    fun `lets the delegation description be left out, in a language or whole, when the resource is not delegable`() =
        testServer(altinn(resource = genericAccessResource.replace(""""delegable": true""", """"delegable": false"""))) {
            val withoutEnglish = put(requiredOnly.replace(""""en": "New rights.", """, ""))

            assertEquals(HttpStatusCode.OK, withoutEnglish.status)
            assertEquals("""{"nb":"Nye rettigheter.","nn":"Nye rettar."}""", sentResource["rightDescription"].toString())

            sent.clear()
            val response = put(withoutDelegationDescription)

            assertEquals(HttpStatusCode.OK, response.status)
            assertFalse("rightDescription" in sentResource)
        }

    @Test
    fun `gives 400 for an id Altinn would not accept, without asking Altinn`() = testServer(altinn()) {
        put(body, path = "/api/genericaccessresources/Kv-matrikkel").assertProblem(
            validationError(FieldError("id", ValidationCode.INVALID_FORMAT, "id must be 4 or more of a-z, 0-9, _ and -")),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 for a body it cannot read, or with fields it cannot change, without asking Altinn`() = testServer(altinn()) {
        val unreadable = listOf(
            "not json",
            "",
            "[]",
            body.replace("\"https://kartverket.no/ny\"", "42"),
            body.replaceFirst("{", """{ "scopes": ["kartverk:matrikkel:ny"],"""),
            body.replaceFirst("{", """{ "delegable": false,"""),
            body.replaceFirst("{", """{ "status": "Withdrawn","""),
        )
        for (unreadableBody in unreadable) {
            val response = put(unreadableBody)

            assertEquals(HttpStatusCode.BadRequest, response.status, "for $unreadableBody")
            assertEquals(ProblemType.MALFORMED_REQUEST, response.errorResponse().type, "for $unreadableBody")
        }

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 for a body that gives a name twice, without asking Altinn`() = testServer(altinn()) {
        put(body.replace(""""en": "New Cadastre API",""", """"en": "New Cadastre API", "en": "Other",""")).assertProblem(
            ErrorResponse.of(ProblemType.MALFORMED_REQUEST, HttpStatusCode.BadRequest, "The body must be one JSON object, with no name given twice"),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 404 when Altinn has no resource with the id, without changing anything`() =
        testServer(altinn(getStatus = 404, resource = """{"title":"Not Found","status":404}""")) {
            put(body).assertProblem(genericAccessResourceNotFound)

            assertTrue(puts.isEmpty(), "A resource was put: $puts")
        }

    @Test
    fun `gives 404 for a MaskinportenSchema, without changing it`() = testServer(altinn(resource = resourceJson())) {
        put(body).assertProblem(genericAccessResourceNotFound)

        assertTrue(puts.isEmpty(), "A resource was put: $puts")
    }

    @Test
    fun `gives 404 for another organisation's resource, without changing it`() =
        testServer(altinn(resource = genericAccessResource.replace("\"971040238\"", "\"991825827\""))) {
            put(body).assertProblem(genericAccessResourceNotFound)

            assertTrue(puts.isEmpty(), "A resource was put: $puts")
        }

    @Test
    fun `gives 400 with Altinn's reasons when Altinn rejects the resource`() = testServer(
        altinn(
            putStatus = 400,
            putBody = """
                {"type":"https://tools.ietf.org/html/rfc9110#section-15.5.1","title":"One or more validation errors occurred.","status":400,
                 "errors":{"RightDescription":["Missing RightDescription in english en"]}}
            """.trimIndent(),
        ),
    ) {
        put(body).assertProblem(
            ErrorResponse.of(
                ProblemType.UPSTREAM_REJECTED,
                HttpStatusCode.BadRequest,
                "Altinn rejected the resource: RightDescription: Missing RightDescription in english en",
            ),
        )
    }

    @Test
    fun `gives 502 when Altinn fails to change the resource`() = testServer(altinn(putStatus = 500, putBody = "boom")) {
        put(body).assertProblem(upstreamError)
    }

    @Test
    fun `gives 502 when Altinn cannot be reached`() = testServer({ throw IOException("Connection refused") }) {
        put(body).assertProblem(upstreamError)
    }
}
