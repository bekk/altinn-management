package no.kartverket.altinnmanagement.client

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal abstract class StringValueSerializer<T : Any>(
    name: String,
    private val parse: (String) -> T,
    private val text: (T) -> String,
) : KSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T): Unit = encoder.encodeString(text(value))

    override fun deserialize(decoder: Decoder): T = parse(decoder.decodeString())
}

internal fun notBlank(value: String, what: String): String {
    require(value.isNotBlank()) { "Expected $what that is not blank" }
    return value
}
