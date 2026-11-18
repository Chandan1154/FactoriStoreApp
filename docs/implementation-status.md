# Implementation status

## Delivered

- Android WebView template with origin/payment navigation policy, restricted
  WebView file/content settings, upload picker, progress/error UI, and runtime
  brand configuration.
- Local Android APK generator, icon density/adaptive icon preparation, package
  ID validation, and APK/signature checks.
- Two regenerated API 34 debug APK proof-of-concepts for Zolara and GKP Vastra.
- Android Studio's default `app` build uses Zolara's package, label, icon
  configuration path, and storefront URL instead of the `example.com` placeholder.
- Authenticated Spring Boot API prototype with tenant claim/scope checks,
  CRUD/status/download/regeneration routes, eligibility provider interface,
  async bounded build queue, and Docker worker execution.
- PostgreSQL Flyway schema with ten table groups, tenant keys, RLS policies,
  and forced RLS.
- Backend/Android tests and architecture, API, database, security, assumption,
  and status documentation.

## Verification performed

- `./gradlew testDebugUnitTest assembleDebug -PSTORE_VERSION_CODE=2
  -PSTORE_VERSION_NAME=2.0` passed with Gradle 8.6, AGP 8.4.1, Kotlin
  1.9.0, and target API 34.
- Both regenerated debug APKs report compile/target API 34, passed package/name
  checks, and passed `apksigner verify`; their embedded synthetic icons differ.
- Public storefront checks returned HTTP 200 and titles Zolara and GKP Vastra.
- `mvn -q -f backend/pom.xml test` passed after upgrading the backend to
  Spring Boot 4.1.2, including Spring context startup with H2.
- A follow-up resolved dependency scan found no known CVEs at scan time.
- PostgreSQL 16.6 migration and basic forced-RLS tenant isolation checks passed.
- Assessment PDF and formula-driven Excel workbook generated under `docs/`.
- Zolara's debug APK was installed and launched successfully on the Pixel 6
  Pro Android 14 emulator. `com.factori.storeapp.MainActivity` was foreground,
  the app process stayed running, and no launch-time Java/Kotlin crash appeared
  in recent logcat output.
- Verified the default Android Studio debug APK embeds package
  `com.factori.store.in.zolara.a6d5ab36280`, label `Zolara`, and
  `https://zolara.in`; installed and launched it on the emulator, where the
  Zolara storefront rendered.

## Not verified / not production-ready

- The Gradle wrapper is restored to 8.6 for Android Studio compatibility.
  First-time distribution use still requires a reachable Gradle source.
- Physical-device installation and runtime behavior are still not verified.
  Emulator coverage is limited to Android 14/API 34 and does not cover every
  supported OS/device variant.
- PostgreSQL 16.6 accepted the migration under the separate migration role.
  A two-tenant RLS check verified one visible tenant row, zero rows without
  tenant context, and owner-role filtering under forced RLS. Full API-backed
  PostgreSQL integration tests are still absent.
- Docker worker image build was attempted but could not complete because the
  local Docker engine reported a read-only overlay filesystem while writing
  an apt-installed package layer. Worker execution and end-to-end remote API
  builds remain unverified.
- No Factori order adapter, dashboard, customer identity, Firebase/FCM
  registration or sending, retailer onboarding, or authoritative order
  system integration exists.
- Eligibility returns `503` until an authoritative provider is configured.
- The build queue is in-process and non-durable. Storage is local filesystem
  and single-node. Retry, retention, and horizontal scaling are absent.
- Release signing, KMS/HSM, retailer-specific key custody, Play publishing,
  and production signing verification are not implemented.
- The Android target is API 34 because this Android Studio installation
  supports AGP 8.4.1; API 36 requires a compatible IDE/plugin upgrade.
  Store submission, edge-to-edge, and device compatibility have not been verified. The
  WebView-centric product still needs policy review and distinct value.
- No official retailer icon assets were supplied. The two POC apps use
  synthetic monograms and must not be presented as approved brand assets.
- The full 30-question assignment is now available, but a complete numbered,
  source-backed answer matrix has not been produced; see
  [assumptions.md](./assumptions.md).

## Gate before any production pilot

1. Map all 30 assignment questions to evidence, clearly labeled assumptions,
   and unanswered Factori decisions; obtain approved retailer assets and
   storefront/payment requirements.
2. Integrate and test the authoritative order source; resolve legal/policy
   interpretations and identity/consent decisions.
3. Upgrade Android Studio/AGP/Gradle to a compatible API 36 toolchain,
   test behavior on supported devices, and resolve Google Play/Apple product
   eligibility with the retailer.
4. Run real PostgreSQL migration/RLS negative tests and configure non-
   superuser runtime roles.
5. Build and scan immutable worker images; test end-to-end in an isolated
   environment without exposing Docker to the API tier.
6. Implement durable queue, private object storage, signing service, audit,
   rate limits, monitoring, retention, backups, disaster recovery, and
   operational ownership.
7. Install/test retailer APKs on supported devices, validate payment flows,
   and use approved production signing keys before distribution.
