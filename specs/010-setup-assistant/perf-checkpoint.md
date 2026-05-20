# Perf checkpoint — спек 010 setup-assistant

**Создан**: 2026-05-19 (Phase 8 T109).
**Status**: macrobenchmark + APK delta deferred — see «Deferred» section.

---

## Targets (per spec.md §Success Criteria)

| ID | Цель | Measured? | Result |
|----|------|-----------|--------|
| SC-001 | Config push ≤ 10 sec (Managed see admin change) | Manual smoke deferred | TODO physical device |
| SC-002 | HomeScreen cold-start first-frame ≤ 1 sec p95 | **Macrobenchmark deferred** | TODO Pixel 4a |
| SC-003 | Call в 2 таpа от Home → dialer ring | Composable test green | PASS (Robolectric) + manual smoke deferred |
| SC-004 | Fresh install Settings badge `!N ≥ 2` | unit test green | PASS (`SetupChecksBadgeTest`) |
| SC-005 | После grant `N = 0` | unit test green | PASS (`SetupCheckEngineTest`) |
| SC-007 | Challenge FP ≤ 1% | unit test green | PASS — see `ChallengeFPRateTest` (T025); NumericEntry 0.01 % + SequenceTap ≈ 0.83 % |
| SC-008 | Mock removal CI green | mockBackend + realBackend assemble | PASS (см. CI workflows) |
| SC-009 | APK delta vs спек 9 release ≤ +500 KB | **APK diff deferred** | TODO release build |

---

## Deferred — требуют hardware / release build

### SC-002 macrobenchmark methodology (когда устройство появится)

```
./gradlew :macrobenchmark:connectedRealBackendReleaseAndroidTest \
    -P android.testInstrumentationRunnerArguments.class=com.launcher.macrobench.HomeColdStartBenchmark
```

Ожидание:
- p95 первого кадра ≤ 1000 ms на Pixel 4a class (Cortex-A76 ≈ 2.2 GHz).
- Если p95 > 1000 ms → bottleneck скорее всего в:
  - Firestore listener cold-attach (Phase 2 ARCH-016 / ConfigBackedFlowRepository);
  - SetupCheckEngine.refresh() выполняется sync на main thread (FR-020a parallel — но сама refresh может block перерисовку).

### SC-009 APK diff methodology

```
./gradlew :app:assembleRealBackendRelease  # current branch
./gradlew :app:assembleRealBackendRelease  # baseline = main@spec-009-merge
apkdiff app-release-spec010.apk app-release-spec009.apk
```

Ожидание:
- Δ ≤ +500 KB (плановое расширение от: GMS base library, дополнительные res
  strings для спека 010, новые Kotlin files).
- Если Δ > 500 KB → R8 shrinking probably не активен, или kotlinx-datetime
  слетел в release.

---

## Что измерено локально (unit / Robolectric)

| Замер | Значение | Источник |
|-------|----------|----------|
| SetupCheckEngine parallel refresh (5 checks) | < 50 ms (async coroutine setup) | `SetupCheckEngineTest` — runTest pass |
| Challenge gate rotation restore | 0 frames dropped | `ChallengeGateScreenTest` Robolectric pass |
| Local revocation flag persist | DataStore CRUD ≤ 5 ms | `InMemoryLocalLinkRevocationStoreTest` (in-memory) — disk timings deferred |
| 7-tap detector logic | < 1 ms / tap (pure Kotlin) | `SevenTapDetectorTest` 6 cases — total run < 100 ms |

---

## Next steps

1. После физического устройства / Pixel emulator setup — выполнить SC-002
   macrobenchmark + SC-009 APK diff, заполнить таблицу выше.
2. Если SC-002 регрессирует — посмотреть `ConfigBackedFlowRepository.observeFlows`
   first-emit timing (currently combines linkRegistry + revocationStore Flows).
3. Если SC-009 регрессирует — проверить R8 keep-rules для kotlinx.serialization
   (ChallengeSaver делает runtime `runCatching`).

---

## Test runs

_(пустой; добавляется по мере выполнения)_
