# Checklist: elderly-friendly
# spec: specs/task-126-wizard-runtime-migration/spec.md
# generated: 2026-07-11

Context: TASK-126 is explicitly a **technical refactoring without UX change** (spec: "технический рефакторинг без изменения UX"; NFR-004: "UX after migration MUST be visually identical to verification-evidence/task-120-xiaomi-first-launch.png"). Most visual/cognitive gates are inherited from the existing wizard UX and are N/A for new design decisions. This checklist focuses on: (a) what the migration *changes* that could regress elderly UX, (b) gaps where elderly-friendly requirements are unverified.

New UX surfaces introduced by TASK-126:
- Android SplashScreen API during `PresetBootstrap` initialization (CL-1, FR-001)
- `WizardScreen` Composable rewrite consuming `StateFlow<ReconcileState>` + `InteractionSink` (FR-008)
- Permission dialog deduplication — each dialog appears exactly once (US2)
- Wizard cancel + profile snapshot restore (US1.4)
- `PresetValidationException` UI path (SEQ-3, FR-006) — new failure mode exposed to user

---

## Visual

- [N/A] **CHK001** — N/A: No new body text introduced. NFR-004 mandates visual identity with pre-migration. Inherited font sizes must be preserved by implementation. No regression gate in spec.
- [N/A] **CHK002** — N/A: No new action labels. Inherited.
- [N/A] **CHK003** — N/A: No new tap targets. Inherited.
- [N/A] **CHK004** — N/A: No new element spacing. Inherited.
- [N/A] **CHK005** — N/A: No new color/contrast decisions. Theme Component introduced (FR-003) uses `paletteSeedHex` — contrast is a downstream concern for theme authoring, not this migration spec.

## Cognitive load

- [N/A] **CHK006** — N/A: Screen layout (one primary action) is unchanged. Inherited from TASK-120 UX.
- [ ] **CHK007** — PARTIAL: The spec introduces `required: Boolean` on pool components (FR-006, CL-3): "wizard завершён когда все `required=true` шаги Applied; необязательные предлагаются, не блокируют запуск." This means wizardFlow can have an unbounded number of steps. The spec does NOT require a progress indicator. For elderly users, a wizard with unknown length is disorienting. **Gap**: spec should require `WizardScreen` to display a step counter or progress bar (e.g. "Шаг 2 из 4") so the user knows how much is left. The number of steps is knowable at wizard start from `preset.wizardFlow.size`.
- [N/A] **CHK008** — N/A: No hidden gestures introduced. Wizard steps are linear.
- [N/A] **CHK009** — N/A: No new user-facing copy introduced directly in spec. Copy is driven by i18n keys in preset (e.g. `wizard.font.title`). The spec correctly uses i18n keys throughout.
- [x] **CHK010** — PASS: Preset model explicitly supports pre-filled defaults. `WizardBehavior.AutoApply` applies silently without user action. `WizardBehavior.InitialDefault` pre-populates choices. `required=false` components are optional — user is not forced to interact with them. This is good elderly-friendly design.

## Predictable navigation

- [N/A] **CHK011** — N/A: Core action placement unchanged. Inherited from existing wizard UX.
- [ ] **CHK012** — FAIL: The spec does not specify Android Back key behavior during wizard. US1.3 covers resume on reopen. US1.4 covers the "Cancel" button. But: what does the Android system Back key do on a wizard step screen? Options: (a) go to previous step, (b) show Cancel confirmation, (c) do nothing (intercept Back). For elderly users, pressing Back by accident could discard progress or trigger unintended navigation. The spec must declare: "Android Back during wizard step = [go to previous step / show Cancel confirmation / intercepted]." If Back goes to previous step, the spec must also confirm that backing past step 1 shows the Cancel confirmation rather than exiting the wizard silently.
- [x] **CHK013** — PASS: Cancel behavior is consistent. US1.4: cancel requires explicit "Отменить" button press + confirmation. Profile restored from snapshot. No surprise re-routing.

## Error recovery

- [ ] **CHK014** — FAIL: `PresetValidationException` (SEQ-3, FR-006) is thrown "before wizard starts" when `wizardFlow` ordering is violated. The spec defines what the system does (throws exception) but NOT what the user sees. For the primary user (elderly person using the device), this is an invisible crash or a blank screen. The spec must define: (a) what error screen/message is shown to the user, (b) what recovery action is offered ("Contact support", "Use default settings", "Retry"). Without this, a corrupted or mis-authored preset leaves the device unusable for a non-technical user — exactly the population this launcher targets.
- [x] **CHK015** — PASS: No error state requires restart. Edge cases all have graceful degradation: `hint-pool.json` missing → empty list (no crash); `PresetValidationException` throws before wizard but does not crash the app; force-close during wizard → resume from ProfileStore. ReconcileState.Loading while bootstrap runs → SplashScreen (no blank screen). **PASS** on no-restart requirement, but CHK014 gap means the user is stranded without a recovery action even if the app doesn't crash.
- [ ] **CHK016** — PARTIAL: US1.4 mentions "подтвердил отмену" — confirmation dialog exists. But the spec provides no copy guidance for the confirmation. For elderly users, the confirmation must be: (a) in simple Russian ("Вы хотите отменить настройку?"), (b) positive-action phrasing (not "Are you sure you don't want to continue?"), (c) clear consequences ("Ваши настройки не сохранятся"). The spec should include the confirmation copy in i18n key format at minimum.

## Sensory

- [x] **CHK017** — PASS: Android SplashScreen API (CL-1) respects system reduced-motion settings natively. No custom animations introduced in the spec. WizardScreen state transitions are driven by `StateFlow` which is display-framework-agnostic.
- [ ] **CHK018** — PARTIAL: `PresetValidationException` error state (CHK014 gap) — if it shows a red error message, it must also include an icon, not rely on color alone. Since the error UI is unspecified, this cannot be confirmed. Mark as gap pending CHK014 resolution.

## Time

- [x] **CHK019** — PASS: No timed challenges. SplashScreen waits indefinitely for `PresetBootstrap` completion (no timeout forcing restart). Wizard has no time limit. Permission dialogs are system-controlled and have no countdown.
- [N/A] **CHK020** — N/A: No auth sessions in this spec.

## Acceptance evidence

- [ ] **CHK021** — FAIL: No AC in the spec references senior-safe UX metrics (font size, tap area, contrast). SC-1 and SC-2 verify the wizard completes successfully and is "visually identical to pre-migration screenshot," but neither explicitly asserts elderly-UX preservation. Add AC: "WizardScreen displays step progress indicator (e.g. 'Шаг N из M') on every interactive step." and "Each wizard step screen passes visual identity check against verification-evidence/task-120-xiaomi-first-launch.png — font sizes, tap targets, and contrast unchanged from pre-migration."
- [ ] **CHK022** — FAIL: No test plan includes manual walkthrough simulating elderly use (slow tapping, reading glasses, potential Back key mis-press). SC-1/SC-2 require Xiaomi physical device testing but only assert functional completion. Add: "SC-X: Manual walkthrough on Xiaomi Redmi Note 11 — tester simulates elderly user (slow navigation, uses Back key, attempts Cancel) — no disorienting navigation, wizard completes or cancels cleanly with clear feedback."

---

## Summary

**Result: 5 PASS, 5 FAIL/PARTIAL, 10 N/A** (out of 22 gates)

Counts: **5 PASS, 4 FAIL, 1 PARTIAL** among applicable gates (12 non-N/A).

### Failures requiring action before implementation

1. **CHK012** — Declare Android Back key behavior during wizard steps. Options: intercept Back (show Cancel confirmation) or go to previous step (with Cancel confirmation at step 1). Must be explicit — elderly users press Back by accident.

2. **CHK014** — Specify user-facing error screen for `PresetValidationException`. The system must show a clear message ("Не удалось загрузить настройки") with a recovery action. This failure leaves the device unusable for the primary persona (elderly user on a fresh device) if the preset is corrupted.

3. **CHK021** — Add ACs asserting elderly-UX preservation: step counter, visual identity assertion beyond just "looks the same."

4. **CHK022** — Add manual walkthrough test plan: tester simulates elderly use on Xiaomi (Back key, slow tapping, Cancel flow).

### Partial issues (low-cost fixes)

5. **CHK007** — Add step counter/progress indicator requirement to `WizardScreen`. "Шаг N из M" label, derivable from `preset.wizardFlow.size` at wizard start.

6. **CHK016** — Add i18n key placeholders for Cancel confirmation copy with positive-phrasing requirement.

7. **CHK018** — Contingent on CHK014: ensure `PresetValidationException` error state uses icon + color (not color alone).

### Key note for implementers

NFR-004 ("visually identical to pre-migration screenshot") is necessary but not sufficient for elderly-friendly verification. A migration that preserves the visual appearance but changes Back key behavior, adds a new error screen without recovery action, or removes the progress indicator would pass NFR-004 while failing elderly-UX standards. The 4 failure items above are the specific gaps this migration must close in the spec before implementation.
