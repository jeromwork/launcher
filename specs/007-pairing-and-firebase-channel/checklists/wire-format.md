# Checklist: wire-format — spec 007

**Generated**: 2026-05-11 by `/speckit.plan` Step 5.
**Per**: CLAUDE.md rule §5 + Article VII §3.

## Result: 18/18 PASS (4 informational notes for /speckit.tasks)

### Wire formats inventory (6 contracts + 1 persistence)

1. `/pairings/{token}` — [`contracts/pairing-token.md`](../contracts/pairing-token.md)
2. `/links/{linkId}` — [`contracts/link.md`](../contracts/link.md)
3. `/links/{linkId}/state/current` (bootstrap) — [`contracts/state-bootstrap.md`](../contracts/state-bootstrap.md)
4. QR deep-link `launcher://pair?token=XXX&v=1` — [`contracts/qr-deeplink.md`](../contracts/qr-deeplink.md)
5. FCM data-message payload — [`contracts/fcm-payload.md`](../contracts/fcm-payload.md)
6. Worker HTTP API `POST /notify` — [`contracts/worker-notify.md`](../contracts/worker-notify.md)
7. DataStore `com.launcher.pairing.identity_v1` (persistence)

### Schema version

| # | Check | Result |
|---|---|---|
| CHK001 | Every wire format has `schemaVersion` from first commit | **PASS** — все 6 contracts + DataStore |
| CHK002 | schemaVersion **read first** during deserialization | **PASS with note** (1) |
| CHK003 | Currently-supported `schemaVersion` const documented | **PASS with note** (2) |

### Backward compatibility

| # | Check | Result |
|---|---|---|
| CHK004 | Reads of previous versions ≥1 major release | **PASS** |
| CHK005 | Adding field OK, missing handled with defaults | **PASS** |
| CHK006 | Rename/remove requires versioned migration | **PASS** (no migrations yet) |
| CHK007 | Migration scoped style | **PASS** (no migrations yet; pattern from spec 005 inherited) |

### Forward compatibility

| # | Check | Result |
|---|---|---|
| CHK008 | Reading newer schemaVersions handled gracefully | **PASS with note** (3) |
| CHK009 | Unknown discriminator → Failure, not crash | **PASS** |

### Tests

| # | Check | Result |
|---|---|---|
| CHK010 | Roundtrip test per wire-format | **PASS** — все 6 contracts перечисляют roundtrip tests |
| CHK011 | Backward-compat test существует | **PASS** — `backwardCompat_v2_reads_v1` infrastructure ready |
| CHK012 | Test fixtures stored as files | **PASS with note** (4) |

### Persistence specifics

| # | Check | Result |
|---|---|---|
| CHK013 | DataStore keys namespaced | **PASS** (`com.launcher.pairing.identity_v1`) |
| CHK014 | SQLDelight migrations | **N/A** (Room появится в спеке 008) |
| CHK015 | Removed stored types: cleanup | **N/A** (no removals) |

### Deep-link / QR / exported config

| # | Check | Result |
|---|---|---|
| CHK016 | URL/QR/payload embeds schemaVersion | **PASS** |
| CHK017 | Truncated/corrupted → user-facing error | **PASS** |

### Contract folder

| # | Check | Result |
|---|---|---|
| CHK018 | Contract has version + policy + fixture links | **PASS with note** (4) |

---

## Informational notes (forward to /speckit.tasks)

### Note 1 (CHK002): schemaVersion read first

Сейчас в контрактах не явно прописано, что parser должен читать `schemaVersion` field **до** основного parse. worker-notify.md имеет `X-Schema-Version` header — правильный pattern.

**Action для /speckit.tasks**: в каждом `*WireFormat` object добавить функцию:
```kotlin
fun parseSchemaVersionOnly(json: JsonElement): Int? =
  (json as? JsonObject)?.get("schemaVersion")?.jsonPrimitive?.intOrNull
```
И использовать ДО полного парсинга в каждом adapter'е (FirebaseRemoteSyncBackend, LauncherFirebaseMessagingService, Worker).

### Note 2 (CHK003): schemaVersion как const

Сейчас `schemaVersion: Int = 1` — default value в data class'ах. Лучше отдельный const для single-source-of-truth.

**Action для /speckit.tasks**: в каждом `*WireFormat` object добавить:
```kotlin
object LinkBootstrapWireFormat {
  const val CURRENT_SCHEMA_VERSION = 1
  // serializer/deserializer functions...
}
```
И использовать `LinkBootstrapWireFormat.CURRENT_SCHEMA_VERSION` везде вместо магического `1`.

### Note 3 (CHK008): policy для encountering future schemaVersion

В каждом `*WireFormat` явно зафиксировать поведение при `data.schemaVersion > CURRENT_SCHEMA_VERSION`:

- **Firestore docs (pairing-token, link, state-bootstrap)**: drop с logging «document from future schema; ignored».
- **QR deep-link**: уже задокументировано — `QrParseResult.UnsupportedVersion` → UI «обновите приложение».
- **FCM payload**: drop с logging.
- **Worker notify**: возвращать 400 если body.schemaVersion > known.

**Action для /speckit.tasks**: добавить unit test'ы для каждого contract: `WireFormat.unknown_future_version_handled_gracefully`.

### Note 4 (CHK012, CHK018): fixture file paths

Контракты перечисляют test names но не explicit fixture file paths.

**Action для /speckit.tasks**: организовать fixtures в:
```
core/src/commonTest/resources/spec007-fixtures/
├── pairing-token-v1-valid.json
├── pairing-token-v1-claimed.json
├── pairing-token-v2-future.json       (для unknown_future_version test)
├── link-v1-valid.json
├── link-bootstrap-v1-with-fcm.json
├── link-bootstrap-v1-no-fcm.json      (GMS-absent case)
├── qr-deeplink-v1-valid.txt
├── qr-deeplink-invalid-scheme.txt
├── qr-deeplink-v2-future.txt
├── fcm-payload-config-changed.json
├── fcm-payload-command-issued.json
└── fcm-payload-unknown-type.json
```

Worker test fixtures — отдельная папка `push-worker/test/fixtures/`.

Pattern из спека 005/006: `legacy-spec00X-*.json` (удалены в cleanup 006). Спек 007 — `spec007-fixtures/` для consistency.

## Re-run trigger

Пере-запускается в `/speckit.analyze` (Step 5) с финальным состоянием артефактов (включая написанный код и fixture файлы).
