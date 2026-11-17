package no.kartverket.altinnmanagement.restserver.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

// kotlinx reads a string such as "true" or "FALSE" as a boolean, even when it is not lenient
internal object StrictBooleanSerializer : KSerializer<Boolean> {
    override val descriptor = PrimitiveSerialDescriptor("no.kartverket.altinnmanagement.restserver.StrictBoolean", PrimitiveKind.BOOLEAN)

    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)

    override fun deserialize(decoder: Decoder): Boolean {
        val value = (decoder as JsonDecoder).decodeJsonElement() as? JsonPrimitive
        if (value != null && !value.isString) {
            when (value.content) {
                "true" -> return true
                "false" -> return false
            }
        }
        throw SerializationException("Expected true or false")
    }
}
