# Quickstart: WhatsApp Contact Tiles via Communication Shell

**Feature**: `002-whatsapp-tile-return`  
**Plan**: [plan.md](./plan.md)

## Prerequisites

- Android SDK 35 and JDK 17 installed.
- WhatsApp installed on test device/emulator for success-path checks.
- Test variant of mock communication config available in app assets/resources.

## Build and test commands

From repository root:

```powershell
.\gradlew.bat :core:testDebugUnitTest :app:testDebugUnitTest
```

Run app on API 26+ device/emulator:

```powershell
.\gradlew.bat :app:installDebug
```

## Manual verification flows

### 1. Confirmed handoff success

1. Open Launcher home.
2. Tap a contact tile action (`Call` or `Video`).
3. Confirm on confirmation screen.
4. Verify transition to WhatsApp occurs and diagnostics event `WHATSAPP_LAUNCH_CONFIRMED` is emitted.

### 2. Cancel flow

1. Tap a contact tile action.
2. Press cancel on confirmation screen.
3. Verify Launcher remains on same home surface and no external transition occurs.

### 3. WhatsApp unavailable warning

1. Use device state without WhatsApp or disable launchability in test setup.
2. Trigger action and confirm.
3. Verify Launcher stays foreground and shows large readable warning.

### 4. Invalid action capability warning

1. Prepare mock config where chosen action is no longer supported.
2. Confirm action from tile.
3. Verify warning state with non-technical guidance and stable reason code.

### 5. Return restoration

1. Start successful handoff from a tile.
2. Return to Launcher via Back/Home/app switcher.
3. Verify same home level is restored; if tile/state changed, verify nearest stable home fallback and `RETURN_RESTORE_FALLBACK` event.

### 6. Duplicate tap protection

1. Rapidly tap same tile action multiple times before and after confirmation.
2. Verify only one action cycle proceeds and overlapping handoff is prevented.

### 7. Localization and accessibility checks

1. Switch to a language with longer strings.
2. Verify action labels/warnings remain understandable and readable.
3. Verify large tap targets and assistive navigation semantics on launcher-owned screens.

### 8. Permission regression checks

1. Inspect `app/src/main/AndroidManifest.xml`.
2. Inspect `core/src/main/AndroidManifest.xml`.
3. Verify no new broad runtime permissions were added for this feature.

### 9. Parity messaging checks

1. Review product-facing note in `docs/product/context-decisions-and-open-questions.md`.
2. Verify wording clearly states Android implementation now and iPhone parity as future product intent.

## Artifact links

- [research.md](./research.md)
- [data-model.md](./data-model.md)
- [contracts/README.md](./contracts/README.md)
