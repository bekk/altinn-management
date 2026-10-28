package no.kartverket.altinnmanagement.client

import kotlinx.serialization.Serializable

public data class MultilingualText(
    public val en: EnglishText,
    public val nb: BokmalText,
    public val nn: NynorskText,
)

@JvmInline
@Serializable(with = EnglishTextSerializer::class)
public value class EnglishText private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        public fun of(value: String): EnglishText = EnglishText(notBlank(value, "an English text"))
    }
}

@JvmInline
@Serializable(with = BokmalTextSerializer::class)
public value class BokmalText private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        public fun of(value: String): BokmalText = BokmalText(notBlank(value, "a Bokmål text"))
    }
}

@JvmInline
@Serializable(with = NynorskTextSerializer::class)
public value class NynorskText private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        public fun of(value: String): NynorskText = NynorskText(notBlank(value, "a Nynorsk text"))
    }
}

internal object EnglishTextSerializer : StringValueSerializer<EnglishText>("EnglishText", EnglishText::of, EnglishText::value)

internal object BokmalTextSerializer : StringValueSerializer<BokmalText>("BokmalText", BokmalText::of, BokmalText::value)

internal object NynorskTextSerializer : StringValueSerializer<NynorskText>("NynorskText", NynorskText::of, NynorskText::value)
