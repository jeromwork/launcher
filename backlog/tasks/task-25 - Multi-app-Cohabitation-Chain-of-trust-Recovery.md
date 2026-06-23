---
id: TASK-25
title: Multi-app Cohabitation + Chain-of-trust Recovery
status: Planned
assignee: []
created_date: '2026-06-23 05:39'
labels:
  - phase-3
  - p-spec
  - p-10
  - multi-app
  - crypto
  - recovery
  - one-way-door
milestone: m-2
dependencies:
  - TASK-2
  - TASK-3
  - TASK-6
  - TASK-21
priority: high
ordinal: 24000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Когда у семьи 3 приложения (launcher + messenger + photo album) на одном телефоне — один клик восстанавливает доступ ко всем same-family app. Chain-of-trust: launcher подтверждает messenger подтверждает album. Выбор варианта: B (ContentProvider+custom permission) / C (Server-mediated) / B+C гибрид. Решается перед спекой. Effort: ~2-3 weeks. Research notes: docs/product/future/multi-app-cohabitation.md.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Технический вариант выбран и обоснован в ADR
- [ ] #2 ChainOfTrustVerifier port в core/crypto/
- [ ] #3 Android adapter (ContentProvider) + iOS adapter (App Groups + shared Keychain)
- [ ] #4 Standalone-install fallback (messenger без launcher работает независимо)
- [ ] #5 Reverse trust + trust revocation
<!-- AC:END -->
