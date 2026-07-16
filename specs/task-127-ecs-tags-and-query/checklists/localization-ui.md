# Checklist: localization-ui

Applied: 2026-07-15
Spec: `specs/task-127-ecs-tags-and-query/spec.md`

Note: This spec introduces no new UI. Only touches existing wizard runtime strings (rendered in WizardHostActivity per TASK-126) and existing HomeActivity tile grid (per TASK-52 / earlier specs).

## Length expansion

- [x] CHK-UI-001 No new surfaces introduced by this spec. Existing wizard layouts inherited from TASK-126.
- [x] CHK-UI-002 N/A — no new fixed-width labels.
- [x] CHK-UI-003 N/A.
- [x] CHK-UI-004 N/A.

## Mock-screenshots

- [x] CHK-UI-005 N/A — no new screen. Existing screens covered by their originating spec.
- [x] CHK-UI-006 N/A.
- [x] CHK-UI-007 N/A.

## RTL specifics

- [x] CHK-UI-008 N/A — no new drawables.
- [x] CHK-UI-009 N/A — no new custom layouts.
- [x] CHK-UI-010 N/A — no new numbers/currency.

## Plural rule variations

- [ ] CHK-UI-011 PARTIAL — `wizard_step_of` is count-driven ("Шаг 2 из 5"). If implemented as string with `%d` placeholder, Russian plurals (шаг/шага/шагов) will be wrong. Recommend `plurals` resource. Cross-ref checklist-localization CHK005.
- [ ] CHK-UI-012 PARTIAL — same as above.

## Line-height / vertical fit

- [x] CHK-UI-013 N/A — no new containers.
- [x] CHK-UI-014 N/A.

## Senior-safe overlap

- [x] CHK-UI-015 N/A — no new critical actions.
- [x] CHK-UI-016 N/A.

## Locale change at runtime

- [x] CHK-UI-017 Standard Android behavior — inherited.
- [x] CHK-UI-018 No cached bitmaps.

## Translation deferral path

- [x] CHK-UI-019 MVP is Russian-only per project convention. FR-008 mandates Russian strings for wizard keys. Follow-up locales deferred.

**Result**: 17/19 passed, 2 open (CHK-UI-011/012 shared with checklist-localization — wizard step counter plural handling).

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Проверили что UI устойчив к localization реальностям (разные длины слов, RTL, plural формы). Спека не добавляет НОВЫХ UI screen'ов — только правит wizard runtime строки (TASK-126) + чиним HomeScreen (TASK-52). 17/19 pass — почти всё N/A по причине «нет новой UI поверхности».

**Конкретика, которую стоит запомнить:**
- CHK-UI-011/012 open — те же plural issues что в checklist-localization CHK005/CHK006 (`wizard_step_of` требует `plurals` ресурс).
- Length expansion / RTL / mock-screenshots — N/A, наследуется от TASK-126 / TASK-52 spec'ов.
- Locale change at runtime — standard Android behavior, inherited.

**На что смотреть с осторожностью:**
- Открытые пункты — cross-ref с checklist-localization; закрытие там автоматически закрывает эти два.
