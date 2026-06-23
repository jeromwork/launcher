---
id: TASK-26
title: iOS Admin Preset
status: Planned
assignee: []
created_date: '2026-06-23 05:39'
labels:
  - phase-4
  - v-spec
  - v-1
  - ios
  - platform-expansion
milestone: m-3
dependencies:
  - TASK-3
  - TASK-8
priority: medium
ordinal: 25000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
iOS-specific implementations поверх KMP foundation. Compose Multiplatform iosMain. iOS-specific OAuth (Apple Sign-In вдобавок к Google). Deep links, share intents. App Store submission flow. Effort: Very large (~3-4 months).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Compose Multiplatform iosMain builds
- [ ] #2 Apple Sign-In adapter в AuthProvider
- [ ] #3 iOS deep links + share intents
- [ ] #4 App Store submission готов
<!-- AC:END -->
