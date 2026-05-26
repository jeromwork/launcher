# Contract: `LocalMediaStore` Layout — `Context.filesDir/private-media/`

**Version:** 1.0.0 · **Status:** Draft (spec 012)
**Owner:** spec 012 (new)
**Tests:** `LocalMediaStoreIntegrationTest`, `LocalMediaStoreBackupExclusionTest`

---

## Scope

Это **НЕ wire format**. Файлы не покидают устройство; не парсятся другим приложением; не versioned per схеме CLAUDE.md rule 5. Контракт документирует **persistent local layout** для:
1. Тестирования (integration tests verify location + cleanup on uninstall).
2. **Mandatory backup exclusion** (`data_extraction_rules.xml`) — без этого утечка PII через Google Drive.
3. Sanity: где будущему разработчику искать файлы.

---

## Path layout

```text
Context.filesDir/
└── private-media/
    ├── f1111111-2222-4333-9444-555555555555      (no extension)
    ├── 22222222-3333-4444-5555-666666666666
    ├── 33333333-4444-5555-6666-777777777777
    └── ...
```

### Filename convention

- **Filename** = `uuid` (lowercase UUIDv4, matches namespace `private:<uuid>` from [icon-id-namespace.md](../../006-provider-capabilities-and-health/contracts/icon-id-namespace.md)).
- **No extension** — content-type определяется через `metadata.kind` envelope'а на момент decrypt'a; после записи в local store расширение не нужно (Coil или Compose Image декодит по magic bytes).
- **No subdirectories per linkId** — uuid сам по себе глобально уникален.

### Content

Raw decrypted bytes ровно того формата, в котором admin загрузил (typically JPEG; future WebP/HEIC). НЕ перекодируется.

### Permissions

`Context.MODE_PRIVATE` (default for `filesDir`). Other apps не имеют доступа без root.

---

## Lifecycle

```text
Created       ── PrivateMediaResolver.resolve("private:<uuid>") at first show
                 (download → decrypt → LocalMediaStore.write)
   │
   ├── Read on every subsequent show (≤ 100 мс)
   │
   ├── Possibly deleted when:
   │     • User clears app data (manual Settings → Storage → Clear)
   │     • App uninstall (Android removes filesDir automatically)
   │     • LocalMediaStore.delete(uuid) called по reconciliation (spec 012 НЕ делает это; out-of-scope для 012)
   │
   └── NOT deleted on revoke (Clarification Q5 — "stop future, not wipe past")
```

### Spec 012 explicit non-actions

- **NO LRU eviction** (это не cache).
- **NO TTL** (файлы persistent).
- **NO size cap** (out of scope, see [TODO-ARCH-019](../../../docs/dev/project-backlog.md)).
- **NO wipe on revoke** (см. spec.md Out-of-Scope).
- **NO encryption-at-rest на файлах** (согласуется с WhatsApp/Telegram baseline; threat model 012).

---

## 🚨 Mandatory: backup exclusion

### Why

Если `private-media/` попадёт в Google Drive backup или device-transfer — расшифрованные фото паспортов / медкарт / СНИЛС бабушки **окажутся в её Google account** (или в device-transfer destination устройства).

Это **критическая утечка PII** в иначе тщательно защищённой системе. E2E шифрование на канале сервер ↔ клиент станет бесполезным, если конечная точка ↔ Google backup незащищена.

### Mitigation — `res/xml/data_extraction_rules.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <!-- Exclude расшифрованные private media from Auto Backup / Google Drive -->
        <exclude domain="file" path="private-media/" />
    </cloud-backup>

    <device-transfer>
        <!-- Exclude from device-to-device transfer (e.g. Smart Switch) -->
        <exclude domain="file" path="private-media/" />
    </device-transfer>
</data-extraction-rules>
```

В `AndroidManifest.xml`:

```xml
<application
    ...
    android:dataExtractionRules="@xml/data_extraction_rules"
    ...>
```

### Verification test (mandatory)

```kotlin
@RunWith(AndroidJUnit4::class)
class LocalMediaStoreBackupExclusionTest {

    @Test
    fun private_media_dir_excluded_from_backup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resourceId = context.resources.getIdentifier("data_extraction_rules", "xml", context.packageName)
        assertTrue("data_extraction_rules.xml resource missing!", resourceId != 0)

        val parser = context.resources.getXml(resourceId)
        val xmlString = parser.serializeToString()  // helper

        assertTrue(
            "Backup exclusion missing for private-media/ directory!",
            xmlString.contains("""path="private-media/"""")
        )
        assertTrue(
            "device-transfer exclusion missing for private-media/!",
            xmlString.contains("device-transfer") && xmlString.contains("""path="private-media/"""")
        )
    }
}
```

### Verification — manual UAT

1. Включить Auto Backup на test device.
2. Положить тестовый файл в `LocalMediaStore.write("00000000-1111-2222-3333-444444444444", "test".toByteArray())`.
3. Trigger backup (`adb shell bmgr backupnow <package>`).
4. Verify через `bmgr list transports` + Google Drive console — папка `private-media/` отсутствует в backup snapshot'е.

---

## Tests (androidTest)

### `LocalMediaStoreIntegrationTest`

```kotlin
@Test
fun write_creates_file_in_private_media_dir() {
    val file = store.write(uuid, jpegBytes)
    assertTrue(file.exists())
    assertTrue(file.absolutePath.contains("/files/private-media/"))
    assertEquals(jpegBytes.size, file.sizeBytes.toInt())
}

@Test
fun read_returns_null_for_missing_uuid() {
    val result = store.read("00000000-0000-0000-0000-000000000000")
    assertNull(result)
}

@Test
fun delete_is_idempotent() {
    store.delete(uuid)  // no error даже если файла нет
    store.write(uuid, bytes)
    store.delete(uuid)
    assertFalse(store.exists(uuid))
}

@Test
fun persists_through_activity_recreation() {
    store.write(uuid, jpegBytes)
    // simulate Activity recreation (rotation)
    val newStore = createStore()  // fresh instance, same Context
    assertTrue(newStore.exists(uuid))
}

@Test
fun cleared_on_app_uninstall() {
    // documented behavior, can't test programmatically — manual UAT
}

@Test
fun total_size_aggregates_all_files() {
    store.write(uuid1, ByteArray(100))
    store.write(uuid2, ByteArray(200))
    assertEquals(300L, store.totalSizeBytes())
}
```

---

## Cross-references

- [`LocalMediaStore` port](../data-model.md#2-new-ports-commonmain) — domain interface.
- [`PrivateMediaResolver`](../data-model.md#privatemediasolver-facade--iconstorage-namespace-dispatch) — main caller.
- [spec.md R5 + risks R1](../plan.md) — mandatory backup exclusion.
- [TODO-ARCH-019](../../../docs/dev/project-backlog.md) — future local quota / wipe-on-revoke.
- [Android Auto Backup documentation](https://developer.android.com/identity/data/autobackup) — `data_extraction_rules.xml` API reference.

---

## TL;DR (для новичка)

**Что**: папка `private-media/` внутри app-private storage телефона, где хранятся уже расшифрованные фото контактов и документов. Это **не временный кэш** — это **постоянное место**, файлы там лежат до удаления приложения.

**Зачем не зашифровано**: модель «как у WhatsApp/Telegram». Сервер не видит — это главное. На своём телефоне у бабушки — файлы открытые, потому что (а) бабушка имеет доступ к своему телефону (это её данные), (б) шифровать локально без преимущества — overhead без выгоды в нашей threat model.

**Что критически важно**: **MUST исключить из Google Drive backup** через `data_extraction_rules.xml`. Иначе расшифрованный паспорт уйдёт в Google account автоматически — катастрофическая утечка. Тест проверяет это в CI.

**Не путать с**: blob в Backblaze B2 (там зашифровано, передаётся, видимо серверу) vs локальный файл (расшифровано, лежит на телефоне).
