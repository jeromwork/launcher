---
id: TASK-27
title: Elderly-Friendly Messenger (Jitsi-based)
status: Planned
assignee: []
created_date: '2026-06-23 05:40'
labels:
  - phase-4
  - v-spec
  - v-2
  - messenger
  - jitsi
  - separate-app
milestone: m-3
dependencies:
  - TASK-3
  - TASK-25
priority: high
ordinal: 26000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Отдельное Android-приложение на Jitsi Meet. SSO с launcher через F-4 AuthProvider. Universal Preset Architecture применяется и к messenger: elderly preset (simplified UX) + adult preset (full). Reuse core/crypto/ для encrypted media. Group call invites. Effort: Very large (~4-6 months).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Standalone Android app (отдельный package)
- [ ] #2 SSO с launcher через F-4 (один Google login)
- [ ] #3 Elderly + Adult presets
- [ ] #4 Group call invites
- [ ] #5 Encrypted media через core/crypto/
- [ ] #6 Cohabitation с launcher через P-10 chain-of-trust
<!-- AC:END -->
