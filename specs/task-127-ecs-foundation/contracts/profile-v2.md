# Wire Format Contract: Profile v2 (+ optional `tags` per Component)

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **Data model**: [../data-model.md](../data-model.md)

Persisted Profile JSON serialised to disk under `ProfileStore`. **`schemaVersion: 2`** — matches shipped code (`Profile.CURRENT_SCHEMA_VERSION = 2`, [Profile.kt:39](../../../core/src/commonMain/kotlin/com/launcher/preset/model/Profile.kt#L39)) and the immutable TASK-120 Decision (Profile v2).

TASK-127 adds four things, **all additive** per rule 5 → **no schemaVersion bump**, **no migration writer** (Clarification Q6 essence preserved):
1. optional `tags` array inside each Component object;
2. optional **`parentId`** on each entity (hierarchy by reference — FR-011);
3. three new Component `type` discriminators — **`Workspace`**, **`Flow`**, **`ToolbarButton`** (FR-013);
4. a fifth `status` value — **`Unverifiable`** (FR-014).

Kotlin-side renames (`ProfileComponent` → `Entity`, `ComponentDeclaration` → `Blueprint`, FR-015) **do not touch the wire format** — class names never appear in JSON; only `@SerialName` discriminators inside `Component` do, and those are unchanged.

---

## Location on disk

- Android: Preferences DataStore `task120_profile`, string key `profile_json_v2` — adapter [DataStoreProfileStore.kt](../../../app/src/main/java/com/launcher/app/preset/task120/adapter/DataStoreProfileStore.kt) (NOT a standalone file `files/profile/current.json` — earlier revision of this contract was wrong).
- Testing: [FakeProfileStore.kt](../../../core/src/commonTest/kotlin/com/launcher/preset/fakes/FakeProfileStore.kt) (in-memory).

## Serializer configuration (normative)

Producer and readers MUST use identical kotlinx.serialization settings (as in `DataStoreProfileStore`):

```kotlin
Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}
```

Contract tests MUST construct their `Json` with these exact settings — a drift between test config and adapter config voids the roundtrip guarantee.

---

## Schema v2

Top-level Profile document (real shape per `Profile.kt` / `ProfileComponent`):

```json
{
  "schemaVersion": 2,
  "basedOnPreset": "<preset id>",
  "presetVersion": 2,
  "layoutKey": "<layout key>",
  "components": [ <ProfileComponent…> ],
  "preWizardSnapshot": null,
  "snapshotTimestamp": null,
  "unknownRefs": [],
  "state": { "opaque": {} }
}
```

Each entry in `components` is an **Entity** wrapper — `id`, `parentId`, `wizardBehavior`, `critical`, `status` live here, NOT on the Component itself:

```json
{
  "id": "tile-settings",
  "component": { "type": "AppTile", … },
  "wizardBehavior": "AutoApply",
  "critical": false,
  "status": "Applied",
  "parentId": "flow-calls"
}
```

The inner `component` object carries a `"type"` discriminator and the subtype's own fields. **New in TASK-127**: optional `"tags"` array of Tag names. If absent — kotlinx.serialization substitutes the constructor-default of the Component subtype (single source of truth).

**`parentId`** (new, optional, default `null` = root): expresses hierarchy **by reference**. Storage stays flat — the tree (`Workspace → Flow → Tile`, `Toolbar → ToolbarButton`) is computed by queries, never nested in JSON (research.md R-7; same pattern as Bevy/Unity DOTS `Parent` and Android Launcher3's `favorites.container`).

### Example — v2 Profile with hierarchy (workspace → 2 flows → tiles; toolbar → 2 buttons)

Owner's target screen shape (US-4). Note: **flat array, tree by `parentId`**.

```json
{
  "schemaVersion": 2,
  "basedOnPreset": "simple-launcher",
  "presetVersion": 2,
  "layoutKey": "grid",
  "components": [
    {
      "id": "ws-main",
      "component": { "type": "Workspace", "layoutKey": "single", "tags": ["Presentation", "Workspace"] },
      "wizardBehavior": "InitialDefault",
      "critical": false,
      "status": "Applied",
      "parentId": null
    },
    {
      "id": "flow-calls",
      "component": { "type": "Flow", "titleKey": "flow_calls", "layoutKey": "2x3", "order": 0,
                     "tags": ["Presentation", "Flow"] },
      "wizardBehavior": "InitialDefault",
      "critical": false,
      "status": "Applied",
      "parentId": "ws-main"
    },
    {
      "id": "flow-apps",
      "component": { "type": "Flow", "titleKey": "flow_apps", "layoutKey": "2x3", "order": 1,
                     "tags": ["Presentation", "Flow"] },
      "wizardBehavior": "InitialDefault",
      "critical": false,
      "status": "Applied",
      "parentId": "ws-main"
    },
    {
      "id": "tile-whatsapp",
      "component": {
        "type": "AppTile",
        "packageName": "com.whatsapp",
        "labelKey": "tile_whatsapp",
        "iconKey": null,
        "pinProtected": false,
        "tags": ["Presentation", "Tile", "Communication"]
      },
      "wizardBehavior": "AutoApply",
      "critical": false,
      "status": "Applied",
      "parentId": "flow-calls"
    },
    {
      "id": "sos-primary",
      "component": {
        "type": "Sos",
        "shareLocation": true,
        "autoAnswer": true,
        "tags": ["Presentation", "Tile", "Safety", "Emergency"]
      },
      "wizardBehavior": "Interactive",
      "critical": true,
      "status": "Applied",
      "parentId": "flow-calls"
    },
    {
      "id": "tile-settings",
      "component": {
        "type": "AppTile",
        "packageName": "com.android.settings",
        "labelKey": "tile_settings",
        "tags": ["Presentation", "Tile"]
      },
      "wizardBehavior": "AutoApply",
      "critical": false,
      "status": "Applied",
      "parentId": "flow-apps"
    },
    {
      "id": "toolbar-main",
      "component": { "type": "Toolbar", "items": [], "layoutKey": "bottom-bar",
                     "tags": ["Presentation", "Toolbar"] },
      "wizardBehavior": "AutoApply",
      "critical": false,
      "status": "Applied",
      "parentId": "ws-main"
    },
    {
      "id": "btn-calls",
      "component": { "type": "ToolbarButton", "targetFlowId": "flow-calls", "labelKey": "btn_calls",
                     "iconKey": null, "order": 0, "tags": ["Presentation", "ToolbarButton"] },
      "wizardBehavior": "AutoApply",
      "critical": false,
      "status": "Applied",
      "parentId": "toolbar-main"
    },
    {
      "id": "btn-apps",
      "component": { "type": "ToolbarButton", "targetFlowId": "flow-apps", "labelKey": "btn_apps",
                     "order": 1, "tags": ["Presentation", "ToolbarButton"] },
      "wizardBehavior": "AutoApply",
      "critical": false,
      "status": "Applied",
      "parentId": "toolbar-main"
    },
    {
      "id": "statusbar-policy",
      "component": { "type": "StatusBarPolicy", "tags": ["System"] },
      "wizardBehavior": "Interactive",
      "critical": false,
      "status": "Unverifiable",
      "parentId": null
    }
  ],
  "preWizardSnapshot": null,
  "snapshotTimestamp": null,
  "unknownRefs": [],
  "state": { "opaque": {} }
}
```

Reading the tree from this flat array:
- `workspace()` → `ws-main` (tag `Workspace`).
- `flows()` → `flow-calls`, `flow-apps` (tag `Flow`, sorted by `order`).
- `tilesOf("flow-calls")` → `tile-whatsapp`, `sos-primary` (`parentId == "flow-calls"`, tags ⊇ {Presentation, Tile}, minus Failed/Skipped).
- `toolbarButtons()` → `btn-calls`, `btn-apps` (tag `ToolbarButton`, sorted by `order`); each `targetFlowId` must resolve to a Flow entity (FR-016 `DanglingTargetRef`).
- `statusbar-policy` carries `status: "Unverifiable"` — applied per the user's word, no OS read-back (FR-014).

### Example — degenerate one-level profile (simple launcher, US-1)

A profile with **no** `Workspace`/`Flow`/`Toolbar` entities is valid: `homeScreenTiles()` then returns all tiles regardless of `parentId`, and the UI renders one flow with no toolbar. Same code path, no special-casing.

Notes vs the earlier (wrong) revision of this contract:
- No `presetId` top-level field — real fields are `basedOnPreset` + `presetVersion` + `layoutKey`.
- `AppTile` has `labelKey` (localization resource key), NOT a raw `label` string.
- `Sos` has `shareLocation` / `autoAnswer`. **No `targetPhone`** — contact targets are device-local state and MUST NOT enter Component params (rule 9: Components flow into shareable Presets; phone numbers are PII).
- `Toolbar` has `items: List<String>` + `layoutKey`, not `buttons`.

### Example — Component without explicit `tags` (constructor-defaults kick in)

```json
{
  "id": "tile-settings",
  "component": {
    "type": "AppTile",
    "packageName": "com.android.settings",
    "labelKey": "tile_settings"
  },
  "wizardBehavior": "AutoApply",
  "critical": false
}
```

Deserialized `AppTile` gets `tags = setOf(Tag.Presentation, Tag.Tile)` from the constructor default. (With `encodeDefaults = true`, re-serialization writes `tags` explicitly — the missing-tags form is read-compatible input, not the canonical output.)

### Tag values

Closed set of **13** string names, must match `com.launcher.preset.model.Tag` enum:

- Semantic: `"Presentation"`, `"Appearance"`, `"System"`, `"Safety"`, `"Capabilities"`, `"Communication"`, `"Accessibility"`, `"Emergency"`.
- Structural: `"Tile"`, `"Toolbar"`, `"Workspace"`, `"Flow"`, `"ToolbarButton"`.

Serialisation: kotlinx.serialization enum default (name string).

### Component `type` discriminators

Closed set of **11**: `"AppTile"`, `"FontSize"`, `"Sos"`, `"Toolbar"`, `"LauncherRole"`, `"Theme"`, `"Language"`, `"StatusBarPolicy"`, `"Workspace"`, `"Flow"`, `"ToolbarButton"`.

`"LauncherRole"` and `"StatusBarPolicy"` changed from Kotlin `object` to `data class` (to carry an overridable `tags` default) — **wire-compatible**: `{"type":"LauncherRole"}` with no fields still deserializes (every param defaulted).

### Status values

Closed set of **5**: `"Pending"`, `"Applied"`, `"Failed"`, `"Skipped"`, `"Unverifiable"`.

`"Unverifiable"` (new, FR-014) = applied on the user's word; the OS exposes no read-back (e.g. system status-bar hiding). `BootCheck` skips these; re-verification happens only via an explicit Settings action.

---

## Forward compat — honest statement (corrected 2026-07-16)

- **Unknown JSON keys** → ignored (`ignoreUnknownKeys = true`). Adding a new *optional field* to an existing subtype is safe for old readers.
- **Unknown Tag value inside `tags` array** → **`SerializationException` (read failure, fail-loud)**. `ignoreUnknownKeys` covers keys only; kotlinx.serialization has no per-element leniency for enum collections ([kotlinx.serialization #1113](https://github.com/Kotlin/kotlinx.serialization/issues/1113)). The earlier claim "v1 readers deserialise future Tag values" was **false**.
- **Unknown Component `"type"` discriminator** → **`SerializationException` (read failure, fail-loud)** — same mechanism.

Consequence: **adding a Tag value or Component subtype is additive for writers but breaking for older readers.** Pre-release, same-device, same-version — acceptable (writer and reader are always the same binary). **Trigger for the lenient path**: before ANY cross-device / cross-version artifact ships (admin push per spec-009, preset import/share per rule 9), implement a lenient `Set<Tag>` serializer (skip unknown names) + unknown-`type` skip policy, with contract tests. Tracked in plan.md Risks (R-8).

Contract tests MUST pin the *current* fail-loud behavior explicitly (see test surface below) so the deferral stays a documented decision, not an accident.

---

## Migration policy (pre-release)

**While MVP is unreleased**:

- No migration writer (Clarification Q6, rule 4 MVA — no consumer exists).
- `tags` addition is additive → schemaVersion **stays 2**.
- A future *breaking* change (rename/remove field) pre-release = reset dev `ProfileStore` (`adb uninstall` / clear data), still no writer.
- Single source of truth for tags-defaults: `Component` subtype constructors.

**First post-release breaking change**:

- Bump `schemaVersion: 2 → 3`, write `ProfileMigrationV2toV3`, roundtrip + backward-compat tests mandatory (rule 5).

---

## Roundtrip guarantee

- `serialize(profile) → JSON → deserialize → profile'` MUST produce `profile == profile'` (data class equality).
- Verified by `ProfileSchemaV2RoundtripTest` (naming follows existing `core/src/commonTest/kotlin/com/launcher/preset/wire/` pattern: `PoolSchemaV2RoundtripTest`, `PresetSchemaV2RoundtripTest`) on `profile-v2-sample.json` fixture.

## Contract test surface

Location: `core/src/commonTest/kotlin/com/launcher/preset/wire/`.

- `ProfileSchemaV2RoundtripTest` — roundtrip of the **hierarchical** fixture (workspace + 2 flows + tiles + toolbar + 2 buttons + Unverifiable entity), explicit `tags` everywhere.
- Missing-`tags` case — fixture omits `tags` → deserialized Component equals constructor-default tags.
- Missing-`parentId` case — fixture omits `parentId` → deserializes as `null` (root); degenerate one-level profile still reads.
- Object-subtype compat case — `{"type":"LauncherRole"}` / `{"type":"StatusBarPolicy"}` with no fields → deserializes with default tags (pins the object → data class conversion).
- Unknown-Tag case — fixture with `"tags": ["FutureTag"]` → assert `SerializationException` (pins fail-loud until lenient serializer ships — TASK-131).
- Unknown-`type` case — fixture with `"type": "FutureComponent"` → assert `SerializationException` (same).
- Unknown-`status` case — fixture with `"status": "FutureStatus"` → assert `SerializationException` (same class of limitation).

Fixtures in `core/src/commonTest/resources/fixtures/profile-wire-format/`:
- `profile-v2-hierarchy.json` — baseline, the full tree above.
- `profile-v2-no-tags.json` — `tags` + `parentId` omitted (constructor-defaults / degenerate case).

---

## TL;DR для владельца

- **Формат хранения** — JSON-строка в локальном хранилище устройства (DataStore), куда мастер настройки записывает собранный профиль.
- **`schemaVersion: 2`** — как в уже написанном коде TASK-120. Раннее решение «сбросить на 1» отменено: код и зафиксированное решение TASK-120 уже говорят «2», а всё, что добавляет TASK-127 (`tags`, `parentId`, три новых типа, статус `Unverifiable`) — **аддитивно**, номер поднимать не нужно.
- **Дерево хранится плоско.** В JSON — просто список объектов; у каждого есть поле `parentId` («кто мой родитель»). Экран собирается запросами: «дай вкладки workspace'а», «дай плитки этой вкладки», «дай кнопки тулбара». Никакой вложенности в файле — так делают и Android-лаунчер, и базы данных.
- **Никакой миграции сейчас** (суть решения Q6 сохранена): релизнутых профилей нет — писать мигратор не для кого.
- **Если в JSON нет полей `tags` / `parentId`** — подставляются значения по умолчанию из кода (`AppTile → {Presentation, Tile}`, `parentId = null` = корень). Старый одноуровневый профиль читается без изменений.
- **Честное ограничение**: старая версия приложения, встретив НЕЗНАКОМЫЙ тег или незнакомый тип компонента, упадёт при чтении с понятной ошибкой. Пока профиль живёт только на одном устройстве и читается тем же приложением, которое его записало — это безопасно. Перед тем как профили начнут ходить между устройствами (админ-пуш, обмен пресетами) — обязателен «снисходительный» читатель, который пропускает незнакомое. Это записано как явный триггер, тесты фиксируют текущее поведение.
- **Гарантия**: roundtrip-тест (профиль → JSON → профиль без потерь) + три теста на краевые случаи.
