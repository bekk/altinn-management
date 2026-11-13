package no.kartverket.altinnmanagement.client.exception

import no.kartverket.altinnmanagement.client.ResourceId

/** Altinn already has a resource with this id, of any type and owned by any organisation. */
public class ResourceAlreadyExistsException internal constructor(
    public val resourceId: ResourceId,
    statusCode: Int,
    responseBody: String,
) : AltinnManagementException("Altinn Resource Registry already has a resource with the id $resourceId", statusCode, responseBody)
