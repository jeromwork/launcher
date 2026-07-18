# Contract: Profile serialization (entity-grouped, TASK-136)

**Wire format**: `Profile` JSON on device (`ProfileStore`) + opaque blob for future zero-knowledge sync (rule 13).
**Schema version**: `schemaVersion` field **present, value = 2, NOT bumped** (Article XX pre-MVP; owner directive 2026-07-18).
**Serializer**: kotlinx.serialization polymorphic, `classDiscriminator = "type"`, `ignoreUnknownKeys = true`, `encodeDefaults = true` — **zero custom serializer**. Json config MUST mirror `DataStoreProfileStore` exactly.
**Migration**: **none** (no migrator, no backward-compat reader — Article XX). Deleted and reintroduced under the new shape in place.

---

## Shape — entity-grouped, mirror of Fleks `Snapshot`

Fleks `world.snapshot()` = `Map<Entity, Snapshot(components: List<Component>, tags: List<UniqueId>)>`. We mirror it: each entity groups its polymorphic component list + its tag list.

```json
{
  "schemaVersion": 2,
  "basedOnPreset": "simple-launcher",
  "presetVersion": 1,
  "layoutKey": "single",
  "entities": [
    {
      "id": "ws-main",
      "components": [ { "type": "Workspace", "layoutKey": "single" } ],
      "tags": ["Presentation", "Workspace"]
    },
    {
      "id": "flow-calls",
      "parentId": "ws-main",
      "components": [ { "type": "Flow", "titleKey": "calls", "layoutKey": "2x3", "order": 0 } ],
      "tags": ["Presentation", "Flow"]
    },
    {
      "id": "tile-wa",
      "parentId": "flow-calls",
      "components": [
        { "type": "AppTile", "packageName": "com.whatsapp", "labelKey": "wa" },
        { "type": "LifecycleState.Applied" }
      ],
      "tags": ["Presentation", "Tile", "Communication"]
    },
    {
      "id": "sos",
      "parentId": "flow-calls",
      "components": [
        { "type": "Sos", "shareLocation": true, "autoAnswer": true },
        { "type": "LifecycleState.Failed", "reason": { … } }
      ],
      "tags": ["Presentation", "Tile", "Safety", "Emergency"]
    }
  ]
}
```

- `parentId` omitted ⇒ root (`null`). `tags` omitted ⇒ `emptySet()`. `components` omitted ⇒ `emptyList()` (degenerate/empty entity — valid, selected by nothing, no crash).
- **`LifecycleState` variants** serialize by their own `@SerialName` (`LifecycleState.Applied`, `LifecycleState.Failed`, …). Data-carrying `Failed` includes `reason`. The exact discriminator string (nested `"LifecycleState.Applied"` vs flat) is fixed at implementation and pinned by the roundtrip fixture — the contract requirement is only that it is stable and distinct from the 11 data components.
- **Entity-grouped, not type-grouped** (OQ-2): components live *inside* their entity, not smeared across per-type tables. This is the canonical ECS wire form (Fleks itself serializes this way); type-grouped archetype/sparse-set is an in-memory perf layout, irrelevant at ~20–40 entities.

---

## Contract tests (required)

### 1. Roundtrip (`ProfileSerializationRoundtripTest`)
`Profile` → JSON → `Profile`, `assertEquals`. Fixture: mixed profile — workspace + 2 flows + tiles + toolbar + 2 buttons + one entity carrying a data-component **and** a `LifecycleState` **and** ≥3 tags. Fixtures rewritten in place under `core/src/commonTest/resources/fixtures/profile-wire-format/`.

### 2. Polymorphic list placement
An entity with several components serializes them as **one polymorphic array inside the entity** (`entities[i].components: [...]`), not as separate top-level tables. Assert JSON shape.

### 3. At-most-one-per-type (`AtMostOneComponentPerTypeFitnessTest`) — CL-3 / FR-015d
No entity holds two components of the same Kotlin type ⇒ `get<T>()` unambiguous. Fitness over every fixture + factory output.

### 4. Coverage (`ComponentCoverageFitnessTest`) — SC-006 / FR-015a
Every `Component` subtype (11 data + `LifecycleState`) has a working serializer; exhaustive `when` compiles; each data subtype that needs an effector has a `Provider` (structural + state components exempt).

### 5. Fail-loud pins (honest forward-compat, inherited from TASK-127)
Unknown component `type` / unknown `Tag` value ⇒ `SerializationException` (kotlinx enum collections have no per-element leniency). Documented, not a bug: a **lenient reader is a hard prerequisite** before cross-device artifact exchange (admin-push / preset-sharing) ships — recorded, not built here.

### Removed vs TASK-127
`ProfileMigrationV2toV3*` and any backward-compat test — **deleted**. Article XX: nothing to migrate pre-MVP; `schemaVersion` unchanged.

---

## Forward compatibility & one-way-door note (rule 3)

- `schemaVersion` retained = the free seam. On the day the first real user installs, the shape shipped becomes the v1 baseline; from then every change is a versioned migration written first (Article XX termination).
- **Exit ramp**: revert to discriminated union (re-collapse `components: List` → single `component`) is viable **only while pre-release**. After ship, the entity-grouped shape is frozen.
- Zero-knowledge (rule 13): whole `Profile` is one opaque blob; the server never parses `entities`. No server endpoint reads this format ⇒ no `server-log.md` entry owed.

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Кратко по-русски

Профиль сохраняется в JSON, сгруппированный **по сущностям**: каждая сущность = `{id, parentId, компоненты:[…], теги:[…]}` — ровно так, как это делает игровой движок Fleks в своём Snapshot. Компоненты внутри сущности пишутся одним полиморфным списком (различаются по полю `type`). Состояние (`Applied`/`Failed`/…) — это тоже компонент в списке, а не отдельное поле. Версия формата (`schemaVersion`) остаётся `2` и **не двигается** — пока приложение не выпущено, мигратор не пишем (правило Article XX): старый формат просто переписываем на месте. Тесты: round-trip (записал → прочитал → должно совпасть), «не больше одного компонента каждого типа», покрытие всех типов сериализатором, и честное падение на незнакомом типе/теге (снисходительный читатель — отдельный будущий шаг перед обменом между устройствами).
<!-- NOVICE-SUMMARY:END -->
