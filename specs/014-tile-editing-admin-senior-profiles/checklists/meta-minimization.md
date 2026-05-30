# Meta-Minimization — spec 014

Generated: 2026-05-29 (speckit-clarify run).

Catches speculative architecture per Article XI + CLAUDE.md rule 4.

## New abstractions

- [x] **CHK001** `EditUiProfile` (sealed class, AdminProfile/SeniorProfile) — два concrete consumer'а в spec: admin Workspace render + senior Simple Launcher render. Не speculative.
- [x] **CHK002** `EditUiProfile` имеет 2 implementations с самого начала (Admin, Senior) — не single-impl interface. PASS.
- [x] **CHK003** `EditUiProfileSelector` — pure function (`presetId → profile`). Не orchestrator/manager, а **decision function**. Минимальная shape (один `selectProfile` метод).
- [x] **CHK004** Никакого DSL / registry / plugin system. Hardcoded `when` mapping per Q2 clarification. Когда понадобится compositability — F-2 (Capability Registry) расширит. Не сейчас.

## New modules / packages

- [x] **CHK005** N/A — F-014 **не вводит** новых gradle модулей. Использует existing: `core` (domain ops), `app` (presentation), `data` (ConfigEditor реализация — already exists per спека 008).
- [x] **CHK006** N/A — package-level организация в `core/commonMain/api/edit/`. Justified: domain verbs для tile editing — это новая coherent capability, заслуживает отдельного package, не модуля.
- [x] **CHK007** Нет "utils" / "common" / "helpers" дампинга. Package `api/edit` имеет coherent purpose.

## New configuration

- [⚠️] **CHK008** Named configs fields (`configName`, `description`, `isDefault`, `activeDeviceIds`, `orphanedAt`, compatibility key) — **current consumer присутствует**: FR-003a..FR-003i (9 sub-FR) определяют конкретные операции над этими полями. **Но**: `orphanedAt` поле имеет limited current use (только UI marker, не triggers auto-delete до TODO-FUTURE-SPEC-008). Это **acceptable** — поле уже нужно для countdown UI (FR-003b), не speculative. OK.
- [x] **CHK009** Default flag invariant документирован (FR-003a atomic transaction). Backward-compat: schemaVersion bump 1→2 в F-014.1 (per §Extends section). Backward-compat read v1 сохраняется ("Backward-compat read v1 (plain) сохраняется"). Migration path есть.

## CLAUDE.md rule 4 self-test

- [x] **CHK010** **Test 1 applied**:
  - `EditUiProfile` inline → потеряли два set'а presentation rules; обе ветки нужны в edit mode (per FR-010) + use mode (per FR-013, FR-021). Inline бы дублировал rules в каждом view. PASS, keep.
  - `EditUiProfileSelector` inline → потеряли testability mapping (SC-005 unit test'ит ИМЕННО selector). Hardcoded `when` всё равно нужен где-то — лучше в одном месте. PASS, keep.
  - `EditError` sealed class inline → потеряли exhaustive `when` в presentation layer (compile-time safety при добавлении variants). PASS, keep.
- [x] **CHK011** **Test 2 applied**:
  - Если ConfigDocument schema поменяется (например `slots[]` → `tiles[]`) — F-014 ops (`addSlot`, `removeSlot`) поменяют signatures. Cost: ≤ 1 день (rename + tests). Это OK для domain — domain types по definition следуют domain shape. NOT a seam violation.
  - Если Firestore deprecated → F-014.1 server backup ломается. Cost: переписать `RemoteConfigStore` adapter. F-014 domain layer не меняется (ConfigEditor port абстрагирует). **Seam justified** через checklist-backend-substitution scope.

## Removal validation

- [x] **CHK012** N/A — F-014 ничего не удаляет, только **расширяет** existing (per §Extends): `EditorScreen`/`EditorComponent` (спека 009), `AddSlotWizardComponent` (спека 005), drag-and-drop modifiers (спека 010).
- [x] **CHK013** Нет "deprecated" markers. F-014 — pure addition.

## Concerns / open items

1. **Named configs (FR-003 + sub-items a..i) — 9 sub-FR на одну concept**. Это много для F-014.0, который сам декларирован как "local-only DataStore". **Возможное упрощение**: F-014.0 поддерживает только 1 config (no named, no multi-device). Multi-config (FR-003c-i) откладывается в F-014.1 + F-014.2. Текущая спека уже использует **progressive disclosure** (FR-003d) — UI скрыт пока count == 1. Но **domain layer** уже строит multi-config model в F-014.0. Это **отчасти speculative для F-014.0**, но oправдано тем, что F-014.1 не должен переписывать domain — только подключить server adapter. **Verdict**: на границе acceptability, но **проходит** Test 1 (если inline'ить — F-014.1 будет refactor a not just additive).
2. **`ProfileSelectionRequiresCapabilityRegistry` error variant** (per Q8) — current consumer: **отсутствует в F-014.0** (нет custom presets в MVP). Это **purely defensive variant** для one-way door. **Justified**: refuse pattern лучше silent fallback (CLAUDE.md rule 3 exit ramp explicit). Зафиксировать в spec'е важнее чем удалить.

**Verdict**: PASS с замечанием по named configs sub-FR. Не блокирует plan.md, но в plan'е стоит явно разделить F-014.0 (single-config domain shape) vs F-014.1+ (multi-config).
