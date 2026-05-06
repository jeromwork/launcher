# reset-and-launch.ps1
# Clears app data and launches FirstLaunchActivity with optional preset extra (debug only).
# Usage:
#   .\scripts\reset-and-launch.ps1 -Serial emulator-5554 -Preset workspace
#   .\scripts\reset-and-launch.ps1 -Serial emulator-5556 -Preset simple-launcher

param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Preset = ""
)

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = "com.launcher.app"
$activity = "$pkg/.firstlaunch.FirstLaunchActivity"

Write-Host "[1] pm clear $pkg on $Serial"
& $adb -s $Serial shell pm clear $pkg

if ($Preset) {
    Write-Host "[2] launch with preset=$Preset"
    & $adb -s $Serial shell am start -n $activity --es preset $Preset
} else {
    Write-Host "[2] launch (no preset, picker will show)"
    & $adb -s $Serial shell am start -n $activity
}
