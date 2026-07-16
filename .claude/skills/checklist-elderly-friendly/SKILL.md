---
name: checklist-elderly-friendly
description: Verifies UX is suitable for elderly / cognitive-load-sensitive / low-vision / reduced-dexterity users — the project's primary persona. Goes beyond generic accessibility (WCAG) to project-specific senior-safe rules per Article VIII §7. Triggered by mentions of elderly, senior, large text, simplified, cognitive load, Article VIII, senior-safe.
---

# Checklist: elderly-friendly

Verifies the spec respects the project's **primary persona**: elderly users with low vision, reduced dexterity, and cognitive-load sensitivity. Aligned with Article VIII §7 of [`/.specify/memory/constitution.md`](/.specify/memory/constitution.md) and [`docs/product/senior-safe-launcher-plan.md`](docs/product/senior-safe-launcher-plan.md).

This is **stricter than generic accessibility** — Article VIII §7: "If a design is elegant for experts but confusing for elderly users, the elderly-friendly design wins by default unless a documented product constraint says otherwise."

Reference: [`specs/002-whatsapp-tile-return/checklists/elderly-friendly-ux.md`](specs/002-whatsapp-tile-return/checklists/elderly-friendly-ux.md).

---

## Visual

- [ ] CHK001 Body text ≥ 18sp (project senior-safe override; WCAG would accept 14sp).
- [ ] CHK002 Primary action labels ≥ 16sp.
- [ ] CHK003 Tap targets ≥ 56dp (project override; WCAG 2.2 baseline is 24px).
- [ ] CHK004 Spacing between interactive elements ≥ 16dp (avoid mis-tap).
- [ ] CHK005 Contrast ≥ 4.5:1 universally — no "decorative" low-contrast text.

## Cognitive load

- [ ] CHK006 Each screen has ONE primary action; secondary actions visually subdued.
- [ ] CHK007 Onboarding / wizards have ≤ 3 steps OR explicit progress indicator.
- [ ] CHK008 No hidden gestures (swipe-from-edge for hamburger, long-press menus) for primary flows.
- [ ] CHK009 Plain-language copy: no jargon ("authenticate" → "sign in"), no negation in confirmations ("Are you sure you don't want to...").
- [ ] CHK010 Default values pre-filled where possible to minimise required input.

## Predictable navigation

- [ ] CHK011 Core actions have **consistent placement** across screens.
- [ ] CHK012 Back behaviour matches user expectation (Android Back goes "up", not "exit").
- [ ] CHK013 No surprise re-routing (action that was "Cancel" yesterday isn't "Delete" today).

## Error recovery

- [ ] CHK014 Every error has a clear recovery action ("Try again", "Go to settings", "Get help").
- [ ] CHK015 No error states that require app restart to leave.
- [ ] CHK016 Destructive actions (delete, remove) have confirmation; confirmation copy is positive ("Remove contact" — clear) not threatening ("This cannot be undone!").

## Sensory

- [ ] CHK017 Animation optional / reduced-motion-aware (Article VIII §5).
- [ ] CHK018 No reliance on colour alone (red error + icon, green success + checkmark).

## Time

- [ ] CHK019 No timed challenges (verification codes with countdowns < 60s, dismiss-now toasts, etc.).
- [ ] CHK020 Sessions / authenticated state generously timed; re-auth events explained.

## Acceptance evidence

- [ ] CHK021 Each US for a primary action has acceptance criterion citing senior-safe metrics (font size, tap area, contrast).
- [ ] CHK022 Test plan includes manual walkthrough by someone simulating elderly use (squinting, slow tapping, voice-over).

---

## How to apply

1. Walk every UI surface.
2. Apply gates with the worse case in mind: 70-year-old with reading glasses on a busy bus.
3. Failures → simplify, never add explanation copy as fix.

## Output

Chat only — one red-only summary line per ADR-011 §5:
`checklist-elderly-friendly: N/Total ✓, FAIL: CHK-XXX (short why)`.
Do NOT create `specs/<id>/checklists/elderly-friendly.md`. Scratch buffer permitted, must be deleted before returning. Grey items land as edits to `spec.md` / `plan.md`.
