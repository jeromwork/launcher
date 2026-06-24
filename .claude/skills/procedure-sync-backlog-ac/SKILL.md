---
name: procedure-sync-backlog-ac
description: Синхронизирует высокоуровневые Success Criteria из spec.md в Acceptance Criteria соответствующего backlog-task'а — file-based по умолчанию (через маркеры `<!-- AC:BEGIN -->`/`<!-- AC:END -->` в `backlog/tasks/*.md`), MCP backlog-сервер — опциональный fallback если подключён. Извлекает только те SC, что помечены маркером `[backlog]`, и обновляет `## Acceptance Criteria` секцию задачи, сохраняя уже отмеченные `[x]` чекбоксы. Вызывается автоматически в конце speckit-specify, speckit-clarify, speckit-tasks (когда SC могут измениться), либо руками после правки spec.md. Backlog становится производным от spec — никакого расхождения источника правды. **Это initial-population skill**; для PR-time sync со статусом Done/Paused — см. [pre-pr-backlog-sync](../pre-pr-backlog-sync/SKILL.md).
---

# Procedure: sync-backlog-ac

Поддерживает синхронизацию между **spec.md (источник правды)** и **backlog-task `## Acceptance Criteria`** (portfolio tracker).

## Зачем

В Spec Kit'е секция `## Success Criteria` спеки содержит **все** критерии готовности (часто 8-15, включая технические детали типа «Argon2id ≤3s P95»). Для portfolio-уровня (Backlog.md Kanban) нужны только **высокоуровневые user-visible** критерии (4-7 штук), помеченные маркером `[backlog]`.

Этот skill извлекает помеченные SC из spec.md и обновляет AC соответствующего backlog-task'а через MCP. Spec.md остаётся каноническим, backlog AC — projection.

## Когда вызывать

- **Автоматически** в конце:
  - `speckit-specify` — первое заполнение AC при создании спеки.
  - `speckit-clarify` — если в clarifications изменились SC.
  - `speckit-tasks` — если ре-формулировка задач затронула SC.
- **Руками** — если правил `## Success Criteria` в spec.md без вызова speckit-команды.

## Предусловия

1. В проекте установлен Backlog.md (есть папка `backlog/tasks/`).
2. У спеки есть соответствующий backlog-task (frontmatter `references: [specs/NNN-slug/]`). Если нет — skill сообщает и НЕ создаёт task автоматически (создание task'а под фичу — отдельное решение владельца).
3. MCP `backlog` — **опционально**. При отсутствии работаем file-based через прямой edit `<!-- AC:BEGIN -->...<!-- AC:END -->` блока.

## Алгоритм

1. **Определить spec.md.** Текущий контекст или явный путь (`specs/020-.../spec.md`).
2. **Извлечь помеченные SC.** Парсить `## Success Criteria` → отбирать строки, содержащие маркер `[backlog]`. Маркер ставится сразу после идентификатора:
   ```markdown
   - **SC-001 [backlog]**: Recovery работает на втором устройстве за <60с
   - **SC-002**: Argon2id timing ≤3s P95 (technical detail, не в backlog)
   ```
   Если ни один SC не помечен — сообщить и завершиться без изменений (это нормально для спек, где AC были написаны вручную автором task'а).
3. **Очистить текст AC от маркера и SC-номера** перед записью:
   ```
   "Recovery работает на втором устройстве за <60с"
   ```
4. **Найти backlog-task.** `Glob "backlog/tasks/*.md"` → `Grep` по frontmatter `references:` с путём спеки. Если task'ов несколько — спросить пользователя. Если ноль — сообщить и остановиться (не создавать автоматически).
5. **Прочитать текущие AC task'а.** Найти блок между `<!-- AC:BEGIN -->` и `<!-- AC:END -->`. Распарсить строки формата:
   ```
   - [ ] #N описание
   - [x] #N описание
   ```
   Сохранить статусы `[x]` для критериев, текст которых совпадает с новыми (matching по нормализованному тексту — trim, lowercase, схлопнуть пробелы).
6. **Сформировать новый список AC**:
   - Каждый помеченный SC из spec.md → одна строка AC.
   - Если такой AC уже был и был отмечен `[x]` — сохранить.
   - Если новый — `[ ]`.
   - AC, которых больше нет в spec.md среди помеченных — **удалить** (с предупреждением, если они были отмечены `[x]`).
   - Нумерация `#N` — последовательная от 1.
7. **Обновить task** через `Edit` tool — заменить блок между маркерами. Если MCP backlog подключён — можно использовать `editTask` как альтернативу (одинаковый результат, MCP пишет тот же файл).
8. **Обновить frontmatter** `updated_date:` на сегодняшнюю дату (YYYY-MM-DD HH:MM).
9. **Отчитаться** владельцу: «Sync OK: N AC в task-X, M добавлено, K удалено, L сохранено отмеченных».

## File-based edit детали

Точная структура AC блока в backlog/tasks (см. реальный пример [task-3](../../../backlog/tasks/task-3 - F-4-AuthProvider-Google-Sign-In.md)):

```markdown
## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 AuthProvider port в core/auth/, Google Sign-In adapter в android/
- [ ] #2 Sign-In происходит при первом cloud action (не при запуске)
- [ ] #3 Identity isolation: per-UID Keystore namespacing
- [ ] #4 Sign-out preserves Keystore (для recovery)
<!-- AC:END -->
```

При записи **не** трогать ничего вне маркеров (description, dependencies, summary).

## Что НЕ делает

- ❌ НЕ создаёт backlog-task под спеку — это явное решение.
- ❌ НЕ переписывает spec.md (источник правды).
- ❌ НЕ синхронизирует обратно (backlog → spec).
- ❌ НЕ трогает не помеченные SC.
- ❌ НЕ меняет статус task'а (`status` — отдельная история, см. speckit-* orchestrator hooks).

## Обработка ошибок

- **MCP backlog не доступен** → сообщить «MCP backlog не подключён, запустите `claude mcp list` для проверки» и остановиться.
- **Backlog-task для спеки не найден** → сообщить «task для specs/NNN-slug не найден; создайте через `backlog task create '<title>' --ref specs/NNN-slug/` или MCP createTask» и остановиться.
- **В spec.md нет помеченных SC** → сообщить «ни один SC не помечен `[backlog]`; AC в task не изменены» и завершиться без ошибки.

## Output

После успешной синхронизации:
```
✅ Sync backlog-ac: task-6 (F-5 Root Key)
   - 5 SC помечены [backlog] в specs/020-.../spec.md
   - 3 AC сохранены (включая 1 отмеченный [x])
   - 2 AC добавлены
   - 0 AC удалены
```
