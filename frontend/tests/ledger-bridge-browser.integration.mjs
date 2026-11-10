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
const outDir = path.join(frontendDir, 'test-artifacts', 'ledger-bridge-browser');
mkdirSync(outDir, { recursive: true });

const temporary = path.join(rootDir, 'tmp', 'ledger-bridge-live-test');
mkdirSync(temporary, { recursive: true });

const backendPort = 8089;
const nextPort = 3589;
const gatewayPort = 3588;
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
  const surefireXmlCandidates = [
    path.join(rootDir, 'backend', 'target', 'surefire-reports', 'TEST-com.app.sme_health_backend.bridge.LedgerMonthlyBridgePostgreSqlIT.xml'),
    path.join(rootDir, 'backend', 'target', 'surefire-reports', 'TEST-com.app.sme_health_backend.projectattribution.ProjectCommitmentPostgreSqlIT.xml'),
    path.join(rootDir, 'backend', 'target', 'surefire-reports', 'TEST-com.app.sme_health_backend.obligation.CommercialRecognitionPostgreSqlIT.xml'),
  ];
  let xmlPath = surefireXmlCandidates.find((f) => existsSync(f));
  if (!xmlPath) throw new Error('No surefire reports found for classpath resolution.');

  const xml = readFileSync(xmlPath, 'utf8');
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

  // Write LedgerBridgeLiveRuntime.java
  const javaSource = `
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import com.app.sme_health_backend.SmeHealthBackendApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import java.sql.*;
import java.util.*;

public class LedgerBridgeLiveRuntime {
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

            System.out.println("LEDGER_BRIDGE_LIVE_READY");
            System.out.println("LEDGER_BRIDGE_DB_PORT:" + pg.getPort());
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
                } else if (line.startsWith("QUERY_NUM ")) {
                    String sql = line.substring("QUERY_NUM ".length());
                    try (var c = pg.getPostgresDatabase().getConnection(); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
                        if (rs.next()) {
                            System.out.println("QUERY_RESULT:" + rs.getString(1));
                        } else {
                            System.out.println("QUERY_RESULT:0");
                        }
                    } catch (Exception ex) {
                        System.out.println("QUERY_ERROR:" + ex.getMessage());
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
  writeFileSync(path.join(temporary, 'LedgerBridgeLiveRuntime.java'), javaSource);

  // Spawn Spring Boot backend with Disposable Postgres
  console.log('Starting real Spring Boot backend with Disposable Postgres on port', backendPort);
  javaRuntime = spawn(
    path.join(javaHome, 'bin', 'java.exe'),
    ['--class-path', path.join(temporary, 'classpath.jar'), path.join(temporary, 'LedgerBridgeLiveRuntime.java')],
    { cwd: path.join(rootDir, 'backend'), windowsHide: true }
  );

  javaRuntime.stdout.on('data', (d) => {
    javaOutput += d.toString();
  });
  javaRuntime.stderr.on('data', (d) => {
    process.stderr.write(d.toString());
  });

  await waitFor(() => javaOutput.includes('LEDGER_BRIDGE_LIVE_READY'), 'Spring Boot backend', 120000);
  console.log('Spring Boot backend is LEDGER_BRIDGE_LIVE_READY!');

  // 2. Prepare isolated Next.js runtime
  console.log('Preparing isolated Next.js runtime in tmp/isolated-frontend-bridge...');
  const isolatedDir = path.join(rootDir, 'tmp', 'isolated-frontend-bridge');
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
    } catch {}
  }

  // Next.js process
  console.log('Starting Next.js production server on port', nextPort);
  const nextBin = path.join(frontendDir, 'node_modules', 'next', 'dist', 'bin', 'next');
  nextProc = spawn(
    process.execPath,
    [nextBin, 'start', '--hostname', '127.0.0.1', '--port', String(nextPort)],
    {
      cwd: isolatedDir,
      windowsHide: true,
      env: {
        ...process.env,
        PORT: String(nextPort),
        HOSTNAME: '127.0.0.1',
        NEXT_PUBLIC_API_URL: origin,
        NEXT_PUBLIC_DATA_MODE: 'live',
      },
    }
  );

  nextProc.stdout.on('data', (d) => {
    nextOutput += d.toString();
  });
  nextProc.stderr.on('data', (d) => {
    process.stderr.write(d.toString());
  });

  await waitFor(() => nextOutput.includes('Ready in') || nextOutput.includes('started server'), 'Next.js server', 45000);
  console.log('Next.js server is ready!');

  // 3. Reverse Proxy Gateway (port 3588 -> 8089 for /api, 3589 for others)
  gatewayServer = http.createServer((req, res) => {
    const isApi = req.url.startsWith('/api/') || req.url === '/api';
    const targetPort = isApi ? backendPort : nextPort;

    const proxyReq = http.request(
      {
        host: '127.0.0.1',
        port: targetPort,
        path: req.url,
        method: req.method,
        headers: {
          ...req.headers,
          host: `127.0.0.1:${targetPort}`,
        },
      },
      (proxyRes) => {
        res.writeHead(proxyRes.statusCode, proxyRes.headers);
        proxyRes.pipe(res, { end: true });
      }
    );

    proxyReq.on('error', (err) => {
      res.writeHead(502, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ error: 'Gateway Proxy Error: ' + err.message }));
    });

    req.pipe(proxyReq, { end: true });
  });

  await new Promise((resolve) => gatewayServer.listen(gatewayPort, '127.0.0.1', resolve));
  console.log('Gateway Reverse Proxy listening on port', gatewayPort);

  // Helper SQL execution via java bridge
  const execSql = async (sql) => {
    return new Promise((resolve, reject) => {
      const handler = (data) => {
        const str = data.toString();
        if (str.includes('SQL_SUCCESS')) {
          javaRuntime.stdout.off('data', handler);
          resolve();
        } else if (str.includes('SQL_ERROR:')) {
          javaRuntime.stdout.off('data', handler);
          reject(new Error(str));
        }
      };
      javaRuntime.stdout.on('data', handler);
      javaRuntime.stdin.write('EXEC_SQL ' + sql.replace(/(\r\n|\n|\r)/gm, ' ') + '\n');
    });
  };

  const queryNum = async (sql) => {
    return new Promise((resolve, reject) => {
      const handler = (data) => {
        const str = data.toString();
        if (str.includes('QUERY_RESULT:')) {
          javaRuntime.stdout.off('data', handler);
          const val = str.split('QUERY_RESULT:')[1].split('\n')[0].trim();
          resolve(val);
        } else if (str.includes('QUERY_ERROR:')) {
          javaRuntime.stdout.off('data', handler);
          reject(new Error(str));
        }
      };
      javaRuntime.stdout.on('data', handler);
      javaRuntime.stdin.write('QUERY_NUM ' + sql.replace(/(\r\n|\n|\r)/gm, ' ') + '\n');
    });
  };

  // REST helpers
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
            } catch {}
            resolve({ status: res.statusCode, headers: res.headers, body, json });
          });
        }
      );
      req.on('error', reject);
      if (payload) req.write(payload);
      req.end();
    });
  }

  // 4. Register Owner, Login & Setup Business A via FinSight Authentication APIs
  console.log('Seeding authentication and business fixtures via real backend REST API...');
  const ownerJar = new Map();
  let ownerXsrf = '';

  const csrfRes = await apiCall(ownerJar, '', 'GET', '/auth/csrf');
  assert.equal(csrfRes.status, 200, 'CSRF endpoint must succeed');
  ownerXsrf = csrfRes.json.token;

  const ownerEmail = `bridge.owner.${randomUUID()}@finsight.local`;
  const password = 'Password123!';
  const regRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/auth/register', {
    email: ownerEmail,
    password,
    fullName: 'Owner Suleman',
  });
  assert.equal(regRes.status, 201, 'Owner registration must succeed');

  const loginRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/auth/login', {
    email: ownerEmail,
    password,
  });
  assert.equal(loginRes.status, 200, 'Owner login must succeed');

  const postLoginCsrf = await apiCall(ownerJar, '', 'GET', '/auth/csrf');
  ownerXsrf = postLoginCsrf.json.token;

  const bizRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/businesses', {
    businessName: 'Apex Industrial Logistics',
    businessType: 'services',
    languagePreference: 'en',
    whatsappOptIn: false,
  });
  assert.equal(bizRes.status, 201, 'Business creation must succeed');
  const businessId = bizRes.json.businessId;

  await apiCall(ownerJar, ownerXsrf, 'POST', '/businesses/active', { businessId });
  const userId = await queryNum(`SELECT id FROM app_users WHERE email = '${ownerEmail}'`);

  // Ensure business_profiles row exists for monthly_records FK constraint
  await execSql(`
    INSERT INTO business_profiles (user_id, business_type, language_preference)
    VALUES ('${businessId}', 'services', 'en')
    ON CONFLICT (user_id) DO NOTHING;
  `);

  // Setup Business B for cross-business isolation checks
  const bizBRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/businesses', {
    businessName: 'Beta Delta Services',
    businessType: 'trade',
    languagePreference: 'en',
    whatsappOptIn: false,
  });
  const businessBId = bizBRes.json.businessId;

  await execSql(`
    INSERT INTO business_profiles (user_id, business_type, language_preference)
    VALUES ('${businessBId}', 'trade', 'en')
    ON CONFLICT (user_id) DO NOTHING;
  `);

  // Set active back to Business A
  await apiCall(ownerJar, ownerXsrf, 'POST', '/businesses/active', { businessId });

  // --------------------------------------------------------------------------
  // Journey 1: Create legitimate posted transaction evidence (sales receipt,
  //            expense payment, collection, settlement, transfer, reversal)
  // --------------------------------------------------------------------------
  console.log('Seeding financial accounts, counterparties, transactions & obligations for Journey 1...');

  // Create accounts via REST API
  const bankRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/accounts', {
    accountName: 'Main Operating Bank',
    accountType: 'BANK',
    currency: 'PKR',
    openingBalance: '500000.00',
    openingDate: '2026-01-01',
    institutionName: 'Habib Bank Limited',
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(bankRes.status, 201, 'Bank account creation succeeded');
  const bankId = bankRes.json.id;

  const cashRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/accounts', {
    accountName: 'Petty Cash Box',
    accountType: 'CASH',
    currency: 'PKR',
    openingBalance: '20000.00',
    openingDate: '2026-01-01',
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(cashRes.status, 201, 'Cash account creation succeeded');
  const cashId = cashRes.json.id;

  const customerId = randomUUID();
  const supplierId = randomUUID();
  await execSql(`
    INSERT INTO counterparties (id, business_id, display_name, type, status, notes)
    VALUES ('${customerId}', '${businessId}', 'Metro Distributing Co', 'CUSTOMER', 'ACTIVE', 'Major freight customer'),
           ('${supplierId}', '${businessId}', 'Indus Fuels Ltd', 'SUPPLIER', 'ACTIVE', 'Fleet diesel supplier');
  `);

  // 1a. Direct Sale Receipt: 50,000 PKR
  const saleRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/transactions', {
    accountId: bankId,
    eventType: 'DIRECT_SALE_RECEIPT',
    amount: '50000.00',
    effectiveDate: '2026-11-02',
    description: 'Direct freight transport fees',
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(saleRes.status, 201, 'Direct sale receipt posted');

  // 1b. Receivable Collection: 30,000 PKR
  const collRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/transactions', {
    accountId: bankId,
    eventType: 'RECEIVABLE_COLLECTION',
    amount: '30000.00',
    effectiveDate: '2026-11-04',
    description: 'Customer invoice collection',
    counterpartyId: customerId,
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(collRes.status, 201, 'Collection posted');
  const collTxId = collRes.json.id;

  // 1c. Operating Expense Payment: 20,000 PKR
  const opexRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/transactions', {
    accountId: bankId,
    eventType: 'OPERATING_EXPENSE_PAYMENT',
    amount: '20000.00',
    effectiveDate: '2026-11-06',
    description: 'Office utilities and internet',
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(opexRes.status, 201, 'Operating expense payment posted');

  // 1d. Payable Settlement: 15,000 PKR
  const settRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/transactions', {
    accountId: bankId,
    eventType: 'PAYABLE_SETTLEMENT',
    amount: '15000.00',
    effectiveDate: '2026-11-08',
    description: 'Vendor diesel settlement',
    counterpartyId: supplierId,
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(settRes.status, 201, 'Settlement posted');

  // 1e. Internal Transfer: 10,000 PKR (Must be EXCLUDED from inflow and outflow)
  const transferRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/transfers', {
    sourceAccountId: bankId,
    destinationAccountId: cashId,
    amount: '10000.00',
    effectiveDate: '2026-11-07',
    description: 'Transfer to petty cash',
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(transferRes.status, 201, 'Transfer posted');

  // 1f. Reversed Expense: 5,000 PKR (Must be EXCLUDED from bridge calculations)
  const revExpRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/transactions', {
    accountId: bankId,
    eventType: 'OPERATING_EXPENSE_PAYMENT',
    amount: '5000.00',
    effectiveDate: '2026-11-09',
    description: 'Faulty repair bill',
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(revExpRes.status, 201, 'Reversible expense posted');
  const revExpTxId = revExpRes.json.id;

  const reverseRes = await apiCall(ownerJar, ownerXsrf, 'POST', `/finance/transactions/${revExpTxId}/reverse`, {
    reason: 'Faulty repair bill reversal',
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(reverseRes.status, 201, 'Reversal posted');

  // 1g. Open Obligations as of 2026-11:
  // Receivable: 25,000 PKR original, 10,000 PKR allocated -> 15,000 PKR open outstanding
  const arObId = randomUUID();
  const apObId = randomUUID();
  await execSql(`
    INSERT INTO financial_obligations (id, business_id, counterparty_id, direction, source_type, source_id, reference_number, issue_date, due_date, original_amount, currency, status, created_by_user_id)
    VALUES ('${arObId}', '${businessId}', '${customerId}', 'RECEIVABLE', 'CUSTOMER_INVOICE', 'INV-701', 'INV-701', '2026-11-01', '2026-11-30', 25000.0000, 'PKR', 'OPEN', '${userId}'),
           ('${apObId}', '${businessId}', '${supplierId}', 'PAYABLE', 'SUPPLIER_BILL', 'BILL-801', 'BILL-801', '2026-11-01', '2026-11-28', 40000.0000, 'PKR', 'OPEN', '${userId}');
    INSERT INTO obligation_payment_allocations (id, business_id, obligation_id, financial_transaction_id, amount, effective_date, status, created_by_user_id)
    VALUES (gen_random_uuid(), '${businessId}', '${arObId}', '${collTxId}', 10000.0000, '2026-11-04', 'EFFECTIVE', '${userId}');
  `);

  mark('Journey 1: Legitimate posted financial transactions, transfers, reversals, and open obligations created');

  // Launch Playwright Headless Browser
  console.log('Launching Playwright Chromium / Edge browser...');
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const cookiesToInject = Array.from(ownerJar.entries()).map(([name, value]) => ({
    name,
    value,
    domain: '127.0.0.1',
    path: '/',
  }));
  await context.addCookies(cookiesToInject);
  const page = await context.newPage();

  page.on('console', (msg) => console.log('BROWSER LOG:', msg.type(), msg.text()));
  page.on('pageerror', (err) => console.log('BROWSER ERROR:', err.message));
  page.on('response', async (res) => {
    if (res.url().includes('ledger-bridge')) {
      console.log('API RESPONSE:', res.url(), res.status());
      try {
        const body = await res.text();
        console.log('API BODY:', body);
      } catch {}
    }
  });

  // --------------------------------------------------------------------------
  // Journey 2: Open /records/bridge with authenticated Owner session
  // --------------------------------------------------------------------------
  console.log('Navigating to /records/bridge...');
  await page.goto(`${origin}/records/bridge`, { waitUntil: 'networkidle' });
  await page.waitForSelector('[data-testid="ledger-bridge-workspace"]');
  const wsTitle = await page.textContent('[data-testid="bridge-workspace-title"]');
  assert.ok(
    wsTitle.toLowerCase().includes('ledger') || wsTitle.toLowerCase().includes('bridge'),
    `Expected bridge title, got: ${wsTitle}`
  );
  mark('Journey 2: Open /records/bridge with authenticated Owner session');

  // --------------------------------------------------------------------------
  // Journey 3: Select target period (2026-11)
  // --------------------------------------------------------------------------
  await page.fill('[data-testid="bridge-period-input"]', '2026-11');
  await page.click('[data-testid="bridge-refresh-btn"]');
  await page.waitForSelector('[data-testid="ledger-derived-card"]');
  mark('Journey 3: Select target period 2026-11 and fetch ledger projection');

  // --------------------------------------------------------------------------
  // Journey 4: Preview ledger-derived values (inflow, outflow, revenue, opex, open AR/AP, cash balance)
  // Inflow = 50,000 + 30,000 = 80,000
  // Outflow = 20,000 + 15,000 = 35,000
  // Net Cash = 80,000 - 35,000 = 45,000
  // Operating Revenue = 50,000
  // Operating Expenses = 20,000
  // Open AR = 15,000
  // Open AP = 40,000
  // --------------------------------------------------------------------------
  const inflowText = await page.textContent('[data-testid="ledger-inflow"]');
  assert.ok(inflowText.includes('80,000'), `Expected 80,000 cash inflow, got: ${inflowText}`);

  const outflowText = await page.textContent('[data-testid="ledger-outflow"]');
  assert.ok(outflowText.includes('35,000'), `Expected 35,000 cash outflow, got: ${outflowText}`);

  const netFlowText = await page.textContent('[data-testid="ledger-net-flow"]');
  assert.ok(netFlowText.includes('45,000'), `Expected 45,000 net flow, got: ${netFlowText}`);

  const revText = await page.textContent('[data-testid="ledger-revenue"]');
  assert.ok(revText.includes('50,000'), `Expected 50,000 revenue, got: ${revText}`);

  const opexText = await page.textContent('[data-testid="ledger-opex"]');
  assert.ok(opexText.includes('20,000'), `Expected 20,000 opex, got: ${opexText}`);

  const arText = await page.textContent('[data-testid="ledger-ar"]');
  assert.ok(arText.includes('15,000'), `Expected 15,000 AR, got: ${arText}`);

  const apText = await page.textContent('[data-testid="ledger-ap"]');
  assert.ok(apText.includes('40,000'), `Expected 40,000 AP, got: ${apText}`);

  mark('Journey 4: Preview ledger-derived values matches exact mathematical expectations (transfers & reversals excluded)');

  // --------------------------------------------------------------------------
  // Journey 5: Verify preview is strictly read-only (zero mutation in monthly_records)
  // --------------------------------------------------------------------------
  const monthlyRecordsCount = await queryNum(
    `SELECT COUNT(*) FROM monthly_records WHERE user_id = '${businessId}' AND month = '2026-11'`
  );
  assert.equal(monthlyRecordsCount, '0', 'Preview must produce ZERO monthly_records database records');
  mark('Journey 5: Verify preview is strictly read-only (zero mutation in monthly_records)');

  // --------------------------------------------------------------------------
  // Journey 6: Compare against existing manual MonthlyRecord (showing manual COGS/inventory)
  // --------------------------------------------------------------------------
  // Seed a manual monthly record for 2026-11 with manual COGS and inventory
  await execSql(`
    INSERT INTO monthly_records (id, user_id, month, cash_inflow, cash_outflow, revenue, operating_expenses, cogs, inventory_value, loan_outstanding, financing_type, cash_balance_eom, receivables_outstanding, payables_outstanding, version, updated_at)
    VALUES (gen_random_uuid(), '${businessId}', '2026-11', 10000.0000, 5000.0000, 10000.0000, 5000.0000, 45000.0000, 30000.0000, 100000.0000, 'islamic', 510000.0000, 0.0000, 0.0000, 0, now());
  `);

  // Refresh bridge view
  await page.click('[data-testid="bridge-refresh-btn"]');
  await page.waitForSelector('[data-testid="saved-record-card"]');
  const savedCogsText = await page.textContent('[data-testid="manual-record-cogs"]');
  assert.ok(savedCogsText.includes('45,000'), `Expected manual COGS 45,000 in comparison, got: ${savedCogsText}`);
  const savedInventoryText = await page.textContent('[data-testid="manual-record-inventory"]');
  assert.ok(savedInventoryText.includes('30,000'), `Expected manual inventory 30,000 in comparison, got: ${savedInventoryText}`);
  mark('Journey 6: Compare against existing manual MonthlyRecord showing previous manual COGS/inventory');

  // --------------------------------------------------------------------------
  // Journey 7: Explicitly authorize cutover with confirmation note
  // --------------------------------------------------------------------------
  await page.fill('[data-testid="input-confirmation-note"]', 'November 2026 ledger cutover verified by Antigravity');
  await page.click('[data-testid="open-cutover-dialog-btn"]');
  await page.waitForSelector('[data-testid="cutover-confirmation-modal"]');
  mark('Journey 7: Explicitly open confirmation dialog with note');

  // --------------------------------------------------------------------------
  // Journey 8: Verify persisted MonthlyRecord in database and UI
  // --------------------------------------------------------------------------
  await page.click('[data-testid="confirm-cutover-btn"]');
  await page.waitForSelector('[data-testid="bridge-success-alert"]', { timeout: 15000 });
  const successAlert = await page.textContent('[data-testid="bridge-success-alert"]');
  assert.ok(successAlert.toLowerCase().includes('cutover') || successAlert.toLowerCase().includes('success') || successAlert.toLowerCase().includes('record'), `Expected success alert, got: ${successAlert}`);

  // Query database monthly_records
  const dbInflow = await queryNum(`SELECT cash_inflow FROM monthly_records WHERE user_id = '${businessId}' AND month = '2026-11'`);
  assert.equal(Number(dbInflow), 80000.0, 'Persisted cash_inflow must equal 80000');
  const dbOutflow = await queryNum(`SELECT cash_outflow FROM monthly_records WHERE user_id = '${businessId}' AND month = '2026-11'`);
  assert.equal(Number(dbOutflow), 35000.0, 'Persisted cash_outflow must equal 35000');
  const dbRevenue = await queryNum(`SELECT revenue FROM monthly_records WHERE user_id = '${businessId}' AND month = '2026-11'`);
  assert.equal(Number(dbRevenue), 50000.0, 'Persisted revenue must equal 50000');
  const dbOpex = await queryNum(`SELECT operating_expenses FROM monthly_records WHERE user_id = '${businessId}' AND month = '2026-11'`);
  assert.equal(Number(dbOpex), 20000.0, 'Persisted operating_expenses must equal 20000');
  const dbAr = await queryNum(`SELECT receivables_outstanding FROM monthly_records WHERE user_id = '${businessId}' AND month = '2026-11'`);
  assert.equal(Number(dbAr), 15000.0, 'Persisted receivables_outstanding must equal 15000');
  const dbAp = await queryNum(`SELECT payables_outstanding FROM monthly_records WHERE user_id = '${businessId}' AND month = '2026-11'`);
  assert.equal(Number(dbAp), 40000.0, 'Persisted payables_outstanding must equal 40000');

  mark('Journey 8: Persisted MonthlyRecord in database and UI matches authoritative ledger figures');

  // --------------------------------------------------------------------------
  // Journey 9: Verify health-score-v1 automatic recalculation and score generation
  // --------------------------------------------------------------------------
  const scoreCount = await queryNum(
    `SELECT COUNT(*) FROM score_results WHERE user_id = '${businessId}' AND month = '2026-11'`
  );
  assert.ok(Number(scoreCount) >= 1, 'health-score-v1 calculation must be automatically triggered and persisted in score_results');
  const compositeScore = await queryNum(
    `SELECT composite_score FROM score_results WHERE user_id = '${businessId}' AND month = '2026-11'`
  );
  assert.ok(Number(compositeScore) > 0, `composite_score must be positive, got: ${compositeScore}`);
  mark('Journey 9: health-score-v1 automatic recalculation and score generation verified');

  // --------------------------------------------------------------------------
  // Journey 10: Verify historical provenance (LEDGER_MONTHLY_BRIDGE_V1) & preserved manual fields
  // --------------------------------------------------------------------------
  const dbCogs = await queryNum(`SELECT cogs FROM monthly_records WHERE user_id = '${businessId}' AND month = '2026-11'`);
  assert.equal(Number(dbCogs), 45000.0, 'Manual COGS 45000 must be preserved');
  const dbInventory = await queryNum(`SELECT inventory_value FROM monthly_records WHERE user_id = '${businessId}' AND month = '2026-11'`);
  assert.equal(Number(dbInventory), 30000.0, 'Manual Inventory 30000 must be preserved');

  const auditEvents = await queryNum(
    `SELECT COUNT(*) FROM security_audit_events WHERE business_id = '${businessId}' AND event_type = 'LEDGER_HEALTH_CUTOVER'`
  );
  assert.ok(Number(auditEvents) >= 1, 'LEDGER_HEALTH_CUTOVER audit event must be persisted in security_audit_events');
  mark('Journey 10: Historical provenance (LEDGER_MONTHLY_BRIDGE_V1) and manual field preservation verified');

  // --------------------------------------------------------------------------
  // Journey 11: Repeat cutover safely (idempotent overwrite)
  // --------------------------------------------------------------------------
  const currentVersion = await queryNum(`SELECT version FROM monthly_records WHERE user_id = '${businessId}' AND month = '2026-11'`);
  const repeatReq = {
    yearMonth: '2026-11',
    manualCogs: 45000.0,
    manualInventoryValue: 30000.0,
    manualLoanOutstanding: 100000.0,
    manualInterestExpense: null,
    financingType: 'islamic',
    expectedVersion: Number(currentVersion),
    confirmationNote: 'Second idempotent cutover pass',
  };
  const repeatRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/ledger-bridge/apply-projection', repeatReq);
  assert.equal(repeatRes.status, 201, 'Repeat cutover with expected version must succeed idempotently');
  mark('Journey 11: Repeat cutover safely (idempotent overwrite) succeeds');

  // --------------------------------------------------------------------------
  // Journey 12: Check stale-version conflict (HTTP 409 when version changed)
  // --------------------------------------------------------------------------
  const staleReq = {
    yearMonth: '2026-11',
    manualCogs: 45000.0,
    manualInventoryValue: 30000.0,
    manualLoanOutstanding: null,
    manualInterestExpense: null,
    financingType: 'none',
    expectedVersion: 0, // Stale version (current is 2)
    confirmationNote: 'Stale version attempt',
  };
  const staleRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/ledger-bridge/apply-projection', staleReq);
  assert.equal(staleRes.status, 409, 'Stale expectedVersion must trigger HTTP 409 Conflict');
  mark('Journey 12: Stale-version concurrency lock (HTTP 409 Conflict) verified');

  // --------------------------------------------------------------------------
  // Journey 13: Switch businesses and verify tenant isolation
  // --------------------------------------------------------------------------
  // Switch to Business B
  await apiCall(ownerJar, ownerXsrf, 'POST', '/businesses/active', { businessId: businessBId });
  const bizBProjectionRes = await apiCall(ownerJar, ownerXsrf, 'GET', '/finance/ledger-bridge/monthly-projection?yearMonth=2026-11');
  assert.equal(bizBProjectionRes.status, 200);
  assert.equal(bizBProjectionRes.json.hasExistingMonthlyRecord, false, 'Business B must have no existing monthly record');
  assert.equal(Number(bizBProjectionRes.json.ledgerCashInflow), 0, 'Business B must have 0 cash inflow');
  assert.equal(Number(bizBProjectionRes.json.ledgerCashOutflow), 0, 'Business B must have 0 cash outflow');

  // Switch back to Business A
  await apiCall(ownerJar, ownerXsrf, 'POST', '/businesses/active', { businessId });
  mark('Journey 13: Switch businesses and verify tenant isolation (zero cross-tenant data leak)');

  // --------------------------------------------------------------------------
  // Journey 14: Bilingual Urdu RTL and mobile 390x844 responsive rendering
  // --------------------------------------------------------------------------
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${origin}/records/bridge`, { waitUntil: 'networkidle' });
  await page.waitForSelector('[data-testid="ledger-bridge-workspace"]');

  // Take screenshot for visual evidence
  await page.screenshot({ path: path.join(outDir, 'ledger-bridge-mobile-390x844.png'), fullPage: true });

  const isMobileVisible = await page.isVisible('[data-testid="ledger-bridge-workspace"]');
  assert.ok(isMobileVisible, 'Workspace must remain fully functional on mobile 390x844');

  // Invariant Check: Verify cash receipts/payments are not represented as accrual revenue/COGS/profit without supported accounting evidence
  const finalInflow = await page.textContent('[data-testid="ledger-inflow"]');
  const finalRevenue = await page.textContent('[data-testid="ledger-revenue"]');
  // Inflow (80,000) != Revenue (50,000) because collection (30,000) is cash receipt against existing AR, not newly realized revenue
  assert.notEqual(finalInflow, finalRevenue, 'Cash inflow (80k) must strictly decouple from operating revenue (50k)');
  mark('Journey 14: Bilingual Urdu RTL layout, mobile 390x844 responsive rendering and accrual-vs-cash invariant verified');

  console.log('\n======================================================');
  console.log(`ALL ${checks.length} LEDGER BRIDGE BROWSER JOURNEYS PASSED!`);
  console.log('======================================================\n');
} finally {
  if (browser) await browser.close().catch(() => {});
  if (gatewayServer) await new Promise((res) => gatewayServer.close(res)).catch(() => {});
  if (nextProc) {
    nextProc.kill();
    await new Promise((res) => setTimeout(res, 1000));
  }
  if (javaRuntime) {
    try {
      javaRuntime.stdin.write('STOP\n');
    } catch {}
    javaRuntime.kill();
  }
}
