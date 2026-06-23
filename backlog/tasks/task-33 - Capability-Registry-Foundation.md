---
id: TASK-33
title: Capability Registry Foundation
status: Draft
assignee: []
created_date: '2026-06-23 05:40'
updated_date: '2026-06-23 06:33'
labels:
  - phase-4
  - f-spec
  - f-2
  - capability-registry
  - ai-ready
  - deferred
milestone: m-3
dependencies: []
priority: medium
ordinal: 33000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Внутренний реестр (registry) всех **возможностей** приложения — каждая возможность называется явно (например, «позвонить контакту», «отправить SOS», «обновить config»). Когда появятся AI-помощники (Google Assistant, MCP-сервер, локальная Gemini), они смогут читать этот реестр и выполнять действия в приложении через единый интерфейс.

**Что происходит по шагам (когда F-2 будет готова):**
1. В коде каждое действие декларируется как capability: «`call_contact(contact_id)`», «`send_sos()`», «`update_config(config_id)`».
2. CapabilityRegistry хранит список всех capabilities (декларативно).
3. Каждая capability имеет `ExposureAdapter` — переводит её в формат конкретного AI-провайдера:
   - `AppActionsExposureAdapter` — Google Assistant.
   - `MCPExposureAdapter` — MCP server.
   - `GeminiNanoExposureAdapter` — локальная Gemini.
4. Пользователь говорит Google Assistant'у «позвони бабушке» → Google Assistant вызывает соответствующее capability → действие выполняется в приложении.

**Зачем "Foundation" а не «реализация»:**
- F-2 строит только **port + interface + Fake adapter** для тестов.
- Конкретные провайдеры (Google Assistant, MCP, Gemini) — отдельные задачи TASK-36 L-3 в Phase 5.
- Это **defer-pattern** (per мета-правило «defer if no rewrite needed»): строим швы для AI **сейчас**, чтобы потом добавлять провайдеров было дописыванием (additive), не переписыванием.

**Откуда capabilities берутся:**
- Через Phase 2, Phase 3, начало Phase 4 в коде расставлены TODO-маркеры `// TODO(capability-registry): ...` (через checklist-capability-registry-readiness skill).
- F-2 собирает все эти TODO в один реестр.

## Зачем

К моменту когда мы захотим интегрировать AI (Phase 5 L-3) — нам нужна **готовая абстракция**. Если построим F-2 после потребителя — придётся переписывать всё. Если построим до момента когда AI становится приоритетом — преждевременная абстракция.

**Поэтому F-2 запланирована на конец Phase 4 (после messenger / album / wearable) и ДО Phase 5 (где первая AI integration).**

## Что входит технически (для AI-агента)

- `CapabilityRegistry` port в `core/capability/`.
- `ExposureAdapter` interface + `FakeAdapter` для тестов.
- Собрать все накопленные `TODO(capability-registry)` маркеры в код → завести соответствующие capabilities в registry.
- ADR-008 «AI affordance posture» написан (если ещё не было).

## Состояние

**Planned (в Phase 4).** Отложен из Phase 1 решением 2026-06-15 v3. Активируется когда появится первый AI-integration consumer (TASK-36 L-3a/b/c).

---

## Готовый промт для `/speckit.specify`

```
Реализуй F-2: Capability Registry Foundation.

ЧТО СТРОИМ:
Декларативный реестр всех capabilities приложения. CapabilityRegistry port в core/capability/. ExposureAdapter interface + FakeAdapter (concrete провайдеры — Google App Actions / MCP / Gemini Nano — будут TASK-36 L-3 в Phase 5). Собрать накопленные TODO(capability-registry) markers в registry. ADR-008 «AI affordance posture: AI-ready, not AI-built» написан.

ЗАЧЕМ:
К моменту первой AI integration (Phase 5 L-3) — должна быть готовая абстракция. Defer-pattern: швы строим сейчас, провайдеры дописываются additively. Без F-2 первая AI integration = переписывание codebase.

SCOPE ВКЛЮЧАЕТ:
- CapabilityRegistry port в core/capability/.
- ExposureAdapter interface (для будущих AI-providers).
- FakeAdapter для tests.
- Сбор всех TODO(capability-registry) markers из Phase 2+3+4 кода в registry.
- ADR-008: «AI affordance posture — AI-ready, not AI-built».
- Capability declaration syntax: каждое action в коде явно описано (что делает, какие parameters).
- Updates to procedure-assess-spec-complexity: capability-registry-readiness checklist уже активирован.

SCOPE НЕ ВКЛЮЧАЕТ:
- Реальные AI-providers (Google Assistant, MCP, Gemini Nano) — TASK-36 L-3 в Phase 5.
- AI-driven UI suggestions (post-MVP).
- Voice activation в самом приложении (отдельная фича).

DEPENDENCIES:
- Накопленные TODO(capability-registry) markers через Phase 2+3+начало Phase 4.
- checklist-capability-registry-readiness skill (уже работает).
- docs/dev/capability-registry-pending.md индекс (уже работает).

ACCEPTANCE CRITERIA:
- CapabilityRegistry port создан + 5+ capabilities из existing TODOs зарегистрированы.
- FakeAdapter покрыт unit-tests: registry.list() → expected capabilities, registry.execute(id, params) → success/failure.
- ADR-008 написан и merged.
- Все TODO(capability-registry) в коде обработаны (либо capability зарегистрирована, либо TODO явно перенесено в Phase 5 с обоснованием).
- Documentation: «как добавить capability» — простой русский в docs/dev/.
- Fitness function: новые actions без capability registration — fail build (post-F-2).

LOCAL TEST PATH:
- Unit-tests CapabilityRegistry + FakeAdapter.
- Grep-проверка: НИ ОДНОГО action не объявлено без capability registration.
- Compile-time check (lint rule).

CONSTITUTION GATES:
- Rule 1 (domain isolation): CapabilityRegistry — pure domain.
- Rule 2 (ACL): AI-providers SDK не вытекают в domain (FakeAdapter, future adapters только).
- Rule 4 (minimum viable architecture): F-2 = port + Fake, реальные providers — следующая фаза.

EFFORT: Medium (~2 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ExposureAdapter interface + FakeAdapter
- [ ] #2 Все накопленные TODO(capability-registry) обработаны
- [ ] #3 ADR-008 'AI affordance posture' написан
- [ ] #4 CapabilityRegistry port создан + 5+ capabilities из existing TODOs зарегистрированы
- [ ] #5 FakeAdapter покрыт unit-tests: registry.list() → expected, registry.execute(id, params) → success/failure
- [ ] #6 ADR-008 'AI affordance posture' написан и merged
- [ ] #7 Все TODO(capability-registry) markers в коде обработаны
- [ ] #8 Documentation 'как добавить capability' — простой русский в docs/dev/
- [ ] #9 Fitness function: новые actions без capability registration — fail build
<!-- AC:END -->
