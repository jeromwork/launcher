---
id: TASK-17
title: Android Deep Integration Steps
status: Planned
assignee: []
created_date: '2026-06-23 05:38'
labels:
  - phase-3
  - p-spec
  - p-2
  - android
  - deep-integration
  - accessibility
milestone: m-2
dependencies:
  - TASK-16
priority: high
ordinal: 16000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Reusable Android-specific wizard steps для полноценного 'безопасного пространства пожилого': BlockNotificationDrawerStep, DisableHorizontalSwipeStep, HideSettingsBehind7TapStep, DisableLockscreenWidgetsStep, RestrictAppListVisibilityStep. Каждый = WizardStep implementation через Android system intent или Accessibility Service. Effort: ~3 weeks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 5+ reusable Android Deep Integration steps реализованы
- [ ] #2 Permissions wizard + Settings deep-links
- [ ] #3 State reconciliation после OS update
- [ ] #4 OEM-specific quirks тесты (Samsung, Xiaomi MIUI)
<!-- AC:END -->
