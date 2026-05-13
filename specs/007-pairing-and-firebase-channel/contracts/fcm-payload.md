# Wire format: FCM data-message payload

**Source of truth**: this document.
**Used by**: spec 007 §FR-016 (Managed receiver), FR-024 (Worker sender).
**Schema version**: 1.
**Transport**: FCM HTTP v1 API `data` field; **silent push** (no `notification` field).

---

## Outgoing message (Worker → FCM)

```json
{
  "message": {
    "topic": "link-{linkId}",
    "data": {
      "schemaVersion": "1",
      "type": "config-changed",
      "linkId": "abc123XYZ"
    },
    "android": {
      "priority": "HIGH"
    }
  }
}
```

**Note**: все значения в `data` — **строки** (это требование FCM API). Парсинг на стороне Managed преобразует.

## Field schema

| Field | Type | Required | Notes |
|---|---|---|---|
| `schemaVersion` | string `"1"` | ✓ | Currently `"1"` (stringified per FCM API) |
| `type` | string | ✓ | One of: `config-changed`, `command-issued`, `revoke` |
| `linkId` | string | ✓ | Identifies which link's documents to refetch |
| `cmdId` | string | only if `type=command-issued` | Command ID to fetch from `/links/{linkId}/commands/{cmdId}` |

## Reception on Managed (`LauncherFirebaseMessagingService.onMessageReceived`)

```kotlin
override fun onMessageReceived(message: RemoteMessage) {
  val data = message.data
  val payload = PushPayload(
    schemaVersion = data["schemaVersion"]?.toIntOrNull() ?: return,  // malformed → drop
    type = parseType(data["type"]) ?: return,                          // unknown type → drop
    linkId = data["linkId"] ?: return,
    extra = buildJsonObject {
      data["cmdId"]?.let { put("cmdId", it) }
    }.takeIf { it.isNotEmpty() }
  )
  pushReceiver.onPush(payload)
}
```

**Idempotency**: Type handlers must be **idempotent**. FCM может доставить duplicate; Managed не должен «применить дважды». Для `config-changed` — read latest, no state mutation; idempotent by design.

## Push types

### `config-changed`

- Trigger: admin wrote `/links/{linkId}/config`.
- Receiver action (spec 007): read `/links/{linkId}/config`, log `received: schemaVersion=N`. **NOT apply** — application is spec 008.
- **TODO в `PushReceiver.kt`**: «expand here in spec 008: apply config → local Room → UI refresh».

### `command-issued`

- Trigger: admin wrote `/links/{linkId}/commands/{cmdId}`.
- Receiver action (spec 007): read command doc, log. **NOT execute** — execution is spec 009.
- **TODO в `PushReceiver.kt`**: «expand here in spec 009: dispatch command handler».

### `revoke`

- Trigger: optional, Managed on revoke calls `pushSender.notify(linkId, Revoke)` so admin'у приходит уведомление.
- Receiver action: admin UI refreshes paired-devices list.

## Tests (commonTest + Worker tests)

| Test | What it verifies |
|---|---|
| `FcmPayloadWireFormat.roundtrip_config_changed` | Build payload → serialize to data-map → parse → original |
| `FcmPayloadWireFormat.roundtrip_command_issued_with_cmdId` | extra.cmdId preserved |
| `FcmPayloadWireFormat.unknown_type_drops` | Unknown `type` in data → parser returns null (drop) |
| `FcmPayloadWireFormat.malformed_schemaVersion_drops` | Non-numeric schemaVersion → drop |
| `Worker.fcm_send_outgoing_format` | Worker outgoing body matches spec (vitest) |
| `FcmPayload.idempotent_config_changed` | Two onMessageReceived calls с одинаковым payload → один read из Firestore (но это OK если оба читают latest) |

## Backward compatibility policy

- `data["v"]` always present, currently `"1"`.
- Adding fields → OK (additive).
- Adding new `type` value → OK, old Managed apps drop unknown types gracefully.
- Removing existing type → breaking; bump schemaVersion to `"2"`.

**TODO в `parseType()`**: «при добавлении новых типов (например `incoming-call` в будущем спеке звонков) — добавлять в when, не падать на unknown».

---

<!-- novice summary -->

## TL;DR

Когда Worker отправляет push на бабушкин телефон, в push'е лежит **маленькая записка** (data-message, не текстовое уведомление!): «schemaVersion=1, type=config-changed, linkId=abc». Бабушкин телефон **просыпается**, читает записку, идёт в Firestore читать обновлённый config. Никакого видимого уведомления на экране — это **silent push**, бабушка ничего не заметит. Идемпотентность важна: FCM может доставить один и тот же push дважды, наш код должен быть к этому готов.
