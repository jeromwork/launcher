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
- [x] #4 [hand] Backward-compat read test для envelope (spec 018 fixture) — реализован в EnvelopeBackwardCompatTest.kt
<!-- AC:END -->

## Verification Pending
<!-- SECTION:VERIFICATION_PENDING:BEGIN -->
Все `[hand]` AC успешно верифицированы.
- **AC #4**: Реализован `EnvelopeBackwardCompatTest.kt` в `core/keys/src/jvmTest/kotlin/family/keys/` с зафиксированным v1 JSON fixture. Тест подтверждает стабильность wire format'а и корректную десериализацию/расшифровку.
<!-- SECTION:VERIFICATION_PENDING:END -->
