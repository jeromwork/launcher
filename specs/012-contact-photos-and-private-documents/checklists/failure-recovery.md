# Checklist: failure-recovery

**Spec**: [spec.md](../spec.md)
**Run**: 2026-05-26 (post-clarify-pass)
**Result**: 14/17 ✓ + 3 open items

---

## Error categories

- [x] **CHK001** — каждый FR с external action перечисляет failure mode: ✓
  - FR-001 upload → `CryptoError.StorageFailure(IOException | SecurityException | QuotaExceededException)`, `MalformedEnvelope` (encrypt error).
  - FR-002 resolve → `BlobMissing / MacFailed / KeyNotFound / RecipientNotFound` (US-3 sc.1-3 покрывают).
  - FR-007 picker → `MediaPickerError.InvalidMimeType` (FR-009), user cancellation (стандартный `RESULT_CANCELED`).
  - FR-011 share intent → отсутствие PHOTO в payload = `photoRef = null` (FR-012; не ошибка).
  - FR-013 overwrite → no failure mode (это уже handled внутри FR).
- [x] **CHK002** — user-visible behaviour specified per failure: ✓
  - Decrypt failures → placeholder + крупное имя/label + admin indicator (FR-021, FR-022).
  - Upload failure → admin UI «нет сети, попробую снова» с retry button (edge case 3).
  - QuotaExceeded → admin UI «у бабушки много фото, удалите часть» (edge case 7).
  - InvalidMimeType → отвергает «без crash» (FR-009) — но **конкретный message не специфицирован**. ⚠️
- [x] **CHK003** — нет silent failures user-initiated actions: ✓
  - Все upload-failures видимы admin'у через индикатор / retry button.
  - User cancellation picker'а — нативный системный UX (понятен).

## Fallbacks

- [ ] **CHK004** — maximum depth fallback chains
  - **Status**: ⚠️ borderline.
  - На decrypt failure: blob → placeholder + name. Это **одношаговый fallback**, не chain. Cycle protection — N/A.
  - При первом show miss → download → decrypt → save: если decrypt падает, fallback на placeholder; нет дальнейшего chain. ✓
  - **Действие**: явно зафиксировать в plan-phase «no fallback chains in 012, decrypt-failure = direct placeholder».
- [x] **CHK005** — fallback specified by data, not hardcoded: ✓
  - `IconResolution.Placeholder` — sealed type result spec 006, унаследованный.
  - `PartialReason` — data-driven enum (state-applied.md).
- [x] **CHK006** — terminal behaviour при finальном failure: ✓
  - Placeholder + name = терминал. Плитка не дальше fallback'ается.
  - Admin indicator — терминал (action button: re-add OR re-pair).

## Retries

- [x] **CHK007** — retry behaviour explicit:
  - Upload retry → user-triggered («попробую снова» button, edge case 3). ✓
  - Resolver — implicit auto: каждое показ плитки — попытка download/decrypt снова (FR-002). ✓
  - Housekeeping reconciliation — 5-минутный cadence (FR-013, FR-023, SC-005). ✓
- [x] **CHK008** — no infinite retry loops: ✓
  - Auto-resolver retry **только при показе плитки** (по user action), не background polling.
  - Housekeeping — fixed cadence, не retry-loop.
- [x] **CHK009** — idempotency: ✓
  - Upload одного и того же blob'а с тем же uuid — idempotent (Storage `allow update: if false` per 011 rules — повторный upload даст error, требуется новый uuid).
  - `BlobReferenceLedger.refCount` — atomic increment/decrement (owned by 011).
  - Decrement при overwrite (FR-013) — идемпотентен, если decrement сделан **ровно один раз** на одно user-action.

## Offline / degraded modes

- [x] **CHK010** — offline behaviour: ✓
  - **Admin offline**: upload откладывается, retry button (edge case 3).
  - **Бабушка offline**: первый показ → placeholder, эмит `PartialReason.MediaDecryptFailed` (subcategory `blob_missing` или `network`). При возврате online — следующий показ плитки попытается download снова. ✓
- [ ] **CHK011** — stale data TTL
  - **Status**: ⚠️ partial.
  - Расшифрованные файлы в LocalMediaStore — **persistent без TTL** (Clarification Q5).
  - `/state.partialApplyReasons` — **не определено**, когда очищается (adjacent concern из mentor-сессии).
  - **Действие**: записать в plan-phase или в spec'е 008 follow-up: когда очищается `partialApplyReasons`? Текущее предположение: при следующем successful apply config'а.

## Permissions denied

- [x] **CHK012** — permission denied first time: ✓
  - Spec 012 **не вводит новых permissions** (SC-007, FR-008). Picker — без permission на всех API levels.
  - `READ_CONTACTS` — обрабатывается в спеке 009 (зависимость).
- [x] **CHK013** — permanent denial recovery: ✓ N/A (нет новых permissions).

## Recovery from invalid state

- [x] **CHK014** — corrupt persistent state recovery: ✓
  - LocalMediaStore corrupt file → next show triggers re-download (FR-002, edge case «выбытие из LocalMediaStore»).
  - Envelope MalformedEnvelope → placeholder (US-3 sc.1).
  - `BlobReferenceLedger` corrupt → owned by 011 (housekeeping reconciler).
- [x] **CHK015** — нет "crash and restart" recovery: ✓
  - Все failure cases → graceful placeholder + indicator.

## Diagnostics

- [x] **CHK016** — failures observable, no PII: ✓
  - Structured log событие категории `media_decrypt_failed` с subcategory (`mac_failed`, `blob_missing`, `key_not_found`, `recipient_not_found`) — FR-021.
  - **Note**: blob uuid — это random UUIDv4, не PII; subcategory — categorical. ✓
- [x] **CHK017** — failures aggregated by category: ✓
  - 4 sub-category в FR-021 — enables rate measurement (как требует CHK017 + ux-quality CHK011).
  - `PartialReason` — categorical enum (state-applied.md:64-67).

---

## Summary

| Status | Count |
|---|---|
| ✓ | 14 |
| ⚠️ open / borderline | 3 (CHK002 message wording, CHK004 fallback-depth doc, CHK011 partialApplyReasons TTL) |
| ✗ violations | 0 |

**Open items**:
1. **CHK002**: добавить точные user-facing messages для каждого `MediaPickerError` / `CryptoError` в plan-phase или в `localization` checklist.
2. **CHK004**: явно зафиксировать в plan «no fallback chains, decrypt-failure = direct placeholder, без cycles».
3. **CHK011** (adjacent concern): когда очищается `/state.partialApplyReasons`? Spec 008 это не покрыл. Решение: при следующем successful apply удаляем устаревшие reasons. Записать как follow-up для спека 008 или явный FR в plan-phase 012.

**Verdict**: failure-recovery story в spec 012 **зрелая** — целый US-3 посвящён honest reporting, 8 edge cases, 4 sub-category для diagnostics, явные recovery paths (re-add, re-pair). Open items — это уточнения уровня plan/strings, не gaps.
