---
id: TASK-4
title: Own config E2E encryption (envelope)
status: Verification
assignee: []
created_date: '2026-06-23 05:01'
updated_date: '2026-06-24 14:40'
labels:
  - phase-1
  - F-feature
  - crypto
  - wire-format
  - f-5b
milestone: m-0
dependencies:
  - TASK-2
  - TASK-3
references:
  - specs/018-f5-config-e2e-encryption/
priority: high
ordinal: 4000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
ConfigDocument E2E encryption через hybrid envelope (Curve25519 + AEAD). F-5b variant вместо изначального symmetric F-5.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] ConfigCipher2 envelope-формат с schemaVersion=1 (Envelope.kt + WireFormatJsonTest)
- [x] #2 [hand] Round-trip byte-equal на Xiaomi 11T — CloudConfigEncryptionE2ETest (commits 25bd5b6, dc1001b)
- [x] #3 [hand] PublicKeyDirectoryRecipientResolver в core/keys/impl
- [ ] #4 [hand] Backward-compat read test для envelope (spec 018 fixture) — есть только RecoveryVaultBackwardCompatTest, envelope-specific теста нет; per CLAUDE.md rule 5 обязателен
<!-- AC:END -->

## Verification Pending
<!-- SECTION:VERIFICATION_PENDING:BEGIN -->
PR #22 merged 2026-06-23. Retroactive sync 2026-06-24 (per CLAUDE.md rule 14 + B+3 hybrid AC model) выявил 1 непокрытый `[hand]` AC:
- **AC #4**: создать fixture v1 envelope blob → decode test → assert decryption успешен на текущем коде (например `core/keys/src/commonTest/.../EnvelopeBackwardCompatTest.kt`). Per CLAUDE.md rule 5 это обязательно для любого wire format'а, пересекающего device boundary.

Spec 018 не имеет `checklists/` папки → `[auto:checklist]` AC отсутствуют. spec 018 tasks.md не использует `[deferred-*]` маркеры → `[auto:deferred-*]` AC отсутствуют. Реализация была верифицирована на физическом устройстве (Xiaomi 11T) до того как hybrid модель появилась, поэтому physical-device гейт уже закрыт `[x]` через `[hand]` AC #2.

Снять Verification → добавить EnvelopeBackwardCompatTest, проставить `[x]`, повторно вызвать `pre-pr-backlog-sync` → Done.
<!-- SECTION:VERIFICATION_PENDING:END -->
