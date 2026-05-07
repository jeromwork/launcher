# Local Android Emulator Workflow

How AI agents and contributors should bring up local Android emulators when working on this project.
Goal: a reproducible, documented setup so anyone (or any agent) starts emulators the same way.

---

## 1. SDK and AVD locations (Windows)

- Android SDK: `C:\Users\<user>\AppData\Local\Android\Sdk`
- `emulator`: `<SDK>\emulator\emulator.exe`
- `adb`:      `<SDK>\platform-tools\adb.exe`
- AVDs live in: `%USERPROFILE%\.android\avd\`

> Do not assume `emulator` / `adb` are on `PATH`. Always invoke them by full path
> when scripting from the agent shell, otherwise calls fail on a clean profile.

The current canonical AVD for this project is **`Medium_Phone_API_36.1`**
(Google APIs Play Store, x86_64, API 36.1). List with:

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

---

## 3. Two emulators in parallel

> **Default approach: clone the AVD into two independent AVDs and run each
> normally.** The `-read-only` trick (sharing one AVD between two instances)
> is fragile in practice — see "Why not -read-only" below.

### 3a. Cloning the AVD (recommended)

The AVD on disk is **not** named the same as the AVD id. The id is
`Medium_Phone_API_36.1`, but the directory is `Medium_Phone.avd`. Confirm with
`ls %USERPROFILE%\.android\avd` before scripting paths.

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

Then run them as ordinary instances on different ports:

```
"<SDK>\emulator\emulator.exe" -avd Medium_Phone_API_36.1   -port 5554 -no-boot-anim -no-snapshot-load
"<SDK>\emulator\emulator.exe" -avd Medium_Phone_API_36.1_2 -port 5556 -no-boot-anim -no-snapshot-load
```

The first cold boot is slower than a snapshot load; subsequent runs can drop
`-no-snapshot-load`.

### 3b. Why not `-read-only`

The Android docs say two instances of the same AVD work if **both** use
`-read-only`. In our setup this consistently produces a stuck `device offline`
state on both adb endpoints — the snapshot loads, the GUI comes up, but the
adb handshake never completes, so `install` / `shell` are unusable.

If you do try `-read-only`, the rule is firm: **all** instances must use it
(no writable + read-only mix), or you get:

> `Another emulator instance is running. Please close it or run all emulators with -read-only flag.`

### 3c. Ports

Ports must be even and ≥ 5554; adb assigns the next odd port automatically
for the emulator console.

### 3d. Cosmetic warnings to ignore

On AMD integrated GPUs (e.g. Vega 8) the Pixel Launcher inside the guest may
show "isn't responding" while booting. This does not block `adb install` /
`am start`; ignore the system dialog and launch your activity directly.

Verify both came up:

```
"<SDK>\platform-tools\adb.exe" devices
# expect:
# emulator-5554   device
# emulator-5556   device
```

Wait until each one has finished booting before installing apks:

```
"<SDK>\platform-tools\adb.exe" -s emulator-5554 wait-for-device
"<SDK>\platform-tools\adb.exe" -s emulator-5554 shell getprop sys.boot_completed
# returns "1" when boot is complete
```

---

## 4. Window placement

> **Mandatory step.** Every time the emulator process starts (initial launch,
> restart after `emu kill`, restart after killing `qemu-system-x86_64`) the
> Qt window position is restored from the AVD config and frequently lands
> *above* the visible work area on multi-monitor setups — the window is then
> effectively invisible. The placement script below is **not optional**: run
> it immediately after every emulator start, before doing anything else.

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

Tile pattern: both instances bordered by 5–10 px, sized from
`Screen.PrimaryScreen.WorkingArea` so the bottom isn't clipped by the
taskbar and so two windows fit side by side regardless of monitor size.
Do **not** hardcode 460×900 etc. — on a 1536×824 work area it crops the
bottom of the phone screen.

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

---

## 6. Shutting down cleanly

```
"<SDK>\platform-tools\adb.exe" -s emulator-5554 emu kill
"<SDK>\platform-tools\adb.exe" -s emulator-5556 emu kill
```

Avoid `taskkill` on `qemu-system-x86_64` unless the device is hung — `emu kill`
gives the guest a chance to shut down and reduces the chance of corrupting the
AVD's writable image (relevant only if the instance was not `-read-only`).
