---
id: TASK-72
title: Pool Browser UI for Opportunistic Preset Authoring
status: Draft
assignee: []
created_date: '2026-06-30 20:00'
updated_date: '2026-06-30 20:00'
labels:
  - phase-3
  - foundation
  - ui
  - preset-authoring
  - follows-task-65
milestone: m-2
dependencies:
  - TASK-65
priority: low
ordinal: 72000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Admin или продвинутый user хочет собрать **свой preset** — выбрать какие настройки из каталога (pool) включить, в каком виде. TASK-72 добавляет UI для просмотра всех pool entries (по типам: Android-settings, UI-customization, layout, ...) и сборки preset'а из них.

Это позволяет создавать кастомные presets **внутри приложения**, без работы напрямую с JSON-файлами. В будущем такие presets можно делиться через marketplace (TASK-35).

## Зачем

Сейчас preset'ы хардкожены в bundled assets. Pool browser открывает дорогу для:
- Admin собирает preset для своей бабушки (специфичный набор плиток, своя тема).
- Продвинутый user экспериментирует с собственным workspace.
- Community marketplace presets.

## Что входит технически (для AI-агента)

- UI экран «Browse Pools» — список всех pools (system-settings, ui-customization, tile-types, ...).
- Per-pool screen — список entries с метаданными (title, description, suggested usage).
- «Build new preset» wizard: выбрать pool entries → задать label / description → save.
- Готовый preset попадает в local store (через `LocalPresetSource` adapter — extension `ConfigSource`).
- Validation: required pool entries не могут быть unchecked.

## Состояние

**Planned (Phase 3+).** Возникла в clarify-фазе TASK-65 как natural extension. TASK-65 закладывает API (`PoolSource.listEntries`), но UI — отдельная фича.

В будущем интегрируется с MCP / AI-agent (TASK-33 Capability Registry): AI-агент может opportunistically собирать preset для пользователя через те же APIs.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Будут заданы при /speckit.specify запуске
<!-- AC:END -->
