# Architecture

## Scope and status

The repository is a technical proof of concept for creating retailer-branded
Android WebView apps. The user-facing app hosts the retailer's HTTPS storefront;
the platform API accepts configuration and an icon, schedules an APK build,
and serves build status and artifacts. Production integrations and app-store
readiness are not implied.

## Components and trust boundaries

```text
Retailer dashboard / trusted operator (not implemented)
                  |
                  | OAuth2 JWT, tenant_id, read/write scopes
                  v
       Spring Boot platform API
         |       |        |
         |       |        +--> filesystem artifact store (single-node POC)
         |       +-----------> bounded in-process build queue
         |                              |
         |                              | isolated Docker invocation
         v                              v
 PostgreSQL + forced RLS       dedicated build-worker host
                                      | network disabled
                                      v
                           Android template -> APK

Retailer/customer device -> Android WebView -> retailer storefront
                                              -> allowlisted payment handoff
```

The API does not mount a Docker socket into an internet-facing service. The
executor invokes Docker on its configured host with no network, resource
limits, dropped Linux capabilities, a non-root worker user, a temporary
workspace, and cleanup. This still requires a trusted worker host: Docker
daemon access is privileged and should be isolated from the public API plane.

## Android app

The Android client embeds the retailer storefront using WebView. JavaScript and
DOM storage are enabled for normal storefront compatibility. File access,
universal access from file URLs, mixed content, and third-party cookies are
disabled. TLS errors are rejected. Top-level web navigation is constrained to
the configured HTTPS storefront origin and explicit payment providers; other
HTTPS links are handed to the external browser. UPI and allowlisted payment
intent links are handled separately. No JavaScript bridge is exposed.

App name, package ID, storefront URL, icon, and version are build-time
properties. The template is not a native storefront implementation and does
not add customer identity, push notification, or order synchronization.

## API request and build flow

1. The caller authenticates with the configured JWT issuer. The API derives
   tenant identity only from the verified `tenant_id` claim and enforces
   endpoint scopes.
2. The generation request includes store name/URL and an icon. The service
   validates the storefront URL and icon, checks current eligibility through
   an authoritative-provider interface, stores the icon, and persists the app
   and queued build in one database transaction.
3. A bounded in-process executor starts the build. The worker context includes
   tenant/app/build identifiers, configuration, icon key, and version.
4. The worker copies only template files into a temporary workspace, builds
   offline, validates APK structure/package/label/signature, and stores the
   result and log.
5. The API records build status and version metadata. Tenant-scoped status and
   download routes authorize every lookup before returning private artifacts.

The queue and filesystem are deliberately single-instance proof-of-concept
components. A process restart can lose queued jobs; there is no durable
broker, retry scheduler, multi-instance object storage, or artifact lifecycle
policy.

## Persistence

The PostgreSQL schema has ten tenant-owned table groups: retailers, apps,
builds, versions, signing metadata, app users, installations, device tokens,
notification campaigns, and delivery logs. Composite foreign keys keep
related records within the same retailer (and where relevant the same app).
Forced PostgreSQL RLS is applied to all ten tables. The API sets a transaction-
local tenant variable from the authenticated identity before repository
access. See [database.md](./database.md).

The current Java model uses the retailer, app, build, and version tables.
Identity, installations, device tokens, notification, and signing metadata
tables are forward-looking schema, not implemented product integrations.

## Availability and scale path

Production evolution should separate public API and isolated build workers;
replace the in-memory executor with a durable queue; move artifacts to
private object storage; use a migration-only database owner and a least-
privilege non-superuser runtime role; add rate limiting, quotas, monitoring,
backup/restore, idempotency, retries, and tenant-aware audit events. Build
worker egress should remain disabled, and worker images should be immutable
and scanned. The current worker definition targets Linux/AMD64.

## Store distribution constraints

The Android template is currently built with SDK 34 because AGP 8.4.1 is the
maximum plugin version supported by this Android Studio installation.
Google's API-level requirement checked on 2026-11-18 requires SDK 36 for
new app submissions/updates after 2026-08-31, so an IDE/toolchain upgrade is
required before Play submission. This does not resolve Play's repetitive-
content and WebView policies, which also require
distinct value and authorization from the site owner. Apple review guidelines
discourage repackaged/template web apps and may reject a thin storefront
wrapper. Store approval is not guaranteed by building an APK.
