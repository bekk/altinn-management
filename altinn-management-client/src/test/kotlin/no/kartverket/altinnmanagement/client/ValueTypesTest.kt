package no.kartverket.altinnmanagement.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValueTypesTest {

    private fun <T> accepts(of: (String) -> T, value: (T) -> String, vararg values: String) {
        for (it in values) assertEquals(it, value(of(it)), "for \"$it\"")
    }

    private fun refuses(of: (String) -> Any, vararg values: String) {
        for (it in values) assertFailsWith<IllegalArgumentException>("for \"$it\"") { of(it) }
    }

    @Test
    fun `a resource id is 4 or more of a-z, 0-9, _ and -`() {
        accepts(ResourceId::of, ResourceId::value, "kv-matrikkel-brukernavn", "app_kv_soknad-utlevering-eiendomsdata", "nrl1")
        refuses(ResourceId::of, "", "abc", "Kv-matrikkel", "kv matrikkel", "kv.matrikkel", "..", "../search", "kv/x", "blåbær")
    }

    @Test
    fun `a scope is not empty and has no whitespace`() {
        accepts(Scope::of, Scope::value, "kartverk:matrikkel:brukernavn", "kartverk:tilgangsstyring/demo.read")
        refuses(Scope::of, "", "kartverk:a b", "kartverk:a\tb")
    }

    @Test
    fun `an organisation number is 9 digits`() {
        accepts(OrganizationNumber::of, OrganizationNumber::value, "971040238")
        refuses(OrganizationNumber::of, "", "97104023", "9710402380", "97104023a")
    }

    @Test
    fun `the texts in each language are not blank`() {
        accepts(EnglishText::of, EnglishText::value, "Cadastre API")
        accepts(BokmalText::of, BokmalText::value, "Matrikkel API")
        accepts(NynorskText::of, NynorskText::value, "Matrikkel API")
        refuses(EnglishText::of, "", " ")
        refuses(BokmalText::of, "", " ")
        refuses(NynorskText::of, "", " ")
    }

    @Test
    fun `a web address starts with http or https and has a host`() {
        accepts(WebAddress::of, WebAddress::value, "https://www.kartverket.no/", "http://kartverket.no/x?y=1")
        refuses(WebAddress::of, "", "www.kartverket.no", "skip.kartverket.no", "ftp://kartverket.no", "https://", "https://a b", "må beskrives")
    }

    @Test
    fun `an email address has one @ with something on both sides`() {
        accepts(EmailAddress::of, EmailAddress::value, "kundesenter@kartverket.no")
        refuses(EmailAddress::of, "", "kundesenter", "@kartverket.no", "kundesenter@", "a@b@c", "kunde senter@kartverket.no")
    }

    @Test
    fun `a contact category, phone number and keyword are not blank`() {
        accepts(ContactCategory::of, ContactCategory::value, "Kundesenter")
        accepts(PhoneNumber::of, PhoneNumber::value, "+47 32 11 80 00")
        accepts(KeywordText::of, KeywordText::value, "matrikkel")
        refuses(ContactCategory::of, "", " ")
        refuses(PhoneNumber::of, "", " ")
        refuses(KeywordText::of, "", " ")
    }

    @Test
    fun `a language is en, nb or nn`() {
        assertEquals(listOf(Language.EN, Language.NB, Language.NN), listOf("en", "nb", "nn").map(Language::of))
        refuses(Language::of, "", "EN", "no", "se", "nb-NO")
    }
}
