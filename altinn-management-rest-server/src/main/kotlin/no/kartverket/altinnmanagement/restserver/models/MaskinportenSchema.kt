package no.kartverket.altinnmanagement.restserver.models

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable
import no.kartverket.altinnmanagement.client.AltinnResource

@Serializable
data class MaskinportenSchema(
    @JsonSchema.Description("The resource's id in Altinn, for example `kv-matrikkel-brukernavn`.")
    val resourceId: String,

    @JsonSchema.Description("The Maskinporten scopes the resource gives access to.")
    val scopes: List<String>,

    @JsonSchema.Description("The name, by language: `en`, `nb` and `nn`.")
    val title: Map<String, String>,

    @JsonSchema.Description("What the resource is, by language: `en`, `nb` and `nn`.")
    val description: Map<String, String>,

    @JsonSchema.Description(
        "What a recipient may do when the resource is delegated to them, by language: `en`, `nb` and `nn`. Altinn " +
            "calls it `rightDescription`.",
    )
    val delegationDescription: Map<String, String>,

    @JsonSchema.Description("A page about the resource, when it has one.")
    val homepage: String? = null,

    @JsonSchema.Description("Who to contact about the resource.")
    val contactPoints: List<ContactPoint>,

    @JsonSchema.Description("Words to find the resource by in Altinn.")
    val keywords: List<Keyword>,

    @JsonSchema.Description(
        "The resource's status in Altinn, for example `Completed`, `Active` or `Withdrawn`, when it has one.",
    )
    val status: String? = null,

    @JsonSchema.Description("Whether organisations can delegate access to the resource. A withdrawn resource cannot.")
    val delegable: Boolean,

    @JsonSchema.Description("Whether Altinn shows the resource. A withdrawn resource is hidden.")
    val visible: Boolean,
) {
    companion object {
        fun from(resource: AltinnResource) = MaskinportenSchema(
            resourceId = resource.identifier,
            scopes = resource.scopes,
            title = resource.title,
            description = resource.description,
            delegationDescription = resource.rightDescription,
            homepage = resource.homepage,
            contactPoints = resource.contactPoints.map {
                ContactPoint(it.category, it.email, it.telephone, it.contactPage)
            },
            keywords = resource.keywords.map { Keyword(it.word, it.language) },
            status = resource.status,
            delegable = resource.delegable,
            visible = resource.visible,
        )
    }
}

@Serializable
data class ContactPoint(
    @JsonSchema.Description("What the contact point is, for example `Kundesenter`.")
    val category: String? = null,
    val email: String? = null,
    val telephone: String? = null,
    val contactPage: String? = null,
)

@Serializable
data class Keyword(
    val word: String? = null,
    @JsonSchema.Description("The keyword's language, for example `nb`.")
    val language: String? = null,
)
