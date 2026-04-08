param()

$watchMode = if ($env:WATCH_MODE) { $env:WATCH_MODE.ToLower() } else { 'on' }
$headless = -not @('1','true','yes','y','on').Contains($watchMode)

$userDataDir = if ($env:PLAYWRIGHT_USER_DATA_DIR) { $env:PLAYWRIGHT_USER_DATA_DIR } else { Join-Path $HOME '.codex\playwright-profile-langflow' }
$outputDir = if ($env:PLAYWRIGHT_OUTPUT_DIR) { $env:PLAYWRIGHT_OUTPUT_DIR } else { Join-Path (Get-Location) 'tools\langflow-mcp\artifacts\mcp' }
$timeoutAction = if ($env:TIMEOUT_ACTION_MS) { $env:TIMEOUT_ACTION_MS } else { '10000' }
$timeoutNavigation = if ($env:TIMEOUT_NAVIGATION_MS) { $env:TIMEOUT_NAVIGATION_MS } else { '60000' }
$browser = if ($env:PLAYWRIGHT_BROWSER) { $env:PLAYWRIGHT_BROWSER } else { 'chrome' }

New-Item -ItemType Directory -Force -Path $userDataDir | Out-Null
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$args = @(
  '-y',
  '@playwright/mcp@latest',
  '--browser', $browser,
  '--user-data-dir', $userDataDir,
  '--output-dir', $outputDir,
  '--save-session',
  '--timeout-action', $timeoutAction,
  '--timeout-navigation', $timeoutNavigation,
  '--caps', 'vision',
  '--snapshot-mode', 'full',
  '--console-level', 'error'
)

if ($headless) {
  $args += '--headless'
}

if ($env:PLAYWRIGHT_ALLOWED_ORIGINS) {
  $args += @('--allowed-origins', $env:PLAYWRIGHT_ALLOWED_ORIGINS)
}

if ($env:PLAYWRIGHT_ALLOWED_HOSTS) {
  $args += @('--allowed-hosts', $env:PLAYWRIGHT_ALLOWED_HOSTS)
}

if ($env:PLAYWRIGHT_IGNORE_HTTPS_ERRORS -eq '1') {
  $args += '--ignore-https-errors'
}

& npx @args
exit $LASTEXITCODE
