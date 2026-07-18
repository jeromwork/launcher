# Data Model: Settings as Profile View (TASK-69)

Canonical ECS types (`Entity`, `Component`, `LifecycleState`, `Profile`, `Preset`, `SettingsMapEntry`) are defined in [`ecs.md`](../../docs/architecture/ecs.md) — **not restated here**. This file covers only the **new types this task introduces**. All are **runtime, in-memory** (not persisted, not wire) unless stated. `SettingsView` is designed serializable for a future JSON-driven render (TASK-133) but carries **no `schemaVersion`** and is not persisted in this task.

## Port

```kotlin
// core/preset/port/SettingsGateway.kt  (domain, pure Kotlin)
interface SettingsGateway {
    fun observe(): Flow<SettingsView>                       // reactive projection of Profile (+ Preset.settingsMap)
    suspend fun apply(poolRef: String, params: JsonObject): ApplyResult
}

sealed interface ApplyResult {
    data object Applied : ApplyResult
    data class Failed(val reason: FailReason) : ApplyResult // reuse existing FailReason
    data object NeedsSystemDialog : ApplyResult             // caller launches the one-step flow (US3)
}
```

## Domain service (projection builder)

```kotlin
// core/preset/settings/SettingsPresentationBuilder.kt  (domain)
class SettingsPresentationBuilder(
    private val i18n: LocalizedResources,
    private val editability: (Entity) -> RowKind,          // derived: has in-app provider? system-dialog? read-only?
) {
    fun build(profile: Profile, settingsMap: List<SettingsMapEntry>): SettingsView
    // for each settingsMap entry: find entity by id==poolRef (I5); skip if absent;
    // value = entity.get<T>() formatted; state = entity.get<LifecycleState>(); group by categoryKey.
    // Shaped for a future shared Home+Settings builder; Home NOT wired here.
}
```

## View descriptor (the projection result)

```kotlin
// core/preset/settings/SettingsView.kt  (domain; @Serializable-shaped, not persisted)
data class SettingsView(
    val sections: List<SettingsSection>,   // profile-projection rows, grouped
    val actions:  List<AppOperation>,      // absorbed legacy app-operations (FR-020)
)

data class SettingsSection(val categoryKey: String, val rows: List<SettingRow>)

data class SettingRow(
    val poolRef: String,
    val titleKey: String,                  // i18n key
    val valueText: String,                 // pre-formatted current value (no when(component) in UI)
    val state: RowState,                   // projection of LifecycleState → UI-facing
    val kind: RowKind,                     // InApp (editable) | SystemDialog | ReadOnly
)

enum class RowState { Applied, Pending, Failed, Skipped, Unverifiable }  // 1:1 projection of LifecycleState
enum class RowKind  { InApp, SystemDialog, ReadOnly }                    // derived, NOT stored (FR-015)

sealed interface AppOperation {            // re-hosted legacy entries, NOT profile components
    data class PresetSwitch(val current: String) : AppOperation
    data object PairingQr    : AppOperation
    data object AdminDevices : AppOperation
    data object DataReset    : AppOperation
}
```

## What is NOT introduced

- **No new `Component` subtype, no new `Tag`** — Settings is a *reader/editor* of existing entities, not a new configurable.
- **No `settingsMap` field change** — `editableInSettings` was rejected; `RowKind` is derived (rule 4). `SettingsMapEntry` unchanged.
- **No persistence / wire format** — `SettingsView` lives for the duration of a screen; the Profile (persisted) and Preset (bundled) are unchanged.

## Invariants honoured

- **I2**: `settingsMap` (presentation) read from the **Preset** at build time; Profile alone is not presentation-self-contained — the builder takes both.
- **I5**: `entity.id == poolRef` lookup; unresolved refs skipped.
- **Rule 1**: `SettingRow.valueText` is pre-formatted in the domain builder so the Compose layer never does `when(component)`.

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Коротко для владельца

Здесь описаны **только новые типы этой задачи** (сама ECS-модель — в `ecs.md`, не дублируем):
- **`SettingsGateway`** — «пульт» настроек: показать список / применить одну настройку. За ним прячется движок.
- **`SettingsPresentationBuilder`** — сборщик: `профиль + settingsMap → готовый список` (`SettingsView`).
- **`SettingsView` / `SettingRow` / `AppOperation`** — готовые данные для экрана: строки (значение, статус, можно ли менять) + действия (сброс, QR, и т.д.). Живут только пока открыт экран, **никуда не сохраняются**.

Важное: **ничего в сохранённом формате не меняется** — новых полей нет, признак «можно менять» вычисляется на лету. Экран получает уже готовые строки, поэтому не содержит логики выбора по типу компонента.
<!-- NOVICE-SUMMARY:END -->
