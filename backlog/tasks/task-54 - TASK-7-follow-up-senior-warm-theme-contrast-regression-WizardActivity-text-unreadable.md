---
id: TASK-54
title: >-
  TASK-7 follow-up: senior-warm theme contrast regression (WizardActivity text
  unreadable)
status: Paused
assignee: []
created_date: '2026-06-25 11:48'
updated_date: '2026-06-25 14:50'
labels:
  - a11y
  - senior-safe
  - bug
  - post-mvp
milestone: m-3
dependencies:
  - TASK-7
priority: low
ordinal: 54000
---

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] WizardActivity text on senior-warm Light theme passes 4.5:1 contrast ratio (WCAG AA)
- [ ] #2 [hand] Both heading and description text readable on white background without squinting
- [ ] #3 [auto:checklist] checklists/elderly-friendly.md regenerated and 100% [x]
<!-- AC:END -->

<!-- SECTION:PAUSE_REASON:BEGIN -->
## Pause reason (2026-06-25)

Owner decision: MVP polish happens via JSON configuration, not via code (Constitution Article II §8 added in amendment 1.8). Senior-warm theme tuning is *configuration* — colors that ship in the theme variant. They will be tuned post-MVP through JSON theme overrides or theme-variant additions, not by editing `SeniorWarmTheme.kt` in this branch.

Task moved to Phase 4 (m-3) "post-MVP polish" milestone. Resume conditions:
- MVP base functional blocks all green (TASK-3, TASK-4, TASK-5, TASK-7, TASK-8, TASK-9, TASK-10 minimum).
- Senior-warm color tuning becomes part of a JSON theme-variant authoring pass.

Not blocking TASK-7 merge — visual polish is explicitly deferred per Article II §8.
<!-- SECTION:PAUSE_REASON:END -->
