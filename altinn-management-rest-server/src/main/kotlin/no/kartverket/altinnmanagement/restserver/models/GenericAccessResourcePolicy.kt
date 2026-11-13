package no.kartverket.altinnmanagement.restserver.models

import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import kotlinx.serialization.Serializable

// The fields are nullable so a missing one is a validation error that names it, not a body that cannot be read
@Serializable
@JsonSchema.Required("rules")
@JsonSchema.Description(
    "A whole policy for a GenericAccessResource, to replace the one in Altinn. It says who may do what with the " +
        "resource, and how securely they must have logged in.",
)
data class GenericAccessResourcePolicy(
    val minimumAuthenticationLevel: AuthenticationLevels? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.MinItems(1)
    @JsonSchema.Description("At least one. A user may do an action when any rule allows it.")
    val rules: List<PolicyRule>? = null,
)

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
@JsonSchema.Description("Lets the subjects do the actions on the resource, or on the parts in `subResources`.")
data class PolicyRule(
    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("What the rule is for, for people to read.")
    val description: String? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.MinItems(1)
    @JsonSchema.Description(
        "At least one. Who the rule is for, as URNs: an access package like `urn:altinn:accesspackage:eksplisitt`, " +
            "a role like `urn:altinn:rolecode:dagl`, or any other subject Altinn knows.",
    )
    val subjects: List<String>? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.MinItems(1)
    @JsonSchema.Description("At least one. What the subjects may do, such as `read`, `write` or `access`.")
    val actions: List<String>? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.Description(
        "The parts of the resource the rule covers, each as attribute ids and values, like " +
            "`{\"urn:kv:geonorge\": \"dataset\"}`. Left out, the rule covers the whole resource.",
    )
    val subResources: List<Map<String, String>>? = null,
)
