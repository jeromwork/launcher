---
id: TASK-72
title: Pool Browser UI for Opportunistic Preset Authoring
status: Draft
assignee: []
created_date: '2026-06-30 20:00'
updated_date: '2026-07-17 04:10'
labels:
  - phase-5
  - foundation
  - ui
  - preset-authoring
  - follows-task-65
milestone: m-4
dependencies:
  - TASK-65
  - TASK-120
  - TASK-131
  - TASK-135
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

<!-- SECTION:NOTES:BEGIN -->

**Аудит 2026-07-17 (сессия взяла в работу и вернула в Draft тем же днём).** Решение владельца: это чистый конструктор пресетов (authoring UI), НЕ живое применение компонентов на устройстве (живое применение = TASK-134 add-flow + уже построенный ReconcileEngine). Собранным пресетам сейчас нет потребителя — тот же аргумент, что и scope-cut TASK-129. Вся область вынесена за MVP: тег `preset-authoring` объединяет TASK-18, 35, 72, 130, 132, 133, 135 (все m-4 / phase-5).

**Терминология карточки устарела (написана до TASK-120/126/127) — при взятии в работу ре-базировать:**
- `PoolSource.listEntries` не существует; порт — `PoolSource.loadPool()` ([PoolSource.kt](../../core/src/commonMain/kotlin/com/launcher/preset/port/PoolSource.kt)).
- `ComponentDeclaration` переименован в `Blueprint` (TASK-126/129).
- `ConfigSource` / `LocalPresetSource` не существуют; порт — `PresetSource`, и он **read-only** — пути записи пресета нет вообще, его придётся спроектировать (wire-format решение, rule 5).
- «required entries» теперь `Blueprint.requires`/`required` + `PresetValidator`, не checkbox-правило браузера.
- Blueprint не несёт UI-метаданных (titleKey/icon/category) — они в TASK-135; TASK-72 = её триггер-потребитель (pool schema v2→v3).
- Preset = три ортогональных поля (`wizardFlow`/`settingsMap`/`activeComponents`) — спека обязана сказать, какие из них собираем.
- Экспорт/шаринг между устройствами заблокирован TASK-131 (lenient reader, R-8 из plan.md TASK-127).
- Пикер FirstLaunchActivity — до сих пор legacy `FlowPreset` (3 hardcoded); собранный пресет туда сам не попадёт.
- Указатель на TASK-33 устарел: seam называется F-2 Capability Registry, порты (`CapabilityQuery`/`CapabilityContract`) уже отгружены TASK-120.

**Индустриальные ориентиры (research 2026-07-17):** каталог = неглубокие категории + поиск + превью перед добавлением (Shortcuts/Storybook); сборка = copy-on-instantiate с меткой источника — у нас уже есть `basedOnPreset`/`presetVersion`; параметры компонентов — селекторы Home Assistant (typed inputs → авто-форма); частичные бандлы VS Code Profiles (пресет переопределяет только выбранные категории); анти-паттерн — Nova Launcher (opaque backup без schemaVersion/превью/зависимостей).

**Смежный вопрос при ре-базировании:** общий surface «список доступных pool entries» с TASK-134 (add-flow тоже показывает каталог пула, но на уровне профиля).

<!-- SECTION:NOTES:END -->
