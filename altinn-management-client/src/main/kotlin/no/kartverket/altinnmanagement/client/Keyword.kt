package no.kartverket.altinnmanagement.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

public data class Keyword(
    public val word: KeywordText,
    public val language: Language,
)

@JvmInline
@Serializable(with = KeywordTextSerializer::class)
public value class KeywordText private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        public fun of(value: String): KeywordText = KeywordText(notBlank(value, "a keyword"))
    }
}

@Serializable
public enum class Language {
    @SerialName("en")
    EN,

    @SerialName("nb")
    NB,

    @SerialName("nn")
    NN,
    ;

    internal companion object {
        fun of(code: String): Language = requireNotNull(entries.find { it.name.lowercase() == code }) {
            "Expected the language en, nb or nn, but was \"$code\""
        }
    }
}

internal object KeywordTextSerializer : StringValueSerializer<KeywordText>("KeywordText", KeywordText::of, KeywordText::value)
