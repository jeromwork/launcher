---
id: TASK-5
title: FCM config-updated push trigger
status: Done
assignee: []
created_date: '2026-06-23 05:01'
updated_date: '2026-06-23 05:35'
labels:
  - phase-1
  - F-feature
  - push
  - cloud
  - f-5c
milestone: m-0
dependencies:
  - TASK-4
references:
  - specs/019-f5c-fcm-config-updated/
priority: high
ordinal: 5000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Generic push foundation; первый use case = config-updated notification от Worker'а к устройству owner'а.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 FCM token registered после Sign-In
- [ ] #2 Push payload содержит configId + senderUid
- [ ] #3 ADB broadcast приёмка работает (debug-only)
- [ ] #4 End-to-end push на Xiaomi 11T против launcher-old-dev
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Working 2026-06-22 на Xiaomi 11T. Generic push foundation для будущих cloud-фич.
<!-- SECTION:FINAL_SUMMARY:END -->
