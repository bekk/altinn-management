package no.kartverket.altinnmanagement.client

import kotlinx.serialization.json.Json
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import java.io.IOException
import java.net.URI
import java.net.URLEncoder

public class ResourceRegistryClient(
    platformBaseUrl: URI,
    private val httpClient: OutgoingHttpClient,
) {
    public constructor(environment: AltinnEnvironment, httpClient: OutgoingHttpClient) :
        this(environment.platformBaseUrl, httpClient)

    private val baseUrl = platformBaseUrl.toString().trimEnd('/')

    /**
     * The newest version of each of [owner]'s MaskinportenSchema resources that points to [scope]. The scope must match
     * a reference exactly, case included.
     */
    public suspend fun maskinportenSchemas(owner: String, scope: String): List<AltinnResource> {
        val query = "organizationId=${encode(owner)}&resourceType=${AltinnResource.MASKINPORTEN_SCHEMA}"
        return search(query).filter { scope in it.scopes }
    }

    private suspend fun search(query: String): List<AltinnResource> {
        val request = OutgoingRequest(
            method = HttpMethod.GET,
            url = URI.create("$baseUrl$SEARCH_PATH?$query"),
            headers = mapOf("Accept" to "application/json"),
        )
        val response = try {
            httpClient.send(request)
        } catch (e: IOException) {
            throw AltinnException("Call to Altinn failed: $e", e)
        }
        if (response.statusCode !in 200..299) {
            throw AltinnException("Altinn answered ${response.statusCode}: ${response.body.decodeToString()}")
        }
        return try {
            json.decodeFromString<List<AltinnResource>>(response.body.decodeToString())
        } catch (e: IllegalArgumentException) {
            throw AltinnException("Altinn answered ${response.statusCode} with something this client cannot read: ${e.message}", e)
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    internal companion object {
        const val SEARCH_PATH = "/resourceregistry/api/v1/resource/search"

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}

public class AltinnException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
