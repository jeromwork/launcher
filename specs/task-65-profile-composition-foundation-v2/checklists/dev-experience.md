# Checklist: dev-experience — TASK-65 (re-run after revised model)

Applied: 2026-06-30 (2nd pass).

## Local-test path

- [x] CHK001 Local Test Path filled — **yes**.
- [x] CHK002 Verification commands exact — **yes**.
- [ ] CHK003 Verification под 5 min — **defer to plan**. **NEW concern**: boot-time settings check (FR-029) adds N callbacks to boot — must benchmark to confirm SC-007 (≤1.5s) holds.
- [x] CHK004 At least one path без emulator — **yes**.
- [x] CHK005 Emulator preset named — **yes**.

## Fake adapters

- [x] CHK006 Every external port has fake — **yes**. Added need для `FakePresetRefRegistry` / `FakeProfileStore` (with composite Map key). **Surface to plan**.
- [x] CHK007 Tests без real Firebase — **yes**.
- [x] CHK008 DI picks fakes for debug/test — **partial** (defer to plan).

## Fixtures

- [x] CHK009 Test data in fixture — **yes**.
- [x] CHK010 Fixtures stable — **yes**.
- [x] CHK011 Cross-version fixtures — **partial**. **NEW gap**: pre-TASK-65 wizard.manifest fixture (with appFamilyId) для backward-compat test. **Surface to plan**.

## Cannot-test-locally gaps

- [x] CHK012-014 Gaps listed + TODO(physical-device) — **yes**.

## Build cycle

- [ ] CHK015 +30s build time — **defer to plan**.
- [x] CHK016 No manual setup — **yes**.
- [x] CHK017 No new credentials — **yes**.

## Crash + log diagnostics

- [ ] CHK018 Sufficient log signal — **defer to plan**. **NEW need**: boot-time callback failures must log (which entry failed, why) to diagnose degraded boot.
- [ ] CHK019 Silent crash modes have log — **defer to plan**. **NEW need**: `PresetReminderService` (banner display + mini-wizard launch) — failure modes (banner click during Activity recreate, mini-wizard process kill) must log.
- [x] CHK020 N/A (no runtime flags).

## Cross-developer reproducibility

- [x] CHK021 No machine-specific paths — **yes**.
- [x] CHK022 Onboarding ≤1 page — **partial**. Detekt setup доку — defer to plan.

---

**Total**: 16/22 ✓, 6 «defer to plan»
**Red-only summary**: dev-experience: 16/22 ✓, FAIL: CHK003 (boot benchmark needed для FR-029), CHK006 (FakeProfileStore с PresetRef key), CHK008 (DI flavor swap detail), CHK011 (pre-TASK-65 fixture для appFamilyId), CHK015 (build time impact), CHK018 (callback failure logs), CHK019 (PresetReminderService failure logs), CHK022 (Detekt onboarding setup). Все defer to /speckit.plan.
