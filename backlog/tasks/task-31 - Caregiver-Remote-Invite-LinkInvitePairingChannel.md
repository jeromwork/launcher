---
id: TASK-31
title: Caregiver Remote Invite + LinkInvitePairingChannel
status: Planned
assignee: []
created_date: '2026-06-23 05:40'
labels:
  - phase-4
  - v-spec
  - v-6
  - caregiver
  - role-based-access
  - b2b-foundation
milestone: m-3
dependencies:
  - TASK-8
  - TASK-21
priority: high
ordinal: 30000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Caregiver = сиделка/соцработник, admin даёт ограниченный доступ через signed invite link. Caregiver видит SOS, может позвонить, НО не видит family album. TTL membership. Foundation для L-1 (clinic B2B). Effort: Large (~3-4 months — с учётом всей инфры).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 LinkInvitePairingChannel adapter (signed invite link)
- [ ] #2 Role-based access на сервере + envelope filtering на клиенте
- [ ] #3 TTL membership с auto-expiry
- [ ] #4 Caregiver UI: SOS feed + call action, без album
- [ ] #5 Audit log integration (через V-7)
<!-- AC:END -->
