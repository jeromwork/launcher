# Tasks: KeyVault Port Boundary — Cross-Platform Cryptographic Contract

**Feature**: TASK-112 KeyVault Port Boundary
**Branch**: `task-112-keyvault-port`
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

**Trace**: each `Tnnn` traces to `FR-nnn` or `SC-nnn` from spec + Decision block section from [TASK-112 file](../../backlog/tasks/task-112%20-%20Decision-Cross-platform-IdentityVault.md).

**Tick-sync HARD RULE** (CLAUDE.md): implementation commit closing a task MUST в том же diff'е проставить `[x]`. Никаких «догоню потом».

---

## Phase 1 — Port + Fakes + Contract Tests (2 days)

### T001 — Create `KeyVault` interface with port shape from Decision block
- [ ] Traces: FR-001, Decision block «Port shape (Kotlin common)» section
- [ ] File: `core/keys/src/commonMain/kotlin/family/keys/api/KeyVault.kt`
- [ ] Content: interface `KeyVault` с 8 методами (`unlock`, `aeadSeal`, `aeadOpen`, `mac`, `verifyMac`, `sign`, `verify`, `publicIdentity`). Все `@Throws(VaultException::class)` (кроме `verify` — pure).
- [ ] Verification: `./gradlew :core:keys:compileCommonMainKotlinMetadata` — compiles.

### T002 — Create `Purpose` enum with registry attributes
- [ ] Traces: FR-002, Decision block Purpose registry
- [ ] File: `core/keys/src/commonMain/kotlin/family/keys/api/Purpose.kt` (или внутри KeyVault.kt)
- [ ] Content: `enum class Purpose(val algorithm: Algorithm, val exportable: Boolean, val rotationPolicy: RotationPolicy)` с 2 variants: CONFIG (XChaCha20Poly1305, false, LazyOnDemand), RECOVERY_BLOB (XChaCha20Poly1305, false, Manual). Plus `Algorithm` + `RotationPolicy` enums.
- [ ] Verification: unit test — все variants имеют `exportable = false`.

### T003 — Create newtypes for port boundary (`Ciphertext`, `MacTag`, `Signature`, `PublicIdentity`, `Aad`)
- [ ] Traces: FR-003, FR-004, Decision block «Newtypes»
- [ ] Files:
  - `commonMain/api/Ciphertext.kt` — class с blob-header accessors (`formatVersion`, `purposeId`, `keyEpoch`)
  - `commonMain/api/MacTag.kt` — value class
  - `commonMain/api/Signature.kt` — value class (identity-scoped)
  - `commonMain/api/PublicIdentity.kt` — value class (Ed25519 pubkey)
  - `commonMain/api/Aad.kt` — value class + `canonicalAad(namespaceId: String, schemaVersion: Int, blobVersion: Int): Aad` helper
- [ ] Verification: unit test `AadCanonicalTest` — length-prefixed encoding matches spec FR-004.

### T004 — Create sealed `VaultException` hierarchy
- [ ] Traces: FR-012, Decision block «Sealed exception hierarchy»
- [ ] File: `commonMain/api/VaultException.kt`
- [ ] Content: sealed class с 7 наследниками (VaultLocked, WrongPurpose, TamperDetected, UnsupportedFormatVersion, NoRootKey, HardwareBackedKeystoreUnavailable, RecoveryFailed).
- [ ] Verification: `when (ex) { ... }` exhaustive check в тесте — все 7 покрыты без else.

### T005 — Create `RecoveryStrategy` port
- [ ] Traces: FR-005, Session 6 owner insight (pluggable recovery)
- [ ] File: `commonMain/api/RecoveryStrategy.kt`
- [ ] Content: interface `RecoveryStrategy { internal fun deriveRoot(): ByteArray }`. Note: `internal fun` — root не пересекает module boundary.
- [ ] Verification: compile — external module не может вызвать `deriveRoot()`.

### T006 — Implement `PassphraseRecovery` adapter matching TASK-6
- [ ] Traces: FR-006, TASK-6 regression preservation
- [ ] File: `commonMain/impl/PassphraseRecovery.kt` + `Argon2Params.kt`
- [ ] Content: class `PassphraseRecovery(passphrase, salt, params: Argon2Params)` → `deriveRoot() = libsodiumArgon2id(passphrase, salt, params.V1)`. `Argon2Params.V1 = memory=64MiB, iterations=3, parallelism=1`.
- [ ] Verification: unit test — same passphrase + salt + params V1 → same root bytes byte-equal. Regression test с TASK-6 fixture.

### T007 — Create `RootKey` internal class + `BlobHeader` internal helper
- [ ] Traces: FR-003, FR-008, Decision block «internal RootKey»
- [ ] Files:
  - `commonMain/impl/RootKey.kt` — `internal class RootKey(internal val bytes: ByteArray)`. NO public constructor.
  - `commonMain/impl/BlobHeader.kt` — internal object с `pack(formatVersion, purposeId, keyEpoch, nonce, payload, mac): ByteArray` + `parse(bytes): BlobHeader`.
- [ ] Verification: unit test `BlobHeaderTest` — pack + parse roundtrip, invalid magic → exception.

### T008 — Implement `FakeKeyVault` (deterministic in-memory)
- [ ] Traces: FR-014, Rule 6 (mock-first)
- [ ] File: `commonTest/kotlin/family/keys/FakeKeyVault.kt`
- [ ] Content: in-memory Map-based vault. Uses libsodium-kmp under hood для реальных крипто операций (не «фейк» с random bytes). Fixed root seed для test vector reproducibility.
- [ ] Verification: contract test — `fakeVault.aeadSeal(...)` + `aeadOpen` → roundtrip. Same seed → same output bytes across test runs.

### T009 — Write contract tests for `KeyVault` port
- [ ] Traces: SC-004, SC-007, SC-008
- [ ] File: `commonTest/kotlin/family/keys/KeyVaultContractTest.kt`
- [ ] Coverage:
  - roundtrip `aeadSeal → aeadOpen` для CONFIG и RECOVERY_BLOB
  - `WrongPurpose` — Ciphertext(CONFIG), open(RECOVERY_BLOB) → exception (SC-008)
  - `TamperDetected` — модифицированный byte в blob → exception (SC-007)
  - `TamperDetected` — модифицированный AAD → exception
  - `mac` + `verifyMac` roundtrip
  - `sign` + `verify` roundtrip (positive), verify(wrong signature) → false (negative)
  - `VaultLocked` — вызовы без `unlock()` → exception
- [ ] Verification: `./gradlew :core:keys:test` — все проходят.

### T010 — Write `PurposeEnforcementTest`
- [ ] Traces: SC-008, FR-003 (purpose_id в blob header)
- [ ] File: `commonTest/PurposeEnforcementTest.kt`
- [ ] Coverage: `aeadOpen` с mismatched purpose → `WrongPurpose(expected=asked, actual=headerPurpose)` exception. Проверка что header покрывает случай.

### T011 — Write `RecoveryStrategyTest`
- [ ] Traces: SC-011, FR-005
- [ ] File: `commonTest/RecoveryStrategyTest.kt`
- [ ] Coverage: `PassphraseRecovery` deterministic (same passphrase+salt → same root). `TestRecoveryStrategy(fakeRootBytes)` — mock adapter. `unlock` c wrong strategy → subsequent `aeadOpen` fails (TamperDetected).

### T012 — Create cross-platform test vectors fixture
- [ ] Traces: FR-011, SC-004
- [ ] File: `commonTest/resources/vectors/v1.json`
- [ ] Content: ≥5 vector cases: (a) seal zeros root + "hello" + basicAad, (b) seal с non-zero root, (c) mac deterministic output, (d) sign с fixed key + message, (e) edge case (empty plaintext).
- [ ] Verification: `CrossPlatformVectorTest` reads JSON, prognon'yaet vectors, byte-equal check.

### T013 — Add fitness rule: no vendor imports in `:core:keys`
- [ ] Traces: FR-007, SC-006, Rule 1
- [ ] File: `core/keys/build.gradle.kts` — configure detekt custom rule OR use lint-rules module ForbidVendorImports.
- [ ] Verification: `./gradlew :core:keys:detekt` fails if a violating import added (test via temporary test file).

---

## Phase 2 — Android Adapter (1.5 days)

### T014 — Implement `AndroidRootKeyStorage` (internal)
- [ ] Traces: FR-010, Decision block «Storage split»
- [ ] File: `androidMain/impl/AndroidRootKeyStorage.kt`
- [ ] Content: wraps `EncryptedSharedPreferences.create(...)` с `MasterKey.Builder(...).setKeyScheme(AES256_GCM).build()`. StrongBox flag optional (per device availability). Methods: `save(rootKey: RootKey)`, `load(): RootKey?`.
- [ ] Verification: `androidInstrumentedTest` — write + read roundtrip on emulator.

### T015 — Implement `AndroidKeyVault` adapter
- [ ] Traces: FR-001, FR-010, Decision block «Storage split»
- [ ] File: `androidMain/impl/AndroidKeyVault.kt`
- [ ] Content: implements `KeyVault` interface. Delegates to libsodium-kmp for all crypto ops (aeadSeal/aeadOpen/mac/sign). Uses `AndroidRootKeyStorage` for root_key at rest. `unlock(strategy)` derives root via strategy, stores encrypted, keeps decrypted in-memory during vault session.
- [ ] Verification: `AndroidKeyVaultIntegrationTest` — real Android Keystore + libsodium через JNI. Compares roundtrip с `FakeKeyVault` output — byte-equal.

### T016 — Write `AndroidKeyVaultIntegrationTest`
- [ ] Traces: SC-004, SC-011 (Android side)
- [ ] File: `androidInstrumentedTest/AndroidKeyVaultIntegrationTest.kt`
- [ ] Coverage: (a) unlock via PassphraseRecovery + aeadSeal + aeadOpen — success; (b) reload vault (simulate app restart) + unlock + aeadOpen — decrypts prior data; (c) VaultLocked exception if aeadSeal called before unlock; (d) HardwareBackedKeystoreUnavailable если Keystore недоступен (mock scenario).
- [ ] Verification: `./gradlew :core:keys:connectedAndroidTest` на pixel_5_api_34 — все зелёные.

### T017 — Write `CrossPlatformVectorAndroidTest`
- [ ] Traces: FR-011, SC-004
- [ ] File: `androidInstrumentedTest/CrossPlatformVectorAndroidTest.kt`
- [ ] Content: same test vectors JSON, executed on Android — proves byte-equal with commonTest run.
- [ ] Verification: `./gradlew :core:keys:connectedAndroidTest --tests *CrossPlatformVector*` — vectors match.

---

## Phase 3 — Migrate Call Sites (1.5 days)

### T018 — Grep repo for `DerivedKey.bytes` / `RootKey.bytes` / direct `keyRegistry.derive(...)` callers
- [ ] Traces: FR-009, SC-001
- [ ] Command: `Grep pattern="DerivedKey\.bytes|RootKey\.bytes|keyRegistry\.derive"`
- [ ] Deliverable: list of files needing migration. Expected: `ConfigCipher2.kt`, `EnvelopeStorage.kt`, возможно ещё 1-2 файла в `:core:cloud` или `:core:push`.

### T019 — Migrate `ConfigCipher2` to `KeyVault.aeadSeal / aeadOpen`
- [ ] Traces: FR-009, SC-001, SC-002
- [ ] File: `core/config/ConfigCipher2.kt`
- [ ] Change: replace `keyRegistry.derive("config").bytes; aead.seal(bytes, plaintext, aad)` → `keyVault.aeadSeal(Purpose.CONFIG, plaintext, canonicalAad(nsId, schemaVer, blobVer))`.
- [ ] Verification: existing `ConfigCipher2Test` passes. New backward-compat test reads pre-migration fixture blob и decrypts successfully.

### T020 — Migrate `EnvelopeStorage` to `KeyVault.aeadSeal / aeadOpen`
- [ ] Traces: FR-009, SC-001
- [ ] File: `core/cloud/EnvelopeStorage.kt` (или где живёт).
- [ ] Verification: `./gradlew :core:cloud:test` — envelope sync tests pass.

### T021 — Wire `KeyVault` через DI (manual constructor injection per CLAUDE.md)
- [ ] Traces: FR-001 (integration into app)
- [ ] Files: `app/src/main/java/com/launcher/app/di/CryptoModule.kt` или где живёт DI wiring.
- [ ] Content: single instance of `AndroidKeyVault` provided per Application scope. `RecoveryStrategy` instantiated per unlock event (не singleton — passphrase changes possible).
- [ ] Verification: `./gradlew :app:assembleMockBackendDebug` — build успешен. App запускается на эмуляторе (skill android-emulator smoke).

### T022 — Write `BackwardCompatTest` для TASK-6 legacy blobs
- [ ] Traces: SC-002
- [ ] Files:
  - Fixture: `commonTest/resources/fixtures/task-6-legacy-blob.bin` (extract из running TASK-6 device перед migration'ом OR synthesize via existing pre-migration crypto path).
  - Test: `commonTest/BackwardCompatTest.kt` — reads fixture bytes через новый code path, expects successful decryption to same plaintext.
- [ ] Verification: тест зелёный.

---

## Phase 4 — Downgrade RootKey + KeyRegistry (1 day)

### T023 — Downgrade `RootKey` public class to `internal`
- [ ] Traces: FR-008, SC-010
- [ ] File: `commonMain/impl/RootKey.kt`
- [ ] Change: `class RootKey(val bytes: ByteArray)` → `internal class RootKey(internal val bytes: ByteArray)`. Move из `api/` в `impl/` folder.
- [ ] Verification: `./gradlew build` — compile errors в external callers (fix in T024).

### T024 — Downgrade `KeyRegistry` public port to `internal helper`
- [ ] Traces: FR-008
- [ ] Files: move `commonMain/api/KeyRegistry.kt` → `commonMain/impl/KeyRegistry.kt`. Add `internal` modifier.
- [ ] Fix all external callers found in T018 — они теперь ходят через `KeyVault`.
- [ ] Verification: `./gradlew build` — clean compile.

### T025 — Fix any remaining external callers found by T023/T024 compile errors
- [ ] Traces: FR-008, SC-001
- [ ] Files: whatever compile errors surface — refactor to use `KeyVault` API.
- [ ] Verification: `./gradlew build check` — full green.

### T026 — Run detekt fitness rule
- [ ] Traces: SC-006, Rule 1
- [ ] Command: `./gradlew :core:keys:detekt`
- [ ] Verification: no violations.

---

## Phase 5 — Cleanup + PR (0.5 days)

### T027 — Remove stale `KeyRegistry` KDoc examples («contacts», «media»)
- [ ] Traces: Decision block «Existing code migration» bullet
- [ ] File: `commonMain/impl/KeyRegistry.kt` (or wherever KDoc lives)
- [ ] Change: remove obsolete purpose examples, add note «internal helper — use KeyVault».

### T028 — Update `docs/architecture/crypto.md` registry
- [ ] Traces: Rule 11 registry sync
- [ ] File: `docs/architecture/crypto.md`
- [ ] Change: `IdentityVault port boundary` row → status `Done` + link to Session 6 Decision block. Add new row for `RecoveryStrategy port` (pluggable per TASK-112).

### T029 — Update `docs/architecture/INDEX.md` registry table
- [ ] Traces: Rule 11 registry sync
- [ ] File: `docs/architecture/INDEX.md`
- [ ] Change: TASK-112 row in Crypto registry table → status `Draft` → `Done`.

### T030 — Run `pre-pr-backlog-sync` skill
- [ ] Traces: CLAUDE.md rule «pre-PR backlog sync HARD RULE»
- [ ] Deliverable: backlog task-112 file updated:
  - `[hand]` AC section populated (5-7 project-specific items).
  - `[auto:checklist]` AC counts regenerated из specs/task-112-keyvault-port/checklists/*.md (если созданы).
  - Status transition Verification (если manual gates) или Done (если все автомат зелёные).

### T031 — Create `PR-DRAFT.md`
- [ ] File: `specs/task-112-keyvault-port/PR-DRAFT.md`
- [ ] Content: summary bullets, test plan checklist, «backlog: task-112 → Done|Verification» line.

### T032 — Final commit + push
- [ ] Command: `rtk git add . && rtk git commit -m ...` + `rtk git push origin task-112-keyvault-port`
- [ ] Verification: PR ready to open via `gh pr create`.

---

## Progress summary (auto-updates via tick-sync)

Phase 1: 0 / 13 tasks (T001-T013)
Phase 2: 0 / 4 tasks (T014-T017)
Phase 3: 0 / 5 tasks (T018-T022)
Phase 4: 0 / 4 tasks (T023-T026)
Phase 5: 0 / 6 tasks (T027-T032)

**Total: 0 / 32 tasks**

**Blockers**: none.
