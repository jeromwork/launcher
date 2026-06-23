---
id: TASK-16
title: Preset Schema v2 + Wizard Engine
status: Planned
assignee: []
created_date: '2026-06-23 05:38'
labels:
  - phase-3
  - p-spec
  - p-1
  - wire-format
  - schema-bump
milestone: m-2
dependencies:
  - TASK-7
  - TASK-8
  - TASK-9
  - TASK-10
  - TASK-11
  - TASK-12
  - TASK-13
priority: high
ordinal: 15000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Bump preset wire format schemaVersion 1 → 2. Backward-compat: Phase 3 reader умеет читать v1 (lift to v2), Phase 2 reader на v2 получает только platformAgnostic секцию через server-side downgrade. Wizard engine эволюционирует: preset декларирует mandatory + optional steps. Effort: ~2 weeks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ConfigDocumentV2 wire format с roundtrip + cross-version tests
- [ ] #2 Lift v1 → v2 алгоритм с тестом fixture-based
- [ ] #3 WizardManifest schema с mandatory/optional разделением
- [ ] #4 Phase 2 reader downgrade test (читает v2, видит только platformAgnostic)
<!-- AC:END -->
