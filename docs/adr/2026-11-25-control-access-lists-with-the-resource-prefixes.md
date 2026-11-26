# Control access lists with the resource prefixes

## Status

Accepted

## Context

An access list in Altinn's Resource Registry is a list of organisations. When it is connected to a resource whose
`accessListMode` is `Enabled`, only the organisations on the list get access to the resource. A policy can also name a
list as a subject.

All of Kartverket's lists are owned by `kv`, and Altinn cannot tell Kartverket's teams apart: a token for Kartverket may
change every list Kartverket owns. So, as for resources, altinn-management has to decide which lists a caller may see
and change.

For resources, the filtering service already answers this, as `exact` ids and `prefix`es in `resources`. A team with the
prefix `kv-matrikkel-` manages every resource whose id starts with it.

## Decision

Access lists use the prefixes a caller already has in `resources`, with no grants of their own.

- A caller may see and change a list whose id starts with one of its `resources` prefixes. An `exact` id gives no
  access to lists, since it names one resource, not a list.
- A list can be connected to a resource only when both are under the same one of the caller's prefixes.
- A policy may only name a list Kartverket owns, as `urn:altinn:access-list:kv:{list}`, under the same prefix as the
  resource.

We decided against one list per resource, since a list could then not be shared by several resources, and against a
separate `accesslists` part in the filtering service's answer, since every team would then need a second set of grants
to set up and keep in step with its resource prefixes.

## Consequences

- A team gets access to lists through the prefixes it already has, with nothing new to set up.
- A team with only `exact` ids cannot use lists.
- Lists whose ids do not start with a team's prefix, like those made in Altinn Studio before, are not managed through
  this API.

## References

- [AccessListsController in Altinn/altinn-auth](https://github.com/Altinn/altinn-auth/blob/main/src/apps/Altinn.ResourceRegistry/src/Altinn.ResourceRegistry/Controllers/AccessListsController.cs)
