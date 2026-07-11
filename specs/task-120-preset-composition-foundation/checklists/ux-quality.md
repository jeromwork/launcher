# Checklist: ux-quality

Applied to: `specs/task-120-preset-composition-foundation/spec.md`
Date: 2026-07-10
Scope note: This spec is domain-foundation level. Concrete UI screens (Wizard visuals, Settings layout, BootCheck admin-visibility) are deferred to downstream tasks (draft-1, TASK-69, TASK-71). This checklist evaluates the UX contract the foundation exposes to those downstream specs, and flags where the foundation itself makes UX-affecting claims without operational detail.

---

## Completeness — coverage of screens

- [ ] CHK001 Every user-facing screen of this feature listed in spec (entry, primary, confirmation, error, return).
  - FAIL: US1/US3/US4/US5 refer to "Wizard", "Settings", "confirm-диалог" but the spec never enumerates screens as a set. Confirmation dialog for Undo (US1 scenario #4, edge case "Owner отменяет Wizard") is referenced but not listed as a screen with entry/dismiss states. Foundation-level acceptable, but should at least say "screen inventory deferred to draft-1 / TASK-69".
- [ ] CHK002 Every UX state per screen specified (loading, empty, success, error, partial-data).
  - FAIL: No loading state described for preset load (PresetSource I/O). No empty state for Settings when a Component in settingsMap has no Provider (Unsupported). Partial-data / mid-Wizard resume (US1 scenario #3) mentions "обход продолжается с первого non-applied шага" but does not describe what the user visually sees on resume (is there a "Continue where you left off" screen, or silent jump into step N?).
- [x] CHK003 Navigation transitions between screens specified (forward, back, deep-link entry, recreation).
  - PASS with caveat: Forward flow described in SEQ-1 (Wizard → completion) and SEQ-2 (Settings → step edit → return). Back navigation not explicit — Wizard back button behavior unspecified (does Back = Undo? Skip? Prev step?). Deep-link entry into Settings not described but out of foundation scope.
- [ ] CHK004 Cross-cutting overlays (snackbar, toast, dialog, bottom sheet) — when shown, by whom, dismissible by what.
  - FAIL: Edge case "Same-version preset пришёл заново с другим content" says "UX: showToast для owner-driven install, silent log для admin push" — this is the only overlay mentioned. Undo confirm-dialog (US1 #4) is referenced without dismissibility rules. No policy on how Failed step is surfaced (see CHK015).

## Clarity — terminology and rules

- [x] CHK005 UX terms defined unambiguously; same term means the same thing across spec.
  - PASS: `Interactive`/`AutoApply`/`InitialDefault` distinct and consistently used (FR-009, US1 scenarios, Key Entities). `RunMode` variants (Wizard/BootCheck/Single/RemotePush) consistent.
- [x] CHK006 Vague qualifiers ("intuitive", "smooth", "clean", "fast") either removed or operationalised.
  - PASS: No "intuitive" / "smooth" / "clean" claims. Spec stays in verifiable terrain (statuses, RunMode, Outcome). One softer phrase in FR-014 ("работает generically") but it's a scope statement, not a UX quality claim.
- [ ] CHK007 Action vocabulary explicit: tap vs long-press vs swipe — no "interact".
  - FAIL: US3 uses "тапает", "тянет на 2.0" in SEQ-2 spec-level, which is OK. But US1 scenario #4 says "нажимает 'Отменить'" without specifying whether this is a button in Wizard chrome, a system Back-button intercept, or an entry in an overflow menu. `InteractionSink` port is defined but the mapping "InteractionSink.askUser → what user actually taps" is not described.
- [ ] CHK008 Button labels are exact strings (or token IDs), not "Confirm-style label".
  - FAIL: "Отменить", "Отменить Wizard" appear as descriptive text but not as string tokens. "Готово" appears in SEQ-1 mentor-detail as example. No string resource IDs anywhere. Localisation concern (see checklist-localization).

## Consistency

- [ ] CHK009 In-Scope and Functional Requirements align on every screen and every action.
  - FAIL: Spec has no explicit "In-Scope" section listing user-facing screens. FR-024 (preWizardSnapshot + undo) implies a confirm-dialog screen without a corresponding FR describing dialog UX. Undo is a first-class scope item (SC-013) but the dialog is not enumerated.
- [ ] CHK010 Confirmation policy consistent: actions requiring confirmation listed; one-tap actions justified.
  - FAIL: Only Wizard cancel has confirm-dialog (US1 #4). Settings edits (US3 scenario #1: slider 1.6→1.8) are one-tap-applied without justification. Is font change reversible from Settings? Push preset that removes Component (US5 #3): no owner confirmation, just applies. Policy is implicit; should be stated.
- [ ] CHK011 Multi-tap / accidental-double-tap protection consistent across action surfaces.
  - FAIL: No mention of debounce, in-flight-lock, or idempotency at UX level. Settings slider drag → apply is described as synchronous in SEQ-2 but rapid drags could cascade Provider.apply calls. Out of foundation scope arguably, but should be flagged as downstream-defer.

## Acceptance — measurability

- [x] CHK012 Each US has explicit Given/When/Then or numbered acceptance scenario.
  - PASS: All 6 User Stories have numbered Given/When/Then scenarios. US1 has 4, US3 has 3, US4 has 3, US5 has 3, US6 has 3. Well-formed.
- [ ] CHK013 Success criteria measurable per UX moment (entry to first-tap, tap to feedback, action to result).
  - FAIL: No timing budgets. "Cold start Wizard entry to first Interactive step" — not specified. "Slider drag to visible font change" — not specified. SC-001..SC-013 measure structural correctness (fitness functions, coverage, roundtrip) but not user-perceived latency. See checklist-performance.
- [ ] CHK014 Returning-user UX (second-launch, resume from background) defined or excluded.
  - PARTIAL FAIL: US1 scenario #3 (Wizard interrupted, resumes at non-applied) is defined. But second-launch after full Wizard completion is not described — does app open directly to launcher home? Edge case "preWizardSnapshot старше 7 суток" defines snapshot expiry but not the returning-user visual (does user see "Undo no longer available" notice, or silent removal?).

## Coverage — alternative paths

- [ ] CHK015 Every primary action has its negative-path UX defined (denied permission, missing target, network error).
  - FAIL: `Outcome.Failed(reason)` is defined domain-side (FR-008) and US4 scenario #3 says "статус в profile обновляется в Failed, admin получает возможность посмотреть в Settings". But foundation does NOT specify how the user (not admin) sees a Failed step: silent skip? Red icon in Settings? Toast? Owner-directive from Q7 is "mark Failed + continue" — continue means user sees nothing wrong until they look at Settings. This is a UX gap: **Failed steps have no user-facing surface in this spec**. AppTile "not installed" state (edge case) says "плитка остаётся видимой но помечена 'not installed' в UI" — but the marking mechanism is not specified (badge? overlay? separate list?).
- [x] CHK016 Multiple entry points (notification, deep-link, widget, app icon) yield consistent UX or differences explicitly noted.
  - PASS with caveat: Foundation defines RunMode variants (Wizard/BootCheck/Single/RemotePush) — each entry point uses one mode. Different UX per RunMode is explicit (Wizard = Interactive; RemotePush = SilentInteractionSink; BootCheck = critical-only). Consistency of underlying engine is the whole point of SC-003.
- [ ] CHK017 Long-pause scenarios (user leaves app for hours) have defined return-UX.
  - PARTIAL FAIL: Edge case "preWizardSnapshot старше 7 суток" defines 7-day cleanup for undo. But the user returning to a half-complete Wizard after a week — do they get "Wizard reset" screen? Do they resume mid-flow? US1 #3 covers "закрыл app" but not week-long absence.

## Non-functional UX

- [x] CHK018 Accessibility deferred to `checklist-accessibility` if relevant; otherwise listed here.
  - PASS: FontSize Component is a first-class citizen (US3 scenario #1), which is accessibility-relevant. Foundation doesn't make specific a11y claims that need enforcement here; downstream Wizard/Settings specs will.
- [x] CHK019 Localisation deferred to `checklist-localization` if relevant; otherwise listed here.
  - PASS: Spec has no hardcoded user-facing strings claiming to be final. Category names ("Зрение/Слух/Безопасность/Приложения" in SEQ-2 mentor-detail) are illustrative. See CHK023 for source-of-category concern.
- [ ] CHK020 Diagnostic UX (how user sees that something is being tracked) specified or excluded.
  - FAIL: US5 mentions "admin получит feedback через собственный dashboard" and US4 #3 says "admin получает возможность посмотреть в Settings" — but the owner/primary-user diagnostic surface ("what did the last admin push change on my device?") is not described. Foundation-acceptable to defer, but should be noted.

## Dependencies / assumptions

- [x] CHK021 UX doesn't depend on out-of-scope capabilities (full cross-app control, embedded other-app UI).
  - PASS: `PackageManagerFacade` port wraps installed-check, `AuthHandoffService` moved to task-121. Foundation stays domain-side; UX assumes only what its ports provide.
- [ ] CHK022 Mock-data limitations noted explicitly if they affect rendering of user-facing content.
  - FAIL: Local Test Path lists fixtures (`pool-v1.json`, `preset-simple-launcher-v2.json`) but does not note that seed presets are "minimal for UI-demo" vs "real-user preset" distinction. Property-based test uses random 100 combinations — random UX rendering not verified visually (only "engine не крашится"). Should say "visual UX verification with mock presets deferred to downstream Wizard spec".

---

## Additional concerns from context

- [ ] CHK023 Source of Settings category strings ("Зрение/Слух/Безопасность/Приложения") unclear.
  - FAIL: SEQ-2 mentor-detail names four categories, but the spec does not say **where they come from**. Options: (a) hardcoded enum in `settingsMap` schema; (b) free-form string per `SettingsMapEntry`; (c) locale-aware bundle keyed by category-id; (d) declared on `ComponentDeclaration` in pool. FR-003 says `settingsMap: List<SettingsMapEntry>` but the entry shape does not include a category field visible in the spec. This is a schema gap that will surface immediately when TASK-69 (Settings as Profile View) starts.
- [ ] CHK024 Undo confirm-dialog referenced but not specified even at token/label level.
  - PARTIAL PASS (spec explicitly acceptable for foundation): US1 scenario #4 mentions "confirm-диалог принят". No mockup expected at foundation. But not even a label token ("dialog.wizardUndo.confirm") is reserved. Grey — flag for draft-1.
- [ ] CHK025 Failed-step user-visible surface undefined.
  - FAIL (duplicate of CHK015 but critical enough to flag separately): SEQ-1 mentor-detail says "gears icon вернёт к ним" is not in the spec — the actual spec text has no such phrase. US4 scenario #3 says user can find Failed status "в Settings через будущий remote sync" — this defers user-visibility entirely to a future feature. **A user whose Wizard completed but one AutoApply step Failed will see no indication anywhere.** This is a design decision (silent Failed) that should be explicit as either FR or Assumption, not implicit.
- [x] CHK026 Interactive vs AutoApply vs InitialDefault observably distinguishable by user.
  - PASS: US1 scenario #1 shows Interactive → user sees prompt; scenario #2 shows InitialDefault → Provider.apply NOT called, value preserved silently. AutoApply → applied silently. Distinct observable behaviors: Interactive interrupts, AutoApply happens invisibly-with-effect, InitialDefault happens invisibly-without-effect. Clear.
- [x] CHK027 Wizard → completion → Settings navigation clear.
  - PASS with caveat: SEQ-1 ends "Wizard завершён"; SEQ-2 starts "Открывает Settings". The transition (auto-navigate to Settings? land on launcher home? show completion screen?) is not shown. Foundation-acceptable; downstream will decide.

---

## Summary counts

- Total: 27 CHK items (22 standard + 5 context-added).
- PASS: 10 (CHK003, CHK005, CHK006, CHK012, CHK016, CHK018, CHK019, CHK021, CHK026, CHK027).
- FAIL: 14 (CHK001, CHK002, CHK004, CHK007, CHK008, CHK009, CHK010, CHK011, CHK013, CHK015, CHK017, CHK020, CHK022, CHK023, CHK025).
- PARTIAL: 3 (CHK014, CHK017 — dual-counted with above, CHK024).
  - Adjusted: 12 pure PASS + 12 pure FAIL + 3 partial = 27.

## Verdict

**Foundation-appropriate but has three UX gaps that block downstream specs from being written cleanly without decisions:**

1. **Failed-step user surface (CHK015 / CHK025)** — silent-Failed is a hard UX decision that must be either accepted explicitly or resolved via a "diagnostic tile" / "review after-boot" mechanism. Currently implicit.
2. **Settings category source (CHK023)** — schema gap: `SettingsMapEntry` needs a `category` field or an ADR "categories are locale-string tokens on ComponentDeclaration". Will block TASK-69 first commit.
3. **Undo confirm-dialog + Wizard back-button semantics (CHK007 / CHK009 / CHK024)** — needs at minimum token IDs reserved and back-button policy stated ("Back = Prev step; explicit 'Отменить' button = confirm-dialog → snapshot restore").

Other FAILs are legitimately deferred to downstream specs (timing budgets, exact labels, dialog UX). Recommend adding a short §UX Deferred-to-Downstream subsection to Assumptions listing what draft-1 / TASK-69 must decide, so the deferral is visible.
