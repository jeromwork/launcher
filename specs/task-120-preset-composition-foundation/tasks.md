# Tasks: Preset Composition Foundation (TASK-120)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Data model**: [data-model.md](data-model.md) | **Contracts**: [contracts/](contracts/)
**Backlog**: [task-120](../../backlog/tasks/task-120%20-%20Decision-Preset-conditional-composition-via-visibleIf-JsonLogic.md)
**Created**: 2026-07-10

Task IDs use `Tnnn` local to this spec. `[P]` = parallel-safe. `[deferred-*]` markers = AI session cannot close directly.

MVP wave: 4 Component subtypes — `AppTile`, `FontSize`, `Sos`, `Toolbar`.

---

## Phase 1 — Domain types (`core/preset/model/`, commonMain)

Zero Android imports. All types `@Serializable` where JSON-bound.

- [ ] **T001** `Component.kt` — sealed hierarchy with 4 MVP subtypes (`AppTile`, `FontSize`, `Sos`, `Toolbar`), `@SerialName` discriminator, kotlinx.serialization polymorphic. (FR-001, data-model.md §1)
  - Acceptance: sealed subclasses list = 4; roundtrip `Json.encodeToString(AppTile(...))` produces `{"type":"AppTile",...}`.

- [ ] **T002** [P] `Outcome.kt` + `FailReason.kt` — sealed hierarchies with `toI18nKey()` mapping on FailReason. (FR-008, data-model.md §6-7)
  - Acceptance: `FailReason.PermissionDenied("android.permission.CAMERA").toI18nKey() == "outcome.failed.permission_denied"`.

- [ ] **T003** [P] `WizardBehavior.kt` + `ComponentStatus.kt` + `RunMode.kt` + `Vendor.kt` — enums. (FR-009, FR-010, FR-018, data-model.md §5, §10)
  - Acceptance: each enum has expected variants (WizardBehavior=3, ComponentStatus=4, RunMode=4, Vendor=6).

- [ ] **T004** [P] `CapabilityFlag.kt` + `ValidationError.kt` — sealed hierarchies with `toI18nKey()` on ValidationError. (FR-027, data-model.md §8-9)
  - Acceptance: `CapabilityFlag` has one MVP variant (`CloudSession`); `ValidationError` has 4 variants.

- [ ] **T005** [P] `HandlerKey.kt` + `ChangeItem.kt` — data class + sealed. (FR-007, data-model.md §10-11)
  - Acceptance: `HandlerKey(KClass, platform?, vendor?)` equality/hashCode work for map lookup.

- [ ] **T006** `ComponentDeclaration.kt` + `Pool.kt` — pool entry + typed catalog with `schemaVersion=1`. (FR-002, contracts/pool.md, data-model.md §2-3)
  - Acceptance: `Pool.byId("font-tile")` returns declaration; unknown id returns null.

- [ ] **T007** `Preset.kt` + `WizardFlowEntry.kt` + `SettingsMapEntry.kt` + `ActiveComponentEntry.kt` + `Sensitivity.kt` — three-field split, `schemaVersion=2`. (FR-003, FR-004, contracts/preset.md, data-model.md §4)
  - Acceptance: `Preset` parses `simple-launcher.json` fixture; fields in three lists distinct.

- [ ] **T008** `Profile.kt` + `ProfileComponent.kt` + `ProfileState.kt` — `schemaVersion=2` + `preWizardSnapshot: Profile?` + opaque state holder. (FR-013, FR-024, FR-029, contracts/profile.md, data-model.md §5)
  - Acceptance: `Profile(components=[...], preWizardSnapshot=null)` serializes/deserializes; nested snapshot's own snapshot is null.

## Phase 2 — Domain ports (`core/preset/port/`, commonMain)

All interfaces, zero implementations except NoOp default.

- [ ] **T009** `Provider.kt` port + `NoOpProvider.kt` domain default. Inline TODO(capability-registry) per plan.md §2. (FR-006, contracts/provider-port.md)
  - Acceptance: `NoOpProvider.check(any, any) == Outcome.Unsupported`.

- [ ] **T010** `ProviderRegistry.kt` port + reference resolve implementation with 3-tier fallback (vendor → platform → NoOp). (FR-007, contracts/provider-port.md)
  - Acceptance: registry unit test: fake vendor-specific hit, fake platform-generic fallback, missing → NoOp.

- [ ] **T011** [P] `PoolSource.kt` + `PresetSource.kt` ports. Inline TODO(shareability) per plan.md §2. (FR-002, FR-003)
  - Acceptance: ports compile with `suspend` signatures.

- [ ] **T012** [P] `ProfileStore.kt` port — `load / save / setPreWizardSnapshot / restoreFromPreWizardSnapshot`. (FR-013, FR-024, FR-029)
  - Acceptance: port compiles.

- [ ] **T013** [P] `InteractionSink.kt` port — `suspend fun askUser(component: ProfileComponent): Component?`. (FR-010)
  - Acceptance: port compiles with nullable return (skip = null).

- [ ] **T014** [P] `ConditionEvaluator.kt` port + minimal MVP adapter (`ProfileStateConditionEvaluator`) supporting only `{"var": "profile.state.<flag>"}`. (FR-020, US6)
  - Acceptance: adapter evaluates `{"var":"profile.state.cloud"}` correctly against ProfileState with/without flag; other JsonLogic ops throw or return false with logged Unsupported.

- [ ] **T015** [P] `CapabilityQuery.kt` port + `Evidence` sealed. (FR-027, contracts/capability-ports.md)
  - Acceptance: port compiles; `Evidence.Token`, `Evidence.Hash`, `Evidence.Marker` variants present.

- [ ] **T016** [P] `CapabilityContract.kt` port — `requires` + `provides`. (FR-027, contracts/capability-ports.md)
  - Acceptance: port compiles.

- [ ] **T017** [P] `LocalizedResources.kt` port. (FR-026)
  - Acceptance: port compiles with `resolve(key: String, args: Map<String,String>): String`.

- [ ] **T017b** [P] `PairingService.kt` port + `PairingId` value class + `FakePairingService` in commonTest. (FR-030)
  - Acceptance: port compiles with `suspend fun currentAdmin(): PairingId?`; fake supports canned response for tests. Real Android adapter deferred to TASK-67.

## Phase 3 — Domain engine (`core/preset/engine/`, commonMain)

- [ ] **T018** `ProfileFactory.kt` — `create(preset, pool) → Profile`; resolves poolRef, applies paramsOverride, initializes statuses. (FR-005)
  - Acceptance: unit test with fake pool + preset produces expected Profile with resolved components + `Pending` status.

- [ ] **T019** `PresetValidator.kt` — walks wizardFlow, checks ordering, returns ValidationError list. (FR-027, US 5.5, contracts/capability-ports.md)
  - Acceptance: valid → empty; malformed → `CapabilityMissing`; unknown ref → `UnknownPoolRef`; schema too high → `SchemaVersionUnsupported`.

- [ ] **T020** `ReconcileEngine.kt` — `run(RunMode, InteractionSink?)`, filters per mode, dispatches through registry, updates Profile after each step. Inline TODO(capability-registry). (FR-006, FR-010)
  - Acceptance: 4 RunMode tests (Wizard/BootCheck/Single/RemotePush) with fake providers pass expected outcomes.

- [ ] **T021** `PresetDiff.kt` — `diff(current, incoming, pool) → List<ChangeItem>`. (FR-011)
  - Acceptance: unit test: Added / Removed / ParamsChanged classifications correct on 3 synthetic pairs.

## Phase 4 — Test fakes (`core/preset/commonTest/fakes/`)

- [ ] **T022** [P] `FakePoolSource` + `FakePresetSource` — deterministic returns from constructor args. (rule 6 mock-first)
  - Acceptance: fakes compile in commonTest, used by engine tests.

- [ ] **T023** [P] `FakeProfileStore` — `MutableStateFlow<Profile>` backed in-memory with preWizardSnapshot support. (rule 6)
  - Acceptance: setPreWizardSnapshot + restoreFromPreWizardSnapshot roundtrip.

- [ ] **T024** [P] `FakeInteractionSink` — canned answers keyed by componentId. (rule 6)
  - Acceptance: `askUser(component)` returns canned answer if provided, null otherwise.

- [ ] **T025** [P] `FakeProvider<T>` — configurable Outcome per invocation; `FakeCapabilityQuery` (in-memory flag set); `FakeCapabilityContract` (map-backed). (rule 6)
  - Acceptance: fakes support fluent config for engine tests.

- [ ] **T026** [P] `FakeLocalizedResources` — canned key→string map. (rule 6, FR-026)
  - Acceptance: `resolve("missing.key")` returns key itself (fallback for tests).

- [ ] **T027** [P] Fake test Components: `FakeSignIn(provides=CloudSession)`, `FakeCloudConsumer(requires=CloudSession)` — subclasses of `Component` used only in tests to exercise Capability model.
  - Acceptance: fake components live in `commonTest/fakes/`, NOT in production Component sealed hierarchy.

## Phase 5 — Wire format tests (`core/preset/commonTest/roundtrip/`)

- [ ] **T028** [P] Pool roundtrip test — encode/decode `assets/pool.json` fixture, assert equality. (SC-005, fitness #4, contracts/pool.md)
  - Acceptance: `PoolRoundtripTest.testRoundtrip` passes.

- [ ] **T029** [P] Preset roundtrip test — all 3 bundled seed fixtures. (SC-005, fitness #4, contracts/preset.md)
  - Acceptance: 3 test cases, each roundtrips.

- [ ] **T030** [P] Profile roundtrip test — synthetic Profile with preWizardSnapshot. (SC-005, fitness #4, contracts/profile.md)
  - Acceptance: encode/decode/equal with all fields populated.

- [ ] **T031** [P] Backward-compat scaffolding — fixture `pool-v1-legacy.json` (identical to v1 since we start at v1) + skeleton test asserting future v2 loader still reads v1. Placeholder until second schemaVersion arrives. (SC-008, fitness #5)
  - Acceptance: test present, marked `@Ignore` with comment, unfreezes when v2 introduced.

## Phase 6 — Fitness functions (`core/preset/commonTest/fitness/`)

- [ ] **T032** [P] Fitness #1 — Import guard: reflection test scans `core/preset/engine/**` for `import com.launcher.preset.model.Component.<subtype>` — fails if found. (plan.md §6.6)
  - Acceptance: passes on current codebase; test manually broken by adding forbidden import fails.

- [ ] **T033** [P] Fitness #2 — `when`-guard: reflection / regex test in engine dir for `when(...component)` on concrete subtypes. (plan.md §6.6)
  - Acceptance: passes; manual break fails.

- [ ] **T034** Fitness #3 — Coverage Component ↔ Provider: reflection test iterates `Component::class.sealedSubclasses`, verifies each has non-NoOp Provider in test DI graph. (SC-010, plan.md §6.6)
  - Acceptance: passes for 4 MVP subtypes; test with orphan subtype fails.
  - Depends on: T024 (FakeProvider), T009 (Provider port).

- [ ] **T035** [P] Fitness #6 — Cross-provider isolation: static check `provider/foo/**` MUST NOT import `provider/bar/**`. (plan.md §6.6)
  - Acceptance: passes; test manually broken by cross-import fails. Applies to `app/androidMain/provider/*` — placeholder test on domain package for now, actual grep on Android module in T056.

- [ ] **T036** Fitness #7 — paramsOverride schema validation: JSON Schema per Component declaration + `mutable: true` allowlist check. (FR-004, plan.md §6.6)
  - Acceptance: schema files per Component in `core/preset/commonTest/schemas/`; test validates 3 valid overrides + 3 invalid override attempts.

- [ ] **T037** Fitness #8 — Anti-explosion pool limit: test loads `pool.json`, groups by Component subtype, warns at 4-5 declarations, errors at ≥6. (FR-025, plan.md §6.6)
  - Acceptance: MVP pool with 4 subtypes × ≤3 declarations passes; synthetic pool with 6 FontSize declarations fails.

- [ ] **T038** Fitness #9 — PresetValidator coverage: three canonical scenarios (valid ordering, malformed CapabilityMissing, optional path). (SC-014, plan.md §6.6)
  - Acceptance: `PresetValidatorTest` passes with 3 scenarios + 2 additional (UnknownPoolRef, SchemaVersionUnsupported).
  - Depends on: T019 (PresetValidator), T025 (FakeCapabilityContract), T027 (fake test Components).

- [ ] **T039** [P] Fitness #10 — No-literal-strings in user-facing wire format: regex-based JSON validator scans `label`, `description`, `wizardTitle`, `category` fields (without `Key` suffix). (FR-026, plan.md §6.6)
  - Acceptance: test on `pool.json` passes; synthetic pool with `"label":"Видеозвонок"` (no Key) fails.

## Phase 7 — Property-based test

- [ ] **T040** Add `io.kotest:kotest-property:5.9+` to `core/preset` commonTest gradle dependencies. Verify build. (plan.md §5)
  - Acceptance: `./gradlew :core:preset:build` succeeds; new dep visible in build report.

- [ ] **T041** `Arb.preset(pool)` generator + property-based test with N=100 iterations. (SC-011, plan.md §6.5)
  - Acceptance: test runs 100 random valid preset compositions through ProfileFactory + ReconcileEngine(Wizard) with FakeInteractionSink; assertions: every ProfileComponent reaches terminal status, no exception, Profile roundtrip bit-identical.
  - Depends on: T040 (dep), T018 (ProfileFactory), T020 (ReconcileEngine), T024 (FakeInteractionSink), T025 (FakeProvider).

## Phase 8 — Engine tests

- [ ] **T042** ReconcileEngine per-RunMode tests — 4 test methods covering each RunMode branch semantic. (FR-010, plan.md §6.3)
  - Acceptance: 4 tests pass with fake providers.
  - Depends on: T020, T022, T024, T025.

- [ ] **T043** PresetDiff tests — Added / Removed / ParamsChanged classifications. (FR-011, SC-009)
  - Acceptance: 3 canonical test cases pass; version rejection test (incoming ≤ current version).
  - Depends on: T021.

## Phase 9 — Android facades (`app/androidMain/facade/`)

- [ ] **T044** `PackageManagerFacade.kt` + Android impl + unit test. Returns Boolean `isInstalled(pkg)` and `List<String> getInstalled()`. (rule 2 ACL, contracts/provider-port.md)
  - Acceptance: unit test with fake Android PackageManager (Robolectric or manual fake) — returns expected booleans.

- [ ] **T045** [P] `HomeScreenFacade.kt` + Android impl + unit test. Methods for tile add/remove/query with `pinProtected` support. (rule 2 ACL)
  - Acceptance: unit test verifies tile added to fake home storage.

- [ ] **T046** [P] `StoreIntentFacade.kt` + Android impl — `canOpenStore(): Boolean` + `openStore(pkg): Intent`. (SC-007, rule 2 ACL)
  - Acceptance: unit test verifies intent construction for `com.android.vending`.

- [ ] **T047** [P] `UiPrefsFacade.kt` + Android impl using DataStore — `fontScale()` / `setFontScale(f)`. (rule 2 ACL)
  - Acceptance: unit test with fake DataStore verifies get/set roundtrip.

## Phase 10 — Android Providers (`app/androidMain/provider/`)

- [ ] **T048** `AppTileProvider.kt` — implements `Provider<Component.AppTile>` via PackageManagerFacade + HomeScreenFacade + StoreIntentFacade. Covers SC-007 install-check + Play Store fallback. (FR-014, SC-007, SEQ-7)
  - Acceptance: unit test — installed → check=Ok apply=Ok; not-installed → check=NeedsApply → apply=Ok (intent) OR Failed(NetworkUnavailable) when store unavailable.
  - Depends on: T044, T045, T046.

- [ ] **T049** [P] `FontSizeProvider.kt` — implements `Provider<Component.FontSize>` via UiPrefsFacade. (FR-006)
  - Acceptance: unit test — scale mismatch → NeedsApply → apply sets scale → Ok.
  - Depends on: T047.

- [ ] **T050** [P] `SosProvider.kt` — implements `Provider<Component.Sos>` via HomeScreenFacade (tile placement) + minimal emergency intent facade (deferred details). Basic tile presence check. (FR-006)
  - Acceptance: unit test — check tile presence; apply adds tile with `pinProtected=true`.
  - Depends on: T045.

- [ ] **T051** [P] `ToolbarProvider.kt` — implements `Provider<Component.Toolbar>` via HomeScreenFacade toolbar API. (FR-006)
  - Acceptance: unit test — items list mismatch → NeedsApply → apply updates toolbar.
  - Depends on: T045.

## Phase 11 — Android capability + persistence adapters

- [ ] **T052** `DataStoreCapabilityAdapter.kt` — implements `CapabilityQuery` port with Android DataStore backing. (FR-027, contracts/capability-ports.md)
  - Acceptance: unit test with Robolectric DataStore — markActive → isActive true; markInactive → isActive false; survives store restart (fresh instance reads).

- [ ] **T053** `DataStoreProfileStore.kt` — implements `ProfileStore` port with Android DataStore + kotlinx.serialization + preWizardSnapshot support. (FR-013, FR-024)
  - Acceptance: unit test — save+load roundtrip; setPreWizardSnapshot+restore roundtrip; process-death simulation (kill store + new instance) recovers state.

- [ ] **T054** `AndroidLocalizedResources.kt` — implements `LocalizedResources` port using Android `Resources.getIdentifier` + string args interpolation. (FR-026)
  - Acceptance: unit test with fake resources returns expected translated string; missing key returns key itself (fallback).

- [ ] **T055** `BundledPoolSource.kt` — implements `PoolSource` port, reads `assets/pool.json`. Includes inline TODO(shareability) comment. (FR-002)
  - Acceptance: unit test — reads test-fixture pool from androidTest resources; unknownRefs handled.

- [ ] **T056** `BundledPresetSource.kt` — implements `PresetSource` port, reads `assets/bundled-presets/*.json`. Includes inline TODO(shareability) comment. (FR-003)
  - Acceptance: unit test — reads 3 bundled fixtures; missing preset returns null.

## Phase 12 — DI wiring (`app/androidMain/di/`)

- [ ] **T057** `@ComponentKey` custom annotation for Hilt @MapKey — `KClass<out Component>` key. (plan.md §7.4)
  - Acceptance: annotation compiles + Hilt code generation succeeds.

- [ ] **T058** `HandlerModule.kt` — Hilt @IntoMap bindings for 4 MVP Providers keyed by ComponentKey. Assembles `ProviderRegistry` via factory. (plan.md §2)
  - Acceptance: DI test — inject `ProviderRegistry`, resolve each Component subtype returns non-NoOp Provider.
  - Depends on: T048, T049, T050, T051, T057.

- [ ] **T059** [P] `CapabilityContractModule.kt` — binds requires/provides per Component subtype. MVP: all 4 subtypes bind empty sets. (FR-027)
  - Acceptance: DI test — `capabilityContract.requires(AppTile::class) == emptySet()`.
  - Depends on: T016.

- [ ] **T060** [P] `FacadeModule.kt` — binds facades (PackageManager, HomeScreen, StoreIntent, UiPrefs) + ProfileStore + CapabilityQuery + LocalizedResources.
  - Acceptance: DI test — inject each facade, non-null.
  - Depends on: T044, T045, T046, T047, T052, T053, T054.

## Phase 13 — Bundled seeds (`app/androidMain/assets/`)

- [ ] **T061** `assets/pool.json` — 4 MVP declarations per contracts/pool.md example (font-tile, tile-whatsapp, sos-main, toolbar-minimal). schemaVersion=1. (FR-002, contracts/pool.md)
  - Acceptance: valid JSON; loaded by BundledPoolSource without error; passes fitness #10 (i18n keys only).

- [ ] **T062** [P] `assets/bundled-presets/simple-launcher.json` per contracts/preset.md schema. Senior-focused: FontSize Interactive, Sos Interactive, AppTile AutoApply, Toolbar InitialDefault. (FR-003, SC-002)
  - Acceptance: PresetValidator returns empty on load.

- [ ] **T063** [P] `assets/bundled-presets/launcher.json` — regular user variant with different `scale` default (1.2 vs 1.6). (SC-002)
  - Acceptance: PresetValidator returns empty.

- [ ] **T064** [P] `assets/bundled-presets/workspace.json` — B2B skeleton (minimal; full workspace preset scope in TASK-68). (SC-002)
  - Acceptance: PresetValidator returns empty.

- [ ] **T065** [P] `app/androidMain/res/values/strings_pool.xml` + `strings_preset.xml` + `strings_outcome.xml` + `strings_validator.xml` — i18n string resources for all keys used in bundled seeds + FailReason + ValidationError toI18nKey outputs. (FR-026, SC-015)
  - Acceptance: `AndroidLocalizedResources.resolve(key)` returns non-empty for all keys used in bundled content.

## Phase 14 — Integration + smoke

- [ ] **T066** Wire foundation into app startup — `App.onCreate` or first activity resolves `PoolSource.loadPool()` + activates bundled `simple-launcher` preset via `PresetValidator` + `ProfileFactory` + saves to ProfileStore.
  - Acceptance: unit test — application boot loads pool + activates preset without error.
  - Depends on: T055, T056, T019, T018, T053, T058.

- [ ] **T067** [deferred-local-emulator] Emulator smoke — `./gradlew :app:assembleDebug` → install on Pixel 5 emulator → verify BundledPoolSource loads assets/pool.json without crash → verify BundledPresetSource loads all 3 seeds → verify PresetValidator returns empty for each. AI session cannot reliably run emulator; owner runs via `.claude/skills/android-emulator/SKILL.md`.
  - Acceptance: manual — no crashes, logs show 3 presets validated cleanly.

## Phase 15 — Cleanup + docs

- [ ] **T068** Update [backlog/tasks/task-120](../../backlog/tasks/task-120%20-%20Decision-Preset-conditional-composition-via-visibleIf-JsonLogic.md) frontmatter title: `Decision: Preset conditional composition via visibleIf + JsonLogic` → `Decision: Component/Preset/Profile foundational model`. Rename file via `git mv` (AC #5 from task-120 backlog).
  - Acceptance: `backlog task list` shows new title; PR description mentions rename.

- [ ] **T069** [P] Update [docs/product/glossary.md](../../docs/product/glossary.md) — full rewrite of §2 (Three layers) and §3 (Wire format kinds) sections to Component/Pool/Preset/Profile model. Remove Step-terminology inline descriptions; keep them only in migration note.
  - Acceptance: glossary reflects TASK-120 model; grep `stepType` returns only migration-note occurrences.

- [ ] **T070** [P] Legacy `Step`-based wizard code in `core/src/commonMain/kotlin/com/launcher/api/wizard/` — evaluate deletion vs adapter bridge. If draft-1 will refactor: leave in place with `@Deprecated` markers pointing to `com.launcher.preset.*`. If not needed: delete + grep-verify.
  - Acceptance: decision documented in commit message; if kept, `@Deprecated(message="Superseded by TASK-120 Component model", replaceWith=...)` applied to top-level Step types.

## Phase 16 — CI + gates

- [ ] **T071** Add `./gradlew :core:preset:test` to CI pipeline. Test suite includes unit + property-based + all 10 fitness functions.
  - Acceptance: CI green on this branch; failing fitness function on synthetic bad code triggers CI red.

- [ ] **T072** [P] Add build-time JSON validator for pool.json / bundled-presets/*.json to CI pipeline (Gradle task `validateBundledPresets`). Uses same PresetValidator.
  - Acceptance: `./gradlew validateBundledPresets` runs; introducing broken preset in fixture fails build.

---

## Coverage trace (procedure-cross-artifact-trace summary)

| FR | Task(s) | Trace confirmed |
|---|---|---|
| FR-001 Component sealed | T001 | ✓ |
| FR-002 PoolSource | T011, T055 | ✓ |
| FR-003 Preset three-field | T007, T056 | ✓ |
| FR-004 paramsOverride | T007, T036 | ✓ |
| FR-005 ProfileFactory | T018 | ✓ |
| FR-006 Provider port | T009, T048-T051 | ✓ |
| FR-007 ProviderRegistry fallback | T010 | ✓ |
| FR-008 Outcome + FailReason | T002 | ✓ |
| FR-009 WizardBehavior | T003 | ✓ |
| FR-010 ReconcileEngine RunMode | T020, T042 | ✓ |
| FR-011 PresetDiff | T021, T043 | ✓ |
| FR-012 schemaVersion reject | T019 | ✓ |
| FR-013 ProfileStore save-per-step | T012, T053 | ✓ |
| FR-014 AppTile install-check | T048 | ✓ (SC-007 covered) |
| FR-015 peripheral vendor pattern | contracts/provider-port.md (documented, no MVP impl) | ✓ (deferred, no code task) |
| FR-016 schemaVersion in wire formats | T006, T007, T008, T028-T031 | ✓ |
| FR-017 Hilt @IntoMap | T057, T058 | ✓ |
| FR-018 Vendor enum | T003 | ✓ |
| FR-019 fitness functions | T032-T039 | ✓ |
| FR-020 ConditionEvaluator (visibleIf seam) | T014 | ✓ |
| FR-021 SosDispatcher deferred | (no code task; fitness #6 strap only) | ✓ (T035) |
| FR-022 Provider.rollback deferred | (no code task; documented in T009 inline TODO) | ✓ |
| FR-023 PoolSource additive | T031 (backward-compat scaffolding) | ✓ |
| FR-024 preWizardSnapshot | T008, T012, T023, T053 | ✓ |
| FR-025 anti-explosion | T037 | ✓ |
| FR-026 i18n keys | T017, T039, T054, T065 | ✓ |
| FR-027 Capability model | T004, T015, T016, T019, T025, T052, T059 | ✓ |
| FR-028 LOCAL mode | (declared in spec Assumptions; no code task — emergent from bundled seed content) | ✓ |
| FR-029 Undo runtime state deferred | T023, T053 (snapshot support only) | ✓ |

| US | Task(s) covering |
|---|---|
| US1 Wizard bootstrap | T020, T042, T067 |
| US2 Developer extensibility | T032-T034 (fitness functions verify), T041 (property-based) |
| US3 Settings edit | T020 RunMode.Single, T042 |
| US4 BootCheck recovery | T020 RunMode.BootCheck, T042 |
| US5 Admin push (schema-only MVP) | T021, T043 |
| US 5.5 PresetValidator UX | T019, T038 |
| US6 visibleIf (schema seam) | T014, T007 (schema field), T019 |

| SC | Task(s) covering |
|---|---|
| SC-001 add Component with 4 file changes | (structural — verified by T041 property-based generating new Component + T034 coverage) |
| SC-002 bundled seeds apply e2e | T062-T064, T066, T067 |
| SC-003 Wizard/Settings/BootCheck one engine | T020, T042 |
| SC-004 10 fitness functions | T032-T039, T072 |
| SC-005 roundtrip | T028-T030 |
| SC-006 platform fallback NoOp | T010 |
| SC-007 AppTile install-check | T048 |
| SC-008 backward-compat | T031 |
| SC-009 PresetDiff features | T043 |
| SC-010 Component ↔ Provider coverage | T034 |
| SC-011 property-based N=100 | T040, T041 |
| SC-012 anti-explosion pool | T037 |
| SC-013 Undo Wizard | T023, T053, T067 (smoke) |
| SC-014 validator scenarios | T038 |
| SC-015 i18n keys in wire format | T039, T065 |
| SC-016 FailReason structured | T002 |

---

## Task counts

- **Total tasks**: 72 (T001-T072)
- **Parallel-safe [P]**: 34
- **Deferred**: 1 (T067 `[deferred-local-emulator]`)
- **Phases**: 16

## Notes

- **No physical device tasks** — owner directive: `physical-device` tests not run in this repo.
- **iOS placeholder** — no iosMain tasks; iOS module empty per plan.md §7.2 risk; iOS provider parity is post-MVP.
- **FR-015 peripheral vendor pattern** — documented in contracts/provider-port.md but no MVP code task (no peripheral Component in MVP wave).
- **FR-021 SosDispatcher deferred** — strapped by fitness #6 (T035); no code task.
- **FR-022 Provider.rollback deferred** — documented in T009 as inline TODO; no code task.
- **FR-028 LOCAL mode** — property of bundled seed content (T061-T064); no separate code task.
- **T041 property-based** — the most flakiness-prone task. Add deterministic seed to CI + shrinking enabled to reduce noise.
- **Fitness #4 and #5** — covered by T028-T030 roundtrip + T031 backward-compat.
