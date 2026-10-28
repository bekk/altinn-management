package no.kartverket.altinnmanagement.restserver

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.yaml.YamlConfig
import no.kartverket.altinnmanagement.client.auth.MaskinportenKey
import no.kartverket.altinnmanagement.client.exception.MaskinportenException
import java.io.File
import java.net.URI
import java.time.Duration

internal class AppConfig(
    val port: Int,
    val accessLog: Boolean,
    val logbackConfigFile: String?,
    val altinnEnvironment: AltinnEnvironmentSetting,
    val maskinportenClientId: String,
    val maskinportenKey: MaskinportenKey,
    val externalFilteringUrl: URI?,
    val externalFilteringCacheDuration: Duration,
) {
    companion object {
        fun load(config: ApplicationConfig = loadApplicationConfig()): AppConfig {
            val externalFiltering = config.boolean("externalFiltering.enabled", default = false)
            return AppConfig(
                port = config.port("server.port"),
                accessLog = config.boolean("accessLog.enabled", default = true),
                logbackConfigFile = config.file("logback.configFile"),
                altinnEnvironment = config.enum("altinn.environment"),
                maskinportenClientId = config.required("maskinporten.clientId"),
                maskinportenKey = config.maskinportenKey("maskinporten.clientJwk"),
                externalFilteringUrl = if (externalFiltering) config.url("externalFiltering.url") else null,
                externalFilteringCacheDuration = if (externalFiltering) {
                    config.seconds("externalFiltering.cacheSeconds", default = Duration.ofMinutes(1))
                } else {
                    Duration.ZERO
                },
            )
        }
    }
}

internal fun loadApplicationConfig(): ApplicationConfig =
    YamlConfig("application.yaml") ?: error("application.yaml is not on the classpath")

internal fun ApplicationConfig.optional(path: String): String? =
    propertyOrNull(path)?.getString()?.trim()?.takeIf { it.isNotEmpty() }

internal fun ApplicationConfig.required(path: String): String =
    optional(path) ?: error("Missing required configuration $path (see .env.example)")

internal fun ApplicationConfig.boolean(path: String, default: Boolean): Boolean {
    val raw = optional(path) ?: return default
    return raw.toBooleanStrictOrNull()
        ?: error("$path must be true or false, but was \"$raw\" (see .env.example)")
}

internal fun ApplicationConfig.seconds(path: String, default: Duration): Duration {
    val raw = optional(path) ?: return default
    return raw.toLongOrNull()?.takeIf { it >= 0 }?.let(Duration::ofSeconds)
        ?: error("$path must be a whole number of seconds, 0 or more, but was \"$raw\" (see .env.example)")
}

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

internal fun ApplicationConfig.url(path: String): URI {
    val raw = required(path)
    val uri = runCatching { URI(raw) }.getOrNull()
    check(uri != null && uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty()) {
        "$path must be an http or https URL, but was \"$raw\" (see .env.example)"
    }
    check(uri.rawQuery == null && uri.rawFragment == null) {
        "$path must not have a query or fragment, but was \"$raw\" (see .env.example)"
    }
    return uri
}

internal fun ApplicationConfig.file(path: String): String? {
    val raw = optional(path) ?: return null
    check(File(raw).isFile) { "$path must point to a file, but was \"$raw\" (see .env.example)" }
    return raw
}

// The message leaves out the parser's, which can quote the key
internal fun ApplicationConfig.maskinportenKey(path: String): MaskinportenKey = try {
    MaskinportenKey.parse(required(path))
} catch (e: MaskinportenException) {
    error("$path must be an RSA private key as a JWK (see .env.example)")
}
