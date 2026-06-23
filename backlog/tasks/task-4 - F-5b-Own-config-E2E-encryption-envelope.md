---
id: TASK-4
title: Own config E2E encryption (envelope)
status: Done
assignee: []
created_date: '2026-06-23 05:01'
updated_date: '2026-06-23 05:35'
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
- [ ] #1 ConfigCipher2 envelope-формат с schemaVersion=1
- [ ] #2 Round-trip byte-equal на Xiaomi 11T
- [ ] #3 PublicKeyDirectoryRecipientResolver в core/keys/impl
- [ ] #4 Backward-compat read test для spec 018 fixture
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Реализован 2026-06-20. 68 JVM-тестов зелёных. Round-trip OK на устройстве.
<!-- SECTION:FINAL_SUMMARY:END -->
