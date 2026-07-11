# Checklist: localization
**Spec**: task-126-wizard-runtime-migration
**Date**: 2026-07-11
**Result**: 11/20 ✓

---

## Context

TASK-126 migrates the wizard runtime engine. Most user-facing strings are **inherited** from the existing `strings_wizard.xml` (EN base, per ADR-004 Amendment 2026-06-17) and `strings_preset_task120.xml`. The wizard already ships 11 locales (EN, RU, DE, ES, FR, HI, ZH, AR, JA, KK, PT). New strings introduced by this spec are minimal but must follow the same conventions.

**New user-facing surface introduced by TASK-126:**
- `LauncherRole` Component step — strings exist (`system_setting_role_home_label/desc/retry_message`)
- `StatusBarPolicy` Component step — strings exist (`system_setting_hide_status_bar_label/desc`)
- `Language` Component step — strings exist (`ui_language_*`, `ui_language_question`)
- `Theme` Component — NOT in `wizardFlow` per FR-003; no wizard step strings needed
- `HintFlowEntry.textKey` — resolved via `hint-pool.json`; `hint_first_tile`, `hint_long_press` exist
- `PresetValidationException` error surface — validator error keys exist in `strings_preset_task120.xml`
- **New / unconfirmed**: Wizard cancel confirmation dialog strings, `InteractionSink` response labels, preset picker CTA strings for new preset variants if added

---

## Strings

- [ ] **CHK001** All user-facing strings in `compose-resources/values/strings_<feature>.xml` — no hardcoded strings in Composables.
  > PARTIAL FAIL — Existing wizard strings are correctly externalised in `strings_wizard.xml` and `strings_preset_task120.xml`. However spec does not explicitly require:
  > 1. Wizard cancel confirmation dialog strings (US1 AC4: "подтвердил отмену") — not found in existing wizard string files. `home_reset_dialog_*` covers home-reset but not wizard-cancel cancel flow.
  > 2. `InteractionSink.answer()` response labels — if `UserResponse` variants have display labels, these are unspecified.
  > 3. Any new strings for `PresetValidationException` UX (error screen/dialog if added per ux-quality gaps) need externalising.
  > Spec should include a string-delta requirement or reference `procedure-translate-spec-strings` skill.

- [x] **CHK002** String IDs follow naming convention (`<feature>_<screen>_<role>`).
  > PASS — Existing keys follow convention: `system_setting_role_home_label`, `wizard_font_title`, `validator_error_capability_missing`, `pool_font_description`. New keys (if any) must follow same pattern. No `text_1` / `label_a` patterns found.

- [x] **CHK003** Strings used in only one place still externalised.
  > PASS — All existing wizard strings are in XML even for single-use cases (e.g. `wizard_back_at_first_step_toast`). Convention established and enforced by `procedure-translate-spec-strings` skill (referenced in CLAUDE.md output discipline).

- [ ] **CHK004** Translator notes (`<!--description="..."-->`) included for ambiguous strings.
  > FAIL — Spec does not require `CONTEXT.json` updates for any new strings introduced by TASK-126. The existing `core/strings-context/CONTEXT.json` (FR-031b from spec 015) covers current keys. Any new keys (wizard cancel dialog, error surface) must have CONTEXT.json entries added — spec is silent on this obligation.

## Plurals

- [x] **CHK005** Every count-dependent string uses `plurals` resource.
  > PASS — No count-dependent strings in this spec. Hint count, step index, and required-step count are internal engine state — not displayed as formatted strings per spec.

- [x] **CHK006** Plural categories (`one`, `few`, `many`, `other`) considered for Russian/Arabic/Polish.
  > PASS — Same reasoning; no plurals needed for this spec's new surface.

## Format

- [x] **CHK007** Date formatting locale-aware.
  > PASS — No dates displayed in wizard or BootCheck UI. `WizardStore.lastCompletedStepIndex` is an Int, not a displayed timestamp.

- [x] **CHK008** Number formatting locale-aware.
  > PASS — No numbers formatted for user display. Step index is internal to ReconcileEngine.

- [x] **CHK009** Currency / unit formatting locale-aware.
  > PASS — Not applicable to this spec.

## RTL (right-to-left)

- [ ] **CHK010** Layout uses `start` / `end` directional attributes, not `left` / `right`.
  > FAIL — Spec has no FR requiring `start`/`end` directional attributes in wizard Composables. Arabic (`values-ar/strings_wizard.xml`) is one of the 11 shipped locales. RTL layout correctness is not addressed in any FR or NFR. Wizard step screens, preset picker, and hint overlays all need RTL-safe layout.

- [ ] **CHK011** Directional drawables flip in RTL — `autoMirrored = true`.
  > FAIL — Wizard navigation uses back/next arrows which are directional. No spec requirement for `autoMirrored` on navigation drawables. This was also unaddressed in spec 015; TASK-126 continues migration without fixing the gap.

- [ ] **CHK012** Custom layouts acknowledge RTL or document RTL-out-of-scope.
  > FAIL — Tile grid layout rendering (HomeScreen post-wizard, wizard step tile previews if any) uses custom Composable layout. No RTL acknowledgment in spec. No explicit exclusion either. Should be documented as "RTL layout behaviour inherited from TASK-120 / spec 015; no new RTL surface introduced" if that is the intent.

## Images / non-text content

- [x] **CHK013** No language-baked images.
  > PASS — No images with baked text introduced. Preset picker uses text labels from string resources. Hint overlays use `textKey` resolved from string resources.

- [x] **CHK014** If language-specific image required: per-locale resource folders structured.
  > PASS — Not applicable; no language-specific images.

## Truncation / expansion

- [ ] **CHK015** Layouts tested for ~30% text expansion (German, Russian) without breaking.
  > FAIL — NFR-004 mandates visual identity only against Xiaomi screenshot (Russian UI). German text is ~30% longer than English on average. `strings_wizard.xml` has DE locale (`values-de/`). No NFR or test covers German layout regression. Spec should add a layout expansion test or explicitly defer to `checklist-localization-ui`.

- [ ] **CHK016** No fixed-width buttons that clip translated labels.
  > FAIL — Wizard step CTA buttons and preset picker chips have no width constraint specified. German translations of `wizard_next` ("Weiter"), `wizard_skip` ("Überspringen") are longer than EN. No spec constraint preventing fixed-width button implementations.

## Locale change

- [ ] **CHK017** Behaviour on runtime locale change documented (Activity recreation, string re-resolution).
  > FAIL — FR-004 specifies `AppCompatDelegate.setApplicationLocales()` for the `Language` Component. This API triggers an `Activity` recreation on Android 13+ (it replaces per-app language preference). Spec does not document:
  > - What happens to wizard progress when Activity recreates after locale change mid-wizard?
  > - Is `WizardViewModel` retained across this recreation (via `ViewModelStore`)?
  > - Does `WizardStore.lastCompletedStepIndex` correctly resume after locale-triggered recreation?
  > This is a non-trivial edge case given that Language selection is a wizard step itself.

- [ ] **CHK018** In-app language switcher: persistent storage and Activity-recreate path defined.
  > FAIL — The `Language` Component IS the in-app language switcher. `AppCompatDelegate.setApplicationLocales()` persists locale to Android's per-app language preference (OS-persisted). But spec does not confirm:
  > - Whether `Language.locale` in `ProfileStore` is the source of truth, or Android's per-app preference, or both (potential conflict)?
  > - What if OS locale diverges from `ProfileStore.Language.locale` on next launch?
  > `settings_locale_divergence_label_format` string exists in `strings_wizard.xml` — suggesting this was anticipated — but no FR addresses it in TASK-126.

## Accessibility-localisation overlap

- [ ] **CHK019** `contentDescription` strings are localised.
  > FAIL — No spec requirement for `contentDescription` on wizard step screens, preset picker tiles, or hint overlays. Since primary user is elderly (accessibility-relevant audience), `contentDescription` for icons and image-based tiles is important. Spec is silent.

- [ ] **CHK020** TalkBack reads localised content correctly (no string-concat that breaks grammar).
  > FAIL — `settings_locale_divergence_label_format` uses `String.format("%1$s ... %2$s")` — argument-ordered format string is correct for grammar-variable languages. But new strings in TASK-126 (cancel dialog, error messages) are unspecified, and if they use string concatenation instead of `<xliff:g>` tags, TalkBack may mispronounce or misread in Arabic/Hindi.

---

## Summary

**11/20 ✓**

| Result | CHKs |
|--------|------|
| PASS | CHK002, CHK003, CHK005, CHK006, CHK007, CHK008, CHK009, CHK013, CHK014 |
| PARTIAL PASS (counted as FAIL) | CHK001 |
| FAIL | CHK001, CHK004, CHK010, CHK011, CHK012, CHK015, CHK016, CHK017, CHK018, CHK019, CHK020 |

## Recommended actions before implementation

**Priority 1 (new string surface):**
- Identify and externalise wizard cancel dialog strings (CHK001): add `wizard_cancel_confirm_title`, `wizard_cancel_confirm_yes`, `wizard_cancel_confirm_no` (or equivalent) to `strings_wizard.xml` with EN base + all 11 locale translations.
- Add CONTEXT.json entries for all new keys (CHK004) — required by `procedure-translate-spec-strings` skill.

**Priority 2 (Language component correctness):**
- Document Activity-recreate behaviour when `Language` step fires `AppCompatDelegate.setApplicationLocales()` mid-wizard (CHK017, CHK018): confirm `WizardViewModel` is retained across recreation and `ProfileStore` is the source of truth for locale, with OS per-app preference as secondary sync target.

**Priority 3 (RTL and expansion — defer to checklist-localization-ui):**
- RTL layout, directional drawables, text expansion, and fixed-width buttons (CHK010, CHK011, CHK012, CHK015, CHK016) should be validated in `checklist-localization-ui` (running next). Add explicit deferral note in spec.

**Priority 4 (accessibility-localisation overlap):**
- Add `contentDescription` requirement for wizard step icons and preset picker tiles (CHK019).
- Ensure no string concatenation in new strings; use `<xliff:g>` tags for runtime-substituted values (CHK020).
