# External services — third-party SDKs & their exit ramps (`external-services` domain)

**This is the single source of truth for external service dependencies** — which third-party SDKs we use, what each costs, the adapter that wraps it, and the migration path off it. If it and any other doc disagree on an external service, this file wins — except: crypto-relevant server endpoints are [`server.md`](server.md), cloud identity model is [`identity.md`](identity.md), config-sync model is [`ecs.md`](ecs.md). Change a dependency → update this file in the same commit.

<!-- AI-TLDR:BEGIN — READ THIS FIRST. If you can answer from this block, STOP. -->

## AI TL;DR

**THE BEACON (do NOT re-decide)**: every external service is a **free-tier stopgap wrapped in an adapter (rule 2) with a documented exit ramp to our own server (rule 8)**. Nothing external appears in a domain signature (rule 1). The three current dependencies:

| Service | Use | Cost | Adapter | Exit ramp (rule 8) |
|---|---|---|---|---|
| **FCM** (Firebase Cloud Messaging) | push (opaque wake-ping, rule 13) | free | `app/adapters/fcm/` (`PushPort`) | own APNS-like push on the Rust server |
| **Firebase Auth** | cloud identity, **lazy** (first cloud action) | Spark (free) | `app/adapters/auth/` | own OIDC provider (Rust server) |
| **Firestore** | config-sync (envelope-encrypted, per-recipient) | Spark (free) | `app/adapters/firestore/` | PostgreSQL row-level security (own server) |

**Whole free-tier posture is temporary** — Cloudflare Worker + Firebase Spark are the MVP stopgap; the target is the own Rust server ([`server.md`](server.md), rule 8). Every free-bypass choice is tracked in [`../dev/server-roadmap.md`](../dev/server-roadmap.md) with a migration route.

**Invariants** (ES1–ES4, see §Invariants). **Status**: Prod (current-state).

**Routing**: which external SDK / cost / exit ramp → stay here. Push payload shape / opacity → [`messaging-delivery.md`](messaging-delivery.md). Cloud identity model → [`identity.md`](identity.md). Config-sync mechanism → [`ecs.md`](ecs.md). Server migration → [`../dev/server-roadmap.md`](../dev/server-roadmap.md).

<!-- AI-TLDR:END -->

## Invariants (decided — do NOT re-derive; changing one is a `decision-supersedes` task)

- **ES1 — every external SDK is behind an adapter (rule 1/2).** No FCM/Firebase/Firestore type in any domain signature; one ACL per service. Test: if the service vanished, only its adapter changes.
- **ES2 — free-tier is a stopgap with a tracked exit ramp (rule 8).** Each dependency has an inline `TODO(server-roadmap)` and an entry in `server-roadmap.md`; the target is the own Rust server.
- **ES3 — the server sees only opaque data (rule 13).** FCM push = opaque wake-ping (no content, no event type); Firestore holds envelope-encrypted blobs; no `userUid` as a routing key.
- **ES4 — lazy cloud (device-self-sufficiency).** External cloud services are invoked only at the first cloud action, never at first launch (see [`identity.md`](identity.md) ID2).

## Operational notes

- **Firebase provider enablement** — on Spark, Anonymous/Email/Phone/Google providers are enabled **via Firebase Console only** (Identity Platform Admin REST API unavailable on Spark); do not attempt to automate (memory `firebase_auth_provider_manual_only`).
- **Secrets** — service-account JSON / keys / tokens never travel through chat; local file + CLI only (memory `secret_handling`).
- **Firebase IAM REST** — list/delete service-account keys via `iam.googleapis.com/v1/...` + firebase-tools refresh-token works (for rotation/audit).

## Rejected (do not re-litigate)

- ❌ External SDK type in a domain signature (rule 1) — behind an adapter (ES1).
- ❌ Free-tier choice without a tracked exit ramp (rule 8) — every one is in `server-roadmap.md` (ES2).
- ❌ `userUid` as a Firestore/FCM routing key (rule 13) — opaque IDs (ES3).
- ❌ Cloud/account required at first launch (ES4).

## Related domains

- Push shape: [`messaging-delivery.md`](messaging-delivery.md). Cloud identity: [`identity.md`](identity.md). Config-sync: [`ecs.md`](ecs.md). Server + migration: [`server.md`](server.md) / [`../dev/server-roadmap.md`](../dev/server-roadmap.md). Client layering: [`client-android.md`](client-android.md).
- Onboarding: [`INDEX.md`](INDEX.md).
