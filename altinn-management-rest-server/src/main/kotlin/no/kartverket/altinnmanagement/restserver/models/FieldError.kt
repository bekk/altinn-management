package no.kartverket.altinnmanagement.restserver.models

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

@Serializable
data class FieldError(
    @JsonSchema.Description("The query parameter, path parameter, header or body field.")
    val field: String,

    @JsonSchema.Description(
        """- `MISSING`: the field is absent or blank.
- `INVALID_FORMAT`: the field is there, but not valid.""",
    )
    val code: ValidationCode,

    @JsonSchema.Description("What is wrong, for people to read. The wording may change.")
    val message: String,
)

enum class ValidationCode {
    MISSING,
    INVALID_FORMAT,
}
