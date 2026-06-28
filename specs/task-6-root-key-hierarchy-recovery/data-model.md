# Data Model — TASK-6: F-5 Root Key Hierarchy + Owner Recovery

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Phase 1 — domain types, fields, invariants, lifetimes, relationships.
Contract-level declarations (sealed errors, port signatures, JSON schemas) live in `contracts/`.

All domain types live under `core/keys/src/commonMain/kotlin/family/keys/api/`.

---

## 1. AuthIdentity (imported from F-4 / spec 017)

**Kind**: data class — **not defined here**, imported from `com.launcher.api.auth.AuthIdentity` (F-4 territory).

**Fields F-5 cares about**:

| Field         | Type     | Notes                                              |
|---------------|----------|----------------------------------------------------|
| `stableId`    | `String` | UUID v4, provider-agnostic. **Only field F-5 reads**. |
| `email`       | `String?`| Ignored by F-5.                                    |
| `displayName` | `String?`| Ignored by F-5.                                    |

**Relationships**: passed into `RootKeyManager.create()` / `.recover()`; F-5 immediately projects to `StableId`.

**Invariants**:
- F-5 MUST NOT branch on `email` / `displayName` / provider hints. Provider-agnostic by FR-001.
- `email` / `displayName` MUST NOT appear in any wire-format produced by F-5 (RecoveryKeyBackupBlob etc.).

**Lifetime**: tied to `AuthProvider.identityFlow`. F-5 subscribes; sign-out emits `null`.

---

## 2. StableId

**Kind**: type alias — `typealias StableId = String`.

**Format**: UUID v4, lowercase hex, with hyphens, **36 characters** (e.g. `00000000-0000-4000-8000-000000000001`).

**Fields**: none (alias).

**Invariants**:
- Provider-agnostic. MUST NOT be a Google `sub`, Firebase UID, email hash, phone-number hash, or any other identifier that leaks the auth provider.
- Generated **server-side** during identity-link upsert (F-4 territory). F-5 is a pure consumer.
- Stable across `signOut → signIn` for the same human identity.
- Two different identities (different providers, different humans) MUST produce different `StableId` values.

**Lifetime**: lifetime of the human identity. Outlives device.

**Relationships**: parameter to `KeyRegistry.derive(...)`, `KeyRegistry.wipeAll(...)`, `RecoveryKeyBackup.fetchBlob(...)`; field on `RecoveryKeyBackupBlob`.

**Forbidden values**: empty string; non-UUID strings; strings containing `@`, `.com`, `+` (provider hints).

---

## 3. RootKey

**Kind**: opaque value class wrapping a 32-byte (256-bit) secret.

**Constructor**: **private** to `family.keys.api`. Instances are produced only by `RootKeyManager.create()` or `.recover()`.

**Fields**:

| Field      | Type       | Notes                                                    |
|------------|------------|----------------------------------------------------------|
| (internal) | `ByteArray`| Length **exactly 32**. Not exposed via public accessor.  |

**Invariants**:
- **NOT serializable**: no `toString()` leaking material, no `copy()` exposing bytes, no `equals()` comparing raw bytes (constant-time check only, or reference identity).
- Plaintext material lives in RAM only for the duration of a single crypto operation; wrapped via Android Keystore / SecureKeystore for at-rest storage.
- One `RootKey` per `(stableId, device)` tuple at any given time.

**Lifetime**:
1. **Created** by `RootKeyManager.create(identity, passphrase)` (first device) or `.recover(identity, passphrase)` (subsequent devices).
2. **In RAM** while the user is signed in; held by `RootKeyManager` internal state.
3. **Cleared** on `signOut()` cascade (FR-019). Wrapped form in Keystore is wiped; in-RAM bytes are zeroized.

**Relationships**:
- Produced by `RootKeyManager` (port in F-5).
- Consumed by `KeyRegistry.derive(...)` as HKDF IKM (input key material).
- Backed up encrypted in `RecoveryKeyBackupBlob.ciphertext` (under passphrase-derived KEK).

**Forbidden**: any API exposing `toByteArray()` / `material` / `bytes` publicly; any logging containing the value.

---

## 4. DerivedKey

**Kind**: opaque value class wrapping the 32-byte output of HKDF-SHA256.

**Constructor**: **private** to `family.keys.api`. Instances are produced only by `KeyRegistry.derive(stableId, purpose)`.

**Fields**:

| Field      | Type       | Notes                                                                |
|------------|------------|----------------------------------------------------------------------|
| (internal) | `ByteArray`| Length **exactly 32** (HKDF-SHA256 output, 1 block).                 |

**Invariants**:
- **NOT serializable** (same rules as `RootKey`).
- Deterministic: same `(RootKey, stableId, purpose)` triple MUST yield byte-equal `DerivedKey` (HKDF-SHA256 RFC 5869 semantics with `purpose` as `info`).
- Cryptographically independent from `RootKey` and from other `DerivedKey`-s with different `purpose`.
- Multiple `DerivedKey`-s coexist for one `RootKey` (one per `purpose` string: `"config"`, `"contacts"`, `"photos"`, ...).

**Lifetime**:
- Same as the parent `RootKey`. When `RootKey` is wiped, all `DerivedKey`-s become unrecoverable from new `derive()` calls (but any cached instance in caller scope continues to work until released).
- Typically short-lived: derived on demand, used for one crypto operation, released to GC.

**Relationships**:
- Produced by `KeyRegistry.derive(...)`.
- Consumed by `ConfigCipher2` (spec 018), future `ContactsCipher`, future `PhotoCipher`.

**Forbidden**: persistence to disk / DataStore / Firestore in any form. `DerivedKey`-s are re-derived on demand from `RootKey`.

---

## 5. KdfParams

**Kind**: value class (data class with single primary use: serialization).

**Fields**:

| Field         | Type     | Default     | Notes                                                  |
|---------------|----------|-------------|--------------------------------------------------------|
| `algorithm`   | `String` | `"Argon2id"`| Free-form string for forward-compat; validated set.    |
| `iterations`  | `Int`    | —           | Argon2 time cost (per FR-009: `3`).                    |
| `memoryKb`    | `Int`    | —           | Argon2 memory cost in KiB (per FR-009: `65536` = 64 MiB).|
| `parallelism` | `Int`    | —           | Argon2 lanes (per FR-009: `1`).                        |

**Invariants** (enforced in constructor / parser):
- `algorithm` MUST be in the known set `{"Argon2id"}`. Unknown algorithm → `BackupError.UnsupportedSchema` on parse, `IllegalArgumentException` on construction.
- `iterations >= 1`.
- `memoryKb >= 1024` (1 MiB minimum — anything smaller is a weak-KDF smell).
- `parallelism >= 1`.

**Lifetime**: embedded in `RecoveryKeyBackupBlob`. Persisted with the blob; immutable.

**Relationships**:
- Field on `RecoveryKeyBackupBlob`.
- Input to `Argon2RootKeyManager` (Android adapter) for KDF derivation during recovery.

**Forbidden**: `algorithm` strings carrying vendor prefixes (`google-argon2id`, `firebase-argon2id`); negative or zero numeric fields.

---

## 6. RecoveryKeyBackupBlob

**Kind**: data class — **wire format** (kotlinx-serialization JSON, per FR-006 Q-K).

**Storage**: Cloudflare Worker R2 bucket; one blob per `stableId`.

**Fields** (per FR-006, FR-010):

| Field           | Type        | Notes                                                                  |
|-----------------|-------------|------------------------------------------------------------------------|
| `schemaVersion` | `Int`       | **`= 1`** in this release. Required from first commit (rule 5).        |
| `stableId`      | `StableId`  | UUID. Echoed for self-describing blob + Worker idempotency.            |
| `salt`          | `ByteArray` | **32 bytes**. Argon2id salt. Base64 in JSON.                           |
| `kdfParams`     | `KdfParams` | Argon2id parameters used to derive KEK from passphrase.                |
| `ciphertext`    | `ByteArray` | XChaCha20-Poly1305 AEAD output of the 32-byte `RootKey`. Base64 in JSON.|
| `nonce`         | `ByteArray` | **24 bytes** (XChaCha20 IETF nonce). Base64 in JSON.                   |
| `createdAt`     | `Instant`   | ISO-8601 in JSON (e.g. `2026-06-28T12:34:56.000Z`). For audit / debug. |

**Invariants** (validated by parser + `RecoveryKeyBackupBlobProviderAgnosticTest`):
- `schemaVersion >= 1`. Strictly-greater than known → `BackupError.UnsupportedSchema` (CHK008).
- `stableId` is a valid UUID v4 string (regex check).
- `salt.size == 32`.
- `nonce.size == 24`.
- `ciphertext.size >= 16` (minimum AEAD tag length; in practice `32 + 16 = 48` bytes for a wrapped 32-byte RootKey).
- `kdfParams` invariants (see §5) hold.
- **Provider-specific fields PROHIBITED** (fitness function `RecoveryKeyBackupBlobProviderAgnosticTest`): no `googleSub`, `firebaseUid`, `providerKind`, `providerId`, `googleDriveFileId`, `appleId`, `phoneNumber`, `email`. Adding any such field is a refuse-the-PR violation of rule 1.

**Lifetime**:
1. **Built** on first device by `RootKeyManager.create(...)` after RootKey generation + Argon2id wrap.
2. **Uploaded** via `RecoveryKeyBackup.uploadBlob(blob)` → Worker `POST /backup` (Bearer JWT + Idempotency-Key).
3. **Fetched** on second device by `RecoveryKeyBackup.fetchBlob(stableId)` during recovery.
4. **Deleted** by `RecoveryKeyBackup.deleteBlob(stableId)` only via explicit Fallback path (5 wrong attempts, US-3). NOT deleted on signOut (FR-019, Q-J).

**Relationships**:
- Produced by `RootKeyManager.create(...)`.
- Consumed by `RootKeyManager.recover(...)` (which Argon2id-derives KEK from passphrase + salt, then AEAD-unwraps `ciphertext` → `RootKey`).
- Transported by `RecoveryKeyBackup` port (Worker adapter only in MVP).

**Wire-format obligations** (rule 5):
- Roundtrip test: write → parse → assert equal (`RecoveryKeyBackupBlobRoundtripTest`).
- Backward-compat test: fixture `recovery-key-backup-v1.json` continues to parse after any future schema bump (`RecoveryKeyBackupBlobBackwardCompatTest`).
- Future fields MUST be additive (nullable with default).

---

## 7. AuthAvailabilityStatus

**Kind**: sealed class.

**Cases**:

| Case                            | Fields                       | Meaning                                                       |
|---------------------------------|------------------------------|---------------------------------------------------------------|
| `Available`                     | (none — object)              | Auth provider is reachable; setup/recovery flow can proceed.  |
| `Unavailable(reason)`           | `reason: AvailabilityReason` | Auth provider not currently usable; UI shows explainer (FR-013). |

**Invariants**:
- Domain-level only — MUST NOT carry vendor-specific data (no Google `apiAvailability` codes, no Huawei HMS status). Adapters translate vendor signals into `AvailabilityReason` enum values.
- `Available` is a singleton object (no fields).

**Lifetime**: produced fresh by every `AuthAvailability.check()` call (not cached at domain layer; adapter may cache for short TTL).

**Relationships**:
- Returned by `AuthAvailability.check()` port (FR-005, FR-013).
- Consumed by wizard / setup flow to gate entry to setup screen.

---

## 8. AvailabilityReason

**Kind**: enum class (domain-level, NOT provider-specific).

**Cases**:

| Case                  | Meaning                                                                      |
|-----------------------|------------------------------------------------------------------------------|
| `NoSupportedProvider` | Device has no auth provider this build was wired with (e.g. GMS-less device on Phase-2 build that only ships GMS adapter). |
| `KeystoreLocked`      | Android Keystore not yet unlocked (lockscreen not authenticated). Extremely rare on minSdk=24+ but possible. |
| `NetworkUnreachable`  | Online identity check would fail because device is offline. Relevant for future when network gate exists at this layer. |

**Invariants**:
- **Forbidden domain values**: any name mentioning `Google`, `GMS`, `Huawei`, `HMS`, `Apple`, `Firebase`, `Sub`, `OAuth`. Those are adapter concerns. The domain enum stays vendor-agnostic.
- New cases MUST be added behind a `when`-exhaustiveness check (kotlin sealed-like style for enums).

**Lifetime**: passed inside `AuthAvailabilityStatus.Unavailable`. No independent lifecycle.

**Relationships**: field on `AuthAvailabilityStatus.Unavailable`.

---

## 9. RootKeyError

**Kind**: sealed class.

**Cases** (per FR-003 / spec edge cases):

| Case              | Meaning                                                                       |
|-------------------|-------------------------------------------------------------------------------|
| `WrongPassphrase` | Argon2id-derived KEK fails to AEAD-unwrap `ciphertext` (auth tag mismatch).   |
| `CorruptedBlob`   | `RecoveryKeyBackupBlob` parses, but ciphertext fails AEAD even on first attempt (very rare; differs from `WrongPassphrase` semantically — wrong material on server side). |
| `NoKeystore`      | Android Keystore unavailable (theoretical edge — minSdk=24 always has it). UI: "device unsupported". |
| `NoIdentity`      | `create()` / `.recover()` called without an `AuthIdentity` (programmer error / race condition during signOut). |

**Invariants**:
- Each case is exhaustively handled by callers via `when`.
- No case carries vendor-specific data.

**Lifetime**: short-lived; wrapped in `Outcome.Failure(RootKeyError.X)` returned from `RootKeyManager.create()` / `.recover()`.

**Relationships**: returned by `RootKeyManager` port; UI maps to screens (entry retry, fallback, "device unsupported" dialog).

**Contract**: declared in `RootKeyManager.kt` (no separate contract file — per F-5 plan §6).

---

## 10. BackupError

**Kind**: sealed class.

**Cases** (per FR-010 / FR-014):

| Case                  | HTTP origin              | Meaning                                                              |
|-----------------------|--------------------------|----------------------------------------------------------------------|
| `NetworkUnavailable`  | network layer            | 3 retries with exponential back-off all failed (timeout 30s each).  |
| `AuthExpired`         | Worker `401`             | Firebase JWT expired / revoked. UI triggers re-sign-in.              |
| `ServerQuotaExceeded` | Worker `507`             | R2 bucket full (operational alert at provider side).                 |
| `Conflict`            | Worker `409`             | `Idempotency-Key` reused with different request body. Programmer error or replay attack. |
| `UnsupportedSchema`   | parser                   | Server returned blob with `schemaVersion` > known (wire-format CHK008 strategy). |

**Invariants**:
- Each case is handled exhaustively by UI / retry policy.
- No vendor-specific data in cases (Worker is "our server" — its HTTP codes are part of our domain).

**Lifetime**: short-lived; wrapped in `Outcome.Failure(BackupError.X)` returned from `RecoveryKeyBackup` port methods.

**Relationships**: returned by `RecoveryKeyBackup` (port). Caller (`RecoveryViewModel`) maps to retry / re-sign-in / "continue without cloud backup" dialog.

**Contract**: declared in `contracts/recovery-key-backup-v1.md` and `RecoveryKeyBackup.kt`.

---

## Relationships graph

```
AuthIdentity (F-4, external)
   │ .stableId : StableId (UUID)
   ▼
StableId  ────────────────────────► used as namespace key in KeyRegistry
   │                                used as ID in RecoveryKeyBackupBlob
   │                                used as ID for Worker /backup endpoints
   │
   ▼
RootKey  (32 bytes, opaque)
   │
   │ HKDF-SHA256(IKM=RootKey, info=purpose)
   ├─────────► DerivedKey("config")   ◄── consumed by ConfigCipher2 (spec 018)
   ├─────────► DerivedKey("contacts") ◄── future ContactsCipher
   └─────────► DerivedKey("photos")   ◄── future PhotoCipher
   │
   │ Argon2id(passphrase, salt, kdfParams) → KEK
   │ XChaCha20-Poly1305 AEAD(RootKey, KEK, nonce) → ciphertext
   ▼
RecoveryKeyBackupBlob {schemaVersion, stableId, salt, kdfParams, ciphertext, nonce, createdAt}
   │
   │ uploaded via RecoveryKeyBackup port
   ▼
Cloudflare Worker (R2 bucket)  ◄── exit ramp: own server per SRV-RECOVERY-001
   │
   │ on error: BackupError.{NetworkUnavailable | AuthExpired | ServerQuotaExceeded | Conflict | UnsupportedSchema}
   │ on recover error: RootKeyError.{WrongPassphrase | CorruptedBlob | NoKeystore | NoIdentity}

AuthAvailability port
   └── check() → AuthAvailabilityStatus
                  ├── Available
                  └── Unavailable(AvailabilityReason.{NoSupportedProvider | KeystoreLocked | NetworkUnreachable})
```

**Reading rule for graph**: arrows point in the direction of data flow / production. Anything below a node depends on the node above.

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Краткое резюме (для не-разработчика)

**Какие сущности есть в F-5 (TASK-6)**:

1. **AuthIdentity** — пользовательский аккаунт (Google или другой). F-5 берёт оттуда **только** `stableId` (анонимный UUID); ни email, ни имя F-5 не знает. Так мы остаёмся «provider-agnostic» — завтра добавим Email-login, и F-5 не нужно будет переписывать.

2. **StableId** — этот самый UUID. Просто строка из 36 символов вида `xxxxxxxx-xxxx-4xxx-...`. Один человек = один `StableId` навсегда, на всех устройствах.

3. **RootKey** — главный 256-битный секрет пользователя. Хранится в Android Keystore (защищённое хранилище телефона). В коде он «опаковый» — нельзя случайно вывести его в лог или скопировать.

4. **DerivedKey** — производный ключ под конкретную задачу (`"config"`, `"contacts"`, `"photos"`). Вычисляется из `RootKey` детерминированно (та же пара → тот же ключ). Никуда не сохраняется — пересчитывается по требованию.

5. **KdfParams** — настройки Argon2id (сколько памяти / итераций нужно для подбора пароля). Хранится внутри `RecoveryKeyBackupBlob`, чтобы при восстановлении на другом устройстве использовать те же параметры.

6. **RecoveryKeyBackupBlob** — зашифрованная копия `RootKey`, лежащая на нашем Cloudflare Worker'е. Содержит: версию схемы, UUID пользователя, соль, параметры KDF, зашифрованный материал, nonce, дату создания. **Никаких** полей про Google / Firebase / email — это фитнес-функция кодом проверяется.

7. **AuthAvailabilityStatus** — «можно ли сейчас входить через провайдера авторизации». Либо `Available`, либо `Unavailable(причина)`.

8. **AvailabilityReason** — почему недоступно: нет поддерживаемого провайдера, Keystore залочен, нет сети. **Запрещено** называть причины «нет Google» / «нет Huawei» — это дело адаптера, не домена.

9. **RootKeyError** — что может пойти не так при создании / восстановлении: неверный пароль, поломанный blob, нет Keystore, нет identity.

10. **BackupError** — что может пойти не так при общении с Worker'ом: нет сети, JWT истёк, у Worker'а кончилось место, конфликт идемпотентности, неизвестная версия схемы.

**Главные правила (инварианты)**:

- `RootKey` и `DerivedKey` **никогда** не сериализуются — нет `toString()`, нет `copy()`, никаких логов с материалом.
- В `RecoveryKeyBackupBlob` запрещены любые поля, выдающие провайдера авторизации (`googleSub`, `firebaseUid`, etc.). Проверяется автоматическим тестом.
- Все wire-форматы имеют `schemaVersion` с первого коммита — на случай будущей миграции.
- `StableId` — провайдер-агностичный UUID. Никаких email-хэшей, никаких Google `sub`.

**Что взаимосвязано**:
- `AuthIdentity` (от F-4) → даёт `stableId`.
- `stableId` + passphrase → `RootKey` (через Argon2id).
- `RootKey` + purpose → `DerivedKey` (через HKDF-SHA256).
- `RootKey` + passphrase + salt → шифруется в `RecoveryKeyBackupBlob` (через XChaCha20-Poly1305).
- `RecoveryKeyBackupBlob` → лежит у нас на Worker'е; при recovery на втором устройстве скачивается и расшифровывается обратно в `RootKey`.

**Что НЕ описано здесь** (живёт в contracts/):
- Сигнатуры портов (`KeyRegistry`, `RootKeyManager`, `RecoveryKeyBackup`, `AuthAvailability`).
- JSON-схема `RecoveryKeyBackupBlob` под provider-agnostic тест.
- HTTP-контракт Worker'а (`POST /backup`, `GET /backup/{stableId}`, `DELETE /backup/{stableId}`).
<!-- NOVICE-SUMMARY:END -->
