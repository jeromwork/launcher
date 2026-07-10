---
id: TASK-112
title: 'Decision: KeyVault port boundary — operation-on-vault + narrow export'
status: In Progress
assignee: []
created_date: '2026-07-07'
updated_date: '2026-07-10 17:30'
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

**Discussion** — открыта 2026-07-07. Session 1 закрыла research (industry patterns). Ожидается Decision Session 2 после согласования с владельцем target shape'а.

**Downstream tasks awaiting**:
- **[TASK-25](task-25%20-%20Multi-app-Cohabitation-Chain-of-trust-Recovery.md)** — builds ChainOfTrustVerifier поверх `IdentityVault`, ждёт port boundary.
- **[TASK-26](task-26%20-%20iOS-Admin-Preset.md)** — iOS adapter of `IdentityVault` (Keychain + Secure Enclave).
- **[TASK-29](task-29%20-%20Android-TV-Preset.md)** — TV использует тот же Android adapter `IdentityVault`, никаких изменений port'а.
- **[TASK-67](task-67%20-%20Pairing-Feature-And-Bucket.md)** — pairing использует `exportDerivedKey("noise-static", ...)` для Noise handshake material.
- **[TASK-11](task-11%20-%20Contact-Photos-Family-Album-foundation.md)**, **[TASK-27](task-27%20-%20Elderly-Friendly-Messenger-Jitsi-based.md)**, **[TASK-28](task-28%20-%20Full-Shared-Family-Album.md)** — используют `aeadSeal / aeadOpen` без изменений при HarmonyOS/iOS platform expansion.

**HarmonyOS NEXT / desktop / future platforms** — тот же port, новый adapter, ноль изменений в domain и downstream tasks. Это и есть цель.

<!-- SECTION:DESCRIPTION:END -->

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

### Decision (English, immutable) 🔒

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

<!-- SECTION:DISCUSSION:END -->
