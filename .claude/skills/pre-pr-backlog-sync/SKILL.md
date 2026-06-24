---
name: pre-pr-backlog-sync
description: ОБЯЗАТЕЛЬНО вызывать перед `gh pr create` / любым другим открытием PR на feature-ветке, которая ссылается на spec (`specs/NNN-slug/`) или на backlog-task (`task-N - ...md`). Синхронизирует Acceptance Criteria в `backlog/tasks/`, проставляет `[x]` по фактически закрытым критериям из ветки, и сдвигает статус задачи: **Done** только если ВСЕ AC зелёные; иначе **Paused** — PR открывается, но задача не считается завершённой. Также подхватывает «оставшиеся блоки» (real-device tests, manual checks) — оставляет их как `[ ]` AC, чтобы они блокировали Done и поднимались позже отдельно. Без вызова этого skill'а — backlog рассинхронизирован с реальностью (что и случилось с TASK-3/TASK-4 в PR #21/#22).
---

# Orchestrator: pre-pr-backlog-sync

Точка между «работа на ветке закончена» и `gh pr create`. Обновляет [`backlog/tasks/`](../../../backlog/tasks/) так, чтобы Kanban-доска отражала реальность, а не намерения.

## Когда вызывать

**ОБЯЗАТЕЛЬНО** перед `gh pr create` если ветка:
- Названа по spec-слагу (`017-f4-auth-provider`, `018-f5-config-e2e-encryption`, ...), ИЛИ
- Изменяет файлы в `specs/NNN-*/`, ИЛИ
- Заявлена как «фича X» (есть соответствующая `task-N - X.md` в `backlog/tasks/`).

**Также** вызывать:
- При первом коммите в feature-ветку (статус: Draft → **In Progress**).
- После merge PR (статус: Paused → Done **только если** все AC зелёные; иначе остаётся Paused).
- Руками после ручной правки spec.md `## Success Criteria` секции с маркером `[backlog]`.

**НЕ вызывать** для:
- repo-chore PR (CLAUDE.md, .gitignore, скрипты) — не привязаны к backlog-task'у.
- Hotfix-ов в `main` без spec'а — backlog не покрывает.

## Принципы

### 1. Backlog AC ≠ Spec SC

`backlog/tasks/<task>.md` `## Acceptance Criteria` — это **5±2 сигнальных пункта** для portfolio Kanban, **не** копия всех SC из spec.md. Пишутся высокоуровневыми мазками («Round-trip byte-equal на physical device #1»), а не техническими деталями («Argon2id ≤ 1500ms P95 на Pixel 5»).

Что сюда попадает (выделено блоками):
- **User-visible behavior** («wizard за <3 сек открывает главный экран без Sign-In»).
- **Architectural invariants, проверяемые на ревью** («ConfigCipher не импортирует `com.google.*`»).
- **Real-device / manual gates** («Round-trip OK на Xiaomi 11T», «two-emulator pairing smoke»).
- **Doc / release gates** («Privacy Policy section добавлен»).

Что НЕ попадает:
- Детальные timing budgets (Argon2id <1.5s) — это SC в spec.md, не backlog.
- Каждый T0NN из tasks.md — это implementation, не сигнал.
- Внутренние имплементационные детали (имена приватных классов).

### 2. Статус ≡ доля зелёных AC

| Состояние AC                          | Статус       |
|---------------------------------------|--------------|
| 0 AC закрыто (только начали)          | In Progress  |
| ≥1 AC закрыто, но не все              | In Progress  |
| Все code-related AC ✅, real-device / manual ❌ | **Paused** ← PR можно открыть |
| Все AC ✅                              | Done         |

**Правило железное**: если хоть один `[ ]` AC остался — статус **НЕ** Done. PR открыть можно (code merge ≠ feature complete), но в Kanban задача стоит на Paused с явным указанием что блокирует.

### 3. Невыполненные блоки переживают PR

Если после PR остаются «висящие» гейты (тесты на реальном телефоне, OEM verification, доки), они остаются как `[ ]` AC в **той же** backlog-таске. Не выносятся в follow-up таску автоматически. Когда гейт закрывается — приходит человек, отмечает `[x]`, запускает этот skill снова → Paused → Done.

Исключение: если оставшийся блок — это **существенный отдельный scope** (не «дотест», а «другая фича»), создаётся явная follow-up таска через [Backlog.md CLI](https://github.com/MrLesk/Backlog.md) или прямой edit, и текущая таска получает строку «↪ See task-N for X».

## Алгоритм

### Шаг 1 — Идентифицировать taргет

1. Текущая ветка: `git branch --show-current`. Если совпадает с `main` → отказаться («pre-PR sync только на feature-ветках»).
2. Извлечь spec-слаг из имени ветки (regex `^(\d{3})-.*$`) → `specs/<NNN>-*/`.
3. Если spec'а нет — найти backlog task через явное упоминание в commit'ах ветки (`git log main..HEAD --grep "task-N\|TASK-N"`) или спросить пользователя.

### Шаг 2 — Найти backlog task

1. Прочитать все `backlog/tasks/*.md` (там сейчас ~50 файлов — приемлемо).
2. Отфильтровать по frontmatter `references:` содержащему путь spec'а.
3. Если несколько — спросить пользователя.
4. Если ноль — спросить: «создать task для этого spec'а?» (default Yes, ordinal = max+1000, status=In Progress).

### Шаг 3 — Прочитать текущие AC

Парсить блок между `<!-- AC:BEGIN -->` и `<!-- AC:END -->`. Формат строки:
```
- [ ] #N описание
- [x] #N описание
```

### Шаг 4 — Собрать «текущее состояние реальности»

Для каждой AC попытаться **автоматически** определить, закрыт ли он:

| Подсказка в AC                              | Проверка                                         |
|---------------------------------------------|--------------------------------------------------|
| «X в core/Y/Z» / «класс Foo»                | `grep -r "class Foo" <module>` → есть/нет        |
| «test Bar проходит» / имя теста             | `grep -r "fun Bar\\|@Test.*Bar"` → есть          |
| «Round-trip byte-equal на physical device»  | **манульная проверка** — спросить пользователя   |
| «Privacy Policy section добавлен»           | `grep -l "Privacy Policy" docs/`                 |
| «Detekt rule X passes»                      | спросить «CI зелёный?» или `gradlew detekt`      |

Автоматические подсказки помечаются `✓ verified` / `✗ not found`. Не-автоматические — `? manual` с пояснением что проверить.

### Шаг 5 — Спросить пользователя

Показать таблицу:

```
TASK-3 (AuthProvider + Google Sign-In)
Current status: In Progress  →  proposed: ?

AC #1: AuthProvider port в core/auth/...           [✓ verified — file exists]
AC #2: Sign-In при первом cloud action            [? manual — был ли smoke test?]
AC #3: Identity isolation: per-UID Keystore       [✓ verified — KeystoreNamespacing.kt]
AC #4: Sign-out preserves Keystore                [✗ not found — есть unit test?]

Mark which AC are closed by this PR (default: only ✓ verified):
[1, 3] OK? (or override "1,2,3,4" / "1,3,4")
```

После ответа → формируется новый AC блок.

### Шаг 6 — Решить статус

```
if all_ac_checked: status = Done; добавить Final Summary с reference на PR
elif any_ac_checked: status = Paused; добавить комментарий «blocked by AC #N, #M»
else: status = In Progress  (sanity check — почему открываем PR без единого закрытого AC?)
```

### Шаг 7 — Записать изменения

- Edit `backlog/tasks/task-N - *.md`:
  - frontmatter `status:` → новый
  - frontmatter `updated_date:` → today
  - AC блок → новый
  - При status=Done — заполнить `<!-- SECTION:FINAL_SUMMARY:BEGIN -->` с «Merged PR #X (YYYY-MM-DD). <one-line summary>»
  - При status=Paused — добавить под AC блоком комментарий:
    ```
    ## Pause Reason
    <!-- SECTION:PAUSE_REASON:BEGIN -->
    PR #X merged YYYY-MM-DD. Blocked AC: #2 (manual smoke), #4 (physical-device test).
    Re-run pre-pr-backlog-sync when AC are closed.
    <!-- SECTION:PAUSE_REASON:END -->
    ```

### Шаг 8 — Commit & continue

- `git add backlog/tasks/task-N - *.md`
- `git commit -m "backlog: sync task-N AC (status → <new>)"` — отдельный commit, **не** amend.
- Сообщить владельцу: «backlog sync committed. Proceed with `gh pr create`».

## Выход в обычный PR flow

После завершения skill'а возвращаем control orchestrator'у (или пользователю) для:
1. `gh pr create` с обычным title/body.
2. В PR description **добавить строку** «Backlog: task-N → \<new status\>».

## Обработка ошибок

- **MCP backlog недоступен** → ОК, работаем через прямой file-edit (`<!-- AC:BEGIN -->`/`<!-- AC:END -->` маркеры). MCP — оптимизация, не обязательное.
- **Несколько tasks для одного spec'а** → спросить.
- **AC блок отсутствует в task'е** → создать пустой блок + сообщить, что AC надо заполнить руками перед sync'ом.
- **Пользователь отказывается от sync'а («skip backlog»)** → залогировать решение в PR description (`Backlog: skipped sync — reason: <X>`), не блокировать PR.

## Что НЕ делает

- ❌ НЕ переписывает spec.md.
- ❌ НЕ создаёт follow-up tasks автоматически — только при явном «split this» от владельца.
- ❌ НЕ удаляет уже отмеченные `[x]` AC — даже если их формулировка изменилась, downgrade требует явного override.
- ❌ НЕ запускает `gh pr create` — это всё ещё ответственность пользователя/orchestrator'а.

## Связанные skills

- [`procedure-sync-backlog-ac`](../procedure-sync-backlog-ac/SKILL.md) — initial population AC из spec.md при создании task'а (file-based mode).
- [`speckit-tasks`](../speckit-tasks/SKILL.md) — после генерации tasks.md тоже зовёт `procedure-sync-backlog-ac` для свежесозданного task'а.
- [`review`](https://docs.claude.com/en/docs/claude-code) (built-in `/review`) — независимо от backlog проверяет код PR'а; не конфликтует.

## Output (success)

```
✅ pre-pr-backlog-sync: TASK-3 (AuthProvider + Google Sign-In)
   Status: In Progress  →  Paused
   AC: 3/4 closed (1 manual smoke remaining)
   Committed: backlog: sync TASK-3 AC (status → Paused)
   PR description hint: «Backlog: task-3 → Paused (1 AC pending: smoke test)»

→ Proceed with `gh pr create`.
```

## Краткое summary для не-разработчика

Этот скилл — **последний шаг перед открытием pull request'а**. Он смотрит на доску задач (`backlog/`) и говорит: «Эта задача закрыта на 3 из 4 пунктов, поэтому я ставлю её на паузу, а не на готово, пока 4-й пункт не закроется». Если пропустить этот шаг — на доске будут «зелёные» задачи, которые на самом деле не доделаны (что случилось с TASK-3 и TASK-4 в июне 2026).
