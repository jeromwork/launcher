# Checklist: modular-delivery — TASK-65 Preset Composition Foundation v2

Applied: 2026-06-30, spec: `specs/task-65-profile-composition-foundation-v2/spec.md`

## Scope of the feature

- [x] CHK001 Form-factor classification declared — **yes**. Spec явно: form-factor-agnostic (handheld Android phone сейчас, но designed под cross-app extension — messenger TASK-27, photo TASK-28; future iOS — TASK-26).
- [x] CHK002 Form-factor-specific module ownership — **N/A** (agnostic).
- [x] CHK003 No SDK/platform/UI leak в shared code — **yes, и lint защищает**:
  - `ExtractionReadinessDetector` (FR-021) запрещает launcher-specific imports в `core/presets/`, `core/wizard/`, `core/pools/`.
  - `UIFontChecker` (FR-024) — Android adapter in `androidMain`, **не** в commonMain.

## Module placement

- [x] CHK004 No new vendor SDK in Core — **yes**. TASK-65 не вводит новых vendor SDK (использует существующий kotlinx.serialization, Compose).
- [x] CHK005 Article V §7 answered for new module — **N/A**. No new Gradle modules (только packages внутри существующих modules).
- [x] CHK006 Regret condition documented for «keep in shared module for now» — **yes**. Adjacent Concern: extraction в sub-repo trigger = messenger TASK-27 / photo TASK-28 (rule 4 explicitly cited). `ExtractionReadinessDetector` обеспечивает готовность.

## Profile / preset declaration

- [x] CHK007 `requiredModules` / `optionalModules` explicit — **yes**. FR-001 включает оба поля для `preset.json` (per Article VII §8).
- [x] CHK008 Schema bump + backward-compat plan — **yes**. `preset.json` schemaVersion=1 from-scratch. `wizard.manifest` schemaVersion bump из-за `appFamilyId` removal + migration writer (FR-002).
- [x] CHK009 Base app + existing presets load без новых модулей — **yes**. TASK-65 не вводит требуемых модулей. Existing simple-launcher продолжает работать через migration FR-015.

## Form-factor expansion

- [x] CHK010-012 N/A (нет non-handheld form factor в TASK-65 scope).

## One-way doors

- [x] CHK013 No irreversible wire format / identifier without exit ramp — **yes**:
  - `preset.json` schemaVersion=1 — exit ramp documented (schemaVersion bump + migration writer).
  - Pool naming convention — namespaced docs + per-pool schemaVersion.
  - Naming inversion (Preset/Profile) — constitution amendment 1.11 applied, exit ramp = rename via touch.
- [x] CHK014 «Vendor disappears tomorrow» test — **yes**. Detekt — single dependency for lint rules; если deprecated → swap на ktlint или Android Lint requires rewrite ~1 day.
- [x] CHK015 Free-workaround → server-roadmap entry — **N/A**. TASK-65 не вводит server zone; TASK-70 (sync) — explicit follow-up.

## Anti-bloat sanity

- [x] CHK016 No module for single class — **yes**. No new modules.
- [x] CHK017 No pre-emptive split — **yes**. Foundation в существующих modules, extraction отложена до trigger (rule 4 правильно применено).
- [x] CHK018 Future split = regret condition — **yes**. Cross-app vision documented as extraction trigger.

---

**Total**: 14/14 applicable ✓, 4 N/A
**Red-only summary**: modular-delivery: 18/18 ✓ (14 applied + 4 N/A correctly).
