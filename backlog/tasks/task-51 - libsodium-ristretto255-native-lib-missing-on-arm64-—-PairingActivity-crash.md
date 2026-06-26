---
id: TASK-51
title: libsodium ristretto255 native lib missing on arm64 — PairingActivity crash
status: Draft
assignee: []
created_date: '2026-06-25 11:48'
labels:
  - crypto
  - bug
milestone: m-1
dependencies:
  - TASK-2
priority: high
ordinal: 51000
---

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] PairingActivity opens on Xiaomi 11T (arm64) without UnsatisfiedLinkError "crypto_core_ristretto255_add"
- [ ] #2 [hand] libsodium native .so for arm64 includes ristretto255 symbols (verify via `nm`)
<!-- AC:END -->
