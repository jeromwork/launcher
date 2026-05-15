# Wire format: `/links/{linkId}/config/current` — additive extension (spec 009)

**Source of truth for baseline**: `specs/008-bidirectional-config-sync/contracts/config.md`.
**Source of truth for additions**: this document.
**Used by**: spec 009 §FR-013 (forward-compat reservation), §FR-019 (`DEFAULT_PHONE_HEALTH_PRESET`), `TODO-ARCH-010`, `TODO-FUTURE-SPEC-005`.
**Schema version**: `schemaVersion = 1` — **unchanged**. Spec 009 расширяет схему additive per spec 008 FR-006 («новые опциональные поля добавляются без bump'a»).

---

## Why this file exists

Spec 009 НЕ создаёт новый wire format и НЕ bump'ает `schemaVersion`. Однако оно резервирует структурное поле (`presetOverrides`), которое **всегда `null`** в эпохе спека 9, но имя/тип уже занято и его нельзя переиспользовать для другого назначения в будущем. Этот документ — **explicit forward-compat reservation**, чтобы:

- читатель спека 9 не думал, что `presetOverrides` свободен и его можно использовать под другое;
- читатель будущего спека-преемника (TODO-FUTURE-SPEC-005 preset-editor, TODO-ARCH-010 phone-health threshold editor) видел маршрут расширения;
- wire-format checklist (CHK013 «reservation documented») имел место для проверки.

Это **НЕ wire-format bump**. Это документация политики.

---

## Spec 008 baseline (unchanged)

```text
ConfigCurrent {
  schemaVersion: Int = 1,
  serverUpdatedAt: Timestamp (server-set),
  lastWriterDeviceId: String,
  presetId: String,
  flows: List<Flow>,
  contacts: List<Contact>
}
```

Полная спецификация: `specs/008-bidirectional-config-sync/contracts/config.md`.

---

## Spec 009 additive change

| Field | Type | Required | Server-set | Spec 009 epoch value | Notes |
|---|---|---|---|---|---|
| `presetOverrides` | `PresetSettings?` | ✗ | ✗ | **always `null`** | Reserved name; FR-013. Old v1 readers ignore (Kotlin Serialization `ignoreUnknownKeys = true`). |

### Nested: `PresetSettings`

Структура (in code), но в эпохе спека 9 никогда не сериализуется (всегда `null` на wire):

| Field | Type | Required | Notes |
|---|---|---|---|
| `phoneHealthSettings` | `PhoneHealthSettings?` | ✗ | Reserved для TODO-ARCH-010 (configurable health thresholds). Spec 009 epoch: всегда `null`. |

Будущие поля (`fontScale`, `dockPosition`, цветовая схема и т.д.) — добавляются additive когда понадобятся (TODO-FUTURE-SPEC-005 preset editor).

### Nested: `PhoneHealthSettings`

Full struct **defined in code** (data-model.md спека 9), но НЕ записывается на wire в спеке 9:

| Field | Type | Required | Spec 009 epoch | Notes |
|---|---|---|---|---|
| `battery` | `BatteryThresholds?` | ✗ | not written | пороги «низкая батарея» / «критическая»; reserved |
| `lastSeen` | `LastSeenThresholds?` | ✗ | not written | пороги «давно не было связи»; reserved |
| `audioMutedSeverity` | `String?` | ✗ | not written | severity-level enum; reserved |
| `connectivityNoneSeverity` | `String?` | ✗ | not written | severity-level enum; reserved |
| `updateCadenceInfoSec` | `Int?` | ✗ | not written | как часто Managed обновляет /state; reserved |
| `pushAdminOnCritical` | `Boolean?` | ✗ | not written | пушить admin'у уведомление при critical; reserved |

В спеке 9 все эти значения берутся из захардкоженного `DEFAULT_PHONE_HEALTH_PRESET` data class в коде (FR-019). На wire — ничего, в коде — single source.

---

## Example: spec 009 epoch document (with reservation present, value null)

```json
{
  "schemaVersion": 1,
  "serverUpdatedAt": {"_seconds": 1747166400, "_nanoseconds": 0},
  "lastWriterDeviceId": "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
  "presetId": "simple-launcher",
  "flows": [...],
  "contacts": [...],
  "presetOverrides": null
}
```

**Equivalent** (с полностью отсутствующим полем — для writer'ов которые не знают про FR-013):

```json
{
  "schemaVersion": 1,
  "serverUpdatedAt": {...},
  "lastWriterDeviceId": "...",
  "presetId": "simple-launcher",
  "flows": [...],
  "contacts": [...]
}
```

Reader логика: `presetOverrides ?: null` — обе формы эквивалентны.

---

## Forward-compat semantics

- **Old reader (spec 008 reader без знания `presetOverrides`)**: читает unknown field — Kotlin Serialization default `ignoreUnknownKeys = true` (spec 008 invariant) — silently игнорирует. Документ читается как valid v1.
- **New reader (spec 009 reader, знает `presetOverrides`)**: видит `null` → применяет code defaults (`DEFAULT_PHONE_HEALTH_PRESET`). Видит non-null (future, после TODO-ARCH-010) → merges с defaults (per-field fallback на default если в overrides поле `null`).
- **Writer (spec 009)**: всегда пишет `presetOverrides = null` ИЛИ не пишет вовсе (обе формы equivalent). Никогда не пишет non-null.

---

## Future evolution (out of scope spec 009)

| Spec | Triggers `presetOverrides` non-null |
|---|---|
| TODO-ARCH-010 (phone health threshold editor) | Populates `presetOverrides.phoneHealthSettings.battery.lowPct` etc. |
| TODO-FUTURE-SPEC-005 (preset editor) | Populates extra `PresetSettings` fields (dock position, fontScale, colors). |

Оба — additive, **schemaVersion остаётся 1**.

---

## Tests (commonTest)

| Test | What it verifies | Phase |
|---|---|---|
| `ConfigCurrentWireFormat.roundtrip_v1_with_presetOverrides_null` | Write `{...flows, contacts, presetOverrides: null}` → read → equal | 2 |
| `ConfigCurrentWireFormat.roundtrip_v1_with_presetOverrides_omitted` | Write без поля `presetOverrides` → reader returns `null` | 2 |
| `ConfigCurrentWireFormat.roundtrip_v1_with_phoneHealthSettings_smoke` | Synthetic future write `{presetOverrides: {phoneHealthSettings: null}}` → read → equal (forward-compat smoke; epoch-9 writer этого НЕ делает, но reader должен уметь) | 2 |
| `ConfigCurrentWireFormat.spec008_reader_ignores_presetOverrides` | Synthetic spec-008-shaped reader reads spec-009 document с `presetOverrides: null` → unknown field ignored, остальные fields preserved (FR-013 forward-compat invariant) | 2 |

**Fixtures** (`commonTest/resources/wire-format/`):
- `config-current-v1-spec9-null-overrides.json`
- `config-current-v1-spec9-omitted-overrides.json`
- `config-current-v1-future-phoneHealth-synthetic.json` (для forward-compat smoke teста)

---

## Backward compatibility policy

- **Schema stays at 1**. Дополнительные поля типа `presetOverrides` — additive per spec 008 FR-006.
- Когда `presetOverrides` начнёт реально записываться (TODO-ARCH-010 / TODO-FUTURE-SPEC-005) — ещё одна additive evolution, опять без bump.
- Rename/remove `presetOverrides` или его вложенных полей → bump 1 → 2 + reader-migration. Не планируется.
- Wire-format invariant: новые поля внутри `PresetSettings` / `PhoneHealthSettings` — всегда опциональные с разумным default (см. `DEFAULT_PHONE_HEALTH_PRESET` pattern).

**TODO comment in code** (`ConfigCurrent.kt`, на поле `presetOverrides`):
> Зарезервированное место (FR-013). В спеке 9 всегда `null`. Будущая запись — TODO-ARCH-010 (phone health) и TODO-FUTURE-SPEC-005 (preset editor). Additive, без bump schemaVersion.

---

<!-- novice summary -->

## TL;DR

«Тихое расширение существующего договора о раскладке». Спек 9 ничего не ломает в формате `/config/current` и не меняет номер версии — но **резервирует** имя нового поля `presetOverrides`. В этом спеке оно всегда пустое (`null`), но имя занято — чтобы в будущих спеках туда положили настройки порогов «здоровья телефона» (TODO-ARCH-010) и кастомизацию preset'ов (TODO-FUTURE-SPEC-005). Старые читалки спека 8 это поле игнорируют. Новые читалки видят `null` и берут значения по умолчанию из захардкоженного `DEFAULT_PHONE_HEALTH_PRESET` в коде. Этот документ нужен, чтобы никто случайно не использовал имя `presetOverrides` для чего-то другого.
