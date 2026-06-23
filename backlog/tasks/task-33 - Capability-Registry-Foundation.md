---
id: TASK-33
title: Capability Registry Foundation
status: Planned
assignee: []
created_date: '2026-06-23 05:40'
labels:
  - phase-4
  - f-spec
  - f-2
  - capability-registry
  - ai-ready
  - deferred
milestone: m-3
dependencies: []
priority: medium
ordinal: 32000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Moved from Phase 2 в Phase 4 (2026-06-15 v3). Capability Registry без consumer'а — преждевременная абстракция. Активируется когда появится первый AI/MCP/voice integration target (Google Assistant App Actions, MCP server, Gemini Nano). Существующий checklist-capability-registry-readiness skill + docs/dev/capability-registry-pending.md накапливают actions через Phase 2+3+начало Phase 4. F-2 собирает всё → CapabilityRegistry port + ExposureAdapter interface + FakeAdapter. Effort: Medium (~2 weeks).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 CapabilityRegistry port в core/capability/
- [ ] #2 ExposureAdapter interface + FakeAdapter
- [ ] #3 Все накопленные TODO(capability-registry) обработаны
- [ ] #4 ADR-008 'AI affordance posture' написан
<!-- AC:END -->
