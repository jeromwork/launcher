---
id: TASK-3
title: AuthProvider + Google Sign-In
status: Verification
assignee: []
created_date: '2026-06-23 05:00'
updated_date: '2026-06-24 14:40'
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
- [ ] #2 [hand] Переписать формулировку: Sign-In триггерится в wizard screen 2 / через standalone SignInTrigger composable (старое «при первом cloud action» устарело после /speckit.clarify Q5 spec 017)
- [x] #3 [hand] Identity isolation: per-UID Keystore namespacing (Spec017AuthIsolationTest fitness test зелёный)
- [ ] #4 [hand] Sign-out preserves Keystore для recovery (unit-test для GoogleSignInAuthAdapter.signOut() ИЛИ instrumentation: sign-out + sign-in под тем же UID → encrypted keys восстанавливаются)
<!-- AC:END -->

## Verification Pending
<!-- SECTION:VERIFICATION_PENDING:BEGIN -->
PR #21 merged 2026-06-18. Retroactive sync 2026-06-24 (per CLAUDE.md rule 14 + B+3 hybrid AC model) выявил 2 непокрытых `[hand]` AC:
- **AC #2**: формулировка устарела — `/speckit.clarify` для spec 017 (Q5) зафиксировал, что cloud-feature кнопок не существует; Sign-In триггерится в wizard screen 2 и через standalone `SignInTrigger` composable (см. `core/src/commonMain/kotlin/com/launcher/ui/auth/SignInTrigger.kt`). Переписать AC, потом проставить `[x]`.
- **AC #4**: sign-out preserves Keystore — нет специфического теста. Добавить тест → проставить `[x]`.

Spec 017 не имеет `checklists/` папки (создавался до того как procedure-assess-spec-complexity запускался автоматически) → `[auto:checklist]` AC отсутствуют. spec 017 tasks.md не использует `[deferred-*]` маркеры → `[auto:deferred-*]` AC отсутствуют. Если при ручном ревью обнаружатся deferred гейты — добавить через повторный `pre-pr-backlog-sync`.
<!-- SECTION:VERIFICATION_PENDING:END -->

