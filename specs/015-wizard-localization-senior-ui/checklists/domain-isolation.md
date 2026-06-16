# Checklist: domain-isolation

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16 (post Group A fixes)
**Verdict**: 13 ✓ / 2 ⚠ / 1 ✗ — один real violation (`Locale` type ambiguity)

---

## Vendor SDKs

- [✗] **CHK001** No vendor SDK type in domain signatures.
  - **Mostly clean, one concern — `Locale` type ambiguity (FR-027)**:
    - `StringResolver` port декларирует `fun currentLocale(): Locale`. Какой `Locale`?
      - Если `java.util.Locale` — это **JVM-only** type, не доступен в commonMain (нет на iOS/native).
      - Если `kotlinx.datetime.???` — kotlinx-datetime не имеет полноценного `Locale`.
      - Если custom `Locale` data class — нужно объявить.
    - **Severity**: Medium. Если оставить `Locale` ambiguous → при добавлении iosMain в будущем (per C-7 + C-3) придётся retrofit'ить тип, что нарушает rule 4 (additive extension).
    - **Fix**: декларировать domain-owned `data class Locale(val language: String, val region: String? = null, val script: String? = null)` в `core/localization/` commonMain. Или явно использовать BCP-47 tag как `String` (как сделано в UserPreferences.languageOverride — там `String?` BCP-47). Consistency would say: use `String` (BCP-47 tag) везде.
  - Все остальные ports (WizardEngine, WizardStep, ConfigSource, SystemSettingPort, UserPreferencesStore, DiagnosticEmitter) — без vendor SDK types в signatures. ✓

- [✓] **CHK002** Each external SDK has exactly one wrapper.
  - moko-resources → `StringResolver` port (один).
  - DataStore → три ports (`WizardCheckpointStore`, `DismissedHintsStore`, `UserPreferencesStore`) — это один DataStore зависимостью wrapped множеством domain-shaped ports, ports — domain, не DataStore-shape. ✓
  - Android system settings → `SystemSettingPort` (один).
  - Compose — только в `core/ui-senior/` (UI layer, не domain).
  - Konsist — JUnit test infrastructure, не domain. N/A.
  - Claude API — used by **dev-time skill** (`procedure-translate-spec-strings`), не runtime. Не domain concern. ✓

- [✓] **CHK003** «Vendor disappears» test documented per A-11/A-12 acceptance:
  - moko-resources gone → edit `StringResolver` impl (1 file).
  - DataStore gone → edit 3 stores impls в `:app/androidMain` (3 files в одном модуле).
  - AccessibilityService Android API change → edit `AndroidSystemSettingAdapter` (1 file).

## Transport types

- [✓] **CHK004** No transport types в domain. F-3 — local-only (A-10), нет network transport. ✓
- [✓] **CHK005** Wire format types domain-owned:
  - `WizardManifest`, `ScreenLayout`, `TileSet`, `SystemSettingsPool` — все data classes в `core/wizard/` commonMain с Kotlin Serialization annotations.
  - Kotlin Serialization — multiplatform stdlib-level (не vendor SDK per spec-kit conventions).
  - `ConfigSourceResult` sealed — domain-owned.

## Platform types

- [✓] **CHK006** No `android.*` / `androidx.*` / `Intent` / `Uri` / `Context` / `Bundle` / `LifecycleOwner` в commonMain.
  - System settings deep-links хранятся как `String` (intent action name), не `Intent` object. ✓
  - `Resources.configuration.locales[0]` — упоминается **только в контексте `LocaleProvider` adapter implementation** в `:app/androidMain` (FR-028), не в domain port.
  - `AccessibilityService` — упоминается в context AndroidSystemSettingAdapter implementation, не в domain port. ✓

- [✓] **CHK007** Platform-derived data carries domain-typed projection:
  - `deepLink: String` — projection of Intent action name. ✓
  - `androidMinApi: Int` — domain integer. ✓
  - permission names как `String` (`"android.permission.POST_NOTIFICATIONS"`) — не raw `Manifest.permission` class reference. ✓
  - `actionType: String` — opaque domain value (per C-11). ✓

## Ports

- [✓] **CHK008** Every external surface через port:
  - moko-resources → `StringResolver`
  - DataStore → `WizardCheckpointStore`, `DismissedHintsStore`, `UserPreferencesStore`
  - Android system settings → `SystemSettingPort`
  - Android system locale → `LocaleProvider`
  - Resource loading (assets/strings) → moko-resources API (which is itself ACL)
  - Permissions → wrapped через `SystemSettingPort` (StandardPermission mechanism)

- [✓] **CHK009** Port shape driven by domain need:
  - `WizardCheckpointStore`: `save(checkpoint)`, `load(manifestId)` — domain need «persist wizard progress, resume on next start». Не «put DataStore key». ✓
  - `ConfigSource`: `list(kind)`, `load(kind, id)` — domain need «list configurations of a kind, load specific one». ✓
  - `StringResolver`: `resolve(key, args)` — domain need. ✓
  - `SystemSettingPort`: `status(settingId)`, `applyOrPrompt(settingId)` — domain need «know if applied / make user apply». ✓

- [✓] **CHK010** Each port has fake adapter:
  - `WizardCheckpointStore` → `InMemoryCheckpointStore`
  - `ConfigSource` → `FakeConfigSource`
  - `DismissedHintsStore` → `InMemoryDismissedHintsStore`
  - `UserPreferencesStore` → `InMemoryUserPreferencesStore`
  - `LocaleProvider` → `FakeLocaleProvider`
  - `SystemSettingPort` → `FakeSystemSettingAdapter`
  - `DiagnosticEmitter` → `RecordingDiagnosticEmitter`
  - `Clock` → `FakeClock` (per A-18)

- [⚠] **CHK011** Each port has real adapter.
  - `WizardCheckpointStore` → `PersistentCheckpointStore` (FR-006) ✓
  - `ConfigSource` → `BundledConfigSource` (FR-020) ✓
  - `DismissedHintsStore` → `PersistentDismissedHintsStore` (FR-024) ✓
  - `UserPreferencesStore` → `PersistentUserPreferencesStore` (FR-048) ✓
  - `StringResolver` → moko-resources impl
  - `LocaleProvider` → Android impl in `:app/androidMain` (FR-028)
  - `SystemSettingPort` → `AndroidSystemSettingAdapter` (FR-055) ✓
  - **`DiagnosticEmitter` — нет real impl в F-3** (per A-17 «конкретный analytics backend — не F-3 scope»). Acceptable: app может предоставить no-op real или underspecified — это **port design**, не violation. Real impl будет в S-1 / S-* спеках.
  - **`Clock` — нет explicit production impl в спеке**, но это standard `kotlinx.datetime.Clock.System` — implicit. Recommend explicit mention.

- [⚠] **CHK012** DI wiring picks fake/real per build.
  - **Finding**: спека не описывает explicit build flavor / DI module pattern.
  - **Rationale**: implementation detail для plan.md. Foundation spec фиксирует port-based architecture; конкретные DI bindings — plan.md.
  - **Acceptable**.

## Source-set placement

- [✓] **CHK013** Source-set placement clear с justification:
  - **commonMain (`core/wizard/`)**: WizardEngine, WizardStep interface, ConfigSource port, UserPreferencesStore port, SystemSettingPort port, DiagnosticEmitter port, all data classes (WizardManifest, TileSet, ScreenLayout, SystemSettingsPool, UserPreferences). Justification: domain logic + types.
  - **commonMain (`core/localization/`)**: StringResolver port, fallback logic. Justification: domain logic + types.
  - **commonMain resources**: bundled JSON files (`android-pool.json`, `test-app-family.json`, etc.) — accessible via moko-resources cross-platform API.
  - **commonTest**: all `Fake*` and `InMemory*` adapters + test fixtures.
  - **androidMain (`core/wizard/`, `core/localization/`)**: реальные имплементации store ports (DataStore-based), `AndroidSystemSettingAdapter`, `LocaleProvider` Android impl. Justification: Android-specific APIs.
  - **`core/ui-senior/` (Android library)**: все Compose UI primitives. Justification: Compose-Android-specific per C-7.
  - **`:app/androidMain`**: production adapters wiring + app integration. Justification: app-level composition.

- [✓] **CHK014** Default commonMain; deviation explicit:
  - `core/ui-senior/` Android-only — justified by C-7 (CMP premature).
  - Все persistence implementations androidMain — Android-specific API.
  - Все deviations имеют explicit rationale.

## Existing-code regressions

- [N/A] **CHK015** F-3 — новые модули, нет pre-existing commonMain для regression.
- [⚠] **CHK016** No `expect`/`actual` where pure Kotlin would suffice.
  - Спека не declares explicit `expect`/`actual`. Likely candidates: `LocaleProvider` (Android-specific impl) — может быть `expect`/`actual` или DI port pattern.
  - Recommendation: prefer port-based DI (`LocaleProvider` interface + Android impl). Avoid `expect`/`actual` для testability — фейк инжектируется в commonTest без `actual` workaround.
  - **Implementation detail** для plan.md.

---

## Issues & fixes

### Issue DI-1 — `Locale` type ambiguity (CHK001, severity Medium)

**Problem**: `StringResolver.currentLocale(): Locale` — Java `Locale` не доступен в KMP commonMain (потеряет iOS source set добавление).

**Fix option A** (recommended): использовать **BCP-47 String** consistent с `UserPreferences.languageOverride`:

```
- **FR-027**: `core/localization/` MUST экспонировать `StringResolver` port:
    `fun resolve(key: String, args: Map<String, Any> = emptyMap()): String`
    + `fun currentLocaleTag(): String` (BCP-47 tag, e.g. "ru-RU", "kk-Latn-KZ")
```

**Fix option B**: domain-owned `Locale` data class:
```kotlin
data class Locale(val language: String, val region: String? = null, val script: String? = null) {
    fun toBcp47(): String = listOfNotNull(language, script, region).joinToString("-")
    companion object { fun parse(bcp47: String): Locale = ... }
}
```

**Recommendation**: Option A — proще, consistent с existing `languageOverride: String?` pattern. Less code, single representation.

---

## Резюме

**13 ✓ / 2 ⚠ / 1 ✗** — один fix нужен:

- **Fix DI-1**: `Locale` → BCP-47 `String` в `StringResolver.currentLocaleTag(): String` + `RTL helper` signature `fun layoutDirectionFor(localeTag: String): LayoutDirection` (FR-032).

Остальные warning'и (CHK011 DiagnosticEmitter без real impl, CHK012 DI wiring, CHK016 expect/actual) — acceptable defer'ы в plan.md.

Applying fix DI-1 inline.
