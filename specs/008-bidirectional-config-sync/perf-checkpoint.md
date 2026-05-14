# Perf Checkpoint: spec 008 — Bidirectional Config Sync

**Date**: 2026-05-14 (skeleton; measurements deferred to device-available checkpoint)
**Tasks**: T140-T142

Per plan.md §Rollout / Verification — pre-merge gates §5 («APK delta < 4 MiB
с R8») and §1 («SC measurable outcomes met»).

This file records **measured values** for spec 008 performance budgets. While
no Android device / emulator is available in the current implementation
session, the structure here mirrors spec 007's `perf-checkpoint.md` so the
measurements can be filled in directly when a device session opens.

---

## SC-004a — Cold start with last-applied (≤ 650 ms p95)

**Target** (spec.md SC-004a): «After process death first frame UI Managed-
телефона MUST отрисовываться с last-applied-config from SQLDelight (без
сетевых обращений до first frame) и укладываться в бюджет SC-007 спека 007:
≤ 650 ms (p95)».

**Sub-budget** (research.md §6): SQLDelight read ≤ 50 ms p95 (Pixel 4a class).

**Measurement method** (T140): Macrobenchmark с `MeasureCriterion.StartupCriterion`,
20 iterations after process kill, capture p95.

**Measured value**: ⏳ pending device session.

**Pass criterion**:
- [ ] first frame to last-applied-config ≤ 650 ms p95;
- [ ] SQLDelight read sub-budget ≤ 50 ms p95.

---

## SC-004b — Post-startup config refresh (5 s timer)

**Target** (FR-044 + SC-004b): «After first frame Managed MUST через 5
seconds запросить /config/current с сервера…».

**Measurement method**: integration test verifying 5-second delay via
TestScope virtual time (covered by FakeConfigEditor wiring tests in
Phase 11) + on-device timing log validation.

**Measured value**: 5 000 ms (constant `ConfigSyncConstants.POST_STARTUP_FETCH_DELAY_MS`).

**Pass criterion**:
- [x] constant value verified by ConfigSyncConstants test (Phase 1 T017);
- [ ] on-device confirmation that fetch fires в 5 s window (deferred).

---

## APK delta — < 4 MiB after R8 (T142)

**Target** (plan.md Risk R3): «APK delta < 4 MiB. If fails — block merge
until TODO-ARCH-006 lands».

**Baseline** (spec 007 SC-006 measurement): realBackend vs mockBackend =
+3.99 MiB SI without R8 minification.

**Spec 008 new dependencies**:
- SQLDelight runtime + coroutines-extensions: commonMain, ~140 KiB.
- SQLDelight android-driver: androidMain, ~120 KiB.
- WorkManager runtime-ktx: androidMain, ~110 KiB.

**Estimated additional delta** (без R8): +370-400 KiB → total ≈ 4.36 MiB
realBackend delta. **Exceeds budget without R8**.

**With R8** (assuming spec 007 TODO-ARCH-006 lands): expected -40-60%
reduction → estimated final delta ≈ 1.7-2.6 MiB. **Within budget**.

**Measurement method**:
```
./gradlew :app:assembleRealBackendRelease :app:assembleMockBackendRelease
diff = sizeof(realBackend.apk) - sizeof(mockBackend.apk)
```

**Measured value**: ⏳ pending device session + TODO-ARCH-006 coordination.

**Pass criterion**:
- [ ] APK delta < 4 MiB realBackend - mockBackend;
- [ ] OR explicit waiver with reasoned APK budget exception.

---

## Background wakeups — < 10/hour (Article IX §3)

**Target** (plan.md Risk profile): «<10 wakeups/hour aggregated across all
4 triggers, well within Article IX §3 cap».

**Sources**:
- T1 FCM `config-changed`: per admin push — bursty но не periodic.
- T2 NetworkCallback: per OS network state change — typically 0-10/hour.
- T3 WorkManager periodic 15-min: 4/hour fixed.
- T4 RESUMED throttled 2-min: user-bound, no background.

**Aggregate worst case**: 4 (T3) + ~5 (T2 spikes during travel) = ~9/hour.

**Measurement method** (T140 follow-up): Android Battery Historian dump after
24h trial run.

**Measured value**: ⏳ pending 24h-trial.

**Pass criterion**:
- [ ] <10 wakeups/hour 95th percentile over 24h trial.

---

## Conflict resolution latency

**Target**: not formally specified (UX feedback covers — FR-015 spinner +
5 s no-network warning + post-resolution re-push).

**Measurement method**: in-process E2E test SC_003_100_pushes_yield_100_outcomes
(Phase 11 — already PASS).

**Result**: 100 pushes × concurrent-writer-every-5th = 80 successes + 20
conflicts; all 100 have deterministic outcome, 0 silent failures. ✅

---

## Summary

```
PERF CHECKPOINT for spec 008:
  SC-004a cold start ≤ 650 ms p95           : ⏳ pending device measurement
  SC-004b post-startup fetch 5 s            : ✅ constant verified в Phase 1
  APK delta < 4 MiB (R8)                    : ⏳ pending R8 (TODO-ARCH-006)
  Background wakeups < 10/hour              : ⏳ pending 24h trial
  SC-003 100 pushes → 100 outcomes          : ✅ E2E green Phase 11

CODE COMPLETION                              : ✅ Phases 0-11 done
DEVICE-DEPENDENT VERIFICATIONS               : ⏳ deferred to device session
```

**Next step**: when device available — run macrobenchmark, measure APK delta,
24h trial; update this file with actual numbers; flip checkboxes.
