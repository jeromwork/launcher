---
id: TASK-3
title: AuthProvider + Google Sign-In
status: Done
assignee: []
created_date: '2026-06-23 05:00'
updated_date: '2026-06-28 19:00'
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
- [x] #1 [hand] AuthProvider port в core/auth/ + Google Sign-In adapter в android/ (AuthProvider.kt, GoogleSignInAuthAdapter.kt)
- [x] #2 [hand] Sign-In триггерится в wizard screen 2 и через standalone SignInTrigger composable (per spec 017 clarification Q5) (см. core/src/commonMain/kotlin/com/launcher/ui/auth/SignInTrigger.kt)
- [x] #3 [hand] Identity isolation: per-UID Keystore namespacing (Spec017AuthIsolationTest fitness test зелёный)
- [x] #4 [hand] Sign-out preserves Keystore для recovery (GoogleSignInAuthAdapterSignOutTest проверен)
<!-- AC:END -->

## Final Summary
<!-- SECTION:FINAL_SUMMARY:BEGIN -->
All 4 [hand] AC verified and closed (2026-06-28 pre-pr-backlog-sync). AuthProvider port + GoogleSignInAuthAdapter shipped per spec 017. AC #4 closed by GoogleSignInAuthAdapterSignOutTest (sign-out preserves Keystore for TASK-6 recovery). No deferred gates remaining.
<!-- SECTION:FINAL_SUMMARY:END -->

