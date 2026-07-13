# Contract: Hint Pool JSON Schema v1

**Spec**: FR-007 (CL-7) | **Plan**: [plan.md](../plan.md) | **Data model**: [data-model.md](../data-model.md)

---

## Purpose

`hint-pool.json` describes optional UI overlays (tooltips, hint bubbles, guided-tour cards) that the wizard MAY show alongside `wizardFlow` steps. Hints are UI-layer metadata — `ReconcileEngine` never processes them (D5). They are consumed by the `WizardScreen` Composable via the `HintPoolSource` port (CL-7).

Introduced in TASK-126 as a **new file** (no v0 predecessor). All future changes bump `schemaVersion`.

---

## Schema version history

| Version | Change | Backward-compat |
|---------|--------|----------------|
| 1 | Initial (TASK-126, CL-7) | — |

---

## Wire format (JSON)

```json
{
  "schemaVersion": 1,
  "hints": [
    {
      "hintId": "hint-launcher-role",
      "titleKey": "hint_launcher_role_title",
      "bodyKey": "hint_launcher_role_body"
    },
    {
      "hintId": "hint-status-bar",
      "titleKey": "hint_status_bar_title",
      "bodyKey": "hint_status_bar_body"
    }
  ]
}
```

---

## Field specification

### Top-level object

| Field | Type | Required | Default | Notes |
|-------|------|---------|---------|-------|
| `schemaVersion` | Int | Yes | — | MUST be 1 for v1 writers; readers reject unknown versions per FR-014 pattern |
| `hints` | Array | No | `[]` | Empty list if no hints defined; missing file → empty list (adapter behavior) |

### HintDescriptor object

| Field | Type | Required | Default | Notes |
|-------|------|---------|---------|-------|
| `hintId` | String | Yes | — | Stable identifier; referenced by `Preset.hintFlow[].hintId` |
| `titleKey` | String | Yes | — | Localization key resolved at UI render time |
| `bodyKey` | String | Yes | — | Localization key resolved at UI render time |

---

## Port + adapter shape (CL-7, CLAUDE.md rule 9)

**Domain port** (`core/commonMain/kotlin/com/launcher/preset/port/HintPoolSource.kt`):

```kotlin
interface HintPoolSource {
    suspend fun load(): HintPool
}
```

**Bundled adapter** (`app/src/main/java/com/launcher/app/preset/task126/BundledHintPoolSource.kt`):

- Reads from `assets/hint-pool.json`.
- Missing / malformed file → returns `HintPool(schemaVersion = 1, hints = emptyList())`. Never throws across domain boundary.

**Additive adapters (future, per rule 9 shareability-readiness)** — implemented when needed, NOT in TASK-126:

- `FileImportHintPoolSource` — user selects `.json` from device storage.
- `ShareIntentHintPoolSource` — Android share-intent payload.
- `MarketplaceHintPoolSource` — curated community source (Phase 3+).

Adding a new source is a **new adapter**, not a wire-format change. `HintPool` schema itself remains v1 unless the fields change.

---

## Backward compatibility

- **v0 (missing file)**: adapter returns empty pool; `hintFlow` overlays simply do not render.
- **v1 → future v2**: fields will be added (never renamed/removed) per CLAUDE.md rule 5. v1 readers ignore unknown keys (`kotlinx.serialization` default).

---

## Roundtrip test requirement (CLAUDE.md rule 5)

`HintPoolRoundtripTest` MUST verify:

1. v1 JSON → deserialize → serialize → re-deserialize → fields equal.
2. Empty JSON (`{"schemaVersion": 1, "hints": []}`) → deserialize → `hints.isEmpty()`.
3. Missing file → `BundledHintPoolSource.load()` returns `HintPool(schemaVersion=1, hints=emptyList())` with no exception.
4. Malformed JSON → `BundledHintPoolSource.load()` returns empty pool (fail-safe adapter, not crash).

---

## Cross-references

- **`Preset.hintFlow`** (see [preset-schema-v2.md](preset-schema-v2.md)): each `HintFlowEntry.hintId` MUST match a `HintDescriptor.hintId` in this pool. Missing match → hint silently not rendered (UI-layer decision; no validation error — hints are optional UX, not a correctness gate).
- **UI layer** consumes both `Preset.hintFlow` (ordering + target Component) and `HintPool` (localized text) to render overlays; the two are joined at render time, not at load time.

---

## Novice Summary (для владельца)

**Что такое этот файл?**

`hint-pool.json` — «каталог подсказок» для wizard'а. В нём лежат тексты подсказок («сделай лаунчер по умолчанию — вот кнопка»), которые wizard может показать поверх обычного экрана шага.

**Зачем отдельный файл?** Чтобы preset автора описывал **порядок** подсказок (в `Preset.hintFlow`), а тексты жили отдельно и легко переводились на другие языки. Можно поменять preset (порядок) не трогая тексты и наоборот.

**Почему `schemaVersion: 1`?** Это правило проекта (CLAUDE.md rule 5): любой файл, который живёт дольше одного запуска app'а, должен иметь версию схемы — чтобы через 2 года мы могли безболезненно добавить поля.

**Почему port + adapter?** Сейчас подсказки берутся из bundled assets. Завтра — может быть импорт из файла или из marketplace. Port (`HintPoolSource`) описывает интерфейс, adapter (`BundledHintPoolSource`) — конкретную реализацию. Новые источники плагинятся дополнительно, старый код не переписывается.
