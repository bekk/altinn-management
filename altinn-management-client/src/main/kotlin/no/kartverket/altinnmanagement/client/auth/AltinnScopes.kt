package no.kartverket.altinnmanagement.client.auth

internal object AltinnScopes {
    const val RESOURCE_WRITE = "altinn:resourceregistry/resource.write"

    // Altinn lets this scope read access lists too
    const val ACCESS_LIST_WRITE = "altinn:resourceregistry/accesslist.write"
}
