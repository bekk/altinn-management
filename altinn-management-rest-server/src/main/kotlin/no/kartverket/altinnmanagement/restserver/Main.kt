package no.kartverket.altinnmanagement.restserver

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    val config = AppConfig.load()

    embeddedServer(Netty, port = config.port) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    routing {
        get("/health/live") {
            call.respond(HttpStatusCode.OK)
        }
    }
}
