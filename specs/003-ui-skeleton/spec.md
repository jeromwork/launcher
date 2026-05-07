# Spec 003: UI Skeleton

**Status**: Active | **Date**: 2026-05-05 | **Author**: project owner

## Overview

Реализация UI-каркаса нового навигационного дизайна приложения без реального backend/sync.  
Этап 0 — закладывает структуру, на которой тестируются все последующие фичи.

## Problem Statement

Feature 002-whatsapp-tile-return реализовала хардкод-тайл WhatsApp в HomeActivity. Это тупиковый режим: нет общей навигации, нет расширяемой системы слотов, нет настроек. Новая UI-парадигма требует:
- горизонтальной полосы флоу (кнопки в bottom bar)
- экрана слотов для каждого флоу
- wizard-потоков для добавления флоу и слотов
- экрана настроек
- заглушки для QR и admin-device flow

## User Stories

| ID | Story | Acceptance Criteria |
|----|-------|---------------------|
| US-301 | Пожилой пользователь видит знакомые контакты в виде крупных кнопок | Bottom bar показывает вкладку «Семья»; внутри — слоты Аня и Олег с кнопкой «Позвонить» |
| US-302 | Пользователь может инициировать звонок через слот | Тап на слот → confirmation overlay → тап «Позвонить» → WhatsApp handoff |
| US-303 | Пользователь может добавить новый флоу | Тап на «+» в bottom bar → wizard-заглушка с выбором шаблона |
| US-304 | Пользователь открывает настройки | Настройки доступны с главного экрана; показывают toggle «разрешить управление» + QR-placeholder |
| US-305 | (Admin preset) Управление устройствами как отдельный флоу | В пресете `flow-light` в списке шаблонов появляется «Управление телефонами» |
| US-306 | При первом запуске пользователь выбирает пресет | Запуск приложения без активного пресета → FirstLaunchActivity → 3 крупные карточки (workspace / launcher / simple-launcher) → выбор сохраняется в DataStore → переход в HomeActivity. Повторный запуск пропускает picker. |
| US-307 | Пользователь может сменить пресет в настройках | Settings → «Сменить пресет» → диалог со списком → выбор → recreate() activity, mock-конфигурация и тема обновляются. Сброс данных возвращает в FirstLaunchActivity. |

## Scope

### In Scope
- NavigationHost в HomeActivity с кастомным BottomFlowBar
- FlowFragment с GridLayout слотов
- item_slot.xml: min 72dp tap target, icon placeholder, label
- WhatsApp confirmation/warning overlay (переезд из HomeActivity в FlowFragment)
- SettingsFragment: язык-placeholder, пресет, переключатель пресета, toggle «разрешить управление», QR-placeholder, сброс данных
- AddFlowWizardFragment: список шаблонов флоу (без реального сохранения)
- AddSlotWizardFragment: выбор типа действия, контакт-placeholder (без реального сохранения)
- AdminDevicesFragment: empty state + «+» placeholder (только в пресете admin)
- Новые API-модели: FlowDescriptor, SlotDescriptor, SlotAction, FlowRepository port, FlowPreset, PresetRepository port
- MockFlowRepository читает разные mock JSON в зависимости от активного пресета (schemaVersion field)
- CoreContractVersions: LAUNCHER_FLOWS v1, LAUNCHER_PRESETS v1
- Миграция 002: убрать хардкод из HomeActivity, Core-логика остаётся
- **First-launch preset picker (Phase 9):** FirstLaunchActivity с тремя крупными карточками (workspace / launcher / simple-launcher), сохранение в DataStore, debug-only intent extra `--es preset <slug>`
- Density-стили `Theme.Launcher.Workspace`, `Theme.Launcher.LauncherPreset`, `Theme.Launcher.SimpleLauncher` — разный размер тапов и шрифтов по пресету
- Скрипты `scripts/reset-and-launch.ps1`, `scripts/test-two-presets.ps1` для smoke-теста двух эмуляторов

### Out of Scope
- Реальное сохранение флоу/слотов (Firebase, Room, etc.)
- Настоящий QR-код scanner/generator
- Admin pairing flow (только UI-заглушка)
- Multi-language selection (only placeholder)

## Related Project Context

- `.specify/memory/constitution.md` v1.4 — binding governance
- `docs/governance/document-map.md` — navigation for all project docs
- `docs/product/context-decisions-and-open-questions.md` — зафиксированные решения Этапа 0
- `docs/product/senior-safe-launcher-plan.md` — долгосрочная стратегия Safe Launcher
- `docs/adr/ADR-001-cross-platform-strategy.md` — Android-first, parity gate
- `docs/adr/ADR-004-localization.md` — все строки локализуемы
- `specs/001-launcher-core-foundation/spec.md` — архитектурный фундамент
- `specs/002-whatsapp-tile-return/spec.md` — мигрируемая фича

## Non-Functional Requirements

| Категория | Требование |
|-----------|------------|
| Accessibility | Все tap targets ≥ 72dp; contentDescription на каждом слоте; контрастность ≥ 4.5:1 |
| Performance | Холодный старт не блокирует main thread; loadFlows() вызывается на IO dispatcher |
| Battery | Никакого polling; никаких фоновых сервисов |
| Локализация | Все user-facing строки через strings.xml (локаль-ready) |
| Android | minSdk 26, targetSdk 35; View + XML only, никакого Compose |
