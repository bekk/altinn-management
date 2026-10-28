package no.kartverket.altinnmanagement.mock

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** A field Altinn could not read a resource's JSON into, by its JSON path, like `$.resourceType`. */
class JsonFieldException(val path: String, message: String) : RuntimeException(message)

/**
 * What Altinn keeps of a resource it is sent: it reads the JSON into its ServiceResource class and writes that back. So
 * field names may have any case, unknown fields and nulls are dropped, enums become their names, and an omitted field
 * gets its default. A value of the wrong type, or null where Altinn's class has a value type, cannot be read.
 */
internal object ServiceResource {
    // ServiceResource's fields, in Altinn's order. versionId, createdAt and updatedAt are Altinn's to set
    private val FIELDS = listOf(
        "identifier", "version", "title", "description", "rightDescription", "homepage", "status", "spatial",
        "contactPoints", "produces", "isPartOf", "thematicAreas", "resourceReferences", "delegable", "visible",
        "hasCompetentAuthority", "keywords", "accessListMode", "selfIdentifiedUserEnabled", "enterpriseUserEnabled",
        "resourceType", "availableForType", "authorizationReference", "consentTemplate", "consentText", "consentMetadata",
        "isOneTimeConsent",
    )

    // The value types, which are also the only fields that cannot be null
    private val DEFAULTS = mapOf(
        "delegable" to JsonPrimitive(true),
        "visible" to JsonPrimitive(true),
        "accessListMode" to JsonPrimitive("Disabled"),
        "selfIdentifiedUserEnabled" to JsonPrimitive(false),
        "enterpriseUserEnabled" to JsonPrimitive(false),
        "resourceType" to JsonPrimitive("Default"),
        "isOneTimeConsent" to JsonPrimitive(false),
    )

    private val REFERENCE_TYPES = listOf(
        "Default", "Uri", "DelegationSchemeId", "MaskinportenScope", "ServiceCode", "ServiceEditionCode", "ApplicationId",
        "ServiceEditionVersion",
    ).mapIndexed { index, name -> name to index }
    private val REFERENCE_SOURCES = listOf("Default", "Altinn1", "Altinn2", "Altinn3", "ExternalPlatform").mapIndexed { index, name -> name to index }
    private val ACCESS_LIST_MODES = listOf("Disabled" to 0, "Enabled" to 1)
    private val PARTY_TYPES = listOf("PrivatePerson", "LegalEntityEnterprise", "Company", "BankruptcyEstate", "SelfRegisteredUser")
        .mapIndexed { index, name -> name to index }

    /** The resource as Altinn would keep it. Throws [JsonFieldException] where Altinn could not read it. */
    fun read(body: JsonObject): JsonObject {
        val fields = body.canonical(FIELDS)
        return buildJsonObject {
            for (field in FIELDS) {
                val value = fields[field] ?: DEFAULTS[field] ?: continue
                if (value == JsonNull && field !in DEFAULTS) continue
                put(field, field(field, value, "$.$field"))
            }
        }
    }

    private fun field(field: String, value: JsonElement, path: String): JsonElement = when (field) {
        "resourceType" -> enum(value, RESOURCE_TYPES, "ResourceType", path)

        "accessListMode" -> enum(value, ACCESS_LIST_MODES, "ResourceAccessListMode", path)

        "delegable", "visible", "selfIdentifiedUserEnabled", "enterpriseUserEnabled", "isOneTimeConsent" -> boolean(value, path)

        "title", "description", "rightDescription", "consentText" -> texts(value, path)

        "spatial", "produces", "thematicAreas" -> list(value, path, ::string)

        "contactPoints" -> list(value, path) { item, itemPath ->
            record(item, "ContactPoint", itemPath, listOf("category", "email", "telephone", "contactPage")) { _, text, textPath -> string(text, textPath) }
        }

        "keywords" -> list(value, path) { item, itemPath ->
            record(item, "Keyword", itemPath, listOf("word", "language")) { _, text, textPath -> string(text, textPath) }
        }

        "authorizationReference" -> list(value, path) { item, itemPath ->
            record(item, "AuthorizationReferenceAttribute", itemPath, listOf("id", "value")) { _, text, textPath -> string(text, textPath) }
        }

        "resourceReferences" -> list(value, path) { item, itemPath ->
            record(item, "ResourceReference", itemPath, listOf("referenceSource", "reference", "referenceType")) { name, part, partPath ->
                when (name) {
                    "referenceSource" -> enum(part, REFERENCE_SOURCES, "ReferenceSource", partPath)
                    "referenceType" -> enum(part, REFERENCE_TYPES, "ReferenceType", partPath)
                    else -> string(part, partPath)
                }
            }
        }

        "availableForType" -> list(value, path) { item, itemPath -> enum(item, PARTY_TYPES, "ResourcePartyType", itemPath) }

        "hasCompetentAuthority" -> record(value, "CompetentAuthority", path, listOf("organization", "orgcode", "name")) { name, part, partPath ->
            if (name == "name") texts(part, partPath) else string(part, partPath)
        }

        "consentMetadata" -> JsonObject(
            (value as? JsonObject ?: throw notReadable("Dictionary`2", path)).mapValues { (key, metadata) ->
                val metadataPath = "$path.$key"
                if (metadata == JsonNull) return@mapValues metadata
                val optional = (metadata as? JsonObject ?: throw notReadable("ConsentMetadata", metadataPath)).canonical(listOf("optional"))["optional"]
                JsonObject(mapOf("optional" to (optional?.let { boolean(it, "$metadataPath.optional") } ?: JsonPrimitive(false))))
            },
        )

        else -> string(value, path)
    }

    // A class in a list, or as a field. Its properties are all nullable, so nulls are dropped like unknown names
    private fun record(
        value: JsonElement,
        type: String,
        path: String,
        names: List<String>,
        read: (name: String, value: JsonElement, path: String) -> JsonElement,
    ): JsonElement {
        if (value == JsonNull) return value
        val fields = (value as? JsonObject ?: throw notReadable(type, path)).canonical(names)
        return JsonObject(fields.filterValues { it != JsonNull }.mapValues { (name, part) -> read(name, part, "$path.$name") })
    }

    private fun list(value: JsonElement, path: String, item: (JsonElement, String) -> JsonElement): JsonArray {
        val items = value as? JsonArray ?: throw notReadable("List`1", path)
        return JsonArray(items.mapIndexed { index, element -> item(element, "$path[$index]") })
    }

    private fun texts(value: JsonElement, path: String): JsonObject {
        val texts = value as? JsonObject ?: throw notReadable("Dictionary`2", path)
        return JsonObject(texts.mapValues { (key, text) -> string(text, "$path.$key") })
    }

    private fun string(value: JsonElement, path: String): JsonElement =
        if (value == JsonNull || (value is JsonPrimitive && value.isString)) value else throw notReadable("String", path)

    // Altinn takes an enum as its name, in any case, or as its number
    private fun enum(value: JsonElement, values: List<Pair<String, Int>>, type: String, path: String): JsonPrimitive {
        val primitive = value as? JsonPrimitive ?: throw notReadable(type, path)
        val name = if (primitive.isString) {
            values.find { it.first.equals(primitive.content, ignoreCase = true) }?.first
        } else {
            values.find { it.second == primitive.intOrNull }?.first
        }
        return JsonPrimitive(name ?: throw notReadable(type, path))
    }

    private fun boolean(value: JsonElement, path: String): JsonPrimitive {
        val primitive = value as? JsonPrimitive
        return if (primitive != null && !primitive.isString && primitive.booleanOrNull != null) primitive else throw notReadable("Boolean", path)
    }

    private fun notReadable(type: String, path: String) =
        JsonFieldException(path, "The JSON value could not be converted to $type. Path: $path")

    // Names in any case become Altinn's, and unknown names are dropped
    private fun JsonObject.canonical(names: List<String>): Map<String, JsonElement> = entries
        .mapNotNull { (key, value) -> names.find { it.equals(key, ignoreCase = true) }?.let { it to value } }
        .toMap()

    /**
     * Altinn's checks of a resource, in Altinn's order. The keys and messages are Altinn's. Only a new resource's
     * identifier must match Altinn's pattern.
     */
    fun validate(resource: JsonObject, isNew: Boolean, orgCode: String?): Map<String, List<String>> {
        val messages = linkedMapOf<String, MutableList<String>>()
        fun invalid(key: String, message: String) = messages.getOrPut(key) { mutableListOf() }.add(message)

        val identifier = resource.text("identifier")!!
        val type = resource.text("resourceType")
        val references = (resource["resourceReferences"] as? JsonArray).orEmpty().map { it as JsonObject }
        val referenceTypes = references.mapNotNull { it.text("referenceType") }
        if (identifier.startsWith("se_") && ("ServiceCode" !in referenceTypes || "ServiceEditionCode" !in referenceTypes)) {
            invalid("Identifier", "Invalid prefix. Only to be used for Altinn 2 services")
        }
        if (identifier.startsWith("app_") && type != "AltinnApp" && type != "MigratedApp") {
            invalid("Identifier", "Invalid Prefix. app_ is only for Altinn Studio apps")
        }
        if (type == "Default") invalid("ResourceType", "ResourceType needs to be set")
        if (!hasValidOwner(resource["hasCompetentAuthority"] as? JsonObject, orgCode)) {
            invalid("HasCompetentAuthority.Organization", "HasCompetentAuthority needs to be set with valid organization number and orgcode")
        }
        if (isNew && !IDENTIFIER.matches(identifier)) {
            invalid("Identifier", "Invalid id. Only a-z and 0-9 is allowed together with _ and -.  Minimum 4 characters")
        }
        for ((language, name) in LANGUAGES) {
            if (!resource.hasLanguage("title", language)) invalid("Title", "Missing title in $name $language")
        }
        if ((resource["delegable"] as JsonPrimitive).booleanOrNull == true) {
            for ((language, name) in LANGUAGES) {
                if (!resource.hasLanguage("rightDescription", language)) invalid("RightDescription", "Missing RightDescription in $name $language")
            }
        }
        for ((language, name) in LANGUAGES) {
            if (!resource.hasLanguage("description", language)) invalid("Description", "Missing Description in $name $language")
        }
        if (type == "MaskinportenSchema" && "MaskinportenScope" !in referenceTypes) {
            invalid("ResourceReferences", "Missing maskinporten scopen for MaskinportenSchema resource")
        }
        return messages
    }

    private val IDENTIFIER = Regex("^[a-z0-9_-]{4,}$")
    private val LANGUAGES = listOf("en" to "english", "nb" to "bokmal", "nn" to "nynorsk")

    private fun hasValidOwner(authority: JsonObject?, orgCode: String?): Boolean {
        authority ?: return false
        val code = authority.text("orgcode")
        if (code.equals("ttd", ignoreCase = true)) return true
        if (authority.text("organization").isNullOrBlank() || code.isNullOrBlank()) return false
        return code.equals(orgCode, ignoreCase = true)
    }

    private fun JsonObject.hasLanguage(field: String, language: String) =
        !((this[field] as? JsonObject)?.get(language) as? JsonPrimitive)?.contentOrNull.isNullOrBlank()

    private fun JsonObject.text(field: String) = (this[field] as? JsonPrimitive)?.contentOrNull
}

/**
 * Altinn's register of organisations, which it looks up a resource owner's org code in. The mock knows the owners of its
 * seed resources.
 */
internal object Organizations {
    private val ORG_CODES = mapOf(KARTVERKET to "kv", "981544315" to "slf")

    /** The org code of [organization], or null when it is blank or unknown. Throws when it is not 9 digits, like Altinn. */
    fun orgCode(organization: String?): String? {
        if (organization.isNullOrEmpty()) return null
        check(organization.length == 9 && organization.all(Char::isDigit)) { "Not an organisation number: $organization" }
        return ORG_CODES[organization]
    }
}
