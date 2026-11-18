# Factori Store App

This repository contains an Android WebView storefront template, a local APK
generator, and a Spring Boot API prototype for tenant-scoped app generation.
It is a proof of concept, not a production-ready retailer app platform.

## What is implemented

- Android app template with HTTPS storefront navigation restrictions, payment
  deep-link handling, file selection, progress/error states, and runtime store
  name, package ID, icon, URL, and version configuration.
- Local APK generation and APK/signature/package validation.
- Authenticated app-generation, status, download, regeneration, listing, and
  eligibility API routes.
- PostgreSQL schema with tenant foreign keys and forced row-level security.
- Bounded in-process build queue and a constrained Docker build worker.
- Eligibility policy tests and URL-policy tests.

Known gaps and verification boundaries are in [docs/implementation-status.md](./docs/implementation-status.md).
Architecture, API, database, security, and assumptions are documented under
[docs/](./docs/).

## Build the Android template

Requirements: JDK 17+, Android SDK platform/build tools as configured in
`app/build.gradle.kts`, and a reachable Gradle distribution source on first
use. Run:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The Android Studio `app` run configuration and default Gradle build open the
Zolara demo storefront (`https://zolara.in`). Override `STORE_APP_NAME`,
`STORE_APP_ID`, and `STORE_URL` Gradle properties when building another store.
The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
Debug signing is for local testing only.

## Generate a retailer-configured APK

Install Pillow for the same Python interpreter that `python3` resolves to:

```sh
python3 -m pip install -r requirements-apk.txt
```

Provide a retailer-approved square PNG icon. The generator creates the
required Android icon densities and checks the result:

```sh
./scripts/generate-apk.sh \
  --store-name "Example Store" \
  --store-url "https://shop.example.org" \
  --icon "/path/to/approved-icon.png" \
  --output "artifacts/example-store-debug.apk"
```

Use `--version-code` and `--version-name` for subsequent builds. Release builds
require the four `STORE_SIGNING_*` environment variables described by the
generator; never commit signing keys or passwords. The sample icons in
`proof-of-concept/icons/` are synthetic and are not approved retailer artwork.

## Run the API locally

Requirements: JDK 17+, Maven 3.9+, PostgreSQL 15+, an OAuth2 JWT issuer, and
Docker available only to a trusted build-worker host. Set:

```text
DATABASE_URL=jdbc:postgresql://localhost:5432/factori
DATABASE_USERNAME=<factori_app runtime role>
DATABASE_PASSWORD=<runtime secret>
MIGRATION_DATABASE_USERNAME=<factori_migrator>
MIGRATION_DATABASE_PASSWORD=<migration secret>
MIGRATION_DATABASE_URL=jdbc:postgresql://localhost:5432/factori
JWT_ISSUER_URI=<trusted issuer URL>
FACTORY_TEMPLATE_ROOT=<absolute path to this checkout>
FACTORY_BUILD_IMAGE=<trusted, prebuilt worker image>
```

Create/provision a retailer row before using the API. The JWT must contain a
UUID `tenant_id` claim and `mobile-apps:read` / `mobile-apps:write` scopes.
Apply the role guidance in [database/roles.sql](./database/roles.sql). For a
local-only test, the two DB credentials may temporarily be the same
non-superuser role. Never use the PostgreSQL bootstrap/superuser account for
the app. Then run:

```sh
mvn -f backend/pom.xml test
mvn -f backend/pom.xml spring-boot:run
```

The API does not provide an order-data adapter, identity onboarding, FCM
delivery, or signing-key integration. Eligibility therefore returns
`503 Service Unavailable` until an authoritative order metrics provider is
configured. The build executor expects a prebuilt offline worker image.
Do not expose a Docker daemon socket to a public API container; isolate builds
on a trusted worker host. See [docs/security.md](./docs/security.md).

For local database development only, `docker-compose.yml` starts PostgreSQL
bound to loopback. Supply `POSTGRES_ADMIN_USERNAME` and
`POSTGRES_ADMIN_PASSWORD`; use that bootstrap account only to create roles.
Run `database/roles.sql` as the DB administrator, set `factori_app` and
`factori_migrator` passwords out of band, and use the distinct credentials
for API and Flyway. Connect to the `factori` database when applying the role
script, for example `psql -h localhost -U <bootstrap-user> -d factori -f database/roles.sql`.
The compose file intentionally does not start the API or mount a Docker socket.

## Generate assessment deliverables

The architecture assessment PDF and editable cost-model workbook are under
`docs/`. Regenerate them after editing the inputs/content with:

```sh
python3 -m pip install -r requirements-docs.txt
python3 scripts/generate-platform-assessment.py
```

The workbook values are planning assumptions, not vendor quotes.

## Build worker image

`build-worker/Dockerfile` is the worker image definition. Image construction
requires network access to the pinned Android SDK command-line archive and
the dependency repositories. The PostgreSQL migration and basic forced-RLS
checks passed on PostgreSQL 16.6; the worker image build did not complete in
this environment. See [docs/implementation-status.md](./docs/implementation-status.md).

## Tests

```sh
./gradlew testDebugUnitTest
mvn -f backend/pom.xml test
```

These are unit/context tests; they do not prove PostgreSQL RLS behavior,
container isolation, storefront rendering on devices, or production signing.
