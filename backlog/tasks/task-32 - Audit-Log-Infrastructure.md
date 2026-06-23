---
id: TASK-32
title: Audit Log Infrastructure
status: Planned
assignee: []
created_date: '2026-06-23 05:40'
labels:
  - phase-4
  - v-spec
  - v-7
  - audit
  - transparency
milestone: m-3
dependencies:
  - TASK-31
priority: medium
ordinal: 31000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Кто из родственников/caregiver'ов что делал в семейном пространстве. Снижает тревогу 'кто-то меняет настройки, я не знаю кто'. Tier 1 (public metadata: actor, timestamp, action_type) + Tier 2 (private payload encrypted to actor only). Используется в V-6 и admin app. Effort: Medium (~2 weeks).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Tier 1: public metadata (actor, timestamp, action_type)
- [ ] #2 Tier 2: encrypted private payload
- [ ] #3 Admin UI: фильтрация по actor / time / type
- [ ] #4 Retention: 90 дней по умолчанию (configurable)
<!-- AC:END -->
