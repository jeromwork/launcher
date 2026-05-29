# Domain Isolation — spec 014

Generated: 2026-05-29.

## Vendor SDKs

- [x] **CHK001** No vendor SDK types in domain signatures. Spec упоминает Firestore (`/admin-self-configs/{adminUid}/configs/{configName}/current`) только в context phase F-014.1 server backup — это **adapter-level** path, не domain shape. Domain ops (FR-001) — `addSlot/removeSlot/moveSlot/replaceSlot` — оперируют ConfigDocument domain type.
- [x] **CHK002** Existing wrappers переиспользуются: `ConfigEditor` port (спека 008) уже абстрагирует Firestore. F-014 не добавляет новых SDK direct'ов в domain. `AppWidgetHost` (FR-018) — placeholder для TODO-UX-027, не текущая dependency.
- [x] **CHK003** "Vendor disappears" test: если Firestore исчезает — меняется ConfigEditor adapter (≤1 module). F-014 domain не trogается. PASS.

## Transport types

- [x] **CHK004** Никаких retrofit/HTTP типов в domain signatures. ConfigEditor port абстрагирует transport.
- [x] **CHK005** ConfigDocument — domain-owned data class (спека 008), не generated DTO. F-014 расширяет фичами named configs (`configName`, `isDefault`, etc.) — добавляются как domain fields с serializers в adapter.

## Platform types

- [x] **CHK006** Spec не использует `android.*`/`Intent`/`Uri`/`Context` в `commonMain`. F-014 domain в `core/commonMain/kotlin/com/launcher/api/edit/` (per Q2 clarification).
- [x] **CHK007** Domain projection: `presetId: String` (FR-008) — strings, не raw enum platform type. `linkId: String` в TargetIdentity — domain string.

## Ports

- [x] **CHK008** External surfaces через ports:
  - `ConfigEditor` (existing, спека 008) — для ConfigDocument ops + push/pull.
  - `EditUiProfileSelector` (new, F-014) — pure function, technically не port (нет I/O), но live в domain.
  - Provider registry для picker tabs — existing (спека 005).
  No new external surfaces in F-014.
- [x] **CHK009** Port shape domain-driven. `addSlot(flowId, slot)` — domain verb, не `writeToFirestore(json)`.
- [x] **CHK010** Fake adapters: `FakeConfigEditor` существует. Для `EditUiProfileSelector` fake не нужен (pure function).
- [x] **CHK011** Real adapter: `FirestoreConfigEditor` existing per спека 008. F-014 не добавляет новых real adapters в F-014.0.
- [x] **CHK012** DI wiring: existing pattern из спеки 008. F-014 не меняет.

## Source-set placement

- [x] **CHK013** Spec явно указывает: `EditUiProfileSelector` → `core/commonMain/kotlin/com/launcher/api/edit/EditUiProfileSelector.kt` (per Q2). Domain operations → `core/domain/` (FR-001).
- [x] **CHK014** Default `commonMain` соблюдён. Presentation extensions (jiggle animation, banner UI) — `app` module (Compose), это правильное deviation.

## Existing-code regressions

- [x] **CHK015** F-014 не reintroduce'ит vendor types в cleansed файлы. Только расширяет existing `EditorScreen`/`EditorComponent` (спека 009) presentation-side.
- [x] **CHK016** Никакого нового `expect`/`actual`. F-014 ops pure-Kotlin.

## Open items

Чисто. Domain isolation PASS — F-014 фактически **усиливает** domain (Q2 — pure function selector в commonMain), не нарушает.

**Verdict**: PASS (16/16 ✓).
