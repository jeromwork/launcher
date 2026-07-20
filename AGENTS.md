# AGENTS.md — общий контракт для AI-агентов

Этот файл — **единственный источник правды** для AI-агентов, работающих в этом репозитории (Claude Code, Gemini в Antigravity, и любые другие). Все агенты обязаны прочитать его перед любой работой.

Подробности и обоснования живут в `CLAUDE.md` (Anthropic-specific deltas) и `.specify/memory/constitution.md` (Spec Kit constitution). AGENTS.md содержит **минимум**, общий для всех агентов.

---

## 1. Разделение труда между агентами

| Фаза работы | Кто выполняет | Почему |
|---|---|---|
| Архитектурные решения, one-way doors | **Claude (Opus)** | Требует reasoning, конституция, mentor-стиль |
| `/speckit.specify` — формулировка ЧТО | Claude | Контекст проекта, plain Russian |
| `/speckit.clarify` — organic-вопросы | Claude | Article XIX, mentor-стиль |
| `/speckit.scenarios` — последовательности | Claude | Владелец читает их сам |
| `/speckit.plan` — архитектура + Constitution Check | Claude | One-way door, гейты Article XVI |
| `/speckit.tasks` — декомпозиция | Claude | Trace на FR/US, FR-номера |
| `/speckit.analyze` — финальный audit | Claude | Cross-artifact verdict |
| `/speckit.implement` — код + тесты + ticks в tasks.md | **Gemini (Antigravity)** | Mechanical, по готовому плану |
| `git add/commit/push` для рутинной фазы | Gemini | Не тратит лимит Claude |
| Review PR / review diff | Claude | Last gate перед merge |
| mentor / one-way-door обсуждения | Claude | Critical stance |

**Жёсткое правило для Gemini**: при выполнении `/speckit.implement` **запрещено**:
- создавать новые абстракции, не описанные в `plan.md`;
- менять структуру модулей, переименовывать порты;
- изменять wire-format (anything с `schemaVersion`);
- принимать архитектурные решения «на ходу».

Если задача в `tasks.md` неясна — **STOP, спросить владельца**, не догадываться.

## 2. Язык общения

- **Plain Russian** для всех explanations, вопросов, comments в чате.
- Технические термины (Activity, port, adapter, wire format) — на английском, в скобках расшифровка при первом упоминании.
- Code identifiers, file paths, commands, log messages — английский (как принято в коде).
- Verdict checklist'ов в `specs/<task>/checklists/*.md` — пишутся на английском (агент-internal), но **итоговое заключение для владельца** — на русском.

## 3. Personas vs domain roles

«Бабушка», «дочка», «admin-родственник», «внук», «семья» — **иллюстративные персоны**, не доменная модель. В доменных формулировках использовать обобщённые роли:
- `primary user` (или `device owner`) — основной пользователь устройства.
- `remote administrator` (или `admin`) — пользователь с полным remote доступом.
- `restricted caregiver` (или `caregiver`) — пользователь с ограниченным доступом.
- `family group` / `care group` / `shared space` — abstract группа.

Конкретные персонажи допустимы только в опциональной секции `## Пример сценария (use-case)`.

## 4. Engineering rules (выжимка из CLAUDE.md)

1. **Domain isolated from infrastructure.** Domain не импортирует vendor SDK, transport types, platform system types. Внешняя поверхность — через port в domain, adapter в отдельном модуле.
2. **Anti-Corruption Layer для каждого external dependency.** Если vendor исчезнет — должно меняться меньше файлов, чем в одном adapter-модуле.
3. **One-way vs two-way doors.** Перед non-trivial change классифицировать. Для one-way door — **обязательно exit ramp** (как откатить).
4. **Minimum Viable Architecture.** Абстракция добавляется только если без неё будущее изменение = переписывание, не дописывание.
5. **Wire-format versioning.** Всё, что уходит с устройства или persistent across версий, — wire format. Правила целиком в [`docs/architecture/wire-format.md`](docs/architecture/wire-format.md) (единственный источник; здесь не дублируем).
6. **Mock-first development.** Domain + UI строятся против fake adapter'ов до интеграции реальных SDK.
7. **Fitness functions.** Архитектурные инварианты — lint/test, не manual review.
8. **Server migration tracking.** Каждое client-side обходное решение фиксируется в `docs/dev/server-roadmap.md` + inline TODO `// TODO(server-roadmap): ...`.
9. **Shareability-readiness.** User-facing конфигурация (layouts, themes, presets) — portable shareable artifact с `schemaVersion` через `ConfigSource` adapter.
10. **Notification minimization.** Каждый push должен пройти 3 критерия: actionable + time-sensitive + user-relevant. Иначе — in-app indicator.

Детали и обоснования каждого правила — в `CLAUDE.md`.

## 5. Refuse and propose alternative — если видите

- Vendor/system type в domain value.
- Domain function возвращает transport/DTO type.
- UI вызывает SDK напрямую.
- Static singleton с external SDK.
- Wire format без `schemaVersion`.
- Schema field переименован без migration.
- Test, мокающий domain вместо adapter'а.
- One-way door без exit ramp.
- Premature abstraction (single-impl interface без port-shaped seam).
- New external dependency в domain для одной фичи.
- Public contract change без major version bump.
- User-facing config без `ConfigSource` adapter pattern.
- Push notification без declared severity criterion.
- PR без `pre-pr-backlog-sync` (для Claude Code).
- AC проставлен `[x]` без проверки `[deferred-*]` маркеров в `tasks.md`.

Surface проблему одной строкой, предложите корректную форму, продолжайте.

## 6. Spec Kit workflow

Любая фича = один backlog-task (`backlog/tasks/task-N - title.md`) + одна спека (`specs/task-N-slug/`).

Pipeline (все фазы — у Claude, кроме `/implement`):
1. `/speckit.specify` → `spec.md`
2. `/speckit.clarify` → secция `## Clarifications` в `spec.md` (3-7 organic вопросов, Article XIX)
3. `/speckit.scenarios` → `## Сценарии использования` в `spec.md`
4. `/speckit.plan` → `plan.md`, `research.md`, `data-model.md`, `contracts/` + Constitution Check
5. `/speckit.tasks` → `tasks.md` с T-номерами, trace на FR/US
6. `/speckit.analyze` → cross-artifact audit, verdict PASS/FAIL
7. **`/speckit.implement`** → реальный код, тесты, ticks `[x]` в `tasks.md`. **Эту фазу делает Gemini.**

**Tasks.md tick-sync (HARD RULE)**: каждый implementation commit, закрывающий `Tnnn`, обязан в том же diff'е проставить `[x]` напротив этих task'ов. Запрещено «потом догонит».

## 7. Backlog status workflow (5 статусов)

- `Draft` — идея, ничего не написано.
- `In Progress` — task взят в работу, идёт Spec Kit pipeline.
- `Verification` — PR merged, ждём manual гейтов (emulator/device/firebase emulator).
- `Paused` — работа начата, владелец переключился; есть частичная работа в stash/ветке/spec'е.
- `Done` — merged + все AC `[x]` или `[N/A]`.

**Никакого «merged = Done» автомата.** Решает `pre-pr-backlog-sync` skill (Claude-only).

## 8. Hybrid workflow Claude ↔ Gemini

Обмен через **git и чат**, не через промежуточные файлы.

1. Claude (Opus) делает фазы 1-6 Spec Kit, коммитит spec/plan/tasks.
2. Владелец в Antigravity: `git pull`, говорит Gemini «выполни `/speckit.implement` для `specs/task-N-slug/tasks.md`, фазы T010-Tnnn».
3. Gemini пишет код, тесты, ticks, коммитит, пушит.
4. Владелец возвращается в Claude Code: «сделай review».
5. Claude читает `git diff main`, выносит verdict в чат (PASS / список замечаний).
6. Если FAIL — владелец копирует замечания в Antigravity, цикл повторяется.

**Gemini в Antigravity НЕ читает**: `.specify/memory/constitution.md`, `.claude/skills/*`, MCP инструкции — он не делает архитектурную работу, ему достаточно AGENTS.md + `plan.md` конкретного task'а + `tasks.md`.

## 9. Что вынесено отдельно

- `CLAUDE.md` — Anthropic-specific: skills, subagents, mentor, ScheduleWakeup, organic question budgets, MCP eastclinic.
- `.specify/memory/constitution.md` — полные Article I-XVI с обоснованиями (читается Claude при `/speckit.plan` и `/speckit.analyze`).
- `backlog/tasks/` — Kanban-карточки с описанием фич на простом русском.
- `docs/dev/server-roadmap.md` — exit ramps для client-side обходных решений.
- `docs/product/vision.md` — стратегия, главный фильтр фич.
