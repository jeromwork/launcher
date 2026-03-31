# Quickstart: Launcher Core Foundation

**Feature**: `001-launcher-core-foundation`  
**Plan**: [plan.md](./plan.md)

## Prerequisites

- Android Studio **Ladybug** or newer (or compatible JDK 17 + Android SDK)
- Android SDK **35** installed; platform tools up to date

## Repository layout

- Root `settings.gradle.kts` with `:app`, `:core`
- `app/` — `LauncherApplication`, `HomeActivity`, XML home layout, `AppModuleDescriptors`
- `core/` — `SystemEventBridge`, `EventRouter`, `ModuleRegistry`, `ProfileEngine`, `AppIndex`, `ActionDispatcher`, `LauncherCore`

Feature docs alongside code:

- `specs/001-launcher-core-foundation/EXTENSION_GUIDE.md` — adding `:feature-*` modules
- `specs/001-launcher-core-foundation/PLATFORM_EVENTS.md` — MVP OS listener / routing table

## Build and test

1. Open the **repository root** in Android Studio.
2. **Sync Gradle**; resolve any SDK path prompts.
3. Run **unit tests** in `core` (`test` source set) from Android Studio or from a shell at repo root:

   ```bash
   ./gradlew :core:testDebugUnitTest
   ```

   On Windows (PowerShell or `cmd`):

   ```bat
   gradlew.bat :core:testDebugUnitTest
   ```

4. Run the **app** configuration on an emulator or device (API 26+).

## Verify architecture rules (manual)

- Grep under `app/src` for `registerReceiver` → should be **empty** (only `core` registers the package bridge; see `SystemEventBridge`).
- Confirm **default profile** JSON exists under assets with `schemaVersion`.
- Open [contracts/README.md](./contracts/README.md) and trace each `contractId` to a package or interface in `core`.

## Related docs

- [spec.md](./spec.md) — product requirements  
- [data-model.md](./data-model.md) — entities and precedence  
- [research.md](./research.md) — toolchain and MVP listener scope  
