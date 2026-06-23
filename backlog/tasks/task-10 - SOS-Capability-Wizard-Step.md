---
id: TASK-10
title: SOS Capability + Wizard Step
status: Planned
assignee: []
created_date: '2026-06-23 05:36'
labels:
  - phase-2
  - s-spec
  - s-4
  - sos
  - safety
milestone: m-1
dependencies:
  - TASK-7
  - TASK-8
priority: high
ordinal: 9000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Phase 2 шаг 3. SOS button capability — large persistent button на home screen, при нажатии notifies всех paired admins через FCM push (F-5c). Включает confirm UI (anti-misclick), location attach, app update deferral (delays SOS during in-progress update). Effort: ~2 weeks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 SOS button capability в Wizard preset
- [ ] #2 Push к admins через F-5c (severity: actionable + time-sensitive + relevant)
- [ ] #3 Confirm UI с timeout (anti-misclick для случайных нажатий)
- [ ] #4 Location attach (если permission granted, async с timeout)
- [ ] #5 App update deferral: SOS блокирует update install до confirm
<!-- AC:END -->
