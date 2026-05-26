# Checklist: wire-format

**Spec**: [spec.md](../spec.md)
**Run**: 2026-05-26 (post-clarify-pass)
**Result**: 13/18 ✓ + 3 deferred-to-plan + 2 explicit-deviation-with-rationale

---

## Scope: which wire formats does 012 touch?

1. **`/config` JSON** (owned by spec 008) — добавляет новый sealed variant `Tile(kind="document", documentRef, label)`.
2. **`EncryptedEnvelope` CBOR** (owned by spec 011) — добавляет первое реальное значение `metadata.kind = "image" | "document"`.
3. **`/state/current` JSON** (owned by spec 007 + 008) — впервые эмитит `PartialReason.MediaDecryptFailed` (enum value уже зарезервирован, не новый).
4. **`LocalMediaStore`** (`Context.filesDir/private-media/<uuid>`) — **не wire format** (не покидает устройство, file content = расшифрованные bytes исходного формата фото/документа без метаданных). Excluded из этого checklist'а.

---

## Schema version

- [x] **CHK001** — `schemaVersion` present:
  - `/config` schemaVersion = 1 (owned by 008). ✓
  - Envelope schemaVersion = 1 (owned by 011). ✓
  - `/state/current` schemaVersion = 1 (owned by 007/008). ✓
- [x] **CHK002** — schemaVersion прочитан первым: ✓ (унаследовано из 008/011).
- [x] **CHK003** — single source of truth для schemaVersion constant: ✓ (унаследовано).

## Backward compatibility

- [ ] **CHK004** — reads of previous schema versions remain possible
  - **Status**: ⚠️ explicit-deviation-with-rationale.
  - **Deviation**: Clarification Q2 явно фиксирует, что **до spec 030+** программа не в production. Backward compat constraint для уже-развёрнутых клиентов **не применяется**. После spec 030 эта свобода истекает.
  - **Rationale**: rule 5 защищает уже-в-проде контракты. Никакого пользователя на старой версии нет — нет от чего защищать.
  - **Действие**: при первом public release добавить snapshot текущего wire format'а как «v1 frozen» + reader-migration.
- [x] **CHK005** — adding a field with defaults: ✓
  - `Tile(kind="document")` — новый sealed variant. Старый reader (если случайно встретится in-progress) → `PartialReason.UnknownSlotKind` (предусмотрено в state-applied.md:67).
  - `metadata.kind` — optional CBOR map entry, отсутствие = nil.
- [x] **CHK006** — renaming/removing требует migration first: N/A (ничего не переименовывается).
- [x] **CHK007** — migration scoped: N/A (нет миграций).

## Forward compatibility

- [x] **CHK008** — reading newer schemaVersion graceful: ✓ (унаследовано из 011 — `CipherSuiteUnsupported` для envelope, `unknown_slot_kind` для Tile).
- [x] **CHK009** — unknown discriminator → Failure: ✓
  - Unknown `Tile.kind` → `PartialReason.UnknownSlotKind` (state-applied.md:67).
  - Unknown `metadata.kind` (например, `"audio"` от future spec) → envelope в 011 нейтрален к metadata content, читается как opaque map; client решает.

## Tests

- [x] **CHK010** — roundtrip test per wire-format type: ✓ (resolved in plan-phase)
  - `TileWireFormatTest.roundtrip_document` — определён в [contracts/tile-document-kind.md](../contracts/tile-document-kind.md) §Tests.
  - `EnvelopeMetadataKindTest.roundtrip_image_document` — определён в [contracts/metadata-kind-registry.md](../contracts/metadata-kind-registry.md) §Tests.
- [x] **CHK011** — backward-compat test: ✓
  - `TileWireFormatTest.forward_compat_unknown_kind` — определён в contracts/tile-document-kind.md (graceful emit `PartialReason.UnknownSlotKind`).
  - `EnvelopeMetadataKindTest.forward_compat_unknown_kind` — graceful opaque bytes.
- [x] **CHK012** — fixtures as files: ✓
  - Fixtures listed в contracts:
    - `commonTest/resources/wire-format/tile-v1-document.json`
    - `commonTest/resources/wire-format/tile-v1-mixed-kinds.json`
    - `commonTest/resources/wire-format/tile-v1-unknown-kind.json`

## Persistence specifics

- [x] **CHK013** — SharedPreferences/DataStore namespacing: N/A (012 не вводит prefs).
- [x] **CHK014** — SQLDelight migration tests: N/A (`BlobReferenceLedger` SQLDelight tables — owned by 011, без изменений в 012).
- [x] **CHK015** — removed types cleanup: N/A.

## Deep-link / QR / exported config

- [x] **CHK016** — deep-link/QR: N/A (012 не вводит новых deep-link'ов или QR payload'ов).
- [x] **CHK017** — corrupted payload не падает: ✓ (унаследовано из 011 — `MalformedEnvelope` для CBOR parse failure).

## Contract folder

- [x] **CHK018** — contracts folder: ✓ (resolved in plan-phase)
  - Создан [`contracts/`](../contracts/) с 3 файлами:
    - [`tile-document-kind.md`](../contracts/tile-document-kind.md) — новый sealed variant.
    - [`metadata-kind-registry.md`](../contracts/metadata-kind-registry.md) — реестр значений `metadata.kind`.
    - [`local-media-store-layout.md`](../contracts/local-media-store-layout.md) — local layout + backup exclusion.

---

## Summary

| Status | Count |
|---|---|
| ✓ | 17 (post-plan-phase) |
| deferred-to-plan | 0 |
| explicit-deviation-with-rationale | 1 (CHK004 — pre-production свобода до spec 030+) |
| ✗ violations | 0 |

**Re-run 2026-05-26 (post-plan-phase)**: все 4 deferred items resolved через contract files в [`contracts/`](../contracts/). Roundtrip + forward-compat tests определены в каждом contract'е.

**Verdict**: spec 012 **переиспользует** существующие wire format'ы 008/011, добавляя один новый sealed variant + один новый metadata key. Оба расширения — additive, без bump'a schemaVersion. Pre-production deviation (CHK004) explicitly закреплён в Clarification Q2 + Assumptions.

**Sunset reminder**: со spec 030 (первый public release) — backward-compat constraint включается. Все additive-без-bump'a решения 012 должны быть зафиксированы snapshot'ом «v1 frozen» с reader-migration policy.

**Constitution alignment**: CLAUDE.md rule 5 ✓ (через Q2 deviation rationale); Article VII §3 — unchanged.
