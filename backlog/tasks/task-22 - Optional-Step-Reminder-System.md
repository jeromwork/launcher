---
id: TASK-22
title: Optional Step Reminder System
status: Planned
assignee: []
created_date: '2026-06-23 05:38'
labels:
  - phase-3
  - p-spec
  - p-7
  - wizard
  - settings
  - notification-discipline
milestone: m-2
dependencies:
  - TASK-16
  - TASK-17
priority: medium
ordinal: 21000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
В preset (schemaVersion 2) есть optionalSteps — юзер может пропустить, но они напоминают через badge в Settings (НЕ push, per CLAUDE.md rule 10). OptionalStepTracker + badge UI + progress list. Effort: ~1 week.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 OptionalStepTracker отслеживает completion для каждого optional step
- [ ] #2 Badge в Settings показывает 'N не настроенных шагов'
- [ ] #3 Tap на step запускает wizard step
- [ ] #4 НЕТ push reminders (rule 10 compliance)
<!-- AC:END -->
