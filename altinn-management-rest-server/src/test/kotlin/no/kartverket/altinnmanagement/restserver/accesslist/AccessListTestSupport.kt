package no.kartverket.altinnmanagement.restserver.accesslist

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.support.response
import no.kartverket.altinnmanagement.restserver.problem.ErrorResponse
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ProblemType
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode

internal const val ALTINN_LISTS = "https://platform.tt02.altinn.no/resourceregistry/api/v1/access-lists/kv"

internal fun altinnList(id: String, name: String = "Kunder", description: String = "") =
    """{"urn":"urn:altinn:access-list:kv:$id","identifier":"$id","name":"$name","description":"$description","createdAt":"2026-11-20T10:15:30.123456+00:00","updatedAt":"2026-11-21T08:00:00+00:00"}"""

internal fun accessList(id: String, name: String = "Kunder", description: String = "") = AccessList(
    accessListId = id,
    urn = "urn:altinn:access-list:kv:$id",
    name = name,
    description = description,
    createdAt = "2026-11-20T10:15:30.123456+00:00",
    updatedAt = "2026-11-21T08:00:00+00:00",
)

/** Altinn that answers every call with [status] and [body], and keeps what it was sent in [sent]. */
internal fun altinnAnswering(sent: MutableList<OutgoingRequest>, status: Int, body: String = "") = OutgoingHttpClient { request ->
    sent += request
    response(status, "application/json", body)
}

internal suspend fun HttpResponse.accessList(): AccessList = Json.decodeFromString(AccessList.serializer(), bodyAsText())

internal val accessListNotFound = ErrorResponse.of(ProblemType.NOT_FOUND, HttpStatusCode.NotFound, "There is no access list with this id")

internal fun invalidId(field: String) = ErrorResponse.of(
    ProblemType.VALIDATION_ERROR,
    HttpStatusCode.BadRequest,
    errors = listOf(FieldError(field, ValidationCode.INVALID_FORMAT, "$field must be 4 or more of a-z, 0-9, _ and -")),
)
