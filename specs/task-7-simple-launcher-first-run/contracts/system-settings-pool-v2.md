# Wire Format: `system-settings.pool` schemaVersion 2

**Date**: 2026-06-24 | **Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **Data model**: [../data-model.md](../data-model.md)

This document defines the schemaVersion 2 of the `system-settings.pool` wire format introduced in TASK-7. v1 readers MUST refuse v2 documents (per CLAUDE.md rule 5 schemaVersion semantics). v2 readers MUST be able to read v1 documents (backward-compat read).

---

## 1. Document header

Same as v1 (unchanged):

```json
{
  "schemaVersion": 2,
  "id": "system-settings.<slug>",
  "name": "<localization_key_for_name>",
  "description": "<localization_key_for_description>",
  "deviceClass": ["android-phone"],
  "body": { /* settings array */ }
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `schemaVersion` | int | yes | **`2`** in this version. Bumped from `1`. |
| `id` | string | yes | Stable globally-unique identifier in `system-settings.<slug>` format. |
| `name` | string (key) | yes | Localization key resolved via `StringResolver`. |
| `description` | string (key) | yes | Localization key. |
| `deviceClass` | string[] | yes | Supported device classes. `["android-phone"]`, `["*"]`, etc. |
| `body` | object | yes | v2-specific body shape (see §2). |

---

## 2. Body shape

```json
"body": {
  "platform": "android",
  "settings": [
    { /* SystemSettingEntry */ },
    { /* ... */ }
  ]
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `platform` | string | yes | Platform identifier. `"android"`, `"ios"`, `"android-tv"`, `"*"` (cross-platform). |
| `settings` | array | yes | Array of `SystemSettingEntry` (see §3). |

---

## 3. `SystemSettingEntry` (v2)

```json
{
  "id": "android.role.home",
  "mechanism": "DeepLink",
  "criticality": "Required",
  "canSkip": true,
  "deepLink": null,
  "androidMinApi": 29,
  "dependsOn": [],
  "detectionStrategy": "Programmatic",
  "labelKey": "system_setting_role_home_label",
  "descriptionKey": "system_setting_role_home_desc",
  "extendedInstructionKey": "system_setting_role_home_retry_message",
  "check": { "kind": "android-package-home" },
  "apply": { "kind": "android-role-request", "role": "HOME" }
}
```

### 3.1 v1 fields (unchanged in v2)

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | yes | Stable unique identifier (e.g., `android.role.home`, `android.permission.POST_NOTIFICATIONS`). |
| `mechanism` | enum | yes | One of `StandardPermission`, `SpecialPermission`, `AccessibilityService`, `DeepLink`, `InAppOnly`. **Retained for v1 backward-compat dispatch.** Will be deprecated in a future schema bump (v3) once all entries are migrated to v2 with explicit `check`/`apply` blocks. |
| `criticality` | enum | yes | `Required` or `Optional`. Default per-pool; can be overridden per-profile by `wizard.manifest` `StepEntry.criticality`. |
| `canSkip` | bool | no, default `false` | Can the wizard step skip this entry? Per-profile override available via `StepEntry.canSkip`. |
| `deepLink` | string?, default `null` | no | Hint for `mechanism: DeepLink`. e.g., `"Settings.ACTION_ACCESSIBILITY_SETTINGS"`. **Used only by legacy v1 dispatch path.** |
| `androidMinApi` | int?, default `null` | no | Minimum Android API level the entry applies to. Entries with API > current device → auto-skipped by engine. |
| `dependsOn` | string[] | no | List of `id` references this entry depends on. Reserved for future use. |
| `detectionStrategy` | enum | yes | One of `Programmatic`, `SelfAttest`, `Indeterminate`. **Retained for v1 backward-compat.** |
| `labelKey` | string (key) | yes | Localization key for the wizard step title. |
| `descriptionKey` | string (key) | yes | Localization key for the wizard step body text. |
| `extendedInstructionKey` | string?, default `null` | no | Localization key for retry / extended help text shown when user refuses in system dialog. |

### 3.2 New fields in v2

| Field | Type | Required | Description |
|---|---|---|---|
| `check` | `CheckSpec`?, default `null` | no | Declarative spec for "is this setting applied?" check. See §4. If `null` → legacy `mechanism + id` dispatch. |
| `apply` | `ApplySpec`?, default `null` | no | Declarative spec for "how to apply this setting" prompt. See §5. If `null` → legacy `mechanism + id` dispatch. |

**Migration policy**: existing v1 entries SHOULD migrate to v2 by adding `check` and `apply` blocks. Once all entries in a pool are migrated, v3 schema bump removes `mechanism`, `deepLink`, `detectionStrategy` from `SystemSettingEntry` (those become derivable from `check`/`apply`).

---

## 4. `CheckSpec` (sealed, polymorphic JSON)

Discriminator field: `kind`.

### 4.1 `android-role`

```json
"check": { "kind": "android-role", "role": "HOME" }
```

Checks if our application holds the named Android role. Implementation: `RoleManager.isRoleHeld(role)` on API 29+; legacy `PackageManager.resolveActivity(CATEGORY_HOME)` fallback on API 26-28.

| Field | Type | Required | Description |
|---|---|---|---|
| `kind` | string | yes | `"android-role"` (discriminator). |
| `role` | string | yes | Android role identifier. e.g., `"HOME"` → maps to `RoleManager.ROLE_HOME`. |

### 4.2 `android-permission`

```json
"check": { "kind": "android-permission", "permission": "android.permission.POST_NOTIFICATIONS" }
```

Checks if the app holds the named standard runtime permission. Implementation: `PermissionRequestPort.isGranted(permission)`.

| Field | Type | Required | Description |
|---|---|---|---|
| `kind` | string | yes | `"android-permission"`. |
| `permission` | string | yes | Fully-qualified Android permission name (e.g., `android.permission.POST_NOTIFICATIONS`, `android.permission.CALL_PHONE`). |

### 4.3 `android-special-permission`

```json
"check": { "kind": "android-special-permission", "variant": "ignore_battery_optimizations" }
```

Checks special permissions that require system-settings deep-link rather than standard runtime request. Implementation dispatches per `variant`.

| Field | Type | Required | Description |
|---|---|---|---|
| `kind` | string | yes | `"android-special-permission"`. |
| `variant` | string | yes | Specific special-permission kind. Known values: `"ignore_battery_optimizations"` (→ `PowerManager.isIgnoringBatteryOptimizations()`). Unknown variants → `Indeterminate`. |

### 4.4 `android-accessibility-service`

```json
"check": { "kind": "android-accessibility-service", "componentName": "com.launcher.app/.MyAccessibilityService" }
```

Checks if our accessibility service is enabled. **Programmatic detection is unreliable across OEMs** (Samsung KNOX restrictions, Xiaomi MIUI quirks); current implementation returns `Indeterminate` (SelfAttest pattern).

| Field | Type | Required | Description |
|---|---|---|---|
| `kind` | string | yes | `"android-accessibility-service"`. |
| `componentName` | string?, default `null` | no | Component name to check. `null` → check any of our accessibility services enabled. |

### 4.5 `android-package-home`

```json
"check": { "kind": "android-package-home", "packageName": null }
```

Checks if a package is the current default home (launcher) app. Implementation: `PackageManager.resolveActivity(Intent(ACTION_MAIN).addCategory(CATEGORY_HOME))` and compare.

| Field | Type | Required | Description |
|---|---|---|---|
| `kind` | string | yes | `"android-package-home"`. |
| `packageName` | string?, default `null` | no | Package to check. `null` → check our own package. |

### 4.6 Future cross-platform variants

When iOS adapter module materializes (TASK-26), additional variants added to the same sealed hierarchy in `commonMain`:
- `ios-info-plist` — checks `Info.plist` key.
- `ios-authorization` — checks Authorization status.
- `android-tv-leanback` — Android TV-specific.

Variants whose handler is not registered in the current build → `SettingStatus.Indeterminate` (graceful degradation per Article VII §15).

---

## 5. `ApplySpec` (sealed, polymorphic JSON)

Discriminator field: `kind`.

### 5.1 `standard-permission-request`

```json
"apply": { "kind": "standard-permission-request", "permission": "android.permission.POST_NOTIFICATIONS" }
```

Calls `PermissionRequestPort.request(permission)`. Returns `Applied`, `Denied`, or `PermanentlyDenied`.

| Field | Type | Required | Description |
|---|---|---|---|
| `kind` | string | yes | `"standard-permission-request"`. |
| `permission` | string | yes | Fully-qualified Android permission name. |

### 5.2 `android-role-request`

```json
"apply": { "kind": "android-role-request", "role": "HOME" }
```

Calls `RoleManager.createRequestRoleIntent(role)` + `context.startActivity()`. Returns `PromptShown`.

| Field | Type | Required | Description |
|---|---|---|---|
| `kind` | string | yes | `"android-role-request"`. |
| `role` | string | yes | Android role identifier. |

### 5.3 `settings-deep-link`

```json
"apply": { "kind": "settings-deep-link", "action": "android.settings.APP_NOTIFICATION_SETTINGS", "packageScoped": true }
```

Builds `Intent(action)` (optionally scoped to package via `Uri.parse("package:${packageName}")`) and `context.startActivity()`.

| Field | Type | Required | Description |
|---|---|---|---|
| `kind` | string | yes | `"settings-deep-link"`. |
| `action` | string | yes | Intent action. e.g., `"android.settings.ACCESSIBILITY_SETTINGS"`, `"android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"`. |
| `packageScoped` | bool | no, default `false` | If `true`, wraps in `Uri.parse("package:${packageName}")`. |

### 5.4 `in-app-only`

```json
"apply": { "kind": "in-app-only" }
```

No-op apply (caller handles in-app toggle UI elsewhere). Returns `PromptShown`.

| Field | Type | Required | Description |
|---|---|---|---|
| `kind` | string | yes | `"in-app-only"`. |

---

## 6. Migration example

Existing v1 entry (current `android-pool.json`):

```json
{
  "id": "android.role.home",
  "mechanism": "DeepLink",
  "criticality": "Required",
  "canSkip": true,
  "deepLink": "RoleManager.createRequestRoleIntent(ROLE_HOME)",
  "androidMinApi": 29,
  "dependsOn": [],
  "detectionStrategy": "Programmatic",
  "labelKey": "system_setting_role_home_label",
  "descriptionKey": "system_setting_role_home_desc"
}
```

After v2 migration:

```json
{
  "id": "android.role.home",
  "mechanism": "DeepLink",
  "criticality": "Required",
  "canSkip": true,
  "deepLink": null,
  "androidMinApi": 29,
  "dependsOn": [],
  "detectionStrategy": "Programmatic",
  "labelKey": "system_setting_role_home_label",
  "descriptionKey": "system_setting_role_home_desc",
  "extendedInstructionKey": "system_setting_role_home_retry_message",
  "check": { "kind": "android-package-home" },
  "apply": { "kind": "android-role-request", "role": "HOME" }
}
```

The `mechanism`, `deepLink`, `detectionStrategy` fields are retained in v2 for backward-compat — readers that ignore `check`/`apply` (none should after migration, but defensively) fall back to legacy dispatch. v3 schema bump (future) removes them.

---

## 7. Roundtrip & backward-compat test requirements

- **Roundtrip test** (CLAUDE.md rule 5): every `SystemSettingsPool` v2 fixture serialize → deserialize → assert deep-equal.
- **Backward-compat read test**: every v1 fixture (no `check`/`apply` blocks) deserialize via v2 reader → assert `check == null && apply == null`; legacy dispatch path then exercises status / apply correctly.
- **CheckSpec roundtrip test**: every variant serialize → deserialize → assert equal. Discriminator field `kind` correctly mapped.
- **ApplySpec roundtrip test**: same.
- **Unknown CheckSpec `kind` test**: deserialize JSON with unknown `kind` value → handler registry returns `Indeterminate` (graceful, not crash).

---

## 8. Backward-compat schedule

| Schema version | Status | Removal target |
|---|---|---|
| v1 (current) | **Read-only** for new readers. v1 readers refuse v2 documents. | Removal of v1 read path: TASK-22 or after all bundled entries migrated to v2. |
| v2 (TASK-7) | **Current write target.** Forward-compat reads supported. | — |
| v3 (future) | Removes `mechanism`, `deepLink`, `detectionStrategy` from `SystemSettingEntry`; relies entirely on `check`/`apply`. | TBD; triggered when v2 entries cover all settings. |

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** SchemaVersion 2 формата `system-settings.pool`. Главное добавление — два новых поля в `SystemSettingEntry`: `check: CheckSpec?` (как проверить, применена ли настройка) и `apply: ApplySpec?` (как её применить). Sealed JSON с дискриминатором `kind`. Старые поля (`mechanism`, `deepLink`, `detectionStrategy`) retained для backward-compat и будут удалены в v3.

**Конкретика:**
- **5 CheckSpec variants**: `android-role`, `android-permission`, `android-special-permission`, `android-accessibility-service`, `android-package-home`.
- **4 ApplySpec variants**: `standard-permission-request`, `android-role-request`, `settings-deep-link`, `in-app-only`.
- **v1 → v2 backward-compat read**: v1 entries загружаются через v2 reader, `check`/`apply` становятся `null`; адаптер fall back на legacy dispatch.
- **Future-compat (Kubernetes-style)**: unknown fields ignored — v3 readers смогут читать v2 без проблем.
- **Roundtrip + backward-compat tests** обязательны для каждой sealed variant и каждого pool fixture.

**На что смотреть с осторожностью:**
- **Field `mechanism` retained в v2** — это не дубликат `apply`, это legacy fallback. Должен быть удалён в v3 после full migration. Inline TODO в коде reader'а.
- **`android-accessibility-service` всегда возвращает Indeterminate** на текущей реализации — programmatic detection unreliable на разных OEM. SelfAttest path для UI.
- **Unknown CheckSpec kind → Indeterminate**, не crash. Это intentional graceful degradation для будущих platform variants (iOS, Android TV).
