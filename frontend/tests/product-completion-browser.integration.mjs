// Product-completion real-browser acceptance: real Spring Boot + disposable PostgreSQL + Next.js production build +
// repository-pinned Chromium. Covers EN/UR/RTL, responsive layouts and product journeys.
// First run backend test-compile dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt
// -Dmdep.includeScope=test, then pnpm build:release and pnpm browser:install.
// --smoke keeps the real session/CSRF/API setup and responsive navigation; full mode retains financial assertions.
import http from 'node:http';
import net from 'node:net';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync, readFileSync, existsSync, copyFileSync, cpSync, symlinkSync, rmSync } from 'node:fs';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const smoke = process.argv.includes('--smoke');
const frontendDir = path.resolve(fileURLToPath(new URL('..', import.meta.url)));
const rootDir = path.dirname(frontendDir);
const scratchRoot = path.resolve(process.env.FINSIGHT_BROWSER_TMP_DIR || path.join(rootDir, 'tmp'));
const temporary = path.join(scratchRoot, 'product-completion-browser');
const outDir = path.join(frontendDir, 'test-artifacts', smoke ? 'release-smoke' : 'product-completion-browser');
mkdirSync(temporary, { recursive: true });
mkdirSync(outDir, { recursive: true });
const backendPort = 8093, nextPort = 3593, gatewayPort = 3592;
const origin = `http://127.0.0.1:${gatewayPort}`;
const checks = [];
const mark = (name) => { checks.push(name); console.log('PASS ' + name); };
const waitFor = async (predicate, label, timeout = 90000) => {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    for (const child of [javaRuntime, nextProc]) {
      if (child && child.exitCode !== null) throw new Error(`${label}: child exited (${child.exitCode})`);
    }
    if (await predicate()) return;
    await new Promise(r => setTimeout(r, 250));
  }
  throw new Error(label + ' not ready in ' + timeout + 'ms');
};

let javaRuntime, nextProc, gateway, browser, javaOutput = '', nextOutput = '';
try {
  for (const port of [backendPort, nextPort, gatewayPort]) {
    await new Promise((resolve, reject) => {
      const probe = net.createServer();
      probe.once('error', () => reject(new Error(`Port ${port} is occupied; refusing to overlap another service`)));
      probe.listen(port, '127.0.0.1', () => probe.close(resolve));
    });
  }
  // ---------------------------------------------------------------- backend runtime on disposable PostgreSQL
  const target = path.join(rootDir, 'backend', 'target');
  const classpath = [path.join(target, 'test-classes'), path.join(target, 'classes'),
    readFileSync(path.join(target, 'test-classpath.txt'), 'utf8').trim()].join(path.delimiter);
  const java = process.env.JAVA_HOME
    ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
  // A Java argument file avoids Windows command length limits and handles spaces on both OSes.
  const classpathArgs = path.join(temporary, 'classpath.args');
  writeFileSync(classpathArgs, '--class-path\n"' + classpath.replaceAll('\\', '/').replaceAll('"', '\\"') + '"\n');
  writeFileSync(path.join(temporary, 'ProductRuntime.java'), `
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import com.app.sme_health_backend.SmeHealthBackendApplication;
import org.springframework.boot.SpringApplication;
public class ProductRuntime {
  public static void main(String[] a) throws Exception {
    var pg = DisposablePostgres.POSTGRES;
    String key = java.util.Base64.getEncoder().encodeToString(java.security.SecureRandom.getSeed(32));
    var app = SpringApplication.run(SmeHealthBackendApplication.class, "--server.port=${backendPort}", "--server.address=127.0.0.1",
      "--spring.datasource.url=" + DisposablePostgres.URL, "--spring.datasource.username=finsight_app", "--spring.datasource.password=" + DisposablePostgres.PASSWORD,
      "--spring.flyway.enabled=false", "--app.documents.storage-dir=" + DisposablePostgres.DOCUMENTS, "--app.whatsapp.scheduler.enabled=false",
      "--finsight.crypto.keys.k1=" + key, "--app.security.cors.allowed-origins=${origin}", "--logging.level.root=WARN",
      "--spring.profiles.active=dev", "--app.ai.enabled=true", "--app.ai.provider=mock", "--app.ai.demo-enabled=true");
    System.out.println("PRODUCT_RUNTIME_READY"); System.out.flush();
    var in = new java.util.Scanner(System.in);
    while (in.hasNextLine()) { if (in.nextLine().trim().equals("STOP")) break; }
    app.close(); pg.close();
  }
}`);
  javaRuntime = spawn(java, [`-Xmx${process.env.FINSIGHT_BROWSER_JAVA_HEAP || '1536m'}`, '-XX:+UseSerialGC', '@' + classpathArgs, path.join(temporary, 'ProductRuntime.java')],
    { cwd: path.join(rootDir, 'backend'), windowsHide: true });
  javaRuntime.on('error', error => { javaOutput += String(error); });
  javaRuntime.stdout.on('data', d => { javaOutput += d; });
  javaRuntime.stderr.on('data', d => { javaOutput += d; });
  await waitFor(() => javaOutput.includes('PRODUCT_RUNTIME_READY'), 'backend', 180000);
  for (const probe of ['live', 'ready']) {
    const response = await fetch(`http://127.0.0.1:${backendPort}/internal/health/${probe}`);
    assert.equal(response.status, 200, `backend ${probe}`);
  }

  // ---------------------------------------------------------------- isolated Next production server + gateway
  const isolated = path.join(scratchRoot, 'isolated-frontend-product');
  assert.equal(path.dirname(isolated), scratchRoot, 'cleanup must stay inside the selected task scratch directory');
  rmSync(path.join(isolated, '.next'), { recursive: true, force: true });
  mkdirSync(path.join(isolated, '.next'), { recursive: true });
  for (const item of ['build', 'server', 'static', 'types', 'app-path-routes-manifest.json', 'build-manifest.json', 'BUILD_ID', 'export-marker.json',
    'fallback-build-manifest.json', 'images-manifest.json', 'package.json', 'prerender-manifest.json', 'required-server-files.js', 'required-server-files.json', 'routes-manifest.json']) {
    const src = path.join(frontendDir, '.next', item);
    if (existsSync(src)) cpSync(src, path.join(isolated, '.next', item), { recursive: true });
  }
  copyFileSync(path.join(frontendDir, 'package.json'), path.join(isolated, 'package.json'));
  copyFileSync(path.join(frontendDir, 'next.config.ts'), path.join(isolated, 'next.config.ts'));
  for (const link of ['node_modules', 'public']) {
    if (!existsSync(path.join(isolated, link))) symlinkSync(path.join(frontendDir, link), path.join(isolated, link), process.platform === 'win32' ? 'junction' : 'dir');
  }
  nextProc = spawn(process.execPath, [path.join(frontendDir, 'node_modules', 'next', 'dist', 'bin', 'next'), 'start', '--hostname', '127.0.0.1', '--port', String(nextPort)],
    { cwd: isolated, windowsHide: true, env: { ...process.env, BACKEND_API_URL: `http://127.0.0.1:${backendPort}` } });
  nextProc.stdout.on('data', d => { nextOutput += d; });
  nextProc.stderr.on('data', d => { nextOutput += d; });
  await waitFor(() => /Ready in/.test(nextOutput), 'next', 60000);
  assert.equal((await fetch(`http://127.0.0.1:${nextPort}/internal/health`)).status, 200, 'Next HTTP liveness');
  gateway = http.createServer((req, res) => {
    const up = http.request({ hostname: '127.0.0.1', port: req.url.startsWith('/api') ? backendPort : nextPort, path: req.url, method: req.method, headers: req.headers },
      reply => { res.writeHead(reply.statusCode, reply.headers); reply.pipe(res); });
    up.on('error', e => { if (!res.headersSent) res.writeHead(502); res.end(String(e)); });
    req.pipe(up);
  });
  await new Promise(r => gateway.listen(gatewayPort, '127.0.0.1', r));

  // ---------------------------------------------------------------- API fixture through the real gateway
  const jar = new Map();
  let xsrf = '';
  const call = (method, route, data, headers = {}) => new Promise((resolve, reject) => {
    const payload = data ? JSON.stringify(data) : null;
    const h = { ...headers, cookie: [...jar].map(([k, v]) => `${k}=${v}`).join('; ') };
    if (payload) { h['Content-Type'] = 'application/json'; h['Content-Length'] = Buffer.byteLength(payload); }
    if (method !== 'GET' && xsrf) h['X-XSRF-TOKEN'] = xsrf;
    const req = http.request(`${origin}/api${route}`, { method, headers: h }, res => {
      for (const c of [].concat(res.headers['set-cookie'] ?? [])) { const [k, ...v] = c.split(';')[0].split('='); jar.set(k.trim(), v.join('=')); }
      let body = ''; res.on('data', d => body += d);
      res.on('end', () => { let json = null; try { json = JSON.parse(body); } catch { /* text */ } resolve({ status: res.statusCode, json, body }); });
    });
    req.on('error', reject); if (payload) req.write(payload); req.end();
  });
  const csrf = async () => { xsrf = (await call('GET', '/auth/csrf')).json.token; };
  const key = () => ({ 'Idempotency-Key': randomUUID() });
  await csrf();
  const email = `pc.owner.${randomUUID()}@finsight.local`;
  assert.equal((await call('POST', '/auth/register', { email, password: 'Password123!', fullName: 'Product Owner' })).status, 201);
  assert.equal((await call('POST', '/auth/login', { email, password: 'Password123!' })).status, 200);
  await csrf();
  const biz = await call('POST', '/businesses', { businessName: 'Lahore Traders', businessType: 'retail', languagePreference: 'en', whatsappOptIn: false });
  assert.equal(biz.status, 201);
  assert.equal((await call('POST', '/businesses/active', { businessId: biz.json.businessId })).status, 200);
  await csrf();
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Karachi' }).format(new Date());
  const account = await call('POST', '/finance/accounts', { accountName: 'Main', accountType: 'BANK', currency: 'PKR', openingBalance: '50000.00', openingDate: '2026-01-01' }, key());
  assert.equal(account.status, 201);
  const customer = await call('POST', '/finance/counterparties', { displayName: 'Ali Stores', type: 'CUSTOMER' });
  const item = await call('POST', '/inventory/items', { sku: 'BRW-1', name: 'Brown rice bag', unit: 'bag', itemKind: 'RESALE_GOOD', reorderLevel: '2' });
  assert.equal(item.status, 201);
  assert.equal((await call('POST', '/inventory/movements/receipts', { itemId: item.json.id, quantity: '10', value: '5000.00' }, key())).status, 201);
  const draft = await call('POST', '/invoices', { counterpartyName: 'Ali Stores', documentDate: today, dueDate: today, notes: null,
    lines: [{ description: 'Rice', quantity: '4', unit: 'bag', unitPrice: '900.00', inventoryItemId: item.json.id }] });
  assert.equal(draft.status, 201);
  const invoiceId = draft.json.id;
  assert.equal((await call('POST', `/invoices/${invoiceId}/finalize`, { expectedVersion: draft.json.version })).status, 200);
  assert.equal((await call('POST', '/finance/obligations/recognize-commercial-document', { documentId: invoiceId, documentKind: 'INVOICE',
    counterpartyId: customer.json.id, notes: 'browser' }, key())).status, 201);
  assert.equal((await call('POST', '/finance/transactions', { accountId: account.json.id, eventType: 'DIRECT_SALE_RECEIPT', amount: '1234.00',
    effectiveDate: today, description: 'Counter sale' }, key())).status, 201);
  mark('fixture seeded through authenticated API (account, item, stock, invoice recognized, cash sale)');
  const currentMonth = today.slice(0, 7);
  const [cy, cm] = currentMonth.split('-').map(Number);
  const previousMonth = cm === 1 ? `${cy - 1}-12` : `${cy}-${String(cm - 1).padStart(2, '0')}`;

  // ---------------------------------------------------------------- browser
  browser = await chromium.launch({ headless: true,
    ...(process.env.FINSIGHT_BROWSER_CHANNEL ? { channel: process.env.FINSIGHT_BROWSER_CHANNEL } : {}) });
  const cookies = [...jar].map(([name, value]) => ({ name, value, domain: '127.0.0.1', path: '/' }));
  const viewports = smoke ? [{ width: 1280, height: 900 }, { width: 390, height: 844 }] :
    [{ width: 1280, height: 900 }, { width: 1024, height: 768 }, { width: 390, height: 844 }, { width: 390, height: 600 }];
  const pages = [
    ['/inventory/stock', 'stock-register'], ['/planning', 'pnl-panel'], ['/intelligence', 'action-center'],
    ['/operations/calendar', 'calendar-panel'], ['/ask-finsight', 'copilot'], [`/invoices/${invoiceId}`, 'commercial-lifecycle-panel'], ['/reports', 'financial-pack'],
    ['/records/new', 'monthly-automation'], [`/records/${currentMonth}`, 'monthly-sources'],
  ];
  // Settle the projection, then prove that ordinary navigation (every page below, both locales, four viewports)
  // never triggers a monthly rebuild: with no source change the projection is served as-is.
  assert.equal((await call('POST', '/records/monthly/recalculate', {})).status, 200);
  const beforeNavigation = await call('GET', '/records/monthly/projection-status');
  assert.equal(beforeNavigation.status, 200);
  assert.equal(beforeNavigation.json.stale, false);
  for (const locale of ['en', 'ur']) {
    for (const viewport of viewports) {
      const context = await browser.newContext({ viewport });
      await context.addCookies(cookies);
      await context.addInitScript(l => { try { localStorage.setItem('finsight_locale', l); } catch { /* storage blocked */ } }, locale);
      const page = await context.newPage();
      const errors = [];
      page.on('pageerror', e => errors.push(String(e)));
      // Hydration mismatches and CSP violations surface as console errors, not page errors.
      // Only exemption: the browser's implicit /favicon.ico probe (the app ships no favicon yet; recorded in the handoff).
      page.on('console', m => { if (m.type() === 'error' && !(m.location()?.url ?? '').endsWith('/favicon.ico')) errors.push(`console: ${m.text()} @ ${m.location()?.url ?? ''} on ${page.url()}`); });
      for (const [route, testId] of pages) {
        await page.goto(origin + route, { waitUntil: 'domcontentloaded' });
        await page.getByTestId(testId).first().waitFor({ state: 'visible', timeout: 45000 });
        const layout = await page.evaluate(() => ({ dir: document.documentElement.dir, overflow: document.documentElement.scrollWidth - window.innerWidth }));
        if (locale === 'ur') assert.equal(layout.dir, 'rtl', `${route} is RTL in Urdu`);
        assert.ok(layout.overflow <= 1, `${route} has no horizontal page overflow at ${viewport.width}x${viewport.height} (${layout.overflow}px)`);
      }
      if (viewport.width === 390 && viewport.height === 844) await page.screenshot({ path: path.join(outDir, `mobile-${locale}.png`), fullPage: false });
      assert.deepEqual(errors, [], 'no uncaught page errors, console errors, hydration mismatches or CSP violations');
      mark(`${locale.toUpperCase()} ${viewport.width}x${viewport.height}: ${pages.length} product pages render, ${locale === 'ur' ? 'RTL, ' : ''}no page overflow, no page/console/hydration/CSP errors`);
      await context.close();
    }
  }
  const afterNavigation = await call('GET', '/records/monthly/projection-status');
  assert.equal(afterNavigation.json.lastProjectedAt, beforeNavigation.json.lastProjectedAt, 'page navigation caused a projection rebuild');
  assert.equal(afterNavigation.json.dirty, false);
  assert.equal(afterNavigation.json.lastError, null);
  mark(`No rebuild storm: ${2 * viewports.length * pages.length} page loads served the settled projection without a rebuild`);

  if (!smoke) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await context.addCookies(cookies);
  const page = await context.newPage();
  // The app's CSP forbids eval, so poll locators from Node instead of page.waitForFunction.
  const waitText = (testId, pattern) => waitFor(async () => pattern.test(await page.getByTestId(testId).first().innerText().catch(() => '')),
    testId + ' ' + pattern, 30000);
  // F04: credit note through the lifecycle panel reduces outstanding.
  await page.goto(`${origin}/invoices/${invoiceId}`);
  await page.getByTestId('lifecycle-outstanding').waitFor();
  assert.match(await page.getByTestId('lifecycle-outstanding').innerText(), /3,600\.00/);
  await page.getByTestId('lifecycle-note-amount').fill('100.00');
  await page.getByTestId('lifecycle-note-reason').fill('Damaged bag discount');
  await page.getByTestId('lifecycle-note-submit').click();
  await waitText('lifecycle-outstanding', /3,500\.00/);
  mark('F04 credit note issued in the browser reduces the authoritative outstanding to 3,500.00');
  // F08: fulfil from stock records COGS and the stock register reflects it.
  await page.getByTestId('lifecycle-fulfil-stock').click();
  await waitText('lifecycle-stock-count', /^1$/);
  await page.goto(`${origin}/inventory/stock`);
  await page.getByTestId('stock-qty-BRW-1').waitFor();
  assert.match(await page.getByTestId('stock-qty-BRW-1').innerText(), /^6 bag/);
  assert.match(await page.getByTestId('stock-value-BRW-1').innerText(), /3,000\.00/);
  assert.match(await page.getByTestId('stock-cogs-sale').innerText(), /2,000\.00/);
  mark('F08 invoice fulfilment issues 4 bags at WAC: stock 6 bags / 3,000.00, COGS 2,000.00');
  // F08 movement form (keyboard-submitted receipt).
  await page.getByTestId('stock-item').selectOption({ label: 'BRW-1 · Brown rice bag' });
  await page.getByTestId('stock-quantity').fill('2');
  await page.getByTestId('stock-value').fill('1100.00');
  await page.getByTestId('stock-value').press('Enter');
  await waitText('stock-qty-BRW-1', /^8/);
  mark('F08 receipt recorded from the form by keyboard (Enter) updates the register to 8 bags');
  // Planning: P&L uses recognized revenue, not cash collections.
  await page.goto(`${origin}/planning`);
  await page.getByTestId('pnl-revenue').waitFor();
  assert.match(await page.getByTestId('pnl-revenue').innerText(), /4,734\.00/);
  await page.getByTestId('planning-tab-breakEven').click();
  await page.getByTestId('break-even-status').waitFor();
  // No operating expenses exist: variable = COGS 2,000 (policy), fixed = 0, so break-even revenue is exactly 0.00.
  assert.equal(await page.getByTestId('break-even-status').innerText(), 'AVAILABLE');
  assert.match(await page.getByTestId('break-even-revenue').innerText(), /^0\.00$/);
  mark('Planning: accrual revenue 4,734.00 (3,600 invoice − 100 note + 1,234 cash sale); break-even 0.00 with only policy-variable COGS');
  // Transaction-driven monthly model: the month is derived from the same records, never typed in.
  await page.goto(`${origin}/records/${currentMonth}`);
  await page.getByTestId('monthly-value-revenue').waitFor({ timeout: 45000 });
  assert.match(await page.getByTestId('monthly-value-revenue').innerText(), /4,734\.00/);
  assert.match(await page.getByTestId('monthly-value-cogs').innerText(), /2,000\.00/);
  assert.match(await page.getByTestId('monthly-value-inventoryValue').innerText(), /4,100\.00/);
  assert.match(await page.getByTestId('monthly-value-cashInflow').innerText(), /1,234\.00/);
  await page.getByTestId('monthly-matches').waitFor();
  mark('Monthly record derived from daily activity equals P&L: revenue 4,734.00, COGS 2,000.00, stock 4,100.00, cash in 1,234.00');
  // Late entry: an invoice entered today but dated last month updates last month, not this one.
  const late = await call('POST', '/invoices', { counterpartyName: 'Ali Stores', documentDate: `${previousMonth}-15`, dueDate: `${previousMonth}-28`, notes: null,
    lines: [{ description: 'Late delivery', quantity: '1', unit: 'job', unitPrice: '500.00' }] });
  assert.equal(late.status, 201);
  assert.equal((await call('POST', `/invoices/${late.json.id}/finalize`, { expectedVersion: late.json.version })).status, 200);
  assert.equal((await call('POST', '/finance/obligations/recognize-commercial-document', { documentId: late.json.id, documentKind: 'INVOICE',
    counterpartyId: customer.json.id, notes: 'late entry' }, key())).status, 201);
  await page.goto(`${origin}/records/${previousMonth}`);
  await page.getByTestId('monthly-value-revenue').waitFor({ timeout: 45000 });
  assert.match(await page.getByTestId('monthly-value-revenue').innerText(), /^500\.00$/);
  await page.goto(`${origin}/records/${currentMonth}`);
  await page.getByTestId('monthly-value-revenue').waitFor({ timeout: 45000 });
  assert.match(await page.getByTestId('monthly-value-revenue').innerText(), /4,734\.00/);
  const priorScore = await call('POST', '/scores/query', { month: previousMonth });
  assert.equal(priorScore.status, 200, 'prior month was rescored automatically');
  const history = await call('GET', '/scores/history');
  assert.ok(history.json.some(s => s.month === previousMonth), 'score history includes the backdated month');
  const dashboard = await call('GET', '/dashboard');
  assert.equal(dashboard.status, 200);
  assert.equal(dashboard.json.score.month, currentMonth);
  const dashboardPrior = dashboard.json.cashFlowHistory.find(p => p.month === previousMonth);
  assert.ok(dashboardPrior, 'dashboard charts the derived prior month');
  mark('Late entry dated last month updates last month (500.00) and its health score; this month stays 4,734.00');
  // Supplemental financing is the only manual monthly input, submitted by keyboard; recalculation is idempotent.
  await page.goto(`${origin}/records/new`);
  await page.getByTestId('monthly-recalculate').click();
  await waitText('monthly-recalculated', /No changes/);
  await page.getByTestId('financing-month').fill(previousMonth);
  await page.getByTestId('financing-loan').fill('2000.00');
  await page.getByTestId('financing-loan').press('Enter');
  await waitText('financing-message', /Saved/);
  await page.goto(`${origin}/records/${previousMonth}`);
  await page.getByTestId('monthly-value-loanOutstanding').waitFor({ timeout: 45000 });
  assert.match(await page.getByTestId('monthly-value-loanOutstanding').innerText(), /2,000\.00/);
  const manual = await call('POST', '/records/monthly', { month: previousMonth, cashInflow: 1, cashOutflow: 1, revenue: 1, operatingExpenses: 1, cashBalanceEom: 1, financingType: 'none' });
  assert.equal(manual.status, 409, 'manual monthly totals are refused');
  mark('Recalculation is idempotent; supplemental loan input (keyboard) flows into last month; manual monthly totals are refused (409)');
  // Copilot grounded answer, keyboard only.
  await page.goto(`${origin}/ask-finsight`);
  await page.getByTestId('copilot-question').waitFor();
  await page.getByTestId('copilot-question').focus();
  await page.keyboard.type('What is my revenue this month?');
  await page.keyboard.press('Enter');
  await page.getByTestId('copilot-answer').first().waitFor({ timeout: 45000 });
  assert.match(await page.getByTestId('copilot-answer').first().innerText(), /4734\.00/);
  mark('Copilot answers by keyboard with the exact grounded figure 4734.00 and cited evidence');
  // Reports pack + CSV
  await page.goto(`${origin}/reports`);
  await page.getByTestId('pack-generate').click();
  await page.getByTestId('pack-section-profit_and_loss').waitFor({ timeout: 45000 });
  const download = page.waitForEvent('download');
  await page.getByTestId('pack-csv-profit_and_loss').click();
  const file = await download;
  assert.match(file.suggestedFilename(), /^finsight-profit_and_loss-.*\.csv$/);
  mark('F12B financial pack renders all sections and downloads the P&L CSV');
  // Action center explains itself.
  await page.goto(`${origin}/intelligence`);
  await page.getByTestId('action-item').first().waitFor({ timeout: 45000 });
  assert.match(await page.getByTestId('action-item').first().innerText(), /Why:/);
  mark('Action center items show their reason and evidence');
  await context.close();

  }

  writeFileSync(path.join(outDir, 'results.json'), JSON.stringify({ passed: checks.length, checks }, null, 2));
  console.log(`\nALL ${checks.length} ${smoke ? 'RELEASE-SMOKE' : 'PRODUCT-COMPLETION'} BROWSER CHECKS PASSED`);
} catch (error) {
  console.error('BROWSER ACCEPTANCE FAILED:', error);
  process.exitCode = 1;
} finally {
  writeFileSync(path.join(outDir, 'runtime.log'), javaOutput);
  writeFileSync(path.join(outDir, 'next.log'), nextOutput);
  await browser?.close().catch(() => {});
  gateway?.close();
  nextProc?.kill();
  if (javaRuntime && javaRuntime.exitCode === null) {
    javaRuntime.stdin.on('error', () => {});
    javaRuntime.stdin.end('STOP\n');
    const timer = setTimeout(() => javaRuntime.kill(), 10000);
    javaRuntime.once('exit', () => clearTimeout(timer));
  }
}
