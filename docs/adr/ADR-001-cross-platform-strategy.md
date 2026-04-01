# ADR-001 — Cross-Platform Strategy

**Status**: Accepted  
**Date**: 2026-04-01

## Context
Продукт стартует на Android, но владелец проекта хочет избежать архитектуры, которая потом вынудит полностью переписывать значительную часть логики для iOS.

## Decision
1. Android — primary launch platform.
2. Кодошеринг не является самоцелью.
3. Обязателен не shared code, а shared product model:
   - domain semantics,
   - configuration semantics,
   - entitlements,
   - localization,
   - partner/distribution concepts.
4. Platform-specific integrations должны быть изолированы.
5. Каждый `plan.md` обязан содержать Platform Parity Gate.

## Consequences
Плюсы:
- снижается риск поздней архитектурной блокировки iOS;
- легче объяснять parity gap;
- проще строить partner/distribution variants.

Минусы:
- нельзя бездумно использовать Android-specific assumptions в domain слоях;
- появляется дополнительная дисциплина документации.

## Non-decisions
Этот ADR не фиксирует:
- Kotlin Multiplatform,
- shared UI,
- shared code percentage,
- конкретную iOS implementation strategy.
