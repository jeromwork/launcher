---
id: TASK-61
title: Consolidate checklist skills + translate to English for token economy
status: Draft
assignee: []
created_date: '2026-06-27 11:56'
labels:
  - phase-tooling
  - tooling
  - checklist
milestone: m-4
dependencies: []
priority: medium
ordinal: 61000
---

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Сейчас в `.claude/skills/` лежит **27 чек-листов** (`checklist-*` — каждый проверяет один аспект: accessibility, security, localization, и т.д.). На любом новом spec'е AI-агент прогоняет несколько чек-листов целиком, генерирует verdict в `specs/<task>/checklists/<name>.md`. **Это жрёт много токенов** — на task-52, например, прогнано 13 чек-листов, каждый ~200-400 строк на русском.

Что хотим сделать:
1. **Слить дублирующие чек-листы** в 6-8 meta-чек-листов. Сейчас `elderly-friendly` + `accessibility` + `localization-ui` пересекаются на ~30% пунктов. Объединим в `senior-ux`. Аналогично — `security` + `tamper-resistance` + `permissions-platform` в `security-platform`.
2. **Перевести содержимое чек-листов на английский.** Русский ~1.5× длиннее в токенах. Владелец сами чек-листы не читает — только итоговый verdict.
3. **Verdict-output (то, что попадает в `specs/<task>/checklists/*.md`)** — оставляем на русском. Это **то, что владелец читает.**
4. **Переписать `procedure-assess-spec-complexity`**, чтобы возвращал 2-3 релевантных meta-чек-листа на средний spec, не 8.

Целевое снижение токенов на phase `/speckit.plan` + `/speckit.analyze`: **~40-50%**.

## Зачем

Владелец упёрся в лимит Claude Opus 4.7. Основной расход — `/speckit.analyze` на спеках с большим количеством чек-листов. Сжатие чек-листов = больше задач помещается в дневной лимит без потери качества проверки (агент всё равно прогоняет ту же логику, просто компактнее).

## Что входит технически

- Audit всех 27 `.claude/skills/checklist-*/SKILL.md` — выявить дубли по пунктам.
- Дизайн 6-8 meta-checklist'ов с явными секциями внутри.
- Migration старых 27 → 6-8: содержимое переводится на английский, секции мерджатся.
- Старые `checklist-*` skill-папки удалить (или оставить тонкие redirect'ы на 1-2 версии).
- Обновить `.claude/skills/procedure-assess-spec-complexity/SKILL.md` — новый mapping spec-content → meta-checklist'ы.
- Обновить упоминания в `CLAUDE.md` / `AGENTS.md`.
- Регрессионный тест: прогнать новый набор на task-52 чек-листах — verdict'ы должны быть **функционально эквивалентны** (могут отличаться формулировкой, но не пропускать issues).

## Состояние

Draft. Запланировано после завершения task-52. Можно начинать когда владелец освободится от текущей работы — это standalone tooling task, не блокирует фичи.

<!-- SECTION:DESCRIPTION:END -->

<!-- SECTION:ACCEPTANCE_CRITERIA:BEGIN -->

## Acceptance Criteria

- [ ] #1 [hand] Количество `checklist-*` skills сокращено с 27 до 6-8 meta-checklist'ов
- [ ] #2 [hand] Содержимое каждого meta-checklist'а на английском (SKILL.md + internal templates)
- [ ] #3 [hand] Verdict-output (`specs/<task>/checklists/*.md`) остаётся на русском
- [ ] #4 [hand] `procedure-assess-spec-complexity` обновлён под новый mapping
- [ ] #5 [hand] Регрессионный прогон на task-52: новый набор выдаёт функционально эквивалентные verdict'ы старым 13 чек-листам
- [ ] #6 [hand] CLAUDE.md / AGENTS.md обновлены под новый набор
- [ ] #7 [hand] Замер: dry-run `/speckit.analyze` на task-52 показывает снижение токенов на ≥30%

<!-- SECTION:ACCEPTANCE_CRITERIA:END -->
