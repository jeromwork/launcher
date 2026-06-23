---
id: TASK-13
title: VersionedConfigViewer + Layout Editor
status: Planned
assignee: []
created_date: '2026-06-23 05:37'
labels:
  - phase-2
  - s-spec
  - s-8
  - admin
  - editor
milestone: m-1
dependencies:
  - TASK-8
priority: high
ordinal: 12000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Phase 2 шаг 5. Admin tool — visual config editor с version history. Admin меняет layout/contacts → push к Managed через F-5c → Managed применяет. История версий с rollback (last 10 версий per spec, client-side housekeeping per CLAUDE.md rule 8). Effort: ~3 weeks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Layout editor: drag-and-drop tiles на 2×3/3×4/4×5 grid
- [ ] #2 Contact list editor: add/remove/reorder + photo upload (intersection с S-5)
- [ ] #3 Version history viewer: last 10 versions, diff между ними
- [ ] #4 Rollback button: восстанавливает выбранную version
- [ ] #5 Push update через F-5c после save
<!-- AC:END -->
