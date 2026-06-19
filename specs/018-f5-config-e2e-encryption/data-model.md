# Data Model — F-5: Root Key Hierarchy + ConfigDocument Encryption + Recovery

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

Phase 1 — описание entities, wire-formats, lifecycle и инвариантов F-5.

---

## 1. Entities

### 1.1 AuthIdentity (внешняя — из F-4, spec 017)

**Источник**: `com.launcher.api.auth.AuthIdentity` (уже в репо).

**Используется в F-5 как**: ключ изоляции / идентификации. F-5 читает только `stableId: StableId` (Google UID).

**Lifecycle**: вне F-5. F-5 подписывается на `AuthProvider.identityFlow` через `IdentityProof`.

---

### 1.2 RootKey

**Шейп**: непрозрачный 256-битный материал (32 байта).

**Storage**: НЕ хранится plaintext. Wrapped через `SecureKeystore.encrypt(...)` (F-CRYPTO), результат — в DataStore под ключом `wrapped-root-${uid}`.

**Lifecycle**:
1. **Generated** — `RootKeyManager.getOrCreate(identity)` при первом setup'е. Source: `Random.nextBytes(32)` через KMP-safe RNG.
2. **Wrapped** — немедленно после generate'а через `SecureKeystore` (alias `rootkey-${uid}`).
3. **Stored encrypted in cloud** — copy wrapped через passphrase-derived key via Argon2id → `RecoveryVaultBlob` → Firestore `users/{uid}/recovery-key`.
4. **Used in RAM** — при `KeyRegistry.getDek(...)` или `ConfigCipher.seal/open`. Unwrap → use → zeroize.
5. **Recovered** — на новом устройстве: `RecoveryKeyVault.fetchVault(uid)` → Argon2id derivation → AEAD unwrap → re-wrap в локальный `SecureKeystore`.
6. **Wiped** — `RootKeyManager.wipe(identity)` (опционально в MVP) или factory reset.

**Инварианты**:
- `RootKey` НИКОГДА не покидает RAM как plaintext дольше длительности одной криптооперации.
- Один UID → один RootKey. При sign-in под другим UID — отдельный RootKey, изолированный по alias namespacing (R-7).

---

### 1.3 WrappedDek

**Шейп** (data class, internal в `core/keys/impl/`):
```kotlin
internal data class WrappedDek(
    val name: String,               // например "config-cipher-aead-v1"
    val ciphertext: ByteArray,      // DEK material, encrypted via AEAD под RootKey
    val nonce: ByteArray,           // AEAD nonce (24 bytes для XChaCha20)
    val algorithm: String,          // "xchacha20poly1305-v1"
    val schemaVersion: Int,         // currently 1
)
```

**Storage**: DataStore (или encrypted file) под ключом `dek-${uid}-${name}`. Multiple per identity.

**Lifecycle**:
1. **Registered** — `KeyRegistry.registerDek(name, dekMaterial)` создаёт новый WrappedDek, encrypted под RootKey.
2. **Retrieved** — `KeyRegistry.getDek(name)` → unwrap → plaintext DEK material в RAM.
3. **Removed** — НЕ предусмотрено в MVP. Removal требует migration (rule 5).

**Инварианты**:
- Имена DEK глобальны в семейной экосистеме, не зависят от package'а (FR-023). Примеры: `config-cipher-aead-v1`, `pair-x25519-v1`, `photo-cipher-aead-v1`.
- Schema additive: новые имена DEK добавляются без bump'а `schemaVersion` (FR-005).

---

### 1.4 KeyRegistry (entity-aggregate)

**Шейп**: логически — map `name → WrappedDek` per identity. Physically — набор записей в DataStore с prefix `dek-${uid}-`.

**Persistence**: `SecureKeystore`-wrapped DEKs в local storage. Optional Firestore mirror (FR-004 «optional Firestore doc для cross-device sync») — **не реализуется в MVP**, остаётся inline TODO для future spec'и (S-2).

**Lifecycle**: tied to `AuthIdentity.stableId`. При recovery RootKey'а — DEKs автоматически re-accessible (потому что они wrapped под RootKey, который снова доступен).

**Инварианты**:
- Зарегистрированный DEK не теряется при sign-out → sign-in под тем же UID.
- DEKs UID1 не видны под UID2 (alias prefix R-7).

---

### 1.5 Passphrase (transient — НЕ entity, НЕ persisted)

**Шейп**: `CharArray` (НЕ `String` — чтобы не оставить копию в string pool).

**Lifecycle**:
1. User вводит → Compose state holds CharArray.
2. Передаётся в `Argon2idPassphraseKdf.derive(passphrase, salt, params)` → 32-byte key.
3. CharArray немедленно zeroized (`for (i in indices) passphrase[i] = ' '`).
4. Derived key используется один раз для unwrap/wrap RootKey'а → тоже zeroized.

**Инварианты** (FR-013):
- НЕТ persistence (logs, analytics, prefs, файлы).
- НЕТ string pool copy.
- В буфере обмена — short-lived, обнуляется (R-6, FR-013a).

---

### 1.6 RecoveryVaultBlob (wire-format)

**Шейп** (data class, public в `core/keys/api/`):
```kotlin
public data class RecoveryVaultBlob(
    val schemaVersion: Int,                  // currently 1
    val algorithm: String,                   // "argon2id-xchacha20poly1305-v1"
    val kdfSalt: ByteArray,                  // 16 bytes random
    val kdfParams: PassphraseKdfParams,      // memory, iterations, parallelism
    val wrappedRootKey: ByteArray,           // RootKey, encrypted via XChaCha20-Poly1305 под passphrase-derived key
    val nonce: ByteArray,                    // 24 bytes для XChaCha20
    val createdAt: Long,                     // Unix ms для debugging / audit
)

public data class PassphraseKdfParams(
    val memoryKiB: Int,                      // 65536 (= 64 MiB)
    val iterations: Int,                     // 3
    val parallelism: Int,                    // 1
)
```

**Wire**: JSON в Firestore. ByteArray поля кодируются как Base64 string (стандарт Firestore для bytes).

**Roundtrip**: write → read → assert equal (тест `RecoveryVaultRoundtripTest`).

**Backward-compat**: `RecoveryVaultBackwardCompatTest` читает fixture `recovery-vault-v1.json` и валидирует, что v1-формат продолжает парситься (rule 5).

**Инварианты**:
- `schemaVersion >= 1` (FR-005).
- `kdfSalt.size == 16` (R-1, валидируется в Firestore Rules R-3).
- `algorithm` — string, не enum, чтобы добавление новых алгоритмов было additive (FR-032).

---

### 1.7 SealedConfig (wire-format)

**Шейп** (data class, public в `core/keys/api/`):
```kotlin
public data class SealedConfig(
    val schemaVersion: Int,                  // currently 1
    val algorithm: String,                   // "xchacha20poly1305-v1"
    val ciphertext: ByteArray,               // ConfigDocument (JSON) encrypted под config-cipher-aead-v1 DEK
    val nonce: ByteArray,                    // 24 bytes
    val aad: ByteArray,                      // associated data, min: uid + schemaVersion
    val recipientMasterSignature: ByteArray? = null, // future cross-signing задел (FR-017)
)
```

**Wire**: JSON в Firestore по пути `users/{uid}/config/{docId}`.

**Roundtrip**: `ConfigCipherRoundtripTest` — seal → open → assert byte-equal.

**Backward-compat**: `SealedConfigBackwardCompatTest` читает `sealed-config-v1.json` и валидирует парсинг (nullable `recipientMasterSignature` валидируется как default null).

**Инварианты**:
- `schemaVersion >= 1`.
- `aad` MUST включать UID (FR-020) — защита от cross-identity replay.
- Plaintext ConfigDocument ≤ 256 KB (FR-029).
- `recipientMasterSignature` в MVP всегда `null`, заполняется в future spec без breaking change.

---

### 1.8 ConfigDocument (внешняя — из spec 008)

**Источник**: будущий `ConfigDocument` (spec 008, ещё не merged). Для F-5 — opaque payload, который `ConfigCipher.seal` принимает как `ByteArray` (сериализованный JSON).

**F-5 не валидирует структуру ConfigDocument** — только размер (≤ 256 KB).

---

## 2. Relationships

```
AuthIdentity (from F-4)
    │
    │ stableId (UID)
    ▼
RootKey ─────────► wraps ─────► [WrappedDek "config-cipher-aead-v1"]
   ▲                       └─► [WrappedDek "pair-x25519-v1"]      (future S-2)
   │                       └─► [WrappedDek "photo-cipher-aead-v1"] (future S-5)
   │                       │
   │ wrap via Keystore     ▼
   │                  KeyRegistry (logical aggregate)
   │
   │ wrap via passphrase-derived key (Argon2id)
   ▼
RecoveryVaultBlob ─────► persisted in Firestore users/{uid}/recovery-key

ConfigDocument ──► ConfigCipher.seal (uses config-cipher-aead-v1 DEK)
                                              │
                                              ▼
                                       SealedConfig ──► persisted in Firestore users/{uid}/config/{id}
```

---

## 3. State Transitions

### 3.1 RootKey lifecycle FSM

```
[None]
   │
   │ first setup (no Keystore entry, no Firestore vault)
   ▼
[Generated] ──► wrap into Keystore ──► [WrappedInKeystore]
   │                                       │
   │ создаётся RecoveryVaultBlob           │
   ▼                                       │
[StoredInCloud] ◄──────────────────────────┘
   │
   │ sign-in на новом устройстве (Keystore пуст, vault есть)
   ▼
[RecoveryRequested] ──► passphrase entry ──► Argon2id derivation ──► unwrap ──► re-wrap into local Keystore
                                                                                       │
                                                                                       ▼
                                                                                [WrappedInKeystore (new device)]
                                                                                       │
                                                                                       │ factory reset / explicit wipe
                                                                                       ▼
                                                                                    [Wiped]
```

### 3.2 KeyRegistry lifecycle

```
[Empty]  ── F-5 setup ─►  [HasConfigCipherDek]
              │
              │ future S-2 install
              ▼
       [HasConfigCipherDek + PairX25519Dek]
              │
              │ future S-5 install
              ▼
       [HasConfigCipherDek + PairX25519Dek + PhotoDek] ...
```

Добавление новых DEK — **additive**, никаких breaking changes к существующим (FR-005).

### 3.3 IdentityProof × KeyRegistry interaction

```
Sign-Out → Sign-In под тем же UID
    └─► Keystore содержит rootkey-${uid}
    └─► НЕТ recovery flow (SC-005)
    └─► KeyRegistry сразу доступен

Sign-Out → Sign-In под другим UID2
    └─► Keystore не содержит rootkey-${uid2}
    └─► Lookup в Firestore users/{uid2}/recovery-key
            ├─► есть → passphrase prompt → recovery flow (User Story 2)
            └─► нет → новый RootKey (User Story 1, SC-006)
    └─► UID1 ключи в Keystore сохраняются изолированно (FR-031)
```

---

## 4. Validation Rules / Invariants (формальный список)

| ID    | Инвариант                                                                     | Enforcement                                                  |
|-------|--------------------------------------------------------------------------------|--------------------------------------------------------------|
| INV-1 | `RecoveryVaultBlob.schemaVersion >= 1`                                          | Firestore Rules (R-3) + client parser                        |
| INV-2 | `RecoveryVaultBlob.kdfSalt.size == 16`                                          | Firestore Rules + `Argon2idPassphraseKdf` constructor        |
| INV-3 | `SealedConfig.aad` содержит UID                                                 | `AeadConfigCipherImpl.seal` строит AAD, тест валидирует      |
| INV-4 | Plaintext ConfigDocument ≤ 256 KB                                               | `ConfigCipher.seal` возвращает `CryptoError.ConfigTooLarge`  |
| INV-5 | Passphrase ≥ 8 символов                                                         | UI валидация + `Argon2idPassphraseKdf.derive` precondition   |
| INV-6 | Passphrase plaintext не оставляет копий (CharArray zeroize)                     | code review + unit test проверяет array zeroized after use   |
| INV-7 | RootKey никогда не уходит в Firestore plaintext                                 | type-level: `RecoveryVaultBlob.wrappedRootKey` — это encrypted bytes; нет API для plaintext upload |
| INV-8 | KeyRegistry изолирован per UID                                                  | alias prefix R-7, `MultiIdentityIsolationTest`               |
| INV-9 | Все wire-format поля имеют `schemaVersion` + `algorithm` от первого коммита    | rule 5, contracts tests                                      |

---

## 5. Error / Outcome shapes

**Single source of truth для sealed type declarations**: `contracts/*.md` (per A-1, A-2, A-3 findings — устраняем duplication между data-model.md и contracts/).

Полные Kotlin declarations см.:
- `KeyRegistryError` → [contracts/key-registry-v1.md](contracts/key-registry-v1.md)
- `VaultError` → [contracts/recovery-vault-v1.md](contracts/recovery-vault-v1.md)
- `IdentityError` → [contracts/identity-proof-v1.md](contracts/identity-proof-v1.md)
- `CryptoError` (re-used `AeadAuthFailed`, `ConfigTooLarge`, `AlgorithmUnsupported`) → F-CRYPTO contracts (spec 016)

**Краткий обзор** (для read-only ориентации, **не** для копирования в код):

| Sealed type | Cases | Owner contract |
|---|---|---|
| `KeyRegistryError` | `NotFound`, `StorageFailure(Throwable)`, `UnknownDek(String)`, `RootKeyUnavailable` | key-registry-v1.md |
| `RootKeyError` | `KeystoreInvalidated`, `StorageFailure(Throwable)`, `RecoveryRequired` | (declared в `RootKeyManager.kt`, no separate contract) |
| `VaultError` | `Unauthorized`, `NotFound`, `Network(Throwable)`, `Conflict(String)`, `Malformed(Throwable)` | recovery-vault-v1.md |
| `RecoveryError` | `WrongPassphrase`, `MalformedVault`, `NoVaultPresent`, `TooManyAttempts` | (declared в `Argon2idPassphraseKdf.kt`/recovery flow) |
| `IdentityError` | `NoSupportedProvider`, `Cancelled`, `Failure(Throwable)` | identity-proof-v1.md |

`CryptoError` (`AeadAuthFailed`, `ConfigTooLarge`, `AlgorithmUnsupported`) — re-uses F-CRYPTO sealed type (FR-026).

---

## Краткое резюме (для не-разработчика)

**Какие «сущности» (entities) есть в F-5**:

1. **AuthIdentity** — Google-аккаунт пользователя; берётся из F-4, F-5 только использует UID.
2. **RootKey** — главный 256-битный ключ; хранится только в защищённом хранилище телефона (Android Keystore) + зашифрованная копия в облаке.
3. **WrappedDek** — зашифрованный «маленький» ключ под конкретную задачу (config / pair / photo); все хранятся под защитой RootKey.
4. **KeyRegistry** — реестр всех WrappedDek для одного UID; добавлять новые ключи можно без переделок.
5. **Passphrase** — пароль восстановления; **не сохраняется** нигде, живёт только в момент ввода и сразу обнуляется.
6. **RecoveryVaultBlob** — зашифрованная копия RootKey в Firestore; имеет `schemaVersion` и `algorithm` для будущей миграции криптоалгоритмов.
7. **SealedConfig** — зашифрованный ConfigDocument в Firestore; то, что Firebase видит как «opaque bytes».

**Главные правила (инварианты)**:

- RootKey никогда не покидает память дольше одной криптооперации.
- UID1 и UID2 на одном телефоне полностью изолированы (свои наборы ключей).
- ConfigDocument в plaintext не превышает 256 КБ.
- Все wire-форматы имеют `schemaVersion` с первого коммита — задел на будущее.

**Lifecycle**:

- Первое использование: создаём RootKey → wrap → создаём RecoveryVaultBlob под passphrase → отправляем в облако.
- Потеря телефона: Sign-In на новом → пароль → восстановление RootKey → все DEKs автоматически снова работают.
- Sign-out + Sign-in на том же телефоне: ничего не меняется, RootKey уже в Keystore.
