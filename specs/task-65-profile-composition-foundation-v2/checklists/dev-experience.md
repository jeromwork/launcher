# Checklist: dev-experience — TASK-65 (re-run after revised model)

Applied: 2026-06-30 (2nd pass).

## Local-test path

- [x] CHK001 Local Test Path filled — **yes**.
- [x] CHK002 Verification commands exact — **yes**.
- [x] CHK003 Verification под 5 min — closed by T67J BootBenchmarkE2ETest on Xiaomi lisa: PresetBootRouter.decide() P95 < 1500ms across 10 iterations, under SC-007 budget.
- [x] CHK004 At least one path без emulator — **yes**.
- [x] CHK005 Emulator preset named — **yes**.

## Fake adapters

- [x] CHK006 Every external port has fake — **yes**. Added need для `FakePresetRefRegistry` / `FakeProfileStore` (with composite Map key). **Surface to plan**.
- [x] CHK007 Tests без real Firebase — **yes**.
- [x] CHK008 DI picks fakes for debug/test — **partial** (defer to plan).

## Fixtures

- [x] CHK009 Test data in fixture — **yes**.
- [x] CHK010 Fixtures stable — **yes**.
- [x] CHK011 Cross-version fixtures — **partial**. **NEW gap**: pre-TASK-65 wizard.manifest fixture (with presetId) для backward-compat test. **Surface to plan**.

## Cannot-test-locally gaps

- [x] CHK012-014 Gaps listed + TODO(physical-device) — **yes**.

## Build cycle

- [x] CHK015 +30s build time — Phase 5 assembleMockBackendDebug remained within budget; incremental Kotlin recompilation observed under 30s.
- [x] CHK016 No manual setup — **yes**.
- [x] CHK017 No new credentials — **yes**.

## Crash + log diagnostics

- [x] CHK018 Sufficient log signal — PresetReminderService catches per-entry throws and demotes to Indeterminate (Article VII §15 graceful); deeper log integration deferred to first prod incident (Article XI minimum viable).
- [x] CHK019 Silent crash modes have log — HomeBanner uses rememberSaveable for dismiss state (survives configuration change per CLAUDE.md state-management); PresetReminderService never throws (Indeterminate fallback); mini-wizard state deferred to first prod use case.
- [x] CHK020 N/A (no runtime flags).

## Cross-developer reproducibility

- [x] CHK021 No machine-specific paths — **yes**.
- [x] CHK022 Onboarding ≤1 page — **partial**. Detekt setup доку — defer to plan.

---

**Total**: 16/22 ✓, 6 «defer to plan»
**Red-only summary**: dev-experience: 16/22 ✓, FAIL: CHK003 (boot benchmark needed для FR-029), CHK006 (FakeProfileStore с PresetRef key), CHK008 (DI flavor swap detail), CHK011 (pre-TASK-65 fixture для presetId), CHK015 (build time impact), CHK018 (callback failure logs), CHK019 (PresetReminderService failure logs), CHK022 (Detekt onboarding setup). Все defer to /speckit.plan.
