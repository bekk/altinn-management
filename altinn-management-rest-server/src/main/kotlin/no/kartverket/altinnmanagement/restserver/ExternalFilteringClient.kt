package no.kartverket.altinnmanagement.restserver

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import java.io.IOException
import java.net.URI

class ExternalFilteringClient(private val url: URI, private val httpClient: OutgoingHttpClient) {
    suspend fun filterFor(clientCertificate: String): AccessFilter {
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
            val envelope = json.decodeFromString(FilteringResponse.serializer(), response.body.decodeToString())
            envelope.result ?: AccessFilter.NOTHING
        } catch (e: IllegalArgumentException) {
            throw ExternalFilteringException("The filtering service did not answer with a valid filter: ${e.message}", e)
        }
    }
}

class ExternalFilteringException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class FilteringRequest(val input: FilteringInput)

@Serializable
private data class FilteringInput(val headers: Map<String, String>)

@Serializable
private data class FilteringResponse(val result: AccessFilter? = null)
