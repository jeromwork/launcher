# Launcher — Governance Document Map

Этот файл объясняет, **где именно хранить правила проекта**, чтобы:
- ИИ через Spec Kit видел контекст в репозитории;
- конституция не превращалась в перегруженную свалку;
- новые правила можно было добавлять без переписывания архитектуры документов.

## 1. Канонический источник правил

### `.specify/memory/constitution.md`
Сюда кладутся только:
- долгоживущие и обязательные правила;
- архитектурные и governance-инварианты;
- обязательные проверки для каждого `plan.md`;
- правила, нарушение которых блокирует одобрение изменений.

### Чего НЕ должно быть в конституции
- текущих ставок, налогов, комиссий;
- подробных store policy excerpts;
- текущих списков стран и каналов;
- подробных рыночных сравнений;
- одноразовых продуктовых решений;
- быстро меняющихся коммерческих деталей.

## 2. Где хранить изменяемый контекст

### `docs/adr/`
Сюда кладутся архитектурные решения, которые важны, но не должны раздувать конституцию.
Рекомендуемые ADR:
- `ADR-001-cross-platform-strategy.md`
- `ADR-002-licensing-and-anti-abuse.md`
- `ADR-003-monetization-entitlements.md`
- `ADR-004-localization-and-global-readiness.md`

### `docs/compliance/`
Сюда кладутся живые реестры ограничений и проверок:
- `store-policy-register.md`
- `distribution-channel-register.md`
- `country-legal-tax-register.md`
- `permissions-and-resource-budget.md`
- `partner-distribution-model.md`

### `docs/product/`
Сюда кладутся приоритеты и продуктовый контекст:
- `feature-priorities.md`
- `context-decisions-and-open-questions.md`

### `docs/research/`
Сюда кладутся исследования:
- `market-and-channel-research.md`
- `messenger-calling-research.md`
- `subscription-model-research.md`

### `docs/operations/`
Сюда кладутся операционные процессы:
- `support-and-feedback-ops.md`

## 3. Что обязан делать каждый новый `plan.md`

Каждый `plan.md` обязан:
- ссылаться на конституцию;
- пройти все Constitution Check gates;
- явно указывать platform parity gap;
- указывать monetization/compliance/localization impact;
- указывать support/error/feedback ownership;
- обновлять соответствующие ADR и регистры, если фича затрагивает их.

## 4. Как добавлять новые правила позже

### Если правило:
- почти никогда не меняется,
- относится ко всему проекту,
- должно быть обязательным для всех фич,

то оно идет в **конституцию**.

### Если правило:
- архитектурное, но может уточняться,
- связано с одним направлением (billing, cross-platform, partner distribution),

то оно идет в **ADR**.

### Если правило:
- зависит от магазинов, стран, налогов, партнеров, разрешений, политик,

то оно идет в **compliance register**.

### Если это:
- приоритеты продукта,
- текущие must-have / not-yet-must,
- вопросы для обсуждения,

то это идет в **product docs**.

## 5. Практическое правило для ИИ

При работе через Spec Kit ИИ должен читать в таком порядке:
1. `.specify/memory/constitution.md`
2. `docs/governance/document-map.md`
3. релевантные ADR
4. релевантные compliance docs
5. релевантный `spec.md`
6. релевантный `plan.md`

Так ИИ будет видеть и жесткие правила, и живой контекст проекта.
