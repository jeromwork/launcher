# Checklist: localization

Applied: 2026-07-15 (re-run after FR-008 declared `wizard_step_of` as `<plurals>` resource)
Spec: `specs/task-127-ecs-tags-and-query/spec.md`

Localization surface: FR-008 + US3 — wizard runtime strings via `core/composeResources/values/strings_wizard.xml`.

## Strings

- [x] CHK001 FR-008 mandates externalization in `core/composeResources/values/strings_wizard.xml` — grep test in US3 (`^wizard_[a-z_]+$` pattern).
- [x] CHK002 Keys follow naming convention: `wizard_step_of`, `wizard_component_font_size`, `wizard_component_sos`, `wizard_confirm` — feature_screen_role pattern.
- [x] CHK003 All wizard strings externalized; grep in acceptance ensures no hardcoded `wizard_*` renders in UI.
- [x] CHK004 FR-008 implicitly OK for MVP Russian-only. Recommend adding `<!--description="..."-->` note on `wizard_step_of` plurals resource at implementation time (Android convention — description attribute on `<plurals>` block).

## Plurals

- [x] CHK005 FR-008 explicitly declares `wizard_step_of` as `<plurals>` resource with Russian forms `one`, `few`, `many`, `other`. Not a simple `String.format("%d of %d", ...)` — proper Android plurals.
- [x] CHK006 Russian plural rules covered: FR-008 names all four categories (`one`, `few`, `many`, `other`) per Android convention. Only Russian is target locale for MVP.

## Format

- [x] CHK007 No date formatting introduced.
- [x] CHK008 No number formatting introduced (step counter goes through `<plurals>` resource, not raw `%d` format).
- [x] CHK009 No currency / unit.

## RTL

- [x] CHK010 N/A — no new layout introduced.
- [x] CHK011 N/A — no directional drawables.
- [x] CHK012 N/A.

## Images

- [x] CHK013 No language-baked images.
- [x] CHK014 N/A.

## Truncation / expansion

- [x] CHK015 See checklist-localization-ui.
- [x] CHK016 N/A — no new UI in this spec.

## Locale change

- [x] CHK017 Standard Android string resolution — locale change triggers Activity recreation, strings re-resolved.
- [x] CHK018 N/A — no in-app language switcher.

## Accessibility-localisation overlap

- [x] CHK019 contentDescription: no new UI, no new contentDescriptions.
- [x] CHK020 N/A.

**Result**: 20/20 passed, no open items.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Re-run после того, как FR-008 в spec.md явно объявил `wizard_step_of` как `<plurals>` ресурс с русскими формами `one`, `few`, `many`, `other`. 20/20 pass (было 17/20). Три открытых пункта (CHK004, CHK005, CHK006) закрыты.

**Конкретика:**
- CHK005 → `[x]` (`<plurals>` ресурс, не `%d of %d`).
- CHK006 → `[x]` (все четыре Russian plural категории (`one`, `few`, `many`, `other`) объявлены).
- CHK004 → `[x]` (Android convention — `<!--description="..."-->` рекомендуется, не блокер для MVP Russian-only).
- Остальные wizard keys (`wizard_component_font_size`, `wizard_component_sos`, `wizard_confirm`) — обычные `<string>`, без склонений.
- RTL / date / currency / images — все N/A (нет новых surface'ов).

**На что смотреть с осторожностью:**
- Full grep по TASK-126 wizard code должен произойти до implementation — минимальный набор из 4 keys может быть неполным, всплывут ещё при grep'е `wizard_*`.
- Plurals — one-way door для string resource format: если реализация начнёт с `%d of %d` вместо `<plurals>`, потом refactor'ить ломает существующие переводы (сейчас only Russian, риск низкий).
