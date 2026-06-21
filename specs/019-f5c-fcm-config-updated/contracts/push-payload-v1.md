# Contract: PushPayload v1

**Wire format**: FCM data-message payload (Worker → recipient device via FCM HTTP v1 API).
**Semantic version**: `1` (initial).
**Backward-compat policy**: Per [CLAUDE.md rule 5](../../../CLAUDE.md) + [spec.md Wire-format policy](../spec.md). Asymmetric: Worker fail-closed на unknown schemaVersion (atomic deploy), client fail-soft (silent ignore — mixed-version fleet).
**Roundtrip test**: `core/push/commonTest/kotlin/com/familycare/push/WireFormatRoundtripTest.kt` — fixture `core/push/commonTest/resources/push-payload-v1.json`.

## FCM data-message structure

FCM HTTP v1 API requires payloads в shape:

```json
{
  "message": {
    "token": "<recipient FCM token>",
    "data": {
      "schemaVersion": "1",
      "eventType": "config-updated",
      "ownerUid": "AbCdEf1234567890",
      "triggerId": "550e8400-e29b-41d4-a716-446655440000",
      "configName": "main"
    },
    "android": {
      "priority": "normal",
      "collapse_key": "config-updated:AbCdEf1234567890:main",
      "ttl": "2419200s"
    }
  }
}
```

**FCM constraints**:
- `data` field must be `Map<String, String>` — all values strings (no nested objects, no booleans, no numbers — must be string-encoded).
- Maximum payload size: 4KB.
- `collapse_key` (NOT в data) — FCM optimization, на recipient side только последнее сообщение с тем же collapse_key доставляется (для offline → online batch).
- `priority`: `normal` (default) или `high` (wakes device immediately). Per EventTypeRegistry entry.
- `ttl`: 2419200s = 4 weeks (FCM default).

## Receiver-side parsed shape (Kotlin)

After `LauncherFirebaseMessagingService.onMessageReceived(RemoteMessage)` extracts `data: Map<String, String>` и parses:

```kotlin
// Decoded PushPayload (data class from core/push/commonMain/api/PushPayload.kt)
PushPayload(
    schemaVersion = 1,
    eventType = "config-updated",
    ownerUid = "AbCdEf1234567890",
    triggerId = "550e8400-e29b-41d4-a716-446655440000",
    fields = mapOf("configName" to "main"),
    linkId = null,                              // DEPRECATED, present только для legacy 008 messages
)
```

## Field definitions

### `schemaVersion: String` (parsed → Int)

**Required.** Current value `"1"`. Receiver:
1. Read first, parse to Int.
2. If `schemaVersion > MAX_SUPPORTED_SCHEMA_VERSION` (currently 1) — silent log + ignore (client fail-soft per Wire-format policy).
3. If missing or unparseable — silent log + ignore (FR-075 malformed payload).

### `eventType: String`

**Required.** Lookup в `PushHandlerRegistry.handlerFor(EventType.fromWireOrNull(eventType))`:
- If found → dispatch handler via `BackgroundDispatcher` with `eventType.handlerTimeout`.
- If null (unknown eventType) → silent log + ignore (FR-023). Per Wire-format policy client fail-soft.

### `ownerUid: String`

**Required** для most event types (config-updated, sos-triggered, etc.) — identifies whose namespace the event concerns.

Receiver's `ConfigUpdatedHandler` logic:
- If `ownerUid === currentUid` (per F-4 AuthIdentity) → `ConfigSaver.loadOwn(configName)`.
- Else → `ConfigSaver.loadForOther(ownerUid, configName)` (cross-UID delegation, F-5b).

### `triggerId: String`

**Required.** UUID v4 echoed from Worker response (which was generated from caller's Idempotency-Key). Used as **receiver dedupe key** (FR-044):
- If receiver gets same `triggerId` дважды в 2-second window → at most one `ConfigSaver.loadOwn` invocation (debounce).
- If FCM delivers duplicate (common: doze wake, network retry) → idempotent receiver behavior.

### Event-specific fields (flat extensions)

**Per `config-updated`**:
- `configName: String` — required. Identifies which named config updated в `ownerUid` namespace.

**Future per `sos-triggered`** (S-4):
- `lat: String` — latitude.
- `lng: String` — longitude.
- `ts: String` — millis-timestamp.

**Future per `album-photo-added`** (V-3):
- `albumId: String`.
- `photoId: String`.

Fields packed flat в data Map (NOT nested) per FCM constraint. Receiver-side `PushPayloadWireFormat.decode` reconstructs `fields: Map<String, String>` from flat keys (e.g., all keys not in `{schemaVersion, eventType, ownerUid, triggerId, linkId}` → fields map).

### `linkId: String?` (DEPRECATED)

**Optional, deprecated.** Present для backward-compat parsing of legacy spec 007/008 events (`PushType.ConfigChanged`, `PushType.CommandIssued`, `PushType.Revoke`). New F-5c events do NOT set `linkId`.

**Removal trigger**: schemaVersion 2 bump after spec 008 rewrite ships AND no in-prod legacy consumers remain. Inline `TODO(removal SRV-PUSH-FOUNDATION future)` в `core/push/api/PushPayload.kt`.

## Worker-side FCM dispatch logic

```typescript
// workers/push/src/dispatch/fcm-dispatcher.ts (pseudocode)
async function dispatchFcm(
  recipient: RecipientDevice,
  request: PushTriggerRequest,
  triggerId: string,
  registryEntry: EventTypeRegistryEntry,
): Promise<DispatchResult> {

  const fcmPayload = {
    message: {
      token: recipient.fcmToken,
      data: {
        schemaVersion: '1',
        eventType: request.eventType,
        ownerUid: request.ownerUid,
        triggerId,
        ...request.payload,  // flatten payload fields into data map
      },
      android: {
        priority: registryEntry.priority,
        collapse_key: registryEntry.collapseKey(request) ?? undefined,
        ttl: registryEntry.ttlSeconds ? `${registryEntry.ttlSeconds}s` : undefined,
      },
    },
  }

  // POST к https://fcm.googleapis.com/v1/projects/{projectId}/messages:send
  // Authorization: Bearer <Service Account JWT>
  return await retryWithBackoff(
    () => fcmHttpPost(fcmPayload),
    { attempts: 3, backoff: [500, 2000, 8000] }  // FR-009
  )
}
```

## Receiver-side parse failure handling

Per FR-075:

```kotlin
override fun onMessageReceived(message: RemoteMessage) {
    val data = message.data
    val payload = PushPayloadWireFormat.parse(data)
        ?: return logWarn("Malformed push payload, ignoring")  // silent ignore

    if (payload.schemaVersion > WireFormatVersion.MAX_SUPPORTED_SCHEMA_VERSION) {
        return logWarn("Unsupported schemaVersion ${payload.schemaVersion}, ignoring")
    }

    val eventType = EventType.fromWireOrNull(payload.eventType)
        ?: return logWarn("Unknown eventType ${payload.eventType}, ignoring")

    val handler = pushHandlerRegistry.handlerFor(eventType)
        ?: return logWarn("No handler for eventType ${payload.eventType}, ignoring")

    coroutineScope.launch {
        backgroundDispatcher.dispatch(
            taskName = "push-${payload.eventType}-${payload.triggerId}",
            timeout = eventType.handlerTimeout,
        ) {
            handler.handle(payload)
        }
    }
}
```

**NEVER crash** на malformed input. NEVER expose stack traces к user. NEVER pop notification (per FR-018, FR-045, CLAUDE.md rule 10).

## Roundtrip test fixture

File: `core/push/commonTest/resources/push-payload-v1.json`

```json
{
  "schemaVersion": 1,
  "eventType": "config-updated",
  "ownerUid": "TestUid1234567890123456789",
  "triggerId": "550e8400-e29b-41d4-a716-446655440000",
  "fields": {
    "configName": "main"
  }
}
```

Test asserts:
1. Parse fixture → PushPayload DTO equals expected.
2. Serialize DTO → JSON equals fixture (after key ordering normalization).
3. Backward-compat parse: fixture с `"linkId": "legacy-pair-id"` field → linkId populated, other fields parsed correctly.
4. Forward-compat: fixture с unknown extra field → parse succeeds, extra field accessible via fields map.

## Versioning roadmap

**v1 → v2 trigger**: same as PushTriggerRequest — breaking change (removal, rename, type change).

**Likely candidates для v2**:
- Final removal of `linkId` field (after legacy spec 008 fully retired).
- Nested payload schema (move event-specific fields under `payload: {...}` instead of flat) — would require FCM payload format change too.

---

## Краткое резюме (для не-разработчика)

Это **формат того, что прилетает на устройство получателя через Google FCM**.

**Что в payload'е**:
- Тип события (`config-updated`).
- Uid владельца данных.
- Уникальный ID (для защиты от дубликатов — FCM может доставить одно сообщение дважды).
- Event-специфичные поля (для config — имя конфига, для будущего фото — id альбома + id фото).
- **НЕ сам контент** — payload только указывает «скачай свежее». Сам зашифрованный config / фото лежит в Firestore / зашифрованном хранилище.

**Что устройство делает при получении**:
1. Проверяет «знаю ли я версию формата?» — если нет, тихо игнорирует.
2. Проверяет «знаю ли я этот тип события?» — если нет (старая версия приложения), тихо игнорирует. Никаких crash'ей, никаких alert'ов пользователю.
3. Если знает — передаёт handler'у в фоновую систему задач (WorkManager). Handler идёт и качает свежие данные.

**Защита от дубликатов**: уникальный ID (`triggerId`). Если придёт 5 раз — обработается 1 раз (debounce 2 секунды).

**Дедупликация по типу события**: Worker устанавливает `collapse_key` — FCM на стороне Google объединяет дубликаты с одинаковым ключом (если 5 push'ей про обновление одного конфига пришли быстро, на устройство доставится только последний).

**Никаких visible alerts** — это data-only message, никаких звонков / banners / lock screen уведомлений (это **plumbing**, не user notification).
