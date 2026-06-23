---
id: TASK-20
title: Config Copy Between Own Devices
status: Planned
assignee: []
created_date: '2026-06-23 05:38'
labels:
  - phase-3
  - p-spec
  - p-5
  - multi-device
  - config-management
milestone: m-2
dependencies:
  - TASK-16
priority: medium
ordinal: 19000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Admin клонирует config между своими устройствами (phone → TV → kitchen-TV) с правкой. Cross-platform copy: копируется только platformAgnostic секция, platformSpecific.android НЕ копируется в platformSpecific.androidTv. Effort: ~1 week.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Clone operation + UI (выбрать source, имя нового)
- [ ] #2 Cross-platform handling: platformAgnostic копируется, platformSpecific остаётся пустым
- [ ] #3 Apply to device через push в namespace
- [ ] #4 Tests: phone-to-phone, phone-to-TV
<!-- AC:END -->
