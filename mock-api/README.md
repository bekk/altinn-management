# mock-api

A mock of Altinn, and of Maskinporten, which issues the tokens Altinn's write routes require, for running
altinn-management locally. It keeps its data in memory, so every start begins from the seed in [`resources.json`](src/main/resources/resources.json): Kartverket's three MaskinportenSchema resources in
production, and one of another organisation's.

It copies Altinn's behaviour from Altinn's source code in
[Altinn/altinn-auth](https://github.com/Altinn/altinn-auth/tree/main/src/apps/Altinn.ResourceRegistry), not from the
documentation. When you add to it, check the source the same way.

## Running

```sh
./gradlew :mock-api:run   # from the repository root, on http://localhost:8090
```

## Routes

These are the routes of Altinn's Resource Registry it plays. Like in Altinn, anyone may read them, with or without a
token.

| Route | Like in Altinn |
| :-- | :-- |
| `GET /resourceregistry/api/v1/resource/{id}` | The newest version of the resource, or the one in `?versionId=`. The id must match exactly |
| `GET /resourceregistry/api/v1/resource/search` | Filters on `id`, `resourceType`, `orgCode`, `organizationId`, `description`, `keyword` and `reference`, and ignores `title`. `resourceType` is a type's name or number, and any other value is 400. `reference` matches part of any reference, of every type. The filters run over every version, so when only an older version of a resource matches, that older version is returned |

Errors look like Altinn's: ProblemDetails, sent as `application/json`.

## Tokens

| Route | Like in Maskinporten and Altinn |
| :-- | :-- |
| `POST /token` | Maskinporten's token endpoint for JWT grants. It checks a grant's claims like Maskinporten does: `aud` must be `http://localhost:8090/`, `iss`, `iat`, `exp` and `scope` must be there, and a grant can only be used once. It does not check the signature, and gives any client the scope it asks for, on behalf of Kartverket |
| `GET /authentication/api/v1/exchange/maskinporten` | Altinn's exchange of a Maskinporten token for an Altinn token. The Altinn token lasts 30 minutes and has the Maskinporten token's claims, `scope` and `consumer` among them |

The mock's tokens are JWTs without a signature. Instead, the mock only accepts a token it issued itself.
