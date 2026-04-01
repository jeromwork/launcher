# ADR-002 — Licensing and Anti-Abuse

**Status**: Accepted  
**Date**: 2026-04-01

## Context
Проекту нужна ранняя защита монетизации и базовая защита от копирования/злоупотреблений. Предлагалась идея жесткой привязки к уникальному ID устройства или Google Play.

## Decision
1. Проект не фиксируется на одном device identifier как на единственной опоре лицензирования.
2. Базовый принцип — entitlement-first.
3. Anti-abuse может включать комбинацию:
   - store purchase verification,
   - server-side entitlements,
   - secure local cache,
   - device binding,
   - integrity / attestation signals,
   - abuse monitoring.
4. Device ID и platform-scoped identifiers рассматриваются как вспомогательный слой, а не как единственный источник истины.
5. Любой выбранный механизм должен иметь:
   - privacy justification,
   - fallback strategy,
   - offline behavior,
   - store-review survivability.

## Consequences
Плюсы:
- меньше риск привязки к нестабильному идентификатору;
- выше переносимость между Android/iOS;
- лучше переживаются store/policy/privacy изменения.

Минусы:
- сложнее архитектурно;
- может потребоваться backend раньше.

## Non-decisions
Не фиксируется конкретный провайдер сервера, attestation API или схема кеширования.
