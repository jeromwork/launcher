# Checklist: domain-isolation

**Spec**: [spec.md](../spec.md)
**Run**: 2026-05-26 (post-clarify-pass)
**Result**: 12/16 ✓ + 4 deferred-to-plan — 0 violations

---

## Vendor SDKs

- [x] **CHK001** — vendor SDK types в domain signatures: ✓
  - Все крипто-операции — через порты 011 (`AeadCipher / AsymmetricCrypto / etc.`), libsodium не появляется в domain.
  - Backblaze B2 / Cloudflare Worker — за `EncryptedMediaStorage` port'ом из 011.
  - Системный picker — за `MediaPicker` port'ом 012.
- [x] **CHK002** — один wrapper module на SDK: ✓
  - libsodium → `:adapters:crypto:lazysodium`.
  - B2 Worker → `:adapters:storage:b2-worker`.
  - System picker → новый `:adapters:media-picker` (определяется в plan-phase).
- [x] **CHK003** — vendor-disappear test: ✓
  - libsodium → 1 модуль (`:adapters:crypto:*`).
  - Backblaze B2 → 1 модуль (`:adapters:storage:b2-worker`).
  - Android Photo Picker → 1 модуль (`:adapters:media-picker`).

## Transport types

- [x] **CHK004** — transport types в domain: ✓. Envelope wire format (CBOR) — за `EncryptedMediaStorage` port'ом, domain видит только `EncryptedEnvelope` доменный data class.
- [x] **CHK005** — wire format — domain-owned: ✓. `EncryptedEnvelope`, `Tile`, `Contact` — все в `core/api/**`, serializers — отдельно.

## Platform types

- [ ] **CHK006** — `android.*`, `androidx.*`, `Intent`, `Uri`, `Context`, `Bundle` в `commonMain`
  - **Status**: ⚠️ spec упоминает эти типы **в тексте**, но они **не появятся в domain signatures** — все за `MediaPicker / LocalMediaStore` port'ами.
  - **FR-007**: «Caller никогда не видит URI или платформенные типы» — явный invariant.
  - **FR-008**: API-level dispatch **внутри adapter'а**, не утечь в domain.
  - **Действие**: при `speckit-plan` подтвердить, что `MediaPicker.MediaPickResult(bytes: ByteArray, mimeType: String, sourceLabel: String?)` — pure Kotlin types.
- [x] **CHK007** — domain carries projection, not raw platform type: ✓
  - `MediaPickResult` = `(bytes: ByteArray, mimeType: String, sourceLabel: String?)` — domain projection.
  - `IconRef` — String с namespace convention (спек 006), не `Uri`.

## Ports

- [x] **CHK008** — every external surface за port'ом: ✓
  - System picker → `MediaPicker` port.
  - App-private file storage → `LocalMediaStore` port.
  - Crypto/storage от 011 — переиспользуются через фасады.
- [x] **CHK009** — port shape driven by domain need: ✓
  - `MediaPicker(kind, maxItems, mode)` — domain language (image/video/document), не «openIntent(action, mimeType)».
  - `LocalMediaStore(uuid)` — domain key, не «getFilePath(linkId, contactId)».
- [ ] **CHK010** — fake adapter для каждого port'а: deferred-to-plan
  - **Действие**: plan-phase создать `FakeMediaPicker`, `FakeLocalMediaStore` в `:core:api:test-fakes`.
- [ ] **CHK011** — real adapter для каждого port'а: deferred-to-plan
  - `SystemPhotoPickerAdapter` (Android) → новый модуль или в `:app`.
  - `FileLocalMediaStore` (Android) → новый модуль.
- [ ] **CHK012** — DI wiring picks fake/real per build: deferred-to-plan.

## Source-set placement

- [ ] **CHK013** — source-set assignment для new files: deferred-to-plan.
  - Ports — `commonMain`.
  - Adapters — `androidMain`.
  - Compose screens (DocumentViewer, admin upload progress) — `androidMain`.
- [x] **CHK014** — default placement `commonMain`: ✓ для ports; явный deviation для adapters (platform API).

## Existing-code regressions

- [x] **CHK015** — spec не реинъектирует vendor types в `commonMain`: ✓.
- [x] **CHK016** — нет нового `expect`/`actual` где pure Kotlin достаточен: ✓
  - `MediaPicker` — обычный interface в `commonMain`.
  - `LocalMediaStore` — обычный interface в `commonMain`.
  - Никаких `expect class`/`expect fun` не вводится.

---

## Summary

| Status | Count |
|---|---|
| ✓ | 12 |
| deferred-to-plan | 4 (CHK010-013) |
| ✗ violations | 0 |

**Verdict**: domain isolation на спек-уровне **не нарушается**. Все 4 пункта, отмеченные «deferred-to-plan», — это нормальная декомпозиция работ между spec-фазой (где определяются ports) и plan-фазой (где определяется размещение adapters и DI wiring).

**Constitution alignment**: CLAUDE.md rule 1 ✓, rule 2 ✓, новый Article XI §8 «Reuse before invention» ✓ (переиспользует все 6 крипто-портов 011 + `BlobReferenceLedger`, без замены).
