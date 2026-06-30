# Implementation Plan: F-5 — Root Key Hierarchy + Owner Recovery

**Branch**: `task-6-root-key-hierarchy-recovery` | **Date**: 2026-06-28 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/task-6-root-key-hierarchy-recovery/spec.md`
**Backlog**: [TASK-6](../../backlog/tasks/task-6%20-%20F-5-Root-Key-Hierarchy-Owner-Recovery.md)

## Summary

F-5 строит **root key hierarchy** на identity'е из F-4 `AuthProvider` port'а: `AuthIdentity.stableId` (provider-agnostic UUID) + passphrase → Argon2id KDF → root key → HKDF-SHA256 derived keys per purpose (`"config"`, `"contacts"`, `"photos"`). Recovery работает через **наш** Cloudflare Worker (`workers/backup/`, R2 storage, Firebase JWT auth с custom claim `stableId`) — **не** Google Drive App Data; см. [spec.md §«Архитектурное решение про backup storage»](./spec.md). Adapter swap на own-server (PostgreSQL per SRV-RECOVERY-001) — single-file change.

Технический подход: новый KMP module `core/keys/` (~12 файлов в commonMain) поверх F-CRYPTO примитивов из TASK-51 (`AeadCipher`, `KeyDerivation`, libsodium) и F-4 `AuthProvider`. Domain ports (`KeyRegistry`, `RootKeyManager`, `RecoveryKeyBackup`, `AuthAvailability`) в `commonMain`, MVP-adapters (`AndroidKeystoreRegistry`, `Argon2RootKeyManager`, `WorkerRecoveryKeyBackup`) в `androidMain` + `app/`. Новый TS Cloudflare Worker `workers/backup/` — отдельный artifact (TASK-X, см. open items). Migration от spec 018 `ConfigCipher2` — byte-equal ciphertext preserved через переход на `KeyRegistry.derive(stableId, "config")` без bump'а envelope schemaVersion.

## Technical Context

**Language/Version**: Kotlin Multiplatform 2.0+ (соответствует core/crypto из TASK-51, core/auth из spec 017). TypeScript для Cloudflare Worker (`workers/backup/`).
**Primary Dependencies**:
- **Kotlin side**: F-CRYPTO ports (`AeadCipher`, `KeyDerivation`, `SecureKeystore`) из TASK-51 `core/crypto`; F-4 ports (`AuthProvider`, `AuthIdentity`, `SessionStore`) из spec 017 `core/auth`; ionspin libsodium-kmp **через** core/crypto (не напрямую); OkHttp 5+ для HTTPS клиента в `WorkerRecoveryKeyBackup`; kotlinx-serialization JSON для wire-format; AndroidX Credentials для Autofill metadata.
- **Worker side**: hono или vanilla `fetch` handler; `@familycare/auth-jwt` (`workers/_shared/auth-jwt/`) для Firebase JWT verification; Cloudflare R2 binding; firebase-admin для custom claim setting (вариант (i) per Q-M).
**Storage**:
- Android Keystore (через `SecureKeystore` из core/crypto): root key + derived keys под alias pattern `key-registry/{stableId}/{purpose}`. Hardware-backed StrongBox (API 28+), software fallback на older.
- DataStore Preferences: per-identity rate-limit counter (`recovery-attempts/{stableId}`), schema-version memory, `recoveryBackupDeferred` flag (FR-014).
- Cloudflare R2 (через Worker): `RecoveryKeyBackupBlob` JSON, path `backup/{stableId}/v1.json`.
**Testing**:
- JVM unit tests (primary): `kotlinx-coroutines-test`, Turbine для Flow, FakeAdapters (`FakeKeyRegistry`, `FakeRootKeyManager`, `FakeRecoveryKeyBackup`, `FakeAuthAvailability`).
- Contract tests в `commonTest`: roundtrip, backward-compat, provider-agnostic, derivation determinism, isolation.
- Instrumented tests в `app/androidTest`: real `AndroidKeystoreRegistry`, real `Argon2RootKeyManager`, Compose UI tests для 3 screens. `[deferred-local-emulator]` для composeUiTest 1.7.x API 35+ mismatch (см. memory `reference_compose_ui_test_api_mismatch.md`); требуется AVD API ≤34.
- Worker integration tests: `wrangler dev` localhost + Android клиент через `BuildConfig.RECOVERY_BACKUP_WORKER_URL=http://localhost:8787`; против deployed `*.workers.dev` — `[deferred-physical-device]` test'ы.
**Target Platform**: Android API 26+ (Autofill API, Argon2 через libsodium); iOS KMP target declared inactive (consistent с F-CRYPTO).
**Project Type**: KMP library module (`core/keys/`) + new TS Cloudflare Worker (`workers/backup/`, отдельный artifact / отдельный backlog item).
**Performance Goals**:
- SC-010: Argon2id derivation ≤ 3s P95 на эмуляторе API 34 (interactive params 64 MiB / 3 iter / 1 par).
- HKDF derivation: sub-ms (libsodium `crypto_kdf_*`).
- SEQ-1 upload latency: 200-500ms typical к Worker (Cloudflare global edge); timeout 30s, 3 retry с back-off.
- SEQ-2 fetch latency: 200-500ms typical; cold-start Argon2id + HKDF + decrypt < 5s total на target hardware (Pixel 5).
- Cold start не блокируется: `RootKeyManager` lazy-init при первом вызове setup/recovery flow, не при app launch.
**Constraints**:
- Никаких libsodium-импортов вне `core/crypto` (rule 2 ACL).
- Никаких Firebase / Google / Cloudflare-импортов в `core/keys/commonMain` (rule 1, SC-007 fitness function).
- Никакого plaintext passphrase в RAM longer than necessary: `CharArray` обнуляется сразу после Argon2id derivation (FR-009 inheritor from spec 018).
- **`android:allowBackup="false"`** на app-manifest + `data_extraction_rules.xml` с явным exclude для DataStore `recovery-attempts/*` и Keystore aliases (closes security CHK024).
- **`docs/compliance/permissions-and-resource-budget.md` MUST be updated** до merge со списком permissions: `INTERNET` (already present для F-CRYPTO/F-4); никакой `drive.appdata` scope (closes security CHK018 — был open в clarify когда Drive ещё был в дизайне; теперь moot).
- Server-side data minimization per [constitution.md Article XIV §7](../../.specify/memory/constitution.md#article-xiv) (added 2026-06-28): opaque routing, ciphertext-only blobs, no cross-user correlation surface, worst-case-provider assumption applied to Worker access logs.
**Scale/Scope**: 3 purposes в `KeyRegistry` для MVP (`config` / `contacts` / `photos`), additive до ~5-7 без architecture change (Q-G plain-string approach); single-identity per device (multi-identity isolation per spec 017 FR-031); ~12 файлов в `core/keys/commonMain` + ~5 в `core/keys/androidMain` + ~6 Compose в `app/` + ~6 test fixtures + ~10 файлов в `workers/backup/`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

**Gate 1 — Architecture (Article III)**: PASS. Новый KMP module `core/keys/` с ports в `commonMain` и Android adapters в `androidMain`. Никаких Firebase / libsodium / Cloudflare / Android-system типов в `commonMain` API (SC-007 fitness function через Konsist в plan-tasks). Adapter swap (Worker → own-server) не трогает `commonMain`.

**Gate 2 — Core / System Integration (Article IV)**: PASS. F-5 не вводит новых системных взаимодействий — переиспользует F-CRYPTO `SecureKeystore` (Android Keystore TEE), F-4 `AuthProvider` (identity + JWT). HTTPS клиент к Worker'у — стандартный OkHttp, не системная интеграция. Никаких новых broadcasts / services / content providers.

**Gate 3 — Configuration (Article VII)**: N/A. F-5 — foundation infrastructure, не user-facing configuration. Wire-format `RecoveryKeyBackupBlob` имеет `schemaVersion` от первого коммита (rule 5).

**Gate 4 — Required Context Review (Article XVI)**: PASS. Прочитаны:
- `.specify/memory/constitution.md` Articles I-XIX, особенно Article XIV §7 (added 2026-06-28).
- `CLAUDE.md` rules 1-16, особенно rule 1 (domain isolation), rule 2 (ACL), rule 3 (one-way doors), rule 5 (wire-format versioning), rule 6 (mock-first), rule 8 (server-roadmap).
- Spec 016 (F-CRYPTO, TASK-51) — ports `AeadCipher`, `KeyDerivation`, `SecureKeystore`.
- Spec 017 (F-4, TASK-3) — ports `AuthProvider`, `AuthIdentity`, `SessionStore`.
- Spec 018 (F-5b, TASK-4) — `ConfigCipher2` integration point, FR-018 byte-equal migration.
- Spec 019 (F-5c, TASK-5) — `workers/push/` precedent для Worker pattern, JWT verification reuse.
- Decision 2026-06-15 (deferred-cloud) — Sign-In откладывается до первого cloud action.
- Decision 2026-05-30 (F-4 identity) — `stableId` UUID provider-agnostic.
- ADR-011 (AI-owner conventions) — sequence inline в spec.md, MENTOR-DETAIL для owner.
- Memory: `reference_compose_ui_test_api_mismatch.md`, `reference_testing_environment.md`, `feedback_exit_ramps_as_todos.md`, `project_qr_pairing_trust_primitive.md` (релевантно future S-2).
- `docs/dev/server-roadmap.md` SRV-RECOVERY-001 (updated 2026-06-28), SRV-CRYPTO-PARAMS-REVIEW.

**Gate 5 — Accessibility (Article VIII)**: PASS для F-5 scope. Три Compose-экрана (`RecoveryPassphraseSetupScreen` / `RecoveryPassphraseEntryScreen` / `RecoveryFallbackScreen`) — Material 3 поля с `autofillHints` (`ContentType.NewPassword` / `ContentType.Password`), tap target ≥ 56dp (senior-safe override), TalkBack labels на полях и spinner'ах. UI не показывает passphrase plaintext (inherits FR-013a из spec 018 pattern). Детали валидируются через `checklist-accessibility` + `checklist-elderly-friendly` на tasks-этапе.

**Gate 6 — Battery / Performance (Article IX)**: PASS. Argon2id interactive params (64 MiB / 3 iter / 1 par) запускается **только** при setup/recovery (редко), не на каждый push / app launch. HKDF derivation sub-ms. Worker upload — single HTTPS request per setup (with up to 3 retry). Никаких background workers, broadcasts, polling. Lazy-init `RootKeyManager` не блокирует cold start. Per-identity rate-limit counter в DataStore — небольшой DataStore write per attempt, не критично для battery.

**Gate 7 — Testing (Article XII)**: PASS. Каждый port имеет fake-adapter в `commonTest` (`FakeKeyRegistry`, `FakeRootKeyManager`, `FakeRecoveryKeyBackup`, `FakeAuthAvailability`). Каждый wire-format (`RecoveryKeyBackupBlob`) имеет roundtrip-тест + backward-compat-тест с fixture v1 (rule 7). Integration test через `wrangler dev` для real Worker. JVM unit tests как primary mode. Provider-agnostic тест (`RootKeyManagerProviderAgnosticTest`) — fitness function для US-6 / SC-009.

**Gate 8 — Simplicity (Article XI, CLAUDE.md rule 4)**: PASS с обоснованием.
- `KeyRegistry` с map purpose→DerivedKey оправдан: уже известны 3 consumer'а (`ConfigCipher2`, future `ContactsCipher`, future `PhotoCipher`), удаление абстракции = rewrite, не addition (rule 4 test 1).
- `RecoveryKeyBackup` port + single MVP adapter (`WorkerRecoveryKeyBackup`) оправдан: exit ramp к own-server (`HttpRecoveryBackupStorage`) задокументирован в server-roadmap SRV-RECOVERY-001, swap — один файл. Удаление port'а делает swap = rewrite.
- `AuthAvailability` port + single MVP adapter оправдан: capability detection критична для US-4 (non-GMS), потенциально приедет EmailPassword / Phone adapter — multi-impl required.
- ~~`NoOpRecoveryKeyBackup`~~ удалён (per round-2 owner pushback 2026-06-28) — это и есть применение rule 4: удалена abstraction которая создавала false sense of «recovery works».
- `RecoveryKeyBackupSelector` ~~удалён~~ (round 2) — capability detection полностью переехала в `AuthAvailability`.

**Article XIV (Security & Privacy) — central tenet feature.** §7 (server-side data minimization, added 2026-06-28) применяется по 5 клаузам (см. spec.md §«Privacy / data minimization — design note»). На plan-этапе фиксируем: (a) opaque `stableId` UUID в Worker'е, (b) ciphertext + KDF params only в blob'е, (c) `/backup/` endpoint не вводит cross-user correlation, (d) Cloudflare access logs minimal, (e) Worker дизайн assumes Cloudflare видит every request. MASVS-аудит — через `checklist-security` после tasks-этапа.

<!-- TODO(security-review-cadence, inherits H-5 from spec 018): review Argon2id params (memory/iterations/parallelism) каждые 2 года против OWASP актуальных рекомендаций; wire-format `kdfParams` поле в RecoveryKeyBackupBlob v1 поддерживает upgrade без breaking change (FR-006). Next review due: 2028-06. См. SRV-CRYPTO-PARAMS-REVIEW в server-roadmap. -->

**Constitution Check verdict**: PASS на всех 8 gates. Никаких violations требующих Complexity Tracking justification.

## Project Structure

### Documentation (this feature)

```text
specs/task-6-root-key-hierarchy-recovery/
├── spec.md              # /speckit.specify + clarify + scenarios output (✓ done)
├── plan.md              # This file (/speckit.plan output)
├── research.md          # Phase 0 output (/speckit.plan output)
├── data-model.md        # Phase 1 output (/speckit.plan output)
├── quickstart.md        # Phase 1 output (/speckit.plan output)
├── contracts/
│   ├── recovery-key-backup-v1.md   # closes wire-format CHK018
│   └── worker-api-v1.md            # HTTP endpoint contract for workers/backup/
├── checklists/          # already exists from /speckit.clarify
│   ├── _overview.md
│   ├── requirements-quality.md
│   ├── meta-minimization.md
│   ├── dev-experience.md
│   ├── wire-format.md
│   ├── domain-isolation.md
│   ├── security.md
│   ├── failure-recovery.md
│   ├── backend-substitution.md
│   └── device-self-sufficiency.md
└── tasks.md             # Phase 2 output (NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
core/keys/                                          # KMP module — existing from origin/020 legacy, rewritten
├── build.gradle.kts                                # KMP config (android + jvm + iosX64/Arm64 targets; depends on core/crypto, core/auth)
├── src/commonMain/kotlin/family/keys/
│   ├── api/
│   │   ├── KeyRegistry.kt                          # port: derive(stableId, purpose) / wipeAll / list
│   │   ├── RootKeyManager.kt                       # port: current Flow / create / recover / forget
│   │   ├── RecoveryKeyBackup.kt                    # port: uploadBlob / fetchBlob / deleteBlob
│   │   ├── AuthAvailability.kt                     # port: check() → Available / Unavailable(reason)
│   │   ├── AuthAvailabilityStatus.kt               # sealed: Available | Unavailable(reason: AvailabilityReason)
│   │   ├── AvailabilityReason.kt                   # enum: NoSupportedProvider | KeystoreLocked | NetworkUnreachable
│   │   ├── RootKey.kt                              # opaque value (wraps 32-byte material)
│   │   ├── DerivedKey.kt                           # opaque value (wraps HKDF output)
│   │   ├── RecoveryKeyBackupBlob.kt                # wire-format data class + schemaVersion=1 + KdfParams
│   │   ├── KdfParams.kt                            # value: algorithm="Argon2id" + iterations + memoryKb + parallelism
│   │   ├── StableId.kt                             # type alias = String (UUID, provider-agnostic)
│   │   ├── RootKeyError.kt                         # sealed: WrongPassphrase | CorruptedBlob | NoKeystore | NoIdentity
│   │   └── BackupError.kt                          # sealed: NetworkUnavailable | AuthExpired | ServerQuotaExceeded | Conflict
│   └── impl/
│       ├── RootKeyManagerImpl.kt                   # Argon2id derive → wrap → store; recover = unwrap from blob
│       ├── KeyRegistryImpl.kt                      # HKDF-SHA256 derive поверх SecureKeystore namespace
│       └── RecoveryBlobCodec.kt                    # JSON serialization (kotlinx.serialization) + base64 fields
├── src/commonTest/kotlin/family/keys/
│   ├── RootKeyManagerProviderAgnosticTest.kt       # US-6 / SC-009 fitness function
│   ├── KeyRegistryDerivationDeterminismTest.kt     # SC-013 contract test
│   ├── KeyRegistryIsolationTest.kt                 # SC-013 contract test
│   ├── RecoveryKeyBackupBlobRoundtripTest.kt       # SC-013 contract test
│   ├── RecoveryKeyBackupBlobBackwardCompatTest.kt  # SC-013 contract test
│   ├── RecoveryKeyBackupBlobProviderAgnosticTest.kt # SC-008 contract test
│   ├── RootKeyForgetFlowTest.kt                    # FR-019 cascade wipe
│   └── fakes/
│       ├── FakeKeyRegistry.kt
│       ├── FakeRootKeyManager.kt
│       ├── FakeRecoveryKeyBackup.kt                # in-memory Map<StableId, RecoveryKeyBackupBlob>
│       └── FakeAuthAvailability.kt
├── src/commonTest/resources/fixtures/
│   ├── recovery-blob-v1-sample.json
│   └── config-ciphertext-spec018-sample.bin        # для FR-018 byte-equal migration test
└── src/androidMain/kotlin/family/keys/impl/
    ├── AndroidKeystoreRegistry.kt                  # SecureKeystore impl: alias key-registry/{stableId}/{purpose}
    ├── Argon2RootKeyManager.kt                     # libsodium через KeyDerivation port из core/crypto
    └── DeviceKeyNamespaceProvider.kt               # для US-4 local-mode (random UUID на свежем запуске)

app/src/main/kotlin/com/launcher/data/recovery/
├── WorkerRecoveryKeyBackup.kt                      # OkHttp client → workers/backup/, JWT в Bearer
├── DataStorePassphraseAttemptCounter.kt            # per-identity rate-limit (FR-015)
├── DataStoreSchemaVersionMemory.kt                 # для backward-compat detection (FR-018 migration)
└── AuthAvailabilityAndroidImpl.kt                  # consults AuthAdapterSelector from F-4

app/src/main/kotlin/com/launcher/ui/recovery/
├── RecoveryPassphraseSetupScreen.kt                # FR-014 (Compose)
├── RecoveryPassphraseEntryScreen.kt                # FR-015
├── RecoveryFallbackScreen.kt                       # FR-016
└── RecoveryViewModel.kt                            # FR-017 (SavedStateHandle)

app/src/main/kotlin/com/launcher/di/
└── KeysModule.kt                                   # bindings (single WorkerRecoveryKeyBackup, no NoOp adapter, no Selector)

app/src/androidTest/kotlin/com/launcher/ui/recovery/
├── RecoveryPassphraseSetupScreenTest.kt            # [deferred-local-emulator] composeUiTest API ≤34 required
├── RecoveryPassphraseEntryScreenTest.kt            # [deferred-local-emulator]
├── RecoveryFallbackScreenTest.kt                   # [deferred-local-emulator]
└── KeyRegistryMigrationFromSpec018Test.kt          # FR-018 byte-equal roundtrip via real AndroidKeystoreRegistry

workers/backup/                                     # NEW Cloudflare Worker — separate artifact (TASK-X)
├── wrangler.toml                                   # R2 binding, env vars
├── package.json                                    # hono / @familycare/auth-jwt
├── src/
│   ├── index.ts                                    # POST/GET/DELETE handlers
│   ├── ratelimit.ts                                # in-memory rate-limit (Q-I MVP)
│   ├── claims.ts                                   # custom claim setting via firebase-admin (Q-M variant i)
│   └── env.ts
├── src/__tests__/
│   ├── auth.test.ts                                # JWT verification + claim check
│   ├── idempotency.test.ts
│   └── roundtrip.test.ts                           # POST → GET → DELETE
└── README.md

docs/
├── recovery-flow.md                                # NEW — plain-Russian user-facing doc (FR-020)
├── dev/
│   ├── key-hierarchy.md                            # NEW — developer-facing (FR-021)
│   └── server-roadmap.md                           # UPDATED (already done 2026-06-28 — SRV-RECOVERY-001)
└── compliance/
    └── permissions-and-resource-budget.md          # UPDATED — INTERNET permission usage for Worker (closes security CHK018)

AndroidManifest.xml                                 # UPDATED — android:allowBackup="false" (closes security CHK024)
res/xml/data_extraction_rules.xml                   # NEW — exclude DataStore recovery-attempts/* and Keystore aliases
```

**Structure Decision**:
- **KMP module `core/keys/`** for domain isolation (rule 1) — ports stay in `commonMain` even though only Android adapters ship (iOS targets declared inactive per F-CRYPTO precedent).
- **Worker artifact `workers/backup/`** parallel to existing `workers/push/` (per owner direction 2026-06-28 — one worker per feature, not consolidated). Separate `wrangler.toml`, separate deploy.
- **No new `workers/recovery-rate/`** — in-memory rate-limit lives inside `workers/backup/` for MVP (Q-I partial resolution); persistent counter moves to own-server PostgreSQL per SRV-RECOVERY-001.

## Phase 0 — Research

See [research.md](./research.md). One-way door decisions:
- **R1**: Storage backend for `RecoveryKeyBackup` MVP adapter — **Cloudflare Worker + R2** (chosen over Drive App Data, Firestore, KV).
- **R2**: JWT custom claim mechanism — **Firebase Admin SDK setCustomUserClaims via worker** at first sign-in (variant (i) per Q-M).
- **R3**: KDF primitive for `KeyRegistry.derive` — **HKDF-SHA256** (RFC 5869) via libsodium `crypto_kdf_*`.
- **R4**: Argon2id parameters — interactive params **iterations=3 / memoryKb=65536 / parallelism=1** (OWASP 2024 recommendation; review cadence 2 years per SRV-CRYPTO-PARAMS-REVIEW).
- **R5**: Wire-format serialization — **JSON** via kotlinx-serialization (chosen over CBOR/Protobuf; ~30% byte overhead vs CBOR acceptable for one-time blob).
- **R6**: ~~`NoOpRecoveryKeyBackup`~~ adapter — **rejected** (owner pushback round 2); single Worker adapter works on all network-reachable devices.

## Phase 1 — Design artifacts

- [data-model.md](./data-model.md) — every new domain type with attributes, invariants, lifetimes.
- [contracts/recovery-key-backup-v1.md](./contracts/recovery-key-backup-v1.md) — wire-format spec (closes wire-format CHK018 from clarify).
- [contracts/worker-api-v1.md](./contracts/worker-api-v1.md) — HTTP contract between Android client and `workers/backup/`.
- [quickstart.md](./quickstart.md) — `wrangler dev` + `./gradlew :core:keys:test` + integration test recipe.

## Dependency Impact

**New Gradle deps** (justified per Article XIII):
- `com.squareup.okhttp3:okhttp:5.x` — HTTPS клиент в `WorkerRecoveryKeyBackup`. App уже использует OkHttp в `app/src/realBackend/` (F-5c push trigger), новой зависимости фактически нет — переиспользование.
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.6+` — wire-format. Уже в проекте (spec 018 ConfigCipher2).
- Никаких новых vendor SDK не вводится. libsodium-kmp — через core/crypto (TASK-51), не напрямую.

**New Worker deps** (`workers/backup/package.json`):
- `hono` (~14 KB) — routing framework для Worker'а; используется уже в `workers/push/`.
- `firebase-admin` — для custom claim setting (variant (i) per Q-M). Альтернатива (ii) — Worker верит claim из body + signature — отказались ради security.
- `@familycare/auth-jwt` (workspace dependency, уже в `workers/_shared/`).

**Existing deps reused**: F-CRYPTO `AeadCipher` / `KeyDerivation` / `SecureKeystore` (TASK-51); F-4 `AuthProvider` / `SessionStore` (spec 017); AndroidX Credentials (autofill metadata, уже в spec 017); DataStore Preferences (уже в проекте).

## Test Strategy

Per CLAUDE.md rule 6 (mock-first) + rule 7 (fitness functions) + spec FR-022/023.

| Layer | Test type | Tooling | Examples |
|---|---|---|---|
| Domain port contract | Roundtrip + backward-compat | kotlinx-coroutines-test, JVM | `RecoveryKeyBackupBlobRoundtripTest`, `RecoveryKeyBackupBlobBackwardCompatTest` |
| Domain port contract | Determinism + isolation | JVM | `KeyRegistryDerivationDeterminismTest`, `KeyRegistryIsolationTest` |
| Domain port contract | Provider-agnostic (fitness) | JVM + FakeAuthAdapter | `RootKeyManagerProviderAgnosticTest`, `RecoveryKeyBackupBlobProviderAgnosticTest` |
| Fitness function | grep core/keys/ for forbidden tokens | Konsist | Konsist rule «no Google/Firebase/OAuth/Apple/Phone/Email/Sub/IdToken in core/keys/src/commonMain/» |
| Adapter | KeyRegistry Android impl | connectedAndroidTest | `AndroidKeystoreRegistryTest` (real Keystore, may need API ≤34 — `[deferred-local-emulator]` if composeUiTest API mismatch) |
| Adapter | WorkerRecoveryKeyBackup HTTP roundtrip | connectedAndroidTest + `wrangler dev` localhost | `WorkerRecoveryKeyBackupIntegrationTest` |
| Migration | spec 018 ciphertext byte-equal preserved | connectedAndroidTest | `KeyRegistryMigrationFromSpec018Test` (FR-018) |
| UI | 3 Compose screens | composeUiTest | `RecoveryPassphraseSetupScreenTest` / `EntryScreenTest` / `FallbackScreenTest` — все `[deferred-local-emulator]` per `reference_compose_ui_test_api_mismatch` memory (API ≤34 AVD required) |
| Performance | Argon2id timing benchmark | androidx.benchmark | `Argon2BenchmarkTest` — `[deferred-physical-device]` Xiaomi 11T для realistic timing |
| Worker | JWT verification + claim check | vitest + miniflare | `auth.test.ts` |
| Worker | Idempotency-Key dedup | vitest | `idempotency.test.ts` |
| Worker | POST→GET→DELETE roundtrip | vitest + miniflare R2 mock | `roundtrip.test.ts` |
| Real Worker E2E | Android client → deployed `workers.dev` | `[deferred-physical-device]` | Manual smoke on Xiaomi 11T with real Firebase JWT |
| Cross-device recovery | Two emulators, shared blob via Fake or wrangler dev | connectedAndroidTest × 2 emulators | `CrossDeviceRecoveryTest` (SC-001, SEQ-2) |
| Autofill GPM sync | Real device test (two physical devices, same Google) | `[deferred-physical-device]` | Manual smoke (SC-005) |

## Risks & Mitigation

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| R-1 | composeUiTest 1.7.x на API 35+ падает `InputManager.getInstance` (memory `reference_compose_ui_test_api_mismatch`) | High — блокирует UI tests | UI tests помечены `[deferred-local-emulator]`; AVD API ≤34 required для local dev; альтернатива — bump до Compose UI Test 1.8 (one-way door, BOM upgrade) — отложено в plan-tasks open item. |
| R-2 | Argon2id timing на slow OEM hardware > 10s | Medium — UX boundary | Spinner + text «проверяем ваш пароль...» (FR-014/015); benchmark `[deferred-physical-device]` Xiaomi 11T; если P95 > 5s — consider OWASP «moderate» params (iterations=2 / memoryKb=64MB). |
| R-3 | Worker `workers/backup/` не задеплоен к моменту integration test | High — блокирует SC-001 | `wrangler dev` localhost option; backlog item «TASK-X implement workers/backup/» — параллельный track к F-5 Android implementation. |
| R-4 | Cloudflare R2 free tier (10GB) исчерпан beta | Low (200-byte blob × 10000 users = 2MB) | Monitoring + alert via Cloudflare dashboard; exit ramp own-server PostgreSQL per SRV-RECOVERY-001. |
| R-5 | Firebase JWT custom claim setting fails (Firebase Admin SDK quota / config error) | Medium — блокирует sign-up flow | Fallback variant (ii) — Worker верит JWT signature + reads stableId from body (Q-M alternative); security trade-off (см. research.md R2 trade-off). |
| R-6 | OEM MIUI / EMUI background-restrict кушает Worker HTTPS upload mid-flight | Medium — UX impact | Blocking upload в SetupScreen (FR-014) — flow ждёт success; 3 retry с back-off; на 3-й неудаче → explicit dialog «продолжить без облачной копии?». |
| R-7 | User забыл passphrase И Autofill не сохранил (на A набирал вручную, Autofill не подсказал save) | High — destructive | UI на SetupScreen активно подсвечивает Autofill «save password» suggestion; в `docs/recovery-flow.md` явно объяснено что забытый passphrase = data loss; SEQ-3 Fallback flow есть как escape hatch. |
| R-8 | Brute-force через сброс client-side counter (Clear App Data) | Medium — MVP-acceptable | Argon2id work-factor (3s P95 × 100k attempts = 3.5 days non-stop); + Worker in-memory rate-limit (короткоживущий, обходим, но требует resources); persistent server-side counter — SRV-RECOVERY-001 (d) exit ramp. |
| R-9 | Cloudflare access logs утечка / subpoena (per Article XIV §7 (e)) | Low — by design no PII | Worker design enforces minimal metadata in path (только `stableId` UUID); blob — ciphertext. Worst-case Cloudflare compliance request yields opaque UUID + IP + timestamp — недостаточно для reidentification без отдельного `stableId → identity` маппинга (он в Firestore у нас). |

## Required Context Review (per Article XII §7)

Прочитаны (галочка) / релевантны (стрелка):

✓ [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — все Articles I-XIX, **особенно Article XIV §7** (added 2026-06-28).
✓ [`CLAUDE.md`](../../CLAUDE.md) — rules 1-16.
✓ [`docs/dev/server-roadmap.md`](../../docs/dev/server-roadmap.md) — SRV-RECOVERY-001 (updated 2026-06-28), SRV-CRYPTO-PARAMS-REVIEW.
✓ [`docs/product/vision.md`](../../docs/product/vision.md) — feature filter / exit ramps (relevant for one-way door section).
✓ [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md) — operational TODOs.
✓ [`specs/016-f-crypto-core-module/spec.md`](../../specs/016-f-crypto-core-module/spec.md) — F-CRYPTO ports `AeadCipher`, `KeyDerivation`, `SecureKeystore`.
✓ [`specs/017-f4-auth-provider/spec.md`](../../specs/017-f4-auth-provider/spec.md) — F-4 ports `AuthProvider`, `AuthIdentity`, `SessionStore`; FR-001/003/005/006 (provider-agnostic).
✓ [`specs/018-f5-config-e2e-encryption/spec.md`](../../specs/018-f5-config-e2e-encryption/spec.md) — `ConfigCipher2` integration point; FR-018 byte-equal preserved migration.
✓ [`specs/019-f5c-fcm-config-updated/spec.md`](../../specs/019-f5c-fcm-config-updated/spec.md) — `workers/push/` precedent; JWT verification pattern.
✓ [`specs/task-49-cloud-feature-inventory-offline-first/spec.md`](../../specs/task-49-cloud-feature-inventory-offline-first/) — TASK-49 defines «first cloud action» trigger (consumed by spec.md FR-008 implicit; F-5 не определяет cloud-checkpoint logic).
✓ [`specs/task-51-libsodium-consolidation/spec.md`](../../specs/task-51-libsodium-consolidation/) — Argon2id + HKDF + AEAD primitives через `core/crypto`.
✓ [`docs/product/decisions/2026-06-15-deferred-cloud/`](../../docs/product/decisions/2026-06-15-deferred-cloud/) — Sign-In deferred to first cloud action.
✓ [`docs/product/decisions/2026-05-30-f4-identity/`](../../docs/product/decisions/2026-05-30-f4-identity/) — `stableId` UUID provider-agnostic decision.
✓ ADR-011 (AI-owner conventions) — sequence inline в spec.md + MENTOR-DETAIL.
✓ Memory:
   - `reference_compose_ui_test_api_mismatch.md` → R-1 mitigation.
   - `reference_testing_environment.md` → `[deferred-physical-device]` markers.
   - `feedback_exit_ramps_as_todos.md` → inline TODOs в FR-007 / FR-010.
   - `feedback_pre_pr_backlog_sync.md` → workflow для closing F-5.
   - `feedback_critical_mentor_stance.md` → applied throughout planning (especially in R-7 risk surfacing).

→ [`docs/dev/key-hierarchy.md`](../../docs/dev/key-hierarchy.md) — NEW (FR-021).
→ [`docs/recovery-flow.md`](../../docs/recovery-flow.md) — NEW (FR-020).
→ [`docs/compliance/permissions-and-resource-budget.md`](../../docs/compliance/permissions-and-resource-budget.md) — UPDATE (security CHK018 closure).

## Rollout & Verification

Phase-aligned per spec.md SC list:

1. **Phase 1 (core/keys/ domain + fakes)**: SC-007 (grep fitness), SC-008 (provider-agnostic JSON), SC-013 (contract tests). JVM-only, fast.
2. **Phase 2 (Android adapters)**: SC-010 (Argon2 timing benchmark — emulator indicative + Xiaomi 11T `[deferred-physical-device]`), SC-011 (rate-limit per-identity), SC-012 (cascade wipe).
3. **Phase 3 (Compose UI screens)**: SC-001 cross-device через `FakeRecoveryKeyBackup`; SC-002 Fallback flow. UI tests `[deferred-local-emulator]` API ≤34.
4. **Phase 4 (workers/backup/ Worker artifact)**: parallel track (TASK-X); deployment + roundtrip tests via vitest + miniflare; localhost integration через `wrangler dev`.
5. **Phase 5 (Migration ConfigCipher2)**: SC-004 (byte-equal preserved). connectedAndroidTest на real Keystore.
6. **Phase 6 (Documentation + checklist closure)**: SC-006 (recovery-flow.md plain-Russian — peer-review owner'ом), permissions-and-resource-budget.md update, allowBackup posture (security CHK024).
7. **Phase 7 (Manual gates / verification)**: SC-005 GPM Autofill cross-device `[deferred-physical-device]`; SC-003 emulator without GMS verification `[deferred-local-emulator]`.

Pre-merge: `pre-pr-backlog-sync` skill для backlog AC синхронизации (per CLAUDE.md HARD RULE).

## Complexity Tracking

> *No Constitution Check violations — table omitted.*

(All 8 gates PASS per §Constitution Check above. No abstractions introduced beyond rule 4 (MVA) test 1.)

## Open items inherited from /speckit.clarify (must resolve in /speckit.tasks)

From [checklists/_overview.md](./checklists/_overview.md):

1. **wire-format CHK018** — `contracts/recovery-key-backup-v1.md` created in this plan (Phase 1). ✓ closed.
2. **wire-format CHK003 / CHK005 / CHK008** — addressed in `contracts/recovery-key-backup-v1.md` (named SCHEMA_VERSION_V1 const; additive-field policy; newer-version read strategy = graceful refuse with `BackupError.UnsupportedSchema`). ✓ closed.
3. **security CHK018** — `docs/compliance/permissions-and-resource-budget.md` update enumerated in Phase 6 above; no `drive.appdata` scope (moot after Worker pivot). ✓ closed in plan.
4. **security CHK024** — `android:allowBackup="false"` + `data_extraction_rules.xml` enumerated in Project Structure; tasks.md will own implementation. ✓ closed in plan.
5. **requirements-quality CHK001 / CHK009** — Composable / library / static-analyzer names moved from spec.md FR'ов в plan.md Project Structure + Test Strategy (where они уместны). Spec.md FR-014/015/016 references к screens оставлены как behavioural anchors (acceptable per CHK001 — spec describes WHAT не HOW в их формулировках). ✓ partially closed; minor spec.md cleanup in `/speckit.tasks` open item.
6. **failure-recovery CHK016 / CHK017** — diagnostic events enumeration + per-category metric aggregation deferred to future observability spec; not blocking F-5. Tracked as open item in `docs/dev/project-backlog.md`. ✓ accepted-as-deferred.

## Plan-level open items (after round 3 owner direction 2026-06-28)

1. **`workers/backup/` + `workers/identity/` implementation — in-scope of TASK-6** (per owner «делаем в этой же таске» 2026-06-28). Both Workers implemented as part of F-5, not split into separate backlog items. Tracked in tasks.md Phase 4 (T653-T670). Code structure follows microservice boundaries (separate `wrangler.toml`, separate deploys) per memory `project_workers_microservice_mapping.md`. Backlog scope unified per memory `feedback_one_task_per_feature.md` (one feature = one TASK-N).
2. **Q-M resolved**: variant (b) — `workers/identity/` is a separate Worker, NOT bundled into `workers/backup/`. Implementation in T662-T665.
3. **Compose UI Test 1.8 upgrade**: option to unblock UI tests on API 35+ emulators. One-way door (BOM upgrade affects entire app). Decision deferred — keep `[deferred-local-emulator]` markers for MVP.
4. **`checklist-server-data-minimization` skill creation**: spec.md `## Notes → Privacy / data minimization` recommended creating this. Open item for skills authoring, not blocker for F-5.

## Architectural rule (added 2026-06-28 per owner direction)

**Workers folder mirrors future microservices.** Each `workers/<name>/` represents one future Go microservice. Do NOT bundle features across Workers (e.g., do NOT put identity-logic in workers/backup/). This preserves clean migration boundaries when we move to own-server architecture (own-server = Go microservices, not monolith).

Current allocations:
- `workers/push/` — notifications (TASK-5 / spec 019, exists)
- `workers/backup/` — recovery blob storage (TASK-X, this F-5)
- `workers/identity/` — custom claims, identity-link ops (TASK-Y, this F-5)
- `workers/_shared/` — reusable libs (e.g., `auth-jwt/`)

Reference: memory `project_workers_microservice_mapping.md` (created 2026-06-28).
