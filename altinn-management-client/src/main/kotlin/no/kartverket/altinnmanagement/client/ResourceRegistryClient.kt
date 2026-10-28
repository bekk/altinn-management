package no.kartverket.altinnmanagement.client

import no.kartverket.altinnmanagement.client.exception.AltinnException
import no.kartverket.altinnmanagement.client.http.Http
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import java.net.URI

public class ResourceRegistryClient(
    platformBaseUrl: URI,
    private val owner: OrganizationNumber,
    private val httpClient: OutgoingHttpClient,
) {
    public constructor(environment: AltinnEnvironment, owner: OrganizationNumber, httpClient: OutgoingHttpClient) :
        this(environment.platformBaseUrl, owner, httpClient)

    private val platformBaseUrl = platformBaseUrl.toString()

    public suspend fun getMaskinportenSchema(id: ResourceId): MaskinportenSchema? {
        // Altinn sends these to its own endpoints, never to a resource with that id
        if (id in FIXED_ROUTES) return null

        val request = OutgoingRequest(
            method = HttpMethod.GET,
            url = Http.url(platformBaseUrl, "$RESOURCE_PATH/${id.value}"),
            headers = mapOf("Accept" to "application/json"),
        )
        val response = try {
            Http.sendExpectingOk(httpClient, request, TARGET, ::AltinnException)
        } catch (e: AltinnException) {
            if (e.statusCode == 404) return null
            throw e
        }
        val body = response.body.decodeToString()
        val resource = try {
            Http.json.decodeFromString(AltinnResource.serializer(), body)
        } catch (e: IllegalArgumentException) {
            throw AltinnException("Failed to parse the resource from $TARGET: ${e.message}", response.statusCode, body, e)
        }
        if (!resource.isMaskinportenSchemaOf(owner)) return null
        return try {
            resource.toMaskinportenSchema()
        } catch (e: IllegalArgumentException) {
            throw AltinnException("The resource from $TARGET is not a valid MaskinportenSchema: ${e.message}", response.statusCode, body, e)
        }
    }

    internal companion object {
        const val RESOURCE_PATH = "/resourceregistry/api/v1/resource"

        val FIXED_ROUTES = listOf("changes", "export", "resourcelist", "search", "updated").map(ResourceId::of).toSet()

        private const val TARGET = "Altinn Resource Registry"
    }
}
