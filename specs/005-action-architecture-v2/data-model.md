# Data Model — Spec 005 (action-architecture-v2)

All types live in `core/src/commonMain/kotlin/com/launcher/api/`. Pure Kotlin, no platform imports.

---

## `Action`

```kotlin
@Serializable
data class Action(
    val schemaVersion: Int = SUPPORTED_SCHEMA_VERSION,  // = 1 for this spec
    val providerId: ProviderId,
    val payload: ActionPayload,
    val fallback: Action? = null,
    val sourceModuleId: String? = null,
) {
    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val MAX_FALLBACK_DEPTH = 2  // action + fallback + fallback-of-fallback
    }
}
```

**Invariants** (validated at construction or first parse):
- `schemaVersion ≥ 1`.
- `providerId.value` matches `[a-z][a-z0-9_-]{1,31}`.
- Fallback chain depth ≤ `MAX_FALLBACK_DEPTH` (deeper → `Failure` at dispatch).

---

## `ProviderId`

```kotlin
@Serializable(with = ProviderIdSerializer::class)
@JvmInline
value class ProviderId(val value: String) {

    companion object {
        val APP             = ProviderId("app")
        val WHATSAPP        = ProviderId("whatsapp")
        val TELEGRAM        = ProviderId("telegram")
        val PHONE           = ProviderId("phone")
        val SMS             = ProviderId("sms")
        val BROWSER         = ProviderId("browser")
        val YOUTUBE         = ProviderId("youtube")
        val SYSTEM_SETTINGS = ProviderId("system_settings")

        private val WIRE_REGEX = Regex("[a-z][a-z0-9_-]{1,31}")

        fun fromWire(s: String): ProviderId =
            require(s.matches(WIRE_REGEX)) { "invalid providerId: $s" }
                .let { ProviderId(s) }
    }
}
```

Per Clarification C1: unknown `providerId.value` is **not** a parse error — surfaces as `ProviderUnavailable(providerId, UnknownInThisVersion)` at dispatch.

---

## `ActionPayload`

```kotlin
@Serializable
sealed class ActionPayload {

    @Serializable @SerialName("open_app")
    data class OpenApp(
        val packageHint: String,
        val storeUrlHint: String? = null,
    ) : ActionPayload()

    @Serializable @SerialName("whatsapp_message")
    data class WhatsAppMessage(val contactRef: String) : ActionPayload()

    @Serializable @SerialName("whatsapp_call")
    data class WhatsAppCall(
        val contactRef: String,
        val kind: WhatsAppCallKind,
    ) : ActionPayload()

    @Serializable @SerialName("phone")
    data class Phone(val number: String) : ActionPayload()

    @Serializable @SerialName("sms")
    data class Sms(val number: String, val body: String? = null) : ActionPayload()

    @Serializable @SerialName("url")
    data class Url(val url: String) : ActionPayload()

    @Serializable @SerialName("youtube")
    data class YouTube(val target: YouTubeTarget) : ActionPayload()

    @Serializable @SerialName("open_settings")
    data class OpenSettings(val target: SettingsTarget = SettingsTarget.General) : ActionPayload()

    @Serializable @SerialName("custom")
    data class Custom(
        val key: String,
        val params: Map<String, String> = emptyMap(),
    ) : ActionPayload()
}

@Serializable
enum class WhatsAppCallKind { VOICE, VIDEO }

@Serializable
sealed class YouTubeTarget {
    @Serializable @SerialName("home") data object Home : YouTubeTarget()
    @Serializable @SerialName("video") data class Video(val videoId: String) : YouTubeTarget()
    @Serializable @SerialName("channel") data class Channel(val channelHandle: String) : YouTubeTarget()
}

@Serializable
enum class SettingsTarget { General }  // expansion in future specs
```

**`Custom` validation** (per security CHK-011, performed by `CustomPayloadValidator`):
- `key` length ≤ 64, matches `[a-z][a-z0-9_.-]{1,63}`.
- `params` size ≤ 16.
- each key length ≤ 64; each value length ≤ 1024.
- no nested-JSON-as-string detected (warn).

---

## `DispatchResult`

```kotlin
sealed class DispatchResult {
    data object Ok : DispatchResult()
    data class BlockedByPolicy(val reason: BlockReason) : DispatchResult()
    data class ProviderUnavailable(
        val providerId: ProviderId,
        val hint: UnavailabilityHint,
    ) : DispatchResult()
    data class Failure(val reason: String) : DispatchResult()
}

enum class BlockReason {
    INVALID_REQUEST,
    PERMISSION_OR_POLICY,
}

enum class UnavailabilityHint {
    Missing,                // app/feature not installed (fallback may help)
    NotApplicable,          // device cannot do this (no SIM, no telephony, no browser)
    UnknownInThisVersion,   // newer providerId, this build doesn't know it (Clarification C1)
}
```

---

## `ProviderRegistry` types

```kotlin
interface ProviderRegistry {
    fun availability(providerId: ProviderId): ProviderAvailability
    fun snapshot(): List<ProviderState>
    val updates: Flow<List<ProviderState>>  // debounced 1s, distinct (Clarification C3)
}

data class ProviderState(
    val providerId: ProviderId,
    val availability: ProviderAvailability,
    val installedPackage: String? = null,    // null for non-app providers (phone, sms, browser if multiple)
    val displayName: String? = null,         // null → UI uses stringResource(provider_name_<id>)
)

sealed class ProviderAvailability {
    data object Available : ProviderAvailability()
    data class Missing(val installHint: InstallHint?) : ProviderAvailability()
    data class NotApplicable(val reason: NotApplicableReason) : ProviderAvailability()
}

data class InstallHint(
    val storeUrl: String,           // market://details?id=...
    val webStoreUrl: String,        // https://play.google.com/...
    val recommendedPackage: String, // com.whatsapp etc.
)

enum class NotApplicableReason {
    NoTelephony,        // tablet without phone hardware
    NoBrowser,          // device without anything answering ACTION_VIEW(http)
    NoDefaultSmsApp,    // device with no default SMS handler
}
```

---

## `ProjectEvent.ActionDispatched`

Extension to existing `ProjectEvent` enum/sealed class in `:core/commonMain/`. See [`contracts/diagnostics-events-v2.md`](contracts/diagnostics-events-v2.md) for full taxonomy.

```kotlin
sealed class ProjectEvent {
    // ... existing variants ...

    data class ActionDispatched(
        val providerId: ProviderId,
        val resultKind: ResultKind,        // Ok | BlockedByPolicy | ProviderUnavailable | Failure
        val fallbackUsed: Boolean,
        val timestampMs: Long,
    ) : ProjectEvent()

    enum class ResultKind { Ok, BlockedByPolicy, ProviderUnavailable, Failure }
}
```

**No fields beyond the four above.** Per security CHK-022 logging contract: handler arguments (numbers, URLs, contactRefs) **never** appear in events.

---

## Wire format example

```json
{
  "schemaVersion": 1,
  "providerId": "whatsapp",
  "payload": {
    "kind": "whatsapp_message",
    "contactRef": "alice"
  },
  "fallback": {
    "schemaVersion": 1,
    "providerId": "app",
    "payload": {
      "kind": "open_app",
      "packageHint": "com.whatsapp",
      "storeUrlHint": "market://details?id=com.whatsapp"
    }
  }
}
```

See [`contracts/action-wire-format.md`](contracts/action-wire-format.md) for full specification including all 9 payload variants.

---

## Removed types (reference for grep)

These types are **deleted** by this spec. Listed here so readers know what was dismissed:

- `ActionRequest` (sealed) → replaced by `Action`.
- `ActionRequest.WhatsAppHandoff` → replaced by `Action(providerId=WHATSAPP, payload=WhatsAppCall(...))`.
- `WhatsAppHandoffRequest`, `WhatsAppHandoffResult` → no longer exist.
- `DispatchResult.WhatsApp` → folded into generic `Failure` / `Ok`.
- `ReturnContextRecord`, `ReturnRestoreOutcome` → feature removed (R3).
- `MockCommunicationEntry` → split into provider-agnostic `MockContact` in `mock_contacts.json`.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Главный тип — `Action(schemaVersion, providerId, payload, fallback, sourceModuleId)`. Сейчас в коде на этом месте `ActionRequest` с тремя WhatsApp-специфичными вариантами; новый `Action` — один тип на все провайдеры с расширяемой полезной нагрузкой.

**Конкретика, которую стоит запомнить:**
- `ProviderId` — value class над String, не enum. 8 константных значений: `APP`, `WHATSAPP`, `TELEGRAM`, `PHONE`, `SMS`, `BROWSER`, `YOUTUBE`, `SYSTEM_SETTINGS`. Регекс валидации: `[a-z][a-z0-9_-]{1,31}`.
- `ActionPayload` — sealed class с 9 вариантами: `OpenApp`, `WhatsAppMessage`, `WhatsAppCall(kind: VOICE|VIDEO)`, `Phone`, `Sms`, `Url`, `YouTube(target: Home|Video|Channel)`, `OpenSettings`, `Custom(key, params)`.
- `Custom.params` = `Map<String,String>` (только строки). Лимиты: ≤ 16 ключей, ключ ≤ 64 символа (регекс `[a-z][a-z0-9_.-]{1,63}`), значение ≤ 1024 символа.
- `DispatchResult` — `Ok | BlockedByPolicy(reason) | ProviderUnavailable(providerId, hint) | Failure(reason)`. Никаких WhatsApp-специфичных вариантов.
- `UnavailabilityHint` — `Missing | NotApplicable | UnknownInThisVersion`.
- `MAX_FALLBACK_DEPTH = 2`. Глубже → `Failure("fallback chain too deep")`.
- `ProjectEvent.ActionDispatched` — ровно 4 поля: `providerId`, `resultKind`, `fallbackUsed`, `timestampMs`. Заморожено автотестом.
- `ProviderRegistry.updates: Flow<List<ProviderState>>` — debounced 1с, distinct.

**На что смотреть с осторожностью:**
- Регекс `[a-z][a-z0-9_-]{1,31}` для `ProviderId.value` — частично перекрывается с регексом `Custom.key` (`[a-z][a-z0-9_.-]{1,63}` — точку допускает). Менять любой = потенциальная поломка совместимости старых JSON.
- Имена `kind` в JSON-дискриминаторе (`open_app`, `whatsapp_message`, `whatsapp_call`, …) — публичные. Переименование = breaking change wire-формата.
