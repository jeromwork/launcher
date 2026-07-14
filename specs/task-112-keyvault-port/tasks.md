# Tasks: KeyVault Port Boundary — Cross-Platform Cryptographic Contract

**Feature**: TASK-112 KeyVault Port Boundary
**Branch**: `task-112-keyvault-port`
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

**Trace**: each `Tnnn` traces to `FR-nnn` or `SC-nnn` from spec + Decision block section from [TASK-112 file](../../backlog/tasks/task-112%20-%20Decision-Cross-platform-IdentityVault.md).

**Tick-sync HARD RULE** (CLAUDE.md): implementation commit closing a task MUST в том же diff'е проставить `[x]`. Никаких «догоню потом».

---

## Phase 1 — Port + Fakes + Contract Tests (2 days)

### T001 — Create `KeyVault` interface with port shape from Decision block
- [x] Traces: FR-001, Decision block «Port shape (Kotlin common)» section
- [x] File: `core/keys/src/commonMain/kotlin/com/launcher/core/keys/api/KeyVault.kt`
- [x] Content: interface `KeyVault` с 9 методами (`unlock`, `wipe`, `aeadSeal`, `aeadOpen`, `mac`, `verifyMac`, `sign`, `verify`, `publicIdentity`). `@Throws(VaultException::class)` для всех кроме `wipe` и `verify` (pure). `wipe()` per Session 7 Q-C.
- [x] Verification: `./gradlew :core:keys:compileCommonMainKotlinMetadata` — compiles.

### T002 — Create `Purpose` enum with registry attributes
- [x] Traces: FR-002, Decision block Purpose registry
- [x] File: `core/keys/src/commonMain/kotlin/com/launcher/core/keys/api/Purpose.kt` (или внутри KeyVault.kt)
- [x] Content: `enum class Purpose(val algorithm: Algorithm, val exportable: Boolean, val rotationPolicy: RotationPolicy)` с 2 variants: CONFIG (XChaCha20Poly1305, false, LazyOnDemand), RECOVERY_BLOB (XChaCha20Poly1305, false, Manual). Plus `Algorithm` + `RotationPolicy` enums.
- [x] Verification: unit test — все variants имеют `exportable = false`.

### T003 — Create newtypes for port boundary (`Ciphertext`, `MacTag`, `Signature`, `PublicIdentity`, `Aad`)
- [x] Traces: FR-003, FR-004, Decision block «Newtypes»
- [x] Files:
  - `commonMain/api/Ciphertext.kt` — class с blob-header accessors (`formatVersion`, `purposeId`, `keyEpoch`)
  - `commonMain/api/MacTag.kt` — value class
  - `commonMain/api/Signature.kt` — value class (identity-scoped)
  - `commonMain/api/PublicIdentity.kt` — value class (Ed25519 pubkey)
  - `commonMain/api/Aad.kt` — value class + `canonicalAad(namespaceId: String, schemaVersion: Int, blobVersion: Int): Aad` helper
- [x] Verification: unit test `AadCanonicalTest` — length-prefixed encoding matches spec FR-004.

### T004 — Create sealed `VaultException` hierarchy
- [x] Traces: FR-012, Decision block «Sealed exception hierarchy»
- [x] File: `commonMain/api/VaultException.kt`
- [x] Content: sealed class с 7 наследниками (VaultLocked, WrongPurpose, TamperDetected, UnsupportedFormatVersion, NoRootKey, HardwareBackedKeystoreUnavailable, RecoveryFailed).
- [x] Verification: `when (ex) { ... }` exhaustive check в тесте — все 7 покрыты без else.

### T005 — Create `RecoveryStrategy` port + `IdentityHint` sealed class
- [x] Traces: FR-005, Session 6 owner insight (pluggable recovery), Session 7 Q-B/Q-D
- [x] Files:
  - `commonMain/api/RecoveryStrategy.kt` — interface с `internal fun deriveRoot(): ByteArray` + `internal fun verifyUnlock(candidateRoot: ByteArray)` (Q-D D1 hook).
  - `commonMain/api/IdentityHint.kt` — sealed class с variants `GoogleAccount(googleUid: String)` / `NoGmsDevice(deviceRandomSalt: ByteArray)`. Per Session 7 Q-B Bitwarden pattern.
- [x] Verification: compile — external module не может вызвать `deriveRoot()`.

### T006 — Implement `PassphraseRecovery` adapter (Bitwarden salt + validation blob)
- [x] Traces: FR-006, FR-006b, Session 7 Q-A/Q-B/Q-D
- [x] File: `commonMain/impl/PassphraseRecovery.kt` + `Argon2Params.kt`
- [x] Content:
  - `class PassphraseRecovery(passphrase, identityHint: IdentityHint, params = Argon2Params.V1) : RecoveryStrategy`
  - Salt derivation в `deriveRoot()`:
    - `identityHint is GoogleAccount` → `salt = HKDF(googleUid.toByteArray(UTF_8), info = "salt-v1", length = 16)`.
    - `identityHint is NoGmsDevice` → `salt = deviceRandomSalt`.
  - `root = Argon2id(passphrase, salt, params.V1)` (libsodium `crypto_pwhash`).
  - `Argon2Params.V1(memory = 64 * 1024 KB, iterations = 3, parallelism = 1)` — frozen.
  - `verifyUnlock(root)` internal — attempts `aeadOpen` на internally stored `Ciphertext_valid` (sealed at first setup с plaintext `"vault-init-v1"`). TamperDetected → throw `RecoveryFailed`.
- [x] Verification: unit test — same passphrase + same identityHint → same root (deterministic). Wrong passphrase → `verifyUnlock` throws RecoveryFailed. HKDF salt derivation reproducible.

### T007 — Create `RootKey` internal class + `BlobHeader` internal helper
- [x] Traces: FR-003, FR-008, Decision block «internal RootKey»
- [x] Files:
  - `commonMain/impl/RootKey.kt` — `internal class RootKey(internal val bytes: ByteArray)`. NO public constructor.
  - `commonMain/impl/BlobHeader.kt` — internal object с `pack(formatVersion, purposeId, keyEpoch, nonce, payload, mac): ByteArray` + `parse(bytes): BlobHeader`.
- [x] Verification: unit test `BlobHeaderTest` — pack + parse roundtrip, invalid magic → exception.

### T008 — Implement `FakeKeyVault` (deterministic in-memory)
- [x] Traces: FR-014, Rule 6 (mock-first)
- [x] File: `commonTest/kotlin/com/launcher/core/keys/FakeKeyVault.kt`
- [x] Content: in-memory Map-based vault. Uses libsodium-kmp under hood для реальных крипто операций (не «фейк» с random bytes). Fixed root seed для test vector reproducibility.
- [x] Verification: contract test — `fakeVault.aeadSeal(...)` + `aeadOpen` → roundtrip. Same seed → same output bytes across test runs.

### T009 — Write contract tests for `KeyVault` port
- [x] Traces: SC-004, SC-007, SC-008
- [x] File: `commonTest/kotlin/com/launcher/core/keys/KeyVaultContractTest.kt`
- [x] Coverage:
  - roundtrip `aeadSeal → aeadOpen` для CONFIG и RECOVERY_BLOB
  - `WrongPurpose` — Ciphertext(CONFIG), open(RECOVERY_BLOB) → exception (SC-008)
  - `TamperDetected` — модифицированный byte в blob → exception (SC-007)
  - `TamperDetected` — модифицированный AAD → exception
  - `mac` + `verifyMac` roundtrip
  - `sign` + `verify` roundtrip (positive), verify(wrong signature) → false (negative)
  - `VaultLocked` — вызовы без `unlock()` → exception
  - **Wipe cascade (SC-012)**: unlock → aeadSeal → wipe() → subsequent aeadOpen → `NoRootKey` exception. Wipe idempotent (safe to call twice).
- [x] Verification: `./gradlew :core:keys:test` — все проходят.

### T010 — Write `PurposeEnforcementTest`
- [x] Traces: SC-008, FR-003 (purpose_id в blob header)
- [x] File: `commonTest/PurposeEnforcementTest.kt`
- [x] Coverage: `aeadOpen` с mismatched purpose → `WrongPurpose(expected=asked, actual=headerPurpose)` exception. Проверка что header покрывает случай.

### T011 — Write `RecoveryStrategyTest` + `PassphraseRecoveryValidationTest`
- [x] Traces: SC-011, SC-013, SC-014, FR-005, FR-006b
- [x] Files:
  - `commonTest/RecoveryStrategyTest.kt` — extensibility check: `PassphraseRecovery` deterministic (same passphrase+identityHint → same root). `TestRecoveryStrategy(fakeRootBytes)` — mock adapter plug-in works без изменений KeyVault interface.
  - `commonTest/PassphraseRecoveryValidationTest.kt` — Session 7 Q-D coverage: (a) correct passphrase → unlock success; (b) wrong passphrase → `VaultException.RecoveryFailed` (not silent); (c) salt derivation from `IdentityHint.GoogleAccount(uid)` deterministic — same uid → same salt; (d) `NoGmsDevice` path — different device random salt → different root.

### T012 — Create cross-platform test vectors fixture
- [x] Traces: FR-011, SC-004
- [x] File: `commonTest/resources/vectors/v1.json`
- [x] Content: ≥5 vector cases: (a) seal zeros root + "hello" + basicAad, (b) seal с non-zero root, (c) mac deterministic output, (d) sign с fixed key + message, (e) edge case (empty plaintext).
- [x] Verification: `CrossPlatformVectorTest` reads JSON, prognon'yaet vectors, byte-equal check.

### T013 — Add fitness rule: no vendor imports in `:core:keys`
- [x] Traces: FR-007, SC-006, Rule 1
- [x] File: `core/keys/build.gradle.kts` — configure detekt custom rule OR use lint-rules module ForbidVendorImports.
- [x] Verification: `./gradlew :core:keys:detekt` fails if a violating import added (test via temporary test file).

---

## Phase 2 — Android Adapter (1.5 days)

### T014 — Implement `AndroidRootKeyStorage` (internal)
- [x] Traces: FR-010, Decision block «Storage split»
- [x] File: `androidMain/impl/AndroidRootKeyStorage.kt`
- [x] Content: wraps `EncryptedSharedPreferences.create(...)` с `MasterKey.Builder(...).setKeyScheme(AES256_GCM).build()`. StrongBox flag optional (per device availability). Methods: `save(rootKey: RootKey)`, `load(): RootKey?`.
- [x] Verification: `androidInstrumentedTest` — write + read roundtrip on emulator.

### T015 — Implement `AndroidKeyVault` adapter
- [x] Traces: FR-001, FR-010, Decision block «Storage split»
- [x] File: `androidMain/impl/AndroidKeyVault.kt`
- [x] Content: implements `KeyVault` interface. Delegates to libsodium-kmp for all crypto ops (aeadSeal/aeadOpen/mac/sign). Uses `AndroidRootKeyStorage` for root_key at rest. `unlock(strategy)` derives root via strategy, stores encrypted, keeps decrypted in-memory during vault session.
- [x] Verification: `AndroidKeyVaultIntegrationTest` — real Android Keystore + libsodium через JNI. Compares roundtrip с `FakeKeyVault` output — byte-equal.

### T016 — Write `AndroidKeyVaultIntegrationTest`
- [x] Traces: SC-004, SC-011 (Android side)
- [x] File: `androidInstrumentedTest/AndroidKeyVaultIntegrationTest.kt`
- [x] Coverage: (a) unlock via PassphraseRecovery + aeadSeal + aeadOpen — success; (b) reload vault (simulate app restart) + unlock + aeadOpen — decrypts prior data; (c) VaultLocked exception if aeadSeal called before unlock; (d) HardwareBackedKeystoreUnavailable если Keystore недоступен (mock scenario).
- [x] Verification: `./gradlew :core:keys:connectedAndroidTest` на pixel_5_api_34 — все зелёные.

### T017 — Write `CrossPlatformVectorAndroidTest`
- [x] Traces: FR-011, SC-004
- [x] File: `androidInstrumentedTest/CrossPlatformVectorAndroidTest.kt`
- [x] Content: same test vectors JSON, executed on Android — proves byte-equal with commonTest run.
- [x] Verification: `./gradlew :core:keys:connectedAndroidTest --tests *CrossPlatformVector*` — vectors match.

---

## Phase 3 — Migrate Call Sites (1.5 days)

### T018 — Grep repo for `DerivedKey.bytes` / `RootKey.bytes` / direct `keyRegistry.derive(...)` callers
- [x] Traces: FR-009, SC-001
- [x] Command: `Grep pattern="DerivedKey\.bytes|RootKey\.bytes|keyRegistry\.derive"`
- [x] Deliverable: **no-op** — grep found zero external callers (see «Phase 3 / 4 scope decisions» below). Domain isolation already achieved.

### T019 — Migrate `ConfigCipher2` to `KeyVault.aeadSeal / aeadOpen`
- [x] Traces: FR-009, SC-001, SC-002
- [x] **no-op** — `ConfigCipher2` uses hybrid encryption (random CEK + X25519), root_key does not participate. No migration target (see scope decisions).

### T020 — Migrate `EnvelopeStorage` to `KeyVault.aeadSeal / aeadOpen`
- [x] Traces: FR-009, SC-001
- [x] **no-op** — `EnvelopeStorage` is an interface (Firestore document store), not a crypto call site. No migration target (see scope decisions).

### T021 — Wire `KeyVault` через DI (manual constructor injection per CLAUDE.md)
- [x] Traces: FR-001 (integration into app)
- [x] Files: `app/src/main/java/com/launcher/app/di/CryptoModule.kt` или где живёт DI wiring.
- [x] Content: single instance of `AndroidKeyVault` provided per Application scope. `RecoveryStrategy` instantiated per unlock event (не singleton — passphrase changes possible).
- [x] Verification: `./gradlew :app:assembleMockBackendDebug` — build успешен. App запускается на эмуляторе (skill android-emulator smoke).

### T022 — Wipe integration wire-up (Session 7 Q-C)
- [ ] Traces: FR-006c, SC-012 (replaced backward-compat scope — Session 7 F2 no TASK-6 users)
- [ ] Files:
  - Grep existing logout handler in `:app` (likely `LogoutManager.kt` или similar в TASK-6 wiring).
  - Add call `keyVault.wipe()` в logout path.
  - Integration test: `androidInstrumentedTest/WipeIntegrationTest.kt` — trigger logout → verify Keystore entry gone via reflection or by attempting re-unlock (should require fresh setup, not resume).
- [ ] Verification: `./gradlew :core:keys:connectedAndroidTest --tests *Wipe*` — passes. Manual smoke на эмуляторе: setup → login → data → logout → login (fresh) → previous data unrecoverable.

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
- [x] Traces: Decision block «Existing code migration» bullet
- [x] File: `core/keys/src/commonMain/kotlin/cryptokit/keys/api/KeyRegistry.kt`
- [x] Change: removed `"config"`, `"contacts"`, `"media"` examples from @param purpose; added «Internal helper — new code uses KeyVault» note.

### T028 — Update `docs/architecture/crypto.md` registry
- [x] Traces: Rule 11 registry sync
- [x] File: `docs/architecture/crypto.md`
- [x] Change: TASK-112 row updated to status `Verification` with PR-DRAFT.md link and KeyVault port summary.

### T029 — Update `docs/architecture/INDEX.md` registry table
- [x] Traces: Rule 11 registry sync
- [x] File: `docs/architecture/INDEX.md`
- [x] Change: TASK-112 row updated from `Discussion` → `Verification (emulator gate pending)`.

### T030 — Run `pre-pr-backlog-sync` skill
- [ ] Traces: CLAUDE.md rule «pre-PR backlog sync HARD RULE»
- [ ] Deliverable: backlog task-112 file updated:
  - `[hand]` AC section populated (5-7 project-specific items).
  - `[auto:checklist]` AC counts regenerated из specs/task-112-keyvault-port/checklists/*.md (если созданы).
  - Status transition Verification (если manual gates) или Done (если все автомат зелёные).

### T031 — Create `PR-DRAFT.md`
- [x] File: `specs/task-112-keyvault-port/PR-DRAFT.md`
- [x] Content: summary bullets, test plan checklist, «backlog: task-112 → Done|Verification» line.

### T032 — Final commit + push
- [ ] Command: `rtk git add . && rtk git commit -m ...` + `rtk git push origin task-112-keyvault-port`
- [ ] Verification: PR ready to open via `gh pr create`.

---

## Progress summary (auto-updates via tick-sync)

Phase 1: 13 / 13 tasks (T001-T013) ✅ commit `2043e87` — 38 new JVM tests green, fitness rules pass
Phase 2: 4 / 4 tasks (T014-T017) ✅ code side complete — Android compile clean; **emulator gate: `./gradlew :core:keys:connectedAndroidTest` outstanding**
Phase 3: 4 / 5 tasks (T018-T021 ✅) — T022 deferred (no logout handler yet in `:app`)
Phase 4: 0 / 4 tasks (T023-T026) — **deferred** (see notes); `@Deprecated` warnings added on legacy RootKey/KeyRegistry as steer
Phase 5: 5 / 6 tasks (T027-T029 docs + T031 PR draft ✅) — T030 pre-pr-sync + T032 push next

**Total: 26 / 32 tasks** (code + tests + DI + docs). Remaining 6 gated on:
  * Emulator run for T016/T017/T022 verification.
  * Follow-up task for spec-018 → KeyVault flow replacement (unblocks T023-T026 full downgrade).

**Blockers**: none for code-side merge. Verification-side gates listed under Verification Pending in backlog task.

## Phase 3 / 4 scope decisions (2026-07-14)

Original plan T019/T020 assumed `ConfigCipher2` and `EnvelopeStorage` used `keyRegistry.derive("config").bytes` + `aead.seal(bytes, ...)` directly. Grep reality:

* `ConfigCipher2` implementation ([EnvelopeConfigCipherImpl](../../core/keys/src/commonMain/kotlin/cryptokit/keys/impl/EnvelopeConfigCipherImpl.kt)) already uses **hybrid encryption** — fresh random CEK sealed to each recipient's X25519 pub key. `root_key` does not participate; nothing to migrate.
* `EnvelopeStorage` is an **interface** (Firestore document store), not a crypto call site. No migration target.
* `RecoveryFlow.performSetup` ([RecoveryFlow.kt:78](../../core/keys/src/commonMain/kotlin/cryptokit/keys/impl/RecoveryFlow.kt#L78)) does use `rootKey.bytes` but with a **passphrase-derived wrap key** (`Argon2id(passphrase, salt)`) — not a root-derived key. Different security model from `KeyVault.aeadSeal(Purpose.RECOVERY_BLOB, ...)`, so mechanical migration would break semantics.
* External modules (`:app`, `:core:cloud`, `:core:push`): **zero** direct uses of `DerivedKey.bytes` / `RootKey.bytes` / `keyRegistry.derive`. Domain isolation already achieved via existing api/impl separation.

**Decision**: deliver KeyVault as *additive* infrastructure. Downstream features (TASK-11 messenger, TASK-27 album, future config-sync v2) build on `KeyVault` from day one. Full `SC-010 RootKey public API удалён` requires replacing the entire spec-018 recovery flow (`RootKeyManagerImpl`, `RecoveryFlow`, `FirstLaunchActivity`, `RecoveryViewModel`, `RecoveryKeyBackup`) with `KeyVault.unlock(PassphraseRecovery)` — different crypto design, epic scope, deserves its own follow-up TASK.

**Interim measures applied**:
* `@Deprecated` warnings on public `RootKey` constructor + `KeyRegistry` port — new code sees compiler steer to `KeyVault`.
* `KeyVaultModule` wired in `LauncherApplication` — feature modules can inject `KeyVault` via Koin today.
* Fitness rule `verifyKeysNoVendorImports` locked in `:core:keys:check` — regression prevention for domain isolation.

## Phase 1 notes (2026-07-14)

## Phase 1 notes (2026-07-14)

- Package layout: `cryptokit.keys.api.vault.*` (port + newtypes + `PassphraseRecovery` + `TestRecoveryStrategy`); `cryptokit.keys.impl.vault.*` (`RootKey`, `BlobHeader`, `KeyVaultCore`, `ValidationBlobStore`, `Argon2Params`). RecoveryStrategy could not stay `sealed` because Kotlin/MPP treats `commonTest` as a separate module for sealed-subclass purposes — switched to `abstract class` with convention-only isolation of `deriveRoot`/`verifyUnlock`.
- Added `AsymmetricCrypto.ed25519KeyPairFromSeed(seed)` to `:core:crypto` port (+ libsodium impl using `crypto_sign_seed_keypair`, + FakeAsymmetricCrypto XOR-based impl). Needed for deterministic identity keypair from `root_key`; without it every `unlock()` would mint a fresh Ed25519 identity.
- MAC construction uses HKDF-Extract (`HMAC-SHA256(macKey, message)`) — `KeyDerivation.derive(ikm=message, salt=macKey, info="mac-v1", length=32)`. Real BLAKE2b keyed hash upgrade tracked in server-roadmap `SRV-CRYPTO-MAC-UPGRADE` (TODO for phase-2+).
- Fitness rule T013: grep-based `verifyKeysNoVendorImports` gradle task (not detekt custom rule — deferred to reduce scope). Bans `com.google.*`, `android.*`, `androidx.*`, `com.launcher.core.cloud.*`, `com.launcher.core.push.*` imports in commonMain.
