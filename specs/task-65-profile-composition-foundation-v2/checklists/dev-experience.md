# Checklist: dev-experience — TASK-65 Preset Composition Foundation v2

Applied: 2026-06-30.

## Local-test path

- [x] CHK001 Local Test Path filled — **yes**. Detailed section в spec.md с emulator preset, fakes list, fixtures, verification commands.
- [x] CHK002 Verification commands exact — **yes**. 8 точных команд (`./gradlew :core:test --tests "*PresetComposition*"`, `./gradlew detektFoundation`, etc.).
- [ ] CHK003 Verification под 5 минут на laptop (cold cycle) — **NEEDS PLAN VALIDATION**. Spec не оценивает время. **Surface to plan** (skill `procedure-constitution-check` Gate 7 проверит).
- [x] CHK004 At least one path verifiable без emulator — **yes**. JVM unit tests для domain logic (`PoolSourceRoundtripTest`, `EngineGenericityFitnessTest`, Detekt rule tests).
- [x] CHK005 Emulator preset named — **yes**. `pixel_5_api_34` через skill `android-emulator`.

## Fake adapters

- [x] CHK006 Every external port has fake — **yes**. `FakeConfigSource`, `FakePoolSource`, `FakeUserPreferencesStore`, `FakeSystemSettingPort` listed in Local Test Path.
- [x] CHK007 Tests не требуют real Firebase / Cloudflare / FCM — **yes**. TASK-65 isolated от backend (per Constitution Gates → rule 8 N/A).
- [x] CHK008 DI picks fakes for debug/test — **partial**. **NEEDS PLAN DETAIL**: build flavor configuration для swap. Existing project уже имеет `mockBackend` / `realBackend` flavors — TASK-65 наследует.

## Fixtures

- [x] CHK009 Test data in checked-in fixture — **yes**. `test-preset.json`, `wizard-simple-launcher-golden.json` (FR-018/020/023/025).
- [x] CHK010 Fixtures stable — **yes**. Fixed JSON files, никаких `Random()`/`now()` в их генерации.
- [x] CHK011 Cross-version fixtures для wire format — **partial**. `wizard-simple-launcher-golden.json` (current) есть, **pre-TASK-65 wizard.manifest fixture с `appFamilyId`** для backward-compat test — surface to plan (см. wire-format CHK011).

## Cannot-test-locally gaps

- [x] CHK012 Gaps explicitly listed — **yes**. OEM Matrix + Local Test Path → Cannot-test-locally gaps subsection: Xiaomi MIUI (`TODO(physical-device)`), Samsung One UI, Huawei EMUI.
- [x] CHK013 Each gap has inline TODO — **yes** в spec.md. **Surface to plan**: убедиться что TODOs попали в реальный код когда implementation начнётся.
- [x] CHK014 No gap «we'll test in prod» — **yes**. Все gaps в TODO(physical-device) с конкретными verification targets.

## Build cycle

- [ ] CHK015 Clean-build time +30s — **NEEDS PLAN VALIDATION**. Detekt rules + new packages — minor увеличение. **Surface to plan** (Gradle build time profiling).
- [x] CHK016 One-time manual setup documented — **N/A**. TASK-65 не требует manual setup beyond standard `./gradlew` + `android-emulator` skill.
- [x] CHK017 No new credentials/API keys для debug builds — **yes**. TASK-65 isolated от backend, no credentials needed.

## Crash + log diagnostics

- [ ] CHK018 Sufficient log signal — **NEEDS PLAN DETAIL**. Spec не уточняет logging strategy. **Surface to plan**: Logcat tags для `PresetSwitchService`, `PresetBootRouter`, `ConfigSource`, `PoolSource`.
- [ ] CHK019 Silent crash modes have opt-in log — **NEEDS PLAN DETAIL**. Background coroutines в reminder check, profile switch persistence — нужны failure logs. **Surface to plan**.
- [x] CHK020 Runtime feature flags loggable — **N/A**. TASK-65 не вводит runtime flags (только Gradle build flavor для PoolSource swap, statically logged at startup).

## Cross-developer reproducibility

- [x] CHK021 No developer-machine-specific paths — **yes**. Spec generic, не embed personal paths.
- [x] CHK022 Onboarding ≤1 page — **partial**. Existing project имеет `docs/dev/dev-environment.md`; TASK-65 не добавляет нового setup beyond Detekt config. **Surface to plan**: добавить Detekt setup инструкцию в onboarding если ещё нет.

---

**Total**: 15/22 ✓, 7 «surface to plan» / «needs plan detail»
**Red-only summary**: dev-experience: 15/22 ✓, FAIL: CHK003 (cold cycle time), CHK008 (DI flavor swap detail), CHK011 (pre-TASK-65 fixture missing), CHK015 (build time impact), CHK018 (Logcat tags), CHK019 (background coroutine crash logs), CHK022 (onboarding Detekt setup). Все — defer to /speckit.plan, не блокеры spec.
