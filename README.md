<div align="center">

# 🗂️ Fleks · Altinn Management

**Kotlin library and REST service for managing Kartverket's MaskinportenSchema resources in Altinn's Resource
Registry.**

<br/>

[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Gradle](https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org)
[![Ktor](https://img.shields.io/badge/Ktor-087CFA?style=flat-square&logo=ktor&logoColor=white)](https://ktor.io)
[![Altinn 3](https://img.shields.io/badge/Altinn-3-1E2B3C?style=flat-square)](https://docs.altinn.studio)
[![Maskinporten](https://img.shields.io/badge/Maskinporten-0069B4?style=flat-square)](https://docs.digdir.no/docs/Maskinporten/)
[![Status](https://img.shields.io/badge/status-in%20development-orange?style=flat-square)](#-about-the-project)

</div>

---

## 📖 Contents

- [🎯 About the project](#-about-the-project)
- [🧩 Modules](#-modules)
- [📦 Prerequisites](#-prerequisites)
- [🚀 Getting started](#-getting-started)
- [🔌 API](#-api)
- [📚 Using the client](#-using-the-client)
  - [Building the client](#building-the-client)
  - [Reading resources](#reading-resources)
  - [HTTP client](#http-client)
  - [Environments](#environments)
- [🔑 Environment variables](#-environment-variables)
- [📝 Logging](#-logging)
- [🧪 Testing](#-testing)
- [🔗 Useful links](#-useful-links)

---

## 🎯 About the project

An API that Kartverket offers through Maskinporten can be delegated in Altinn when Altinn's Resource Registry has a
resource of type `MaskinportenSchema` for it. The resource names the API's Maskinporten scopes, and has the texts
Altinn shows when access is delegated.

The REST server lets Kartverket's apps manage these resources over plain JSON, without speaking Altinn's API
themselves. A call only reaches it after the platform has checked that the calling app may make it. When filtering is
on, lists only show the resources the calling app has access to.

> [!NOTE]
> The server answers with Altinn's values as they are, under its own names: Altinn's `identifier` is `resourceId`,
> `rightDescription` is `delegationDescription`, and `scopes` holds the `MaskinportenScope` references from
> `resourceReferences`. A field Altinn has no value for is left out.

---

## 🧩 Modules

| Module                                                           | What it is                                                    | Published as                                                                  |
| :--------------------------------------------------------------- | :------------------------------------------------------------ | :---------------------------------------------------------------------------- |
| [`altinn-management-client`](altinn-management-client)           | Kotlin library that talks to Maskinporten and Altinn directly | nothing yet, as only the server uses it                                       |
| [`altinn-management-rest-server`](altinn-management-rest-server) | Ktor server exposing a REST/JSON API over the client          | a Docker image, built with [Jib](https://github.com/GoogleContainerTools/jib) |
| [`mock-api`](mock-api)                                           | A mock of Altinn and Maskinporten, with its data in memory    | nothing, as it is only for local runs                                         |

`altinn-management-rest-server` is the intended consumer of `altinn-management-client`, so other systems can manage
resources over plain JSON without speaking Maskinporten and Altinn's API themselves.

---

## 📦 Prerequisites

- JDK 25, or let the Gradle toolchain resolver provision one
- To call Altinn: a [Maskinporten client](https://docs.digdir.no/docs/Maskinporten/maskinporten_guide_apikonsument.html)
  granted `altinn:resourceregistry/resource.write`, with its private key as JWK. Altinn's write endpoints require
  its token, and the server needs the client at startup, although reading from Altinn needs no token

---

## 🚀 Getting started

```bash
./gradlew build                # compile, ktlint and tests
./gradlew :mock-api:run        # in another terminal: the mock of Altinn on http://localhost:8090
cp .env.example .env
scripts/dev.sh                 # run the server on http://localhost:8080
curl -i localhost:8080/api/maskinportenschemas
```

`.env.example` sets `ALTINN_ENVIRONMENT` to `LOCAL`, so the server talks to the mock instead of Altinn, and needs no
real Maskinporten client. The mock starts from a few of Kartverket's resources each time. See its
[README](mock-api/README.md) for what it plays and how closely it follows Altinn.

`dev.sh` is what turns `.env` into real environment variables. The server itself only reads the environment, so
starting it any other way, like an IDE run configuration or `./gradlew :altinn-management-rest-server:run`, means
setting them yourself.

---

## 🔌 API

| Endpoint                                     | What it is                                                              |
| :------------------------------------------- | :---------------------------------------------------------------------- |
| `GET /api/maskinportenschemas`               | Lists Kartverket's MaskinportenSchemas, or with `?scope=` those for one |
| `POST /api/maskinportenschemas`              | Creates a MaskinportenSchema                                            |
| `GET /api/maskinportenschemas/{id}`          | Gets a MaskinportenSchema                                               |
| `PUT /api/maskinportenschemas/{id}`          | Changes a MaskinportenSchema                                            |
| `PUT /api/maskinportenschemas/{id}/withdraw` | Withdraws a MaskinportenSchema                                          |
| `PUT /api/maskinportenschemas/{id}/policy`   | Gives a MaskinportenSchema its policy                                   |
| `POST /api/genericaccessresources`           | Creates a GenericAccessResource                                         |
| `GET /api/genericaccessresources/{id}`       | Gets a GenericAccessResource                                            |
| `GET /openapi`                               | The OpenAPI spec as JSON                                                |
| `GET /health/live`                           | Liveness probe for the platform, left out of the spec                   |

The server does no authentication itself: the platform checks the calling app before a call reaches it. When
filtering is on, the server sends the caller's `X-Forwarded-Client-Cert` header, which Istio sets, to the filtering
service, which says which resources and scopes the caller has access to. A list then shows a resource only when the
caller has access to it and to all its scopes.

Every call gets a request id: the caller's `X-Request-Id` header, or a new UUID when it is missing or not safe to log.
The server logs it on every line of the call as `request_id`, and sends it back in the response's `X-Request-Id`
header, so a caller can tell us which call to look for.

The server's errors follow Problem Details ([RFC 9457](https://www.rfc-editor.org/info/rfc9457/)). They are sent as
`application/problem+json`. Their `type` is a name that says what went wrong, like `tag:kartverket.no,2026:not-found`,
and your code can rely on it. A validation error has an `errors` list that names the fields that failed.

The OpenAPI spec documents every field and error. The server serves it at `GET /openapi`, and the same spec is checked
in as [`openapi.json`](altinn-management-rest-server/openapi.json).

---

## 📚 Using the client

### Building the client

```kotlin
val httpClient = JavaOutgoingHttpClient(
    HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
    requestTimeout = Duration.ofSeconds(10),
)

val client = ResourceRegistryClient(
    platformBaseUrl = AltinnEnvironment.TT02.platformBaseUrl,
    httpClient = httpClient,
)
```

Build one client and reuse it.

### Reading resources

```kotlin
val schemas = client.listMaskinportenSchemas()
val schema = client.getMaskinportenSchema(ResourceId.of("kv-matrikkel-brukernavn"))
```

`listMaskinportenSchemas` gives Kartverket's resources of type `MaskinportenSchema`. `getMaskinportenSchema` gives
`null` when Altinn has no resource with the id, or one of another type. `ResourceId.of` takes 4 or more of `a-z`,
`0-9`, `_` and `-`, like Altinn.

A call throws an `AltinnException` when Altinn cannot be called, answers with an error, or gives an answer the client
cannot read. When Altinn answered, it has Altinn's status and body.

### HTTP client

`httpClient` takes an `OutgoingHttpClient`, an interface with one method. Use `JavaOutgoingHttpClient`, or implement
it on the HTTP client you already use. Timeouts, proxy and so on are set on that client.

> [!IMPORTANT]
> An `OutgoingHttpClient` must not follow redirects, since the calls carry tokens, and must throw an `IOException`
> when a call fails. `JavaOutgoingHttpClient` refuses a Java `HttpClient` that follows redirects.

The server connects within 2 s and waits at most 10 s per call. A list makes at most two calls, to the filtering
service and then to Altinn, so callers of `GET /api/maskinportenschemas` should allow 25 s.

### Environments

| Environment | Altinn platform (`platformBaseUrl`) | Maskinporten (`maskinportenTokenUrl`) |
| :---------- | :---------------------------------- | :------------------------------------ |
| `TT02`      | `https://platform.tt02.altinn.no`   | `https://test.maskinporten.no/token`  |
| `PROD`      | `https://platform.altinn.no`        | `https://maskinporten.no/token`       |

The server picks one with `ALTINN_ENVIRONMENT`, which also takes `LOCAL` for the mock on `http://localhost:8090`.

---

## 🔑 Environment variables

| Variable                           | Required             | Default | What it is                                                                     |
| :--------------------------------- | :------------------- | :------ | :----------------------------------------------------------------------------- |
| `ALTINN_ENVIRONMENT`               | yes                  | -       | Which Altinn and Maskinporten to call: `TT02`, `PROD` or `LOCAL` for the mock  |
| `MASKINPORTEN_CLIENT_ID`           | yes                  | -       | Our Maskinporten client. `LOCAL` takes any id                                  |
| `MASKINPORTEN_CLIENT_JWK`          | yes                  | -       | The client's private key, as an RSA JWK on one line, with its registered `kid` |
| `EXTERNAL_FILTERING_ENABLED`       | no                   | `false` | Whether lists only show the resources the calling app has access to            |
| `EXTERNAL_FILTERING_URL`           | when filtering is on | -       | The filtering service                                                          |
| `EXTERNAL_FILTERING_CACHE_SECONDS` | no                   | `60`    | How long the server remembers what each caller has access to. `0` turns it off |
| `PORT`                             | no                   | `8080`  | The port the server listens on                                                 |
| `ACCESS_LOG_ENABLED`               | no                   | `true`  | Whether the server logs one line per call                                      |
| `ACCESS_LOG_HEALTH_ENABLED`        | no                   | `false` | Whether calls to the health endpoints get a line too                           |
| `LOGBACK_CONFIG_FILE`              | no                   | -       | A `logback.xml` that replaces the bundled one                                  |

See [`.env.example`](.env.example) for more about each variable and where to get it.

`altinn-management-rest-server/src/main/resources/application.yaml` maps each one onto a configuration key, so a
missing required variable stops the server at startup rather than at the first request.

---

## 📝 Logging

The server logs to stdout through logback, as JSON, set up by the bundled
`altinn-management-rest-server/src/main/resources/logback.xml`. To replace it, point `LOGBACK_CONFIG_FILE` at your own
file. The server refuses to start if that path is not a file. Every line logged during a call has the call's
`request_id` and the calling `workload`.

---

## 🧪 Testing

```bash
./gradlew build # compile, ktlint, and the tests of the client, the server and the mock
```

CI runs `./gradlew build` on every pull request.

The build fails if the checked-in `openapi.json` is stale. Regenerate it after changing the API:

```bash
./gradlew :altinn-management-rest-server:generateOpenApiSpec
```

---

## 🔗 Useful links

| Resource                            | Link                                                                             |
| :---------------------------------- | :------------------------------------------------------------------------------- |
| Resource Registry API               | https://docs.altinn.studio/nb/api/resourceregistry/                              |
| Altinn's guides for resource owners | https://docs.altinn.studio/nb/authorization/guides/resource-owner/               |
| Resource Registry source code       | https://github.com/Altinn/altinn-auth/tree/main/src/apps/Altinn.ResourceRegistry |
| Altinn Studio documentation         | https://docs.altinn.studio                                                       |
| Maskinporten                        | https://docs.digdir.no/docs/Maskinporten                                         |
| Altinn TT02 (test)                  | https://tt02.altinn.no                                                           |

---

<div align="center">
<sub>Made by <b>Fleks-Team Tilgangsstyring</b> at Bekk, for Kartverket</sub>
</div>
