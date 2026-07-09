---
name: android-emulator
description: Single source of truth for running Android emulators in this project — start one or two emulators, position windows, build/install/verify-fresh APK, take screenshots, dismiss known runtime gotchas. Invoke whenever a UI change needs visual verification, when iterating against a device, or when running multi-device smoke tests (T072 etc.).
---

# Android Emulator Workflow

Binding workflow for running Android emulators against this project.
This skill replaces any prior `docs/dev/emulators.md` — when this file
disagrees with anything else, this file wins.

---

## 1. SDK and AVD locations (Windows)

- Android SDK: `C:\Users\<user>\AppData\Local\Android\Sdk`
- `emulator`: `<SDK>\emulator\emulator.exe`
- `adb`:      `<SDK>\platform-tools\adb.exe`
- AVDs live in: `%USERPROFILE%\.android\avd\`

> Do not assume `emulator` / `adb` are on `PATH`. Always invoke them by full
> path when scripting from the agent shell, otherwise calls fail on a clean
> profile (and you randomly hit the scrcpy-bundled `adb` instead, which
> collides on port 5037).

The current canonical AVDs for this project are:

- **`Medium_Phone_API_36.1`** — primary, 1080×2400, API 36.1, Play Store image.
- **`Medium_Phone_API_36.1_2`** — independent clone for two-emulator scenarios
  (created via the recipe in §3a).

List with:

```
"<SDK>\emulator\emulator.exe" -list-avds
```

---

## 2. Single-emulator mode (default)

For everyday work, use one emulator on the default port:

```
"<SDK>\emulator\emulator.exe" -avd Medium_Phone_API_36.1 -port 5554 -no-boot-anim
```

This instance is writable — snapshots and installed apps persist between runs.

Run the window-placement block from §4 immediately after startup.

---

## 3. Two emulators in parallel

> **Default approach: clone the AVD into two independent AVDs and run each
> normally.** The `-read-only` trick (sharing one AVD between two instances)
> is fragile in practice — see §3b.

### 3a. Cloning the AVD (recommended)

The AVD on disk is **not** named the same as the AVD id. The id is
`Medium_Phone_API_36.1`, but the directory is `Medium_Phone.avd`. Confirm
with `ls %USERPROFILE%\.android\avd` before scripting paths.

PowerShell to make a second independent AVD `Medium_Phone_API_36.1_2` from
the existing one:

```powershell
$avdRoot = Join-Path $env:USERPROFILE ".android\avd"
$srcDir  = Join-Path $avdRoot "Medium_Phone.avd"
$dstDir  = Join-Path $avdRoot "Medium_Phone_2.avd"
$dstName = "Medium_Phone_API_36.1_2"
$dstIni  = Join-Path $avdRoot "$dstName.ini"

Copy-Item -Recurse $srcDir $dstDir
@"
avd.ini.encoding=UTF-8
path=$dstDir
path.rel=avd\Medium_Phone_2.avd
target=android-36.1
"@ | Set-Content -Path $dstIni -Encoding ASCII

# patch the cloned AVD's identity + any baked absolute paths
$cfg = Join-Path $dstDir "config.ini"
(Get-Content $cfg) `
  -replace '(?i)AvdId\s*=.*', "AvdId=$dstName" `
  -replace '(?i)avd\.ini\.displayname\s*=.*', "avd.ini.displayname=$dstName" |
  Set-Content -Path $cfg -Encoding ASCII

$hw = Join-Path $dstDir "hardware-qemu.ini"
if (Test-Path $hw) {
  (Get-Content $hw) -replace [regex]::Escape("Medium_Phone.avd"), "Medium_Phone_2.avd" |
    Set-Content -Path $hw -Encoding ASCII
}
```

Run them as ordinary instances on different ports:

```
"<SDK>\emulator\emulator.exe" -avd Medium_Phone_API_36.1   -port 5554 -no-boot-anim -no-snapshot-load
"<SDK>\emulator\emulator.exe" -avd Medium_Phone_API_36.1_2 -port 5556 -no-boot-anim -no-snapshot-load
```

The first cold boot is slower than a snapshot load; subsequent runs can drop
`-no-snapshot-load`.

### 3b. Why not `-read-only`

The Android docs say two instances of the same AVD work if **both** use
`-read-only`. On this machine that consistently produces a stuck
`device offline` state on both adb endpoints — the snapshot loads, the GUI
comes up, but the adb handshake never completes, so `install` / `shell` are
unusable. Use the cloned-AVD recipe in §3a instead.

If you do try `-read-only`, the rule is firm: **all** instances must use it
(no writable + read-only mix), or you get:

> `Another emulator instance is running. Please close it or run all emulators with -read-only flag.`

### 3c. Ports

Ports must be even and ≥ 5554; adb assigns the next odd port automatically
for the emulator console.

### 3d. Verify both came up and finished booting

```
"<SDK>\platform-tools\adb.exe" devices
# expect:
# emulator-5554   device
# emulator-5556   device
```

```
"<SDK>\platform-tools\adb.exe" -s emulator-5554 wait-for-device
"<SDK>\platform-tools\adb.exe" -s emulator-5554 shell getprop sys.boot_completed
# returns "1" when boot is complete
```

Cap any boot-wait loop at 120s per device. If it doesn't reach `1` —
**stop and inspect logs**, don't keep polling forever.

---

## 4. Window placement

> **Mandatory after every emulator start.** Otherwise the Qt window
> position restores from AVD config and frequently lands above the visible
> work area on multi-monitor setups — the window becomes effectively
> invisible. The placement script below is **not optional**.

The emulator does not honor any documented `-window-x` / `-window-y` flag in
recent versions. Move the window into view via Win32 `MoveWindow` from
PowerShell:

```powershell
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win {
  [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr h, int x, int y, int w, int h2, bool r);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int n);
}
"@
Add-Type -AssemblyName System.Windows.Forms

# wait up to 20 s for both windows to actually appear
$deadline = (Get-Date).AddSeconds(20)
do {
  $wins = Get-Process | Where-Object { $_.MainWindowTitle -match 'Android Emulator' } |
          Sort-Object MainWindowTitle
  if ($wins.Count -ge 2) { break }
  Start-Sleep -Milliseconds 500
} while ((Get-Date) -lt $deadline)

# Size from the actual working area (excludes the taskbar) — never hardcode pixels.
$wa = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
$h  = $wa.Height - 10
$w  = [int]($h * 9 / 19.5) + 40                   # +40 ~= emulator sidebar
$maxW = [int](($wa.Width - 30) / 2)               # must fit two side by side
if ($w -gt $maxW) { $w = $maxW; $h = [int](($w - 40) * 19.5 / 9) }

$x = $wa.X + 10
$y = $wa.Y + 5
foreach ($win in $wins) {
  [Win]::ShowWindow($win.MainWindowHandle, 9) | Out-Null   # SW_RESTORE
  [Win]::MoveWindow($win.MainWindowHandle, $x, $y, $w, $h, $true) | Out-Null
  $x += $w + 5
}
```

Tile pattern: instances bordered by 5–10 px, sized from
`Screen.PrimaryScreen.WorkingArea` so the bottom isn't clipped by the
taskbar and so two windows fit side by side regardless of monitor size.
**Do not** hardcode 460×900 etc. — on a 1536×824 work area it crops the
bottom of the phone screen.

The 20-second deadline is the cap. If windows never appear within 20s —
stop, inspect the emulator output file, don't loop.

---

## 5. Install + launch the app

After the APK is built (`./gradlew.bat :app:assembleDebug`):

```
APK=app\build\outputs\apk\debug\app-debug.apk
"<SDK>\platform-tools\adb.exe" -s emulator-5554 install -r %APK%
"<SDK>\platform-tools\adb.exe" -s emulator-5556 install -r %APK%

"<SDK>\platform-tools\adb.exe" -s emulator-5554 shell am start -n com.launcher.app/.firstlaunch.FirstLaunchActivity
"<SDK>\platform-tools\adb.exe" -s emulator-5556 shell am start -n com.launcher.app/.firstlaunch.FirstLaunchActivity
```

Always run the freshness check from §5a immediately after `install -r`.

---

## 5a. Verifying the installed APK is the just-built one

> **Build green ≠ device runs the new code.** Common ways the device ends
> up running an older artifact: previous `assembleDebug` failed half-way,
> install step skipped, two emulators where only one got `install -r`,
> packagepath caching by ART. Always verify before drawing conclusions
> from a screenshot.

Compare the local APK's checksum against the installed one:

```bash
SDK_ADB="C:/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe"
APK="app/build/outputs/apk/debug/app-debug.apk"
SERIAL="emulator-5554"

LOCAL=$(md5sum "$APK" | awk '{print $1}')
PKG_PATH=$("$SDK_ADB" -s "$SERIAL" shell pm path com.launcher.app | tail -1 | cut -d: -f2 | tr -d '\r')
INSTALLED=$("$SDK_ADB" -s "$SERIAL" shell md5sum "$PKG_PATH" | awk '{print $1}')

if [ "$LOCAL" = "$INSTALLED" ]; then
  echo "FRESH"
else
  echo "STALE — re-running install -r"
  "$SDK_ADB" -s "$SERIAL" install -r "$APK"
fi
```

Run this **after every `install -r`** before screenshotting. If the
checksum still differs after the second install — the install itself is
failing silently. Stop, read `install -r` output, do not keep iterating.

`pm clear com.launcher.app` is unrelated to install freshness — it just
wipes runtime data. Use it before relaunch when you need a clean state
(e.g. to see FirstLaunchActivity again instead of a saved preset).

---

## 5b. Keep-alive workflow (default; do not cold-boot per step)

A cold boot of `Medium_Phone_API_36.1` takes 30–90 s. An install +
relaunch cycle with the emulator already up takes 5–10 s. Always prefer
the latter:

1. Bring up the emulator **once** at the start of a working session
   (single or two-emulator mode per §2 / §3).
2. Run the window-placement block from §4 once.
3. Per-step iteration:
   ```bash
   ./gradlew.bat :app:assembleDebug                       # build
   "$SDK_ADB" -s emulator-5554 install -r "$APK"          # install
   # verify fresh per §5a
   "$SDK_ADB" -s emulator-5554 shell pm clear com.launcher.app   # optional, for clean state
   "$SDK_ADB" -s emulator-5554 shell am start -n com.launcher.app/.firstlaunch.FirstLaunchActivity
   sleep 3
   "$SDK_ADB" -s emulator-5554 exec-out screencap -p > build/<step-name>.png
   ```

Only kill the emulator when you actually need to (machine reboot,
reclaiming memory, or recovering from a stuck `device offline` adb state).

---

## 5c. Taking screenshots and acting on them

Default screenshot location: `build/<step-name>.png` (project's `build/`
is already gitignored). Use distinct names per step so you don't
overwrite evidence of previous progress.

> **HARD RULE — screenshot size ≤ 2000 px on the longest side.** Claude
> refuses images where either dimension exceeds 2000 px and **the whole
> conversation stream dies** — you cannot recover the session, only
> restart it. This has already burned one working session (2026-07-08,
> Xiaomi 11T screencap 1080×2400 → 2400 > 2000 → stream aborted). The
> raw `screencap -p` from a modern phone / AVD (1080×2400, 1440×3200,
> Xiaomi 11T @ 1080×2400) always violates this — **you must downscale
> before `Read`**. No exceptions.
>
> Target: **longest side ≤ 1800 px** (safety margin under the 2000 hard
> cap). Aspect ratio preserved, PNG output.

Capture + downscale in one shot (default recipe — always use this
instead of raw `screencap -p > file.png`):

```bash
SDK_ADB="C:/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe"
OUT="c:/work/launcher/build/<step>.png"
RAW="c:/work/launcher/build/<step>.raw.png"

"$SDK_ADB" -s emulator-5554 exec-out screencap -p > "$RAW"
ffmpeg -y -i "$RAW" -vf "scale='if(gt(iw,ih),min(1800,iw),-2)':'if(gt(ih,iw),min(1800,ih),-2)'" "$OUT"
rm "$RAW"
```

Alternative (Python + Pillow) if ffmpeg is unavailable:

```bash
python -c "from PIL import Image; im=Image.open('$RAW'); im.thumbnail((1800,1800)); im.save('$OUT')"
```

Physical devices via `adb -s <serial>`: same rule — raw Xiaomi 11T
screencap is 1080×2400 → downscale required (2400 > 2000).

**Verify before `Read`** if in doubt:

```bash
ffprobe -v error -select_streams v:0 -show_entries stream=width,height -of csv=p=0 "$OUT"
# expect e.g. 810,1800 — both ≤ 2000
```

Then `Read` the file in the agent — Claude renders the PNG inline.

Each screenshot returns a coordinate-mapping note like
`displayed at 900x2000, multiply by 1.20 to map to original`. **Tap
coordinates passed to `adb shell input tap X Y` must be on the original
1080×2400 system, not the displayed image's.** Multiply the coordinates
read off the displayed image by the printed factor before tapping. The
downscale-before-Read step does NOT change the tap coordinate system —
adb still receives coordinates on the device's real display grid.

---

## 5d. Common runtime gotchas (already understood, do not re-debug)

- **"System UI isn't responding" ANR dialog.** Known AMD GPU driver issue
  at cold boot on this machine. Doesn't break adb, install, or app launch
  — the dialog is purely a system overlay. Either wait several seconds (it
  often dismisses itself once Pixel Launcher recovers) or tap "Wait" at
  approximate original coordinates `(384, 1596)` to dismiss it manually.
  **Never** infer "the app is broken" from this dialog appearing.

- **`adb` server collision with scrcpy's bundled `adb`.** Symptom:
  emulator stuck `offline` despite normal boot, all `am start` / `install`
  fail. Two `adb` daemons fight for port 5037. Recovery:
  ```
  "<SCRCPY_ADB_PATH>" kill-server
  "<SDK_ADB>" kill-server
  "<SDK_ADB>" start-server
  ```
  Then `adb reconnect offline`. If still stuck, kill `qemu-system-x86_64`
  (acceptable here because the AVD writable image isn't being held by
  anyone else) and restart per §2 / §3a.

- **Two `-read-only` instances stuck offline.** Already documented as the
  reason cloned AVDs (§3a) are the default; do not go back to `-read-only`
  for multi-emulator runs.

- **Pixel Launcher "isn't responding" inside the guest** on AMD integrated
  GPUs (Vega 8 etc.). Cosmetic; doesn't block `adb install` / `am start`.
  Same dismissal as the systemui ANR above.

---

## 6. Shutting down cleanly

Only when actually needed (machine reboot, low memory, recovering from a
fully stuck adb state):

```
"<SDK>\platform-tools\adb.exe" -s emulator-5554 emu kill
"<SDK>\platform-tools\adb.exe" -s emulator-5556 emu kill
```

Avoid `taskkill` on `qemu-system-x86_64` unless the device is hung — `emu
kill` gives the guest a chance to shut down and reduces the chance of
corrupting the AVD's writable image.

Default posture is **leave running** between steps. Saves ~30–60 s per
cycle and avoids the cold-boot ANR / GPU spin-up issues entirely.

---

## When to invoke this skill

- Any UI / screen change that should be visually verified.
- Any step in `specs/004-ui-stack-migration/tasks.md` (Compose migration)
  whose `tasks.md` line says "screenshot" / "smoke" / "T072" / "gate-decision".
- `specs/003-ui-skeleton/` T072 (mandatory two-emulator smoke).
- Bug investigation that needs a real device behavior trace.

Do **not** spin up an emulator just for `:app:assembleDebug` build
verification — build green is sufficient for non-UI changes.
