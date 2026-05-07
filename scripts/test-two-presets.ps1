# test-two-presets.ps1
# Resets both running emulators and launches each with a different preset.
# Usage:
#   .\scripts\test-two-presets.ps1
#   .\scripts\test-two-presets.ps1 -OldPreset simple-launcher -AdminPreset workspace

param(
    [string]$OldSerial    = "emulator-5554",
    [string]$AdminSerial  = "emulator-5556",
    [string]$OldPreset    = "simple-launcher",
    [string]$AdminPreset  = "workspace"
)

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$script = Join-Path $here "reset-and-launch.ps1"

Write-Host "OLD   ($OldSerial)   -> $OldPreset"
& $script -Serial $OldSerial -Preset $OldPreset

Write-Host "ADMIN ($AdminSerial) -> $AdminPreset"
& $script -Serial $AdminSerial -Preset $AdminPreset
