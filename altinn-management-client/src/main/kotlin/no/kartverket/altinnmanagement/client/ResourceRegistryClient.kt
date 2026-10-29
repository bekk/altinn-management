package no.kartverket.altinnmanagement.client

import no.kartverket.altinnmanagement.client.exception.AltinnException
import no.kartverket.altinnmanagement.client.http.Http
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import java.net.URI

public class ResourceRegistryClient(
    platformBaseUrl: URI,
    private val httpClient: OutgoingHttpClient,
) {
    private val platformBaseUrl = platformBaseUrl.toString()

    public suspend fun getMaskinportenSchema(id: ResourceId): MaskinportenSchema? {
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
        return if (resource.isMaskinportenSchema) resource.toMaskinportenSchema(id) else null
    }

    internal companion object {
        const val RESOURCE_PATH = "/resourceregistry/api/v1/resource"

        private const val TARGET = "Altinn Resource Registry"
    }
}
