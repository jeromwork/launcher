# Checklist: modular-delivery

**Spec**: [spec.md](../spec.md)
**Run**: 2026-05-26 (post-clarify-pass)
**Result**: 16/18 ✓ + 2 deferred-to-plan — 0 violations, 0 blockers

---

## Scope of the feature

- [x] **CHK001** — form-factor scoping: ✓
  - Spec 012 — **form-factor-agnostic в части доменной логики** (фасады `PrivateMediaUploader/Resolver`, `LocalMediaStore`, доменные types — в `commonMain`).
  - **Form-factor-specific** в UI слое (admin upload form, DocumentViewer, плитки) — handheld-only (phone). TV/Wear/Auto — out of scope.
- [x] **CHK002** — form-factor-specific module ownership: ✓
  - Compose screens (DocumentViewer, admin upload form, admin indicator) — handheld feature module (например, `:features:private-media:ui` или extension существующего `:features:settings:ui`).
  - Не в Core, не в TV/Wear/Auto модуле (которые не существуют пока).
- [x] **CHK003** — form-factor-agnostic core demo: ✓
  - **Доменные ports** (`PrivateMediaUploader / Resolver / LocalMediaStore / MediaPicker`) — без vendor SDK / platform API в signatures.
  - **`MediaPicker.MediaPickResult(bytes, mimeType, sourceLabel?)`** — Pure Kotlin types (см. domain-isolation CHK006/007).
  - Никаких `Intent / Uri / Context` / D-pad / voice intent assumptions в `commonMain`.

## Module placement

- [x] **CHK004** — no new vendor SDK in Core: ✓
  - libsodium — owned by 011 (`:adapters:crypto:lazysodium`).
  - Backblaze B2 + Cloudflare Worker — owned by 011 (`:adapters:storage:b2-worker`).
  - System Photo Picker — **новый adapter** (`:adapters:media-picker`), не в Core.
- [x] **CHK005** — new gradle module justified: ✓
  - **`:adapters:media-picker`** (новый): API boundary = Anti-Corruption Layer для System Photo Picker + SAF dispatch (CLAUDE.md rule 2). Removed complexity now: prevents `Intent / Uri / ContentResolver` утечку в UI / domain. Package недостаточен — это `androidMain` source set с platform API.
  - **`:facades:private-media`** (новый, опционально — plan-phase решит) или часть существующего `:adapters:crypto:lazysodium`: фасады изолируют UI/domain от прямого использования крипто-портов 011. Если plan-phase решит — оставить в `:core:api` без отдельного модуля (для меньшего фрагментирования) — допустимо, потому что фасады — pure Kotlin (no platform API).
- [x] **CHK006** — regret condition для form-factor-specific code в shared: ✓
  - Domain layer **не** содержит form-factor-specific code.
  - UI handheld-only — explicit decision; **regret condition**: «когда понадобится Wear/TV/Auto preset с private documents → создаётся отдельный `:features:private-media:wear-ui` (или аналог), доменные ports переиспользуются без изменений».
  - **Это and есть exit ramp** (CLAUDE.md rule 3): фасады позволяют добавить новый UI handler без касания фасадов и портов.

## Profile / preset declaration

- [x] **CHK007** — profile `requiredModules` / `optionalModules`: ✓ N/A
  - Spec 012 **не вводит и не модифицирует profile**.
  - Документы и фото контактов — функциональность handheld launcher'а, доступная всем active profile'ам (no opt-in).
- [x] **CHK008** — profile schema bump: ✓ N/A.
- [x] **CHK009** — graceful degradation when module absent: ✓
  - Если `:adapters:media-picker` отсутствует (теоретически на минимальном build) — admin не может «+ документ» (FR-015), но remaining flows (плитки контактов с фото — через VCard share) работают.
  - Если `LocalMediaStore` adapter отсутствует — Resolver fall-back на placeholder (graceful degradation, известно из FR-002).

## Form-factor expansion

- [x] **CHK010-012** — non-handheld form factor delivery: ✓ N/A
  - Spec 012 — **handheld-only**. Никаких TV/Wear/Auto SDK, никаких новых form factors.

## One-way doors raised by the feature

- [x] **CHK013** — reversible dependencies / identifiers / wire formats: ✓
  - **Tile `kind="document"`** + `documentRef` — additive (Clarification Q2, до spec 030+ свобода есть; после — bump policy).
  - **`metadata.kind` envelope key** — additive optional в envelope; reader 011 нейтрален к metadata content.
  - **`LocalMediaStore` layout** `Context.filesDir/private-media/<uuid>` — app-private, не покидает устройство, не cross-app contract. Reversible.
- [x] **CHK014** — vendor-disappear test: ✓
  - libsodium → один adapter (`:adapters:crypto:lazysodium`).
  - B2 → один adapter (`:adapters:storage:b2-worker`).
  - System Photo Picker → **новый adapter** (`:adapters:media-picker`); если Android deprecates picker — меняем только эту папку.
- [x] **CHK015** — free-workaround vs server component: ✓
  - **Уже зафиксировано в 011**: `SRV-CRYPTO-001` в [server-roadmap.md](../../../docs/dev/server-roadmap.md) — миграция Storage Spark/B2 → собственный server.
  - Spec 012 **не вводит новых free workarounds**; всё унаследовано из 011.
  - **`TODO-ARCH-019`** ([backlog](../../docs/dev/project-backlog.md)) — local storage quota, заведён из этого спека.

## Anti-bloat sanity

- [ ] **CHK016** — no Gradle module за single class / single-impl interface
  - **Status**: ⚠️ deferred-to-plan.
  - `:adapters:media-picker` будет содержать `MediaPicker` port + `SystemPhotoPickerAdapter` (3 internal API-level branches) — это **multi-class** module.
  - `:facades:private-media` (если plan-phase решит создать) — содержит `PrivateMediaUploader` + `PrivateMediaResolver` + `PrivateMediaKind` enum — multi-class. Но **borderline** — рассмотрим в plan-phase, не создавать ради «modular feel».
  - **Действие**: plan-phase явно ответить «module vs package» для facade и `LocalMediaStore`.
- [x] **CHK017** — no pre-emptive splits for future form factors: ✓
  - Spec 012 НЕ предсоздаёт `:features:private-media:wear-ui` или `:features:private-media:tv-ui`. Это **explicit «regret condition» путь** (CHK006).
- [x] **CHK018** — future split recorded as regret condition: ✓
  - Зафиксировано в CHK006 + Extensibility секция spec.md: «новые формат-факторы → новые UI handlers поверх неизменных domain фасадов».

---

## Summary

| Status | Count |
|---|---|
| ✓ | 16 |
| deferred-to-plan | 2 (CHK005 partial — `:facades:private-media` module-vs-package; CHK016 multi-class verification) |
| ✗ violations | 0 |
| 🚫 blockers | 0 |

**Verdict**: Spec 012 **строго modular-delivery-compliant**:
- Form-factor-agnostic doмen + handheld UI с явной regret condition для future form factors.
- 1 новый adapter module (`:adapters:media-picker`) для Anti-Corruption Layer.
- 0 новых profile-changes.
- 0 form-factor SDK leak в Core.
- Все wire-format расширения — additive и reversible (через Q2 + sunset до spec 030+).
- Vendor-swap cost — bounded в один adapter каждый.

**Constitution alignment**: Article V ✓, Article VII §6 ✓, CLAUDE.md rule 1-4 ✓.
