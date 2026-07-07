---
id: TASK-112
title: 'Decision: IdentityVault port boundary — operation-on-vault + narrow export'
status: Discussion
assignee: []
created_date: '2026-07-07'
updated_date: '2026-07-07'
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

<!-- SECTION:DISCUSSION:END -->
