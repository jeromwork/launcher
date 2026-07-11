# Checklist: localization-ui — task-126 Wizard Runtime Migration

Layout resilience under translation. Complements `localization.md` (which covers string tables / format / plurals).

Spec: `specs/task-126-wizard-runtime-migration/spec.md` (Draft, 414 lines).

**Context**: TASK-126 is a runtime-engine refactor. UX is required to be visually identical to `verification-evidence/task-120-xiaomi-first-launch.png` (NFR-004) — Xiaomi is Russian locale. Layouts inherited from TASK-120. No new screens introduced; but three Component subtypes (`LauncherRole`, `StatusBarPolicy`, `Language`) get their own wizard step surfaces + hint overlays + new `wizardPresentation` sizing (CL-2).

## Length expansion

- [ ] CHK-UI-001 Flex/wrap vs fixed width. **FAIL**: spec does not state layout policy for step CTA buttons, preset picker chips, hint overlay text, or `PresetValidationException` error surface. `wizardPresentation.typographyScale` (FR-003) is defined but does not constrain button geometry.
- [ ] CHK-UI-002 Max-char budget. **FAIL**: no character budget declared for any user-facing surface. RU (shipped) and DE (shipped locale of `strings_wizard.xml`) both expand ~30-40% vs EN. `wizard_next`/`wizard_skip` DE ("Weiter"/"Überspringen") is significantly longer.
- [ ] CHK-UI-003 maxLines=1 without overflow. **WARN**: not enforced by spec. Legacy TASK-120 layouts may or may not use `TextOverflow.Ellipsis` — spec silent.
- [ ] CHK-UI-004 Multi-line button reserves 2 lines. **WARN**: not addressed. Hint overlays are especially at risk on long AR/HI translations.

## Mock-screenshots (minimum 3 locales)

- [ ] CHK-UI-005 Mocks in ≥3 locales. **FAIL**: only one screenshot referenced (`verification-evidence/task-120-xiaomi-first-launch.png` — Russian). No DE-long, no AR-RTL mock referenced. Given the refactor claim "UX identical", regression will be measured only against RU on Xiaomi.
- [ ] CHK-UI-006 Longest-string mock clean. **FAIL**: no DE/RU verification-evidence artifact requested by NFR-004. Layout regression on long strings would ship undetected.
- [ ] CHK-UI-007 RTL mock. **FAIL**: no AR mock. `values-ar/strings_wizard.xml` ships per localization.md CHK005; layout untested in RTL.

## RTL specifics

- [ ] CHK-UI-008 `autoMirrored = true`. **FAIL**: no FR requires directional drawables be `autoMirrored`. Wizard navigation implies back/next arrows.
- [ ] CHK-UI-009 Custom layouts acknowledge RTL. **FAIL**: neither in-scope (test) nor out-of-scope (documented). Consistent with prior gap (localization.md CHK012).
- [x] CHK-UI-010 Number formatting per locale. N/A — no numbers displayed in wizard/BootCheck UI (see localization.md CHK008).

## Plural rule variations

- [x] CHK-UI-011 Plurals resource. N/A — no count-driven labels in this spec (localization.md CHK005).
- [x] CHK-UI-012 Space for widest plural form. N/A — same reason.

## Line-height / vertical fit

- [ ] CHK-UI-013 Content-based height. **WARN**: not asserted. `wizardPresentation.typographyScale` (FR-003) modifies scale — vertical fit under scale × language expansion is compounding risk unaddressed.
- [ ] CHK-UI-014 Tall-glyph padding. **WARN**: not addressed. AR/HI shipped; taller glyphs not accommodated in spec.

## Senior-safe overlap (project-specific)

- [ ] CHK-UI-015 Font-size + expansion vs tap target. **FAIL**: senior preset baseline (20sp) + DE expansion (30-40%) can push CTA labels beyond intended button width. Spec does not enforce ≥48dp baseline / ≥56dp senior tap target for step CTAs, cancel buttons, or hint dismiss controls.
- [ ] CHK-UI-016 fontScale=2.0. **FAIL**: no requirement to test/verify wizard at `fontScale=2.0`. Elderly primary user is the target audience — high fontScale is expected.

## Locale change at runtime

- [ ] CHK-UI-017 Behaviour on runtime locale change. **FAIL**: FR-004 uses `AppCompatDelegate.setApplicationLocales()` which triggers Activity recreation mid-wizard. Spec does not describe UI reflow after recreation. See localization.md CHK017 same finding.
- [x] CHK-UI-018 No cached pre-rendered text bitmaps. PASS — no such caching introduced by refactor.

## Translation deferral path

- [x] CHK-UI-019 Translation-deferred strings declared. PASS — `procedure-translate-spec-strings` skill (referenced in CLAUDE.md) handles the delta between EN base and 9 auto-managed locales; RU manual. Pattern already in place — no per-locale deferral needed for this spec.

---

## Summary

- **Pass**: 4/19 (CHK-UI-010, CHK-UI-011, CHK-UI-012, CHK-UI-018, CHK-UI-019 — mostly N/A)
- **Warn**: 4/19 (CHK-UI-003, CHK-UI-004, CHK-UI-013, CHK-UI-014)
- **Fail**: 11/19 (CHK-UI-001, 002, 005, 006, 007, 008, 009, 015, 016, 017)

**Verdict**: layout resilience is **not verified** by this spec. NFR-004 anchors only against a RU screenshot on Xiaomi. Consistent with `localization.md` (11/20 pass) — the spec inherits TASK-120's known gaps without closing them.

**Action items before implementation** (align with localization.md):
1. Extend NFR-004 to require verification-evidence in RU + DE (long) + AR (RTL) at least for the three new Component step surfaces (`LauncherRole`, `StatusBarPolicy`, `Language`).
2. Declare max-char budgets for step CTAs and hint overlay text; require `TextOverflow.Ellipsis` + minimum 2-line reservation on wizard buttons.
3. Add explicit senior-safe test: wizard at `fontScale=2.0` × DE locale × senior typographyScale, tap targets ≥ 56dp.
4. Document mid-wizard locale-change UI reflow behaviour (Activity recreation via `AppCompatDelegate.setApplicationLocales()`).
5. Assert `autoMirrored=true` on directional drawables introduced by any new step surface.

**Note**: many failures are pre-existing gaps inherited from TASK-120, not new regressions. If the owner accepts them as "TASK-120 legacy" — record explicit deferral in spec.
