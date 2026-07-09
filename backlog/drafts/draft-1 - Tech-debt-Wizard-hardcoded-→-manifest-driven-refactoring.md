---
id: DRAFT-1
title: 'Tech debt: Wizard hardcoded → manifest-driven refactoring'
status: Draft
assignee: []
created_date: '2026-07-08 12:02'
labels:
  - tech-debt
  - architecture
  - wizard
  - f-3-followup
  - phase-3
milestone: m-2
dependencies:
  - TASK-120
priority: medium
ordinal: 120000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Сейчас first-run wizard (то что видит пользователь при первой установке — выбор preset'а, вход в аккаунт, задание пароля, ROLE_HOME, permissions) **запрограммирован как жёсткий state machine в Kotlin коде** внутри `FirstLaunchActivity`. По архитектуре ([docs/product/glossary.md](../../docs/product/glossary.md) Article VII §11), Wizard **должен** собираться из JSON-конфига `wizard.manifest`, а `FirstLaunchActivity` быть просто engine'ом который читает manifest и рендерит steps последовательно.

**Wizard — это view профиля** (preset'а). Settings — другое view того же профиля. Обе поверхности должны читать один и тот же конфиг, просто отображать по-разному.

## Зачем

- **Разделение concerns**: сейчас порядок шагов, их содержание, условия skip'а — размазаны по Kotlin коду. Изменить порядок или добавить шаг = code change + пересборка APK. С manifest'ом = править JSON.
- **Preset variability**: разные preset'ы (Simple Launcher, Admin App, Workspace) должны иметь разный wizard flow. Сейчас невозможно без code fork.
- **Settings как view profile**: чтобы «Войти в аккаунт» появилось и в wizard step, и в Settings опции — обе поверхности должны читать общий source of truth (preset).
- **Testability**: wizard flow в JSON проще тестировать чем hardcoded activity.

## Что входит технически (для AI-агента)

**Текущая реализация (что переделываем)**:
- `app/src/main/java/com/launcher/app/firstlaunch/FirstLaunchActivity.kt` — цепочка `renderAuthChoiceStep() → renderRoleHomeStep() → renderPostNotificationsStep() → renderRecoverySetupStep()` через hardcoded callbacks.
- `AuthChoiceStep`, `RoleHomeStep`, `PostNotificationsStep` composables в `core/ui/setup/` — как composable OK, но композиция их порядка — hardcoded.

**Целевая архитектура** (из glossary):
- **`WizardEngine`** в `core/wizard/` (KMP common) — читает `wizard.manifest` JSON, рендерит steps последовательно.
- **`wizard.manifest`** schema — `presetId`, `steps[]` с `stepType` (`UIChoice` / `SystemSetting` / `TutorialHint` / `Auth` / `Custom`) и параметрами.
- **`system-settings.pool`** — каталог Android platform-level settings (ROLE_HOME, POST_NOTIFICATIONS, etc.) которые manifest может ссылаться по `refId`.
- **`ui-customization.pool`** — каталог UI-опций (theme, grid, tileSet) для manifest.

**Уже частично сделано** (по glossary):
- `WizardEngine`, `WizardStep` interface, generic steps — заявлены как готовые в F-3 (TASK-1 Done по бумаге).
- Три JSON-схемы (`wizard.manifest`, `screen.layout`, `tile.set`) — заявлены готовыми.
- **Но `FirstLaunchActivity` не мигрирован** на использование `WizardEngine` — работает своим hardcoded state machine'ом.

**Гипотеза**: `WizardEngine` был написан, но launcher app использует старую реализацию. Может быть нужна проверка что WizardEngine существует и работает, потом миграция FirstLaunchActivity на него, либо доработка если engine недостаточен для наших нужд.

**Sub-tasks (примерно, уточнить при взятии в работу)**:
1. Аудит текущего состояния `core/wizard/` — что реализовано, что нет.
2. Написать `wizard.manifest` JSON для Simple Launcher (первый пресет).
3. Перевести `FirstLaunchActivity` с hardcoded state machine на `WizardEngine.run(manifest)`.
4. Написать manifest'ы для других preset'ов (Workspace, Admin, etc.) — когда появятся.
5. Settings screen как второе view того же profile — отдельная future task.

## Состояние

**Draft. Не в работе.** Создана 2026-07-08 при smoke TASK-6 когда владелец заметил что wizard hardcoded нарушает архитектуру. Записана как техдолг чтобы не потерялась. Приоритет medium — Phase 3 работа, не блокирует MVP (m-0/m-1).

**Триггер для взятия в работу**:
- Второй preset потребует другой wizard flow (не Simple Launcher), ИЛИ
- Настройки как view того же preset'а начинают проектироваться (S-1 spec), ИЛИ
- Владелец эксплицитно берёт в работу как техдолг-cleanup.

До этого — draft, живёт в backlog как напоминание.

## Ссылки

- [docs/product/glossary.md](../../docs/product/glossary.md) — Article VII §11, «Wizard — это view профиля».
- [TASK-1](../tasks/task-1%20-%20Wizard-Engine-Foundation.md) F-3 Wizard Engine Foundation — предположительно foundation готов (Done).
- Discovered 2026-07-08 during TASK-6 T682 smoke — FirstLaunchActivity.kt строки 190-400 содержат hardcoded state machine.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria (draft — уточнить при взятии)

<!-- AC:BEGIN -->
- [ ] #1 [hand] Аудит `core/wizard/` — задокументировано что реализовано, что нужно добавить.
- [ ] #2 [hand] `wizard.manifest` JSON для Simple Launcher preset'а написан и валидирован.
- [ ] #3 [hand] `FirstLaunchActivity` мигрирован на `WizardEngine.run(manifest)` — hardcoded state machine удалён.
- [ ] #4 [hand] Все существующие wizard steps работают через engine на Xiaomi 11T (regression parity).
- [ ] #5 [hand] Preset schema поддерживает разный wizard flow для разных preset'ов (Simple Launcher vs Workspace vs Admin).
<!-- AC:END -->
