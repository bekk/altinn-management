package no.kartverket.altinnmanagement.client.support

import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.http.OutgoingResponse

public fun response(statusCode: Int, contentType: String, body: String): OutgoingResponse =
    OutgoingResponse(statusCode, contentType, body.encodeToByteArray())

public fun recording(requests: MutableList<OutgoingRequest>, answer: (OutgoingRequest) -> OutgoingResponse): OutgoingHttpClient =
    OutgoingHttpClient { request ->
        requests += request
        answer(request)
    }
