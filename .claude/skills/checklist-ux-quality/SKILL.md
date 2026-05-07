---
name: checklist-ux-quality
description: Verifies UX requirements for completeness, clarity, consistency, and acceptance-criteria measurability. Catches missing screens, ambiguous interaction language, and unverifiable "intuitive"/"smooth" claims. Triggered when spec mentions screen, UI, Composable, button, tap, gesture, wizard, picker, navigation, user flow.
---

# Checklist: ux-quality

Verifies the **UX requirements** are complete, clear, and verifiable — not the visual design. Aligned with Article VIII and §III.1 of [`/.specify/memory/constitution.md`](/.specify/memory/constitution.md), and Material Design guidelines.

Reference: [`specs/002-whatsapp-tile-return/checklists/ux.md`](specs/002-whatsapp-tile-return/checklists/ux.md).

---

## Completeness — coverage of screens

- [ ] CHK001 Every user-facing screen of this feature listed in spec (entry, primary, confirmation, error, return).
- [ ] CHK002 Every UX state per screen specified (loading, empty, success, error, partial-data).
- [ ] CHK003 Navigation transitions between screens specified (forward, back, deep-link entry, recreation).
- [ ] CHK004 Cross-cutting overlays (snackbar, toast, dialog, bottom sheet) — when shown, by whom, dismissible by what.

## Clarity — terminology and rules

- [ ] CHK005 UX terms defined unambiguously; same term means the same thing across spec.
- [ ] CHK006 Vague qualifiers ("intuitive", "smooth", "clean", "fast") either removed or operationalised (e.g. "≤ 600ms cold start").
- [ ] CHK007 Action vocabulary explicit: tap vs long-press vs swipe — no "interact".
- [ ] CHK008 Button labels are exact strings (or token IDs), not "Confirm-style label".

## Consistency

- [ ] CHK009 In-Scope and Functional Requirements align on every screen and every action — no FR for a screen not in In-Scope, no In-Scope item without an FR.
- [ ] CHK010 Confirmation policy consistent: actions requiring confirmation listed; one-tap actions justified.
- [ ] CHK011 Multi-tap / accidental-double-tap protection consistent across action surfaces.

## Acceptance — measurability

- [ ] CHK012 Each US has explicit Given/When/Then or numbered acceptance scenario.
- [ ] CHK013 Success criteria measurable per UX moment (entry to first-tap, tap to feedback, action to result).
- [ ] CHK014 Returning-user UX (second-launch, resume from background) defined or excluded.

## Coverage — alternative paths

- [ ] CHK015 Every primary action has its negative-path UX defined (denied permission, missing target, network error).
- [ ] CHK016 Multiple entry points (notification, deep-link, widget, app icon) yield consistent UX or differences explicitly noted.
- [ ] CHK017 Long-pause scenarios (user leaves app for hours) have defined return-UX.

## Non-functional UX

- [ ] CHK018 Accessibility deferred to `checklist-accessibility` if relevant; otherwise listed here.
- [ ] CHK019 Localisation deferred to `checklist-localization` if relevant; otherwise listed here.
- [ ] CHK020 Diagnostic UX (how user sees that something is being tracked) specified or excluded.

## Dependencies / assumptions

- [ ] CHK021 UX doesn't depend on out-of-scope capabilities (full cross-app control, embedded other-app UI).
- [ ] CHK022 Mock-data limitations noted explicitly if they affect rendering of user-facing content.

---

## How to apply

1. Walk every screen + every action.
2. Apply gates.
3. Failures → add FR / AC; or add to Out-of-Scope with reason.

## Output

Inline into `specs/<id>/checklists/ux-quality.md`.
