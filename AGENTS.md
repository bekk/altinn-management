# AGENTS.md

Kotlin library and Ktor REST service that manage Kartverket's resources in Altinn's Resource Registry. See
[README.md](README.md) for how to run it.

## Modules

- `altinn-management-client`: the library. It talks to Altinn. It uses Kotlin's explicit API mode, so make `public`
  only what other services need.
- `altinn-management-rest-server`: a Ktor server that offers a JSON API on top of the client.

## Commands

```bash
./gradlew build          # compile, ktlint and tests; must pass before a change is done
./gradlew ktlintFormat   # fix formatting
```

## Conventions

- `main()` reads the settings into `AppConfig` and starts the server. `Application.module()` adds the routes, so
  tests and production run the same code.
- Server tests start the app with `testServer(...)`. Name a test with a sentence in backticks.
- Comment only a reason the code cannot show.

## Decisions (ADRs)

Important decisions are recorded in `docs/adr`, with `template-brief.md` or `template-detailed.md`. When a change
makes such a decision, suggest an ADR, but don't write one unless asked. Never rewrite an accepted ADR: write a new one
that supersedes it.
