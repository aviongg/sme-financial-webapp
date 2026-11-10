// Product-completion real-browser acceptance: real Spring Boot + disposable PostgreSQL + Next.js production build +
// system Microsoft Edge (Playwright channel "msedge"). Covers EN/UR/RTL, desktop/1024/390x844/390x600, keyboard and
// the main F04/F08/planning/intelligence/calendar/reports/Copilot journeys. Run after `npm run build` and after the
// backend failsafe suite (it reuses the failsafe test classpath). PLAYWRIGHT_MODULE may point at playwright-core.
import http from 'node:http';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync, readFileSync, existsSync, statSync, copyFileSync, cpSync, symlinkSync, rmSync } from 'node:fs';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { randomUUID } from 'node:crypto';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const frontendDir = path.resolve(fileURLToPath(new URL('..', import.meta.url)));
const rootDir = path.dirname(frontendDir);
const temporary = path.join(rootDir, 'tmp', 'product-completion-browser');
const outDir = path.join(frontendDir, 'test-artifacts', 'product-completion-browser');
mkdirSync(temporary, { recursive: true });
mkdirSync(outDir, { recursive: true });
const backendPort = 8093, nextPort = 3593, gatewayPort = 3592;
const origin = `http://127.0.0.1:${gatewayPort}`;
const checks = [];
const mark = (name) => { checks.push(name); console.log('PASS ' + name); };
const waitFor = async (predicate, label, timeout = 90000) => {
  const end = Date.now() + timeout;
  while (Date.now() < end) { if (await predicate()) return; await new Promise(r => setTimeout(r, 250)); }
  throw new Error(label + ' not ready in ' + timeout + 'ms');
};

let javaRuntime, nextProc, gateway, browser, javaOutput = '', nextOutput = '';
try {
  // ---------------------------------------------------------------- backend runtime on disposable PostgreSQL
  const reports = path.join(rootDir, 'backend', 'target', 'failsafe-reports');
  const xml = readFileSync(path.join(reports, 'TEST-com.app.sme_health_backend.inventory.InventoryLedgerPostgreSqlIT.xml'), 'utf8');
  const property = (name) => {
    const m = xml.match(new RegExp('<property name="' + name.replaceAll('.', '\\.') + '" value="([^"]*)"'));
    if (!m) throw new Error('missing ' + name);
    return m[1].replaceAll('&amp;', '&').replaceAll('&quot;', '"');
  };
  const javaHome = property('java.home');
  const classpath = property('surefire.test.class.path');
  const manifest = classpath.split(';').filter(Boolean).map(e => pathToFileURL(e).href + (statSync(e).isDirectory() ? '/' : '')).join(' ');
  writeFileSync(path.join(temporary, 'MANIFEST.MF'), 'Manifest-Version: 1.0\r\n' + ('Class-Path: ' + manifest).match(/.{1,70}/g).map((l, i) => (i ? ' ' : '') + l).join('\r\n') + '\r\n\r\n');
  await new Promise((resolve, reject) => spawn(path.join(javaHome, 'bin', 'jar.exe'), ['cfm', path.join(temporary, 'cp.jar'), path.join(temporary, 'MANIFEST.MF')], { windowsHide: true })
    .on('exit', c => c === 0 ? resolve() : reject(new Error('jar failed'))));
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
  javaRuntime = spawn(path.join(javaHome, 'bin', 'java.exe'), ['--class-path', path.join(temporary, 'cp.jar'), path.join(temporary, 'ProductRuntime.java')],
    { cwd: path.join(rootDir, 'backend'), windowsHide: true });
  javaRuntime.stdout.on('data', d => { javaOutput += d; });
  javaRuntime.stderr.on('data', d => { javaOutput += d; });
  await waitFor(() => javaOutput.includes('PRODUCT_RUNTIME_READY'), 'backend', 180000);

  // ---------------------------------------------------------------- isolated Next production server + gateway
  const isolated = path.join(rootDir, 'tmp', 'isolated-frontend-product');
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
    if (!existsSync(path.join(isolated, link))) { try { symlinkSync(path.join(frontendDir, link), path.join(isolated, link), 'junction'); } catch { /* exists */ } }
  }
  nextProc = spawn(process.execPath, [path.join(frontendDir, 'node_modules', 'next', 'dist', 'bin', 'next'), 'start', '--hostname', '127.0.0.1', '--port', String(nextPort)],
    { cwd: isolated, windowsHide: true, env: { ...process.env, BACKEND_API_URL: `http://127.0.0.1:${backendPort}` } });
  nextProc.stdout.on('data', d => { nextOutput += d; });
  nextProc.stderr.on('data', d => { nextOutput += d; });
  await waitFor(() => /Ready in/.test(nextOutput), 'next', 60000);
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

  // ---------------------------------------------------------------- browser
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  const cookies = [...jar].map(([name, value]) => ({ name, value, domain: '127.0.0.1', path: '/' }));
  const viewports = [{ width: 1280, height: 900 }, { width: 1024, height: 768 }, { width: 390, height: 844 }, { width: 390, height: 600 }];
  const pages = [
    ['/inventory/stock', 'stock-register'], ['/planning', 'pnl-panel'], ['/intelligence', 'action-center'],
    ['/operations/calendar', 'calendar-panel'], ['/ask-finsight', 'copilot'], [`/invoices/${invoiceId}`, 'commercial-lifecycle-panel'], ['/reports', 'financial-pack'],
  ];
  for (const locale of ['en', 'ur']) {
    for (const viewport of viewports) {
      const context = await browser.newContext({ viewport });
      await context.addCookies(cookies);
      await context.addInitScript(l => { try { localStorage.setItem('finsight_locale', l); } catch { /* storage blocked */ } }, locale);
      const page = await context.newPage();
      const errors = [];
      page.on('pageerror', e => errors.push(String(e)));
      for (const [route, testId] of pages) {
        await page.goto(origin + route, { waitUntil: 'domcontentloaded' });
        await page.getByTestId(testId).first().waitFor({ state: 'visible', timeout: 45000 });
        const layout = await page.evaluate(() => ({ dir: document.documentElement.dir, overflow: document.documentElement.scrollWidth - window.innerWidth }));
        if (locale === 'ur') assert.equal(layout.dir, 'rtl', `${route} is RTL in Urdu`);
        assert.ok(layout.overflow <= 1, `${route} has no horizontal page overflow at ${viewport.width}x${viewport.height} (${layout.overflow}px)`);
      }
      if (viewport.width === 390 && viewport.height === 844) await page.screenshot({ path: path.join(outDir, `mobile-${locale}.png`), fullPage: false });
      assert.deepEqual(errors, [], 'no uncaught page errors');
      mark(`${locale.toUpperCase()} ${viewport.width}x${viewport.height}: ${pages.length} product pages render, ${locale === 'ur' ? 'RTL, ' : ''}no page overflow`);
      await context.close();
    }
  }

  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await context.addCookies(cookies);
  const page = await context.newPage();
  // F04: credit note through the lifecycle panel reduces outstanding.
  await page.goto(`${origin}/invoices/${invoiceId}`);
  await page.getByTestId('lifecycle-outstanding').waitFor();
  assert.match(await page.getByTestId('lifecycle-outstanding').innerText(), /3,600\.00/);
  await page.getByTestId('lifecycle-note-amount').fill('100.00');
  await page.getByTestId('lifecycle-note-reason').fill('Damaged bag discount');
  await page.getByTestId('lifecycle-note-submit').click();
  await page.waitForFunction(() => document.querySelector('[data-testid="lifecycle-outstanding"]')?.textContent?.includes('3,500.00'), null, { timeout: 30000 });
  mark('F04 credit note issued in the browser reduces the authoritative outstanding to 3,500.00');
  // F08: fulfil from stock records COGS and the stock register reflects it.
  await page.getByTestId('lifecycle-fulfil-stock').click();
  await page.waitForFunction(() => document.querySelector('[data-testid="lifecycle-stock-count"]')?.textContent === '1', null, { timeout: 30000 });
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
  await page.waitForFunction(() => document.querySelector('[data-testid="stock-qty-BRW-1"]')?.textContent?.startsWith('8'), null, { timeout: 30000 });
  mark('F08 receipt recorded from the form by keyboard (Enter) updates the register to 8 bags');
  // Planning: P&L uses recognized revenue, not cash collections.
  await page.goto(`${origin}/planning`);
  await page.getByTestId('pnl-revenue').waitFor();
  assert.match(await page.getByTestId('pnl-revenue').innerText(), /4,734\.00/);
  await page.getByTestId('planning-tab-breakEven').click();
  await page.getByTestId('break-even-status').waitFor();
  assert.equal(await page.getByTestId('break-even-status').innerText(), 'UNAVAILABLE');
  mark('Planning: accrual revenue 4,734.00 (3,600 invoice − 100 note + 1,234 cash sale); break-even refuses unclassified costs');
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

  writeFileSync(path.join(outDir, 'results.json'), JSON.stringify({ passed: checks.length, checks }, null, 2));
  console.log(`\nALL ${checks.length} PRODUCT-COMPLETION BROWSER CHECKS PASSED`);
} catch (error) {
  console.error('BROWSER ACCEPTANCE FAILED:', error);
  process.exitCode = 1;
} finally {
  await browser?.close().catch(() => {});
  gateway?.close();
  nextProc?.kill();
  if (javaRuntime) { javaRuntime.stdin.write('STOP\n'); setTimeout(() => javaRuntime.kill(), 5000); }
}
