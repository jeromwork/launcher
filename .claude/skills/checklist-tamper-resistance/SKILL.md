---
name: checklist-tamper-resistance
description: Verifies that any cloud feature (paid or subscription-gated) is protected from client-side bypass via local flag patching. Per decision 2026-06-15-deferred-cloud/03 — billing is cloud-only; cloud features must validate entitlement on server (server-validated JWT), not via client-computed flag. Triggered on any spec touching subscription, billing, entitlement, premium features, server features, or cloud-gated functionality.
---

# Checklist: tamper-resistance

Per [decision 2026-06-15-deferred-cloud/03](../../../docs/product/decisions/2026-06-15-deferred-cloud/03-billing-cloud-only.md) — billing is cloud-only. Local-mode is free forever. Cloud features (pair, sync, push, remote) require active subscription after trial month.

This skill enforces that **cloud features cannot be unlocked by patching the local APK** — entitlement is always validated server-side.

---

## Server-validated entitlement

- [ ] CHK-TAM-001 Every cloud feature gates access through a **server endpoint** call (Cloudflare Worker), not through a client-computed flag.
- [ ] CHK-TAM-002 The server endpoint validates the user's subscription state from authoritative source (server-side billing record / Play Billing API verification), NOT from client-passed token.
- [ ] CHK-TAM-003 If the spec defines a "premium" flag in client state — refuse. The flag is decoration only; access decisions live on server.
- [ ] CHK-TAM-004 Entitlement JWT (if used) is **short-lived** (≤ 1 hour) and refreshed via signed call to server. NOT stored long-term in DataStore.

## Subscription state source-of-truth

- [ ] CHK-TAM-005 Subscription state is read from **server**, not from local storage, before granting access to any cloud feature.
- [ ] CHK-TAM-006 If user is offline, the spec describes a grace period (e.g. 7 days) during which cached entitlement is honored. After grace, cloud features pause (NOT crash, NOT silent allow).
- [ ] CHK-TAM-007 Cache of entitlement is signed by server with `expiresAt`, and client refuses to honor cache past `expiresAt + drift_tolerance`.

## Platform integrity

- [ ] CHK-TAM-008 The spec acknowledges (or has inline TODO to) Play Integrity API for verifying the app is unmodified and from Play Store. Failed integrity check → cloud features denied even with valid Sign-In.
- [ ] CHK-TAM-009 R8 obfuscation is enabled for release builds (gradle config check).
- [ ] CHK-TAM-010 If the spec adds a critical entitlement path, an inline TODO exists for **code attestation** (post-MVP): critical method hash sent to server periodically and compared to known-good.

## Local-mode is genuinely free

- [ ] CHK-TAM-011 The spec does NOT introduce subscription gating on local features (themes, tiles, contacts entered locally, etc.). These are forever free per decision 03.
- [ ] CHK-TAM-012 The spec does NOT show subscription-required banners over local features.
- [ ] CHK-TAM-013 If a feature has both local and cloud aspects (e.g. contact tile = local; contact photo upload = cloud), the local aspect remains free even on expired cloud subscription.

## Anti-patterns to refuse

- [ ] CHK-TAM-014 Client-side `isPremium: Boolean` flag that gates UI. Refuse. UI may decorate based on cached entitlement, but the actual server call decides.
- [ ] CHK-TAM-015 Local-only feature unlock based on Google Sign-In presence (without server-side subscription check). Sign-In ≠ paid subscriber.
- [ ] CHK-TAM-016 Crypto key embedded in APK used to "sign" entitlement claims locally. This always falls to APK extraction.
- [ ] CHK-TAM-017 Free trial implemented as a client-counted day counter. Refuse — server tracks trial start/end.

## Cloudflare Worker contract

- [ ] CHK-TAM-018 If the spec adds a new cloud feature, the corresponding Worker endpoint is named (`/api/...`) and its entitlement check is documented (1 line: "verifies subscription via X").
- [ ] CHK-TAM-019 Endpoint is rate-limited per UID + per IP to prevent abuse via stolen credentials.
- [ ] CHK-TAM-020 Entitlement failures return distinct error codes (no subscription / trial expired / integrity failed) so client can show targeted message — but the **decision** is server-side.

## Audit trail

- [ ] CHK-TAM-021 Subscription state changes (start trial / start subscription / cancel / expire) are logged server-side with timestamp. This is the legal audit trail.
- [ ] CHK-TAM-022 Failed entitlement checks are logged (sample, not every call) to detect cracking attempts.

---

## When to refuse

Refuse the spec if any of:

1. Client-side flag gates access to cloud features.
2. Trial period is client-counted.
3. Cryptographic verification of entitlement is done locally with embedded key.
4. Free local features become paywall-gated.
5. Worker endpoint not specified for new cloud feature.

## Output

Inline into `specs/<id>/checklists/tamper-resistance.md`.

## Relationship to other skills

- **`checklist-device-self-sufficiency`** — covers local-vs-cloud boundary. This skill covers protecting the cloud side from local bypass.
- **`checklist-security`** — covers general PII / intent / permission security. This skill covers specifically subscription tampering.
- **`checklist-backend-substitution`** — covers swap of backend. This skill assumes a backend exists; ensures it's the authority on entitlement.

## Notes

- **R8 obfuscation alone is NOT enough**. It raises the bar but doesn't stop a determined attacker. The defense is: server is the source of truth, not the client.
- **Play Integrity API rejection** can have false positives (rooted dev devices, legitimate alternative app stores). The spec should describe the appeal path for legitimate users hitting this.
- Server-validated entitlement is the **single** thing that prevents bypass. Everything else (obfuscation, Play Integrity, attestation) is defense-in-depth.
