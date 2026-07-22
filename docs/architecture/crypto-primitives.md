# Crypto primitives (`:core:crypto` / `family.crypto`)

**This file is the single source of truth for the cryptographic *primitive* layer.** If it and any other doc disagree on primitives, this file wins — except: versioning of any wire format is owned by [`wire-format.md`](wire-format.md), and the umbrella routing/inventory is [`crypto.md`](crypto.md). Change the model → update this file in the same commit (the `crypto` skill is a thin router, never a second copy).

<!-- AI-TLDR:BEGIN — READ THIS FIRST. If you can answer from this block, STOP. -->

## AI TL;DR

**THE BEACON (do NOT re-decide)**: primitives are **libsodium** (via [ionspin KMP](https://github.com/ionspin/kotlin-multiplatform-libsodium), pinned `0.9.5`), wrapped behind small Kotlin ports in `:core:crypto` (`family.crypto` namespace). A primitive **takes key bytes and returns bytes** — it never learns whose key it is, what the key is *for*, how long it lives, or how it is stored. That knowledge lives one layer up ([`crypto-key-hierarchy.md`](crypto-key-hierarchy.md)). We do **not** invent crypto; where our code differs from the vendor (hand-rolled HKDF) the diff is tiny and checked byte-for-byte against the RFC.

**Zone charter**

| Owns | Must NOT own |
|---|---|
| AEAD, key-agreement, signature, KDF, CSPRNG algorithm APIs; the platform keystore as a **sibling port**; the validation set (KAT / Wycheproof / property / parity) that proves the algorithms | version fields, serialization, key *purposes* / lifecycle / rotation policy, storage paths, recipient lists, any business policy |

**Primitive ports** (all in `family.crypto.api`, built):
`AeadCipher`, `AsymmetricCrypto` (ECDH + sign + sealed-box), `KeyDerivation`, `PasswordHash`, `RandomSource`, `SecureKeyStore` (expect/actual), plus stub ports `KeyEscrow`, `KeyRotation`, `KeyBlobStore`.

**Algorithm choices (frozen)**

| Purpose | Primitive | Port / impl |
|---|---|---|
| AEAD | XChaCha20-Poly1305 IETF (24-byte nonce) | `AeadCipher` / `LibsodiumAeadCipher` |
| Key agreement | X25519 (`crypto_scalarmult`, RFC 7748) | `AsymmetricCrypto.deriveSharedSecret` |
| Signatures | Ed25519 detached (RFC 8032) | `AsymmetricCrypto.sign` / `verify` |
| Sealed-box envelope | `crypto_box_seal` / `_open` | `AsymmetricCrypto.sealForRecipient` / `openSealed` |
| KDF | HKDF-SHA256 (RFC 5869), hand-rolled over platform HMAC | `KeyDerivation` / `LibsodiumKeyDerivation` |
| Password hash | Argon2id | `PasswordHash` / `LibsodiumArgon2idPasswordHash` |
| CSPRNG | libsodium `randombytes_buf` | `RandomSource` / `LibsodiumRandomSource` |
| Key-at-rest (Android) | Android Keystore AES-256-GCM wrap, StrongBox→TEE | `SecureKeyStore` (android actual) |

**Two primitive stacks (do NOT unify)**: this file covers the **Kotlin-side libsodium** primitives serving key-hierarchy / envelope / recovery / pairing. **MLS does NOT use these** — openmls brings its own Rust-side crypto backend behind its `OpenMlsCrypto` provider trait, below the FFI bridge ([`crypto.md`](crypto.md) zone map, TASK-124). Two primitive stacks across an FFI border is the industry-standard shape (Wire core-crypto). Writing a custom `OpenMlsCrypto` backend on libsodium to "unify" them is Rejected — high-risk crypto work, no benefit.

**Invariants** (P1–P4, do NOT re-derive — see §Invariants): P1 bytes-in-bytes-out. P2 keystore is a sibling port, never fused into the algorithm API. P3 no version/serialization here (rule 1 crypto exception + TASK-141). P4 typed keys — signing ≠ agreement keys are distinct types.

**Rejected**: SGX; own ECDH; own AEAD/KDF construction. See §Rejected.

**Status**: BUILT (TASK-2, TASK-51, TASK-56 Done). iOS adapters are stub-screamers → V-1 (TASK-26).

**Routing**: primitive question → stay here. Key purpose / envelope / recovery → [`crypto-key-hierarchy.md`](crypto-key-hierarchy.md). Pairing → [`crypto-pairing.md`](crypto-pairing.md). Versioning → [`wire-format.md`](wire-format.md). MLS → [`crypto.md`](crypto.md) zone map.

<!-- AI-TLDR:END -->

## Invariants (decided — do NOT re-derive; changing one is a `decision-supersedes` task)

- **P1 — bytes in, bytes out.** A primitive receives raw key material and data as bytes and returns bytes. It must not know the key's purpose, owner, lifetime, or storage location. Symptom of violation: a primitive that references a purpose string, a rotation schedule, or a file path. (NIST SP 800-57 vs 800-131A separation; libsodium's own stop-line.)
- **P2 — keystore is a sibling port, never fused with the algorithm API.** `SecureKeyStore` (hardware-backed key-at-rest) is a distinct port from `AeadCipher`/`AsymmetricCrypto`. A key living in the TEE is a *key-management placement*, not an algorithm. (Apple CryptoKit models `SecureEnclave` as a separate namespace, not inside the cipher API.)
- **P3 — no version field, no serialization in this layer.** Primitive types are plain classes: no `schemaVersion`, no `@Serializable`. Version headers and wire encoding live above, in `:core:wire` + adapter DTOs (rule 1 crypto exception, TASK-141; unanimously confirmed by age / JWE / Tink / libsodium).
- **P4 — misuse-resistant typed keys.** Signing keys and key-agreement keys are distinct Kotlin types; the API makes "sign with an agreement key" unrepresentable. (CryptoKit lesson.)

## Validation set (how we prove we did not break it)

Replaces one-off friend-review with a CI-runnable corpus (decision 2026-06-17). Six families:

- **A. RFC Known-Answer-Test vectors** — byte-for-byte against the RFC: `X25519KatTest` (RFC 7748), `Ed25519KatTest` (RFC 8032), `ChaCha20Poly1305KatTest` (RFC 8439 + xchacha draft), `HkdfKatTest` (RFC 5869), `SealedBoxRoundtripTest`. CI: `./gradlew :core:crypto:jvmTest`.
- **B. Google Wycheproof subset** — adversarial corpus, subset pinned to a Wycheproof commit SHA (bump = deliberate PR): X25519 low-order-point rejection, Ed25519 malleability, ChaCha20-Poly1305 AAD edges.
- **C. Property-based** (1000 iterations, deterministic seeds): AEAD roundtrip + tamper, ECDH symmetry, sign/verify/tamper, nonce-reuse rejection, key-id prefix.
- **D. Cross-platform byte parity** — JSON `KeyBlob` + deterministic AEAD output identical JVM ↔ Android (guarantees a config written on Android reads on iOS/desktop).
- **E. Backward-compat fixtures** — `v1-sample.json` frozen; future minors must still parse; `schemaVersion=999` throws `UnsupportedSchemaVersion`. (Wire-format discipline owned by [`wire-format.md`](wire-format.md).)
- **F. Android Keystore instrumentation** — persistence against real TEE; no-plaintext-leak on disk. Verified emulator API 34/35; physical-device pending (`TODO(physical-device)`).

## Industrial reference baseline (we are not alone on these choices)

- **XChaCha20-Poly1305** — WireGuard, age, Signal Sealed Sender, Threema, Bitwarden Send (IRTF draft-irtf-cfrg-xchacha-03).
- **X25519** — Signal, WhatsApp, WireGuard, Wire, age (RFC 7748).
- **Ed25519** — Signal Sealed Sender, age, OpenSSH, Tor, WireGuard (RFC 8032).
- **HKDF-SHA256** — TLS 1.3, Signal Double Ratchet, age, WireGuard, MLS (RFC 5869).
- **Android Keystore wrap for Curve25519** — Signal Android, Bitwarden, Threema (the Keystore has no native Curve25519, so all wrap the raw private key under a TEE-resident AES key).
- Layer-boundary grounding: Google Tink (primitives vs key management), NIST SP 800-57 Pt 1 Rev 5 (lifecycle is a separate discipline from algorithms), Apple CryptoKit (SecureEnclave separation), libsodium docs (deliberately stops at primitives).

Sources: RFC 7748 / 8032 / 8439 / 5869; https://developers.google.com/tink/key-management-overview ; https://nvlpubs.nist.gov/nistpubs/specialpublications/nist.sp.800-57pt1r5.pdf ; https://developer.apple.com/documentation/cryptokit/secureenclave ; https://doc.libsodium.org/ .

## Rejected (do not re-litigate)

- ❌ **SGX enclave** — limited consumer-Android availability, complexity unjustified.
- ❌ **Own ECDH handshake** — use vetted primitives; the pairing handshake uses `snow` (see [`crypto-pairing.md`](crypto-pairing.md)).
- ❌ **Own AEAD / KDF construction** — RFC-standard primitives only.
- ❌ **Unifying the two primitive stacks** (custom `OpenMlsCrypto` backend on libsodium) — high-risk, no benefit; MLS keeps its own Rust primitives.

## Fitness functions

- `verifyCryptoIsolation` — `:core:crypto` has zero project dependencies.
- `NoLegacyFamilyNamespaceTest` — guards the `cryptokit.* → family.*` rename (no `cryptokit.` string may reappear).
- `NoFakeCryptoInAppTest` (Konsist) + R8 strip — fakes never ship in release.

## Exit ramps

- **libsodium / ionspin** → the ports are ours; a swap is an adapter rewrite behind `AeadCipher` etc. Where a primitive is missing from ionspin's public API today (`crypto_box_seal`, first-class HKDF) we hand-roll over the platform HMAC and switch to the vendor primitive when it lands.
- **iOS** → `iosMain/SecureKeyStore.ios.kt` (Keychain) + `HmacSha256.ios.kt` (CommonCrypto); `commonMain` unchanged (rule 1). Trigger: V-1 (TASK-26).

## Related

- Umbrella + zone map: [`crypto.md`](crypto.md). Key hierarchy: [`crypto-key-hierarchy.md`](crypto-key-hierarchy.md). Pairing: [`crypto-pairing.md`](crypto-pairing.md). Versioning: [`wire-format.md`](wire-format.md). Extraction: [`extraction-policy.md`](extraction-policy.md).
- Pre-release operational checklist: [`../dev/crypto-prerelease.md`](../dev/crypto-prerelease.md).
