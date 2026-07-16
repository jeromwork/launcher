---
name: checklist-localization-ui
description: Verifies UI/UX is resilient to localization realities — different word lengths (RU/DE ~30-40% longer than EN), RTL (AR/HI), plural rule variations, line-height differences. Complements checklist-localization (which covers string tables / format / plurals) by focusing on layout robustness. Triggered by any spec with UI Composables, screens, tiles, buttons, dialogs, wizard steps.
---

# Checklist: localization-ui

Verifies the UI **survives translation** — not just whether strings are externalised (that's `checklist-localization`), but whether the **layout** holds up when those strings expand, contract, or flip direction.

Per project decision 2026-06-15: localization is mandatory from day 1. Translations themselves can be done later, but **localizability of UI must be checked at every spec**.

---

## Length expansion

- [ ] CHK-UI-001 Each user-facing surface (button, tile, label, dialog title) documents whether the layout uses **flex / wrap** or has a **fixed/max width**.
- [ ] CHK-UI-002 If fixed/max width — explicit max-character budget declared (e.g. "button label ≤ 12 chars after translation"), and the budget chosen against the **longest** of supported languages (typically DE/RU ~+30-40% vs EN; AR/JA can be shorter but with taller glyphs).
- [ ] CHK-UI-003 No `Text` with `maxLines=1` + no overflow handling (`TextOverflow.Ellipsis` or `.basicMarquee()`).
- [ ] CHK-UI-004 Multi-line button / tile labels — minimum 2 lines reserved in vertical space.

## Mock-screenshots (minimum 3 locales)

- [ ] CHK-UI-005 Spec includes (or references) mock-screenshot / sketch / Figma for **each new screen** in **at least 3 locales**:
  - **EN** (baseline short),
  - **DE or RU** (long expansion),
  - **AR or HE** (RTL).
- [ ] CHK-UI-006 Mock for **longest-string locale** does NOT show: clipped text, overlapping siblings, layout collapse, ellipsis on critical labels.
- [ ] CHK-UI-007 Mock for **RTL locale** shows: mirrored directional drawables (back arrow, list chevron), `start`/`end` alignment, NOT `left`/`right`.

## RTL specifics

- [ ] CHK-UI-008 Directional drawables marked `autoMirrored = true` OR explicit per-locale variants planned.
- [ ] CHK-UI-009 Custom layouts (canvas drawing, gestures, swipe directions) acknowledge RTL or document RTL-out-of-scope with rationale.
- [ ] CHK-UI-010 Number formatting + currency placement follow locale (e.g. `1 234,56 ₽` RU vs `$1,234.56` US vs `1.234,56 €` DE).

## Plural rule variations

- [ ] CHK-UI-011 Any count-driven label uses `plurals` resource — NOT `String.format("%d items", count)`.
- [ ] CHK-UI-012 Layout reserves space for the **widest plural form** (RU has 3 forms, AR has 6; some forms 2-3× longer than others).

## Line-height / vertical fit

- [ ] CHK-UI-013 Layout uses content-based height (`wrap_content` / Compose intrinsic), NOT fixed-pixel heights, on any container holding translated text.
- [ ] CHK-UI-014 AR/HI/TH glyphs are taller than Latin — vertical padding sufficient for tall script (recommended: ≥1.4× line-height multiplier).

## Senior-safe overlap (project-specific)

- [ ] CHK-UI-015 Project-specific minimum font size (16sp baseline / 20sp senior preset) + 30% expansion does NOT push critical actions below tap-target threshold (≥48dp baseline / ≥56dp senior).
- [ ] CHK-UI-016 No text fits only because `fontScale=1.0` — explicitly test against `fontScale=2.0` (Android max).

## Locale change at runtime

- [ ] CHK-UI-017 Behaviour on runtime locale change documented (Activity recreation, string re-resolution path).
- [ ] CHK-UI-018 No cached pre-rendered text bitmaps that survive locale change.

## Translation deferral path

- [ ] CHK-UI-019 If translation is deferred (only RU+EN at MVP, other languages later), spec declares which strings ship in MVP vs follow-up — and the CI fitness function (from F-3) is configured to allow this gap explicitly, not silently fall back.

---

## How to apply

1. Walk each new screen / Composable / dialog in the spec.
2. For each, mentally render in EN-short, DE-long, AR-RTL.
3. If a checkpoint fails — propose layout change (flex, wrap, larger max-width, marquee, etc.) BEFORE the spec is accepted.

## Output

Chat only — one red-only summary line per ADR-011 §5:
`checklist-localization-ui: N/Total ✓, FAIL: CHK-XXX (short why)`.
Do NOT create `specs/<id>/checklists/localization-ui.md`. Scratch buffer permitted, must be deleted before returning. Grey items land as edits to `spec.md` / `plan.md`.

## When to refuse

If a spec adds a new screen but contains:
- no mock-screenshot/sketch reference,
- no max-character budget for fixed-width labels,
- no RTL acknowledgement,

→ refuse the spec until the spec author adds them. This is cheaper than rebuilding UI three months later when actual translations arrive.

## Relationship to other skills

- **`checklist-localization`** — covers string tables, plural resources, format APIs, RTL drawables (the *plumbing*).
- **`checklist-localization-ui`** (this skill) — covers whether the **layout** survives the realities those strings impose (the *UX*).
- Both run on any spec with UI.
