# CLAUDE.md — Anthropic-specific addendum

**Сначала прочитай [`AGENTS.md`](AGENTS.md)** — это общий контракт для всех агентов (engineering rules, Spec Kit workflow, разделение труда Claude ↔ Gemini, personas vs roles, plain Russian).

Этот файл содержит **только то, что специфично для Claude Code** и не имеет смысла для Gemini в Antigravity или другого агента.

---

## 1. Skills (Anthropic Skills API)

В `.claude/skills/` лежат skill'ы, доступные **только в Claude Code**. Ключевые:

- **`mentor`** — discussion / deep deliberation mode. Вызывать при discussion-сигналах: «я новичок в X», «что лучше — A или B?», «как работает X?», «стоит ли...», вставленный mentor-промпт, **любой архитектурный выбор / one-way door**, любое подтверждение пользователем выбора из вариантов («ок, берём A»). НЕ вызывать для task-сообщений («сделай», «запусти», `/speckit.*`).
- **`pre-pr-backlog-sync`** — ОБЯЗАТЕЛЬНО перед `gh pr create` на spec-привязанной ветке. Регенерирует `[auto:checklist]` и `[auto:deferred-*]` AC, решает status (Done / Verification / In Progress / Paused).
- **`procedure-sync-backlog-ac`** — sync `## Success Criteria [backlog]` из spec.md в `## Acceptance Criteria` backlog-task'а. Вызывается в `/speckit.clarify` Step 5c и `/speckit.tasks` Step 4c.
- **`procedure-sync-backlog-description`** — sync описания backlog-task'а с финальным состоянием spec/plan/tasks. Вызывается в конце `/speckit.analyze` (verdict PASS).
- **`procedure-constitution-check`** — gate'ы Article XVI. Вызывается из `/speckit.plan` и `/speckit.analyze`.
- **`procedure-assess-spec-complexity`** — выбирает релевантные checklist'ы для spec'а.
- **`procedure-cross-artifact-trace`** — coverage spec ↔ plan ↔ tasks ↔ contracts ↔ checklists.
- **`procedure-translate-spec-strings`** — авто-перевод strings_wizard.xml на 9 локалей через Claude API.
- **`procedure-add-novice-summary`** — TL;DR на plain Russian в конце каждого артефакта.
- **`speckit-specify` / `speckit-clarify` / `speckit-scenarios` / `speckit-plan` / `speckit-tasks` / `speckit-analyze`** — orchestrator'ы Spec Kit фаз.
- **`backlog-task-format`** — правильная разметка `<!-- SECTION:* -->` маркеров.
- **`new-branch`** — pattern j_* для других репо. **В launcher НЕ использовать** (там `task-N-slug`).
- **`checklist-*`** (27 штук — будут консолидированы в TASK-61) — verification checklists.

## 2. Subagents

Claude Code поддерживает per-subagent model selection. Стандартные:
- `Explore` (Haiku) — read-only поиск кода.
- `general-purpose` (inherits) — multi-step tasks.
- `Plan` — implementation strategy.

## 3. Article XIX — Organic Question Budgets

`/speckit.clarify` и `mentor` задают **столько вопросов, сколько требует тема** (3-7 типично). Padding к 5 — bug. Trim ниже organic count — leak архитектурного риска.

## 4. Discussion mode → invoke `mentor`

Discussion-сигналы (любой триггерит mentor):
- «я новичок в X», «не знаю X», «помоги разобраться»;
- choice/evaluation: «что лучше A или B?», «стоит ли», «можно ли заменить»;
- how-it-works: «как работает X?»;
- вставленный mentor-промпт;
- **архитектурное / one-way-door решение в середине разговора**;
- **пользователь выбрал что-то из предложенных вариантов** (перепроверяем выбор, не фиксируем).

НЕ вызывать для: «сделай X», «запусти Y», `/speckit.*`, «создай PR», «обнови файл».

Escape — пользователь явно написал «без mentor, коротко».

## 5. MCP servers

- `claude.ai eastclinic` — backend API для проекта eastclinic. **В launcher не используется**, отключить если активирован. Engineering rules для launcher живут здесь, не в MCP.

## 6. Memory system

`C:\Users\user\.claude\projects\c--work-launcher\memory\` — persistent memory через MEMORY.md index + типизированные `.md` файлы (user / feedback / project / reference). Только Claude Code видит. Gemini в Antigravity не имеет аналога.

## 7. Output discipline (Claude-specific)

- Tool calls параллельно когда независимы; sequentially когда зависят друг от друга.
- Кратко в чате; план в TodoWrite.
- Никаких hook-bypassing флагов без явного approval.
- Не коммитить секреты.
- **Tasks.md tick-sync (HARD RULE)** — см. AGENTS.md §6.

## 8. Conflict resolution

При конфликте инструкции пользователя с правилом из AGENTS.md — surface одной строкой и продолжать на основе ответа.

## 9. Branching

- Feature = своя ветка `task-N-slug`, не `main`.
- `main` — только через PR.
- One feature = one branch = one PR.
- Если scope растёт — follow-up spec + follow-up branch, не расширение текущего PR.

## 10. Где что искать

- Engineering rules + Spec Kit workflow + разделение труда: [`AGENTS.md`](AGENTS.md).
- Полная конституция (Articles I-XVI): [`.specify/memory/constitution.md`](.specify/memory/constitution.md).
- Portfolio (Kanban): `backlog/` через `backlog overview` / `backlog browser`.
- Стратегия: [`docs/product/vision.md`](docs/product/vision.md).
- Server migration roadmap: [`docs/dev/server-roadmap.md`](docs/dev/server-roadmap.md).
