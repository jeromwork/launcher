---
name: procedure-sync-backlog-description
description: Синхронизирует **описание** backlog-task'а (секции «Что это простыми словами», «Зачем», «Что входит технически», «Состояние», корректировки модели) с финальным состоянием spec.md + plan.md + tasks.md + analyze-report.md после полного speckit-цикла. Backlog description часто застывает в момент написания задачи (когда taskа кладётся в `In Progress`), пока spec.md эволюционирует в clarify → scenarios → plan → tasks → analyze. Этот skill — post-cycle projection финального scope'а обратно в backlog для Kanban-читателя. Парный с `procedure-sync-backlog-ac` (который синхронизирует только AC). Вызывается автоматически в конце `speckit-analyze` (когда verdict PASS), либо руками после major scope-shift в speckit-плане.
---

# Procedure: sync-backlog-description

Поддерживает синхронизацию между **финальным speckit-набором артефактов** (`spec.md` + `plan.md` + `tasks.md` + `analyze-report.md`) и **`## Description` секцией backlog-task'а**.

## Зачем

В backlog/tasks/task-N-*.md description обычно пишется **один раз** — когда taskа создаётся / берётся в работу. Это short, mentor-style projection для Kanban-читателя (per CLAUDE.md «mentor-style backlog tasks» rule). Но в процессе работы:

- **specify → clarify**: вопросы выявляют новые требования, отбрасывают исходные допущения.
- **clarify → scenarios**: UX patterns могут измениться (precedent revision).
- **scenarios → plan**: архитектурные решения уточняются с research.md, alternatives considered, exit ramps.
- **plan → tasks**: scope может расшириться (новые phases) или сжаться (отброшенный feature).
- **tasks → analyze**: cross-artifact trace может вскрыть скрытые сценарии.

**Пример из практики (TASK-7, 2026-06-24)**: исходное описание рассматривало `simple-launcher` как hardcoded Kotlin manifest с 3 bundled JSON под 6/9/12 плиток. После clarify pass'а модель полностью пересмотрена — `simple-launcher = composition of bundled JSON`, plus 3 архитектурные дыры в F-3 закрываются (engine.computePending, AppCompatDelegate.setApplicationLocales, pool schema v1→v2). Effort revised Large→Medium→Medium+. **Если оставить старое описание, Kanban-читатель увидит устаревшую модель**, а implementation pойдёт по новой.

Этот skill — projection финального speckit-набора в backlog description **без потери mentor-style понятности**.

## Когда вызывать

- **Автоматически** в конце:
  - `speckit-analyze` Step 5d — **only if** verdict PASS / READY-WITH-CAVEATS (not for NOT-READY). Skill срабатывает раз в цикле; повторный analyze без description-shifting changes — no-op.
- **Руками** — если владелец редактирует spec.md / plan.md мимо speckit-команд и хочет project changes в backlog description.

## Предусловия

1. `specs/<id>/spec.md` существует и прошёл speckit-analyze c verdict PASS / READY-WITH-CAVEATS.
2. У спеки есть соответствующий backlog-task (frontmatter `references: specs/<id>/`).
3. Backlog-task description обёрнут маркерами `<!-- SECTION:DESCRIPTION:BEGIN -->` / `<!-- SECTION:DESCRIPTION:END -->` (per CLAUDE.md backlog convention).

## Алгоритм

1. **Идентифицировать backlog-task** через `references:` в frontmatter (или явный path аргумент).
2. **Прочитать текущую description** между маркерами.
3. **Извлечь финальный scope из speckit-набора**:
   - `spec.md` → **«Что это простыми словами»** (US-1 happy path как narrative + scope boundaries).
   - `spec.md` § Контекст и цель + § Cross-cutting concerns → **«Зачем»**.
   - `plan.md` § Summary + § Architecture (high-level) → **«Что входит технически»** code + content + tests sections.
   - `analyze-report.md` § Verdict + speckit cycle status → **«Состояние»**.
   - `research.md` alternatives considered + scenarios pass refinements → **«Корректировки модели в процессе»** (timeline of major shifts).
4. **Прочитать историю изменений модели**:
   - Если description содержит уже-существующую секцию «Корректировки модели» — preserve её и append новые корректировки (chronological).
   - Если нет — добавить.
5. **Preserve owner-handwritten секции** (если в description есть кастомные секции вне standard mentor-style template — не трогать).
6. **Сохранить «Готовый промт для /speckit.specify»** секцию **как archival history** с пометкой «historical, kept for archival», но не использовать как стартовую точку (она устарела после clarify).
7. **Обновить frontmatter** `updated_date:` на сейчас.
8. **Обновить ссылки на артефакты** — pointer к финальным `specs/<id>/spec.md` / `plan.md` / `tasks.md` / `analyze-report.md` в header description.
9. **Логировать changes** в commit message: какие секции обновились, что добавлено / убрано.

## Что НЕ делает

- ❌ НЕ переписывает spec.md / plan.md / tasks.md (источник правды).
- ❌ НЕ меняет AC (это работа `procedure-sync-backlog-ac`).
- ❌ НЕ меняет статус task'а (`status` — отдельная история).
- ❌ НЕ создаёт backlog-task под спеку.
- ❌ НЕ удаляет «Готовый промт» секцию — оставляет как archival history (для понимания «как изначально формулировалась задача vs что вышло»).
- ❌ НЕ модифицирует frontmatter поля кроме `updated_date`.

## Output

После успешной синхронизации:

```
✅ Sync backlog description: task-N (Simple Launcher Profile S-1)
   - Updated sections: «Что это простыми словами», «Зачем», «Что входит технически (для AI-агента)», «Состояние»
   - Added: «Корректировки модели в процессе» (3 entries)
   - Preserved: «Готовый промт для /speckit.specify» as historical archive
   - Description bytes: 5400 → 7200 (+1800)
   - updated_date: 2026-06-24 14:00 → 2026-06-24 16:00
```

## Обработка ошибок

- **Backlog-task не найден** → сообщить и остановиться (не создавать автоматически).
- **Description маркеры отсутствуют** → сообщить как warning, обернуть существующий description в маркеры (single-shot migration), затем синхронизировать.
- **`spec.md` / `plan.md` / `tasks.md` / `analyze-report.md` отсутствуют** → сообщить какие артефакты missing и какие секции нельзя обновить.
- **Verdict NOT READY** в analyze-report.md → отказаться запускать sync (артефакты ещё не final).

## Anti-pattern

«Просто оставим старое description, implementation сделается по spec.md/plan.md» — нарушает CLAUDE.md backlog rule. Backlog Kanban для Owner'а / team-reader'а — primary navigation surface; устаревший description там вводит в заблуждение и приводит к неправильным expectations / replanning.

«Удалю старую description и напишу новую с нуля» — теряет mentor-style continuity и историю принятия решений («корректировки модели в процессе»). Skill добавляет, preserve'ит archival, не erase'ит.

## Relationship to other skills

- **`procedure-sync-backlog-ac`** — парный skill, синхронизирует **AC** (Success Criteria → Acceptance Criteria).
- **`pre-pr-backlog-sync`** — PR-time, синхронизирует **статус** (In Progress / Verification / Done) и **deferred markers**, плюс re-runs AC sync. **НЕ** syncs description.
- **`speckit-analyze`** — caller. Должен инвокить этот skill в Step 5d после verdict READY.

## Implementation hint

Skill читает 4 артефакта (spec, plan, tasks, analyze-report) — substantial context. Если context-economy critical, можно ограничиться spec.md только (минимум) + analyze-report.md (status verdict). Plan + tasks дают более точные «что входит технически» детали но не critical для top-level description sync.
