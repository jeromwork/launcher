---
id: TASK-4
title: Own config E2E encryption (envelope)
status: Paused
assignee: []
created_date: '2026-06-23 05:01'
updated_date: '2026-06-24 13:30'
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
- [x] #1 ConfigCipher2 envelope-формат с schemaVersion=1 (Envelope.kt + WireFormatJsonTest)
- [x] #2 Round-trip byte-equal на physical device #1 (Xiaomi 11T) — CloudConfigEncryptionE2ETest (commits 25bd5b6, dc1001b)
- [x] #3 PublicKeyDirectoryRecipientResolver в core/keys/impl
- [ ] #4 Backward-compat read test для spec 018 fixture (envelope) — есть только RecoveryVaultBackwardCompatTest, envelope-specific теста нет
<!-- AC:END -->

## Pause Reason
<!-- SECTION:PAUSE_REASON:BEGIN -->
PR #22 merged 2026-06-23. Retroactive sync 2026-06-24 (per CLAUDE.md rule 14) выявил 1 непокрытый AC:
- **AC #4**: backward-compat read test для **envelope wire-format** (spec 018) отсутствует. `core/keys/src/commonTest/.../RecoveryVaultBackwardCompatTest.kt` покрывает recovery-vault формат, но не envelope. Per CLAUDE.md rule 5 (wire-format versioning) — это обязательный тест для любого формата, который пересекает device boundary. Нужно: создать fixture v1 envelope blob → decode test → assert decryption успешен на текущем коде.

Снять Paused → добавить `EnvelopeBackwardCompatTest`, отметить `[x]`, повторно вызвать `pre-pr-backlog-sync`.
<!-- SECTION:PAUSE_REASON:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Реализован 2026-06-20. 68 JVM-тестов зелёных. Round-trip OK на устройстве. **Paused** retroactively (см. Pause Reason).
<!-- SECTION:FINAL_SUMMARY:END -->
