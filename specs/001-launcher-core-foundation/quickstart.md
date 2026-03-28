# Quickstart: Launcher Core Foundation

**Feature**: `001-launcher-core-foundation`  
**Plan**: [plan.md](./plan.md)

## Prerequisites

- Android Studio **Ladybug** or newer (or compatible JDK 17 + Android SDK)
- Android SDK **35** installed; platform tools up to date

## Repository state

The repository may not yet contain the Gradle project; implementation tasks will add:

- Root `settings.gradle.kts` including `:app`, `:core`
- `app/` — `HomeActivity`, application class, XML home layout
- `core/` — SystemEventBridge, EventRouter, ModuleRegistry, ProfileEngine, AppIndex, ActionDispatcher

## After the Android project exists

1. Open the **repository root** in Android Studio.
2. **Sync Gradle**; resolve any SDK path prompts.
3. Run **unit tests** in `core` (`test` source set) from Android Studio or:

   ```bash
   ./gradlew :core:testDebugUnitTest
   ```

4. Run the **app** configuration on an emulator or device (API 26+).

## Verify architecture rules (manual)

- Grep for `registerReceiver` outside `core` → should be **empty** (except framework-generated).
- Confirm **default profile** JSON exists under assets with `schemaVersion`.
- Open [contracts/README.md](./contracts/README.md) and trace each `contractId` to a package or interface in `core`.

## Related docs

- [spec.md](./spec.md) — product requirements  
- [data-model.md](./data-model.md) — entities and precedence  
- [research.md](./research.md) — toolchain and MVP listener scope  
