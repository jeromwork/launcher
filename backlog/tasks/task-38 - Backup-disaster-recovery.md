---
id: TASK-38
title: Backup / disaster recovery
status: Draft
assignee: []
created_date: '2026-06-23 05:43'
updated_date: '2026-06-23 06:35'
labels:
  - phase-5
  - l-spec
  - l-5
  - backup
  - resilience
  - parking-lot
milestone: m-4
dependencies: []
priority: low
ordinal: 38000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Per-family encrypted backup в cold storage + recovery flow при cloud provider outage. Сейчас (Phase 2-3) полагаемся на Cloudflare + Firebase availability. L-5 даёт «план Б» если cloud упал на сутки.

## Зачем

Если Firebase или Cloudflare downtime — пользователи теряют access. Backup + cross-region replication = continuity.

## Состояние

**Parking lot.** Активируется при росте критичности продукта (medical / clinic users).

---

## Готовый промт для `/speckit.specify` (когда придёт время)

```
Реализуй L-5: Backup / disaster recovery.

ЧТО СТРОИМ:
Per-family encrypted backup в cold storage (AWS Glacier / Backblaze B2 cold tier / R2 lifecycle). Recovery flow при cloud outage: switch на cold backup как read-only source. Cross-region replication active configs.

ЗАЧЕМ:
Cloud provider outage не должен блокировать пользователей надолго.

SCOPE ВКЛЮЧАЕТ:
- Backup scheduler (Cloudflare Worker cron daily).
- Encryption preserves end-to-end (server не видит plaintext).
- Restore flow UI.
- Cross-region replication.
- Status page / dashboard.

DEPENDENCIES:
- Все cloud features Phase 2-3 done.

EFFORT: TBD.
```
<!-- SECTION:DESCRIPTION:END -->
