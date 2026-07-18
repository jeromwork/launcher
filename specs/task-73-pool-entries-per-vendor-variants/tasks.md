# Tasks: Vendor-aware dispatch for OEM-sensitive Providers (TASK-73)

Source: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/vendor-recipe-catalogue.md](contracts/vendor-recipe-catalogue.md). Model: [`ecs.md`](../../docs/architecture/ecs.md) (do not restate). `[P]` = parallel-safe.

## Phase 1 — Domain types + ports (`core/preset/`, pure Kotlin)

- [ ] **T073-001** [P] Add `VendorRecipeCatalogue` + `VendorOverride` data classes (`schemaVersion` + companion `CURRENT_SCHEMA_VERSION = 1`) in `core/src/commonMain/kotlin/com/launcher/preset/model/VendorRecipeCatalogue.kt`. Trace: FR-006, data-model.md. Acceptance: compiles, `@Serializable`.
- [ ] **T073-002** [P] Add `VendorDetector` port (`fun detect(): Vendor`) in `core/src/commonMain/kotlin/com/launcher/preset/port/VendorDetector.kt`. Trace: FR-001, plan §3. Acceptance: compiles, zero Android imports.
- [ ] **T073-003** [P] Add `VendorRecipeSource` port (`suspend fun loadCatalogue(): VendorRecipeCatalogue`) in `core/src/commonMain/kotlin/com/launcher/preset/port/VendorRecipeSource.kt`. Trace: FR-005, plan §3. Acceptance: compiles, zero Android imports. Requires: T073-001.

## Phase 2 — Wire-format tests (`core:test`, JVM, no adapter needed)

- [ ] **T073-004** Fixture `core/src/commonTest/resources/fixtures/vendor-recipes-v1.json` — 3 vendor overrides for `"LauncherRole"` (Xiaomi/Huawei/Samsung), per contracts/vendor-recipe-catalogue.md example. Trace: FR-008, contracts. Requires: T073-001.
- [ ] **T073-005** [P] Roundtrip test: `VendorRecipeCatalogue` encode → decode → `assertEquals`. Trace: SC-004, contracts "Tests". Requires: T073-001.
- [ ] **T073-006** [P] Lenient-read test: fixture with an extra unknown `componentType` key parses successfully, entry absent from result. Trace: FR-007, contracts "Tests". Requires: T073-001.
- [ ] **T073-007** [P] Lenient-read test: fixture with an extra unknown `Vendor`-name key under `"LauncherRole"` parses successfully, entry absent from result. Trace: FR-007, contracts "Tests". Requires: T073-001.

## Phase 3 — Fakes + Android adapters

- [ ] **T073-008** [P] `FakeVendorDetector` (`core/src/commonTest/kotlin/com/launcher/test/fakes/FakeVendorDetector.kt`, mutable `var vendor: Vendor`), shape matches `FakeGmsAvailabilityPort.kt`. Trace: rule 6, plan §7. Requires: T073-002.
- [ ] **T073-009** [P] `FakeVendorRecipeSource` (`core/src/commonTest/kotlin/com/launcher/test/fakes/FakeVendorRecipeSource.kt`, mutable `var catalogue: VendorRecipeCatalogue`). Trace: rule 6, plan §7. Requires: T073-003.
- [ ] **T073-010** `AndroidVendorDetector : VendorDetector` (`core/src/androidMain/kotlin/com/launcher/preset/adapter/AndroidVendorDetector.kt`) — reads `Build.MANUFACTURER`, explicit alias table (`Redmi`/`POCO` → `Vendor.Xiaomi`), unrecognized manufacturer → `Vendor.GenericAndroid`. Trace: FR-001, Clarifications #2. Requires: T073-002.
- [ ] **T073-011** [P] `AndroidVendorDetector` unit tests: manufacturer stub → correct `Vendor`; `"Redmi"`/`"POCO"` (any case) → `Vendor.Xiaomi`; unrecognized manufacturer → `Vendor.GenericAndroid`. Trace: plan §7, Clarifications #2. Requires: T073-010.
- [ ] **T073-012** `BundledVendorRecipeSource : VendorRecipeSource` (`app/src/main/java/com/launcher/app/preset/task120/adapter/BundledVendorRecipeSource.kt`) — reads `assets/preset/vendor-recipes.json` via the same `Json{classDiscriminator="type"; ignoreUnknownKeys=true}` pattern as `BundledPoolSource`; drops unknown `componentType`/`Vendor`-name keys in a post-decode filter step (`ignoreUnknownKeys` alone only covers unknown *fields*, not unknown *map keys* — contracts doc). Trace: FR-005, FR-007, contracts "Read semantics". Requires: T073-001, T073-003, T073-004.
- [ ] **T073-013** [P] `BundledVendorRecipeSource` missing-file test → empty `VendorRecipeCatalogue`, no exception. Trace: contracts "Tests", contracts "Read semantics" step 5. Requires: T073-012.
- [ ] **T073-014** [P] `BundledVendorRecipeSource` unsupported-`schemaVersion` test (fixture with `schemaVersion=99`) → empty `VendorRecipeCatalogue`, warning logged, no exception. Trace: contracts "Read semantics" step 2. Requires: T073-012.

## Phase 4 — `LauncherRoleProvider` extension + DI

- [ ] **T073-015** Extend `LauncherRoleProvider` constructor with `vendorDetector: VendorDetector`, `vendorRecipes: VendorRecipeSource`, `gmsAvailability: GmsAvailabilityPort`. Trace: plan §3, data-model.md. Requires: T073-002, T073-003.
- [ ] **T073-016** `apply()`: build and try the vendor-recipe intent (from `intentAction`/`intentPackage`/`intentClassName`/`intentCategory` when present for `vendorRecipes.loadCatalogue().entries["LauncherRole"]?.get(vendorDetector.detect().name)`) **before** the existing generic Android path. Trace: FR-003, US1. Requires: T073-015.
- [ ] **T073-017** `apply()`: when neither vendor-recipe intent nor the existing generic intent resolves (`resolveActivity() == null` on both), return `Outcome.Failed(FailReason.InternalError(messageKey = override?.fallbackTextKey ?: "launcher_role.fallback.generic"))` — **not** a shown dialog (research.md R2). Trace: FR-003, FR-004, US2. Requires: T073-016.
- [ ] **T073-018** Huawei branch: consult `gmsAvailability` **only** when `vendorDetector.detect() == Vendor.Huawei`; `Vendor` derivation itself stays manufacturer-only (Clarifications #3). Trace: FR-002, US2. Requires: T073-015.
- [ ] **T073-019** `check()` regression test: confirm unchanged — still returns `Outcome.Ok`/`Outcome.NeedsApply` without throwing, across every `Vendor` value (no vendor branching added to `check()` in v1, per plan §3). Trace: FR-004. Requires: T073-015.
- [ ] **T073-020** DI (Hilt): bind `AndroidVendorDetector`/`BundledVendorRecipeSource` in `PresetModule.kt` (same file as existing `LauncherRoleProvider`/`BundledPoolSource` bindings), wire into `LauncherRoleProvider`'s existing `@IntoMap` provision. Trace: plan §6. Requires: T073-010, T073-012, T073-015.
- [ ] **T073-021** `LauncherRoleProviderTest` (extend existing `app/src/test/java/com/launcher/app/preset/task126/LauncherRoleProviderTest.kt`): vendor-override present → correct intent built; vendor-override absent → existing generic path unchanged (regression-proof against pre-TASK-73 behaviour); neither resolves → `Outcome.Failed(FailReason.InternalError(fallbackTextKey))`; missing `fallbackTextKey` in override → falls back to `"launcher_role.fallback.generic"`. Trace: SC-005, plan §7. Requires: T073-016, T073-017.
- [ ] **T073-022** [P] `LauncherRoleProviderTest`: `Vendor.Huawei` + `GmsAvailabilityPort` unavailable → no-GMS branch selected; `Vendor.Huawei` + GMS available (pre-2019 device) → does **not** take the no-GMS branch. Trace: Clarifications #3, plan §7. Requires: T073-018.
- [ ] **T073-023** [P] Regression: `ComponentProviderCoverageTest` stays green unmodified — `LauncherRole` still resolves to a non-NoOp `Provider` regardless of `Vendor`. Trace: SC-006. Requires: T073-020.
- [ ] **T073-024** Manual review checkpoint (no automated gate for this — call it out explicitly in the PR description): confirm zero diff in `ReconcileEngine.kt` / `ProviderRegistry.kt` / `ProfileFactory.kt`, and that `PresetModule.kt`'s `DefaultProviderRegistry(..., runtimeVendor = null)` line is unchanged. Trace: research.md R1, ecs.md §4 item 7. Requires: T073-020.
- [ ] **T073-025** US3 demonstration test (SC-003): add a 4th recipe entry (e.g. a second Xiaomi variant or a placeholder new vendor override) to a test-only fixture copy, reload through `VendorRecipeSource`, confirm `LauncherRoleProvider` picks it up with **zero** Kotlin-code change — the literal acceptance test for "no APK rebuild for a new override of an already-known vendor". Trace: SC-003, US3 Independent Test. Requires: T073-021.

## Phase 5 — Manifest + compliance (Android 11+ package visibility)

- [ ] **T073-026** Add `<queries>` entries to `AndroidManifest.xml` for every OEM package referenced by `vendor-recipes.json`'s v1 content (e.g. explicit-component targets under `com.android.settings`, and any MIUI-specific package actually used by the Xiaomi override). Trace: FR-010. Requires: T073-004.
- [ ] **T073-027** New `ManifestQueriesCoverageTest`: every `intentPackage` value present in the bundled `vendor-recipes.json` has a matching `<queries><package>` entry in `AndroidManifest.xml` — fails CI loudly if a future recipe addition forgets the manifest entry. Trace: plan §7 Risks. Requires: T073-026.
- [ ] **T073-028** Update [`docs/compliance/permissions-and-resource-budget.md`](../../docs/compliance/permissions-and-resource-budget.md) with the new `<queries>` entries from T073-026. Trace: FR-011. Requires: T073-026.

## Phase 6 — i18n + diagnostics

- [ ] **T073-029** Add `launcher_role.fallback.{xiaomi,huawei,samsung,generic}` string keys (EN+RU), plain language per Article VIII §7 (no jargon — "Open Settings → Apps → Default apps → Home app" style, not technical terms). Trace: FR-003, plan §7 Gate 5. Requires: T073-017.
- [ ] **T073-030** [P] Extend `WireFormatI18nKeysTest`/`PoolI18nCoverageTest` fitness suite to cover the new keys (EN+RU coverage asserted). Trace: plan §7 Gate 5. Requires: T073-029.
- [ ] **T073-031** [P] Structured diagnostic log event on unsuccessful dispatch (`check()` → `NeedsApply`/`Unsupported`, `apply()` reaches the fallback branch): fields `vendor`, `componentType`, `outcome` — no PII. Trace: FR-012. Requires: T073-017.

## Phase 7 — CI (OEM matrix)

> **[deferred-external]** T073-032 requires Firebase Test Lab / GCP project + billing setup, which this AI session cannot provision.

- [ ] **T073-032** [deferred-external] Add Firebase Test Lab CI job triggered by PR label `oem-matrix-required`, running instrumentation tests on Pixel 8 + Samsung Galaxy S24 + Xiaomi Redmi against `LauncherRoleProvider`. Owner/CI-infra sets up the GCP project and billing; this session wires the job definition (YAML/Gradle task) but cannot verify it actually runs against real device farm quota. Trace: FR-009, US4, SC-007. Requires: T073-021.

## Phase 8 — Emulator / device verification

> **[deferred-local-emulator]** T073-033 — memory `reference_testing_environment`: emulator smoke runs at the end of the phase per project convention, on an AVD the AI session can drive.
> **[deferred-physical-device]** T073-034 — memory `reference_testing_environment`: no physical Xiaomi/Huawei/Samsung device available to this AI session; real-device verification is TASK-128's device rotation.

- [ ] **T073-033** [deferred-local-emulator] Emulator smoke on `pixel_5_api_34` (skill `android-emulator`): tap "Set default launcher" still opens the same system dialog as before this change — Pixel/generic-path behaviour must be visually unaffected by the vendor-aware extension. Trace: plan §11 Rollout, OEM Matrix (Pixel row).
- [ ] **T073-034** [deferred-physical-device] Real-device verification on Xiaomi/Huawei/Samsung hardware: vendor-specific intent actually opens the correct OEM screen; Huawei-without-GMS fallback text is legible and actionable. Trace: spec.md Local Test Path "Cannot-test-locally gaps", OEM Matrix. Owner runs manually via TASK-128 rotation; AI session has no physical device access.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** 34 задачи в 8 фазах. MVP-ядро — фазы 1-4 (типы, wire-format, адаптеры, расширение `LauncherRoleProvider`); остальное — манифест/compliance, i18n, CI и verification-гейты.

**Конкретика, которую стоит запомнить:**
- Фазы 1-2 (T073-001..007) — чистый Kotlin, без адаптеров: типы + порты + wire-format тесты (roundtrip, 2 lenient-read теста).
- Фаза 3 (T073-008..014) — fakes + `AndroidVendorDetector` (алиас-таблица Redmi/POCO) + `BundledVendorRecipeSource`.
- Фаза 4 (T073-015..025) — сердце задачи: расширение `LauncherRoleProvider`, DI, регрессионные тесты. **T073-024** — единственная задача без автоматического гейта (ручная сверка, что `ProviderRegistry`/`ReconcileEngine`/`ProfileFactory` не тронуты). **T073-025** — буквальный тест US3/SC-003: добавить 4-й vendor-override в фикстуру и убедиться, что подхватилось без единой строчки Kotlin.
- Фаза 5 (T073-026..028) — `<queries>` в манифесте + `ManifestQueriesCoverageTest` + compliance-doc.
- Фаза 7 (T073-032) — `[deferred-external]`: Firebase Test Lab job, GCP-биллинг не настраивается в AI-сессии.
- Фаза 8 (T073-033/034) — `[deferred-local-emulator]` / `[deferred-physical-device]`: реальные Xiaomi/Huawei/Samsung — только через TASK-128.

**На что смотреть с осторожностью:**
- T073-020 (DI wiring) — блокирующая задача для T073-023/024 (regression + manual review) и T073-032/033/034 (все verification-гейты); если она застряла — ничего дальше не проверяемо.
- Backward-compat тест для `vendor-recipes.json` **сознательно отсутствует** в этом tasks.md — появится только с `schemaVersion=2` (см. research.md/contracts).

