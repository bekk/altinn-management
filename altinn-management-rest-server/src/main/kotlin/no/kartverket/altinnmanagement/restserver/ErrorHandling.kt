package no.kartverket.altinnmanagement.restserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import no.kartverket.altinnmanagement.restserver.models.ErrorResponse
import no.kartverket.altinnmanagement.restserver.models.FieldError
import no.kartverket.altinnmanagement.restserver.models.ProblemType

internal fun StatusPagesConfig.errorResponses() {
    exception<BadRequestException> { call, cause ->
        val causes = generateSequence<Throwable>(cause) { it.cause }.joinToString(" caused by ") { it.javaClass.name }
        call.application.log.warn("Malformed request: $causes")
        call.respondProblem(ProblemType.MALFORMED_REQUEST, HttpStatusCode.BadRequest)
    }
    exception<Throwable> { call, cause ->
        call.application.log.error("Unhandled exception", cause)
        call.respondProblem(ProblemType.INTERNAL_ERROR, HttpStatusCode.InternalServerError)
    }
    status(HttpStatusCode.NotFound) { call, status ->
        call.respondProblem(ProblemType.NOT_FOUND, status, "There is no endpoint at this path")
    }
    status(HttpStatusCode.MethodNotAllowed) { call, status ->
        call.respondProblem(ProblemType.METHOD_NOT_ALLOWED, status, "The endpoint at this path does not accept this method")
    }
}

private suspend fun ApplicationCall.respondProblem(
    type: ProblemType,
    status: HttpStatusCode,
    detail: String? = null,
    errors: List<FieldError>? = null,
) = respondText(
    Json.encodeToString(ErrorResponse.serializer(), ErrorResponse.of(type, status, detail, errors)),
    ContentType.Application.ProblemJson,
    status,
)
