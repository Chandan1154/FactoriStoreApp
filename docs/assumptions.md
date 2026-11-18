# Assumptions and open decisions

## Assumptions used in the prototype

1. Factori's retailer identifier maps to a UUID `tenant_id` in a verified JWT.
2. Store eligibility is cumulative since store launch and requires either
   500 completed orders or INR 250,000 in qualifying sales.
3. The sales threshold is inclusive (`>=`); the order-or-sales condition is
   inclusive. Eligibility data must come from an authoritative Factori source.
4. Retailers supply an approved HTTPS storefront URL and square PNG logo.
5. Initial app behavior is a WebView storefront, with externally opened
   non-storefront links and payment-app handoffs.
6. Android is the first deliverable. This repository does not implement an
   iOS client.
7. Release signing credentials belong to Factori/retailer and must be
   provisioned through a managed secret/key service, not source code.
8. FCM is a later integration. Firebase's FCM delivery product is documented
   as no-cost, but associated infrastructure, network, support, and adjacent
   Firebase services may incur costs.
9. A trusted dedicated host runs isolated builds. Docker is not exposed to the
   public API tier.
10. API/build storage currently targets a single-node prototype deployment.
11. The generated APKs are locally debug-signed; no app-store release claim is
    made.
12. Synthetic icons in proof-of-concept builds are not retailer-approved.
13. Cost estimates in the companion workbook are editable planning
    assumptions, not vendor quotes or commitments.
14. The full 30-question assignment is available. The architecture pack
    addresses the major topics, but does not yet provide a complete numbered,
    evidence-backed response to every question.

## Decisions required from Factori

- Supply the authoritative order/sales API contract and definitions:
  qualifying statuses, cancellation/return/refund treatment, GST/discount
  inclusion, currency conversion, attribution/start date, freshness, and
  dispute correction.
- Confirm qualifying-sales formula and whether both thresholds are lifetime
  or rolling.
- Confirm retailer onboarding, offboarding, staff identity, tenant claim
  issuance, and permission model.
- Provide approved brand assets, app names, store URLs, payment providers,
  deep links, and approved external host list per retailer.
- Define app store account ownership, signing-key custody, update/revocation,
  release approval, and distribution channel.
- Decide Android-only versus iOS scope; review Apple 4.2 constraints before
  committing to web-wrapper distribution.
- Define customer identity provider, consent text, FCM token collection,
  privacy notice, retention, deletion, and data subject request workflow.
- Define push notification audience/segmentation, opt-out, quiet hours,
  localization, approval, quotas, audit, and delivery reporting.
- Choose durable queue, object storage, deployment topology, supported cloud
  region, disaster recovery, and retention.
- Define service SLOs, build turnaround, support/on-call, rate limits, and
  tenant quotas.
- Set budget currency/tax treatment and obtain current regional vendor quotes.
- Confirm which assignment requirements are acceptance-critical for the first
  review and whether a fully numbered response is expected.
