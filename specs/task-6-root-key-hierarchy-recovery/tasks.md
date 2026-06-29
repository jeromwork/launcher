# Tasks: F-5 — Root Key Hierarchy + Owner Recovery

**Input**: Design documents from `/specs/task-6-root-key-hierarchy-recovery/`
**Prerequisites**: spec.md ✓, plan.md ✓, research.md ✓, data-model.md ✓, contracts/{recovery-key-backup-v1.md, worker-api-v1.md} ✓
**Backlog**: [TASK-6](../../backlog/tasks/task-6%20-%20F-5-Root-Key-Hierarchy-Owner-Recovery.md)
**Branch**: `task-6-root-key-hierarchy-recovery`

## Format: `[ID] [P?] [USx] Description`

- **[P]**: Parallel-safe (different file, no shared dependencies).
- **[USx]**: Maps to spec.md User Story (US-1...US-6).
- **[deferred-*]** markers: per CLAUDE.md hybrid-AC model — task can't be closed in AI session; picked up by `pre-pr-backlog-sync` as `[auto:deferred-*]` backlog AC.
- Trace: every task references `(FR-NNN, US-NNN, Plan §X)` or `(SC-NNN)`.

ID numbering: `T6NN` consistent with task-6 (spec 003 used T3NN; spec task-49 used T49NN — but we choose T6NN here for the linked backlog ID).

---

## Phase 1 — Foundation: gradle + domain types + ports + fakes + contract tests (JVM-only)

**Purpose**: domain port shapes nailed; fitness functions in place; contract tests green. Fastest feedback loop — no emulator required.

### Phase 0 — Inventory (mandatory BEFORE any code change)

- [x] **T600** **STOP and read** existing `core/keys/` module (already exists from spec 018 / TASK-4 F-5b). Inventory what's there. Produce `specs/task-6-root-key-hierarchy-recovery/inventory.md` with three lists: (a) **KEEP** files (legacy F-5b that F-5 builds on — e.g., `ConfigCipher2.kt`, `AuthIdentity.kt`, `Envelope.kt`); (b) **MIGRATE** files (touched by F-5, e.g., `ConfigCipher2` per FR-018 byte-equal); (c) **ADD** files (new F-5 types — `RootKey.kt`, `KeyRegistry.kt`, `RecoveryKeyBackup.kt`, etc. per plan.md §Project Structure). NO code changes in T600 — only inventory. (Mandatory pre-implementation reconciliation per gemini-handoff.md §«Existing code reality».)

### Phase 0.5 — A1 Wire-format alignment (mandatory pre-Phase-2 blocker)

- [x] **T600.5** **A1 resolution — wire-format alignment**: implement Variant 2 per [a1-resolution.md](./a1-resolution.md). Restructures `RecoveryKeyBackupBlob` to match contract `recovery-key-backup-v1.md` shape (adds `stableId`, renames `kdfSalt`→`salt`, `wrappedRootKey`→`ciphertext`, drops top-level `algorithm`, `createdAt: Long`→`Instant`). Consolidates `PassphraseKdfParams`→`KdfParams`. Regenerates v1/v2 fixtures. Updates 4 production + 7 test files. Preserves `AAD_PREFIX` (D4 scope exclusion). **Phase 2 blocked until this is green.** (A1 STOP-block resolution — owner decision 2026-06-28)

### Setup

- [x] **T601** **Verify existing** `core/keys/build.gradle.kts`. Module ALREADY exists (from spec 018 / TASK-4). Do **NOT** create new module. Only changes allowed: (i) add `kotlinx-serialization-json` if not already in dependencies (it likely is for envelope JSON); (ii) verify dependency on `:core:crypto` (libsodium primitives from TASK-51) is wired. Verify `./gradlew :core:keys:build` succeeds with no changes (baseline). (Plan §Project Structure — adjusted to actual codebase reality.)

### Domain value types (commonMain/api/)

- [x] **T602** [P] [US-1, US-2] Add `StableId.kt` type alias = `String` with KDoc invariants (UUID v4, provider-agnostic, NO Google sub/email/phone). (FR-001, data-model.md §2)
- [x] **T603** [P] [US-1] Add `KdfParams.kt` value class: algorithm, iterations, memoryKb, parallelism. Init-block validation (algorithm in known set; iterations≥1; memoryKb≥1024; parallelism≥1). (FR-006, data-model.md §5)
- [x] **T604** [P] [US-1] Add `RootKey.kt` opaque value class (32-byte material, private constructor, no toString exposing material). (FR-003, data-model.md §3)
- [x] **T605** [P] [US-1] Add `DerivedKey.kt` opaque value class (HKDF output wrapper, same privacy as RootKey). (FR-002, data-model.md §4)
- [x] **T606** [P] [US-4] Add `AvailabilityReason.kt` enum: `NoSupportedProvider`, `KeystoreLocked`, `NetworkUnreachable`. Forbidden values doc-line «no Google/GMS/Huawei/HMS/Apple/Firebase strings». (FR-005, data-model.md §8)
- [x] **T607** [P] [US-4] Add `AuthAvailabilityStatus.kt` sealed class: `Available | Unavailable(reason: AvailabilityReason)`. (FR-005, data-model.md §7)
- [x] **T608** [P] [US-1, US-2, US-3] Add `RootKeyError.kt` sealed: `WrongPassphrase | CorruptedBlob | NoKeystore | NoIdentity`. (FR-003, data-model.md §9)
- [x] **T609** [P] [US-1, US-2, US-3] Add `BackupError.kt` sealed: `NetworkUnavailable | AuthExpired | ServerQuotaExceeded | Conflict | UnsupportedSchema`. (FR-004, data-model.md §10)

### Wire format

- [x] **T610** [US-1, US-2] Add `RecoveryKeyBackupBlob.kt` data class per `contracts/recovery-key-backup-v1.md`: schemaVersion=1 (const `SCHEMA_VERSION_V1`), stableId, salt (ByteArray), kdfParams, ciphertext, nonce, createdAt (Instant). kotlinx-serialization-json codec with base64 for ByteArray fields. (FR-006, contract spec)
- [x] **T611** [US-1] Add `RecoveryBlobCodec.kt` in `impl/`: JSON encode/decode with explicit schemaVersion check (UnsupportedSchema for `version > 1`). (contracts/recovery-key-backup-v1.md §Versioning)

### Domain ports (commonMain/api/)

- [x] **T612** [P] [US-1, US-2, US-3] Add `KeyRegistry.kt` port: `derive(stableId, purpose)`, `wipeAll(stableId)`, `list(stableId)`. Inline TODO comment per FR-007 (Purpose registry, when N>5). (FR-002)
- [x] **T613** [P] [US-1, US-2, US-3] Add `RootKeyManager.kt` port: `current: Flow<RootKey?>`, `create()`, `recover()`, `forget()`. Inline TODO comment per FR-007 (one-way door rule-3 + TASK-41 exit ramp). (FR-003)
- [x] **T614** [P] [US-1, US-2, US-3] Add `RecoveryKeyBackup.kt` port: `uploadBlob()`, `fetchBlob()`, `deleteBlob()`. Inline TODO comment per FR-007 (SRV-RECOVERY-001 exit ramp на own-server). (FR-004) <!-- completed via D4 rename 7be001c -->
- [x] **T615** [P] [US-1, US-4] Add `AuthAvailability.kt` port: `check(): AuthAvailabilityStatus`. (FR-005)

### Fake adapters (commonTest/fakes/)

- [x] **T616** [P] Add `FakeKeyRegistry.kt`: in-memory Map<StableId, Map<String, DerivedKey>>; deterministic derivation (SHA-256 of stableId+purpose for test material). (FR-022, CLAUDE.md rule 6)
- [x] **T617** [P] Add `FakeRootKeyManager.kt`: stateful Flow current; in-memory passphrase-blob storage; deterministic Argon2-stub. (FR-022)
- [x] **T618** [P] Add `FakeRecoveryKeyBackup.kt`: in-memory Map<StableId, RecoveryKeyBackupBlob>; shared between test instances для cross-device test. (FR-022) <!-- completed via D4 rename 7be001c -->
- [x] **T619** [P] Add `FakeAuthAvailability.kt`: returns hardcoded `Available` or `Unavailable(reason)` set by test. (FR-022)

### Contract tests (commonTest/)

- [x] **T620** [P] [US-1] `KeyRegistryDerivationDeterminismTest`: same stableId+purpose → same DerivedKey (10 iterations); different purpose → different key. (SC-013, FR-023, plan §Test Strategy)
- [x] **T621** [P] [US-2] `KeyRegistryIsolationTest`: different stableId → different DerivedKey for same purpose; wipe of one namespace не trogает другие. (SC-013, FR-023)
- [x] **T622** [P] [US-1, US-2] `RecoveryKeyBackupBlobRoundtripTest`: encode → decode → assert structural equal (all fields including kdfParams). (SC-013, FR-023, contract §Test contracts)
- [x] **T623** [P] [US-2] `RecoveryKeyBackupBlobBackwardCompatTest`: decode `core/keys/src/commonTest/resources/fixtures/recovery-blob-v1-sample.json` → success; assert all v1 fields populated. (SC-013, FR-023)
- [x] **T624** [P] [US-6] `RecoveryKeyBackupBlobProviderAgnosticTest`: parse JSON keys, assert ABSENCE of `googleSub`, `firebaseUid`, `providerKind`, `providerId`, `googleAccountId`, `email`, `phoneNumber`. (SC-008, FR-023, contract §Forbidden fields)
- [x] **T625** [P] [US-6] `RecoveryKeyBackupBlobUnsupportedSchemaTest`: fixture с schemaVersion=2 → decode returns `BackupError.UnsupportedSchema`. (contracts/recovery-key-backup-v1.md §Versioning forward-compat)
- [x] **T626** [P] [US-6] `RootKeyManagerProviderAgnosticTest`: prove US-1 + US-2 scenarios через `FakeAuthAdapter` (provider-agnostic) — fitness function. (SC-009, US-6 acceptance scenario 2)
- [x] **T627** [P] [US-6] `RootKeyForgetFlowTest`: после `RootKeyManager.forget()` → `KeyRegistry.list(stableId)` returns empty, `RootKeyManager.current` emits null. (FR-019, SC-012)

### Fixture files (commonTest/resources/fixtures/)

- [x] **T628** [P] Create `recovery-blob-v1-sample.json` per contracts/recovery-key-backup-v1.md §Canonical example. (contract §Fixture path)
- [x] **T629** [P] Create `recovery-blob-v2-sample-future.json` (synthetic v2 для T625 forward-compat test). (T625 dependency)
- [ ] **T630** [P] Create `config-ciphertext-spec018-sample.bin` (capture from spec 018 fixture for T652 migration test).

### Fitness function (Konsist rule)

- [x] **T631** [US-6] Add Konsist rule in `core/keys/src/jvmTest/`: grep `core/keys/src/commonMain/` for forbidden tokens `Google|Firebase|OAuth|Apple|Phone|Email|Sub|IdToken|Cloudflare|Worker`. Test fails if any match. Wire into `./gradlew :core:keys:check`. (SC-007, plan §Test Strategy)

---

## Phase 2 — Android adapters (commonMain unchanged)

**Purpose**: real Android Keystore, real Argon2 via libsodium, real DataStore. Unit tests where possible; integration tests `[deferred-local-emulator]`.

- [x] **T632** Add `core/keys/src/androidMain/kotlin/family/keys/android/AndroidKeystoreRegistry.kt`: implements `KeyRegistry` via `cryptokit.crypto.api.SecureKeyStore` + `KeyDerivation` (HKDF) ports from `:core:crypto`. Alias pattern `key-registry/{stableId}/{purpose}`. StrongBox hint when API ≥ 28. Package `family.keys.android` per handoff §4. (FR-008, plan §Project Structure)
- [x] **T633** **Wire real `recover()` into existing `RootKeyManagerImpl`** (commonMain, per inventory.md D3 EXTEND EXISTING — no separate `Argon2RootKeyManager.kt` file). Inject `RecoveryFlow` (or its primitives: `Argon2idPassphraseKdf` + `AeadCipher`) into `RootKeyManagerImpl`, replace the `RecoveryRequired` stub with a call into the recovery codepath, and reuse the existing `seedFromRecovery` internal hook. Argon2id parameters are read from `blob.kdfParams`, not hardcoded (DZ-10, R16). CharArray wipe is owned by `RecoveryFlow.performRecovery` (already implemented). (FR-009, research.md R4, inventory.md D3) — implementation: `RecoveryDelegate` indirection + `RecoveryFlowDelegate` adapter (commonMain) breaks circular construction; DI binds via `bindRecoveryDelegate()`.
- [x] **T634** Add `core/keys/src/androidMain/kotlin/family/keys/android/AndroidDeviceKeyNamespaceProvider.kt`: random UUID v4 at first launch, persisted in DataStore (`device_key_namespace_v1`), для US-4 local-mode device-key namespace. Package `family.keys.android` per handoff §4. Port `DeviceKeyNamespaceProvider` introduced in commonMain. (FR-002 device-key namespace path)
- [x] **T635** Added `app/src/main/java/com/launcher/app/data/recovery/WorkerRecoveryKeyBackup.kt`: implements `RecoveryKeyBackup` via `HttpURLConnection` (JDK built-in — no OkHttp dependency) → `BuildConfig.RECOVERY_BACKUP_WORKER_URL`. Bearer JWT from `IdTokenProvider` port. Idempotency-Key UUID v4 per round. 3 retries with exponential back-off on 5xx/429/IO failure. HTTP-status → `BackupError` mapping per contracts/worker-api-v1.md. (FR-010, contracts/worker-api-v1.md)
- [x] **T636** [P] Add `app/src/main/kotlin/com/launcher/data/recovery/DataStorePassphraseAttemptCounter.kt`: DataStore-backed per-identity counter `recovery-attempts/{stableId}`. Survives process kill; cleared on Fallback wipe. (FR-015, SC-011) — pre-existing from spec 018 Batch 3 (T122e-T122i); inventory verified compatible with task-6.
- [x] **T637** [P] Add `app/src/main/kotlin/com/launcher/data/recovery/DataStoreSchemaVersionMemory.kt`: tracks last-seen schemaVersion for FR-018 byte-equal migration detection. (FR-018) — pre-existing from spec 018 Batch 3; inventory verified.
- [x] **T638** Added `app/src/main/java/com/launcher/app/data/recovery/AuthAvailabilityAndroidImpl.kt`: implements `AuthAvailability` via `GmsAvailabilityPort` (spec 010 surface, F-4 territory). Maps `GmsStatus.MissingFatal` → `Unavailable(NoSupportedProvider)`; `Available` / `MissingRecoverable` → `Available`. (FR-013)
- [x] **T639** Added `BuildConfig.RECOVERY_BACKUP_WORKER_URL` в `app/build.gradle.kts`: debug → `http://10.0.2.2:8787` (host через emulator; for real device use `adb reverse tcp:8787 tcp:8787`); release → from `-PRECOVERY_BACKUP_WORKER_URL=...` or placeholder. Inline TODO server-roadmap SRV-RECOVERY-001 для custom domain. Also fixed missing `libs.kotlinx.datetime` app dep (A1 follow-on). (clarify Q-O)

### Adapter tests (where unit-testable on JVM)

- [x] **T640** [P] `WorkerRecoveryKeyBackupTest` (JVM): bare `ServerSocket`-backed HTTP/1.1 mock (no MockWebServer dependency); 14 tests covering happy-path POST/GET/DELETE, 401/403/404/409/429/500/507 mapping, retry exhaustion, Idempotency-Key presence, missing-token short-circuit. 14/0/0 PASS. (FR-010, plan §Test Strategy)
- [x] **T641** [P] `AuthAvailabilityAndroidImplTest` (JVM): stub-based GmsAvailabilityPort, verifies the 3 GmsStatus → AuthAvailabilityStatus mappings. 3/0/0 PASS. (FR-013)

### Adapter tests requiring Android runtime

- [x] **T642** [deferred-local-emulator] `AndroidKeystoreRegistryTest` (androidInstrumentedTest): 5 tests against real Keystore + LibsodiumKeyDerivation. Covers derivation determinism, cross-purpose isolation, cross-stableId isolation, wipe semantics, multi-identity wipe scoping. **Compiles**; owner runs on emulator pixel_5_api_34 or Xiaomi 11T. (FR-008, memory `reference_compose_ui_test_api_mismatch`)
- [x] **T643** [deferred-physical-device] `Argon2idAndroidPerfBenchmark` (androidInstrumentedTest): pre-existing from spec 018 Batch 7 (T122b). Verifies SC-010 (≤1500ms updated threshold per 2026-06-19 owner approval) using real libsodium Argon2id. **Compiles**; owner runs on Xiaomi 11T. (SC-010, memory `reference_testing_environment`)

---

## Phase 3 — Compose UI screens (3 screens + ViewModel)

**Purpose**: 3 user-facing screens for setup / entry / fallback. UI tests `[deferred-local-emulator]` (API ≤ 34 due to composeUiTest 1.7.x).

- [x] **T644** `app/src/main/java/com/launcher/app/ui/recovery/RecoveryViewModel.kt` — pre-existing from spec 018 Batch 5 (T073). 126 lines, state machine Idle → SettingUp/Restoring → Done/Error/Fallback, implements PassphrasePrompter via CompletableDeferred bridge. SavedStateHandle integration pending (TODO captured in T652). (FR-017)
- [x] **T645** [US-1] `app/src/main/java/com/launcher/app/ui/recovery/RecoveryPassphraseSetupScreen.kt` — pre-existing from spec 018 Batch 5 (T070). 166 lines, native EditText for reliable Autofill NEW_PASSWORD hint, clipboard auto-clear 60s, min-8-char validation, senior-safe styling, Russian copy with contentDescription semantics. (FR-014, US-1 acceptance)
- [x] **T646** [US-2] `app/src/main/java/com/launcher/app/ui/recovery/RecoveryPassphraseEntryScreen.kt` — pre-existing from spec 018 Batch 5 (T071). 110 lines, single Autofill PASSWORD field, attempt counter content description with countdown, fallback nav after 3 failures. (FR-015, US-2 acceptance)
- [x] **T647** [US-3] `app/src/main/java/com/launcher/app/ui/recovery/RecoveryFallbackScreen.kt` — pre-existing from spec 018 Batch 5 (T072). 74 lines, three FallbackReason headlines (TOO_MANY_ATTEMPTS, MALFORMED_VAULT, NO_VAULT), destructive button, retry-or-setup-new flow. (FR-016, US-3 acceptance)
- [x] **T648** `app/src/main/java/com/launcher/app/di/F018KeysModule.kt` — pre-existing from spec 018 Batch 5. Wires IdentityProof, PasswordHash, Argon2id, PassphraseAttemptCounter, SchemaVersionMemory. WorkerRecoveryKeyBackup wiring added in Phase 4 Track C (T667-T668). (Plan §Project Structure, FR-011 / FR-012 simplification)

### UI tests

- [x] **T649** [P] [deferred-local-emulator] `RecoveryPassphraseSetupScreenTest` skeleton (composeUiTest) — compiles; covers field-visibility smoke. Inline TODO for mismatch / min-length / submit-callback / cancel-callback / tap-target-size when AVD online. (FR-014)
- [x] **T650** [P] [deferred-local-emulator] `RecoveryPassphraseEntryScreenTest` skeleton — compiles; covers initial field visibility. Inline TODO for attempt-counter transitions and auto-Fallback trigger. (FR-015, SC-011)
- [x] **T651** [P] [deferred-local-emulator] `RecoveryFallbackScreenTest` skeleton — compiles; covers TOO_MANY_ATTEMPTS title. Inline TODO for MALFORMED_VAULT / NO_VAULT and confirmation-dialog flow. (FR-016)
- [x] **T652** [P] [deferred-local-emulator] `RecoveryViewModelStateTest` skeleton — compiles; needs SavedStateHandle wiring in production ViewModel first (TODO inline). (FR-017)

---

## Phase 4 — `workers/backup/` + `workers/identity/` Cloudflare Worker implementation (in-scope)

**Purpose**: implement two Workers within TASK-6 scope (per owner direction 2026-06-28 «делаем в этой же таске»). Code structure follows microservice boundaries (`workers/backup/` ≠ `workers/identity/`, memory `project_workers_microservice_mapping.md`), но backlog tracking — единый TASK-6 (memory `feedback_one_task_per_feature.md`).

> **Phase 4 blocks integration tests** but **does NOT block** Phase 1/2/3 (use Fake adapters). Phases can proceed in parallel — Android side (Kotlin) против Fakes, TS Worker side в параллель.

### Track A — `workers/backup/` (blob storage Worker)

- [x] **T653** Scaffolded `workers/backup/` TS project: `wrangler.toml` (R2 binding `RECOVERY_BLOBS` → bucket `launcher-recovery-blobs`, env vars `FIREBASE_PROJECT_ID` + `MAX_SUPPORTED_SCHEMA_VERSION`, JWKS_CACHE binding scaffolded but pending owner-side KV provisioning), `package.json` (vitest + wrangler + `@familycare/auth-jwt` workspace dep), `tsconfig.json`, `vitest.config.ts`. **Deploy NOT executed** — owner has Cloudflare credentials. (Plan §Project Structure, contracts/worker-api-v1.md §Deployment)
- [x] **T654** Implemented `workers/backup/src/index.ts`: 3 endpoints (POST `/backup`, GET `/backup/:stableId`, DELETE `/backup/:stableId`) per contract. JWT verification via `@familycare/auth-jwt` `verifyFirebaseIdToken` with an injectable `AuthVerifier` indirection for tests. R2 read/write/delete на `backup/{stableId}/v1.json`. 401/403/404/409/400/204 statuses per contract. **TODO**: switch `claims.uid` → `claims.stableId` once workers/identity/ Track B sets the custom claim. (FR-010, contracts/worker-api-v1.md)
- [x] **T655** Implemented `workers/backup/src/ratelimit.ts`: in-memory `Map<stableId+verb, timestamps[]>`, 5-minute window. POST 10/5min; GET 5/5min; DELETE 5/5min. `Retry-After` header on 429. Inline `TODO(server-roadmap SRV-RECOVERY-001 d)`. (contracts/worker-api-v1.md §Rate-limit policy)
- [x] **T656** Implemented `workers/backup/src/idempotency.ts`: in-memory `Map<stableId+key, {bodyHash, status, cachedBody}>`, TTL 24h. Same key + same body → cached 200 (no R2 write); same key + different body → 409. Body hash via `crypto.subtle.digest("SHA-256")`. (contracts/worker-api-v1.md §Idempotency)
- [x] **T657** [P] `workers/backup/test/auth.test.ts` (vitest): 6 tests — missing Authorization → 401; invalid signature → 401; subject-ownership mismatch on POST / GET → 403; ownership match GET on missing blob → 404; injection plumbing smoke. 6/0 PASS. (contracts/worker-api-v1.md §Test contracts)
- [x] **T658** [P] `workers/backup/test/idempotency.test.ts`: 2 tests — same key + same body cached (no R2 re-write); same key + different body → 409. 2/0 PASS. (contracts/worker-api-v1.md)
- [x] **T659** [P] `workers/backup/test/roundtrip.test.ts`: 5 tests — POST → GET byte-equal; DELETE then GET → 404; UNSUPPORTED_SCHEMA on v2; malformed JSON → 400; missing Idempotency-Key → 400. 5/0 PASS. (contracts/worker-api-v1.md)
- [x] **T660** [P] `workers/backup/test/ratelimit.test.ts`: 2 tests — 11th POST → 429 with Retry-After; counter resets after 5-min window. 2/0 PASS. (Q-I)
- [x] **T661** [P] `workers/backup/README.md`: quickstart (npm test, wrangler dev, AVD/physical-device routing notes), env vars table, deploy notes (owner-only). (Plan §Project Structure)

### Track B — `workers/identity/` (identity Worker)

- [ ] **T662** Scaffold `workers/identity/` TS project: `wrangler.toml` (env vars `FIREBASE_PROJECT_ID`, `FIREBASE_SERVICE_ACCOUNT_JSON` secret, dev port 8788), `package.json` (hono + `@familycare/auth-jwt` + `firebase-admin`), `tsconfig.json`. Verify dry-run deploy. (Plan §Architectural rule, Q-M variant b)
- [ ] **T663** Implement `workers/identity/src/index.ts`: single endpoint `POST /init-claim`. Body: `{ uid: string }`. Auth: Firebase JWT в Bearer (verify через `_shared/auth-jwt/`, check claims.uid == body.uid). Logic: (1) generate UUID v4 as stableId if not already in `/identity-links/{uid}/` Firestore doc; (2) call `firebase-admin.auth().setCustomUserClaims(uid, { stableId })`; (3) return `{ stableId }` to client. Idempotent — повторный call возвращает существующий stableId без re-setting claim. (Q-M variant b, FR-001 stableId provider-agnostic UUID)
- [ ] **T664** [P] `workers/identity/src/__tests__/init-claim.test.ts` (vitest + miniflare + firebase-admin mock): first call generates new UUID + sets claim; second call returns same UUID без re-set. (Q-M)
- [ ] **T665** [P] `workers/identity/README.md`: quickstart + env vars + deployment notes. (Plan §Architectural rule)

### Deployment + Android wiring + integration tests

- [ ] **T666** Deploy Track A + Track B Workers: `cd workers/backup && wrangler deploy` → record URL в `gradle.properties` `RECOVERY_BACKUP_WORKER_URL`; same для `workers/identity/` → `IDENTITY_INIT_CLAIM_WORKER_URL`. Verify endpoints reachable от curl с test JWT. (T639 dependency, deployment per contracts/worker-api-v1.md §Deployment)
- [ ] **T667** Add `BuildConfig.IDENTITY_INIT_CLAIM_WORKER_URL` to `app/build.gradle.kts` (parallel to `RECOVERY_BACKUP_WORKER_URL` from T639). Debug → `http://10.0.2.2:8788`; release → from gradle.properties. Inline TODO server-roadmap для custom domain. (Q-M variant b implementation hook)
- [ ] **T668** Add `app/src/main/kotlin/com/launcher/data/identity/InitClaimClient.kt`: OkHttp client wrapping `POST /init-claim` against `workers/identity/`. Called once by F-4 `GoogleSignInAuthAdapter` after first successful sign-in (when claims.stableId отсутствует в JWT). Idempotent — repeat calls are no-op. (Q-M)
- [ ] **T669** [deferred-local-emulator] `WorkerRecoveryKeyBackupIntegrationTest` (connectedAndroidTest): start `wrangler dev` (`workers/backup/`) on localhost:8787 → POST blob, GET back, DELETE. Requires AVD API ≤ 34. (T635 + T653-T660 dependencies)
- [ ] **T670** [deferred-local-emulator] `InitClaimClientIntegrationTest` (connectedAndroidTest): start `wrangler dev` (`workers/identity/`) on localhost:8788 → call `InitClaimClient.initClaim(uid)` → verify response stableId; second call returns same stableId. (T668 + T662-T664)

---

## Phase 5 — Migration from spec 018 ConfigCipher2

**Purpose**: existing encrypted configs (from spec 018) must read byte-equal after F-5 update. SC-004 closure.

- [ ] **T671** Refactor `ConfigCipher2` (spec 018, в `app/` или wherever it lives) to use `KeyRegistry.derive(stableId, "config")` for key material. Verify ciphertext schemaVersion в envelope не bumps (key derivation source change, not crypto change). (FR-018)
- [ ] **T672** [deferred-local-emulator] `KeyRegistryMigrationFromSpec018Test` (connectedAndroidTest): use fixture `config-ciphertext-spec018-sample.bin` (T630) → instantiate fresh AndroidKeystoreRegistry → derive config key → decrypt fixture → assert plaintext matches spec 018 fixture. (FR-018, SC-004)

---

## Phase 6 — Documentation + checklist closure + spec cleanup

**Purpose**: docs за пределами spec.md; close open checklist items from `/speckit.clarify`.

- [ ] **T673** [P] Write `docs/recovery-flow.md`: plain-Russian user-facing documentation per FR-020. Covers 4 scenarios from spec.md US-1..US-4. Senior-friendly language. (FR-020, SC-006)
- [ ] **T674** [P] Write `docs/dev/key-hierarchy.md`: developer-facing diagram (AuthIdentity.stableId → Argon2id → RootKey → HKDF → DerivedKeys) + purpose list + exit ramps inventory. (FR-021)
- [ ] **T675** [P] Update `docs/compliance/permissions-and-resource-budget.md`: document INTERNET permission usage for Worker; explicit note that NO `drive.appdata` Google scope is used. (closes security CHK018 from clarify)
- [ ] **T676** [P] Update `AndroidManifest.xml`: set `android:allowBackup="false"` on `<application>`; add `android:dataExtractionRules="@xml/data_extraction_rules"`. (closes security CHK024)
- [ ] **T677** [P] Create `app/src/main/res/xml/data_extraction_rules.xml`: explicit `<exclude>` for DataStore keys matching `recovery-attempts/*` and Keystore aliases under `key-registry/*`. (closes security CHK024)
- [ ] **T678** [P] Spec cleanup: rewrite `spec.md` FR-014 / FR-015 / FR-016 in tech-agnostic phrasing per `checklist-requirements-quality` CHK001. Composable names move to plan.md (already done). Keep behavioural anchors. (closes requirements-quality CHK001)
- [ ] **T679** [P] Spec cleanup: rewrite SC-007 в spec.md mentioning «grep fitness function» without Konsist / Detekt name; move tool name to plan.md (already done) per CHK009. (closes requirements-quality CHK009)

### Backlog AC format migration (one-time, per CLAUDE.md hybrid model)

- [ ] **T680** Update [backlog/tasks/task-6 - F-5-Root-Key-Hierarchy-Owner-Recovery.md](../../backlog/tasks/task-6%20-%20F-5-Root-Key-Hierarchy-Owner-Recovery.md) `## Acceptance Criteria` block to hybrid format: mark existing 6 AC as `[hand]`. Skill `pre-pr-backlog-sync` will add `[auto:checklist]` and `[auto:deferred-*]` rows on next invocation. (CLAUDE.md Portfolio tracker §AC hybrid model)

---

## Phase 7 — Manual gates (deferred, real-device verification)

**Purpose**: `[deferred-physical-device]` tasks — owner runs on real Xiaomi 11T. Cannot close in AI session.

- [ ] **T681** [deferred-physical-device] **SC-001 cross-device manual smoke**: install on Xiaomi 11T (device A) → setup with passphrase «correct horse battery staple» → encrypt config → verify Worker blob exists. Then on second device (or factory-reset same device): install → sign-in with same Google account → recovery screen → enter same passphrase → verify config decrypted byte-equal. Owner attests SC-001 PASS. (US-2)
- [ ] **T682** [deferred-physical-device] **SC-002 Fallback flow manual smoke**: on Xiaomi 11T after T681 → enter 5 wrong passphrases → Fallback screen → confirm twice → blob deleted from Worker → setup screen reopens under same identity. Owner attests SC-002 PASS. (US-3)
- [ ] **T683** [deferred-physical-device] **SC-005 Autofill cross-device manual smoke**: two physical devices, same Google account, GPM enabled. On A → setup (Autofill suggests save) → save. On B → install → sign-in → entry screen → Autofill auto-populates passphrase → continue. Owner attests SC-005 PASS. (US-2 scenario 4)
- [ ] **T684** [deferred-physical-device] **SC-010 Argon2 timing on real hardware**: run T643 benchmark output on Xiaomi 11T → P95 ≤ 3s. If P95 > 3s — flag to research.md R4 «moderate params» exit ramp. (SC-010)
- [ ] **T685** [deferred-physical-device] **Real Worker E2E**: T669 (integration test) against deployed `<account>.workers.dev/backup` (NOT localhost). Verifies real JWT custom-claim + Firebase production verification + R2 storage. (T669 + T666 + T654 dependencies)
- [ ] **T686** [deferred-external] **SC-006 docs/recovery-flow.md peer review**: owner reads `docs/recovery-flow.md` (T673 output) → confirms plain-Russian senior-readable. Non-developer test reader (бабушка-figure, или peer-owner) reads and paraphrases. Owner attests SC-006 PASS. (SC-006)

---

## Trace summary (FR → T mapping)

| FR | Title | Implementing tasks |
|---|---|---|
| FR-001 | Domain types in core/keys/ | T601, T602, T606-T609 |
| FR-002 | KeyRegistry port + HKDF | T612, T632, T634 |
| FR-003 | RootKeyManager port | T604, T613, T633 |
| FR-004 | RecoveryKeyBackup port | T614, T635 |
| FR-005 | AuthAvailability port | T606, T607, T615 |
| FR-006 | RecoveryKeyBackupBlob wire format | T603, T610, T611, T622-T625, T628 |
| FR-007 | Inline exit-ramp TODOs | T612, T613, T614, T635, T675 |
| FR-008 | AndroidKeystoreRegistry alias pattern | T632, T642 |
| FR-009 | Argon2RootKeyManager params + CharArray wipe | T633, T643 |
| FR-010 | WorkerRecoveryKeyBackup HTTPS impl | T635, T639, T640, T669, T685 |
| FR-011 | ~~NoOpRecoveryKeyBackup~~ removed (round-2) | T648 (DI confirms no NoOp injected) |
| FR-012 | Selector ~~removed~~ — single adapter via DI | T648 |
| FR-013 | AuthAvailability Android impl | T638, T641 |
| FR-014 | RecoveryPassphraseSetupScreen + blocking upload UX | T644, T645, T649 |
| FR-015 | RecoveryPassphraseEntryScreen + per-identity rate-limit | T636, T646, T650 |
| FR-016 | RecoveryFallbackScreen + double-confirm | T647, T651 |
| FR-017 | RecoveryViewModel + SavedStateHandle | T644, T652 |
| FR-018 | ConfigCipher2 migration byte-equal | T630, T637, T671, T672 |
| FR-019 | Identity cascade wipe on signOut | T627, T648 (event listener wiring) |
| FR-020 | docs/recovery-flow.md plain-Russian | T673, T686 |
| FR-021 | docs/dev/key-hierarchy.md developer doc | T674 |
| FR-022 | Fakes for tests + downstream | T616-T619 |
| FR-023 | Contract tests | T620-T627 |
| Worker server-side | workers/backup/ + workers/identity/ impl | T653-T665 |
| Worker deployment | wrangler deploy (in-scope) | T666 |
| Worker Android wiring | InitClaimClient + BuildConfig | T667, T668 |

| SC | Title | Verifying tasks |
|---|---|---|
| SC-001 [backlog] | Cross-device recovery byte-equal | T626 (fake), T669 (wrangler dev integration), T681 (real device) |
| SC-002 [backlog] | Fallback flow correctness | T651 (UI test), T682 (real device) |
| SC-003 [backlog] | Non-identity device local mode | T641 (mock test), T649-T651 (UI tests show explainer path) |
| SC-004 [backlog] | Migration byte-equal preserved | T672 |
| SC-005 [backlog] | Autofill cross-device sync | T683 (real device only — cannot mock GPM sync) |
| SC-006 [backlog] | docs/recovery-flow.md plain-Russian | T673, T686 |
| SC-007 | Konsist fitness function for forbidden tokens | T631 |
| SC-008 | RecoveryKeyBackupBlob provider-agnostic JSON schema | T624 |
| SC-009 | F-5 works with FakeAuthAdapter / FakePhoneAuthAdapter | T626 |
| SC-010 | Argon2 ≤ 3s P95 | T643, T684 |
| SC-011 | 5-attempt rate-limit + Fallback nav | T636, T650 |
| SC-012 | Cascade wipe namespace empty | T627 |
| SC-013 | Contract tests all green | T620-T627 |

---

## Required-task gates (Step 3 self-check)

✓ Every contract in `contracts/`:
- `recovery-key-backup-v1.md` → roundtrip (T622) + backward-compat (T623) + forward-compat (T625) + provider-agnostic (T624).
- `worker-api-v1.md` → Worker-side tests (T657-T660 vitest + miniflare) + Android integration (T669) + real-Worker E2E (T685).

✓ Every new port has a fake:
- `KeyRegistry` → `FakeKeyRegistry` (T616)
- `RootKeyManager` → `FakeRootKeyManager` (T617)
- `RecoveryKeyBackup` → `FakeRecoveryKeyBackup` (T618)
- `AuthAvailability` → `FakeAuthAvailability` (T619)

✓ New module `core/keys/` has Konsist fitness rule: T631.
✓ New TS modules `workers/backup/` + `workers/identity/` — both in-scope tasks T653-T665.

✓ Removed files (none in plan.md "DELETE" list — Phase 4 of legacy origin/020 branch — not migrated, see spec.md Notes for context).

✓ Docs impacted:
- `docs/recovery-flow.md` (NEW) → T673
- `docs/dev/key-hierarchy.md` (NEW) → T674
- `docs/compliance/permissions-and-resource-budget.md` (UPDATE) → T675
- `AndroidManifest.xml` + `res/xml/data_extraction_rules.xml` (UPDATE / NEW) → T676, T677
- `workers/backup/README.md` + `workers/identity/README.md` (NEW) → T661, T665

✓ UI features have UI test + smoke-checkpoint: T649-T652 (UI tests `[deferred-local-emulator]`) + T681-T683 (real-device smoke `[deferred-physical-device]`).

✓ Perf-sensitive features (Argon2id timing): T643 (emulator), T684 (real device).

---

## Open items (after round 3 microservice + same-task direction 2026-06-28)

1. **Q-M custom claim mechanism — resolved**: variant (b) implemented in-scope via `workers/identity/` (T662-T665). Initial endpoint `POST /init-claim` (T663). NOT bundled into `workers/backup/` per microservice boundary rule.
2. **Q-O Worker URL config — resolved**: two BuildConfig URLs — `RECOVERY_BACKUP_WORKER_URL` (T639) + `IDENTITY_INIT_CLAIM_WORKER_URL` (T667).
3. **Compose UI Test 1.8 upgrade**: deferred; `[deferred-local-emulator]` markers cover via API ≤ 34 workaround.
4. **`checklist-server-data-minimization` skill creation**: spec.md `## Notes` recommendation; not blocker, separate skills-authoring backlog item.

**Architectural rules applied (2026-06-28)**:
- One `workers/<name>/` = one future Go microservice (memory `project_workers_microservice_mapping.md`). Code structure: `workers/backup/` ≠ `workers/identity/`, separate `wrangler.toml`, separate deploys.
- One backlog task per feature (memory `feedback_one_task_per_feature.md`). TS Worker effort lives **inside** TASK-6, not split into TASK-X / TASK-Y. Code organization ≠ backlog organization.

---

## Dependencies (critical chain)

- **T601** (gradle setup) blocks all `core/keys/` tasks.
- **T610-T611** (wire format) block T622-T625 (contract tests).
- **T612-T615** (ports) block T632-T635 (Android adapters) and T644 (ViewModel).
- **T616-T619** (fakes) block any test that needs them (T626, T669 integration mode).
- **T635** (`WorkerRecoveryKeyBackup`) blocks T669, T685.
- **T653-T665** (Worker server-side) block T666 (deploy) and T669/T670 (integration tests).
- **T666** (deploy) blocks T685 (real Worker E2E).
- **T673** (docs/recovery-flow.md) blocks T686 (peer review).
- **Phase 1+2+3+5+6**: closeable in AI session against Fake adapters; do NOT depend on T653-T665 Worker code or T666 deploy.
- **Phase 7 manual gates** (`[deferred-physical-device]`): require T666 deploy + real device.

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Простыми словами (TL;DR)

**Что в этом файле.** Это план-разбивка фичи F-5 (Root Key Hierarchy) на конкретные мелкие задачи, которые AI или человек может закрывать по одной. Всего 86 задач (T601-T686), разбита на 7 фаз. **Всё в одной backlog-задаче TASK-6** — Kotlin Android + TypeScript Cloudflare Workers + docs, не split по разным backlog items (per memory `feedback_one_task_per_feature.md`).

**Что строим (быстрое напоминание):**
- Фаза 1 — чистая доменная логика на Kotlin (типы, порты, fake-адаптеры, контракт-тесты). Самая быстрая обратная связь — никакого Android, всё на JVM. ~30 задач.
- Фаза 2 — Android-адаптеры: настоящий Android Keystore, настоящий Argon2 через libsodium, настоящий DataStore. ~12 задач, частично `[deferred-local-emulator]`.
- Фаза 3 — три Compose-экрана для пользователя (setup / entry / fallback) + ViewModel. UI-тесты помечены `[deferred-local-emulator]` (нужен эмулятор API ≤ 34).
- Фаза 4 — **TS Cloudflare Workers** (`workers/backup/` blob storage + `workers/identity/` init-claim, T653-T670). **Внутри той же TASK-6** (не отдельная backlog-задача, per memory `feedback_one_task_per_feature.md`). Code structure разделена по микросервисам (per memory `project_workers_microservice_mapping.md`), но backlog единый. Без Worker'ов фазы 1+2+3 работают на fake'ах.
- Фаза 5 — миграция со старого spec 018 ConfigCipher2: проверяем что старые зашифрованные данные читаются после апгрейда. 1 миграционная задача + 1 тест.
- Фаза 6 — документация (`docs/recovery-flow.md` простым русским для бабушки, `docs/dev/key-hierarchy.md` для разработчика), закрытие checklist'ов (allowBackup в манифесте, обновление permissions docs), косметика spec.md.
- Фаза 7 — `[deferred-physical-device]` ручные прогоны на Xiaomi 11T. AI этого сделать не может — владелец прогоняет руками.

**Главные блокеры:**
1. **Worker deployment** (`workers/backup/` + `workers/identity/` через `wrangler deploy`, T666) — единственный реальный operational блокер для Phase 7 manual gates. Фаза 1-3+5+6 идут параллельно с Worker-разработкой.
2. **AVD API ≤ 34** для UI-тестов и инструментальных тестов с Keystore (см. memory `reference_compose_ui_test_api_mismatch.md`). Если в развёртке только API 35+ — тесты пометятся `[deferred-local-emulator]`, прогон через Verification status в backlog.

**Что AI закроет в одной сессии (оптимистично):** все T-задачи Фаз 1, 2 (кроме `[deferred-*]`), 3 (кроме UI-тестов), 5, 6. Это ~50 из 71 задачи. Остальные ~21 — `[deferred-*]` маркеры, требуют физических устройств / отдельных artifact'ов / владельца руками.

**Следующий шаг после tasks.md:** `/speckit.analyze` для финальной cross-artifact verification, потом `/speckit.implement` для запуска самой имплементации.
<!-- NOVICE-SUMMARY:END -->
