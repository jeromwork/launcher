# Dev-experience Checklist: HomeActivity loading regression

**Purpose**: Verify feature is buildable, testable, debuggable locally without paid services / production accounts / fragile rituals.
**Created**: 2026-06-26
**Feature**: [spec.md](../spec.md)

## Checks

- [x] **Buildable locally** — `./gradlew :app:assembleRealBackendDebug` — existing build.
- [x] **Unit testable on JVM** — `HomeComponentLoadingStateTest` с fake `FlowRepository` (Local Test Path). 3 transitions покрываются.
- [x] **No production service required** — fake repository inline в test, no Firebase emulator, no network.
- [x] **Smoke на pixel_5_api_34** — emulator skill доступен. `installRealBackendDebug` + wizard → home, секундомер. Воспроизводимо.
- [x] **Smoke на физическом Xiaomi 11T** — `[deferred-physical-device]` явно — owner запускает руками.
- [x] **Logcat trace для debugging** — FR-012 + SC-008 (зафиксировать в research.md).
- [x] **Reproducible repro** — свежая установка → wizard → main. Не зависит от device-specific account / paired admin.
- [x] **No flaky network** — fix полностью local.
- [x] **No fragile manual rituals** — кроме `adb install fresh + manual wizard` (стандартный flow).
- [x] **Baseline cold-start measurement** — `[deferred-local-emulator]` task в tasks.md, через `adb shell am start -W`.
- [x] **Test names match feature** — `HomeComponentLoadingStateTest`, `HomeActivityLoadingTest`.
- [x] **Fixtures self-contained** — fake `FlowRepository` не зависит от bundled JSON loading через AssetManager (для unit-теста).
- [x] **Verification command in spec** — `./gradlew :core:testDebugUnitTest --tests "*HomeComponent*"` explicit.

## Verdict

✅ **13/13 passed.**

## Notes

Bug-fix спека с минимальной dev-experience friction. Главный manual gate — `[deferred-physical-device]` на Xiaomi 11T — owner владеет устройством, не блокер для разработки.
