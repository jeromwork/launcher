# Checklist: meta-minimization

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16
**Verdict**: 10 ✓ / 0 ⚠ / 3 ✗ — **три violations rule 4 MVA**

---

## New abstractions

- [✗] **CHK001** Every new interface/port has at least one concrete consumer **in this spec**.
  - **VIOLATION #1 — `ResourceReader` port (FR-043)**:
    - Заявлен в FR-043 без concrete implementation в spec'е и без consumer FR. Wrapping motivation — «vendor SDK / Android system types в signatures», но `moko-resources` уже **является** этой ACL (выбран в C-8 + A-14). Дополнительный port дублирует.
    - **Fix**: убрать упоминание `ResourceReader` из FR-043. Достаточно сказать «resource loading через moko-resources API (которая является platform-aware abstraction сама по себе)».

- [✗] **CHK002** Если new interface has only one implementation: justified by port-shape need (DI, fakes, platform asymmetry) — не «extensibility».
  - **VIOLATION #2 — `WizardStepRegistry` (FR-009)**:
    - Заявлен с rationale «app-family может добавить свой кастомный step, не trying модифицировать `core/wizard/`».
    - **Test 1 (rule 4)**: inlined = `WizardEngine(steps: Map<StepType, WizardStep>)` constructor parameter. Loss = только future optionality.
    - **Test 2**: ни одна current spec (S-1, S-2) не декларирует custom step type. Bundled steps (LanguageStep, SystemSettingStep, TextSizeStep, ThemeStep, GridSelectionStep, ScreenLayoutPickerStep, TileSetPickerStep, PairingStep, TutorialHintStep) — закрывают MVP.
    - **Fix**: убрать FR-009. WizardEngine принимает `steps: Map<StepType, WizardStep>` через constructor. Если в будущем S-1 действительно понадобится custom step — это additive change (новый FR в S-1 спеке), не предусмотренный сейчас registry.

- [✓] **CHK003** Mediator/orchestrator/manager justified by data transformation.
  - `WizardEngine` — orchestrator: трансформирует `WizardManifest` → traversal → `WizardOutcome`. ✓
  - `TutorialHintManager` — managает persistent dismissed state + UI overlay coordination. ✓
  - `AndroidSystemSettingAdapter` — трансформирует abstract `settingId` → конкретный Android Intent / API call. ✓

- [⚠ → ✗] **CHK004** No custom DSL/registry/plugin system unless simpler composition documented as failing.
  - **VIOLATION #3 — `MigrationRegistry` skeleton (FR-45)**:
    - Заявлен «empty registry + interface MUST быть подготовлен», rationale — «когда первая migration понадобится».
    - **Direct violation §Refuse #9**: «single-implementation interface with no port-shaped seam».
    - Migrations нет в F-3, и до bump'а первой `schemaVersion` (что произойдёт минимум через несколько релизов) registry будет пустым.
    - **Fix**: убрать FR-045 skeleton requirement. Оставить policy statement «при первом version bump'е добавим migration code path, форма решается тогда». Это **доступно по docs**, не **обязано** быть в коде до того как реально нужно.

  - **`WizardStepRegistry`** — see CHK002 above (also violation of CHK004 angle).

## New modules / packages

- [✓] **CHK005** New gradle module satisfies Article V §3 criteria.
  - `core/wizard/`: ownership boundary (wizard logic, не сжатый в app), build isolation (KMP commonMain → JVM testable), stable API (port-based, ConfigSource/WizardEngine/UserPreferencesStore), material testability gain (no эмулятор для бизнес-логики).
  - `core/localization/`: ownership boundary (i18n logic), KMP build isolation, stable API (StringResolver port), material testability gain.
  - `core/ui-senior/`: ownership boundary (senior-friendly UI primitives), Android Compose only — build isolation marginal, но stable API (named primitives), independent enable/disable (app-family может swap).

- [✓] **CHK006** Why is package not enough?
  - Implicit в C-7 (KMP commonMain target) + C-2 (future ecosystem reuse). Package within `:app` потеряет JVM testability + KMP target option. **Acceptable rationale**.

- [✓] **CHK007** No "utils/common/helpers" dumping ground.
  - Нет модулей с обобщёнными именами. `core/ui-senior/` имеет utilities (`rememberFontScaleAware`, `SeniorContentDescription`) — но named purpose, не dumping ground. ✓

## New configuration

- [✓] **CHK008** Every new config field has current FR consuming it.
  - **`wizard.manifest`** body: `appFamilyId` → FR-005, `steps` → FR-002. ✓
  - **`screen.layout`** body: `gridRows/gridCols/bottomToolbar/topTabs` → consumer is `BundledConfigSource.load` (FR-019) + future `HomeRenderer` в `:app/` (out of F-3 scope, но FR-013 декларирует format для S-1 consumer). ✓ acceptable as foundation contract.
  - **`tile.set`** body: `tiles/position/actionType/labelKey/iconKey` → consumed by FR-002 WizardEngine produces `initialConfig`. ✓
  - **`system-settings.pool`** body: `settings/mechanism/deepLink/detectionStrategy/etc.` → consumed by FR-055 `AndroidSystemSettingAdapter`. ✓
  - **6-field common header** → consumed by reader infrastructure (FR-015, FR-016). ✓
  - **`CONTEXT.json`** fields `value/context/screenshot` → consumed by FR-031a translation skill. ✓
  - **`UserPreferences`** fields `theme/fontScale/languageOverride/attestedSettings` → consumed by FR-049/050/058. ✓

- [✓] **CHK009** Defaults documented; backward-compat policy defined; migration path для non-trivial structure.
  - `schemaVersion` policy: FR-015 (forward-compat read) + FR-016 (hard-fail на breaking).
  - Defaults: `ThemeChoice = Auto`, `fontScale = null = system`, `languageOverride = null = system`, `canSkip: Boolean? = false`.
  - Migration path: documented в FR-016 (hard-fail UI) + FR-045 policy statement. Note: skeleton removed per CHK004 fix.

## CLAUDE.md rule 4 self-test

- [⚠] **CHK010** Test 1 (inline what would be lost) applied for each new abstraction.
  - `WizardEngine` inlined → coupling first-run logic в app, KMP reuse loss → **Keep**. ✓
  - `ConfigSource` inlined → future FileConfigSource/NetworkConfigSource не additive → **Keep**. ✓
  - `StringResolver` inlined → fallback chain scattered → **Keep**. ✓
  - `WizardStepRegistry` inlined → loss = future optionality only → **REMOVE per CHK002**. ✗
  - `MigrationRegistry skeleton` inlined → loss = nothing (нет миграций) → **REMOVE per CHK004**. ✗
  - `ResourceReader` inlined → loss = nothing (moko-resources уже abstracts) → **REMOVE per CHK001**. ✗
  - `DiagnosticEmitter` inlined → testability + analytics swap loss; `RecordingDiagnosticEmitter` fake уже используется → **Keep marginal**. ✓
  - `TutorialHintManager` inlined → persistent dismissed flag scattered → **Keep**. ✓
  - `SystemSettingPort` inlined → permission/deeplink/accessibility logic в каждом step → **Keep**. ✓
  - `UserPreferencesStore` inlined → DataStore direct в `:app`, потеря testability → **Keep**. ✓
  - `LocaleProvider` inlined → fake невозможен, runtime locale tests требуют эмулятор → **Keep**. ✓

- [✓] **CHK011** Test 2 (swap cost if dependency dies).
  - moko-resources → 2-3 days swap via StringResolver port. ✓
  - DataStore → 1 day swap via UserPreferencesStore/WizardCheckpointStore/DismissedHintsStore ports. ✓
  - AccessibilityService → SystemSettingPort абстрагирует; swap impl ~1 day. ✓
  - Claude API translation → **⚠ minor concern**: FR-031a..d tightly coupled to Claude API. Если Claude дороже/deprecated, swap к DeepL/GPT требует переписать skill body. No `Translator` port декларирован.
    - **Acceptable per rule 4**: alternative providers — explicitly OUT-021. Port = premature до second consumer (= second translator). Принимаем coupling.
    - **Inline-TODO suggestion** (для clarity): добавить comment в FR-031a про exit ramp на alternative provider если Claude pricing change.

## Removal validation

- [✓] **CHK012** Dangling references audited.
  - `PermissionRequestPort` — removed cleanly from spec (только historical mentions в C-18 + FR-008 как «заменяет старый PermissionStep» — это intentional traceability, не dangling).
  - `PermissionStep` — mentioned only в (a) C-18 historical context, (b) FR-008 as «заменяет старый PermissionStep», (c) OUT-009 в quote из glossary §3 — все три intentional.
  - **No actual dangling references**. ✓

- [N/A] **CHK013** Deprecated code removal task — N/A: F-3 строит новые модули, не deprecates existing.

---

## Issues & fixes

### Issue M-1 — `WizardStepRegistry` (FR-009) premature

**Severity**: Medium (one-way door через DI architecture choice).

**Fix**: убрать FR-009. Изменить FR-002:
```
WizardEngine конструируется через DI с параметром `steps: Map<StepType, WizardStep>`.
Bundled steps (FR-008) регистрируются на уровне app DI module.
Custom steps от app-family добавляются в тот же Map в app's DI module — без отдельного registry.
```

### Issue M-2 — `MigrationRegistry` skeleton (FR-045) premature

**Severity**: Low (just remove the «MUST be prepared» mandate).

**Fix**: переписать FR-045:
```
Wire format breaking changes MUST требовать написания migration code до ship'а.
Migration mechanism определяется когда первый bump происходит (в будущей spec), не предзаложен в F-3.
```

### Issue M-3 — `ResourceReader` port (FR-043) orphan

**Severity**: Low (cosmetic — port mentioned, never used).

**Fix**: переписать FR-043:
```
Все три модуля MUST соответствовать CLAUDE.md rule 1: domain ports в commonMain,
без vendor SDK / Android system types в signatures. Resource loading осуществляется
через moko-resources API (которая сама — platform-aware ACL); прямые Android API
calls запрещены в core/* модулях (защищено lint rule FR-038).
```

---

## Резюме

**10 ✓ / 3 ✗** — три fix'а нужны до plan.md:

1. Убрать `WizardStepRegistry` (FR-009) → constructor-based DI
2. Убрать `MigrationRegistry` skeleton (FR-045) → policy statement only
3. Убрать `ResourceReader` port (FR-043) → moko-resources уже abstracts

Это **классический premature abstraction** паттерн — три места где «на будущее» заложили инфраструктуру без current consumer. Rule 4 MVA explicit prohibits.

После fix'ов спека становится чище и меньше на ~6 строк. Никаких функциональных изменений.
