# Database design and operations

## Entity groups

`backend/src/main/resources/db/migration/V1__platform_schema.sql` defines ten
PostgreSQL tables:

| Table | Purpose |
|---|---|
| `retailers` | Tenant root, mapped to an external retailer identity |
| `mobile_apps` | Store URL, package ID, icon key, app state, and version |
| `app_builds` | Asynchronous build lifecycle, artifact/log keys, checksum |
| `app_versions` | Successful immutable APK version records |
| `app_signing_metadata` | Secret-manager reference and certificate metadata; no key bytes |
| `app_users` | Retailer customer identity reference and consent/revocation |
| `app_installations` | App installation and lifecycle metadata |
| `device_tokens` | Encrypted push token, HMAC lookup value, key reference, consent |
| `notification_campaigns` | Push campaign metadata and app-scoped payload |
| `notification_delivery_logs` | Per-campaign, per-token delivery state |

Each tenant-owned table has a retailer ID, RLS policy, and forced RLS. Composite
foreign keys prohibit relationships that cross retailer boundaries; key
relationships for builds, versions, installations, and delivery records also
bind the app identity. Indexes support tenant app/build lists, active token
lookups, scheduled campaigns, and recent delivery history.

## Tenant enforcement

The API derives tenant ID from the validated JWT, starts a transaction, and
sets `app.tenant_id` transaction-locally before querying. Each table policy
compares its `retailer_id` (or `retailers.id`) to that setting. `FORCE ROW
LEVEL SECURITY` also subjects table owners to policy checks. A PostgreSQL
superuser or role with `BYPASSRLS` can still bypass RLS; neither may be used
by the application.

For production, split migration and runtime identities. The migration role
should be narrowly controlled and own the migrated objects; the runtime
identity must be a non-superuser, `NOBYPASSRLS`, and receive only required
DML privileges via default grants. Do not grant the runtime role role-
management, schema-create, or superuser privileges. Configure Flyway with
`MIGRATION_DATABASE_*` and the app datasource with `DATABASE_*`.
`database/roles.sql` documents the role skeleton; set credentials out of band.
Apply it while connected to the `factori` database, before the first Flyway
migration, so default privileges cover newly created objects.

## Migration and test status

Flyway applies `V1` at startup and Hibernate validates mappings without
creating schema. Maven tests use H2 for application-context verification; the
migration's PostgreSQL-specific RLS, `JSONB`, trigger, and policy behavior has
not been executed against PostgreSQL in this environment. Validate it against
the production PostgreSQL major version before deployment, and add two-tenant
integration tests proving both reads and writes are isolated.

## Data retention and privacy

The schema records customer identifiers and consent metadata, but the API does
not yet implement customer identity, token enrollment, deletion workflows,
retention schedules, or notification sending. Define lawful purpose, consent,
retention, deletion, access auditing, encryption key rotation, and breach
procedures before collecting real customer data. Eligibility orders remain
outside this database until Factori provides the authoritative integration.
