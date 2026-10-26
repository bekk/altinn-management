package no.kartverket.altinnmanagement.restserver

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.yaml.YamlConfig
import no.kartverket.altinnmanagement.client.AltinnEnvironment

internal class AppConfig(
    val port: Int,
    val altinnEnvironment: AltinnEnvironment,
) {
    companion object {
        fun load(config: ApplicationConfig = loadApplicationConfig()): AppConfig = AppConfig(
            port = config.port("server.port"),
            altinnEnvironment = config.enum("altinn.environment"),
        )
    }
}

internal fun loadApplicationConfig(): ApplicationConfig =
    YamlConfig("application.yaml") ?: error("application.yaml is not on the classpath")

internal fun ApplicationConfig.optional(path: String): String? =
    propertyOrNull(path)?.getString()?.trim()?.takeIf { it.isNotEmpty() }

internal fun ApplicationConfig.required(path: String): String =
    optional(path) ?: error("Missing required configuration $path (see .env.example)")

internal inline fun <reified T : Enum<T>> ApplicationConfig.enum(path: String): T {
    val raw = required(path)
    return enumValues<T>().find { it.name == raw }
        ?: error("$path must be one of ${enumValues<T>().joinToString()}, but was \"$raw\" (see .env.example)")
}

internal fun ApplicationConfig.port(path: String): Int {
    val raw = required(path)
    return raw.toIntOrNull()?.takeIf { it in 1..65535 }
        ?: error("$path must be a port number, but was \"$raw\" (see .env.example)")
}
