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
    private val platformBaseUrl = platformBaseUrl.toString()

    public suspend fun getMaskinportenSchema(id: ResourceId): MaskinportenSchema? {
        if (id.value in FIXED_ROUTES) return null
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

    public suspend fun listMaskinportenSchemas(): List<MaskinportenSchema> =
        search(MASKINPORTEN_SCHEMA) { id, resource -> resource.toMaskinportenSchema(id) }

    public suspend fun listGenericAccessResources(): List<GenericAccessResource> =
        search(GENERIC_ACCESS_RESOURCE) { id, resource -> resource.toGenericAccessResource(id) }

    private suspend fun <T> search(resourceType: String, convert: (ResourceId, AltinnResource) -> T): List<T> {
        val request = OutgoingRequest(
            method = HttpMethod.GET,
            url = Http.url(
                platformBaseUrl,
                "$RESOURCE_PATH/search?organizationId=$KARTVERKET_ORGANIZATION_NUMBER&resourceType=$resourceType",
            ),
            headers = mapOf("Accept" to "application/json"),
        )
        val response = Http.sendExpectingOk(httpClient, request, TARGET, ::AltinnException)
        val body = response.body.decodeToString()
        return try {
            Http.json.decodeFromString(ListSerializer(AltinnResource.serializer()), body).map { resource ->
                val id = requireNotNull(resource.identifier) { "A resource has no identifier" }
                convert(ResourceId.of(id), resource)
            }
        } catch (e: IllegalArgumentException) {
            throw AltinnException("Failed to parse the resources from $TARGET: ${e.message}", response.statusCode, body, e)
        }
    }

    internal companion object {
        const val RESOURCE_PATH = "/resourceregistry/api/v1/resource"

        private val FIXED_ROUTES = setOf("resourcelist", "export", "search", "updated", "changes", "orgs")

        private const val TARGET = "Altinn Resource Registry"

        private const val KARTVERKET_ORGANIZATION_NUMBER = "971040238"
    }
}
