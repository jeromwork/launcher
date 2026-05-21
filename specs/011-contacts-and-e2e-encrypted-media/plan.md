# Implementation Plan: Contacts Photos and E2E Encrypted Private Media

**Branch**: `011-contacts-and-e2e-encrypted-media`
**Date**: 2026-05-21
**Spec**: [spec.md](spec.md)
**Input**: post-clarify (C-1..C-8 в §Clarifications зафиксированы mentor-сессией 2026-05-21).

---

## Summary

Спек 011 даёт проекту **крипто-фундамент для end-to-end зашифрованных приватных медиа**: per-device asymmetric key pairs (X25519) поверх Android Keystore, hybrid encryption (XChaCha20-Poly1305 для blob'a + `crypto_box_seal` для CEK), membership-agnostic envelope format с массивом `recipients` произвольной длины. В 011 list получателей всегда содержит **одного** партнёра пары (одностороннее admin→Managed направление из спеков 7-9 сохраняется). Архитектурное ядро — `RecipientResolver` интерфейс, который для 011 имеет одну реализацию (`PairRecipientResolver`), и три гарантированные будущие реализации в roadmap (~015 двусторонние пары, ~016 группы, ~017 multi-device).

С точки зрения видимой функциональности 011 закрывает один пробел: фотография контакта из Android Contacts Picker (спек 009) и фото личных документов **впервые попадают на телефон бабушки в виде реальной картинки** вместо `null`. Blob'ы хранятся в Firebase Storage в зашифрованном виде; reference counting с учётом history snapshots из спека 009; cleanup при revoke link через расширение `Link.KNOWN_SUBCOLLECTIONS`.

---

## Technical Context

**Language/Version**: Kotlin 2.0.20+ (KMP common; existing project version).
**Primary Dependencies (new in 011)**:
- **Lazysodium-android** (`com.goterl:lazysodium-android:5.x.x`) — JNI обёртка над нативной libsodium. Поставляет `.so` под 4 ABI Android. Все крипто-операции 011 идут через эту библиотеку.
- **JNA** (`net.java.dev.jna:jna:5.x.x@aar`) — runtime для Lazysodium (нативный bridge).
- Никаких новых vendor SDKs кроме крипто. Reuses Firebase BoM (Firestore / Auth / Storage / Messaging) из спеков 7-8.

**Storage**:
- Firebase Storage objects `/links/{linkId}/private-media/{uuid}` (encrypted blobs, leave device).
- Firestore documents (existing): `/links/{linkId}/devices/{deviceId}` (NEW — Pub-key publication), `/links/{linkId}/config/current` (extended via уже зарезервированного `Contact.photoRef`). Reference counting/housekeeping выполняется **полностью client-side** через `BlobReferenceLedger` SQLite таблицу (см. data-model.md §3) — никаких server-side index документов не вводится в 011.
- SQLDelight таблицы (расширяют существующую DB из спека 008): `PrivateMediaCache` (расшифрованные blob bytes per uuid с TTL), `BlobReferenceLedger` (локальный учёт known references для admin-side cleanup).
- Android Keystore — private key per device (`alias = "launcher_device_priv_v1"`), hardware-backed при доступности (StrongBox / TEE).

**Testing**: kotlin-test (common), JUnit + Robolectric (androidUnitTest), Firebase Emulator + Storage Emulator (integration), Konsist (fitness functions). Та же стек, что в 007/008. **Дополнительно для крипто**: detached vector tests (RFC 8439 для XChaCha20-Poly1305, RFC 7748 для X25519, libsodium official test vectors для `crypto_box_seal`).

**Target Platform**: Android API 30+ (project minSdk per 007). iOS out of scope (ADR-001), но crypto layer параллельно-готов: libsodium доступен на iOS через ту же C-библиотеку; commonMain код isolates platform-specific bits через port mechanism (`SecureKeystore`, `AeadCipher`, `AsymmetricCrypto` — interfaces в commonMain, real adapters в androidMain). Будущая iOS-implementation добавит iosMain-adapters без изменения portов.

**Project Type**: KMP library + Android app (existing structure из 007/008); один новый gradle модуль НЕ создаётся — крипто и Storage adapters встают в существующую структуру `core/` (см. Module Map ниже).

**Performance Goals**:
- SC-001: фото на плитке у бабушки в ≤ 30 сек p95 от admin tap'а через Picker до отрисовки (covers full path: encrypt → upload → push → download → decrypt → render).
- Шифрование blob'a 200 KB на admin device: ≤ 100 ms p95 (Pixel 4a baseline).
- Расшифровка blob'a 200 KB на Managed device: ≤ 80 ms p95.
- Cold start не должен регрессировать — расшифровка blob'ов **lazy** на тап по плитке, **не** на ConfigApplier. Cache hot-path не более 50 ms.
- Storage object size ≤ 500 KB per photo (на admin side compress JPEG до этого budget перед шифрованием).

**Constraints**:
- APK delta: Lazysodium-android `.so` под 4 ABI ≈ 1.0-1.2 MiB. **Это значительный delta** поверх уже узкого budget'a спеков 007/008. План mitigation — ABI splits в release build (см. Risks §R3).
- Никаких новых runtime permissions (`READ_CONTACTS` уже взят спеком 009; FOREGROUND_SERVICE для длинных upload'ов — не нужен, upload идёт быстро).
- Один новый one-way door — **выбор libsodium как библиотеки** (см. spec.md §Clarifications C-4); все остальные one-way doors из C-1..C-8 уже зафиксированы exit ramp'ом на уровне envelope (cipherSuiteId + schemaVersion).
- Plain-text photo bytes ≠ Storage / logs / crash reports (FR-051/052). Память — `sodium_mlock()` на буферы где возможно.

**Scale/Scope**:
- Per pair: ≤ 50 контактов с фото + ≤ 30 документов = ≤ 80 blob'ов. Avg blob 200 KB → ≤ 16 MB / pair.
- При retention 10 history snapshots (спек 009 Q2): верхняя оценка ≤ 30 контактов × 10 snapshots = 300 references на пару; реальное число uniqueusterns << из-за дедупликации.
- Firebase Storage Spark plan: 5 GB total, 1 GB/day download, 20K writes/day. При 1000 пар × 16 MB ≈ 16 GB → **выход за лимит Spark при success**. Не блокер для 011 (есть запас), но **обязательно отметить в server-roadmap как trigger миграции на Blaze или свой Storage**.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

**Run**: 2026-05-21 via `procedure-constitution-check`. Inline-полная сверка по 8 gates Article XVI ниже. Detailed report (если потребуется доработка) — в [constitution-check.md](constitution-check.md) (создаётся при FAIL).

```
Gate 1 Architecture           : PASS — port-adapter паттерн сохранён; новые порты SecureKeystore, AsymmetricCrypto, AeadCipher, EncryptedMediaStorage, RecipientResolver; адаптеры в androidMain; no new gradle module
Gate 2 Core/System Integration: PASS — system events не нужны; Android Keystore wrapped via SecureKeystore port; Firebase Storage wrapped via EncryptedMediaStorage adapter
Gate 3 Configuration          : PASS — envelope wire-format несёт schemaVersion + cipherSuiteId с первого commit; backward-compat policy explicit (additive only); roundtrip tests planned; namespace `private:` уже зарезервирован спеком 006
Gate 4 Required Context Review: PASS — constitution + 6 ADRs + roadmap + compliance + server-roadmap + спек 6/7/8/9 dependencies все linked в §Required Context Review
Gate 5 Accessibility          : PASS — UI surface 011 ограничена двумя точками (admin: «+ документ» button, Managed: error placeholder + tap-to-view фото); senior-safe baseline из спека 003+010 наследуется; нет новых dialogs/screens, требующих специального cognitive-load review
Gate 6 Battery/Performance    : PASS — крипто-операции event-driven (only on add/apply), no polling, no background services; cache TTL для расшифрованных blob'ов в SQLite; perf-checkpoint в Phase 11
Gate 7 Testing                : PASS — mock-first via FakeAeadCipher/FakeAsymmetricCrypto/FakeSecureKeystore/FakeEncryptedMediaStorage; contract tests с official libsodium vectors; integration via Storage Emulator; fitness via Konsist; 9 levels (см. §Test Strategy)
Gate 8 Simplicity             : PASS WITH JUSTIFICATION — RecipientResolver интерфейс с одной реализацией (`PairRecipientResolver`) формально нарушает CLAUDE.md rule 4 «no single-impl interface». Обоснование явное в spec.md C-8 + ADR-007 (создаётся в Phase 1): roadmap гарантирует ≥3 будущих реализации (~015 BidirectionalPairResolver, ~016 GroupRecipientResolver, ~017 MyDevicesRecipientResolver). Это архитектурная инвестиция в membership-agnostic envelope per Article XI («minimum viable architecture, not minimum viable product»).

OVERALL: 8 PASS (1 with documented justification per Article XVII §3), 0 FAIL — plan is COMPLETE.
```

**RecipientResolver exception** (Gate 8) — documented per Article XVII §3:
- **Article affected**: Article XI (Anti-bloat / minimum viable architecture); CLAUDE.md rule 4.
- **Reason**: главное требование пользователя в discussion 2026-05-21 — «механизм шифрования не должен быть завязан на механизм взаимодействия телефонов». Без `RecipientResolver` seam'a envelope format становится hardcoded под «один получатель = другой член пары»; миграция на группы/multi-device в будущем = breaking change envelope.
- **Scope**: один интерфейс + одна реализация + одна fake-реализация в 011.
- **Risk**: low — интерфейс простой (1 метод), trivial test parity.
- **Mitigation**: вторая и третья реализации появятся в спеках ~016, ~017 (roadmap entries committed).
- **Removal condition**: если по итогам ~018 решим, что multi-recipient никогда не понадобится — упростить обратно на hardcoded single recipient. Маловероятно.

---

## Architecture

### Module map (existing structure, no new gradle modules)

```text
core/
├── src/commonMain/kotlin/com/launcher/
│   ├── api/
│   │   ├── crypto/                            ← NEW (this spec)
│   │   │   ├── DeviceKeyPair.kt               # domain: PublicKey + PrivateKey opaque
│   │   │   ├── EncryptedEnvelope.kt           # domain: schemaVersion, cipherSuiteId, ciphertext, recipients[], nonce, mac
│   │   │   ├── Recipient.kt                   # domain: deviceId, encryptedCEK
│   │   │   ├── ContentEncryptionKey.kt        # domain: opaque 32 bytes; not Serializable
│   │   │   ├── AeadCipher.kt                  # PORT: encrypt(plaintext, key, aad) / decrypt
│   │   │   ├── AsymmetricCrypto.kt            # PORT: generateKeyPair() / sealCEK(cek, pub) / unsealCEK(blob, priv)
│   │   │   ├── SecureKeystore.kt              # PORT: storePrivate(alias, key) / loadPrivate(alias) / delete(alias)
│   │   │   ├── RecipientResolver.kt           # PORT: resolveRecipients(linkId) → List<DeviceIdentity>
│   │   │   ├── EncryptedMediaStorage.kt       # PORT: upload(uuid, envelope) / download(uuid) / delete(uuid) / exists(uuid)
│   │   │   ├── CryptoEnvelopeWireFormat.kt    # kotlinx.serialization; schemaVersion 1
│   │   │   └── CryptoError.kt                 # sealed: KeyNotFound, MacFailed, BlobMissing, CipherSuiteUnsupported
│   │   ├── media/                             ← NEW (this spec)
│   │   │   ├── PrivateMediaReference.kt       # value class wrapping "private:<uuid>" namespace iconId
│   │   │   ├── PrivateMediaCache.sq           # SQLDelight schema (расширяет ConfigSyncDatabase из 008)
│   │   │   ├── BlobReferenceLedger.sq         # SQLDelight: known references for cleanup
│   │   │   ├── PrivateMediaResolver.kt        # ICONSTORAGE namespace resolver implementing IconStorage for `private:` per spec 006
│   │   │   └── DecryptCache.kt                # in-memory LRU расшифрованных bytes
│   │   ├── identity/                          ← NEW (this spec)
│   │   │   ├── DeviceIdentity.kt              # domain: deviceId + publicKey
│   │   │   ├── DeviceIdentityWireFormat.kt    # Firestore document /devices/{deviceId}
│   │   │   ├── DeviceIdentityRepository.kt    # PORT: publishOwn() / fetchPeer(deviceId)
│   │   │   └── DeviceIdentityError.kt
│   │   ├── link/                              ← EXISTING (007); EXTEND KNOWN_SUBCOLLECTIONS
│   │   ├── config/                            ← EXISTING (008); EXTEND Contact.photoRef usage; no new fields
│   │   └── ...
│   └── fake/
│       ├── crypto/                            ← NEW (fakes для тестов)
│       │   ├── FakeAeadCipher.kt              # in-memory XOR-stub-cipher (НЕ настоящее шифрование — для тестов поведения!)
│       │   ├── FakeAsymmetricCrypto.kt        # deterministic key pair gen
│       │   ├── FakeSecureKeystore.kt          # in-memory map
│       │   ├── FakeRecipientResolver.kt       # programmable list
│       │   └── FakeEncryptedMediaStorage.kt   # in-memory map<uuid, envelope>
│       └── identity/
│           └── FakeDeviceIdentityRepository.kt
├── src/androidMain/kotlin/com/launcher/
│   ├── adapters/crypto/                       ← NEW (real adapters)
│   │   ├── LibsodiumAeadCipher.kt             # XChaCha20-Poly1305 via Lazysodium
│   │   ├── LibsodiumAsymmetricCrypto.kt       # X25519 + crypto_box_seal via Lazysodium
│   │   ├── AndroidKeystoreSecureKeystore.kt   # Android Keystore wrapping
│   │   ├── PairRecipientResolver.kt           # single-impl: returns other pair member
│   │   └── FirebaseEncryptedMediaStorage.kt   # Firebase Storage SDK
│   ├── adapters/identity/                     ← NEW
│   │   └── FirestoreDeviceIdentityRepository.kt
│   ├── adapters/media/                        ← NEW
│   │   ├── SqlDelightPrivateMediaCache.kt
│   │   └── SqlDelightBlobReferenceLedger.kt
│   └── di/                                    ← EXISTING; extends Koin modules
└── src/commonTest/                            ← NEW tests
    ├── api/crypto/
    │   ├── CryptoEnvelopeWireFormatTest.kt    # roundtrip + backward-compat
    │   ├── AeadCipherContractTest.kt          # RFC 8439 vectors
    │   ├── AsymmetricCryptoContractTest.kt    # X25519 + crypto_box_seal vectors
    │   ├── EnvelopeRecipientsTest.kt          # multi-recipient roundtrip (даже если 011 использует 1)
    │   └── CryptoErrorTest.kt
    └── ...

app/                                            ← EXISTING
├── src/main/kotlin/com/launcher/ui/
│   ├── settings/                              ← EXISTING (009); extended: contact picker теперь читает Photo URI
│   ├── admin/contacts/                        ← EXISTING (009); extended: photo upload progress UI
│   ├── admin/documents/                       ← NEW (this spec, US-2): «+ документ» flow
│   │   ├── DocumentPickerScreen.kt
│   │   ├── DocumentPickerComponent.kt
│   │   └── DocumentLabelDialog.kt
│   └── tile/                                  ← EXISTING; extended: tap on document tile → full-screen viewer
│       └── DocumentViewerScreen.kt            # NEW: full-screen photo viewer для документов
└── src/main/AndroidManifest.xml               ← no changes (no new permissions)

push-worker/                                    ← NO CHANGES
└── src/                                       # ничего не меняем — крипто не идёт через Worker

specs/011-contacts-and-e2e-encrypted-media/
├── spec.md                                    # ✅ created в speckit-specify
├── plan.md                                    # ← THIS FILE
├── research.md                                # ← created в Step 3
├── data-model.md                              # ← created в Step 3
├── quickstart.md                              # ← created в Step 3 (Lazysodium setup)
├── contracts/                                 # ← created в Step 3
│   ├── crypto-envelope.md                     # envelope wire format
│   ├── device-identity.md                     # /devices/{deviceId} Firestore document
│   └── encrypted-media-storage.md             # Firebase Storage layout + Rules
└── tasks.md                                   # Phase 2 — created via /speckit.tasks
```

**Konsist gates** (extend спек 007 Phase 10 + спек 008):
- `commonMain/api/crypto/` MUST NOT import `com.goterl.lazysodium.*`, `android.*`, `com.google.firebase.*`.
- `commonMain/api/media/` MUST NOT import `android.graphics.*`, `androidx.*`, `com.google.firebase.storage.*`.
- `commonMain/api/identity/` MUST NOT import `com.google.firebase.*`.
- `androidMain/adapters/crypto/LibsodiumAeadCipher.kt` MUST keep Lazysodium types internal — public API only project-defined `ContentEncryptionKey` and `EncryptedEnvelope`.
- `androidMain/adapters/crypto/AndroidKeystoreSecureKeystore.kt` MUST keep `KeyStore`, `KeyGenerator` types internal.
- Firebase Storage types confined to `FirebaseEncryptedMediaStorage.kt`.

### Port-adapter shape

| Port (commonMain) | Real adapter (androidMain) | Fake adapter (commonTest) | Consumer |
|---|---|---|---|
| `AeadCipher` | `LibsodiumAeadCipher` (XChaCha20-Poly1305) | `FakeAeadCipher` (XOR-stub for behavioural tests only) | encrypt/decrypt blob; FR-011/053 |
| `AsymmetricCrypto` | `LibsodiumAsymmetricCrypto` (X25519 + `crypto_box_seal`) | `FakeAsymmetricCrypto` (deterministic) | seal CEK for recipient pub; unseal own CEK; FR-010 |
| `SecureKeystore` | `AndroidKeystoreSecureKeystore` (Android Keystore + StrongBox if available) | `FakeSecureKeystore` (in-memory map) | store/load priv keys; FR-002 |
| `RecipientResolver` | `PairRecipientResolver` (returns other pair member by linkId) | `FakeRecipientResolver` (programmable list) | resolve recipients for blob encrypt; **C-8** |
| `EncryptedMediaStorage` | `FirebaseEncryptedMediaStorage` (Firebase Storage SDK) | `FakeEncryptedMediaStorage` (in-memory blob map) | upload/download/delete blobs; FR-020/023/030 |
| `DeviceIdentityRepository` | `FirestoreDeviceIdentityRepository` | `FakeDeviceIdentityRepository` | publish own pub, fetch peer pub; pairing extension |

**Per CLAUDE.md rule 4**: каждый port имеет ≥2 реализации (real + fake) с первого commit'a — никаких speculative single-impl interfaces за исключением `RecipientResolver` (см. Gate 8 justification выше).

### Data flow — Encryption happy path (US-1, US-2)

```text
admin device                                    Firebase                       Managed device
   │                                             │                                  │
   │ (user picks contact with photo OR document) │                                  │
   ▼                                             │                                  │
[Get Pub_managed from DeviceIdentityRepository.fetchPeer(deviceId)]                  │
   │                                             │                                  │
   │ if not yet cached locally → read from /links/{linkId}/devices/{managedDeviceId} │
   │                                             ◄──────────────────────────────────┤
   │                                             │                                  │
[Read photo bytes from Android Contacts URI OR Gallery URI]                         │
   │                                             │                                  │
[Compress JPEG to ≤ 500 KB (FR-cap)]            │                                  │
   │                                             │                                  │
[Generate CEK = 32 random bytes (libsodium)]    │                                  │
   │                                             │                                  │
[blob_ct = AeadCipher.encrypt(jpeg_bytes, CEK, aad=envelope_metadata)]              │
   │                                             │                                  │
[For each recipient (here: 1):                  │                                  │
   sealed_cek = AsymmetricCrypto.sealCEK(CEK, Pub_managed)]                         │
   │                                             │                                  │
[Compose envelope:                              │                                  │
   { schemaVersion: 1,                          │                                  │
     cipherSuiteId: "xchacha20poly1305_x25519_sealed_v1",                           │
     nonce: ...,                                │                                  │
     recipients: [{deviceId: managedDeviceId, sealedCEK: ...}],                     │
     ciphertext: blob_ct,                       │                                  │
     mac: ... }]                                │                                  │
   │                                             │                                  │
[EncryptedMediaStorage.upload(uuid, envelope)] ─►(Firebase Storage путь /links/{linkId}/private-media/{uuid})
   │                                             │                                  │
[Write photoRef = "private:<uuid>" в /config.contacts[i] OR /config.flows[].slots[].iconId]
   │                                             │                                  │
[push /config (existing спек 008 flow)] ───────►(Firestore /links/{linkId}/config/current)
                                                 │
                                                 ▼  (FCM trigger)
                                                                                    │
                                                                                    │
                                          ConfigApplier (спек 008) reads /config    │
                                                                                    ▼
                                                                  Sees photoRef = "private:<uuid>"
                                                                  Calls IconStorage.resolve("private:<uuid>")
                                                                                    │
                                                                  PrivateMediaResolver intercepts namespace
                                                                                    │
                                                                  Check DecryptCache (in-memory LRU)
                                                                                    │
                                                                  If miss: EncryptedMediaStorage.download(uuid)
                                                                                    │ ◄──── (Storage)
                                                                                    │
                                                                  Read own priv from AndroidKeystoreSecureKeystore
                                                                                    │
                                                                  Find recipient entry for own deviceId
                                                                                    │
                                                                  unsealed_cek = AsymmetricCrypto.unsealCEK(sealedCEK, priv)
                                                                                    │
                                                                  plaintext = AeadCipher.decrypt(ciphertext, unsealed_cek, aad)
                                                                                    │
                                                                  If success: cache in DecryptCache + show in tile
                                                                  If MAC failed: PartialApplyEmitter.emit(media_decrypt_failed)
                                                                                    │
                                                                                    ▼
                                                                  Tile shows photo OR placeholder (graceful)
```

### Data flow — Pairing extension (US-5 / ADR-007)

```text
spec 007 pairing flow:                          spec 011 ADR-007 extension:
admin scans QR → pairing-token claimed          (additive — no breaking change to спека 007)
   │                                            │
   ▼                                            ▼
consent.allow on both                ──► Each device:
   │                                            │
   │                                            ├─ generate (Pub, Priv) via LibsodiumAsymmetricCrypto.generateKeyPair()
   │                                            ├─ store Priv in AndroidKeystoreSecureKeystore (alias = "launcher_device_priv_v1")
   │                                            └─ publish Pub to /links/{linkId}/devices/{deviceId} via DeviceIdentityRepository.publishOwn()
   │
   ▼
Link established (existing)                     Both devices know each other's deviceId from spec 007.
                                                On first encrypt: fetch peer's Pub from /devices/{peerDeviceId}.
                                                Cache locally for offline.

Pairing-token wire format не меняется (см. spec 011 §Architectural one-way doors ADR-007).
schemaVersion pairing-token остаётся 1.
```

### Data flow — Cleanup (US-3)

```text
admin removes contact "Маша" (photoRef = "private:<uuid>")
   │
   ▼
push new /config without Маша → FR-022 ConfigEditor commits
   │
   ▼
BlobReferenceLedger (local SQLite на admin side) считает refs:
   - current /config: 0 refs for <uuid>
   - history snapshots (спек 009 retention 10): check каждый
       - if any history snapshot ссылается на <uuid> → keep blob
       - if 0 references total → schedule deletion
   │
   ▼
Best-effort delete: EncryptedMediaStorage.delete(uuid) — async via WorkManager
   │
   ▼  (на success)
BlobReferenceLedger updates state for <uuid> = DELETED

revoke link (спек 007 FR-033) cleanup:
   │
   ▼
Link.KNOWN_SUBCOLLECTIONS список расширен с "private-media" (added в спеке 011)
LinkRegistry.revoke(linkId) enumerates все subcollections + Storage paths
   │
   ▼
Recursive delete /links/{linkId}/private-media/* в Storage + /devices/* в Firestore
   │
   ▼
SecureKeystore.delete("launcher_device_priv_v1_link_<linkId>") — приватный ключ wipe'нут локально
```

---

## Data Model

См. [data-model.md](data-model.md). Новые domain types:

- `DeviceIdentity(deviceId: DeviceId, publicKey: PublicKey, createdAt: Timestamp)`
- `DeviceKeyPair(publicKey: PublicKey, privateKey: PrivateKey)` — internal; PrivateKey is opaque handle to Keystore alias, не Serializable.
- `EncryptedEnvelope(schemaVersion: Int, cipherSuiteId: String, nonce: ByteArray, recipients: List<Recipient>, ciphertext: ByteArray, mac: ByteArray)`
- `Recipient(deviceId: DeviceId, sealedCEK: ByteArray)`
- `ContentEncryptionKey(bytes: ByteArray)` — opaque, не Serializable, `clear()` обнуляет в finally.
- `PrivateMediaReference(uuid: Uuid)` — value class wrapping `private:<uuid>` namespace iconId.
- `BlobReference(uuid: Uuid, refCount: Int, lastSeenInConfig: Boolean, lastSeenInHistorySlot: Int?)` — local ledger entry.
- `CryptoError` — sealed:
  - `KeyNotFound(alias: String, cause: Throwable?)` — Keystore не нашёл priv key.
  - `MacFailed(uuid: Uuid)` — расшифровка не прошла MAC проверку.
  - `BlobMissing(uuid: Uuid)` — Storage вернул 404.
  - `CipherSuiteUnsupported(suiteId: String)` — envelope с future cipherSuiteId.
  - `RecipientNotFound(deviceId: DeviceId)` — envelope не содержит recipient entry для own deviceId.

---

## Wire Formats

3 new wire formats (each in [contracts/](contracts/)):

| Wire format | File | Origin | Notes |
|---|---|---|---|
| `EncryptedEnvelope` (per-blob in Firebase Storage) | [contracts/crypto-envelope.md](contracts/crypto-envelope.md) | NEW in 011 | FR-010..013; schemaVersion: 1; cipherSuiteId: `xchacha20poly1305_x25519_sealed_v1` |
| `/links/{linkId}/devices/{deviceId}` (Firestore document) | [contracts/device-identity.md](contracts/device-identity.md) | NEW in 011 | FR-001..005; pub key publication |
| Firebase Storage path layout `/links/{linkId}/private-media/{uuid}` | [contracts/encrypted-media-storage.md](contracts/encrypted-media-storage.md) | NEW in 011 | FR-012/013/050; Security Rules; reference counting policy |

Wire-format discipline (CLAUDE.md §5):
- Envelope: `schemaVersion: Int` + `cipherSuiteId: String` — оба first-class fields с первого commit'a.
- Roundtrip + backward-compat read tests required (`commonTest/resources/wire-format/`).
- Additive-only changes; добавление новых `cipherSuiteId` value = forward-compat (reader returns `CipherSuiteUnsupported`, не падает). Rename/remove fields requires schemaVersion bump 1→2 + reader-migration code.

Также **extends** существующие wire formats спека 008 (не breaking):
- `Contact.photoRef`: nullable String, **впервые** в 011 содержит реальное значение (раньше всегда `null`). Контракт не меняется ([спек 008 data-model.md:64](../008-bidirectional-config-sync/data-model.md#L64)).
- `PartialReason`: enum value `media_decrypt_failed` **впервые** в 011 эмитируется реальным кодом (раньше зарезервировано) ([спек 008 state-applied.md:65](../008-bidirectional-config-sync/contracts/state-applied.md#L65)).
- `Link.KNOWN_SUBCOLLECTIONS`: добавляется `"private-media"` для recursive revoke (extends [Link.kt:37-44](../../core/src/commonMain/kotlin/com/launcher/api/link/Link.kt#L37)).
- Pairing-token wire-format (спек 007): **не меняется**. Не добавляется `pairingKey` field (см. ADR-007 — обмен Pub-ключами идёт через `/devices/` Firestore document после `consent.allow`, не через QR).

---

## Dependency Impact

### New dependencies (Android only — androidMain)

| Dependency | Version | Justification | Article XIII |
|---|---|---|---|
| `com.goterl:lazysodium-android` | 5.1.x (latest stable; verify in research.md §1) | crypto implementation per spec.md C-4; only proven libsodium binding for Android | OK — мейнстрим библиотека, активная поддержка, no vendor lock-in |
| `net.java.dev.jna:jna@aar` | 5.x (transitive of Lazysodium) | native bridge for Lazysodium | OK — transitive |

### No new common deps

- UUID via `kotlin.uuid.Uuid` (existing).
- Serialization via existing `kotlinx-serialization-json` (existing).
- SQLDelight: spec 011 расширяет existing `ConfigSyncDatabase` из спека 008 (новые таблицы `PrivateMediaCache`, `BlobReferenceLedger`), **никаких новых SQLDelight зависимостей не добавляется**.

### APK delta budget

- Спек 008 baseline (after R8): TBD из perf-checkpoint спека 008.
- Спек 011 addition: Lazysodium-android `.so` files под 4 ABI ≈ 1.0-1.2 MiB total.
- **Mitigation**: ABI splits в release build (один APK per ABI — каждый получает только свой `.so`, экономия до 75% от crypto deps).
- **Phase 11 perf-checkpoint** валидирует APK delta < +0.5 MiB после splits.

### Firebase Storage impact (NEW в проекте)

- Спеки 6-10 не использовали Firebase Storage (только Firestore).
- Спек 011 **первый**, кто включает Firebase Storage SDK + Storage Rules.
- Spark plan: 5 GB storage / 1 GB download daily / 20K writes daily.
- Reasonable usage: 1000 пар × avg 8 MB blob data = 8 GB → **выход за Spark при ≥1000 пар**.
- **Action**: добавить в `docs/dev/server-roadmap.md` запись `SRV-MEDIA-001: миграция Storage на свой server при превышении 5GB`. **TODO в Phase 0** (см. Phasing ниже).

---

## Test Strategy

Per CLAUDE.md §6 (mock-first) и §7 (fitness functions). 9 levels (расширяют спек 008's 8):

| Level | What | Where |
|---|---|---|
| 1. Domain unit | Envelope structure validation; refCount accounting; CipherSuiteId parsing | `commonTest/api/crypto/EnvelopeTest.kt`, `BlobReferenceTest.kt` |
| 2. Contract crypto | Official libsodium test vectors для XChaCha20-Poly1305, X25519, `crypto_box_seal`; RFC 8439 vectors | `commonTest/api/crypto/AeadCipherContractTest.kt`, `AsymmetricCryptoContractTest.kt` |
| 3. Contract wire | Roundtrip + backward-compat reads для CryptoEnvelope, DeviceIdentity; fixtures в `commonTest/resources/wire-format/` | `CryptoEnvelopeWireFormatTest.kt`, `DeviceIdentityWireFormatTest.kt` |
| 4. Fake-adapter | Behavioural parity Fake* vs Libsodium* via shared contract tests; FakeEncryptedMediaStorage roundtrip | `commonTest/fake/crypto/*ContractTest.kt` |
| 5. Firebase Emulator | Storage upload/download/delete; Storage Rules (admin+Managed only, foreign uid denied); Firestore Rules для `/devices/`; revoke recursive delete includes Storage path | `androidUnitTest` with Firebase Emulator + Storage Emulator |
| 6. Recipient resolver | PairRecipientResolver возвращает other pair member; behaviour with non-existing peer | `androidUnitTest/PairRecipientResolverTest.kt` |
| 7. UI Compose | Tile с photo (расшифрованной); placeholder при `media_decrypt_failed`; document picker flow; document viewer (US-2) | `androidUnitTest` with Robolectric, StateRestorationTester для viewer state |
| 8. Fitness (Konsist) | commonMain/api/crypto/* clean of vendor types; Lazysodium types confined; Firebase Storage types confined | `core/src/test/kotlin/.../KonsistEncryptedMediaTest.kt` |
| 9. Smoke / manual | 2-device e2e: admin picks contact-with-photo → bot wave 30s → бабушка видит фото; document add-view-remove cycle; revoke wipes Storage | manual; documented в `smoke/011/README.md` |

**Test fixtures**: `commonTest/resources/wire-format/`:
- `crypto-envelope-v1-single-recipient.json` (типичный 011 use case)
- `crypto-envelope-v1-multi-recipient.json` (3 recipients — forward-compat test, 011 не использует, но roundtrip обязан работать)
- `crypto-envelope-v0-synthetic.json` (для backward-compat read)
- `device-identity-v1.json`
- `libsodium-vectors/` (official test vectors — copied for reproducibility)

**Test isolation для крипто**: `FakeAeadCipher` и `FakeAsymmetricCrypto` намеренно НЕ настоящее шифрование (XOR-stub) — они only для тестов поведения слоёв выше (refCount, retry logic, error mapping). Настоящий шифр-механизм тестируется ТОЛЬКО на уровне 2 (libsodium contract tests с official vectors) и уровне 5 (Storage Emulator integration). Это явное и documented разделение: **никакой code в production не должен использовать `Fake*` cipher**.

---

## Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| **R1** | **One-way door — выбор Lazysodium как библиотеки** | — | High (миграция = подмена 6 adapter'ов + Konsist re-verification) | Documented exit ramp: `cipherSuiteId` в envelope. Смена библиотеки в будущем = новые blob'ы с другим `cipherSuiteId`, старые читаются по правилам v1. См. research.md §1. |
| R2 | Android Keystore quirks на Xiaomi/Huawei/Samsung — потеря ключей после OTA-обновления MIUI/EMUI | Medium | High (пара требует re-pairing) | Hardware-backed Keystore preferred (StrongBox/TEE); fallback на software-backed; user-facing UI «требуется re-pairing» при `KeyNotFound`; известный список OEM-quirks в research.md §3 |
| R3 | APK delta превышает +0.5 MiB budget из-за нативных .so | High (1.0-1.2 MiB до splits) | Medium | ABI splits в release build (4 APK на Play Store, один per ABI); install size per device ≈ 300 KiB; Phase 11 проверяет |
| R4 | Firebase Storage Spark plan лимит 5 GB | Medium (при success) | Medium | Server-roadmap entry SRV-MEDIA-001 + trigger миграции на Blaze; monitoring storage usage в admin UI |
| R5 | Cold start regression — DecryptCache populate на старте | Low | Medium | Lazy decrypt — расшифровка только on first tap on tile; в Cold start cache пустой, tile показывает placeholder, async decrypt в фоне; Phase 11 macrobenchmark |
| R6 | Blob upload падает после `/config` push → orphan blob ИЛИ /config refers к no-existing blob | Medium | Medium | Upload **до** /config push (FR-021); BlobReferenceLedger ловит mismatch; background reconciler в Phase 8 |
| R7 | Bytes plaintext в logs / crash reports / analytics | Low (with discipline) | Critical (privacy breach) | Конкретный FR-051/052; Konsist rule «no ByteArray to Log.d/Timber»; manual code review checklist; `sodium_mlock()` где applicable |
| R8 | MITM при первом обмене Pub-ключами через Firestore | Low (Firestore TLS) | High (e2e bypass) | TLS Firestore + Security Rules ограничивают write own Pub только own uid; safety-numbers UI deferred to future spec (см. spec §Open questions) |
| R9 | Reference counting bug — blob удалён, но references in history | Medium | Medium (rollback shows broken tile) | Unit tests на BlobReferenceLedger; integration test «add → rollback → blob still resolves»; conservative policy: delay delete by 24h grace period |
| R10 | DecryptCache memory pressure на старых устройствах | Low | Low | LRU eviction; max 10 entries; ≤ 5 MB total in memory |
| R11 | Backward compatibility: пара apgraded на 011 vs Managed на 010 | High (during rollout) | Low | Managed на 010 видит `private:<uuid>` → IconStorage возвращает Placeholder per FR-009 спека 006 (graceful — не падает). Когда оба обновятся — фото появится |
| R12 | libsodium ANR на старом железе (Android 7-class) | Low | Low | Все encrypt/decrypt — на `Dispatchers.Default`; никогда на UI thread; perf budget 100 ms p95 покрывает worst case |
| R13 | Pre-existing IconStorage не имеет namespace dispatcher | Medium | Medium | Phase 3: убедиться, что spec 006 IconStorage умеет routing по namespace; если нет — добавить PrivateMediaResolver как chained resolver (намерение спека 006 — он уже подготовлен per [icon-id-namespace.md:48](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md#L48)) |

---

## Required Context Review

Per Article XVI Required Context Review Gate, следующие документы консультировались (и нужны implementers):

**Constitution & rules**:
- [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — Articles I-XVI, особенно VIII (accessibility), IX (battery), XI (anti-bloat), XII §7 (context review), XIV (privacy/security), XVI (constitution check gates).
- [`CLAUDE.md`](../../CLAUDE.md) — rules 1 (domain isolation), 2 (ACL), 3 (one-way doors), 4 (MVA), 5 (wire-format), 6 (mock-first), 8 (server-roadmap).

**ADRs** (relevant subset):
- [`docs/adr/ADR-001-cross-platform-strategy.md`](../../docs/adr/ADR-001-cross-platform-strategy.md) — iOS out of scope; commonMain stays parity-ready. libsodium доступен для iOS, наша domain-layer изоляция позволяет добавить iOS-actual в будущем без переписки port'ов.
- [`docs/adr/ADR-004-localization-and-global-readiness.md`](../../docs/adr/ADR-004-localization-and-global-readiness.md) — strings для UI документов извлекаются.
- [`docs/adr/ADR-005-ui-stack-compose-multiplatform.md`](../../docs/adr/ADR-005-ui-stack-compose-multiplatform.md) — Compose Multiplatform для DocumentPicker / DocumentViewer.
- **ADR-007 (NEW в спеке 011)** — second subtype `TrustEdgeBootstrap` для per-device asymmetric keys + Pub-key publication в Firestore через `/devices/{deviceId}`. Создаётся в Phase 1.

**Product**:
- [`docs/product/roadmap.md`](../../docs/product/roadmap.md) §Spec 011 (lines 264-284) — feature scope; §Specs 015-018 (NEW от 2026-05-21) — будущие спеки, exit ramp для модели доверия.
- [`docs/product/senior-safe-launcher-plan.md`](../../docs/product/senior-safe-launcher-plan.md) если существует — baseline accessibility для DocumentViewer.

**Compliance**:
- [`docs/compliance/permissions-and-resource-budget.md`](../../docs/compliance/permissions-and-resource-budget.md) — обновляется: запись о POST_NOTIFICATIONS уточняется (см. spec §Clarifications C-6 — deferred to spec 012/013); запись READ_CONTACTS остаётся (уже в 009).
- [`docs/dev/server-roadmap.md`](../../docs/dev/server-roadmap.md) — добавляется запись **SRV-MEDIA-001** «Firebase Storage migration to own server when usage > 5GB» (Phase 0 action).
- [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md) — `TODO-DOC-001` (ADR-007) разрешается в Phase 1; `TODO-FUTURE-SPEC-007..010` (added 2026-05-21) — будущие спеки.

**Spec dependencies** (already in main):
- [`specs/006-provider-capabilities-and-health/contracts/icon-id-namespace.md`](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md) §Reserved namespaces — `private:` зарезервирован для 011; envelope-agnostic content type (см. spec.md C-5).
- [`specs/007-pairing-and-firebase-channel/`](../007-pairing-and-firebase-channel/) — pairing flow; ADR-007 расширяет post-claim handshake.
- [`specs/008-bidirectional-config-sync/data-model.md`](../008-bidirectional-config-sync/data-model.md) §Contact — `photoRef: String?` field; впервые получает non-null значение.
- [`specs/008-bidirectional-config-sync/contracts/state-applied.md`](../008-bidirectional-config-sync/contracts/state-applied.md) §PartialReason — `media_decrypt_failed` enum value; впервые эмитируется реальным кодом.
- [`specs/009-admin-mode-flows/`](../009-admin-mode-flows/) — Contacts Picker; 011 расширяет с photo upload.

**No ADRs needed beyond ADR-007**: libsodium choice и envelope structure documented в spec.md C-4 + research.md §1; не достигают порога ADR за исключением TrustEdgeBootstrap (modifying существующего concept из спека 007).

---

## Implementation Phasing

Mirrors спек 008's 12-phase layout, adapted для крипто специфики. Total estimate: 5-7 weeks.

| Phase | Goal | Gate to next phase |
|---|---|---|
| **0** | Env prep: Lazysodium-android dependency declared в `libs.versions.toml`; ABI splits configured; Firebase Storage SDK added; Storage Rules written + deployed to Firebase Emulator; `SRV-MEDIA-001` запись в server-roadmap.md; ADR-007 black draft создан | Build green с новыми deps; Storage Emulator тест-suite ready |
| **1** | ADR-007 finalized: per-device asymmetric keys + Pub-key publication; spec.md §Architectural one-way doors обновлён; domain types создаются (commonMain): DeviceIdentity, EncryptedEnvelope, CryptoError, PrivateMediaReference; ports defined | All commonTest unit tests для domain types green |
| **2** | Fake adapters: FakeAeadCipher (XOR-stub), FakeAsymmetricCrypto (deterministic), FakeSecureKeystore (memory), FakeRecipientResolver (programmable), FakeEncryptedMediaStorage (memory), FakeDeviceIdentityRepository; wire-format roundtrip + backward-compat tests (Level 3) | All Level 1-4 tests green |
| **3** | Real crypto adapters: LibsodiumAeadCipher (XChaCha20-Poly1305 via Lazysodium), LibsodiumAsymmetricCrypto (X25519 + crypto_box_seal), AndroidKeystoreSecureKeystore; pure crypto tests с official libsodium vectors (Level 2) | All Level 2 vector tests green; AndroidKeystoreSecureKeystore contract-parity с FakeSecureKeystore |
| **4** | Pairing extension: DeviceIdentityRepository real adapter (Firestore); pairing flow (спек 007) extended additive — after `consent.allow` оба устройства publishOwn() Pub; Security Rules для `/devices/{deviceId}` (write only own deviceId by own uid) | Firebase Emulator integration test: pairing → both Pub published; foreign uid write fails |
| **5** | Storage adapter: FirebaseEncryptedMediaStorage; Storage Rules (admin+Managed только, foreign uid denied); upload/download/delete round-trip integration test (Level 5) | Storage Emulator tests green |
| **6** | Recipient resolver: PairRecipientResolver (single-impl, takes linkId, returns other pair member's deviceId via Firestore state); contract test против Fake | Resolver returns correct peer для test pair; handles missing peer gracefully |
| **7** | Cleanup machinery: BlobReferenceLedger (SQLDelight); reference counting includes history snapshots; Link.KNOWN_SUBCOLLECTIONS extended; LinkRegistry.revoke() cleans Storage path | Unit test «add → remove → blob deleted»; integration test «revoke → Storage empty» |
| **8** | IconStorage integration: PrivateMediaResolver реализует IconStorage namespace dispatch для `private:`; DecryptCache LRU; lazy decrypt on tap; PartialReason.media_decrypt_failed emit at FR-040 categories | Tile shows photo от тестовых blob; placeholder on corrupted blob; partialApplyReasons populated |
| **9** | UI: admin contact picker extended (photo upload progress); admin documents flow (DocumentPickerScreen, DocumentLabelDialog, US-2); Managed tile с photo + DocumentViewerScreen full-screen | StateRestorationTester green; manual UI smoke на 2 устройствах |
| **10** | Konsist fitness gates: commonMain/api/crypto/ clean, lazysodium confined, firebase confined; перечисленные правила в §Module Map | Konsist tests green; 0 violations |
| **11** | Perf checkpoint: macrobenchmark cold start regression (cache miss path); APK delta после ABI splits; encrypt/decrypt timing на reference devices; storage usage; manual 2-device smoke | All SC pass; perf-checkpoint.md committed |
| **12** | Compliance docs update: permissions-and-resource-budget (POST_NOTIFICATIONS deferral noted), server-roadmap (SRV-MEDIA-001 finalized), backlog (TODO-FUTURE-SPEC-007..010 cross-references); novice summaries on all artifacts | Cross-artifact trace clean; analyze pass |

**Push policy**: per CLAUDE.md §Branching — push after each Phase. PR opens after Phase 0.

---

## Rollout / Verification

**Pre-merge gates**:
1. All 8 SC measurable outcomes met (особенно SC-001 ≤ 30s, SC-002 zero plaintext в logs, SC-005 revoke cleanup, SC-006 graceful decrypt failure).
2. libsodium contract tests green против official vectors (Level 2).
3. Storage Emulator integration tests green (Level 5).
4. Konsist fitness gates green (Level 8 / Phase 10).
5. APK delta after ABI splits ≤ +0.5 MiB (Phase 11).
6. Manual 2-device smoke documented в `smoke/011/README.md` (Phase 11).
7. `procedure-cross-artifact-trace` clean — FRs ↔ tasks ↔ contracts ↔ acceptance scenarios.
8. `/speckit.analyze` PASS.

**Post-merge**:
- Update [`docs/product/roadmap.md`](../../docs/product/roadmap.md) §Spec 011 status to «Готов».
- Update [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md): close `TODO-DOC-001` (ADR-007 written); cross-link TODO-FUTURE-SPEC-007..010 как «unblocked by 011».
- Update [`docs/dev/server-roadmap.md`](../../docs/dev/server-roadmap.md): finalize `SRV-MEDIA-001` as monitoring item.
- Trigger planning для следующего спека на critical path (012, 013, или 015 в зависимости от приоритетов).

---

## Open Items for tasks-phase

Из plan-level checklists прогнаны 3 critical (`domain-isolation`, `wire-format`, `meta-minimization`). 40 ✅ / 6 ⚠️ / 0 ❌. Два ⚠️ зафиксированы в этом plan.md (drop server-side index doc; clarify port mechanism — не expect/actual). Остальные 4 ⚠️ переходят в tasks.md как concrete remediation:

- **CHK007 (domain-isolation)** — в data-model.md §1 `PrivateKey` явно типизировать как opaque alias-based domain value (alias: String + sealed marker), не оставлять «opaque handle» как комментарий. **Task target**: Phase 1 ADR-007 finalization.
- **CHK003 (wire-format)** — ввести один `SUPPORTED_SCHEMA_VERSION` Kotlin-constant в `core/api/crypto/CryptoEnvelopeWireFormat.kt`, ссылаться на него из всех мест (envelope, device-identity); вместо магической литералы `1`. **Task target**: Phase 2 fake adapters + roundtrip tests.
- **CHK014 (wire-format)** — добавить SQLDelight migration roundtrip test (existing schema N-1 from спека 008 + new tables → schema N) в Test Strategy Level 4. **Task target**: Phase 7 BlobReferenceLedger + PrivateMediaCache.
- **CHK002 (meta-minimization, RecipientResolver)** — мониторить статус будущих спеков ~016/~017 в roadmap. Если ~016 не появится в backlog как committed work в течение 2 кварталов после релиза 011 — пересмотреть seam в follow-up. **Task target**: no immediate action; review item для cross-artifact-trace в `/speckit.analyze`.

---

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| `RecipientResolver` single-implementation interface в 011 | User explicit requirement (mentor session 2026-05-21): «механизм шифрования не должен быть завязан на механизм взаимодействия телефонов»; envelope must remain membership-agnostic for cheap migration to ~016 (groups) / ~017 (multi-device) | Hardcoded single recipient (other pair member) — переход к группам/multi-device потребует переделки envelope format и миграции уже залитых blob'ов; cost оценочно ≈ 2 недели vs 0 строк сейчас. Trade-off explicit в spec.md §Clarifications C-8 + ADR-007. |

(One row only — никаких других speculative abstractions в плане нет; все остальные ports имеют ≥2 реализации с первого commit'a per CLAUDE.md rule 4.)

---

<!-- novice summary -->

## TL;DR (простым языком, для бабушки-владельца проекта и для будущего AI)

**Что этот план описывает.** Это «строительный чертёж» для спека 011: какие новые модули кода появятся, какие библиотеки добавим, как мы будем тестировать каждый кусок, и в каком порядке всё это пилить (12 фаз, оценка 5-7 недель).

**Главные технические решения, которые здесь зафиксированы:**

1. **Берём библиотеку Lazysodium-android** (это обёртка над знаменитой libsodium — самой репутационной крипто-библиотекой в мире, ею пользуется Signal, Discord, многие другие). Никакого «самодельного шифрования» — только проверенная математика.

2. **Архитектура изолирована.** Шифрование происходит в отдельных модулях (`core/api/crypto/`, `core/adapters/crypto/`), и весь остальной код проекта **не знает деталей крипто** — он просто видит «зашифровать этот файл для этого получателя». Если завтра решим сменить библиотеку — поменяем один модуль, остальной код останется как есть.

3. **Шесть новых «портов»** (это интерфейсы — как розетки, в которые втыкаются разные реализации): AeadCipher (шифровать байты), AsymmetricCrypto (шифровать ключ для получателя), SecureKeystore (хранить приватный ключ в железе телефона), RecipientResolver (определять, кому шифровать), EncryptedMediaStorage (загружать-скачивать зашифрованные файлы), DeviceIdentityRepository (публиковать публичный ключ устройства). Каждый порт сразу имеет ДВЕ реализации — настоящую (для production) и тестовую (для unit-тестов), это требование CLAUDE.md.

4. **APK станет тяжелее на 1-1.2 MB** из-за нативных крипто-библиотек под 4 архитектуры процессоров. Решение — ABI splits: каждый пользователь скачает только под своё устройство (≈ 300 KB), не все 4 версии сразу.

5. **Firebase Storage впервые в проекте.** До 011 проект использовал только Firestore (документы), а здесь нам нужен файловый storage для зашифрованных blob'ов. Бесплатный план Spark даёт 5 GB — этого хватит до ~1000 пар пользователей; дальше — миграция на платный план или на свой сервер (записано в server-roadmap).

6. **Constitution Check пройден** — все 8 проверок конституции проекта (архитектура, конфигурация, accessibility, batter, тестирование, и т.д.) дают «PASS». Один пункт «с обоснованием»: интерфейс `RecipientResolver` имеет одну реализацию в 011, что обычно запрещено правилом 4 CLAUDE.md, но в нашем случае разрешено явно (см. одна строка в §Complexity Tracking), потому что три гарантированных реализации в будущих спеках.

7. **13 рисков выписаны явно** с mitigation. Главные: Android Keystore на Xiaomi (известная проблема — теряет ключи после OTA), APK delta (решается splits), Spark plan лимит Storage (записано на radar).

8. **Phases 0-12** дают пошаговый ход реализации. Каждая фаза имеет gate перед переходом к следующей. Phase 0 — подготовка окружения; Phase 1 — ADR-007; Phase 2-7 — core реализация; Phase 8 — UI; Phase 9-12 — финиш.

**Что дальше:** этот plan.md уйдёт в `/speckit.tasks` — там он разложится на 60-80 конкретных задач с trace'ом к FR из spec.md. Потом `/speckit.analyze` проверит, что всё консистентно, и можно начинать кодить.

**Чего здесь нет (на будущее):**
- ADR-007 написан как заглушка в Phase 0 — финализируется в Phase 1.
- Точные версии libsodium / Lazysodium / Storage SDK уточняются в research.md.
- UX документов (DocumentPickerScreen, DocumentViewerScreen) пока заявлен функционально, дизайн-detalisation — на стадии Phase 9.
