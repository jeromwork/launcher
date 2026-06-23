---
id: TASK-2
title: 'F-CRYPTO: Core crypto module foundation'
status: Done
assignee: []
created_date: '2026-06-23 05:00'
labels:
  - phase-1
  - F-feature
  - crypto
  - one-way-door
milestone: m-0
dependencies: []
references:
  - specs/016-f-crypto-core-module/
priority: high
ordinal: 2000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
core/crypto/ KMP module (Curve25519, Ed25519, HKDF, AEAD) на ionspin libsodium-kmp. Validation = RFC KAT + Wycheproof + property + industrial reference.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 AeadCipher port + RFC KAT тесты зелёные
- [ ] #2 Curve25519 wrap pattern для TEE задокументирован
- [ ] #3 Library живёт в launcher-репо до 2-го потребителя
- [ ] #4 iOS targets day 1 (компиляция)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Реализован spec 016 (2026-06-17). Foundation для F-5, S-2, V-2.
<!-- SECTION:FINAL_SUMMARY:END -->
