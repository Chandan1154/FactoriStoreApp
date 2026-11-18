# Security model and residual risks

## Implemented controls

- OAuth2 resource-server JWT validation against a configured issuer. The
  tenant ID comes from the authenticated claim, not client-supplied fields.
- GET/POST scopes separate read and write operations; unmatched API routes
  are denied.
- Tenant-scoped service lookups and PostgreSQL tenant policies with
  `FORCE ROW LEVEL SECURITY`.
- Store URL validation rejects non-HTTPS schemes, credentials, unusual ports,
  IP literals, and non-public-looking hostnames.
- Icon upload size is capped by servlet multipart configuration and image
  decoding/validation is bounded by the icon storage helper.
- APK downloads are authorized before access and use `private, no-store`,
  attachment disposition, and `nosniff`.
- Android WebView blocks TLS errors, mixed content, file URL access, and
  untrusted top-level navigation; no JavaScript bridge is exposed.
- Build worker invocation disables network, limits CPU/memory/PIDs, drops
  capabilities, sets no-new-privileges, runs as a non-root UID, and uses a
  temporary workspace that is cleaned up.
- Signing material is not stored in the database; schema stores references.
- API error responses omit stack traces and do not expose build logs.

## Security assumptions

- The JWT issuer authenticates users and emits correct tenant/scope claims.
- Database runtime credentials are non-superuser and do not have `BYPASSRLS`.
- The host running Docker is dedicated/trusted and is not exposed as an API
  service with a shared Docker socket.
- Worker images and dependencies are reviewed, pinned, and delivered through
  a controlled registry. No build-time network access is required.
- Artifact and icon storage directories are private, access-controlled, and
  backed up according to an approved retention policy.
- Retailers authorize Factori to distribute branded apps and provide logos,
  storefront URLs, and signing ownership approvals.

## Outstanding risks / controls before production

| Risk | Required mitigation |
|---|---|
| In-process build queue loses jobs on restart and has no recovery/retry | Use durable queue, idempotent jobs, bounded retries, dead-lettering, and reconciliation |
| Local filesystem artifact store is single-node and has no expiry policy | Move to private object storage with encryption, retention, integrity checks, and tenant-scoped access |
| Docker daemon access is privileged | Isolate worker service/host, enforce image allowlists and seccomp/AppArmor, audit jobs, patch the host |
| Rate limiting, quotas, and abuse protection are absent | Add gateway/API rate limits, per-tenant quotas, upload throttling, and abuse monitoring |
| Signing lifecycle and ownership are not implemented | Use per-retailer managed keys, KMS/HSM-backed access, rotation/revocation, and signed release authorization; never debug-sign production |
| No customer consent/FCM/API flows | Design explicit consent, token encryption/key rotation, uninstall/revocation, delivery suppression, and deletion before collecting data |
| RLS hasn't been tested on PostgreSQL | Add real-PostgreSQL two-tenant negative tests and verify runtime grants/role attributes |
| Production Android target is behind Play requirement | Upgrade toolchain/SDK and test edge-to-edge behavior before Play delivery |
| WebView-only/repetitive content may fail app review | Obtain site-owner permission, add genuine app-specific value, and validate current Google/Apple review rules |
| No app-level API/DNS egress protection for user-driven content | Keep worker egress disabled; review redirect/payment hosts and Android intent handling against retailer requirements |
| No verified India privacy-law assessment | Obtain current India privacy/legal review before customer-data processing; do not infer compliance from this design |

## Dependency review

An earlier dependency scan found 22 advisories across six dependency groups
in the initial Spring Boot 3.5.16 baseline. The backend was upgraded to Spring
Boot 4.1.2 and a follow-up scan returned no known CVEs at scan time. This is
not a guarantee of continued safety; rerun the dependency scanner on every
release. See [dependency-scan-summary.md](./dependency-scan-summary.md) and
dependency versions in `backend/pom.xml`.

## External policy references

Checked 2026-11-18; policy pages can change:

- Google Play spam and repetitive-content policy:
  <https://support.google.com/googleplay/android-developer/answer/9899034>
- Android target API requirements:
  <https://support.google.com/googleplay/android-developer/answer/11926878>
- Android signing:
  <https://developer.android.com/studio/publish/app-signing>
- Apple App Review Guidelines, including 4.2 and 4.2.6:
  <https://developer.apple.com/app-store/review/guidelines/>
- Android WebView security guidance:
  <https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion>
- Firebase Cloud Messaging pricing/quotas:
  <https://firebase.google.com/docs/cloud-messaging>

India privacy/legal sources were not successfully verified during research;
legal compliance is explicitly out of scope for this technical prototype.
