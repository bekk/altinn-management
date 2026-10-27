# Manage Kartverket's resources in Altinn through our own REST API

Status: accepted  
Deciders: Team Tilgangsstyring

## Context and Problem Statement

Teams at Kartverket create Maskinporten scopes through maskinporten-management. For other organisations to get access to
a scope through Altinn, Altinn needs a resource of the type MaskinportenSchema that points to the scope. So when a team
makes a scope available, it must be able to see whether the scope has such a resource, and to create, change and
withdraw it. The teams have their own user interfaces and services, so they need an API to do this.

These resources live in Altinn's Resource Registry. Anyone can read from it, without a token. To write, a caller needs
an Altinn token, which it gets by exchanging a Maskinporten token with the scope
`altinn:resourceregistry/resource.write`. Altinn only checks which organisation the token is for: a token for Kartverket
may change every resource Kartverket owns, and Altinn cannot tell one team at Kartverket from another. Resources cannot
be deleted in production. Instead, Altinn recommends withdrawing a resource by publishing it again as not delegable, not
visible and with the status withdrawn.

How should teams at Kartverket manage Kartverket's MaskinportenSchema resources in Altinn?

## Decision Drivers

* A Maskinporten client that may write to the Resource Registry may change all of Kartverket's resources. The fewer such
clients and keys, the better.
* Since Altinn cannot tell Kartverket's teams apart, what a team may change has to be decided by us.
* Altinn's API has pitfalls a caller must know about: search matches part of any reference and can return an older
version of a resource, a change replaces the whole resource, and nothing can be deleted.
* Getting a token means a Maskinporten client with a key, a JWT grant and a token exchange, which every team that writes
would otherwise build itself.

## Considered Options

* Every team calls Altinn's Resource Registry itself.
* A Kotlin client library, for teams to use in their own services.
* A REST API on top of a client library, run by our team.

## Decision Outcome

Chosen option: "A REST API on top of a client library", because it is the only option where a single client of
Kartverket's holds the access to write, and where our API, not each caller, decides what a caller may change. The client
library is kept alongside it, for the REST API's own use and for teams that would rather use it directly.

### Positive Consequences

* Only this service needs a Maskinporten client that may write to the Resource Registry, and its key.
* Callers use plain JSON, and do not need to know Altinn's pitfalls, Maskinporten or the token exchange.
* Fixes, and changes to follow Altinn's API, are made in one place.

### Negative Consequences

* A new service to run, between the callers and Altinn.
* The API must decide what each caller may change. How it does that is a decision of its own.
* A team calls two APIs: maskinporten-management to make the scope available, and then this one for the resource.

## Pros and Cons of the Options

### Every team calls Altinn's Resource Registry itself

* Good, because there is no service to build or run.
* Bad, because every team that writes needs its own Maskinporten client and key, each of which may change all of
Kartverket's resources.
* Bad, because nothing on our side decides what a team may change.
* Bad, because every team has to learn Altinn's pitfalls, and build the token handling, itself.

### A Kotlin client library

* Good, because the token handling and Altinn's pitfalls are solved once, for every team that uses it.
* Bad, because every service that uses it still needs its own Maskinporten client and key.
* Bad, because nothing stops a team from changing another team's resources.
* Bad, because teams that do not use Kotlin or Java cannot use it.

### A REST API on top of a client library

* Good, because only this service holds the access to write.
* Good, because it can decide what each caller may change.
* Good, because any team can call it over HTTP.
* Bad, because it is a service to run, and when it is down, no one can change a resource.

## References

* [Altinn's Resource Registry, in Altinn/altinn-auth](https://github.com/Altinn/altinn-auth/tree/main/src/apps/Altinn.ResourceRegistry)
* [The same decision in altinn-pdp, for PDP lookups](https://github.com/kartverket/altinn-pdp/blob/main/docs/adr/2026-10-14-rest-api-for-pdp-lookup.md)

