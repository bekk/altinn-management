package no.kartverket.altinnmanagement.client.exception

/** Kartverket already has an access list with this id in Altinn. */
public class AccessListAlreadyExistsException internal constructor(
    public val accessListId: String,
    statusCode: Int,
    responseBody: String,
) : AltinnManagementException("Altinn Resource Registry already has an access list with the id $accessListId", statusCode, responseBody)
