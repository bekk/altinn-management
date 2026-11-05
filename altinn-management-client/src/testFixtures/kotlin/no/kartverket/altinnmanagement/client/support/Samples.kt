package no.kartverket.altinnmanagement.client.support

public fun resourceJson(resourceType: String = "MaskinportenSchema"): String = """
    {
      "identifier": "kv-matrikkel-brukernavn",
      "version": "v1.0",
      "createdAt": "2026-10-14T07:26:00.171396+00:00",
      "updatedAt": "2026-10-14T07:26:00.171396+00:00",
      "title": { "en": "Cadastre API", "nb": "Matrikkel API", "nn": "Matrikkel API" },
      "description": {
        "en": "Machine-to-machine access to the Cadastre.",
        "nb": "Maskinell tilgang til Matrikkelen.",
        "nn": "Maskinell tilgang til Matrikkelen."
      },
      "rightDescription": {
        "en": "Allows a system provider to use the Matrikkel API on behalf of your organisation.",
        "nb": "Gir en systemleverandør rett til å bruke Matrikkel API på vegne av virksomheten din.",
        "nn": "Gir ein systemleverandør rett til å bruke Matrikkel API på vegner av verksemda di."
      },
      "homepage": "https://kartverket.github.io/api-dokumentasjon/docs/matrikkelen",
      "status": "Completed",
      "contactPoints": [
        { "category": "Kartverket Kundesenter", "email": "kundesenter@kartverket.no", "telephone": "", "contactPage": "" }
      ],
      "resourceReferences": [
        { "referenceSource": "Default", "reference": "kartverk:matrikkel:brukernavn", "referenceType": "MaskinportenScope" },
        { "referenceSource": "Default", "reference": "https://kartverket.no", "referenceType": "Uri" }
      ],
      "delegable": true,
      "visible": true,
      "hasCompetentAuthority": {
        "name": { "en": "Norwegian Mapping Authority", "nb": "Kartverket", "nn": "Kartverket" },
        "organization": "971040238",
        "orgcode": "kv"
      },
      "keywords": [{ "word": "matrikkel", "language": "nb" }, { "word": "cadastre", "language": "en" }],
      "accessListMode": "Disabled",
      "selfIdentifiedUserEnabled": false,
      "enterpriseUserEnabled": false,
      "resourceType": "$resourceType",
      "authorizationReference": [{ "id": "urn:altinn:resource", "value": "kv-matrikkel-brukernavn" }],
      "isOneTimeConsent": false,
      "versionId": 3774
    }
""".trimIndent()
