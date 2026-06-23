---
id: TASK-21
title: Account Recovery + 2FA escrow
status: Planned
assignee: []
created_date: '2026-06-23 05:38'
labels:
  - phase-3
  - p-spec
  - p-6
  - recovery
  - 2fa
  - security
milestone: m-2
dependencies:
  - TASK-3
  - TASK-6
  - TASK-12
priority: high
ordinal: 20000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Расширяет baseline recovery (F-5 Phase 1): добавляет pair-key recovery через 2FA escrow в Firestore. При pairing короткий код отсылается на старое связанное устройство, юзер вводит на новом → pair восстанавливается без физической встречи. Social recovery (друг помогает) — НЕ здесь (deprecated D-25 OWD-4). Effort: ~2 weeks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 2FA escrow document в Firestore (encrypted)
- [ ] #2 Recovery wizard в new-device flow
- [ ] #3 Short code TTL 10 минут
- [ ] #4 Cooldown after failed attempts
<!-- AC:END -->
