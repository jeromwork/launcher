# Data Model: Provider Capabilities and Health

**Spec:** [`spec.md`](./spec.md) · **Plan:** [`plan.md`](./plan.md) · **Date:** 2026-05-09

This document is the **single source of truth** for type definitions introduced by спек 006. Per CLAUDE.md rule 1, all types live в `commonMain` (pure Kotlin). Adapters в `androidMain` consume these types but never expose Android-specific replacements.

---

## 1. `Capability`

Per-provider snapshot. Source: FR-001, FR-006, FR-041, FR-042. Wire-format contract: [`contracts/capability-wire-format.md`](./contracts/capability-wire-format.md).

```kotlin
package com.launcher.api.capability

import com.launcher.api.action.ProviderId
import kotlinx.serialization.Serializable

@Serializable
data class Capability(
    val schemaVersion: Int = SUPPORTED_SCHEMA_VERSION,
    val providerId: ProviderId,
    val displayName: String,
    val iconId: String,           // namespace-prefixed, see IconRef
    val iconSha256: String? = null,
    val available: Boolean,
    val versionCode: Long? = null, // null when unknown (iOS) or provider not installed
) {
    companion object {
        const val SUPPORTED_SCHEMA_VERSION: Int = 1
    }
}
```

**Field constraints:**
- `schemaVersion` ≥ 1; reader accepts >SUPPORTED with best-effort parse (FR-043).
- `providerId` reuses spec 005 `ProviderId` (value class over String, namespace `[a-z][a-z0-9_-]{1,31}`).
- `displayName` user-facing label, NOT a brand trademark check. Length 1..64.
- `iconId` non-empty string в формате `<namespace>:<name>`, см. §4.
- `iconSha256` 64-char lowercase hex if present; nullable for `bundled:` icons (no remote checksum needed in спек 006).
- `available` true ⟺ provider can dispatch right now (per spec 005 ProviderRegistry availability rules).
- `versionCode` Long because Android `versionCode` is Int but Long-typed in PackageInfoCompat for forward-compat.

---

## 2. `Health`

Per-device snapshot. Source: FR-013, FR-041, FR-042. Wire-format contract: [`contracts/health-wire-format.md`](./contracts/health-wire-format.md).

```kotlin
package com.launcher.api.health

import kotlinx.serialization.Serializable

@Serializable
data class Health(
    val schemaVersion: Int = SUPPORTED_SCHEMA_VERSION,
    val batteryPercent: Int,
    val charging: Boolean,
    val connectivity: Connectivity,
    val ringerVolumePercent: Int,
    val audioStreamMuted: Boolean,
    val lastSeen: Long,           // epoch millis
    val appVersion: String,       // launcher's own versionName, e.g. "1.4.2"
) {
    companion object {
        const val SUPPORTED_SCHEMA_VERSION: Int = 1
    }
}
```

**Field constraints:**
- `batteryPercent` 0..100 (clamped); 0 if unknown.
- `charging` false if unknown.
- `connectivity` enum, см. §3.
- `ringerVolumePercent` 0..100 (normalised, NOT raw `STREAM_RING` units per FR-015).
- `audioStreamMuted` true ⟺ ringer effectively muted (FR-016): `STREAM_RING == 0` OR system DND active suppressing ringer.
- `lastSeen` epoch millis of most recent `RESUMED` event (or app start time if never RESUMED yet).
- `appVersion` launcher's own `BuildConfig.VERSION_NAME`.

---

## 3. `Connectivity`

Network reachability enum. Source: FR-014, FR-044.

```kotlin
package com.launcher.api.health

import kotlinx.serialization.Serializable

@Serializable
enum class Connectivity {
    Wifi,
    Mobile,
    None;

    companion object {
        /** Safe fallback for unknown enum values (per FR-044). */
        fun fromWireOrNone(name: String?): Connectivity =
            entries.firstOrNull { it.name == name } ?: None
    }
}
```

**Reserved namespace for future:** `Vpn`, `Ethernet`. Adding values is non-breaking; readers using `fromWireOrNone` survive.

---

## 4. `IconRef` (namespace conventions)

`iconId` is a string with namespace prefix. Source: FR-009, contract [`contracts/icon-id-namespace.md`](./contracts/icon-id-namespace.md).

```kotlin
package com.launcher.api.capability

/** Helpers to build / parse iconId strings. Not a wrapper type — iconId stays String in Capability. */
object IconRef {
    private val VALID = Regex("^[a-z][a-z0-9_-]*:[A-Za-z0-9_-]{1,128}$")

    const val NAMESPACE_BUNDLED = "bundled"
    const val NAMESPACE_CUSTOM = "custom"   // reserved for spec 007/009
    const val NAMESPACE_PRIVATE = "private" // reserved for spec 011 (e2e media)

    fun bundled(name: String): String = "$NAMESPACE_BUNDLED:$name"

    fun isValid(iconId: String): Boolean = VALID.matches(iconId)

    fun namespaceOf(iconId: String): String? =
        iconId.substringBefore(':', missingDelimiterValue = "").takeIf { it.isNotEmpty() }

    fun nameOf(iconId: String): String? =
        iconId.substringAfter(':', missingDelimiterValue = "").takeIf { it.isNotEmpty() }
}
```

**Bundled name → drawable resource mapping** (Android adapter):

| iconId | drawable |
|--------|----------|
| `bundled:app` | `R.drawable.provider_app` |
| `bundled:whatsapp` | `R.drawable.provider_whatsapp` |
| `bundled:telegram` | `R.drawable.provider_telegram` |
| `bundled:phone` | `R.drawable.provider_phone` |
| `bundled:sms` | `R.drawable.provider_sms` |
| `bundled:browser` | `R.drawable.provider_browser` |
| `bundled:youtube` | `R.drawable.provider_youtube` |
| `bundled:system_settings` | `R.drawable.provider_system_settings` |

Unknown `bundled:` name → `IconResolution.Placeholder` (not crash).

---

## 5. `IconResolution`

Result of `IconStorage.resolve(iconId)`. Source: FR-008.

```kotlin
package com.launcher.api.capability

sealed class IconResolution {
    /** Successfully resolved drawable. Carries opaque platform handle as Int (Android resource id) or kotlinx.io ByteString in future implementations. */
    data class Drawable(val androidResourceId: Int) : IconResolution()
    /** Known namespace but not found / not yet downloaded. UI shows placeholder. */
    data object Placeholder : IconResolution()
    /** Unknown namespace — recoverable: UI shows placeholder, but log structured event. */
    data object NotFound : IconResolution()
}
```

**Note:** `Drawable.androidResourceId: Int` is Android-specific. This is acceptable because `IconResolution` consumers in спеке 006 are Android UI Composables, and iOS deferred per Spec §6. When iOS comes online, this becomes `expect class IconResolution.Drawable` with Android `actual` returning resource id and iOS `actual` returning `UIImage`. Tracked в research.md R3.

---

## 6. `LauncherSettings`

User-toggleable banner preferences. Source: FR-032, FR-033, FR-034, FR-041, FR-051.

```kotlin
package com.launcher.api.settings

import kotlinx.serialization.Serializable

@Serializable
data class LauncherSettings(
    val schemaVersion: Int = SUPPORTED_SCHEMA_VERSION,
    val banners: BannerToggles = BannerToggles(),
) {
    companion object {
        const val SUPPORTED_SCHEMA_VERSION: Int = 1

        /** Defaults applied when DataStore is empty или corrupted (FR-051). */
        fun defaultsForPreset(presetSlug: String): LauncherSettings = when (presetSlug) {
            "simple-launcher" -> LauncherSettings(banners = BannerToggles(airplane = true, mute = true))
            else              -> LauncherSettings(banners = BannerToggles(airplane = false, mute = false))
        }
    }
}

@Serializable
data class BannerToggles(
    val airplane: Boolean = false,
    val mute: Boolean = false,
)
```

**Note:** `raiseRingerOnLongOffline` and `banners.offline` фигурируют в спеке 013 — добавляются как новые поля без миграции существующих читателей (per FR-042 default values).

---

## 7. `AlertBanner`

Sealed type representing a banner in the home screen stack. Source: FR-031.

```kotlin
package com.launcher.api.alerts

sealed class AlertBanner {
    data object Airplane : AlertBanner()
    data object Mute : AlertBanner()
    // Reserved for spec 013: data object NoInternet : AlertBanner()
}
```

Stack order (FR-031): `Airplane` rendered above `Mute`.

---

## 8. Ports (interfaces)

### `CapabilityRepository`

```kotlin
package com.launcher.api.capability

import kotlinx.coroutines.flow.Flow

interface CapabilityRepository {
    /** Hot flow of current snapshot. Replays last value on subscribe. */
    fun observe(): Flow<List<Capability>>

    /** Synchronous read of last-known snapshot (used by debug screens / one-shot consumers). */
    fun snapshot(): List<Capability>
}
```

### `HealthRepository`

```kotlin
package com.launcher.api.health

import kotlinx.coroutines.flow.Flow

interface HealthRepository {
    fun observe(): Flow<Health>
    fun snapshot(): Health
}
```

### `IconStorage`

```kotlin
package com.launcher.api.capability

interface IconStorage {
    /** Pure function: given iconId, return resolution. No I/O for bundled namespace. */
    fun resolve(iconId: String): IconResolution
}
```

### `SettingsRepository`

```kotlin
package com.launcher.api.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observe(): Flow<LauncherSettings>
    fun snapshot(): LauncherSettings
    suspend fun updateBanners(transform: (BannerToggles) -> BannerToggles)
}
```

---

## 9. Validation rules summary

| Rule | Where enforced |
|------|----------------|
| `schemaVersion ≥ 1`, parse-only, no exception on `>SUPPORTED` (FR-043) | `WireFormatJson` shared instance + per-type roundtrip tests |
| `Connectivity` unknown → `None` (FR-044) | `Connectivity.fromWireOrNone` companion |
| `iconId` valid namespace format (FR-009) | `IconRef.isValid` validator (called в Capability builder, NOT в data class init — to allow forward-compat unknown namespaces parsed без exception) |
| `batteryPercent` 0..100 | Adapter clamps before construction |
| `ringerVolumePercent` 0..100 normalisation (FR-015) | Adapter normalises from raw `STREAM_RING` units |
| Optional fields default values applied when missing (FR-042) | kotlinx.serialization `@Serializable` defaults |

---

## 10. Cross-spec dependencies

- `ProviderId` from спека 005 (`core/src/commonMain/kotlin/com/launcher/api/action/`) — re-used as `Capability.providerId`. No changes required.
- `ProviderState` from спека 005 — **deleted** as part of FR-007. Call sites (4 fitness tests + ProviderRegistry consumers) are migrated to read from `CapabilityRepository.snapshot().map { it.providerId to it.available }` per spec 005 backward-compat shape.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Документ фиксирует 6 новых типов данных в `commonMain` (общий код): `Capability` (карточка провайдера), `Health` (состояние устройства), `Connectivity` (enum WiFi/Mobile/None), `IconRef` (helper для namespace `bundled:`/`custom:`/`private:`), `IconResolution` (результат поиска иконки), `LauncherSettings` (настройки баннеров) — и 4 порта (`CapabilityRepository`, `HealthRepository`, `IconStorage`, `SettingsRepository`).

**Конкретика, которую стоит запомнить:**
- Каждая wire-format структура имеет `companion object` с константой `SUPPORTED_SCHEMA_VERSION = 1`.
- `Capability` — 7 полей; `versionCode: Long?` (nullable, на iOS будет null).
- `Health` — 8 полей; `audioStreamMuted` отражает effective state (true если громкость 0 ИЛИ DND активен).
- `Connectivity.fromWireOrNone()` — safe parser, неизвестное значение → `None` (FR-44).
- `IconRef.bundled("whatsapp")` → `"bundled:whatsapp"`. Валидация regex `[a-z][a-z0-9_-]*:[A-Za-z0-9_-]{1,128}`.
- `LauncherSettings.defaultsForPreset(slug)` — при corruption (FR-051) или пустом DataStore: для `simple-launcher` оба banner toggle ON, для `workspace`/`launcher` оба OFF.
- `IconResolution.Drawable.androidResourceId: Int` — единственное место где Android-тип просочился в `commonMain`. Acceptable пока iOS не нужен; станет `expect class` когда iOS пора.
- Маппинг `bundled:<name>` → `R.drawable.provider_<name>` для 8 провайдеров.

**На что смотреть с осторожностью:**
- `Capability.iconId` — string с namespace, **НЕ** sealed class. Валидация через `IconRef.isValid` вызывается в builder, а **не** в data class `init` — чтобы forward-compat unknown namespaces (`future:foo`) парсились без exception.
- `ProviderState` из спека 005 **удаляется** — каждое его использование в спеке 005 fitness tests мигрируется. Если что-то забыто — спек 005 fitness tests упадут после merge.
- `IconResolution.NotFound` vs `Placeholder`: `NotFound` для unknown namespace (логируем event), `Placeholder` для known но missing (drawable нет в APK или remote не загружено). Не путать.
