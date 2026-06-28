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
- [x] **T614** [P] [US-1, US-2, US-3] Add `RecoveryKeyBackup.kt` port: `uploadBlob()`, `fetchBlob()`, `deleteBlob()`. Inline TODO comment per FR-007 (SRV-RECOVERY-001 exit ramp на own-server). (FR-004)
- [x] **T615** [P] [US-1, US-4] Add `AuthAvailability.kt` port: `check(): AuthAvailabilityStatus`. (FR-005)

### Fake adapters (commonTest/fakes/)

- [x] **T616** [P] Add `FakeKeyRegistry.kt`: in-memory Map<StableId, Map<String, DerivedKey>>; deterministic derivation (SHA-256 of stableId+purpose for test material). (FR-022, CLAUDE.md rule 6)
- [x] **T617** [P] Add `FakeRootKeyManager.kt`: stateful Flow current; in-memory passphrase-blob storage; deterministic Argon2-stub. (FR-022)
- [x] **T618** [P] Add `FakeRecoveryKeyBackup.kt`: in-memory Map<StableId, RecoveryKeyBackupBlob>; shared between test instances для cross-device test. (FR-022)
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

- [ ] **T632** Add `core/keys/src/androidMain/kotlin/family/keys/impl/AndroidKeystoreRegistry.kt`: implements `KeyRegistry` via `core/crypto` `SecureKeystore` port. Alias pattern `key-registry/{stableId}/{purpose}`. StrongBox hint when API ≥ 28. (FR-008, plan §Project Structure)
- [ ] **T633** Add `core/keys/src/androidMain/kotlin/family/keys/impl/Argon2RootKeyManager.kt`: implements `RootKeyManager` via `core/crypto` `KeyDerivation` port (libsodium Argon2id). Params from KdfParams (iterations=3, memoryKb=65536, parallelism=1). Wipes `CharArray` after derivation. (FR-009, research.md R4)
- [ ] **T634** Add `core/keys/src/androidMain/kotlin/family/keys/impl/DeviceKeyNamespaceProvider.kt`: random UUID at first launch, stored under Keystore alias `device-root-key`, для US-4 local-mode device-key namespace. (FR-002 device-key namespace path)
- [ ] **T635** Add `app/src/main/kotlin/com/launcher/data/recovery/WorkerRecoveryKeyBackup.kt`: implements `RecoveryKeyBackup` via OkHttp client → `BuildConfig.RECOVERY_BACKUP_WORKER_URL`. Bearer JWT from F-4 `SessionStore`. Idempotency-Key UUID v4. 3 retries with exponential back-off на network failure. Maps HTTP statuses to BackupError. (FR-010, contracts/worker-api-v1.md)
- [ ] **T636** [P] Add `app/src/main/kotlin/com/launcher/data/recovery/DataStorePassphraseAttemptCounter.kt`: DataStore-backed per-identity counter `recovery-attempts/{stableId}`. Survives process kill; cleared on Fallback wipe. (FR-015, SC-011)
- [ ] **T637** [P] Add `app/src/main/kotlin/com/launcher/data/recovery/DataStoreSchemaVersionMemory.kt`: tracks last-seen schemaVersion for FR-018 byte-equal migration detection. (FR-018)
- [ ] **T638** Add `app/src/main/kotlin/com/launcher/data/recovery/AuthAvailabilityAndroidImpl.kt`: implements `AuthAvailability` via F-4 `AuthAdapterSelector.pickAdapter()`. Maps `NoSupportedProvider` from F-4 to domain `AvailabilityReason`. (FR-013)
- [ ] **T639** Add `BuildConfig.RECOVERY_BACKUP_WORKER_URL` в `app/build.gradle.kts`: debug → `http://10.0.2.2:8787` (host через emulator); release → `${RECOVERY_BACKUP_WORKER_URL}` from gradle.properties. Inline TODO server-roadmap для custom domain. (clarify Q-O)

### Adapter tests (where unit-testable on JVM)

- [ ] **T640** [P] `WorkerRecoveryKeyBackupTest` (JVM): MockWebServer для OkHttp; tests for happy-path, 401, 403, 409, 429, 507, network timeout (3 retry). (FR-010, plan §Test Strategy)
- [ ] **T641** [P] `AuthAvailabilityAndroidImplTest` (JVM): мокирует F-4 AuthAdapterSelector; проверяет mapping ошибок. (FR-013)

### Adapter tests requiring Android runtime

- [ ] **T642** [deferred-local-emulator] `AndroidKeystoreRegistryTest` (connectedAndroidTest): real Keystore, derivation determinism + isolation cross-restart. Requires AVD API ≤ 34. (FR-008, memory `reference_compose_ui_test_api_mismatch`)
- [ ] **T643** [deferred-physical-device] `Argon2BenchmarkTest` (androidx.benchmark): timing P50/P95/P99 на Xiaomi 11T. Target SC-010 ≤ 3s P95. Owner runs manually. (SC-010, memory `reference_testing_environment`)

---

## Phase 3 — Compose UI screens (3 screens + ViewModel)

**Purpose**: 3 user-facing screens for setup / entry / fallback. UI tests `[deferred-local-emulator]` (API ≤ 34 due to composeUiTest 1.7.x).

- [ ] **T644** Add `app/src/main/kotlin/com/launcher/ui/recovery/RecoveryViewModel.kt`: subscribes to `AuthProvider.currentUser`, drives 3-screen state-machine, holds passphrase CharArray ephemerally, SavedStateHandle for process death survival. (FR-017)
- [ ] **T645** [US-1] Add `RecoveryPassphraseSetupScreen.kt` Composable: two password fields, ContentType.NewPassword autofill hint, blocking upload with spinner + retry-with-confirm dialog (FR-014 Q-C resolution). Senior-safe styling (≥ 18sp, ≥ 56dp tap targets, ≥ 4.5:1 contrast). Neutral copy. (FR-014, US-1 acceptance)
- [ ] **T646** [US-2] Add `RecoveryPassphraseEntryScreen.kt` Composable: single password field, ContentType.Password autofill hint, attempt counter + auto-nav to Fallback after 5 fails. Slow-device progress spinner. (FR-015, US-2 acceptance)
- [ ] **T647** [US-3] Add `RecoveryFallbackScreen.kt` Composable: explainer text + destructive button styling + dialog двойного подтверждения. (FR-016, US-3 acceptance)
- [ ] **T648** Add `app/src/main/kotlin/com/launcher/di/KeysModule.kt`: DI bindings — single `WorkerRecoveryKeyBackup` (no Selector, no NoOp per round-2 owner pushback). `FakeRecoveryKeyBackup` for debug/test flavor. (Plan §Project Structure, FR-011 / FR-012 simplification)

### UI tests

- [ ] **T649** [P] [deferred-local-emulator] `RecoveryPassphraseSetupScreenTest` (composeUiTest): valid passphrase enables confirm; mismatch / < 8 chars disables; upload progress shown; retry dialog on 3 failures. (FR-014)
- [ ] **T650** [P] [deferred-local-emulator] `RecoveryPassphraseEntryScreenTest`: counter increments per wrong attempt; auto-nav to Fallback after 5. (FR-015, SC-011)
- [ ] **T651** [P] [deferred-local-emulator] `RecoveryFallbackScreenTest`: button → dialog → confirm → wipe sequence; cancel в dialog не destruct'ит. (FR-016)
- [ ] **T652** [P] [deferred-local-emulator] `RecoveryViewModelStateTest`: process kill via SavedStateHandle replay → state восстанавливается. (FR-017)

---

## Phase 4 — `workers/backup/` + `workers/identity/` Cloudflare Worker implementation (in-scope)

**Purpose**: implement two Workers within TASK-6 scope (per owner direction 2026-06-28 «делаем в этой же таске»). Code structure follows microservice boundaries (`workers/backup/` ≠ `workers/identity/`, memory `project_workers_microservice_mapping.md`), но backlog tracking — единый TASK-6 (memory `feedback_one_task_per_feature.md`).

> **Phase 4 blocks integration tests** but **does NOT block** Phase 1/2/3 (use Fake adapters). Phases can proceed in parallel — Android side (Kotlin) против Fakes, TS Worker side в параллель.

### Track A — `workers/backup/` (blob storage Worker)

- [ ] **T653** Scaffold `workers/backup/` TS project: create `wrangler.toml` (R2 binding `RECOVERY_BLOBS`, env vars `FIREBASE_PROJECT_ID`, dev port 8787), `package.json` (hono ~14KB router, `@familycare/auth-jwt` workspace dep, vitest + miniflare for tests), `tsconfig.json`, `.gitignore`. Verify: `cd workers/backup && npm install && wrangler deploy --dry-run` succeeds. (Plan §Project Structure, contracts/worker-api-v1.md §Deployment)
- [ ] **T654** Implement `workers/backup/src/index.ts`: 3 endpoints (POST `/backup`, GET `/backup/:stableId`, DELETE `/backup/:stableId`) per [contracts/worker-api-v1.md](./contracts/worker-api-v1.md). JWT verification через `workers/_shared/auth-jwt/`. R2 read/write/delete для blob path `backup/{stableId}/v1.json`. 401/403/404 statuses per contract. (FR-010, contracts/worker-api-v1.md)
- [ ] **T655** Implement `workers/backup/src/ratelimit.ts`: in-memory Map<stableId, attempt[]>, 5-minute window. POST: 10/5min; GET: 5/5min; DELETE: 5/5min. Returns `Retry-After` header on 429. Inline TODO `// TODO(server-roadmap, SRV-RECOVERY-001 d): switch to persistent counter at own-server cutover`. (Q-I partial-resolution, contracts/worker-api-v1.md §Rate-limit policy)
- [ ] **T656** Implement `workers/backup/src/idempotency.ts`: in-memory Map<stableId+idempotencyKey, cachedResponseHash>, TTL 24h. POST с тем же key + same body → cached 200; same key + different body → 409. (contracts/worker-api-v1.md §Idempotency)
- [ ] **T657** [P] `workers/backup/src/__tests__/auth.test.ts` (vitest + miniflare): JWT signature verification (valid + expired + tampered); claims.stableId match (matching + mismatching). (contracts/worker-api-v1.md §Test contracts)
- [ ] **T658** [P] `workers/backup/src/__tests__/idempotency.test.ts`: dedup same body → 200; different body → 409. (contracts/worker-api-v1.md)
- [ ] **T659** [P] `workers/backup/src/__tests__/roundtrip.test.ts`: POST → GET → DELETE round-trip via miniflare R2 mock. 404 на non-existent stableId. (contracts/worker-api-v1.md)
- [ ] **T660** [P] `workers/backup/src/__tests__/ratelimit.test.ts`: 11-й POST в 5min → 429; counter сбрасывается через 5min. (Q-I)
- [ ] **T661** [P] `workers/backup/README.md`: quickstart (wrangler dev + test commands), env vars, deploy step. (Plan §Project Structure)

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
