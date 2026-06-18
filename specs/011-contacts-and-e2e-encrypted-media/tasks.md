# Tasks: E2E Crypto Foundation

**Branch**: `011-contacts-and-e2e-encrypted-media` | **Date**: 2026-05-21 / **rev. 2** 2026-05-22 (regenerated after scope-split + speckit-analyze remediation) / **rev. 3 exec-order override** 2026-05-25
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md) | **Data model**: [data-model.md](./data-model.md) | **Contracts**: [contracts/](./contracts/) | **Quickstart**: [quickstart.md](./quickstart.md) | **Analyze report**: [analyze-report.md](./analyze-report.md)

---

## Overview

**52 задачи в 11 фазах**. Регенерация после mentor scope-split (2026-05-22): UI-related task'и (~20) переехали в спек 012; добавлены ~10 новых task'ов под Hash + Signature ports + manual smoke + security findings.

**Маркеры**:
- `[P]` — task можно делать параллельно с другими `[P]` в той же фазе (разные файлы, no shared state).
- `[M]` — manual step (требует ручного действия владельца проекта).
- `[CRIT]` — критический путь / gate перед следующей фазой.
- `[US-N]` — задача поднимает US-N (cross-artifact trace).

**Параллелизация между фазами**:
- Phase 4 (Pairing extension) можно делать параллельно с Phase 3 (Crypto adapters) после общего Phase 2.
- Phase 5 (Storage adapter) можно делать параллельно с Phase 3.
- Phase 6 (Recipient resolver) ждёт Phase 4.

**Push policy**: per CLAUDE.md §Branching — push после каждой фазы. PR открывается после Phase 0.

---

## Execution Order Override (rev. 3, 2026-05-25)

Phase-номера остаются неизменными для FR-trace, но **порядок исполнения** перегруппирован по типу тестирования: сначала задачи без runtime-тестов, потом с эмулятором, потом с реальным железом. Это уменьшает простой на ожидании настройки эмулятора и оставляет real-device smoke как финальный gate перед merge.

### Группа A — без runtime-тестов (JVM-only / compile-only / static checks)

Тесты гоняются как обычные JVM unit-тесты в `commonTest` — не нужен ни Android-эмулятор, ни Firebase Emulator, ни реальное устройство.

1. **Phase 0** — env prep (T001-T008) — *уже сделано в `888b1c6`, осталось T001 [M] readiness checklist + T008 PR*.
2. **Phase 1** — domain types + ports (T010-T028) — pure Kotlin commonMain, validation tests.
3. **Phase 2** — fake adapters + wire-format tests (T030-T040) — in-memory fakes, CBOR roundtrip, SQLDelight migration test через JVM driver.
4. **Phase 9** — Konsist fitness gates (T110-T113) — static AST analysis, JVM-only.
5. **T120** (Phase 10) — spec 012 cross-references — docs only.

### Группа B — с эмулятором (Android Robolectric / Firebase Emulator / Storage Emulator)

Нужен либо Robolectric для Android Keystore, либо Firebase/Storage Emulator для Firestore/Storage интеграций. Android-эмулятор как такового **не требуется** — всё гоняется через JVM-based emulator suite.

6. **Phase 3** — real crypto adapters (T050-T056) — Robolectric для Keystore.
7. **Phase 4** — pairing extension с Firestore signature publish (T060-T063) — Firestore Emulator.
8. **Phase 5** — Storage adapter (T070-T072) — Storage Emulator.
9. **Phase 6** — recipient resolver (T080) — fake-driven, опционально Firestore Emulator для integration.
10. **Phase 7** — cleanup machinery (T090-T093) — SQLDelight Android driver + Storage Emulator.

### Группа C — на реальном Android-устройстве (manual smoke)

Требуется 2 реальных Android-устройства для end-to-end проверки.

11. **Phase 8** — manual smoke (T100-T102) — debug-build buttons + 2-device hex match.

### Группа D — финальный gate (no runtime)

12. **T121** — final speckit-analyze pass.
13. **T122** [M] — PR merge readiness checklist.

### Зависимости между группами (нельзя нарушать)

- Phase 3-7 (группа B) требуют Phase 1-2 (группа A) — порты и fake-контракты должны существовать.
- Phase 8 (группа C) требует Phase 3-7 (группа B) — нужен реальный crypto + Firestore + Storage flow.
- Phase 9 (Konsist) логически в группе A, но удобно гонять её **после** всех адаптеров (Phase 3-7), потому что её правила проверяют, что vendor types confined в adapters — нечего проверять, пока адаптеров нет. Поэтому в exec-order Phase 9 идёт **между** Phase 7 и Phase 8.
- T120 (cross-ref docs) можно сделать в любой момент после Phase 1 — параллельно.

---

## Phase 0 — Environment prep & coordination

**Goal**: подготовка зависимостей, Storage Emulator, ADR-007 draft.

### T001 [M] [CRIT] Verify Phase 0 readiness checklist
- **Traces to**: [quickstart.md](./quickstart.md) §1-§12.
- **Action**: пройти все 12 пунктов quickstart.md. Особенно — libsodium доступен на CI machines + ABI splits сконфигурированы.
- **Acceptance**: checklist пройден; CI green после deps update.

### T002 [P] Wire Lazysodium-android + JNA + kotlinx-serialization-cbor dependencies
- **Traces to**: plan.md §Dependency Impact, quickstart.md §1, FR-001..009, FR-010..014, FR-020..025.
- **File(s)**: `gradle/libs.versions.toml`, `core/build.gradle.kts`.
- **Action**: добавить `lazysodium-android:5.1.0` + `jna:5.13.0` в androidMain; `kotlinx-serialization-cbor` в commonMain.
- **Acceptance**: `./gradlew :core:compileDebugKotlinAndroid` green; APK contains .so под 4 ABI; CBOR serializer accessible.

### T003 [P] Configure ABI splits for release build
- **Traces to**: plan.md §APK delta budget, quickstart.md §2, Risk R3.
- **File(s)**: `app/build.gradle.kts`.
- **Action**: enable `android.splits.abi`, include 4 ABI, `isUniversalApk = false`.
- **Acceptance**: 4 ABI-specific APK, каждый < +500 KiB delta vs spec 010 baseline.

### T004 [P] Add Firebase Storage SDK dependency
- **Traces to**: plan.md §Storage, quickstart.md §4.
- **File(s)**: `gradle/libs.versions.toml`, `core/build.gradle.kts`.
- **Action**: `firebase-storage-ktx` через Firebase BoM.
- **Acceptance**: `FirebaseStorage.getInstance()` accessible from androidMain.

### T005 [P] Initialize Storage Emulator
- **Traces to**: plan.md §Test Strategy Level 5, quickstart.md §5.
- **File(s)**: `firebase.json`, `storage.rules` (new).
- **Action**: emulator port `9199`; копировать Storage Rules из contracts/encrypted-media-storage.md.
- **Acceptance**: `firebase emulators:start --only firestore,auth,storage` загружает rules без error.

### T006 [P] Update compliance: permissions-and-resource-budget.md
- **Traces to**: spec.md §Clarifications C-6, scope-split 2026-05-22.
- **File(s)**: `docs/compliance/permissions-and-resource-budget.md`.
- **Action**: обновить запись POST_NOTIFICATIONS «deferred to spec 011+» → «deferred to future balance-alerts spec / offline-detection spec (legacy numbering 017/018 pre-2026-06-18; spec 017 reassigned to F-4 AuthProvider — see ADR-008 numbering note)». READ_CONTACTS — cross-link на спек 012 (там реально trigger'ится).
- **Acceptance**: нет orphan references на «deferred to spec 011/012/013» (старая нумерация).

### T007 [P] Create ADR-007 draft
- **Traces to**: research.md §8, plan.md §Required Context Review, project-backlog.md TODO-DOC-001.
- **File(s)**: `docs/adr/ADR-007-trust-edge-bootstrap-subtypes.md`.
- **Action**: draft per research.md §8. Status: Draft (finalize в Phase 1).
- **Acceptance**: файл с context/decision/consequences.

### T008 [CRIT] First commit + push + PR open
- **Traces to**: CLAUDE.md §Branching.
- **Action**: commit Phase 0 changes (один логический commit); push; open PR с link на spec/plan/research/data-model/contracts/quickstart/analyze-report.
- **Acceptance**: CI green; PR open с описанием scope (фундамент, без UI).

**Checkpoint Phase 0**: deps wired, emulator ready, ADR-007 draft, PR open.

---

## Phase 1 — Domain types (commonMain)

**Goal**: domain-уровни types + port interfaces, no implementation. Tests на validation rules.

### T010 [CRIT] Finalize ADR-007
- **Traces to**: research.md §8.
- **File(s)**: `docs/adr/ADR-007-trust-edge-bootstrap-subtypes.md`.
- **Action**: status Draft → Accepted; полная formulation context/decision/consequences (X25519 + Ed25519 subtypes; Pub publication через Firestore с signature).
- **Acceptance**: ADR-007 status = Accepted.

### T011 [P] Domain type: `DeviceId`
- **Traces to**: data-model.md §1, FR-001.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/crypto/DeviceId.kt`.
- **Action**: value class + UUIDv4 validation + `random()` factory.
- **Acceptance**: `DeviceIdInitTest` green.

### T012 [P] Domain types: `PublicKey` (X25519) + `SigningPublicKey` (Ed25519)
- **Traces to**: data-model.md §1, FR-001, FR-006.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/crypto/PublicKey.kt`, `SigningPublicKey.kt`.
- **Action**: value classes + 32-byte validation + equals/hashCode by bytes.
- **Acceptance**: `PublicKeyInitTest`, `SigningPublicKeyInitTest` green.

### T013 [P] Domain types: `PrivateKey` + `SigningPrivateKey` (opaque)
- **Traces to**: data-model.md §1, FR-002, CHK-DOM-007.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/crypto/PrivateKey.kt`, `SigningPrivateKey.kt`.
- **Action**: `sealed interface` с `alias: String` field. **NOT Serializable. NOT extractable.**
- **Acceptance**: Konsist rule — sealed interface не имеет `bytes` accessor.

### T014 [P] Domain types: `DeviceKeyPair` + `DeviceSigningKeyPair`
- **Traces to**: data-model.md §1.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/crypto/DeviceKeyPair.kt`, `DeviceSigningKeyPair.kt`.
- **Action**: data classes.
- **Acceptance**: compile-only test.

### T015 [P] Domain type: `DeviceIdentity` (signed wire format)
- **Traces to**: data-model.md §1, FR-006, contracts/device-identity.md.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/crypto/DeviceIdentity.kt`.
- **Action**: data class с schemaVersion, deviceId, publicKey, signingPublicKey, signedTimestamp, signature, createdAt; helper `signedPayloadBytes(): ByteArray` (canonical CBOR encoding).
- **Acceptance**: `DeviceIdentityInitTest` green; `signedPayloadBytes()` deterministic (alphabetical key order).

### T016 [P] Domain type: `ContentEncryptionKey` + `use` extension
- **Traces to**: data-model.md §1, FR-021, CHK-STATE-005.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/crypto/ContentEncryptionKey.kt`.
- **Action**: class with `AutoCloseable`, `close()` zeroes bytes; inline `use { }` extension function.
- **Acceptance**: `CEKZeroizationTest` green (verify bytes.all { it == 0 } после close()).

### T017 [P] Domain type: `Recipient` + `EncryptedEnvelope`
- **Traces to**: data-model.md §1, FR-010..014, contracts/crypto-envelope.md.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/crypto/Recipient.kt`, `EncryptedEnvelope.kt`.
- **Action**: data classes; envelope `init` enforces `recipients.isNotEmpty() && nonce.size == 24`; metadata = `Map<String, ByteArray>` (freeform).
- **Acceptance**: `EnvelopeInitTest` (empty recipients → reject; nonce size mismatch → reject); `RecipientInitTest` (sealedCEK size != 80 → reject).

### T018 [P] [CRIT] Domain type: `CryptoError` sealed interface (включая MalformedEnvelope, SignatureVerifyFailed)
- **Traces to**: data-model.md §1, FR-050, CHK-FR-008.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/crypto/CryptoError.kt`.
- **Action**: sealed interface; sub-cases: KeyNotFound, MacFailed, BlobMissing, CipherSuiteUnsupported, RecipientNotFound, SignatureVerifyFailed, MalformedEnvelope, StorageFailure, KeystoreFailure.
- **Acceptance**: `CryptoErrorTest` — каждый sub-case constructable + has correct fields; никаких byte-content fields (только uuid/alias/deviceId/cause).

### T019 [CRIT] `SUPPORTED_SCHEMA_VERSION` constant + wire-format module
- **Traces to**: plan.md §Open Items CHK003, contracts/crypto-envelope.md.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/crypto/CryptoEnvelopeWireFormat.kt`.
- **Action**: `const val SUPPORTED_SCHEMA_VERSION = 1`; reference в envelope serialization + decoder validation. Никаких magic-literal `1`.
- **Acceptance**: grep tests — `1` not appearing as literal в envelope handling code.

### T020 [P] Domain type: `BlobReference`
- **Traces to**: data-model.md §1, FR-040.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/media/BlobReference.kt`.
- **Action**: data class (uuid, linkId, refSource, refUpdatedAt).
- **Acceptance**: compile-only.

### T021 [P] Port interface: `AeadCipher`
- **Traces to**: data-model.md §2, FR-020.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/crypto/AeadCipher.kt`.
- **Action**: `interface AeadCipher { encrypt, decrypt (Result<...>), randomNonce, generateCEK }`.
- **Acceptance**: compile-only.

### T022 [P] Port interface: `AsymmetricCrypto`
- **Traces to**: data-model.md §2, FR-022, FR-023.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/crypto/AsymmetricCrypto.kt`.
- **Action**: `interface AsymmetricCrypto { generateX25519Pair, sealCEK, unsealCEK (Result<...>) }`.
- **Acceptance**: compile-only.

### T023 [P] Port interface: `DigitalSignature` *(new)*
- **Traces to**: data-model.md §2, FR-025, FR-006.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/crypto/DigitalSignature.kt`.
- **Action**: `interface DigitalSignature { generateEd25519Pair, sign, verify (Result<...>) }`.
- **Acceptance**: compile-only.

### T024 [P] Port interface: `HashFunction` *(new)*
- **Traces to**: data-model.md §2, FR-024.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/crypto/HashFunction.kt`.
- **Action**: `interface HashFunction { hash(data): ByteArray (32 bytes) }`.
- **Acceptance**: compile-only.

### T025 [P] Port interface: `SecureKeystore` (расширенный)
- **Traces to**: data-model.md §2, FR-002.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/crypto/SecureKeystore.kt`.
- **Action**: `interface SecureKeystore { generateAndStoreEncryption, generateAndStoreSigning, loadEncryption, loadSigning, delete, exists }`.
- **Acceptance**: compile-only.

### T026 [P] Port interface: `RecipientResolver`
- **Traces to**: data-model.md §2, FR-060, spec.md §Clarifications C-8.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/crypto/RecipientResolver.kt`.
- **Action**: `fun interface RecipientResolver { suspend resolveRecipients(linkId) }`.
- **Acceptance**: compile-only.

### T027 [P] Port interface: `EncryptedMediaStorage`
- **Traces to**: data-model.md §2, FR-030..032.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/crypto/EncryptedMediaStorage.kt`.
- **Action**: `interface EncryptedMediaStorage { upload, download, delete, exists, list — все возвращают Result<..., CryptoError> }`.
- **Acceptance**: compile-only.

### T028 [P] Port interface: `DeviceIdentityRepository`
- **Traces to**: data-model.md §2, FR-006..009.
- **File(s)**: `core/src/commonMain/kotlin/com/launcher/api/crypto/DeviceIdentityRepository.kt`.
- **Action**: `interface DeviceIdentityRepository { publishOwn, fetchPeer (Result<...>), listAll }`. `fetchPeer` MUST verify Ed25519 signature before return.
- **Acceptance**: compile-only.

**Checkpoint Phase 1**: 8 ports + 9 domain types defined, validation tests green, Konsist rules ready.

---

## Phase 2 — Fake adapters + wire-format tests

**Goal**: in-memory fakes для всех portов + wire-format roundtrip tests (Level 2-4).

### T030 [P] FakeAeadCipher (XOR-stub)
- **Traces to**: plan.md §Port-adapter shape, CLAUDE.md §6.
- **File(s)**: `core/src/commonTest/kotlin/com/launcher/fake/crypto/FakeAeadCipher.kt`.
- **Action**: XOR-stub (НЕ настоящее шифрование); deterministic nonce; CEK = random 32 bytes.
- **Acceptance**: round-trip XOR работает; comment-warning «не использовать в production».

### T031 [P] FakeAsymmetricCrypto (deterministic)
- **File(s)**: `core/src/commonTest/.../FakeAsymmetricCrypto.kt`.
- **Action**: deterministic seed-based keypair generation; sealCEK = simple wrap.
- **Acceptance**: contract-parity tests с real adapter (T040) — same inputs → same outputs.

### T032 [P] FakeDigitalSignature (deterministic) *(new)*
- **File(s)**: `core/src/commonTest/.../FakeDigitalSignature.kt`.
- **Action**: deterministic; sign = simple hash-based MAC; verify проверяет совпадение.
- **Acceptance**: round-trip sign+verify работает.

### T033 [P] FakeHashFunction (deterministic) *(new)*
- **File(s)**: `core/src/commonTest/.../FakeHashFunction.kt`.
- **Action**: XOR-fold deterministic.
- **Acceptance**: same input → same 32-byte output.

### T034 [P] FakeSecureKeystore (in-memory map)
- **File(s)**: `core/src/commonTest/.../FakeSecureKeystore.kt`.
- **Action**: `mutableMapOf<String, ByteArray>` для каждого type (encryption / signing).
- **Acceptance**: store + load + delete работают; `exists` корректен.

### T035 [P] FakeRecipientResolver (programmable)
- **File(s)**: `core/src/commonTest/.../FakeRecipientResolver.kt`.
- **Action**: `fun setRecipients(linkId, list)` для конфигурации в тесте.
- **Acceptance**: returns set list.

### T036 [P] FakeEncryptedMediaStorage (in-memory)
- **File(s)**: `core/src/commonTest/.../FakeEncryptedMediaStorage.kt`.
- **Action**: `mutableMapOf<Pair<LinkId, Uuid>, EncryptedEnvelope>`.
- **Acceptance**: upload/download/delete/exists/list работают; `BlobMissing` при missing uuid.

### T037 [P] FakeDeviceIdentityRepository
- **File(s)**: `core/src/commonTest/.../FakeDeviceIdentityRepository.kt`.
- **Action**: in-memory map; `fetchPeer` верифицирует Ed25519 signature через injected `DigitalSignature` port.
- **Acceptance**: publish + fetchPeer round-trip; tampered signature → SignatureVerifyFailed.

### T038 [CRIT] Wire-format roundtrip tests: CryptoEnvelope
- **Traces to**: contracts/crypto-envelope.md §Roundtrip tests, FR-010..014.
- **File(s)**: `core/src/commonTest/kotlin/.../CryptoEnvelopeWireFormatTest.kt`, fixtures в `commonTest/resources/wire-format/`.
- **Action**: 12 tests per contracts/crypto-envelope.md (roundtrip single/multi/empty-metadata/freeform, forward-compat unknown cipherSuite/extra field, aadBinding, cek_zeroized, malformedEnvelope_truncated/typeMismatch/emptyRecipients, recipientNotFound).
- **Acceptance**: все 12 tests green.

### T039 [CRIT] Wire-format roundtrip tests: DeviceIdentity
- **Traces to**: contracts/device-identity.md §Tests, FR-006.
- **File(s)**: `core/src/commonTest/.../DeviceIdentityWireFormatTest.kt`.
- **Action**: roundtrip, backwardCompat rejection, schema mismatch tests. Signature sign+verify on Fake.
- **Acceptance**: все tests green.

### T040 [CRIT] SQLDelight migration test (BlobReferenceLedger + SystemMeta)
- **Traces to**: plan.md §Open Items CHK014, data-model.md §3.
- **File(s)**: `core/src/commonTest/.../ConfigSyncDatabaseMigrationTest.kt`.
- **Action**: создать DB из старой schema (спек 008 v1) → apply migration to v2 → проверить BlobReferenceLedger + SystemMeta tables присутствуют, existing data (configs/state) сохранены.
- **Acceptance**: migration roundtrip green.

**Checkpoint Phase 2**: все fakes + wire-format tests green. CI первый раз показывает реальные crypto-test runs.

---

## Phase 3 — Real crypto adapters (androidMain)

**Goal**: libsodium adapters + Android Keystore wrapping.

### T050 [P] LibsodiumAeadCipher
- **Traces to**: research.md §2, FR-020.
- **File(s)**: `core/src/androidMain/kotlin/com/launcher/adapters/crypto/LibsodiumAeadCipher.kt`.
- **Action**: `crypto_secretbox_xchacha20poly1305_*` calls; combined-mode (ciphertext includes MAC).
- **Acceptance**: `AeadCipherContractTest` против libsodium official vectors (RFC 8439) + Fake parity.

### T051 [P] LibsodiumAsymmetricCrypto
- **Traces to**: research.md §2, FR-022, FR-023.
- **File(s)**: `core/src/androidMain/.../LibsodiumAsymmetricCrypto.kt`.
- **Action**: `crypto_box_keypair`, `crypto_box_seal`, `crypto_box_seal_open`.
- **Acceptance**: `AsymmetricCryptoContractTest` против libsodium vectors + RFC 7748 X25519 vectors.

### T052 [P] LibsodiumDigitalSignature *(new)*
- **Traces to**: research.md §2b, FR-025.
- **File(s)**: `core/src/androidMain/.../LibsodiumDigitalSignature.kt`.
- **Action**: `crypto_sign_keypair`, `crypto_sign_detached`, `crypto_sign_verify_detached`.
- **Acceptance**: `DigitalSignatureContractTest` против RFC 8032 Ed25519 vectors.

### T053 [P] LibsodiumHashFunction *(new)*
- **Traces to**: research.md §2c, FR-024.
- **File(s)**: `core/src/androidMain/.../LibsodiumHashFunction.kt`.
- **Action**: `crypto_generichash` (BLAKE2b, output 32 bytes).
- **Acceptance**: `HashFunctionContractTest` против RFC 7693 BLAKE2b vectors.

### T054 [CRIT] AndroidKeystoreSecureKeystore — X25519 AES-wrap strategy
- **Traces to**: research.md §3, quickstart.md §6, FR-002.
- **File(s)**: `core/src/androidMain/.../AndroidKeystoreSecureKeystore.kt`.
- **Action** (sub-tasks внутри одного task для exactness):
  - **T054a**: keygen via libsodium (`crypto_box_keypair`).
  - **T054b**: AES-256 key generation в Android Keystore (StrongBox if available, TEE fallback).
  - **T054c**: AES-GCM wrap of X25519 priv bytes; store encrypted bytes + nonce в EncryptedSharedPreferences под alias.
  - **T054d**: load reverse — Keystore unwrap → libsodium-readable X25519 priv.
- **Acceptance**: `AndroidKeystoreSecureKeystoreTest` (Robolectric); generate → load → roundtrip; delete → exists=false.

### T055 [CRIT] AndroidKeystoreSecureKeystore — Ed25519 storage *(new)*
- **Traces to**: research.md §2b, quickstart.md §6.
- **File(s)**: `core/src/androidMain/.../AndroidKeystoreSecureKeystore.kt` (same file as T054).
- **Action**:
  - **API 31+ path**: native Keystore Ed25519 — `KeyPairGenerator` с `Ed25519` algorithm + `KeyGenParameterSpec` (PURPOSE_SIGN | PURPOSE_VERIFY).
  - **API 30 fallback**: AES-wrap (как X25519 в T054).
- **Acceptance**: `AndroidKeystoreEd25519Test` оба пути работают на разных API levels; sign + verify roundtrip через Keystore-stored priv.

### T056 [CRIT] Constant-time recipient search в AsymmetricCrypto adapter
- **Traces to**: research.md §2d, CHK-SEC-018.
- **File(s)**: `core/src/androidMain/.../LibsodiumAsymmetricCrypto.kt`.
- **Action**: при `unsealCEK` для envelope с N recipients — перебрать **все** N recipients (не early-return при первом match); использовать `libsodium.utils.sodium_memcmp` для deviceId comparison.
- **Acceptance**: `ConstantTimeRecipientSearchTest` — measure timing variance for envelope where own deviceId at position 0 vs N-1; variance < 5%.

**Checkpoint Phase 3**: все libsodium adapters green против official vectors; Keystore работает оба path (X25519 AES-wrap + Ed25519 native/fallback); constant-time recipient search verified.

---

## Phase 4 — Pairing extension (Pub publication with Ed25519 signature)

**Goal**: расширить pairing flow (спек 007) — после `consent.allow` оба устройства публикуют DeviceIdentity с подписью.

### T060 [CRIT] FirestoreDeviceIdentityRepository
- **Traces to**: data-model.md §2, contracts/device-identity.md.
- **File(s)**: `core/src/androidMain/.../FirestoreDeviceIdentityRepository.kt`.
- **Action**: publishOwn → write `/links/{linkId}/devices/{deviceId}`; fetchPeer → read + Ed25519 verify + freshness check (signedTimestamp в пределах 7 days).
- **Acceptance**: `DeviceIdentityRepositoryTest` Robolectric + Firebase Emulator; tampered document → SignatureVerifyFailed; stale signedTimestamp → SignatureVerifyFailed.

### T061 [CRIT] PairingCoordinator extension — keygen + publishOwn
- **Traces to**: research.md §8 (ADR-007), FR-006.
- **File(s)**: `core/src/androidMain/.../pairing/PairingCoordinator.kt` (existing — extend).
- **Action**:
  - **T061a**: на первом запуске generate `(X25519, Ed25519)` keypairs через `SecureKeystore.generateAndStore*()`.
  - **T061b**: после `consent.allow` (спек 007 FR-009) — собрать `DeviceIdentity` с canonical CBOR payload + sign via Ed25519 + publishOwn.
  - **T061c**: integration test «factory-fresh pair → consent.allow → оба Pub в Firestore с valid signatures».
- **Acceptance**: integration test на Firebase Emulator проходит.

### T062 [P] Firestore Security Rules для `/links/{linkId}/devices/`
- **Traces to**: contracts/device-identity.md §Security Rules.
- **File(s)**: `firestore.rules`.
- **Action**: добавить правила: isLinkMember, isOwnDevice, freshSignedTimestamp (7-day gate с 1-min skew), hasRequiredFields. + deviceOwnership auxiliary collection.
- **Acceptance**: 8 security tests из contracts/device-identity.md green (read/create/update/delete + ownership).

### T063 Link.KNOWN_SUBCOLLECTIONS + KNOWN_STORAGE_PATHS extension
- **Traces to**: FR-043, quickstart.md §7.
- **File(s)**: `core/src/commonMain/.../link/Link.kt`.
- **Action**: добавить `"devices"`, `"deviceOwnership"` в KNOWN_SUBCOLLECTIONS; создать новую константу `KNOWN_STORAGE_PATHS = listOf("private-media")`. Обновить LinkRegistry.revoke() для enumerate обеих.
- **Acceptance**: revoke test — после revoke все 3 path удалены.

**Checkpoint Phase 4**: pairing генерирует ключи + публикует Pub'ы с подписями; Security Rules enforce freshness + ownership; revoke зачищает.

---

## Phase 5 — Storage adapter

**Goal**: Firebase Storage upload/download/delete с Storage Rules.

### T070 [CRIT] FirebaseEncryptedMediaStorage
- **Traces to**: data-model.md §2, FR-030..032, contracts/encrypted-media-storage.md.
- **File(s)**: `core/src/androidMain/.../FirebaseEncryptedMediaStorage.kt`.
- **Action**: upload/download/delete/exists/list на Firebase Storage path `/links/{linkId}/private-media/{uuid}`; download wraps CBOR parse failure в `MalformedEnvelope`.
- **Acceptance**: `FirebaseEncryptedMediaStorageTest` Robolectric + Storage Emulator; round-trip envelope; foreign uid → PERMISSION_DENIED.

### T071 [P] Storage Rules для `/links/{linkId}/private-media/*`
- **Traces to**: research.md §4, FR-033, contracts/encrypted-media-storage.md.
- **File(s)**: `storage.rules`.
- **Action**: isLinkMember check via Firestore.get cross-service rule; 500 KB size cap.
- **Acceptance**: 4 security tests — pair-member upload/download OK; foreign uid → denied; oversized → denied.

### T072 WorkManager retry policy
- **Traces to**: research.md §5b, CHK-FR-012, FR-044.
- **File(s)**: `core/src/androidMain/.../media/StorageRetryWorker.kt`.
- **Action**: WorkManager job с exponential backoff (1m → 5m → 30m → 2h → 12h, max 5 attempts); после exhaustion — structured log warning, no automatic retry.
- **Acceptance**: `StorageRetryWorkerTest` — verify backoff sequence; verify exhaustion → log + give up.

**Checkpoint Phase 5**: Storage adapter работает с retry policy; Rules enforce member-only access.

---

## Phase 6 — Recipient resolver

### T080 PairRecipientResolver
- **Traces to**: data-model.md §2, FR-060, spec.md §Clarifications C-8.
- **File(s)**: `core/src/androidMain/.../PairRecipientResolver.kt`.
- **Action**: для linkId — вернуть DeviceIdentity других members'ов пары (1 entry в 011). Использует DeviceIdentityRepository.listAll + filter out own deviceId.
- **Acceptance**: `PairRecipientResolverTest` — для pair → returns peer identity; missing peer → empty list (graceful).

**Checkpoint Phase 6**: resolver работает; контракт-парность с Fake.

---

## Phase 7 — Cleanup machinery

### T090 [CRIT] BlobReferenceLedger (SQLDelight)
- **Traces to**: data-model.md §3, FR-040..042.
- **File(s)**: `core/src/commonMain/sqldelight/.../BlobReferenceLedger.sq`, `core/src/androidMain/.../SqlDelightBlobReferenceLedger.kt`.
- **Action**: SQL schema + Kotlin wrapper (addRef, removeRef, countRefs, deleteByLink).
- **Acceptance**: `BlobReferenceLedgerTest` — добавить/убрать refs, count корректный, deleteByLink работает.

### T091 [P] SystemMeta table + clear-data sentinel *(new)*
- **Traces to**: data-model.md §3, research.md §5c, CHK-FR-015.
- **File(s)**: `core/src/commonMain/sqldelight/.../SystemMeta.sq`, `core/src/androidMain/.../ClearDataDetector.kt`.
- **Action**: SQL schema (key/value); ClearDataDetector — при startup проверяет наличие row, если нет → записывает `clearDataAt = now`.
- **Acceptance**: `ClearDataDetectorTest` — fresh DB → clearDataAt записан; existing DB → не trogается.

### T092 [CRIT] BackgroundReconciler (orphan blob cleanup)
- **Traces to**: research.md §5, §5c, FR-042.
- **File(s)**: `core/src/androidMain/.../media/BackgroundReconciler.kt`.
- **Action**: WorkManager periodic job (24h cadence); enumerate Storage `/links/{linkId}/private-media/*` → cross-check с BlobReferenceLedger → удалить orphans. **MUST skip if clearDataAt < 7 days ago**.
- **Acceptance**: `BackgroundReconcilerTest` — orphan removed; recent clear-data → skip; refs present → no delete.

### T093 LinkRegistry.revoke() Storage cleanup integration
- **Traces to**: FR-043, spec 007 FR-033.
- **File(s)**: `core/src/androidMain/.../link/LinkRegistry.kt` (existing — extend).
- **Action**: при revoke — enumerate Storage KNOWN_STORAGE_PATHS + delete; clear BlobReferenceLedger.deleteByLink.
- **Acceptance**: integration test «add blob → revoke link → Storage empty under linkId + ledger clean».

**Checkpoint Phase 7**: cleanup machinery + clear-data grace + revoke integration работают.

---

## Phase 8 — Manual smoke procedure (gate перед merge)

**Goal**: debug build с smoke buttons + 2-device manual test.

### T100 Debug-build only: EncryptTestBlob / DecryptTestBlob buttons
- **Traces to**: FR-070, US-6.
- **File(s)**: `app/src/debug/kotlin/com/launcher/ui/debug/EncryptTestBlobButton.kt`, `DecryptTestBlobButton.kt`.
- **Action**: debug-only UI: «Encrypt 16 bytes» → генерирует 16 random bytes → encrypt через фундамент → upload → возвращает uuid + hex. «Decrypt by uuid» → fetches by uuid → decrypt → возвращает hex.
- **Acceptance**: оба кнопки accessible только в debug build; не появляются в release APK.

### T101 Smoke procedure document
- **Traces to**: FR-070.
- **File(s)**: `specs/011-contacts-and-e2e-encrypted-media/smoke/README.md`.
- **Action**: пошаговая инструкция — 2 factory-fresh устройства, pairing через QR, encrypt на одном, decrypt на другом, compare hex.
- **Acceptance**: документ создан, инструкции воспроизводимы.

### T102 [M] [CRIT] Run manual smoke on 2 real devices
- **Traces to**: FR-070, US-6.
- **Action**: владелец проекта проходит smoke/README.md procedure на 2 реальных Android-устройствах.
- **Acceptance**: hex match; время end-to-end < 60 секунд; гейт перед merge.

**Checkpoint Phase 8**: manual smoke прошёл, фундамент работает на реальном железе.

---

## Phase 9 — Konsist fitness gates

### T110 Konsist rule: commonMain/api/crypto/ no vendor imports
- **Traces to**: plan.md §Konsist gates, CLAUDE.md rule 1.
- **File(s)**: `core/src/test/kotlin/.../KonsistCryptoIsolationTest.kt`.
- **Action**: assert `commonMain/api/crypto/` MUST NOT import `com.goterl.lazysodium.*`, `android.*`, `com.google.firebase.*`.
- **Acceptance**: rule green; intentional violation → test fails.

### T111 Konsist rule: vendor types confined в adapters
- **File(s)**: same.
- **Action**: assert Lazysodium types только в `LibsodiumAeadCipher`, `LibsodiumAsymmetricCrypto`, `LibsodiumDigitalSignature`, `LibsodiumHashFunction`. Firebase Storage types только в `FirebaseEncryptedMediaStorage`. Firestore types только в `FirestoreDeviceIdentityRepository`.
- **Acceptance**: rule green.

### T112 Konsist rule: no plaintext/key bytes в logs
- **Traces to**: FR-051, FR-080.
- **Action**: regex check — no `Log.d(.*plaintext|priv|cek|secret.*)` patterns.
- **Acceptance**: rule green.

### T113 Konsist rule: no Exception throw из CryptoError sub-cases
- **Traces to**: FR-052.
- **Action**: scan `core/api/crypto/` для `throw` statements; allow only inside `init` validation blocks. All other errors → Result<_, CryptoError>.
- **Acceptance**: rule green.

**Checkpoint Phase 9**: 4 Konsist rules green, 0 violations.

---

## Phase 10 — Final docs + analyze gate

### T120 [P] Update spec 012 stub cross-references
- **Traces to**: scope-split 2026-05-22.
- **File(s)**: `specs/012-contact-photos-and-private-documents/spec.md`.
- **Action**: убедиться, что stub точно ссылается на 011 как фундамент; verify «moved from 011» notes корректны.
- **Acceptance**: cross-references clean.

### T121 [P] Final analyze pass
- **Traces to**: spec-kit process.
- **Action**: запустить `speckit-analyze` ещё раз — verify все DRIFT'ы из rev. 1 analyze-report.md закрыты; constitution check 7 PASS + 1 N/A; нет новых open items.
- **Acceptance**: analyze-report.md (rev. 2) verdict = READY-WITH-CAVEATS (с 3 known accepted-as-risk items) или READY.

### T122 [M] PR merge readiness checklist
- **Action**: владелец проекта проверяет: все Konsist green; manual smoke прошёл; CI green; analyze rev. 2 PASS.
- **Acceptance**: чек-лист пройден; готов к merge в main.

**Checkpoint Phase 10**: 011 готов к merge. Спек 012 разблокирован.

---

## Summary

**52 task'a в 11 фазах** (Phase 0 → Phase 10):
- Phase 0: 8 tasks (env prep)
- Phase 1: 19 tasks (domain types + ports)
- Phase 2: 11 tasks (fakes + wire-format tests)
- Phase 3: 7 tasks (real crypto adapters)
- Phase 4: 4 tasks (pairing extension)
- Phase 5: 3 tasks (Storage adapter)
- Phase 6: 1 task (recipient resolver)
- Phase 7: 4 tasks (cleanup machinery)
- Phase 8: 3 tasks (manual smoke)
- Phase 9: 4 tasks (Konsist)
- Phase 10: 3 tasks (final)

Total: **52**. Снижено с 73 за счёт scope-split (UI задачи переехали в спек 012). Добавлено: T023/T024 (Hash/Signature ports), T032/T033 (Fake versions), T052/T053 (Libsodium versions), T055 (Ed25519 Keystore storage), T056 (constant-time iteration), T072 (WorkManager retry policy), T091 (clear-data sentinel), T100-T102 (manual smoke).

**Estimated total time**: 3-4 weeks (вместо 5-7 в rev. 1).

---

<!-- novice summary -->

## TL;DR (простым языком)

**Что в этом файле.** Точный список из 52 задач для реализации криптофундамента, разбитых на 11 фаз. Каждая задача — конкретный файл, который надо создать или изменить, плюс тест, который должен пройти.

**Главные группы задач:**

1. **Phase 0 (8 задач)** — подготовка: добавить библиотеки (Lazysodium для крипто, Firebase Storage), настроить эмуляторы, написать ADR-007.

2. **Phase 1 (19 задач)** — domain types в чистом Kotlin: 9 классов (DeviceId, X25519/Ed25519 ключи, envelope, error sealed class, BlobReference) + 8 интерфейсов-портов (AEAD, X25519, **Ed25519 — новое**, **BLAKE2b — новое**, Keystore, RecipientResolver, Storage, Identity Repo).

3. **Phase 2 (11 задач)** — поддельные реализации (fake) для всех 8 портов + 12 тестов формата envelope + 1 тест миграции БД.

4. **Phase 3 (7 задач)** — настоящие реализации через libsodium и Android Keystore. Включает 2 новых: LibsodiumDigitalSignature (Ed25519) и LibsodiumHashFunction (BLAKE2b). Плюс constant-time поиск получателя (защита от timing-атак).

5. **Phase 4 (4 задачи)** — pairing: при сканировании QR оба устройства генерируют ключи + публикуют публичные в Firestore с подписью.

6. **Phase 5 (3 задачи)** — Storage adapter + Rules + retry политика (5 попыток с экспоненциальной задержкой).

7. **Phase 6-7 (5 задач)** — resolver + reference counting + cleanup. Включает clear-data 7-day grace (если admin стёр данные — ждём 7 дней перед чисткой Storage).

8. **Phase 8 (3 задачи)** — manual smoke: debug-кнопки в build + инструкция + **ручной тест на 2 устройствах** (gate перед merge — без него не сольёмся).

9. **Phase 9 (4 задачи)** — Konsist fitness rules: автоматическая проверка что крипто-код не утекает из изоляции.

10. **Phase 10 (3 задачи)** — final analyze + PR readiness.

**Что переехало в спек 012 (не в этом файле):**
- UI: Contacts Picker integration, DocumentPicker, DocumentViewer.
- PrivateMediaResolver (IconStorage namespace dispatch).
- PrivateMediaCache (кеш расшифрованных байтов).
- BlobMetadata.kind discriminator (Image/Document).
- Real photo encryption tests.

**Время на всё**: 3-4 недели (раньше было 5-7 — UI ушла в 012, scope сжался).

**После Phase 10** — фундамент готов к merge в main. Спек 012 (фото на плитках) разблокирован и может начинаться.
