# Implementation Plan: Preset Composition Foundation

**Spec**: [spec.md](spec.md)
**Backlog**: [task-120](../../backlog/tasks/task-120%20-%20Decision-Component-Preset-Profile-foundational-model.md)
**Status**: Draft
**Created**: 2026-07-10

---

## 1. Overview

Foundation for launcher configuration composition. Replaces hardcoded `FirstLaunchActivity` Sign-In flow with a Component/Pool/Preset/Profile model driven by declarative JSON and dispatched via ProviderRegistry. Downstream: draft-1 wizard, TASK-71/69/68/19, task-121 messenger tile.

MVP wave: 4 Component subtypes (`AppTile`, `FontSize`, `Sos`, `Toolbar`) + full port infrastructure (Provider, ProviderRegistry, PoolSource, PresetSource, ProfileStore, InteractionSink, ConditionEvaluator, CapabilityFlag/Query/Contract, PresetValidator, LocalizedResources, PackageManagerFacade, HomeScreenFacade, StoreIntentFacade, UiPrefsFacade).

---

## 2. Architecture

### Module map

```
core/
  preset/                            # KMP commonMain — pure Kotlin, zero Android
    src/
      commonMain/kotlin/com/launcher/preset/
        model/
          Component.kt               # sealed hierarchy (4 MVP subtypes)
          ComponentDeclaration.kt    # pool entry (typed payload + id + wizardBehavior + critical + labelKey + descriptionKey?)
          Pool.kt                    # typed catalog
          Preset.kt                  # 3 orthogonal fields (wizardFlow / settingsMap / activeComponents)
          Profile.kt                 # activeComponents + preWizardSnapshot + state (opaque holder)
          ProfileComponent.kt        # per-entry runtime state + status
          ComponentStatus.kt         # enum Pending | Applied | Failed | Skipped
          WizardBehavior.kt          # enum Interactive | AutoApply | InitialDefault
          Outcome.kt                 # sealed Ok | NeedsApply | Failed(FailReason) | Unsupported
          FailReason.kt              # sealed PermissionDenied | PolicyBlocked | NetworkUnavailable | Cancelled | InternalError
          RunMode.kt                 # enum Wizard | BootCheck | Single | RemotePush
          HandlerKey.kt              # (KClass, Platform?, Vendor?)
          Vendor.kt                  # enum Xiaomi | Samsung | Huawei | GoogleTV | GenericAndroid | iOS
          CapabilityFlag.kt          # sealed — MVP: CloudSession
          ValidationError.kt         # sealed CapabilityMissing | UnknownPoolRef | SchemaVersionUnsupported | CircularOrdering
        port/
          Provider.kt                # check + apply + inline TODO(capability-registry)
          ProviderRegistry.kt        # resolve with fallback vendor→platform→NoOp
          PoolSource.kt              # loadPool() + inline TODO(shareability)
          PresetSource.kt            # loadPreset(ref) + inline TODO(shareability)
          ProfileStore.kt            # load / save / setPreWizardSnapshot / restoreFromPreWizardSnapshot
          InteractionSink.kt         # askUser(component)
          ConditionEvaluator.kt      # evaluate(rule, ctx) — minimal MVP {"var": "profile.state.<flag>"}
          CapabilityQuery.kt         # isActive / markActive
          CapabilityContract.kt      # requires / provides
          LocalizedResources.kt      # resolve i18n key
        engine/
          ReconcileEngine.kt         # run(RunMode, InteractionSink?) — inline TODO(capability-registry)
          ProfileFactory.kt          # create(preset, pool) → Profile
          PresetValidator.kt         # validate(preset, pool) → List<ValidationError>
          PresetDiff.kt              # diff(current, incoming, pool) → List<ChangeItem>
        adapter/
          NoOpProvider.kt            # returns Outcome.Unsupported
      commonTest/kotlin/com/launcher/preset/
        fakes/                       # FakePoolSource, FakePresetSource, FakeProfileStore, FakeInteractionSink, FakeProvider<T>, FakeCapabilityQuery, FakeCapabilityContract, FakeLocalizedResources
        roundtrip/                   # SC-005 wire format roundtrip
        propertybased/               # SC-011 random preset generation via kotest-property Arb
        fitness/                     # 10 fitness functions
        engine/                      # ReconcileEngine tests per RunMode
        validator/                   # PresetValidator tests (SC-014)
      androidMain/kotlin/com/launcher/preset/adapter/  # currently empty for MVP
      iosMain/kotlin/com/launcher/preset/adapter/      # currently empty (placeholder module)

app/
  src/
    androidMain/kotlin/com/launcher/app/
      provider/                      # per-platform/vendor Provider impls
        AppTileProvider.kt           # generic Android, uses PackageManagerFacade + HomeScreenFacade + StoreIntentFacade
        FontSizeProvider.kt          # generic Android, uses UiPrefsFacade
        SosProvider.kt               # generic Android, uses HomeScreenFacade + emergency intent facade (deferred)
        ToolbarProvider.kt           # generic Android, uses HomeScreenFacade
      facade/                        # Android SDK wrappers (ACL per rule 2)
        PackageManagerFacade.kt
        HomeScreenFacade.kt
        StoreIntentFacade.kt
        UiPrefsFacade.kt
      capability/                    # CapabilityQuery + LocalizedResources Android impls
        DataStoreCapabilityAdapter.kt
        AndroidLocalizedResources.kt
      di/
        HandlerModule.kt             # Hilt @IntoMap + custom @ComponentKey annotation
        CapabilityContractModule.kt  # binds requires/provides per Component subtype
        FacadeModule.kt              # binds facades
    androidMain/assets/
      pool.json                      # bundled pool (MVP wave declarations)
      bundled-presets/
        simple-launcher.json
        launcher.json
        workspace.json
    androidMain/res/values/
      strings_pool.xml               # i18n keys for all pool labelKey / descriptionKey values
      strings_preset.xml             # i18n keys for wizardTitleKey / categoryKey values
      strings_outcome.xml            # i18n keys for FailReason.toI18nKey() outputs
      strings_validator.xml          # i18n keys for ValidationError messages
```

### Data flow (spec-level)

```
User / Admin / Bundle
        │
        ▼
   PresetSource  ──►  loadPreset(ref)
        │
        ▼
   PresetValidator  ──►  validate(preset, pool)  ──►  ValidationError (fail-fast)
        │ ok
        ▼
   ProfileFactory  ──►  create(preset, pool)  ──►  Profile
        │
        ▼
   ReconcileEngine.run(RunMode)
        │
   ┌────┴────┐
   │ Wizard  │──► InteractionSink for Interactive
   │ Boot    │
   │ Single  │
   │ Push    │
   └────┬────┘
        │
   for each ProfileComponent:
   ProviderRegistry.resolve → Provider.check / .apply
        │
        ▼
   Outcome  ──►  Profile.mark(id, status)  ──►  ProfileStore.save
```

### Domain isolation invariant

- `core/preset/` MUST NOT import `android.*` or vendor SDKs. Enforced by fitness function #1 (import guard).
- `app/androidMain/provider/*` MAY import Android SDKs but only through facade wrappers (fitness function per rule 2 ACL).
- `PackageManagerFacade` returns `Boolean` / `String`, never `PackageInfo`. Same for other facades — no Android types leak to Providers.

---

## 3. Data model

See [data-model.md](data-model.md).

Sealed hierarchies:
- `Component` — 4 MVP subtypes.
- `Outcome` — 4 variants.
- `FailReason` — 5 variants.
- `CapabilityFlag` — 1 MVP (CloudSession).
- `ValidationError` — 4 variants.

Data classes: `ComponentDeclaration`, `Preset`, `Profile`, `ProfileComponent`, `HandlerKey`, `WizardFlowEntry`, `SettingsMapEntry`, `ActiveComponentEntry`, `ChangeItem`.

Enums: `WizardBehavior`, `ComponentStatus`, `RunMode`, `Vendor`.

---

## 4. Wire formats

Three wire formats, each carries `schemaVersion` per rule 5:

- [contracts/pool.md](contracts/pool.md) — `pool.json` schemaVersion=1.
- [contracts/preset.md](contracts/preset.md) — `preset.json` schemaVersion=2.
- [contracts/profile.md](contracts/profile.md) — `profile.json` schemaVersion=2.

Additional contract docs:
- [contracts/provider-port.md](contracts/provider-port.md) — Provider interface + Outcome + Registry resolve semantics.
- [contracts/capability-ports.md](contracts/capability-ports.md) — CapabilityFlag / Query / Contract + PresetValidator semantics.

---

## 5. Dependency impact

New gradle dependencies (justified per Article XIII):

| Dependency | Version | Where | Justification |
|---|---|---|---|
| `kotlinx-serialization-json` | 1.7+ (BOM) | `core:preset` commonMain | Polymorphic sealed serialization for Component. Already in project. |
| `androidx.datastore-preferences` | 1.1+ | `app` androidMain | `ProfileStore` persistence. Already in project. |
| `androidx.datastore-preferences-core` | 1.1+ | `core:preset` commonMain | KMP-friendly DataStore surface if needed; else JVM equivalent for tests. |
| `io.kotest:kotest-property` | 5.9+ | `core:preset` commonTest | SC-011 property-based test of random preset combinations. **NEW**. |
| `io.kotest:kotest-assertions-core` | 5.9+ | `core:preset` commonTest | Testing assertions (may already be present). |
| Hilt | current | `app` androidMain | ProviderRegistry `@IntoMap` binding. Already in project. |

No new production runtime dependencies beyond Kotlin / Coroutines / kotlinx.serialization / DataStore / Hilt (all already present per session 2.5 confirmation).

Rejected alternatives (documented in spec Decision block):
- Fleks/Ashley/Artemis ECS runtime — scale mismatch.
- Arrow Either/Raise — unnecessary for 4-variant Outcome sealed.
- Koin — project already uses Hilt.
- Full JsonLogic runtime — MVP only handles `{"var": "profile.state.<flag>"}`.

---

## 6. Test strategy

Per CLAUDE.md rule 6 (mock-first) + rule 7 (fitness functions):

### 6.1 Contract tests

- **Roundtrip** (SC-005): `pool.json + preset.json → ProfileFactory → Profile → serialize → deserialize → equal Profile`.
- **Backward-compat** (SC-008): if any pre-existing wire format lives in fixtures, read it under new code.
- **Polymorphic sealed roundtrip**: each Component subtype serializes/deserializes via `classDiscriminator = "type"` without loss.
- **JSON Schema validation** (fitness #7): `paramsOverride` validated per Component `mutable: true` field allowlist.

### 6.2 Fake-adapter tests

`core/preset/commonTest/fakes/` module:
- `FakePoolSource(entries: List<ComponentDeclaration>)` — deterministic pool.
- `FakePresetSource(presets: Map<PresetRef, Preset>)` — deterministic preset lookup.
- `FakeProfileStore(initial: Profile)` — in-memory `MutableStateFlow`.
- `FakeInteractionSink(cannedAnswers: Map<String, Component>)` — deterministic Wizard choices.
- `FakeProvider<T : Component>(outcomes: (Component) -> Outcome)` — canned Outcome per invocation.
- `FakeCapabilityQuery(activeFlags: MutableSet<CapabilityFlag>)`.
- `FakeCapabilityContract(reqs: Map<KClass, Set<CapabilityFlag>>, provs: Map<KClass, Set<CapabilityFlag>>)`.
- `FakeLocalizedResources(bundle: Map<String, String>)`.

### 6.3 Engine tests

Cover all 4 RunMode branches per FR-010:
- `RunMode.Wizard` — non-applied only, Interactive→sink, InitialDefault→skip apply, AutoApply→silent.
- `RunMode.BootCheck` — only critical, check→apply loop.
- `RunMode.Single` — targetComponentId, single dispatch.
- `RunMode.RemotePush` — after PresetDiff, applies only ChangeItem entries.

### 6.4 Validator tests (SC-014)

- Valid ordering `[FontSize (no reqs), FakeSignIn (provides CloudSession), FakeCloud (requires CloudSession)]` → validator returns empty.
- Malformed `[FakeCloud (requires CloudSession), FakeSignIn (provides CloudSession)]` → `CapabilityMissing`.
- Optional path — Component with `requires = emptySet()` unaffected by ordering.
- Unknown poolRef → `UnknownPoolRef`.
- schemaVersion too high → `SchemaVersionUnsupported`.

### 6.5 Property-based test (SC-011)

`kotest-property` with `Arb.preset(pool)` generator:
- Emits N=100 random valid preset compositions from MVP pool.
- Runs each through `ProfileFactory → ReconcileEngine.run(RunMode.Wizard)` with `FakeInteractionSink` returning canned answers.
- Assertions:
  - Every `ProfileComponent` reaches terminal status (Applied / Skipped / Failed).
  - Engine never throws.
  - `Profile → serialize → deserialize → Profile'` bit-identical.

### 6.6 Fitness functions (10 total)

| # | Name | Enforcement |
|---|---|---|
| 1 | Import guard on engine | Detekt rule: `core/preset/engine/**` MUST NOT import concrete `Component.*` subtypes. |
| 2 | `when`-guard on engine | Detekt / regex: no `when(component)` on subtypes in `core/preset/engine/**`. |
| 3 | Coverage: Component ↔ Provider | Reflection test: every `Component::class.sealedSubclasses` has non-NoOp Provider in test DI graph. |
| 4 | Roundtrip pool+preset→profile | SC-005 unit test. |
| 5 | Backward-compat wire format | Fixture-based backward-read test. |
| 6 | Cross-provider isolation | Detekt: `provider/foo/**` MUST NOT import `provider/bar/**`. |
| 7 | paramsOverride schema validation | JSON Schema per declaration + `mutable: true` allowlist. |
| 8 | Anti-explosion pool limit | Test scans `pool.json`, warns at N=4-5 declarations per Component subtype, errors at N≥6. |
| 9 | PresetValidator coverage | Three-scenario test per SC-014. |
| 10 | No-literal-strings in user-facing wire format | Regex-based JSON validator: literal strings in `labelKey`/`descriptionKey`/`wizardTitleKey`/`categoryKey` fields → build error. |

---

## 7. Risks & mitigations

### 7.1 Kotlin sealed exhaustiveness across modules

- **Risk**: Kotlin 1.7+ allows sealed hierarchy split across files in same module. If future Component subtypes live in different modules (e.g. `messenger-module` for MessengerTile from task-121), sealed exhaustiveness weakens.
- **Mitigation**: MVP keeps all Component subtypes in single `core/preset/model/Component.kt` file. Deferred: revisit when task-121 lands; fitness #3 catches any orphan subtype at test time.

### 7.2 kotlinx.serialization polymorphic sealed at KMP boundary

- **Risk**: `classDiscriminator` mismatch across platforms; iOS-side deserialization edge cases.
- **Mitigation**: MVP is Android-only ship, iOS module is placeholder. Roundtrip test covers Android JVM. iOS test parity — TODO when iOS ships.

### 7.3 DataStore migration on profile.json schemaVersion bump

- **Risk**: Profile schema evolves; migration writer must handle v2→v3 transition.
- **Mitigation**: Backward-compat test (fitness #5) reads previous fixture. Migration writer template in `ProfileStore` implementation with schemaVersion field mandatory.

### 7.4 Hilt `@IntoMap` KClass key ergonomics

- **Risk**: Hilt map-key annotations require boilerplate; `KClass` keys sometimes need custom annotation.
- **Mitigation**: One `@ComponentKey` custom annotation defined once. Ergonomic per Component. Standard Hilt pattern documented in HandlerModule.

### 7.5 CapabilityQuery.markActive persistence

- **Risk**: `markActive(CloudSession, evidence)` must persist across process death (survives after Sign-In apply completes).
- **Mitigation**: `DataStoreCapabilityAdapter` writes to same DataStore as ProfileStore (Profile.state opaque holder). Test: process-death simulation via killing ProfileStore fake + reload.

### 7.6 Property-based test flakiness

- **Risk**: `Arb.preset(pool)` may generate degenerate cases that hit unrelated bugs.
- **Mitigation**: Deterministic seed for CI reproducibility. Shrinking enabled. Failing case → new fixture in `commonTest/fixtures/`.

### 7.7 OEM package manager quirks

- **Risk**: Huawei / MIUI aggressive PackageManager caching, silent Play Store unavailability.
- **Mitigation**: `PackageManagerFacade.isInstalled` returns Boolean; `StoreIntentFacade.canOpenStore()` returns Boolean. Concrete OEM handling lives in facades. TODO(physical-device) in facade tests.

---

## 8. Required Context Review

Files reviewed for this plan (per Article XII §7):

- [CLAUDE.md](../../CLAUDE.md) — rules 1 (domain isolation), 2 (ACL), 4 (MVA), 5 (wire-format versioning), 6 (mock-first), 7 (fitness functions), 9 (shareability-readiness), 11 (Decision block mutability window).
- [.specify/memory/constitution.md](../../.specify/memory/constitution.md) — Article V (Modularization With Restraint), Article VII §9-13 (Preset composition), Article XVI (Constitution Check), Amendment 1.11 (naming inversion).
- [docs/product/glossary.md](../../docs/product/glossary.md) — legacy Step-terminology, superseded by TASK-120 header note 2026-07-10.
- [docs/architecture/pool-naming.md](../../docs/architecture/pool-naming.md) — pool architectural invariant.
- [docs/dev/server-roadmap.md](../../docs/dev/server-roadmap.md) — no direct touch (foundation LOCAL per FR-028).
- [backlog/tasks/task-120](../../backlog/tasks/task-120%20-%20Decision-Component-Preset-Profile-foundational-model.md) — Decision block (English) is contract.
- [backlog/tasks/task-121](../../backlog/tasks/task-121%20-%20Messenger-tile-with-SSO-handoff.md) — downstream, receives foundation contract.
- [docs/adr/ADR-011-ai-owner-collaboration-conventions.md](../../docs/adr/ADR-011-ai-owner-collaboration-conventions.md) — output discipline, MENTOR-DETAIL blocks, language-by-audience.

---

## 9. Constitution Check

Ran manually against Article XVI 8 gates (procedure-constitution-check pattern):

### Gate 1 — Architecture (Article I, V)
- **PASS**. Module structure: `core/preset/` (commonMain domain) + `app/androidMain/provider/*` (adapters) matches port/adapter pattern. No premature modularization (Article V §7): single `core/preset/` module in MVP, no gradle sub-modules per Component group. `requiredModules`/`optionalModules` skipped per owner MVA (spec §Assumptions).

### Gate 2 — Core+System Integration (Article II, III)
- **PASS**. Every Android SDK access via facade (`PackageManagerFacade`, `HomeScreenFacade`, `StoreIntentFacade`, `UiPrefsFacade`, `DataStoreCapabilityAdapter`). Provider's public interface uses only domain types + `Component` subtypes + `Profile`.

### Gate 3 — Configuration (Article VII)
- **PASS** with note. Preset schemaVersion=2 introduced with three-field split (§9-13 composition semantics preserved). Wire format additions are backward-compat: schemaVersion=1 (previous) → 2 (this) via migration writer per rule 5. Article VII §8 `requiredModules`/`optionalModules` — deliberately skipped, accepted per rule 4 MVA (documented in spec §Assumptions with future schemaVersion=3 additive migration path if modules come). **Note added to constitution check log**: article conflict resolved by explicit owner directive, not silent skip.

### Gate 4 — Required Context Review (Article XII §7)
- **PASS**. See §8 above — 8 governance/architecture files cited.

### Gate 5 — Accessibility (Article VIII)
- **DEFERRED to downstream**. This foundation spec is domain-level; no UI. Accessibility concerns (a11y, TalkBack, contrast, tap target) live in downstream task specs (draft-1 wizard, TASK-69 Settings). This plan reserves `LocalizedResources` port and i18n key discipline (FR-026) which supports future a11y label injection. Explicit note in this plan: no a11y assertion here, but wire format resilient to future a11y string additions.

### Gate 6 — Battery+Performance (Article IX)
- **PASS with mitigation**. `Provider.apply` MUST NOT contain persistent background loops (FR-006). Apply = configure + return (uses WorkManager/AlarmManager/geofencing for durable work). ReconcileEngine invocations are sub-second (walks 4-15 components, each check+apply < 100ms for MVP subtypes). DataStore writes: batched or per-step per FR-013 owner directive (bounded by wizardFlow length, typically 5-7 saves). Cold start impact: pool.json + preset load ~10ms JVM parse (kotlinx.serialization benchmark). No polling / broadcast / foreground service in this foundation.

### Gate 7 — Testing (Article X)
- **PASS**. Test strategy §6 covers: contract, fake-adapter, engine per RunMode, validator scenarios, property-based, 10 fitness functions. Coverage per rule 6 (mock-first): every port has fake adapter listed. Local test path: pure JVM per spec §Local Test Path. No emulator required for foundation.

### Gate 8 — Simplicity (Article XI, rule 4 MVA)
- **PASS**. Deferred seams justified: visibleIf full JsonLogic (schema seam), SosDispatcher (fitness function #6 straps future addition), ConsumerFilter (bounded 4 RunMode enough), Provider.rollback (FR-029 snapshot restore + BootCheck drift reconcile suffices), requiredModules (owner MVA). MessengerTile deferred to task-121 (prevents code sprawl). MVP wave: 4 Component subtypes (not 15+). Pool anti-explosion fitness #8 enforces parametrization over cataloging.

**Verdict**: 8/8 PASS (Gate 5 deferred to downstream with justification).

---

## 10. Rollout / verification

### 10.1 Perf checkpoint

- Cold start delta from current app baseline: **≤ 30ms** on Pixel 5 emulator (measured via Macrobenchmark once integrated into `app`).
- Wizard first-render TTI (bundled `simple-launcher`): **≤ 500ms** on Pixel 5 (excluding user Interactive input).
- DataStore write P95: **≤ 20ms** per save.

### 10.2 Smoke path

Manual verification steps (physical device or emulator):
1. Fresh install → bundled `simple-launcher` preset loads → Wizard shows 4 shagsov → each applied.
2. Open Settings → 4 Components visible categorized → edit FontSize → apply → verify runtime shrift changed.
3. Force-close app during Wizard step 2 → reopen → Wizard resumes at step 2.
4. Complete Wizard, force reboot → BootCheck runs on `critical: true` components (Sos if marked critical) → verify persistence.
5. Trigger undo (via debug menu or Settings option) → confirm dialog → Profile restored to pre-wizard → runtime state drift accepted → BootCheck reconciles on next start.

### 10.3 Fitness function CI gate

All 10 fitness functions MUST be green in CI before merging. Enforced via `./gradlew :core:preset:test --tests "*FitnessTest"` in CI pipeline.

### 10.4 Property-based CI gate

`./gradlew :core:preset:test --tests "*PropertyTest"` MUST pass 100 iterations with fixed seed. Any regression → new fixture in `commonTest/fixtures/regressions/`.

---

## 11. Downstream contract signals

Downstream tasks receiving stable machine-readable contract from this plan:

- **draft-1 wizard refactor** — reads Preset via `PresetSource` port, invokes `ReconcileEngine.run(RunMode.Wizard)`. Introduces `SignInGoogle` Component subtype with `provides: {CloudSession}` — foundation supports mechanism, doesn't ship subtype.
- **TASK-71 hidden steps** — uses `WizardBehavior.InitialDefault` + `visibleIf` schema seam.
- **TASK-69 Settings as Profile View** — uses `settingsMap` field, `ReconcileEngine.run(RunMode.Single, targetComponentId)`.
- **TASK-68 workspace preset** — creates bundled `workspace.json` per contracts/preset.md schema.
- **TASK-19 Adaptive UX Presets** — uses `paramsOverride` for tremor/vision variants.
- **task-121 MessengerTile** — reads Provider port contract per contracts/provider-port.md, adds `MessengerTile` sealed subtype + Provider + facade for SSO handoff.
