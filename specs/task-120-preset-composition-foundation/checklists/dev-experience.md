# Checklist: dev-experience — TASK-120 spec.md

**Target**: `specs/task-120-preset-composition-foundation/spec.md`
**Date**: 2026-07-10
**Stage**: post-clarify, pre-plan

## Context

Foundation spec for Component / Preset / Profile composition model. Claims JVM-only test path — no emulator required for foundation. Downstream tasks (draft-1 wizard, TASK-71, TASK-69, TASK-68, TASK-19) own their own emulator / device gaps.

Property-based test (SC-011) requires `kotest-property`. Verified present in `gradle/libs.versions.toml:203` (`kotest-property = { group = "io.kotest", name = "kotest-property", version.ref = "kotest" }`). Already consumed by `core/crypto` and `core/keys` modules — mature usage pattern in-repo.

Ports enumerated in FR list: `PoolSource` (FR-002), `PresetSource` (Key Entities), `Provider` (FR-006), `ProviderRegistry` (FR-007), `ProfileStore` (FR-013), `InteractionSink` (FR-010), `ConditionEvaluator` (FR-020, seam), `PackageManagerFacade` (FR-014).

---

## Results

### Local-test path

- [x] **CHK001** "Local Test Path" section is filled — real content, not placeholder. Lists fakes, fixtures, verification commands, gaps.
- [x] **CHK002** Verification commands are exact: `./gradlew :core:test --tests "com.launcher.preset.*"`, `./gradlew :core:test --tests "com.launcher.preset.fitness.*"`, `./gradlew :app:testDebugUnitTest`. Not "run the tests".
- [x] **CHK003** All three are pure JVM Gradle unit-test runs. On a warm daemon each stays sub-minute; even cold, well under the 5-minute budget for a core/ + app testDebugUnitTest cycle. Foundation has no instrumentation / no APK build in the loop.
- [x] **CHK004** Foundation is 100% JVM. Every FR (engine, factory, registry, diff, wire format, condition evaluator seam) is verifiable in `commonTest` / JVM unit tests. Explicitly declared.
- [x] **CHK005** N/A — foundation declares "no emulator required". Downstream Provider-implementation specs will name presets. Correctly deferred.

### Fake adapters

- [~] **CHK006** Fakes cover most ports but **the list in "Local Test Path" is incomplete relative to the FR/Key-Entities set**:
  - Covered: `FakePoolSource`, `FakePresetSource`, `FakeProfileStore`, `FakeInteractionSink`, `FakeProvider<T>`.
  - Missing from the fake-adapter list: `FakeConditionEvaluator` (needed for FR-020 / US6 acceptance #2), `FakePackageManagerFacade` (mentioned once in SC-007 but not listed here), `FakeProviderRegistry` (or a real Registry wired with fakes — spec should say which pattern).
  - **Spurious**: `FakeAuthHandoffService` is listed but `AuthHandoffService` is explicitly deferred to TASK-121 (FR-014, Downstream Contract). Should be removed from TASK-120 scope.
  - Recommendation: add the three missing fakes explicitly and drop `FakeAuthHandoffService`.
- [x] **CHK007** No real Firebase / Cloudflare / FCM required. All external touch-points (PackageManager, Android system fonts, WorkManager) go through ports with fakes. Wire format tests are pure serialization.
- [~] **CHK008** DI wiring described at FR-017 (Hilt `@IntoMap` with custom `@ComponentKey`) but spec **does not describe how fakes are swapped for reals in `test` vs `release`**. Verification command `:app:testDebugUnitTest` implies a Hilt test module exists, but the swap pattern is not documented in the spec. Not fatal — plan.md can specify — but the spec should at least assert "test module overrides Provider bindings with fakes".

### Fixtures

- [x] **CHK009** Fixtures enumerated as checked-in JSON files under `core/src/test/resources/fixtures/`: `pool-v1.json`, `preset-simple-launcher-v2.json`, `preset-workspace-v2.json`, `profile-partial-applied.json`. Realistic KMP-JVM path (`core/src/test/resources/` is standard for `jvmTest` / `commonTest` with JVM target).
- [~] **CHK010** Stability partially addressed: fixed JSON fixtures are stable by construction. **But SC-011 uses `Arb.preset()` (kotest-property) generating 100 random combinations** — property tests need a **seeded** `Arb` or an explicit `PropTestConfig(seed = …)` to be reproducible across runs. Spec doesn't call this out. Also nothing states that `Clock`/`now()` is injected via a `TimeProvider` port for `preWizardSnapshot` 7-day expiry (FR-024) — currently reads like `Instant.now()` at call site, which would flake the FR-024 tests. Recommendation: add "fixed `Clock` / `TimeProvider` port; property tests use fixed `PropTestConfig` seed".
- [~] **CHK011** Cross-version fixture partial. `pool-v1.json` is named for v1, but Preset fixtures are v2. Fitness function #5 in SC-004 is called "backward-compat pool v1→v2" — good, requires the v1 fixture. **However there is no v1 preset fixture** to protect the Preset wire format across schema bumps. FR-016 promises additive-only + migration writer, but spec doesn't commit to a `preset-v1.json` (or older) fixture. Since this is the foundation shipping v2 as the first version, a `preset-v2-canonical.json` frozen as "the v2 sample" that a future v3 must read is the minimum. Add "preset-v2 canonical sample kept immutable as backward-compat anchor" to fixtures.

### Cannot-test-locally gaps

- [x] **CHK012** Gaps section says `none` for foundation. Justified: pure Kotlin domain + JVM tests. Explicit statement.
- [x] **CHK013** N/A — no `TODO(physical-device)` / `TODO(real-account)` needed since there are no such gaps at foundation level. Downstream adapter specs will introduce them.
- [x] **CHK014** No "test in prod" claims. Every SC binds to a JVM unit test or fitness function.

### Build cycle

- [x] **CHK015** Foundation adds a pure-Kotlin module (`core/preset/`) — no annotation processor changes beyond existing Hilt, no new plugins. Clean-build impact bounded to compilation of ~15-20 new Kotlin files + kotest-property (already in `core/crypto` / `core/keys` classpath). Well under 30s.
- [x] **CHK016** No manual setup step described. No "enable X in Firebase console" instruction. Bundled `pool.json` + `preset-*.json` ship in `assets/` and `test/resources/`.
- [x] **CHK017** No new credential / API key / service-account required for `debug`. Everything runs off checked-in fixtures.

### Crash + log diagnostics

- [ ] **CHK018** **FAIL** — spec does not describe any logging surface for the engine. No mention of Logcat tag, no structured log fields for `ReconcileEngine.run()`, no diagnostic output when `Provider.apply` returns `Failed`. Edge case "Provider returns Failed(reason)" says status updates in Profile, but reason isn't declared as logged. A developer debugging why a step went `Failed` in a downstream feature would have to attach a debugger. Recommendation: add "engine emits a structured log per step: `preset.reconcile step=<id> mode=<Wizard|…> outcome=<Ok|NeedsApply|Failed(reason)|Unsupported>`".
- [ ] **CHK019** **FAIL** — `preWizardSnapshot` cleanup (FR-024) runs opportunistically ("7 суток после последнего Wizard-run"). This is exactly the kind of background job that dies silently. No opt-in log line specified. Recommendation: log every `preWizardSnapshot` expire / commit.
- [ ] **CHK020** **FAIL** — no runtime feature flags declared here, but the seam-only `ConditionEvaluator` (FR-020) with the hardcoded `device.hasGms` rule is effectively a feature-flag proxy at runtime. Spec doesn't say the current evaluator state is loggable on demand. If a downstream wizard step gets silently skipped because `device.hasGms=false`, a developer needs to see it in Logcat. Recommendation: log `condition.evaluate expr=<…> result=<bool|Unsupported> → step=<id> visible=<bool>`.

### Cross-developer reproducibility

- [x] **CHK021** No hard-coded developer-machine paths / env vars / phone numbers. Fixtures are checked in; verification commands are `./gradlew` relative to repo root.
- [x] **CHK022** Onboarding is trivially covered by the three `./gradlew` commands in Local Test Path plus fixtures in the repo. No separate `docs/dev/dev-environment.md` section needed for this feature.

---

## Summary

**17/22 PASS** (3 caveats marked `[~]` counted as PASS with recommendations, 3 FAIL on diagnostics).

**PASS**: CHK001, CHK002, CHK003, CHK004, CHK005, CHK006 (with caveat), CHK007, CHK008 (with caveat), CHK009, CHK010 (with caveat), CHK011 (with caveat), CHK012, CHK013, CHK014, CHK015, CHK016, CHK017, CHK021, CHK022.

**FAIL**:
- **CHK018** — no structured logging surface for `ReconcileEngine` step outcomes.
- **CHK019** — silent background job (`preWizardSnapshot` 7-day expiry) has no opt-in log line.
- **CHK020** — `ConditionEvaluator` seam has no on-demand loggability for current evaluation.

**Caveats to fix in plan.md / tasks.md**:
1. Add `FakeConditionEvaluator`, `FakePackageManagerFacade`, `FakeProviderRegistry` to the "Fake adapters used" list. Drop `FakeAuthHandoffService` (deferred to TASK-121).
2. Declare fixed `Clock` / `TimeProvider` port for FR-024 expiry and a `PropTestConfig(seed = ...)` for SC-011 property tests — stability guarantee.
3. Freeze a `preset-v2-canonical.json` fixture as the immutable backward-compat anchor for the first schema bump.
4. Describe the Hilt test-module override pattern for swapping `Provider<*>` bindings to fakes.

**Overall**: foundation is genuinely locally testable (JVM only, checked-in fixtures, concrete Gradle commands, kotest-property present). The three logging FAILs and four caveats are cheap to fix — add three log-line contracts, one `TimeProvider` port, and tighten the fake-adapter list before `/speckit.plan`.

---

## Plain Russian summary

Foundation-спека TASK-120 действительно проверяется локально на JVM без эмулятора. Gradle-команды точные, fixtures лежат в стандартном месте `core/src/test/resources/fixtures/`, kotest-property (для property-теста SC-011) уже в `gradle/libs.versions.toml` и используется в `core/crypto` / `core/keys`. Три провала — про диагностику: engine не логирует исходы шагов, фоновая очистка `preWizardSnapshot` тихо умирает без лога, состояние `ConditionEvaluator` не видно в Logcat. Также в списке fakes лишний `FakeAuthHandoffService` (это территория TASK-121), а нужных `FakeConditionEvaluator` / `FakePackageManagerFacade` — нет. Property-тест SC-011 должен получить фиксированный seed, иначе будет флейкать; для FR-024 (7-суточный snapshot expiry) нужен порт `TimeProvider`. Первый v2 preset стоит заморозить как эталонный fixture — чтобы будущий v3 обязан был его читать.
