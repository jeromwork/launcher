# Checklist: localization-ui

Spec: `specs/task-120-preset-composition-foundation/spec.md`
Date: 2026-07-10
Evaluator: AI (checklist-localization-ui skill)

Context: This spec is a **domain/foundation-level** spec (`core/preset/` KMP commonMain, zero Android). It defines ports, wire formats, and domain types — NOT concrete Composables / screens. However, it references UI-facing surfaces indirectly:
- Wizard screens (SEQ-1, US1)
- Settings screens (SEQ-2, US3) — with example categories in Russian ("Зрение / Слух / Безопасность / Приложения")
- Undo confirm dialog (edge case, US1 scenario #4)
- Component labels in `pool.json` declarations
- Wizard step titles (`wizardFlow` entries)

Because concrete UI surfaces will land in downstream tasks (draft-1 wizard refactor, TASK-69 Settings, TASK-71 hidden steps), most CHK-UI items apply to those downstream specs. But wire-format-level decisions made HERE bake in translatability (labelKey vs label, wizardTitleKey vs wizardTitle). Those decisions are load-bearing and MUST be checked now — silently shipping a `label: String` field in `pool.json` schema v2 is a wire-format-level regression that hurts every downstream feature.

---

## Length expansion

- [ ] CHK-UI-001 Each user-facing surface documents flex/wrap vs fixed/max width. **FAIL** — spec does not describe layout properties for Wizard steps, Settings tiles, or Undo dialog. Deferred to downstream specs (draft-1, TASK-69, TASK-71) but no forward pointer in this spec.
- [ ] CHK-UI-002 Max-character budget declared against longest supported language. **FAIL** — no budget for Component labels, category names, or wizard step titles. Since bundled `pool.json` and seed presets ship with `label` values (Russian in owner examples: "Видеозвонок", "Зрение", "Слух"), the budget question is already relevant.
- [N/A] CHK-UI-003 `Text` with `maxLines=1` + missing overflow — no concrete Composable in this spec.
- [N/A] CHK-UI-004 Multi-line button/tile labels reserve ≥2 lines — no concrete Composable in this spec.

## Mock-screenshots (minimum 3 locales)

- [ ] CHK-UI-005 Mock/sketch/Figma for each new screen in ≥3 locales (EN, DE/RU, AR/HE). **FAIL** — spec has zero mock references. Wizard, Settings, and Undo dialog are behavior-described only. Downstream specs must supply mocks.
- [ ] CHK-UI-006 Longest-string-locale mock shows no clipping/overlap/collapse. **FAIL** — no mocks exist to verify.
- [ ] CHK-UI-007 RTL locale mock shows mirrored drawables and start/end alignment. **FAIL** — no mocks exist to verify.

## RTL specifics

- [ ] CHK-UI-008 Directional drawables `autoMirrored = true` OR per-locale variants planned. **FAIL** — spec does not mention RTL at all. No `autoMirrored` policy for wizard back-arrow, settings chevron, or category list chevron. This is a foundation-level gap: `pool.json` `iconRef` field (implicit in ComponentDeclaration) needs a policy whether icon assets carry RTL mirroring metadata.
- [ ] CHK-UI-009 Custom layouts / gestures acknowledge RTL or document out-of-scope. **FAIL** — no acknowledgement anywhere in spec. Wizard swipe direction (next/back), Settings list swipe-to-delete, slider direction (SEQ-2 FontSize slider) — none discussed. Deferred to downstream specs BUT the spec should at least state "RTL layout resilience is downstream concern; wire format carries no directional assumption".
- [ ] CHK-UI-010 Number formatting + currency follow locale. **PARTIAL/N/A** — spec mentions `scale=1.6`, `scale=1.8`, `scale=2.0` in scenarios. These are numeric parameters displayed by future Composables. Formatting policy (RU `1,6` vs EN `1.6`) not declared. Flag for downstream.

## Plural rule variations

- [N/A] CHK-UI-011 Count-driven label uses `plurals` resource — no count-driven labels in this spec (no "N applications installed", "N steps remaining" etc.). Downstream wizard progress ("Шаг 3 из 5") will need this.
- [N/A] CHK-UI-012 Layout reserves space for widest plural form — same as above, deferred.

## Line-height / vertical fit

- [N/A] CHK-UI-013 Content-based height (wrap_content) on translated-text containers — no Composable in this spec.
- [N/A] CHK-UI-014 Vertical padding sufficient for tall scripts (AR/HI/TH) — no Composable in this spec.

## Senior-safe overlap (project-specific)

- [ ] CHK-UI-015 Minimum font size (16sp baseline / 20sp senior preset) + 30% expansion does NOT push critical actions below tap-target threshold. **FAIL** — spec mentions FontSize `scale=1.6`/`1.8`/`2.0` as user-configurable but does not describe how translated Settings tile labels + font scale interact. Wizard "Отменить" button must remain reachable at scale=2.0 + Russian text. Deferred to downstream but the interaction is spec-level relevant because FontSize IS a MVP Component defined here.
- [ ] CHK-UI-016 Test against `fontScale=2.0`. **FAIL** — not mentioned. Since FontSize scale up to 2.0 is a first-class Component parameter, this MUST be tested for every downstream screen. Foundation spec should note this as invariant.

## Locale change at runtime

- [ ] CHK-UI-017 Behaviour on runtime locale change documented. **FAIL** — spec does not address: what happens when OS locale changes mid-Wizard? Does `preWizardSnapshot` capture locale? Does `activeComponents` carry locale-dependent state? Wire-format-level relevant: `pool.json` labels should be locale-independent keys, resolved at render time.
- [ ] CHK-UI-018 No cached pre-rendered text bitmaps surviving locale change. **N/A** — no rendering in this spec.

## Translation deferral path

- [ ] CHK-UI-019 If translation deferred, spec declares MVP vs follow-up language coverage. **FAIL** — spec does not declare supported languages. Given owner examples use Russian ("Зрение", "Слух", "Безопасность", "Приложения", "Видеозвонок"), RU appears to be primary; EN as fallback assumed. This should be explicit.

---

## Wire-format-level concerns (foundation-specific, added by evaluator)

These are NOT stock CHK-UI items but arise because this is a wire-format spec:

- [ ] **W-1 — Component labels: key vs literal.** `ComponentDeclaration` (Key Entities) does not specify whether the `label` shown to user is a **literal string** or an **i18n key**. Owner examples show Russian literals ("Видеозвонок"). If wire format carries literal strings, sharing a preset from RU device to EN device shows Russian labels. **RECOMMENDATION**: `pool.json` declarations carry `labelKey: String` (resolves through app i18n bundle) OR structured `labels: Map<LocaleTag, String>` (embedded translations) — with `labelKey` preferred (smaller wire format, single source of truth in app). Owner-provided `paramsOverride` can override the resolved literal for custom cases. **FAIL** — not addressed in FR-001..FR-025 or Key Entities.
- [ ] **W-2 — Settings category names: key vs literal.** `settingsMap` entries reference a "category" for grouping ("Зрение / Слух / Безопасность / Приложения" per SEQ-2 MENTOR-DETAIL). Wire format should carry `categoryKey: String` (enum-like: `vision`, `hearing`, `safety`, `apps`), NOT the literal Russian string. **FAIL** — not addressed. Risk: shipping `pool.json` v2 with literal "Зрение" as category value locks Russian into wire format v2, breaks rule 5 backward-compat when EN localization ships.
- [ ] **W-3 — Wizard step titles / subtitles: key vs literal.** `wizardFlow` entries need a title ("Выберите размер шрифта" per SEQ-1). Wire format concern identical to W-1/W-2. **FAIL** — not addressed. FR-003 lists `wizardFlow: List<WizardFlowEntry>` without specifying `titleKey` policy.
- [ ] **W-4 — `paramsOverride` for user-supplied literals** (e.g. custom AppTile label = "Внук Петя"). This IS legitimate literal because user-authored, but wire format should distinguish `labelKey` (pool-provided, i18n-resolved) from `customLabel` (user-authored, verbatim). **FAIL** — FR-004 allows `paramsOverride` on all three fields but does not distinguish user-authored literals from developer-authored keys. Risk: shareable preset with `paramsOverride: {label: "Внук Петя"}` shared across locales shows Russian on EN device — but this is arguably expected (user chose it). Needs explicit policy.
- [ ] **W-5 — RTL neutrality of wire format.** Wire format (`pool.json`, `preset.json`, `profile.json`) should carry no directional assumption (no `alignLeft`, `swipeRight`). **PASS-BY-OMISSION** — spec does not introduce directional fields. Note as satisfied by absence, flag for downstream to preserve.
- [ ] **W-6 — Locale-independent numeric encoding.** Parameters like FontSize `scale=1.6` in wire format use `.` decimal separator (JSON standard). Displayed value formatting is a rendering concern, not a wire-format concern. **PASS** by JSON spec.

---

## Summary counts

- Total CHK items evaluated: 19 (CHK-UI-001..019) + 6 wire-format items (W-1..W-6) = 25
- PASS: 1 (W-5), 1 (W-6) = 2
- N/A: 6 (CHK-UI-003, 004, 011, 012, 013, 014, 018) = 7
- FAIL: 16

## Top issues (by impact)

1. **W-1/W-2/W-3 (wire format literal-vs-key policy)** — foundation-blocking. Must be resolved before `pool.json` v2 ships. Otherwise every downstream spec inherits ambiguity.
2. **CHK-UI-019 (translation deferral policy)** — spec should declare RU primary / EN fallback / others deferred, and link to F-3 fitness function.
3. **CHK-UI-008/009 (RTL acknowledgement)** — spec should state "RTL out of scope for MVP; wire format carries no directional assumption (see W-5); downstream Composable specs must address layout mirroring". One paragraph suffices.
4. **CHK-UI-015/016 (font scale × translation × tap target)** — foundation spec defines FontSize scale 1.0..2.0 as MVP Component; must note that downstream Composable specs verify tap target ≥56dp at scale=2.0 + longest Russian label.
5. **CHK-UI-005/006/007 (mocks)** — no mocks in foundation spec (acceptable for domain-only spec). Downstream specs (draft-1 Wizard, TASK-69 Settings) MUST supply mocks in ≥3 locales.

## Recommended follow-ups

- Add to spec.md `Assumptions`: "Wire format uses **i18n keys** for developer-authored labels (`labelKey`, `categoryKey`, `titleKey`); user-authored literals via `paramsOverride` remain verbatim across locales."
- Add to spec.md `Assumptions`: "Primary language RU; fallback EN; other locales additive via app resource bundles, no wire-format change."
- Add to spec.md `Assumptions`: "RTL layout resilience deferred to downstream Composable specs; foundation wire format carries no directional assumption."
- Add follow-up backlog entry: "Localization-UI verification for all downstream preset consumers (draft-1, TASK-69, TASK-71) — mocks in EN/RU/AR, fontScale=2.0 tap-target test".
