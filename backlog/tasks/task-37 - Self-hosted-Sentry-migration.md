---
id: TASK-37
title: Self-hosted Sentry migration
status: Planned
assignee: []
created_date: '2026-06-23 05:43'
updated_date: '2026-06-23 06:34'
labels:
  - phase-5
  - l-spec
  - l-4
  - infra
  - server-roadmap
  - parking-lot
milestone: m-4
dependencies: []
priority: low
ordinal: 35000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Миграция crash reporting с Android Vitals (бесплатно, через Google Play) на собственный self-hosted Sentry. Триггер: если перейдём на non-Play distribution (например, F-Droid, прямой APK) или если GDPR станет строже.

## Зачем

Android Vitals привязан к Google Play. Если мы захотим distribute вне Play Store — нужен альтернативный crash reporter. Self-hosted Sentry даёт control над данными.

## Состояние

**Parking lot.** Активируется при non-Play distribution или GDPR ужесточении.

---

## Готовый промт для `/speckit.specify` (когда придёт время)

```
Реализуй L-4: Self-hosted Sentry migration.

ЧТО СТРОИМ:
CrashReporter port (already существует через rule 1) получает SentryAdapter. Сервер: self-hosted Sentry instance (Docker / k8s). Миграция: switch на Sentry adapter via DI при сборке non-Play APK.

ЗАЧЕМ:
Non-Play distribution OR GDPR ужесточение требует альтернативу Android Vitals.

SCOPE ВКЛЮЧАЕТ:
- SentryAdapter implementation.
- Self-hosted Sentry deployment guide (Docker compose).
- DI configuration для switch.
- PII sanitization (CLAUDE.md fitness function).

DEPENDENCIES:
- CrashReporter port (existing).

EFFORT: TBD.
```
<!-- SECTION:DESCRIPTION:END -->
