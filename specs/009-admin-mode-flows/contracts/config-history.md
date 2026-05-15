# Wire format: `/links/{linkId}/config/history/{autoId}`

**Source of truth**: this document.
**Used by**: spec 009 §FR-036..045 (history + restore), FR-045a (anti-spoofing rule).
**Schema version**: `snapshotSchemaVersion = 1` (first commit).
**Lifetime**: до 10 версий per `linkId`, FIFO ротация (FR-038). Удаляется вместе с `/links/{linkId}/` на revoke (spec 007 FR-033).
**Subcollection root**: NEW в спеке 009 — расширяет spec 008 `/links/{linkId}/config/current` тем, что выносит историю в отдельный subcollection (OUT-007 спека 008 → in scope 009).

---

## Document path

`/links/{linkId}/config/history/{autoId}` — Firestore subcollection с auto-generated document IDs. Каждый документ — immutable snapshot предыдущей `/config/current` (записывается **до** очередного push'a, чтобы текущая версия всегда оставалась откатываемой).

## Field schema (envelope)

| Field | Type | Required | Server-set | Notes |
|---|---|---|---|---|
| `snapshotSchemaVersion` | `Int` | ✓ | ✗ | `1`. Версия envelope, **независимая** от `config.schemaVersion` внутри (FR-036, clarification C2). |
| `config` | `ConfigCurrent` | ✓ | ✗ | Полная копия предыдущего `/config/current` (включая его собственный `schemaVersion`, `serverUpdatedAt`, `lastWriterDeviceId`, `presetId`, `flows`, `contacts`, и опциональный `presetOverrides`). См. spec 008 `contracts/config.md` + `config-current-additions.md`. |
| `recordedAt` | `Long` | ✓ | ✗ | Epoch millis на момент write'a snapshot'a; ~совпадает с `config.serverUpdatedAt` предыдущей версии (FR-036). |
| `recordedFromDeviceId` | `String` | ✓ | ✗ | Firebase Auth `uid` устройства, инициировавшего push (FR-036). Security Rule FR-045a enforces `recordedFromDeviceId == request.auth.uid` (anti-spoofing). |

**Independence of versions**: `snapshotSchemaVersion` эволюционирует отдельно от `config.schemaVersion`. Envelope может стать v2 (новое поле `recordedReason`) при `config.schemaVersion = 1`, или наоборот (`config` bumped to 2, envelope остаётся 1). См. R-002 в research.md + FR-036.

---

## Example

```json
{
  "snapshotSchemaVersion": 1,
  "config": {
    "schemaVersion": 1,
    "serverUpdatedAt": {"_seconds": 1747166400, "_nanoseconds": 0},
    "lastWriterDeviceId": "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
    "presetId": "simple-launcher",
    "flows": [
      {"id": "f1111111-1111-4111-8111-111111111111", "title": "Главный", "slots": []}
    ],
    "contacts": [
      {"id": "c1111111-1111-4111-8111-111111111111", "displayName": "Маша", "phoneNumber": "+71234567890"}
    ]
  },
  "recordedAt": 1747166400000,
  "recordedFromDeviceId": "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a"
}
```

---

## Lifecycle

```text
Created    ── before each /config/current push by editor (FR-036):
   │            1. read current /config/current
   │            2. write snapshot of THAT into /config/history/{autoId}
   │            3. write new /config/current (optimistic concurrency)
   │            4. housekeeping: list all history → if ≥ 11, delete oldest (FR-038)
   │
   ├── Immutable — never updated after create.
   │
   ├── Read by editor (admin or Managed) to show «История» list (FR-039)
   │   and to power «Откатиться» action (FR-040..044).
   │
   └── Deleted:
        • by housekeeping (FIFO past retention 10), OR
        • by revoke cascade (spec 007 FR-033, recursive subtree delete).
```

---

## Versioning policy

- **Additive fields** в envelope (новые опциональные поля) — НЕ bump `snapshotSchemaVersion`.
- **Renaming / removing** fields → bump `snapshotSchemaVersion` 1 → 2 + lazy transformer в reader (`TODO-ARCH-015`).
- **Forward-compat (reader sees future version)** — при `snapshot.snapshotSchemaVersion > SUPPORTED_VERSION` reader MUST **fail closed**: вернуть `Failure.SnapshotTooNew` (FR-043), кнопка отката заблокирована с пояснением «История создана более новой версией приложения, обновитесь».
- **Backward-compat (reader sees older version)** — при `snapshot.snapshotSchemaVersion < SUPPORTED_VERSION`: применить transformer chain, когда тот появится (`TODO-ARCH-015`); до тех пор — drop snapshot с пояснением «История слишком старая для отката, обновите приложение и сделайте новый push».
- **First-read invariant**: `snapshotSchemaVersion` MUST be deserialized FIRST (wire-format checklist CHK002), до любых `config.*` полей.

`config.schemaVersion` внутри envelope валидируется по правилам spec 008 (`contracts/config.md` §Backward compatibility policy) — **независимо** от envelope.

---

## Retention policy

- **Limit**: 10 snapshots per `linkId` (FR-038).
- **Algorithm**: client-side housekeeping — после успешного push в `/config/current` клиент читает все snapshots в `/config/history/` (ordered by `recordedAt`), и если их ≥ 11, удаляет старейшие до остатка 10.
- **Migration path**: `// TODO(server-roadmap SRV-CONFIG-002): housekeeping должен стать server cron job. Сейчас — клиент при каждом push.`
- **Race condition note**: при одновременных push'ах с двух устройств housekeeping каждого может попытаться удалить overlapping snapshots — это benign (idempotent delete), не баг.

---

## Security Rules requirements

Расширяет `firestore.rules` спека 008:

```text
match /links/{linkId}/config/history/{snapshotId} {
  allow read:   if isAdmin(linkId) || isManaged(linkId);
  allow create: if (isAdmin(linkId) || isManaged(linkId))
                && request.resource.data.snapshotSchemaVersion is int
                && request.resource.data.recordedFromDeviceId == request.auth.uid;  // FR-045a anti-spoofing
  allow update: if false;                          // immutable
  allow delete: if isAdmin(linkId) || isManaged(linkId);  // housekeeping + revoke
}
```

- **Create**: by adminId OR managedDeviceFirebaseUid (FR-036 — равноправные editors); MUST set `recordedFromDeviceId == request.auth.uid` (FR-045a).
- **Read**: same (история отображается обоим сторонам).
- **Update**: forbidden (snapshot immutable).
- **Delete**: same (для client-side housekeeping FR-038 + revoke cascade).
- **Migration**: `// TODO(server-roadmap SRV-CONFIG-001): когда переедем на server-side history writes — заменить client-write rules на server-only.`

---

## Tests (commonTest + Firebase Emulator)

| Test | What it verifies | Phase |
|---|---|---|
| `ConfigSnapshotWireFormat.roundtrip_v1_minimal` | Write v1 минимальный envelope → read → assertEquals | 2 |
| `ConfigSnapshotWireFormat.roundtrip_v1_full` | Envelope с full ConfigCurrent inside → read → equal | 2 |
| `ConfigSnapshotWireFormat.forwardCompat_v99_failsClosed` | Synthetic snapshot с `snapshotSchemaVersion=99` → reader returns `Failure.SnapshotTooNew` (FR-043), не крашится | 2 |
| `ConfigSnapshotWireFormat.backwardCompat_additive_v2_on_v1_reader` | Synthetic v2 envelope с дополнительным `recordedReason` field → v1 reader ignores unknown field, читает остальное | 2 |
| `ConfigHistoryHousekeeping.eleven_writes_keep_ten` | 11 sequential push'ей → only 10 latest snapshots remain (FR-038) | 4 |
| `Security.history.admin_can_write_self_uid` | adminId создаёт snapshot с `recordedFromDeviceId == auth.uid` → OK (Emulator) | 5 |
| `Security.history.spoofed_deviceId_denied` | adminId пытается write с `recordedFromDeviceId == managedUid` → PERMISSION_DENIED (FR-045a) | 5 |
| `Security.history.managed_can_write_self_uid` | managedDeviceFirebaseUid создаёт snapshot с self uid → OK | 5 |
| `Security.history.foreign_uid_denied` | Чужой uid → PERMISSION_DENIED | 5 |
| `Security.history.update_forbidden` | Любая попытка update существующего snapshot → PERMISSION_DENIED | 5 |

**Fixtures** (`commonTest/resources/wire-format/`):
- `config-snapshot-v1-minimal.json`
- `config-snapshot-v1-full.json`
- `config-snapshot-v99-synthetic.json` (forward-compat fail-closed test)
- `config-snapshot-v2-synthetic-additive.json` (future additive field, читается v1 reader'ом)

---

## Backward compatibility policy

- Spec 009 ships с `snapshotSchemaVersion = 1`. Все будущие additions — additive (новые опциональные поля без bump).
- Rename/remove → bump 1 → 2 + transformer в Phase 0 следующего спека (TODO-ARCH-015).
- Envelope и embedded `config` версионируются **независимо** (FR-036 / R-002): bump одного не требует bump другого.

**TODO comment in code** (`ConfigSnapshot.kt`):
> При расширении envelope — добавлять опциональные поля **без** изменения `snapshotSchemaVersion`. Rename/remove — bump 1 → 2 + transformer в Phase 0 нового спека (TODO-ARCH-015). `config.schemaVersion` внутри эволюционирует отдельно по правилам spec 008.

---

<!-- novice summary -->

## TL;DR

«История раскладок бабушкиного телефона на сервере». Перед каждой записью новой раскладки в `/config/current` мы сохраняем копию старой в отдельную «папку» истории — до 10 последних версий. Можно открыть любую старую и нажать «Откатиться» — это станет новой текущей раскладкой (а текущая тоже уйдёт в историю). У envelope-конверта своя версия схемы, отдельная от версии самой раскладки внутри — чтобы можно было независимо менять формат записи истории и формат самой раскладки. Security-правило проверяет, что устройство, записавшее snapshot, не может выдать себя за другое (поле «кто записал» обязано совпадать с Firebase Auth uid). Если читатель видит snapshot из будущей версии формата — кнопка отката блокируется с пояснением «обновите приложение».
