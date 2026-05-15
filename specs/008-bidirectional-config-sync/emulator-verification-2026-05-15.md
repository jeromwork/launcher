# Emulator Verification — Spec 008

**Date**: 2026-05-15
**Session**: post-`/speckit.analyze`, pre-merge verification on local emulator + Firebase Emulator Suite.
**Goal**: independent re-prove every Phase 0–11 claim from `analyze-report.md` by actually executing the build/tests, plus close as much device-dependent work as feasible without physical hardware.

---

## TL;DR

```
SPEC 008 IMPLEMENTATION VERIFICATION (2026-05-15):

UNIT + INTEGRATION TESTS         : ✅ 1073 tests, 0 failures (mockBackend + realBackend)
  Spec 008 specific subset       : ✅ 92 tests across 16 test classes
FIRESTORE SECURITY RULES         : ✅ 28/28 PASS via Firebase Emulator Suite
                                       (incl. 4 spec-008 collaborative-editing rules)
KONSIST FITNESS GATES            : ✅ Spec008IsolationTest 4/4 PASS
APK DELTA (T142)                 : ✅ 3.79 MiB realBackend - mockBackend
                                       (under 4 MiB budget WITHOUT R8)
DI WIRING                        : ✅ assembleMockBackendDebug + assembleRealBackendDebug
                                       both SUCCESS
EMULATOR SMOKE (mockBackend)     : ✅ FirstLaunchActivity renders;
                                       ConfigRefreshWorker (T3) fires;
                                       ConnectivityManager NetworkCallback (T2)
                                       registers and unregisters per lifecycle.
EMULATOR SMOKE (realBackend)     : ❌ Blocked by TODO-SMOKE-001 (google-services
                                       plugin not applied; FirebaseApp init fails →
                                       LauncherApplication crash on start).

ALSO FIXED THIS SESSION          : Source-set wiring for `androidRealBackendUnitTest`
                                       was missing — FirebaseConfigApplierTest (4) and
                                       DefaultConfigEditorTest (4) were dead code.
                                       Now wired via `android.sourceSets.testRealBackend`
                                       srcDir override; both run green.

VERDICT: Spec 008 implementation is COMPLETE at the code/contract/unit-test level.
         End-to-end smoke on emulator is BLOCKED by inherited spec-007 plugin TODO,
         not by spec 008 itself. Real-device verification still required for
         OEM-specific background-restriction behaviour and 24h wakeups budget.
```

---

## What was actually executed

### 1. Firestore Security Rules — `firestore-tests/rules.test.ts` via Firebase Emulator

```
Override JAVA_HOME → C:\Program Files\Android\Android Studio\jbr (Java 21)
$ cd firestore-tests && npm test
  → firebase emulators:exec --only firestore,auth --project demo-test "vitest run"
  → 28 tests, all PASS in 14.6s
```

Spec-008-specific cases PASSED:
- `foreign_uid_cannot_create_config`
- `schemaVersion_must_be_1_on_create`
- `schemaVersion_cannot_decrease_on_update`
- `only_managed_can_delete_config_via_revoke`
- (+ 24 spec-007 inherited rules still green)

Closes: T072, T073, T074, T075.

### 2. `:core` unit tests — both flavors

```
$ ./gradlew :core:testMockBackendDebugUnitTest :core:testRealBackendDebugUnitTest \
    -x verifyCommonMainConfigStoreMigration -x verifySqlDelightMigration
  → BUILD SUCCESSFUL, 1073 tests, 0 failures
```

`-x verify*Migration` excluded due to known sqlite-jdbc/JDK21 native-library mismatch in the SQLDelight Gradle plugin's migration verifier — not a spec 008 defect, separate Gradle tooling issue. Manual `verifySqlDelightMigration` should be re-attempted on a CI image with sqlite-jdbc 3.47+.

Spec-008 test inventory (verified by parsing `core/build/test-results/.../TEST-*.xml`):

| Test class | Count | Phase | Status |
|---|---|---|---|
| `ElementIdTest` | 6 | 1 | ✅ |
| `ServerTimestampTest` | 3 | 1 | ✅ |
| `ConfigDocumentWireFormatTest` | 9 | 2 | ✅ |
| `StateAppliedWireFormatTest` | 7 | 2 | ✅ |
| `ConfigDiffTest` | 8 | 2 | ✅ |
| `FakeLocalConfigStoreTest` | 6 | 2 | ✅ |
| `FakeConfigApplierTest` | 4 | 2 | ✅ |
| `FakeConfigEditorTest` | 5 | 2 | ✅ |
| `SqlDelightLocalConfigStoreTest` | 7 | 3 | ✅ |
| `FirebaseConfigApplierTest` | 4 | 4 | ✅ **rescued this session** |
| `DefaultConfigEditorTest` | 4 | 4 | ✅ **rescued this session** |
| `ProcessLifecycleForegroundEventsTest` | 4 | 7 | ✅ |
| `PushIndicatorPresenterTest` | 9 | 8 | ✅ |
| `MergeResolverTest` | 7 | 9 | ✅ |
| `Spec008IsolationTest` (Konsist) | 4 | 10 | ✅ |
| `ConfigSyncE2ETest` | 5 | 11 | ✅ |
| **TOTAL** | **92** | | |

### 3. Source-set fix — `androidRealBackendUnitTest`

The folder `core/src/androidRealBackendUnitTest/kotlin/...` contained `FirebaseConfigApplierTest.kt` and `DefaultConfigEditorTest.kt` (committed in `feat(008): Phase 4 — Firebase adapters + tests` a8c7359). However AGP did not auto-discover this folder for the `realBackend` flavor's unit-test source set, so the 8 tests were silently never executed.

`analyze-report.md` 2026-05-14 entry «`androidRealBackendUnitTest : ✅ 8 tests, 100% green`» was inaccurate — those tests existed as source but were dead.

Fix in [`core/build.gradle.kts`](../../core/build.gradle.kts):

```kotlin
android {
  sourceSets {
    getByName("testRealBackend") {
      java.srcDirs("src/androidRealBackendUnitTest/kotlin")
    }
  }
}
```

After fix, `:core:testRealBackendDebugUnitTest` picks them up; both classes run, all 8 PASS. Should be committed alongside the other end-of-spec-008 work.

### 4. APK delta — T142

```
$ ./gradlew :app:assembleMockBackendRelease :app:assembleRealBackendRelease
  → BUILD SUCCESSFUL

mockBackend release: 9 434 891 bytes ≈ 9.00 MiB
realBackend release: 13 406 221 bytes ≈ 12.78 MiB
Delta              : 3 971 330 bytes ≈ 3.79 MiB
```

**3.79 MiB ≤ 4 MiB budget — passes EVEN WITHOUT R8 minification.** This invalidates the conservative estimate in `perf-checkpoint.md` («≈ 4.36 MiB without R8, blocks merge») — actual measured value is under budget. R8 (TODO-ARCH-006) remains nice-to-have but no longer a release blocker for spec 008.

Closes: T142.

### 5. Emulator runtime smoke — mockBackend

Single emulator (`Medium_Phone_API_36.1`, port 5554), `app-mockBackend-debug.apk` installed, MD5 verified fresh. Launched via `am start -n com.launcher.app.mock/.firstlaunch.FirstLaunchActivity`.

**FirstLaunchActivity rendered** (3-card chooser: Workspace / Launcher / Simple launcher) — see `build/008-launch-2.png`.

**Lifecycle adapters live in logcat**:
- `WM-WorkerWrapper: Worker result SUCCESS for com.launcher.adapters.lifecycle.ConfigRefreshWorker` — Phase 7 T3 wired correctly via Application init.
- `WM-NetworkStateTracker: Network capabilities changed: ... Unregistering network callback` — Phase 7 T2 (`ConnectivityManagerNetworkAvailability`) registers on subscribe, unregisters on no-subscribers per implementation contract.

**Not exercised on mockBackend** (by design — no Firebase): T1 (FCM push), T4 (RESUMED throttle) — covered by unit tests only.

### 6. Emulator runtime smoke — realBackend

**❌ FATAL EXCEPTION at start**:

```
W FirebaseApp: Default FirebaseApp failed to initialize because no default options
               were found. This usually means that com.google.gms:google-services
               was not applied to your gradle project.
E [Koin]: * Instance creation error : could not create instance for
          '[Singleton: com.google.firebase.firestore.FirebaseFirestore]':
          java.lang.IllegalStateException: Default FirebaseApp is not initialized
E AndroidRuntime: FATAL EXCEPTION: main
                  Process: com.launcher.app, PID: 7836
                  java.lang.RuntimeException: Unable to create application
                  com.launcher.app.LauncherApplication
```

Root cause: `app/google-services.json` exists at module root but the Gradle plugin `com.google.gms.google-services` is **never applied** in `app/build.gradle.kts` — the alias is declared in `gradle/libs.versions.toml:143` but no `id(...)` line uses it. There is an explicit `TODO(spec 007 Phase 4)` at `app/build.gradle.kts:27` flagging this exact gap.

Tracked in **TODO-SMOKE-001** in [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md) with full repro and fix recipe (per-flavor JSON + plugin apply).

**Implication**: T143 manual smoke (`smoke/README.md` US-1..US-5) **cannot run on emulator until TODO-SMOKE-001 closes**, regardless of physical-vs-emulator hardware. This is an inherited spec-007 gap, not a spec-008 defect.

---

## Spec 008 task verification matrix

Each task from `tasks.md` re-checked against actual code/test state:

| Phase | Task | Claim | Verification | Status |
|---|---|---|---|---|
| 0 | T001 | TODO-ARCH-006 status checked | Manual coordination — out of scope here | ⚪ |
| 0 | T002 | SQLDelight in `:core` Gradle | `core/build.gradle.kts:46-48,62-63` ✓ | ✅ |
| 0 | T003 | `permissions-and-resource-budget.md` updated | File present | ✅ |
| 0 | T004 | Security Rules diff baseline | `firestore.rules` extended for `/config/current` | ✅ |
| 0 | T005 | Legacy mock-storage paths inventoried | `legacy-cleanup-inventory.md` ✓ | ✅ |
| 0 | T006 | PR opened | Branch `008-bidirectional-config-sync` pushed | ✅ |
| 1 | T010-T017 | 5 ports + value classes + constants | All `.kt` files exist in `core/src/commonMain/kotlin/com/launcher/api/{config,link,lifecycle,push}/` | ✅ |
| 1 | T018-T022 | 5 port interfaces | All present | ✅ |
| 1 | T023 | PushType KDoc updated | `PushType.kt` annotated | ✅ |
| 2 | T030-T035 | Wire-format + roundtrip + backward-compat tests | 9+7+8 tests PASS | ✅ |
| 2 | T036-T037 | ConfigDiff + 5 acceptance scenarios | 8 tests PASS (US-2 scenarios 1-5) | ✅ |
| 2 | T038-T044 | 5 fake adapters + contract tests | 6+4+5 tests PASS | ✅ |
| 3 | T050-T055 | SQLDelight schema + adapter + cleanup | 7 PASS; verify-migration task fails on JDK21 sqlite-jdbc — separate tooling issue | ✅ (with caveat) |
| 4 | T060-T066 | Firebase adapters + 6 tests | 4+4 PASS **after this session's source-set fix** | ✅ |
| 5 | T070-T076 | Security Rules + 5 emulator tests | 4 collaborative-editing rule tests PASS via Firebase Emulator | ✅ |
| 6 | T080-T082 | Cloudflare Worker config-changed payload | Worker code present in `push-worker/src/`, deploy still pending | ⚪ (deploy needed) |
| 7 | T090-T096 | 4 trigger adapters + Application init | T2 + T3 verified live in logcat; T1/T4 unit-only | ✅ |
| 8 | T100-T108 | Editor UI components + state restoration | Composable files present; Compose UI tests deferred (TODO-INSTRUMENT-001) | 🟡 |
| 9 | T110-T115 | MergeScreen + auto-resolve + strings | MergeResolver 7 tests PASS; Composable UI not instrumented-tested | 🟡 |
| 10 | T120-T123 | Konsist fitness gates | Spec008IsolationTest 4 PASS | ✅ |
| 11 | T130-T131 | E2E + SC-003 100× | ConfigSyncE2ETest 5 PASS (incl. 100-push outcome counter) | ✅ |
| 12 | T140-T146 | Macrobenchmark, APK delta, smoke, docs | T142 ✅ measured; T140 needs `:benchmark` module (TODO-PERF-001); T143 needs TODO-SMOKE-001 | 🟡 |

Legend: ✅ verified working · 🟡 implemented + unit-tested but instrumented coverage missing · ⚪ out-of-scope-here / external dependency.

---

## What's left (and why)

Tracked in [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md):

| TODO | Severity | Blocks | Reason |
|---|---|---|---|
| **TODO-SMOKE-001** | 🟡 | T143 emulator smoke + ANY realBackend launch | Inherited spec-007 plugin wiring gap |
| **TODO-SMOKE-002** | 🟢 | (alternative to SMOKE-001 for local-only testing) | Optional ergonomic improvement |
| **TODO-INSTRUMENT-001** | 🟡 | T091, T095, Compose UI verification | `androidTest/` source set is empty — analyze-report's claim of `8 tests` was off |
| **TODO-PERF-001** | 🟡 | T140 macrobenchmark | `:benchmark` Gradle module doesn't exist |
| **TODO-DEVICE-001** | 🟢 | 24h wakeups trial (`perf-checkpoint.md`) | Battery Historian needs real device |
| **TODO-DEVICE-002** | 🟡 | OEM-specific T143 coverage | Samsung/Xiaomi/Huawei background restrictions can't be emulated |

Of these, **only TODO-DEVICE-001 and TODO-DEVICE-002 truly require physical hardware**. SMOKE-001, INSTRUMENT-001, PERF-001 are pure Gradle/code work that can land in a future session and unlock further emulator coverage.

---

## Files added/changed this session

- `core/build.gradle.kts` — added `android.sourceSets.testRealBackend.java.srcDir` block (8 lines).
- `docs/dev/project-backlog.md` — appended 6 TODO entries (SMOKE-001/002, INSTRUMENT-001, PERF-001, DEVICE-001/002).
- `specs/008-bidirectional-config-sync/emulator-verification-2026-05-15.md` — this file.
- `build/008-launch.png`, `build/008-launch-2.png`, `build/008-realBackend-launch.png` — screenshot evidence (gitignored).

## Files NOT changed but should be (next session)

- `specs/008-bidirectional-config-sync/perf-checkpoint.md` — flip APK-delta checkbox to ✅ measured (3.79 MiB).
- `specs/008-bidirectional-config-sync/analyze-report.md` — note that `androidRealBackendUnitTest` claim was inaccurate until source-set fix; correct numbers post-fix.
- `specs/008-bidirectional-config-sync/tasks.md` — append note next to T091/T095/T108 that instrumented coverage was deferred and tracked in TODO-INSTRUMENT-001 (currently those tasks read as if done).

<!-- novice summary -->

## TL;DR (на простом русском)

«Финальная проверка спека 008 на эмуляторе — что реально работает, что не работает, и почему». Прогнал все юнит- и интеграционные тесты (1073 теста зелёные), Firestore-правила через локальный эмулятор Firebase (28 тестов зелёные), измерил размер APK (укладывается в бюджет даже без оптимизации R8), запустил приложение на одном эмуляторе и убедился, что workers и сетевые слушатели спека 008 заводятся в живую (видно в логах). Случайно нашёл проблему: 8 тестов спека 008 (для Firebase-адаптеров) были закоммичены, но никогда не запускались — папка лежала не там, где Gradle ожидает. Починил, теперь они тоже зелёные. Полный двухэмуляторный smoke-тест («admin поправил → managed применил») сделать не получилось — отдельный плагин Firebase для Gradle не подключен (это явный TODO ещё с спека 007). Записал 6 задач в `project-backlog.md`: 4 можно сделать на эмуляторе в следующей сессии (подключить плагин, написать инструменты-тесты, добавить модуль для бенчмарка), 2 действительно требуют реальный телефон (24-часовое измерение батареи + проверка OEM-багов Samsung/Xiaomi/Huawei). Спек 008 готов на уровне кода и контрактов; end-to-end проверка ждёт закрытия одного из 4 эмулятор-задач.
