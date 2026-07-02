---
name: procedure-decision-drift-check
description: Walks the `dependencies:` graph of backlog tasks and flags downstream tasks whose upstream decision-task has been superseded (`superseded-by` field set) or whose Decision block has been edited after status transitioned past Discussion. Replaces the retired `procedure-crypto-alignment-sweep` — much thinner (no domain-specific frontmatter), works for any architectural domain via CLAUDE.md rule 11 Discussion→Decision→Done model.
---

# Procedure: decision-drift-check

Проходит по backlog-task'ам с заполненным `dependencies:`, проверяет что upstream decision-task'и не были superseded'ы или не получили post-freeze edit'ы в Decision block'е.

## Зачем

Per CLAUDE.md rule 11, cross-task references идут **только** через `dependencies:` graph. Downstream task зависит от upstream `TASK-N Decision block` как от контракта. Когда upstream Decision заменяется новым (через `decision-supersedes` mechanism) — downstream tasks нужно review.

Skill автоматизирует detection этого drift'а.

**Vs retired `procedure-crypto-alignment-sweep`**:
- **Sweep** проставлял markers на 6 frontmatter полях в task'ах, был domain-specific (crypto only), требовал ручного invocation после каждой mentor-сессии.
- **Drift-check** walks existing `dependencies:` graph (не добавляет новые frontmatter поля), universal (любой архитектурный домен), invoke-able когда угодно или в CI/pre-commit hook.

## Когда вызывать

- **После merge PR'а с decision-task**: если Decision block edited (status transitioned past Discussion + Decision content changed) — check downstream.
- **После создания supersedeng task'а**: TASK-N `superseded-by: TASK-K` → walk deps → flag downstream.
- **Периодически** (weekly manual или в CI weekly job): sweep всех Done decision-task'ов на нестыковки.
- **Перед `/speckit.specify`** feature-task'а: verify все её dependency Decision block'и актуальны.

## Предусловия

1. Backlog-tasks следуют `backlog-task-format` skill: valid frontmatter, `SECTION:DISCUSSION` с `### Decision (English, immutable) 🔒` sub-блоком для decision-task'ов.
2. Decision-task'и содержат `decision-supersedes` и `superseded-by` поля в frontmatter (даже если пустые / null).
3. Feature-tasks корректно указывают `dependencies:` list.

## Алгоритм

### Step 1 — Собрать decision-task'и

1. `ls backlog/tasks/*.md` → parse frontmatter.
2. Фильтр: task'и у которых `SECTION:DISCUSSION` содержит `### Decision (English, immutable) 🔒` sub-блок.
3. Сохранить: `{task-id, status, superseded-by, decision-supersedes, decision-block-hash}`. Hash = SHA-1 первых 500 байт Decision block'а.

### Step 2 — Собрать dependency graph

Для каждого backlog-task:
1. Parse `dependencies:` из frontmatter.
2. Построить reverse index: `{decision-task-id → [dependent-task-ids]}`.

### Step 3 — Проверить drift

Для каждого decision-task:

**Case A: `superseded-by: TASK-K` установлен**:
- Каждый downstream task в reverse index → warn: «depends on superseded TASK-N (now TASK-K). Review whether to migrate.»

**Case B: Decision block hash изменился после последнего known hash** (для этого нужен snapshot; в MVP — просто check timestamp `updated_date` vs `decided_date` метаданных в task).
- Пометить как potentially-drifted. Downstream tasks → warn: «upstream Decision may have shifted. Re-read.»

**Case C: Feature-task имеет `dependencies: [TASK-N]` где `TASK-N` не существует или в `Discussion` статусе**:
- Warn: «broken dependency» или «depends on unresolved discussion».

### Step 4 — Сгенерировать отчёт

```markdown
# Decision Drift Check — YYYY-MM-DD

**Decision tasks scanned**: N
**Feature tasks with dependencies**: M

## Superseded decisions with active downstream

- **TASK-101** (Done, superseded-by TASK-201) → downstream: TASK-27, TASK-28, TASK-32.
  Action: review each downstream to migrate to TASK-201 Decision or add TASK-201 to dependencies alongside.

## Broken dependencies

- **TASK-45** dependencies `[TASK-999]` — TASK-999 does not exist.
- **TASK-67** dependencies `[TASK-11 (status: Discussion)]` — depends on unresolved discussion.

## Potentially drifted decisions

- **TASK-58** (Done, updated_date 2026-07-15 > decided_date 2026-07-01) → downstream: TASK-27, TASK-42, TASK-46, TASK-70.
  Action: read TASK-58 Decision block, verify unchanged intent.

## No issues

X decision tasks with clean downstream.

## Summary

Total warnings: N. Recommend: review flagged downstream tasks and update dependencies or Decision references.
```

### Step 5 — Return control

Skill сам **не** редактирует ничего. Возвращает отчёт. Owner / другой AI решает по каждой warn:
- Add new dependency alongside old.
- Fully migrate downstream to new Decision.
- Ignore (drift был intentional, downstream действительно ссылается на old contract).

## Что skill НЕ делает

- ❌ Не редактирует Decision block (immutable per rule 11).
- ❌ Не редактирует dependencies (owner responsibility).
- ❌ Не создаёт новые tasks.
- ❌ Не меняет status.
- ❌ Не delete'ит superseded task'и (они остаются как archival).

## Обработка ошибок

- **Task frontmatter повреждён** — skip, записать в отчёт как «malformed frontmatter».
- **`SECTION:DISCUSSION` без Decision block** — если status past `Draft` — warn как «Decision block missing».
- **Circular dependencies** — detect cycles, warn.
- **Backlog folder пустой** — сообщить и остановиться.

## Пример invocation

**Владелец**: «Проверь decision drift.»

**Skill**:
1. Reads all `backlog/tasks/*.md`.
2. Identifies decision-tasks (те с Decision block).
3. Builds dependency reverse-index.
4. Runs Cases A, B, C.
5. Prints report.

**Владелец**: применяет action items вручную или через `Edit` tool.

## Связанные skills

- `backlog-task-format` — формат task'а + Discussion + Decision block.
- `pre-pr-backlog-sync` — AC + status sync перед PR.
- `procedure-sync-backlog-description` — sync description после speckit-cycle.
- `mentor` — invoke внутри Discussion-задачи.

## Migration от retired `procedure-crypto-alignment-sweep`

Для task'ов, которые содержали устаревшие `crypto-alignment`, `crypto-source`, `blocks-on`, `crypto-sweep-date` frontmatter поля — просто **удалить** эти поля. Machine-readable контракт теперь живёт в `dependencies:` graph + Decision block.

Пилотные task'и (TASK-40, 42, 46, 58, 66, 67) очищены при migration branch'е.
