# Mock Altinn from its source code, in memory

## Status

Accepted

## Context

altinn-management calls Altinn's Resource Registry and, to write to it, Maskinporten and Altinn's token exchange. Local
runs need these without the real services: a write to TT02 changes real data, and needs a Maskinporten client with
`altinn:resourceregistry/resource.write`. Altinn's documentation leaves out how the registry behaves in places, and
differs from it in others. For example, search matches part of any reference, and can return an older version of a
resource.

## Decision

`mock-api` plays the routes of Maskinporten, Altinn's token exchange and the Resource Registry that our endpoints call,
with only the rules our API has to react to.

- It copies Altinn's behaviour from Altinn's source code in Altinn/altinn-auth: the status codes, the order of the
  checks and the bodies of the answers. When it is extended, the source is checked the same way.
- It keeps its data in memory, seeded with Kartverket's resources in production and one of another organisation's.
  maskinporten-management's mock uses Postgres. This one needs no database, so it runs without Docker, and its tests run
  in `./gradlew build`.
- It does not check signatures. Its tokens are JWTs without a signature, and it only accepts tokens it issued itself.
  Every token is for Kartverket.
- Altinn's check of scope prefixes depends on a setting whose value in production we cannot see. The mock runs Altinn's
  default: every Maskinporten scope must start with the token's `consumer_prefix`, and a scope that does not gives 401.

## Consequences

- The mock can be wrong where Altinn changes, and where it guesses: the setting for scope prefixes, and details ASP.NET
  adds to error answers. When Altinn turns out to answer differently, the mock is changed to follow Altinn.
- A 401 from Altinn for a scope prefix looks like a refused token. Our endpoints avoid that by checking the `kartverk:`
  prefix before they call Altinn.
- A route can come to the mock before our client calls it. If an endpoint ends up calling Altinn differently, its pull
  request changes the mock.

## References

- [Altinn.ResourceRegistry in Altinn/altinn-auth](https://github.com/Altinn/altinn-auth/tree/main/src/apps/Altinn.ResourceRegistry)
- [mock-api/README.md](../../mock-api/README.md)

