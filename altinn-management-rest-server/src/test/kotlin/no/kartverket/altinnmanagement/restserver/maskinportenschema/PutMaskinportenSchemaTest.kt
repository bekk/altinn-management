package no.kartverket.altinnmanagement.restserver.maskinportenschema

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
import no.kartverket.altinnmanagement.restserver.maskinportenSchemaNotFound
import no.kartverket.altinnmanagement.restserver.problem.ErrorResponse
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ProblemType
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode
import no.kartverket.altinnmanagement.restserver.testServer
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PutMaskinportenSchemaTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun altinn(
        resource: String = resourceJson(),
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

    private val path = "/api/maskinportenschemas/kv-matrikkel-brukernavn"

    private val body = """
        {
          "scopes": ["kartverk:matrikkel:ny"],
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
          "scopes": ["kartverk:matrikkel:ny"],
          "title": { "en": "New Cadastre API", "nb": "Ny Matrikkel API", "nn": "Ny Matrikkel API" },
          "description": { "en": "New description.", "nb": "Ny beskrivelse.", "nn": "Ny skildring." },
          "delegationDescription": { "en": "New rights.", "nb": "Nye rettigheter.", "nn": "Nye rettar." }
        }
    """.trimIndent()

    private val withoutDelegationDescription = requiredOnly.replace(Regex(""",\s*"delegationDescription": \{[^}]*\}"""), "")

    private suspend fun ApplicationTestBuilder.put(body: String, contentType: ContentType = ContentType.Application.Json, path: String = this@PutMaskinportenSchemaTest.path) =
        client.put(path) {
            contentType(contentType)
            setBody(body)
        }

    private val puts get() = sent.filter { it.method == HttpMethod.PUT }

    private val sentResource get() = Json.parseToJsonElement(puts.single().body!!.decodeToString()).jsonObject

    private fun validationError(vararg errors: FieldError) =
        ErrorResponse.of(ProblemType.VALIDATION_ERROR, HttpStatusCode.BadRequest, errors = errors.toList())

    private fun JsonObject.with(vararg fields: Pair<String, String>) =
        JsonObject(this + fields.associate { (key, value) -> key to Json.parseToJsonElement(value) })

    @Test
    fun `answers with the changed MaskinportenSchema`() = testServer(altinn()) {
        val response = put(body)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            MaskinportenSchema(
                resourceId = "kv-matrikkel-brukernavn",
                scopes = listOf("kartverk:matrikkel:ny"),
                title = MultilingualText(en = "New Cadastre API", nb = "Ny Matrikkel API", nn = "Ny Matrikkel API"),
                description = MultilingualText(en = "New description.", nb = "Ny beskrivelse.", nn = "Ny skildring."),
                delegationDescription = MultilingualText(en = "New rights.", nb = "Nye rettigheter.", nn = "Nye rettar."),
                homepage = "https://kartverket.no/ny",
                contactPoints = listOf(ContactPoint(category = "Kundesenter", email = "post@kartverket.no")),
                keywords = listOf(Keyword(word = "ny", language = "nb")),
                status = "Completed",
                delegable = true,
                visible = true,
                accessListMode = "Disabled",
            ),
            Json.decodeFromString(MaskinportenSchema.serializer(), response.bodyAsText()),
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
    fun `replaces this API's fields and keeps the rest of the resource as Altinn has it`() = testServer(altinn()) {
        put(body)

        val expected = Json.parseToJsonElement(resourceJson()).jsonObject
            .minus(setOf("versionId", "createdAt", "updatedAt"))
            .let(::JsonObject)
            .with(
                "title" to """{ "en": "New Cadastre API", "nb": "Ny Matrikkel API", "nn": "Ny Matrikkel API" }""",
                "description" to """{ "en": "New description.", "nb": "Ny beskrivelse.", "nn": "Ny skildring." }""",
                "rightDescription" to """{ "en": "New rights.", "nb": "Nye rettigheter.", "nn": "Nye rettar." }""",
                "homepage" to "\"https://kartverket.no/ny\"",
                "contactPoints" to """[{ "category": "Kundesenter", "email": "post@kartverket.no" }]""",
                "keywords" to """[{ "word": "ny", "language": "nb" }]""",
                "resourceReferences" to """
                    [
                      { "referenceSource": "Default", "reference": "kartverk:matrikkel:ny", "referenceType": "MaskinportenScope" },
                      { "referenceSource": "Default", "reference": "https://kartverket.no", "referenceType": "Uri" }
                    ]
                """,
            )
        assertEquals(expected, sentResource)
    }

    @Test
    fun `removes the homepage, contact points and keywords that are left out`() = testServer(altinn()) {
        val response = put(requiredOnly)

        assertFalse("homepage" in sentResource)
        assertEquals("[]", sentResource["contactPoints"].toString())
        assertEquals("[]", sentResource["keywords"].toString())
        assertEquals(setOf("resourceId", "scopes", "title", "description", "delegationDescription", "contactPoints", "keywords", "status", "delegable", "visible", "accessListMode"), Json.parseToJsonElement(response.bodyAsText()).jsonObject.keys)
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
    fun `gives 400 for every required field that is missing, without asking Altinn`() = testServer(altinn()) {
        put("{}").assertProblem(
            validationError(
                FieldError("scopes", ValidationCode.MISSING, "scopes is required"),
                FieldError("title", ValidationCode.MISSING, "title is required"),
                FieldError("description", ValidationCode.MISSING, "description is required"),
            ),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 for an empty list of scopes`() = testServer(altinn()) {
        put(requiredOnly.replace("""["kartverk:matrikkel:ny"]""", "[]")).assertProblem(
            validationError(FieldError("scopes", ValidationCode.MISSING, "scopes is required")),
        )
    }

    @Test
    fun `gives 400 for a blank scope`() = testServer(altinn()) {
        put(requiredOnly.replace("""["kartverk:matrikkel:ny"]""", """["kartverk:ok", " ", ""]""")).assertProblem(
            validationError(
                FieldError("scopes[1]", ValidationCode.INVALID_FORMAT, "scopes[1] must not be blank"),
                FieldError("scopes[2]", ValidationCode.INVALID_FORMAT, "scopes[2] must not be blank"),
            ),
        )
    }

    @Test
    fun `gives 400 for a scope with whitespace, which no OAuth scope can hold, without asking Altinn`() = testServer(altinn()) {
        put(requiredOnly.replace("""["kartverk:matrikkel:ny"]""", """["kartverk:ok", "kartverk:a b", "kartverk:a\tb"]""")).assertProblem(
            validationError(
                FieldError("scopes[1]", ValidationCode.INVALID_FORMAT, "scopes[1] must not contain whitespace"),
                FieldError("scopes[2]", ValidationCode.INVALID_FORMAT, "scopes[2] must not contain whitespace"),
            ),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
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
        testServer(altinn(resource = resourceJson().replace(""""delegable": true,""", ""))) {
            put(withoutDelegationDescription).assertProblem(
                validationError(FieldError("delegationDescription", ValidationCode.MISSING, "delegationDescription is required")),
            )
        }

    @Test
    fun `lets the delegation description be left out, in a language or whole, when the resource is not delegable`() =
        testServer(altinn(resource = resourceJson().replace(""""delegable": true""", """"delegable": false"""))) {
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
        put(body, path = "/api/maskinportenschemas/Kv-matrikkel").assertProblem(
            validationError(FieldError("id", ValidationCode.INVALID_FORMAT, "id must be 4 or more of a-z, 0-9, _ and -")),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 for a body it cannot read, without asking Altinn`() = testServer(altinn()) {
        val unreadable = listOf(
            "not json" to ContentType.Application.Json,
            "" to ContentType.Application.Json,
            "[]" to ContentType.Application.Json,
            body.replace("\"https://kartverket.no/ny\"", "42") to ContentType.Application.Json,
            body.replace("\"https://kartverket.no/ny\"", "{}") to ContentType.Application.Json,
            body.replaceFirst("{", """{ "status": "Withdrawn",""") to ContentType.Application.Json,
            body to ContentType.Text.Plain,
        )
        for ((unreadableBody, contentType) in unreadable) {
            val response = put(unreadableBody, contentType)

            assertEquals(HttpStatusCode.BadRequest, response.status, "for $unreadableBody as $contentType")
            assertEquals(ProblemType.MALFORMED_REQUEST, response.errorResponse().type, "for $unreadableBody as $contentType")
        }

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    private suspend fun ApplicationTestBuilder.putBytes(body: ByteArray, contentType: String?) = client.put(path) {
        contentType?.let { header(HttpHeaders.ContentType, it) }
        setBody(body)
    }

    private fun malformed(detail: String) = ErrorResponse.of(ProblemType.MALFORMED_REQUEST, HttpStatusCode.BadRequest, detail)

    @Test
    fun `gives 400 for a body that gives a name twice or holds more than one value, so it is read as filtering reads it`() = testServer(altinn()) {
        val ambiguous = listOf(
            body.replaceFirst("{", """{ "scopes": ["kartverk:annet"],"""),
            body.replace(""""en": "New Cadastre API",""", """"en": "New Cadastre API", "en": "Other",""""),
            "$body {}",
        )
        for (ambiguousBody in ambiguous) {
            put(ambiguousBody).assertProblem(malformed("The body must be one JSON object, with no name given twice"))
        }

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 for a body that is not UTF-8, whatever charset it names`() = testServer(altinn()) {
        putBytes(body.replace("New", "Ny å").toByteArray(Charsets.ISO_8859_1), "application/json; charset=ISO-8859-1")
            .assertProblem(malformed("The body must be UTF-8"))

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 for a body that is not sent as JSON`() = testServer(altinn()) {
        for (contentType in listOf(null, "text/plain", "application/xml", "application/json-patch+json")) {
            putBytes(body.toByteArray(), contentType)
                .assertProblem(malformed("The body must be JSON, sent with Content-Type application/json"))
        }

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `does not log or echo a body it cannot read`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        root.addAppender(appender)
        try {
            testServer(altinn()) {
                for (unreadable in listOf("""{ "secret-marker": 1 }""", """{ "secret-marker": 1, "secret-marker": 2 }""")) {
                    val response = put(unreadable)

                    assertFalse("secret-marker" in response.bodyAsText())
                }
            }
            val logged = appender.list.flatMap { listOfNotNull(it.formattedMessage, it.throwableProxy?.let(ThrowableProxyUtil::asString)) }
            assertTrue(logged.none { "secret-marker" in it }, "The body was logged: $logged")
        } finally {
            root.detachAppender(appender)
        }
    }

    @Test
    fun `gives 404 when Altinn has no resource with the id, without changing anything`() =
        testServer(altinn(getStatus = 404, resource = """{"title":"Not Found","status":404}""")) {
            put(body).assertProblem(maskinportenSchemaNotFound)

            assertTrue(puts.isEmpty(), "A resource was put: $puts")
        }
}
