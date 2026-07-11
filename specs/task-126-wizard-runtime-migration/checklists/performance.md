# Checklist: performance — task-126 Wizard Runtime Migration

Applies Article IX + ADR-005 performance targets + Android Vitals.

Spec: `specs/task-126-wizard-runtime-migration/spec.md` (Draft, 414 lines).

Context: this is a refactor (three engines → one) with no new user-facing feature. Perf impact expected non-negative.

## Startup

- [x] CHK001 Cold/warm/hot start target documented. NFR-001: "ReconcileEngine cold-start delta MUST remain ≤ 30 ms on Pixel 5 baseline (already verified in TASK-120, regression guard)". Note: ADR-005 baseline is HomeActivity ≤ 600ms / FirstLaunchActivity ≤ 700ms; the 30ms delta is scoped to the engine, not full activity — reasonable.
- [ ] CHK002 No new I/O / parsing on cold-start path without budget. **WARN**: FR-001 wires `PresetBootstrap.bootstrap()` into `FirstLaunchActivity.onCreate()` — includes JSON parsing of `preset` + `pool` + optional `hint-pool`. Budget covered under NFR-001 delta but individual load cost not itemized. Loading state (`ReconcileState.Loading`) explicitly covers UX (CL-1) — startup visual is protected by SplashScreen.
- [x] CHK003 Application.onCreate init documented. Spec doesn't add work to Application.onCreate — Koin `PresetModule` (FR-016) inherits `task120Module` wiring already measured in TASK-120.

## Runtime — frames

- [x] CHK004 Scrolling frame budget. N/A — wizard is step-by-step, no scrolling surfaces added/changed.
- [x] CHK005 Animation duration documented. N/A — no new animations introduced by refactor; UX identical to pre-migration (NFR-004).
- [x] CHK006 No sync network/disk on main thread. `PresetBootstrap` explicitly emits `Loading` state (CL-1) — implies async off-main; `ProfileStore.save()` in SEQ-1/SEQ-2 sits behind port, inherits TASK-120 async pattern. **WARN**: spec doesn't call out threading contract for `Provider.check()` / `Provider.apply()` — should be specified in plan.md.

## ANR risk

- [ ] CHK007 Operations > 100ms on main thread. **WARN**: `PresetValidator.validate()` runs before wizard starts (FR-006, SEQ-3) — for a preset with N components + `requires` graph traversal, cost bound not stated. Likely trivial (<10ms) but should be affirmed.
- [x] CHK008 BroadcastReceiver body short. `BootCheckReceiver.onReceive()` invokes `ReconcileEngine.run(RunMode.BootCheck)` (FR-012, SEQ-2). **WARN**: spec doesn't say whether receiver delegates to WorkManager or runs inline. Critical components include Provider.check + apply — could exceed 10s on OEM cold boot. Plan.md must resolve.

## Background work

- [x] CHK009 Each new background task justified. Only `BootCheckReceiver` (already exists, migrated per FR-012). No new WorkManager jobs, Services, or Application-scope coroutines.
- [x] CHK010 Polling avoided. Wizard is event-driven (`InteractionSink`, `StateFlow`). `check()` runs on resume (per-user action), not polled (CL-3).
- [x] CHK011 Event listeners documented. `BOOT_COMPLETED` broadcast covered in SEQ-2 with fallback semantics (only `critical: true` components).

## Memory

- [x] CHK012 New caches. Spec doesn't introduce caches. `WizardStore` stores only `lastCompletedStepIndex: Int`; `ProfileStore` inherits from TASK-120.
- [x] CHK013 Bitmap/image loading. N/A — no image loading in this refactor.
- [x] CHK014 No Activity/Context leaks. `WizardViewModel` wraps `ReconcileEngine` (domain) + `InteractionSink` (domain port) — clean shape. FR-001 explicitly separates Activity from engine.

## APK / binary size

- [x] CHK015 New dependencies vs ADR-005 budget. Net **negative**: FR-005 removes `AccessibilityService`; FR-017 deletes `core/androidMain/assets/wizard/` tree; FR-011 deletes `WizardCheckpointStore`; FR-016 merges 2 Koin modules into 1. Custom lint rule FF-011 (FR-015) adds trivial size.
- [x] CHK016 Native libs / large assets. None added.

## Measurement

- [x] CHK017 Measurement method for NFR-001. Documented: "already verified in TASK-120, regression guard" — inherits macrobenchmark harness from TASK-120.
- [ ] CHK018 Perf checkpoint in tasks.md. **WARN**: cannot verify — `tasks.md` not yet generated (spec at Draft). Plan/tasks phase must include perf-checkpoint validating NFR-001 after Phase 5 (E2E golden regen).

## Battery

- [x] CHK019 Wake locks. None introduced.
- [x] CHK020 Alarms / Doze bypass. None introduced. BootCheck is one-shot on `BOOT_COMPLETED`.

---

## Summary

- **Pass**: 16/20
- **Warn**: 4/20 (CHK002 bootstrap parse cost, CHK006 Provider threading contract, CHK007 validator cost bound, CHK008 receiver-vs-WorkManager, CHK018 perf checkpoint in tasks.md)
- **Fail**: 0/20

Net: refactor is perf-neutral or positive (assets deleted, engines collapsed). Two plan-time items: (a) declare threading contract for `Provider.check/apply`, (b) resolve whether `BootCheckReceiver` runs `ReconcileEngine` inline or dispatches to WorkManager.
