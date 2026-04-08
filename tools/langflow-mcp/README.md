# Langflow MCP Automation

This directory contains a ready-to-run setup for:
- Playwright MCP browser control (`start-playwright-mcp.ps1`)
- Full Langflow workspace autopipeline (`langflow-pipeline.mjs`)
- Live Watch mode (`WATCH_MODE=on`) with visible browser actions

## 1) One-time install

```powershell
cd C:\work\launcher\tools\langflow-mcp
npm install
npx playwright install chrome
```

## 2) Required environment variables

```powershell
$env:LANGFLOW_BASE_URL = "http://YOUR_SERVER:7860"
$env:WATCH_MODE = "on"   # on=headful/live, off=headless
```

Optional:

```powershell
$env:PLAYWRIGHT_USER_DATA_DIR = "C:\Users\user\.codex\playwright-profile-langflow"
$env:TIMEOUT_ACTION_MS = "10000"
$env:TIMEOUT_NAVIGATION_MS = "60000"
$env:PIPELINE_RETRY_ATTEMPTS = "3"
$env:PLAYWRIGHT_SLOW_MO_MS = "350"
$env:KEEP_OPEN_ON_FAILURE = "1"
```

## 3) MCP server (for Codex tools)

Configured through `~/.codex/config.toml` to run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File C:\work\launcher\tools\langflow-mcp\start-playwright-mcp.ps1
```

Mode switching:
- `WATCH_MODE=on` -> visible browser window
- `WATCH_MODE=off` -> headless mode

## 4) Full autopipeline run

```powershell
cd C:\work\launcher\tools\langflow-mcp
node .\langflow-pipeline.mjs
```

Artifacts are written to `tools/langflow-mcp/artifacts/run-<timestamp>/`:
- `result.json`
- `execution-log.json`
- `screenshots/*.png`
- `fatal-dom-snapshot.html` (on failure)

## 5) Flow template calibration

Template file:
- `flow-template.json` (working copy)
- `flow-template.example.json` (example baseline)

You must calibrate selectors to your Langflow version:
- workspace create buttons/inputs
- node search selectors
- node handle selectors (`sourcePortPattern`, `targetPortPattern`)

Placeholders supported in selector patterns:
- `{{node}}`
- `{{fromNode}}`, `{{fromPort}}`, `{{toNode}}`, `{{toPort}}`

## 6) Smoke checks

```powershell
npx -y @playwright/mcp@latest --help
node --check .\langflow-pipeline.mjs
```
