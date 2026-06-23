---
id: TASK-18
title: Preset Authoring + Sharing
status: Planned
assignee: []
created_date: '2026-06-23 05:38'
labels:
  - phase-3
  - p-spec
  - p-3
  - authoring
  - sharing
  - config-source
milestone: m-2
dependencies:
  - TASK-16
  - TASK-17
priority: high
ordinal: 17000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
UI для admin'а: создание/экспорт/импорт preset'ов. ConfigSource adapters: BundledConfigSource (из F-3) + ImportFromFileConfigSource + ShareIntentConfigSource. Маркетплейс (curated catalog) НЕ здесь — отдельная L-2. Переписать spec 014 на v2 schema. Effort: ~2-3 weeks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Authoring UI: создать preset на основе существующего
- [ ] #2 Export preset как .json file + share через Android share intent
- [ ] #3 Import .json или принять share intent
- [ ] #4 5 named configs limit per cloud namespace
- [ ] #5 ImportFromFileConfigSource + ShareIntentConfigSource adapters
<!-- AC:END -->
