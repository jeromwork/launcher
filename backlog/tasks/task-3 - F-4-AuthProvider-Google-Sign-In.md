---
id: TASK-3
title: AuthProvider + Google Sign-In
status: Done
assignee: []
created_date: '2026-06-23 05:00'
updated_date: '2026-06-23 05:35'
labels:
  - phase-1
  - F-feature
  - auth
  - identity
  - f-4
milestone: m-0
dependencies:
  - TASK-2
references:
  - specs/017-f4-auth-provider/
priority: high
ordinal: 3000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
AuthProvider port + Google Sign-In adapter. Anonymous Firebase Auth удалён. Каждое устройство = свой Google UID.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 AuthProvider port в core/auth/, Google Sign-In adapter в android/
- [ ] #2 Sign-In происходит при первом cloud action (не при запуске)
- [ ] #3 Identity isolation: per-UID Keystore namespacing
- [ ] #4 Sign-out preserves Keystore (для recovery)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Merged PR #21 (2026-06-18). Identity foundation.
<!-- SECTION:FINAL_SUMMARY:END -->
