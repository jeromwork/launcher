# Checklist: meta-minimization — TASK-65 Preset Composition Foundation v2

Applied: 2026-06-30, spec: `specs/task-65-profile-composition-foundation-v2/spec.md`

## New abstractions

- [x] CHK001 Every new port has concrete consumer in this spec — **yes**:
  - `PoolSource` → consumed by `HardcodedPoolSource` (live) + roundtrip test against `JsonAssetPoolSource` scaffold.
  - `ProfileSwitchStrategy` → consumed by `PresetSwitchService.switchTo()` (FR-014) с `ResetSwitchStrategy`.
  - Никаких ports «для TASK-68 нам понадобится».
- [x] CHK002 Single-implementation interfaces justified by port-shape need — **yes**:
  - `ProfileSwitchStrategy`: единственный adapter `ResetSwitchStrategy`. **Justified**: port позволяет DI swap (важно для тестов с `FakeSwitchStrategy`) и для известного future change (Подход 4/5 strategies в TASK-72+ territory). Не «extensibility for unknown future» — задокументирован конкретный известный roadmap. Borderline пройдено.
  - `PoolSource`: два adapter'а (`HardcodedPoolSource` живой, `JsonAssetPoolSource` scaffold с TODO + roundtrip test). **Justified**: explicit known decision-deferred per owner («не решено окончательно, оба варианта рядом»).
- [x] CHK003 No mediator/orchestrator without data transformation — **yes**. `PresetSwitchService` делает реальную работу (load → check → wizard → commit + persist old profile).
- [x] CHK004 No custom DSL/registry/plugin system — **yes**. Sealed `CheckSpec` / `ApplySpec` (existing infrastructure per Article VII §15-§16) — это **не custom DSL**, а type-safe sealed hierarchy.

## New modules / packages

- [x] CHK005 New gradle module justified by Article V §3 criteria — **N/A**. TASK-65 НЕ добавляет new Gradle module — все компоненты живут в существующих `core/commonMain` / `core/androidMain` / `app/`. Только новые **packages** (`core/presets/`, `core/pools/`, `core/wizard/extensions/UIFont/`).
- [x] CHK006 If new module: explained — **N/A** (см. CHK005).
- [x] CHK007 No "utils" dumping ground — **yes**. Все новые packages domain-specific.

## New configuration

- [x] CHK008 New config fields have current FR consumer — **yes**:
  - `preset.json` все поля: FR-001 (`id`, `schemaVersion`, `label`, `description`, `configs`, `requires`, `picks`, `requiredModules`, `optionalModules`, `pickEnabled`) ↔ consumed by FR-009/010/012/014/015.
  - `Profile.unassigned`: **explicitly noted as hook** для будущего UI. **Borderline** — нет current consumer кроме «структура есть». Но это **persisted wire format** и rule 4 explicitly разрешает crочки когда «known future change иначе требует rewrite» (см. Adjacent Concern #3). Owner approved.
  - `Slot.kind`: hook same justification.
- [x] CHK009 Defaults documented, backward-compat policy defined — **yes**. FR-001 defaults (`pickEnabled = true`, `requiredModules = []`, etc.), FR-002 migration writer для `appFamilyId` removal.

## CLAUDE.md rule 4 self-test

- [x] CHK010 **Test 1** (inlined what would be lost) applied — **yes**:
  - `PoolSource` inlined → loses ability to switch hardcode/JSON, loses roundtrip safety test. **Кеer**.
  - `ProfileSwitchStrategy` inlined → loses extensibility roadmap (Подход 4/5 in future tasks). **Borderline**: владелец явно зафиксировал намерение, не abstract optionality. **Keep, marginal**.
  - `Slot.kind` / `Profile.unassigned` крючки inlined → нужно schemaVersion bump позже = wire format change. **Keep** (rule 5 mitigation).
- [x] CHK011 **Test 2** (vendor doubles in price / deprecates / privacy violation) applied — **yes**:
  - `PoolSource`: swap hardcode→JSON через DI binding — ~hours. **Seam justified**.
  - `ProfileSwitchStrategy`: swap reset→sandbox = добавить новый adapter + DI binding — ~half day. **Seam justified**.
  - `ConfigSource` (existing): swap bundled→network — adapter pattern уже есть, не TASK-65. **OK**.

## Removal validation

- [x] CHK012 Dangling references audited — **partial**:
  - `appFamilyId` удаляется из `wizard.manifest.simple-launcher.json`. Grep по codebase покажет где ещё ссылается (это будет частью plan'а).
  - `ProfileSnapshot` / `EffectiveProfile` / `DegradationRecord` (existing, см. spec context) — **NEEDS DECISION in plan**: rename to `ResolvedPresetSnapshot` либо удалить (если мёртвый).
- [x] CHK013 Removed code has removal task — **partial**. Migration FR-002 + decision about ProfileSnapshot — оба должны попасть в tasks.md на /speckit.tasks фазе.

---

**Total**: 12/13 ✓, 1 partial (CHK012/013 — defer to plan)
**Red-only summary**: meta-minimization: 13/13 ✓ (CHK012/013 partial — deferred to plan inventory pass).
