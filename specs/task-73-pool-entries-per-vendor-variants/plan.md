# Implementation Plan: Vendor-aware dispatch for OEM-sensitive Providers (TASK-73)

## 1. Overview

Make `LauncherRoleProvider` vendor-aware: on Xiaomi/Huawei/Samsung, try a vendor-specific intent (from a new bundled `vendor-recipes.json`) before falling back to the existing generic Android path, and surface an honest fallback instruction (via the existing `FailReason.InternalError` → `ApplyResult.Failed` channel, TASK-69) when neither resolves. See [spec.md](spec.md). Model is defined once in [`docs/architecture/ecs.md`](../../docs/architecture/ecs.md) — this plan cites it, does not restate it. Architecture alternatives compared in [research.md](research.md) (R1: recipe-inside-Provider vs. `HandlerKey.vendor` tier; R2: `FailReason.InternalError` vs. Provider-shown `AlertDialog`).

## 2. Technical Context

- **Language/stack**: Kotlin Multiplatform. Domain in `core/src/commonMain/kotlin/com/launcher/preset/` (pure Kotlin). Android adapter in `core/src/androidMain/kotlin/com/launcher/preset/adapter/`. `Provider` implementation + `Bundled*` adapter in `app/src/main/java/com/launcher/app/preset/task120/{provider,adapter}/` (existing convention, not `:core`, per how `LauncherRoleProvider`/`BundledPoolSource` already live there).
- **Model authority**: [`ecs.md`](../../docs/architecture/ecs.md) §4 item 7 (no `ReconcileEngine`/`ProviderRegistry`/`ProfileFactory` edits), §10 (UI depends only on ports; `Provider` is an adapter, not a UI layer).
- **Scope decided** (spec Clarifications + research.md): reuse existing `enum Vendor` (no new `VendorProfile`); recipe data lives inside `LauncherRoleProvider`, not the dormant `HandlerKey.vendor` tier; fallback text via existing `FailReason.InternalError(messageKey)`, no new UI mechanism; minimum v1 coverage = `Component.LauncherRole` only (`POST_NOTIFICATIONS` has no `Component`, dropped from scope).

## 3. Architecture

Layers (arrows down only — rule 1 fitness, unchanged by this task):

```
LauncherRoleProvider (app/, existing class, extended)   — Provider<Component.LauncherRole>
   ├─ VendorDetector (NEW port)          core/commonMain/preset/port/    — detect(): Vendor
   │    └─ AndroidVendorDetector (NEW)   core/androidMain/preset/adapter/ — Build.MANUFACTURER + alias table
   ├─ VendorRecipeSource (NEW port)      core/commonMain/preset/port/    — loadCatalogue(): VendorRecipeCatalogue
   │    └─ BundledVendorRecipeSource (NEW) app/…/task120/adapter/        — reads assets/preset/vendor-recipes.json
   └─ GmsAvailabilityPort (existing, TASK-49) — consulted only in the Huawei branch
```

- `ProviderRegistry`/`HandlerKey`/`ReconcileEngine`/`PresetModule.kt`'s `runtimeVendor` wiring — **untouched** (research.md R1). This is a deliberate non-change, not an oversight; call it out in code review.
- `apply()` fallback order: vendor-recipe intent (if present for `vendorDetector.detect()`) → existing generic Android intent (unchanged code) → `Outcome.Failed(FailReason.InternalError(fallbackTextKey))`.
- `check()` unchanged — no vendor branching needed for the *check* step in v1 (data-model.md).
- New wire type `VendorRecipeCatalogue` follows the exact `Pool`/`Preset` `schemaVersion` pattern (`core/…/model/`), loaded once at `LauncherRoleProvider` construction time via DI (same lifecycle as `Pool`/`Preset` loading today — no new caching/refresh mechanism).

## 4. Data Model

New **wire** type `VendorRecipeCatalogue`/`VendorOverride` (persisted as a bundled asset, `schemaVersion=1`) + two new **runtime** ports `VendorDetector`/`VendorRecipeSource` — see [data-model.md](data-model.md). No changes to `Component`, `Outcome`, `FailReason`, `LifecycleState`, `Vendor` (all reused as-is).

## 5. Wire Formats

**One new wire format**: `vendor-recipes.json` / `VendorRecipeCatalogue`, `schemaVersion=1` — see [contracts/vendor-recipe-catalogue.md](contracts/vendor-recipe-catalogue.md). Rule 9 (shareability) does **not** apply — this is infrastructure data, not a user-facing/preset artifact (same category as `pool.json`, see contract doc's closing section). `Pool`/`Preset`/`Profile` `schemaVersion`s unchanged — no migration, no roundtrip delta to existing formats.

## 6. Dependency Impact

**No new gradle dependencies.** Reuses `kotlinx.serialization` (already used for `Pool`/`Preset`), Hilt DI, existing Android SDK surface (`Build`, `Intent`, `PackageManager`). Article XIII: nothing to justify. DI wiring: `AndroidVendorDetector`/`BundledVendorRecipeSource` bound in `PresetModule.kt` (same file/pattern as the existing `LauncherRoleProvider`/`BundledPoolSource` bindings), then injected into `LauncherRoleProvider`'s constructor at its existing `@IntoMap` provision site — no new DI module.

## 7. Test Strategy

- **Fake adapters** (rule 6): `FakeVendorDetector`, `FakeVendorRecipeSource` in `core/src/commonTest/kotlin/com/launcher/test/fakes/`, mutable-`var` shape matching `FakeGmsAvailabilityPort.kt`. Existing `FakeGmsAvailabilityPort` reused as-is.
- **`LauncherRoleProvider` unit tests** (extends existing `app/src/test/java/com/launcher/app/preset/task126/LauncherRoleProviderTest.kt`): vendor-override present → correct intent built; vendor-override absent → existing generic path (regression-proof against today's behaviour); neither resolves → `Outcome.Failed(FailReason.InternalError(fallbackTextKey))`; missing `fallbackTextKey` in override → falls back to the universal per-Component default key (Clarifications #4); Huawei + GMS-unavailable → generic-path branch selected correctly (Clarifications #3); Huawei + GMS-available (pre-2019 device) → does not take the no-GMS branch.
- **`VendorDetector` unit tests**: `Build.MANUFACTURER` stub → correct `Vendor`; `"Redmi"`/`"POCO"` (any case) → `Vendor.Xiaomi`; unrecognized manufacturer → `Vendor.GenericAndroid`.
- **Wire-format tests** (contracts/vendor-recipe-catalogue.md "Tests" section): roundtrip, lenient unknown-component-key, lenient unknown-vendor-key, missing-file → empty catalogue. Fixture: `core/src/commonTest/resources/fixtures/vendor-recipes-v1.json`.
- **i18n coverage (Gate 5 / Article VIII)**: the new `launcher_role.fallback.{xiaomi,huawei,samsung,generic}` string keys MUST be added to the existing `WireFormatI18nKeysTest`/`PoolI18nCoverageTest` fitness suite (EN+RU minimum) — same gate TASK-69 extended for its own new keys. No new UI screen/button is introduced (this task only supplies text into TASK-69's existing Settings row rendering, already tap-target/contrast/TalkBack-verified — SC-011 of that task), so no new `checklist-accessibility`/`checklist-elderly-friendly` surface beyond string-quality (plain language, no jargon, per Article VIII §7).
- **Fitness (unchanged, must stay green)**: `ComponentProviderCoverageTest` — `LauncherRole` still resolves to a non-NoOp `Provider` regardless of `Vendor` (SC-006); `PresetEngineIsolationTest` — no Android import leaks into `core/commonMain`; manual review gate — no diff in `ReconcileEngine.kt`/`ProviderRegistry.kt`/`ProfileFactory.kt`/`PresetModule.kt`'s `runtimeVendor` line (research.md R1 — not a fitness test, a PR-review checklist item since there's no automated way to assert "this file wasn't touched" as a gate here).
- **CI (US4/FR-009)**: new Firebase Test Lab job triggered by PR label `oem-matrix-required`, running instrumentation tests on Pixel 8 + Samsung Galaxy S24 + Xiaomi Redmi against `LauncherRoleProvider`.
- **Manifest**: `<queries>` entries for OEM packages referenced by `vendor-recipes.json` (FR-010) — a `ManifestQueriesCoverageTest`-style check (new, small) asserting every `intentPackage` value appearing in the bundled `vendor-recipes.json` has a matching `<queries><package>` entry in `AndroidManifest.xml`, so a future recipe addition without the matching manifest entry fails CI loudly instead of silently breaking `resolveActivity()`.
- **Emulator** (`pixel_5_api_34`, skill `android-emulator`): generic-path regression only — dispatch logic itself is unit-tested, not emulator-dependent (Local Test Path).
- **`TODO(physical-device)`**: real MIUI/EMUI/One UI behaviour — Firebase Test Lab is the closest automatable substitute; a final manual pass belongs to TASK-128's device rotation, not this task's local test path (spec.md Local Test Path).

## 8. Risks

| Risk | Mitigation |
|---|---|
| OEM package/activity names drift across MIUI/EMUI versions (`com.miui.securitycenter` internals change) | Fallback chain always ends in a safe `Outcome.Failed` + honest text, never a crash; `vendor-recipes.json` is data — fixing a drifted package name is a content update, not a code release (the whole point of FR-005/SC-003) |
| Missing `<queries>` entry silently breaks `resolveActivity()` for a new recipe | New `ManifestQueriesCoverageTest` (§7) fails CI loudly instead of a silent field-only bug |
| `Vendor` enum's sub-brand ambiguity (Redmi/POCO not literal `Vendor.Xiaomi`) missed by a future manufacturer string | Explicit alias table in `AndroidVendorDetector`, unit-tested; unknown sub-brand safely degrades to `GenericAndroid` (existing behaviour), not a crash |
| Firebase Test Lab cost/quota/availability | Job runs only on `oem-matrix-required` label (not every PR); unavailable → inconclusive status, not silently green (spec.md Edge Cases) |
| Future readers confuse this task's `LauncherRoleProvider` extension with the still-dormant `HandlerKey.vendor` tier and "finish" that tier redundantly | research.md R1 explicitly documents the rejection + exit ramp; `Out of Scope` in spec.md calls it out |
| `StatusBarPolicy`/other OEM-sensitive `Component`s stay vendor-blind (out of scope here) | Explicit in spec.md Out of Scope — same reasoning TASK-69 already surfaced (not reachable via any bundled preset yet); follow-up task once content catches up |

## 9. Required Context Review

- [`ecs.md`](../../docs/architecture/ecs.md) — §4 (Provider checklist, the "no engine edits" rule this plan follows), §9 (I1/I6 — Profile/Preset self-containment, unaffected here), §10 (layering, gateway-seam pattern; this task adds a Provider dependency, not a new gateway).
- [ADR-013 — Canonical ECS](../../docs/adr/ADR-013-canonical-ecs.md) — supersedes the TASK-65 vocabulary this spec was originally (incorrectly) written against; see spec.md's "Grounding correction" note.
- `specs/task-120-preset-composition-foundation/contracts/provider-port.md` — **historical reference only** (TASK-120 superseded by TASK-136); its `Provider`/`Outcome`/`ProviderRegistry` contract text still matches real code (verified directly against `Provider.kt`/`ProviderRegistry.kt`) and its "peripheral-vendor nested pattern" note informed research.md R1's analysis of what that pattern does and does not cover — cited for grounding, not treated as an independent source of truth (`ecs.md` is, per the `ecs` skill).
- `specs/task-69-settings-as-profile-view/data-model.md` — `ApplyResult.Failed(reason)` shape this plan's fallback-text delivery (research.md R2) depends on.
- [`docs/architecture/pool-naming.md`](../../docs/architecture/pool-naming.md) — confirms `ConfigSource`/`PoolEntry`/`check`/`apply` vocabulary is superseded; confirms the `<pool>.<domain>.<subject>` id convention does not apply here (this task doesn't add a Pool entry, it extends an existing `Component`'s `Provider`).
- [`docs/compliance/permissions-and-resource-budget.md`](../../docs/compliance/permissions-and-resource-budget.md) — updated per FR-011 (new `<queries>` entries).
- Constitution: Article VII (profile-driven config, unaffected — no `Component`/`Preset` change), Article IX (perf — one extra bundled-asset read at `LauncherRoleProvider` construction, negligible), Article XIV (OEM resilience — this task's entire purpose).
- CLAUDE.md rules: 1 (domain isolation — `Build.MANUFACTURER` confined to `AndroidVendorDetector`), 2 (ACL — `LauncherRoleProvider` remains the sole `RoleManager`/`Intent` wrapper), 4 (MVA — research.md R1's explicit Test 1/Test 2 self-check), 5 (wire format — `VendorRecipeCatalogue.schemaVersion`), 9 (shareability — explicitly N/A, see contracts doc).

## 10. Constitution Check

Per Article XVI (run 2026-07-19):

| Gate | Verdict | Note |
|---|---|---|
| 1 Architecture | PASS | No new gradle module; extends one existing `Provider`, adds two ports mirroring `PoolSource`/`PresetSource`/`HintPoolSource`; boundaries explicit in §3. |
| 2 Core/System Integration | PASS | `Build.MANUFACTURER` is a static read, not a system event; no new `BroadcastReceiver`/boot hook; existing `Intent`/`RoleManager` ACL (`LauncherRoleProvider`) unchanged in shape. |
| 3 Configuration | PASS | New wire format `VendorRecipeCatalogue` carries `schemaVersion=1` from commit 1; validation/backward-compat policy explicit in contracts/vendor-recipe-catalogue.md; no `Profile`/`Preset` schema touched. |
| 4 Required Context Review | PASS | §9 links ecs.md, ADR-013, TASK-120 contract (historical), TASK-69 data-model, pool-naming.md, permissions-and-resource-budget.md, relevant Constitution articles + CLAUDE.md rules. |
| 5 Accessibility | PASS (after fix) | No new screen/button — reuses TASK-69's already-verified Settings row rendering (SC-011: ≥56dp, contrast, TalkBack, no dead buttons). **Added**: new `fallbackTextKey` strings routed through existing `WireFormatI18nKeysTest`/`PoolI18nCoverageTest` fitness (EN+RU), plain-language check (§7). |
| 6 Battery/Performance | PASS | One extra bundled-asset read at `Provider` construction (not per-request); no background work, no polling. |
| 7 Testing | PASS (after fix) | Fake + real adapters for both new ports; roundtrip + lenient-read wire tests; fitness tests stay green; **added**: explicit DI-wiring point (§6) so ports aren't hand-waved into existence. |
| 8 Simplicity | PASS | research.md R1/R2 run CLAUDE.md rule 4 Test 1/Test 2 explicitly for both new ports; each has exactly one current consumer (`LauncherRoleProvider`), matching the established one-port-per-artifact pattern already used three times (`PoolSource`/`PresetSource`/`HintPoolSource`). |

**OVERALL: 8/8 PASS** (Gates 5 and 7 fixed via i18n-coverage line and explicit DI-wiring line during this check). Plan is complete.

## 11. Rollout / Verification

- JVM: `./gradlew :core:test --tests "*Vendor*"` + `:app:testDebugUnitTest --tests "*LauncherRoleProvider*"` green (detector, recipe roundtrip/lenient-read, provider dispatch).
- Manifest: `ManifestQueriesCoverageTest` green (no drifted `<queries>` entries).
- CI: `oem-matrix-required`-labeled PR triggers the new Firebase Test Lab job; verify the job definition itself runs (not just that it would pass) before merge.
- Emulator: `pixel_5_api_34` generic-path regression (existing `LauncherRoleProviderTest`-style coverage stays green — Pixel behaviour must not change).
- Definition of done: `LauncherRoleProvider` vendor-aware for Xiaomi/Huawei/Samsung, `ComponentProviderCoverageTest` still green, `ProviderRegistry`/`ReconcileEngine`/`ProfileFactory` diff-free, all `[hand]` AC green; `TODO(physical-device)` real-MIUI/EMUI/One-UI pass → TASK-128 rotation.

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Коротко для владельца

**Что планируем построить.** Один существующий класс (`LauncherRoleProvider` — тот, что сегодня открывает системный диалог «сделать launcher по умолчанию») учится сначала пробовать вендорский путь (для Xiaomi/Huawei/Samsung), и только если его нет — падает на то же поведение, что работает сейчас.

**Как устроено (два ключевых решения, оба записаны в research.md с альтернативами):**
1. **Vendor-логика — внутри `LauncherRoleProvider`, не через отдельный диспетчер.** В коде уже есть встроенный, но выключенный механизм «диспетчер по производителю» (`ProviderRegistry`) — мы его **не включаем**, потому что он потребовал бы нового скомпилированного класса и нового релиза на каждый vendor-override, а мы хотим обновлять OEM-правила файлом, без пересборки. `ecs.md` прямо говорит «не трогай движок» — наше решение с этим совпадает.
2. **Текст ошибки — не новый диалог, а существующий канал.** Провайдер не может сам показать диалог (это работа экрана, не адаптера). Вместо этого он возвращает «не получилось, вот причина» через уже существующий тип (`FailReason`), а Settings-экран (уже построенный в TASK-69) сам покажет текст пользователю сразу после тапа.

**Что нового появляется:** `VendorDetector` (определяет производителя устройства + таблицу псевдонимов Redmi/POCO→Xiaomi) и `VendorRecipeSource` (читает файл `vendor-recipes.json` с рецептами intent'ов на vendor). Оба — по образцу того, как уже читаются `pool.json`/`preset.json`.

**Что НЕ трогаем:** ни один существующий тип (`Component`, `Outcome`, `Vendor`), ни движок (`ReconcileEngine`/`ProviderRegistry`), ни UI-механизм показа ошибок.

**Проверки перед кодом:** конституция — ниже; домен изолирован (чтение `Build.MANUFACTURER` — только в одном адаптере); формат версионирован; лишних абстракций нет (оба research-решения явно проходят тест rule 4 «что теряем, если убрать»).

**Что дальше:** `/speckit.tasks` (разбивка на конкретные задачи) → `/speckit.analyze` (финальная сверка) → код в отдельной сессии.
<!-- NOVICE-SUMMARY:END -->
