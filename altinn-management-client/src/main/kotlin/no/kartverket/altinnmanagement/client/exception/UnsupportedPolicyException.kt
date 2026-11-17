package no.kartverket.altinnmanagement.client.exception

/**
 * The policy in Altinn has parts that [no.kartverket.altinnmanagement.client.GenericAccessResourcePolicy] cannot hold.
 * It is not shown without them, since it could then be sent back and replace the one in Altinn.
 */
public class UnsupportedPolicyException internal constructor(
    reason: String,
    cause: Throwable? = null,
) : AltinnManagementException("The policy in Altinn has parts this client cannot hold: $reason", cause = cause)
