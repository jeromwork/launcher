# Wire Format: `simple-launcher.wizard.manifest.json` content

**Date**: 2026-06-24 | **Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md)

> **⚠️ UPDATE 2026-06-25.** Production manifest is now **3 steps**, not 4. The 4th `Custom("pair-admin") Optional` entry was **removed** per constitution amendment 1.10 (no `StepType.Custom`, no per-refId handlers). Pair-admin returns at TASK-8 as a standard `SystemSetting` step — see TODO-TASK7-005 in [`../../../docs/dev/project-backlog.md`](../../../docs/dev/project-backlog.md). The "expected JSON" example below still shows 4 entries as a historical record of the original Phase-5 design; the **canonical current state** lives in [`core/src/androidMain/assets/wizard/wizard-manifests/simple-launcher.json`](../../../core/src/androidMain/assets/wizard/wizard-manifests/simple-launcher.json).

This document specifies the **content** of the bundled `simple-launcher.wizard.manifest.json` file after TASK-7 migrates it from `autoOrder: true, steps: null` to explicit steps with per-profile overrides.

The wire format itself (`wizard.manifest` schema) is owned by F-3 / spec 015 and remains at `schemaVersion: 1`. TASK-7 only updates the content of this specific bundled instance.

---

## 1. Manifest document

```json
{
  "schemaVersion": 1,
  "id": "wizard-manifest.simple-launcher",
  "name": "wizard_manifest_simple_launcher_name",
  "description": "wizard_manifest_simple_launcher_desc",
  "deviceClass": ["android-phone"],
  "body": {
    "presetId": "simple-launcher",
    "autoOrder": false,
    "steps": [
      {
        "stepType": "SystemSetting",
        "refId": "android.role.home",
        "params": {},
        "canSkip": false,
        "criticality": "Required"
      },
      {
        "stepType": "UIChoice",
        "refId": "tileSet",
        "params": {},
        "canSkip": false,
        "criticality": "Required"
      },
      {
        "stepType": "SystemSetting",
        "refId": "android.permission.POST_NOTIFICATIONS",
        "params": {},
        "canSkip": true,
        "criticality": "Required"
      },
      {
        "stepType": "Custom",
        "refId": "pair-admin",
        "params": {},
        "canSkip": true,
        "criticality": "Optional"
      }
    ]
  }
}
```

---

## 2. Step-by-step rationale

### Step 1 — `SystemSetting` → `android.role.home`

| Field | Value | Source / rationale |
|---|---|---|
| `stepType` | `SystemSetting` | References a `system-settings.pool` entry. |
| `refId` | `android.role.home` | Entry in `system-settings/android-pool.json`. |
| `canSkip` | `false` | **Per-profile override.** Pool default is `true` (admin profile may skip it). For `simple-launcher`, ROLE_HOME is essential — without it Home button doesn't open our app. |
| `criticality` | `Required` | Matches pool default. |

**Texts**: `labelKey`, `descriptionKey`, `extendedInstructionKey` come from the pool entry (not from manifest). Wizard shows `system_setting_role_home_label` ("Сделать наш лончер главным") + `system_setting_role_home_desc` + on retry shows `system_setting_role_home_retry_message` ("Без этого Home button не откроет приложение — попробуй ещё раз").

### Step 2 — `UIChoice` → `tileSet`

| Field | Value | Source / rationale |
|---|---|---|
| `stepType` | `UIChoice` | References a `ui-customization.pool` entry. |
| `refId` | `tileSet` | Entry in `ui-customization/ui-pool.json` (existing). `kind: "pick-from-bundled", choicesFrom: { kind: "tile.set" }`. |
| `canSkip` | `false` | Matches pool default. Without a tileSet, home screen is empty. |
| `criticality` | `Required` | Matches pool default. |

**Available choices**: bundled tile.set documents. Currently only `classic-6` ships in TASK-7. Future tile.set bundles ship as new JSON without code changes (Article II §8).

### Step 3 — `SystemSetting` → `android.permission.POST_NOTIFICATIONS`

| Field | Value | Source / rationale |
|---|---|---|
| `stepType` | `SystemSetting` |  |
| `refId` | `android.permission.POST_NOTIFICATIONS` | Entry in pool. `androidMinApi: 33` → engine auto-skips on API < 33. |
| `canSkip` | `true` | **Per-profile override.** Pool default is `false` (for profiles where notifications are critical). For `simple-launcher`, notifications activate when admin push lands (TASK-8); in TASK-7 LOCAL mode, notifications are nice-to-have, not blocking. |
| `criticality` | `Required` | Matches pool default. Required-with-skip means: shown in wizard with skip option; if skipped, surfaces in Settings pending checklist with `[!]` indicator (per Сценарий 4). |

### Step 4 — `Custom` → `pair-admin`

| Field | Value | Source / rationale |
|---|---|---|
| `stepType` | `Custom` | Pairing doesn't fit `UIChoice` or `SystemSetting`; it launches an external Activity (spec 007 `PairingActivity`). |
| `refId` | `pair-admin` | Identifier for `PairAdminCustomStepHandler` in DI registry. |
| `canSkip` | `true` | Optional. |
| `criticality` | `Optional` | "Optional Silent" per Article VII §12 — no banner in Settings after skip (cloud config push isn't available until TASK-8 anyway). |

**Step handler**: `PairAdminCustomStepHandler` in `core/androidMain/adapters/wizard/handlers/`. Launches `PairingActivity` (spec 007), awaits result.

---

## 3. What's NOT in this manifest

Per spec.md FR-004, these pool entries are **not** added to `simple-launcher` wizard manifest. They remain "Optional Silent" — available via Settings, no wizard step, no banner:

- `android.permission.CALL_PHONE` (Optional in pool)
- `android.accessibility.our-service` (Optional)
- `android.battery.ignore_optimizations` (Optional)
- `android.hide_status_bar` (Optional)
- `language` (Required in pool, but **auto-detected** in TASK-7 — language step removed; override via Settings UI)
- `theme` (Optional in pool, **default Auto** applied; change via future Settings entry)
- `fontScale` (Optional)
- `grid` (Optional)
- `screenLayout` (Optional in pool, but only one bundled `screen.layout` in TASK-7 anyway)

---

## 4. Per-profile overrides summary

| `refId` | Pool default `canSkip` | `simple-launcher` override | Rationale |
|---|---|---|---|
| `android.role.home` | `true` | **`false`** | Without ROLE_HOME, simple-launcher isn't a launcher. |
| `android.permission.POST_NOTIFICATIONS` | `false` | **`true`** | Notifications activate with TASK-8; in TASK-7 LOCAL mode skippable. |
| `tileSet` | `false` | `false` (matches) | Empty tileSet = empty home screen; required. |
| `pair-admin` (Custom) | n/a | `true` | Optional silent. |

These overrides demonstrate Article VII §12 (mandatory / optional / skip semantics via pool defaults + per-profile manifest override) on the first concrete profile.

---

## 5. Localization keys consumed

Keys referenced by this manifest (all resolved via `StringResolver`):

| Key | Source | Notes |
|---|---|---|
| `wizard_manifest_simple_launcher_name` | manifest header `name` | Already present in TASK-1. |
| `wizard_manifest_simple_launcher_desc` | manifest header `description` | Already present. |
| `system_setting_role_home_label` | pool entry | Already present. |
| `system_setting_role_home_desc` | pool entry | Already present. |
| `system_setting_role_home_retry_message` | pool entry | **NEW in TASK-7** — added to extendedInstructionKey. |
| `system_setting_post_notifications_label` | pool entry | Already present. |
| `system_setting_post_notifications_desc` | pool entry | Already present. |
| `ui_tileSet_question` | UI pool entry | Already present. |
| `pair_admin_step_label` | **NEW in TASK-7** | Custom step label. |
| `pair_admin_step_desc` | **NEW in TASK-7** | Custom step description. |
| `pair_admin_step_skip_button` | **NEW** | "Настрою позже". |

All NEW keys ship en + ru per ADR-004.

---

## 6. Roundtrip & content validation

- **Roundtrip test**: `simple-launcher.json` deserialize → serialize → assert deep-equal.
- **Content validation test**: assert all 4 steps present in correct order with correct override values (Article VII §12 enforcement).
- **`refId` resolution test**: assert every step's `refId` resolves to an actual entry in the bundled pool (FAIL if `refId` is dangling).
- **Localization key validation**: assert every key referenced by manifest + pool entries has en + ru records in string resources (CI fitness function).

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Манифест `simple-launcher.wizard.manifest.json` после TASK-7 имеет 4 явных step'а в фиксированном order'е: ROLE_HOME (Required, canSkip=false override), tileSet (Required), POST_NOTIFICATIONS (Required, canSkip=true override), pair-admin (Custom Optional). `autoOrder` сменён с `true` на `false`. Старая структура `steps: null` заменена на полный список.

**Конкретика:**
- **2 per-profile canSkip override'а**: ROLE_HOME (pool default true → simple-launcher false), POST_NOTIFICATIONS (pool default false → simple-launcher true).
- **Pairing — `Custom` stepType**, не новый ConfigKind. Handler в androidMain, launches PairingActivity (spec 007).
- **Texts всех шагов** живут в pool entry (labelKey/descriptionKey/extendedInstructionKey), не в manifest'е.
- **3 NEW localization keys** в TASK-7: `system_setting_role_home_retry_message`, `pair_admin_step_label`, `pair_admin_step_desc` (+ `pair_admin_step_skip_button`). Остальные ключи уже в TASK-1.
- **What's NOT included**: language (auto-detect), theme (default Auto), fontScale/grid/screenLayout/CALL_PHONE/accessibility/battery/hide-status-bar — Optional Silent.

**На что смотреть с осторожностью:**
- **`refId` dangling** — если pool entry удалён, а manifest всё ссылается, engine returns "no handler" → wizard падает. Content validation test обязателен.
- **`Custom("pair-admin")` refId должен совпадать** с handler registry key в Koin DI — typo = runtime crash. Konsist fitness function проверяет registry keys vs manifest refIds.
- **`canSkip: true` для POST_NOTIFICATIONS** — это per-profile override от pool default false. Если кто-то решит, что в simple-launcher notifications должны быть mandatory без skip — менять manifest, не pool (per Article VII §12).
