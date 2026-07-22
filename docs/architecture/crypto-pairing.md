# Crypto pairing / membership — our Authentication Service (`family.pairing`)

**This file is the single source of truth for the pairing / membership zone** — how two devices establish an identity↔key binding, and the policy for revoking a member. If it and any other doc disagree, this file wins — except: versioning is owned by [`wire-format.md`](wire-format.md), primitives by [`crypto-primitives.md`](crypto-primitives.md), the umbrella by [`crypto.md`](crypto.md). The device-management revoke *decision* is contracted in **TASK-102's `### Decision (English)` block** — that block wins over any prose here on revoke specifics. Change the model → update this file in the same commit.

<!-- AI-TLDR:BEGIN — READ THIS FIRST. If you can answer from this block, STOP. -->

## AI TL;DR

**THE BEACON (do NOT re-decide)**: this zone is **our RFC 9750 Authentication Service (AS)** — it produces the trusted binding "this device ↔ this public key" that the MLS core later consumes and never re-derives. Two devices meet via a **QR-carried Noise_XX handshake**, and membership/revoke is **application-layer policy** (RFC 9750 §3.5: MLS does not enforce access control — the app does). Authorization is *our* decision, not the protocol's.

**Two-world split (critical — do NOT assume one stack)**:
- **Kotlin side (BUILT)**: identity↔key binding + revoke policy — ports `DeviceIdentityRepository`, `RecipientResolver`, `EncryptedMediaStorage` (`family.pairing.api`). Uses libsodium primitives ([`crypto-primitives.md`](crypto-primitives.md)).
- **Rust side (PLANNED, TASK-67)**: the Noise_XX handshake itself via the `snow` crate, through `:crypto-ffi`. A hand-rolled ECDH handshake was **Rejected** ([`crypto-primitives.md`](crypto-primitives.md) §Rejected).

**Zone charter**

| Owns | Must NOT own |
|---|---|
| identity↔key binding (the AS output), Noise_XX handshake (via `snow`), authorization / revoke policy | ratchet / group-crypto internals (that is MLS core), primitive re-implementation (incl. own ECDH) |

**Revoke model** (device-management group — TASK-102 Decision block is the contract): the **primary user's device is the sole MLS Commit signer**. Admins do **not** issue MLS Remove directly — they edit the primary user's **profile** on the server under an **edit lock** (TTL 300 s). At sync, the device compares the profile's authorized-device list against the actual MLS roster, detects the diff, and issues the MLS Remove Commit. Single source of truth = the primary user's device; a rogue admin cannot silently kick (profile change is visible to all synced clients). Roster roles (declared in profile, not enforced by MLS): `owner` (primary user's device, sole executor), `admin` (paired devices, may propose profile edits), reserved roles for Phase-3+. Granularity: identity-level in UI.

**⚠ Scope boundary (do NOT conflate)**: this revoke model is for the **device-management group** (TASK-102, MVP path). It does **NOT** apply to the future **family messenger group** (TASK-42, parking m-4) — that is a separate group with its own future policy. Config sharing today uses envelope encryption ([`crypto-key-hierarchy.md`](crypto-key-hierarchy.md)), not this group.

**Module home** (TASK-146, done 2026-07-22): `family.pairing.*` lives in its own module **`:core:pairing`** (depends on `:core:crypto` + `:core:wire`). Serialization is **legal here** — pairing is our Authentication Service, not a crypto primitive. The `@Serializable` types `PublicKey` / `SigningPublicKey` / `DeviceId` moved with it. `:core:crypto` now carries **zero** serialization (plugin removed), restoring the crypto-SDK "no serialization" invariant fully (not the narrower package-scoped reading it had while the debt stood).

> ⚠️ SUPERSEDED (2026-07-22): an earlier plan put `ByteArrayBase64Serializer` into `:core:pairing`. Corrected — the Base64 codec is a **wire primitive**, not pairing-specific: `KeyBlob` (`:core`) and the recovery-blob codec (`:app`) also consume it. Its home is **`:core:wire`** — the leaf module that is the extractability barrier keeping crypto clean ([`extraction-policy.md`](extraction-policy.md), [`wire-format.md`](wire-format.md), TASK-141). Its serial descriptor name `family.ByteArrayBase64` is stack-wide and unchanged, so no wire format moved.

**Invariants** (PR1–PR3, see §Invariants): PR1 AS output is trusted-for-authentication only; MLS consumes it, never re-derives. PR2 handshake uses `snow`, never hand-rolled. PR3 membership policy lives here, never in MLS core.

**Related decisions** (most DECIDED — read the Decision block): TASK-102 (device-group ownership/revoke — DECIDED), TASK-106 (signup gate — DECIDED), TASK-116 (iconic pairing challenge — Discussion), TASK-143 (QR deep-link versioning — Done/paused; do not resolve here).

**Routing**: pairing / binding / revoke → stay here. Handshake primitives → [`crypto-primitives.md`](crypto-primitives.md). Group mechanics → [`crypto.md`](crypto.md) MLS zone. Versioning → [`wire-format.md`](wire-format.md).

<!-- AI-TLDR:END -->

## Invariants (decided — do NOT re-derive; changing one is a `decision-supersedes` task)

- **PR1 — the AS output is trusted for authentication only.** This zone establishes identity↔key bindings; the MLS core *consumes* them and must never re-derive or second-guess the binding. (RFC 9750 AS role.)
- **PR2 — the handshake uses `snow` (Noise_XX), never hand-rolled.** Own-ECDH is rejected. The Rust `snow` crate, bridged through `:crypto-ffi`, owns the handshake state machine.
- **PR3 — membership / authorization policy lives here, never in the group protocol.** MLS executes *how* adds/removes happen cryptographically; *who may* add/remove is this zone's policy (RFC 9750 §3.5/§6.4: access control is application-layer by design). Symptom of violation: the MLS core deciding authorization.

## Industry grounding

- **RFC 9750 (MLS architecture)** — separates the Authentication Service (identity↔key binding, trusted-for-authentication) from the protocol and the Delivery Service. Access control is explicitly application-layer (§3.5). https://www.rfc-editor.org/rfc/rfc9750.html
- **Signal** — publishes the handshake (X3DH/PQXDH), the ratchet, and session/device management (Sesame) as three separate specs; device linking is even a separate crate. Handshake ≠ ratchet ≠ membership. https://signal.org/docs/
- **Noise Protocol Framework / `snow`** — Noise_XX is a standard mutually-authenticated handshake pattern; `snow` is a vetted Rust implementation.

## Exit ramps

- **`snow` swap** → the handshake is behind a `:crypto-ffi` boundary; replacing it is an adapter-level change, the Kotlin binding contract unchanged.
- **Revoke escalation** (if reconciliation-based revoke is too slow for a compromised admin while the primary device is offline) → server-side "eviction quorum": N admins sign an immediate Remove signal, applied at next sync. Additive, no wire-format break (TASK-102 exit ramp). Device-level lock for a stolen device = TASK-103.

## Related

- Umbrella + zone map: [`crypto.md`](crypto.md). Primitives (incl. `snow`/FFI): [`crypto-primitives.md`](crypto-primitives.md). Key hierarchy: [`crypto-key-hierarchy.md`](crypto-key-hierarchy.md). Versioning: [`wire-format.md`](wire-format.md). Server endpoints (profile lock, group inbox): [`server.md`](server.md).
- Pairing feature: TASK-67. Device-management revoke decision: TASK-102.
