# Checklist: meta-minimization

**Spec**: [spec.md](../spec.md)
**Run**: 2026-05-26 (post-clarify-pass)
**Result**: 11/13 ✓ + 2 N/A — 0 violations, 1 borderline

---

## New abstractions

- [x] **CHK001** — every new port has concrete consumer in 012
  - `PrivateMediaUploader` → FR-011 + FR-016.
  - `PrivateMediaResolver` → FR-002 + FR-014 + FR-018.
  - `LocalMediaStore` → FR-003 (consumed by FR-002).
  - `MediaPicker` → FR-015 (только один consumer — admin «+ документ»). Допустимо: ACL для системного picker'а (rule 2).
  - `PrivateMediaKind` enum (Image/Document) — borderline (см. CHK010).
- [x] **CHK002** — single-impl interfaces justified by port-shape: ✓
  - Все фасады/порты — port-shape need (DI fakes, platform asymmetry, Anti-Corruption Layer для libsodium/Android system picker).
- [x] **CHK003** — no pass-through mediators: ✓
  - `PrivateMediaUploader` делает реальную работу (encrypt+upload+ledger), не pass-through.
  - `PrivateMediaResolver` — реальная dispatch + decrypt + cache lookup.
- [x] **CHK004** — no custom DSL/registry/plugin: ✓.

## New modules / packages

- [x] **CHK005** — new gradle module satisfies Article V §3: ✓ (resolved in plan-phase)
  - **`:adapters:media-picker`** (new): ownership boundary = system Photo Picker ACL; build isolation = androidMain platform code; testability gain = SystemPhotoPickerAdapter testable через ActivityScenario. Article V §3 satisfied.
  - **`:facades:private-media`** (rejected) — pure Kotlin facades живут в `:core:domain/media/` без отдельного module. Package достаточен.
  - **`:adapters:local-media-store`** (rejected) — `FileLocalMediaStore` single class в `:app` достаточен.
- [x] **CHK006** — package vs module: ✓ (resolved in plan-phase, research.md R6)
  - `:adapters:media-picker` justified: 3 API-level branches + future testability + Android platform API.
  - Facades в package `core.domain.media` достаточно — нет platform-specific code.
  - `FileLocalMediaStore` в `:app` — single class, packaging его в отдельный module = premature.
- [x] **CHK007** — нет "utils/common/helpers" модулей: ✓.

## New configuration

- [x] **CHK008** — каждый новый config-field имеет consumer'а:
  - `Tile.kind="document"` + `documentRef: String` → FR-015..020 (US-2), FR-023 (US-4 cleanup).
  - `metadata.kind` envelope ключ → FR-006 (privacy invariant), потенциально US-1 (image vs document marker).
- [x] **CHK009** — defaults + backward-compat policy:
  - До spec 030+: additive без bump'a (Clarification Q2).
  - После spec 030+: bump policy CLAUDE.md rule 5 включается.
  - `documentRef` обязателен для `kind="document"` (новый sealed variant, не optional field).

## CLAUDE.md rule 4 self-test

- [x] **CHK010** — Test 1 (inline ablation):
  - `PrivateMediaUploader` inline → утечка крипто в UI (нарушение rule 1). **Lost: domain isolation.** Justified.
  - `PrivateMediaResolver` inline → дубль logiка LocalMediaStore-check + decrypt + единая точка `PartialReason.MediaDecryptFailed` теряется. Justified.
  - `LocalMediaStore` inline → `Context.filesDir/...` paths прямо в Resolver, fake-adapter test (rule 6) невозможен. Justified.
  - `MediaPicker` inline → 3 ветки API-dispatch в admin Composable. **Lost: ACL за API-level differences.** Justified.
  - `PrivateMediaKind` enum (2 значения, Image/Document) — **borderline**. Можно inline'нить как string literals "image"/"document". Inline — допустимо; oставление — закладывает удобство расширения (audio/video). **Recommend**: оставить enum, но фиксировать в плане «расширение enum'а — additive, не требует port-shape сюрпризов».
- [x] **CHK011** — Test 2 (swap cost):
  - libsodium deprecated → меняем `:adapters:crypto:lazysodium`, фасады не трогаем. Swap cost ≈ 3-5 дней, **меньше** чем без фасадов (где пришлось бы менять UI/admin/Resolver). Justified.
  - Backblaze B2 deprecated → меняем `:adapters:storage:b2-worker`, фасады не трогаем. Аналогично.

## Removal validation

- [x] **CHK012** — spec 012 ничего не удаляет: ✓ N/A.
- [x] **CHK013** — нет deprecation markers: ✓ N/A.

---

## Summary

| Status | Count |
|---|---|
| ✓ | 13 (post-plan-phase) |
| N/A | 0 |
| ✗ violations | 0 |
| ⚠️ borderline | 1 (CHK010 — `PrivateMediaKind` enum, plan-phase resolution: оставлен per research.md R6) |

**Re-run 2026-05-26 (post-plan-phase)**: CHK005 + CHK006 resolved через research.md R6 + plan.md §Project Structure.

**Verdict**: spec 012 **не плодит speculative абстракции**. Все 4-5 новых портов имеют конкретных consumer'ов в 012; все 4 проходят Test 1 (inline-ablation justified); все проходят Test 2 (swap cost оправдан).

Единственный borderline — `PrivateMediaKind` enum с 2 значениями. Решение оставить — слабо justified (запас на audio/video). Если plan-phase решит inline'ить как string literals — допустимо без потерь.

**Constitution alignment**: Article XI §1-7 ✓, новый §8 «Reuse before invention» — выполнен (никаких новых портов поверх уже-готовых в 011, реюз `AeadCipher / EncryptedMediaStorage / BlobReferenceLedger` через фасад).
