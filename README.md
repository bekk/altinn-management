# 🗂️ Fleks · Altinn Management

Kotlin library and REST service for managing resources in Altinn's Resource Registry.

## Getting started

```bash
./gradlew build        # compile, ktlint and tests
cp .env.example .env
scripts/dev.sh         # run the server on http://localhost:8080
curl -i localhost:8080/health/live
```

## API

The server serves its OpenAPI spec at `GET /openapi`, and the same spec is checked in as
[`openapi.json`](altinn-management-rest-server/openapi.json). The build fails if that file is stale. Regenerate it
after changing the API:

```bash
./gradlew :altinn-management-rest-server:generateOpenApiSpec
```

## Logging

The server logs JSON to stdout through logback, set up by the bundled
`altinn-management-rest-server/src/main/resources/logback.xml`. To replace it, point `LOGBACK_CONFIG_FILE` at your
own file. Every line logged during a call has the call's `request_id` and the calling `workload`.
