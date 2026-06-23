---
id: TASK-23
title: Provider Recipe Catalogue
status: Planned
assignee: []
created_date: '2026-06-23 05:39'
labels:
  - phase-3
  - p-spec
  - p-8
  - server
  - recipes
  - deep-link
  - privacy
milestone: m-2
dependencies:
  - TASK-16
priority: high
ordinal: 22000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Серверный публичный каталог launch recipes — 'как открыть конкретный app с параметрами через deep-link' для региональных apps (Uber/Bolt/Yandex Taxi/etc). Расширяет 8 встроенных провайдеров spec 005 до сотен. Privacy: pull всего региона, никакой telemetry. В config лежит только {recipeId, parameters}, не копия recipe (recipe протухает). Effort: ~2-3 weeks клиент + отдельная серверная спека.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Recipe wire format schemaVersion=1 с roundtrip+backcompat tests
- [ ] #2 Cloudflare Worker endpoint GET /recipes?region=&since=
- [ ] #3 Локальный кэш на admin+senior с TTL 1 день
- [ ] #4 Resolver в момент тапа: recipeId → cache → deepLinkTemplate с parameters
- [ ] #5 Admin UI: пересечение recipe-каталог × installed apps
- [ ] #6 Privacy: НЕТ telemetry о тапах, НЕТ server-side фильтрации
<!-- AC:END -->
