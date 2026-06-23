---
id: TASK-8
title: Admin App + QR Pairing
status: Planned
assignee: []
created_date: '2026-06-23 05:36'
labels:
  - phase-2
  - s-spec
  - s-2
  - cloud
  - admin
  - pairing
milestone: m-1
dependencies:
  - TASK-3
  - TASK-6
  - TASK-7
priority: high
ordinal: 7000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Phase 2 шаг 6. CLOUD feature. Admin App preset (для родственника-помощника) + QR-based pairing channel (primary per decision 2026-06-15-deferred-cloud/04). Remote invites через adapter (deferred). Admin видит paired Managed-устройства, может редактировать config, мониторить активность. Effort: ~3-4 weeks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 AdminAppWizardManifest (отдельный preset)
- [ ] #2 QR pairing: Admin scan → Managed accept → handshake через Curve25519
- [ ] #3 Pairing key стored в KeyRegistry per identity
- [ ] #4 Admin dashboard: список paired Managed + статус последнего sync
- [ ] #5 Sign-out preserves pairing (для recovery)
<!-- AC:END -->
