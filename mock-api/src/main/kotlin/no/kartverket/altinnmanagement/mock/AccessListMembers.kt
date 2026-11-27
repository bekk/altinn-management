package no.kartverket.altinnmanagement.mock

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.TreeMap
import java.util.UUID

/** An organisation Altinn's Register knows, which can be a member of an access list. */
data class Party(val uuid: String, val id: Long, val organisationNumber: String)

/** The members of each access list, by party uuid, which is the order Altinn pages them in, and the parties Register knows. */
class AccessListMembers(private val parties: List<Party>) {
    private val members = mutableMapOf<String, TreeMap<String, String>>()

    /** The party the URN names, or null when it names none Register knows. */
    fun party(urn: PartyUrn): Party? = when (urn) {
        is PartyUrn.Uuid -> parties.find { it.uuid == urn.value }
        is PartyUrn.Id -> parties.find { it.id == urn.value }
        is PartyUrn.OrganisationNumber -> parties.find { it.organisationNumber == urn.value }
    }

    /** Up to [PAGE_SIZE] members of the list [id] from the party uuid [from] on, [from] included, and the uuid the next page starts at. */
    fun page(id: String, from: String?): Pair<List<JsonObject>, String?> {
        val all = members[id] ?: TreeMap()
        val rest = (if (from == null) all else all.tailMap(from, true)).entries.take(PAGE_SIZE + 1)
        return rest.take(PAGE_SIZE).map { (uuid, since) -> member(parties.first { it.uuid == uuid }, since) } to rest.getOrNull(PAGE_SIZE)?.key
    }

    /** Adds the parties to the list [id], keeping when those already on it were added. */
    fun add(id: String, added: Collection<Party>) {
        val now = OffsetDateTime.now(ZoneOffset.UTC).format(SINCE)
        val list = members.getOrPut(id) { TreeMap() }
        added.forEach { list.putIfAbsent(it.uuid, now) }
    }

    fun remove(id: String, removed: Collection<Party>) {
        members[id]?.keys?.removeAll(removed.map { it.uuid }.toSet())
    }

    // Altinn gives each identifier as a key of its own
    private fun member(party: Party, since: String) = buildJsonObject {
        put("id", "urn:altinn:party:uuid:${party.uuid}")
        put("since", since)
        putJsonObject("identifiers") {
            put("urn:altinn:party:uuid", party.uuid)
            put("urn:altinn:party:id", party.id)
            put("urn:altinn:organization:identifier-no", party.organisationNumber)
        }
    }

    companion object {
        const val PAGE_SIZE = 100

        private val SINCE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx")

        /** Kartverket, Digdir and a synthetic organisation from the test Enhetsregister, with made-up party ids and uuids. */
        fun seeded() = AccessListMembers(
            listOf("971040238", "991825827", "311718371").mapIndexed { index, number ->
                Party(UUID.nameUUIDFromBytes(number.toByteArray()).toString(), 50_000_001L + index, number)
            },
        )
    }
}

/** One of the three ways Altinn takes a member: `urn:altinn:party:uuid:`, `urn:altinn:party:id:` or an organisation number. */
sealed interface PartyUrn {
    data class Uuid(val value: String) : PartyUrn

    data class Id(val value: Long) : PartyUrn

    data class OrganisationNumber(val value: String) : PartyUrn

    companion object {
        /** The URN, or null when Altinn could not read it. Altinn takes an organisation number with 9 digits and no check digit. */
        fun parse(urn: String): PartyUrn? = when {
            urn.startsWith(UUID_PREFIX) ->
                runCatching { UUID.fromString(urn.removePrefix(UUID_PREFIX)).toString() }.getOrNull()?.let(::Uuid)

            urn.startsWith(ID_PREFIX) -> urn.removePrefix(ID_PREFIX).toLongOrNull()?.takeIf { it > 0 }?.let(::Id)

            urn.startsWith(ORGANISATION_PREFIX) ->
                urn.removePrefix(ORGANISATION_PREFIX).takeIf { n -> n.length == 9 && n.all { it in '0'..'9' } }?.let(::OrganisationNumber)

            else -> null
        }

        private const val UUID_PREFIX = "urn:altinn:party:uuid:"
        private const val ID_PREFIX = "urn:altinn:party:id:"
        private const val ORGANISATION_PREFIX = "urn:altinn:organization:identifier-no:"
    }
}
