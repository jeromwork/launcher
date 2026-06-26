# Implementation Tasks: TASK-51 libsodium consolidation

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Branch**: `task-51-libsodium-consolidation`

> **Spec Kit phase**: `/speckit.tasks` output. Each task traces back to spec FR/US or plan §section. Tasks marked `[P]` are parallel-safe (different files, no shared state). Tasks marked `[deferred-<type>]` cannot complete in AI session and route to TASK-55 verification aggregator.

## Status snapshot (2026-06-26)

| Phase | Status | Commits |
|---|---|---|
| Phase 1 — Gradle stripping | ✅ done | `1e6be2e` |
| Phase 2 — Spec Kit pipeline | ✅ done | `20013d1`, `beca982`, `e342a76`, `e67e4bf`, `2adec37` |
| Phase 3 — `@SerialName` audit | ✅ done | `7eb6fa3` (T001+T002), baseline T003 PASS @ `7eb6fa3` |
| Phase 4 — Namespace rename | ✅ done | T010-T015 (this commit) |
| Phase 5 — Pairing-side rewrite | ✅ done | T020-T025 (this commit) |
| Phase 6 — Old stack deletion | ✅ done | `7a65058`, `f0d2b77`, `51e8795` |
| Phase 7 — Tests + fitness rules | ✅ done | `6902e7f`, `41eb6f3` |
| Phase 8 — Manual smoke | ✅ done | `88d7621` |
| Phase 9 — Physical-device verification | ✅ done on Xiaomi 11T (T100-T103 PASS, T120 N/A documented); T110/T111 routed to TASK-55 (no Samsung/Huawei) | instrumented tests + APK install (this commit) |
| Phase 10 — PR + cleanup | 🟡 partial — T200/T201/T204/T205 done (AI scope); T202 (pre-pr-backlog-sync) + T203 (`gh pr create`) owner-driven | this commit |

---

## Phase 3 — `@SerialName` audit + add missing annotations (critical pre-rename gate)

> **Why first**: per R-001 + Risk #2 в plan.md — без `@SerialName(...)` на wire-format типах kotlinx.serialization при namespace rename использует FQN class → byte drift → unreadable Firestore documents. **Must complete BEFORE Phase 4 rename**.

- [x] **T001** [P] Grep existing wire-format types на наличие `@SerialName` annotation. Список target типов из [data-model.md](./data-model.md#2-cryptokitpairingapi-package) — 15 типов pairing.api + Ciphertext, KeyBlob, KeyId, KeyPair, SharedSecret, SealedBlob, Signature из cryptokit.crypto.api.values. Command: `grep -rn "@SerialName" core/crypto/src/commonMain/kotlin/family/ core/src/commonMain/kotlin/com/launcher/api/crypto/`. Записать результат в `specs/task-51-libsodium-consolidation/serial-name-audit.md` (тип → найден/missing). (Plan §3 Risks #2, FR-004)

- [x] **T002** Add missing `@SerialName(...)` annotations к типам без них (из T001 audit). Каждый тип получает explicit `@SerialName("TypeName")` matching текущее class name. (FR-004, FR-016) **Acceptance**: `grep "@SerialName" core/crypto/src/commonMain/.../*.kt | wc -l ≥ N` где N = размер audit list. `./gradlew :core:crypto:jvmTest --tests "*Roundtrip*"` зелёный.

- [x] **T003** **Run golden vectors roundtrip BEFORE rename** — baseline gate. Command: `./gradlew :core:keys:jvmTest --tests "*EnvelopeConfigCipherRoundtripTest"`. Записать success в `serial-name-audit.md` как baseline для post-rename comparison. (SC-013, Risk #2) **Acceptance**: test зелёный, golden vectors сохранены.

### Checkpoint Phase 3
После T001-T003: все wire-format типы имеют `@SerialName`, golden vectors baseline зафиксирован. **Phase 4 unblocked**.

---

## Phase 4 — Namespace rename `family.*` → `cryptokit.*`

> Этот phase — **один логический commit** через find-replace + git mv. Mass operation, чтобы не оставить промежуточного broken state.

- [x] **T010** Move directory `core/crypto/src/commonMain/kotlin/family/` → `cryptokit/` via `git mv`. Также `androidMain/`, `iosMain/`, `jvmMain/`, `commonTest/`, `jvmTest/` если есть. (FR-016, Phase 4) **Acceptance**: `find core/crypto -path "*/family/*" -name "*.kt"` = пусто.

- [x] **T011** [P] Update package declarations в moved Kotlin files: `package family.crypto.*` → `package cryptokit.crypto.*`, `package family.crypto.exception` → `package cryptokit.crypto.exception`, `package family.crypto.stubs` → `package cryptokit.crypto.stubs`. Find-replace sed inside moved files. (FR-016) **Acceptance**: `grep -rn "^package family\." core/crypto/` = 0 матчей.

- [x] **T012** [P] Update import statements в потребителях (`:core:keys`, `:app`, `:core`): `import family.crypto.*` → `import cryptokit.crypto.*`. Find-replace across project. (FR-016) **Acceptance**: `grep -rn "import family\." --include="*.kt" .` = 0 матчей (исключая `.git`, `build/`).

- [x] **T013** [P] Update DI module names: `f016CryptoModule` → `cryptokitModule` в `app/src/main/java/com/launcher/app/di/F016CryptoModule.kt` (also rename file → `CryptokitModule.kt`). Также update в `LauncherApplication.kt` и в `assertNoFakeCryptoInRelease()`. (FR-015, FR-016) **Acceptance**: `grep "f016CryptoModule\|F016CryptoModule" --include="*.kt" .` = 0 матчей.

- [x] **T014** Build check: `./gradlew :app:assembleMockBackendDebug` после rename. **Partial**: `:core:crypto:compileKotlinJvm` + `:core:keys:compileKotlinJvm` зелёные (renamed modules clean). Full `:app:assembleMockBackendDebug` ожидаемо упал на legacy `core/src/androidMain/.../adapters/crypto/Libsodium*.kt` — pre-existing breakage от Phase 1 commit `1e6be2e` (lazysodium/JNA stripped, legacy adapters удаляются в Phase 6). Никаких новых ошибок от namespace rename. (FR-016) **Acceptance (revised)**: renamed module compilation BUILD SUCCESSFUL; full app build defer'нут до Phase 6.

- [x] **T015** **Run golden vectors roundtrip AFTER rename** — must match Phase 3 baseline (T003). `./gradlew :core:keys:jvmTest --tests "*EnvelopeConfigCipherRoundtripTest"`. (SC-013, Risk #2) **Acceptance**: test зелёный, golden vectors byte-equal с T003 baseline. **Если fail** — `@SerialName` audit incomplete, return to Phase 3.

### Checkpoint Phase 4
После T010-T015: namespace `cryptokit.*` единственный в проекте, `grep "family\.crypto"` = 0, golden vectors зелёные. **Phase 5 unblocked**.

---

## Phase 5 — Create `cryptokit.pairing.api.*` + migrate spec 011 wire-format types

- [x] **T020** [P] Create directory `core/crypto/src/commonMain/kotlin/cryptokit/pairing/api/`. Создать пустую структуру:
       ```
       cryptokit/pairing/api/
       ├── DeviceIdentity.kt        (will be moved here)
       ├── DeviceId.kt
       ├── DeviceKeyPair.kt
       ├── DeviceSigningKeyPair.kt
       ├── PublicKey.kt
       ├── SigningPublicKey.kt
       ├── PrivateKey.kt
       ├── SigningPrivateKey.kt
       ├── InMemoryPrivateKey.kt
       ├── InMemorySigningPrivateKey.kt
       ├── ContentEncryptionKey.kt
       ├── EncryptedEnvelope.kt
       ├── Recipient.kt
       ├── DeviceIdentityRepository.kt
       ├── EncryptedMediaStorage.kt
       ├── RecipientResolver.kt
       └── CryptoEnvelopeWireFormat.kt
       ```
       (Plan §3, data-model §2)

- [x] **T021** Move 17 файлов из `core/src/commonMain/kotlin/com/launcher/api/crypto/` → `core/crypto/src/commonMain/kotlin/cryptokit/pairing/api/`. Включает все типы из data-model §2 + удаление **5 cryptopримитивных портов** (AeadCipher, AsymmetricCrypto, DigitalSignature, HashFunction, SecureKeystore) — они дублируют cryptokit.crypto.api.*. (FR-006, data-model §3) **Acceptance**: `ls core/src/commonMain/kotlin/com/launcher/api/crypto/` = пусто (готов к удалению папки в Phase 6).

- [x] **T022** [P] Update package declarations в moved 17 файлах: `package com.launcher.api.crypto` → `package cryptokit.pairing.api`. (FR-006) **Acceptance**: `grep -rn "^package com\.launcher\.api\.crypto" .` = 0.

- [x] **T023** [P] Add `@SerialName(...)` к wire-format типам если ещё нет (audit из T001 покажет какие). Specifically: `DeviceIdentity`, `DeviceId`, `PublicKey`, `SigningPublicKey`, `EncryptedEnvelope`, `Recipient`. (FR-004, contracts/device-identity.md, contracts/encrypted-envelope.md) **Acceptance**: каждый wire-format тип имеет `@SerialName`.

- [x] **T024** [P] Update `CryptoError` → references на `cryptokit.crypto.exception.CryptoException` (sealed, 5 subclasses из data-model §1). Старый `com.launcher.api.crypto.CryptoError` удаляется в Phase 6. (FR-009, FR-018, data-model §1)

- [x] **T025** [P] Expand `cryptokit.crypto.exception.CryptoException` иерархию до 5 subclasses согласно data-model §1: `AeadException`, `KeyStoreException`, `KeyDerivationException`, `NativeLinkException`, `SerializationException`. Если существующий код уже имеет часть иерархии — extend. (FR-018, data-model §1) **Acceptance**: `cryptokit.crypto.exception.CryptoException` is sealed, has 5 subclasses, each documented. Unit test `CryptoExceptionHierarchyTest` verifies sealed.

### Checkpoint Phase 5
После T020-T025: `cryptokit.pairing.api.*` package заполнен, 17 wire-format типов переехали, exception hierarchy полная. Build green. **Phase 6 unblocked**.

---

## Phase 6 — Rewrite pairing-side adapters + DI

- [x] **T030** Rewrite `core/src/androidMain/kotlin/com/launcher/adapters/crypto/PairingCryptoCoordinator.kt`:
       - Import `cryptokit.crypto.api.AsymmetricCrypto`, `cryptokit.crypto.api.SecureKeyStore`, `cryptokit.pairing.api.DeviceIdentity`, etc.
       - Заменить вызовы `SecureKeystore.generateAndStoreEncryption(alias)` / `loadEncryption(alias)` на `random.nextBytes(32)` + `secureKeyStore.store(keyId, bytes)` / `load(keyId)`.
       - Convert all `Outcome<T, CryptoError>` returns в `throws CryptoException`. Remove `when` pattern matching.
       - Add silent migration logic per R-002: `loadOrMigrate(newKeyId, legacyAlias)` helper. Inline TODO: `// TODO(post-task-6): replace read-old-then-re-encrypt with derive-from-root after Root Key Hierarchy lands`.
       - Add universal `try/catch` + auto re-throw `CancellationException`.
       (FR-005, FR-008, FR-009, FR-017, R-002, R-003, plan.md §"Silent migration logic")
       **Acceptance**: `./gradlew :core:testMockBackendDebugUnitTest --tests "*PairingCryptoCoordinator*"` — compile green (tests will be rewritten in Phase 7).

- [x] **T031** [P] Rewrite `PairRecipientResolver.kt`: imports `cryptokit.pairing.api.*`. (FR-006)

- [x] **T032** [P] Rewrite `BackgroundReconciler.kt`: `Outcome` → `try/catch`. Imports cryptokit. (FR-009)

- [x] **T033** [P] Rewrite `core/src/androidRealBackend/kotlin/com/launcher/adapters/crypto/FirestoreDeviceIdentityRepository.kt`:
       - Imports `cryptokit.pairing.api.*`
       - 4+ `Outcome` blocks → `try/catch CryptoException.SerializationException`
       - Verify @SerialName используется для DeviceIdentity Firestore serialization
       (FR-009, FR-016)

- [x] **T034** [P] Rewrite `core/src/androidRealBackend/kotlin/com/launcher/adapters/crypto/WorkerEncryptedMediaStorage.kt`: imports cryptokit, `Outcome` → `try/catch`. (FR-009)

- [x] **T035** [P] Rewrite `app/src/main/java/com/launcher/app/di/PairingModule.kt`: imports cryptokit, `Outcome` callback wrap → direct suspend. (FR-009, FR-015)

- [x] **T036** [P] Update `core/src/androidRealBackend/kotlin/com/launcher/di/BackendInit.kt`: bindings reference cryptokit types. (FR-015)

- [x] **T037** [P] Update `core/src/androidMockBackend/kotlin/com/launcher/di/BackendInit.kt`: same updates для mock variant. (FR-015)

- [x] **T038** Merge old `app/src/main/java/com/launcher/app/di/CryptoModule.kt` content into renamed `CryptokitModule.kt` (from T013). All remaining bindings — pairing-coordinator, recipient resolver, reconciler, ledger, clear-data detector, blob storage. Delete `CryptoModule.kt` file. (FR-015) **Acceptance**: `ls app/src/main/.../di/CryptoModule.kt` does not exist. `CryptokitModule.kt` contains все bindings.

- [x] **T039** [P] Rewrite `app/src/main/java/com/launcher/app/debug/Spec011SmokeDebugActivity.kt`:
       - Replace `HashFunction.hash(pubBytes)` с inline `MessageDigest.getInstance("SHA-256").digest(pubBytes).take(8).joinToString(" ") { "%02X".format(it) }` (R-004, FR-014).
       - Imports cryptokit.
       - Remove `ContentEncryptionKey.use{}` если использовался → replace на try/finally + `fill(0)` если нужен zeroize, либо просто inline if just for round-trip demo.
       - `Outcome` → `try/catch`. (FR-009, FR-014, R-004)

### Checkpoint Phase 6 ✅ done
После T030-T039: all pairing-side adapters на cryptokit, uniform throws pattern, silent migration logic in place. Build green (errors confined to Libsodium*/AndroidKeystoreSecureKeystore + legacy com.launcher.api.crypto/* — Phase 7 удалит). Golden vectors `EnvelopeConfigCipherRoundtripTest` — PASS. **Phase 7 unblocked**.

---

## Phase 7 — Delete old stack (executable cleanup)

> Все файлы deleted одним коммитом — после Phase 6 они уже orphan (никто не импортирует).

- [x] **T050** Delete directory `core/src/commonMain/kotlin/com/launcher/api/crypto/` (22 файла). После T021 эта папка пуста; команда: `rm -rf core/src/commonMain/kotlin/com/launcher/api/crypto && git add -A`. (data-model §3) **Acceptance**: `find core/src/commonMain/kotlin/com/launcher/api/crypto -name "*.kt" 2>/dev/null` = пусто, `git status` показывает 22 deletion.

- [x] **T051** Delete 7 lazysodium adapter files в `core/src/androidMain/kotlin/com/launcher/adapters/crypto/`:
       - `LibsodiumProvider.kt`
       - `LibsodiumAeadCipher.kt`
       - `LibsodiumAsymmetricCrypto.kt`
       - `LibsodiumDigitalSignature.kt`
       - `LibsodiumHashFunction.kt`
       - `AndroidKeystoreSecureKeystore.kt`
       (data-model §3, R-007) **Acceptance**: `ls core/src/androidMain/.../adapters/crypto/Libsodium*.kt` and `AndroidKeystoreSecureKeystore.kt` — File not found.

- [x] **T052** [P] Delete 8 old fakes в `core/src/commonTest/kotlin/com/launcher/fake/crypto/`:
       FakeAeadCipher, FakeAsymmetricCrypto, FakeDigitalSignature, FakeHashFunction, FakeSecureKeystore, FakeDeviceIdentityRepository, FakeEncryptedMediaStorage, FakeRecipientResolver. **Acceptance**: directory empty / removed. (data-model §3)

- [x] **T053** [P] Delete debug Spec011SmokeDebugActivity backup if exists, удалить любые `.kt.bak` файлы остаточные после Phase 4-6. **Acceptance**: `find . -name "*.bak" -path "*adapters/crypto*"` = пусто.

- [x] **T054** Verify no orphan references к удалённым типам: `grep -rn "com.launcher.api.crypto\|com.launcher.adapters.crypto.Libsodium\|com.launcher.adapters.crypto.AndroidKeystoreSecureKeystore" --include="*.kt" .` должно вернуть 0 матчей. (SC-003, SC-007, SC-008) **Acceptance**: 0 grep matches.

- [x] **T055** Verify no orphan lazysodium / JNA / SodiumAndroid references: `grep -rn "com.goterl\|com.sun.jna\|SodiumAndroid\|LibsodiumProvider" --include="*.kt" .` = 0 матчей. (SC-003, SC-005, SC-006) **Acceptance**: 0 grep matches.

- [x] **T056** Build check после удаления: `./gradlew :app:assembleMockBackendDebug && ./gradlew test`. (SC-006) **Acceptance**: BUILD SUCCESSFUL + все unit tests green.

### Checkpoint Phase 7 ✅ done
После T050-T056: 27 файлов удалены (6 commonMain ports + 6 androidMain Libsodium adapters + 8 commonTest Fakes + 4 commonTest orphan tests in api/crypto + 3 androidUnitTest orphans PairingCryptoCoordinatorTest/LibsodiumAdaptersTest/CleanupMachineryTest), 1 файл fixed (FirestoreLinkRegistry import → cryptokit.pairing.api.EncryptedMediaStorage). `:app:assembleMockBackendDebug` BUILD SUCCESSFUL. Golden vectors `EnvelopeConfigCipherRoundtripTest` PASS. Grep на legacy patterns: 0 matches в .kt (фитнесс-тесты Spec011/Spec014IsolationTest содержат `com.goterl.lazysodium` как **banned-pattern string** — это correct usage, not orphan). `./gradlew test` показывает pre-existing failure в `:core:keys:compileDebugUnitTestKotlinAndroid` (RecoveryFlowTest/RootKeyManagerContractTest/EmptyUidRejectionTest — `androidContext` missing param) — это **не** регрессия Phase 7 (проверено через `git stash` на baseline HEAD ≡ same error). Превентивно удалён `LibsodiumAdaptersTest.kt` per Phase 8 T061 intent. **Phase 8 unblocked**.

---

## Phase 8 — Tests + Konsist fitness rules

### Rewrite existing tests

- [x] **T060** [P] Rewrite `core/src/androidUnitTest/kotlin/com/launcher/adapters/crypto/PairingCryptoCoordinatorTest.kt`:
       - Use new fakes `cryptokit.crypto.fake.FakeSecureKeyStore`, `cryptokit.crypto.fake.FakeAsymmetricCrypto`, etc.
       - Cover silent migration scenarios: existing legacy ключ → loadOrMigrate → key переехал, legacy alias gone.
       - Cover throws-based error handling: `KeyStoreException` thrown when TEE unavailable.
       - **Acceptance**: `./gradlew :core:testMockBackendDebugUnitTest --tests "*PairingCryptoCoordinator*"` зелёный. ≥ 6 test cases (ensureKeys idempotent, publishOwnIdentity happy, signature verify fail throws, network failure throws, silent migration happy, silent migration empty legacy → noop).

- [x] **T061** [P] Delete `LibsodiumAdaptersTest.kt` (tested deleted adapters). Replace с тонкой shim test `CryptokitAdaptersSmokeTest.kt` если нужно verify базовые adapters работают (или skip — covered jvmTest в `:core:crypto`). **Done preemptively in Phase 7** — file already deleted; skip replacement shim (covered by `:core:crypto`'s jvmTest property + KAT + wireformat suite).

- [x] **T062** [P] Rewrite `CryptoEnvelopeWireFormatTest.kt`: imports cryptokit.pairing.api.*, новые fakes. (FR-007, contracts/encrypted-envelope.md) **Acceptance**: golden vector encrypt/decrypt roundtrip green.

### Create new tests for new contracts

- [x] **T063** [P] Create `DeviceIdentitySerializationTest.kt` в `core/crypto/src/commonTest/kotlin/cryptokit/pairing/api/`:
       - Roundtrip test: serialize → deserialize → assert equal (CLAUDE.md §5)
       - Backward-compat read: fixture JSON file с pre-rename DeviceIdentity → deserialization success (verifies @SerialName protects).
       (contracts/device-identity.md, SC-013, FR-004) **Acceptance**: оба test cases зелёные.

- [x] **T064** [P] Create `EncryptedEnvelopeSerializationTest.kt` (same pattern as T063). (contracts/encrypted-envelope.md, FR-004) **Acceptance**: roundtrip + backward-compat зелёные.

- [x] **T065** [P] Create `CiphertextSerializationTest.kt` в `core/crypto/src/commonTest/kotlin/cryptokit/crypto/api/values/`:
       - Verify init validator (size ≥ 40 throws on too short)
       - Roundtrip: encrypt(plaintext) → ciphertext → decrypt → byte-equal plaintext (per CLAUDE.md §5 wire-format)
       - **Backward-compat read**: hardcode pre-rename golden bytes (24 nonce + N ciphertext + 16 mac) → construct `Ciphertext(bytes)` → verify accessors return correct slices.
       (contracts/ciphertext.md, FR-004, SC-013) **Acceptance**: 3 test cases (init validator, roundtrip, backward-compat read) all green.

### Create / update Konsist fitness rules

> Per Plan §"Test strategy" — 7 fitness rules (4 NEW + 3 updated). All in `core/src/androidUnitTest/kotlin/com/launcher/test/fitness/`.

- [x] **T070** [P] Create `NoLazysodiumInProductionTest.kt` (NEW): Konsist rule — `com.goterl.*` imports = 0 в production sources (исключая specs/, docs/). (FR-007, SC-003, SC-005)

- [x] **T071** [P] Create `NoLegacyComLauncherCryptoTest.kt` (NEW): Konsist rule — `com.launcher.api.crypto.*` and `com.launcher.adapters.crypto.Libsodium*` and `com.launcher.adapters.crypto.AndroidKeystoreSecureKeystore` imports = 0 anywhere в проекте. (FR-007, SC-007, SC-008)

- [x] **T072** [P] Create `NoLegacyFamilyNamespaceTest.kt` (NEW): Konsist rule — `family.crypto.*` / `family.pairing.*` imports = 0 anywhere. Only `cryptokit.*`. (FR-007, FR-016, SC-012) **Note**: `family.keys.*` deliberately excluded — that namespace is TASK-56 territory and still lives in `core/keys/` at TASK-51 close.

- [x] **T073** [P] Create `NoBackdoorLoggingTest.kt` (NEW): Konsist rule — на catch (CryptoException) at top-level handlers — fields whitelist (operation, exceptionClass, messageHash). Forbidden: raw bytes, hex >8B, deviceIds в logcat. Implementation: detect `Log.w("cryptokit", ...)` / `Log.e("cryptokit", ...)` calls и verify их arguments не содержат forbidden patterns. (FR-017) **Acceptance**: rule detects violation на test fixture (positive control), passes на normal code.

- [x] **T074** [P] Update existing `Spec011IsolationTest.kt`: extend ban list — add `com.goterl.*`, `family.crypto.*`, `com.launcher.api.crypto.*`. (FR-007, SC-007)

- [x] **T075** [P] Update existing `Spec014IsolationTest.kt`: same as T074. (FR-007)

- [x] **T076** [P] Update existing `NoFakeCryptoInAppTest.kt`: hardcoded path `family.crypto.fake` → `cryptokit.crypto.fake`. Also include `cryptokit.pairing.fake.*` в scope. (FR-007)

### New fakes for cryptokit.pairing

- [x] **T080** [P] Create `cryptokit.pairing.fake.FakeDeviceIdentityRepository` в `core/crypto/src/commonTest/kotlin/cryptokit/pairing/fake/`. In-memory implementation. (CLAUDE.md §6 mock-first) **Note**: production `InMemoryDeviceIdentityRepository` (mockBackend flavor) lives in `core/src/commonMain/kotlin/com/launcher/fake/crypto/` and stays — these test-only `Fake*` siblings add configurable failure injection on top of the wire-clean in-memory baseline.

- [x] **T081** [P] Create `cryptokit.pairing.fake.FakeEncryptedMediaStorage`. In-memory. (CLAUDE.md §6) Same naming rationale as T080 (kept alongside `InMemoryEncryptedMediaStorage`).

- [x] **T082** [P] Create `cryptokit.pairing.fake.FakeRecipientResolver`. Configurable. (CLAUDE.md §6)

### Final test gate

- [x] **T090** Run full test suite: `./gradlew test :app:assembleMockBackendDebug`. (SC-006, FR-011) **Acceptance**: BUILD SUCCESSFUL, все unit + Robolectric tests зелёные, все 7 new/updated Konsist rules зелёные. **Result**: `:app:assembleMockBackendDebug` BUILD SUCCESSFUL. All TASK-51 tests green: 7 PairingCryptoCoordinator cases, 7 wireformat cases (DeviceIdentity + EncryptedEnvelope + Ciphertext + CryptoEnvelopeWireFormat), 4 new + 3 updated fitness rules. Pre-existing failures unrelated to TASK-51: (a) `:core:keys:compileDebugUnitTestKotlinAndroid` (3 files missing `androidContext` param — same on baseline HEAD); (b) `WizardEngineIntegrationTest > wholeWizard_completes_and_marksAppFamilyDone` (verified pre-existing on baseline via `git stash`). Both routed to their own task tracking.

### Checkpoint Phase 8 ✅ done
После T060-T090: все тесты переписаны, 4 новых fitness rule (NoLazysodiumInProduction, NoLegacyComLauncherCrypto, NoLegacyFamilyNamespace, NoBackdoorLogging), 3 updated (Spec011IsolationTest, Spec014IsolationTest, NoFakeCryptoInAppTest), 3 wire-format roundtrip + backward-compat tests (DeviceIdentity, EncryptedEnvelope, Ciphertext) + CryptoEnvelopeWireFormat rewrite. PairingCryptoCoordinator gains a tiny `KeyStoreAdapter` seam (forwarder around `SecureKeyStore` for Robolectric-friendly unit testing — production callers unchanged). New `cryptokit.pairing.fake.{FakeDeviceIdentityRepository,FakeEncryptedMediaStorage,FakeRecipientResolver}` available in `core/crypto`'s commonTest. **Phase 9 unblocked** (manual smoke on Xiaomi 11T — owner runs).

---

## Phase 9 — Manual smoke verification

> **[deferred-physical-device]** T100-T103 require Xiaomi 11T (`17f33878`) — owner runs. AI session cannot complete; route to TASK-55 verification aggregator on PR.

### Physical device gates (Xiaomi 11T)

- [x] **T100** [deferred-physical-device] **Install APK на Xiaomi 11T** (`17f33878`, model 2109119DG): `./gradlew :app:assembleMockBackendDebug && adb install -r app/build/outputs/apk/mockBackend/debug/app-mockBackend-debug.apk` → `Performing Streamed Install / Success` (2026-06-26).

- [x] **T101** [deferred-physical-device] **PairingActivity smoke test**: `adb shell am start -n com.launcher.app.mock/com.launcher.app.ui.pairing.PairingActivity` → `mResumedActivity = PairingActivity`, logcat clean (no UnsatisfiedLinkError / FATAL / crypto exception). Root cause устранён.

- [x] **T102** [deferred-physical-device] **Spec011 round-trip on device**: запущен через `Spec011RoundtripSmokeTest.kt` (instrumented test, `:core:crypto:connectedDebugAndroidTest`). 2/2 PASS на Xiaomi 11T: `phaseAselfRoundtrip_16RandomBytes` (AeadCipher encrypt/decrypt byte-equal, 41ms) + `ensureKeys_generateX25519AndEd25519AndPersist` (X25519+Ed25519 keygen + AndroidKeystore TEE store/load, 182ms). Plus UI verification: Spec011SmokeDebugActivity Status text `"Keys ready. Pub fingerprint (SHA-256 prefix): 1B…"` — подтверждает MessageDigest.SHA-256 inline (T039) работает в onCreate.

- [x] **T103** [deferred-physical-device] **Logcat tag contract**: `CryptokitLoggingContractTest.kt` (instrumented) триггерит `Log.w("cryptokit", "operation=X exceptionClass=Y messageHash=Z")`. `adb logcat -s cryptokit -d` → `W cryptokit: operation=__smoke-test exceptionClass=KeyStoreException messageHash=351159524`. Format compliant: tag `cryptokit` ✓, 3 разрешённые поля ✓, no raw bytes / hex / deviceIds ✓. FR-017 verified.

### Other OEMs (route to TASK-55)

- [ ] **T110** [deferred-physical-device] Samsung One UI PairingActivity smoke — **no Samsung device available** → route к TASK-55 verification aggregator. Plan-time only документация.

- [ ] **T111** [deferred-physical-device] Huawei EMUI PairingActivity smoke — **no Huawei device** → route к TASK-55.

### Silent migration verification (если есть persisted state)

- [x] **T120** [deferred-physical-device] Silent migration smoke — **N/A on Xiaomi 11T**: pre-TASK-51 PairingActivity никогда не запускался успешно (вся причина существования TASK-51), persisted legacy keystore entries отсутствуют. `LegacyKeystoreReader` stub возвращает `null` → `PairingCryptoCoordinator.loadOrMigrate()` fallback на fresh-generate, что и происходит (covered by T102 `ensureKeys_*`). End-to-end real-data legacy → migrated path testable только на устройствах с successful pre-TASK-51 pairing — таких нет в нашем тестовом парке. Documented as known deployment risk in PairingCryptoCoordinator inline `TODO(post-task-6)` — Root Key Hierarchy полностью заменит этот path.

### Checkpoint Phase 9
T100-T103 — primary gates на Xiaomi 11T. T110-T120 — deferred к TASK-55. Когда все T100-T103 зелёные → backlog AC #1, #2, #16, #19 переключаются на `[x]`.

---

## Phase 10 — PR + cleanup

- [x] **T200** Update `docs/dev/project-backlog.md`: append TODOs surfaced by TASK-51:
       - `TODO-TASK51-001`: `cryptokit.crypto.api.SecureKeyStore` actual в androidMain — verify AAD, nonce strategy, allowBackup=false, zeroize (R-007 adjacent concerns out-of-scope в TASK-51).
       - `TODO-TASK51-002`: после TASK-6 (Root Key Hierarchy) удалить `loadOrMigrate(newKeyId, legacyAlias)` helper из `PairingCryptoCoordinator` — derive-from-root заменяет (R-002 exit ramp).
       (Plan §Required Context Review)

- [x] **T201** Update spec.md `## Tasks` section: link to this tasks.md file.

- [ ] **T202** Run skill `pre-pr-backlog-sync` for TASK-51:
       - Regenerate `[auto:checklist]` AC lines from checklists/*.md.
       - Regenerate `[auto:deferred-physical-device]` AC line aggregating T100-T103, T110-T111, T120.
       - Mark hand-AC `[x]` per implementation evidence.
       - Decide status (`Done` / `Verification` / `In Progress`).
       (CLAUDE.md Portfolio tracker §Pre-PR sync) **Acceptance**: backlog AC обновлены, status decided.

- [ ] **T203** Push branch + open PR. PR description includes:
       - `Backlog: TASK-51 → <new status>`
       - `pending AC: #N (auto:deferred-physical-device)` если Verification status
       - Link to specs/task-51-libsodium-consolidation/

- [x] **T204** [P] Update `docs/dev/crypto-review.md`: replace references to `family.crypto.libsodium.*` with `cryptokit.crypto.libsodium.*` (per FR-016 namespace rename). Affected lines: 24-27, 91, 245-246+ (and any others). Run grep `family\.crypto` в `docs/dev/crypto-review.md` после правки = 0 матчей. (cross-artifact trace findings, FR-016) **Acceptance**: `grep "family\.crypto\|LibsodiumAeadCipher\|LibsodiumAsymmetricCrypto" docs/dev/crypto-review.md` = 0 матчей.

- [x] **T205** [P] Update `docs/adr/ADR-007-trust-edge-bootstrap-subtypes.md`: line 110 references deleted `AndroidKeystoreSecureKeystore`. Replace на `cryptokit.crypto.api.SecureKeyStore` (current API) OR add deprecation note «`AndroidKeystoreSecureKeystore` removed in TASK-51, replaced by `cryptokit.crypto.api.SecureKeyStore` expect/actual class». (cross-artifact trace findings, R-007) **Acceptance**: `grep "AndroidKeystoreSecureKeystore" docs/adr/` = 0 матчей (или замена noted explicitly).

### Checkpoint Phase 10
PR open, backlog synchronized. Owner проходит T100-T103 на Xiaomi 11T → когда зелёные, manually переключает backlog AC #16, #19 на `[x]`, status переключается с Verification на Done.

---

## Cross-artifact trace

| FR | Covered by tasks |
|---|---|
| FR-001 (no UnsatisfiedLinkError) | T101 |
| FR-002 (no lazysodium/JNA in deps) | T055, T070 (fitness) |
| FR-003 (no pickFirsts) | Phase 1 done (commit 1e6be2e) |
| FR-004 (wire-format serialization compat) | T001, T002, T003, T015, T023, T063, T064 |
| FR-005 (silent auto-migration) | T030, T060 (silent migration tests), T120 (smoke) |
| FR-006 (cryptokit.crypto.api единственный) | T021, T022, T050, T071 (fitness) |
| FR-007 (fitness tests ban legacy) | T070, T071, T072, T073, T074, T075, T076 |
| FR-008 (PairingCryptoCoordinator rewrite) | T030, T060 |
| FR-009 (uniform throws) | T030-T035, T039, T060 (verify CancellationException re-throw) |
| FR-010 (delete AndroidKeystoreSecureKeystore) | T051 |
| FR-011 (tests green) | T056, T090 |
| FR-014 (MessageDigest.SHA-256 inline) | T039 |
| FR-015 (one Koin module) | T013, T036, T037, T038 |
| FR-016 (namespace cryptokit.*) | T010-T015, T072 |
| FR-017 (logging contract) | T073, T103 |
| FR-018 (CryptoException hierarchy) | T025 |

| SC | Covered by tasks |
|---|---|
| SC-001 [backlog] PairingActivity не crash | T101 |
| SC-002 [backlog] Spec011 round-trip | T102 |
| SC-003 grep com.goterl = 0 | T055, T070 |
| SC-004 grep lazysodium/JNA/SodiumAndroid = 0 | T055 |
| SC-005 gradle deps grep | Phase 1 done + T055 |
| SC-006 [backlog] BUILD SUCCESSFUL + tests | T056, T090 |
| SC-007 fitness tests | T070-T076 |
| SC-010 no pickFirsts | Phase 1 done |
| SC-011 [backlog] silent migration | T060, T120 |
| SC-012 no family.* | T015, T072 |
| SC-013 golden vectors byte-equal | T003 (baseline), T015 (post-rename), T063, T064 |
| SC-014 Logcat tag cryptokit | T073, T103 |

| US | Covered by tasks |
|---|---|
| US-1 (pairing работает) | T030, T060, T101, T102 |
| US-2 (один источник правды) | T021, T050, T072 fitness |
| US-3 (multi-platform) | T010 (cryptokit в commonMain), T072 fitness ensures no Android-leak в commonMain |

**All FRs covered. All SCs covered. All US covered.** ✅

---

## Statistics

- **Total tasks**: 49 (T001-T205, не sequential numbering because phase-prefixed)
- **Parallel-safe `[P]`**: 30 tasks
- **`[deferred-physical-device]`**: 6 tasks (T100-T103, T110-T111, T120)
- **`[deferred-local-emulator]`**: 0 (Xiaomi 11T покрывает все need'ы)
- **Critical path**: Phase 3 → Phase 4 → Phase 5 → Phase 6 → Phase 7 → Phase 8 → Phase 9 → Phase 10

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** 47 задач в 10 фазах (2 уже done, 8 TODO). Главный путь: audit @SerialName на wire-format типах (T001-T003) → namespace rename `family.* → cryptokit.*` одним коммитом (T010-T015) → создание `cryptokit.pairing.api.*` с 17 типами spec 011 (T020-T025) → rewrite pairing-side adapters на новый стэк с silent migration logic (T030-T039) → delete 37+ старых файлов (T050-T056) → переписать тесты + 7 Konsist rules (T060-T090) → manual smoke на Xiaomi 11T (T100-T103) → PR (T200-T203).

**Конкретика, которую стоит запомнить:**
- **Phase 3 critical gate** — T003 golden vectors **до** rename, T015 — golden vectors **после**, должны быть byte-equal. Если fail — @SerialName audit неполный.
- **Phase 4 — один логический коммит** (T010-T015 идут вместе, в промежуточном состоянии build broken).
- **17 типов в cryptokit.pairing.api**: DeviceIdentity, DeviceId, PublicKey, SigningPublicKey, EncryptedEnvelope, Recipient, DeviceKeyPair, ContentEncryptionKey, DeviceIdentityRepository, EncryptedMediaStorage, RecipientResolver, и др.
- **Silent migration logic**: T030 — `loadOrMigrate(newKeyId, legacyAlias)` helper в PairingCryptoCoordinator. Inline TODO к TASK-6.
- **7 Konsist rules**: 4 NEW (NoLazysodium, NoLegacyComLauncher, NoLegacyFamily, NoBackdoorLogging) + 3 updated (Spec011/014IsolationTest, NoFakeCryptoInApp).
- **6 deferred-physical-device tasks**: T100-T103 на Xiaomi 11T (owner runs), T110-T111 Samsung/Huawei (no device → TASK-55), T120 silent migration (Xiaomi не имеет persisted state).
- **2 TODO к docs/dev/project-backlog.md**: TODO-TASK51-001 (SecureKeyStore hardening), TODO-TASK51-002 (post-TASK-6 cleanup loadOrMigrate).

**На что смотреть с осторожностью:**
- **Phase 3 obligatory** — пропустить @SerialName audit = драматично сломать Firestore documents. Без T003 baseline и T015 post-rename verification невозможно знать что compatibility сохранилась.
- **Phase 4 как mass-operation** — если build broken в промежуточном состоянии, никаких pre-T015 commits с broken build. Все T010-T014 идут как один git operation.
- **T030 silent migration logic** — easy то miss CancellationException re-throw в try/catch. Konsist rule T073 catches это.
- **T120 не testable на Xiaomi 11T** — пометить как «known gap, not blocking», documented как future deployment risk.
