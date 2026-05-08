# Contract: Action Wire Format

**Version**: 1.0.0
**Owner**: `:core/commonMain` — `com.launcher.api.Action` + `ActionWireFormat`
**Producers**: mock JSON assets (`flows_mock_*.json`), future backend (spec 007), future QR share (spec 010), in-app config exports.
**Consumers**: `AndroidActionDispatcher`, `iOSActionDispatcher` (when introduced), test fixtures.
**Test fixture path**: `core/src/commonTest/resources/fixtures/action-wire-format/` (Clarification C4).

---

## Top-level shape

```json
{
  "schemaVersion": 1,
  "providerId": "<string>",
  "payload": { "kind": "<string>", ... },
  "fallback": { ...recursive Action... } | null,
  "sourceModuleId": "<string>" | null
}
```

### Field rules

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `schemaVersion` | integer | Yes | `1` for this contract. Readers must check first; reject silently if `> SUPPORTED_SCHEMA_VERSION`. |
| `providerId` | string | Yes | Matches `[a-z][a-z0-9_-]{1,31}`. Unknown values are valid wire data — surface as `ProviderUnavailable(UnknownInThisVersion)` at dispatch (Clarification C1). |
| `payload` | object | Yes | Tagged union. `kind` discriminator selects variant. Unknown `kind` → `Failure("unknown payload kind")` at dispatch. |
| `fallback` | Action or null | No (default `null`) | Recursive same-shape. Max chain depth: 2 (action + fallback + fallback-of-fallback). Deeper → `Failure("fallback chain too deep")`. |
| `sourceModuleId` | string or null | No | Free-form module/feature identifier for diagnostics; not consumed by dispatch logic. |

---

## Payload variants

Each variant is identified by `payload.kind`. Variants below are exhaustive for v1.0.0.

### `open_app`

```json
{ "kind": "open_app", "packageHint": "com.example.app", "storeUrlHint": "market://details?id=com.example.app" }
```

| Field | Type | Required |
|-------|------|----------|
| `packageHint` | string | Yes — Android package name to launch |
| `storeUrlHint` | string | No — Play Store fallback URI; if absent, dispatcher computes default. |

### `whatsapp_message`

```json
{ "kind": "whatsapp_message", "contactRef": "alice" }
```

`contactRef` resolves via `MockContact` (in spec 005) or backend contact registry (spec 007+). Handler builds `https://wa.me/<phone>?text=...` after resolving.

### `whatsapp_call`

```json
{ "kind": "whatsapp_call", "contactRef": "alice", "callKind": "VOICE" | "VIDEO" }
```

`callKind` field uses `WhatsAppCallKind` enum (`VOICE`, `VIDEO`). Renamed from
`kind` to avoid collision with the polymorphic discriminator at the same JSON
object level (`classDiscriminator = "kind"` in `ActionWireFormat.json`).

### `phone`

```json
{ "kind": "phone", "number": "+79991234567" }
```

`number` is E.164-normalised. Handler uses `Intent.ACTION_DIAL` (no auto-call).

### `sms`

```json
{ "kind": "sms", "number": "+79991234567", "body": "Optional pre-filled body" }
```

Handler uses `Intent.ACTION_SENDTO` with `smsto:` URI.

### `url`

```json
{ "kind": "url", "url": "https://example.com" }
```

Must start with `http://` or `https://`. Handler uses `ACTION_VIEW` with default browser.

### `youtube`

```json
{ "kind": "youtube", "target": { "kind": "home" | "video" | "channel", ... } }
```

Targets:
- `{ "kind": "home" }` — opens YouTube app home.
- `{ "kind": "video", "videoId": "dQw4w9WgXcQ" }` — opens specific video.
- `{ "kind": "channel", "channelHandle": "@channelname" }` — opens channel.

### `open_settings`

```json
{ "kind": "open_settings", "target": "General" }
```

`target` enum (`General` only in v1.0.0; expansion possible).

### `custom`

```json
{ "kind": "custom", "key": "my_provider_key", "params": { "k1": "v1", "k2": "v2" } }
```

Per Clarification C2 + security CHK-011:
- `key` matches `[a-z][a-z0-9_.-]{1,63}`.
- `params` size ≤ 16; key length ≤ 64; value length ≤ 1024.
- Values are strings only — no nested JSON.

---

## Versioning rules

### Forward compatibility

A reader of v1 **must** accept:
- Unknown `providerId` strings (per Clarification C1) — handed off to dispatch as `ProviderUnavailable(UnknownInThisVersion)`.
- Unknown fields in `payload` body — ignored silently.

A reader of v1 **must reject**:
- `schemaVersion > 1` — return `Failure("unsupported schema")` at dispatch.
- Unknown `payload.kind` — return `Failure("unknown payload kind")` at dispatch.
- Negative `schemaVersion`, missing required fields — `Failure` at parse.

### Backward compatibility

This is v1.0.0 — no prior wire format to be backward-compatible with.

**Bridge from spec 003 mock format**: `migrateLegacyAction(legacyJson: String): Action` exists in `commonMain`, with fitness-function-gated removal in spec 006 per Clarification C5 (build-time constant `MIGRATE_LEGACY_ACTION_DEADLINE_SPEC`).

### Breaking change policy

A breaking change (renaming, removing fields, changing semantics) requires:
1. Major version bump (`2.0.0`).
2. Migration function from `1.x` to `2.x` written **before** the breaking change ships.
3. Backward-compat test fixture for v1 read by v2 (the old format must still parse correctly into the new domain shape).
4. Fitness-function-gated removal of the migration bridge in a named follow-up spec.

---

## Test coverage requirement

Per CLAUDE.md §5 + spec 005 §8 fitness function 2:

- Roundtrip test for every `payload.kind` variant — `commonTest/.../ActionWireFormatTest.allPayloadVariantsRoundtrip`.
- Backward-compat test for every legacy spec 003 fixture — `commonTest/.../ActionWireFormatTest.legacyMigration_*`.
- Forward-compat test for unknown `providerId` and unknown `payload.kind`.
- Schema-version test: `schemaVersion: 99` rejected at dispatch with `Failure`.

Failure of any test blocks the PR.

---

## Examples

### Whatsapp message with Play Store fallback (workspace preset, alice tile)

```json
{
  "schemaVersion": 1,
  "providerId": "whatsapp",
  "payload": { "kind": "whatsapp_message", "contactRef": "alice" },
  "fallback": {
    "schemaVersion": 1,
    "providerId": "app",
    "payload": {
      "kind": "open_app",
      "packageHint": "com.whatsapp",
      "storeUrlHint": "market://details?id=com.whatsapp"
    }
  },
  "sourceModuleId": "spec-005-mock-workspace"
}
```

### Phone dial with browser fallback (tablet without telephony)

```json
{
  "schemaVersion": 1,
  "providerId": "phone",
  "payload": { "kind": "phone", "number": "+74951234567" },
  "fallback": {
    "schemaVersion": 1,
    "providerId": "browser",
    "payload": { "kind": "url", "url": "https://example.com/contact" }
  }
}
```

### Custom provider (forward-compat demo, payload from future spec 008)

```json
{
  "schemaVersion": 1,
  "providerId": "smart_assistant",
  "payload": {
    "kind": "custom",
    "key": "ask_assistant",
    "params": { "prompt": "schedule meeting", "lang": "ru" }
  }
}
```

When read by spec 005's launcher: `ProviderUnavailable("smart_assistant", UnknownInThisVersion)` → `AddSlotWizardScreen` hides this provider; `FlowScreen` showing this slot displays "feature unavailable in this version".

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Контракт wire-формата `Action` v1.0.0. JSON-корень: `{schemaVersion, providerId, payload, fallback?, sourceModuleId?}`. Используется в моках (`flows_mock_*.json`), будущей backend-sync (spec 007), будущем QR-share (spec 010), in-app экспорте конфига.

**Конкретика, которую стоит запомнить:**
- 9 вариантов `payload` (дискриминатор `kind`): `open_app`, `whatsapp_message`, `whatsapp_call`, `phone`, `sms`, `url`, `youtube`, `open_settings`, `custom`.
- Forward-compat: неизвестный `providerId` парсится OK (станет `ProviderUnavailable(UnknownInThisVersion)` при dispatch); неизвестный `payload.kind` — `Failure("unknown payload kind")` при dispatch (не падает на парсинге).
- Reject hard: `schemaVersion > 1` → `Failure("unsupported schema")`; отрицательный schemaVersion / отсутствующие обязательные поля → `Failure` на parse.
- Test fixtures лежат в `core/src/commonTest/resources/fixtures/action-wire-format/`.
- Breaking change → новый major + миграция написана до релиза + бэкомпат-фикстура + спек-дедлайн удаления моста.

**На что смотреть с осторожностью:**
- Имена `kind` (`open_app`, `whatsapp_message`, …) — публичные, переименование = breaking change.
- Регекс `[a-z][a-z0-9_-]{1,31}` для `providerId` — менять = поломать существующие JSON.
- Раздел Forward compatibility — нарушить эти правила = старые приложения станут падать на конфигах от новых.
