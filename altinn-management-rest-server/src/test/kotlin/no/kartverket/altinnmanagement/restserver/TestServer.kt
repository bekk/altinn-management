package no.kartverket.altinnmanagement.restserver

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.kartverket.altinnmanagement.client.ResourceRegistryClient
import no.kartverket.altinnmanagement.client.filtering.ExternalFilteringClient
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.http.OutgoingResponse
import no.kartverket.altinnmanagement.restserver.models.ErrorCode
import no.kartverket.altinnmanagement.restserver.models.ErrorResponse
import no.kartverket.altinnmanagement.restserver.models.FieldError
import java.net.URI

internal const val CLIENT_CERTIFICATE =
    "By=spiffe://cluster.local/ns/a/sa/b;Hash=abc;Subject=\"\";URI=spiffe://cluster.local/ns/team/sa/app"

private val noCalls = OutgoingHttpClient { error("unexpected call to ${it.url}") }

/** [filtering] null turns filtering off. */
internal fun testServer(
    altinn: OutgoingHttpClient = noCalls,
    filtering: OutgoingHttpClient? = null,
    block: suspend ApplicationTestBuilder.() -> Unit,
) = testApplication {
    application { module(testRegistry(altinn), testFiltering(filtering)) }
    block()
}

internal fun testRegistry(altinn: OutgoingHttpClient = noCalls) = ResourceRegistryClient(URI("https://altinn.test"), altinn)

internal fun testFiltering(filtering: OutgoingHttpClient? = null) =
    ExternalFiltering(filtering?.let { ExternalFilteringClient(URI("https://filtering.test/filtering"), it) })

internal fun json(status: Int, body: String) = OutgoingResponse(status, "application/json", body.toByteArray())

internal fun recording(requests: MutableList<OutgoingRequest>, answer: (OutgoingRequest) -> OutgoingResponse) =
    OutgoingHttpClient { request ->
        requests += request
        answer(request)
    }

internal suspend fun HttpResponse.errorResponse(): ErrorResponse =
    Json.decodeFromString(ErrorResponse.serializer(), bodyAsText())

internal fun validationFailed(vararg errors: FieldError) =
    ErrorResponse.of(HttpStatusCode.BadRequest, "Validation failed", ErrorCode.VALIDATION_ERROR, errors.toList())

internal fun upstreamError(detail: String) = ErrorResponse.of(HttpStatusCode.BadGateway, detail, ErrorCode.UPSTREAM_ERROR)

@Serializable
private data class FilteringInput(val headers: Map<String, String>)

@Serializable
private data class FilteringRequestBody(val input: FilteringInput)

// Reads the certificate the app relayed to the filtering service, out of the request body it sent it in
internal fun OutgoingRequest.certificateSentToFiltering(): String =
    Json.decodeFromString(FilteringRequestBody.serializer(), body!!.decodeToString()).input.headers.getValue("x-forwarded-client-cert")
