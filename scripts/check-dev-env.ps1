# check-dev-env.ps1
#
# Валидатор dev-окружения для проекта launcher.
# Проверяет: Git, JDK, Node, npm, firebase-tools, wrangler, login states,
# Firebase project access, Cloudflare account access.
#
# Запуск: pwsh .\scripts\check-dev-env.ps1
#
# Per docs/dev/dev-environment.md.

$ErrorActionPreference = "Continue"
$ProgressPreference = "SilentlyContinue"

$ok = 0
$fail = 0
$warn = 0

function Check {
    param([string]$Label, [scriptblock]$Test, [string]$FixHint)
    Write-Host -NoNewline "  "
    try {
        $result = & $Test
        if ($result) {
            Write-Host "OK  " -ForegroundColor Green -NoNewline
            Write-Host "$Label" -NoNewline
            if ($result -is [string] -and $result.Length -gt 0) { Write-Host " ($result)" -ForegroundColor DarkGray } else { Write-Host "" }
            $script:ok++
        } else {
            Write-Host "FAIL" -ForegroundColor Red -NoNewline
            Write-Host " $Label"
            if ($FixHint) { Write-Host "       fix: $FixHint" -ForegroundColor DarkYellow }
            $script:fail++
        }
    } catch {
        Write-Host "FAIL" -ForegroundColor Red -NoNewline
        Write-Host " $Label ($($_.Exception.Message))"
        if ($FixHint) { Write-Host "       fix: $FixHint" -ForegroundColor DarkYellow }
        $script:fail++
    }
}

function Warn {
    param([string]$Label, [string]$Hint)
    Write-Host "  " -NoNewline
    Write-Host "WARN" -ForegroundColor Yellow -NoNewline
    Write-Host " $Label"
    if ($Hint) { Write-Host "       hint: $Hint" -ForegroundColor DarkGray }
    $script:warn++
}

Write-Host ""
Write-Host "=== launcher dev-env check ===" -ForegroundColor Cyan
Write-Host ""

# System tools
Write-Host "[system tools]" -ForegroundColor Cyan
Check "Git installed" {
    $v = (git --version 2>$null) -replace 'git version ', ''
    if ($v) { return $v }
    return $null
} "install Git from https://git-scm.com/download/win"

Check "JDK 17+ available (PATH or JAVA_HOME)" {
    # Check 1: PATH `java`
    $out = (java -version 2>&1 | Out-String)
    if ($out -match '"(\d+)\.[\d\._]+"') {
        $major = [int]$matches[1]
        if ($major -ge 17) { return "PATH: $major.x" }
    }
    # Check 2: JAVA_HOME
    if ($env:JAVA_HOME) {
        $javaInHome = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $javaInHome) {
            $out2 = (& $javaInHome -version 2>&1 | Out-String)
            if ($out2 -match '"(\d+)\.[\d\._]+"') {
                $major2 = [int]$matches[1]
                if ($major2 -ge 17) { return "JAVA_HOME: $major2.x" }
            }
        }
    }
    # Check 3: Android Studio bundled JDK (typical path)
    $studioJdkPath = "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\java.exe"
    if (Test-Path $studioJdkPath) {
        $out3 = (& $studioJdkPath -version 2>&1 | Out-String)
        if ($out3 -match '"(\d+)\.[\d\._]+"') {
            $major3 = [int]$matches[1]
            if ($major3 -ge 17) { return "Android Studio bundled: $major3.x" }
        }
    }
    return $null
} "install JDK 17 from https://adoptium.net/ and set JAVA_HOME, OR ensure Android Studio bundled JBR ≥17"

Check "Node.js LTS 20+" {
    $v = (node --version 2>$null) -replace 'v', ''
    if (-not $v) { return $null }
    $major = [int]($v -split '\.' | Select-Object -First 1)
    if ($major -ge 20) { return "v$v" } else { return $false }
} "install Node.js LTS from https://nodejs.org/"

Check "npm" {
    $v = npm --version 2>$null
    if ($v) { return "v$v" }
    return $null
} "comes with Node.js"

Write-Host ""

# CLI tools
Write-Host "[CLI tools]" -ForegroundColor Cyan
Check "firebase-tools installed" {
    $v = firebase --version 2>$null
    if ($v) { return "v$v" }
    return $null
} "npm install -g firebase-tools"

Check "wrangler installed" {
    $out = (wrangler --version 2>&1 | Out-String)
    # wrangler --version output is just the version number on a line, possibly with " ⛅️ wrangler" prefix
    if ($out -match '(\d+\.\d+\.\d+)') { return "v$($matches[1])" }
    return $null
} "npm install -g wrangler"

Write-Host ""

# Login states
Write-Host "[cloud authentication]" -ForegroundColor Cyan
Check "firebase login" {
    $out = firebase login:list 2>$null | Out-String
    if ($out -match 'g\.jeromwork@gmail\.com') { return "g.jeromwork@gmail.com" }
    if ($out -match '@') { return $false }  # logged in but wrong account
    return $null
} "firebase login (browser opens, choose g.jeromwork@gmail.com)"

Check "wrangler login" {
    $out = wrangler whoami 2>$null | Out-String
    if ($out -match 'gpt1\.jeromwork@gmail\.com') { return "gpt1.jeromwork@gmail.com" }
    if ($out -match 'logged in') { return $false }
    return $null
} "wrangler login (browser opens, choose gpt1.jeromwork@gmail.com)"

Write-Host ""

# Cloud project access
Write-Host "[cloud project access]" -ForegroundColor Cyan
Check "Firebase project 'launcher-old-dev' accessible" {
    $out = firebase projects:list 2>$null | Out-String
    if ($out -match 'launcher-old-dev') { return "found" }
    return $null
} "verify g.jeromwork@gmail.com has access to launcher-old-dev project"

Check "Cloudflare account ID matches" {
    $out = wrangler whoami 2>$null | Out-String
    if ($out -match 'c8f9c8c59e930e0283d713b91c01fb13') { return "match" }
    if ($out -match '[a-f0-9]{32}') { return $false }
    return $null
} "verify wrangler login points to correct account (c8f9c8c59e930e0283d713b91c01fb13)"

Write-Host ""

# Repo state
Write-Host "[repo state]" -ForegroundColor Cyan
$repoRoot = git rev-parse --show-toplevel 2>$null
if ($repoRoot) {
    Check "app/google-services.json present (dev)" {
        if (Test-Path "$repoRoot/app/google-services.json") { return "found" }
        return $null
    } "git checkout app/google-services.json"

    Check "firestore.rules present" {
        if (Test-Path "$repoRoot/firestore.rules") { return "found" }
        return $null
    } "git checkout firestore.rules"

    Check ".firebaserc points to launcher-old-dev" {
        $c = Get-Content "$repoRoot/.firebaserc" -Raw -ErrorAction SilentlyContinue
        if ($c -match 'launcher-old-dev') { return "match" }
        return $null
    } "verify .firebaserc content"

    Check "push-worker/wrangler.toml present" {
        if (Test-Path "$repoRoot/push-worker/wrangler.toml") { return "found" }
        return $null
    } "ensure push-worker/ is scaffolded (Phase 5 of spec 007)"
} else {
    Warn "Not inside a git repo -- skipping repo state checks"
}

Write-Host ""

# Optional / informational
Write-Host "[optional]" -ForegroundColor Cyan
if (Get-Command gradle -ErrorAction SilentlyContinue) {
    Check "Gradle on PATH (optional -- wrapper preferred)" { return "yes" } ""
} else {
    Warn "Gradle not on PATH -- use ./gradlew (wrapper) для всех команд"
}

if (Get-Command adb -ErrorAction SilentlyContinue) {
    Check "ADB on PATH (for emulator workflow)" { return "yes" } ""
} else {
    Warn "ADB not on PATH -- set PATH to include Android SDK platform-tools (для emulator skill)"
}

Write-Host ""

# Summary
Write-Host "=== Summary ===" -ForegroundColor Cyan
Write-Host "  $ok OK, $fail FAIL, $warn WARN"
Write-Host ""
if ($fail -gt 0) {
    Write-Host "FAIL -- fix the issues above and re-run." -ForegroundColor Red
    exit 1
} else {
    Write-Host "PASS -- dev environment ready." -ForegroundColor Green
    exit 0
}
