package no.kartverket.altinnmanagement.client

import kotlinx.serialization.Serializable

public data class ContactPoint(
    public val category: ContactCategory?,
    public val email: EmailAddress?,
    public val telephone: PhoneNumber?,
    public val contactPage: WebAddress?,
)

@JvmInline
@Serializable(with = ContactCategorySerializer::class)
public value class ContactCategory private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        public fun of(value: String): ContactCategory = ContactCategory(notBlank(value, "a contact category"))
    }
}

@JvmInline
@Serializable(with = EmailAddressSerializer::class)
public value class EmailAddress private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        private val EMAIL = Regex("[^@\\s]+@[^@\\s]+")

        public fun of(value: String): EmailAddress {
            require(EMAIL.matches(value)) { "Expected an email address, but was \"$value\"" }
            return EmailAddress(value)
        }
    }
}

@JvmInline
@Serializable(with = PhoneNumberSerializer::class)
public value class PhoneNumber private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        public fun of(value: String): PhoneNumber = PhoneNumber(notBlank(value, "a phone number"))
    }
}

internal object ContactCategorySerializer :
    StringValueSerializer<ContactCategory>("ContactCategory", ContactCategory::of, ContactCategory::value)

internal object EmailAddressSerializer : StringValueSerializer<EmailAddress>("EmailAddress", EmailAddress::of, EmailAddress::value)

internal object PhoneNumberSerializer : StringValueSerializer<PhoneNumber>("PhoneNumber", PhoneNumber::of, PhoneNumber::value)
