# ADR-009: AI affordance via Capability Registry + Exposure Adapter

**Status**: Accepted (2026-05-28)
**Date**: 2026-05-28
**Decided in**: Phase 0 vision discussion (use-case 12 — AI integration).
**Linked artifacts**:
- [`docs/product/use-cases/12-ai-integration.md`](../product/use-cases/12-ai-integration.md)
- [`docs/product/roadmap.md`](../product/roadmap.md) (F-2 Capability Registry foundation spec)
- [`CLAUDE.md`](../../CLAUDE.md) §1 (domain isolation), §2 (Anti-Corruption Layer)
- Skill [`checklist-ai-readiness`](../../.claude/skills/checklist-ai-readiness/SKILL.md)

---

## Context

### Проблема

Vision позиционирует продукт как family-care ecosystem с потенциальным AI-усилением: «найди бабушке удобный пресет», «подскажи следующий шаг настройки», «сгруппируй фото за неделю». При этом:

1. **Конкретный AI-провайдер выбирать рано.** Gemini Nano, Claude, OpenAI, MCP-серверы — все эволюционируют быстрее, чем мы выпустим MVP. Lock-in на одного провайдера сегодня = переписывать через 6 месяцев.
2. **Принципиально нельзя позволить SDK-типам провайдера проникнуть в домен.** Это нарушение CLAUDE.md rule 1. Если завтра мы меняем Gemini на Claude — должны переписать один адаптер, не половину приложения.
3. **AI-функции — value-add, а не value-core.** MVP должен работать **без** AI: контакты звонят, фото открываются, SOS срабатывает. AI добавляется поверх как «акселератор».
4. **PII не должна утекать LLM-провайдеру по умолчанию.** Имена контактов, фото, медкарта — всё это пользовательский plaintext. Любой исходящий трафик к третьей стороне = privacy decision.

### Constraints

- Нельзя ставить Gemini SDK / OpenAI SDK / Anthropic SDK в `core/` или в любую feature module — только в выделенный adapter.
- Нельзя проектировать спеки под конкретный prompt-формат / function-call schema провайдера.
- Capability должен быть выразим как **доменный глагол** (`createFamilyGroup(name)`, `inviteMember(groupId, ref)`), не как HTTP endpoint и не как SDK call.
- В MVP не строим ни одной реальной AI-интеграции — только архитектурную готовность.

### Альтернативы рассмотренные и отвергнутые

| Альтернатива | Почему отвергнута |
|---|---|
| **Ничего не делать сейчас, добавим позже** | Capability shape всё равно проектируется в каждой спеке F-1..S-8. Без guardrails окажется, что каждый feature module вытащил SDK-типы наружу, и future-AI-spec = переписывание. |
| **Сразу выбрать Gemini Nano и проектировать под него** | One-way door на конкретный провайдер; lock-in; SDK ещё в active development; on-device limits меняются |
| **Сразу выбрать MCP и проектировать под него** | MCP — это transport, а не семантика; всё равно понадобится capability registry над ним |
| **Спрятать AI-интеграцию в один общий «AssistantService»** | Single-implementation interface на будущее — нарушение CLAUDE.md rule 4 (MVA); добавит абстракцию без текущего consumer'а |

---

## Decision

**Принято: каждая feature exposes capabilities через Capability Registry + Exposure Adapter паттерн.**

### Capability Registry (домен)

Каждая capability — это:

- **domain verb** с типами из домена (`createFamilyGroup(name: GroupName, locale: Locale): GroupId`);
- **natural-language description** (one-line, для grounding'а агента);
- **affordance contract**: что читает, что пишет, идемпотентна ли, обратима ли, требует ли подтверждения;
- **PII boundary**: возвращает опаковые handles, не сырую PII, если не задекларировано иначе.

Registry — простой `Map<CapabilityId, CapabilityMeta>` плюс runtime dispatch. **Не** framework. **Не** dependency injection container поверх существующего DI.

### Exposure Adapter (вне домена)

Конкретный провайдер (`GeminiExposureAdapter`, `ClaudeMcpAdapter`, `LocalRuleEngineAdapter`) живёт в отдельном модуле, реализует выбранный transport (function calling, MCP, rule matching), и **сводит** capability call'ы к доменным глаголам через registry.

- В MVP: ни одного adapter'а не ставится. Только registry + capability decls + fake `LocalRuleEngineAdapter` для тестов.
- Post-MVP: первая интеграция — `GeminiNanoExposureAdapter` (on-device, no PII leaves device). Каждая следующая — additive: новый adapter, тот же registry.

### Guardrails

- **Skill `checklist-ai-readiness`** активируется в `procedure-assess-spec-complexity` для каждого спека, где есть user-facing action.
- **Spec template** обязывает заполнить секцию «AI Affordance»: какие capabilities exposable, или явный `no AI affordance — [reason]`.
- **Refuse pattern**: SDK-тип AI-провайдера в доменной сигнатуре → отказ + предложение adapter pattern.

---

## Consequences

### Positive

- AI integration — additive change в любой момент; одна новая adapter-модуль на провайдера.
- Privacy by design: registry → adapter → провайдер; PII фильтруется на границе adapter'а, не в феатур-коде.
- Тестируемость: `LocalRuleEngineAdapter` с детерминированными ответами заменяет LLM для unit-тестов.
- Multi-provider возможен без переписывания: один и тот же capability вызывается через разные adapter'ы.

### Negative

- Лёгкая overhead: каждое user-facing действие должно быть declared в capability terms в спеке (один paragraph). Mitigation: skill автоматизирует проверку.
- Соблазн перепроектировать registry в monolithic AI framework. Mitigation: rule 4 MVA + checklist-meta-minimization.

### Neutral

- Phase 0 MVP не ставит ни одной AI-фичи. Все capabilities декларируются, но fake-adapter в DI.

---

## Exit ramp

Если паттерн окажется лишним — registry схлопывается в простой `sealed class Capability` + `when`-dispatch внутри одного модуля. Стоимость отката: 1 день, без изменения wire format'а и без миграций.

Если LLM landscape стабилизируется на одном провайдере (маловероятно к 2027) — registry упрощается до direct adapter calls; exposure adapter становится thin shim.

---

## How to apply

1. Каждый спек с user-facing action заполняет секцию «AI Affordance» в spec-template.
2. `procedure-assess-spec-complexity` запускает `checklist-ai-readiness` автоматически.
3. F-2 (Capability Registry foundation) — первый спек, который реализует registry как domain port.
4. Первый Exposure Adapter — отдельный пост-MVP спек, не входит в S-1..S-8.
