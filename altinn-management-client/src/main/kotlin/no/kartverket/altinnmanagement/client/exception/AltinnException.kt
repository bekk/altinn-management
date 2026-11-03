package no.kartverket.altinnmanagement.client.exception

public class AltinnException internal constructor(
    message: String,
    statusCode: Int? = null,
    responseBody: String? = null,
    cause: Throwable? = null,
) : AltinnManagementException(message, statusCode, responseBody, cause)
