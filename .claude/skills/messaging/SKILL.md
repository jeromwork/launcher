---
name: messaging
description: The ecosystem's messenger architecture — one MLS substrate ("one pipe, three faucets": chat / gallery / config-sync) behind a transport facade, a blind-courier delivery server, and feature taxonomy that lives in the domain not the vendor adapter. Invoke whenever work touches messenger / messaging / chat / message / conversation / group chat / reaction / reply / thread / edit / receipt / typing / mention / MessagingPort / DeliveryServicePort / delivery service / mailbox / fan-out / KeyPackage directory / Matrix / matrix-rust-sdk / Megolm / Olm / MLS messenger / Phoenix / phnx / MIMI / Wire / Kalium / Jitsi / LiveKit / SFU / WebRTC / call / voice / video / gallery / album / media transfer / push routing, or asks "how should our messenger work / build vs buy for the messenger". Routes to docs/architecture/messaging.md (umbrella) + messaging-substrate.md so the model is never re-derived or re-decided. Crypto is owned by the `crypto` skill, not here.
---

# Skill: messaging — the messenger architecture (router)

**This skill is a thin router, not a copy of the model.** The single source of truth is the umbrella [`docs/architecture/messaging.md`](../../../docs/architecture/messaging.md) and its substrate file. Read the umbrella **AI-TLDR** first (the beacon + the cutting + zone map). **Do not re-derive the model or re-decide settled questions** — they are decided and industry-verified there (TASK-148 Decision block). If this skill and `messaging.md` ever disagree, **`messaging.md` wins** — fix the skill.

## When this fires
Any work touching: messenger / chat / message / conversation / group chat, message features (reaction / reply / thread / edit / delete / forward / mention / receipt / typing / pin), group governance (roles / admin-block / kick / mute / invite-by-link), the substrate (`MessagingPort` / envelope / ordering / mailbox), the server (`DeliveryServicePort` / delivery service / fan-out / KeyPackage directory), calls (Jitsi / LiveKit / SFU / WebRTC / SFrame / voice / video), media/gallery (`MediaPort` / album / blob transfer), push routing; or the libraries/systems in this space (Matrix / matrix-rust-sdk / Olm / Megolm / MLS messenger / Phoenix / phnx / Wire / Kalium / MIMI); or "how should our messenger work / build vs buy".

## The adopted approach in one line (the beacon)
**One MLS substrate → opaque payload to opaque recipient tokens through a blind-courier server ("one pipe, three faucets": chat / gallery / config-sync).** The transport (Matrix maybe-MVP → MLS target) sits behind `MessagingPort`; the server (Cloudflare stopgap → own Rust) behind `DeliveryServicePort`; calls behind `CallPort` (Jitsi). **Feature taxonomy lives in the DOMAIN; adapters only marshal.** Crypto (MLS/openmls) is owned by `crypto.md`, consumed here. Precedent: RFC 9750 (AS/DS split), Phoenix (metadata-blind MLS delivery), Signal Sesame (multi-device), MIMI (feature taxonomy + federation on MLS). It is an **intersection of established patterns copied from published specs, not an invention**. Full map + rejected list in `messaging.md`.

## Guardrail invariants (never violate; authoritative list = messaging.md §Invariants + messaging-substrate.md)
1. **Feature taxonomy lives in the domain, adapters only marshal** (INV-M1 / S2 — the anti-rewrite cut). A reaction/reply/edit is OUR domain type; the Matrix/MLS adapter translates. Never define a feature *as* a vendor event type inside an adapter.
2. **One pipe, three faucets; big blobs never ride the group ratchet** (INV-M2 / S5). Media via `MediaPort` + a pointer message; never a 5 MB photo through MLS.
3. **The server is a blind courier** (INV-M3 / S3, rule 13): opaque group id + integer epoch + opaque mailbox tokens; zero crypto/content/graph. App messages loose-ordered (client sorts); only Commits serialized one-per-epoch.
4. **Crypto is NOT re-decided here** (INV-M4). MLS/keys/pairing/KeyPackage → `crypto.md` (skill `crypto`). Messaging consumes `GroupPort`/`CryptoPort`/`KeyPackagePort`.
5. **Volatile choices behind ports with rule-8 exit ramps** (INV-M5). Transport/server/calls/media/push = adapter swaps, inline `TODO(exit-ramp)` / `TODO(server-roadmap)`.

## Build-vs-buy discipline (the headline truth)
There is **no per-feature SDK marketplace**. Reusable assets are only two piles: **(a) infra libs** (openmls MIT, axum/tokio/sqlx, Opus/VP8/VP9, Jitsi/LiveKit Apache-2.0) and **(b) published spec-taxonomies** you copy clean-room (Matrix event relations, IETF MIMI). Message features + group governance are **thin app-logic you write** from the copied taxonomy — 🟡. Heavy 🔴 items: calls, the delivery server, multi-device. Copy the *design* from published specs (RFC 9750 + Phoenix docs + Signal + MIMI); **never read AGPL code** (Phoenix/Wire) to reimplement.

## Not-built zones — STOP, do not improvise
The whole domain is **designed, not built** (0 code; messenger = TASK-42, m-4). For any zone marked *stub* in the umbrella (feature taxonomy, delivery service, calls, media): the contract is the owning task's `### Decision` block + the copied published spec, not invented prose. If asked to design/implement, surface TASK-42 (+ TASK-110 for media) and copy from the named blueprint — do not invent an answer.

## Hard sync rule
If you change the substrate model, an invariant, a port, or a zone boundary, **update `messaging.md` / `messaging-substrate.md` in the same commit**. Never leave the SoT behind — it is what the ecosystem reads.

## Reading map (jump straight to the file)
- Routine question / the cutting / build-vs-buy → `messaging.md` AI-TLDR (zone map), stop there.
- Substrate: `MessagingPort` / envelope / ordering / mailbox → [`messaging-substrate.md`](../../../docs/architecture/messaging-substrate.md).
- A message feature (reaction/reply/edit/role/block) → `messaging.md` §build-vs-buy → copy Matrix/MIMI taxonomy, put the type in the DOMAIN (INV-M1).
- Server / delivery / KeyPackage directory → [`server.md`](../../../docs/architecture/server.md) + keep it a blind courier (INV-M3).
- Calls → `messaging.md` calls stub → Jitsi + SFrame/MLS (TASK-42).
- Media / gallery → `messaging.md` media stub → `MediaPort`, pointers not blobs (TASK-110/TASK-42).
- Anything crypto (MLS/keys/pairing/KeyPackage) → **STOP** → skill `crypto` / [`crypto.md`](../../../docs/architecture/crypto.md).
- Versioning → [`wire-format.md`](../../../docs/architecture/wire-format.md) (skill `wire-format`).
