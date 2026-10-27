package no.kartverket.altinnmanagement.restserver

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.yaml.YamlConfig

internal class AppConfig(
    val port: Int,
) {
    companion object {
        fun load(config: ApplicationConfig = loadApplicationConfig()): AppConfig = AppConfig(
            port = config.port("server.port"),
        )
    }
}

internal fun loadApplicationConfig(): ApplicationConfig =
    YamlConfig("application.yaml") ?: error("application.yaml is not on the classpath")

internal fun ApplicationConfig.optional(path: String): String? =
    propertyOrNull(path)?.getString()?.trim()?.takeIf { it.isNotEmpty() }

internal fun ApplicationConfig.required(path: String): String =
    optional(path) ?: error("Missing required configuration $path")

internal fun ApplicationConfig.port(path: String): Int {
    val raw = required(path)
    return raw.toIntOrNull()?.takeIf { it in 1..65535 }
        ?: error("$path must be a port number, but was \"$raw\"")
}
