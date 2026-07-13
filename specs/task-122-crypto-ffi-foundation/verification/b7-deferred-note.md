# B7 arm64 Emulator Attempt — Deferred

Date: 2026-07-13
Attempt owner: AI (TASK-122 B7 subagent)
Outcome: **DEFERRED** (Variant B). T015 and T016 `[deferred-*]` markers unchanged.

## What was attempted

Goal: run `./gradlew :crypto-ffi:connectedAndroidTest` on an arm64 Android emulator
to resolve T016 `[deferred-local-emulator]` while owner is away from Xiaomi 11T.

Steps executed:

1. Enumerated installed Android SDK system images — only `x86_64` variants present
   (`android-35/google_apis`, `android-36.1/google_apis_playstore`). No arm64.
2. Installed `system-images;android-34;google_apis;arm64-v8a` via `sdkmanager`.
   Download + unzip succeeded (~800 MB, ~4 min).
3. Created AVD `pixel_5_api_34_arm64` via `avdmanager create avd -d pixel_5 -k
   system-images;android-34;google_apis;arm64-v8a`. Auto-selected ABI `arm64-v8a`.
   AVD file created successfully at `%USERPROFILE%\.android\avd\pixel_5_api_34_arm64.avd`.
4. Attempted `emulator -avd pixel_5_api_34_arm64 -no-snapshot-load -no-window`.

## Blocker

Emulator refused to start with a **hard fatal error**:

```
INFO  | Found AVD target architecture: arm64
INFO  | Found systemPath ...\system-images\android-34\google_apis\arm64-v8a\
FATAL | Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator
        on x86_64 host. System image must match the host architecture.
```

Android Emulator version: `36.5.10.0` (build 15081367).
Host: Windows x86_64 (Intel).

## Root cause

The Android Studio emulator on **Windows x86_64 hosts does not ship a QEMU
build capable of emulating arm64 guests**. This differs from Linux and macOS,
where QEMU can (slowly) emulate arm64 via TCG. On Windows, the emulator
requires the guest architecture to match the host — x86_64 host = x86_64 image
only. This is documented behaviour of the Android Emulator on Windows and is
not fixable by SDK reinstall, HAXM changes, or Hyper-V toggling.

The `arm64-v8a` system image is only usable on Windows when the host is an
arm64 Windows device (Snapdragon-based Windows-on-ARM), which is not the case
here.

## Impact on TASK-122 AC

Neither T015 nor T016 was unblocked by this attempt.

- **T015** `[deferred-physical-device]` — still the primary path. Owner runs
  `./gradlew :crypto-ffi:connectedAndroidTest` on Xiaomi 11T via USB when back
  at desk. 3/3 tests must go green (`HelloFfiTest.hello_returnsGreeting`,
  `HelloFfiTest.hello_worksWithNonAsciiName`,
  `PanicFfiTest.panic_isConvertedToKotlinException`).
- **T016** `[deferred-local-emulator]` — was the optional fallback. Confirmed
  **not achievable on this Windows x86_64 dev machine**. Remains deferred; if
  ever exercised it would have to be on:
  (a) an arm64 Windows machine, or
  (b) a Linux/macOS host with the same repo checkout, or
  (c) after the crypto-ffi module gains an `x86_64` `.so` build target
      (currently only `arm64-v8a` is built per plan.md decision — device-first).

## How to unblock

Option A (**recommended, matches plan**): owner attaches Xiaomi 11T to
desktop-PC and runs T015. This is the physical-device path the plan already
prescribes as primary.

Option B (**would require plan revision**): add `x86_64-linux-android` /
`x86_64` Rust target to the `crypto-ffi` cargo-ndk build, producing an
`x86_64` `.so` alongside `arm64-v8a`. Then a standard x86_64 AVD can host the
test APK. This would technically resolve T016 on any Windows dev box but
contradicts the current "device-first, arm64-only" plan decision and increases
build time. Not recommended just to close T016 — T015 already exists as the
verifying gate.

## Cleanup performed

- AVD `pixel_5_api_34_arm64` deleted via `avdmanager delete avd`.
- `system-images;android-34;google_apis;arm64-v8a` intentionally kept
  installed (~800 MB) in case owner wants to try another approach; safe to
  `sdkmanager --uninstall` if disk space matters.
- No emulator process left running (never successfully started).

## Verdict for B7

Path: **Variant B (arm64 setup blocked)**. Ready to proceed to B8. Neither
tasks.md nor backlog AC state changes for T015/T016.
