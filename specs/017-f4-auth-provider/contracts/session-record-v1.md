# Contract: SessionRecord wire format v1

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **Date**: 2026-06-18
**Type**: Local persistence wire format (EncryptedSharedPreferences).
**Source**: `core/domain/auth/internal/SessionRecord.kt`.
**Visibility**: **Internal к F-4**. Consumer'ы (F-5, S-2, S-8, etc.) НЕ видят этот type (clarification Q2).

---

## Purpose

`SessionRecord` — внутренний blob, который `EncryptedLocalSessionStore` записывает на диск для:
1. Восстановления identity после app restart (Session persistence, US 5).
2. Хранения refreshToken для token refresh flow (US 4).
3. Хранения provider-specific blob (Firebase JWT) — adapter-internal use.

## Format

JSON, serialized via `kotlinx.serialization.json`.

### Schema v1

```json
{
  "schemaVersion": 1,
  "stableId": "550e8400-e29b-41d4-a716-446655440000",
  "expiresAt": 1739456789000,
  "refreshToken": "1//04xxxxxxxxxxxxxxxxxx",
  "extra": {
    "firebase_jwt": "eyJhbGciOiJSUzI1NiIs..."
  }
}
```

### Fields

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `schemaVersion` | Int | No | Always 1 в v1. Bump при breaking change. **Read first** during deserialization. |
| `stableId` | String | No | UUID v4 (36 chars). = `AuthIdentity.stableId`. |
| `expiresAt` | Long? | Yes | Epoch milliseconds. Null = unknown/never (для adapter'ов без token concept). For Google: Firebase JWT expiry timestamp. |
| `refreshToken` | String? | Yes | Opaque refresh token. Used by adapter для refresh flow. **Never exposed** к consumer'ам. |
| `extra` | Map<String, String> | No | Adapter-specific blob. For Google: `extra["firebase_jwt"]` = current Firebase JWT. Domain не интерпретирует. |

## Storage

- **File**: `auth_session_v1.preferences.xml` в app sandbox SharedPreferences directory.
- **Key**: single key `session` → JSON string of `SessionRecord`.
- **Encryption**: `EncryptedSharedPreferences` через Jetpack Security `androidx.security:security-crypto`. Master AES-256-GCM key в Android Keystore TEE.
- **Backup**: **EXCLUDED** from `android:allowBackup` and device-transfer (per [research.md §R10](../research.md#r10-backup-exclusion-strategy)). См. `data_extraction_rules.xml`.

## Invariants

| Invariant | Verification |
|-----------|--------------|
| `schemaVersion` read first | Standard `kotlinx.serialization.json` pattern. |
| `stableId` UUID v4 format | Property test: round-trip `UUID.randomUUID().toString()` через JSON. |
| `expiresAt` epoch milliseconds, not seconds | Test: hardcode known epoch (e.g., 2026-01-01 = 1735689600000). |
| `extra` keys всегда lowercase + snake_case | Convention для consistency между adapter implementations. |
| Corrupted blob → `current()` returns null | `EncryptedLocalSessionStorePropertyTest.kt`: random byte flip → no crash. |

## Tests

### Required tests

- **Roundtrip test** (`SessionRecordRoundtripTest.kt`):
  ```kotlin
  val original = SessionRecord(schemaVersion = 1, stableId = "550e8400-e29b-41d4-a716-446655440000", expiresAt = ..., refreshToken = ..., extra = mapOf("firebase_jwt" to "..."))
  val json = Json.encodeToString(original)
  val decoded = Json.decodeFromString<SessionRecord>(json)
  assertEquals(original, decoded)
  ```

- **Backward-compat read test** (`SessionRecordBackwardCompatTest.kt`):
  ```kotlin
  val v1Fixture = readResource("auth-fixtures/session-record-v1.json")
  val decoded = Json.decodeFromString<SessionRecord>(v1Fixture)
  assertEquals(1, decoded.schemaVersion)
  assertEquals("550e8400-e29b-41d4-a716-446655440000", decoded.stableId)
  // ... assert other expected fields
  ```

- **Corrupted blob handling** (`EncryptedLocalSessionStorePropertyTest.kt`):
  ```kotlin
  propertyTest(1000.iterations()) { seed: Int ->
      val store = EncryptedLocalSessionStore(...)
      store.save(validSessionRecord())
      // Flip random byte в file
      corruptFileRandomly(seed)
      assertEquals(null, store.current())  // null, не throw
      // Verify warning logged
  }
  ```

### Test fixtures

Location: `core/commonTest/resources/auth-fixtures/`:
- `session-record-v1.json` — golden fixture для backward-compat reads. **Hardcoded UUID** (не `UUID.randomUUID()` — stable across runs).

## Future schema bumps

### v2 hypothetical (additive — adding field)

If, например, нужно добавить field `lastUsedAt: Long?`:

1. Bump `schemaVersion = 2`.
2. Add field with `@SerialName` + default `null`:
   ```kotlin
   @SerialName("last_used_at")
   val lastUsedAt: Long? = null,
   ```
3. Old v1 fixture reads correctly (missing field → null default). **No migrator needed** для additive changes.
4. Write `SessionRecordV2RoundtripTest` для new field.
5. Update `session-record-v1.md` (this contract) to v2 section.

### v2 hypothetical (breaking — renaming field)

If, например, переименовать `refreshToken` → `refresh_token`:

1. Bump `schemaVersion = 2`.
2. Write `SessionRecordMigrator.migrateV1ToV2(JsonObject): SessionRecord` function. Migrator reads `refreshToken` field, writes `refresh_token`.
3. `EncryptedLocalSessionStore.current()` detects schemaVersion, dispatches:
   ```kotlin
   val raw = Json.parseToJsonElement(blob).jsonObject
   val version = raw["schemaVersion"]?.jsonPrimitive?.int ?: 1
   return when (version) {
       1 -> migrator.migrateV1ToV2(raw).also { save(it) }  // write back в new format
       2 -> Json.decodeFromJsonElement<SessionRecord>(raw)
       else -> { AuthLog.sessionCorrupted(); null }  // forward-compat: unknown version → corrupted (per wire-format CHK008)
   }
   ```
4. Write `SessionRecordMigrationTest` covering all paths.
5. Document migration breaking change в commit message + Spec changes.

### Forward compat (newer version read by older app)

Per wire-format CHK008: if app reads SessionRecord с `schemaVersion > CURRENT_VERSION` → treat as corrupted (return null, log warning). NOT crash, NOT silent ignore.

This handles scenario: user installs newer app version, signs in, then downgrades к older app version. Older version sees v2 blob, treats как corrupted, user has to sign in заново. Acceptable.

## Related

- [contracts/identity-link-v1.md](identity-link-v1.md) — server-side wire format.
- [research.md §R5](../research.md#r5-wire-format-choice-json-vs-protobuf-vs-cbor-для-sessionrecord) — JSON choice justification.
- [research.md §R10](../research.md#r10-backup-exclusion-strategy) — backup exclusion.

## TL;DR для не-разработчика

`SessionRecord` — это **зашифрованный файл на телефоне**, который хранит:
- Наш UUID пользователя (для восстановления при следующем запуске).
- Refresh-токен Google (чтобы автоматически обновлять авторизацию каждый час).
- Текущий Firebase токен (чтобы делать запросы на сервер).

**Хранится** в формате JSON, зашифрованным AES-ключом, который лежит в защищённой аппаратной зоне Android (TEE).

**Исключён из автобэкапа** Google Drive — иначе токен утёк бы в облако пользователя.

**Версия формата = 1**. Если в будущем добавим поле (например, «последний раз использован») — это не сломает старые файлы (читаются с дефолтом null). Если переименуем поле — напишем «переводчик» (migrator) который читает старый формат и записывает в новый.

**Если файл повредился** (редкий случай, сбой Android) — приложение не падает, просто делает вид что пользователь не вошёл. Безопасно.

**Тесты**: что записал → прочитал → получил то же самое (round-trip), что старый формат всё ещё читается, что повреждённый файл не вызывает крэш.
