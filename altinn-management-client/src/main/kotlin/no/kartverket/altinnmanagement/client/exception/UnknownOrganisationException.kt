package no.kartverket.altinnmanagement.client.exception

/** Altinn does not know one or more of the organisation numbers it was sent, and does not say which. */
public class UnknownOrganisationException internal constructor(
    statusCode: Int?,
    responseBody: String?,
) : AltinnManagementException("Altinn Resource Registry does not know one or more of the organisations", statusCode, responseBody)
