---
id: TASK-24
title: Device Inventory Sync
status: Planned
assignee: []
created_date: '2026-06-23 05:39'
labels:
  - phase-3
  - p-spec
  - p-9
  - sync
  - privacy
  - inventory
milestone: m-2
dependencies:
  - TASK-13
  - TASK-23
priority: medium
ordinal: 23000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Senior устройство собирает список установленных apps локально через PackageManager, шифрует через envelope encryption (F-5), пушит на сервер. Admin при редактировании читает список и видит, что у бабушки реально установлено. Сервер видит blob, не структуру. Effort: ~1-1.5 weeks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Inventory wire format schemaVersion=1 (soft limit ~500 apps)
- [ ] #2 Broadcast receiver на PACKAGE_ADDED/REMOVED + sanity refresh 1x/day
- [ ] #3 Envelope encryption тем же ключом что и config
- [ ] #4 Admin UI: пересечение recipe-каталог × inventory senior'а + warning при obsolete
- [ ] #5 Privacy: НЕТ analytics, НЕТ crash reports с этими данными
<!-- AC:END -->
