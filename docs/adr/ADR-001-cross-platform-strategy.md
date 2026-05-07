# ADR-001 — Cross-Platform Strategy

**Status**: Accepted; **partially superseded by [ADR-005](ADR-005-ui-stack-compose-multiplatform.md)** (2026-05-07).
**Date**: 2026-04-01

> **Update 2026-05-07.** Decision §2 («кодошеринг не является самоцелью») и §Non-decisions
> (Kotlin Multiplatform, shared UI) **отменены и заменены ADR-005**, который фиксирует
> Compose Multiplatform + Kotlin Multiplatform как обязательный stack для UI и domain слоя.
> Decision §3 (shared product model), §4 (platform-specific integrations изолированы) и §5
> (Platform Parity Gate в каждом `plan.md`) **остаются в силе** и подтверждены в ADR-005.

## Context
Продукт стартует на Android, но владелец проекта хочет избежать архитектуры, которая потом вынудит полностью переписывать значительную часть логики для iOS.

## Decision
1. Android — primary launch platform.
2. ~~Кодошеринг не является самоцелью.~~ **Отменено ADR-005:** код-шеринг через KMP/CMP теперь обязателен для UI и domain слоя.
3. Обязателен ~~не shared code, а~~ shared product model: domain semantics, configuration semantics, entitlements, localization, partner/distribution concepts. **(ADR-005 расширил это до shared code в `commonMain`.)**
4. Platform-specific integrations должны быть изолированы. **(Reaffirmed в ADR-005 §3.)**
5. Каждый `plan.md` обязан содержать Platform Parity Gate. **(Reaffirmed в ADR-005; добавлены Cross-Platform Implementation Gate и Documented Platform Asymmetry.)**

## Consequences
Плюсы:
- снижается риск поздней архитектурной блокировки iOS;
- легче объяснять parity gap;
- проще строить partner/distribution variants.

Минусы:
- нельзя бездумно использовать Android-specific assumptions в domain слоях;
- появляется дополнительная дисциплина документации.

## Non-decisions
~~Этот ADR не фиксирует:~~
~~- Kotlin Multiplatform,~~
~~- shared UI,~~
~~- shared code percentage,~~
~~- конкретную iOS implementation strategy.~~

**Все четыре пункта зафиксированы в [ADR-005](ADR-005-ui-stack-compose-multiplatform.md):** Kotlin Multiplatform для domain, Compose Multiplatform + Material 3 для UI; конкретная iOS implementation strategy остаётся открытым решением до spec'а, инициирующего iOS-разработку.
