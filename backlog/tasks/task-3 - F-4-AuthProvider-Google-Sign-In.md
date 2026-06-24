---
id: TASK-3
title: AuthProvider + Google Sign-In
status: Paused
assignee: []
created_date: '2026-06-23 05:00'
updated_date: '2026-06-24 13:30'
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
- [x] #1 AuthProvider port в core/auth/, Google Sign-In adapter в android/
- [ ] #2 Sign-In триггерится в wizard screen 2 / через SignInTrigger composable (не при запуске) — формулировка устарела после clarify (см. Pause Reason)
- [x] #3 Identity isolation: per-UID Keystore namespacing (Spec017AuthIsolationTest fitness test зелёный)
- [ ] #4 Sign-out preserves Keystore (для recovery) — нет покрывающего теста
<!-- AC:END -->

## Pause Reason
<!-- SECTION:PAUSE_REASON:BEGIN -->
PR #21 merged 2026-06-18. Retroactive sync 2026-06-24 (per CLAUDE.md rule 14) выявил 2 непокрытых AC:
- **AC #2**: формулировка устарела — в `/speckit.clarify` для spec 017 (Q5) владелец зафиксировал, что cloud-feature кнопок не существует; Sign-In триггерится **в wizard screen 2** и через **standalone SignInTrigger composable** (см. `core/src/commonMain/kotlin/com/launcher/ui/auth/SignInTrigger.kt`). Старая формулировка («при первом cloud action») должна быть переписана и потом отмечена `[x]`.
- **AC #4**: sign-out preserves Keystore — нет специфического теста; нужен либо unit-test на `GoogleSignInAuthAdapter.signOut()` либо instrumentation подтверждение что после sign-out + sign-in под тем же UID encrypted keys восстанавливаются.

Снять Paused → отметить оба AC, повторно вызвать `pre-pr-backlog-sync` (или прямо отредактировать файл, если изменения тривиальные).
<!-- SECTION:PAUSE_REASON:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Merged PR #21 (2026-06-18). Identity foundation. **Paused** retroactively (см. Pause Reason).
<!-- SECTION:FINAL_SUMMARY:END -->
