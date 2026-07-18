# Architecture: Pool naming convention

> **Scope:** this doc covers **only pool-entry ID naming stability**. For the configuration **model** (Entity · Component · Blueprint · Provider · reconcile) the single source of truth is **[`ecs.md`](ecs.md)** — read it for the model. Any `Config` / `PoolEntry` / `check` / `apply` vocabulary in older examples below is superseded TASK-65 wording; the **naming rules still apply**, the model terms are `ecs.md`'s (`Blueprint` / `Component` / `Provider`).

**Origin**: TASK-65 (FR-028). Moved from `specs/task-65-.../contracts/` to `docs/architecture/` 2026-07-09 (архитектурный инвариант, применяется ко всем preset'ам, не только TASK-65).
**Purpose**: Stable identifiers для pool entries (настроек-кубиков), которые preset'ы пикают и компонуют. Identifiers видны в bundled presets, в server-distributed presets (future), в community/marketplace presets (future TASK-35). Стабильность критична.

---

## MVP override

**Правила иммутабельности ниже — жёсткие ПОСЛЕ первого public release.** До этого (текущий pre-MVP phase per constitution Article XX «Pre-MVP transitional overrides») можно свободно переименовывать / удалять / менять semantics — installed base = 0, backward-compat не нужна.

**Дата активации жёстких правил**: TBD (первое Play Store submission / первый non-developer install). После этой даты — правила иммутабельности вступают в силу, любое изменение существующего identifier'а становится breaking change с обязательным migration writer.

Пока активация не наступила — правила ниже воспринимай как **best-practice defaults**, не как hard constraints. Используй consistent naming чтобы не пришлось потом массово переписывать.

---

## Format

```
<pool>.<domain>.<subject>
```

**Examples**:
- `system-settings.android.role.home` — Android HOME role.
- `system-settings.android.permission.post-notifications` — POST_NOTIFICATIONS runtime permission.
- `system-settings.android.permission.read-contacts` — READ_CONTACTS runtime permission.
- `ui-customization.font.large` — preferred large font.
- `ui-customization.theme.dark` — dark theme preference.
- `ui-customization.contrast.high` — high contrast mode.
- `tile.pairing.list` — pairing-list tile (TASK-67 territory).

**Parts**:
- `<pool>` — pool id, kebab-case. Examples: `system-settings`, `ui-customization`, `tile`.
- `<domain>` — area within pool, kebab-case. Examples: `android`, `font`, `theme`, `pairing`.
- `<subject>` — specific entry, kebab-case. Examples: `role.home`, `permission.post-notifications`, `large`, `dark`.

Multi-level subject allowed (`role.home`, `permission.post-notifications`) — uses additional `.` separators. Total entry id: `<pool>.<domain>.<rest...>`.

---

## Immutability rules

**These rules are STRICT** — break them and existing presets stop working.

1. **An id, once shipped, NEVER changes its meaning.**
   - Renaming = breaking change for existing presets that reference the id.
   - Re-purposing (same id, new semantics) = breaking change.

2. **An id is NEVER deleted.**
   - To remove an entry: mark `deprecated: true` on the PoolEntry. Keep id in pool forever.
   - Reasoning: existing presets in DataStore (user devices) reference the id. Removing it = breaking their settings on next read.

3. **An id MAY be deprecated.**
   ```json
   { "id": "ui-customization.font.large", "deprecated": true, ... }
   ```
   - Existing presets that pick this entry continue to work.
   - New presets MUST NOT pick this entry (validation в `BundledPresetsParseTest`).
   - PoolEntry record remains in pool for backward compat read.

4. **New entries MAY be added freely.**
   - Adding entries = no breaking change. Old presets ignore new entries.
   - Bump `Pool.schemaVersion` if structure of PoolEntry itself changes (rare).

---

## Versioning (per-pool `schemaVersion`)

Each pool has its own `schemaVersion: Int`:
```kotlin
data class Pool(
    val id: String,
    val schemaVersion: Int,    // bumped on breaking structural change to PoolEntry shape
    val entries: List<PoolEntry>
)
```

**Per-pool, not per-entry**. Bumping pool schema = all entries re-parsed under new schema.

**When to bump**:
- New required field on `PoolEntry`. (E.g., added `documentationUrl: String` without default.)
- Field semantic change. (E.g., `criticality: String` becomes `criticality: Criticality enum`.)

**When NOT to bump**:
- New optional field with default. (Old code reads, ignores. New code reads, uses.)
- Adding new entries.
- Deprecating entry (`deprecated: true` flag was already optional).

---

## Preset references to pool entries

Each `Config` in `preset.configs[]` references a specific pool entry **with pool version at pick-time**:

```jsonc
{
  "id": "android-role-home",         // unique within preset.configs
  "poolId": "system-settings",
  "poolVersion": 3,                  // pool version when this Config was picked
  "entryId": "system-settings.android.role.home",
  "title": "...", "description": "...",
  "check": {...}, "apply": {...},
  "criticality": "Required"
}
```

**Why poolVersion is captured**:
- Preset becomes self-contained (embedded snapshot per R1).
- If pool evolves (entry deprecated, added, schema bumped) — preset works with its captured snapshot.
- If user upgrades pool (new APK version with newer pool) — preset still works because Config carries full data.

**Refresh policy**: When admin (TASK-72 Pool Browser UI) edits preset, may opt to refresh Config snapshots from current pool. Outside TASK-65 scope.

---

## Detekt validation

`PresetIdBranchingDetector` (FR-020) treats pool entry ids as **opaque strings**:
- Whitelisted: code in `core/presets/`, `core/pools/`, `core/wizard/` may have `if (entry.id == "system-settings.android.role.home")` branches when needed.
- Elsewhere: `if (entryId == "...")` triggers ISSUE — use composition (preset.configs lookup) instead.

---

## Examples by pool

### `system-settings` pool

| Entry id | Description |
|---|---|
| `system-settings.android.role.home` | HOME role |
| `system-settings.android.role.assistant` | ASSISTANT role |
| `system-settings.android.permission.post-notifications` | POST_NOTIFICATIONS |
| `system-settings.android.permission.read-contacts` | READ_CONTACTS |
| `system-settings.android.permission.call-phone` | CALL_PHONE |
| `system-settings.android.special-permission.usage-stats` | PACKAGE_USAGE_STATS special access |

### `ui-customization` pool

| Entry id | Description |
|---|---|
| `ui-customization.font.large` | font scale ≥ 1.3 (NEW в TASK-65, CheckSpec.UIFont) |
| `ui-customization.font.xlarge` | font scale ≥ 1.5 |
| `ui-customization.theme.light` | light theme |
| `ui-customization.theme.dark` | dark theme |
| `ui-customization.contrast.high` | high contrast |

### `tile` pool (future, TASK-67 / TASK-68)

| Entry id | Description |
|---|---|
| `tile.contacts.single` | single-contact tile |
| `tile.pairing.list` | pairing list tile |
| `tile.web.url` | web URL tile |

---

## Adding new pool entries

When you want to add a new entry:

1. **Pick an id** following format `<pool>.<domain>.<subject>`. Check it doesn't collide with existing ids in the same pool.
2. **Decide which pool** it belongs to (`system-settings` for Android system, `ui-customization` for app UI, `tile` for tile types).
3. **Define `CheckSpec`** if new check kind needed (e.g., new Android API check) — extend sealed hierarchy. **Or** reuse existing kind (e.g., another `AndroidPermission` variant).
4. **Define `ApplySpec`** similarly.
5. **Add entry to pool** (HardcodedPoolSource constants OR JSON file in assets).
6. **Bump pool `schemaVersion` IF** entry uses new structural feature.
7. **Tests**: `BundledPresetsParseTest` auto-validates id format. Roundtrip test covers new entry.
8. **Documentation**: add to this file's "Examples by pool" section.

---

## Owner-readable summary

**Pool naming** — это правила именования настроек-кубиков, из которых преsetы собирают свои конфиги.

**Главные правила** (на простом):

1. **Имя состоит из трёх частей через точку**: `<какой пул>.<какая область>.<что именно>`. Пример: `system-settings.android.role.home` = «из пула системных настроек, в области Android, конкретно HOME role».

2. **Раз дали имя — оно навсегда такое**. Нельзя переименовать (старые preset'ы сломаются). Нельзя удалить (старые preset'ы ссылаются). Можно пометить `deprecated: true` — старые preset'ы продолжают работать, новые не используют.

3. **Каждый пул имеет свою версию**. Bump если внутренняя структура pool entry меняется (редко). Добавление новых entries — без bump.

4. **Preset captures version** при пике entry. Preset = самосодержащий, работает даже если pool обновился.

**Зачем эти правила**: чтобы preset, который собрал админ для бабушки сегодня, **работал и через 5 лет**, даже если pool сильно эволюционировал. Стабильность identifiers = долговечность шаренных preset'ов.

**Аналогия**: pool entries — это товары в каталоге IKEA с артикулами (sku). Артикул раз дан — никогда не меняется. Товар может быть снят с производства (deprecated), но в каталоге остаётся (старые мебельные инструкции ссылаются по артикулу). Новые товары добавляются с новыми артикулами.
