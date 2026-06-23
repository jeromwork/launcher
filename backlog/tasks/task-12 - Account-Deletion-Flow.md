---
id: TASK-12
title: Account Deletion Flow
status: Planned
assignee: []
created_date: '2026-06-23 05:37'
labels:
  - phase-2
  - s-spec
  - s-6
  - gdpr
  - deletion
  - play-store
milestone: m-1
dependencies:
  - TASK-3
  - TASK-4
priority: high
ordinal: 11000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Phase 2 шаг 7. GDPR + Play Store mandatory. Pre-public-release blocker. 30-day grace period (configurable per region post-MVP). Удаляет identity, конфиги, paired keys, blobs (через storage adapter). Visible в Settings + email confirmation. Effort: ~1-2 weeks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Settings → Delete Account UI с подтверждением через passphrase
- [ ] #2 30-day grace period (с undo-button в email confirmation)
- [ ] #3 После grace: cascade wipe identity + configs + KeyRegistry + blobs
- [ ] #4 Email confirmation с deletion summary
- [ ] #5 Privacy Policy section про deletion published
<!-- AC:END -->
