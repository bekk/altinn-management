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
scripts/dev.sh           # run the server on :8080 with the variables in .env
```

## Conventions

- `main()` reads the settings into `AppConfig`, sets up logging and starts the server. `Application.module()` installs
  the plugins and adds the routes, so tests and production run the same code.
- A new setting goes in `application.yaml` as `"$?ENV_VAR"` and in `.env.example`. Its errors name the
  `application.yaml` key and end with `(see .env.example)`, like the helpers in `Config.kt`.
- Server tests start the app with `testServer(...)`. Name a test with a sentence in backticks.
- Comment only a reason the code cannot show.
- Never log request bodies or tokens.

## Decisions (ADRs)

Important decisions are recorded in `docs/adr`, with `template-brief.md` or `template-detailed.md`. When a change
makes such a decision, suggest an ADR, but don't write one unless asked. Never rewrite an accepted ADR: write a new one
that supersedes it.
