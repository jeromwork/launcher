# Wire Formats: F-3

**Date**: 2026-06-16 | **Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md)

Все **8** wire formats F-3: 5 bundled JSON schemas + 3 persistent stores.

**Versioning policy**: per CLAUDE.md rule 5 + glossary §4.3:
- Forward-compat: unknown additive fields silently ignored.
- Hard-fail on `schemaVersion > known`: returns `IncompatibleVersion`, UI shows fallback screen (per Q-6 (b)).
- Backward-compat reads: `schemaVersion < current` supported for ≥ 1 major release; migration code MUST ship before bump.

---

## Bundled JSON schemas (5)

All share the **6-field common header** (per glossary §4.1):

```json
{
  "schemaVersion": <int>,                 // initial = 1; bump на breaking change
  "id": "<kind>.<slug>",                  // stable globally-unique id
  "name": "<localization-key>",           // key, NOT literal
  "description": "<localization-key>",    // key, NOT literal
  "deviceClass": ["android-phone" | "android-tv" | "*"],
  "body": { /* kind-specific */ }
}
```

---

### Schema 1: `wizard.manifest`

**Purpose**: Описание first-run flow для одной app-family.
**Consumer**: `WizardEngine.run()`.
**Bundled location**: `core/wizard/src/commonMain/resources/MR/files/wizard-manifests/<id>.json`.
**Test fixture**: `core/wizard/src/commonTest/resources/fixtures/wizard-manifests/test-app-family.json`.

#### Body schema (v1)

```json
{
  "appFamilyId": "simple-launcher",
  "autoOrder": true,
  "steps": [
    {
      "stepType": "UIChoice" | "SystemSetting" | "TutorialHint",
      "refId": "language" | "android.role.home" | "first-tile-hint",
      "params": { /* step-specific params, JsonElement */ },
      "canSkip": false,
      "criticality": "Required" | "Optional"
    }
  ]
}
```

- `autoOrder: true` → engine ignores `steps[]`, generates order automatically from pool entries (Required first, Optional after). Per FR-014c.
- `autoOrder: false` → `steps[]` is authoritative.

#### Example

```json
{
  "schemaVersion": 1,
  "id": "wizard-manifest.simple-launcher",
  "name": "wizard_manifest.simple_launcher.name",
  "description": "wizard_manifest.simple_launcher.desc",
  "deviceClass": ["android-phone"],
  "body": {
    "appFamilyId": "simple-launcher",
    "autoOrder": true,
    "steps": null
  }
}
```

---

### Schema 2: `screen.layout`

**Purpose**: Каркас экрана: размер grid + опциональные toolbar / tabs. **НЕ содержит данные плиток**.
**Consumer**: HomeRenderer в `:app/` (out of F-3 scope), но F-3 defines schema + parsing for S-1 consumption.
**Bundled location**: `core/wizard/src/commonMain/resources/MR/files/screen-layouts/<id>.json`.

#### Body schema (v1)

```json
{
  "gridRows": 4,
  "gridCols": 3,
  "bottomToolbar": {
    "actions": ["back", "settings"]
  },
  "topTabs": [
    { "labelKey": "tab.home", "iconKey": "icon.home" }
  ]
}
```

`bottomToolbar` и `topTabs` опциональные.

---

### Schema 3: `tile.set`

**Purpose**: Обезличенный стартовый набор плиток (data, не behavior).
**Consumer**: WizardEngine (когда `UIChoiceStep("tileSet")` показывает picker) + S-1 (когда генерирует initial ConfigDocument).
**Bundled location**: `core/wizard/src/commonMain/resources/MR/files/tile-sets/<id>.json`.

#### Body schema (v1)

```json
{
  "tiles": [
    {
      "position": { "row": 0, "col": 0 },
      "actionType": "phone.call",           // opaque ref к capability registry (спека 005)
      "labelKey": "tile.maria.label",
      "iconKey": "icon.contact_woman"
    }
  ]
}
```

**Note F-3 scope**: tile.set — только **data schema**. F-3 НЕ обрабатывает tap behavior — это S-1 (tile-action handler) + F-2 (capability registry runtime).

---

### Schema 4: `system-settings.pool` (per Part K)

**Purpose**: Реестр доступных Android system settings с метаданными (mechanism, detection, deep-link).
**Consumer**: `AndroidSystemSettingAdapter.applyOrPrompt / status` + `SystemSettingStep`.
**Bundled location**: `core/wizard/src/commonMain/resources/MR/files/system-settings/android-pool.json`.

#### Body schema (v1)

```json
{
  "platform": "android",
  "settings": [
    {
      "id": "android.role.home",
      "mechanism": "DeepLink",
      "criticality": "Required",
      "canSkip": true,
      "deepLink": "RoleManager.createRequestRoleIntent(ROLE_HOME)",
      "androidMinApi": 29,
      "dependsOn": [],
      "detectionStrategy": "Programmatic",
      "labelKey": "system_setting.role_home.label",
      "descriptionKey": "system_setting.role_home.desc",
      "extendedInstructionKey": null
    }
  ]
}
```

#### F-3 bundled entries (per FR-053a)

| id | mechanism | criticality | canSkip | androidMinApi | detection |
|---|---|---|---|---|---|
| `android.role.home` | DeepLink | **Required** | true | 29 | Programmatic |
| `android.permission.POST_NOTIFICATIONS` | StandardPermission | **Required** | false | 33 | Programmatic |
| `android.permission.CALL_PHONE` | StandardPermission | Optional | true | — | Programmatic |
| `android.accessibility.our-service` | AccessibilityService | Optional | true | — | Programmatic |
| `android.battery.ignore_optimizations` | SpecialPermission | Optional | true | 23 | Programmatic |
| `android.hide_status_bar` | AccessibilityService | Optional | true | — | Indeterminate → SelfAttest |

---

### Schema 5: `ui-customization.pool` (new per FR-014a)

**Purpose**: Реестр UI / language / theme опций пользователя. **Parallel structure** к `system-settings.pool` (per C-25).
**Consumer**: `UIChoiceStep`.
**Bundled location**: `core/wizard/src/commonMain/resources/MR/files/ui-customization/ui-pool.json`.

#### Body schema (v1)

```json
{
  "platform": "*",
  "options": [
    {
      "id": "language",
      "kind": "simple-choice",
      "questionKey": "ui.language.question",
      "descriptionKey": null,
      "criticality": "Required",
      "defaultValue": "en",
      "choices": [
        { "value": "en", "labelKey": "ui.language.en" },
        { "value": "ru", "labelKey": "ui.language.ru" }
      ],
      "choicesFrom": null
    },
    {
      "id": "tileSet",
      "kind": "pick-from-bundled",
      "questionKey": "ui.tileSet.question",
      "descriptionKey": null,
      "criticality": "Required",
      "defaultValue": "classic-6",
      "choices": null,
      "choicesFrom": { "kind": "tile.set", "filter": null }
    }
  ]
}
```

#### F-3 bundled entries

| id | kind | criticality | choices source |
|---|---|---|---|
| `language` | simple-choice | Required | inline — 11 поддерживаемых языков (en, ru, es, zh, ar, hi, pt, de, fr, ja, kk-Latn) |
| `theme` | simple-choice | Optional | inline — `light`, `dark`, `auto` |
| `fontScale` | simple-choice | Optional | inline — `1.0`, `1.3`, `1.6` |
| `grid` | simple-choice | Optional | inline — `2×3`, `3×4`, `4×5` |
| `screenLayout` | pick-from-bundled | Optional | choicesFrom: `screen.layout` bundled JSONs |
| `tileSet` | pick-from-bundled | Required | choicesFrom: `tile.set` bundled JSONs |

---

## Persistent stores (3)

These wire formats persist across app version updates. Per CLAUDE.md rule 5, each has explicit `schemaVersion`.

---

### Format A: `WizardCheckpoint`

**Purpose**: Прогресс прохождения wizard'а. Переживает process death + device reboot.
**Storage**: DataStore (app-private), key namespace `wizard.checkpoint.<manifestId>`.

```kotlin
@Serializable
data class WizardCheckpoint(
  val schemaVersion: Int = 1,
  val manifestId: String,
  val currentStepIndex: Int,
  val answers: Map<String, JsonElement>   // StepId → answer
)
```

**Bumped schemaVersion handling**: load with `schemaVersion > 1` → engine treats as invalid → starts from step 0 (graceful, no crash, no UI dialog). Per FR-003.

---

### Format B: `UserPreferences`

**Purpose**: User UX preferences (theme, fontScale, language override) + self-attestation records.
**Storage**: DataStore (app-private), key `user.preferences`.
**Future migration target**: `ConfigDocument.userPreferences` (in spec 008) after F-4 + cloud sync ready.

```kotlin
@Serializable
data class UserPreferences(
  val schemaVersion: Int = 1,
  val theme: ThemeChoice,
  val fontScale: Float?,
  val languageOverride: String?,    // BCP-47 tag
  val attestedSettings: Map<String, AttestationRecord>
)

@Serializable
enum class ThemeChoice { Light, Dark, Auto }

@Serializable
data class AttestationRecord(
  val attestedAt: Instant,           // kotlinx.datetime.Instant
  val value: Boolean
)
```

**Bumped schemaVersion handling**: load with `schemaVersion > 1` → store returns defaults (graceful migration: wizard re-runs, prefs reset). Per FR-047.

---

### Format C: `DismissedHints`

**Purpose**: Persistent set of dismissed hint IDs.
**Storage**: DataStore, key `dismissed.hints`.

```kotlin
@Serializable
data class DismissedHintsState(
  val ids: Set<String>
)
```

**Note**: minimal structure — нет `schemaVersion` field, потому что просто `Set<String>` (additive по природе). Если в будущем понадобится метадата per hint (`dismissedAt: Instant`) — bump'аем schema тогда.

---

## Dev-time JSON (1)

### Format: `CONTEXT.json`

**Purpose**: Per-key context для translation pipeline (per FR-031b). Read by `procedure-translate-spec-strings` skill.
**Location**: `core/localization/strings-context/CONTEXT.json`.

```json
{
  "schemaVersion": 1,
  "entries": {
    "wizard.next_button": {
      "value": "Next",
      "context": "Button on bottom of wizard step screen. Tap → moves to next step. Senior-friendly: ≥56dp tap target, primary color.",
      "screenshot": "docs/screenshots/wizard-step-bottom.png"
    }
  }
}
```

**Bumped schemaVersion handling**: skill aborts с error message «CONTEXT.json schemaVersion mismatch — update translation skill or revert».

---

## Test coverage requirements

Per FR-017 + SC-002, **каждая** из 8 форматов требует:

| Format | Roundtrip test | Forward-compat test | Hard-fail test |
|---|---|---|---|
| `wizard.manifest` | ✓ FR-017 | ✓ FR-015 (unknown field ignored) | ✓ FR-016 (schemaVersion=999) |
| `screen.layout` | ✓ | ✓ | ✓ |
| `tile.set` | ✓ | ✓ | ✓ |
| `system-settings.pool` | ✓ | ✓ | ✓ |
| `ui-customization.pool` | ✓ (new) | ✓ | ✓ |
| `WizardCheckpoint` | ✓ | N/A (controlled by engine) | ✓ (graceful → step 0) |
| `UserPreferences` | ✓ | N/A | ✓ (graceful → defaults) |
| `DismissedHints` | ✓ | N/A | N/A (no schemaVersion yet) |
| `CONTEXT.json` | ✓ (skill-side) | N/A | ✓ (skill abort) |

---

## Краткое содержание простым русским языком

Этот документ — **формат всех файлов**, которые F-3 будет читать и писать на диске.

**5 «постоянных» JSON-файлов** (которые поставляются вместе с приложением и не меняются):

1. **wizard.manifest** — «программа мастера»: какие шаги показывать, в каком порядке.
2. **screen.layout** — «каркас экрана»: сколько столбцов и строк в сетке плиток, есть ли нижняя панель.
3. **tile.set** — «стартовый набор плиток»: где какая плитка, какое действие, какая иконка.
4. **system-settings.pool** — «справочник системных настроек»: какие настройки Android доступны (ROLE_HOME, уведомления и т.д.), как их применить, как проверить.
5. **ui-customization.pool** *(новый)* — «справочник UI опций»: какой язык/тема/шрифт можно выбрать, варианты, по умолчанию.

**3 «изменяющихся» файла** (хранятся локально, обновляются по мере использования):

6. **WizardCheckpoint** — «закладка»: где остановился пользователь в мастере.
7. **UserPreferences** — «настройки пользователя»: тема, шрифт, язык + что пользователь подтвердил при настройке.
8. **DismissedHints** — «прочитанные подсказки»: что пользователь нажал «Понял», чтобы не показывать второй раз.

**1 файл для разработки** (не в приложении, а в репозитории):

9. **CONTEXT.json** — «справочник переводчика»: для каждого ключа строки — описание контекста («это кнопка на дне экрана wizard'а, для пожилых») чтобы Claude переводил качественно.

**Главное правило**: каждый формат имеет **версию** (`schemaVersion`). Когда мы захотим изменить формат — поднимаем версию, пишем код миграции, старые версии приложения покажут понятное сообщение «обновите приложение». Это страховка от ломания телефонов пользователей при обновлении.
