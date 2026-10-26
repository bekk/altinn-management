# AGENTS.md

Altinn Management manages Kartverket's resources in Altinn's Resource Registry. See [README.md](README.md)
for how to run it.

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
