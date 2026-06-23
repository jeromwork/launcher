---
id: TASK-15
title: Subscription Server Timer
status: Planned
assignee: []
created_date: '2026-06-23 05:37'
labels:
  - phase-2
  - s-spec
  - s-10
  - billing
  - cloud-only
  - tamper-resistance
milestone: m-1
dependencies:
  - TASK-3
priority: medium
ordinal: 14000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Phase 2 шаг 9 (added 2026-06-15 v3). Subscription entitlement validation на сервере (per decision 2026-06-15-deferred-cloud/03 — cloud-only billing, anti-tamper). Local-only функции навсегда бесплатны; subscription нужна только для cloud features (admin sharing, multi-device sync). Server-validated JWT, не client-flag.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 BillingEntitlement port в core/billing/
- [ ] #2 ServerBillingAdapter: JWT validation против Cloudflare Worker
- [ ] #3 Worker endpoint /billing/check возвращает signed entitlement
- [ ] #4 Cloud features (S-2 admin app, S-5 photos) проверяют entitlement before action
- [ ] #5 Local-only mode остаётся бесплатным навсегда (нет gating)
<!-- AC:END -->
