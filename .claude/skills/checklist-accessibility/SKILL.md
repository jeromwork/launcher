---
name: checklist-accessibility
description: Verifies accessibility requirements per WCAG 2.2 AA + Android Accessibility guidelines + Material Design — TalkBack semantics, contentDescription, focus order, contrast, tap target ≥ 48dp baseline (≥ 56dp project senior-safe override), large text scaling. Triggered by mentions of a11y, accessibility, TalkBack, screen reader, contentDescription, contrast, tap target, focus.
---

# Checklist: accessibility

Verifies the spec defines accessibility behaviour at the requirements level. Aligned with Article VIII of [`/.specify/memory/constitution.md`](/.specify/memory/constitution.md), [WCAG 2.2 AA](https://www.w3.org/TR/WCAG22/), [Android Accessibility guidelines](https://developer.android.com/guide/topics/ui/accessibility), and Material Design accessibility specs.

Note: a separate `checklist-elderly-friendly` covers project-specific senior-user constraints (Article VIII §7).

---

## Tap targets / interactive areas

- [ ] CHK001 Every interactive element has minimum tap area ≥ 48dp (Android baseline) — project default ≥ 56dp per spec 003 senior-safe override.
- [ ] CHK002 Tap area ≥ visible bounds when needed (custom hit area documented).

## Visual contrast

- [ ] CHK003 Text contrast ≥ 4.5:1 for normal text (WCAG 2.2 AA).
- [ ] CHK004 Large text (≥ 18sp regular / ≥ 14sp bold) contrast ≥ 3:1.
- [ ] CHK005 Non-text UI (icons, focus rings, important borders) contrast ≥ 3:1 (WCAG 2.2 AA non-text).
- [ ] CHK006 Theme overrides (dark, light, high-contrast) explicitly handled.

## Screen reader (TalkBack)

- [ ] CHK007 Every interactive element has `contentDescription` (or `Modifier.semantics { contentDescription = ... }`) — meaningful, not the bare label.
- [ ] CHK008 Decorative-only images marked `null` description (don't read).
- [ ] CHK009 Custom controls have `Role` semantics (button, checkbox, toggleable) defined.
- [ ] CHK010 Reading order matches visual order; explicit `traversalIndex` only when default fails.
- [ ] CHK011 TalkBack path to primary action ≤ 3 swipes per screen (per ADR-005 senior-safe).
- [ ] CHK012 State changes announced (`stateDescription`, `LiveRegion`).

## Text scaling / dynamic type

- [ ] CHK013 Text uses `sp` (not `dp`); fontScale 200% supported without truncation/clipping.
- [ ] CHK014 Layouts adapt to font scale — no fixed-height text containers that clip.
- [ ] CHK015 No text shrinking ("autoSize" to maintain layout) without user opt-in.

## Focus

- [ ] CHK016 Keyboard / D-pad / external-keyboard navigation works on every screen (TV remote, accessibility switch).
- [ ] CHK017 Focus trapped where appropriate (modal dialogs).
- [ ] CHK018 Focus indicator visible (3:1 contrast).

## Motion / time

- [ ] CHK019 Auto-dismissing UI (toast, snackbar) lasts ≥ 5s OR user-controllable per WCAG 2.2 timing.
- [ ] CHK020 Animations honour `Settings.Global.ANIMATOR_DURATION_SCALE` (reduce-motion).
- [ ] CHK021 No content flashes > 3 times/second (WCAG 2.3 — seizure safety).

## Errors / forms

- [ ] CHK022 Form errors associated programmatically with the input (`labelFor`, semantics).
- [ ] CHK023 Required fields announced as required by TalkBack.

## Test plan

- [ ] CHK024 At least one screen tested with Android Accessibility Scanner; failures listed.
- [ ] CHK025 At least one TalkBack walkthrough planned per primary US.

---

## How to apply

1. Walk every UI requirement.
2. Apply gates per element / screen.
3. Failures → add FR or acceptance criterion; "we'll add contentDescription later" is not acceptance.

## Output

Chat only — one red-only summary line per ADR-011 §5:
`checklist-accessibility: N/Total ✓, FAIL: CHK-XXX (short why)`.
Do NOT create `specs/<id>/checklists/accessibility.md`. A scratch buffer under that path is permitted during evaluation but must be deleted before returning. `.gitignore` (`specs/**/checklists/`) is safety net. Grey items land as edits to `spec.md` / `plan.md`, not in a checklist file.
