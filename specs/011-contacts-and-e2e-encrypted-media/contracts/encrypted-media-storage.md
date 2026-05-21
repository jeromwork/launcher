# Contract: Firebase Storage Layout `/links/{linkId}/private-media/{uuid}`

**Version:** 1.0.0 · **Status:** Stable from спек 011 · **Owner:** spec 011
**Storage**: Firebase Cloud Storage objects
**Test**: `EncryptedMediaStorageContractTest`, `Storage.privateMedia.*`

---

## Purpose

Описывает path layout, Storage Rules, и operational semantics для зашифрованных blob'ов в Firebase Storage. Сами блобы в формате [crypto-envelope.md](crypto-envelope.md).

Это первое использование Firebase Storage в проекте (см. plan.md §Dependency Impact). До спека 011 проект использовал только Firestore.

---

## Path layout

```
/links/{linkId}/private-media/{uuid}
```

- `{linkId}` — same as Firestore `/links/{linkId}/` (existing спек 007 namespace).
- `{uuid}` — UUIDv4 (same value as in `private:<uuid>` `iconId`).
- Object content — CBOR-serialized `EncryptedEnvelope` ([crypto-envelope.md](crypto-envelope.md)).

**Object metadata** (Firebase Storage object metadata, not envelope metadata):
- `Content-Type: application/cbor`
- Custom metadata: NONE (никаких leaked hints о содержимом — uuid и так namespace-coupled).

---

## Storage Rules

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /links/{linkId}/private-media/{uuid} {
      function isLinkMember() {
        let link = firestore.get(/databases/(default)/documents/links/$(linkId));
        return request.auth != null && (
          link.data.adminId == request.auth.uid ||
          link.data.managedDeviceFirebaseUid == request.auth.uid
        );
      }

      function isUnderSizeCap() {
        return request.resource.size < 500 * 1024;  // 500 KB cap (FR-cap)
      }

      allow read:   if isLinkMember();
      allow create: if isLinkMember() && isUnderSizeCap();
      allow update: if false;  // blobs are immutable
      allow delete: if isLinkMember();
    }

    // Catch-all: deny everything else (defense-in-depth)
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

**Rationale:**
- **`isLinkMember()`** uses Firestore cross-service rule to check membership. Verified at [Firebase docs (cross-service rules)](https://firebase.google.com/docs/rules/rules-and-auth#cross-service_rules).
- **`isUnderSizeCap()`** enforces 500 KB per blob — admin-side compression target.
- **`allow update: if false`** — blobs are immutable; modification = new uuid.
- **Catch-all deny** — defense-in-depth; no other Storage paths exist in this project (yet).

**Cross-service note**: cross-service rules require:
- Firestore and Storage in **same project** (yes, our case).
- `firestore.get()` adds ~50ms latency per call (acceptable for our usage — Storage I/O dwarfs this).
- Rules cache per request (single get() called once even if referenced in multiple conditions).

---

## Reference counting and housekeeping

Spec.md FR-030..034 + Clarifications C-7 define когда blob удаляется. Implementation в [data-model.md §3 BlobReferenceLedger.sq](../data-model.md). Это **client-side** (admin device) tracking, not server-side.

**Algorithm summary** (см. detailed in plan.md §Architecture and data-model.md §4):
1. На каждый push `/config` admin'a — recompute references from current + history snapshots.
2. Blob с refCount = 0 + 24h since last reference — best-effort delete via WorkManager.
3. Background reconciler (24h cadence) — full scan: enumerate Storage objects via `EncryptedMediaStorage.list(linkId)`, compare with ledger, prune orphans.
4. On revoke (`спек 007 FR-033`) — recursive delete via `LinkRegistry.revoke()`. Этот спек **расширяет** existing logic with Storage path enumeration.

**Storage path enumeration on revoke**:
```kotlin
// In LinkRegistry.revoke(linkId):
// existing logic deletes Firestore subcollections
// NEW (spec 011):
encryptedMediaStorage.list(linkId).forEach { uuid ->
    encryptedMediaStorage.delete(linkId, uuid)
}
// also delete /devices/* and /deviceOwnership/* Firestore subcollections
```

**Note on `list()`**: Firebase Storage list operation requires `storage.objects.list` permission. Storage Rules above grant it implicitly via `read` permission on the path. Spark plan allows list operations free (counts toward `Operations/day` budget — 50k/day Spark default).

---

## Operational characteristics

| Aspect | Value | Source |
|---|---|---|
| Max blob size | 500 KB | FR-cap (admin compresses JPEG before encrypt) |
| Expected avg blob size | 200 KB | research.md §4 |
| Expected blobs per pair | ≤ 80 | spec.md §Scale/Scope |
| Spark plan storage limit | 5 GB total | Firebase Spark |
| Spark plan download limit | 1 GB/day | Firebase Spark |
| Spark plan write limit | 20K writes/day | Firebase Spark |
| Server-roadmap trigger | storage > 4 GB OR download > 800 MB/day | `SRV-MEDIA-001` (see [server-roadmap.md](../../../docs/dev/server-roadmap.md)) |
| Listing operation cost | 1 op per call (no per-item cost) | Firebase Storage SDK |
| Object deletion cost | 1 op per call | Firebase Storage SDK |

---

## Operations API (Kotlin port)

The `EncryptedMediaStorage` port (см. data-model.md §2):

```kotlin
interface EncryptedMediaStorage {
    suspend fun upload(linkId: LinkId, uuid: Uuid, envelope: EncryptedEnvelope)
    suspend fun download(linkId: LinkId, uuid: Uuid): EncryptedEnvelope
    suspend fun delete(linkId: LinkId, uuid: Uuid)
    suspend fun exists(linkId: LinkId, uuid: Uuid): Boolean
    suspend fun list(linkId: LinkId): List<Uuid>
}
```

**Error handling**:
- Network failure → `CryptoError.StorageFailure(IOException)`.
- Permission denied (Storage Rules block) → `CryptoError.StorageFailure(SecurityException)`.
- Blob 404 → `CryptoError.BlobMissing(uuid)` (specific case for FR-024).
- Size cap exceeded → `CryptoError.StorageFailure(SizeCapException)` — admin should compress further.
- Quota exceeded — `CryptoError.StorageFailure(QuotaExceededException)` — UI shows "Storage full, see admin".

---

## Tests

| Test | What | Phase |
|---|---|---|
| `upload_download_roundtrip` | Upload envelope → download → deep-equal | 5 |
| `upload_sizeCap` | Upload 501 KB blob → SizeCapException | 5 |
| `download_blobMissing` | Download non-existing uuid → BlobMissing | 5 |
| `delete_idempotent` | Delete existing → success; delete non-existing → success (no error) | 5 |
| `exists_check` | exists() returns true for uploaded, false for deleted | 5 |
| `list_perLink` | list(linkA) returns only linkA's uuids, not linkB's | 5 |
| `Security.privateMedia.read.member_OK` | Admin reads Managed's blob → allowed | 5 |
| `Security.privateMedia.read.foreign_DENIED` | Non-member uid → PERMISSION_DENIED | 5 |
| `Security.privateMedia.create.sizeCap_DENIED` | 501 KB upload → PERMISSION_DENIED (rules level) | 5 |
| `Security.privateMedia.update.always_DENIED` | Try update → PERMISSION_DENIED (immutability) | 5 |
| `Security.privateMedia.delete.foreign_DENIED` | Non-member delete → PERMISSION_DENIED | 5 |
| `revokeRecursive_clearsStorage` | Revoke link → /links/{linkId}/private-media/* deleted | 5 |

---

## Backward compatibility policy

- Path layout `/links/{linkId}/private-media/{uuid}` — **immutable**. Changing = breaking change for all existing blobs. Treated as one-way door.
- Object content format = CBOR envelope (см. [crypto-envelope.md](crypto-envelope.md)) — schemaVersion + cipherSuiteId provide forward-compat.
- Storage Rules — modifications must maintain or strengthen permissions; cannot weaken (e.g. allow non-members to read) without major bump.

---

<!-- novice summary -->

## TL;DR (простым языком)

**Что в этом файле.** Описание того, **где именно** зашифрованные файлы лежат в Firebase Storage и **кто может их трогать**.

**Расположение:** `/links/{linkId}/private-media/{uuid}` — папка под каждый пары, в ней зашифрованные файлы по UUID-именам. Без любых hint'ов, что это такое — даже название файла = просто рандомный UUID.

**Security Rules (правила доступа):**
- **Читать и писать может только пара** (admin + Managed этого link'a). Чужие uid → отказ.
- **Размер файла ≤ 500 KB** — иначе отказ (превентим случайные мегабайтные фото).
- **Файлы immutable** — нельзя перезаписать, можно только создать новый под другим UUID.
- **Все остальные пути в Storage запрещены** — defense-in-depth (если кто-то ошибётся в коде и попытается записать в `/some/other/path`, правила его остановят).

**Лимиты Firebase Spark (бесплатный план):**
- 5 GB storage всего.
- 1 GB downloads в день.
- 20K writes в день.
- При средних 200 KB на blob, 80 blob'ов на пару = ≈ 250-500 пар до превышения. После — миграция на платный Blaze или на собственный server (записано в server-roadmap как `SRV-MEDIA-001`).

**Удаление файлов (housekeeping):**
- Реализация на admin device (локальный SQLite-счётчик ссылок).
- Когда контакт удалён + 24 часа прошло → blob удаляется через WorkManager.
- Когда revoke link → весь `/links/{linkId}/private-media/*` сносится сразу.
- Раз в день — «уборка» (scan Storage vs local ledger, удаление сирот).

**12 тестов** проверяют roundtrip, лимиты, immutability, и Security Rules.
