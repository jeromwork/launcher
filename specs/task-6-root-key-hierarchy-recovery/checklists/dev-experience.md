# Checklist: dev-experience — F-5 Root Key Hierarchy + Owner Recovery

Spec: `specs/task-6-root-key-hierarchy-recovery/spec.md`
Date: 2026-06-28

| ID | Item | Status | Evidence / Why |
|---|---|---|---|
| CHK001 | Local Test Path section filled in | [x] | Spec §«Local Test Path» (lines 370–389) полностью заполнен: emulator preset, fakes, fixtures, verification commands, cannot-test-locally gaps. |
| CHK002 | Verification command exact | [x] | Точные команды: `./gradlew :core:keys:test`, `./gradlew :app:connectedDebugAndroidTest --tests *Recovery*`, `./gradlew :app:connectedDebugAndroidTest --tests *KeyRegistryMigration*` (lines 381–384). |
| CHK003 | Verification command < 5 min cold | [x] | JVM unit tests (`:core:keys:test`) + instrumented Recovery tests на одном `pixel_5_api_34` — fits 5-min budget. Cross-device × 2 emulators — отдельный smoke, не на каждой итерации. |
| CHK004 | At least one path verifiable without emulator | [x] | US-6 (provider-agnostic test) + contract tests (FR-023) — pure JVM unit tests в `core/keys/src/commonTest/`. |
| CHK005 | If emulator required, preset named | [x] | `pixel_5_api_34` явно указан (lines 373–374), preset из skill `android-emulator`. |
| CHK006 | Fake adapter for every external port | [x] | FR-022: `FakeKeyRegistry`, `FakeRootKeyManager`, `FakeRecoveryKeyBackup`, `FakeAuthAvailability`. Также `FakeAuthAdapter` (из spec 017). Покрывает все 4 порта. |
| CHK007 | Fakes used in tests (no real Firebase/Drive needed) | [x] | US-1/US-2 Independent Test использует `FakeAuthAdapter`. Real Drive REST API явно помечен `[deferred-physical-device]` (line 386). |
| CHK008 | DI picks fakes for debug/test, reals for release | [~] | FR-012 описывает `RecoveryKeyBackupSelector` capability-based, но build-flavor split (debug vs release fakes) не упомянут явно. Existing drafts на `origin/020-...` имеют `realBackend` source set (line 459) — convention присутствует в проекте, но spec не делает это explicit. **Minor**: добавить одну строку в FR-012 или Local Test Path о DI override в `debug`/`androidTest` builds. |
| CHK009 | Test data in checked-in fixture | [x] | `core/keys/src/commonTest/resources/fixtures/recovery-blob-v1-sample.json` + `config-ciphertext-spec018-sample.bin` (lines 378–379). |
| CHK010 | Fixtures stable (no Random/now) | [x] | Sample fixture v1 — статический файл; `RecoveryKeyBackupBlob.createdAt` в JSON примере — фиксированная строка `"2026-06-28T10:00:00Z"` (line 199). `stableId` тоже фиксирован для test'ов (`"00000000-0000-4000-8000-000000000001"`, line 138). |
| CHK011 | Cross-version fixtures for wire formats | [x] | FR-023: `RecoveryKeyBackupBlobBackwardCompatTest` (read v1 → success, v2 → graceful error). Sample blob v1 stored as fixture. |
| CHK012 | Cannot-test-locally gaps explicitly listed | [x] | §«Cannot-test-locally gaps» (lines 385–389): Drive REST API, GPM Autofill cross-device sync, Argon2 timing on real hw, Huawei/EMUI — все 4 явно перечислены с `[deferred-physical-device]` маркером. |
| CHK013 | Each gap has inline TODO | [~] | Spec упоминает inline TODO в FR-007 (RootKeyManager, RecoveryKeyBackupBlob, KeyRegistry.derive) и FR-010 (`// TODO(server-roadmap)`), но **отсутствуют** explicit `TODO(physical-device): ...` маркеры для 4 gaps из Cannot-test-locally section. OEM Matrix (line 407) использует `[deferred-physical-device]` в spec'е, но это не code-level TODO. **Minor**: добавить в FR-009 (Argon2 timing) и FR-010 (Drive integration) inline-TODO `physical-device` для будущего grep-аудита. |
| CHK014 | No gap silently swept under "test in prod" | [x] | Все gaps documented + Huawei явно помечен «устройства нет — DI-override test обязателен» (line 389, 407). |
| CHK015 | Clean-build time impact ≤ 30s | [x] | F-5 добавляет `core/keys/` module + UI screens — modest. libsodium binding уже в `core/crypto` (TASK-51). Argon2 native binding — единственный новый non-trivial cost, но он уже консолидирован в существующий module. Acceptable. |
| CHK016 | No undocumented one-time manual setup | [~] | Drive App Data scope `https://www.googleapis.com/auth/drive.appdata` явно указан (FR-010, Q-D). Firebase Auth providers на Spark plan требуют **manual console enablement** (memory `reference_firebase_auth_provider_manual_only.md`) — но F-5 не вводит **новые** providers (наследует от F-4). Setup для Drive App Data не требует console (works с любым signed-in Google account). **Minor concern**: spec не упоминает явно «no extra console step needed для F-5». |
| CHK017 | No new credential/API key for debug | [x] | F-5 не вводит новые service-account / API keys. Drive App Data использует user OAuth (предоставляется через F-4 sign-in). `FakeRecoveryKeyBackup` для debug — нулевой setup. |
| CHK018 | Log signal for failure diagnosis | [~] | FR-011 упоминает debug log в `NoOpRecoveryKeyBackup` («recovery backup unavailable on this device, blob retained locally only»). FR-010 описывает `BackupError.AuthRevoked`. Но **общая стратегия логирования** (Logcat tag namespace, structured fields для `RootKeyManager.create/recover/forget`, `KeyRegistry.derive/wipeAll`) явно не описана. **Minor**: добавить FR про logging tags (e.g., `tag = "F5/Recovery"` или structured `Outcome.Failure` логи). |
| CHK019 | Silent-crash failure modes have opt-in log | [~] | `RecoveryViewModel` (FR-017) subscribed to `currentUser` flow — lifecycle-detached cleanup. Background coroutine для `uploadBlob()` (FR-014 blocking) — visible UI progress, не silent. Однако Argon2 derivation в coroutine — если cancelled mid-flight, лог не упомянут. **Minor**: explicit log requirement для cancellation paths. |
| CHK020 | Feature flags / remote config loggable | [N/A] | F-5 не использует remote config / feature flags. Argon2 params hardcoded в FR-009 (wire-format constraint per Q-A). N/A. |
| CHK021 | No developer-machine-specific paths/env | [x] | Verification commands — pure `./gradlew` без env vars. Никаких `MY_PHONE_NUMBER` / personal account assumptions в test path. |
| CHK022 | Onboarding < 1 page | [x] | Verification path = 3 gradle команды + emulator preset name. Covered by skill `android-emulator` + existing `docs/dev/` setup. Less than one page. |

---

## Summary

- **PASS clean**: 14
- **PASS with minor caveat (`[~]`)**: 6 — CHK008, CHK013, CHK016, CHK018, CHK019
- **N/A**: 1 — CHK020
- **FAIL**: 0

### Minor caveats (not blocking; recommend follow-up touches)

- **CHK008** — Add one line to FR-012 or Local Test Path noting DI override in `debug`/`androidTest` (existing `realBackend` source set convention).
- **CHK013** — Convert the 4 `[deferred-physical-device]` items in Cannot-test-locally section into explicit `// TODO(physical-device): ...` markers in code (FR-009 for Argon2 timing, FR-010 for Drive integration).
- **CHK016** — Add explicit sentence: «F-5 не вводит новых Firebase Console manual steps; inherits OAuth from F-4».
- **CHK018** — Add FR for Logcat tag namespace + structured log fields для `RootKeyManager` / `KeyRegistry` operations.
- **CHK019** — Add log requirement для coroutine cancellation paths (Argon2 mid-derivation cancel).

### Headline answer to context question

**Can a dev verify this feature locally without paid accounts?** YES. JVM unit tests + dual `pixel_5_api_34` emulators + comprehensive fakes (`FakeAuthAdapter` / `FakeKeyRegistry` / `FakeRootKeyManager` / `FakeRecoveryKeyBackup` / `FakeAuthAvailability`) cover US-1 through US-6. No paid services required for dev loop. Real Drive REST API + real-device Argon2 timing + Huawei verification correctly deferred with `[deferred-physical-device]` markers.

**Are fakes complete?** YES — every external port has a fake (FR-022).

**Is the verification command concrete?** YES — three exact `./gradlew` invocations (lines 381–384).
