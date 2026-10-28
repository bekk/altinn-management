package no.kartverket.altinnmanagement.client.filtering

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import java.io.IOException
import java.net.URI

/** Asks the filtering service, OPA in production, which scopes and Altinn resources the calling app may see. */
public class ExternalFilteringClient(private val url: URI, private val httpClient: OutgoingHttpClient) {
    public suspend fun accessFor(clientCertificate: String): Access {
        val requestBody = FilteringRequest(FilteringInput(mapOf(CLIENT_CERTIFICATE_HEADER.lowercase() to clientCertificate)))
        val request = OutgoingRequest(
            method = HttpMethod.POST,
            url = url,
            headers = mapOf("Accept" to "application/json", "Content-Type" to "application/json"),
            body = json.encodeToString(FilteringRequest.serializer(), requestBody).encodeToByteArray(),
        )
        val response = try {
            httpClient.send(request)
        } catch (e: IOException) {
            throw ExternalFilteringException("Call to the filtering service failed: $e", e)
        }
        if (response.statusCode !in 200..299) {
            throw ExternalFilteringException("The filtering service answered ${response.statusCode}")
        }
        return try {
            // OPA leaves out the result when the policy gives none
            json.decodeFromString(FilteringResponse.serializer(), response.body.decodeToString()).result ?: Access()
        } catch (e: IllegalArgumentException) {
            throw ExternalFilteringException("The filtering service did not answer with a valid filter: ${e.message}", e)
        }
    }

    public companion object {
        public const val CLIENT_CERTIFICATE_HEADER: String = "X-Forwarded-Client-Cert"
    }
}

/** What an app may see: the scopes, and the Altinn resources by their id. */
@Serializable
public data class Access(
    public val scopes: Filter = Filter(),
    public val resources: Filter = Filter(),
)

@Serializable
public data class Filter(
    public val exact: List<String> = emptyList(),
    public val prefix: List<String> = emptyList(),
) {
    init {
        require(prefix.none(String::isBlank)) { "A prefix must not be blank" }
    }

    public fun allows(value: String): Boolean = value in exact || prefix.any { value.startsWith(it) }
}

public class ExternalFilteringException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class FilteringRequest(val input: FilteringInput)

@Serializable
private data class FilteringInput(val headers: Map<String, String>)

@Serializable
private data class FilteringResponse(val result: Access? = null)
