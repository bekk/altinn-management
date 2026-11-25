package no.kartverket.altinnmanagement.client

import kotlinx.coroutines.runBlocking
import no.kartverket.altinnmanagement.client.auth.AccessToken
import no.kartverket.altinnmanagement.client.auth.AccessTokenProvider
import no.kartverket.altinnmanagement.client.exception.AltinnException
import no.kartverket.altinnmanagement.client.exception.AltinnRejectedException
import no.kartverket.altinnmanagement.client.exception.ResourceAlreadyExistsException
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.http.OutgoingResponse
import no.kartverket.altinnmanagement.client.support.recording
import no.kartverket.altinnmanagement.client.support.resourceJson
import no.kartverket.altinnmanagement.client.support.response
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResourceRegistryClientTest {

    private val sent = mutableListOf<OutgoingRequest>()
    private val refused = mutableListOf<AccessToken>()

    private val tokens = object : AccessTokenProvider {
        override suspend fun accessToken() = AccessToken.of("token")

        override suspend fun refused(token: AccessToken) {
            refused += token
        }
    }

    private val id = ResourceId.of("kv-matrikkel-brukernavn")
    private val text = MultilingualText(en = "Text", nb = "Tekst", nn = "Tekst")
    private val newSchema = NewMaskinportenSchema(id, listOf("kartverk:a"), text, text, text)
    private val policy = GenericAccessResourcePolicy(
        rules = listOf(PolicyRule(subjects = listOf(listOf("urn:altinn:accesspackage:eksplisitt")), actions = listOf("access"))),
    )

    private fun json(status: Int, body: String) = response(status, "application/json", body)

    /** Altinn answering a read of the resource with [resource], of its policy with [policy], and any write with [write]. */
    private fun client(
        resource: OutgoingResponse = json(200, resourceJson()),
        policy: OutgoingResponse = response(404, "text/plain", "Unable to find requested policy"),
        write: OutgoingResponse = json(200, ""),
        accessTokenProvider: AccessTokenProvider? = tokens,
    ) = ResourceRegistryClient(
        AltinnEnvironment.TT02.platformBaseUrl,
        recording(sent) { request ->
            when {
                request.method != HttpMethod.GET -> write
                request.url.path.endsWith("/policy") -> policy
                else -> resource
            }
        },
        accessTokenProvider,
    )

    private fun client(httpClient: OutgoingHttpClient) =
        ResourceRegistryClient(AltinnEnvironment.TT02.platformBaseUrl, httpClient, tokens)

    // Every call that acts only on Kartverket's resources of one type, giving null, or false, when there is none
    private val maskinportenSchemaCalls: List<suspend ResourceRegistryClient.(ResourceId) -> Any?> = listOf(
        { getMaskinportenSchema(it) },
        { updateMaskinportenSchema(it) { MaskinportenSchemaContent(listOf("kartverk:a"), text, text, text, null, emptyList(), emptyList()) } },
        { withdrawMaskinportenSchema(it) },
        { setMaskinportenSchemaPolicy(it).takeIf { set -> set } },
    )

    private val genericAccessResourceCalls: List<suspend ResourceRegistryClient.(ResourceId) -> Any?> = listOf(
        { getGenericAccessResource(it) },
        { updateGenericAccessResource(it) { GenericAccessResourceContent(text, text, text, null, emptyList(), emptyList()) } },
        { withdrawGenericAccessResource(it) },
        { setGenericAccessResourcePolicy(it, policy).takeIf { set -> set } },
        { getGenericAccessResourcePolicy(it) },
    )

    private fun assertNothingChanged() = assertTrue(sent.all { it.method == HttpMethod.GET }, "Altinn was changed: $sent")

    @Test
    fun `reads without an AccessTokenProvider`(): Unit = runBlocking {
        assertEquals(id, client(accessTokenProvider = null).getMaskinportenSchema(id)?.resourceId)
    }

    @Test
    fun `refuses to change a resource without an AccessTokenProvider, before asking Altinn`(): Unit = runBlocking {
        val content = MaskinportenSchemaContent(listOf("kartverk:a"), text, text, text, null, emptyList(), emptyList())

        assertFailsWith<IllegalStateException> { client(accessTokenProvider = null).updateMaskinportenSchema(id) { content } }
        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `refuses to create a resource with an id that Altinn uses as a route, before asking Altinn`(): Unit = runBlocking {
        val schema = NewMaskinportenSchema(ResourceId.of("search"), listOf("kartverk:a"), text, text, text)

        assertFailsWith<IllegalArgumentException> { client().createMaskinportenSchema(schema) }
        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `finds nothing, and changes nothing, when Altinn has no resource with the id`(): Unit = runBlocking {
        val client = client(resource = json(404, """{"title":"Not Found","status":404}"""))

        for (call in maskinportenSchemaCalls + genericAccessResourceCalls) {
            assertNull(client.call(id))
        }
        assertNothingChanged()
    }

    @Test
    fun `treats a resource of another type, another organisation's or one without an owner as not there`(): Unit = runBlocking {
        fun notKartverkets(type: String) = listOf(
            resourceJson(type).replace("\"971040238\"", "\"981544315\""),
            """{"identifier":"kv-matrikkel-brukernavn","resourceType":"$type"}""",
        )
        val notThere = maskinportenSchemaCalls.map { it to notKartverkets("MaskinportenSchema") + resourceJson("GenericAccessResource") } +
            genericAccessResourceCalls.map { it to notKartverkets("GenericAccessResource") + resourceJson("MaskinportenSchema") }

        for ((call, resources) in notThere) {
            for (resource in resources) {
                assertNull(client(resource = json(200, resource)).call(id), resource)
            }
        }
        assertNothingChanged()
        assertTrue(sent.none { it.url.path.endsWith("/policy") }, "Asked Altinn for a policy: $sent")
    }

    @Test
    fun `does not ask Altinn about an id that is one of its fixed routes`(): Unit = runBlocking {
        val client = client()

        for (route in listOf("resourcelist", "export", "search", "updated", "changes", "orgs")) {
            for (call in maskinportenSchemaCalls + genericAccessResourceCalls) {
                assertNull(client.call(ResourceId.of(route)), route)
            }
        }
        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives no policy when the resource has none yet`(): Unit = runBlocking {
        assertNull(client(resource = json(200, resourceJson("GenericAccessResource"))).getGenericAccessResourcePolicy(id))
    }

    @Test
    fun `fails when Altinn answers a read with an error or with something else than asked for`(): Unit = runBlocking {
        assertFailsWith<AltinnException> { client(resource = json(500, "boom")).getMaskinportenSchema(id) }
        assertFailsWith<AltinnException> { client(resource = json(200, "[]")).getMaskinportenSchema(id) }
        assertFailsWith<AltinnException> { client(resource = json(500, "boom")).listMaskinportenSchemas() }
        assertFailsWith<AltinnException> { client(resource = json(200, "{}")).listGenericAccessResources() }
        assertFailsWith<AltinnException> { client(resource = json(200, """[{"resourceType":"MaskinportenSchema"}]""")).listMaskinportenSchemas() }
        assertFailsWith<AltinnException> { client(resource = json(200, """[{"identifier":"Not an id"}]""")).listGenericAccessResources() }
        assertFailsWith<AltinnException> {
            client(resource = json(200, resourceJson("GenericAccessResource")), policy = response(500, "text/plain", "boom"))
                .getGenericAccessResourcePolicy(id)
        }
    }

    @Test
    fun `fails when Altinn cannot be reached`(): Unit = runBlocking {
        val client = client(OutgoingHttpClient { throw IOException("Connection refused") })

        assertFailsWith<AltinnException> { client.getMaskinportenSchema(id) }
        assertFailsWith<AltinnException> { client.listMaskinportenSchemas() }
        assertFailsWith<AltinnException> { client.createMaskinportenSchema(newSchema) }
    }

    @Test
    fun `drops a token Altinn refuses, so the next change gets a new one`(): Unit = runBlocking {
        val client = client(write = json(401, ""))

        assertFailsWith<AltinnException> { client.createMaskinportenSchema(newSchema) }
        assertFailsWith<AltinnException> { client.withdrawMaskinportenSchema(id) }
        assertFailsWith<AltinnException> { client.setMaskinportenSchemaPolicy(id) }
        assertEquals(listOf("token", "token", "token"), refused.map { it.value })
    }

    @Test
    fun `keeps the token when Altinn rejects what was sent, or does not let Kartverket change it`(): Unit = runBlocking {
        assertFailsWith<AltinnRejectedException> { client(write = json(400, "\"The resource is not valid\"")).withdrawMaskinportenSchema(id) }
        assertFailsWith<AltinnRejectedException> {
            client(write = json(401, """{"errors":{"InvalidPrefix":["digdir:annet"]}}""")).withdrawMaskinportenSchema(id)
        }
        assertFailsWith<AltinnException> { client(write = json(403, "")).withdrawMaskinportenSchema(id) }
        assertTrue(refused.isEmpty(), "refused: $refused")
    }

    @Test
    fun `gives Altinn's reasons for a rejection, by field, as scopes without Kartverket's prefix, or as a message`(): Unit = runBlocking {
        val rejections = mapOf(
            json(
                400,
                """{"title":"One or more validation errors occurred.","status":400,
                   "errors":{"Title":["Missing title in english en"],"Description":["Missing Description in bokmal nb","Missing Description in nynorsk nn"]}}""",
            ) to listOf("Title: Missing title in english en", "Description: Missing Description in bokmal nb, Missing Description in nynorsk nn"),
            json(401, """{"title":"Unauthorized","status":401,"errors":{"InvalidPrefix":["skatteetaten:demo"]}}""") to
                listOf("InvalidPrefix: skatteetaten:demo"),
            json(400, "\"The resource is not valid\"") to listOf("The resource is not valid"),
        )

        for ((answer, reasons) in rejections) {
            val e = assertFailsWith<AltinnRejectedException> { client(write = answer).createMaskinportenSchema(newSchema) }
            assertEquals(reasons, e.reasons)
        }
    }

    @Test
    fun `tells when Altinn already has a resource with the id`(): Unit = runBlocking {
        val e = assertFailsWith<ResourceAlreadyExistsException> {
            client(write = json(409, "\"The Resource already exist: kv-matrikkel-brukernavn\"")).createMaskinportenSchema(newSchema)
        }
        assertEquals(id, e.resourceId)
    }
}
