# Smoke Checkpoint — Spec 004 (T413)

**Date**: 2026-05-07
**Branch**: `004-ui-stack-migration`
**Closes**: T072 of spec 003.

## Setup

- Two emulators per skill `.claude/skills/android-emulator/SKILL.md` §3a:
  - `emulator-5554` — `Medium_Phone_API_36.1` (preset = Workspace)
  - `emulator-5556` — `Medium_Phone_API_36.1_2` (preset = Simple launcher)
- Window placement applied per skill §4.
- APK installed and freshness-verified per skill §5a (md5 match) on both
  devices.

## Walkthrough

### 5554 (Workspace preset)

| step | result |
|---|---|
| Launch `FirstLaunchActivity` | ✅ Title "How do you want to use the app?" + 3 preset cards render |
| Tap "Workspace" card | ✅ Navigates to Home; BottomFlowBar shows "Семья" tab; tiles render: Аня / Олег / Контакт 3 / Контакт 4 |
| Tap tile "Аня" | ✅ ConfirmationOverlay: "Позвонить: Аня / Вы хотите Позвонить — Аня?" with Отмена / Позвонить |
| Tap "Отмена" | ✅ Overlay dismissed; tiles still visible |
| Tap settings icon in BottomFlowBar | ✅ SettingsScreen: Язык/Русский, Пресет/Workspace, Удалённое управление/Выключено, Сбросить данные |
| Tap "Сменить" near Пресет | ✅ Dialog "Выберите пресет" with 3 options + Закрыть |
| Tap "Simple launcher" | ⚠️ Pressed Pre-state updates (Settings now shows Пресет/Simple launcher), but **auto-return to Home does not fire** — user remains on Settings |

### 5556 (Simple launcher preset)

| step | result |
|---|---|
| Launch `FirstLaunchActivity` | ✅ Same picker UI |
| Tap "Simple launcher" card | ✅ Navigates to Home with simple-launcher density profile (mock data: Аня / Олег) |
| Tap tile "Олег" | ✅ ConfirmationOverlay rendered |
| Tap "Позвонить" (Confirm) | ✅ WarningOverlay: "WhatsApp недоступен / WhatsApp не установлен или не настроен на этом телефоне." with Понятно — correct fallback path (no WhatsApp installed on emulator) |

## Result

T072 closed. All US-301..US-306 (spec 003) and US-401..US-404 (spec 004)
user-visible behaviors are reachable through the migrated Compose
Multiplatform UI.

## Known issues (deferred to spec 005)

1. **Auto-return on preset switch from Settings.** `SettingsComponent.selectPreset()`
   calls `onPresetChanged()`; the `RootComponent` wiring should pop Settings
   off the stack and re-render Home with the new density profile, but the
   user is left on Settings. Workaround: manual Back. Investigate the
   onPresetChanged callback wiring in `RootComponent`.

2. **Simple-launcher density override is visually too subtle.** Tiles on
   Simple-launcher preset look approximately the same size as Workspace
   tiles — the density modifier in `LauncherTheme` is not producing the
   senior-safe scale-up promised by ADR-005. The Compose UI tests pass
   because they only assert presence of nodes, not size deltas. Action:
   audit `densityFor()` and `LauncherTypography.scaleFor()` in spec 005,
   add a UI test that asserts tile size delta between presets.
