# ADR-003 — Monetization and Entitlement Model

**Status**: Accepted  
**Date**: 2026-04-01

## Context
Приоритет монетизации — подписка. Но система должна безболезненно переживать:
- разные subscription tiers,
- offers,
- partner bundles,
- one-time purchases,
- future model changes.

## Decision
1. В продукте вводится единый entitlement domain.
2. Внутренний домен НЕ должен напрямую зависеть от терминов конкретного store API.
3. Минимальные доменные сущности:
   - Product,
   - Plan,
   - Offer,
   - Entitlement,
   - Access Scope,
   - Billing State,
   - Renewal State,
   - Grace/Hold/Retry state where applicable.
4. Android/Apple/store-specific billing adapters маппятся в этот домен.
5. UI не должен принимать решения о paid access напрямую из raw store response.
6. Entitlement subsystem обязателен как foundation concern.

## Consequences
Плюсы:
- смена модели не ломает product domain;
- легче поддерживать cross-platform evolution;
- partner packaging встраивается чище.

Минусы:
- выше первоначальная архитектурная стоимость;
- нужен аккуратный mapping слой.

## Non-decisions
Не фиксируются:
- provider backend,
- analytics stack,
- billing SDK composition beyond store requirements.
