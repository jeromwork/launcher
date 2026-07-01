# Data Model: TASK-65 — Preset Composition Foundation v2

All types live в `core/commonMain/kotlin/com/launcher/api/`. Implementations в `core/androidMain/kotlin/com/launcher/adapters/`.

---

## 1. Identity types

### `PresetRef` (composite identity)

```kotlin
package com.launcher.api.preset

@Serializable
data class PresetRef(
    val uid: String,    // globally unique reverse-DNS or UUID — e.g. "com.launcher.preset.simple-launcher"
    val version: Int    // bump on every breaking content change
) {
    init {
        require(!uid.contains("::")) { "uid MUST NOT contain '::' (Map key separator)" }
        require(uid.isNotBlank()) { "uid MUST be non-blank" }
        require(version >= 1) { "version MUST be ≥ 1" }
    }

    /** Composite key for Map<PresetRef, ProfileData> serialization. See research R3. */
    fun toCompositeKey(): String = "$uid::$version"

    companion object {
        fun parseCompositeKey(key: String): PresetRef {
            val (uid, versionStr) = key.split("::", limit = 2)
                .also { require(it.size == 2) { "invalid composite key: $key" } }
            return PresetRef(uid = uid, version = versionStr.toInt())
        }
    }
}
```

---

## 2. Preset (wire format, shareable, no PII)

### `Preset`

```kotlin
package com.launcher.api.preset

@Serializable
data class Preset(
    val schemaVersion: Int = PRESET_SCHEMA_VERSION,   // 1
    val uid: String,
    val version: Int,
    val slug: String,                                  // human-readable, e.g. "simple-launcher" — NOT identity
    val label: String,                                 // i18n key, e.g. "preset_simple_launcher_label"
    val description: String,                           // i18n key
    val configs: List<Config>,
    val abstractProfile: AbstractProfile? = null,
    val requiredModules: List<String> = emptyList(),
    val optionalModules: List<String> = emptyList(),
    val pickEnabled: Boolean = true
) {
    val ref: PresetRef get() = PresetRef(uid, version)
}

const val PRESET_SCHEMA_VERSION = 1
```

### `Config` (pool entry pick — embedded snapshot)

```kotlin
package com.launcher.api.preset

import com.launcher.api.wizard.data.CheckSpec
import com.launcher.api.wizard.data.ApplySpec

@Serializable
data class Config(
    val id: String,                  // unique within preset.configs
    val poolId: String,              // traceability: source pool ("system-settings", "ui-customization", ...)
    val poolVersion: Int,            // pool version at pick-time (per R1)
    val entryId: String,             // pool entry id within the pool
    val title: String,               // i18n key
    val description: String,         // i18n key
    val check: CheckSpec,            // dispatch by kind: AndroidRole, AndroidPermission, UIFont, ...
    val apply: ApplySpec,            // SettingsDeepLink, etc
    val criticality: Criticality = Criticality.Optional,
    val defaultValue: String? = null,    // optional — used by TASK-71 hideInWizard
    val hideInWizard: Boolean = false,   // hook for TASK-71
    val showInSettings: Boolean = true   // hook for TASK-71
)

enum class Criticality { Required, Optional }
```

### `AbstractProfile` (optional initial layout/bindings inside preset)

```kotlin
package com.launcher.api.preset

import com.launcher.api.profile.Binding
import com.launcher.api.profile.Layout

@Serializable
data class AbstractProfile(
    val layout: Layout,
    val bindings: List<Binding> = emptyList()  // placeholder bindings, no PII
)
```

---

## 3. Profile (per-device, runtime, syncs to server encrypted)

### `ProfileStore` (top-level storage)

```kotlin
package com.launcher.api.profile

import com.launcher.api.preset.PresetRef

@Serializable
data class ProfileStoreState(
    val schemaVersion: Int = PROFILE_STORE_SCHEMA_VERSION,   // 1
    val activePresetRef: PresetRef? = null,
    /** Map serialized with composite string key "<uid>::<version>" per R3. */
    val profiles: Map<String, ProfileData> = emptyMap()      // key = PresetRef.toCompositeKey()
)

const val PROFILE_STORE_SCHEMA_VERSION = 1

/** Port — implementation: PreferencesProfileStore (androidMain). */
interface ProfileStore {
    suspend fun load(): ProfileStoreState
    suspend fun save(state: ProfileStoreState)

    suspend fun getActive(): ProfileData? {
        val s = load()
        val ref = s.activePresetRef ?: return null
        return s.profiles[ref.toCompositeKey()]
    }

    suspend fun putProfile(ref: PresetRef, data: ProfileData) {
        val s = load()
        save(s.copy(profiles = s.profiles + (ref.toCompositeKey() to data)))
    }

    suspend fun setActive(ref: PresetRef) {
        save(load().copy(activePresetRef = ref))
    }
}
```

### `ProfileData` (one preset's runtime state)

```kotlin
package com.launcher.api.profile

@Serializable
data class ProfileData(
    val layout: Layout,
    val bindings: List<Binding> = emptyList(),
    val settings: List<SettingEntry> = emptyList(),
    /** Hook from spec — see Clarification on Slot.kind / unassigned. */
    val unassigned: List<Binding> = emptyList()
)
```

### `Layout`, `Slot`, `Binding`

```kotlin
package com.launcher.api.profile

@Serializable
data class Layout(
    val screens: List<Screen> = emptyList(),
    val grid: Grid = Grid(rows = 2, columns = 3),
    val toolbarTop: List<Slot> = emptyList(),
    val toolbarBottom: List<Slot> = emptyList()
)

@Serializable
data class Screen(
    val id: String,
    val slots: List<Slot>
)

@Serializable
data class Grid(val rows: Int, val columns: Int)

@Serializable
data class Slot(
    val position: Int,
    val kind: String? = null   // hook для будущего kind-matching switch strategy
)

@Serializable
data class Binding(
    val slotPosition: Int,
    val targetPackage: String? = null,    // app package; null OK for URL/contact bindings
    val contactRef: String? = null,        // opaque local handle (NOT phone number)
    val url: String? = null,
    val intentExtras: Map<String, String> = emptyMap()
)
```

### `SettingEntry` (Config + current applied state)

```kotlin
package com.launcher.api.profile

import com.launcher.api.preset.Config

@Serializable
data class SettingEntry(
    val config: Config,
    val state: AppliedState = AppliedState.NotApplied
)

@Serializable
sealed class AppliedState {
    @Serializable object NotApplied : AppliedState()
    @Serializable object Applied : AppliedState()
    @Serializable data class WithValue(val value: String) : AppliedState()
    @Serializable object Indeterminate : AppliedState()    // callback failed gracefully — per Article VII §15
}
```

---

## 4. Pools

### `PoolSource` (port)

```kotlin
package com.launcher.api.pools

interface PoolSource {
    suspend fun load(poolId: String): Pool
    suspend fun version(poolId: String): Int
    /** Used by JsonAsset↔Hardcoded roundtrip test + future Pool Browser UI (TASK-72). */
    suspend fun listEntries(poolId: String): List<PoolEntry>
}

@Serializable
data class Pool(
    val id: String,
    val schemaVersion: Int,
    val entries: List<PoolEntry>
)

@Serializable
data class PoolEntry(
    val id: String,                  // namespaced — "<pool>.<domain>.<subject>", per pool-naming.md
    val title: String,               // i18n key
    val description: String,         // i18n key
    val check: CheckSpec,
    val apply: ApplySpec,
    val criticality: Criticality = Criticality.Optional,
    val defaultValue: String? = null,
    val deprecated: Boolean = false  // immutability per pool-naming.md (no delete/rename)
)
```

---

## 5. ProfileSwitchStrategy (port + default adapter)

```kotlin
package com.launcher.api.switchstrategy

import com.launcher.api.preset.Preset
import com.launcher.api.profile.ProfileData

interface ProfileSwitchStrategy {
    /** Called only when target preset has no existing ProfileData in ProfileStore. */
    suspend fun migrate(from: ProfileData?, toPreset: Preset): ProfileData
}

/** Default (and only) adapter in TASK-65. Copies preset.abstractProfile + preset.configs. */
class CopyOnActivateStrategy : ProfileSwitchStrategy {
    override suspend fun migrate(from: ProfileData?, toPreset: Preset): ProfileData {
        // 'from' ignored — Reset semantics.
        return ProfileData(
            layout = toPreset.abstractProfile?.layout ?: Layout.empty(),
            bindings = toPreset.abstractProfile?.bindings ?: emptyList(),
            settings = toPreset.configs.map { SettingEntry(config = it, state = AppliedState.NotApplied) }
        )
    }
}
```

---

## 6. CheckSpec extensions (additive to existing sealed hierarchy)

```kotlin
// existing core/commonMain/api/wizard/data/CheckSpec.kt extended:

@Serializable
@SerialName("ui-font")
data class UIFont(val minScale: Float) : CheckSpec()
```

Handler in `androidMain/adapters/wizard/UIFontChecker.kt`:

```kotlin
class UIFontChecker(private val context: Context) : CheckHandler<CheckSpec.UIFont> {
    override fun check(spec: CheckSpec.UIFont): AppliedStatusRecord {
        val current = context.resources.configuration.fontScale
        return if (current >= spec.minScale) AppliedStatusRecord.Applied
               else AppliedStatusRecord.NotApplied
    }
}
```

---

## 7. Relationships

```
Preset (wire format, shareable)
  ├── PresetRef ← composite identity (uid + version)
  ├── configs[] ── of Config
  │     └── check: CheckSpec ← Android-role / Android-permission / UIFont / ... (sealed)
  └── abstractProfile? ── AbstractProfile
        ├── layout
        └── bindings[]

ProfileStore (per-device, syncs to server encrypted)
  ├── activePresetRef ← PresetRef (current active)
  └── profiles: Map<String, ProfileData>      ← key = PresetRef.toCompositeKey()
        └── ProfileData (per preset)
              ├── layout
              ├── bindings[]
              ├── settings[] ── of SettingEntry
              │     ├── config ← copy of Config from preset at activation time
              │     └── state ← AppliedState (Applied / NotApplied / WithValue / Indeterminate)
              └── unassigned[]

Pool (caterpillar source, versioned)
  └── entries[] ── of PoolEntry
        └── (same shape as Config but без poolVersion captured at pick-time)

ProfileSwitchStrategy
  └── migrate(from, toPreset) → ProfileData
        └── CopyOnActivateStrategy ← default (and only) adapter
```

---

## 8. Schema versioning rules

| Wire format | Current version | Read first? | Migration writer |
|---|---|---|---|
| `preset.json` | 1 | yes | n/a (version 1 launch) |
| `wizard.manifest` | 2 (bump from 1) | yes | `migrateLegacyWizardManifest(v1) → v2` (removes `appFamilyId`) per R6 |
| `Pool` per `*.pool.json` | per-pool | yes | n/a until 2nd version |
| `ProfileStoreState` (DataStore) | 1 | yes | n/a (version 1 launch); future migrations via `migrateProfileStore(vN → vN+1)` pattern |

All formats: unknown variant → `Indeterminate` (graceful), not crash (Article VII §15).

---

## Plain Russian summary (для не-разработчика владельца)

**Этот файл описывает все типы данных** которые TASK-65 вводит, и как они связаны.

**Главные сущности**:

1. **`PresetRef(uid, version)`** — глобально уникальный идентификатор preset'а. uid = строка типа `"com.launcher.preset.simple-launcher"`, version = число которое bump'ается при breaking изменениях. Это нужно чтобы избежать collision между preset'ами от разных авторов с одинаковым названием.

2. **`Preset`** — JSON-файл, который шарится. Внутри: identity (uid + version + slug для человека), массив `configs` (настройки приложения с UX hints inline), опциональный `abstractProfile` (изначальный layout с placeholder иконками типа YouTube, Chrome — без личных данных).

3. **`Config`** — одна настройка-кубик внутри preset'а. Содержит title/description (i18n), check (как проверить applied), apply (что сделать чтобы применить), criticality (Required/Optional).

4. **`ProfileStoreState`** — то что хранится в памяти телефона. `activePresetRef` (какой preset активен сейчас) + `profiles: Map<...>` (история всех preset'ов которые когда-либо активировали). Каждый profile содержит layout + bindings (реальные контакты) + settings (массив настроек с current state).

5. **`ProfileSwitchStrategy`** — порт для будущих способов миграции при переключении preset'а. Сейчас одна реализация — `CopyOnActivateStrategy` (новый preset = чистая копия preset'а). Будущие: kind-matching (попытка перенести совместимые слоты), sandbox (примерить не commit'я).

6. **`CheckSpec.UIFont`** — новый вариант проверки (размер шрифта). Используется в `test-preset.json` чтобы доказать что движок generic (умеет проверять не только Android-permissions).

**Схема версионирования**: каждый формат имеет `schemaVersion: Int`, читается первым. Для breaking изменений пишется migration writer ДО того как breaking изменение шипится. `appFamilyId` field удаляется через migration writer ДО merge TASK-65.
