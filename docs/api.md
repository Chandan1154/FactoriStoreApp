# API contract (prototype)

All routes use OAuth2 bearer JWTs from `JWT_ISSUER_URI`. The verified token
must carry a UUID `tenant_id` claim. GET routes require
`SCOPE_mobile-apps:read`; POST routes require `SCOPE_mobile-apps:write`.
Requests without a valid token are rejected. A tenant is never accepted from
the request body or query string.

## Routes

| Method | Route | Request / response | Behavior |
|---|---|---|---|
| `GET` | `/api/apps/eligibility` | JSON `EligibilityStatus` | Loads authoritative retailer order metrics, then applies the policy |
| `POST` | `/api/apps/generate` | Multipart `request` JSON (`storeName`, `storeUrl`) and `icon` image; `202` with `MobileAppResponse` | Requires eligibility, creates app/build, queues the build; `Location` points to app |
| `GET` | `/api/apps?page=0&size=25` | Spring `Page<MobileAppResponse>` | Lists only the caller's tenant apps |
| `GET` | `/api/apps/{appId}` | `MobileAppResponse` | Returns app and latest build status |
| `GET` | `/api/apps/{appId}/download` | APK bytes | Downloads latest successful APK; private/no-store |
| `POST` | `/api/apps/{appId}/regenerate` | `202` with `MobileAppResponse` | Increments version and queues a rebuild when no build is active |

`MobileAppResponse` contains app ID, application ID, store name, store URL,
app status, created time, and latest build. The build contains ID, status,
attempt, failure code, creation time, and completion time. The server never
returns worker logs through the current public API.

## Errors

Errors use RFC 9457 `ProblemDetail` responses.

| Status | Meaning |
|---|---|
| `400` | Invalid store configuration/icon, invalid request fields, missing multipart part |
| `401` | Missing/invalid bearer token |
| `403` | Missing required scope |
| `404` | App/build/APK not available to the authenticated tenant |
| `409` | Duplicate store identity, active build conflict, or retailer below threshold |
| `429` | Bounded build queue full; response includes `Retry-After: 30` |
| `503` | Eligibility integration unavailable |

## Eligibility rule

The policy returns eligible when either cumulative completed orders are at
least 500 or qualifying sales since store launch are at least INR 250,000.
The exact qualifying-sales definition and authoritative data source must be
approved by Factori. A `measuredAt` timestamp is mandatory. The API does not
silently assume eligibility if the provider is missing or fails.

## Operational notes

- Upload limit: 5 MiB icon and 6 MiB total multipart request.
- Store URLs must be HTTPS, use a public DNS hostname, contain no credentials,
  and use the standard HTTPS port.
- Builds are asynchronous, but the queue is in-process and non-durable.
- The eligibility endpoint currently returns `503` because no provider bean
  is configured. No request should be considered eligible until integration.
- There is no dashboard or retailer onboarding endpoint. A retailer row and
  identity-provider claims must be provisioned separately.
