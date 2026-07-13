# Checklist: ux-quality
**Spec**: task-126-wizard-runtime-migration
**Date**: 2026-07-11
**Result**: 9/22 ✓

---

## Context

TASK-126 is primarily a **technical refactoring** (three engines → one ReconcileEngine). The explicit NFR is "UX after migration MUST be visually identical to pre-migration screenshot" (NFR-004). Many UX items are therefore inherited from the legacy wizard and TASK-120. Failures below are gaps in *specification* of the UX behaviour, not necessarily missing implementation.

---

## Completeness — coverage of screens

- [ ] **CHK001** Every user-facing screen of this feature listed in spec (entry, primary, confirmation, error, return).
  > FAIL — Spec references screens by behaviour but does not enumerate them:
  > - Preset picker screen (who renders it? which Composable/Activity/ViewModel?)
  > - Wizard step screen for each `WizardBehavior.Interactive` component
  > - AutoApply step indicator (is there a visible step screen or silent progress?)
  > - Cancel confirmation dialog
  > - Home screen (post-wizard)
  > - Error screen for `PresetValidationException` (Edge Cases mention it throws, but no screen)
  > - "Already set up" screen on second launch (if wizard completed)
  > Missing screens: error states for `ReconcileState` failures, boot-check result (no UI — correct, but not stated).

- [ ] **CHK002** Every UX state per screen specified (loading, empty, success, error, partial-data).
  > FAIL — Only partially specified:
  > - Loading state: SplashScreen API + `ReconcileState.Loading` (CL-1, FR-001) ✓
  > - Interactive state: `ReconcileState.Interactive(componentId)` ✓
  > - Done state: `ReconcileState.Done` → navigate to HomeScreen ✓
  > - **Missing**: Error state — what does `WizardScreen` show if `PresetBootstrap` fails (missing JSON, `PresetValidationException`)? No error state defined.
  > - **Missing**: What does AutoApply step look like to the user? Progress bar? Silent? Spinner?
  > - **Missing**: Empty preset (preset with zero `wizardFlow` entries) — does wizard skip entirely?

- [ ] **CHK003** Navigation transitions between screens specified (forward, back, deep-link entry, recreation).
  > FAIL — Partial:
  > - Forward: wizard step → next step (implied by ReconcileEngine loop) ✓
  > - Cancel/back: US1 AC4 covers cancel → snapshot restore ✓
  > - **Missing**: Back from preset picker (before wizard starts) — what happens?
  > - **Missing**: Activity recreation (screen rotation) during wizard — `WizardViewModel` + StateFlow should survive, but no spec text.
  > - **Missing**: App process killed and relaunched from Recent Apps (vs force-close) — different from US3?
  > - **Missing**: Navigation from Settings back to wizard (if wizard not complete).

- [ ] **CHK004** Cross-cutting overlays (snackbar, toast, dialog, bottom sheet) — when shown, by whom, dismissible by what.
  > FAIL — Only cancel confirmation dialog is implied (US1 AC4: "подтвердил отмену"). Not specified:
  > - Dialog type (AlertDialog? Bottom sheet?)
  > - Button labels ("Отменить" / "Продолжить" / "Выйти"?)
  > - Whether dialog is cancellable (tap outside dismisses?)
  > - System permission dialogs (ROLE_HOME) — spec notes they appear once (US2) but doesn't describe what user sees or what to do if dialog is dismissed without action.
  > - Error toasts/snackbars for failed steps (Provider returns error).

## Clarity — terminology and rules

- [x] **CHK005** UX terms defined unambiguously; same term means the same thing across spec.
  > PASS — "Applied", "Pending", "Interactive", "AutoApply", "wizardFlow", "Component", "Provider" used consistently from TASK-120 vocabulary throughout. "required=true" / "required=false" defined in FR-006 and CL-3.

- [x] **CHK006** Vague qualifiers removed or operationalised.
  > PASS — "visually identical" operationalised by reference to screenshot `verification-evidence/task-120-xiaomi-first-launch.png` (NFR-004). ReconcileEngine timing ≤ 30ms (NFR-001). No free-floating "smooth" or "intuitive". "without errors" in SC-2 is slightly vague but covered by SC-7/SC-8 tech gates.

- [x] **CHK007** Action vocabulary explicit: tap vs long-press vs swipe — no "interact".
  > PASS — User actions expressed as "opens", "selects", "нажал" (tapped), "проходит", "закрыл". No "interact" or "engage with".

- [ ] **CHK008** Button labels are exact strings (or token IDs), not "Confirm-style label".
  > FAIL — Only "Отменить" (cancel action) mentioned. Missing:
  > - Preset picker: button/chip labels for "workspace / launcher / simple-launcher"
  > - Wizard step CTAs (e.g. "Далее" / "Разрешить" / "Пропустить"?)
  > - Cancel confirmation dialog: both buttons
  > - Final wizard completion: "Готово" / "Начать"?
  > Since spec states UX is identical to pre-migration, these may exist in existing code — but spec should reference string resource IDs or token names.

## Consistency

- [ ] **CHK009** In-Scope FRs and screens align — no orphan FRs, no unspecified screens.
  > FAIL — The preset picker screen has no dedicated FR. FR-001 wires `FirstLaunchActivity.onCreate()` → `PresetBootstrap` but does not specify the picker ViewModel, picker state, or how user selection feeds into `PresetBootstrap.bootstrap(presetId)`. US1 AC1 describes the picker behaviourally but no FR owns it.

- [ ] **CHK010** Confirmation policy consistent: actions requiring confirmation listed; one-tap actions justified.
  > FAIL — Only cancel wizard is confirmed (US1 AC4). No policy statement for:
  > - "LauncherRole" system dialog (one-tap system prompt — but what if dismissed?)
  > - Step-level "skip optional step" (if offered — spec unclear)
  > - Snapshot restore (what does user see after confirming cancel? Any confirmation of restoration?)

- [ ] **CHK011** Multi-tap / accidental-double-tap protection consistent across action surfaces.
  > FAIL — Not addressed. InteractionSink `answer(componentId, response)` — if called twice rapidly, spec does not say ReconcileEngine is idempotent for double answer. No UI debounce specified.

## Acceptance — measurability

- [x] **CHK012** Each US has explicit Given/When/Then or numbered acceptance scenario.
  > PASS — All 7 User Stories have numbered Given/When/Then acceptance scenarios (US1: 4 scenarios, US2: 2, US3: 2, US4: 2, US5: 2, US6: 3, US7: 2).

- [ ] **CHK013** Success criteria measurable per UX moment (entry to first-tap, tap to feedback, action to result).
  > FAIL — NFR-001 gives ReconcileEngine cold-start delta ≤ 30ms. But no UX timing for:
  > - Preset picker → wizard start latency
  > - Wizard step render latency after user taps a choice
  > - Feedback latency between Provider.apply() and next step appearing
  > - HomeScreen first-frame after wizard completes
  > NFR-004 (visual identity) is the only UX-quality NFR. Timing NFRs are engine-internal only.

- [ ] **CHK014** Returning-user UX (second-launch, resume from background) defined or excluded.
  > FAIL — US3 covers force-close resume mid-wizard ✓. CL-3 covers Provider.check() re-run on resume ✓. But not defined:
  > - Second launch **after full wizard completion**: does app go straight to HomeScreen? Is wizard skipped? What signals "wizard done"?
  > - Launch from Recent Apps when wizard was mid-step (process still alive vs killed): different resume path?
  > - User launches app while BootCheck is running: race condition UX?

## Coverage — alternative paths

- [ ] **CHK015** Every primary action has its negative-path UX defined.
  > FAIL — Several negative paths unspecified:
  > - **LauncherRole denied**: user dismisses system dialog without assigning. Wizard step shows what? Retry? Skip? Error? (Required=true case would block completion — how is that communicated?)
  > - **PresetValidationException**: thrown before wizard starts (Edge Cases). No UX — does app crash? Show error screen? Which screen?
  > - **Provider.apply() fails** (e.g. `StatusBarPolicyProvider` on incompatible MIUI): ReconcileEngine error → user sees what?
  > - **DataStore write fails** (`ProfileStore.save`): wizard progress lost silently?
  > - **PresetBootstrap fails** (JSON missing/corrupt): SplashScreen stuck? Error screen?

- [x] **CHK016** Multiple entry points yield consistent UX or differences explicitly noted.
  > PASS — Wizard entered only via `FirstLaunchActivity` (app icon cold start). Settings edit via Settings screens (US5). BootCheck is headless (no UX). No deep-link or notification entry to wizard. Entry points are limited and explicit.

- [x] **CHK017** Long-pause scenarios (user leaves app for hours) have defined return-UX.
  > PASS — CL-3 explicitly covers: "On resume, ReconcileEngine starts from `lastCompletedStepIndex + 1` but re-runs `check()` for each step." System state re-queried on each resume — correct for the use case where OS state could have changed.

## Non-functional UX

- [ ] **CHK018** Accessibility deferred to `checklist-accessibility` if relevant; otherwise listed here.
  > FAIL — No mention in spec of accessibility deferral. Given that primary user is elderly (Xiaomi Redmi Note 11 target), accessibility (touch targets, contrast, TalkBack compatibility) is relevant. Should be explicitly deferred to `checklist-accessibility`.

- [ ] **CHK019** Localisation deferred to `checklist-localization` if relevant; otherwise listed here.
  > FAIL — `Language` Component (FR-004) with `"system"` locale sentinel is specified. Wizard UI strings in Russian (spec text). No mention of whether wizard UI strings are localised or if there's a deferred checklist reference.

- [ ] **CHK020** Diagnostic UX (how user sees that something is being tracked / going wrong) specified or excluded.
  > FAIL — No diagnostic UX specified. If ReconcileEngine encounters an error mid-wizard, user sees nothing (no spec text). No loading indicator for individual step apply() calls. No explicit exclusion of diagnostic UX either.

## Dependencies / assumptions

- [x] **CHK021** UX doesn't depend on out-of-scope capabilities.
  > PASS — Wizard depends on TASK-120 foundation (explicitly in scope per spec input line 7). No cross-app embedded UI. Preset picker shows only bundled presets (no external catalog). iOS out of scope (iOS providers deferred per Assumptions).

- [ ] **CHK022** Mock-data limitations noted explicitly if they affect rendering of user-facing content.
  > FAIL — `mockBackend` flavor is used for unit tests. Spec does not note whether `WizardScreen` renders differently with mock vs real `PresetBootstrap` data, or whether `simple-launcher` golden JSON is the same in both. If mock data has stub step texts / icons, the visual identity comparison (NFR-004) against Xiaomi screenshot may not be meaningful on the emulator.

---

## Summary

**9/22 ✓**

| Result | CHKs |
|--------|------|
| PASS | CHK005, CHK006, CHK007, CHK012, CHK016, CHK017, CHK021 |
| PASS (marginal) | CHK005, CHK017 |
| FAIL | CHK001, CHK002, CHK003, CHK004, CHK008, CHK009, CHK010, CHK011, CHK013, CHK014, CHK015, CHK018, CHK019, CHK020, CHK022 |

## Recommended actions before implementation

**Rationale note**: most failures stem from the spec being a technical-migration spec that assumes UX is inherited from legacy wizard. The recommended approach is to add a short "Inherited UX" section referencing the legacy screens and explicitly stating which UX behaviours are unchanged, then specify only the deltas. This resolves most failures with minimal text.

**Priority 1 (blocks wizard acceptance testing):**
- Add screen inventory (CHK001): list Composables/screens by name, even if inherited.
- Add error state UX for `PresetValidationException` and `PresetBootstrap` failure (CHK002, CHK015).
- Specify LauncherRole denial UX — what wizard shows if user dismisses system dialog (CHK015, CHK003).

**Priority 2 (clarity for implementors):**
- Add exact button label string resource IDs or Russian text tokens for wizard CTAs and cancel dialog (CHK008).
- Add second-launch post-wizard UX — does app skip wizard and go to Home? (CHK014).
- Document Activity recreation (rotation) handling — StateFlow survives? (CHK003).

**Priority 3 (documentation hygiene):**
- Add explicit deferral lines: "Accessibility → checklist-accessibility", "Localisation → checklist-localization" (CHK018, CHK019).
- Note mock data limitations for NFR-004 visual identity comparison (CHK022).
- Add double-tap/idempotency note for `InteractionSink.answer()` (CHK011).
