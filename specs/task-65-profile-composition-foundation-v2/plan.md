# Implementation Plan: TASK-65 — Preset Composition Foundation v2

**Spec**: [`spec.md`](spec.md) (Clarified, 13 clarifications, 5 sequences, 31 FRs, 12 SC).
**Branch**: `task-65-profile-composition-foundation-v2`.
**Status**: Draft.
**Date**: 2026-06-30.

---

## 1. Overview

TASK-65 строит **foundation** для preset composition: новый wire format `preset.json` (self-contained), новая per-device сущность `Profile` со storage shape `Map<PresetRef, ProfileData>`, generic boot-time settings check, 2 Detekt fitness rules. Implementation reuses ~60% existing infrastructure (`ConfigSource`, `WizardEngine.computePending`, `CheckSpec` sealed hierarchy, `BundledConfigSource`) и добавляет ~40% нового (PoolSource port + 2 adapters, ProfileSwitchStrategy, PresetBootRouter, PresetReminderService, PresetRef identity, ConfigKind.Preset, CheckSpec.UIFont demo, 2 Detekt rules).

Главный architectural axiom (revised в Clarification #10): **boot now performs settings check** (callbacks, ms-cheap) → critical missing рендерит banner на HomeActivity. Optional missing — silent until Settings opens.

---

## 2. Architecture

### 2.1 Module map

```
core/ (existing module — TASK-65 adds here, no new Gradle module)
├── src/commonMain/kotlin/com/launcher/
│   ├── api/
│   │   ├── preset/                      ← NEW package
│   │   │   ├── Preset.kt                (wire format, serializable)
│   │   │   ├── Config.kt                (embedded snapshot of pool entry)
│   │   │   ├── AbstractProfile.kt       (optional initial layout/bindings)
│   │   │   ├── PresetRef.kt             (composite identity uid + version)
│   │   │   └── PresetSchemaVersion.kt   (const)
│   │   ├── profile/                     ← NEW package
│   │   │   ├── ProfileData.kt           (layout + bindings[] + settings[])
│   │   │   ├── ProfileStore.kt          (state: activeRef + Map<PresetRef, ProfileData>)
│   │   │   ├── Binding.kt               (slot content)
│   │   │   ├── SettingEntry.kt          (config + current state)
│   │   │   └── ProfileSchemaVersion.kt  (const)
│   │   ├── pools/                       ← NEW package
│   │   │   ├── PoolSource.kt            (port, list/load/version)
│   │   │   └── PoolEntry.kt             (existing types — reorganized here)
│   │   ├── switchstrategy/              ← NEW package
│   │   │   └── ProfileSwitchStrategy.kt (port; CopyOnActivateStrategy default)
│   │   └── wizard/                      (existing — extended)
│   │       ├── ConfigSource.kt          (existing — adds ConfigKind.Preset)
│   │       ├── WizardEngine.kt          (existing — reuse computePending)
│   │       └── data/CheckSpec.kt        (existing — adds UIFont variant)
│   └── ... (existing)
│
├── src/androidMain/kotlin/com/launcher/
│   ├── adapters/
│   │   ├── pools/
│   │   │   ├── HardcodedPoolSource.kt   ← NEW: primary adapter
│   │   │   └── JsonAssetPoolSource.kt   ← NEW: scaffold с TODO
│   │   ├── preset/
│   │   │   ├── PresetReminderService.kt ← NEW
│   │   │   ├── PresetSwitchService.kt   ← NEW
│   │   │   └── PresetSelectionService.kt ← NEW
│   │   ├── profile/
│   │   │   └── PreferencesProfileStore.kt ← NEW: DataStore Preferences impl
│   │   └── wizard/
│   │       └── UIFontChecker.kt         ← NEW: CheckSpec.UIFont handler
│   └── ui/
│       ├── PresetPickerScreen.kt        ← NEW: Compose, reused first-launch + Settings
│       ├── PresetBootRouter.kt          ← NEW: Activity router
│       └── HomeBanner.kt                ← NEW: boot-time critical-missing banner
│
└── src/androidTest/assets/
    ├── presets/
    │   ├── simple-launcher.preset.json
    │   ├── launcher.preset.json
    │   ├── workspace.preset.json
    │   └── test-preset.json             (with CheckSpec.UIFont для FR-026)
    └── wizard-manifests/
        └── legacy-with-app-family-id.json (pre-TASK-65 fixture для backward-compat test)

lint-rules/ (NEW Gradle module — see Decision R5 в research.md)
├── build.gradle.kts                     (detekt-rules dependencies)
└── src/main/kotlin/com/launcher/lint/
    ├── PresetIdBranchingDetector.kt
    └── ExtractionReadinessDetector.kt

app/ (existing module — minimal changes)
├── src/main/kotlin/com/launcher/app/
│   └── FirstLaunchActivity.kt           (existing — routes to PresetBootRouter)
└── src/main/AndroidManifest.xml         (no changes — Activities уже есть)
```

### 2.2 Port-adapter shape (visual fitness for rule 1)

```
        ┌──────────────────────────────────────────────────┐
        │  UI (Compose)                                    │
        │  PresetPickerScreen, HomeBanner, SettingsActivity │
        └──────────┬───────────────────────────────────────┘
                   │ ↓ calls
        ┌──────────▼───────────────────────────────────────┐
        │  Services (androidMain adapters)                  │
        │  PresetBootRouter, PresetSwitchService,           │
        │  PresetSelectionService, PresetReminderService    │
        └──────────┬───────────────────────────────────────┘
                   │ ↓ depends on ports
        ┌──────────▼───────────────────────────────────────┐
        │  Domain ports (commonMain)                        │
        │  ConfigSource, PoolSource, ProfileSwitchStrategy, │
        │  ProfileStore (interface), WizardEngine           │
        └──────────┬───────────────────────────────────────┘
                   │ ↓ implemented by
        ┌──────────▼───────────────────────────────────────┐
        │  Adapter implementations (androidMain)            │
        │  BundledConfigSource, HardcodedPoolSource,        │
        │  JsonAssetPoolSource (scaffold), CopyOnActivate-  │
        │  Strategy, PreferencesProfileStore,               │
        │  UIFontChecker (CheckSpec.UIFont handler)         │
        └──────────┬───────────────────────────────────────┘
                   │ ↓ delegates to
        ┌──────────▼───────────────────────────────────────┐
        │  External (Android SDK / DataStore / Compose BOM) │
        └──────────────────────────────────────────────────┘

       Arrows ONLY downward. Visual fitness check passed.
```

### 2.3 Data flow (boot — SEQ-3)

```
Sys.launch
    → PresetBootRouter.onCreate
    → ProfileStore.load(activePresetRef)               [DataStore read, single]
    → ConfigSource.load(Preset, activePresetRef.uid)   [bundled JSON parse, cached]
    → WizardEngine.computePending(profileData.settings) [N callbacks via SystemSettingPort]
        → for each SettingEntry: dispatch by entry.check.kind
            → AndroidRole → RoleManager.isRoleHeld(...)
            → AndroidPermission → PackageManager.checkPermission(...)
            → UIFont → Configuration.fontScale (UIFontChecker)
            → ... (handler registry)
    → classify by criticality (Required vs Optional)
    → if criticalMissing.isNotEmpty() → HomeActivity with HomeBanner
    → else → HomeActivity без banner
```

**No call** to wizard launch on boot. Banner = display only. User tap → mini-wizard launched explicitly.

---

## 3. Data model

See [`data-model.md`](data-model.md). Highlights:

- `Preset` — wire format с composite identity `PresetRef(uid, version)` + slug (display).
- `Config` — embedded snapshot of pool entry. UX hints inline.
- `AbstractProfile` — optional initial state inside preset (placeholder bindings, no PII).
- `ProfileData` — runtime state per preset (layout + bindings + settings).
- `ProfileStore` — `{activePresetRef, profiles: Map<PresetRef, ProfileData>}`. Syncs to server (TASK-70).
- `SettingEntry` — config + current applied state. Boot callback dispatches by `check.kind`.

---

## 4. Wire formats

| Format | File | Contract doc | schemaVersion |
|---|---|---|---|
| `preset.json` | `core/src/androidMain/assets/presets/*.preset.json` | [`contracts/preset-wire-format.md`](contracts/preset-wire-format.md) | 1 |
| `wizard.manifest` (bumped) | `core/src/androidMain/assets/wizard/wizard-manifests/*.json` | existing + migration writer для `appFamilyId` removal | 2 (bumped from 1) |
| `*.pool.json` per-pool | hardcoded в HardcodedPoolSource OR JSON в assets | [`contracts/pool-naming.md`](contracts/pool-naming.md) | per-pool |
| ProfileStore (sync to server) | DataStore Preferences | [`contracts/profile-store-format.md`](contracts/profile-store-format.md) | 1 |

All wire formats: `schemaVersion: Int` read first; `IncompatibleVersion` returned cleanly; roundtrip + backward-compat tests required.

---

## 5. Dependency impact

### 5.1 New external dependencies

| Dep | Module | Justification (Article XIII) |
|---|---|---|
| `io.gitlab.arturbosch.detekt:detekt-api` | `lint-rules/` (new) | Custom Detekt rules — CLAUDE.md rule 7 fitness functions. Decision R5 в research. |
| `io.gitlab.arturbosch.detekt:detekt-test` | `lint-rules/` (test) | Detekt rule tests (positive/negative cases). |

### 5.2 Existing deps reused

- `kotlinx.serialization-json` — для `Preset` JSON parse (existing).
- `androidx.datastore-preferences` — для `ProfileStore` persistence (existing).
- `androidx.compose.material3` — для `PresetPickerScreen`, `HomeBanner` (existing).
- `kotlinx.coroutines` — boot callback dispatch, persistence (existing).

### 5.3 NOT added (deferred)

- **Proto DataStore** — rejected (Decision R3 в research). Stick with Preferences DataStore + JSON-string serialization of `Map<PresetRef, ProfileData>`. Simpler, KMP-friendly via `multiplatform-settings` migration path.
- **Server SDK** — TASK-70 territory.

---

## 6. Test strategy

Per CLAUDE.md rule 6 (mock-first) + rule 7 (fitness functions):

### 6.1 Contract tests (per wire format)

- `PresetWireFormatRoundtripTest` (JVM) — write Preset → JSON → read → assertEquals. Tests `PresetRef` serialization specifically.
- `ProfileStoreSerializationTest` (JVM) — write `ProfileStore` → string → read → assertEquals. Includes `Map<PresetRef, ProfileData>` with ≥2 entries.
- `WizardManifestBackwardCompatTest` (JVM) — read `legacy-with-app-family-id.json` fixture → migration writer → assert post-TASK-65 valid structure.
- `PoolSourceRoundtripTest` (JVM) — `HardcodedPoolSource.listEntries(id) == JsonAssetPoolSource.listEntries(id)` (когда JsonAsset реализован; иначе `@Ignore("scaffold")`).

### 6.2 Fake adapters (rule 6)

- `FakeConfigSource` (controllable list/load).
- `FakePoolSource` (controllable entries).
- `FakeProfileStore` (in-memory Map<PresetRef, ProfileData>).
- `FakeSystemSettingPort` (controllable applied state per refId).
- `FakeUserPreferencesStore` (controllable fontScale для UIFont test).
- `FakeProfileSwitchStrategy` (deterministic migrate result).

### 6.3 Integration tests (instrumentation, pixel_5_api_34)

- `FirstLaunchPickerE2ETest` — fresh install → 3 cards → tap → wizard → HomeActivity.
- `PresetSwitchE2ETest` — simple-launcher → test-preset → mini-wizard → switch back → bindings preserved.
- `MigrationE2ETest` — pre-TASK-65 state → upgrade → silent migration → no picker.
- `SettingsRemindersE2ETest` — revoke ROLE_HOME → Settings onResume → banner.
- `BootCriticalMissingBannerE2ETest` (NEW) — revoke ROLE_HOME → cold boot → HomeActivity with banner; tap banner → mini-wizard.
- `BootBenchmarkTest` (NEW) — cold boot → measure Sys.launch → HomeActivity.onResume; assert ≤ 1.5s P95 (SC-007).

### 6.4 Fitness functions (rule 7)

- `PresetIdBranchingDetectorTest` — positive case (`if (presetId == "x")` в `app/`) → ISSUE; negative case (same в `core/presets/`) → no ISSUE; `when (appFamilyId)` legacy term → ISSUE.
- `ExtractionReadinessDetectorTest` — `import com.launcher.app.tiles.*` в `core/presets/` → ISSUE; same в `app/` → no ISSUE.
- `BundledPresetsParseTest` (build-time) — все `*.preset.json` в assets parsable, schema valid.
- `EngineGenericityFitnessTest` — test-preset.json with `CheckSpec.UIFont` → dispatched handler == UIFontChecker.
- `SimpleLauncherCompositionRegressionTest` — composition wizard for simple-launcher = golden snapshot.

### 6.5 Edge case tests (CHK011 wire-format gap closure)

- `SettingsCallbackThrowsTest` — fake callback throws → engine treats as `Indeterminate` (Article VII §15 graceful) → step kept as pending, не crash. **Closes wire-format CHK011 edge case gap.**

---

## 7. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Boot benchmark fails SC-007 (≤1.5s) после добавления N callbacks | High | `BootBenchmarkTest` enforces SC-007 в CI. Если падает — async path: HomeActivity показывается immediately, banner апдейтится после background callback (fallback strategy в research R4). |
| `Map<PresetRef, ProfileData>` serialization key collision (json double quotes в uid) | Medium | Composite string `"<uid>::<version>"` с escaping (decision R3). Roundtrip test покрывает edge cases с special characters. |
| Pre-TASK-65 wizard.manifest без `appFamilyId` field в production у некоторых users (malformed migration trigger) | Medium | Migration writer checks both: `wizardDone == true && activePresetRef == null` (FR-015). Idempotent. Unit test покрывает legacy fixture. |
| OEM-specific deep-link to ROLE_HOME settings (Xiaomi MIUI) | Medium | Fallback toast в ApplySpec.SettingsDeepLink. **Follow-up: TASK-73** — per-vendor CheckSpec/ApplySpec overrides + recipe catalogue wire format (covers Xiaomi MIUI, Huawei без GMS, Samsung One UI, Oppo/Vivo/OnePlus/Honor). TASK-65 ships vendor-blind foundation; TASK-73 layers vendor dispatch on top. |
| Detekt rules false positives на legitimate `presetId ==` в test code | Low | Whitelist `com.launcher.core.presets.*` + `core.presets.test.*` (FR-020). |
| Existing `ProfileSnapshot` тип (см. spec context) — что с ним | Low | **T600 inventory (2026-06-30): RENAME** — live consumers: `core/src/commonMain/kotlin/com/launcher/core/profile/CompositionResolver.kt`, `core/src/commonTest/kotlin/com/launcher/core/profile/CompositionResolverTest.kt`, `core/src/androidMain/kotlin/com/launcher/core/profile/ProfileEngine.kt`. Plan: T640 renames `ProfileModels.kt` → `ResolvedPresetModels.kt`, types `ProfileSnapshot → ResolvedPresetSnapshot`, `EffectiveProfile → EffectivePreset`, `DegradationRecord` kept (no name conflict). |
| Memory pressure от Map<PresetRef, ProfileData> с большой историей (admin app с 10+ preset'ов) | Low | Limit eviction policy не нужна сейчас (single user, 3-4 preset'ов max). TASK-70 (multi-user) добавит при необходимости. |
| Banner на HomeActivity конфликтует с lifecycle (configuration change) | Low | Banner state в SavedStateHandle, переживает recreate (CLAUDE.md state-management). |

---

## 8. Required Context Review

Per Article XII §7:

### Governance / Constitution

- [`.specify/memory/constitution.md`](.specify/memory/constitution.md) — Articles VII §3-§16 (Profile composition), Article XI (Simplicity), Article XIX (Organic question budgets), Amendment 1.11 (naming inversion preset/profile).
- [`CLAUDE.md`](../../CLAUDE.md) — rules 1 (domain isolation), 2 (ACL), 4 (MVA), 5 (wire format versioning), 6 (mock-first), 7 (fitness functions), 9 (shareability), 10 (notification minimization).

### ADRs

- [`docs/adr/ADR-011-ai-owner-collaboration-conventions.md`](../../docs/adr/ADR-011-ai-owner-collaboration-conventions.md) — sequences format used in this spec.

### Existing specs

- [`specs/task-7-simple-launcher-first-run/`](../task-7-simple-launcher-first-run/) — TASK-7 baseline (Done). Used as regression target via `SimpleLauncherCompositionRegressionTest` (FR-025).
- [`specs/015-...`](../015-wizard-localization-senior-ui/) (F-3) — wizard-manifest schema source.

### Memory

- `feedback_no_user_action_for_internal_migrations.md` — migration FR-015 conforms.
- `feedback_plain_russian_for_novice.md` — все user-facing docs + MENTOR-DETAIL blocks на RU.
- `feedback_sequences_adr011_canonical.md` — sequences format applied.

### Product

- [`docs/product/vision.md`](../../docs/product/vision.md) — preset composition foundation enables Phase 2 product variants.

---

## 9. Constitution Check

Per Article XVI, 8 gates. Inline report below.

### Gate 1 — Architecture (Articles I-V)

**PASS**. Module boundary preserved (no new Gradle module except `lint-rules/` which is justified per CHK005). Port-adapter shape: 5 new ports (`PoolSource`, `ProfileSwitchStrategy`, `ProfileStore`, `PresetRef` data class, `ConfigKind.Preset` variant) live в `commonMain`. Adapters в `androidMain`. Visual check (§2.2): arrows only downward. Rule 1 (domain isolation) enforced by `ExtractionReadinessDetector`. Rule 2 (ACL): DataStore, RoleManager, PackageManager wrapped in adapters.

### Gate 2 — Core/System Integration (Articles III, VII §15)

**PASS**. `CheckSpec.UIFont` extends existing sealed hierarchy additively (Article VII §16). `UIFontChecker` registered in `androidMain` DI. Unregistered `CheckSpec` variants return `Indeterminate` (graceful degradation per §15). Multi-platform seam intact — engine, ports, existing CheckSpec variants unchanged.

### Gate 3 — Configuration (Article VII)

**PASS**. New ConfigKind `preset` added per §10 evolution rule (justified — existing 5 kinds insufficient for shareable preset composition). Per-preset `requiredModules`/`optionalModules` per §8. `wizard.manifest` schemaVersion bump for `appFamilyId` removal — migration writer ships in same commit (rule 5). Article VII §13 enforced via Detekt rule `PresetIdBranchingDetector`. Article VII §14 reused: `WizardEngine.computePending` is config-check master pattern; no per-refId handlers introduced. **Note**: Amendment 1.11 (naming inversion) applied; per-section rewording deferred per «do not preemptively migrate».

### Gate 4 — Required Context Review (Article XII §7)

**PASS**. All relevant docs linked в §8 above (constitution, CLAUDE.md, ADRs, existing specs, memory, product vision). Memory `feedback_sequences_adr011_canonical` informs sequences format. Memory `feedback_no_user_action_for_internal_migrations` informs FR-015 silent migration.

### Gate 5 — Accessibility (Article VIII §7 senior-safe)

**PARTIAL PASS** — defer to plan-level checklist subset.
- `PresetPickerScreen` cards: tap target ≥ 56dp (senior-safe override), contrast ≥ 4.5:1, TalkBack descriptions for cards.
- `HomeBanner` (FR-030 critical-missing): tap target ≥ 56dp, dismissible, focusable for TalkBack.
- Settings reminder banners (FR-016): same.
- **Open item**: `checklist-accessibility` not run yet (was not in 7 critical list). Recommend running on UI Composables before implementation.

### Gate 6 — Battery/Performance (Article IX)

**PASS** with monitoring:
- SC-007 enforced via `BootBenchmarkTest`: ≤1.5s P95 на pixel_5_api_34.
- FR-029 boot callbacks: synchronous in `Boot.onCreate()`, N×~few-ms each. Estimated total <100ms для 10 entries. **Mitigation if exceeds**: async path documented in R4.
- No WorkManager, no polling, no background services introduced. ProfileStore writes only on user action (wizard done, switch commit).

### Gate 7 — Testing (CLAUDE.md rule 6, 7)

**PASS**. Contract tests + fakes + integration tests + fitness functions enumerated в §6. Roundtrip tests cover composite PresetRef key (R3 decision). Backward-compat test для appFamilyId removal (R6). Edge case tests for callback throws (CHK011).

### Gate 8 — Simplicity (Article XI, CLAUDE.md rule 4)

**PASS**. No premature abstractions. Single-implementation interfaces justified by **known future strategies** (kind-match, sandbox — explicit roadmap, not «future optionality»). `PoolSource` 2 adapters — owner explicit deferred decision (hardcode vs JSON), not abstract optionality. `JsonAssetPoolSource` scaffold с inline TODO + roundtrip test gate — protects against drift. Hooks (`Slot.kind`, `Profile.unassigned`, `pickEnabled`) justified by wire format rule 5 (avoid later schemaVersion bump for known future). Не строим: NetworkPresetSource (TASK-70), FilePresetSource (TASK-35), KindMatchStrategy, SandboxStrategy, unassigned UI, kind dictionary, Settings UI generator (TASK-69), wizard hidden steps (TASK-71), pool browser UI (TASK-72).

---

### Constitution Check verdict: **8/8 PASS** (Gate 5 partial — accessibility checklist defer to UI implementation phase).

---

## 10. Rollout / Verification

### 10.1 Implementation phases

Plan recommends **6 phases** (will be detailed into `Tnnn` tasks в `/speckit.tasks`):

1. **Foundation types** (commonMain): `PresetRef`, `Preset`, `Config`, `AbstractProfile`, `ProfileData`, `ProfileStore` interface, `SettingEntry`, `Binding`, ConfigKind.Preset enum addition, CheckSpec.UIFont variant.
2. **Ports**: `PoolSource`, `ProfileSwitchStrategy`. `CopyOnActivateStrategy` adapter.
3. **PoolSource adapters**: `HardcodedPoolSource` (live), `JsonAssetPoolSource` (scaffold с TODO + roundtrip test).
4. **Persistence**: `PreferencesProfileStore` (DataStore Preferences + JSON-string serialization). Migration writer для `appFamilyId` removal in `wizard.manifest`. Pre-TASK-65 fixture.
5. **UI + services**: `PresetPickerScreen`, `PresetBootRouter`, `PresetSelectionService`, `PresetSwitchService`, `PresetReminderService`, `HomeBanner`, `UIFontChecker`.
6. **Fitness**: `lint-rules/` module + 2 Detekt rules + pre-commit hook setup. `BootBenchmarkTest`.

### 10.2 Verification gates

- **After phase 1-2**: contract tests green (PresetWireFormatRoundtripTest, ProfileStoreSerializationTest).
- **After phase 3**: PoolSourceRoundtripTest (with JsonAsset scaffold @Ignore'd).
- **After phase 4**: WizardManifestBackwardCompatTest green.
- **After phase 5**: integration tests на emulator pixel_5_api_34: FirstLaunchPickerE2ETest, PresetSwitchE2ETest, MigrationE2ETest, SettingsRemindersE2ETest, BootCriticalMissingBannerE2ETest.
- **After phase 6**: Detekt rules positive/negative tests green; BootBenchmarkTest SC-007 confirmed.
- **Pre-PR**: `pre-pr-backlog-sync` skill (per CLAUDE.md mandatory). Backlog AC переходят в `[auto:checklist]` / `[auto:deferred-*]` маркеры.

### 10.3 Physical device verification (deferred items)

- `TODO(physical-device)`: Xiaomi 11T — ROLE_HOME deep-link behavior, MIUI fallback toast.
- `TODO(physical-device)`: Samsung — verification when device available.
- `TODO(physical-device)`: Huawei (no GMS) — verification when device available.

These remain `[ ]` в backlog AC after merge → backlog task переходит в **Verification** статус (per CLAUDE.md hybrid AC model).

---

## 11. Open items резолюция (from clarify-фазы checklists)

| # | Item | Resolution |
|---|------|-----------|
| 1 | ProfileStore Map serialization (CRITICAL) | **Composite string key `<uid>::<version>`** in JSON map. See R3 в research.md. Rationale: KMP-friendly, simpler than Proto, supports `multiplatform-settings` migration. |
| 2 | contracts/profile-store-format.md | Created (see contracts/). |
| 3 | contracts/preset-wire-format.md + pool-naming.md | Both created. |
| 4 | Inventory existing code | Done в §2.1 + R2 (ProfileSnapshot decision). |
| 5 | Boot benchmark contract | `BootBenchmarkTest` (§6.3). SC-007 enforced. |
| 6 | Pre-TASK-65 fixture | `legacy-with-app-family-id.json` в androidTest/assets (§2.1). |
| 7 | Banner dismissibility | Yes — both banners dismissible. HomeBanner has "later" button (hide until next boot OR until state changes). Settings banners persist while requirement missing. See R4. |
| 8 | Logs strategy | Logcat tag per service (`PresetBoot`, `PresetSwitch`, `PresetReminder`, `ConfigSource`, `PoolSource`). Failure logs for: settings callback throws, persistence write failure, mini-wizard launch failure. Structured fields: presetRef, operation, duration_ms. See R7. |
| 9 | DataStore keys namespacing | `preset.active.uid`, `preset.active.version`, `profile.<uid>.<version>.json` (single key per profile, JSON-serialized ProfileData). See R3. |
| 10 | Detekt setup | New `lint-rules/` Gradle module (Decision R5). Pre-commit hook script в `quickstart.md`. |
| 11 | Migration writer pattern | `migrateLegacyWizardManifest(json): Result<ConfigDocument>` — scoped function, no `if (version == 1) else` scatter. See R6. |
| 12 | Edge case "callback throws" | `Indeterminate` per Article VII §15. `SettingsCallbackThrowsTest` (§6.5). |

---

## 12. Next steps

After plan PASS:
1. `/speckit.tasks` — generate `tasks.md` with `Tnnn` execution items per phase.
2. `procedure-cross-artifact-trace` — verify spec ↔ plan ↔ tasks ↔ contracts coverage.
3. `procedure-translate-spec-strings` — i18n keys для `Preset.label`, `Preset.description`, banner text.
4. `/speckit.analyze` — pre-implementation audit.
5. `/speckit.implement` — coding.
6. Pre-PR: `pre-pr-backlog-sync`.

---

## Plain Russian summary (для не-разработчика владельца)

**Что описывает план**:

Plan берёт spec.md (11 правок clarify, 5 диаграмм, 31 требование) и превращает в конкретный технический маршрут реализации.

**Главные решения принятые в плане**:

1. **Map<PresetRef, ProfileData> сериализуется как JSON со строковым ключом `"uid::version"`** — не protobuf, не отдельный файл на профиль. Простое, KMP-совместимое решение. (research R3)
2. **Detekt setup — отдельный gradle модуль `lint-rules/`** — не разбрасываем lint-конфиг по существующим модулям. Pre-commit hook через bash script. (R5)
3. **Boot-time check включён**, но если benchmark покажет что >1.5s — есть fallback: показать HomeActivity сразу, banner async. (R4)
4. **Existing `ProfileSnapshot` тип** (был в коде до TASK-65) — переименовать или удалить во время implementation, решит grep по реальным consumer'ам. (R2)
5. **Migration writer для `appFamilyId` removal** — scoped функция, не разбросанный `if (version == 1)`. Pre-TASK-65 fixture в тестах. (R6)
6. **Логи** — каждый сервис имеет свой Logcat tag, структурированные поля (preset ref, operation, duration). (R7)

**Constitution check — 8/8 PASS** (Gate 5 accessibility — partial, требует `checklist-accessibility` на UI implementation phase).

**Артефакты сгенерированы**:
- `plan.md` (этот файл, главный) — архитектура, риски, тестовая стратегия.
- `data-model.md` — все классы и их связи.
- `research.md` — 7 решений с альтернативами и обоснованием.
- `contracts/preset-wire-format.md` — формат preset.json.
- `contracts/profile-store-format.md` — формат ProfileStore (синхронизируется на сервер).
- `contracts/pool-naming.md` — правила именования pool entries.
- `quickstart.md` — как новому разработчику сюда зайти + Detekt setup.

**Следующий шаг**: `/speckit.tasks` — разбить план на конкретные задачи (`Tnnn`), которые выполняются по одной.
