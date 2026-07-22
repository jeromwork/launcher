---
name: crypto
description: The ecosystem's cryptographic architecture — layered zones (primitives → key hierarchy → pairing/AS → MLS group protocol) with a two-primitive-stack split across an FFI border. Invoke whenever work touches crypto / ключи / key hierarchy / root key / HKDF / envelope / AEAD / libsodium / Argon2id / recovery / vault / passphrase / escrow / rotation / pairing / handshake / Noise / snow / MLS / openmls / TreeKEM / KeyPackage / CryptoPort / GroupPort / KeyPackagePort / KeyVault / FFI / UniFFI / crypto-ffi / SecureKeyStore / RootKeyManager / KeyRegistry / ConfigCipher2 / Ed25519 / Curve25519 / X25519, or asks "how does our crypto work". Routes to docs/architecture/crypto.md (umbrella) + per-zone files so the model is never re-derived or re-decided. Cross-app — spans the whole ecosystem, not only the launcher.
---

# Skill: crypto — the layered crypto architecture (router)

**This skill is a thin router, not a copy of the model.** The single source of truth is the umbrella [`docs/architecture/crypto.md`](../../../docs/architecture/crypto.md) and its per-zone files. Read the umbrella **AI-TLDR** first (the beacon + zone map). **Do not re-derive the model or re-decide settled questions** — they are decided and industry-verified there. If this skill and `crypto.md` ever disagree, **`crypto.md` wins** — fix the skill.

## When this fires
Any work touching: crypto primitives, ключи / key hierarchy / root key / HKDF / derived key, envelope / config encryption / ConfigCipher2, recovery / vault / passphrase / Argon2id / anti-brute-force, escrow / rotation, pairing / handshake / Noise / `snow` / QR SAS, MLS / openmls / TreeKEM / epoch / KeyPackage, `CryptoPort` / `GroupPort` / `KeyPackagePort` / `KeyVault` / `IdentityVault`, FFI / UniFFI / cargo-ndk / `crypto-ffi`, `SecureKeyStore` / `RootKeyManager` / `KeyRegistry`, Ed25519 / X25519 / AEAD — in the launcher or any ecosystem app; or "how does our crypto work / what's our approach".

## The adopted approach in one line (the beacon)
Crypto is **layers, each knowing only what it may**: primitives (bytes math, `family.crypto`, libsodium) ← key hierarchy (what keys are *for*, `family.keys`) ← protocols (pairing/AS `family.pairing`; MLS group). **Two primitive stacks coexist across the FFI border and are NOT unified**: Kotlin libsodium (built) serves key-hierarchy/envelope/recovery/pairing; **openmls carries its own Rust primitives** (`OpenMlsCrypto`) — MLS does not call our libsodium. Precedent: Tink (primitives vs key mgmt), NIST 800-57 (lifecycle ≠ algorithms), RFC 9750 (AS/DS/protocol split), Wire core-crypto (two stacks over FFI). It is an **intersection of established patterns, not an invention**. Full map + rejected list in `crypto.md`.

## Guardrail invariants (never violate; authoritative list = the zone files)
1. **Bytes in, bytes out at the primitive layer.** A primitive never knows key purpose / owner / lifetime / storage. No version field, no serialization in `family.crypto.*` / `family.keys.*` (rule 1 crypto exception, TASK-141). `crypto-primitives.md` P1–P4.
2. **Keystore is a sibling port** (`SecureKeyStore`), never fused into the algorithm API. CryptoKit precedent. `crypto-primitives.md` P2.
3. **Envelope encryption is key management**, sits on primitives, never reaches into `RootKeyManager` internally. `crypto-key-hierarchy.md` K3.
4. **Membership / authorization policy lives in the pairing/AS zone, never in the MLS core** (RFC 9750 §3.5 — access control is application-layer). `crypto-pairing.md` PR3.
5. **FFI bridges are dumb** — no crypto logic, no policy (libsignal/Wire). Two primitive stacks stay separate; do NOT write a custom `OpenMlsCrypto` backend on libsodium to "unify" them (Rejected).
6. **Do NOT conflate the two MLS groups**: device-management group (TASK-102) ≠ future messenger group (TASK-42).

## Not-built zones — the architecture is IN the file, do not improvise
For **MLS core** and **KeyPackage lifecycle** (0 code): the complete architecture lives in [`crypto-mls.md`](../../../docs/architecture/crypto-mls.md) — grounded in researched prior art (openmls/Wire/RFC 9750/Signal), not our internal decisions. Read the file, not a task. `KeyVault`/`IdentityVault` is not built (TASK-112, still not consolidated).

## Hard sync rule
If you change the model, an invariant, or a zone boundary, **update the relevant `crypto*.md` in the same commit**. Never leave the SoT behind — it is what the whole ecosystem reads.

## Reading map (jump straight to the file)
- Routine question → `crypto.md` AI-TLDR (zone map), stop there.
- Primitive / algorithm / validation → [`crypto-primitives.md`](../../../docs/architecture/crypto-primitives.md).
- Root key / HKDF / envelope / recovery vault → [`crypto-key-hierarchy.md`](../../../docs/architecture/crypto-key-hierarchy.md).
- Pairing / handshake / identity binding / revoke → [`crypto-pairing.md`](../../../docs/architecture/crypto-pairing.md) (+ TASK-102 Decision block).
- MLS / KeyPackage / FFI / keystore → [`crypto-mls.md`](../../../docs/architecture/crypto-mls.md) (self-sufficient — import openmls MIT, copy Wire structure clean-room).
- Versioning / schemaVersion → [`wire-format.md`](../../../docs/architecture/wire-format.md) (skill `wire-format`).
- Extraction to a shared module → [`extraction-policy.md`](../../../docs/architecture/extraction-policy.md).
- Server endpoints → [`server.md`](../../../docs/architecture/server.md). Pre-release / roadmap → [`crypto-prerelease.md`](../../../docs/dev/crypto-prerelease.md).
- FFI panic contract → skill [`crypto-ffi-panic-check`](../crypto-ffi-panic-check/SKILL.md).
