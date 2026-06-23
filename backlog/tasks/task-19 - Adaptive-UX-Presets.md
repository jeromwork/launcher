---
id: TASK-19
title: Adaptive UX Presets
status: Planned
assignee: []
created_date: '2026-06-23 05:38'
labels:
  - phase-3
  - p-spec
  - p-4
  - accessibility
  - adaptive
  - senior-friendly
milestone: m-2
dependencies:
  - TASK-16
  - TASK-17
priority: medium
ordinal: 18000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
5 bundled adaptive presets для разных групп пользователей с ограниченными возможностями: tremor-mild (72dp targets), tremor-severe (long-press 1s), perception-impaired (dwell-to-activate), vision-impaired (контраст+TTS). Runtime AdaptiveTouchBehavior. Effort: ~2 weeks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 5 bundled adaptive presets (default, tremor-mild, tremor-severe, perception-impaired, vision-impaired)
- [ ] #2 AdaptiveTouchBehavior (debounce/long-press/dwell)
- [ ] #3 Accessibility audit per preset
- [ ] #4 Admin может выбрать в P-3 authoring UI
<!-- AC:END -->
