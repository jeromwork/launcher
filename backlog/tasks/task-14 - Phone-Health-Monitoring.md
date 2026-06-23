---
id: TASK-14
title: Phone Health Monitoring
status: Planned
assignee: []
created_date: '2026-06-23 05:37'
labels:
  - phase-2
  - s-spec
  - s-9
  - monitoring
  - senior-care
milestone: m-1
dependencies:
  - TASK-8
priority: medium
ordinal: 13000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Phase 2 шаг 8 (added 2026-06-15 v3). Admin видит health-сигналы Managed устройства: last seen, battery level, app usage анонимный, network status. Privacy-respecting (aggregated, не keystroke-level). Severity-based push: low battery → push, normal → in-app indicator.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 HealthSnapshot wire format (battery, last_seen, network, storage)
- [ ] #2 Уплоад snapshot 1x/4h через WorkManager
- [ ] #3 Admin dashboard view с health per Managed
- [ ] #4 Severity rules: critical (battery <5%) → push; normal → in-app indicator
- [ ] #5 Privacy: НИКАКИХ keystrokes, NO location tracking, NO call logs
<!-- AC:END -->
