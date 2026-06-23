---
id: TASK-30
title: Wearable Health Monitoring
status: Planned
assignee: []
created_date: '2026-06-23 05:40'
labels:
  - phase-4
  - v-spec
  - v-5
  - wearable
  - bluetooth
  - health
milestone: m-3
dependencies:
  - TASK-14
priority: medium
ordinal: 29000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
BLE pairing с smart watches через BluetoothPairingChannel. Heart rate, steps, fall detection. Alert escalation. Privacy boundaries. Effort: Large (~3 months).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 BluetoothPairingChannel adapter
- [ ] #2 Heart rate + steps + fall detection
- [ ] #3 Alert escalation (fall detected → SOS path)
- [ ] #4 Privacy: aggregated data, no continuous stream к admin'у
<!-- AC:END -->
