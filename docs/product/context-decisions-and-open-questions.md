# Launcher — Контекст, решения и открытые вопросы

Этот файл фиксирует исходные проектные требования, решения по размещению правил в документах и текущий приоритетный контекст для будущих Spec Kit артефактов.

## Зафиксированные направления

### 1. Базовая платформа — Android, но архитектура не должна блокировать iOS
Решение:
- Android — primary platform.
- Cross-platform parity не предполагается автоматически, но каждый `plan.md` обязан заранее указывать parity gap.
- Доменные модели, конфигурация, entitlement-модель, localization и high-value user flows не должны без необходимости привязываться к Android-only деталям.

Хранение:
- конституция,
- `docs/adr/ADR-001-cross-platform-strategy.md`.

### 2. Если фича невозможна на одной платформе — предупреждать заранее
Решение:
- добавлен обязательный Platform Parity Gate в конституцию;
- каждый `plan.md` обязан фиксировать ограничения, временный/постоянный характер, fallback, влияние на упаковку и ценообразование.

Хранение:
- конституция,
- `docs/adr/ADR-001-cross-platform-strategy.md`.

### 3. Защита от взлома и копирования должна закладываться рано
Решение:
- anti-abuse и monetization security проектируются с ранних этапов;
- система не фиксируется на одном device ID;
- entitlement layer обязателен;
- допускаются store receipt validation, server-side entitlements, device binding, integrity checks, secure cache;
- конкретный механизм выбирается ADR и feature-plan.

Хранение:
- конституция,
- `docs/adr/ADR-002-licensing-and-anti-abuse.md`,
- `docs/adr/ADR-003-monetization-entitlements.md`.

### 4. Must-have направление — звонки/видеозвонки через мессенджеры
Решение:
- это стратегический пользовательский сценарий;
- action architecture должна заранее учитывать deep links, app availability, fallback и платформенные ограничения;
- детальный UX и feature-scope будут оформляться отдельным spec позже.

Хранение:
- конституция (как стратегический flow),
- `docs/product/feature-priorities.md`,
- `docs/research/messenger-calling-research.md`.

### 5. Каналы распространения — сначала исследовать самые доходные и сразу учитывать их ограничения
Решение:
- baseline по умолчанию — official stores;
- alternative distribution и partner distribution — отдельные режимы с отдельным compliance слоем;
- каждый feature plan обязан учитывать влияние канала распространения.

Хранение:
- конституция,
- `docs/compliance/distribution-channel-register.md`,
- `docs/research/market-and-channel-research.md`.

### 6. География — глобальная, с приоритетом по покупательской способности
Решение:
- продукт global-ready;
- localization обязательна с foundation stage;
- региональные ограничения и коммерческие приоритеты ведутся отдельно в живых документах.

Хранение:
- конституция,
- `docs/adr/ADR-004-localization-and-global-readiness.md`,
- `docs/compliance/country-legal-tax-register.md`.

### 7. Монетизация — подписка в приоритете, но архитектура должна переживать смену модели
Решение:
- monetization must be store-decoupled at domain level;
- entitlement model обязательна;
- система должна поддерживать subscription tiers, offers, bundles, one-time purchases и partner packaging без структурной переделки продукта;
- billing subsystem обязателен.

Хранение:
- конституция,
- `docs/adr/ADR-003-monetization-entitlements.md`,
- `docs/research/subscription-model-research.md`.

### 8. Политики магазинов, каналы, юридические и налоговые ограничения должны учитываться постоянно
Решение:
- это не хранится детально в конституции;
- для этого ведутся живые реестры;
- каждый `plan.md` обязан проходить Compliance and Distribution Gate.

Хранение:
- конституция,
- `docs/compliance/store-policy-register.md`,
- `docs/compliance/country-legal-tax-register.md`,
- `docs/compliance/distribution-channel-register.md`.

### 9. Партнерские каналы распространения нужно продумать отдельно
Решение:
- партнерская дистрибуция выделяется в отдельный контур;
- там же рассматриваются ценообразование, кастомные сборки, юридическая модель, SLA, ownership support/billing.

Хранение:
- `docs/compliance/partner-distribution-model.md`,
- при необходимости будущий ADR.

### 10. Минимум разрешений и минимум ресурсов
Решение:
- минимизация permissions, battery, memory, storage и network — обязательный проектный принцип;
- каждый `plan.md` обязан иметь resource budget section.

Хранение:
- конституция,
- `docs/compliance/permissions-and-resource-budget.md`.

### 11. Поддержка, ошибки, отзывы и обратная связь должны собираться централизованно
Решение:
- это не core feature subsystem, а operational contour проекта;
- released features должны иметь support/error/feedback ownership;
- нужна централизованная схема intake, triage, review handling и feedback loop;
- важно учитывать AI/MCP workflow, но не зашивать его внутрь core без необходимости.

Хранение:
- конституция,
- `docs/operations/support-and-feedback-ops.md`.

### 12. Плитки и конфигурация UX пока не must-have; must сейчас — messenger calls/video
Решение:
- это фиксируется как product priority, а не как constitution-level rule;
- текущий must — research + future spec по calls/video via messengers;
- tile layout / detailed UX пока могут ориентироваться на лидеров рынка, но не должны раздувать Core раньше времени.

Хранение:
- `docs/product/feature-priorities.md`,
- будущий feature spec.

### 13. Parity disclosure for WhatsApp tile return feature (002-whatsapp-tile-return)
Решение:
- текущая реализация поддерживает только Android handoff/return;
- iPhone не реализован в этом релизе и не должен описываться как доступный;
- parity обещание относится к launcher-owned product semantics (confirmation, warning, return continuity), а не к идентичной платформенной механике.

Хранение:
- `specs/002-whatsapp-tile-return/spec.md`,
- `specs/002-whatsapp-tile-return/plan.md`,
- release notes соответствующего релиза.

## Что дальше должен делать ИИ в проекте

Когда появляется новая задача, ИИ должен:
1. прочитать конституцию;
2. прочитать `docs/governance/document-map.md`;
3. прочитать этот файл;
4. проверить релевантные ADR;
5. проверить релевантные compliance/regulatory docs;
6. только после этого формировать `spec.md` и `plan.md`.
