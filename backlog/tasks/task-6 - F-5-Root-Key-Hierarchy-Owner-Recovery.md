---
id: TASK-6
title: Root Key Hierarchy + Owner Recovery
status: In Progress
assignee: []
created_date: '2026-06-23 05:01'
updated_date: '2026-06-23 05:35'
labels:
  - phase-1
  - F-feature
  - crypto
  - recovery
  - one-way-door
  - f-5
milestone: m-0
dependencies:
  - TASK-2
  - TASK-3
  - TASK-4
  - TASK-5
references:
  - specs/020-f5-root-key-hierarchy-recovery/
priority: high
ordinal: 6000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Root key из owner identity → KeyRegistry → per-spec subkeys. Recovery через Google Sign-In + passphrase + Firestore-encrypted vault + Android Autofill.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 KeyRegistry port + impl с per-identity namespacing
- [ ] #2 ConfigCipher2 мигрирован на KeyRegistry (byte-equal preserved)
- [ ] #3 Identity isolation cascade wipe
- [ ] #4 Recovery Compose screens (Setup / Entry / Fallback) + Autofill
- [ ] #5 Round-trip миграция spec 018 ciphertext на Xiaomi 11T
- [ ] #6 NoOpRecoveryKeyVault для non-GMS (Huawei) + DI detection
- [ ] #7 Documentation: recovery-flow.md + key-hierarchy.md на простом русском
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Spec 020 написана 2026-06-22, в работе на ветке 020-f5-root-key-hierarchy-recovery. Закрывает Phase 1.
<!-- SECTION:NOTES:END -->
