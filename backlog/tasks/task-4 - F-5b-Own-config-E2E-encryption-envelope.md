---
id: TASK-4
title: Own config E2E encryption (envelope)
status: Done
assignee: []
created_date: '2026-06-23 05:01'
updated_date: '2026-06-28 19:00'
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

## Final Summary
<!-- SECTION:FINAL_SUMMARY:BEGIN -->
All 4 [hand] AC verified and closed (2026-06-28 pre-pr-backlog-sync). ConfigCipher2 envelope wire format schemaVersion=1 shipped per spec 018. AC #4 closed by EnvelopeBackwardCompatTest with frozen v1 JSON fixture proving wire-format stability and correct deserialization+decryption. No deferred gates remaining.
<!-- SECTION:FINAL_SUMMARY:END -->
