#Requires -Version 5.1
<#
.SYNOPSIS
    Sets up Rust + cargo-ndk + UniFFI + Android Rust targets on a Windows dev machine
    for the F-CRYPTO wave (TASK-122 crypto-ffi module).

.DESCRIPTION
    Idempotent — safe to run multiple times. Only installs what is missing.

    Steps:
      1. Verify prerequisites (git, Android Studio installed, MSVC Build Tools).
      2. Detect Android NDK, export ANDROID_NDK_HOME to user env.
      3. Install rustup + Rust stable (if missing) or update.
      4. Install 4 Android targets (aarch64, armv7, i686, x86_64).
      5. Install cargo-ndk + uniffi-bindgen (via cargo install --locked).
      6. Verify all tools are on PATH and reachable.

    Requires: Windows 10/11, PowerShell 5+, Android Studio installed,
              internet connection (~1.5 GB download total).

    Does NOT require admin except one optional case: installing MSVC Build Tools
    if absent (script will prompt).

.EXAMPLE
    .\scripts\setup-rust-android.ps1
    # Runs full setup, ~15 minutes on fresh machine, ~30 seconds on already-set-up machine.

.EXAMPLE
    .\scripts\setup-rust-android.ps1 -Verbose
    # Full verbose output for troubleshooting.

.NOTES
    Owner-authored 2026-07-13 for TASK-122. Companion to skill `rust-android-setup`.
    Do NOT run inside WSL — this script is Windows-native. Use bash setup on Linux/Mac.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'  # Speeds up Invoke-WebRequest

# --- Logging helpers -----------------------------------------------------------

function Write-Header {
    param([string]$Text)
    Write-Host ""
    Write-Host "==== $Text ====" -ForegroundColor Cyan
}

function Write-Ok {
    param([string]$Text)
    Write-Host "  [OK] $Text" -ForegroundColor Green
}

function Write-Skip {
    param([string]$Text)
    Write-Host "  [SKIP] $Text" -ForegroundColor DarkGray
}

function Write-Do {
    param([string]$Text)
    Write-Host "  [..] $Text" -ForegroundColor Yellow
}

function Write-Fail {
    param([string]$Text)
    Write-Host "  [FAIL] $Text" -ForegroundColor Red
}

function Exit-WithError {
    param([string]$Text)
    Write-Fail $Text
    Write-Host ""
    Write-Host "Setup aborted. See error above for next steps." -ForegroundColor Red
    exit 1
}

# --- Environment helpers -------------------------------------------------------

function Add-ToUserPath {
    param([string]$NewPath)
    $current = [Environment]::GetEnvironmentVariable('Path', 'User')
    if ($current -notlike "*$NewPath*") {
        $updated = if ([string]::IsNullOrEmpty($current)) { $NewPath } else { "$current;$NewPath" }
        [Environment]::SetEnvironmentVariable('Path', $updated, 'User')
        $env:Path = "$NewPath;$env:Path"
        Write-Ok "Added to user PATH: $NewPath"
    } else {
        Write-Skip "Already on user PATH: $NewPath"
    }
}

function Set-UserEnvVar {
    param([string]$Name, [string]$Value)
    [Environment]::SetEnvironmentVariable($Name, $Value, 'User')
    Set-Item -Path "Env:$Name" -Value $Value
    Write-Ok "Set user env: $Name = $Value"
}

function Test-CommandExists {
    param([string]$Cmd)
    $null -ne (Get-Command $Cmd -ErrorAction SilentlyContinue)
}

# --- Step 1: Prerequisites -----------------------------------------------------

Write-Header "Step 1 of 6: Verify prerequisites"

if ($IsLinux -or $IsMacOS) {
    Exit-WithError "This script is Windows-only. Use the standard rustup.sh on Linux/macOS."
}

if (-not (Test-CommandExists 'git')) {
    Exit-WithError "git not found on PATH. Install Git for Windows: https://git-scm.com/download/win"
}
Write-Ok "git present: $((git --version) -split '\s+' | Select-Object -Last 1)"

$androidStudioFound = $false
$candidates = @(
    "$env:LOCALAPPDATA\Google\AndroidStudio*"
    "$env:ProgramFiles\Android\Android Studio"
    "${env:ProgramFiles(x86)}\Android\Android Studio"
)
foreach ($c in $candidates) {
    if (Get-ChildItem -Path $c -ErrorAction SilentlyContinue) {
        $androidStudioFound = $true
        break
    }
}
if (-not $androidStudioFound) {
    Exit-WithError "Android Studio not detected. Install from https://developer.android.com/studio then re-run."
}
Write-Ok "Android Studio detected"

# MSVC Build Tools check — cargo needs a linker (link.exe or clang).
$msvcFound = Test-CommandExists 'link' -or `
    (Get-ChildItem "$env:ProgramFiles\Microsoft Visual Studio\*\*\VC\Tools\MSVC" -ErrorAction SilentlyContinue) -or `
    (Get-ChildItem "${env:ProgramFiles(x86)}\Microsoft Visual Studio\*\*\VC\Tools\MSVC" -ErrorAction SilentlyContinue)

if (-not $msvcFound) {
    Write-Do "MSVC Build Tools not detected. cargo requires them to link Windows binaries."
    $consent = Read-Host "Install MSVC Build Tools now via winget (may prompt for admin)? [Y/n]"
    if ([string]::IsNullOrEmpty($consent) -or $consent -match '^[Yy]') {
        if (-not (Test-CommandExists 'winget')) {
            Exit-WithError "winget not available. Install manually: https://visualstudio.microsoft.com/downloads/#build-tools-for-visual-studio-2022"
        }
        Write-Do "Installing MSVC Build Tools (this may take 5-10 minutes)..."
        winget install --id Microsoft.VisualStudio.2022.BuildTools --silent --accept-source-agreements --accept-package-agreements `
            --override "--wait --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"
        if ($LASTEXITCODE -ne 0) {
            Exit-WithError "winget install failed (exit $LASTEXITCODE). Install manually and re-run."
        }
        Write-Ok "MSVC Build Tools installed. You may need to restart PowerShell for PATH to refresh."
    } else {
        Exit-WithError "MSVC Build Tools required for cargo on Windows. Aborting."
    }
} else {
    Write-Ok "MSVC Build Tools detected"
}

# --- Step 2: Android NDK -------------------------------------------------------

Write-Header "Step 2 of 6: Detect Android NDK"

$androidHome = $env:ANDROID_HOME
if ([string]::IsNullOrEmpty($androidHome)) {
    $androidHome = $env:ANDROID_SDK_ROOT
}
if ([string]::IsNullOrEmpty($androidHome)) {
    $defaultSdk = "$env:LOCALAPPDATA\Android\Sdk"
    if (Test-Path $defaultSdk) {
        $androidHome = $defaultSdk
        Write-Ok "ANDROID_HOME auto-detected: $androidHome"
        Set-UserEnvVar -Name 'ANDROID_HOME' -Value $androidHome
    }
}

if ([string]::IsNullOrEmpty($androidHome) -or -not (Test-Path $androidHome)) {
    Exit-WithError "Android SDK not found. Set ANDROID_HOME env var or install SDK via Android Studio → SDK Manager."
}

$ndkRoot = Join-Path $androidHome 'ndk'
if (-not (Test-Path $ndkRoot)) {
    Exit-WithError "Android NDK not installed at '$ndkRoot'. Open Android Studio → Tools → SDK Manager → SDK Tools → check 'NDK (Side by side)' → Apply. Then re-run this script."
}

$ndkVersions = Get-ChildItem $ndkRoot -Directory | Sort-Object Name -Descending
if ($ndkVersions.Count -eq 0) {
    Exit-WithError "NDK directory exists but no versions installed. Install via Android Studio SDK Manager."
}

$latestNdk = $ndkVersions[0].FullName
Set-UserEnvVar -Name 'ANDROID_NDK_HOME' -Value $latestNdk

# --- Step 3: rustup + Rust stable ---------------------------------------------

Write-Header "Step 3 of 6: Install / update Rust"

if (Test-CommandExists 'rustup') {
    Write-Skip "rustup already installed. Updating..."
    rustup update stable
    if ($LASTEXITCODE -ne 0) { Exit-WithError "rustup update failed (exit $LASTEXITCODE)" }
} else {
    Write-Do "Downloading rustup-init.exe..."
    $rustupPath = Join-Path $env:TEMP 'rustup-init.exe'
    Invoke-WebRequest -Uri 'https://win.rustup.rs/x86_64' -OutFile $rustupPath
    Write-Do "Running rustup-init (this installs ~500 MB into $env:USERPROFILE\.cargo)..."
    & $rustupPath -y --default-toolchain stable --profile minimal
    if ($LASTEXITCODE -ne 0) { Exit-WithError "rustup-init failed (exit $LASTEXITCODE)" }
    Remove-Item $rustupPath -Force
    Add-ToUserPath -NewPath "$env:USERPROFILE\.cargo\bin"
}

Write-Ok "rustc: $((rustc --version))"
Write-Ok "cargo: $((cargo --version))"

# --- Step 4: Android targets ---------------------------------------------------

Write-Header "Step 4 of 6: Install Android Rust targets"

$targets = @(
    'aarch64-linux-android',
    'armv7-linux-androideabi',
    'i686-linux-android',
    'x86_64-linux-android'
)

$installed = (rustup target list --installed) -split "`n" | ForEach-Object { $_.Trim() }
foreach ($t in $targets) {
    if ($installed -contains $t) {
        Write-Skip "target $t already installed"
    } else {
        Write-Do "Adding target $t..."
        rustup target add $t
        if ($LASTEXITCODE -ne 0) { Exit-WithError "rustup target add $t failed" }
        Write-Ok "target $t installed"
    }
}

# --- Step 5: cargo-ndk + uniffi-bindgen ---------------------------------------

Write-Header "Step 5 of 6: Install cargo-ndk + uniffi-bindgen"

if (Test-CommandExists 'cargo-ndk') {
    Write-Skip "cargo-ndk already installed: $((cargo ndk --version))"
} else {
    Write-Do "Installing cargo-ndk (~2-3 min)..."
    cargo install cargo-ndk --locked
    if ($LASTEXITCODE -ne 0) { Exit-WithError "cargo install cargo-ndk failed" }
    Write-Ok "cargo-ndk installed: $((cargo ndk --version))"
}

if (Test-CommandExists 'uniffi-bindgen') {
    Write-Skip "uniffi-bindgen already installed: $((uniffi-bindgen --version))"
} else {
    Write-Do "Installing uniffi-bindgen (~2-3 min)..."
    cargo install uniffi-bindgen --locked
    if ($LASTEXITCODE -ne 0) { Exit-WithError "cargo install uniffi-bindgen failed" }
    Write-Ok "uniffi-bindgen installed: $((uniffi-bindgen --version))"
}

# --- Step 6: Verify ------------------------------------------------------------

Write-Header "Step 6 of 6: Verify"

$checks = @{
    'rustc'          = { rustc --version }
    'cargo'          = { cargo --version }
    'cargo-ndk'      = { cargo ndk --version }
    'uniffi-bindgen' = { uniffi-bindgen --version }
}
$allOk = $true
foreach ($name in $checks.Keys) {
    try {
        $result = & $checks[$name]
        Write-Ok "$name -> $result"
    } catch {
        Write-Fail "$name check failed: $_"
        $allOk = $false
    }
}

$envNdk = [Environment]::GetEnvironmentVariable('ANDROID_NDK_HOME', 'User')
if ([string]::IsNullOrEmpty($envNdk)) {
    Write-Fail "ANDROID_NDK_HOME not set in user env"
    $allOk = $false
} else {
    Write-Ok "ANDROID_NDK_HOME (user env) = $envNdk"
}

$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
if ($userPath -like "*$env:USERPROFILE\.cargo\bin*") {
    Write-Ok "~/.cargo/bin on user PATH"
} else {
    Write-Fail "~/.cargo/bin missing from user PATH"
    $allOk = $false
}

Write-Host ""
if ($allOk) {
    Write-Host "==== Rust Android toolchain ready ====" -ForegroundColor Green
    Write-Host ""
    Write-Host "  Next steps for TASK-122:" -ForegroundColor White
    Write-Host "    1. Restart PowerShell (to pick up updated PATH/env)."
    Write-Host "    2. Verify: rustc --version; cargo ndk --version; uniffi-bindgen --version"
    Write-Host "    3. When TASK-122 lands: cd to repo root, run ./gradlew :crypto-ffi:build"
    Write-Host ""
    exit 0
} else {
    Write-Host "==== Setup partially completed — see FAIL lines above ====" -ForegroundColor Red
    exit 1
}
