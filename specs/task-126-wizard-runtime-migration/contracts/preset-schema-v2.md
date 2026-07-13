# Contract: Preset JSON Schema v2

**Spec**: FR-003, FR-007, FR-014 | **Plan**: [plan.md](../plan.md) | **Data model**: [data-model.md](../data-model.md)

---

## Schema version history

| Version | Change | Backward-compat |
|---------|--------|----------------|
| 1 | Initial (TASK-120) | — |
| 2 | Added `hintFlow`, `wizardPresentation` | ✓ new fields nullable, v1 readers ignore |

---

## Wire format (JSON)

```json
{
  "schemaVersion": 2,
  "presetId": "simple-launcher",
  "version": 2,
  "layoutKey": "simple",
  "wizardFlow": [
    {
      "poolRef": "launcher-role",
      "order": 1,
      "wizardTitleKey": "step_launcher_role_title",
      "behavior": "AutoApply"
    },
    {
      "poolRef": "font-size-large",
      "order": 2,
      "wizardTitleKey": "step_font_size_title",
      "behavior": "Interactive"
    }
  ],
  "settingsMap": [
    {
      "poolRef": "font-size-large",
      "categoryKey": "settings_display",
      "sensitivity": "Normal"
    }
  ],
  "activeComponents": [],
  "hintFlow": [
    {
      "hintId": "hint-launcher-role",
      "targetComponentId": "launcher-role",
      "textKey": "hint_launcher_role_body"
    }
  ],
  "wizardPresentation": {
    "darkMode": false,
    "typographyScale": "Large"
  }
}
```

---

## Field specification

| Field | Type | Required | Default | Notes |
|-------|------|---------|---------|-------|
| `schemaVersion` | Int | Yes | — | MUST be 2 for v2 writers; v1 readers accept any version they know |
| `presetId` | String | Yes | — | Opaque identifier; stable across versions |
| `version` | Int | Yes | — | Content version (increment on content change) |
| `layoutKey` | String | Yes | — | References home screen layout template |
| `wizardFlow` | Array | Yes | `[]` | Ordered steps; `order` field is canonical sort key |
| `settingsMap` | Array | Yes | `[]` | Settings screen entries |
| `activeComponents` | Array | Yes | `[]` | Always-on components outside wizard |
| `hintFlow` | Array? | **No** | `null` | v2 addition; v1 readers MUST ignore this field |
| `wizardPresentation` | Object? | **No** | `null` | v2 addition; v1 readers MUST ignore; applied once at wizard start |

### `wizardPresentation` object

| Field | Type | Required | Default |
|-------|------|---------|---------|
| `darkMode` | Boolean | No | `false` |
| `typographyScale` | Enum | No | `"Medium"` |

Valid `typographyScale` values: `"Small"`, `"Medium"`, `"Large"`, `"ExtraLarge"`

---

## Backward compatibility

v1 readers (schemaVersion=1 codebases reading a v2 Preset JSON):
- `hintFlow` field → **ignored** (kotlinx.serialization default: unknown keys skipped)
- `wizardPresentation` field → **ignored**
- No crash, no behavior change

v2 readers reading a v1 Preset JSON:
- `hintFlow` → `null` → no hints rendered
- `wizardPresentation` → `null` → wizard uses app default theme

---

## Roundtrip test requirement (CLAUDE.md rule 5)

`PresetSchemaVersionTest` MUST verify:
1. v2 JSON → deserialize → serialize → re-deserialize → fields equal
2. v1 JSON (no `hintFlow`, no `wizardPresentation`) → deserialize into v2 model → `hintFlow == null`, `wizardPresentation == null`

---

## Novice Summary (для владельца)

**Что такое этот файл?**

Это «договор» о том, как выглядит файл настроек пресета (JSON). Указывает какие поля есть, что они значат и как новый код читает старые файлы без ошибок.

**Главное**: добавляем два новых необязательных поля (`hintFlow` для подсказок и `wizardPresentation` для вида wizard'а). Старые файлы без этих полей работают как раньше — новый код просто использует значения по умолчанию.
