# Tasks: F-5 — Root Key Hierarchy + ConfigDocument Encryption + Recovery

**Feature**: F-5 (spec 018) — implemented as **F-5b envelope variant** since 2026-06-20.
**Branch**: `018-f5-config-e2e-encryption`
**Plan**: [plan.md](./plan.md)
**Spec**: [spec.md](./spec.md) (see §"🔀 2026-06-20 — Architectural pivot to F-5b").
**Generated**: 2026-06-19
**Revised**: 2026-06-20 (F-5b batches appended at the end).

## F-5b status (envelope architecture)

Все 7 batches F-5b закрыты в commits на этой ветке:

| Batch | Commit | Что |
|---|---|---|
| 1 | `b364649` | RemoteStorage facade + Envelope wire format + EnvelopeConfigCipherImpl + 25 tests |
| 2 | `6097b9d` | Firestore + InMemory adapters (EnvelopeStorage, PublicKeyDirectory, RecipientResolver) + encapsulation fitness rule |
| 3 | `754dc52` | AndroidDeviceIdentity (Keystore + DataStore) в `:core:keys/androidMain` |
| 4 | `8e327f9` | DI wiring (real + mock) + Firestore Security Rules для envelope/devices/grants + backup exclusion device_identity |
| 5 | `c6498ad` | ConfigSaver + EnvelopeBootstrap caller API + 6 tests |
| 6 | `d135216` | Removal of legacy symmetric pattern (AeadConfigCipherImpl, KeyRegistry, SealedConfig, KeyHierarchy, WrappedDek + tests + fixtures) |
| 7 | (this commit) | Docs sync (spec.md, data-model.md, ecosystem-vision.md, server-roadmap.md, tasks.md) |

**Tests state**: 68 jvmTest'ов в `:core:keys:jvmTest`, all green.

**What stays from original F-5 task list** (Phases 1-4 + 7):
- Phase 1 (T001-T006 Setup): done in original F-5 commits.
- Phase 4 recovery flow (T060-T089): done; root key + Argon2id + recovery
  passphrase flow preserved unchanged.
- Phase 7 polish: T120 + T121 fitness functions done (commits da63e1d
  + 6097b9d encapsulation rule for `family.keys.api.internal.*`).

**What changed from original F-5 task list** (no longer applies):
- Phase 2 (T010-T033 Foundational) — ports + wire format переписаны под
  envelope. Original `ConfigCipher`, `KeyRegistry`, `SealedConfig`,
  `WrappedDek`, contracts/sealed-config-v1.md, contracts/key-registry-v1.md
  — **удалены** (Batch 6).
- Phase 3 US1 (T040-T057) — symmetric AeadConfigCipherImpl pipeline
  заменён на envelope path; T050/T051 picture redefined через
  `ConfigSaver.saveOwn/saveForOther` + `RemoteStorage`.
- Phase 5 US3 (T100-T106) — multi-DEK / identity isolation tests removed
  (DEKs concept gone; envelope is membership-agnostic). Recipient
  isolation testing folded into `EnvelopeConfigCipherRoundtripTest`
  (нон-recipient → `NotARecipient`).
- Phase 6 US4 (T110-T113) — three-tier cache invariant proved at unit
  test level (`emptyPlaintextRoundtrip`, `firestorePathOpaqueToCallerGreptest`).
- SC-001 end-to-end **закрыт на Xiaomi 11T** (2026-06-20):
  - emulator path (Firebase Emulator + `adb reverse`): 2/2 PASSED
  - real cloud path (`launcher-old-dev` + signed-in Google user): 2/2 PASSED
  - см. [`CloudConfigEncryptionE2ETest`](../../app/src/androidTest/java/com/launcher/app/data/envelope/CloudConfigEncryptionE2ETest.kt)
    и `manual-setup-dev.md §3.3` пути A / A' / B.
- WorkManager async push — wired in commit `06af513`
  ([`InMemoryAsyncConfigPushQueueImpl`](../../app/src/main/java/com/launcher/app/data/envelope/InMemoryAsyncConfigPushQueueImpl.kt)
  + WorkManager production adapter).

**Tasks deferred outside this spec** (separate spec when needed):
- FCM-driven config-updated notification — see
  [`docs/dev/server-roadmap.md` SRV-FCM-CONFIG-UPDATE](../../docs/dev/server-roadmap.md#srv-fcm-config-update-fcm-notifier-on-remote-storage-write-spec-018-f-5b-отложено).
- Integration into existing `DefaultConfigEditor` save flow — deferred to
  spec 008 rewrite.

---

## Original F-5 task list (kept for traceability — see status table above)

## Overview

F-5 строит новый KMP-модуль `core/keys/` (`lib-family-keys`) с пятью domain ports (`RootKeyManager`, `KeyRegistry`, `IdentityProof`, `RecoveryKeyVault`, `ConfigCipher`) поверх F-CRYPTO примитивов и F-4 AuthProvider. Первый потребитель — `ConfigCipher` для шифрования `ConfigDocument` перед отправкой в Firestore. Параллельно реализуется recovery flow: passphrase-encrypted root key в Firestore, Argon2id KDF, Android Autofill UX.

**MVP path** = Phase 1 + Phase 2 + Phase 3 (US1) + Phase 4 (US2). После Phase 4 фичу можно демонстрировать end-to-end: admin шифрует config → теряет телефон → recovery на новом устройстве → байт-равенство.

## Dependencies

- **F-4 (spec 017)** ✅ merged (PR #21) — `AuthProvider`, `AuthIdentity`, `AuthAdapterSelector`, `NoOp*` для non-GMS.
- **F-CRYPTO (spec 016)** 🚧 InProgress — должен дойти до minimum: `AeadCipher` (XChaCha20-Poly1305), `AsymmetricCrypto`, `SecureKeystore`, `KeyDerivation` (Argon2id), `CryptoError` sealed type. Без этого Phase 2 заблокирована.
- **Spec 008 (ConfigDocument)** — не модифицируется F-5; F-5 добавляет только adapter-слой (`ConfigCipher.seal/open`).

## MVP Path

| Phase | Story | Result |
|-------|-------|--------|
| 1 | Setup | KMP module `core/keys/` создан, gradle подключён к app/ |
| 2 | Foundational | Все 5 ports + wire-formats + fake adapters + contract tests готовы |
| 3 | US1 (P1) | Cloud config улетает зашифрованным — demo через Firestore Emulator |
| 4 | US2 (P1) | Recovery flow end-to-end работает — demo на двух «устройствах» |
| 5 | US3 (P2) | Foundation для S-2/S-5 валидирована mock-DEK тестом |
| 6 | US4 (P2) | three-tier cache invariant не сломан |
| 7 | Polish | Detekt fitness, security rules deploy, OEM matrix, perf benchmarks |

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Создание module skeleton, gradle config, базовая структура.

- [ ] T001 Создать новый KMP module-каталог `core/keys/` с `src/commonMain/kotlin/com/launcher/api/keys/api/`, `src/commonMain/kotlin/com/launcher/api/keys/impl/`, `src/commonTest/kotlin/com/launcher/api/keys/`, `src/commonTest/resources/fixtures/`, `src/androidMain/kotlin/com/launcher/api/keys/impl/` (per plan.md Project Structure)
- [ ] T002 Создать `core/keys/build.gradle.kts` с KMP targets (android + jvm + iosX64/iosArm64 declared inactive per F-CRYPTO decisions 2026-06-17), dependency на `core:crypto` (F-CRYPTO API), Kotlinx.serialization, kotlinx-coroutines-test, Turbine (FR-001, FR-002)
- [ ] T003 [P] Подключить `core/keys/` в `settings.gradle.kts` через `include(":core:keys")` и добавить в `app/build.gradle.kts` `implementation(project(":core:keys"))` (FR-001)
- [ ] T004 [P] Создать `core/keys/consumer-rules.pro` (Proguard — пустой пока, задел) и `core/keys/.gitignore`
- [ ] T005 [P] Добавить в `core/keys/build.gradle.kts` Detekt config с custom rule «no `com.ionspin.kotlin.crypto.*` imports outside `com.launcher.api.crypto (libsodium adapter)`» (CLAUDE.md rule 7 fitness function — final implementation в Polish phase)
- [ ] T006 Verify, что `./gradlew :core:keys:compileKotlinJvm` проходит (empty module компилируется)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain ports, wire-formats, sealed error types, fake adapters, contract tests. Блокирует все US-фазы.

**CRITICAL**: Никакая US-фаза не может стартовать до завершения Phase 2.

### Port declarations (parallel — каждый порт в своём файле)

- [ ] T010 [P] Объявить port `com.launcher.api.keys.KeyRegistry` в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/KeyRegistry.kt` с операциями `registerDek(name, dekMaterial): Outcome<Unit, KeyRegistryError>`, `getDek(name): Outcome<ByteArray, KeyRegistryError>`, `hasDek(name): Boolean` (FR-004, FR-023)
- [ ] T011 [P] Объявить port `com.launcher.api.keys.RootKeyManager` в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/RootKeyManager.kt` с операциями `getOrCreate(identity: AuthIdentity): Outcome<RootKey, RootKeyError>`, `wipe(identity: AuthIdentity): Outcome<Unit, RootKeyError>` (FR-003)
- [ ] T012 [P] Объявить port `com.launcher.api.keys.IdentityProof` в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/IdentityProof.kt` с операциями `currentIdentity(): AuthIdentity?`, `identityFlow: Flow<AuthIdentity?>`, `requestSignIn(): Outcome<AuthIdentity, IdentityError>`, `signOut(): Outcome<Unit, IdentityError>` (FR-006, per contracts/identity-proof-v1.md — 4 ops)
- [ ] T013 [P] Объявить port `com.launcher.api.keys.RecoveryKeyVault` в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/RecoveryKeyVault.kt` с операциями `fetchVault(uid: String): Outcome<RecoveryVaultBlob, VaultError>` (non-nullable; отсутствие → `VaultError.NotFound`, per contracts/recovery-vault-v1.md — sealed-error pattern идиоматичнее), `storeVault(uid: String, blob): Outcome<Unit, VaultError>`, `deleteVault(uid: String): Outcome<Unit, VaultError>` + inline `// TODO(future-spec V-2/V-3/P-10): cross-app root key sharing via broker pattern — see docs/product/future/multi-app-cohabitation.md` (FR-008, FR-022, FR-024)
- [ ] T014 [P] Объявить port `com.launcher.api.keys.ConfigCipher` в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/ConfigCipher.kt` с операциями `seal(configBytes: ByteArray, uid: String): Outcome<SealedConfig, CryptoError>`, `open(sealed: SealedConfig, uid: String): Outcome<ByteArray, CryptoError>` (FR-016, per contracts/sealed-config-v1.md — `ConfigDocument` принадлежит spec 008, F-5 работает с serialized `ByteArray` + uid для AAD binding)

### Wire-format data classes (parallel — каждый в своём файле)

- [ ] T015 [P] Объявить `com.launcher.api.keys.SealedConfig` data class в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/SealedConfig.kt`: `schemaVersion: Int = 1`, `algorithm: String = "xchacha20poly1305-v1"`, `ciphertext: ByteArray`, `nonce: ByteArray`, `aad: ByteArray`, `recipientMasterSignature: ByteArray? = null` (FR-017, CLAUDE.md rule 5)
- [ ] T016 [P] Объявить `com.launcher.api.keys.RecoveryVaultBlob` data class в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/RecoveryVaultBlob.kt`: `schemaVersion: Int = 1`, `algorithm: String = "argon2id-xchacha20poly1305-v1"`, `kdfSalt: ByteArray`, `kdfParams: PassphraseKdfParams`, `wrappedRootKey: ByteArray`, `nonce: ByteArray`, `createdAt: Long` (FR-010)
- [ ] T017 [P] Объявить `com.launcher.api.keys.PassphraseKdfParams` value class в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/PassphraseKdfParams.kt`: `memory: Int`, `iterations: Int`, `parallelism: Int`; default = interactive (64MB / 3 / 1) (FR-010, FR-030)
- [ ] T018 [P] Объявить `com.launcher.api.keys.WrappedDek` data class в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/WrappedDek.kt`: `name: String`, `schemaVersion: Int = 1`, `algorithm: String`, `ciphertext: ByteArray`, `nonce: ByteArray` (FR-004, FR-005)
- [ ] T019 [P] Объявить `com.launcher.api.keys.RootKey` opaque value class (oбёртка над ByteArray, не toString-able) в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/RootKey.kt` (FR-013, security)

### Sealed error types (parallel)

- [ ] T020 [P] Объявить `com.launcher.api.keys.KeyRegistryError` sealed class: `NotFound | StorageFailure | UnknownDek | RootKeyUnavailable` в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/KeyRegistryError.kt` (FR-025, per contracts/key-registry-v1.md — `RootKeyUnavailable` нужен, если RootKey ещё не загружен и `KeyRegistryImpl` не может unwrap'ить DEK)
- [ ] T021 [P] Объявить `com.launcher.api.keys.RootKeyError` sealed class: `KeystoreInvalidated | StorageFailure | RecoveryRequired` в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/RootKeyError.kt` (FR-025; unified set — `KeystoreInvalidated` после OS update инвалидирует hardware-backed key, `RecoveryRequired` когда Keystore пуст и нужен recovery flow)
- [ ] T022 [P] Объявить `com.launcher.api.keys.IdentityError` sealed class: `NoSupportedProvider | Cancelled | Failure(val cause: Throwable)` в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/IdentityError.kt` (FR-028; consistent with contracts/identity-proof-v1.md + data-model.md §5 — NotSignedIn это not error, а valid Outcome; NetworkFailure покрывается Failure(IOException))
- [ ] T023 [P] Объявить `com.launcher.api.keys.VaultError` sealed class: `Network | Unauthorized | NotFound | Conflict | Malformed` в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/VaultError.kt` (FR-025, per contracts/recovery-vault-v1.md — `Malformed` для shape-валидации Firestore read'а)
- [ ] T024 [P] Объявить `com.launcher.api.keys.RecoveryError` sealed class: `WrongPassphrase | MalformedVault | NoVaultPresent | TooManyAttempts` в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/RecoveryError.kt` (FR-027)

### Fake adapters (parallel)

- [ ] T025 [P] Реализовать `FakeIdentityProof` в `core/keys/src/commonTest/kotlin/com/launcher/api/keys/fakes/FakeIdentityProof.kt` — возвращает deterministic `AuthIdentity` по конструктору (FR-006, FR-007, mock-first development per CLAUDE.md rule 6)
- [ ] T026 [P] Реализовать `FakeRecoveryKeyVault` в `core/keys/src/commonTest/kotlin/com/launcher/api/keys/fakes/FakeRecoveryKeyVault.kt` — in-memory ConcurrentMap<uid, RecoveryVaultBlob> (FR-008, rule 6)
- [ ] T027 [P] Реализовать `FakeKeyRegistry` в `core/keys/src/commonTest/kotlin/com/launcher/api/keys/fakes/FakeKeyRegistry.kt` — in-memory map для прогона ConfigCipher тестов без Keystore (FR-004, rule 6)

### Contract tests (один на порт, parallel)

- [ ] T028 [P] Contract test `KeyRegistryContract` в `core/keys/src/commonTest/kotlin/com/launcher/api/keys/contracts/KeyRegistryContractTest.kt`: register → has → get roundtrip; unknown DEK → `NotFound`; повторная регистрация под тем же именем → `Conflict` или overwrite (per contracts/key-registry-v1.md, FR-004)
- [ ] T029 [P] Contract test `RootKeyManagerContract` в `core/keys/src/commonTest/kotlin/com/launcher/api/keys/contracts/RootKeyManagerContractTest.kt`: getOrCreate дважды для одного UID → тот же RootKey; для разных UID → разные ключи (FR-003, FR-031)
- [ ] T030 [P] Contract test `IdentityProofContract` в `core/keys/src/commonTest/kotlin/com/launcher/api/keys/contracts/IdentityProofContractTest.kt`: currentIdentity до Sign-In → null; после → возвращает `AuthIdentity` (per contracts/identity-proof-v1.md, FR-006)
- [ ] T031 [P] Contract test `RecoveryKeyVaultContract` в `core/keys/src/commonTest/kotlin/com/launcher/api/keys/contracts/RecoveryKeyVaultContractTest.kt`: store → fetch → equal; fetch для unknown UID → `NotFound`; delete → fetch → `NotFound` (per contracts/recovery-vault-v1.md, FR-008)
- [ ] T032 [P] Contract test `ConfigCipherContract` в `core/keys/src/commonTest/kotlin/com/launcher/api/keys/contracts/ConfigCipherContractTest.kt`: seal → open → byte-equal; tampered ciphertext → `AeadAuthFailed` (per contracts/sealed-config-v1.md, FR-016, FR-026)

### DI scaffolding

- [ ] T033 Создать `app/src/main/java/com/launcher/app/di/KeysModule.kt` — Koin/Hilt модуль с биндингами port→adapter (пока пустой, биндинги добавляются в US-фазах) (plan.md Project Structure)

**Checkpoint**: 5 ports declared, 5 wire-formats, 5 error types, 3 fake adapters, 5 contract tests passing. Phase 3+ может стартовать.

---

## Phase 3: User Story 1 (Priority: P1) — Cloud-конфиг улетает зашифрованным MVP

**Story goal**: Admin пушит ConfigDocument → Firestore видит только opaque bytes. 0 plaintext-имён/телефонов/labels в storage.

**Independent Test**: JVM-тест с Firestore Emulator — push fixture-config → прямое чтение из Emulator → grep по «Bobby Tables 555-1234» возвращает 0 совпадений; обратный open → byte-equal.

### Implementation — ConfigCipher + DEK registration

- [ ] T040 [US1] Реализовать `AeadConfigCipherImpl` в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/impl/AeadConfigCipherImpl.kt`: использует F-CRYPTO `AeadCipher` (XChaCha20-Poly1305) + ключ из `KeyRegistry.getDek("config-cipher-aead-v1")`; AAD = `uid || schemaVersion` (FR-015, FR-016, FR-020)
- [ ] T041 [US1] Реализовать `RootKeyManagerImpl` в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/impl/RootKeyManagerImpl.kt`: generate 256-bit random root key, wrap через F-CRYPTO `SecureKeystore`, lookup-by-identity через UID namespace (FR-003, FR-031)
- [ ] T042 [US1] Реализовать `KeyRegistryImpl` в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/impl/KeyRegistryImpl.kt`: name → WrappedDek storage, ключи partitioned by `AuthIdentity.stableId`, schemaVersion check на чтение (FR-004, FR-005, FR-031)
- [ ] T043 [US1] В `RootKeyManagerImpl.getOrCreate` при первом создании root key — авторегистрировать `config-cipher-aead-v1` DEK через `KeyRegistry.registerDek` (FR-015)
- [ ] T044 [US1] Реализовать 256 KB size enforcement в `AeadConfigCipherImpl.seal` — превышение → `CryptoError.ConfigTooLarge` (FR-029)

### Firestore adapter (app-layer)

- [ ] T045 [US1] Реализовать `FirestoreRecoveryKeyVault` в `app/src/main/java/com/launcher/app/data/recovery/FirestoreRecoveryKeyVault.kt` — Firebase Firestore SDK импортируется ТОЛЬКО здесь; путь `users/{uid}/recovery-key`; mapping `RecoveryVaultBlob` ↔ Firestore document (FR-009, CLAUDE.md rule 2 ACL)
- [ ] T046 [US1] Реализовать `NoOpRecoveryKeyVault` в `app/src/main/java/com/launcher/app/data/recovery/NoOpRecoveryKeyVault.kt` — все операции → `VaultError.Unauthorized` (для Huawei/non-GMS) (FR-028, OEM matrix)
- [ ] T047 [US1] Реализовать `GoogleSignInIdentityProof` в `app/src/main/java/com/launcher/app/data/identity/GoogleSignInIdentityProof.kt` — wraps F-4 `AuthProvider`; единственное место, где AuthProvider использован вне F-4 (FR-007)
- [ ] T048 [US1] Реализовать `NoOpIdentityProof` в `app/src/main/java/com/launcher/app/data/identity/NoOpIdentityProof.kt` — `requestSignIn()` → `IdentityError.NoSupportedProvider` (FR-028)
- [ ] T049 [US1] В `app/src/main/java/com/launcher/app/di/KeysModule.kt` подключить биндинги через F-4 `AuthAdapterSelector`: GMS → `GoogleSignInIdentityProof` + `FirestoreRecoveryKeyVault`; non-GMS → NoOp-варианты (FR-028, OEM matrix Huawei row)

### Integration with spec 008 push pipeline (touch only adapter layer)

- [ ] T050 [US1] Подключить `ConfigCipher.seal` в существующий ConfigDocument push pipeline (`app/src/main/java/com/launcher/app/data/sync/` или эквивалент из spec 008): plaintext config → seal → push SealedConfig в Firestore (FR-018, не модифицируя domain spec 008)
- [ ] T051 [US1] Подключить `ConfigCipher.open` в pull pipeline: pull SealedConfig из Firestore → open → передать plaintext дальше в app cache (FR-018, three-tier cache model invariant)

### Tests (parallel)

- [ ] T052 [P] [US1] Roundtrip test `ConfigCipherRoundtripTest` в `core/keys/src/commonTest/kotlin/com/launcher/api/keys/ConfigCipherRoundtripTest.kt`: seal → open → byte-equal на 10 разных config-fixtures (CLAUDE.md rule 7, SC-002 perf check < 50ms)
- [ ] T053 [P] [US1] Backward-compat test `SealedConfigBackwardCompatTest` в `core/keys/src/commonTest/kotlin/com/launcher/api/keys/SealedConfigBackwardCompatTest.kt`: читает fixture `sealed-config-v1.json` (schemaVersion=1) → расшифровывает byte-equal с ожидаемым plaintext fixture (CLAUDE.md rule 5)
- [ ] T054 [P] [US1] Создать fixtures `core/keys/src/commonTest/resources/fixtures/sealed-config-v1.json` + `sealed-config-v1-plaintext.json` + `sealed-config-v1-key.hex` (fixed root key, fixed nonce, fixed ciphertext) для backward-compat test (CLAUDE.md rule 5)
- [ ] T055 [P] [US1] Size-limit test `ConfigCipherSizeLimitTest`: config > 256 KB → `CryptoError.ConfigTooLarge`, не crash (FR-029)
- [ ] T056 [US1] Firestore Emulator integration test `CloudConfigEncryptionE2ETest` в `app/src/androidTest/java/com/launcher/app/data/sync/CloudConfigEncryptionE2ETest.kt`: setup fixture config с recognisable string «Bobby Tables 555-1234» → seal+push → прямое чтение Firestore Emulator → assert 0 совпадений по grep (SC-001 acceptance)
- [ ] T057 [US1] AAD identity-binding test `AadIdentityBindingTest` в `core/keys/src/commonTest/kotlin/com/launcher/api/keys/`: seal под uid1 → попытка open с aad=uid2 → `AeadAuthFailed` (FR-020, replay defence)

**Checkpoint US1**: Firestore Emulator integration test passes; SC-001 verified. MVP demo-able: «cloud config opaque».

---

## Phase 4: User Story 2 (Priority: P1) — Recovery после потери телефона MVP

**Story goal**: Admin ставит app на новом телефоне → Sign-In тем же Google → вводит passphrase (Autofill подставит) → recovery успешен → byte-equal config.

**Independent Test**: setup на client A через FakeIdentityProof + Firestore Emulator → wipe Keystore state → recovery на client B (тот же UID) → byte-equal восстановленного config.

### Argon2id KDF + RecoveryKeyVault wire format

- [ ] T060 [US2] Реализовать `Argon2idPassphraseKdf` в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/impl/Argon2idPassphraseKdf.kt` — passphrase (CharArray) + salt + **info-string включающий UID** (для domain separation между identities, FR-021) + params → 32-byte key через F-CRYPTO `KeyDerivation` port; CharArray passphrase обнуляется немедленно после derivation; **derived 32-byte key также обнуляется** после wrap/unwrap operation completes (FR-013, FR-013a, FR-021, FR-030, G-1 finding)
- [ ] T061 [US2] В `RootKeyManagerImpl.getOrCreate` добавить путь «Keystore пуст → fetchVault → unwrap через passphrase → restore root»: запрашивает passphrase через injected `PassphrasePrompter` port (новый, в commonMain) (FR-003, FR-027, US2 acceptance)
- [ ] T062 [US2] Объявить `com.launcher.api.keys.PassphrasePrompter` port в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/PassphrasePrompter.kt`: `requestSetupPassphrase(): Outcome<CharArray, RecoveryError>`, `requestRecoveryPassphrase(): Outcome<CharArray, RecoveryError>` (port для UI bridge). Add inline `// TODO(ios H-7): CharArray zeroize semantics в Kotlin/Native (iosX64/iosArm64) отличается от JVM; iOS deferred per F-CRYPTO decisions 2026-06-17. При активации iOS — verify, что `for (i in indices) charArr[i] = ' '` действительно обнуляет underlying memory на iOS target.`
- [ ] T063 [US2] Реализовать setup-flow в `RootKeyManagerImpl`: generate root key → `PassphrasePrompter.requestSetupPassphrase` → Argon2id derive wrapKey → wrap root key через `AeadCipher` → `RecoveryKeyVault.storeVault` (US2 acceptance 1, FR-003)
- [ ] T064 [US2] Реализовать recovery-flow в `RootKeyManagerImpl`: fetchVault → если есть → `PassphrasePrompter.requestRecoveryPassphrase` → derive → unwrap; failure → `RecoveryError.WrongPassphrase`; AEAD auth failure tag mismatch → `WrongPassphrase` (не `MalformedVault`) (FR-027, US2 acceptance 2-3)
- [ ] T065 [US2] Implement attempt counter: 3 wrong passphrases подряд → `RecoveryError.TooManyAttempts` + UI offers «настроить заново» (US2 acceptance 4)

### Compose UI — 3 screens

- [ ] T070 [P] [US2] Реализовать `RecoveryPassphraseSetupScreen` в `app/src/main/java/com/launcher/app/ui/recovery/RecoveryPassphraseSetupScreen.kt`: EditText с `autofillHints = listOf("newPassword")`, кнопка «Скопировать в буфер обмена» с auto-clear через 60s + явный `ClipboardManager.clearPrimaryClip()` на navigation away; passphrase НЕ показывается plaintext на экране; min 8 chars validation (FR-011, FR-013a, FR-014)
- [ ] T071 [P] [US2] Реализовать `RecoveryPassphraseEntryScreen` в `app/src/main/java/com/launcher/app/ui/recovery/RecoveryPassphraseEntryScreen.kt`: EditText с `autofillHints = listOf("password")`; ошибка «неверный passphrase, попробуйте ещё раз»; после 3 fail — кнопка «Настроить как новое устройство» (FR-012, FR-027, US2 acceptance 3-4)
- [ ] T072 [P] [US2] Реализовать `RecoveryFallbackScreen` в `app/src/main/java/com/launcher/app/ui/recovery/RecoveryFallbackScreen.kt`: показывает после 3 неудачных попыток или при `MalformedVault`; объясняет последствия (потеря cloud config + потеря pair-keys, нужен заново pairing per S-2) (US2 acceptance 4, trouble case 2.b)
- [ ] T072a [US2] Test `ThreeAttemptsTooManyAttemptsFallbackTest` в `core/keys/src/commonTest/kotlin/com/launcher/api/keys/`: 3 wrong passphrase подряд → `RecoveryError.TooManyAttempts` → ViewModel state переходит в `Error(TooManyAttempts)` → UI навигация на `RecoveryFallbackScreen` (FR-027, G-2 finding)
- [ ] T073 [US2] Реализовать `RecoveryViewModel` в `app/src/main/java/com/launcher/app/ui/recovery/RecoveryViewModel.kt`: state machine `Idle | SettingUp | Restoring | Error(RecoveryError) | Done`; bridges Compose UI ↔ `RootKeyManager` ↔ `PassphrasePrompter` (plan.md, US2 acceptance)
- [ ] T074 [US2] Реализовать `AndroidPassphrasePrompter` adapter в `app/src/main/java/com/launcher/app/ui/recovery/AndroidPassphrasePrompter.kt` — реализует port через ViewModel state flow (CLAUDE.md rule 2 ACL — UI-types не в domain)
- [ ] T075 [US2] Подключить recovery screens в navigation graph (`app/src/main/java/com/launcher/app/ui/nav/` или эквивалент): trigger при первом запуске после Sign-In если `RootKeyManager.getOrCreate` возвращает `RecoveryRequired`

### DI wiring extensions

- [ ] T076 [US2] Расширить `app/src/main/java/com/launcher/app/di/KeysModule.kt`: bind `Argon2idPassphraseKdf`, `RootKeyManagerImpl`, `AeadConfigCipherImpl`, `AndroidPassphrasePrompter`

### Tests (parallel)

- [ ] T080 [P] [US2] Roundtrip test `RecoveryVaultRoundtripTest` в `core/keys/src/commonTest/kotlin/com/launcher/api/keys/RecoveryVaultRoundtripTest.kt`: storeVault → fetchVault → equal blob (CLAUDE.md rule 7)
- [ ] T081 [P] [US2] Backward-compat test `RecoveryVaultBackwardCompatTest` в `core/keys/src/commonTest/kotlin/com/launcher/api/keys/RecoveryVaultBackwardCompatTest.kt`: читает fixture `recovery-vault-v1.json` (schemaVersion=1) → unwrap правильным passphrase → byte-equal с ожидаемым root key fixture (CLAUDE.md rule 5)
- [ ] T082 [P] [US2] Создать fixtures `core/keys/src/commonTest/resources/fixtures/recovery-vault-v1.json` + `recovery-vault-v1-passphrase.txt` + `recovery-vault-v1-rootkey.hex` (CLAUDE.md rule 5)
- [ ] T083 [P] [US2] Wrong-passphrase test `RecoveryWrongPassphraseTest`: unwrap c неправильным passphrase → `RecoveryError.WrongPassphrase`, не `MalformedVault` (FR-027, SC-004)
- [ ] T084 [P] [US2] Malformed-vault test `RecoveryMalformedVaultTest`: corrupted blob bytes → `RecoveryError.MalformedVault` (отличимо от WrongPassphrase) (FR-027)
- [ ] T085 [P] [US2] Argon2id perf test `Argon2idPerfTest`: derivation < 500ms на test JVM с interactive params (SC-002)
- [ ] T086 [US2] Firestore Emulator E2E test `RecoveryE2ETest` в `app/src/androidTest/java/com/launcher/app/ui/recovery/RecoveryE2ETest.kt`: client A setup → push config → wipe Keystore → client B (same UID, clean state) → fetchVault → enter passphrase → restore root → pull config → assert byte-equal (SC-003, US2 independent test)
- [ ] T087 [US2] Sign-out/Sign-in no-recovery test `SignOutSignInNoRecoveryTest`: после setup → sign-out → sign-in под тем же UID → 0 recovery flow triggered (Keystore still has root) (SC-005, edge case 2.d)
- [ ] T088 [US2] Passphrase memory hygiene test `PassphraseMemoryHygieneTest`: после Argon2id derivation CharArray заполнен '' (FR-013)
- [ ] T088a [US2] Derived key zeroize test `DerivedKeyZeroizeTest`: после wrap/unwrap операции в `RootKeyManagerImpl` derived 32-byte `ByteArray` (output Argon2id) обнулён (`all == 0`) (FR-013 расширение, G-1 finding)
- [ ] T089 [US2] Clipboard auto-clear test `ClipboardAutoClearTest`: после copy → navigation away → `getPrimaryClip()` пуст (FR-013a)

**Checkpoint US2**: Recovery E2E работает; SC-003, SC-004, SC-005 verified. MVP полностью demo-able: «потеря телефона → новый → восстановление».

---

## Phase 5: User Story 3 (Priority: P2) — Foundation для будущих фич

**Story goal**: Проверить, что `KeyRegistry` корректно поддерживает множественные DEKs + identity isolation + cross-app forward-compat invariants.

**Independent Test**: симулировать S-2 регистрацию `pair-x25519-v1` DEK → recovery → проверить, что mock DEK тоже доступен.

- [ ] T100 [P] [US3] Multiple-DEK test `KeyRegistryMultiDekTest` в `core/keys/src/commonTest/kotlin/com/launcher/api/keys/KeyRegistryMultiDekTest.kt`: registerDek("config-cipher-aead-v1") + registerDek("pair-x25519-v1" mock) + registerDek("photo-aead-v1" mock) → все три доступны через getDek (FR-004, US3 acceptance 1)
- [ ] T101 [P] [US3] Recovery-restores-all-DEKs test `RecoveryRestoresAllDeksTest`: setup + register 3 DEKs → wipe → recovery → все 3 DEKs автоматически доступны без passphrase prompt #2 (US3 acceptance 2, SC-008 partial)
- [ ] T102 [P] [US3] Identity isolation test `MultiIdentityIsolationTest` в `core/keys/src/commonTest/kotlin/com/launcher/api/keys/MultiIdentityIsolationTest.kt`: register DEK под uid1 → switch to uid2 → `hasDek` returns false для uid2 namespace → register тот же name под uid2 → switch back to uid1 → uid1 DEK не пересоздан (FR-031, SC-006)
- [ ] T103 [P] [US3] Forward-compat unknown DEK test `UnknownDekIgnoreTest`: storage содержит DEK с name «future-spec-v2» → старый клиент игнорирует, не падает (FR-005, US3 acceptance 3)
- [ ] T104 [P] [US3] Scale test `KeyRegistry100DeksTest`: register 100 DEKs → all readable, no degradation (SC-008)
- [ ] T105 [P] [US3] KeyRegistry storage backward-compat test `KeyRegistryStorageBackwardCompatTest`: читает fixture `multi-dek-keyregistry-v1.json` (schemaVersion=1) → все DEKs accessible (CLAUDE.md rule 5, FR-005)
- [ ] T106 [P] [US3] Создать fixture `core/keys/src/commonTest/resources/fixtures/multi-dek-keyregistry-v1.json` с 3 DEKs (CLAUDE.md rule 5)

**Checkpoint US3**: Foundation validated для S-2/S-5/V-2 регистраций. Cross-spec note: S-2 будет вызывать `KeyRegistry.registerDek("pair-x25519-v1", ...)` поверх unchanged F-5 API.

---

## Phase 6: User Story 4 (Priority: P2) — Senior из cache (verification)

**Story goal**: Подтвердить, что F-5 не ломает three-tier cache model. F-5 шифрует ТОЛЬКО на adapter layer; app cache продолжает хранить plaintext locally.

**Independent Test**: integration test «pull from Firestore → decrypt → app cache (plaintext) → offline → Senior всё ещё видит config из cache».

- [ ] T110 [US4] Cache invariant test `F5DoesNotBreakAppCacheTest` в `app/src/androidTest/java/com/launcher/app/cache/F5DoesNotBreakAppCacheTest.kt`: pull SealedConfig → decrypt → app cache (plaintext) → отключить network → Senior всё ещё видит config (US4 acceptance 1, memory `project_config_cache_model`). **Additionally** (G-5 finding): verify `ConfigCipher.open` возвращает plaintext **только caller'у**, и что `core/keys/` module НЕ persist'ит plaintext в любой encrypted/cached форме (caching = ответственность spec 008, не F-5) — assert grep по `core/keys/` codebase на отсутствие persistence calls с plaintext config payload.
- [ ] T111 [US4] Local plaintext-only verification: на app cache layer plaintext, на disk через `allowBackup=false` защищено от Google Drive backup leak'а — verify `app/src/main/AndroidManifest.xml` содержит `android:allowBackup="false"` (FR-018, clarify Q «local encryption» = A)
- [ ] T112 [US4] Offline senior smoke test через skill `android-emulator`: запустить emulator, установить app, выполнить pull, отключить network на эмуляторе, перезапустить app — assert Senior screen rendering из cache (US4 acceptance 1)
- [ ] T113 [US4] Empty-config edge case test: Firestore возвращает пустой config (admin ещё не push'нул) → Senior видит wizard / default (US4 acceptance 2, явный note: это S-1 territory, F-5 только не блокирует)

**Checkpoint US4**: three-tier cache invariant подтверждён, F-5 не регрессирует Senior offline UX.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Security hardening, fitness functions, OEM coverage, performance benchmarks, docs.

### Fitness functions (CLAUDE.md rule 7)

- [ ] T120 [P] Finalize Detekt rule «no `com.ionspin.kotlin.crypto.*` imports outside `com.launcher.api.crypto (libsodium adapter)` package» в `config/detekt/detekt.yml` или `core/keys/detekt-config.yml`; run `./gradlew :core:keys:detekt` → 0 violations (CLAUDE.md rule 7, ACL enforcement)
- [ ] T121 [P] Add Detekt rule «no `com.google.firebase.*` imports outside `app/src/main/java/com/launcher/app/data/recovery/` and `data/identity/`» (CLAUDE.md rule 1, domain isolation)
- [ ] T122 [P] Add module-graph check (Konsist или custom Gradle task): `core:keys` module MUST NOT depend on `app` or `:firebase` modules (CLAUDE.md rule 1)
- [ ] T122a [P] Compile-time test (Detekt custom rule) `NoCrossUidApiInKeyRegistryTest`: verify, что public API `KeyRegistry`/`RootKeyManager`/`ConfigCipher` НЕ содержит ни одного method, принимающего два UID параметра (`uid1` + `uid2`) — strict isolation per FR-031a (G-3 finding, declarative verification)
- [ ] T122b [P] AndroidTest perf benchmark `Argon2idAndroidEmulatorBenchmark` в `app/src/androidTest/java/com/launcher/app/perf/`: measure Argon2id derivation latency на эмуляторе `pixel_5_api_34` с реальным JNI overhead libsodium (in contrast to T129 на pure JVM); assert < 500ms — covers SC-002 emulator path (G-4 finding)
- [ ] T122c [P] Security test `AadSchemaVersionEarlyValidationTest`: `ConfigCipher.open()` MUST validate `sealed.schemaVersion` **до** передачи в AEAD layer; unsupported schemaVersion → `CryptoError.AlgorithmUnsupported` без attempt'а decrypt (H-3 finding — защита от бесконечной попытки decrypt'а неподдерживаемой версии)
- [ ] T122d [P] Security test `EmptyUidRejectionTest` в `core/keys/src/commonTest/kotlin/com/launcher/api/keys/`: `KeyRegistry.getDek(name)` при `currentIdentity()` returns `AuthIdentity` с пустым `stableId` → fail с `KeyRegistryError.RootKeyUnavailable`; защищает от cross-UID alias formation race в `KeyRegistryImpl` (H-4 finding)

### H-1 mitigation — persistent attempt counter (DataStore, WhatsApp/Signal pattern)

- [ ] T122e [US2] Реализовать `PassphraseAttemptCounter` interface в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/PassphraseAttemptCounter.kt`: операции `recordFailedAttempt(uid)`, `currentCount(uid): Int`, `resetIfExpired(uid)`, `clear(uid)` — все persistent (FR-027 H-1 mitigation)
- [ ] T122f [US2] Реализовать `DataStorePassphraseAttemptCounter` в `app/src/main/java/com/launcher/app/data/recovery/DataStorePassphraseAttemptCounter.kt`: ключ `passphrase_attempts_${uid}`, structure `{ attemptCount: Int, firstAttemptAt: Long, lastAttemptAt: Long }`; auto-reset через 1 час с `lastAttemptAt`; DataStore — `androidx.datastore:datastore-preferences` (FR-027 H-1 mitigation)
- [ ] T122g [US2] Wire `PassphraseAttemptCounter` в `RootKeyManagerImpl.recovery flow` (T064): перед каждой попыткой → `resetIfExpired` → check `currentCount < 3`; на failed attempt → `recordFailedAttempt`; на 3+ → `RecoveryError.TooManyAttempts`; на success → `clear(uid)` (FR-027 H-1 mitigation)
- [ ] T122h [P] [US2] Test `PassphraseAttemptCounterPersistenceTest`: kill ViewModel mid-recovery → re-launch → counter persists; 3 attempts → app restart → still locked out; через 1 час `lastAttemptAt` → counter resets (FR-027 H-1 mitigation, H-1 acceptance)
- [ ] T122i [P] [US2] Test `AttemptCounterClearAppDataBypassTest`: simulate Clear App Data между попытками → counter resets, **но** local root key cache тоже cleared → attacker starts from zero, no progress. Document accepted residual risk в comment (FR-027 H-1 acceptance, accepted residual)

### H-2 mitigation — schema-version downgrade protection

- [ ] T122j [US2] Update Firestore Rules в `firebase/firestore.rules` (T123 расширение): add `allow update: if request.resource.data.schemaVersion >= resource.data.schemaVersion` для `users/{uid}/recovery-key` и `users/{uid}/config` (FR-028a H-2 mitigation)
- [ ] T122k [P] [US2] Rules unit test через `@firebase/rules-unit-testing` в `firebase/test/h2-downgrade.test.js`: owner update with `schemaVersion = current` → allow; owner update with `schemaVersion > current` → allow; owner update with `schemaVersion < current` → reject (FR-028a H-2 mitigation)
- [ ] T122l [US2] Реализовать `SchemaVersionMemory` interface в `core/keys/src/commonMain/kotlin/com/launcher/api/keys/api/SchemaVersionMemory.kt`: операции `recordSeenVersion(uid, blobKind, version)`, `lastSeenVersion(uid, blobKind): Int?` — TOLU (Trust On Last Use) per identity per blob kind (FR-028b)
- [ ] T122m [US2] Реализовать `DataStoreSchemaVersionMemory` в `app/src/main/java/com/launcher/app/data/recovery/DataStoreSchemaVersionMemory.kt`: ключ `tolu-${uid}-${blobKind}` (blobKind = "recoveryVault" | "configBlob"); store `max(stored, fetched)` always (FR-028b)
- [ ] T122n [US2] Wire `SchemaVersionMemory` в `FirestoreRecoveryKeyVault.fetchVault` и `AeadConfigCipherImpl.open`: при чтении → `recordSeenVersion`; при чтении нового документа — check `fetched.schemaVersion >= lastSeenVersion` else return `VaultError.SchemaDowngradeDetected` / `CryptoError.SchemaDowngradeDetected` (FR-028b)
- [ ] T122o [P] [US2] Добавить `SchemaDowngradeDetected` case в `VaultError` (T023) и `CryptoError` (F-CRYPTO consumer-extension); update data-model.md §5 (FR-028b)
- [ ] T122p [P] [US2] Test `SchemaDowngradeDetectionTest`: write v1 vault → read → memory = 1; client получает (моделируемый) v0 blob → reject as `SchemaDowngradeDetected`; client получает v2 → accept, memory = 2; повторно v1 → reject (FR-028b, H-2 acceptance)
- [ ] T122q [P] [US2] Test `SchemaDowngradeUiTest`: при `SchemaDowngradeDetected` → UI показывает «обнаружено tampering данных в облаке, требуется переустановка / обращение в support»; кнопка «Настроить как новое устройство» как fallback (FR-028b UI requirement)

### Firestore Security Rules

- [ ] T123 Update `firebase/firestore.rules`: add rule for `users/{uid}/recovery-key` — read/write only if `request.auth.uid == uid`; same for `users/{uid}/config` (если ещё не закрыто spec 008) (FR-009, contracts/firestore-security-rules.md)
- [ ] T124 Deploy updated rules via `firebase deploy --only firestore:rules` (manual step, document в `docs/dev/manual-setup-f5-firestore-rules.md`)
- [ ] T125 [P] Add Firestore Emulator rules-test `RecoveryKeyVaultSecurityTest` в `app/src/androidTest/java/com/launcher/app/data/recovery/`: anonymous read → denied; other UID read → denied; owner read → allowed (FR-009, SC-001)

### OEM matrix & non-GMS

- [ ] T126 [P] Verify `NoOpIdentityProof` + `NoOpRecoveryKeyVault` wiring через `AuthAdapterSelector` на non-GMS profile: `IdentityProof.requestSignIn` → `NoSupportedProvider`; UI shows «облачные функции недоступны на этом устройстве» (FR-028, SC-007)
- [ ] T127 OEM smoke matrix через skill `android-emulator`: pixel_5_api_34 (baseline Autofill UX with Google Password Manager), samsung-like AVD if available; document результаты в `specs/018-f5-config-e2e-encryption/oem-smoke-results.md`; пометить `TODO(physical-device)` для Samsung Galaxy A / Redmi Note / Huawei P (OEM matrix table в spec.md)

### Performance benchmarks

- [ ] T128 [P] Benchmark `ConfigCipherBenchmark` в `core/keys/src/jvmTest/kotlin/com/launcher/api/keys/perf/`: 10 KB config seal+open < 50 ms — assert SC-002
- [ ] T129 [P] Benchmark `Argon2idBenchmark`: derivation с interactive params (64MB / 3 / 1) < 500 ms — assert SC-002
- [ ] T130 [US2] E2E recovery latency benchmark `RecoveryE2ELatencyTest` в `app/src/androidTest/`: full flow (Sign-In → fetchVault → unwrap → restore root → getDek → decrypt config) < 3 sec при network < 500ms — assert SC-003

### Constitution re-check & traceability

- [ ] T131 Re-run procedure-constitution-check на plan.md после implementation: все 8 gates ещё PASS; никаких violations не появилось во время реализации (Article XVI, CLAUDE.md)
- [ ] T132 Re-run procedure-cross-artifact-trace: каждый FR покрыт хотя бы одной задачей; каждая US имеет independent test path; каждый contract имеет contract test (см. Validation Checklist ниже)

### Documentation

- [ ] T133 [P] Update root `CLAUDE.md` или `docs/governance/document-map.md`: добавить ссылку на `core/keys/` module и spec 018
- [ ] T134 [P] Update `docs/dev/server-roadmap.md`: добавить SRV-RECOVERY-001 entry «когда переедем на свой сервер — `OwnServerRecoveryKeyVault` adapter заменит Firestore variant; F-5 domain не трогается» (CLAUDE.md rule 8)
- [ ] T135 [P] Update `docs/product/roadmap.md` шаг 3 status: F-5 → done; unblock S-2, S-4, S-5, V-2, V-3, S-8, S-9 dependencies
- [ ] T136 [P] Update `docs/dev/project-backlog.md`: close TODO-SEC-CRITICAL-024
- [ ] T137 [P] Inline TODO sweep: убедиться что `// TODO(future-spec algorithm-migration): ...` присутствует в `Argon2idPassphraseKdf.kt` и `AeadConfigCipherImpl.kt` (FR-033); `// TODO(future-spec passphrase-change): ...` в `RootKeyManagerImpl.kt`; `// TODO(capability-registry): ConfigCipher.open exposes ConfigDocument to potential AI affordance layer — AI must run client-side only` в `ConfigCipher.kt` (FR-033, AI Affordance section spec)
- [ ] T138 Run `quickstart.md` validation end-to-end (manual): новый developer следует quickstart → может собрать `core/keys` + запустить все JVM tests + поднять Firestore Emulator + пройти RecoveryE2ETest

---

## Dependencies & Execution Order

### Phase Dependencies

```
Setup (P1) ──► Foundational (P2) ──┬─► US1 (P3) ──┐
                                    ├─► US2 (P4) ──┼─► Polish (P7)
                                    ├─► US3 (P5) ──┤
                                    └─► US4 (P6) ──┘
```

- **Phase 1 → Phase 2**: blocking. Module skeleton needed before port declarations.
- **Phase 2 → Phase 3..6**: blocking (per template). После Phase 2 все US фазы могут стартовать параллельно (если есть капасити).
- **Phase 3, 4, 5, 6 → Phase 7**: Polish зависит от завершённой реализации (perf benchmarks нужен реальный code).
- **US1 ↔ US2**: US2 переиспользует `RootKeyManagerImpl`, `KeyRegistryImpl`, `AeadConfigCipherImpl` из US1, но обе могут разрабатываться параллельно (US2 в начале пишет setup-flow, использующий те же impl-классы).

### Within-Phase Dependencies

- **Phase 2**: T010..T024 parallel (разные файлы); T025..T027 parallel; T028..T032 parallel (require T010-T024 done); T033 last.
- **Phase 3 US1**: T040..T044 sequential (один за другим в core/keys/impl/); T045..T049 parallel (разные файлы app-layer); T050..T051 sequential (touch pipeline); T052..T055 parallel; T056, T057 после T050/T051.
- **Phase 4 US2**: T060..T062 sequential; T063..T065 после T060-T062; T070..T072 parallel (3 разных Compose screens); T073..T075 sequential после T070-T072; T076 после T060+T063; T080..T085 parallel; T086..T089 после implementation.

### MVP cutoff

После T089 (Phase 4 done) — MVP полностью функционален. Можно ship'нуть и продолжить параллельно с Phase 5/6/7.

---

## Parallel Execution Examples

**Phase 2 — Foundational batch 1 (port declarations)**:
T010 + T011 + T012 + T013 + T014 — 5 разработчиков параллельно.

**Phase 2 — Foundational batch 2 (wire-formats)**:
T015 + T016 + T017 + T018 + T019 — 5 разработчиков параллельно.

**Phase 2 — Foundational batch 3 (sealed types)**:
T020 + T021 + T022 + T023 + T024 — 5 разработчиков параллельно.

**Phase 2 — Foundational batch 4 (fakes + contract tests)**:
T025..T027 + T028..T032 — 8 tasks параллельно.

**Phase 3 US1 — adapter batch**:
T045 + T046 + T047 + T048 — 4 разных файла, parallel.

**Phase 3 US1 — test batch**:
T052 + T053 + T054 + T055 — parallel.

**Phase 4 US2 — UI batch**:
T070 + T071 + T072 — 3 разных Compose screens parallel.

**Phase 4 US2 — test batch**:
T080..T085 — 6 tasks parallel.

**Phase 5 US3** — почти всё parallel (T100..T106).

**Phase 7 Polish** — большинство [P].

---

## Validation Checklist

### Coverage: каждый FR → хотя бы одна task

| FR | Tasks |
|----|-------|
| FR-001 (KMP module) | T001, T002, T003 |
| FR-002 (only F-CRYPTO primitives) | T002, T120 (Detekt) |
| FR-003 (RootKeyManager ops) | T011, T041, T061, T063, T064 |
| FR-004 (KeyRegistry ops) | T010, T042, T100 |
| FR-005 (schemaVersion in KeyRegistry) | T018, T103, T105 |
| FR-006 (IdentityProof ops) | T012, T025, T030 |
| FR-007 (Google Sign-In reuse F-4) | T047 |
| FR-008 (RecoveryKeyVault ops) | T013, T026, T031 |
| FR-009 (Firestore path + rules) | T045, T123, T124, T125 |
| FR-010 (RecoveryVaultBlob schema) | T016, T017, T060 |
| FR-011 (autofillHints newPassword) | T070 |
| FR-012 (autofillHints password) | T071 |
| FR-013 (no plaintext passphrase) | T060, T088 |
| FR-013a (clipboard auto-clear) | T070, T089 |
| FR-014 (min 8 chars) | T070 |
| FR-015 (auto-register config-cipher-aead-v1) | T043 |
| FR-016 (ConfigCipher port) | T014, T040, T052 |
| FR-017 (SealedConfig wire format) | T015, T053, T054 |
| FR-018 (ciphertext only on server) | T050, T111 |
| FR-019 (one CEK per lifetime) | T041, T043 (implicit, no rotation logic) |
| FR-020 (AAD uid binding) | T040, T057 |
| FR-021 (KDF info-string uid) | T060 (Argon2id salt + uid in info) |
| FR-022 (RecoveryVaultBlob app-agnostic) | T016, T134 |
| FR-023 (stable DEK names) | T010, T042 |
| FR-024 (no broker pattern in F-5, TODO note) | T013, T137 |
| FR-024a (single signing key future) | T134 (docs) |
| FR-025 (sealed error types) | T020..T024 |
| FR-026 (AeadAuthFailed not crash) | T032, T040 |
| FR-027 (WrongPassphrase vs MalformedVault) | T024, T064, T083, T084 |
| FR-028 (non-GMS NoSupportedProvider) | T046, T048, T049, T126 |
| FR-029 (256 KB limit) | T044, T055 |
| FR-030 (Argon2id interactive params) | T017, T060, T129 |
| FR-031 (UID-partitioned namespace) | T041, T042, T102 |
| FR-031a (no cross-UID ops) | T102 (implicit verification) |
| FR-032 (algorithm field cosuществование) | T015, T016 |
| FR-033 (inline TODO algorithm-migration) | T137 |
| FR-033a (server migration deferred) | T134 |

### Coverage: каждая US → independent test

- **US1**: T056 (Firestore Emulator grep test) ✓
- **US2**: T086 (Recovery E2E two-client test) ✓
- **US3**: T101 (recovery-restores-all-DEKs) + T102 (identity isolation) ✓
- **US4**: T110 (cache invariant) + T112 (offline emulator smoke) ✓

### Coverage: каждый Success Criterion

- SC-001 (0 plaintext in Firestore): T056, T125 ✓
- SC-002 (< 50 ms seal/open, < 500 ms Argon2id): T128, T129 ✓
- SC-003 (recovery < 3s): T130 ✓
- SC-004 (правильный/неправильный passphrase): T083, T086 ✓
- SC-005 (Sign-out/In без recovery): T087 ✓
- SC-006 (multi-identity isolation): T102 ✓
- SC-007 (non-GMS local mode): T126 ✓
- SC-008 (100+ DEKs): T104 ✓

### Coverage: каждый contract → contract test

- `key-registry-v1.md` → T028 ✓
- `recovery-vault-v1.md` → T031 ✓
- `sealed-config-v1.md` → T032 ✓
- `identity-proof-v1.md` → T030 ✓
- `firestore-security-rules.md` → T125 ✓

### Coverage: каждый wire-format → roundtrip + backward-compat

- `SealedConfig` → T052 (roundtrip) + T053 (backward-compat v1) ✓
- `RecoveryVaultBlob` → T080 (roundtrip) + T081 (backward-compat v1) ✓
- `KeyRegistry` storage (WrappedDek map) → T100 (multi-DEK roundtrip) + T105 (backward-compat v1) ✓

### Constitution re-check

- T131 — explicit task per Polish phase ✓

### Cross-artifact trace

- T132 — explicit task per Polish phase ✓

---

## Notes

- **[P]** = разные файлы, нет cross-dependencies на незавершённые tasks.
- **[US#]** label маппит task к user story (US1..US4) для traceability; Setup/Foundational/Polish tasks — без US-label.
- Все file paths абсолютные от repo root (`c:/work/launcher/` → представлены как POSIX paths без drive letter для cross-platform).
- Commit после каждой task или logical group (per CLAUDE.md «Output discipline»).
- Push в `018-f5-config-e2e-encryption` branch после каждого significant step.
- Open PR как только Phase 1 + первая Phase 2 task ready (per CLAUDE.md «Branching»).
- Не использовать `--no-verify` / `--no-gpg-sign` без explicit user approval.
- Cross-spec future-work notes inline в коде через `// TODO(future-spec ...)` — НЕ создаём новые backlog entries в этой спеке.

---

## Краткое резюме (для не-разработчика)

**Что внутри tasks.md**:

- **107 задач** разбиты на 7 фаз (101 base + 6 новых задач из analyze-report fixes 2026-06-19: T072a, T088a, T122a-d). Фазы 1+2+3+4 — это MVP, который можно показать (admin шифрует конфиг → теряет телефон → восстанавливается через passphrase).
- **Фаза 1 (Setup, 6 задач)**: создать новый модуль `core/keys/`, подключить в gradle, базовая структура.
- **Фаза 2 (Foundational, 24 задачи)**: объявить 5 интерфейсов (port'ов), 5 wire-format'ов (схем данных, что лежит в облаке), 5 типов ошибок, 3 fake-адаптера для тестов, 5 контрактных тестов.
- **Фаза 3 (US1, 18 задач)**: первый рабочий ConfigCipher — конфиг улетает в Firestore зашифрованным. Включает Firestore Emulator тест, который ищет fixture-имя «Bobby Tables 555-1234» в blob'ах и подтверждает, что 0 совпадений.
- **Фаза 4 (US2, 24 задачи)**: recovery flow — три Compose-экрана (придумать пароль, ввести пароль, fallback при забытом пароле) + Argon2id + интеграционный тест «потерял телефон → новый → пароль → byte-equal config».
- **Фаза 5 (US3, 7 задач)**: проверка, что в `KeyRegistry` можно регистрировать мок DEK'и от будущих фич (S-2, S-5) и они тоже восстановятся.
- **Фаза 6 (US4, 4 задачи)**: проверка, что F-5 не сломал работу бабушки из локального кэша при отсутствии интернета.
- **Фаза 7 (Polish, 19 задач)**: Detekt-правила (нельзя импортировать libsodium вне crypto-модуля), деплой Firestore security rules, OEM smoke на эмуляторах, perf benchmarks, обновление документации.
- В конце — таблица coverage'а: каждый из 33 FR-ов, 4 US, 8 SC и 5 контрактов имеет хотя бы одну задачу.
- **38 задач помечены [P]** — могут выполняться параллельно несколькими разработчиками.

**Что дальше**:

- `/speckit.analyze` — финальный pre-implementation audit (рекомендуется). Перепроверит, что после генерации tasks.md ничего не разъехалось между spec ↔ plan ↔ tasks ↔ contracts.
- Или сразу `/speckit.implement` — старт работы по T001.
