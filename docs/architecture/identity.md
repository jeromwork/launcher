# Identity — who a user/device is (`identity` domain)

**This is the single source of truth for the identity domain** — the LOCAL/CLOUD identity model, registration, the Authentication-Service relationship, profile/contacts, and the signup gate. If it and any other doc disagree on identity, this file wins — except: the identity↔key binding (the AS) is owned by [`crypto-pairing.md`](crypto-pairing.md), key material by [`crypto-key-hierarchy.md`](crypto-key-hierarchy.md), server endpoints by [`server.md`](server.md). Change the model → update this file in the same commit. The messenger and other domains **consume** identity; they do not re-decide it.

<!-- AI-TLDR:BEGIN — READ THIS FIRST. If you can answer from this block, STOP. -->

## AI TL;DR

**THE BEACON (do NOT re-decide)**: identity is **LOCAL-first, cloud lazy**. Every device has a **LOCAL per-device identity** (the MLS client / device keypair) that works with **no sign-in** — the launcher and local features never require an account. A **CLOUD per-user identity** (for cross-device sync + pairing) is added **lazily, at the first cloud action** (Firebase Auth), not at first launch. The **Authentication Service** (the trusted "this device ↔ this public key" binding) is **QR pairing** — owned by [`crypto-pairing.md`](crypto-pairing.md), not re-derived here. Server-facing IDs are **opaque UUIDs** (rule 13) — never a Google `sub` / email / phone / Firebase UID as a routing or storage key.

**Model (decided)**

| Concern | Choice | Owner |
|---|---|---|
| Identity model | Hybrid: LOCAL per-device (MLS) + CLOUD per-user (sync) | TASK-106 (decided) |
| Cloud auth | Firebase Auth, **lazy** (only at first cloud action) | existing (Spark plan) |
| Identity↔key binding (AS) | QR pairing = RFC 9750 Authentication Service | [`crypto-pairing.md`](crypto-pairing.md) (TASK-67/102) |
| Recovery | Auto MLS Add + post-facto notification | [`crypto-key-hierarchy.md`](crypto-key-hierarchy.md) (TASK-101) |
| Remote lock (stolen device) | Keystore wipe + full recovery on unlock | crypto (TASK-103) |
| Server IDs | opaque UUID / `nsId` — never identity-provider PKs | rule 13 |

**Invariants** (ID1–ID5, see §Invariants). **Open**: the signup gate (§Open, TASK-106 — Discussion), username, presence, 2FA.

**Routing**: identity model / registration / profile / contacts → stay here. Identity↔key binding / pairing → [`crypto-pairing.md`](crypto-pairing.md). Root key / recovery → [`crypto-key-hierarchy.md`](crypto-key-hierarchy.md). Server endpoints (`/v1/identity/*`) → [`server.md`](server.md). Presence/personal-block (messenger surface) → [`messaging.md`](messaging.md).

<!-- AI-TLDR:END -->

## Invariants (decided — do NOT re-derive; changing one is a `decision-supersedes` task)

- **ID1 — LOCAL-first: a device works with no account.** The launcher and local features never gate on sign-in. The LOCAL identity is a device keypair (the MLS client), present from first launch.
- **ID2 — cloud is a lazy upgrade at the first cloud action, not at first launch.** Firebase Auth is invoked only when the user first does something needing the cloud (pairing, sync). No account wall.
- **ID3 — the Authentication Service is QR pairing, owned by crypto-pairing.** Identity↔key binding is not re-derived here; this domain consumes the AS output. (RFC 9750 AS role.)
- **ID4 — opaque server IDs (rule 13).** The server sees an opaque UUID / `nsId`; the `userUid → nsId` mapping stays client-side. No Google `sub` / email / phone / Firebase UID as a routing or storage key.
- **ID5 — recovery, root key, and remote lock are owned by crypto**, not here (TASK-101/103, [`crypto-key-hierarchy.md`](crypto-key-hierarchy.md)). This domain owns the *account/profile* model, not the key math.

## Open questions (stated completely here — do NOT defer to a task)

- **OQ-ID1 — signup gate (TASK-106, Discussion).** *Options*: (A) invitation-code from an admin (family: мама-дочка invites бабушка); (B) open self-signup; (C) phone/identity verification. *Criteria*: gate strength vs onboarding friction vs segment (family vs clinic vs B2B). *Preset field*, not a hardcoded invariant (rule 11 preset-vs-invariant): the gate value differs per segment — family default = invitation-code; clinic/B2B TBD. Architectural invariant = "a signup gate exists and is server-checkable"; the *policy* is a preset. *Owner*: TASK-106.
- **OQ-ID2 — username / public handle.** Only meaningful if public groups arrive (see [`messaging.md`](messaging.md) §Public groups). Deferred.
- **OQ-ID3 — presence (online/last-seen).** Metadata; likely in-app indicator, not server-observed (rule 10/13). See [`messaging.md`](messaging.md) OQ-4.
- **OQ-ID4 — 2FA / passcode / app-lock.** App-lock exists via TASK-103 (crypto); a second factor for recovery is TASK-21 (escrow). Profile-level passcode TBD.

## Rejected (do not re-litigate)

- ❌ **Google Sign-In (or any account) at first launch** — superseded by LOCAL-first (ID1/ID2); an account wall breaks the device-self-sufficiency principle.
- ❌ **`userUid` (Google `sub` / email / phone / Firebase UID) as a server routing or storage key** — opaque UUID only (ID4/rule 13).
- ❌ **Re-deriving the identity↔key binding here** — it is the AS ([`crypto-pairing.md`](crypto-pairing.md)).

## Build-vs-buy

- 🟢 **Firebase Auth** (cloud identity, Spark/free) — behind an adapter (rule 1/2). Exit ramp (rule 8): own OIDC provider on the future Rust server ([`server.md`](server.md)). Providers are enabled via Firebase Console only on Spark (see memory `firebase_auth_provider_manual_only`).
- 🟢 **JWT** verification for server endpoints — baseline in [`server.md`](server.md) (rules 12/13).

## Related domains

- Identity↔key binding / pairing: [`crypto-pairing.md`](crypto-pairing.md). Root key / recovery: [`crypto-key-hierarchy.md`](crypto-key-hierarchy.md). Server endpoints: [`server.md`](server.md). Consumers: [`messaging.md`](messaging.md) (profile/presence/contacts surface), [`ecs.md`](ecs.md) (config is device-local, identity-independent).
- Owner tasks (history, not truth): TASK-106 (model + signup gate), TASK-101 (recovery), TASK-103 (remote lock), TASK-67 (pairing).
