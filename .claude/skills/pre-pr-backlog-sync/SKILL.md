---
name: pre-pr-backlog-sync
description: ОБЯЗАТЕЛЬНО вызывать перед `gh pr create` / любым другим открытием PR на feature-ветке, которая ссылается на spec (`specs/NNN-slug/`) или на backlog-task (`task-N - ...md`). Синхронизирует Acceptance Criteria в `backlog/tasks/` по hybrid-модели: `[hand]` AC (author-written, project-specific behaviour) проставляются по grep/owner-confirm; `[auto:checklist]` AC регенерируются из `specs/<NNN>/checklists/*.md`; `[auto:deferred-*]` AC регенерируются из `[deferred-*]` маркеров в `tasks.md`. Решает статус: все [x] → Done; есть [ ] среди deferred → Verification (PR merged, ждём железа); есть [ ] среди hand/checklist → In Progress (фича не закончена, PR open нельзя). Без вызова этого skill'а — backlog рассинхронизирован с реальностью (incident PR #21/#22 → TASK-3/TASK-4 ушли в Done с 0/N AC; incident 2026-06-24 TASK-49 → 8/8 проставлено на слово при 12/45 deferred tasks).
---

# Orchestrator: pre-pr-backlog-sync

Точка между «работа на ветке закончена» и `gh pr create`. Обновляет [`backlog/tasks/`](../../../backlog/tasks/) так, чтобы Kanban-доска отражала реальность по 5-статусной модели (`Draft` → `In Progress` → `Verification` → `Done`; `Paused` — orthogonal).

## Когда вызывать

**ОБЯЗАТЕЛЬНО** перед `gh pr create` если ветка:
- Названа по spec-слагу (`017-f4-auth-provider`, `018-f5-config-e2e-encryption`, ...), ИЛИ
- Изменяет файлы в `specs/NNN-*/`, ИЛИ
- Заявлена как «фича X» (есть соответствующая `task-N - X.md` в `backlog/tasks/`).

**Также** вызывать:
- При первом коммите в feature-ветку (статус: Draft → **In Progress**).
- После merge PR (статус: In Progress → **Verification** если deferred гейты остались; иначе → Done).
- При закрытии deferred гейта (Verification → Done) — повторный sync.
- Руками после ручной правки spec.md `## Success Criteria` секции с маркером `[backlog]`.

**НЕ вызывать** для:
- repo-chore PR (CLAUDE.md, .gitignore, скрипты) — не привязаны к backlog-task'у.
- Hotfix-ов в `main` без spec'а — backlog не покрывает.

## Hybrid AC модель

`<!-- AC:BEGIN -->...<!-- AC:END -->` блок содержит flat-список AC, каждый с inline-маркером источника:

```markdown
## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] CloudAvailability port + Android adapter
- [x] #2 [hand] FcmTokenRegistrationGuard откладывает FCM до cloudAvailable=true
- [x] #3 [auto:checklist] checklists/domain-isolation.md: 16/16 CHK [x]
- [x] #4 [auto:checklist] checklists/meta-minimization.md: 13/13 CHK [x]
- [N/A] #5 [auto:checklist] checklists/wire-format.md: N/A (no wire format)
- [ ] #6 [auto:deferred-local-emulator] Emulator smoke pixel_5_api_34 (T043, T031-T036)
- [ ] #7 [auto:deferred-physical-device] Physical device Xiaomi 11T (T041)
<!-- AC:END -->
```

### Marker semantics

| Marker | Источник | Регенерируется | Проверка `[x]` |
|---|---|---|---|
| `[hand]` | Автор задачи (5±2 user-visible criteria) | Нет — `[hand]` AC сохраняются как есть, только меняется checkbox | grep кода/тестов + owner-confirm |
| `[auto:checklist]` | `specs/<NNN>/checklists/*.md` (по одному на файл) | Да — полностью пересчитывается count `[x]` vs total | `[x]` если все CHK `[x]` или `[N/A]`; иначе `[ ]` |
| `[auto:deferred-local-emulator]` | `tasks.md` grep `\[deferred-local-emulator\]` | Да — список затронутых Tnnn в тексте | `[x]` только если все Tnnn deferred-local-emulator выполнены (требует owner-confirm + указание AVD) |
| `[auto:deferred-physical-device]` | `tasks.md` grep `\[deferred-physical-device\]` | Да | `[x]` только при owner-confirm с указанием устройства + commit hash |
| `[auto:deferred-firebase-emulator]` | `tasks.md` grep `\[deferred-firebase-emulator\]` | Да | `[x]` только при owner-confirm |
| `[auto:deferred-external]` | `tasks.md` grep `\[deferred-external\]` | Да | `[x]` только при owner-confirm |

AC **без маркера** — legacy формат; при первом sync классифицируется как `[hand]` (если не очевидно из текста что это checklist/deferred).

## Status decision matrix

| Состояние AC | Статус |
|---|---|
| Все `[hand]` + `[auto:checklist]` + `[auto:deferred-*]` зелёные (`[x]` или `[N/A]`) | **Done** |
| Все `[hand]` + `[auto:checklist]` зелёные; есть `[ ]` среди `[auto:deferred-*]` | **Verification** (PR merged) |
| Есть `[ ]` среди `[hand]` или `[auto:checklist]` | **In Progress** (PR open нельзя — фича не закончена) |
| Owner явно сказал «переключаюсь на task-Y» | **Paused** (orthogonal) |

## Алгоритм

### Шаг 1 — Идентифицировать target

1. Текущая ветка: `git branch --show-current`. Если совпадает с `main` → отказаться («pre-PR sync только на feature-ветках»).
2. Извлечь spec-слаг из имени ветки (regex `^(\d{3}|task-\d+)-.*$`) → `specs/<NNN>-*/`.
3. Если spec'а нет — найти backlog task через явное упоминание в commit'ах (`git log main..HEAD --grep "task-N\|TASK-N"`) или спросить пользователя.

### Шаг 2 — Найти backlog task

1. `Glob "backlog/tasks/*.md"` + `Grep` по frontmatter `references:` содержащему путь spec'а.
2. Если несколько — спросить пользователя.
3. Если ноль — спросить: «создать task для этого spec'а?».

### Шаг 3 — Прочитать текущие AC

Парсить блок между `<!-- AC:BEGIN -->` и `<!-- AC:END -->`. Сохранить статусы `[x]` / `[N/A]` для всех AC по нормализованному тексту (для последующего merge).

### Шаг 4 — Регенерировать auto-секции

**4a. `[auto:checklist]` — по одной строке на каждый `specs/<NNN>/checklists/*.md`:**

```bash
for f in specs/<NNN>/checklists/*.md; do
  total=$(grep -cE "^- \[" "$f")
  done=$(grep -cE "^- \[x\]|^- \[N/A\]" "$f")
  if [ "$total" -eq "$done" ]; then
    # all [x] or [N/A] — emit [x]
  fi
done
```

Формат строки: `[auto:checklist] checklists/<name>.md: X/Y CHK [x]` (или `: N/A` если все N/A).

**4b. `[auto:deferred-*]` — grep `tasks.md` по маркерам:**

```bash
grep -oE "\[deferred-(local-emulator|physical-device|firebase-emulator|external)\]" specs/<NNN>/tasks.md | sort -u
```

Для каждого уникального маркера — одна строка AC. В тексте перечислить **затронутые Tnnn** через короткую сводку:

```bash
grep -nE "T0?\d+.*\[deferred-physical-device\]" specs/<NNN>/tasks.md
# → T041 → текст: «Physical device verification (T041)»
```

Default checkbox: `[ ]`. Поднять в `[x]` можно **только** при явном owner-confirm с указанием:
- для `local-emulator`: какой AVD использован (имя + API level), какой smoke-test прогнан;
- для `physical-device`: имя устройства + commit hash / скриншот / артефакт;
- для `firebase-emulator`: какой emulator suite, какие тесты прогнаны;
- для `external`: что именно завершено (provisioning approved, hardware delivered, и т.д.).

**4c. Hand AC: верификация по grep + owner-confirm.**

Для каждой `[hand]` строки — попытка grep кода/тестов. Если кандидат найден → пометить `✓ verified`. Иначе → `? manual` (требует owner-confirm).

**4d. Pseudo-gate detection.** Если `[hand]` AC содержит фразу, не верифицируемую в текущем окружении (no-GMS Huawei, AOSP-only, конкретный неизвестный девайс) — **REFUSE auto-checkmark**, требовать рефактора AC в `[hand]` DI-override + inline-TODO `physical-device`.

### Шаг 5 — Спросить owner'а

Показать таблицу:

```
TASK-49 (Cloud Feature Inventory)
Current status: In Progress  →  proposed: Verification

[hand] AC (verify or rewrite):
  AC #1: CloudAvailability port            [✓ verified]
  AC #2: FcmTokenRegistrationGuard         [✓ verified]
  AC #3: docs/dev/cloud-availability.md    [✓ verified — exists]
  AC #4: docs/dev/offline-online-architecture.md [✗ not found]
  AC #5: «Huawei без GMS работает»         [✗ pseudo — rewrite as [hand] DI-override]

[auto:checklist] AC (regenerated):
  AC #6: domain-isolation.md: 16/16        [x] auto
  AC #7: meta-minimization.md: 13/13       [x] auto
  AC #8: wire-format.md: 11/11 N/A         [N/A] auto

[auto:deferred-*] AC (regenerated from tasks.md):
  AC #9:  [deferred-local-emulator]  T031-T036, T043     [ ] blocked
  AC #10: [deferred-physical-device] T041               [ ] blocked

Recommended status: Verification (2 deferred gates blocked).
Override status / mark [hand] AC closed? (default: keep blocked):
```

### Шаг 6 — Записать изменения

- Edit `backlog/tasks/task-N - *.md`:
  - frontmatter `status:` → новый
  - frontmatter `updated_date:` → today
  - AC блок → новый flat-список с маркерами
  - `<!-- SECTION:VERIFICATION_PENDING:BEGIN -->...<!-- :END -->` — для Verification, список pending AC + recovery steps.
  - `<!-- SECTION:PAUSE_REASON:BEGIN -->...<!-- :END -->` — только для Paused (orthogonal).
  - `<!-- SECTION:FINAL_SUMMARY:BEGIN -->...<!-- :END -->` — для Done.

### Шаг 7 — Commit & continue

- `git add backlog/tasks/task-N - *.md`
- `git commit -m "backlog: sync task-N AC (status → <new>)"` — отдельный commit, **не** amend.
- Сообщить владельцу: «backlog sync committed. Proceed with `gh pr create`».
- В PR description добавить `Backlog: task-N → <new status>` + список pending AC (если Verification).

## Edge cases

- **Spec без checklists/** → `[auto:checklist]` секция пустая, это OK.
- **Spec без tasks.md** → `[auto:deferred-*]` секция пустая, это OK.
- **Spec без AC блока в task'е** → создать пустой блок + сообщить, что `[hand]` AC надо заполнить руками.
- **Owner отказывается от sync** → залогировать решение в PR description (`Backlog: skipped sync — reason: <X>`), не блокировать PR.
- **Несколько specs ссылаются на один task** → sync все, mergeить AC.

## Что НЕ делает

- ❌ НЕ переписывает spec.md.
- ❌ НЕ создаёт follow-up tasks автоматически — только при явном «split this» от владельца.
- ❌ НЕ удаляет `[x]` `[hand]` AC — даже если grep не нашёл артефакта, требует owner-confirm на downgrade.
- ❌ НЕ запускает `gh pr create` — это всё ещё ответственность пользователя/orchestrator'а.
- ❌ НЕ ставит `[x]` на `[auto:deferred-*]` без явного указания AVD / устройства / суита / commit hash.

## Связанные skills

- [`procedure-sync-backlog-ac`](../procedure-sync-backlog-ac/SKILL.md) — initial population `[hand]` AC из spec.md `[backlog]`-маркированных SC при создании task'а.
- [`speckit-tasks`](../speckit-tasks/SKILL.md) — обязан помечать emulator/physical-device/firebase-emulator/external tasks `[deferred-<type>]` маркерами, чтобы pre-PR sync их подхватывал.

## Output (success)

```
✅ pre-pr-backlog-sync: TASK-49 (Cloud Feature Inventory)
   Status: In Progress  →  Verification

   AC summary (10 total):
     [hand]    : 3/4 verified, 1 rewrite needed (#5 pseudo-gate)
     [auto:checklist] : 3/3 green (16+13+11=40 CHK)
     [auto:deferred-*]: 0/2 closed (local-emulator + physical-device)

   Committed: backlog: sync TASK-49 AC (status → Verification)
   PR description hint:
     «Backlog: task-49 → Verification (pending: #5 rewrite, #9 deferred-local-emulator, #10 deferred-physical-device)»

→ Proceed with `gh pr create`.
```

## Краткое summary для не-разработчика

Этот скилл — **последний шаг перед открытием pull request'а**. Он смотрит на доску задач (`backlog/`) и говорит: «Эта задача готова в коде, но 2 проверки требуют железа — ставлю в Verification, не в Done. Когда прогонишь — сдвинется». Если пропустить этот шаг — на доске будут «зелёные» задачи, которые на самом деле не доделаны. AC берутся из трёх источников: руками-написанные (`[hand]`), checklists/ (`[auto:checklist]`), отложенные tasks из tasks.md (`[auto:deferred-*]`).
