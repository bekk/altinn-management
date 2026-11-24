package no.kartverket.altinnmanagement.mock

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json

// Two of the three Kartverket resources in the seed, so a list shows that filtering is on
private val ANSWER = Json.parseToJsonElement(
    """{"result":{"scopes":{"exact":[],"prefix":["kartverk:nrl."]},"resources":{"exact":[],"prefix":["nrl-"]}}}""",
)

/** Plays our own filtering service, not one of Altinn's. */
fun Route.filtering() {
    post("/filtering") { call.respondJson(HttpStatusCode.OK, ANSWER) }
}
