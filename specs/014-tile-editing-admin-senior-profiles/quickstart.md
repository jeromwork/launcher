# Quickstart: F-014.0 dev workflow

## Prerequisites

- JDK 21 (per project standard).
- Android Studio Hedgehog+ или Cursor / IntelliJ IDEA с Kotlin Multiplatform plugin.
- Existing repo `:core`, `:data`, `:app` modules build clean (`./gradlew assembleDebug`).
- No new credentials / API keys needed for F-014.0 (local-only).

## Build the feature

```bash
./gradlew :core:compileKotlinMetadata :data:assembleDebug :app:assembleDebug
```

## Run domain tests (fast feedback loop)

```bash
./gradlew :core:test --tests "com.launcher.api.edit.*"
```

Expected: pure JVM unit tests, <30 sec cold, <5 sec warm.

Specifically:
- `EditUiProfileSelectorTest` — SC-005.
- `TileEditOperationsTest` — FR-001 ops.
- `EditErrorTest` — sealed exhaustiveness.

## Run adapter tests

```bash
./gradlew :data:test --tests "com.launcher.adapter.edit.*"
```

- `NamedConfigsLocalStoreTest` — wire-format roundtrip + invariants.
- `NamedConfigsProcessDeathTest` — persistence across DataStore reload.

## Run UI integration tests

```bash
./gradlew :app:test --tests "com.launcher.ui.edit.*"
```

Note: Compose UI tests use Robolectric + Compose test rules. No emulator required for unit-level UI tests; full integration smoke needs emulator (next step).

## 2-emulator smoke (manual)

Per `android-emulator` skill — start two emulators:
1. **Admin Pixel 7** (API 34, GMS) — Workspace preset.
2. **Managed Pixel 4a** (API 33, GMS) — Simple Launcher preset.

Run:
```bash
./gradlew :app:installDebug
adb -s <pixel7-serial> shell am start -n com.launcher/.MainActivity
adb -s <pixel4a-serial> shell am start -n com.launcher/.MainActivity
```

Verify per spec §Local Test Path:
- Admin Workspace: long-press empty → bottom sheet → tap "+" → picker shows 5 tabs → add app → exit edit mode.
- Senior Simple Launcher: 7-tap empty space → challenge gate (existing спека 010) → edit mode → drag tile to new cell.
- Admin remote edit: tap target tile in `admin_devices` flow → Target Editor opens с frame + banner → picker shows 3 tabs (Widget/Action hidden per FR-019) → push to FakeConfigEditor.

## Konsist fitness functions

```bash
./gradlew :core:test --tests "KonsistDomainIsolationTest"
```

Verifies:
- No class in `core.commonMain.api.edit` imports `android.*`, `androidx.*`, `com.google.firebase.*`.
- No `expect`/`actual` declarations in `api.edit`.

## APK size check

```bash
./gradlew :app:assembleRelease
ls -la app/build/outputs/apk/release/app-release.apk
```

Compare to baseline (pre-F-014.0): delta should be ≤300KB per SC-008.

## Add a new tile type in picker (for future TODO-UX-027/028 work)

(Reference docs — not for F-014.0 implementation.)

1. Add `PickerType.NewKind` enum variant.
2. Add provider in `:data` module per existing спека 005 pattern.
3. Add tab Composable in `UnifiedPickerSheet`.
4. Add visibility rule in target-preset filter (default: hidden in Simple Launcher target).
5. Add `// TODO(shareability)` comment if introduces new wire-format.

## Common gotchas

- **Long-press на эмуляторе**: используйте `adb shell input swipe x y x y 1000` (1 сек hold) — стандартный mouse long-press в Android Studio эмуляторе может не triggerit'ься правильно.
- **7-tap gesture тест**: окно 5 секунд жёсткое; используйте `adb shell input tap` в скрипте.
- **DataStore reset между тестами**: каждый тест должен сам очищать namespace `f014.named_configs.v1` (fixture utility `DataStoreTestRule`).
- **Compose recomposition в jiggle test**: используйте `composeTestRule.mainClock.advanceTimeBy(...)` для контроля времени анимации.

## Troubleshooting

| Симптом | Причина | Решение |
|---|---|---|
| `ProfileSelectionRequiresCapabilityRegistry` thrown | Tried to edit custom (non-built-in) preset | Expected per Q8 — custom presets unsupported until F-2 ships. |
| Snackbar "Бабушка только что изменила" появляется на admin'е | Senior local edit won race, admin's push rejected | Per Q7 — expected silent senior-side, post-hoc snackbar admin-side. |
| Empty state «+» не открывает picker | Edit mode active flag confuses entry path | Per Q6 / FR-020a — empty-state «+» bypasses edit mode; check `EditMode.active == false` precondition. |
| TalkBack drag не работает | Drag-and-drop incompatible с screen reader | Open issue per R1 in plan. **Pending FR-012a addition.** |

## Next development steps

After F-014.0 plan PASSes Constitution Check:
1. Run `speckit-tasks` для генерации `tasks.md`.
2. Address R1 — TalkBack — добавить FR-012a в spec.md (recommended before tasks).
3. Implement tasks в branch `014-tile-editing-admin-senior-profiles` (current branch).

---

## TL;DR на русском

**Что в этом quickstart**: команды для сборки, тестов, smoke F-014.0 на dev машине.

**Главное**:
- **Domain тесты** запускаются за <30 секунд: `./gradlew :core:test --tests "com.launcher.api.edit.*"`.
- **2-эмулятор smoke** — Pixel 7 (admin Workspace) + Pixel 4a (бабушкин Simple Launcher).
- **Никаких credentials** для F-014.0 — всё локально. F-014.1 потребует Firebase project setup.
- **Konsist fitness functions** автоматически проверяют что domain не impоrt'ит android.* / Firebase.

**Главные подводные камни**:
- Long-press на эмуляторе — через `adb shell input swipe x y x y 1000`.
- 7-tap — через `adb shell input tap` скрипт, окно 5 сек жёсткое.
- DataStore нужно очищать между тестами (через `DataStoreTestRule`).

**Перед tasks.md**: добавить FR-012a в спеку — TalkBack alternative для drag-and-drop (R1 в плане §8).
