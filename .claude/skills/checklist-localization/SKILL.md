---
name: checklist-localization
description: Verifies the spec respects internationalisation (i18n) and localisation (l10n) per ADR-004 — all user-facing strings externalised, plural rules, RTL readiness, locale-aware formatting, no language-baked images. Triggered by mentions of string, locale, translation, i18n, RTL, plural, format, ADR-004.
---

# Checklist: localization

Verifies the spec is **localisation-ready** — strings externalised, formats locale-aware, layout RTL-safe. Aligned with [`docs/adr/ADR-004-localization-and-global-readiness.md`](docs/adr/ADR-004-localization-and-global-readiness.md).

---

## Strings

- [ ] CHK001 All user-facing strings in `compose-resources/values/strings_<feature>.xml` (or single `strings.xml` per project convention) — no hardcoded strings in Composables.
- [ ] CHK002 String IDs follow naming convention (`<feature>_<screen>_<role>`), no `text_1` / `label_a`.
- [ ] CHK003 Strings used in only one place still externalised — implementer doesn't get to decide "this is internal".
- [ ] CHK004 Translator notes (`<!--description="..."-->`) included for ambiguous strings ("OK" can be 5+ words in some languages).

## Plurals

- [ ] CHK005 Every count-dependent string uses `plurals` resource, not `String.format("%d items", count)`.
- [ ] CHK006 Plural categories (`one`, `few`, `many`, `other`) considered for languages with > 2 plural forms (Russian, Polish, Arabic).

## Format

- [ ] CHK007 Date formatting via `DateTimeFormatter.ofLocalizedDate(...)` / Compose-Multiplatform locale-aware utility — no hardcoded `dd.MM.yyyy`.
- [ ] CHK008 Number formatting via `NumberFormat.getInstance(Locale.getDefault())`.
- [ ] CHK009 Currency / unit formatting locale-aware where applicable.

## RTL (right-to-left)

- [ ] CHK010 Layout uses `start` / `end` directional attributes, not `left` / `right`.
- [ ] CHK011 Drawables that have a directional meaning (back arrow, list arrow) flip in RTL — `autoMirrored = true` or explicit per-locale variants.
- [ ] CHK012 Custom layouts (canvas drawing, gestures) acknowledge RTL or document RTL-out-of-scope.

## Images / non-text content

- [ ] CHK013 No language-baked images (text rendered in source PNG/SVG).
- [ ] CHK014 If language-specific image required: per-locale resource folders structured.

## Truncation / expansion

- [ ] CHK015 Layouts tested for ~30% text expansion (German, Russian) without breaking.
- [ ] CHK016 No fixed-width buttons that clip translated labels.

## Locale change

- [ ] CHK017 Behaviour on runtime locale change documented (Activity recreation, string re-resolution).
- [ ] CHK018 In-app language switcher (if planned): persistent storage and Activity-recreate path defined.

## Accessibility-localisation overlap

- [ ] CHK019 `contentDescription` strings are localised.
- [ ] CHK020 TalkBack reads localised content correctly (no string-concat that breaks grammar).

---

## How to apply

1. Walk every user-facing string and every formatted value.
2. Apply gates.
3. Failures → externalise, switch to plural / locale-aware formatter, or document exclusion.

## Output

Chat only — one red-only summary line per ADR-011 §5:
`checklist-localization: N/Total ✓, FAIL: CHK-XXX (short why)`.
Do NOT create `specs/<id>/checklists/localization.md`. Scratch buffer permitted, must be deleted before returning. Grey items land as edits to `spec.md` / `plan.md`.
