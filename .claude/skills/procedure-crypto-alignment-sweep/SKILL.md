---
name: procedure-crypto-alignment-sweep
description: Инкрементально сверяет backlog task'и с текущим состоянием крипто-архитектуры (`docs/dev/crypto-mentor-overview.md` + `docs/dev/crypto-open-questions.md`) и проставляет каждой задаче `crypto-alignment` marker в frontmatter (aligned / needs-review / scope-reset / needs-desc-update / new-from-mentor / parked / blocked-on-question). Не пытается закрыть все 74 задачи за раз — работает **батчами по session-tag или feature-area** (10-15 задач). Вызывается после накопления решений в mentor-overview.md, чтобы backlog не разошёлся с архитектурой. **Не редактирует Description сам** — только помечает и генерирует checklist для владельца / другого AI.
---

# Procedure: crypto-alignment-sweep

Проходит по batch'у backlog-task'ов, сверяет каждый с текущим mentor-overview + open-questions, проставляет marker в frontmatter, генерирует **отчёт-подсказку** для владельца о необходимых update'ах.

## Зачем

Крипто-архитектура (`docs/dev/crypto-mentor-overview.md`) — living Source of Truth, растёт по мере mentor-сессий. Backlog task'и написаны в разные моменты времени под разные предпосылки. Периодически они рассинхронизируются:

- Одна задача была написана **до** MLS pivot'а → предполагает envelope-with-recipient-set.
- Другая — **до** WhatsApp compression pattern → предполагает server-side transcoding.
- Третья — **до** T0→T1 pseudonym roadmap → hardcode'ит `stableId` в domain.

Ручной sweep всех 74 задач за одну сессию = гарантированный failure (усталость → халтура). Skill предоставляет **машину** для инкрементальной ре-классификации.

**Ключевая идея**: skill **не переписывает** description'ы. Он только:
1. Помечает alignment status в frontmatter.
2. Генерирует actionable checklist «что делать с этой задачей».
3. Owner / другой AI применяет изменения по checklist'у в отдельной сессии.

Это разделение — **сознательное**. Sweep = быстрый triage. Rewrite = отдельный focused effort.

## Когда вызывать

- **После mentor-сессии, добавившей 1+ секций в mentor-overview** — sweep задач, потенциально затронутых новыми решениями.
- **Перед `speckit-specify` большого task'а** — убедиться что все blocked-on-question для него решены.
- **Периодически** (раз в 1-2 недели) — batch по 10-15 задач, поддержание синхронизации.
- **Руками** — «пройди все задачи с меткой phase-2» / «пройди задачи связанные с pairing».

## Предусловия

1. `docs/dev/crypto-mentor-overview.md` существует и имеет якорные секции (`Δ.N`, `Π.N`, `Ρ.N`, `Ξ.N`, `Θ.N`).
2. `docs/dev/crypto-open-questions.md` существует с реестром Q-NN и session-tag'ами.
3. Backlog task'и следуют `backlog-task-format` skill соглашениям (frontmatter + SECTION маркеры).
4. Владелец указал **batch scope** — какие задачи в этот sweep:
   - По label: `all tasks with label=crypto`.
   - По session-tag из open-questions: `all tasks blocked by session-tag=theme-9-recovery-propagation`.
   - Явный список: `TASK-42, TASK-46, TASK-58`.
   - По feature area: `all pairing-related tasks`.

## Frontmatter поля (добавляются skill'ом)

Skill добавляет / обновляет три поля в frontmatter каждой task'и:

```yaml
crypto-alignment: aligned | needs-review | scope-reset | needs-desc-update | new-from-mentor | parked | blocked-on-question
crypto-source: [Ξ.5, Π.7, Ρ.4]           # секции mentor-overview что информируют задачу
blocks-on: [Q-09, Q-12]                   # open-questions что блокируют, если blocked-on-question
crypto-sweep-date: 2026-07-02             # когда последний раз sweep'нули (auto)
```

Значения `crypto-alignment`:

- **`aligned`** — description соответствует текущей архитектуре, никаких действий не требуется.
- **`needs-review`** — не sweep'нута ни разу, статус неизвестен (initial default для legacy tasks).
- **`scope-reset`** — feature approach устарел (например envelope→MLS pivot). Требуется **major** rewrite description + промта. Проставить `crypto-source` секциями которые определяют новый подход.
- **`needs-desc-update`** — feature scope верный, но description говорит про устаревшие детали (например «server-side compress» вместо «client-side compress»). Требуется **minor** rewrite конкретных секций.
- **`new-from-mentor`** — задача должна **появиться** в backlog как результат mentor-сессии (например `QuotaEnforcer` port, `BlobStorage` port, Durable Objects setup). Создаётся `backlog task create` командой отдельно, не skill'ом.
- **`parked`** — задача была OK, но теперь семантически устарела и не harmful (например TASK-40 Multi-device — покрыт Блоком 3). Sweep помечает для последующего merge/close решения.
- **`blocked-on-question`** — задача ждёт разрешения одного или нескольких Q-NN из open-questions. `blocks-on` перечисляет какие. Task'у **нельзя** брать в /speckit.specify пока `blocks-on` не пуст.

## Алгоритм

### Step 1 — Собрать контекст

1. Прочитать `docs/dev/crypto-mentor-overview.md` — построить **index секций** (Δ.1..Δ.11, Π.1..Π.8, Ρ.1..Ρ.5, Ξ.1..Ξ.8, Θ.1..Θ.4, Блоки 1..19, Часть Σ backlog cleanup список).
2. Прочитать `docs/dev/crypto-open-questions.md` — построить **index Q-NN** с priority + session-tag'ами + blocks-tasks.
3. Прочитать `CLAUDE.md` engineering rules 1-16 (в частности §Portfolio tracker + §Session hygiene) для контекста.

### Step 2 — Определить batch

По input'у от владельца:

- Если указан явный список task'ов — использовать его.
- Если указан label — `backlog task list --plain -l <label>`.
- Если указан session-tag — из open-questions.md найти Q-NN с этим tag'ом, из них — task'и в поле «Blocks tasks» → union.
- Если «all needs-review» — `backlog task list --plain` → filter по frontmatter `crypto-alignment` не задан или `needs-review`.

**Cap batch на 15 задач.** Если больше — предложить владельцу разбить.

### Step 3 — Для каждой задачи в batch

Для каждой task-N:

1. **Прочитать `backlog/tasks/task-N *.md`**:
   - Frontmatter (labels, dependencies, references, current crypto-* поля).
   - Description секцию.
   - Acceptance Criteria секцию.
2. **Классифицировать по критериям (см. Step 4)**.
3. **Обновить frontmatter** через `Edit`:
   - Set / update `crypto-alignment`, `crypto-source`, `blocks-on`, `crypto-sweep-date`.
   - **НЕ трогать другие frontmatter поля** (status, priority, labels, ordinal, references остаются как есть).
   - **НЕ трогать Description / AC** — только frontmatter.
4. **Записать в отчёт** одну строку с diagnosis + рекомендованным действием.

### Step 4 — Критерии классификации

Проверяем в порядке приоритета — первое совпадение определяет marker:

**`scope-reset` если**:
- Description упоминает устаревшую архитектуру: `envelope-with-recipient-set` / `recipient-list` / `access-grants` / `RecipientResolver` как основной механизм (а не как compat layer).
- Description строится на `Signal Sender Keys` для group encryption (мы выбрали MLS в Блоке 4).
- Description assumes `server-side transcoding` / `server can inspect content` (нарушает Ρ.2).
- Description hardcode'ит `stableId` / `Firebase UID` в domain paths (нарушает Ξ.5 opaque port pattern).
- В Часть Σ mentor-overview task явно указан как reset / rewrite / merge.

**`needs-desc-update` если**:
- Feature scope верный, но упоминает detail'ы противоречащие текущим секциям mentor-overview (например «AES-CBC» вместо ChaCha20-Poly1305 если так решено).
- Description старше 30 дней **и** касается крипто-темы, mentor-overview обновлялся с тех пор в relevant секциях.
- Missing `crypto-source` frontmatter но task явно крипто-relevant.

**`blocked-on-question` если**:
- Task depends on unresolved Q-NN из open-questions.md (там указано в «Blocks tasks»).
- Task затрагивает area где нет **decided** секции в mentor-overview (например history-backup, cross-platform IdentityVault, Durable Objects design).

**`new-from-mentor` если** (проверяется отдельно — task ещё не существует):
- В mentor-overview / open-questions есть actionable item без соответствующего backlog task (например «Q-17 abuse response — нет task'а»).
- В этом случае skill не редактирует frontmatter (task'а нет), а **записывает в отчёт как рекомендацию `backlog task create`**.

**`parked` если**:
- Task семантически dublicated другим task'ом или mentor-overview section (например Часть Σ говорит «TASK-40 duplicate of Блок 3»).
- Task адресует проблему которая устарела (например original TASK-42 «migration to Signal Sender Keys» после решения выбрать MLS).

**`aligned` если**:
- Не подпадает под критерии выше.
- Description упоминает current mentor-overview секции (или неявно с ними согласована).
- `crypto-source` указан и актуален.
- Все blocks-on разрешены.

### Step 5 — Сгенерировать отчёт

Формат (Markdown в терминал + опционально записать в `docs/dev/crypto-alignment-sweep-YYYY-MM-DD.md`):

```markdown
# Crypto Alignment Sweep — YYYY-MM-DD

**Scope**: <batch description, e.g. «label:crypto» / «Q-09 blocked tasks» / «TASK-40..48»>
**Tasks scanned**: N

## Summary

| Status | Count |
|---|---|
| aligned | X |
| needs-desc-update | X |
| scope-reset | X |
| blocked-on-question | X |
| parked | X |
| needs-review (unchanged) | X |

## Detail per task

### TASK-N: <title>

- **Marker**: `<new marker>` (was `<old marker>` or `unset`)
- **crypto-source**: `[Ξ.5, Π.7]` (added / unchanged)
- **blocks-on**: `[Q-09]` (if applicable)
- **Diagnosis**: <one-sentence explanation>
- **Recommended action**: <one-sentence, actionable>
  - Пример для scope-reset: «Rewrite Description секцию «Что входит технически» полностью, opираясь на mentor-overview Блок 4 (MLS group) + Ξ.5 (opaque port pattern). Промт для /speckit.specify — переделать с нуля.»
  - Пример для needs-desc-update: «В Description секции «Что входит технически» заменить упоминание server-side compression на client-side (Ρ.2). Обновить один bullet.»
  - Пример для blocked-on-question: «Не брать в /speckit.specify пока Q-09 (history backup) не decided. Ждать mentor-сессии theme-12-history-backup.»
  - Пример для parked: «Не редактировать. При закрытии другого task'а (например TASK-58) — рассмотреть close этого как duplicate.»

## Recommended new tasks (new-from-mentor)

- `QuotaEnforcer` port + Cloudflare Durable Objects setup. Source: `Π.3`, `Ξ.5`.
- `AbuseResponse` mechanism (report + hash blocklist). Source: `Π.6`.
- `BlobStorage` port + presigned upload tokens. Source: `Π.2`, `Ρ.4`.

Owner: рассмотреть создание через `backlog task create` (см. `.claude/skills/backlog-task-format`).

## Recommended next mentor session

Из open-questions.md priority queue — Q-NN (session-tag=theme-X).
```

### Step 6 — Commit changes

Skill сам **не** делает commit. Возвращает control владельцу с сообщением:

```
✅ Sweep complete. N task frontmatter обновлены.
Report: docs/dev/crypto-alignment-sweep-YYYY-MM-DD.md (optional persistence).
Next: owner decides — commit changes or review individual tasks first.
Recommended commit message:
  `chore(backlog): crypto alignment sweep — <scope description>`
```

## Что skill НЕ делает

- ❌ НЕ редактирует `Description` / `Acceptance Criteria` секции task'ов. Только frontmatter.
- ❌ НЕ переписывает mentor-overview.md.
- ❌ НЕ создаёт новые task'и через `backlog task create` (рекомендует владельцу).
- ❌ НЕ закрывает open-questions Q-NN (это делает mentor-сессия).
- ❌ НЕ меняет status / priority / labels / dependencies task'ов.
- ❌ НЕ пытается пройти > 15 задач в одном sweep'е (fatigue → халтура).
- ❌ НЕ делает commit автоматически.

## Обработка ошибок

- **Task frontmatter повреждён (не YAML)** — skip, записать в отчёт как «malformed frontmatter — needs manual fix».
- **`docs/dev/crypto-mentor-overview.md` не найден** — abort с ошибкой (это предусловие).
- **Batch пустой** — сообщить владельцу и остановиться.
- **crypto-source ссылается на несуществующую секцию** — warning в отчёте, marker всё равно проставляется.

## Пример invocation

**Владелец**: «Пройди все задачи связанные с pairing и multi-admin по крипте.»

**Skill**:
1. Reads mentor-overview.md, open-questions.md.
2. `backlog task list --plain -l crypto` + filter по keyword pairing / admin в labels / title.
3. Формирует batch: TASK-8 (Admin Pairing), TASK-31 (LinkInvitePairingChannel), TASK-42 (group encryption), TASK-46 (shared admin book), TASK-58 (MLS research), TASK-67 (Pairing Feature Bucket).
4. Для каждого — читает файл, классифицирует.
5. Обновляет frontmatter каждого.
6. Возвращает отчёт с diagnosis + рекомендациями.

**Результат** (пример):
- TASK-8 → `aligned` (уже подтягивает Блок 2 pairing).
- TASK-31 → `blocked-on-question` [Q-11] (revoke policy для link-invite).
- TASK-42 → `scope-reset` (Signal Sender Keys устарел, теперь MLS Блок 4).
- TASK-46 → `parked` (duplicate of Блок 5 profile sync + MLS group).
- TASK-58 → `needs-desc-update` (research уже частично done — MLS выбран в mentor-overview).
- TASK-67 → `aligned` (уже строится под Блок 2 + Π.2 + Ξ.5).

## Связанные skills

- `procedure-sync-backlog-description` — синхронизирует description **после** speckit-цикла. Этот skill — **до** speckit'а, проставляет alignment markers.
- `procedure-sync-backlog-ac` — синхронизирует AC. Не пересекается.
- `pre-pr-backlog-sync` — синхронизирует status + AC перед PR. Не пересекается.
- `backlog-task-format` — формат task-файла (SECTION маркеры). Этот skill использует его convention для frontmatter.
- `mentor` — discussion mode. Этот skill — post-mentor triage.

## Session hygiene reminder

После каждой mentor-сессии крипто владелец обязан (per CLAUDE.md §Session hygiene):

1. **Update mentor-overview.md** — новая секция для решённых вопросов.
2. **Update crypto-open-questions.md** — вычеркнуть решённые, добавить новые всплывшие.
3. **Опционально запустить этот skill** — sweep задач, затронутых новыми решениями (batch 10-15 задач).

Skill окупается когда есть > 3 новых секций в mentor-overview за последние 2 недели.
