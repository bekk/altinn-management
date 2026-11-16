package no.kartverket.altinnmanagement.client

@JvmInline
public value class ResourceId private constructor(public val value: String) {
    public val isFixedRoute: Boolean get() = value in FIXED_ROUTES

    override fun toString(): String = value

    public companion object {
        private val IDENTIFIER = Regex("[a-z0-9_-]{4,}")
        private val FIXED_ROUTES = setOf("resourcelist", "export", "search", "updated", "changes", "orgs")

        // Altinn's rule for an identifier. Altinn gets it in the path, so it also keeps ".." and "/" out of the URL
        public fun of(value: String): ResourceId {
            require(IDENTIFIER.matches(value)) {
                "Expected an identifier of 4 or more of a-z, 0-9, _ and -, but was \"$value\""
            }
            return ResourceId(value)
        }
    }
}
