---
id: TASK-112
title: 'Decision: KeyVault port boundary — operation-on-vault + narrow export'
status: Verification
assignee: []
created_date: '2026-07-07'
updated_date: '2026-07-14'
labels:
  - decision
  - crypto
  - identity
  - port-design
  - phase-2
milestone: m-1
dependencies:
  - TASK-6
priority: high
ordinal: 112000
decision-supersedes: []
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

**IdentityVault** — port, отвечающий за хранение и использование `root_key` пользователя и всех производных от него ключей. Из root_key детерминистически выводятся: `identity_id = hash(root_public)`, ключи для шифрования Profile, ключ для MLS signature, ключ для recovery blob, ключ для каждого будущего purpose.

**Вопрос этого task'а — один-единственный**: **где проходит граница port'а**. Что port возвращает наружу, а что делает внутри себя. Это архитектурный one-way door (rule 3): если сейчас отдать naive shape, потом придётся переписывать **все** call site'ы во всех модулях.

**Что этот task НЕ решает** (уже владеется другими task'ами):
- Cross-app root key sharing между разными Android package'ами → **[TASK-25](task-25%20-%20Multi-app-Cohabitation-Chain-of-trust-Recovery.md)** Multi-app Cohabitation.
- iOS platform adapter → **[TASK-26](task-26%20-%20iOS-Admin-Preset.md)** iOS Admin Preset.
- Android TV form factor + pairing → **[TASK-29](task-29%20-%20Android-TV-Preset.md)** Android TV Preset.
- Root key hierarchy + generate/recover flow → **[TASK-6](task-6%20-%20F-5-Root-Key-Hierarchy-Owner-Recovery.md)** F-5 Root Key Hierarchy (Verification, готов).
- Passphrase unlock UX и biometric — **не архитектура**, а optional UX-adapter'ы. Vault port сам не знает про биометрию.

TASK-112 определяет только **shape port'а**, чтобы все перечисленные task'и могли строить platform-adapter'ы **без изменения domain**.

## Зачем

Сегодня в коде `core/keys/api/` уже есть частичные port'ы:
- `KeyRegistry.derive(stableId, purpose) → DerivedKey` — derivation.
- `RecoveryKeyBackup` — backup blob.
- `RootKey(val bytes: ByteArray)` — публичный wrapper.

Проблемы для будущих platform-adapter'ов:
1. **`RootKey.bytes` публичный** — legitimate call site может случайно (или намеренно) вытащить raw material в logs / внешний код / IPC. Rule 1 (domain isolation) — трещина.
2. **AEAD шифрование сегодня живёт в domain** — domain берёт `DerivedKey.bytes`, зовёт AEAD напрямую. Значит при переезде на Rust vault-адаптер (via UniFFI) или iOS Swift-адаптер — переписывать ВСЕ call site'ы шифрования.
3. **Нет operation-on-vault методов** — нет `vault.aeadSeal(purpose, plaintext)`. Значит нет пути изолировать key material в отдельном процессе / в Secure Enclave / в HSM позже.

Правильная граница сегодня = **дешёвый adapter swap завтра**. Неправильная = переписывание domain на каждой новой платформе.

## Что входит технически (для AI-агента)

**Что решает TASK-112:**
1. **Shape port'а `IdentityVault`** — сигнатуры методов, DTO-newtype'ы, exception hatches.
2. **Что убрать из public API** — какие поля `RootKey`, `DerivedKey`, `KeyRegistry` становятся `internal` / удаляются.
3. **Что port делает сам** vs что отдаёт наружу как bytes.
4. **Wire-format DTO** для крипто-объектов, пересекающих port boundary (Ciphertext, Signature, DerivedKeyBytes).

**Research context** (см. Discussion Session 1):

Индустриальные libs делят port'ы на три shape'а:
- **Opaque handle** (Tink `KeysetHandle`, matrix `Account`) — не проходит через FFI, отпадает для KMP + UniFFI.
- **Operation-on-vault** (HashiCorp Vault Transit, AWS KMS Sign/Encrypt, age Identity, Bitwarden SDK) — bytes in / bytes out, FFI-friendly.
- **Typed DTO derived key bytes** (libsignal ProtocolStore, OpenMLS `export_secret`) — плоские ByteArray fields, FFI-friendly.

Signal + OpenMLS сходятся на **гибриде**: default = operation-on-vault, узкое исключение = `export_secret` для случаев, когда внешняя library управляет ключом сама.

**Никто** не отдаёт fixed DTO `{ signingKey, encryptionKey, macKey }` — purpose'ов слишком много, они меняются.

**Draft target shape (обсуждается в Discussion, финализируется в Decision block):**

```kotlin
interface IdentityVault {
    // Operation-on-vault: 90% случаев. Vault сам derive'ит DerivedKey internal'но, сам AEAD.
    suspend fun aeadSeal(purpose: PurposeId, plaintext: ByteArray, aad: ByteArray): Outcome<Ciphertext, VaultError>
    suspend fun aeadOpen(purpose: PurposeId, ciphertext: Ciphertext, aad: ByteArray): Outcome<ByteArray, VaultError>
    suspend fun mac(purpose: PurposeId, message: ByteArray): Outcome<Mac, VaultError>

    // Narrow export hatch: для внешних libs (openmls MLS signature key, snow Noise handshake),
    // которые управляют raw material сами. Purpose whitelist enforced.
    suspend fun exportDerivedKey(purpose: PurposeId, context: ByteArray, length: Int): Outcome<DerivedKeyBytes, VaultError>
}

// Newtype-ы, tagged purpose'ом. Все с schemaVersion где выходят на wire (rule 5).
value class PurposeId(val id: String)  // whitelist: "config", "contacts", "media", "mls-signature", "noise-static", ...
class Ciphertext(val bytes: ByteArray, val purpose: PurposeId, val schemaVersion: Int)
class Mac(val bytes: ByteArray, val purpose: PurposeId)
class DerivedKeyBytes(val bytes: ByteArray, val purpose: PurposeId) : AutoCloseable {
    override fun close() { bytes.fill(0) }
}

// RootKey — internal only, никогда не пересекает port boundary.
internal class RootKey(internal val bytes: ByteArray)
```

**Migration plan (после Decision):**
- Существующий `KeyRegistry.derive(...) → DerivedKey` остаётся internal / helper внутри `IdentityVault` impl. Public API уходит в `IdentityVault`.
- Существующие call site'ы `ConfigCipher2`, `EnvelopeStorage` — переходят на `IdentityVault.aeadSeal / aeadOpen`.
- Openmls integration (TASK-58 + Phase-3+ messenger) использует `exportDerivedKey("mls-signature", ...)` для sign key внутри openmls.
- `RootKey` — понижается до `internal class` в `family.keys.impl.*`. Публичный API `RootKey(val bytes: ByteArray)` — удаляется.

## Состояние

**Discussion — 6 сессий закрыты 2026-07-14.** Session 1 research (industry patterns) + Session 2 initial Decision (2026-07-07, superseded) + Session 3 mentor explainer + Session 4 external CryptoKit contract review + Session 5 mentor self-adversarial deliberation + Session 6 owner sign-off + openmls verification.

**Decision block REVISED 2026-07-14** (per rule 11 mutability window — implementation не начат). Ключевые архитектурные решения:
- `KeyVault` port + `RecoveryStrategy` port (pluggable — passphrase MVP, BIP39/2FA future additive).
- Purpose enum minimal: `CONFIG` + `RECOVERY_BLOB` (MLS_SIGNATURE / NOISE_STATIC removed — TASK-100 new-device-new-identity model).
- Blob header format `magic || format_version || purpose_id || key_epoch || nonce` + mandatory AAD canonical layout.
- libsodium-kmp для всей крипто-работы, Android Keystore только для `root_key` at rest.
- Cross-platform test vectors как DoD.

**Готово к implementation phase-1** через speckit pipeline (specify → plan → tasks → analyze → fresh session для impl per owner memory pattern «push spec-kit cycle → fresh session for one-way-door»).

**Downstream tasks awaiting** (unchanged в терминах зависимости, обновлено в терминах контракта):
- **[TASK-26](task-26%20-%20iOS-Admin-Preset.md)** — iOS adapter of `KeyVault` (Keychain root storage + swift-sodium ops).
- **[TASK-29](task-29%20-%20Android-TV-Preset.md)** — TV uses same Android adapter, zero port changes.
- **[TASK-115](task-115%20-%20Decision-Launcher-anchored-spoke-app-onboarding.md)** *(supersedes TASK-25)* — cross-app trust via `sealed_box`, отдельный port `FamilyAppInviter`, НЕ зависит от `KeyVault` API changes.
- **[TASK-11](task-11%20-%20Contact-Photos-Family-Album-foundation.md)**, **[TASK-27](task-27%20-%20Elderly-Friendly-Messenger-Jitsi-based.md)**, **[TASK-28](task-28%20-%20Full-Shared-Family-Album.md)** — bucket AEAD via `keyVault.aeadSeal(Purpose.CONFIG, ..., aad)`.
- **[TASK-124](task-124%20-%20F-CRYPTO-openmls-integration.md)** — openmls integration; signature keys stay internal to openmls (verified 2026-07-14). May use `KeyVault.aeadSeal` for openmls `StorageProvider` at-rest MLS group state encryption (Phase-3 decision).

**HarmonyOS NEXT / desktop / future platforms** — тот же port, новый adapter. libsodium-kmp обеспечивает determinism.

**Ветка `task-112-keyvault-port`** rebased on `main` (2026-07-14). Session 6 revision + regenerated Decision block committed на branch. Zero code changes yet. Next commit — speckit artifacts.

**Downstream tasks awaiting**:
- **[TASK-25](task-25%20-%20Multi-app-Cohabitation-Chain-of-trust-Recovery.md)** — builds ChainOfTrustVerifier поверх `IdentityVault`, ждёт port boundary.
- **[TASK-26](task-26%20-%20iOS-Admin-Preset.md)** — iOS adapter of `IdentityVault` (Keychain + Secure Enclave).
- **[TASK-29](task-29%20-%20Android-TV-Preset.md)** — TV использует тот же Android adapter `IdentityVault`, никаких изменений port'а.
- **[TASK-67](task-67%20-%20Pairing-Feature-And-Bucket.md)** — pairing использует `exportDerivedKey("noise-static", ...)` для Noise handshake material.
- **[TASK-11](task-11%20-%20Contact-Photos-Family-Album-foundation.md)**, **[TASK-27](task-27%20-%20Elderly-Friendly-Messenger-Jitsi-based.md)**, **[TASK-28](task-28%20-%20Full-Shared-Family-Album.md)** — используют `aeadSeal / aeadOpen` без изменений при HarmonyOS/iOS platform expansion.

**HarmonyOS NEXT / desktop / future platforms** — тот же port, новый adapter, ноль изменений в domain и downstream tasks. Это и есть цель.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria

<!-- AC:BEGIN -->
- [x] #1 [hand] `KeyVault` port + `Purpose` enum + sealed `VaultException` (7 variants) compile in `:core:keys commonMain` — no vendor/platform imports (`verifyKeysNoVendorImports` fitness rule wired in `:check`)
- [x] #2 [hand] JVM contract tests pass: aeadSeal→aeadOpen roundtrip, purpose-mismatch throws `WrongPurpose`, tamper-detection throws `TamperDetected`, wipe cascade → `NoRootKey` (38 tests green, `FakeKeyVault` + `KeyVaultContractTest`)
- [x] #3 [hand] `AndroidKeyVault` wired via Koin in `LauncherApplication` — `assembleMockBackendDebug` succeeds, new code injects `KeyVault` instead of `KeyRegistry`
- [x] #4 [hand] `PassphraseRecovery` deterministic: same passphrase + same `IdentityHint` → same root key across JVM test runs; wrong passphrase → `VaultException.RecoveryFailed`
- [x] #5 [hand] `@Deprecated(WARNING)` steer on legacy `KeyRegistry` + public `RootKey` constructor — compiler warns any new caller to use `KeyVault` instead
- [ ] #6 [hand] Android instrumented tests on pixel_5_api_34: `AndroidKeyVaultIntegrationTest` (unlock + seal + reopen after restart) + `CrossPlatformVectorAndroidTest` (byte-equal with JVM vectors) — `./gradlew :core:keys:connectedAndroidTest`
<!-- AC:END -->

<!-- SECTION:VERIFICATION_PENDING:BEGIN -->
### Verification Pending

PR merged 2026-07-14. Pending AC:

- **#6** `[hand]` Android instrumented tests on pixel_5_api_34 — requires AVD `pixel_5_api_34`.
  Recovery: run `./gradlew :core:keys:connectedAndroidTest` on a running emulator; confirm all tests green; update AC #6 to `[x]` → transition to Done via second `pre-pr-backlog-sync`.
<!-- SECTION:VERIFICATION_PENDING:END -->

## Discussion

<!-- SECTION:DISCUSSION:BEGIN -->

### Session 1 — Research (industry patterns for vault port boundary)

**Question**: given a vault holding root_key with derived per-purpose keys (HKDF), where should the port boundary sit? What crosses the boundary — raw bytes, opaque handles, or typed DTOs?

**Libraries examined:**

| Library | Pattern | What crosses boundary |
|---|---|---|
| **libsignal** (ProtocolStore) | Store = dumb byte-vault, signing outside | `IdentityKeyPair.serialize() → Box<[u8]>` — typed bytes |
| **matrix-rust-sdk** (CryptoStore) | Store persists, `Account.sign()` separate | Typed struct with methods |
| **age / rage** (Identity trait) | Operation-on-vault | Never returns private key; only unwrap result |
| **Bitwarden SDK** (KeyStore) | Ops inside SDK | Ciphertext in / plaintext out |
| **OpenMLS** (CryptoProvider) | Per-op service + narrow `export_secret` hatch | Hybrid |
| **HashiCorp Vault Transit** | Pure operation-on-vault | Keys physically cannot leave |
| **Google Tink** (KeysetHandle) | Opaque handle → get primitive per purpose | `Aead` / `Signer` primitive per purpose |
| **AWS KMS / GCP KMS** | Operation-on-vault; envelope pattern for offline | Sign(KeyId, msg) or wrapped DEK |
| **1Password SDK** | IPC to 1Password app process | Field values on demand |

**FFI viability** (critical for KMP + UniFFI + iOS Swift):

| Shape | Crosses FFI? | Verdict |
|---|---|---|
| Opaque handle | ❌ needs stateful handle-table on both sides | Reject |
| Operation-on-vault (`vault.sign(purpose, msg)`) | ✅ bytes in / bytes out | **Winner default** |
| Typed DTO derived key bytes | ✅ ByteArray fields | **Winner for narrow export cases** |

**Industry convergence** (libsignal + OpenMLS + matrix-rust-sdk):
- Default = operation-on-vault.
- Narrow hatch = `export_secret(purpose, context, length) → bytes` for external libs (openmls MLS signature key, external protocol keying).
- Never `{ signingKey, encryptionKey, macKey }` fixed DTO — purpose proliferation kills fixed shape.
- Newtype per crypto object, tagged with purpose + schemaVersion on wire.

**Alignment with existing `core/keys/` code**:
- Existing `KeyRegistry.derive(stableId, purpose) → DerivedKey` = close to libsignal shape (purpose-parameterized derivation with typed wrapper).
- Existing `RootKey(val bytes: ByteArray)` = **public raw bytes, violates rule 1** (leaks material across domain boundary).
- Existing `DerivedKey(val bytes: ByteArray)` = **public raw bytes**, but has `wipe()` and suppressed `toString()`.
- Existing `ConfigCipher2` = domain-side AEAD, uses `DerivedKey.bytes` directly.

**Migration cost** (draft estimate):
- Tighten `RootKey` to internal: ~1 day (only internal impl uses it).
- Introduce `IdentityVault` port with `aeadSeal / aeadOpen / mac`: ~2 days (port + Android adapter wrapping existing KeyRegistry).
- Migrate `ConfigCipher2`, `EnvelopeStorage` call sites: ~2 days.
- Introduce `exportDerivedKey` hatch: ~0.5 day (part of port).
- **Total ~1 week**, mostly mechanical, all in `core/keys/`.

### Open questions for Session 2 (Decision)

1. **Purpose whitelist enforcement** — где список валидных purpose'ов? Enum vs registry vs строковый параметр?
   - **Option a**: `enum class PurposeId { CONFIG, CONTACTS, MEDIA, MLS_SIGNATURE, NOISE_STATIC, RECOVERY, ... }` — compile-time enforcement, но каждый новый purpose = domain change.
   - **Option b**: registry-based `value class PurposeId(val id: String)` + runtime validation в vault. Extensible без domain change.
   - **Bias**: option b (registry-based) — соответствует существующему `KeyRegistry` String purpose, avoids domain churn per new module.

2. **Async vs sync методы** — `suspend fun aeadSeal(...)` или `fun aeadSeal(...)`?
   - Существующий `KeyRegistry` — `suspend`. Android Keystore ops sometimes block.
   - **Bias**: keep `suspend` (соответствие текущему коду).

3. **Ошибки** — единый `VaultError` sealed hierarchy или per-method error type?
   - Существующие `RootKeyError`, `CipherError`, `BackupError` — per-domain sealed classes.
   - **Bias**: единый `VaultError` для vault ops, cоответствие operation-on-vault паттерну.

4. **Schema version** для `Ciphertext` — где живёт (в самом Ciphertext или снаружи)?
   - **Bias**: внутри Ciphertext (`class Ciphertext(bytes, purpose, schemaVersion)`) — self-describing, rule 5.

5. **`DerivedKeyBytes` lifetime** — `AutoCloseable` + `use { }` block?
   - **Bias**: yes, explicit close pattern из age/OpenMLS.

### Session 2 — Decision (owner sign-off 2026-07-07)

Additional research delivered to owner in mentor-mode by chat covering all 5 open questions with concrete library evidence (libsignal, OpenMLS, libsodium, Tink, keyring-rs, Bitwarden EncString, Signal SignalMessage, age Secret<>, RustCrypto zeroize, UniFFI Kotlin bindings, kotlinx.coroutines, JetBrains stdlib guidance).

Additional Session 2 finding — **existing `Outcome<T, E>` sealed class is Rust-in-Kotlin-clothing** (Kotlin stdlib grain = throw + nullable pair; JetBrains discourages `kotlin.Result` for domain modeling; UniFFI Kotlin backend emits throwing methods not sealed Outcome; every referenced Kotlin crypto lib uses exceptions). `Outcome` currently used in 143 files, 358 occurrences, three parallel `Outcome.kt` definitions in `core/keys`, `core/push`, `core/`. Migration deferred to separate task (TASK-113) — not blocking TASK-112 Decision.

### Decision (English, ~~immutable 🔒~~ REVISED 2026-07-14)

> **⚠️ SUPERSEDED 2026-07-14 by Session 6 Decision block below.** Per rule 11 mutability window (implementation not started, ветка `task-112-keyvault-port` содержит только status-flip коммит), this Session 2 Decision was mutable and has been replaced. Current Decision — search for «### Decision (English, revised 2026-07-14)» at the bottom of Session 6 area. This Session 2 content preserved for revision history.

**Port name**: `KeyVault` — chosen over `IdentityVault` / `CryptoOperator` / `KeyBox` / `RootKeyBox`. Rationale: `Vault` conveys guarded storage (Azure KeyVault, HashiCorp Vault, 1Password Vault convention); `Key` scopes to root_key + all derived keys (not just root, not all crypto). Matches project port-naming convention (role, not type; no `Port` suffix — `RemoteStorage`, `ConfigSaver`, `KeyRegistry` precedent).

**Port shape (Kotlin common)**:

```kotlin
package family.keys.api

interface KeyVault {
    // Operation-on-vault: 90% cases. Vault internally derives key + performs op.
    @Throws(VaultException::class)
    fun aeadSeal(purpose: Purpose, plaintext: ByteArray, aad: ByteArray): Ciphertext

    @Throws(VaultException::class)
    fun aeadOpen(purpose: Purpose, ciphertext: Ciphertext, aad: ByteArray): ByteArray

    @Throws(VaultException::class)
    fun mac(purpose: Purpose, message: ByteArray): Mac

    // Narrow export hatch: for external Rust libs (openmls signature key,
    // snow Noise handshake material) that manage raw material themselves.
    @Throws(VaultException::class)
    fun exportDerivedKey(purpose: Purpose, context: ByteArray, length: Int): DerivedKeyBytes
}

// Q1: Enum for compile-time exhaustiveness. Real Phase 2-3 purposes = 4.
enum class Purpose {
    CONFIG,          // ConfigCipher2 AEAD (existing consumer)
    MLS_SIGNATURE,   // openmls signature key via exportDerivedKey (Phase 3+)
    NOISE_STATIC,    // snow Noise handshake static key via exportDerivedKey (TASK-67)
    RECOVERY_BLOB    // Recovery blob wrap (TASK-6 F-5, existing)
}
// External purposes (openmls internal HKDF-Expand-Label transcripts) live outside
// this enum — callers of exportDerivedKey pass raw label bytes via a separate
// Purpose.External(labelBytes: ByteArray) variant IF the enum is proved
// insufficient. Not needed today.

// Q3: Sealed exception hierarchy, grouped by nature (not by method).
// Kotlin idiomatic: try/catch on call site. Same exception class may be
// thrown from multiple methods (correct — same nature of failure).
sealed class VaultException(message: String, cause: Throwable? = null)
    : Exception(message, cause) {

    // Category: hardware / platform
    class HardwareBackedKeystoreUnavailable(cause: Throwable? = null)
        : VaultException("hardware keystore not available on this device", cause)
    class VaultLocked : VaultException("vault requires unlock (passphrase/biometric)")

    // Category: user action
    class AuthCancelled : VaultException("user cancelled biometric/passphrase prompt")

    // Category: data integrity
    class CorruptedCiphertext(cause: Throwable? = null)
        : VaultException("ciphertext MAC verification failed or malformed", cause)
    class UnsupportedSchemaVersion(val version: Int)
        : VaultException("ciphertext schemaVersion=$version not supported")

    // Category: programming error (should be caught in tests, not runtime)
    class UnknownPurpose(val purpose: String) : VaultException("purpose $purpose unknown")
}

// Q4: schemaVersion in-band (first byte of `bytes`), class exposes parsed getter.
// Survives serialization to wire (Firestore → future own-server migration transparent).
// Matches Bitwarden EncString, Signal SignalMessage, MLS RFC 9420, age, JWE convention.
class Ciphertext(val bytes: ByteArray, val purpose: Purpose) {
    val schemaVersion: Int get() = bytes[0].toInt() and 0xFF
}

class Mac(val bytes: ByteArray, val purpose: Purpose)

// Q5: Zeroize-on-close via AutoCloseable + Kotlin `use { }` block.
// Best-effort on JVM (GC may have copied buffer; documented as such — matches
// Tink #492 honesty). Prevents accidental key retention in the common case.
class DerivedKeyBytes(val bytes: ByteArray, val purpose: Purpose) : AutoCloseable {
    override fun close() { bytes.fill(0) }
    // TODO(quality-check): heap dump test in built app to verify zeroization scope
    // (e.g. via JVisualVM after `use { }` exits — check no residual key material).
}
```

**Q2 sync/async**: **Sync** default. `suspend` reserved for biometric-gated methods (none exist today). Rationale: every reference lib is sync (Tink Android, AndroidX Security-Crypto, libsignal JNI, libsodium-kmp, OpenMLS). Google Android Keystore guidance: wrap call site in `Dispatchers.IO`, do not lie via `suspend` about I/O vs UI-blocking.

**Purpose whitelist enforcement (Q1)**: enum with 4 variants covers Phase 2-3 reality (was mistakenly documented as 5+ in prior draft including "contacts"/"media" — those are stale KDoc examples, not production purposes; contacts live in Profile bucket, media transformation via TASK-110). If Phase-3+ modules exceed 8-10 purposes → revisit as `Purpose.External(labelBytes)` variant additively.

**Existing code migration (`core/keys/`)**:
- `RootKey(val bytes: ByteArray)` public class → **internal class** in `family.keys.impl.*`. External code goes through `KeyVault`.
- `KeyRegistry.derive(stableId, purpose) → DerivedKey` — **remains** as internal helper used by `KeyVault` impl. Its public API status downgraded to `internal`.
- `ConfigCipher2` — refactored to consume `KeyVault.aeadSeal / aeadOpen` instead of `DerivedKey.bytes`.
- `KeyRegistry` KDoc — remove stale `"contacts"`, `"media"` examples (they refer to Profile buckets, not vault purposes).
- **NOT touched in this task**: existing `Outcome<T, E>` return types on other ports. Those migrate via TASK-113.

**Applies to**:
- All downstream tasks awaiting: TASK-25 (multi-app cohabitation ChainOfTrustVerifier over `KeyVault`), TASK-26 (iOS Keychain adapter of `KeyVault`), TASK-29 (TV uses same Android adapter, zero port changes), TASK-67 (pairing via `exportDerivedKey(Purpose.NOISE_STATIC)`), TASK-11 / TASK-27 / TASK-28 (bucket AEAD via `aeadSeal / aeadOpen`).
- Future platforms (HarmonyOS NEXT, desktop): new adapter of `KeyVault`, zero changes to domain or downstream tasks.

**Trade-offs**:
- Enum vs registry (Q1): trades extensibility for compile-time safety. If Phase-3+ hits >10 purposes, upgrade path is additive `Purpose.External(bytes)` variant, not enum→string breaking rewrite.
- Sync vs suspend (Q2): trades ergonomics (caller wraps in `Dispatchers.IO`) for honest API (no false `suspend` on CPU-bound ops).
- Sealed exceptions vs Outcome (Q3): trades one-time island-of-exceptions (until TASK-113 migrates rest) for idiomatic Kotlin + zero-friction UniFFI Rust FFI integration.
- Inband schemaVersion (Q4): trades one byte of ciphertext for wire-safety across storage migrations (Firestore → Cloudflare KV → own PostgreSQL — all transparent).
- AutoCloseable zeroize (Q5): trades caller discipline (must use `use { }`) for best-effort protection against heap dump leakage.

**Exit ramp**:
- Enum insufficient for external-lib label bytes → additive `Purpose.External(labelBytes: ByteArray)` variant (~0.5 day). Non-breaking.
- Sync insufficient because biometric prompt required → add `@Throws(...) suspend fun signWithBiometric(...)` as separate method (~0.5 day). Non-breaking (only new method).
- Sealed exceptions cause TASK-113 refactor pain → reverse decision Post-TASK-113 by wrapping `KeyVault` throws in `Outcome` at TASK-113 boundary (~1 day). Wire format unchanged.
- Inband schemaVersion insufficient for cross-scheme evolution (e.g. algorithm bump not just version bump) → follow libsodium precedent, split port method (`aeadSealV2 / aeadSealV3`) instead of version bump (~2-3 days per algorithm).
- Zeroization insufficient (heap dump test finds leaks) → escalate to native buffer allocation (`sun.misc.Cleaner` or `MemorySegment` in newer JDK) — Phase-4+ work.

---

### Session 3 — mentor explainer (2026-07-14)

**Trigger**: владелец попросил mentor-style разбор task'а перед началом implementation. Decision (S2) закрыт технически, но owner-facing картины «что мы делаем, что физически поменяется в коде, во что это упирается» не было. Explainer нужен для informed consent — новичок не должен подписывать implementation на веру.

**Note on mutability**: Session 3 не пересматривает Decision (он sealed 🔒 per rule 11 mutability window). Только объясняет уже принятое + собирает ответы владельца на понимание, чтобы Part B (recommended implementation path) построить с учётом реальных знаний / инстинктов, не «по среднему».

---

#### 1. Что за область (30-секундная версия)

Мы разбираемся с **KeyVault** — компонентом, где живёт корневой ключ пользователя (`root_key`, 32 байта случайности, уже существует после TASK-6) и все производные от него ключи. Задача — **зафиксировать границу**, через которую другой код (шифрование конфига, MLS-мессенджер, pairing) с ключами общается. Правильная граница = ключи никогда не покидают vault в виде «сырых байтов»; неправильная — раздача сырого материала во все стороны, невозможность заменить реализацию (Android → iOS → HarmonyOS) без переписывания домена.

#### 2. Карта темы (где это в коде)

```
┌─────────────────────────────────────────────────────────────┐
│  DOMAIN (core/, чистая логика, знает только про интерфейсы) │
│  ┌──────────────┐  ┌────────────────┐  ┌─────────────────┐  │
│  │ ConfigCipher │  │ MLS Messenger  │  │ Pairing (Noise) │  │
│  │  (существует │  │  (TASK-124,    │  │  (TASK-67, буд.)│  │
│  │   уже)       │  │   будущее)     │  │                 │  │
│  └──────┬───────┘  └────────┬───────┘  └─────────┬───────┘  │
│         │                   │                     │         │
│         ▼                   ▼                     ▼         │
│  ┌──────────────────────────────────────────────────────┐   │
│  │           KeyVault (port, ИНТЕРФЕЙС)                 │   │
│  │  aeadSeal / aeadOpen / mac / exportDerivedKey        │   │
│  │  ← ГРАНИЦА, которую этот task проектирует             │   │
│  └───────────────────────────┬──────────────────────────┘   │
└──────────────────────────────┼──────────────────────────────┘
                               │
        ┌──────────────────────┴──────────────────────┐
        ▼                      ▼                      ▼
┌───────────────┐   ┌──────────────────┐   ┌──────────────────┐
│AndroidKeyVault│   │ iOSKeyVault      │   │ FakeKeyVault     │
│  (Android     │   │  (TASK-26,       │   │  (для тестов,    │
│   Keystore +  │   │   Keychain +     │   │   mock-first     │
│   KeyRegistry)│   │   Secure Enclave)│   │   per rule 6)    │
└───────────────┘   └──────────────────┘   └──────────────────┘
    Adapters — специфичные для платформы, живут ОТДЕЛЬНО от домена
```

Ключевые файлы, которые будут затронуты implementation'ом:
- `core/keys/api/KeyVault.kt` — **новый** port (interface).
- `core/keys/api/RootKey.kt` — **сейчас public**, станет `internal` (никто снаружи не должен видеть сырые байты).
- `core/keys/api/KeyRegistry.kt` — **сейчас public**, станет `internal` helper внутри реализации KeyVault.
- `core/config/ConfigCipher2.kt` — **уже есть**, будет мигрирован: вместо `keyRegistry.derive(...).bytes → aead.seal(...)` будет `keyVault.aeadSeal(Purpose.CONFIG, plaintext, aad)`.
- `core/keys/impl/AndroidKeyVault.kt` — **новый** adapter (обёртка над существующим KeyRegistry + Android Keystore).
- `core/keys/impl/FakeKeyVault.kt` — **новый** fake для тестов.

#### 3. Главное для новичка (must-know)

1. **Port ≠ implementation.** Port — интерфейс (контракт «что можно делать»); implementation — конкретный класс (Android Keystore / iOS Keychain / fake). Domain знает **только про port**. Замена implementation = ноль изменений в domain.
2. **Operation-on-vault ≠ export bytes.** Два стиля работы с ключом: (а) «дай мне ключ, я сам зашифрую» — export, ключ покидает vault; (б) «зашифруй это для меня, вот открытый текст» — operation, ключ остаётся внутри. Мы выбираем (б) как default + (а) как узкое исключение для внешних библиотек (openmls, snow), которые сами хотят работать с сырым материалом.
3. **Domain isolation = переносимость.** Если domain использует `keyVault.aeadSeal(...)` — переезд с Android на iOS = написать `iOSKeyVault` и подменить DI. Если domain использует напрямую `androidKeystore.getKey(...).encrypt(...)` — переезд = переписать весь domain. Это rule 1 CLAUDE.md.
4. **Decision уже принят.** Session 1 (research) + Session 2 (owner sign-off) закрыты. **Мы уже НЕ решаем что делать — мы решаем как имплементировать уже решённое.** Port shape, Purpose enum, ошибки, sync/async — всё зафиксировано.
5. **One-way door — потому что call site'ы.** Если сейчас сделаем «просто отдадим `DerivedKey.bytes` наружу», то через 6 месяцев в проекте будет 30+ мест, вытаскивающих bytes и шифрующих сами. Тогда переезд на iOS Keychain / Rust-vault = переписать 30 мест. Правильная граница сейчас = дешёвая замена implementation'а завтра.

#### 4. Ключевые термины

- **root_key** — 32 байта случайности, из которых детерминистически выводятся все остальные ключи пользователя. Существует уже (TASK-6 F-5). Никогда не покидает устройство в открытом виде.
- **Derived key** — ключ, полученный из `root_key` через HKDF (стандартный алгоритм key derivation) с параметром «purpose». Пример: `HKDF(root_key, purpose="config")` → 32 байта ключа для шифрования конфига. Тот же root_key + другой purpose → другой ключ. Детерминистично → воспроизводится на другом устройстве после recovery.
- **Purpose** — метка «для чего этот ключ». В нашем Decision — enum из 4: `CONFIG` (шифрование конфига), `MLS_SIGNATURE` (подпись MLS-сообщений в мессенджере), `NOISE_STATIC` (Noise handshake при pairing), `RECOVERY_BLOB` (обёртка recovery blob'а). Purpose — часть contract: тот же purpose → тот же key, всегда.
- **Port (в контексте DDD / hexagonal architecture)** — интерфейс, объявленный в domain layer, описывающий «что мы хотим уметь делать» без указания «как». `KeyVault` — port. Domain говорит с port'ом, никогда с конкретным Android/iOS API.
- **Adapter** — конкретная реализация port'а, живёт в отдельном модуле, знает про SDK/platform. `AndroidKeyVault` использует Android Keystore под капотом; iOS-версия будет использовать Keychain. Domain про них не знает.
- **Operation-on-vault** — паттерн, где vault принимает «данные + purpose», выполняет крипто-операцию внутри, возвращает результат. Ключ не покидает vault. Индустриальный стандарт (HashiCorp Vault Transit, AWS KMS, Bitwarden SDK).
- **Narrow export (`exportDerivedKey`)** — узкое исключение из operation-on-vault: vault отдаёт сырой ключ наружу, но **только** когда его требует внешняя библиотека (openmls хочет signature key внутри Rust-кода; snow хочет static key для Noise handshake). Purpose — из whitelist enum'а, не произвольная строка.
- **AEAD (Authenticated Encryption with Associated Data)** — стандартный шифро-режим, где ciphertext защищён от подмены (MAC внутри). Наши `aeadSeal / aeadOpen` — это оно. AAD (Associated Data) — данные, не шифруются, но участвуют в MAC (например, «header» blob'а, который читаем перед расшифровкой).
- **Newtype** — Kotlin-класс-обёртка над `ByteArray`, добавляющий типовую защиту («это не любые байты, это `Ciphertext` для `Purpose.CONFIG` со `schemaVersion=1`»). Компилятор ловит перепутанное (нельзя случайно `aeadOpen(mac)` — mac ≠ ciphertext).
- **Sealed exception** — Kotlin-паттерн: базовый `VaultException` + фиксированный набор наследников (`HardwareBackedKeystoreUnavailable`, `AuthCancelled`, `CorruptedCiphertext`, …). Позволяет `when(...)` быть exhaustive — компилятор проверит, что все ветки обработаны.

#### 5. Uncomfortable questions (проверка понимания)

Шесть вопросов. На каждом — inline объяснение зачем спрашиваю. **Ответь на каждый** (можно коротко, «а», «б» или своими словами) — это откроет Part B: sanity-check рисков, recommended implementation path (фазы + AC + branch strategy), 1-2 альтернативы, типичные ошибки в нашем стеке, adjacent concerns.

**Q1 — Про Purpose enum extensibility.** Decision закрепил 4 варианта: `CONFIG`, `MLS_SIGNATURE`, `NOISE_STATIC`, `RECOVERY_BLOB`. Через год мы захотим шифровать контакты (аватары, номера, private-notes). Что делаем: (а) добавляем `CONTACTS` в enum — считается ли это «breaking change» для всех callers?, (б) переиспользуем `CONFIG` — но config и contacts — разные bucket'ы, разные ключи derived, plaintext leak если ключ скомпрометирован, (в) используем `Purpose.External(labelBytes)` exit-ramp variant из Decision? *Зачем спрашиваю*: проверить, что ты понимаешь разницу между «per-bucket key isolation» и «share one key everywhere», и знаешь ли ты про exit ramp из Decision.

**Q2 — Про migration story call site'ов.** Сейчас в коде уже 3-5 мест используют `keyRegistry.derive(...).bytes` напрямую (`ConfigCipher2`, `EnvelopeStorage`, возможно ещё что-то — надо будет grep'ать). Мы их мигрируем: (а) одним PR — рискованно, много изменений сразу, зато чисто; (б) фазами — сначала port + fakes + тесты, потом Android adapter, потом мигрируем call site'ы по одному, потом downgrade RootKey в internal; (в) parallel roll — новый API рядом со старым, старый deprecated, удаляем через месяц? *Зачем спрашиваю*: показать разные trade-off'ы risk-vs-cleanness; выбор влияет на branch strategy (один PR vs 4-5).

**Q3 — Про тесты mock-first.** Правило 6 CLAUDE.md — mock-first: сначала fake adapter (`FakeKeyVault` — in-memory, никакой реальной криптографии, для проверки контракта) + тесты domain'а против fake, **потом** real Android adapter. Значит первые 3-5 дней работы = писать интерфейс + fake + тесты, которые проверяют «форму», а не реальное шифрование. Тебе комфортно, что «настоящее шифрование» появится в имплементации только на 4-5й день? *Зачем спрашиваю*: mock-first часто ощущается новичкам как «трата времени на пустое» — на самом деле это способ поймать плохую shape port'а до того, как real adapter будет написан.

**Q4 — Про backwards compatibility ciphertext'а.** Сейчас на устройствах пользователей уже лежат зашифрованные `ConfigCipher2` blob'ы (локально в EncryptedSharedPreferences / DataStore, в облаке — TASK-4 F-5b). После миграции формат ciphertext'а меняется: раньше был просто `ByteArray`, теперь `class Ciphertext(bytes, purpose)` со `schemaVersion` в первом байте. Твоё ожидание: (а) вся старая ciphertext сломается, пользователи потеряют локальный конфиг после апдейта; (б) нужен читатель старого формата → конвертер в новый (миграция при первом запуске после апдейта); (в) новый формат совместим со старым «случайно», ничего не сломается? *Зачем спрашиваю*: это wire-format break, попадает под rule 5 (schemaVersion + backward compat). Если ответ (а) — тебе комфортно с data loss? Если (б) — кто пишет converter? Если (в) — на чём основано убеждение?

**Q5 — Про multi-app (TASK-25).** TASK-25 говорит: два наших приложения на одном устройстве (например, `launcher` + `admin app`) должны разделять один и тот же `root_key`, чтобы после recovery оба одновременно восстановились. Как KeyVault это обеспечивает? (а) оба приложения имеют свою копию KeyVault, они синхронизируются через some IPC / ContentProvider; (б) один из них — «главный» vault, второй ходит в него через ContentProvider каждый раз; (в) root_key живёт в общем шифрованном хранилище (Android Keystore shared UID / `EncryptedSharedPreferences` в shared storage), оба KeyVault'а читают из него? *Зачем спрашиваю*: TASK-25 supersedes'ит выбор (`Multi-app Cohabitation` уже проговорил ChainOfTrustVerifier поверх KeyVault) — проверяю, знаешь ли ты, что там уже решено, или сейчас будешь угадывать.

**Q6 — Про one-way door reversibility.** Decision заявляет ~1 неделя миграции + exit ramps на каждый пункт (включая `sync → suspend` если понадобится). Если через 3 месяца окажется что `sync` был ошибкой (например, Android Keystore начал блокировать основной поток при определённых операциях на новых Android 16 devices), сколько будет стоить откатить? (а) переписать все call site'ы (throw everywhere → coroutines) — недели; (б) добавить `suspend` варианты рядом со sync без удаления sync — дни, не breaking; (в) обернуть sync в `withContext(Dispatchers.IO)` на call site'ах и оставить port как есть — часы, zero API change? *Зачем спрашиваю*: exit ramp из Decision заявляет вариант (б) как ~0.5 day. Хочу увидеть, догадаешься ли ты сам, что «reversibility» ≠ «отменить всё» — часто = «добавить рядом».

---

**Waiting for owner answers → then Part B (implementation path).**

---

### Session 4 — External CryptoKit contract review (2026-07-14)

**Trigger**: владелец получил внешний рекомендательный документ «CryptoKit (KeyVault) — рекомендации и контракт для реализации» (12 разделов: инварианты, порт, AAD, wrapped export, recovery через BIP39+Argon2id, ротация с epoch, blob header, гигиена памяти, platform-адаптеры, тесты, prohibited list, связь с этажом данных). Документ позиционирован как «дополнение к TASK-112», но фактически предлагает **существенное расширение** sealed Decision block'а.

**Mutability check (rule 11)**: Decision block sealed 2026-07-07, но implementation НЕ начат (zero code), ветка `task-112-keyvault-port` содержит только status-flip commit. Per rule 11 mutability window — Decision **всё ещё mutable**, revision разрешён напрямую (не через `decision-supersedes` task). При revision добавить «Revision note (2026-07-14): incorporated external CryptoKit contract review, expanded scope from port-shape-only to full crypto surface contract» в конец Session 2.

**Source alignment check** — прежде чем адаптировать:

| Recommendation | Совпадает с? | Конфликтует с? |
|---|---|---|
| §4 wrapped export via `crypto_box_seal` | ✅ TASK-115 `sealed_box(messenger_public_key, ...)` — тот же примитив X25519 sealed box | — |
| §5 BIP39 + Argon2id recovery | — | ⚠️ **TASK-6 (Done)** использует Google UID + user-chosen passphrase, НЕ BIP39. Recovery entropy низкая, полагается на Google account как second factor |
| §5.3 «PIN не механизм recovery» | — | ⚠️ TASK-6 passphrase — та же категория low-entropy secret (пользователь сам придумывает слово, ~30-40 бит) |
| §4 remove `exportDerivedKey` | ✅ openmls генерирует свой SignatureKeyPair внутри (не derives from root) — MLS_SIGNATURE purpose в текущем Decision скорее всего был ошибкой | — |
| §9 cross-platform test vectors | ⚠️ форсирует libsodium-kmp derivation, Android Keystore = только хранилище для `root_key` at rest, НЕ участник derivation. Big architectural commitment | — |
| §7 blob header (magic+version+purpose_id+epoch+nonce) | улучшение поверх текущего «schemaVersion в первом байте» | — |

---

#### Bucket A — clear wins (adopt as-is в rewritten Decision)

- **§3 AAD mandatory contents** — `namespace_id || 0x00 || schema_version || 0x00 || blob_version` canonical (или CBOR). Текущий Decision имел AAD как параметр без обязательного содержимого — leak под rollback attack.
- **§7 blob header** — `magic (2b) || format_version (1b) || purpose_id (2b) || key_epoch (2b) || nonce`. Replaces «schemaVersion в первом байте». Crypto agility + вторая линия защиты (aeadOpen валидирует что purpose в header'е совпадает с запрошенным).
- **§2 sign/verify Ed25519** — добавить в порт. Текущий Decision имел только mac/aeadSeal, sign отсутствовал. Ed25519 нужен для rule 13 (server verifies signature against namespace-recorded pubkey).
- **§4 Purpose registry с атрибутами** — `Purpose` теряет статус plain enum, становится registry с полями `algorithm`, `exportable: Boolean`, `rotation-policy`. Новый Purpose = registry change, не «ещё один enum variant».
- **§7 nonce строго от CSPRNG, никаких persistent counter'ов** — matches libsodium XChaCha20-Poly1305 recommendation. Явный prohibit в §11.
- **§11 explicit prohibited list** — формализует то, что было размазано по CLAUDE.md rules. Автотест на сигнатуры port'а (никакой `ByteArray` ключа наружу).

#### Bucket B — adopt формат сейчас, defer implementation

- **§6 rotation с epoch** — добавить `key_epoch (2b)` в blob header **сейчас** (crypto agility), НО `rotateKey(purpose)` метод в порту — не MVP. Тело метода = `TODO(rotation): Phase-3+`. Format допускает ротацию не блокируя one-way door.
- **§4 exportWrappedFor / importWrapped** — sibling app sharing. Технически совпадает с TASK-115 sealed_box handoff, НО TASK-115 живёт в `core/onboarding/FamilyAppInviter`, не в `KeyVault`. Предлагаю: `KeyVault` **не** экспортирует sibling APK key sharing напрямую — это задача TASK-115 port'а. `KeyVault` предоставляет только `sign(Purpose.SIBLING_HANDOFF, payload)` — используется TASK-115 для генерации sealed_box outside vault. Иначе `KeyVault` становится швейцарским ножом.

#### Bucket C — конфликты, требуют явного owner-решения

**C1 — §5 recovery model (BIP39+Argon2id vs Google+passphrase).**

TASK-6 Done, реализация уже на устройствах: user picks passphrase (низкая энтропия), root = HKDF(Google UID, passphrase). Recommendation говорит: недостаточно, brute-force офлайн при компрометации сервера за минуты.

Три варианта:
- **C1-a**: adopt BIP39 как Phase-3+ upgrade. TASK-6 остаётся as-is для MVP. Добавить `TASK-N-recovery-v2` в roadmap с миграционным путём (user выбирает upgrade к BIP39, старая passphrase остаётся fallback). Exit-ramp inline TODO в `RecoveryPhrase`. **Pro**: не ломаем TASK-6. **Con**: recommendation прав — passphrase weak, если сервер скомпрометируется — все пользователи под ударом. Miss chance закрыть дыру сейчас.
- **C1-b**: reject BIP39. Argue: Google account 2FA = наш second factor. Passphrase + Google UID + Google-side rate limit + account lockout = достаточно. **Pro**: TASK-6 не трогаем, UX не меняется (senior users не запомнят 12 слов). **Con**: полагаемся на Google as trust anchor — что и так уже так (TASK-6 assumption), но это **не** protection от server compromise.
- **C1-c**: rewrite TASK-6 сейчас — expensive, TASK-6 Done, есть реализация.

**Мой bias — C1-a**: BIP39 как opt-in upgrade для user'ов «я хочу максимальную безопасность» (не default для senior'ов, у которых Google-аккаунт покрывает 95% сценариев). RecoveryPhrase newtype в KeyVault поддерживает оба path'а (`fun deriveRootFromPassphrase(uid, passphrase)` + `fun deriveRootFromBip39(phrase)`).

**C2 — §4 remove `exportDerivedKey` (breaking change vs current Decision).**

Текущий Decision имел `exportDerivedKey(purpose, context, length): DerivedKeyBytes` для openmls MLS_SIGNATURE + snow NOISE_STATIC. Recommendation говорит удалить.

Проверил: **openmls generates its own SignatureKeyPair internally** (`SignatureKeyPair::new(ciphersuite)` — random from CSPRNG, stored в KeyPackageStore). НЕ derives from root_key. MLS_SIGNATURE purpose в текущем Decision — **скорее всего архитектурная ошибка** (я предполагал что openmls принимает external signing key, это неверно).

Про Noise (TASK-67 pairing): текущее наше pairing (TASK-115 chain-of-trust) использует sealed_box, не Noise handshake. Так что NOISE_STATIC purpose тоже спорный.

**Мой bias — adopt removal**: убрать `exportDerivedKey` из порта. Уменьшить Purpose enum до 2 variants (`CONFIG`, `RECOVERY_BLOB`), обосновать в Decision почему MLS_SIGNATURE / NOISE_STATIC были ошибкой. Экзит-ramp: если через 6 месяцев понадобится external-lib раскисло с сырым ключом → additive variant `Purpose.External(labelBytes)` (уже был в текущих exit ramps).

**C3 — §9 cross-platform test vectors force libsodium-kmp derivation.**

Recommendation: «зафиксированный root + входы → ожидаемые шифртексты; вектор обязан сходиться на Android/iOS/Fake». Это форсирует что **вся крипто-работа происходит в libsodium-kmp software layer**, Android Keystore участвует только как encrypted storage for `root_key` at rest (не в derive/seal/open).

**Trade-off**:
- **Pro**: cross-platform determinism. iOS/HarmonyOS адаптер идентичен по семантике. Тесты фиксируют вектора один раз.
- **Con**: не используем StrongBox / Secure Enclave hardware crypto for anything кроме root_key storage. Android Keystore hardware-accelerated AES-GCM не задействован. StrongBox attestation невозможен (потому что derived key живёт в software).

**Мой bias — adopt**: cross-platform determinism важнее hardware crypto acceleration на этом этапе. У нас нет security requirement «AES только в hardware». Recovery scenario требует что мы можем **воспроизвести root на другом устройстве** — что математически несовместимо с «root никогда не покидает StrongBox hardware». Recommendation правильно ставит границу: **storage = hardware; derivation/seal/open = software (libsodium-kmp)**.

---

#### Recommended path forward

1. **Owner sign-off по C1 / C2 / C3** (три yes/no + BIP39 = opt-in или mandatory).
2. Regenerate Decision block в task-112 (mutability window применяется, per rule 11 revision protocol). Session 2 «signed off» останется в истории; новый Decision block заменит текущий с Revision note.
3. Session 3 questions Q1-Q6 остаются валидны — часть ответов уже покрыта bucket A/B/C выше (Q1 Purpose extensibility → registry pattern из §4; Q4 backwards compat → blob header §7 с format_version=1 первый релиз, converter не нужен если старых ciphertext'ов ещё нет в проде; Q5 multi-app → TASK-115 sealed_box).
4. После regenerated Decision — implementation phasing:
   - phase-1: `KeyVault` interface + `Purpose` registry + `Ciphertext`/`Mac`/`Signature` newtypes + `FakeKeyVault` + contract tests + cross-platform test vector fixtures.
   - phase-2: `AndroidKeyVault` adapter (Android Keystore для root storage, libsodium-kmp для derive/seal/open).
   - phase-3: миграция `ConfigCipher2` / `EnvelopeStorage` call sites.
   - phase-4: downgrade `RootKey` / `KeyRegistry` в internal.
   - phase-5: cleanup + PR.

**Waiting for owner C1/C2/C3 sign-off → then rewrite Decision block.**

---

### Session 5 — mentor deliberation on C1/C2/C3 (2026-07-14)

**Trigger**: владелец invoked mentor skill после того как Session 4 представила три архитектурные развилки с моими biases (C1-a / C2-adopt / C3-adopt). Владелец — новичок в crypto trade-offs; предыдущий Session 4 быстро сформировался за 20 мин анализа. Есть шанс что мои biases пропустили attack scenarios / переоценили exit ramps / недооценили UX-cost.

**Self-adversarial disclosure**: Session 5 = я критикую **свои же** Session 4 биасы. Не потому что рекомендация плохая, а потому что «первый ответ AI на one-way door decision» = типичный failure mode (rule 3 CLAUDE.md: «slow down, alternatives, exit ramps»). Session 5 замедляется.

---

#### Attack scenario map — что каждая развилка закрывает / оставляет открытым

| Attack scenario | Кто | C1 recovery | C2 exportDerivedKey | C3 libsodium-only |
|---|---|---|---|---|
| Server dump (Firestore leak) → offline brute-force | remote attacker | **критично** | — | — |
| Device theft, залокан, аппаратная атака | форензик-лаборатория | — | — | **касается** (StrongBox) |
| Malicious sibling app scanning IPC | вредонос на устройстве | — | **касается** (bounded blast) | — |
| openmls buffer overrun exploit | атакующий через lib bug | — | касается | касается (Keystore = process boundary) |
| Rate-limited attempts via Google Sign-In | атакующий имеет Google email+pw | **C1-b полагается на это** | — | — |
| Cold-boot / DRAM residual | физический доступ к устройству | — | — | касается (softare keys в heap) |

**Ключевое**: C1-b + C3 = **два ослабления одновременно** (полагаемся на Google + меньше hardware protection). C1-a + C3 частично компенсирует **если** BIP39 opt-in реально enable'ится (см. Q1). Без реального adoption'а — C1-a ≈ C1-b по effective security.

---

#### Что физически на кону — три главных факта, которые владелец должен принять или отвергнуть

1. **Password entropy — арифметика, не opinion.** `"Люба1948"` = ~28 bit. WhatsApp E2E Backup 2021 case study: при compromise'е зашифрованного backup blob'а с пользователь-выбранным паролем → 40% пользователей crack'нулись за 8 часов на одной GPU (top-10000 паролей + локальные данные). Наш family segment с паролями «имя+год_рождения» — **та же категория**. Если сервер утечёт с Argon2id(passphrase, salt) — крекнется первая волна.
2. **BIP39 в senior context — real UX cost.** 12 слов = 5 мин диктовать, senior не поймёт «зачем». Retention drop 30-50% при mandatory adoption реалистичен (research based). C1-a «opt-in Phase-3+» звучит хорошо, но **security features которые никто не enable'ит = zero improvement**. Мой bias C1-a может быть theater.
3. **StrongBox — маркетинговый термин без ratchet.** «Hardware-backed encryption» звучит серьёзно; на деле защищает `root_key` at rest от chip-level attacks. НЕ protects: rooted device, unlocked device forensics, derived keys в heap. Если наш threat model = «вор нашёл телефон» — Keystore обычный достаточен. Если threat = «Cellebrite / GrayKey лаборатория» — public reports показывают что даже StrongBox преодолевается. C3 tradeoff может быть меньше чем кажется.

---

#### Terms используемых в вопросах

- **Offline brute-force** — атакующий имеет encrypted blob, локально пробует все пароли без обращения к серверу. Server rate-limit **не защищает**.
- **KDF material** — данные которые нужны чтобы прогонять candidate passwords: salt + Argon2id parameters. Если salt на сервере → атакующий имеет KDF material. Если salt только на устройстве → нет.
- **Retention drop** — % пользователей теряющих на этапе onboarding при добавлении friction. Мы не имеем прямых данных, но industry benchmarks для «2FA mandatory» = 20-40% drop для B2C.
- **Compliance model** — HIPAA (medical US), GDPR (EU), MDR (medical device EU). Требуют FIPS-validated crypto primitives, hardware attestation, audit trail. Наш `[LOW] TASK-34 clinic B2B` в parking-lot намекает на потенциальную необходимость через 12-18 мес.

---

#### Uncomfortable questions — targeted, не opinion-based

Каждый вопрос имеет **правильный ответ по существу** (не «что нравится») и **уточняет один конкретный аспект** C1/C2/C3. Отвечать можно коротко.

**Q1 (C1) — retention math.** Реалистичный сценарий: если BIP39 mandatory на onboarding → drop 30-50%. Какая цифра для тебя приемлема? (a) 0-5% — тогда BIP39 mandatory невозможно, C1-b безальтернативно; (b) 5-20% — BIP39 mandatory только для «power user» варианта preset'а (clinic / self-managed), не family; (c) 20-50% — BIP39 mandatory для всех, family с «сложной защитой» = наш differentiator vs конкуренты. *Без этой цифры C1 нельзя решить арифметически.*

**Q2 (C1) — server-side KDF material reality.** В нашей текущей TASK-6 реализации: Argon2id salt хранится **на сервере** (в Firestore user doc) или **только на устройстве** (EncryptedSharedPreferences)? Если на сервере → C1-b **опасен** (Firestore dump = offline brute-force enabled). Если только на устройстве → C1-b **приемлем** (нужен и dump + физический доступ к устройству). *Знаешь ли ты факт или нужно грепнуть код перед решением?*

**Q3 (C2) — verification cost of openmls claim.** Мой C2 bias («openmls generates SignatureKeyPair internally, external key useless») основан на documentation reading, НЕ на source-code проверке. Я могу ошибаться. Прежде чем локать C2 в Decision — хочешь я потрачу 30 мин на github.com/openmls/openmls Rust code check (что реально принимает `KeyPackageStore` API)? Или доверяем docs-based уверенности? *Cost эрора: если C2 неверен, add-back — не additive, а port-breaking change.*

**Q4 (C3) — threat model owner-facing.** Кого мы защищаем **сейчас** (Phase 1-2)? (a) family segment (senior от phishing / spam / случайной утечки) → hardware не критично, C3 adopt fine; (b) family + clinic B2B в течение 12 мес → StrongBox attestation становится компаунсу-требованием, C3 = блокер; (c) B2B enterprise / medical device в Phase-3+ → уже нужен FIPS crypto, StrongBox, audit log. *Твой ответ по (a/b/c) определяет C3 direction.*

**Q5 (мета) — compliance timeline realistic?** TASK-34 (clinic B2B) сидит в Phase-5 parking-lot. **Реалистично**: он останется parking-lot ещё 12-18 мес или ты видишь путь для clinic pilot в Phase-3? Крипто-архитектура сегодня закладывается на 24+ мес — если clinic realistic в Phase-3, C3 direction меняется от «libsodium software» к «hybrid Keystore + libsodium с attestation path». *Если Phase-5 parking-lot = 24+ мес → C3 adopt безопасно. Если Phase-3 realistic → нет.*

---

**Waiting for owner Q1-Q5 answers → Part B (sanity-check + regenerated Decision block draft + phase-1 tasks.md skeleton).**

Meta: если хочешь короткий путь — отвечай только Q1, Q2, Q4 (три главных). Q3 и Q5 можно закрыть моими defaults (Q3 = «проверю openmls source перед commit'ом», Q5 = «Phase-5 parking-lot остаётся 24+ мес»).

---

### Session 6 — Owner decisions + openmls verification (2026-07-14)

**Trigger**: owner ответил на C1/C2/C3 после Session 4/5 mentor-разбора. Плюс запросил verification openmls source перед lock'ом C2 (30 min research).

#### Owner decisions

**C1 — Recovery model**: **passphrase-based (TASK-6 as-is), НО архитектурно съёмный**. Owner refactored my A/B/C options into a stronger architectural framing: recovery mechanism is a **replaceable component**, not hardcoded. Today = passphrase (matches TASK-6). Tomorrow = 12-word mnemonic (BIP39) / SMS 2FA / hardware key / social recovery — plug in as `RecoveryStrategy` adapter without touching `KeyVault`.

Architectural insight: `KeyVault` agnostic to how root is unlocked. Introduces new port `RecoveryStrategy`:
```kotlin
interface RecoveryStrategy {
    fun deriveRoot(): RootKey  // internal-facing
}
class PassphraseRecovery(passphrase, salt, kdfParams) : RecoveryStrategy  // MVP, matches TASK-6
class Bip39Recovery(words) : RecoveryStrategy                             // Phase-3+ opt-in
class TwoFactorRecovery(smsCode, backupKey) : RecoveryStrategy            // future
```

`KeyVault.unlock(strategy: RecoveryStrategy)` — vault не знает конкретики recovery. Accepted risk: passphrase уязвим к offline brute-force при server compromise (per Session 5 attack scenario map). Exit ramp: add BIP39 strategy adapter without breaking existing PassphraseRecovery callers.

**C2 — Remove `exportDerivedKey`**: adopted.

**C3 — libsodium-only derivation** (Keystore только для `root_key` at rest): adopted.

Threat model (implicit from C3-A pick): **family segment only for MVP + next 12-18 месяцев**. Clinic/B2B (TASK-34) остаётся Phase-5 parking-lot 24+ мес per default. Compliance path (FIPS, StrongBox attestation) — не в scope сейчас.

#### openmls verification (2026-07-14 research)

**Задача**: проверить моё утверждение из Session 4 «openmls генерирует SignatureKeyPair сам, external key useless» перед тем как удалять `exportDerivedKey` из port'а.

**Sources checked**:
- [`openmls_basic_credential::SignatureKeyPair` docs.rs](https://docs.rs/openmls_basic_credential/latest/openmls_basic_credential/struct.SignatureKeyPair.html)
- [openmls `key_packages` module](https://docs.rs/openmls/latest/openmls/key_packages/index.html)
- [OpenMLS Book — Traits](https://book.openmls.tech/traits/traits.html)

**Finding**: моё Session 4 утверждение **фактически неверно**. `SignatureKeyPair::from_raw(signature_scheme, private, public)` — legitimate constructor accepting arbitrary external private/public bytes. openmls **умеет** принимать signature key derived снаружи (через HKDF от `root_key`, например).

Значит my C2 rationale в Session 4 был wrong reasoning с правильным conclusion. Correct rationale:

**Revised C2 rationale**: `exportDerivedKey(Purpose.MLS_SIGNATURE)` **удаляется не потому что** openmls не умеет принимать external, **а потому что** [TASK-100 «History backup strategy for MVP»](task-100%20-%20Decision-History-backup-strategy-for-MVP.md) уже зафиксировал Signal-style модель: **новое устройство после recovery = новая MLS-идентичность в группах**. Пользователь заново присоединяется к группам (Signal precedent, user expectation). История MLS-чатов **не переносится**.

Consequence:
- MLS signature key **не должен** переживать recovery (иначе противоречит TASK-100 design).
- openmls генерирует SignatureKeyPair internally через `SignatureKeyPair::new(scheme)` — правильный path для MVP.
- `Purpose.MLS_SIGNATURE` в `exportDerivedKey` был архитектурной ошибкой Session 2.
- `Purpose.NOISE_STATIC` тоже удаляется — [TASK-115](task-115%20-%20Decision-Launcher-anchored-spoke-app-onboarding.md) pairing использует `sealed_box`, не Noise handshake.

**Exit ramp — реальный, не теоретический**: если в Phase-3+ решим сохранять MLS device identity across devices (opt-in feature «keep my group history on new device»), путь:
1. Add `Purpose.External(labelBytes: ByteArray)` variant (~0.5 day, additive).
2. Re-add `exportDerivedKey` method to `KeyVault` port (~0.5 day, additive method, non-breaking для CONFIG/RECOVERY_BLOB callers).
3. Client code derives signature key via `keyVault.exportDerivedKey(Purpose.External(mlsLabel), ...)`.
4. Pass raw bytes to openmls via `SignatureKeyPair::from_raw(scheme, priv, pub)` (openmls API confirmed).
5. Migration recovery flow: пользователь opt-in'ится → post-recovery vault derives signature key deterministically → new device имеет same MLS identity.
Total add-back cost: 2-3 days, non-breaking для existing CONFIG/RECOVERY_BLOB call sites.

**Verdict for C2**: adopt removal. Conclusion stands, rationale is now grounded in TASK-100 design (not incorrect openmls claim).

#### Next steps

1. Regenerate Decision block (below) replacing 2026-07-07 sealed content with new architecture. Session 2 preserved historically as «Superseded 2026-07-14 by Session 6» note.
2. Update `## Состояние` section in SECTION:DESCRIPTION with current status.
3. Commit task file changes on `task-112-keyvault-port` branch.
4. Run `/speckit.specify` → `specs/task-112-keyvault-port/spec.md` (Russian, owner-facing).
5. Skip `/speckit.clarify` and `/speckit.scenarios` (Decision definitive, refactor mechanical).
6. Run `/speckit.plan` → constitution check + phase decomposition.
7. Run `/speckit.tasks` → 5-phase tick-sync checklist.
8. Run `/speckit.analyze` → pre-impl audit.
9. Commit spec artifacts + push → **STOP этой сессии** per owner memory pattern для one-way-door features.
10. Fresh session для phase-1 implementation.

---

### Decision (English, revised 2026-07-14) 🔒

**Mutability**: sealed at Session 6 owner sign-off (2026-07-14). Implementation not yet begun — this block remains mutable until first implementation commit references it (per rule 11). After that, immutable; changes require `decision-supersedes` task.

**Port name**: `KeyVault` (unchanged from Session 2).

**Port shape (Kotlin common, `com.launcher.core.keys.api`)**:

*Package naming — Session 7 F3 alignment*: implementation uses `com.launcher.core.keys.*` matching existing `:core:*` module convention (`com.launcher.core.crypto`, `com.launcher.core.cloud`, `com.launcher.core.push`). Prose snippet below shows short `keys.api` for readability; actual code uses fully-qualified `com.launcher.core.keys.api.*`.

```kotlin
// Purpose registry — attributes per purpose (not raw enum)
enum class Purpose(
    val algorithm: Algorithm,
    val exportable: Boolean,     // false for all MVP purposes
    val rotationPolicy: RotationPolicy
) {
    CONFIG(Algorithm.XChaCha20Poly1305, exportable = false, RotationPolicy.LazyOnDemand),
    RECOVERY_BLOB(Algorithm.XChaCha20Poly1305, exportable = false, RotationPolicy.Manual);
    // Exit ramp: additive sealed class migration to Purpose.External(labelBytes)
    // if Phase-3+ requires external-lib key material (openmls SignatureKeyPair::from_raw etc).
}
enum class Algorithm { XChaCha20Poly1305, Ed25519, Blake2bMac, Argon2id }
enum class RotationPolicy { LazyOnDemand, Manual, PerEpoch }

// Newtypes crossing port boundary
value class Aad(val bytes: ByteArray)  // canonical layout — see AAD contents below
class Ciphertext(val bytes: ByteArray, val purpose: Purpose) {
    // Blob header: magic(2) || format_version(1) || purpose_id(2) || key_epoch(2) || nonce(24) || payload || tag(16)
    val formatVersion: Int
    val purposeId: Int
    val keyEpoch: Int
}
class MacTag(val bytes: ByteArray, val purpose: Purpose)
class Signature(val bytes: ByteArray)         // Ed25519, identity-scoped
class PublicIdentity(val bytes: ByteArray)    // 32-byte Ed25519 pubkey, safe to expose

interface KeyVault {
    // Bootstrap: initialize vault by unlocking root via RecoveryStrategy adapter
    @Throws(VaultException::class) fun unlock(strategy: RecoveryStrategy)

    // Lifecycle: cascade-wipe on logout (Session 7 Q-C, C1 decision)
    // Wipes root_key from Android Keystore + clears in-memory state.
    // All previously encrypted data becomes unrecoverable on this device.
    fun wipe()

    // AEAD (operation-on-vault; derived key never leaves vault)
    @Throws(VaultException::class) fun aeadSeal(purpose: Purpose, plaintext: ByteArray, aad: Aad): Ciphertext
    @Throws(VaultException::class) fun aeadOpen(purpose: Purpose, ciphertext: Ciphertext, aad: Aad): ByteArray

    // MAC (operation-on-vault)
    @Throws(VaultException::class) fun mac(purpose: Purpose, message: ByteArray): MacTag
    @Throws(VaultException::class) fun verifyMac(purpose: Purpose, message: ByteArray, tag: MacTag): Boolean

    // Signatures (Ed25519, identity-scoped — used for rule 13 server-verified proofs)
    @Throws(VaultException::class) fun sign(message: ByteArray): Signature
    fun verify(publicIdentity: PublicIdentity, message: ByteArray, signature: Signature): Boolean
    fun publicIdentity(): PublicIdentity

    // NOTE: no exportDerivedKey — removed per TASK-100 (new-device-new-identity model).
    // See Session 6 exit ramp for add-back path (~2-3 days additive, openmls from_raw verified).
}

// Recovery strategy — pluggable adapter (Session 6 owner insight; Session 7 Q-D validation blob inside adapter)
interface RecoveryStrategy {
    // deriveRoot returns opaque root material to KeyVault impl; never exposed to callers
    internal fun deriveRoot(): ByteArray
    // Post-unlock verification hook (Session 7 D1 decision):
    // Adapter internally seals a known plaintext ("vault-init-v1") on first setup,
    // stores Ciphertext_valid in adapter-owned scope. On unlock: derive candidate root,
    // try aeadOpen(Ciphertext_valid). Success = correct; TamperDetected = wrong -> throws RecoveryFailed.
    // KeyVault.unlock(strategy) delegates this check to the strategy.
    @Throws(VaultException::class) internal fun verifyUnlock(candidateRoot: ByteArray)
}

// Session 7 Q-B salt derivation — Bitwarden pattern (client-side, deterministic from stable ID)
class PassphraseRecovery(
    val passphrase: String,
    val identityHint: IdentityHint,   // wraps googleUid for GMS devices, or device-random for no-GMS
    val params: Argon2Params = Argon2Params.V1
) : RecoveryStrategy {
    // salt derivation:
    //   GMS device:    salt = HKDF(googleUid.toByteArray(UTF_8), info = "salt-v1", length = 16)
    //   no-GMS device: salt = deviceRandomSalt (stored in Android Keystore at first-setup)
    // Deterministic from stable public identifier — no separate salt storage needed on GMS path.
}

sealed class IdentityHint {
    class GoogleAccount(val googleUid: String) : IdentityHint()
    class NoGmsDevice(val deviceRandomSalt: ByteArray) : IdentityHint()  // 16 bytes CSPRNG, stored device-only
}

// Future adapters: Bip39Recovery, TwoFactorRecovery, HardwareKeyRecovery — plug in without changing KeyVault

// Sealed exception hierarchy
sealed class VaultException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class VaultLocked : VaultException("vault requires unlock via RecoveryStrategy")
    class WrongPurpose(val expected: Purpose, val actual: Purpose)
        : VaultException("purpose mismatch: expected $expected, got $actual in ciphertext header")
    class TamperDetected(cause: Throwable? = null) : VaultException("ciphertext MAC failed or AAD mismatch", cause)
    class UnsupportedFormatVersion(val version: Int) : VaultException("blob format_version=$version not supported")
    class NoRootKey : VaultException("no root key — bootstrap or recovery required")
    class HardwareBackedKeystoreUnavailable(cause: Throwable? = null) : VaultException("hardware keystore unavailable", cause)
    class RecoveryFailed(cause: Throwable? = null) : VaultException("RecoveryStrategy.deriveRoot failed", cause)
}
```

**Blob format (§7 rec adopted)**:
```
magic (2b = 0x4B 0x56 "KV")
|| format_version (1b, currently 0x01)
|| purpose_id (2b, big-endian)
|| key_epoch (2b, currently 0x0000 — rotation deferred; format ready)
|| nonce (24b, XChaCha20-Poly1305 random from CSPRNG)
|| ciphertext_payload
|| MAC (16b)
```
Overhead: 7b header + 24b nonce + 16b MAC = 47b per blob before payload.

**AAD contents (§3 rec adopted, MANDATORY)**:
```
Aad.bytes = namespace_id_len(2b) || namespace_id_bytes || schema_version(2b) || blob_version(2b)
```
Length-prefixed layout eliminates ambiguity (no string concatenation). Callers of `aeadSeal / aeadOpen` MUST populate — protects against namespace substitution and version rollback attacks.

**Cryptographic primitives (per C3 — all libsodium-kmp)**:
- AEAD: **XChaCha20-Poly1305** (24-byte random nonce prevents reuse).
- MAC: **Blake2b-256** (via `crypto_generichash`, keyed mode).
- Signatures: **Ed25519** (`crypto_sign_detached`).
- Per-purpose key derivation: **libsodium `crypto_kdf`** (Blake2b-based, root_key + purpose_id + key_epoch → derived key). Deterministic.
- Recovery KDF: **Argon2id** (`crypto_pwhash`, params: memory=64MiB, iterations=3, parallelism=1 — FROZEN for `Argon2Params.V1`, versioned for future evolution).

**Storage split (per C3)**:
- **Hardware layer** (Android Keystore / iOS Keychain / Secure Enclave): encrypts `root_key` at rest ONLY. Adapter-specific. NEVER touches derived keys or participates in `aeadSeal/aeadOpen`.
- **Software layer** (libsodium-kmp `commonMain`): all derivation, AEAD, MAC, signatures. Deterministic across platforms. Same input bytes on Android/iOS/Fake → same output bytes.

**Purpose registry rules**:
- New Purpose = adding enum variant + declaring attributes. Not «pass any string». Compile-time enforcement.
- `exportable = false` for all MVP purposes. `exportable = true` would require additive `Purpose.External(labelBytes)` sealed variant migration (exit ramp).
- If Purpose count exceeds ~10 variants — migrate to `sealed class Purpose` with object subclasses (additive, non-breaking).

**Cross-platform test vectors (DoD requirement)**:
- Fixture: fixed root_key + fixed inputs + fixed AAD → expected ciphertext bytes.
- Vectors MUST match byte-for-byte on: Android (native libsodium via JNI), JVM (native libsodium in tests), iOS (swift-sodium via cinterop when TASK-26 adds `iOSKeyVault`), Fake (deterministic in-memory).
- Version control: `core/keys/src/commonTest/resources/vectors/v1.json`. Break = major format_version bump.

**Migration plan** (implementation phasing, ~1 week estimated):
1. **Phase-1** — Port + fakes + contract tests. `KeyVault` interface + `Purpose` + newtypes + `VaultException` + `FakeKeyVault` (in-memory) + cross-platform test vector fixtures + `RecoveryStrategy` + `PassphraseRecovery` matching TASK-6.
2. **Phase-2** — `AndroidKeyVault` adapter (Android Keystore for root_key at rest, libsodium-kmp for all crypto ops). Wraps existing `KeyRegistry` as internal helper.
3. **Phase-3** — Migrate `ConfigCipher2` and `EnvelopeStorage` call sites to `keyVault.aeadSeal(Purpose.CONFIG, ..., canonicalAad(...))`.
4. **Phase-4** — Downgrade `RootKey` from public to `internal class family.keys.impl.RootKey`. Downgrade `KeyRegistry` from public port to `internal` helper.
5. **Phase-5** — Cleanup: remove `RootKey.bytes` public accessor, remove stale `KeyRegistry` KDoc `"contacts"`/`"media"` examples, PR + backlog sync.

**Applies to** (downstream tasks awaiting):
- [TASK-25](task-25%20-%20Multi-app-Cohabitation-Chain-of-trust-Recovery.md) *superseded by TASK-115* — no `KeyVault` port dependency; uses `sealed_box` in TASK-115 `FamilyAppInviter` port.
- [TASK-26](task-26%20-%20iOS-Admin-Preset.md) — iOS `KeyVault` adapter (Keychain root storage + swift-sodium ops).
- [TASK-29](task-29%20-%20Android-TV-Preset.md) — uses same Android adapter, zero port changes.
- [TASK-11](task-11%20-%20Contact-Photos-Family-Album-foundation.md), [TASK-27](task-27%20-%20Elderly-Friendly-Messenger-Jitsi-based.md), [TASK-28](task-28%20-%20Full-Shared-Family-Album.md) — bucket AEAD via `KeyVault.aeadSeal/aeadOpen`.
- [TASK-124](task-124%20-%20F-CRYPTO-openmls-integration.md) — openmls integration; does NOT depend on `KeyVault` for signature keys (uses openmls's internal `SignatureKeyPair::new`). May depend on `KeyVault.aeadSeal` for openmls `StorageProvider` at-rest encryption of MLS group state (~Phase 3 decision).
- [TASK-113](task-113%20-%20Refactor-Outcome-T-E-sealed-VaultException-typed-exceptions-across-codebase.md) — Outcome refactor is orthogonal; TASK-112 uses sealed exceptions per Q3 Session 2; TASK-113 aligns other ports separately.

**Trade-offs**:
- Purpose enum vs sealed class extensibility → enum for MVP (2 variants), sealed class exit ramp non-breaking.
- Sync (not `suspend`) API → matches libsodium / libsignal JNI / OpenMLS reference. Callers wrap in `Dispatchers.IO` if needed.
- libsodium software vs hardware crypto → cross-platform determinism + recoverability > hardware tamper resistance. Accepted for family segment MVP; revisit if clinic B2B (TASK-34) exits parking-lot.
- Passphrase recovery vs BIP39 → UX + retention priority. `RecoveryStrategy` port makes future BIP39/2FA additive without breaking existing.
- Sealed exceptions vs Outcome → idiomatic Kotlin + FFI-friendly. TASK-113 wraps at own boundary later if needed.
- AAD length-prefixed vs CBOR → simpler encoding, no external CBOR dep in domain (rule 1 clean).

**Exit ramps** (actionable, verified where possible):
- **Purpose enum insufficient** → additive `sealed class Purpose` + `Purpose.External(labelBytes)` for external-lib key material. openmls `SignatureKeyPair::from_raw(scheme, priv, pub)` API path **verified 2026-07-14**. ~0.5 day port change + 2-3 days recovery flow adaptation.
- **Sync insufficient (biometric prompt required)** → additive `@Throws suspend fun signWithBiometric(...)`. ~0.5 day, non-breaking.
- **Sealed VaultException friction with TASK-113 Outcome refactor** → wrap `KeyVault` throws in `Outcome<T, VaultException>` at TASK-113 boundary. ~1 day, wire format unchanged.
- **libsodium insufficient (clinic B2B compliance)** → hybrid model: `AndroidKeyVault` gains attestation-mode for opt-in enterprise preset. root_key stays in StrongBox; derived keys still software. ~1 week, adapter-only change.
- **Passphrase insufficient (server compromise incident)** → add `Bip39Recovery` strategy adapter + migration UX «upgrade to 12-word recovery». ~1 week. `KeyVault` port unchanged. New users default Bip39, existing users prompted.
- **AAD canonical layout evolution** → `format_version` bump (currently 0x01) → new blob layout + `UnsupportedFormatVersion` migration handler. ~2-3 days per version.
- **Cross-platform vectors diverge (iOS swift-sodium ≠ Android libsodium)** → freeze test vectors as Android reference, iOS adapter carries compatibility shim if primitives differ. Discovered at TASK-26 implementation time.

**Revision history**:
- **Session 2 (2026-07-07)** — original Decision. Superseded 2026-07-14. See Session 2 content above (`~~immutable 🔒~~ REVISED` marker).
- **Session 6 (2026-07-14)** — REVISED via rule 11 mutability window (implementation not started). Incorporates:
  - Session 4: external CryptoKit contract review (AAD, blob header, Purpose registry, sign/verify, prohibited list).
  - Session 5: mentor self-adversarial deliberation (attack scenarios, self-critique of Session 4 biases).
  - Owner Session 6 decisions on C1 (passphrase with pluggable RecoveryStrategy port — architectural insight added), C2 (remove exportDerivedKey), C3 (libsodium software layer).
  - openmls source verification (2026-07-14) — corrected wrong Session 4 rationale, C2 conclusion re-grounded on TASK-100 model.
- **Session 7 (2026-07-14)** — Owner micro-decisions after post-analyze review; salt approach research (WhatsApp/Signal/Bitwarden industry patterns). Decision block re-touched (still mutable, still zero implementation):
  - **F2 dropped** — no TASK-6 users exist yet; SC-002 backward-compat requirement removed, T022 dropped from tasks.md.
  - **F3 direction B** — package naming aligned to existing `com.launcher.core.keys.*` (follows `:core:*` module convention), Decision prose updated in-place from `family.keys.*` references.
  - **Q-A** — `Argon2Params.V1` confirmed at `memory=64MiB, iterations=3, parallelism=1`. Matches Bitwarden default 2023 + OWASP recommendation.
  - **Q-B** — salt approach: **Bitwarden pattern** (client-side derivation from stable public identifier). Formula: `salt = HKDF(googleUid, info="salt-v1", 16 bytes)`. For no-GMS devices (Huawei fallback, TASK-6 already handles): random 16 bytes stored device-only in Android Keystore, cross-device recovery impossible in that mode. Exit ramp if server compromise scenario materializes: migrate to per-user random salt + Google Drive Backup API for cross-device transfer (~1 week additive).
  - **Q-C** — `KeyVault.wipe()` method added to port. On logout event → wipes root_key from Keystore + clears in-memory state. `KeyVault` = singleton per app instance (not per identity — user re-login = re-unlock, previous data cascaded-wiped).
  - **Q-D** — validation blob logic **inside `PassphraseRecovery` adapter** (D1). Adapter internally seals a known plaintext (`"vault-init-v1"`) at first setup, stores `Ciphertext_valid`. On subsequent unlock: derive candidate root → attempt `aeadOpen(Ciphertext_valid)`. Success = correct passphrase; `TamperDetected` = wrong passphrase → adapter throws `VaultException.RecoveryFailed`.

<!-- SECTION:DISCUSSION:END -->
