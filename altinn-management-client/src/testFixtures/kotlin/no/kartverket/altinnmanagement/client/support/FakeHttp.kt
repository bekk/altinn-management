package no.kartverket.altinnmanagement.client.support

import no.kartverket.altinnmanagement.client.http.OutgoingResponse

public fun response(statusCode: Int, contentType: String, body: String): OutgoingResponse =
    OutgoingResponse(statusCode, contentType, body.encodeToByteArray())
