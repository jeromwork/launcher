import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_TEMPLATE = path.join(SCRIPT_DIR, 'flow-template.json');
const DEFAULT_ARTIFACTS_DIR = path.join(SCRIPT_DIR, 'artifacts');
const DEFAULT_PROFILE_DIR = path.join(os.homedir(), '.codex', 'playwright-profile-langflow');

function nowStamp() {
  const date = new Date();
  const pad = (v) => String(v).padStart(2, '0');
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
}

function toBool(value, fallback) {
  if (value == null || value === '') return fallback;
  return ['1', 'true', 'on', 'yes', 'y'].includes(String(value).toLowerCase());
}

function toInt(value, fallback) {
  const parsed = Number.parseInt(String(value ?? ''), 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function renderPattern(pattern, values) {
  let out = pattern;
  for (const [key, value] of Object.entries(values)) {
    out = out.replaceAll(`{{${key}}}`, String(value));
  }
  return out;
}

async function mkdirp(dir) {
  await fs.mkdir(dir, { recursive: true });
}

async function readJson(filePath) {
  const raw = await fs.readFile(filePath, 'utf8');
  return JSON.parse(raw);
}

async function exists(filePath) {
  try {
    await fs.access(filePath);
    return true;
  } catch {
    return false;
  }
}

async function withRetry(stepName, attempts, action) {
  let lastError = null;
  for (let i = 1; i <= attempts; i += 1) {
    try {
      return await action(i);
    } catch (error) {
      lastError = error;
      if (i === attempts) break;
    }
  }
  throw new Error(`[${stepName}] failed after ${attempts} attempts: ${lastError?.message ?? lastError}`);
}

async function clickFirstVisible(page, selectors, timeoutMs) {
  for (const selector of selectors ?? []) {
    const locator = page.locator(selector).first();
    if (await locator.isVisible({ timeout: Math.min(timeoutMs, 1500) }).catch(() => false)) {
      await locator.click({ timeout: timeoutMs });
      return selector;
    }
  }
  throw new Error(`No clickable selector found: ${JSON.stringify(selectors)}`);
}

async function fillFirstVisible(page, selectors, value, timeoutMs) {
  for (const selector of selectors ?? []) {
    const locator = page.locator(selector).first();
    if (await locator.isVisible({ timeout: Math.min(timeoutMs, 1500) }).catch(() => false)) {
      await locator.fill(String(value), { timeout: timeoutMs });
      return selector;
    }
  }
  throw new Error(`No input selector found: ${JSON.stringify(selectors)}`);
}

async function isAnyVisible(page, selectors, timeoutMs = 1000) {
  for (const selector of selectors ?? []) {
    const visible = await page.locator(selector).first().isVisible({ timeout: timeoutMs }).catch(() => false);
    if (visible) return selector;
  }
  return null;
}

async function main() {
  const watchMode = String(process.env.WATCH_MODE ?? 'on').toLowerCase();
  const isWatchOn = toBool(watchMode, true);
  const slowMo = toInt(process.env.PLAYWRIGHT_SLOW_MO_MS, isWatchOn ? 350 : 0);
  const retries = toInt(process.env.PIPELINE_RETRY_ATTEMPTS, 3);
  const actionTimeout = toInt(process.env.TIMEOUT_ACTION_MS, 10000);
  const navigationTimeout = toInt(process.env.TIMEOUT_NAVIGATION_MS, 60000);
  const baseUrl = process.env.LANGFLOW_BASE_URL;
  const templatePath = process.env.FLOW_TEMPLATE ? path.resolve(process.env.FLOW_TEMPLATE) : DEFAULT_TEMPLATE;
  const artifactsRoot = process.env.PIPELINE_ARTIFACTS_DIR ? path.resolve(process.env.PIPELINE_ARTIFACTS_DIR) : DEFAULT_ARTIFACTS_DIR;
  const userDataDir = process.env.PLAYWRIGHT_USER_DATA_DIR ? path.resolve(process.env.PLAYWRIGHT_USER_DATA_DIR) : DEFAULT_PROFILE_DIR;

  if (!baseUrl) {
    throw new Error('LANGFLOW_BASE_URL is required');
  }
  if (!(await exists(templatePath))) {
    throw new Error(`Flow template was not found: ${templatePath}`);
  }

  const template = await readJson(templatePath);
  const stamp = nowStamp();
  const runDir = path.join(artifactsRoot, `run-${stamp}`);
  const screenshotsDir = path.join(runDir, 'screenshots');
  await mkdirp(screenshotsDir);
  await mkdirp(userDataDir);

  const executionLog = [];
  let shotIndex = 1;

  const logStep = async (step, status, details = {}) => {
    const record = {
      ts: new Date().toISOString(),
      step,
      status,
      ...details,
    };
    executionLog.push(record);
    await fs.writeFile(path.join(runDir, 'execution-log.json'), JSON.stringify(executionLog, null, 2), 'utf8');
  };

  const screenshot = async (page, name) => {
    const fileName = `${String(shotIndex).padStart(2, '0')}-${name}.png`;
    shotIndex += 1;
    const fullPath = path.join(screenshotsDir, fileName);
    await page.screenshot({ path: fullPath, fullPage: true });
    return fullPath;
  };

  const launchOptions = {
    channel: process.env.PLAYWRIGHT_BROWSER_CHANNEL || 'chrome',
    headless: !isWatchOn,
    slowMo,
    viewport: { width: 1600, height: 1000 },
  };

  let context;
  let page;
  let workspaceName = '';

  const summary = {
    status: 'running',
    watch_mode: isWatchOn ? 'on' : 'off',
    workspace_name: null,
    base_url: baseUrl,
    template_path: templatePath,
    user_data_dir: userDataDir,
    run_dir: runDir,
    screenshots: [],
    export_file: null,
  };

  try {
    await logStep('plan', 'info', {
      message: 'open_base -> session_guard -> workspace_create -> node_add -> edge_connect -> validate_run -> save_export',
    });

    context = await chromium.launchPersistentContext(userDataDir, launchOptions);
    context.setDefaultTimeout(actionTimeout);
    context.setDefaultNavigationTimeout(navigationTimeout);
    page = context.pages()[0] ?? (await context.newPage());

    await withRetry('open_base', retries, async (attempt) => {
      await page.goto(baseUrl, { waitUntil: 'domcontentloaded' });
      await page.waitForLoadState('networkidle', { timeout: navigationTimeout }).catch(() => {});
      const shot = await screenshot(page, `open-base-attempt-${attempt}`);
      summary.screenshots.push(shot);
      await logStep('open_base', 'ok', { attempt, screenshot: shot });
    });

    await withRetry('session_guard', retries, async (attempt) => {
      const loginVisible = await isAnyVisible(page, template.session?.loginRequiredSelectors);
      if (loginVisible) {
        throw new Error(`Login screen detected by selector: ${loginVisible}. Sign in once manually and rerun.`);
      }
      const loggedIn = await isAnyVisible(page, template.session?.loggedInSelectors);
      await logStep('session_guard', 'ok', { attempt, loggedInSelector: loggedIn ?? 'not-detected-but-continued' });
    });

    const wsPrefix = template.workspace?.namePrefix || process.env.WORKSPACE_NAME_TEMPLATE || 'AUTO_';
    workspaceName = `${wsPrefix}${stamp}`;

    await withRetry('workspace_create', retries, async (attempt) => {
      const selectors = template.workspace?.selectors ?? {};
      if (selectors.newWorkspaceButton?.length) {
        const usedSelector = await clickFirstVisible(page, selectors.newWorkspaceButton, actionTimeout);
        await logStep('workspace_create_click', 'ok', { attempt, selector: usedSelector });
      }
      if (selectors.workspaceNameInput?.length) {
        const usedSelector = await fillFirstVisible(page, selectors.workspaceNameInput, workspaceName, actionTimeout);
        await logStep('workspace_create_fill', 'ok', { attempt, selector: usedSelector, workspaceName });
      }
      if (selectors.workspaceCreateSubmit?.length) {
        const usedSelector = await clickFirstVisible(page, selectors.workspaceCreateSubmit, actionTimeout);
        await logStep('workspace_create_submit', 'ok', { attempt, selector: usedSelector });
      }
      const shot = await screenshot(page, `workspace-created-attempt-${attempt}`);
      summary.screenshots.push(shot);
      await logStep('workspace_create', 'ok', { attempt, screenshot: shot, workspaceName });
    });

    const canvas = template.canvas ?? {};
    for (let i = 0; i < (template.nodes ?? []).length; i += 1) {
      const node = template.nodes[i];
      const nodeName = node.name;
      await withRetry(`node_add_${nodeName}`, retries, async (attempt) => {
        if (canvas.addNodeButton?.length) {
          await clickFirstVisible(page, canvas.addNodeButton, actionTimeout);
        }
        if (canvas.nodeSearchInput?.length) {
          await fillFirstVisible(page, canvas.nodeSearchInput, nodeName, actionTimeout);
          await page.keyboard.press('Enter');
        }

        if (node.position && canvas.canvasNodeByTitlePattern) {
          const nodeSelector = renderPattern(canvas.canvasNodeByTitlePattern, { node: nodeName });
          const nodeLocator = page.locator(nodeSelector).first();
          await nodeLocator.waitFor({ state: 'visible', timeout: actionTimeout });
          const nodeBox = await nodeLocator.boundingBox();
          const canvasLocator = page.locator(canvas.selector || '.react-flow').first();
          await canvasLocator.waitFor({ state: 'visible', timeout: actionTimeout });
          const canvasBox = await canvasLocator.boundingBox();
          if (nodeBox && canvasBox) {
            const startX = nodeBox.x + nodeBox.width / 2;
            const startY = nodeBox.y + nodeBox.height / 2;
            const endX = canvasBox.x + node.position.x;
            const endY = canvasBox.y + node.position.y;
            await page.mouse.move(startX, startY);
            await page.mouse.down();
            await page.mouse.move(endX, endY, { steps: 14 });
            await page.mouse.up();
          }
        }

        const shot = await screenshot(page, `node-${i + 1}-${nodeName.replace(/\s+/g, '-').toLowerCase()}-attempt-${attempt}`);
        summary.screenshots.push(shot);
        await logStep('node_add', 'ok', { attempt, node: nodeName, screenshot: shot });
      });
    }

    for (let i = 0; i < (template.edges ?? []).length; i += 1) {
      const edge = template.edges[i];
      await withRetry(`edge_connect_${i + 1}`, retries, async (attempt) => {
        if (!canvas.sourcePortPattern || !canvas.targetPortPattern) {
          throw new Error('sourcePortPattern/targetPortPattern are required in template.canvas to connect edges');
        }
        const sourceSelector = renderPattern(canvas.sourcePortPattern, edge);
        const targetSelector = renderPattern(canvas.targetPortPattern, edge);
        const source = page.locator(sourceSelector).first();
        const target = page.locator(targetSelector).first();
        await source.waitFor({ state: 'visible', timeout: actionTimeout });
        await target.waitFor({ state: 'visible', timeout: actionTimeout });
        const sourceBox = await source.boundingBox();
        const targetBox = await target.boundingBox();
        if (!sourceBox || !targetBox) {
          throw new Error(`Cannot compute source/target boxes for edge ${i + 1}`);
        }
        const sx = sourceBox.x + sourceBox.width / 2;
        const sy = sourceBox.y + sourceBox.height / 2;
        const tx = targetBox.x + targetBox.width / 2;
        const ty = targetBox.y + targetBox.height / 2;
        await page.mouse.move(sx, sy);
        await page.mouse.down();
        await page.mouse.move(tx, ty, { steps: 16 });
        await page.mouse.up();

        const shot = await screenshot(page, `edge-${i + 1}-attempt-${attempt}`);
        summary.screenshots.push(shot);
        await logStep('edge_connect', 'ok', { attempt, edge, screenshot: shot });
      });
    }

    await withRetry('validate_run', retries, async (attempt) => {
      if (template.run?.runButton?.length) {
        const usedSelector = await clickFirstVisible(page, template.run.runButton, actionTimeout);
        await logStep('validate_run_click', 'ok', { attempt, selector: usedSelector });
      }
      const successSelector = await isAnyVisible(page, template.run?.runSuccessSelectors, actionTimeout);
      const shot = await screenshot(page, `validate-run-attempt-${attempt}`);
      summary.screenshots.push(shot);
      await logStep('validate_run', 'ok', {
        attempt,
        runSuccessSelector: successSelector ?? 'not-detected',
        screenshot: shot,
      });
    });

    await withRetry('save_export', retries, async (attempt) => {
      if (template.run?.saveButton?.length) {
        const usedSelector = await clickFirstVisible(page, template.run.saveButton, actionTimeout);
        await logStep('save_click', 'ok', { attempt, selector: usedSelector });
      }
      if (toBool(process.env.ENABLE_EXPORT, false) && template.run?.exportButton?.length) {
        const usedSelector = await clickFirstVisible(page, template.run.exportButton, actionTimeout);
        await logStep('export_click', 'ok', { attempt, selector: usedSelector });
      }
      const shot = await screenshot(page, `save-export-attempt-${attempt}`);
      summary.screenshots.push(shot);
      await logStep('save_export', 'ok', { attempt, screenshot: shot });
    });

    summary.status = 'success';
    summary.workspace_name = workspaceName;
    await logStep('pipeline', 'success', { workspaceName });
  } catch (error) {
    summary.status = 'failed';
    summary.workspace_name = workspaceName || null;
    await logStep('pipeline', 'failed', { error: String(error?.message ?? error) });
    if (page) {
      const failShot = await screenshot(page, 'fatal-error').catch(() => null);
      if (failShot) summary.screenshots.push(failShot);
      const htmlDumpPath = path.join(runDir, 'fatal-dom-snapshot.html');
      await fs.writeFile(htmlDumpPath, await page.content(), 'utf8').catch(() => {});
    }
    throw error;
  } finally {
    await fs.writeFile(path.join(runDir, 'result.json'), JSON.stringify(summary, null, 2), 'utf8');
    if (context) {
      if (summary.status === 'failed' && isWatchOn && toBool(process.env.KEEP_OPEN_ON_FAILURE, true)) {
        await logStep('watch_mode', 'info', { message: 'Browser kept open due to failure in WATCH_MODE=on' });
      } else {
        await context.close().catch(() => {});
      }
    }
  }

  process.stdout.write(`${JSON.stringify(summary, null, 2)}\n`);
}

main().catch((error) => {
  process.stderr.write(`Langflow pipeline failed: ${error.message}\n`);
  process.exitCode = 1;
});
