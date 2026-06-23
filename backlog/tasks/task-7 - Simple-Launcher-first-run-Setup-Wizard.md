---
id: TASK-7
title: Simple Launcher first-run + Setup Wizard
status: Planned
assignee: []
created_date: '2026-06-23 05:36'
updated_date: '2026-06-23 05:46'
labels:
  - phase-2
  - s-spec
  - s-1
  - ui
  - wizard
milestone: m-1
dependencies:
  - TASK-1
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Phase 2 шаг 1. LOCAL mode (без Google Sign-In в первой версии). Wizard ведёт через язык → permissions (ROLE_HOME, POST_NOTIFICATIONS) → theme → grid preset → bundled ConfigTemplate (3 starter JSON через BundledConfigSource). Расширение спеки 010 setup-assistant через Wizard Module из F-3. После wizard'а никогда нет empty top-level screen. Effort: ~3 weeks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Wizard 5 mandatory steps + autohints
- [ ] #2 3 bundled ConfigTemplate (6tiles-classic, 9tiles-with-calendar, 12tiles-dense)
- [ ] #3 Home screen рендерит из /config/current (не mock)
- [ ] #4 Skip-with-banner pattern для пропущенных steps
- [ ] #5 Senior-safe walkthrough на эмуляторе через android-emulator skill
<!-- AC:END -->
