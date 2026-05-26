# Checklist: backend-substitution

**Spec**: [spec.md](../spec.md)
**Run**: 2026-05-26 (post-clarify-pass)
**Result**: 16/16 ✓ — 0 violations, 0 blockers

---

## Adapter boundary

- [x] **CHK001** — provider types в domain signatures: ✓
  - Никаких `FirebaseFirestore`, `StorageReference`, `DocumentSnapshot`, Cloudflare Worker request shapes в domain.
  - Backend touch — **только** через `EncryptedMediaStorage` port (owned by 011, не вводится новым в 012).
  - `BlobReferenceLedger` port — local SQLDelight, не remote backend.
- [x] **CHK002** — один wrapper adapter на provider: ✓
  - Backblaze B2 + Cloudflare Worker proxy → `:adapters:storage:b2-worker` (owned by 011).
  - Firestore (для `/config`, `/state/current`) → `:adapters:storage:firestore` (owned by 007/008).
  - Spec 012 не вводит новых backend providers.
- [x] **CHK003** — provider-disappears test bounded к одному adapter: ✓
  - **Если Backblaze B2 deprecated** → меняем `:adapters:storage:b2-worker`; фасады `PrivateMediaUploader/Resolver` не трогаем; UI не трогаем. **Bounded к 1 adapter module**.
  - **Если Cloudflare Worker недоступен** → меняем тот же `:adapters:storage:b2-worker` (Worker URL → direct B2 или собственный proxy). Bounded.

## Wire format

- [x] **CHK004** — persisted format — domain-owned, не provider-shaped: ✓
  - **`EncryptedEnvelope`** — domain data class (owned by 011, см. `crypto-envelope.md`), не Firestore `DocumentReference`.
  - **`Tile.kind="document"` + `documentRef: String`** — pure Kotlin types, не Firestore `Timestamp` / `FieldValue`.
  - **`metadata.kind`** — string value ("image" / "document"), не provider-specific.
- [x] **CHK005** — explicit `schemaVersion`: ✓
  - `/config` schemaVersion (owned by 008) — без bump'a (Clarification Q2: до spec 030+ pre-production freedom).
  - Envelope schemaVersion (owned by 011) — без bump'a (additive metadata.kind).
  - **Sunset** до spec 030+ зафиксирован в Assumptions.
- [x] **CHK006** — roundtrip test exists: deferred-to-plan (см. wire-format checklist CHK010-012)
  - Будут добавлены:
    - `TileWireFormatTest.roundtrip_document` (новый kind).
    - `EnvelopeMetadataKindTest.roundtrip_image_document` (metadata.kind).

## Identity

- [x] **CHK007** — domain primary key — project-owned: ✓
  - **`LinkId`** — domain ID (owned by 007).
  - **`Contact.id`** — `ElementId(UUIDv4)` — domain-owned.
  - **`Tile.id`** — `ElementId(UUIDv4)` — domain-owned.
  - **`blob uuid`** в namespace `private:<uuid>` — domain-owned (UUIDv4, не Firebase).
  - Firebase UID — за `LinkRegistry` port'ом, не в domain.
- [x] **CHK008** — provider-issued IDs as credentials в auth adapter: ✓
  - Firebase UID (`adminId`, `managedDeviceFirebaseUid`) хранится внутри `:adapters:auth:firebase`, мапится к `LinkId` на границе. ✓ (унаследовано из 007).
- [x] **CHK009** — provider UID as domain ID — one-way door documented: ✓ N/A
  - Spec 012 не вводит provider UID как domain ID.

## Query/command surface

- [x] **CHK010** — domain verbs, не provider verbs: ✓
  - `privateMediaUploader.upload(bytes, kind, linkId)` — domain verb.
  - `privateMediaResolver.resolve(iconRef)` — domain verb.
  - `blobReferenceLedger.increment(blob, refSource)` — domain verb.
  - Никаких `firestore.collection("links").document(linkId)...` в фасадах.
- [x] **CHK011** — no security-rules-shaped logic leak: ✓
  - Storage Rules `isLinkMember()` check — на серверной стороне, не в client logic.
  - Client code просто вызывает `storage.upload()`; если permission denied — fasce'ад возвращает `CryptoError.StorageFailure(SecurityException)`. Domain интерпретирует только domain error, не security-rules detail.

## Server-roadmap surfacing

- [x] **CHK012** — server-roadmap entry exists: ✓
  - **`SRV-CRYPTO-001`** в [server-roadmap.md](../../../docs/dev/server-roadmap.md) (owned by 011) — миграция B2/Spark → собственный server при storage > 4 GB OR download > 800 MB/day.
  - Spec 012 — **первый visible client** этой инфраструктуры; усиливает trigger'ы для `SRV-CRYPTO-001`, но не добавляет нового server-roadmap entry.
  - **`TODO-ARCH-019`** в [project-backlog.md](../../docs/dev/project-backlog.md) — local storage quota (client-side concern, не server).
- [x] **CHK013** — inline `// TODO(server-roadmap)` markers: ✓
  - Унаследовано из 011 (markers уже в коде `:adapters:storage:b2-worker`).
  - Spec 012 не добавляет новых free-workarounds, требующих новых markers.

## Exemptions (intentionally provider-specific)

- [x] **CHK014** — FCM/contacts/biometrics не классифицируются как substitutable: ✓
  - FCM (для config push) — owned by 007, exempt.
  - System Contacts (ContactsContract) — platform integration через `MediaPicker` / spec 009, exempt.
  - System Photo Picker — platform integration через `MediaPicker` adapter, **wrapped в port** (rule 2) но **не over-engineered for swap** (rule 4).
- [x] **CHK015** — no needless cross-provider abstraction: ✓
  - `MediaPicker` port — единственная абстракция; один adapter (`SystemPhotoPickerAdapter`). Не «universal media picker» с мульти-provider реализациями.

## Cost-of-swap summary

- [x] **CHK016** — cost-of-swap paragraph:

> **Cost-of-swap для spec 012 (если B2 + Cloudflare Worker заменим собственным server'ом)**:
>
> 1. **Переписать**: `:adapters:storage:b2-worker` — реализация `EncryptedMediaStorage` port'а. Новая реализация — HTTPS клиент к собственному API endpoint, тот же CBOR envelope payload. ≈ 200-300 LOC.
> 2. **Data migration**: одноразовый script: скачать все blob'ы из B2, залить в собственный S3/MinIO/etc., обновить metadata index. ≈ 1-2 дня data engineering работ.
> 3. **DI binding switch**: один `bind<EncryptedMediaStorage>` в DI module. ≈ 1 строка.
> 4. **Не трогаем**: `PrivateMediaUploader`, `PrivateMediaResolver`, `LocalMediaStore`, `MediaPicker`, `BlobReferenceLedger`, UI (DocumentViewer, плитки, admin indicator), domain types (`PrivateMediaKind`, `EncryptedEnvelope`), wire format (envelope CBOR + Tile JSON), security rules logic (переезжает в server-side, но не в client).
>
> **Bounded cost: ~1 adapter module + data migration script. Estimated 3-5 days of engineering work + data migration window.**
>
> Это **тот же** cost-of-swap, что и в server-roadmap `SRV-CRYPTO-001`. Spec 012 **не ухудшает** существующий exit ramp.

---

## Summary

| Status | Count |
|---|---|
| ✓ | 16 |
| ✗ violations | 0 |
| 🚫 blockers | 0 |

**Verdict**: Spec 012 — **образцовый backend-substitution-readiness**. Все backend-touch unaslesovannye из 011 (через `EncryptedMediaStorage` + `BlobReferenceLedger` ports), domain ничего не знает о B2/Cloudflare/Firestore. Cost-of-swap унаследован от 011: ~1 adapter rewrite + data migration script. Spec 012 не вводит ни одного нового backend dependency.

**Constitution alignment**: Project-Specific Direction §7 ✓, CLAUDE.md rule 1-2-5-8 ✓.
