# Checklist: dev-experience

Applied: 2026-07-15 (re-run after spec.md added `## Local Test Path` section)
Spec: `specs/task-127-ecs-tags-and-query/spec.md`

## Local-test path

- [x] CHK001 Spec's `## Local Test Path` section is filled in (lines 197-203) with unit tests, fitness function, instrumented tests, manual verification, physical device path.
- [x] CHK002 Verification commands are exact: `./gradlew :core:test --tests "*ProfileQueryTest*"`, `./gradlew :core:test --tests "*ComponentTagsFitnessTest*"`, `./gradlew :core:connectedAndroidTest --tests "*HomeComponentLoadingStateTest*"`, `./gradlew :app:installMockBackendDebug`.
- [x] CHK003 Test scope (unit + Robolectric) fits <5 min laptop cycle.
- [x] CHK004 US2 Independent Test = pure unit test on Profile query; US1 Independent Test = Robolectric integration (JVM, no emulator).
- [x] CHK005 SC-001 physical device is separately flagged; primary local path avoids emulator. Manual verification step names `installMockBackendDebug` (no specific AVD preset required — any working emulator per skill `android-emulator`).

## Fake adapters

- [x] CHK006 `FakeProfileStore` implied by NFR-002 test description; `FakeConfigEditor` mentioned in Assumptions as unchanged. Existing test fakes reused.
- [x] CHK007 No real Firebase/Cloudflare/FCM dependency introduced.
- [x] CHK008 FR-007 mandates DI wiring in both mockBackend + realBackend flavors — split respected.

## Fixtures

- [x] CHK009 Migration test (SC-003) implies v2 Profile fixture checked in.
- [x] CHK010 No `now()` / `Random()` mentioned; Profile fixtures deterministic.
- [x] CHK011 v2 Profile fixture required by SC-003 roundtrip test — explicit cross-version fixture.

## Cannot-test-locally gaps

- [x] CHK012 SC-001 explicitly names Xiaomi Redmi Note 11 physical verification as gap; `## Local Test Path` line 203 restates it as separate step.
- [ ] CHK013 PARTIAL — SC-001 flags physical device but no explicit `TODO(physical-device)` inline TODO mandated for code. Recommend adding to plan phase.
- [x] CHK014 No silent sweep — physical gate is a first-class SC-001 and listed in `## Local Test Path` as separate step.

## Build cycle

- [x] CHK015 Additive change (new enum, new methods on Profile, one new adapter class) — negligible build impact.
- [x] CHK016 No new manual setup (no Firebase console, no external provisioning).
- [x] CHK017 No credential / API key introduced.

## Crash + log diagnostics

- [ ] CHK018 PARTIAL — spec doesn't mandate log tag for `ProfileBackedFlowRepository` transitions (null→non-null Profile emissions). Recommend adding in plan.
- [x] CHK019 Edge case "ProfileStore.observe() emits null" → contract is explicit (stay in Loading), not silent.
- [x] CHK020 N/A — no runtime feature flag introduced.

## Cross-developer reproducibility

- [x] CHK021 No machine-specific paths / env vars. adb id `17f33878` cited as example, not requirement.
- [x] CHK022 Onboarding covered by existing repo docs (no new setup required).

**Result**: 20/22 passed, 2 open items (recommendations, not blockers):
- CHK013 recommend inline `TODO(physical-device)` in code touched by ProfileBackedFlowRepository DI wiring.
- CHK018 recommend log tag on ProfileBackedFlowRepository state transitions.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Re-run после добавления `## Local Test Path` секции в spec.md (lines 197-203) с точными gradle-командами. 20/22 pass (было 18/22). Два blocker'а CHK001+CHK002 закрыты. Осталось 2 recommendation'а (не блокеры) — inline `TODO(physical-device)` в код + log tag на `ProfileBackedFlowRepo` для диагностики.

**Конкретика:**
- CHK001 + CHK002 → `[x]` (Local Test Path section с командами `./gradlew :core:test --tests "*ProfileQueryTest*"`, `./gradlew :core:connectedAndroidTest --tests "*HomeComponentLoadingStateTest*"`, physical device path на Xiaomi 17f33878).
- CHK013 остался PARTIAL — inline `// TODO(physical-device)` в код должен появиться в plan/tasks phase.
- CHK018 остался PARTIAL — log tag `ProfileBackedFlowRepo` для null→non-null Profile transitions должен появиться в plan.
- Physical device gap flagged (CHK012, CHK014), no silent sweep.

**На что смотреть с осторожностью:**
- TODO'шки CHK013/CHK018 должны попасть в plan phase → tasks.md, иначе потеряются.
