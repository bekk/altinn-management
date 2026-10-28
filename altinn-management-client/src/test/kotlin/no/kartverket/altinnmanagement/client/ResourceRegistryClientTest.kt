package no.kartverket.altinnmanagement.client

import kotlinx.coroutines.runBlocking
import no.kartverket.altinnmanagement.client.exception.AltinnException
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.support.TestHttpServer
import no.kartverket.altinnmanagement.client.support.TestResponse
import no.kartverket.altinnmanagement.client.support.resourceJson
import no.kartverket.altinnmanagement.client.support.testHttpClient
import java.io.IOException
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ResourceRegistryClientTest {

    private lateinit var server: TestHttpServer

    @BeforeTest
    fun startServer() {
        server = TestHttpServer.start()
    }

    @AfterTest
    fun stopServer() = server.close()

    private val id = ResourceId.of("kv-matrikkel-brukernavn")

    private val path = "${ResourceRegistryClient.RESOURCE_PATH}/kv-matrikkel-brukernavn"

    private fun client(httpClient: OutgoingHttpClient = testHttpClient) =
        ResourceRegistryClient(URI(server.baseUrl), OrganizationNumber.of("971040238"), httpClient)

    private fun altinnAnswers(body: String) = server.on(path) { TestResponse(body = body) }

    @Test
    fun `gets the resource with a GET, without a token`() = runBlocking {
        altinnAnswers(resourceJson())

        client().getMaskinportenSchema(id)

        val request = server.lastRequest(path)
        assertEquals("GET", request.method)
        assertEquals("application/json", request.header("Accept"))
        assertNull(request.header("Authorization"))
    }

    @Test
    fun `reads the owner's MaskinportenSchema into strong types`() = runBlocking {
        altinnAnswers(resourceJson())

        assertEquals(
            MaskinportenSchema(
                resourceId = id,
                scopes = listOf(Scope.of("kartverk:matrikkel:brukernavn")),
                title = MultilingualText(
                    en = EnglishText.of("Cadastre API"),
                    nb = BokmalText.of("Matrikkel API"),
                    nn = NynorskText.of("Matrikkel API"),
                ),
                description = MultilingualText(
                    en = EnglishText.of("Machine-to-machine access to the Cadastre."),
                    nb = BokmalText.of("Maskinell tilgang til Matrikkelen."),
                    nn = NynorskText.of("Maskinell tilgang til Matrikkelen."),
                ),
                delegationDescription = MultilingualText(
                    en = EnglishText.of("Allows a system provider to use the Matrikkel API on behalf of your organisation."),
                    nb = BokmalText.of("Gir en systemleverandør rett til å bruke Matrikkel API på vegne av virksomheten din."),
                    nn = NynorskText.of("Gir ein systemleverandør rett til å bruke Matrikkel API på vegner av verksemda di."),
                ),
                homepage = WebAddress.of("https://kartverket.github.io/api-dokumentasjon/docs/matrikkelen"),
                contactPoints = listOf(
                    ContactPoint(
                        category = ContactCategory.of("Kartverket Kundesenter"),
                        email = EmailAddress.of("kundesenter@kartverket.no"),
                        telephone = null,
                        contactPage = null,
                    ),
                ),
                keywords = listOf(
                    Keyword(KeywordText.of("matrikkel"), Language.NB),
                    Keyword(KeywordText.of("cadastre"), Language.EN),
                ),
                status = ResourceStatus.COMPLETED,
                delegable = true,
                visible = true,
            ),
            client().getMaskinportenSchema(id),
        )
    }

    @Test
    fun `reads a resource without the fields Altinn does not require`() = runBlocking {
        altinnAnswers(
            resourceJson()
                .replace(Regex(""""(homepage|status)": "[^"]*",\s*"""), "")
                .replace(Regex(""""(contactPoints|keywords)": \[[^\]]*],\s*"""), ""),
        )

        val schema = client().getMaskinportenSchema(id)!!

        assertEquals(listOf(null, null), listOf(schema.homepage, schema.status))
        assertEquals(listOf(emptyList<Any>(), emptyList()), listOf(schema.contactPoints, schema.keywords))
    }

    @Test
    fun `reads a status a tool in Altinn has added as UNKNOWN`() = runBlocking {
        altinnAnswers(resourceJson().replace("\"Completed\"", "\"SomethingNew\""))

        assertEquals(ResourceStatus.UNKNOWN, client().getMaskinportenSchema(id)?.status)
    }

    @Test
    fun `gives null when Altinn has no resource with the id`() = runBlocking {
        server.on(path) { TestResponse(status = 404, body = """{"title":"Not Found","status":404}""") }

        assertNull(client().getMaskinportenSchema(id))
    }

    @Test
    fun `gives null for another organisation's resource, also when its values would not be valid here`() = runBlocking {
        altinnAnswers(resourceJson(organization = "974760223").replace("https://kartverket.github.io", "www.example.no"))

        assertNull(client().getMaskinportenSchema(id))
    }

    @Test
    fun `gives null for the owner's resources of other types`() = runBlocking {
        altinnAnswers(resourceJson(resourceType = "GenericAccessResource"))

        assertNull(client().getMaskinportenSchema(id))
    }

    @Test
    fun `gives null without asking Altinn for an id that Altinn sends to one of its own endpoints`() = runBlocking {
        val client = client { error("unexpected call to ${it.url}") }

        for (route in listOf("changes", "export", "resourcelist", "search", "updated")) {
            assertNull(client.getMaskinportenSchema(ResourceId.of(route)), "for $route")
        }
    }

    @Test
    fun `fails on the owner's resource when a value is not valid`() = runBlocking {
        val cases = mapOf(
            "a scope with whitespace" to resourceJson().replace("kartverk:matrikkel:brukernavn", "kartverk:matrikkel brukernavn"),
            "no scope" to resourceJson().replace("\"MaskinportenScope\"", "\"Uri\""),
            "a title without Nynorsk" to resourceJson().replace(", \"nn\": \"Matrikkel API\" }", " }"),
            "a blank Bokmål description" to resourceJson().replace("\"Maskinell tilgang til Matrikkelen.\",\n    \"nn\"", "\" \",\n    \"nn\""),
            "a homepage without http" to resourceJson().replace("https://kartverket.github.io", "kartverket.github.io"),
            "an email address without @" to resourceJson().replace("kundesenter@kartverket.no", "kundesenter"),
            "a keyword in another language" to resourceJson().replace("\"language\": \"en\"", "\"language\": \"se\""),
        )
        for ((why, body) in cases) {
            altinnAnswers(body)

            val e = assertFailsWith<AltinnException>(why) { client().getMaskinportenSchema(id) }

            assertContains(e.message!!, "not a valid MaskinportenSchema", message = "for $why")
        }
    }

    @Test
    fun `fails with Altinn's status and answer on any other error`() = runBlocking {
        server.on(path) { TestResponse(status = 500, body = "boom", contentType = "text/plain") }

        val e = assertFailsWith<AltinnException> { client().getMaskinportenSchema(id) }

        assertEquals(500, e.statusCode)
        assertEquals("boom", e.responseBody)
    }

    @Test
    fun `fails on an answer that is not a resource`() = runBlocking {
        altinnAnswers("[]")

        val e = assertFailsWith<AltinnException> { client().getMaskinportenSchema(id) }

        assertContains(e.message!!, "Failed to parse")
    }

    @Test
    fun `fails when Altinn cannot be reached`() = runBlocking {
        val e = assertFailsWith<AltinnException> {
            client { throw IOException("Connection refused") }.getMaskinportenSchema(id)
        }

        assertContains(e.message!!, "Connection refused")
    }
}
