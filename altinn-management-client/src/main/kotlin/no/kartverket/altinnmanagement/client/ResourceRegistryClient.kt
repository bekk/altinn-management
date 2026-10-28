package no.kartverket.altinnmanagement.client

import kotlinx.serialization.builtins.ListSerializer
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
    public constructor(environment: AltinnEnvironment, httpClient: OutgoingHttpClient) :
        this(environment.platformBaseUrl, httpClient)

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

    public suspend fun listMaskinportenSchemas(): List<MaskinportenSchema> {
        val request = OutgoingRequest(
            method = HttpMethod.GET,
            url = Http.url(
                platformBaseUrl,
                "$RESOURCE_PATH/search?organizationId=$KARTVERKET_ORGANIZATION_NUMBER&resourceType=$MASKINPORTEN_SCHEMA",
            ),
            headers = mapOf("Accept" to "application/json"),
        )
        val response = Http.sendExpectingOk(httpClient, request, TARGET, ::AltinnException)
        val body = response.body.decodeToString()
        return try {
            Http.json.decodeFromString(ListSerializer(AltinnResource.serializer()), body).map { resource ->
                val id = requireNotNull(resource.identifier) { "A resource has no identifier" }
                resource.toMaskinportenSchema(ResourceId.of(id))
            }
        } catch (e: IllegalArgumentException) {
            throw AltinnException("Failed to parse the resources from $TARGET: ${e.message}", response.statusCode, body, e)
        }
    }

    internal companion object {
        const val RESOURCE_PATH = "/resourceregistry/api/v1/resource"

        private const val TARGET = "Altinn Resource Registry"

        private const val KARTVERKET_ORGANIZATION_NUMBER = "971040238"
    }
}
