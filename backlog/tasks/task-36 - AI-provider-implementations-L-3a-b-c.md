---
id: TASK-36
title: AI provider implementations (L-3a/b/c)
status: Planned
assignee: []
created_date: '2026-06-23 05:43'
updated_date: '2026-06-23 06:34'
labels:
  - phase-5
  - l-spec
  - l-3
  - ai
  - capability-registry
  - parking-lot
milestone: m-4
dependencies:
  - TASK-33
priority: low
ordinal: 34000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Реальные AI-провайдеры, использующие foundation из TASK-33 F-2 (Capability Registry). Три независимых направления:
- **L-3a Google App Actions** — пользователь говорит «OK Google, позвони бабушке» → Google вызывает наше capability.
- **L-3b MCP Server** — AI-агенты (типа Claude Desktop) подключаются к нашему MCP-серверу и выполняют actions.
- **L-3c Gemini Nano** — локальная on-device AI для image description / smart suggestions.

## Зачем

После того как F-2 готова — добавление каждого AI-провайдера = новый ExposureAdapter (additive, не rewrite). Активируется когда какое-то направление становится приоритетом.

## Состояние

**Parking lot.** Зависит от TASK-33 (F-2 Capability Registry). Три независимых sub-задачи, активируются отдельно при появлении конкретного use-case.

---

## Готовый промт для `/speckit.specify` (по каждой sub-задаче отдельно)

```
Реализуй L-3a (или L-3b или L-3c — выбирается):

ЧТО СТРОИМ:
- L-3a: AppActionsExposureAdapter (Google Assistant integration через App Actions).
- L-3b: MCPExposureAdapter (MCP server, Cloudflare Worker extends as MCP host).
- L-3c: GeminiNanoExposureAdapter (Gemini Nano on-device, image description / suggestion).

ЗАЧЕМ:
Конкретный AI use-case востребован (определяется на момент activation).

SCOPE ВКЛЮЧАЕТ:
(per sub-task; см. TASK-33 F-2 ExposureAdapter contract.)

DEPENDENCIES:
- TASK-33 (F-2 Capability Registry) — must be Done.

EFFORT: TBD per sub-task.
```
<!-- SECTION:DESCRIPTION:END -->
