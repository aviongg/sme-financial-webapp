// DEF-11 real multi-tab browser acceptance: real Spring Boot + disposable PostgreSQL + Next.js production build +
// Chromium/Edge/Chrome with real HttpOnly cookies, CSRF, and BroadcastChannel across two live tabs.
import http from 'node:http';
import net from 'node:net';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import {
  mkdirSync,
  writeFileSync,
  readFileSync,
  existsSync,
  copyFileSync,
  cpSync,
  symlinkSync,
  rmSync,
} from 'node:fs';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const frontendDir = path.resolve(fileURLToPath(new URL('..', import.meta.url)));
const rootDir = path.dirname(frontendDir);
const scratchRoot = path.resolve(process.env.FINSIGHT_BROWSER_TMP_DIR || path.join(rootDir, 'tmp'));
const temporary = path.join(scratchRoot, 'def11-browser');
const outDir = path.join(frontendDir, 'test-artifacts', 'def11-browser');
mkdirSync(temporary, { recursive: true });
mkdirSync(outDir, { recursive: true });

const backendPort = 8094;
const nextPort = 3594;
const gatewayPort = 3593;
const origin = `http://127.0.0.1:${gatewayPort}`;

const checks = [];
const mark = (name) => {
  checks.push(name);
  console.log('PASS ' + name);
};

let javaRuntime;
let nextProc;
let gateway;
let browser;
let javaOutput = '';
let nextOutput = '';

const waitFor = async (predicate, label, timeout = 90000) => {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    for (const child of [javaRuntime, nextProc]) {
      if (child && child.exitCode !== null) {
        throw new Error(`${label}: child exited (${child.exitCode})\nJAVA:\n${javaOutput}\nNEXT:\n${nextOutput}`);
      }
    }
    if (await predicate()) return;
    await new Promise((r) => setTimeout(r, 200));
  }
  throw new Error(`${label} not ready in ${timeout}ms`);
};

async function launchBrowser() {
  if (process.env.FINSIGHT_BROWSER_CHANNEL) {
    return chromium.launch({ headless: true, channel: process.env.FINSIGHT_BROWSER_CHANNEL });
  }
  for (const candidate of [undefined, 'chrome', 'msedge']) {
    try {
      return await chromium.launch({
        headless: true,
        ...(candidate ? { channel: candidate } : {}),
      });
    } catch {
      // Try next browser channel
    }
  }
  throw new Error('No Playwright Chromium, Chrome, or Edge browser available.');
}

try {
  for (const port of [backendPort, nextPort, gatewayPort]) {
    await new Promise((resolve, reject) => {
      const probe = net.createServer();
      probe.once('error', () => reject(new Error(`Port ${port} is occupied`)));
      probe.listen(port, '127.0.0.1', () => probe.close(resolve));
    });
  }

  // ---------------------------------------------------------------- backend runtime on disposable PostgreSQL
  const target = path.join(rootDir, 'backend', 'target');
  const classpath = [
    path.join(target, 'test-classes'),
    path.join(target, 'classes'),
    readFileSync(path.join(target, 'test-classpath.txt'), 'utf8').trim(),
  ].join(path.delimiter);
  const java = process.env.JAVA_HOME
    ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    : 'java';
  const classpathArgs = path.join(temporary, 'classpath.args');
  writeFileSync(classpathArgs, '--class-path\n"' + classpath.replaceAll('\\', '/').replaceAll('"', '\\"') + '"\n');
  writeFileSync(
    path.join(temporary, 'Def11Runtime.java'),
    `
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import com.app.sme_health_backend.SmeHealthBackendApplication;
import org.springframework.boot.SpringApplication;
public class Def11Runtime {
  public static void main(String[] a) throws Exception {
    var pg = DisposablePostgres.POSTGRES;
    String key = java.util.Base64.getEncoder().encodeToString(java.security.SecureRandom.getSeed(32));
    var app = SpringApplication.run(SmeHealthBackendApplication.class,
      "--server.port=${backendPort}", "--server.address=127.0.0.1",
      "--spring.datasource.url=" + DisposablePostgres.URL,
      "--spring.datasource.username=finsight_app",
      "--spring.datasource.password=" + DisposablePostgres.PASSWORD,
      "--spring.flyway.enabled=false",
      "--app.documents.storage-dir=" + DisposablePostgres.DOCUMENTS,
      "--app.whatsapp.scheduler.enabled=false",
      "--finsight.crypto.keys.k1=" + key,
      "--app.security.cors.allowed-origins=${origin}",
      "--logging.level.root=WARN",
      "--spring.profiles.active=dev",
      "--OCR_INTEGRATION_ENABLED=false",
      "--app.ai.enabled=true",
      "--app.ai.provider=mock",
      "--app.ai.demo-enabled=true");
    System.out.println("DEF11_RUNTIME_READY");
    System.out.flush();
    var in = new java.util.Scanner(System.in);
    while (in.hasNextLine()) { if (in.nextLine().trim().equals("STOP")) break; }
    app.close();
    pg.close();
  }
}`,
  );
  javaRuntime = spawn(
    java,
    [`-Xmx${process.env.FINSIGHT_BROWSER_JAVA_HEAP || '1536m'}`, '-XX:+UseSerialGC', '@' + classpathArgs, path.join(temporary, 'Def11Runtime.java')],
    { cwd: path.join(rootDir, 'backend'), windowsHide: true },
  );
  javaRuntime.on('error', (error) => { javaOutput += String(error); });
  javaRuntime.stdout.on('data', (d) => { javaOutput += d; });
  javaRuntime.stderr.on('data', (d) => { javaOutput += d; });
  await waitFor(() => javaOutput.includes('DEF11_RUNTIME_READY'), 'backend', 180000);
  assert.equal((await fetch(`http://127.0.0.1:${backendPort}/internal/health/ready`)).status, 200);

  // ---------------------------------------------------------------- isolated Next production server + gateway
  const isolated = path.join(scratchRoot, 'isolated-frontend-def11');
  rmSync(path.join(isolated, '.next'), { recursive: true, force: true });
  mkdirSync(path.join(isolated, '.next'), { recursive: true });
  for (const item of [
    'build',
    'server',
    'static',
    'types',
    'app-path-routes-manifest.json',
    'build-manifest.json',
    'BUILD_ID',
    'export-marker.json',
    'fallback-build-manifest.json',
    'images-manifest.json',
    'package.json',
    'prerender-manifest.json',
    'required-server-files.js',
    'required-server-files.json',
    'routes-manifest.json',
  ]) {
    const src = path.join(frontendDir, '.next', item);
    if (existsSync(src)) cpSync(src, path.join(isolated, '.next', item), { recursive: true });
  }
  copyFileSync(path.join(frontendDir, 'package.json'), path.join(isolated, 'package.json'));
  copyFileSync(path.join(frontendDir, 'next.config.ts'), path.join(isolated, 'next.config.ts'));
  for (const link of ['node_modules', 'public']) {
    if (!existsSync(path.join(isolated, link))) {
      symlinkSync(path.join(frontendDir, link), path.join(isolated, link), process.platform === 'win32' ? 'junction' : 'dir');
    }
  }
  nextProc = spawn(
    process.execPath,
    [path.join(frontendDir, 'node_modules', 'next', 'dist', 'bin', 'next'), 'start', '--hostname', '127.0.0.1', '--port', String(nextPort)],
    { cwd: isolated, windowsHide: true, env: { ...process.env, BACKEND_API_URL: `http://127.0.0.1:${backendPort}` } },
  );
  nextProc.stdout.on('data', (d) => { nextOutput += d; });
  nextProc.stderr.on('data', (d) => { nextOutput += d; });
  await waitFor(() => /Ready in/.test(nextOutput), 'next', 60000);

  gateway = http.createServer((req, res) => {
    const up = http.request(
      {
        hostname: '127.0.0.1',
        port: req.url.startsWith('/api') ? backendPort : nextPort,
        path: req.url,
        method: req.method,
        headers: req.headers,
      },
      (reply) => {
        res.writeHead(reply.statusCode, reply.headers);
        reply.pipe(res);
      },
    );
    up.on('error', (e) => {
      if (!res.headersSent) res.writeHead(502);
      res.end(String(e));
    });
    req.pipe(up);
  });
  await new Promise((r) => gateway.listen(gatewayPort, '127.0.0.1', r));

  // ---------------------------------------------------------------- Seed user with 3 businesses (Alpha, Beta, Gamma)
  const jar = new Map();
  let xsrf = '';
  const api = (method, route, data) =>
    new Promise((resolve, reject) => {
      const payload = data ? JSON.stringify(data) : null;
      const headers = { cookie: [...jar].map(([k, v]) => `${k}=${v}`).join('; ') };
      if (payload) {
        headers['Content-Type'] = 'application/json';
        headers['Content-Length'] = Buffer.byteLength(payload);
      }
      if (method !== 'GET' && xsrf) headers['X-XSRF-TOKEN'] = xsrf;
      const req = http.request(`${origin}/api${route}`, { method, headers }, (res) => {
        for (const c of [].concat(res.headers['set-cookie'] ?? [])) {
          const [k, ...v] = c.split(';')[0].split('=');
          jar.set(k.trim(), v.join('='));
        }
        let body = '';
        res.on('data', (d) => { body += d; });
        res.on('end', () => {
          let json = null;
          try { json = JSON.parse(body); } catch { /* ignore */ }
          resolve({ status: res.statusCode, json, body });
        });
      });
      req.on('error', reject);
      if (payload) req.write(payload);
      req.end();
    });

  const csrf = async () => { xsrf = (await api('GET', '/auth/csrf')).json.token; };
  const email = `def11.owner.${randomUUID()}@finsight.local`;
  const password = 'Password123!';
  await csrf();
  assert.equal((await api('POST', '/auth/register', { email, password, fullName: 'DEF11 Owner' })).status, 201);
  assert.equal((await api('POST', '/auth/login', { email, password })).status, 200);
  await csrf();

  const rA = await api('POST', '/businesses', { businessName: 'Alpha Textiles', businessType: 'manufacturing', languagePreference: 'en', whatsappOptIn: false });
  assert.equal(rA.status, 201, `bizA failed: ${rA.body}`);
  const bizA = rA.json;
  const rB = await api('POST', '/businesses', { businessName: 'Beta Trading Co', businessType: 'trade', languagePreference: 'en', whatsappOptIn: false });
  assert.equal(rB.status, 201, `bizB failed: ${rB.body}`);
  const bizB = rB.json;
  const rC = await api('POST', '/businesses', { businessName: 'Gamma Services', businessType: 'services', languagePreference: 'en', whatsappOptIn: false });
  assert.equal(rC.status, 201, `bizC failed: ${rC.body}`);
  const bizC = rC.json;
  assert.equal((await api('POST', '/businesses/active', { businessId: bizA.businessId })).status, 200);
  mark('Seeded user with 3 active businesses (Alpha, Beta, Gamma)');

  browser = await launchBrowser();
  const cookies = [...jar].map(([name, value]) => ({ name, value, domain: '127.0.0.1', path: '/' }));

  const attachErrorRecorder = (page, errors) => {
    page.on('pageerror', (e) => errors.push(`pageerror: ${e}`));
    page.on('console', (m) => {
      if (m.type() === 'error' && !(m.location()?.url ?? '').endsWith('/favicon.ico')) {
        errors.push(`console: ${m.text()} @ ${m.location()?.url ?? ''}`);
      }
    });
  };

  // Helper to switch business cleanly in Tab 2 using real UI /settings selector so real BroadcastChannel fires
  const switchInTab2 = async (tab2, targetBusinessId, expectedName) => {
    const select = tab2.getByLabel(/Active business|فعال کاروبار/);
    await select.waitFor({ state: 'visible', timeout: 30000 });
    await select.selectOption(targetBusinessId);
    await waitFor(
      async () => (await tab2.getByTestId('header-business-name').innerText().catch(() => '')) === expectedName,
      `Tab 2 switched to ${expectedName}`,
      20000,
    );
  };

  // ---------------------------------------------------------------- Scenario 1: Clean switch -> no modal
  {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    await context.addCookies(cookies);
    const errors = [];
    const tab1 = await context.newPage();
    const tab2 = await context.newPage();
    attachErrorRecorder(tab1, errors);
    attachErrorRecorder(tab2, errors);

    await tab1.goto(`${origin}/invoices/new`);
    await tab1.getByTestId('commercial-counterparty-input').waitFor({ state: 'visible', timeout: 30000 });
    await tab2.goto(`${origin}/settings`);
    await switchInTab2(tab2, bizB.businessId, 'Beta Trading Co');

    await waitFor(
      async () => (await tab1.getByTestId('header-business-name').innerText().catch(() => '')) === 'Beta Trading Co',
      'Tab 1 synced clean switch to Beta',
      20000,
    );
    assert.equal(await tab1.getByTestId('unsaved-business-switch-modal').count(), 0, 'No modal for clean form');
    assert.deepEqual(errors, []);
    mark('Scenario B / Test 1 & 22: Clean form switches seamlessly across tabs without modal');
    await context.close();
  }

  // Ensure server active business is reset to Alpha before Scenario 2
  await csrf();
  assert.equal((await api('POST', '/businesses/active', { businessId: bizA.businessId })).status, 200);

  let savedAlphaInvoiceId = null;

  // ---------------------------------------------------------------- Scenario 2, 4, 6, 11, 17, 20, 21: Dirty invoice + Return + Draft Recovery + Escape
  {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    await context.addCookies(cookies);
    const errors = [];
    const tab1 = await context.newPage();
    const tab2 = await context.newPage();
    attachErrorRecorder(tab1, errors);
    attachErrorRecorder(tab2, errors);

    await tab1.goto(`${origin}/invoices/new`);
    await tab1.getByTestId('commercial-counterparty-input').fill('Alpha Yarn Client');
    await tab1.getByTestId('commercial-line-description-0').fill('Combed Yarn');
    await tab1.getByTestId('commercial-line-quantity-0').fill('5');
    await tab1.getByTestId('commercial-line-unit-price-0').fill('1200.00');
    await waitFor(
      async () => /6,000\.00/.test(await tab1.getByTestId('commercial-preview-total').innerText().catch(() => '')),
      'Server preview settled',
      15000,
    );

    await tab2.goto(`${origin}/settings`);
    await switchInTab2(tab2, bizB.businessId, 'Beta Trading Co');

    const modal = tab1.getByTestId('unsaved-business-switch-modal');
    await modal.waitFor({ state: 'visible', timeout: 20000 });
    assert.match(await tab1.getByTestId('def11-modal-title').innerText(), /Business changed in another tab/);
    assert.equal(await tab1.getByTestId('def11-previous-business-name').innerText(), 'Alpha Textiles');
    assert.equal(await tab1.getByTestId('def11-current-business-name').innerText(), 'Beta Trading Co');
    assert.equal(await tab1.getByTestId('commercial-workspace').count(), 0, 'Stale form is unmounted while modal is open');

    // Escape must keep modal open and show polite hint
    await tab1.keyboard.press('Escape');
    await tab1.getByTestId('def11-choice-required-hint').waitFor({ state: 'visible', timeout: 5000 });
    assert.equal(await modal.isVisible(), true);

    // Verify Beta has zero invoices leaked
    await csrf();
    const betaListBefore = await api('GET', '/invoices');
    assert.equal(betaListBefore.json.items.length, 0, 'Beta must have 0 invoices');

    // Click Return to previous business
    await tab1.getByTestId('def11-return-business').click();
    await modal.waitFor({ state: 'hidden', timeout: 20000 });
    await tab1.getByTestId('commercial-draft-restored-notice').waitFor({ state: 'visible', timeout: 20000 });
    assert.equal(await tab1.getByTestId('header-business-name').innerText(), 'Alpha Textiles');
    assert.equal(await tab1.getByTestId('commercial-counterparty-input').inputValue(), 'Alpha Yarn Client');
    assert.equal(await tab1.getByTestId('commercial-line-description-0').inputValue(), 'Combed Yarn');
    assert.equal(await tab1.getByTestId('commercial-line-unit-price-0').inputValue(), '1200.00');

    // Save draft into Alpha Textiles
    await tab1.getByTestId('commercial-save-draft').click();
    await waitFor(() => /\/invoices\/[0-9a-f-]+$/.test(tab1.url()), 'Navigated to saved invoice detail', 20000);
    savedAlphaInvoiceId = tab1.url().split('/').pop();

    // Verify sessionStorage cleared after save
    const remainingKeys = await tab1.evaluate(() =>
      Object.keys(sessionStorage).filter((k) => k.startsWith('finsight:commercial-draft:v1:')),
    );
    assert.equal(remainingKeys.length, 0, 'sessionStorage draft cleared after save');

    // Verify Beta still has zero invoices
    await csrf();
    assert.equal((await api('POST', '/businesses/active', { businessId: bizB.businessId })).status, 200);
    assert.equal((await api('GET', '/invoices')).json.items.length, 0, 'Beta still has 0 invoices after Alpha save');

    assert.deepEqual(errors, []);
    mark('Scenario A & C / Tests 2, 4, 6, 11, 17, 20, 21: Dirty invoice intercepted, Escape guarded, Return restored draft & saved to Alpha only');
    await context.close();
  }

  // Reset active business to Alpha
  await csrf();
  assert.equal((await api('POST', '/businesses/active', { businessId: bizA.businessId })).status, 200);

  // ---------------------------------------------------------------- Scenario 3 & 5 & 12: Dirty bill + Discard
  {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    await context.addCookies(cookies);
    const errors = [];
    const tab1 = await context.newPage();
    const tab2 = await context.newPage();
    attachErrorRecorder(tab1, errors);
    attachErrorRecorder(tab2, errors);

    await tab1.goto(`${origin}/bills/new`);
    await tab1.getByTestId('commercial-counterparty-input').fill('Punjab Mill Supply');
    await tab1.getByTestId('commercial-line-description-0').fill('Buttons');
    await tab1.getByTestId('commercial-line-unit-price-0').fill('300.00');

    await tab2.goto(`${origin}/settings`);
    await switchInTab2(tab2, bizB.businessId, 'Beta Trading Co');

    const modal = tab1.getByTestId('unsaved-business-switch-modal');
    await modal.waitFor({ state: 'visible', timeout: 20000 });
    await tab1.getByTestId('def11-discard-changes').click();
    await modal.waitFor({ state: 'hidden', timeout: 20000 });

    await tab1.getByTestId('commercial-counterparty-input').waitFor({ state: 'visible', timeout: 20000 });
    assert.equal(await tab1.getByTestId('header-business-name').innerText(), 'Beta Trading Co');
    assert.equal(await tab1.getByTestId('commercial-counterparty-input').inputValue(), '', 'Discarded Alpha bill draft must never appear in Beta');

    const remainingKeys = await tab1.evaluate(() =>
      Object.keys(sessionStorage).filter((k) => k.startsWith('finsight:commercial-draft:v1:')),
    );
    assert.equal(remainingKeys.length, 0, 'Discarded draft purged from sessionStorage');
    assert.deepEqual(errors, []);
    mark('Scenario D / Tests 3, 5, 12: Dirty bill discarded cleanly with zero cross-tenant draft leakage');
    await context.close();
  }

  // Reset active business to Alpha
  await csrf();
  assert.equal((await api('POST', '/businesses/active', { businessId: bizA.businessId })).status, 200);

  // ---------------------------------------------------------------- Test 8: Repeated switches (A -> B -> C -> A)
  {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    await context.addCookies(cookies);
    const errors = [];
    const tab1 = await context.newPage();
    const tab2 = await context.newPage();
    attachErrorRecorder(tab1, errors);
    attachErrorRecorder(tab2, errors);

    await tab1.goto(`${origin}/invoices/new`);
    await tab1.getByTestId('commercial-counterparty-input').fill('Repeated Switch Client');

    await tab2.goto(`${origin}/settings`);
    await switchInTab2(tab2, bizB.businessId, 'Beta Trading Co');
    await tab1.getByTestId('unsaved-business-switch-modal').waitFor({ state: 'visible', timeout: 20000 });

    await switchInTab2(tab2, bizC.businessId, 'Gamma Services');
    await waitFor(
      async () => (await tab1.getByTestId('def11-current-business-name').innerText().catch(() => '')) === 'Gamma Services',
      'Tab 1 current business updated to Gamma while preserving Alpha',
      20000,
    );
    assert.equal(await tab1.getByTestId('def11-previous-business-name').innerText(), 'Alpha Textiles');

    // Switch back to Alpha in Tab 2 -> Tab 1 auto-resolves and restores draft
    await switchInTab2(tab2, bizA.businessId, 'Alpha Textiles');
    await tab1.getByTestId('unsaved-business-switch-modal').waitFor({ state: 'hidden', timeout: 20000 });
    await tab1.getByTestId('commercial-counterparty-input').waitFor({ state: 'visible', timeout: 20000 });
    assert.equal(await tab1.getByTestId('commercial-counterparty-input').inputValue(), 'Repeated Switch Client');
    assert.deepEqual(errors, []);
    mark('Test 8: Repeated cross-tab switches (A -> B -> C -> A) preserve Alpha and auto-restore');
    await context.close();
  }

  // ---------------------------------------------------------------- Scenario E / Test 10: Detail route 404 explanation
  {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    await context.addCookies(cookies);
    const errors = [];
    const tab1 = await context.newPage();
    const tab2 = await context.newPage();
    attachErrorRecorder(tab1, errors);
    attachErrorRecorder(tab2, errors);

    await tab1.goto(`${origin}/invoices/${savedAlphaInvoiceId}`);
    await tab1.getByTestId('commercial-counterparty-input').waitFor({ state: 'visible', timeout: 20000 });

    await tab2.goto(`${origin}/settings`);
    await switchInTab2(tab2, bizB.businessId, 'Beta Trading Co');

    await tab1.getByTestId('commercial-business-changed').waitFor({ state: 'visible', timeout: 20000 });
    assert.match(await tab1.getByTestId('commercial-business-changed').innerText(), /Alpha Textiles/);
    assert.match(await tab1.getByTestId('commercial-business-changed').innerText(), /Beta Trading Co/);

    await tab1.getByTestId('commercial-return-previous-business').click();
    await tab1.getByTestId('commercial-counterparty-input').waitFor({ state: 'visible', timeout: 20000 });
    assert.equal(await tab1.getByTestId('commercial-counterparty-input').inputValue(), 'Alpha Yarn Client');
    // Expect only the legitimate backend 404 when Beta intentionally probed Alpha's invoice URL
    const unexpectedErrors = errors.filter(
      (msg) => !msg.includes(`404 (Not Found) @ ${origin}/api/invoices/${savedAlphaInvoiceId}`),
    );
    assert.deepEqual(unexpectedErrors, []);
    mark('Scenario E / Test 10: Detail page shows contextual business-changed view and returns cleanly');
    await context.close();
  }

  // ---------------------------------------------------------------- Tests 15 & 16: Failed switch-back (network 500 -> retry -> 403 unavailable -> discard)
  {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    await context.addCookies(cookies);
    const errors = [];
    const tab1 = await context.newPage();
    const tab2 = await context.newPage();
    attachErrorRecorder(tab1, errors);
    attachErrorRecorder(tab2, errors);

    await tab1.goto(`${origin}/invoices/new`);
    await tab1.getByTestId('commercial-counterparty-input').fill('Resilient Error Client');

    await tab2.goto(`${origin}/settings`);
    await switchInTab2(tab2, bizB.businessId, 'Beta Trading Co');
    await tab1.getByTestId('unsaved-business-switch-modal').waitFor({ state: 'visible', timeout: 20000 });

    // Simulate transient 500 on first return attempt, then 403 revoked access on second attempt
    let attemptCount = 0;
    await tab1.route('**/api/businesses/active', async (route) => {
      if (route.request().method() === 'POST') {
        attemptCount++;
        if (attemptCount === 1) {
          await route.fulfill({
            status: 500,
            contentType: 'application/json',
            body: JSON.stringify({ error: 'server_error', message: 'Temporary failure' }),
          });
          return;
        }
        await route.fulfill({
          status: 403,
          contentType: 'application/json',
          body: JSON.stringify({ error: 'access_denied', message: 'Membership revoked' }),
        });
        return;
      }
      await route.continue();
    });

    await tab1.getByTestId('def11-return-business').click();
    await tab1.getByTestId('def11-switch-error').waitFor({ state: 'visible', timeout: 10000 });
    assert.match(await tab1.getByTestId('def11-switch-error').innerText(), /Could not switch back/);
    assert.equal(await tab1.getByTestId('def11-return-business').isVisible(), true);

    // Retry -> receives 403 unavailable -> hides Return button cleanly, leaving Discard available
    await tab1.getByTestId('def11-return-business').click();
    await waitFor(
      async () => (await tab1.getByTestId('def11-return-business').count()) === 0,
      'Return button hidden after 403 unavailable',
      10000,
    );
    assert.match(await tab1.getByTestId('def11-switch-error').innerText(), /no longer available/);

    await tab1.unroute('**/api/businesses/active');
    await tab1.getByTestId('def11-discard-changes').click();
    await tab1.getByTestId('commercial-counterparty-input').waitFor({ state: 'visible', timeout: 20000 });
    assert.equal(await tab1.getByTestId('header-business-name').innerText(), 'Beta Trading Co');
    assert.equal(await tab1.getByTestId('commercial-counterparty-input').inputValue(), '');

    const unexpectedErrors = errors.filter(
      (msg) => !msg.includes('500 (Internal Server Error)') && !msg.includes('403 (Forbidden)'),
    );
    assert.deepEqual(unexpectedErrors, []);
    mark('Tests 15 & 16: Failed return-to-previous-business (500 retry & 403 unavailable) handled gracefully');
    await context.close();
  }

  // Reset active business to Alpha before Urdu RTL mobile test
  await csrf();
  assert.equal((await api('POST', '/businesses/active', { businessId: bizA.businessId })).status, 200);

  // ---------------------------------------------------------------- Tests 18 & 19: Urdu RTL + 390x600 Mobile Viewport
  {
    const context = await browser.newContext({ viewport: { width: 390, height: 600 } });
    await context.addCookies(cookies);
    await context.addInitScript(() => {
      try { localStorage.setItem('finsight_locale', 'ur'); } catch { /* ignore */ }
    });
    const errors = [];
    const tab1 = await context.newPage();
    const tab2 = await context.newPage();
    attachErrorRecorder(tab1, errors);
    attachErrorRecorder(tab2, errors);

    await tab1.goto(`${origin}/invoices/new`);
    await tab1.getByTestId('commercial-counterparty-input').fill('اردو کلائنٹ');

    await tab2.goto(`${origin}/settings`);
    await switchInTab2(tab2, bizB.businessId, 'Beta Trading Co');

    const modal = tab1.getByTestId('unsaved-business-switch-modal');
    await modal.waitFor({ state: 'visible', timeout: 20000 });
    assert.match(await tab1.getByTestId('def11-modal-title').innerText(), /دوسرے ٹیب میں کاروبار تبدیل ہو گیا ہے/);
    const metrics = await tab1.evaluate(() => {
      const m = document.querySelector('[data-testid="unsaved-business-switch-modal"]');
      const btn = document.querySelector('[data-testid="def11-return-business"]');
      return {
        dir: m?.getAttribute('dir'),
        overflow: document.documentElement.scrollWidth - window.innerWidth,
        btnHeight: btn?.getBoundingClientRect().height ?? 0,
      };
    });
    assert.equal(metrics.dir, 'rtl');
    assert.ok(metrics.overflow <= 1, `No mobile horizontal overflow (${metrics.overflow}px)`);
    assert.ok(metrics.btnHeight >= 44, `Touch-friendly button height (${metrics.btnHeight}px)`);
    await tab1.screenshot({ path: path.join(outDir, 'def11-modal-ur-mobile.png'), fullPage: false });
    assert.deepEqual(errors, []);
    mark('Tests 18 & 19: Urdu RTL modal & 390x600 mobile viewport pass with zero overflow');
    await context.close();
  }

  // Reset active business to Alpha
  await csrf();
  assert.equal((await api('POST', '/businesses/active', { businessId: bizA.businessId })).status, 200);

  // ---------------------------------------------------------------- Test 14: Logout in Tab 2 takes precedence over pending modal
  {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    await context.addCookies(cookies);
    const errors = [];
    const tab1 = await context.newPage();
    const tab2 = await context.newPage();
    attachErrorRecorder(tab1, errors);
    attachErrorRecorder(tab2, errors);

    await tab1.goto(`${origin}/invoices/new`);
    await tab1.getByTestId('commercial-counterparty-input').fill('Logout Purge Client');
    await tab2.goto(`${origin}/settings`);
    await switchInTab2(tab2, bizB.businessId, 'Beta Trading Co');
    await tab1.getByTestId('unsaved-business-switch-modal').waitFor({ state: 'visible', timeout: 20000 });

    // Log out from Tab 2 using settings sign out button
    await tab2.getByRole('button', { name: /Sign out|سائن آؤٹ/i }).click();
    await waitFor(
      async () => (await tab1.getByTestId('unsaved-business-switch-modal').count()) === 0,
      'Tab 1 dismissed modal on sign out',
      20000,
    );
    await tab1.locator('input[type="password"]').first().waitFor({ state: 'visible', timeout: 20000 });
    const remainingKeys = await tab1.evaluate(() =>
      Object.keys(sessionStorage).filter((k) => k.startsWith('finsight:commercial-draft:v1:')),
    );
    assert.equal(remainingKeys.length, 0, 'Drafts purged on cross-tab sign out');
    const unexpectedErrors = errors.filter(
      (msg) => !msg.includes(`401 (Unauthorized) @ ${origin}/api/auth/me`),
    );
    assert.deepEqual(unexpectedErrors, []);
    mark('Test 14: Cross-tab logout takes precedence over modal and purges drafts');
    await context.close();
  }

  console.log(`\nDEF-11 REAL BROWSER VERIFICATION PASSED (${checks.length} multi-tab journeys)`);
} finally {
  if (browser) await browser.close().catch(() => {});
  if (gateway) await new Promise((r) => gateway.close(r));
  if (nextProc && nextProc.exitCode === null) {
    nextProc.kill('SIGTERM');
    await new Promise((r) => setTimeout(r, 1000));
    if (nextProc.exitCode === null) nextProc.kill('SIGKILL');
  }
  if (javaRuntime && javaRuntime.exitCode === null) {
    try { javaRuntime.stdin.write('STOP\n'); javaRuntime.stdin.end(); } catch { }
    await new Promise((r) => setTimeout(r, 2000));
    if (javaRuntime.exitCode === null) javaRuntime.kill('SIGKILL');
  }
}
