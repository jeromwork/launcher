# ADR-004 — Localization and Global Readiness

**Status**: Accepted  
**Date**: 2026-04-01

## Context
Продукт планируется к глобальному распространению. Нужна мультиязычность и готовность к региональным различиям без дальнейшей структурной переделки.

## Decision
1. Localization — mandatory foundation capability.
2. Все user-facing strings и configuration-facing labels должны быть вынесены в локализуемые ресурсы/контракты.
3. Региональные различия должны проектироваться через configuration/profile/contracts, а не через разрастание platform-specific forks.
4. Store metadata localization и legal/regional packaging учитываются отдельными compliance docs, а не в core code.
5. Каждый `plan.md`, затрагивающий user-facing content, обязан явно указать localization impact.

## Consequences
Плюсы:
- легче масштабировать на новые языки и страны;
- меньше риск поздних массовых rewrite.

Минусы:
- дисциплина ресурсной архитектуры нужна с самого начала.
