---
id: TASK-55
title: >-
  TASK-7 follow-up: PairAdmin Custom step needs confirm UI (auto-launch is
  wrong)
status: Draft
assignee: []
created_date: '2026-06-25 11:48'
labels:
  - wizard
  - phase-5
  - arch
milestone: m-1
dependencies:
  - TASK-7
priority: medium
ordinal: 55000
---

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] Custom step host renders confirmation screen ("Соединить с админ-устройством? [Пропустить] [Соединить]") before calling handler.execute()
- [ ] #2 [hand] If user taps "Пропустить" handler is NEVER called → PairingActivity never starts
- [ ] #3 [hand] PairAdminStepIntegrationTest extended to cover confirm/skip paths
<!-- AC:END -->
