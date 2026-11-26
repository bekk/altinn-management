package no.kartverket.altinnmanagement.restserver.genericaccessresource

import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import kotlinx.serialization.Serializable
import no.kartverket.altinnmanagement.client.GenericAccessResourcePolicy as ClientGenericAccessResourcePolicy

// The fields are nullable so a missing one is a validation error that names it, not a body that cannot be read
@Serializable
@JsonSchema.Required("rules")
@JsonSchema.Description(
    "A whole policy for a GenericAccessResource. It says who may do what with the resource, and how securely they " +
        "must have logged in.",
)
data class GenericAccessResourcePolicy(
    val minimumAuthenticationLevel: AuthenticationLevels? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.MinItems(1)
    @JsonSchema.Description("At least one. A user may do an action when a rule permits it and no rule denies it.")
    val rules: List<PolicyRule>? = null,
) {
    companion object {
        fun from(policy: ClientGenericAccessResourcePolicy) = GenericAccessResourcePolicy(
            minimumAuthenticationLevel = policy.minimumAuthenticationLevel.run { AuthenticationLevels(endUser, organisation, systemUser) },
            rules = policy.rules.map { PolicyRule(it.description, it.subjects, it.actions, it.subResources.ifEmpty { null }, it.effect.value) },
        )
    }
}

@Serializable
@JsonSchema.Description(
    "The lowest security level a user must have logged in with, for each kind of user, such as `3` or `4`. A level " +
        "that is left out is not required.",
)
data class AuthenticationLevels(
    @JsonSchema.Type(JsonType.INTEGER)
    @JsonSchema.Description("For a person.")
    val endUser: Int? = null,

    @JsonSchema.Type(JsonType.INTEGER)
    @JsonSchema.Description("For an organisation, such as one using Maskinporten.")
    val organisation: Int? = null,

    @JsonSchema.Type(JsonType.INTEGER)
    @JsonSchema.Description("For a system user.")
    val systemUser: Int? = null,
)

@Serializable
@JsonSchema.Required("subjects", "actions")
@JsonSchema.Description(
    "Lets the subjects do the actions on the resource, or on the parts in `subResources`, or with `effect` `Deny` stops them.",
)
data class PolicyRule(
    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("What the rule is for, for people to read.")
    val description: String? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.MinItems(1)
    @JsonSchema.Description(
        "At least one group. Who the rule is for, as groups of URNs: an access package like " +
            "`urn:altinn:accesspackage:eksplisitt`, a role like `urn:altinn:rolecode:dagl`, an access list as " +
            "`urn:altinn:access-list:{owner}:{list}` (a PUT takes only Kartverket's, `kv`), or any other subject Altinn " +
            "knows. A user the rule is for has every subject in one of the groups, so `[[\"A\", \"B\"], [\"C\"]]` is for " +
            "users with both A and B, and for users with C.",
    )
    val subjects: List<List<String>>? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.MinItems(1)
    @JsonSchema.Description("At least one. What the subjects may do, such as `read`, `write` or `access`.")
    val actions: List<String>? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.Description(
        "The parts of the resource the rule covers, each as attribute ids and values, like " +
            "`{\"urn:kv:geonorge\": \"dataset\"}`, but not `urn:altinn:resource`, which the rule has already. Left out, " +
            "the rule covers the whole resource.",
    )
    val subResources: List<Map<String, String>>? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Enum("Permit", "Deny")
    @JsonSchema.Description(
        "`Permit` lets the subjects do the actions, and `Deny` stops them, even when another rule permits it. Left out, " +
            "the rule permits.",
    )
    val effect: String? = null,
)
