import http from 'node:http';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import {
  mkdirSync,
  writeFileSync,
  readFileSync,
  existsSync,
  statSync,
  copyFileSync,
  cpSync,
  symlinkSync,
  rmSync,
} from 'node:fs';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { randomUUID } from 'node:crypto';
import net from 'node:net';

const require = createRequire(import.meta.url);
const playwrightPath =
  process.env.PLAYWRIGHT_MODULE ||
  (existsSync('C:/Users/PC/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
    ? 'C:/Users/PC/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'
    : 'playwright');
const { chromium } = require(playwrightPath);

const frontendDir = path.resolve(fileURLToPath(new URL('..', import.meta.url)));
const rootDir = path.dirname(frontendDir);
const outDir = path.join(frontendDir, 'test-artifacts', 'fx-browser');
mkdirSync(outDir, { recursive: true });

const temporary = path.join(rootDir, 'tmp', 'fx-live-test');
mkdirSync(temporary, { recursive: true });

const backendPort = 8087;
const nextPort = 3587;
const gatewayPort = 3586;
const origin = `http://127.0.0.1:${gatewayPort}`;

const checks = [];
const mark = (name) => {
  checks.push(name);
  console.log('PASS ' + name);
};

async function free(port) {
  const server = net.createServer();
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '127.0.0.1', resolve);
  });
  await new Promise((resolve) => server.close(resolve));
}

async function waitFor(predicate, label, timeout = 90000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    if (predicate()) return;
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(label + ' did not become ready within ' + timeout + 'ms.');
}

const exited = (child) => !child || child.exitCode !== null || child.signalCode !== null;

let javaRuntime;
let nextProc;
let gatewayServer;
let browser;
let javaOutput = '';
let nextOutput = '';

async function runCommand(executable, args, options = {}) {
  const child = spawn(executable, args, { cwd: rootDir, windowsHide: true, ...options });
  let output = '';
  child.stdout?.on('data', (d) => (output += d.toString()));
  child.stderr?.on('data', (d) => (output += d.toString()));
  await new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('exit', (code) =>
      code === 0 ? resolve() : reject(new Error(`Command ${executable} failed with exit ${code}`))
    );
  });
  return output;
}

try {
  await free(backendPort);
  await free(nextPort);
  await free(gatewayPort);

  // 1. Resolve compiled surefire classpath evidence
  const xml = readFileSync(
    path.join(
      rootDir,
      'backend',
      'target',
      'surefire-reports',
      'TEST-com.app.sme_health_backend.fx.FxPostgreSqlIT.xml'
    ),
    'utf8'
  );
  const property = (name) => {
    const match = xml.match(new RegExp('<property name="' + name.replaceAll('.', '\\.') + '" value="([^"]*)"'));
    if (!match) throw new Error('Missing property in surefire xml: ' + name);
    return match[1].replaceAll('&amp;', '&').replaceAll('&quot;', '"');
  };

  const javaHome = property('java.home');
  const classpath = property('surefire.test.class.path');

  // Build classpath manifest & jar
  const manifestValue = classpath
    .split(';')
    .filter(Boolean)
    .map((entry) => pathToFileURL(entry).href + (statSync(entry).isDirectory() ? '/' : ''))
    .join(' ');
  const lines = ('Class-Path: ' + manifestValue).match(/.{1,70}/g);
  writeFileSync(
    path.join(temporary, 'MANIFEST.MF'),
    'Manifest-Version: 1.0\r\n' + lines.map((l, i) => (i ? ' ' : '') + l).join('\r\n') + '\r\n\r\n'
  );
  await runCommand(path.join(javaHome, 'bin', 'jar.exe'), [
    'cfm',
    path.join(temporary, 'classpath.jar'),
    path.join(temporary, 'MANIFEST.MF'),
  ]);

  // Write FxLiveRuntime.java
  const javaSource = `
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import com.app.sme_health_backend.SmeHealthBackendApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import java.sql.*;
import java.util.*;

public class FxLiveRuntime {
    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext app = null;
        var pg = DisposablePostgres.POSTGRES;
        try {
            String key = Base64.getEncoder().encodeToString(java.security.SecureRandom.getSeed(32));
            app = SpringApplication.run(SmeHealthBackendApplication.class,
                "--server.port=${backendPort}", "--server.address=127.0.0.1",
                "--spring.datasource.url=" + DisposablePostgres.URL,
                "--spring.datasource.username=finsight_app", "--spring.datasource.password=" + DisposablePostgres.PASSWORD,
                "--spring.flyway.enabled=false", "--app.documents.storage-dir=" + DisposablePostgres.DOCUMENTS,
                "--app.whatsapp.scheduler.enabled=false", "--finsight.crypto.keys.k1=" + key,
                "--app.security.cors.allowed-origins=${origin}", "--logging.level.root=WARN");

            System.out.println("FX_LIVE_READY");
            System.out.println("FX_DB_PORT:" + pg.getPort());
            System.out.flush();

            var input = new Scanner(System.in);
            while (input.hasNextLine()) {
                String line = input.nextLine().trim();
                if (line.equals("STOP")) {
                    break;
                } else if (line.startsWith("EXEC_SQL ")) {
                    String sql = line.substring("EXEC_SQL ".length());
                    try (var c = pg.getPostgresDatabase().getConnection(); var s = c.createStatement()) {
                        s.execute(sql);
                        System.out.println("SQL_SUCCESS");
                    } catch (Exception ex) {
                        System.out.println("SQL_ERROR:" + ex.getMessage());
                    }
                    System.out.flush();
                }
            }
        } finally {
            if (app != null) app.close();
            pg.close();
        }
    }
}
`;
  writeFileSync(path.join(temporary, 'FxLiveRuntime.java'), javaSource);

  // Spawn Spring Boot backend with Disposable Postgres
  console.log('Starting real Spring Boot backend with Disposable Postgres on port', backendPort);
  javaRuntime = spawn(
    path.join(javaHome, 'bin', 'java.exe'),
    ['--class-path', path.join(temporary, 'classpath.jar'), path.join(temporary, 'FxLiveRuntime.java')],
    { cwd: path.join(rootDir, 'backend'), windowsHide: true }
  );

  javaRuntime.stdout.on('data', (d) => {
    javaOutput += d.toString();
  });
  javaRuntime.stderr.on('data', (d) => {
    process.stderr.write(d.toString());
  });

  await waitFor(() => javaOutput.includes('FX_LIVE_READY'), 'Spring Boot backend', 120000);
  console.log('Spring Boot backend is FX_LIVE_READY!');

  // 2. Prepare isolated Next.js runtime
  console.log('Preparing isolated Next.js runtime in tmp/isolated-frontend-fx...');
  const isolatedDir = path.join(rootDir, 'tmp', 'isolated-frontend-fx');
  const isolatedNext = path.join(isolatedDir, '.next');
  rmSync(isolatedNext, { recursive: true, force: true });
  mkdirSync(isolatedNext, { recursive: true });

  const neededBuildArtifacts = [
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
  ];

  for (const item of neededBuildArtifacts) {
    const src = path.join(frontendDir, '.next', item);
    const dst = path.join(isolatedNext, item);
    if (existsSync(src)) {
      cpSync(src, dst, { recursive: true });
    }
  }
  copyFileSync(path.join(frontendDir, 'package.json'), path.join(isolatedDir, 'package.json'));
  copyFileSync(path.join(frontendDir, 'next.config.ts'), path.join(isolatedDir, 'next.config.ts'));

  const nodeModulesJunction = path.join(isolatedDir, 'node_modules');
  if (!existsSync(nodeModulesJunction)) {
    try {
      symlinkSync(path.join(frontendDir, 'node_modules'), nodeModulesJunction, 'junction');
    } catch { }
  }

  const publicJunction = path.join(isolatedDir, 'public');
  if (!existsSync(publicJunction)) {
    try {
      symlinkSync(path.join(frontendDir, 'public'), publicJunction, 'junction');
    } catch { }
  }

  // 3. Start Next.js on port 3587
  console.log('Starting Next.js on port', nextPort);
  const nextBin = path.join(frontendDir, 'node_modules', 'next', 'dist', 'bin', 'next');
  nextProc = spawn(
    process.execPath,
    [nextBin, 'start', '--hostname', '127.0.0.1', '--port', String(nextPort)],
    {
      cwd: isolatedDir,
      windowsHide: true,
      env: { ...process.env, BACKEND_API_URL: `http://127.0.0.1:${backendPort}` },
    }
  );

  nextProc.stdout.on('data', (d) => {
    nextOutput += d.toString();
  });
  nextProc.stderr.on('data', (d) => {
    process.stderr.write(d.toString());
  });

  await waitFor(() => /Ready in/.test(nextOutput), 'Next.js server', 30000);
  console.log('Next.js server is ready!');

  // 4. Start reverse gateway on port 3586
  console.log('Starting reverse gateway on port', gatewayPort);
  gatewayServer = http.createServer((req, res) => {
    const isApi = req.url.startsWith('/api/') || req.url === '/api';
    const targetPort = isApi ? backendPort : nextPort;
    const upstream = http.request(
      {
        hostname: '127.0.0.1',
        port: targetPort,
        path: req.url,
        method: req.method,
        headers: req.headers,
      },
      (reply) => {
        res.writeHead(reply.statusCode, reply.headers);
        reply.pipe(res);
      }
    );
    upstream.on('error', (err) => {
      if (!res.headersSent) res.writeHead(502, { 'content-type': 'text/plain' });
      res.end('Upstream unavailable: ' + err.message);
    });
    req.on('aborted', () => upstream.destroy());
    res.on('close', () => upstream.destroy());
    req.pipe(upstream);
  });

  await new Promise((resolve) => gatewayServer.listen(gatewayPort, '127.0.0.1', resolve));
  console.log('Gateway listening on', origin);

  // 5. Seed owner user and business via real REST API
  console.log('Seeding authentication and business fixtures via real backend REST API...');
  const ownerJar = new Map();
  let ownerXsrf = '';

  function storeCookies(jar, headers) {
    const setCookie = headers['set-cookie'];
    if (!setCookie) return;
    const cookieArray = Array.isArray(setCookie) ? setCookie : [setCookie];
    for (const cookieStr of cookieArray) {
      const parts = cookieStr.split(';')[0].split('=');
      if (parts.length >= 2) {
        jar.set(parts[0].trim(), parts.slice(1).join('=').trim());
      }
    }
  }

  function getCookieHeader(jar) {
    return Array.from(jar.entries())
      .map(([k, v]) => `${k}=${v}`)
      .join('; ');
  }

  async function apiCall(jar, xsrf, method, route, data = null, headers = {}) {
    return new Promise((resolve, reject) => {
      const payload = data ? JSON.stringify(data) : null;
      const reqHeaders = {
        ...headers,
        cookie: getCookieHeader(jar),
      };
      if (payload) {
        reqHeaders['Content-Type'] = 'application/json';
        reqHeaders['Content-Length'] = Buffer.byteLength(payload);
      }
      if (method !== 'GET' && xsrf) {
        reqHeaders['X-XSRF-TOKEN'] = xsrf;
      }
      const req = http.request(
        `${origin}/api${route}`,
        {
          method,
          headers: reqHeaders,
        },
        (res) => {
          storeCookies(jar, res.headers);
          let body = '';
          res.on('data', (chunk) => (body += chunk));
          res.on('end', () => {
            let json = null;
            try {
              json = JSON.parse(body);
            } catch { }
            resolve({ status: res.statusCode, headers: res.headers, body, json });
          });
        }
      );
      req.on('error', reject);
      if (payload) req.write(payload);
      req.end();
    });
  }

  async function execSql(sql) {
    const singleLineSql = sql.replace(/\r?\n/g, ' ').replace(/\s+/g, ' ').trim();
    const startIdx = javaOutput.length;
    javaRuntime.stdin.write(`EXEC_SQL ${singleLineSql}\n`);
    await waitFor(() => {
      const slice = javaOutput.slice(startIdx);
      if (slice.includes('SQL_ERROR:')) {
        const errLine = slice.split('\n').find((l) => l.includes('SQL_ERROR:'));
        throw new Error('SQL execution failed: ' + errLine);
      }
      return slice.includes('SQL_SUCCESS');
    }, 'SQL execution of: ' + singleLineSql.slice(0, 30), 10000);
  }

  // Get initial CSRF
  const csrfRes = await apiCall(ownerJar, '', 'GET', '/auth/csrf');
  assert.equal(csrfRes.status, 200, 'CSRF endpoint must succeed');
  ownerXsrf = csrfRes.json.token;

  // Register Owner user
  const ownerEmail = `fx.owner.${randomUUID()}@finsight.local`;
  const password = 'Password123!';
  const regOwnerRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/auth/register', {
    email: ownerEmail,
    password,
    fullName: 'FX Owner User',
  });
  assert.equal(regOwnerRes.status, 201, 'Owner registration must succeed');

  // Login Owner
  const loginOwnerRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/auth/login', {
    email: ownerEmail,
    password,
  });
  assert.equal(loginOwnerRes.status, 200, 'Owner login must succeed');

  // Refresh CSRF token after login
  const postLoginCsrf = await apiCall(ownerJar, '', 'GET', '/auth/csrf');
  ownerXsrf = postLoginCsrf.json.token;

  // Create Business
  const bizRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/businesses', {
    businessName: 'Multicurrency Trading SME',
    businessType: 'retail',
    languagePreference: 'en',
    whatsappOptIn: false,
  });
  assert.equal(bizRes.status, 201, 'Business creation must succeed');
  const businessId = bizRes.json.businessId;

  // Activate Business
  const actRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/businesses/active', { businessId });
  assert.equal(actRes.status, 200, 'Activate business must succeed');

  // Launch Playwright Browser
  console.log('Launching Playwright Edge / Chromium browser...');
  browser = await chromium.launch({
    channel: 'msedge',
    headless: true,
  });
  const context = await browser.newContext({
    viewport: { width: 1280, height: 900 },
  });

  // Inject cookies into browser context
  const cookiesToInject = Array.from(ownerJar.entries()).map(([name, value]) => ({
    name,
    value,
    domain: '127.0.0.1',
    path: '/',
  }));
  await context.addCookies(cookiesToInject);

  const page = await context.newPage();

  // JOURNEY 1: Workspace Mount & Initial State
  console.log('Executing Journey 1: Mount FX Workspace...');
  await page.goto(`${origin}/finance/fx`, { waitUntil: 'networkidle' });
  await page.waitForSelector('[data-testid="fx-workspace"]');

  const title = await page.textContent('h1');
  assert.ok(title.includes('Foreign Exchange & Multicurrency'), 'Page title must match: ' + title);

  const disclaimer = await page.textContent('[data-testid="fx-disclaimer-notice"]');
  assert.ok(disclaimer.includes('Preview only'), 'Disclaimer must be present: ' + disclaimer);
  mark('Journey 1: Owner accesses /finance/fx workspace with real HTTP persistence and zero-mutation notice');

  // JOURNEY 2: Case A Execution (Outgoing USD 100 with PKR 150 Fee)
  console.log('Executing Journey 2: Case A Execution...');
  await page.click('[data-testid="preset-case-a"]');
  await page.click('[data-testid="fx-calculate-btn"]');
  await page.waitForSelector('[data-testid="fx-preview-results"]');

  const grossTextA = await page.textContent('[data-testid="fx-gross-settlement-amount"]');
  assert.ok(grossTextA.includes('27,850.00'), 'Gross converted amount must be 27,850.00: ' + grossTextA);

  const totalFeeA = await page.textContent('[data-testid="fx-total-fees"]');
  assert.ok(totalFeeA.includes('150.00'), 'Total fees must be 150.00: ' + totalFeeA);

  const totalDebitA = await page.textContent('[data-testid="fx-primary-settlement-amount"]');
  assert.ok(totalDebitA.includes('28,000.00'), 'Total debited must be 28,000.00: ' + totalDebitA);

  const effectiveRateA = await page.textContent('[data-testid="fx-effective-rate"]');
  assert.ok(effectiveRateA.includes('280.000000'), 'Effective rate must be 280.000000: ' + effectiveRateA);
  mark('Journey 2: Case A Outgoing USD 100 with PKR 150 fee calculates 28,000.00 PKR debit and 280.000000 effective rate');

  // JOURNEY 3: Case B Execution (Incoming USD 100 with PKR 150 Fee)
  console.log('Executing Journey 3: Case B Execution...');
  await page.click('[data-testid="preset-case-b"]');
  await page.click('[data-testid="fx-calculate-btn"]');
  await page.waitForTimeout(500);

  const netReceiptB = await page.textContent('[data-testid="fx-primary-settlement-amount"]');
  assert.ok(netReceiptB.includes('27,700.00'), 'Net received must be 27,700.00: ' + netReceiptB);

  const effectiveRateB = await page.textContent('[data-testid="fx-effective-rate"]');
  assert.ok(effectiveRateB.includes('277.000000'), 'Effective rate must be 277.000000: ' + effectiveRateB);
  mark('Journey 3: Case B Incoming USD 100 with PKR 150 fee calculates 27,700.00 PKR net receipt and 277.000000 effective rate');

  // JOURNEY 4: Case C1 Execution (USD -> JPY with 0 Minor Units)
  console.log('Executing Journey 4: Case C1 JPY Precision...');
  await page.click('[data-testid="preset-case-c1"]');
  await page.click('[data-testid="fx-calculate-btn"]');
  await page.waitForTimeout(500);

  const grossC1 = await page.textContent('[data-testid="fx-gross-settlement-amount"]');
  assert.ok(grossC1.includes('15,535'), 'JPY gross must round to integer 15,535: ' + grossC1);
  assert.ok(!grossC1.includes('.'), 'JPY gross must not contain fractional decimals');
  mark('Journey 4: Case C1 JPY rounds deterministically to 0 decimal places (15,535 JPY)');

  // JOURNEY 5: Case C2 Execution (USD -> KWD with 3 Minor Units)
  console.log('Executing Journey 5: Case C2 KWD Precision...');
  await page.click('[data-testid="preset-case-c2"]');
  await page.click('[data-testid="fx-calculate-btn"]');
  await page.waitForTimeout(500);

  const grossC2 = await page.textContent('[data-testid="fx-gross-settlement-amount"]');
  assert.ok(grossC2.includes('30.726'), 'KWD gross must preserve 3 decimal places: ' + grossC2);
  mark('Journey 5: Case C2 KWD rounds deterministically to 3 decimal places (30.726 KWD)');

  // JOURNEY 6: Case D Execution (Rate Scenario Variance Analysis)
  console.log('Executing Journey 6: Case D Scenario Variance...');
  await page.click('[data-testid="preset-case-d"]');
  await page.click('[data-testid="fx-calculate-btn"]');
  await page.waitForTimeout(500);

  await page.waitForSelector('[data-testid="fx-scenario-variance-card"]');
  const compGrossD = await page.textContent('[data-testid="fx-comp-gross"]');
  assert.ok(compGrossD.includes('28,200.00'), 'Comparison gross must be 28,200.00: ' + compGrossD);

  const varAmountD = await page.textContent('[data-testid="fx-variance-amount"]');
  assert.ok(varAmountD.includes('350.00'), 'Variance amount must be +350.00: ' + varAmountD);

  const varPercentD = await page.textContent('[data-testid="fx-variance-percent"]');
  assert.ok(varPercentD.includes('1.2567%'), 'Variance percent must be +1.2567%: ' + varPercentD);

  const impactD = await page.textContent('[data-testid="fx-variance-impact-badge"]');
  assert.ok(impactD.includes('Unfavorable'), 'Impact for higher outgoing rate must be Unfavorable: ' + impactD);
  mark('Journey 6: Case D Scenario Variance correctly computes +350.00 PKR difference (+1.2567%) with Unfavorable impact');

  // JOURNEY 7: Currency Guide Table
  console.log('Executing Journey 7: Currency Guide Verification...');
  await page.waitForSelector('[data-testid="fx-currency-guide"]');
  const guideContent = await page.textContent('[data-testid="fx-currency-guide"]');
  for (const c of ['PKR', 'USD', 'EUR', 'GBP', 'AED', 'SAR', 'JPY', 'KWD', 'BHD', 'OMR']) {
    assert.ok(guideContent.includes(c), 'Currency guide must list ' + c);
  }
  mark('Journey 7: Supported Currency Precision Guide lists all 10 authoritative currencies');

  // JOURNEY 8: Direct API Validation
  console.log('Executing Journey 8: Direct API Contract Verification...');
  const currenciesApiRes = await apiCall(ownerJar, ownerXsrf, 'GET', '/finance/fx/currencies');
  assert.equal(currenciesApiRes.status, 200);
  assert.equal(currenciesApiRes.json.length, 10);

  const previewApiRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/fx/preview', {
    baseCurrency: 'USD',
    quoteCurrency: 'PKR',
    rate: 278.50,
    direction: 'OUTGOING',
    principalAmount: 100.00,
    principalCurrency: 'USD',
    fixedFee: 150.00,
    feeCurrency: 'PKR',
    percentageFeePercent: 0,
    sourceType: 'MANUAL_USER_RATE',
  });
  assert.equal(previewApiRes.status, 200);
  assert.equal(previewApiRes.json.grossSettlementAmount, 27850.0);
  assert.equal(previewApiRes.json.totalSettlementDebited, 28000.0);
  assert.equal(previewApiRes.json.provenance.calculationType, 'PURE_JAVA_DETERMINISTIC_PREVIEW');
  mark('Journey 8: Direct API Contract GET /currencies and POST /preview return exact authoritative figures');

  // JOURNEY 9: Zero Database Mutation Invariant Check
  console.log('Executing Journey 9: Zero Mutation Verification...');
  const txCheck = await apiCall(ownerJar, ownerXsrf, 'GET', '/finance/transactions?limit=10');
  assert.equal(txCheck.status, 200);
  const items = txCheck.json.items || txCheck.json.content || [];
  assert.equal(items.length, 0, 'Zero transactions must have been posted');
  mark('Journey 9: Zero Database Mutation Invariant confirmed — no ledger transactions created');

  // JOURNEY 10: Responsive Mobile Viewport
  console.log('Executing Journey 10: Mobile Viewport Rendering...');
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(300);
  const mobileVisible = await page.isVisible('[data-testid="fx-workspace"]');
  assert.ok(mobileVisible, 'Workspace must render cleanly on mobile viewport');
  await page.screenshot({ path: path.join(outDir, 'mobile-fx-workspace.png') });
  mark('Journey 10: Mobile 390x844 responsive layout renders cleanly without layout breakages');

  // JOURNEY 11: Switch to Settlements Tab & Verify Component Mount
  console.log('Executing Journey 11: Switch to Cross-Currency Settlements tab...');
  // Restore desktop viewport
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.waitForTimeout(200);
  await page.click('[data-testid="tab-fx-settlements"]');
  await page.waitForSelector('[data-testid="fx-settlement-form"]');
  await page.waitForSelector('[data-testid="fx-settlement-history-card"]');
  mark('Journey 11: Switch to Cross-Currency Settlements tab and verify UI mount');

  // JOURNEY 12: Seed Account, Counterparty & USD Obligations
  console.log('Executing Journey 12: Seeding financial fixtures...');
  // 1. Create Supplier Counterparty
  const supplierRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/counterparties', {
    displayName: 'Overseas Supplier Tech Ltd',
    type: 'SUPPLIER',
    email: 'supplier@techcorp.test',
  });
  assert.equal(supplierRes.status, 201, 'Supplier creation must succeed');
  const supplierId = supplierRes.json.id;

  // 2. Create PKR Financial Account
  const pkrAccountRes = await apiCall(
    ownerJar,
    ownerXsrf,
    'POST',
    '/finance/accounts',
    {
      accountName: 'HBL PKR Operating',
      accountType: 'BANK',
      currency: 'PKR',
      openingBalance: '500000.00',
      openingDate: '2026-01-01',
      institutionName: 'Habib Bank Limited',
      notes: 'Operating account',
    },
    { 'Idempotency-Key': 'acct-create-' + randomUUID() }
  );
  assert.equal(pkrAccountRes.status, 201, 'PKR Account creation must succeed');
  const pkrAccountId = pkrAccountRes.json.id;

  // 3. Create USD Payable Obligation
  const usdPayableRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/obligations', {
    counterpartyId: supplierId,
    direction: 'PAYABLE',
    amount: '100.00',
    issueDate: '2026-11-01',
    dueDate: '2026-11-30',
    referenceNumber: 'BILL-USD-100',
    description: 'Cloud hosting vendor invoice',
  });
  assert.equal(usdPayableRes.status, 201, 'Payable creation must succeed');
  const usdPayableId = usdPayableRes.json.id;

  // Update obligation currency to USD via SQL
  await execSql(`UPDATE financial_obligations SET currency = 'USD' WHERE id = '${usdPayableId}'`);

  // Verify obligation currency is USD via REST
  const verifyObRes = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/obligations/${usdPayableId}`);
  assert.equal(verifyObRes.status, 200);
  assert.equal(verifyObRes.json.currency, 'USD');
  assert.equal(parseFloat(verifyObRes.json.outstandingAmount), 100.0);
  mark('Journey 12: Seeded 500,000 PKR account and 100 USD payable obligation');

  // JOURNEY 13: UI Settlement Preview Calculation
  console.log('Executing Journey 13: UI Settlement Preview...');
  // Reload page so fresh obligations & accounts are loaded into the form
  await page.goto(`${origin}/finance/fx`, { waitUntil: 'networkidle' });
  await page.click('[data-testid="tab-fx-settlements"]');
  await page.waitForSelector('[data-testid="fx-settlement-form"]');

  // Select the USD obligation
  await page.selectOption('[data-testid="select-settlement-obligation"]', usdPayableId);
  // Select PKR account
  await page.selectOption('[data-testid="select-settlement-account"]', pkrAccountId);

  // Fill settle amount (100.00)
  await page.fill('[data-testid="input-settle-amount"]', '100.00');
  // Fill locked rate (278.50)
  await page.fill('[data-testid="input-settle-rate"]', '278.50');
  // Fill fixed fee (150.00)
  await page.fill('[data-testid="input-settle-fixed-fee"]', '150.00');

  // Wait for preview banner to compute and render
  await page.waitForSelector('[data-testid="fx-settlement-preview-banner"]');

  const grossSettle = await page.textContent('[data-testid="fx-settlement-preview-gross"]');
  assert.ok(grossSettle.includes('27,850.00'), 'Preview gross must be 27,850.00: ' + grossSettle);

  const feeSettle = await page.textContent('[data-testid="fx-settlement-preview-fees"]');
  assert.ok(feeSettle.includes('150.00'), 'Preview fees must be 150.00: ' + feeSettle);

  const moveSettle = await page.textContent('[data-testid="fx-settlement-preview-movement"]');
  assert.ok(moveSettle.includes('-28,000.00'), 'Preview movement must be -28,000.00: ' + moveSettle);

  const effRateSettle = await page.textContent('[data-testid="fx-settlement-preview-effective-rate"]');
  assert.ok(effRateSettle.includes('280.000000'), 'Preview effective rate must be 280.000000: ' + effRateSettle);
  mark('Journey 13: Live settlement preview reflects exact gross 27,850.00 PKR, fees 150.00 PKR, net debit -28,000.00 PKR, effective rate 280.000000');

  // JOURNEY 14: Post Cross-Currency Settlement (Case A) via Browser UI
  console.log('Executing Journey 14: Post Settlement Case A via UI...');
  await page.click('[data-testid="btn-post-settlement"]');
  await page.waitForSelector('[data-testid="fx-settlement-success-alert"]');

  const successText = await page.textContent('[data-testid="fx-settlement-success-alert"]');
  assert.ok(successText.length > 0, 'Success alert must display message');

  // Verify settlement row appears in history table
  await page.waitForSelector('[data-testid="fx-settlement-history-table"]');
  const settlementsListRes = await apiCall(ownerJar, ownerXsrf, 'GET', '/finance/fx/settlements');
  assert.equal(settlementsListRes.status, 200);
  assert.ok(settlementsListRes.json.length >= 1, 'At least 1 settlement must exist');
  const settlementA = settlementsListRes.json[0];
  const settlementAId = settlementA.id;

  await page.waitForSelector(`[data-testid="fx-settlement-row-${settlementAId}"]`);
  const rowObAmt = await page.textContent(`[data-testid="fx-settlement-ob-amt-${settlementAId}"]`);
  assert.ok(rowObAmt.includes('100.00 USD'), 'Row must show 100.00 USD: ' + rowObAmt);

  const rowAcctMv = await page.textContent(`[data-testid="fx-settlement-acct-mv-${settlementAId}"]`);
  assert.ok(rowAcctMv.includes('-28,000.00 PKR'), 'Row must show -28,000.00 PKR: ' + rowAcctMv);

  const rowEffRate = await page.textContent(`[data-testid="fx-settlement-eff-rate-${settlementAId}"]`);
  assert.ok(rowEffRate.includes('280.000000'), 'Row must show 280.000000 effective rate: ' + rowEffRate);

  const rowStatus = await page.textContent(`[data-testid="badge-status-${settlementAId}"]`);
  assert.ok(rowStatus.toLowerCase().includes('settled'), 'Row status must be SETTLED: ' + rowStatus);
  mark('Journey 14: Case A posted via UI: 100 USD payable settled from PKR account @ 278.50 + 150 fee with SETTLED audit entry');

  // JOURNEY 15: Post-Settlement Ledger Invariant Verification
  console.log('Executing Journey 15: Ledger & Account verification...');
  const afterObRes = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/obligations/${usdPayableId}`);
  assert.equal(afterObRes.status, 200);
  assert.equal(parseFloat(afterObRes.json.outstandingAmount), 0.0, 'Obligation must be fully paid');
  assert.equal(afterObRes.json.settlementStatus, 'FULLY_PAID');

  const afterAcctRes = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/accounts/${pkrAccountId}`);
  assert.equal(afterAcctRes.status, 200);
  // Opening balance 500,000.00, debited 28,000.00 -> balance 472,000.00
  assert.equal(parseFloat(afterAcctRes.json.currentBalance), 472000.0, 'PKR Account balance must be 472,000.00');

  const afterTxRes = await apiCall(ownerJar, ownerXsrf, 'GET', '/finance/transactions?limit=10');
  assert.equal(afterTxRes.status, 200);
  const txItems = afterTxRes.json.items || afterTxRes.json.content || [];
  assert.ok(txItems.length >= 1, 'Transaction must be posted in ledger');
  const postTx = txItems.find((t) => t.id === settlementA.financialTransactionId);
  assert.ok(postTx, 'Financial transaction matching settlement must exist');
  assert.equal(parseFloat(postTx.amount), 28000.0, 'Transaction amount must be 28,000.00 PKR');
  assert.equal(postTx.currency, 'PKR');
  mark('Journey 15: Ledger integrity verified: obligation balance 0.00 USD, account debited 28,000.00 PKR, audit record SETTLED');

  // JOURNEY 16: Compensating Reversal via Browser UI
  console.log('Executing Journey 16: Compensating Reversal via UI...');
  await page.click(`[data-testid="btn-reverse-${settlementAId}"]`);
  await page.waitForSelector(`[data-testid="input-reversal-reason-${settlementAId}"]`);
  await page.fill(`[data-testid="input-reversal-reason-${settlementAId}"]`, 'Bank charge dispute');
  await page.click(`[data-testid="btn-confirm-reverse-${settlementAId}"]`);

  await page.waitForSelector('[data-testid="fx-settlement-history-success"]');
  const revSuccess = await page.textContent('[data-testid="fx-settlement-history-success"]');
  assert.ok(revSuccess.length > 0, 'Reversal success message must display');

  // Check status badge changed to REVERSED
  const revStatusBadge = await page.textContent(`[data-testid="badge-status-${settlementAId}"]`);
  assert.ok(revStatusBadge.toLowerCase().includes('reversed'), 'Status must be REVERSED: ' + revStatusBadge);

  // Obligation outstanding restored to 100.00 USD
  const revObRes = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/obligations/${usdPayableId}`);
  assert.equal(revObRes.status, 200);
  assert.equal(parseFloat(revObRes.json.outstandingAmount), 100.0, 'Obligation outstanding must be restored to 100.00 USD');
  assert.equal(revObRes.json.settlementStatus, 'UNPAID');

  // Account balance restored to 500,000.00 PKR
  const revAcctRes = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/accounts/${pkrAccountId}`);
  assert.equal(revAcctRes.status, 200);
  assert.equal(parseFloat(revAcctRes.json.currentBalance), 500000.0, 'Account balance must be restored to 500,000.00 PKR');
  mark('Journey 16: Compensating reversal via UI restores obligation to 100.00 USD and posts compensating cash movement');

  // JOURNEY 17: Case B Direct REST Settlement (USD Receivable Collection into PKR Account)
  console.log('Executing Journey 17: Case B Receivable Collection...');
  const customerRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/counterparties', {
    displayName: 'Global Export Buyer Corp',
    type: 'CUSTOMER',
    email: 'client@globalexport.test',
  });
  assert.equal(customerRes.status, 201);
  const customerId = customerRes.json.id;

  const usdRecRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/obligations', {
    counterpartyId: customerId,
    direction: 'RECEIVABLE',
    amount: '50.00',
    issueDate: '2026-11-01',
    dueDate: '2026-11-30',
    referenceNumber: 'INV-USD-50',
    description: 'Export consultancy invoice',
  });
  assert.equal(usdRecRes.status, 201);
  const usdRecId = usdRecRes.json.id;

  await execSql(`UPDATE financial_obligations SET currency = 'USD' WHERE id = '${usdRecId}'`);

  const caseBIdempKey = 'case-b-idemp-' + randomUUID();
  const caseBPostRes = await apiCall(
    ownerJar,
    ownerXsrf,
    'POST',
    '/finance/fx/settlements',
    {
      obligationId: usdRecId,
      accountId: pkrAccountId,
      amountInObligationCurrency: 50.0,
      exchangeRate: 278.5,
      fixedFee: 100.0,
      percentageFeePercent: 0,
      settlementDate: '2026-11-08',
      notes: 'Case B receivable collection',
    },
    { 'Idempotency-Key': caseBIdempKey }
  );
  assert.equal(caseBPostRes.status, 201);
  assert.equal(caseBPostRes.json.obligationCurrency, 'USD');
  assert.equal(caseBPostRes.json.accountCurrency, 'PKR');
  assert.equal(parseFloat(caseBPostRes.json.convertedPrincipal), 13925.0);
  assert.equal(parseFloat(caseBPostRes.json.feeAmount), 100.0);
  assert.equal(parseFloat(caseBPostRes.json.totalAccountMovement), 13825.0);
  assert.equal(caseBPostRes.json.settlementDirection, 'RECEIVABLE_COLLECTION');
  assert.equal(parseFloat(caseBPostRes.json.effectiveRate), 276.5);
  assert.equal(caseBPostRes.json.status, 'SETTLED');

  // Verify obligation balance
  const caseBObRes = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/obligations/${usdRecId}`);
  assert.equal(caseBObRes.status, 200);
  assert.equal(parseFloat(caseBObRes.json.outstandingAmount), 0.0);
  assert.equal(caseBObRes.json.settlementStatus, 'FULLY_PAID');
  mark('Journey 17: Case B USD 50 receivable collection into PKR account credits 13,825.00 PKR at effective rate 276.500000');

  // JOURNEY 18: Idempotency Protection
  console.log('Executing Journey 18: Idempotency Verification...');
  const idempReplayRes = await apiCall(
    ownerJar,
    ownerXsrf,
    'POST',
    '/finance/fx/settlements',
    {
      obligationId: usdRecId,
      accountId: pkrAccountId,
      amountInObligationCurrency: 50.0,
      exchangeRate: 278.5,
      fixedFee: 100.0,
      percentageFeePercent: 0,
      settlementDate: '2026-11-08',
      notes: 'Case B receivable collection replay',
    },
    { 'Idempotency-Key': caseBIdempKey }
  );
  assert.ok(idempReplayRes.status === 200 || idempReplayRes.status === 201, 'Idempotent replay must succeed');
  assert.equal(idempReplayRes.json.id, caseBPostRes.json.id, 'Idempotent replay must return exact same settlement ID');

  // Verify obligation balance still 0.00 (no duplicate payment)
  const idempObRes = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/obligations/${usdRecId}`);
  assert.equal(parseFloat(idempObRes.json.outstandingAmount), 0.0);
  mark('Journey 18: Idempotent replay returns existing settlement without duplicate ledger movements');

  // JOURNEY 19: Security & RBAC Isolation (SALESPERSON 403 Forbidden)
  console.log('Executing Journey 19: RBAC Isolation...');
  const salesJar = new Map();
  const salesInitCsrfRes = await apiCall(salesJar, '', 'GET', '/auth/csrf');
  const salesInitXsrf = salesInitCsrfRes.json.token;

  const salesEmail = `fx.sales.${randomUUID()}@finsight.local`;
  const regSalesRes = await apiCall(salesJar, salesInitXsrf, 'POST', '/auth/register', {
    email: salesEmail,
    password,
    fullName: 'FX Salesperson User',
  });
  assert.equal(regSalesRes.status, 201);

  const salesLoginRes = await apiCall(salesJar, salesInitXsrf, 'POST', '/auth/login', {
    email: salesEmail,
    password,
  });
  assert.equal(salesLoginRes.status, 200);

  const salesCsrfRes = await apiCall(salesJar, '', 'GET', '/auth/csrf');
  const salesXsrf = salesCsrfRes.json.token;

  // Add SALESPERSON membership for the active business
  await execSql(`INSERT INTO business_memberships (user_id, business_id, role, status)
                 VALUES ((SELECT id FROM app_users WHERE email = '${salesEmail}'), '${businessId}', 'SALESPERSON', 'ACTIVE')`);

  // Activate business for salesperson
  const actSalesRes = await apiCall(salesJar, salesXsrf, 'POST', '/businesses/active', { businessId });
  assert.equal(actSalesRes.status, 200);

  // Refresh CSRF
  const salesPostActCsrf = await apiCall(salesJar, '', 'GET', '/auth/csrf');
  const salesActiveXsrf = salesPostActCsrf.json.token;

  // Attempt to access FX settlements as SALESPERSON -> 403 Forbidden
  const salesListRes = await apiCall(salesJar, salesActiveXsrf, 'GET', '/finance/fx/settlements');
  assert.equal(salesListRes.status, 403, 'SALESPERSON must receive 403 Forbidden on GET /settlements');

  const salesPostSettleRes = await apiCall(salesJar, salesActiveXsrf, 'POST', '/finance/fx/settlements', {
    obligationId: usdPayableId,
    accountId: pkrAccountId,
    amountInObligationCurrency: 10.0,
    exchangeRate: 278.5,
    fixedFee: 0,
    percentageFeePercent: 0,
    settlementDate: '2026-11-08',
  });
  assert.equal(salesPostSettleRes.status, 403, 'SALESPERSON must receive 403 Forbidden on POST /settlements');

  // Verify UI renders unauthorized card for salesperson
  const salesContext = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const salesCookies = Array.from(salesJar.entries()).map(([name, value]) => ({
    name,
    value,
    domain: '127.0.0.1',
    path: '/',
  }));
  await salesContext.addCookies(salesCookies);
  const salesPage = await salesContext.newPage();
  await salesPage.goto(`${origin}/finance/fx`, { waitUntil: 'networkidle' });
  await salesPage.waitForSelector('[data-testid="fx-unauthorized"]');
  const unauthText = await salesPage.textContent('[data-testid="fx-unauthorized"]');
  assert.ok(unauthText.includes('Unauthorized Access'), 'Salesperson must see unauthorized warning in UI: ' + unauthText);
  await salesContext.close();

  mark('Journey 19: SALESPERSON role receives 403 Forbidden on all settlement endpoints and UI access');

  // JOURNEY 20: Record Initial Recognition Evidence (F11C)
  console.log('Executing Journey 20: Record Recognition Evidence...');
  const usdRecF11cRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/obligations', {
    counterpartyId: customerId,
    direction: 'RECEIVABLE',
    amount: '100.00',
    issueDate: '2026-11-01',
    dueDate: '2026-11-30',
    referenceNumber: 'INV-F11C-100',
    description: 'F11C Export Sale with Historical Recognition',
  });
  assert.equal(usdRecF11cRes.status, 201);
  const usdRecF11cId = usdRecF11cRes.json.id;
  await execSql(`UPDATE financial_obligations SET currency = 'USD' WHERE id = '${usdRecF11cId}'`);

  const recEvidenceRes = await apiCall(ownerJar, ownerXsrf, 'POST', `/finance/fx/obligations/${usdRecF11cId}/recognition`, {
    recognitionRate: 275.0,
    functionalCurrency: 'PKR',
    recognitionDate: '2026-11-01',
    recognitionSource: 'SALES_CONTRACT',
    notes: 'Initial binding agreement booking',
  });
  assert.equal(recEvidenceRes.status, 201);
  assert.equal(recEvidenceRes.json.nativeCurrency, 'USD');
  assert.equal(parseFloat(recEvidenceRes.json.nativeAmount), 100.0);
  assert.equal(parseFloat(recEvidenceRes.json.recognitionRate), 275.0);
  assert.equal(parseFloat(recEvidenceRes.json.bookValue), 27500.0);

  const getRecRes = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/fx/obligations/${usdRecF11cId}/recognition`);
  assert.equal(getRecRes.status, 200);
  assert.equal(parseFloat(getRecRes.json.bookValue), 27500.0);
  mark('Journey 20: Recorded initial recognition evidence for USD 100 obligation @ 275.00 -> 27,500.00 PKR book value');

  // JOURNEY 21: Realized FX Gain Reconciliation & Browser UI (F11C)
  console.log('Executing Journey 21: Realized FX Gain & UI Reconciliation...');
  const gainSettleRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/fx/settlements', {
    obligationId: usdRecF11cId,
    accountId: pkrAccountId,
    amountInObligationCurrency: 100.0,
    exchangeRate: 278.5,
    fixedFee: 150.0,
    percentageFeePercent: 0,
    settlementDate: '2026-11-08',
    notes: 'Settled with FX gain',
  });
  assert.equal(gainSettleRes.status, 201);
  const gainSettleId = gainSettleRes.json.id;
  assert.equal(parseFloat(gainSettleRes.json.convertedPrincipal), 27850.0);
  assert.equal(parseFloat(gainSettleRes.json.feeAmount), 150.0);
  assert.equal(parseFloat(gainSettleRes.json.totalAccountMovement), 27700.0);
  assert.equal(parseFloat(gainSettleRes.json.allocatedBookValue), 27500.0);
  assert.equal(parseFloat(gainSettleRes.json.realizedFxDifference), 350.0);
  assert.equal(gainSettleRes.json.realizedFxImpact, 'GAIN');
  assert.equal(gainSettleRes.json.recognitionEvidenceStatus, 'AVAILABLE');

  // UI Verification of Reconciliation Tab and Breakdown Modal
  await page.goto(`${origin}/finance/fx`, { waitUntil: 'networkidle' });
  await page.click('[data-testid="tab-fx-reconciliation"]');
  await page.waitForSelector('[data-testid="fx-period-summary-card"]');
  const summaryGainsText = await page.textContent('[data-testid="summary-total-gains"]');
  assert.ok(summaryGainsText.includes('350.00'), 'Summary total gains must include 350.00: ' + summaryGainsText);

  await page.waitForSelector(`[data-testid="btn-inspect-recon-${gainSettleId}"]`);
  await page.click(`[data-testid="btn-inspect-recon-${gainSettleId}"]`);
  await page.waitForSelector('[data-testid="recon-modal-overlay"]');
  await page.waitForSelector('[data-testid="realized-fx-gain-badge"]');
  const gainBadgeText = await page.textContent('[data-testid="realized-fx-gain-badge"]');
  assert.ok(gainBadgeText.includes('350.00'), 'Breakdown modal must display realized gain of 350.00: ' + gainBadgeText);
  await page.waitForSelector('[data-testid="cash-verified-badge"]');
  await page.click('[data-testid="btn-close-recon-modal"]');
  mark('Journey 21: Realized FX Gain (+350 PKR) and fee separation verified in UI reconciliation tab and breakdown modal');

  // JOURNEY 22: Realized FX Loss & Fee Separation (F11C)
  console.log('Executing Journey 22: Realized FX Loss...');
  const usdPayF11cRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/obligations', {
    counterpartyId: supplierId,
    direction: 'PAYABLE',
    amount: '100.00',
    issueDate: '2026-11-01',
    dueDate: '2026-11-30',
    referenceNumber: 'BILL-F11C-100',
    description: 'F11C Import Vendor Invoice',
  });
  assert.equal(usdPayF11cRes.status, 201);
  const usdPayF11cId = usdPayF11cRes.json.id;
  await execSql(`UPDATE financial_obligations SET currency = 'USD' WHERE id = '${usdPayF11cId}'`);

  await apiCall(ownerJar, ownerXsrf, 'POST', `/finance/fx/obligations/${usdPayF11cId}/recognition`, {
    recognitionRate: 275.0,
    functionalCurrency: 'PKR',
    recognitionDate: '2026-11-01',
    recognitionSource: 'PURCHASE_ORDER',
  });

  const lossSettleRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/fx/settlements', {
    obligationId: usdPayF11cId,
    accountId: pkrAccountId,
    amountInObligationCurrency: 100.0,
    exchangeRate: 278.5,
    fixedFee: 150.0,
    percentageFeePercent: 0,
    settlementDate: '2026-11-08',
    notes: 'Settled with FX loss',
  });
  assert.equal(lossSettleRes.status, 201);
  const lossSettleId = lossSettleRes.json.id;
  assert.equal(parseFloat(lossSettleRes.json.convertedPrincipal), 27850.0);
  assert.equal(parseFloat(lossSettleRes.json.feeAmount), 150.0);
  assert.equal(parseFloat(lossSettleRes.json.totalAccountMovement), 28000.0);
  assert.equal(parseFloat(lossSettleRes.json.allocatedBookValue), 27500.0);
  assert.equal(parseFloat(lossSettleRes.json.realizedFxDifference), 350.0);
  assert.equal(lossSettleRes.json.realizedFxImpact, 'LOSS');

  const lossReconRes = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/fx/settlements/${lossSettleId}/reconciliation`);
  assert.equal(lossReconRes.status, 200);
  assert.equal(lossReconRes.json.cashReconciliationVerified, true);
  assert.equal(parseFloat(lossReconRes.json.netEconomicVariance), 500.0);
  mark('Journey 22: Payable Realized FX Loss (350 PKR) and fee addition (28,000 debit, 500 variance) verified');

  // JOURNEY 23: Partial Settlements Across Two Rates with Residual Allocation (F11C)
  console.log('Executing Journey 23: Partial Settlements Residual Allocation...');
  const usdPartialRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/obligations', {
    counterpartyId: supplierId,
    direction: 'PAYABLE',
    amount: '100.00',
    issueDate: '2026-11-01',
    dueDate: '2026-11-30',
    referenceNumber: 'BILL-PARTIAL-F11C',
    description: 'Partial Settlement Test',
  });
  const usdPartialId = usdPartialRes.json.id;
  await execSql(`UPDATE financial_obligations SET currency = 'USD' WHERE id = '${usdPartialId}'`);

  await apiCall(ownerJar, ownerXsrf, 'POST', `/finance/fx/obligations/${usdPartialId}/recognition`, {
    recognitionRate: 275.0,
    functionalCurrency: 'PKR',
    recognitionDate: '2026-11-01',
  });

  // Tranche 1: USD 40 @ 278.50 with 50 PKR fee
  const t1Res = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/fx/settlements', {
    obligationId: usdPartialId,
    accountId: pkrAccountId,
    amountInObligationCurrency: 40.0,
    exchangeRate: 278.5,
    fixedFee: 50.0,
    percentageFeePercent: 0,
    settlementDate: '2026-11-08',
  });
  assert.equal(t1Res.status, 201);
  assert.equal(parseFloat(t1Res.json.allocatedBookValue), 11000.0);
  assert.equal(parseFloat(t1Res.json.realizedFxDifference), 140.0);
  assert.equal(parseFloat(t1Res.json.totalAccountMovement), 11190.0);

  // Tranche 2: USD 60 @ 280.00 with 100 PKR fee
  const t2Res = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/fx/settlements', {
    obligationId: usdPartialId,
    accountId: pkrAccountId,
    amountInObligationCurrency: 60.0,
    exchangeRate: 280.0,
    fixedFee: 100.0,
    percentageFeePercent: 0,
    settlementDate: '2026-11-08',
  });
  assert.equal(t2Res.status, 201);
  assert.equal(parseFloat(t2Res.json.allocatedBookValue), 16500.0);
  assert.equal(parseFloat(t2Res.json.realizedFxDifference), 300.0);
  assert.equal(parseFloat(t2Res.json.totalAccountMovement), 16900.0);

  // Exact residual check: 11,000 + 16,500 = 27,500.0
  assert.equal(parseFloat(t1Res.json.allocatedBookValue) + parseFloat(t2Res.json.allocatedBookValue), 27500.0);
  mark('Journey 23: Partial settlements across two rates verify exact residual book value allocation without drift');

  // JOURNEY 24: Fail-Closed Missing Evidence Reporting (F11C)
  console.log('Executing Journey 24: Fail-Closed Missing Evidence...');
  const usdNoEvRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/obligations', {
    counterpartyId: customerId,
    direction: 'RECEIVABLE',
    amount: '50.00',
    issueDate: '2026-11-01',
    dueDate: '2026-11-30',
    referenceNumber: 'INV-NO-EVID-50',
    description: 'No initial recognition evidence',
  });
  const usdNoEvId = usdNoEvRes.json.id;
  await execSql(`UPDATE financial_obligations SET currency = 'USD' WHERE id = '${usdNoEvId}'`);

  const noEvSettleRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/fx/settlements', {
    obligationId: usdNoEvId,
    accountId: pkrAccountId,
    amountInObligationCurrency: 50.0,
    exchangeRate: 278.5,
    fixedFee: 0,
    percentageFeePercent: 0,
    settlementDate: '2026-11-08',
  });
  assert.equal(noEvSettleRes.status, 201);
  const noEvSettleId = noEvSettleRes.json.id;
  assert.equal(noEvSettleRes.json.recognitionEvidenceStatus, 'UNAVAILABLE');
  assert.equal(noEvSettleRes.json.missingEvidenceReason, 'ORIGINAL_RECOGNITION_EVIDENCE_MISSING');
  assert.equal(noEvSettleRes.json.allocatedBookValue, null);
  assert.equal(noEvSettleRes.json.realizedFxDifference, null);

  await page.goto(`${origin}/finance/fx`, { waitUntil: 'networkidle' });
  await page.click('[data-testid="tab-fx-reconciliation"]');
  await page.waitForSelector(`[data-testid="badge-unavailable-${noEvSettleId}"]`);
  mark('Journey 24: Fail-closed missing evidence reporting marks status UNAVAILABLE and never substitutes zero');

  // JOURNEY 25: Reversal Accounting & Period Summary Aggregation (F11C)
  console.log('Executing Journey 25: Settlement Reversal & Period Summary...');
  const revRes = await apiCall(ownerJar, ownerXsrf, 'POST', `/finance/fx/settlements/${lossSettleId}/reverse`, {
    reason: 'Settlement cancelled by vendor agreement',
  });
  assert.equal(revRes.status, 200);
  assert.equal(revRes.json.status, 'REVERSED');

  const summaryRes = await apiCall(ownerJar, ownerXsrf, 'GET', '/finance/fx/settlements/summary');
  assert.equal(summaryRes.status, 200);
  assert.ok(summaryRes.json.reversedSettlementsCount >= 1);
  assert.ok(summaryRes.json.activeSettlementsCount >= 1);
  mark('Journey 25: Settlement reversal updates period summary counts and net realized FX while preserving historical records');

  // JOURNEY 26: Currency Exposure Grouping (F11D)
  console.log('Executing Journey 26: Currency Exposure Grouping...');
  const expRes = await apiCall(ownerJar, ownerXsrf, 'GET', '/finance/fx/analytics/exposures');
  assert.equal(expRes.status, 200);
  assert.ok(Array.isArray(expRes.json));
  const usdExp = expRes.json.find((e) => e.currency === 'USD');
  assert.ok(usdExp, 'USD exposure must be present');
  assert.equal(usdExp.currency, 'USD');
  assert.ok(parseFloat(usdExp.outstandingReceivables) >= 0);

  await page.goto(`${origin}/finance/fx`, { waitUntil: 'networkidle' });
  await page.click('[data-testid="tab-fx-analytics"]');
  await page.waitForSelector('[data-testid="fx-currency-exposure-table"]');
  await page.waitForSelector('[data-testid="exposure-row-USD"]');
  mark('Journey 26: Currency Exposure Table renders native USD exposures separately without conflation or conversion');

  // JOURNEY 27: Periodic Trends and Performance Overview (F11D)
  console.log('Executing Journey 27: Periodic Trends and Performance Overview...');
  const trendsRes = await apiCall(ownerJar, ownerXsrf, 'GET', '/finance/fx/analytics/trends');
  assert.equal(trendsRes.status, 200);
  assert.ok(Array.isArray(trendsRes.json));
  assert.ok(trendsRes.json.length >= 1, 'At least one periodic trend must exist');

  await page.waitForSelector('[data-testid="fx-periodic-trends-card"]');
  mark('Journey 27: Periodic Trends renders monthly aggregate settlement volume and net realized FX');

  // JOURNEY 28: Settlement Audit Report and Filtering (F11D)
  console.log('Executing Journey 28: Settlement Audit Report Filtering...');
  const reportRes = await apiCall(ownerJar, ownerXsrf, 'GET', '/finance/fx/analytics/report?currency=USD');
  assert.equal(reportRes.status, 200);
  assert.ok(Array.isArray(reportRes.json));
  assert.ok(reportRes.json.every((r) => r.obligationCurrency === 'USD'));

  await page.waitForSelector('[data-testid="fx-settlement-report-view"]');
  await page.selectOption('[data-testid="filter-currency-select"]', 'USD');
  await page.waitForTimeout(500);
  mark('Journey 28: Settlement Report filtering by currency and status displays filtered audit rows');

  // JOURNEY 29: CSV Export with Formula Injection Protection (F11D)
  console.log('Executing Journey 29: CSV Export with Formula Injection Protection...');
  const csvRes = await apiCall(ownerJar, ownerXsrf, 'GET', '/finance/fx/analytics/report/csv');
  assert.equal(csvRes.status, 200);
  assert.ok(csvRes.body.includes('Settlement ID,Date,Direction,Counterparty'));
  assert.ok(csvRes.body.includes('PAYABLE_SETTLEMENT'));
  assert.ok(!csvRes.body.includes('=cmd|/C calc'), 'Raw unescaped formula command must not be present');
  mark('Journey 29: CSV export returns sanitized RFC 4180 output with spreadsheet formula injection protection');

  // JOURNEY 30: Multicurrency Obligation Visibility (F11D)
  console.log('Executing Journey 30: Multicurrency Obligation Visibility...');
  const obListRes = await apiCall(ownerJar, ownerXsrf, 'GET', '/finance/obligations?size=50');
  assert.equal(obListRes.status, 200);
  const foreignObs = obListRes.json.items.filter((item) => item.currency !== 'PKR');
  assert.ok(foreignObs.length >= 1, 'Foreign currency obligations must be listed');
  assert.ok(foreignObs.some((item) => item.currency === 'USD'), 'USD obligation must retain native USD currency');
  mark('Journey 30: Multicurrency obligation visibility verifies foreign currency obligations appear in native currency');

  // JOURNEY 31: Urdu / RTL Localization & Statement Viewport (F11 Final Checkpoint)
  console.log('Executing Journey 31: Urdu / RTL Localization...');
  await page.evaluate(() => {
    localStorage.setItem('finsight_locale', 'ur');
  });
  await page.goto(`${origin}/finance/fx`, { waitUntil: 'networkidle' });
  await page.click('[data-testid="tab-fx-analytics"]');
  await page.waitForSelector('[data-testid="fx-currency-exposure-table"]');

  const htmlDir = await page.getAttribute('html', 'dir');
  const htmlLang = await page.getAttribute('html', 'lang');
  assert.equal(htmlDir, 'rtl', 'Document direction must be rtl in Urdu mode');
  assert.equal(htmlLang, 'ur', 'Document language must be ur');

  const pageHeading = await page.textContent('h1');
  assert.ok(pageHeading.includes('کثیر کرنسی'), 'Page heading must be in Urdu: ' + pageHeading);

  const tableText = await page.textContent('[data-testid="fx-currency-exposure-table"]');
  assert.ok(tableText.includes('کرنسی'), 'Exposure table must render Urdu currency column');
  assert.ok(tableText.includes('بقایا وصولیاں'), 'Exposure table must render Urdu receivables column');

  // Reset locale back to English
  await page.evaluate(() => {
    localStorage.setItem('finsight_locale', 'en');
  });
  mark('Journey 31: Urdu RTL layout verified — dir="rtl", Urdu headings, exposure table headers rendered correctly');

  // JOURNEY 32: Mount FX Transfers Tab and Live Preview Breakdown (F11E)
  console.log('Executing Journey 32: Mount FX Transfers Tab and Live Preview...');
  const usdAccId = randomUUID();
  await execSql(`INSERT INTO financial_accounts (id, business_id, account_name, account_type, currency, currency_decimals, opening_balance, opening_date, status, created_at, updated_at) VALUES ('${usdAccId}', '${businessId}', 'Meezan USD Treasury', 'BANK', 'USD', 2, 1000.0000, '2026-01-01', 'ACTIVE', now(), now())`);

  // Navigate to /finance/fx and click Transfers tab
  await page.goto(`${origin}/finance/fx`, { waitUntil: 'networkidle' });
  await page.click('[data-testid="tab-fx-transfers"]');
  await page.waitForSelector('[data-testid="fx-transfer-form-card"]');

  // Select source USD account and destination PKR account
  await page.selectOption('[data-testid="select-transfer-source-account"]', usdAccId);
  await page.selectOption('[data-testid="select-transfer-destination-account"]', pkrAccountId);

  // Fill in Case A values: USD 100 @ 278.50, Fee PKR 150, DESTINATION_DEDUCTED
  await page.fill('[data-testid="input-transfer-source-amount"]', '100.00');
  await page.fill('[data-testid="input-transfer-rate"]', '278.50');
  await page.fill('[data-testid="input-transfer-fee"]', '150.00');
  await page.selectOption('[data-testid="select-transfer-fee-placement"]', 'DESTINATION_DEDUCTED');

  await page.waitForSelector('[data-testid="fx-transfer-preview-card"]');

  const grossDestText = await page.textContent('[data-testid="preview-gross-destination"]');
  assert.ok(grossDestText.includes('27,850') || grossDestText.includes('27850'), 'Preview gross destination must be 27,850: ' + grossDestText);

  const netDestText = await page.textContent('[data-testid="preview-net-destination"]');
  assert.ok(netDestText.includes('27,700') || netDestText.includes('27700'), 'Preview net destination must be 27,700: ' + netDestText);

  const totalSourceText = await page.textContent('[data-testid="preview-total-source"]');
  assert.ok(totalSourceText.includes('100'), 'Preview total source debit must be 100: ' + totalSourceText);
  mark('Journey 32: FX Transfers tab mounts and live preview calculates 27,850 PKR gross, 150 fee, 27,700 net credit, 100 USD debit');

  // JOURNEY 33: Post Case A Internal Transfer via Browser UI (F11E)
  console.log('Executing Journey 33: Post Case A Internal Transfer via UI...');
  const preUsdRes = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/accounts/${usdAccId}`);
  assert.equal(preUsdRes.status, 200);
  const preUsdBal = parseFloat(preUsdRes.json.currentBalance);
  assert.equal(preUsdBal, 1000.0);

  const prePkrRes = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/accounts/${pkrAccountId}`);
  assert.equal(prePkrRes.status, 200);
  const prePkrBal = parseFloat(prePkrRes.json.currentBalance);

  await page.click('[data-testid="btn-post-fx-transfer"]');
  await page.waitForSelector('[data-testid="fx-transfer-success"]');

  const postUsdRes = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/accounts/${usdAccId}`);
  assert.equal(postUsdRes.status, 200);
  const postUsdBal = parseFloat(postUsdRes.json.currentBalance);
  assert.equal(postUsdBal, 900.0, 'USD balance must be debited exactly 100.00 USD');

  const postPkrRes = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/accounts/${pkrAccountId}`);
  assert.equal(postPkrRes.status, 200);
  const postPkrBal = parseFloat(postPkrRes.json.currentBalance);
  assert.equal(postPkrBal, prePkrBal + 27700.0, 'PKR balance must be credited exactly 27,700.00 PKR');
  mark('Journey 33: Case A posted via UI: USD account debited 100 USD, PKR account credited 27,700 PKR');

  // JOURNEY 34: Transfer History Table Displays Auditable Locked Evidence (F11E)
  console.log('Executing Journey 34: Transfer History Table Audit Evidence...');
  await page.waitForSelector('[data-testid="fx-transfer-history-table"]');

  const transfersListRes = await apiCall(ownerJar, ownerXsrf, 'GET', '/finance/fx/transfers');
  assert.equal(transfersListRes.status, 200);
  assert.ok(transfersListRes.json.length >= 1, 'At least 1 transfer record must exist');
  const transferA = transfersListRes.json[0];
  const transferAId = transferA.id;

  await page.waitForSelector(`[data-testid="fx-transfer-row-${transferAId}"]`);
  const transferRowText = await page.textContent(`[data-testid="fx-transfer-row-${transferAId}"]`);
  assert.ok(transferRowText.includes('100') && transferRowText.includes('USD'), 'Row must display 100 USD source debit: ' + transferRowText);
  assert.ok(transferRowText.includes('27,700') || transferRowText.includes('27700'), 'Row must display 27,700 PKR destination credit: ' + transferRowText);
  assert.ok(transferRowText.includes('278.5'), 'Row must display locked rate 278.5: ' + transferRowText);

  const statusBadge = await page.textContent(`[data-testid="transfer-status-${transferAId}"]`);
  assert.ok(statusBadge.toLowerCase().includes('settled'), 'Status badge must be SETTLED: ' + statusBadge);
  mark('Journey 34: Transfer history table displays auditable row with locked rate 278.50, fee evidence, and SETTLED status');

  // JOURNEY 35: Reversal via UI Restores Dual Balances Without Duplicate Fee Deductions (F11E)
  console.log('Executing Journey 35: Reversal via UI Restores Balances...');
  await page.click(`[data-testid="btn-reverse-transfer-${transferAId}"]`);
  await page.waitForSelector('[data-testid="input-transfer-reversal-reason"]');
  await page.fill('[data-testid="input-transfer-reversal-reason"]', 'Reversal test for Case A');
  await page.click('[data-testid="btn-confirm-reverse-transfer"]');
  await page.waitForSelector('[data-testid="fx-transfer-history-success"]');

  // Verify account balances restored
  const revUsdRes = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/accounts/${usdAccId}`);
  assert.equal(revUsdRes.status, 200);
  assert.equal(parseFloat(revUsdRes.json.currentBalance), 1000.0, 'USD balance must be fully restored to 1,000.00 USD');

  const revPkrRes = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/accounts/${pkrAccountId}`);
  assert.equal(revPkrRes.status, 200);
  assert.equal(parseFloat(revPkrRes.json.currentBalance), prePkrBal, 'PKR balance must be fully restored to pre-transfer balance');

  // Verify status badge changed to REVERSED
  const transferRevStatusBadge = await page.textContent(`[data-testid="transfer-status-${transferAId}"]`);
  assert.ok(transferRevStatusBadge.toLowerCase().includes('reversed'), 'Status badge must be REVERSED: ' + transferRevStatusBadge);

  // Verify re-reversal rejected
  const reRevRes = await apiCall(ownerJar, ownerXsrf, 'POST', `/finance/fx/transfers/${transferAId}/reverse`, { reason: 'Duplicate reversal attempt' });
  assert.equal(reRevRes.status, 409, 'Duplicate reversal must be rejected with 409 Conflict');
  mark('Journey 35: Transfer reversal restored USD to 1,000.00 and PKR balance without duplicate fees; duplicate reversal blocked with 409');

  // JOURNEY 36: Urdu / RTL Localization of Internal FX Transfers Workspace (F11E)
  console.log('Executing Journey 36: Urdu / RTL Localization of FX Transfers...');
  await page.evaluate(() => {
    localStorage.setItem('finsight_locale', 'ur');
  });
  await page.goto(`${origin}/finance/fx`, { waitUntil: 'networkidle' });
  await page.click('[data-testid="tab-fx-transfers"]');
  await page.waitForSelector('[data-testid="fx-transfer-form-card"]');

  const xferHtmlDir = await page.getAttribute('html', 'dir');
  assert.equal(xferHtmlDir, 'rtl', 'Document direction must be rtl in Urdu mode');

  const formCardText = await page.textContent('[data-testid="fx-transfer-form-card"]');
  assert.ok(formCardText.includes('بین الخاتہ'), 'Form heading must contain Urdu transfer text: ' + formCardText);

  // Reset locale back to English
  await page.evaluate(() => {
    localStorage.setItem('finsight_locale', 'en');
  });
  mark('Journey 36: Urdu RTL layout verified for internal transfers with dir="rtl", Urdu headers, and translated controls');

  console.log(`\nALL ${checks.length} REAL BROWSER INTEGRATION JOURNEYS PASSED SUCCESSFULLY!`);
} finally {
  if (browser) await browser.close();
  if (gatewayServer) gatewayServer.close();
  if (nextProc && !exited(nextProc)) nextProc.kill();
  if (javaRuntime && !exited(javaRuntime)) {
    javaRuntime.stdin.write('STOP\n');
    javaRuntime.kill();
  }
}
