---
id: TASK-9
title: Contact Tiles + Handoff Calling
status: Planned
assignee: []
created_date: '2026-06-23 05:36'
labels:
  - phase-2
  - s-spec
  - s-3
  - contacts
  - call
milestone: m-1
dependencies:
  - TASK-7
  - TASK-8
priority: high
ordinal: 8000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Phase 2 шаг 2. Contact tiles в Simple Launcher с handoff calling (передача вызова на родственника при недозвоне). Contacts управляются Admin'ом через S-2, рендерятся в Simple Launcher tiles. Handoff = WhatsApp/Phone fallback chain. Effort: ~2-3 weeks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ContactTile рендерит контакт из config (photo placeholder, name, action chain)
- [ ] #2 Tap → primary action (call WhatsApp / Phone) с fallback chain
- [ ] #3 Handoff: при недозвоне в N секунд → автоматически следующий контакт в группе
- [ ] #4 Admin может редактировать contact list через S-2 admin UI
- [ ] #5 Contact data — encrypted в config через F-5b envelope
<!-- AC:END -->
