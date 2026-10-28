package no.kartverket.altinnmanagement.client

import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URISyntaxException

@JvmInline
@Serializable(with = WebAddressSerializer::class)
public value class WebAddress private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        public fun of(value: String): WebAddress {
            val uri = try {
                URI(value)
            } catch (e: URISyntaxException) {
                null
            }
            require(uri?.scheme?.lowercase() in setOf("http", "https") && !uri?.host.isNullOrEmpty()) {
                "Expected a web address that starts with http:// or https://, but was \"$value\""
            }
            return WebAddress(value)
        }
    }
}

internal object WebAddressSerializer : StringValueSerializer<WebAddress>("WebAddress", WebAddress::of, WebAddress::value)
