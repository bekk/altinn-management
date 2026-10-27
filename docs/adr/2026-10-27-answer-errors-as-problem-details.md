# Answer errors as Problem Details, with a name as `type`

## Status

Accepted

## Context

maskinporten-management and altinn-pdp answer errors as Problem Details (RFC 9457), with a name for each kind of error
as `type`. Their ADRs weigh the options. altinn-management has no endpoints yet, so it can start with the same format,
and callers can handle errors from all three APIs with the same code.

## Decision

Errors are sent as `application/problem+json`, in the same format and with the same `type` names as in
maskinporten-management and altinn-pdp:

```json
{
  "type": "tag:kartverket.no,2026:not-found",
  "title": "Not found",
  "status": 404,
  "detail": "There is no endpoint at this path"
}
```

- `type` says what went wrong, and code should check it. It is a `tag:` URI (RFC 4151): `tag:kartverket.no,2026:`
  followed by a name. It is not a link.
- `title` is the same for every error of a type, `status` is the HTTP status, and `detail` explains this error. `detail`
  is left out when the title says it all.
- `errors` comes only with `validation-error`, and lists every field that failed, each with `field`, `code` and
  `message`.

| `type`, after `tag:kartverket.no,2026:` | Status | When |
|---|---|---|
| `validation-error` | 400 | One or more fields are missing or not valid |
| `malformed-request` | 400 | The request could not be read, for example a body that is not valid JSON |
| `upstream-rejected` | 400 | The values passed this API's checks, but Altinn rejected them |
| `not-found` | 404 | There is no endpoint at the path |
| `method-not-allowed` | 405 | The endpoint does not accept the method |
| `upstream-error` | 502 | This API could not get an answer from Altinn |
| `internal-error` | 500 | An unexpected error in this API |

All seven types are part of the format from the start, so the first endpoints use them as they are.

## Consequences

- Callers handle errors from altinn-management, maskinporten-management and altinn-pdp the same way, and tools that
  know RFC 9457 can read them.
- The names are part of the API. Renaming one breaks callers, so a new meaning needs a new name. A new kind of error
  that the APIs share gets the same name in all of them.
- `type` is not a link, so what each type means is described in the OpenAPI spec.

## References

- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457)
- [RFC 4151: The 'tag' URI Scheme](https://www.rfc-editor.org/rfc/rfc4151)
- [The decision in maskinporten-management](https://github.com/kartverket/maskinporten-management/blob/main/docs/adr/2026-10-22-answer-errors-as-problem-details.md)
- [The decision in altinn-pdp](https://github.com/kartverket/altinn-pdp/blob/main/docs/adr/2026-10-22-answer-errors-as-problem-details.md)
