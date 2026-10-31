// Opt-in production Next + real Spring/session/CSRF + disposable PostgreSQL.
// Launch through DevBIntegrationBrowserAcceptance only. All accounts/data are synthetic.
// Verifies that the local integrated Developer-B candidate keeps every navigation entry (F04/F05/F06/F08/F09/F12B)
// together with the frontend UX-hardening fixes, in English and Urdu/RTL, without any write.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.FINSIGHT_BROWSER_BASE_URL || 'http://127.0.0.1:3100';
assert.match(base, /^http:\/\/127\.0\.0\.1:\d+$/);
assert.ok(process.env.FINSIGHT_DB_BASELINE_READY && process.env.FINSIGHT_DB_BASELINE_CAPTURED,
  'Launch through DevBIntegrationBrowserAcceptance so database write probes are installed');
const out = resolve(process.env.BROWSER_ARTIFACT_DIR || '../tmp/devb-integration/real-browser');
mkdirSync(out, { recursive: true });
const checks = [], errors = [], externalRequests = [], requests = [];
const mark = name => { checks.push(name); console.log('PASS', name); };
const password = 'Synthetic-DevB-2026!';
const email = `devb-owner-${Date.now()}@example.test`;
const SIDEBAR = ['/', '/records', '/estimates', '/reports', '/projects', '/samples', '/invoices', '/bills', '/inventory', '/settings'];
const SHEET = ['/projects', '/samples', '/invoices', '/bills', '/inventory', '/estimates', '/reports'];
const browser = await chromium.launch({ headless: true, channel: 'msedge' });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
context.on('request', request => {
  const url = new URL(request.url());
  requests.push({ path: url.pathname, method: request.method() });
  if (/^https?:/.test(url.protocol) && url.hostname !== '127.0.0.1') externalRequests.push(url.origin);
});
const page = await context.newPage();
page.setDefaultTimeout(20000);
page.on('pageerror', error => errors.push(error.message));
page.on('console', message => {
  if (message.type() === 'error' && /Content Security Policy|Refused to|Hydration/i.test(message.text())) errors.push(message.text());
});
page.on('response', response => { if (response.status() >= 500) errors.push(`${response.status()} ${response.url()}`); });
const id = name => page.getByTestId(name);
async function api(path, { method = 'GET', data, status = 200 } = {}) {
  const headers = {};
  if (method !== 'GET') {
    const token = await (await context.request.get(`${base}/api/auth/csrf`)).json();
    headers[token.headerName] = token.token;
  }
  const response = await context.request.fetch(`${base}${path}`, { method, headers, ...(data === undefined ? {} : { data }) });
  assert.equal(response.status(), status, `${method} ${path}: ${await response.text()}`);
  return response.status() === 204 ? null : response.json();
}
async function settled() {
  await page.evaluate(async () => {
    await document.fonts?.ready;
    await Promise.all(document.getAnimations({ subtree: true }).map(animation => animation.finished.catch(() => {})));
    await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
  });
}
async function noOverflow(label) {
  await settled();
  const size = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
  assert.ok(size.document <= size.viewport + 2, `${label}: ${JSON.stringify(size)}`);
}
async function capture(name) { await settled(); await page.screenshot({ path: resolve(out, name) }); }
async function openSheet() {
  await page.locator('button[class*="-mt-5"]').click();
  await id('sheet-scroll-region').waitFor();
  await settled();
}

/** The sheet fits the viewport, scrolls internally, keeps its close button and exposes every action unobstructed. */
async function verifySheet(label, height) {
  await page.setViewportSize({ width: 390, height });
  await page.goto(`${base}/`); await page.locator('button[class*="-mt-5"]').waitFor();
  await openSheet();
  const region = id('sheet-scroll-region');
  const geometry = await region.evaluate(element => {
    const panel = element.parentElement.getBoundingClientRect();
    return { panelTop: panel.top, panelBottom: panel.bottom, panelLeft: panel.left, panelRight: panel.right,
      scrollHeight: element.scrollHeight, clientHeight: element.clientHeight, scrollWidth: element.scrollWidth,
      clientWidth: element.clientWidth, overflowY: getComputedStyle(element).overflowY, innerHeight, innerWidth };
  });
  assert.ok(geometry.panelTop >= -1 && geometry.panelBottom <= geometry.innerHeight + 1, `${label} sheet fits vertically: ${JSON.stringify(geometry)}`);
  assert.ok(geometry.panelLeft >= -1 && geometry.panelRight <= geometry.innerWidth + 1, `${label} sheet fits horizontally: ${JSON.stringify(geometry)}`);
  assert.equal(geometry.overflowY, 'auto');
  assert.ok(geometry.scrollWidth <= geometry.clientWidth + 1, `${label} sheet has no horizontal overflow`);
  const actions = region.locator('button');
  const count = await actions.count();
  const targets = [];
  for (let index = 0; index < count; index++) {
    const action = actions.nth(index);
    await action.scrollIntoViewIfNeeded(); await settled();
    const box = await action.boundingBox();
    assert.ok(box && box.y >= geometry.panelTop - 1 && box.y + box.height <= geometry.innerHeight + 1, `${label} action ${index} visible: ${JSON.stringify(box)}`);
    assert.ok(box.x >= -1 && box.x + box.width <= geometry.innerWidth + 1, `${label} action ${index} horizontally inside`);
    const unobstructed = await action.evaluate(element => {
      const rect = element.getBoundingClientRect();
      const hit = document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2);
      return hit === element || element.contains(hit);
    });
    assert.ok(unobstructed, `${label} action ${index} is reachable, not covered`);
    targets.push((await action.innerText()).split('\n')[0]);
  }
  assert.ok(count >= SHEET.length + 1, `${label}: expected every feature action plus record entry, got ${count}: ${targets}`);
  for (const testId of ['mobile-samples-action', 'mobile-inventory-action', 'mobile-invoices-action', 'mobile-bills-action', 'mobile-reports-action']) {
    assert.equal(await id(testId).count(), 1, `${label} ${testId}`);
  }
  await region.evaluate(element => element.scrollTo({ top: element.scrollHeight }));
  await settled();
  assert.ok(await page.getByRole('button', { name: 'Close sheet' }).isVisible(), `${label} close button stays visible after scrolling`);
  await noOverflow(`${label} with sheet open`);
  return { count, targets, geometry };
}

try {
  await api('/api/auth/register', { method: 'POST', status: 201, data: { email, password, fullName: 'Synthetic Integration Owner' } });
  await page.goto(`${base}/`);
  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill(password);
  const login = page.waitForResponse(response => new URL(response.url()).pathname === '/api/auth/login');
  await page.locator('form button[type="submit"]').click();
  assert.equal((await login).status(), 200);
  const business = await api('/api/businesses', { method: 'POST', status: 201, data: {
    businessName: 'Integrated Candidate Textiles', businessType: 'manufacturing', languagePreference: 'en', whatsappOptIn: false } });
  for (const month of ['2026-08', '2026-09']) await api('/api/records/monthly', { method: 'POST', status: 201, data: {
    month, cashInflow: 10000, cashOutflow: 8000, revenue: 12000, operatingExpenses: 3000, cashBalanceEom: 6000, cogs: 4000,
    receivablesOutstanding: 0, payablesOutstanding: 0, inventoryValue: 2500, loanOutstanding: 0, interestExpense: 0, financingType: 'none' } });
  await api('/api/projects', { method: 'POST', status: 201, data: { code: 'INT-FIXTURE', name: 'Existing project', kind: 'JOB' } });
  await api('/api/dashboard');
  const scoreBefore = await api('/api/scores/latest');
  assert.equal(scoreBefore.methodologyVersion, 'health-score-v1');
  mark('1. Real sign-in with a real active business and pre-existing record, score and project fixtures');

  const baseline = requests.length;
  writeFileSync(process.env.FINSIGHT_DB_BASELINE_READY, 'Fixtures ready.');
  const deadline = Date.now() + 120000;
  while (!existsSync(process.env.FINSIGHT_DB_BASELINE_CAPTURED) && Date.now() < deadline) await delay(100);
  assert.ok(existsSync(process.env.FINSIGHT_DB_BASELINE_CAPTURED), 'Java must capture the database baseline first');
  mark('2. Database baseline and write probes captured before any navigation');

  await page.goto(`${base}/`); await id('header-business-name').waitFor();
  assert.equal((await id('header-business-name').innerText()).trim(), business.businessName ?? 'Integrated Candidate Textiles');
  for (const href of SIDEBAR) assert.ok(await page.locator(`aside a[href="${href}"]`).count() >= 1, `sidebar ${href}`);
  mark('3. Desktop sidebar keeps every canonical, F04, F05, F06, F08, F09 and F12B entry; header shows the active business');

  const offset = () => page.locator('aside').evaluate(aside => {
    const main = document.querySelector('main');
    const shell = main.closest('[class*="md:ms-"]') ?? main.parentElement;
    return { aside: aside.getBoundingClientRect().width, contentStart: shell.getBoundingClientRect().left };
  });
  const expanded = await offset();
  await page.getByRole('button', { name: 'Collapse sidebar' }).click(); await settled();
  const collapsed = await offset();
  assert.ok(Math.abs(expanded.contentStart - expanded.aside) <= 2 && Math.abs(collapsed.contentStart - collapsed.aside) <= 2,
    `content offset follows sidebar: ${JSON.stringify({ expanded, collapsed })}`);
  assert.ok(collapsed.aside < expanded.aside);
  await page.getByRole('button', { name: 'Expand sidebar' }).click(); await settled();
  mark('4. Sidebar collapse and expand move the content offset with the sidebar width');

  for (const href of ['/samples', '/inventory', '/estimates', '/projects/new']) {
    await page.goto(`${base}${href}`); await page.locator('main').waitFor(); await settled();
    await noOverflow(`desktop EN ${href}`);
  }
  mark('5. F09, F08, F06 and F05 routes render on desktop English without overflow');

  const englishTall = await verifySheet('mobile EN 844', 844);
  await capture('sheet-mobile-en-844.png');
  const englishShort = await verifySheet('mobile EN 600', 600);
  assert.ok(englishShort.geometry.scrollHeight > englishShort.geometry.clientHeight, 'At 600px the sheet must scroll internally');
  await capture('sheet-mobile-en-600-scrolled.png');
  mark(`6. Mobile English + sheet: ${englishTall.count} actions fit the viewport, scroll internally at 600px, all reachable, no overflow`);

  await page.keyboard.press('Escape');
  await page.setViewportSize({ width: 390, height: 844 });
  await openSheet();
  await id('mobile-samples-action').click(); await page.waitForURL(`${base}/samples`); await page.locator('main').waitFor();
  await openSheet();
  await id('mobile-inventory-action').click(); await page.waitForURL(`${base}/inventory`); await id('inventory-register').waitFor();
  mark('7. Mobile sheet actions navigate to F09 Samples and F08 Inventory');

  const language = page.waitForResponse(response => response.url().endsWith('/api/profile/language') && response.request().method() === 'PATCH');
  await page.getByRole('button', { name: 'اردو', exact: true }).first().click();
  assert.equal((await language).status(), 200);
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  const urduTall = await verifySheet('mobile UR 844', 844);
  await capture('sheet-mobile-ur-844.png');
  const urduShort = await verifySheet('mobile UR 600', 600);
  assert.ok(urduShort.geometry.scrollHeight > urduShort.geometry.clientHeight, 'At 600px the Urdu sheet must scroll internally');
  assert.ok(urduTall.targets.every(text => /[؀-ۿ]/.test(text) || text.trim() === ''), `Urdu action labels: ${urduTall.targets}`);
  await capture('sheet-mobile-ur-600-scrolled.png');
  mark(`8. Mobile Urdu/RTL + sheet: ${urduTall.count} Urdu actions fit, scroll internally, all reachable, no overflow`);

  await page.keyboard.press('Escape');
  await page.setViewportSize({ width: 390, height: 844 });
  for (const href of ['/settings', '/samples', '/inventory', '/estimates', '/projects', '/invoices', '/reports']) {
    await page.goto(`${base}${href}`); await page.locator('main').waitFor();
    await noOverflow(`mobile UR ${href}`);
  }
  await capture('settings-mobile-ur.png');
  mark('9. Mobile Urdu Settings and every feature register render without horizontal overflow');

  await page.goto(`${base}/projects/new`); await page.locator('main').waitFor(); await settled();
  const arrows = await page.locator('[data-testid="back-to-projects-link"] svg').evaluateAll(nodes => nodes.map(node => {
    const style = getComputedStyle(node);
    return { rotate: style.rotate, transform: style.transform };
  }));
  assert.ok(arrows.length > 0 && arrows.every(arrow => /180deg/.test(arrow.rotate) || (arrow.transform !== 'none' && arrow.transform !== '')),
    `RTL project back arrows rotate: ${JSON.stringify(arrows)}`);
  mark('10. Project back arrows reverse in RTL');

  assert.deepEqual(await api('/api/scores/latest'), scoreBefore);
  const writes = requests.slice(baseline).filter(request => request.method !== 'GET' && !/^\/api\/(auth|profile\/language)/.test(request.path));
  assert.deepEqual(writes, [], 'Navigation and UX checks send no feature or financial writes');
  assert.deepEqual(errors, []); assert.deepEqual(externalRequests, []);
  mark('11. No writes beyond session/language, identical score, no page/hydration/CSP/5xx errors or external requests');
  writeFileSync(resolve(out, 'results.json'), JSON.stringify({
    scope: 'Production Next + real Spring/session/CSRF + disposable PostgreSQL; synthetic accounts/data; no paid providers',
    checks, errors, externalRequests,
    sheet: { englishTall, englishShort, urduTall, urduShort },
  }, null, 2));
  console.log(`RESULT ${checks.length} checks passed`);
} catch (error) {
  await page.screenshot({ path: resolve(out, 'failure.png'), fullPage: true }).catch(() => {});
  writeFileSync(resolve(out, 'failure.txt'), `${error.stack}\n${await page.locator('body').innerText().catch(() => '')}`);
  throw error;
} finally { await browser.close(); }
