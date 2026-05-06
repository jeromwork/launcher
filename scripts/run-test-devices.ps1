# run-test-devices.ps1
# Запуск OLD-эмулятора + опционально деплой APK на эмулятор и/или реальный телефон по WiFi.
#
# Использование:
#   .\scripts\run-test-devices.ps1                          # только запустить эмулятор
#   .\scripts\run-test-devices.ps1 -Deploy                  # запустить + собрать + деплой на эмулятор
#   .\scripts\run-test-devices.ps1 -Deploy -PhoneIP 192.168.1.100  # + деплой на телефон

param(
    [string]$OldAvd     = "Pixel_4",
    [switch]$Deploy,
    [string]$PhoneIP    = "",
    [int]   $BootWait   = 60
)

$SDK  = "$env:LOCALAPPDATA\Android\Sdk"
$emu  = "$SDK\emulator\emulator.exe"
$adb  = "$SDK\platform-tools\adb.exe"
$APK  = "C:\work\launcher\app\build\outputs\apk\debug\app-debug.apk"

function Wait-ForDevice($serial, $maxSec) {
    for ($i = 0; $i -lt $maxSec; $i++) {
        $state = & $adb -s $serial get-state 2>$null
        if ($state -eq "device") { return $true }
        Start-Sleep 1
    }
    return $false
}

# --- Запуск OLD-эмулятора в фоне ---
Write-Host "[1] Запуск эмулятора $OldAvd..."
$proc = Start-Process -FilePath $emu `
    -ArgumentList "-avd $OldAvd -no-snapshot-load -no-snapshot-save -no-audio -no-boot-anim -memory 1024 -cores 2" `
    -PassThru -WindowStyle Normal

Write-Host "    PID: $($proc.Id). Ждём загрузки (до $BootWait сек)..."

# Ждём пока adb увидит устройство
$serial = $null
for ($i = 0; $i -lt $BootWait; $i++) {
    $lines = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "emulator.*device$" }
    if ($lines) {
        $serial = ($lines[0] -split "\s+")[0]
        Write-Host "    Эмулятор готов: $serial"
        break
    }
    Start-Sleep 1
}

if (-not $serial) {
    Write-Host "ОШИБКА: эмулятор не загрузился за $BootWait сек" -ForegroundColor Red
    exit 1
}

# --- Подключение телефона по WiFi ---
if ($PhoneIP) {
    Write-Host "[2] Подключение телефона $PhoneIP..."
    & $adb connect "$PhoneIP:5555"
    Start-Sleep 2
    $phoneState = & $adb -s "$PhoneIP:5555" get-state 2>$null
    if ($phoneState -eq "device") {
        Write-Host "    Телефон подключён: $PhoneIP:5555" -ForegroundColor Green
    } else {
        Write-Host "    Не удалось подключить телефон — продолжаем без него" -ForegroundColor Yellow
        $PhoneIP = ""
    }
}

# --- Сборка и деплой APK ---
if ($Deploy) {
    Write-Host "[3] Сборка APK..."
    Push-Location C:\work\launcher
    .\gradlew.bat assembleDebug --quiet
    Pop-Location

    if (-not (Test-Path $APK)) {
        Write-Host "ОШИБКА: APK не найден после сборки: $APK" -ForegroundColor Red
        exit 1
    }

    Write-Host "[4] Деплой APK..."
    Write-Host "    → $serial (эмулятор OLD)"
    & $adb -s $serial install -r $APK

    if ($PhoneIP) {
        Write-Host "    → $PhoneIP:5555 (телефон Admin)"
        & $adb -s "$PhoneIP:5555" install -r $APK
    }
}

# --- Итог ---
Write-Host ""
Write-Host "Устройства:" -ForegroundColor Cyan
& $adb devices
Write-Host ""
Write-Host "Эмулятор $OldAvd запущен. Закрой окно эмулятора или нажми Ctrl+C для завершения." -ForegroundColor Green
Write-Host ""
Write-Host "Полезные команды:"
Write-Host "  Логи OLD:   $adb -s $serial logcat -s launcher"
if ($PhoneIP) {
Write-Host "  Логи Admin: $adb -s $PhoneIP:5555 logcat -s launcher"
}
Write-Host "  QR-скрин:   $adb -s <admin-serial> exec-out screencap -p > qr.png"
Write-Host "              $adb -s $serial push qr.png /sdcard/DCIM/qr.png"

# Держим скрипт живым пока эмулятор работает
$proc | Wait-Process
