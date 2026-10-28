package no.kartverket.altinnmanagement.client

import kotlinx.coroutines.runBlocking
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.http.OutgoingResponse
import java.io.IOException
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResourceRegistryClientTest {

    private val requests = mutableListOf<OutgoingRequest>()

    private fun client(status: Int = 200, body: String) = ResourceRegistryClient(
        URI("https://altinn.test/"),
        OutgoingHttpClient { request ->
            requests += request
            OutgoingResponse(status, "application/json", body.toByteArray())
        },
    )

    private fun reference(reference: String, type: String = "MaskinportenScope") =
        """{"referenceSource":"Default","reference":"$reference","referenceType":"$type"}"""

    private fun resource(id: String, vararg references: String) =
        """{"identifier":"$id","resourceType":"MaskinportenSchema","delegable":true,"visible":true,
        "resourceReferences":[${references.joinToString(",")}]}"""

    private suspend fun ids(body: String, scope: String) =
        client(body = body).maskinportenSchemas("971040238", scope).map { it.identifier }

    @Test
    fun `searches Altinn for the owner's MaskinportenSchema resources, without the reference filter`() = runBlocking<Unit> {
        ids("[]", "kartverk:a")

        assertEquals(
            "https://altinn.test/resourceregistry/api/v1/resource/search?organizationId=971040238&resourceType=MaskinportenSchema",
            requests.single().url.toString(),
        )
    }

    @Test
    fun `keeps the resources with a Maskinporten scope that matches exactly`() = runBlocking<Unit> {
        val body = listOf(
            resource("kv-exact", reference("kartverk:a")),
            resource("kv-longer", reference("kartverk:a.read")),
            resource("kv-case", reference("KARTVERK:A")),
            resource("kv-other-type", reference("kartverk:a", type = "ServiceCode")),
            resource("kv-second", reference("kartverk:b"), reference("kartverk:a")),
        ).joinToString(",", "[", "]")

        assertEquals(listOf("kv-exact", "kv-second"), ids(body, "kartverk:a"))
    }

    @Test
    fun `reads the fields it knows, with null lists as empty, and ignores the rest`() = runBlocking<Unit> {
        val body = """[{"identifier":"kv-a","title":{"nb":"A"},"status":"Withdrawn","keywords":null,"delegable":false,
            "visible":false,"homepage":"https://kartverket.no","newField":1,"resourceReferences":[${reference("kartverk:a")}]}]"""

        val resource = client(body = body).maskinportenSchemas("971040238", "kartverk:a").single()

        assertEquals(mapOf("nb" to "A"), resource.title)
        assertEquals("Withdrawn", resource.status)
        assertEquals(emptyList(), resource.keywords)
        assertEquals(false, resource.delegable)
        assertEquals(listOf("kartverk:a"), resource.scopes)
    }

    @Test
    fun `an error from Altinn, an answer it cannot read or a failed call is an AltinnException`() = runBlocking<Unit> {
        assertFailsWith<AltinnException> { client(status = 500, body = "{}").maskinportenSchemas("971040238", "kartverk:a") }
        assertFailsWith<AltinnException> { client(body = "{}").maskinportenSchemas("971040238", "kartverk:a") }
        val failing = ResourceRegistryClient(URI("https://altinn.test"), OutgoingHttpClient { throw IOException("refused") })
        assertFailsWith<AltinnException> { failing.maskinportenSchemas("971040238", "kartverk:a") }
    }
}
