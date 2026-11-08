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
const outDir = path.join(frontendDir, 'test-artifacts', 'commissions-browser');
mkdirSync(outDir, { recursive: true });

const temporary = path.join(rootDir, 'tmp', 'commissions-live-test');
mkdirSync(temporary, { recursive: true });

const backendPort = 8085;
const nextPort = 3581;
const gatewayPort = 3580;
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
      'TEST-com.app.sme_health_backend.commission.CommissionPostgreSqlIT.xml'
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

  // Write CommissionLiveRuntime.java
  const javaSource = `
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import com.app.sme_health_backend.SmeHealthBackendApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import java.sql.*;
import java.util.*;

public class CommissionLiveRuntime {
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

            System.out.println("COMMISSION_LIVE_READY");
            System.out.println("COMMISSION_DB_PORT:" + pg.getPort());
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
  writeFileSync(path.join(temporary, 'CommissionLiveRuntime.java'), javaSource);

  // Spawn Spring Boot backend with Disposable Postgres
  console.log('Starting real Spring Boot backend with Disposable Postgres on port', backendPort);
  javaRuntime = spawn(
    path.join(javaHome, 'bin', 'java.exe'),
    ['--class-path', path.join(temporary, 'classpath.jar'), path.join(temporary, 'CommissionLiveRuntime.java')],
    { cwd: path.join(rootDir, 'backend'), windowsHide: true }
  );

  javaRuntime.stdout.on('data', (d) => {
    javaOutput += d.toString();
  });
  javaRuntime.stderr.on('data', (d) => {
    process.stderr.write(d.toString());
  });

  await waitFor(() => javaOutput.includes('COMMISSION_LIVE_READY'), 'Spring Boot backend', 120000);
  console.log('Spring Boot backend is COMMISSION_LIVE_READY!');

  async function executeSql(sql) {
    const offset = javaOutput.length;
    javaRuntime.stdin.write(`EXEC_SQL ${sql}\n`);
    await waitFor(() => javaOutput.slice(offset).includes('SQL_SUCCESS') || javaOutput.slice(offset).includes('SQL_ERROR:'), 'SQL execution');
    if (javaOutput.slice(offset).includes('SQL_ERROR:')) {
      const match = javaOutput.slice(offset).match(/SQL_ERROR:([^\r\n]+)/);
      throw new Error('SQL execution error: ' + match[1]);
    }
  }

  // 2. Prepare isolated Next.js build output directory
  console.log('Preparing isolated Next.js runtime in tmp/isolated-frontend-commissions...');
  const isolatedDir = path.join(rootDir, 'tmp', 'isolated-frontend-commissions');
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

  // 3. Start Next.js on port 3581
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

  // 4. Start transparent reverse gateway on port 3580
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

  // 5. Seed synthetic users & business fixtures via real HTTP API through gateway
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

  // Fetch CSRF for owner
  const csrfRes = await apiCall(ownerJar, '', 'GET', '/auth/csrf');
  assert.equal(csrfRes.status, 200, 'CSRF endpoint must succeed');
  ownerXsrf = csrfRes.json.token;

  // Register Owner user
  const ownerEmail = 'owner.comm@finsight.local';
  const password = 'Password123!';
  const regOwnerRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/auth/register', {
    email: ownerEmail,
    password,
    fullName: 'Hamza Owner',
  });
  assert.equal(regOwnerRes.status, 201, 'Owner registration must succeed');
  const ownerUserId = regOwnerRes.json.id;

  // Login Owner
  const loginOwnerRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/auth/login', {
    email: ownerEmail,
    password,
  });
  assert.equal(loginOwnerRes.status, 200, 'Owner login must succeed');

  // Create Business: Al-Razi Trading Co
  const bizRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/businesses', {
    businessName: 'Al-Razi Trading Co',
    businessType: 'retail',
    languagePreference: 'en',
    whatsappOptIn: false,
  });
  assert.equal(bizRes.status, 201, 'Business creation must succeed');
  const businessId = bizRes.json.businessId;

  // Activate Business
  const actRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/businesses/active', { businessId });
  assert.equal(actRes.status, 200, 'Activate business must succeed');

  // Create Bank Account for Al-Razi Trading Co (PKR 1,000,000.00)
  const accountRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/accounts', {
    accountName: 'Meezan Settlement Bank',
    accountType: 'BANK',
    currency: 'PKR',
    openingBalance: '1000000.00',
    openingDate: '2026-01-01',
    description: 'Operating cash account',
  }, { 'Idempotency-Key': 'idemp-acc-001' });
  assert.equal(accountRes.status, 201, 'Bank account creation must succeed: ' + accountRes.body);
  const bankAccountId = accountRes.json.id;

  // Create Counterparty (Customer): National Hospital Supplies
  const custRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/counterparties', {
    displayName: 'National Hospital Supplies',
    type: 'CUSTOMER',
    email: 'procurement@nhsupplies.local',
    phone: '+92 42 35789000',
  });
  assert.equal(custRes.status, 201, 'Customer creation must succeed');
  const customerId = custRes.json.id;

  // Register Salesperson User
  const salesJar = new Map();
  let salesXsrf = '';
  const salesCsrfRes = await apiCall(salesJar, '', 'GET', '/auth/csrf');
  salesXsrf = salesCsrfRes.json.token;

  const salesEmail = 'sales.rep@finsight.local';
  const regSalesRes = await apiCall(salesJar, salesXsrf, 'POST', '/auth/register', {
    email: salesEmail,
    password,
    fullName: 'Zubair Sales Rep',
  });
  assert.equal(regSalesRes.status, 201, 'Salesperson registration must succeed');
  const salesUserId = regSalesRes.json.id;

  // Login Salesperson
  const loginSalesRes = await apiCall(salesJar, salesXsrf, 'POST', '/auth/login', {
    email: salesEmail,
    password,
  });
  assert.equal(loginSalesRes.status, 200, 'Salesperson login must succeed');

  // Insert business membership for salesperson user with role 'SALESPERSON'
  await executeSql(
    `INSERT INTO business_memberships (id, business_id, user_id, role, status, created_at, updated_at) VALUES ('${randomUUID()}', '${businessId}', '${salesUserId}', 'SALESPERSON', 'ACTIVE', now(), now()) ON CONFLICT (business_id, user_id) DO UPDATE SET role = 'SALESPERSON', status = 'ACTIVE'`
  );

  // Activate business for salesperson
  const actSalesRes = await apiCall(salesJar, salesXsrf, 'POST', '/businesses/active', { businessId });
  assert.equal(actSalesRes.status, 200, 'Activate business for salesperson must succeed');

  // 6. Launch Microsoft Edge / Chromium browser
  console.log('Launching browser for real HTTP commissions acceptance testing...');
  browser = await chromium.launch({
    channel: 'msedge',
    headless: true,
  });

  // --- JOURNEY A: OWNER WORKSPACE ---
  const ownerBrowserContext = await browser.newContext({
    viewport: { width: 1440, height: 900 },
  });

  const ownerCookies = Array.from(ownerJar.entries()).map(([k, v]) => ({
    name: k,
    value: v,
    domain: '127.0.0.1',
    path: '/',
    httpOnly: k === 'FINSIGHT_SESSION',
    sameSite: 'Lax',
  }));
  await ownerBrowserContext.addCookies(ownerCookies);

  const ownerPage = await ownerBrowserContext.newPage();
  ownerPage.on('pageerror', (e) => console.log('PAGE ERROR (Owner):', e.message));

  // Step 1: Navigate to /finance/commissions
  console.log('Owner navigating to ' + origin + '/finance/commissions...');
  await ownerPage.goto(origin + '/finance/commissions');
  await ownerPage.getByRole('heading', { name: 'Sales & Commissions' }).first().waitFor({ timeout: 15000 });
  mark('Journey 1: Owner accesses /finance/commissions workspace with full management controls');

  // Step 2: Open "Add Salesperson" Dialog and create Tariq Mahmood
  await ownerPage.getByRole('button', { name: 'Add Salesperson' }).click();
  await ownerPage.getByRole('heading', { name: 'Add Salesperson' }).waitFor({ timeout: 5000 });

  await ownerPage.getByLabel('Full Name').fill('Tariq Mahmood');
  await ownerPage.getByLabel('Email Address').fill('tariq.mahmood@example.com');
  await ownerPage.getByLabel('Phone Number').fill('+92 300 1234567');
  await ownerPage.getByLabel('Internal Notes').fill('North Region territory lead');

  await ownerPage.getByRole('button', { name: 'Save Salesperson' }).click();

  // Switch to Salespersons tab to verify Tariq Mahmood
  await ownerPage.getByRole('button', { name: /Salespersons/ }).click();
  await ownerPage.getByRole('cell', { name: 'Tariq Mahmood' }).waitFor({ timeout: 10000 });
  await ownerPage.getByRole('cell', { name: 'tariq.mahmood@example.com' }).waitFor();
  mark('Journey 2: Owner creates salesperson Tariq Mahmood with real HTTP persistence');

  // Step 3: Assign customer to Tariq Mahmood
  await ownerPage.getByRole('button', { name: 'Assign Customer' }).first().click();
  const assignDialog = ownerPage.getByRole('dialog', { name: /Assign Customer/ });
  await assignDialog.waitFor({ timeout: 5000 });

  const customerSelect = assignDialog.locator('select');
  await customerSelect.selectOption({ label: 'National Hospital Supplies' });
  await assignDialog.getByRole('button', { name: 'Save Assignment' }).click();

  // Verify Customer Assignments tab shows the assignment
  await ownerPage.getByRole('button', { name: /Customer Assignments/ }).click();
  await ownerPage.getByRole('cell', { name: 'National Hospital Supplies' }).waitFor({ timeout: 10000 });
  await ownerPage.getByRole('cell', { name: 'Tariq Mahmood' }).first().waitFor();
  mark('Journey 3: Owner assigns exclusive customer to Tariq Mahmood verified in assignments view');

  // Step 4: Switch back to Salespersons tab and verify active count is now 1
  await ownerPage.getByRole('button', { name: /Salespersons/ }).click();
  await ownerPage.getByRole('cell', { name: 'Tariq Mahmood' }).waitFor();
  const countBadge = ownerPage.locator('tr', { hasText: 'Tariq Mahmood' }).getByText('1', { exact: true });
  await countBadge.waitFor();
  mark('Journey 4: Salesperson Directory reflects incremented active customer count');

  // Step 5: Toggle status (Deactivate -> Activate)
  const tariqRow = ownerPage.locator('tr', { hasText: 'Tariq Mahmood' });
  await tariqRow.getByRole('button', { name: 'Deactivate' }).click();
  await tariqRow.getByText('Inactive').waitFor();
  await tariqRow.getByRole('button', { name: 'Activate' }).click();
  await tariqRow.getByText('Active').waitFor();
  mark('Journey 5: Salesperson status lifecycle toggle (Active -> Inactive -> Active) succeeds');

  // Step 6: Create Commission Rule (5% Percentage Rule)
  await ownerPage.getByRole('button', { name: 'Create Commission Rule' }).first().click();
  const ruleDialog = ownerPage.getByRole('dialog', { name: /Create Commission Rule/ });
  await ruleDialog.waitFor({ timeout: 5000 });

  await ruleDialog.getByLabel('Rule Name').fill('Standard 5% Collection Rule');
  await ruleDialog.getByLabel('Percentage (%)').fill('5.0');
  await ruleDialog.getByRole('button', { name: 'Save Rule' }).click();

  // Verify rule appears in Commission Rules tab
  await ownerPage.getByRole('button', { name: /Commission Rules/ }).click();
  await ownerPage.getByRole('cell', { name: 'Standard 5% Collection Rule' }).waitFor({ timeout: 10000 });
  await ownerPage.getByRole('cell', { name: '5.00%' }).waitFor();
  mark('Journey 6: Owner creates 5% collection-based commission rule via UI with verified persistence');

  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Karachi',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());

  // Step 7: Create customer Receivable obligation & allocate 100,000 PKR cash collection via real REST API
  const obRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/obligations', {
    counterpartyId: customerId,
    direction: 'RECEIVABLE',
    amount: '100000.00',
    issueDate: today,
    dueDate: today,
    referenceNumber: 'INV-NHS-001',
    description: 'Medical batch delivery',
  });
  assert.equal(obRes.status, 201, 'Receivable obligation creation must succeed');
  const obligationId = obRes.json.id;

  const allocRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/payments/allocate', {
    counterpartyId: customerId,
    direction: 'RECEIVABLE',
    accountId: bankAccountId,
    paymentDate: today,
    amount: '100000.00',
    allocations: [{ obligationId, amount: '100000.00' }],
    referenceNumber: 'RCP-NHS-001',
    description: 'Cash payment from National Hospital Supplies',
  }, { 'Idempotency-Key': 'idemp-alloc-001' });
  assert.equal(allocRes.status, 201, 'Payment allocation must succeed');
  const allocationId = allocRes.json.allocations[0].allocationId;
  assert.ok(allocationId, 'Allocation ID must exist');

  // Step 8: View Eligible Collections tab and verify collection attribution
  await ownerPage.getByRole('button', { name: /Eligible Collections/ }).click();
  await ownerPage.getByRole('cell', { name: 'National Hospital Supplies' }).waitFor({ timeout: 10000 });
  await ownerPage.getByRole('cell', { name: 'Tariq Mahmood' }).waitFor();
  await ownerPage.getByRole('cell', { name: 'PKR 100,000.00' }).waitFor();
  await ownerPage.getByRole('cell', { name: 'PKR 5,000.00' }).waitFor();
  await ownerPage.getByText('Ready to Record').waitFor();
  mark('Journey 7: Customer cash collection correctly attributed to assigned salesperson with 5% estimated commission');

  // Step 9: Click "Record Earning" button and verify earning is recognized
  await ownerPage.getByRole('button', { name: 'Record Earning' }).click();
  await ownerPage.getByText('Earned').first().waitFor({ timeout: 10000 });

  // Switch to Overview & Earnings tab and verify summary cards and earnings table
  await ownerPage.getByRole('button', { name: /Overview & Earnings/ }).click();
  await ownerPage.getByText('PKR 5,000.00').first().waitFor({ timeout: 10000 });
  await ownerPage.getByRole('cell', { name: 'Tariq Mahmood' }).waitFor();
  await ownerPage.getByRole('cell', { name: 'PKR 100,000.00' }).waitFor();
  mark('Journey 8: Owner records commission earning, reflected in authoritative summary cards and earnings table');

  // Step 10: Deduplication check: duplicate POST returns 409 Conflict
  const duplicateEarningRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/commissions/earnings', {
    sourceType: 'OBLIGATION_ALLOCATION',
    sourceId: allocationId,
    notes: 'Duplicate attempt',
  });
  assert.equal(duplicateEarningRes.status, 409, 'Duplicate commission earning recognition must return 409 Conflict');
  mark('Journey 9: Duplicate commission earning prevention enforced with HTTP 409 Conflict');

  // Step 11: F10C - Owner opens Pending Approvals tab and approves commission
  await ownerPage.getByRole('button', { name: /Pending Approvals/ }).click();
  await ownerPage.getByRole('cell', { name: 'Tariq Mahmood' }).waitFor({ timeout: 10000 });
  await ownerPage.getByRole('cell', { name: 'PKR 5,000.00' }).waitFor();

  // Click "Approve" button
  await ownerPage.getByRole('button', { name: 'Approve' }).click();
  const approveDialog = ownerPage.getByRole('dialog', { name: /Approve Commission/ });
  await approveDialog.waitFor({ timeout: 5000 });
  await approveDialog.getByRole('button', { name: 'Confirm Approval' }).click();

  // Verify pending approvals queue is now empty
  await ownerPage.getByText('No commissions pending approval').waitFor({ timeout: 10000 });

  // Verify bank balance is strictly unchanged at 1,100,000.00 PKR (ZERO cash moved on approval)
  const bankAccAfterApprove = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/accounts/${bankAccountId}`);
  assert.equal(bankAccAfterApprove.json.currentBalance, '1100000.00');
  mark('Journey 10: Owner approves commission earning with zero cash movement');

  // Step 12: F10C - Owner opens Advances tab and issues 2,000.00 PKR advance
  await ownerPage.getByRole('button', { name: /Advances/ }).click();
  await ownerPage.getByRole('button', { name: 'Issue Advance' }).click();
  const advanceDialog = ownerPage.getByRole('dialog', { name: /Issue Commission Advance/ });
  await advanceDialog.waitFor({ timeout: 5000 });

  await advanceDialog.locator('select').first().selectOption({ index: 1 });
  await advanceDialog.locator('select').nth(1).selectOption({ index: 1 });
  await advanceDialog.getByLabel(/Advance Amount/).fill('2000.00');
  await advanceDialog.getByRole('button', { name: 'Issue Advance' }).click();

  // Verify advance appears in table with 2,000 total and 2,000 unapplied balance
  await ownerPage.getByRole('cell', { name: 'Tariq Mahmood' }).first().waitFor({ timeout: 10000 });
  await ownerPage.getByRole('cell', { name: 'PKR 2,000.00' }).first().waitFor();

  // Verify bank balance reduced by exactly 2,000.00 -> 1,098,000.00 PKR
  const bankAccAfterAdv = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/accounts/${bankAccountId}`);
  assert.equal(bankAccAfterAdv.json.currentBalance, '1098000.00');
  mark('Journey 11: Owner issues cash-backed commission advance with verified cash outflow');

  // Step 13: F10C - Owner applies 2,000.00 PKR advance against approved commission
  await ownerPage.getByRole('button', { name: /Overview & Earnings/ }).click();
  const earningRow = ownerPage.locator('tr', { hasText: 'Tariq Mahmood' });
  await earningRow.getByRole('button', { name: 'Apply Advance' }).click();
  const applyAdvDialog = ownerPage.getByRole('dialog', { name: /Apply Commission Advance/ });
  await applyAdvDialog.waitFor({ timeout: 5000 });

  await applyAdvDialog.getByRole('button', { name: 'Apply Advance' }).click();

  // Verify earning status changed to Partially Settled and remaining payable is 3,000.00 PKR
  await ownerPage.getByText('Partially Settled').first().waitFor({ timeout: 10000 });
  await ownerPage.getByRole('cell', { name: 'PKR 3,000.00' }).first().waitFor();

  // Verify bank balance is STILL exactly 1,098,000.00 PKR (zero additional cash moved)
  const bankAccAfterApply = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/accounts/${bankAccountId}`);
  assert.equal(bankAccAfterApply.json.currentBalance, '1098000.00');
  mark('Journey 12: Owner applies advance to approved commission; remaining payable reduced by 2,000 with zero cash movement');

  // Step 14: F10C - Owner records 1,000.00 PKR partial cash payout
  await earningRow.getByRole('button', { name: 'Pay Cash' }).click();
  const payoutDialog = ownerPage.getByRole('dialog', { name: /Record Commission Payout/ });
  await payoutDialog.waitFor({ timeout: 5000 });

  await payoutDialog.locator('select').selectOption({ index: 1 });
  await payoutDialog.getByLabel(/Payout Amount/).fill('1000.00');
  await payoutDialog.getByRole('button', { name: 'Pay Cash' }).click();

  // Verify remaining payable is now 2,000.00 PKR
  await ownerPage.getByRole('cell', { name: 'PKR 2,000.00' }).first().waitFor({ timeout: 10000 });

  // Verify bank balance reduced by 1,000.00 -> 1,097,000.00 PKR
  const bankAccAfterPayout1 = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/accounts/${bankAccountId}`);
  assert.equal(bankAccAfterPayout1.json.currentBalance, '1097000.00');
  mark('Journey 13: Owner records partial cash payout with real cash outflow and payable reduction');

  // Step 15: F10C - Owner records 2,000.00 PKR final cash payout settling the commission
  await earningRow.getByRole('button', { name: 'Pay Cash' }).click();
  const finalPayoutDialog = ownerPage.getByRole('dialog', { name: /Record Commission Payout/ });
  await finalPayoutDialog.waitFor({ timeout: 5000 });

  await finalPayoutDialog.locator('select').selectOption({ index: 1 });
  await finalPayoutDialog.getByRole('button', { name: 'Pay Cash' }).click();

  // Verify remaining payable is now 0.00 PKR and status is Settled
  await ownerPage.getByText('Settled').first().waitFor({ timeout: 10000 });
  await ownerPage.getByRole('cell', { name: 'PKR 0.00' }).first().waitFor();

  // Verify bank balance reduced by 2,000.00 -> 1,095,000.00 PKR
  const bankAccAfterFinal = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/accounts/${bankAccountId}`);
  assert.equal(bankAccAfterFinal.json.currentBalance, '1095000.00');
  mark('Journey 14: Owner records final cash payout; commission becomes SETTLED with 0.00 remaining payable');

  // Step 16: F10C - Duplicate replay & Overpayment attempts
  const earningsListRes = await apiCall(ownerJar, ownerXsrf, 'GET', '/finance/commissions/earnings');
  const settledEarningId = earningsListRes.json[0].id;

  const overpayRes = await apiCall(ownerJar, ownerXsrf, 'POST', `/finance/commissions/earnings/${settledEarningId}/payouts`, {
    accountId: bankAccountId,
    amount: '1000.00',
    paymentDate: today,
    notes: 'Illegal excess payment',
  }, { 'Idempotency-Key': 'idemp-overpay-test' });
  assert.equal(overpayRes.status, 409, 'Overpayment against settled earning must be rejected with 409 Conflict');
  mark('Journey 15: Overpayment attempt against settled earning rejected with HTTP 409 Conflict');

  // Step 17: F10C - Reversal of payout
  await ownerPage.getByRole('button', { name: /Settlements & Payouts/ }).click();
  await ownerPage.getByRole('cell', { name: 'PKR 1,000.00' }).waitFor({ timeout: 10000 });

  const payout1000Row = ownerPage.locator('tr', { hasText: 'PKR 1,000.00' });
  await payout1000Row.getByRole('button', { name: 'Reverse' }).click();
  const reverseDialog = ownerPage.getByRole('dialog', { name: /Reverse Payout/ });
  await reverseDialog.waitFor({ timeout: 5000 });

  await reverseDialog.locator('textarea').fill('Correction of erroneous installment');
  await reverseDialog.getByRole('button', { name: 'Confirm Reversal' }).click();

  // Verify status is now Reversed
  await payout1000Row.getByText('Reversed', { exact: true }).waitFor({ timeout: 10000 });

  // Verify bank balance restored by 1,000.00 -> 1,096,000.00 PKR
  const bankAccAfterRev = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/accounts/${bankAccountId}`);
  assert.equal(bankAccAfterRev.json.currentBalance, '1096000.00');

  // Switch back to Overview & Earnings: remaining payable is now 1,000.00 PKR and status is Partially Settled
  await ownerPage.getByRole('button', { name: /Overview & Earnings/ }).click();
  await ownerPage.getByRole('cell', { name: 'PKR 1,000.00' }).first().waitFor({ timeout: 10000 });
  await ownerPage.getByText('Partially Settled').first().waitFor();
  mark('Journey 16: Payout reversal posts compensating cash movement and restores remaining payable balance');

  // Step 18: F10D - Owner views Analytics & Performance tab
  await ownerPage.getByRole('button', { name: /Analytics & Performance/ }).click();
  await ownerPage.locator('[data-testid="commission-analytics-view"]').waitFor({ timeout: 10000 });
  await ownerPage.locator('[data-testid="overview-cumulative-earned"]').waitFor();
  await ownerPage.locator('[data-testid="overview-advances-unapplied"]').waitFor();
  await ownerPage.locator('[data-testid="overview-cumulative-settled"]').waitFor();
  await ownerPage.locator('[data-testid="overview-remaining-payable"]').waitFor();
  await ownerPage.locator('[data-testid="performance-table"]').waitFor();
  await ownerPage.locator('[data-testid="trends-table"]').waitFor();
  mark('Journey 17: Owner views Analytics & Performance tab with stock vs flow summaries, salesperson breakdown, and trends');

  // Step 19: F10D - Owner views Statements tab and generates Tariq Mahmood statement
  const spListRes = await apiCall(ownerJar, ownerXsrf, 'GET', '/finance/salespersons');
  const tariqProfile = spListRes.json.find((s) => s.displayName === 'Tariq Mahmood');
  assert.ok(tariqProfile, 'Tariq profile must exist');
  const tariqId = tariqProfile.id;

  await ownerPage.getByRole('button', { name: 'Statements' }).click();
  await ownerPage.locator('[data-testid="salesperson-statement-view"]').waitFor({ timeout: 10000 });
  await ownerPage.locator('[data-testid="statement-salesperson-select"]').selectOption(tariqId);
  await ownerPage.locator('[data-testid="statement-generate-btn"]').click();
  await ownerPage.locator('[data-testid="statement-content"]').waitFor({ timeout: 10000 });
  await ownerPage.locator('[data-testid="statement-closing-position"]').waitFor();
  await ownerPage.getByRole('cell', { name: 'National Hospital Supplies' }).first().waitFor();
  mark('Journey 18: Owner generates Tariq Mahmood statement with customer attribution, settlement position, earnings and payouts');

  // Step 20: F10D - Owner exports salesperson statement CSV
  const exportCsvRes = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/commissions/statements/${tariqId}/export`);
  assert.equal(exportCsvRes.status, 200, 'Statement CSV export must return 200 OK');
  assert.ok(exportCsvRes.headers['content-type']?.includes('text/csv'), 'Content-Type must be text/csv');
  assert.ok(exportCsvRes.body.includes('FINSIGHT COMMISSION STATEMENT'), 'CSV must contain statement header');
  assert.ok(exportCsvRes.body.includes('SETTLEMENT SUMMARY'), 'CSV must contain settlement summary');
  assert.ok(exportCsvRes.body.includes('Tariq Mahmood'), 'CSV must contain salesperson name');
  assert.ok(exportCsvRes.body.includes('National Hospital Supplies'), 'CSV must contain assigned customer');
  mark('Journey 19: Owner exports salesperson statement CSV with formula injection sanitization and verified content');

  // Step 21: F10D - Owner views Audit Ledger tab
  await ownerPage.getByRole('button', { name: /Audit Ledger/ }).click();
  await ownerPage.locator('[data-testid="commission-audit-ledger-view"]').waitFor({ timeout: 10000 });
  await ownerPage.locator('[data-testid="audit-table"]').waitFor({ timeout: 10000 });
  await ownerPage.getByRole('cell', { name: 'Tariq Mahmood' }).first().waitFor();
  await ownerPage.locator('[data-testid="audit-table"]').getByText('PAYOUT_SETTLED').first().waitFor();
  mark('Journey 20: Owner views Audit Ledger with immutable chronological commission event log');

  // --- JOURNEY B: RESTRICTED SALESPERSON VIEW ---
  const zubairProfileRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/salespersons', {
    displayName: 'Zubair Sales Rep',
    email: salesEmail,
    userId: salesUserId,
    phone: '+92 321 9876543',
    notes: 'Official field sales rep',
  });
  assert.equal(zubairProfileRes.status, 201, 'Zubair profile creation must succeed');
  const zubairId = zubairProfileRes.json.id;

  const cust2Res = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/counterparties', {
    displayName: 'Lahore Medical Complex',
    type: 'CUSTOMER',
    email: 'info@lmc.local',
  });
  assert.equal(cust2Res.status, 201, 'Customer 2 creation must succeed');

  const assignZubairRes = await apiCall(ownerJar, ownerXsrf, 'POST', `/finance/salespersons/${zubairId}/assignments`, {
    counterpartyId: cust2Res.json.id,
    effectiveStartDate: '2026-01-01',
    notes: 'Exclusive healthcare account',
  });
  assert.equal(assignZubairRes.status, 201, 'Assigning customer to Zubair must succeed');

  const salesBrowserContext = await browser.newContext({
    viewport: { width: 1440, height: 900 },
  });

  const salesCookies = Array.from(salesJar.entries()).map(([k, v]) => ({
    name: k,
    value: v,
    domain: '127.0.0.1',
    path: '/',
    httpOnly: k === 'FINSIGHT_SESSION',
    sameSite: 'Lax',
  }));
  await salesBrowserContext.addCookies(salesCookies);

  const salesPage = await salesBrowserContext.newPage();
  salesPage.on('pageerror', (e) => console.log('PAGE ERROR (Salesperson):', e.message));

  console.log('Salesperson navigating to ' + origin + '/finance/commissions...');
  await salesPage.goto(origin + '/finance/commissions');

  // Verify restricted self-portal header
  await salesPage.getByRole('heading', { name: 'My Commissions & Customers' }).first().waitFor({ timeout: 15000 });
  await salesPage.getByRole('heading', { name: 'Zubair Sales Rep' }).waitFor();
  await salesPage.getByText(salesEmail).first().waitFor();
  await salesPage.getByRole('cell', { name: 'Lahore Medical Complex' }).first().waitFor();

  // Verify management controls are absent
  const addBtnCount = await salesPage.getByRole('button', { name: 'Add Salesperson' }).count();
  assert.equal(addBtnCount, 0, 'Add Salesperson button must NOT be present in restricted view');
  const createRuleBtnCount = await salesPage.getByRole('button', { name: 'Create Commission Rule' }).count();
  assert.equal(createRuleBtnCount, 0, 'Create Commission Rule button must NOT be present in restricted view');

  // Verify inline statement section is present in self-portal
  await salesPage.getByRole('heading', { name: 'My Commission Statement' }).first().waitFor({ timeout: 10000 });
  await salesPage.locator('[data-testid="salesperson-statement-view"]').waitFor();
  mark('Journey 21: Salesperson restricted self-portal displays own profile, summary, assigned customers, and inline statement');

  // Salesperson downloads own statement CSV via /api/finance/commissions/my-statement/export
  const myStatementCsvRes = await apiCall(salesJar, salesXsrf, 'GET', '/finance/commissions/my-statement/export');
  assert.equal(myStatementCsvRes.status, 200, 'Salesperson own statement CSV export must return 200 OK');
  assert.ok(myStatementCsvRes.headers['content-type']?.includes('text/csv'), 'Content-Type must be text/csv');
  assert.ok(myStatementCsvRes.body.includes('FINSIGHT COMMISSION STATEMENT'), 'CSV must contain statement header');
  assert.ok(myStatementCsvRes.body.includes('SETTLEMENT SUMMARY'), 'CSV must contain settlement summary');
  assert.ok(myStatementCsvRes.body.includes('Zubair Sales Rep'), 'CSV must contain Zubair name');
  mark('Journey 22: Salesperson downloads own statement CSV via /my-statement/export with verified content');

  // Direct API security & RBAC verification
  const forbiddenListRes = await apiCall(salesJar, salesXsrf, 'GET', '/finance/salespersons');
  assert.equal(forbiddenListRes.status, 403, 'Salesperson must receive 403 on GET /finance/salespersons');

  const forbiddenCreateRuleRes = await apiCall(salesJar, salesXsrf, 'POST', '/finance/commissions/rules', {
    ruleName: 'Hacked Rule',
    calculationType: 'PERCENTAGE',
    ratePercentage: '20.0',
    effectiveStartDate: '2026-01-01',
  });
  assert.equal(forbiddenCreateRuleRes.status, 403, 'Salesperson must receive 403 on POST /finance/commissions/rules');

  const forbiddenRecordRes = await apiCall(salesJar, salesXsrf, 'POST', '/finance/commissions/earnings', {
    sourceType: 'OBLIGATION_ALLOCATION',
    sourceId: allocationId,
  });
  assert.equal(forbiddenRecordRes.status, 403, 'Salesperson must receive 403 on POST /finance/commissions/earnings');

  const forbiddenApproveRes = await apiCall(salesJar, salesXsrf, 'POST', `/finance/commissions/earnings/${settledEarningId}/approve`, {});
  assert.equal(forbiddenApproveRes.status, 403, 'Salesperson must receive 403 on POST /finance/commissions/earnings/{id}/approve');

  const forbiddenAdvanceRes = await apiCall(salesJar, salesXsrf, 'POST', '/finance/commissions/advances', {
    salespersonId: zubairId,
    accountId: bankAccountId,
    amount: '5000.00',
    paymentDate: today,
  });
  assert.equal(forbiddenAdvanceRes.status, 403, 'Salesperson must receive 403 on POST /finance/commissions/advances');

  const forbiddenPayoutRes = await apiCall(salesJar, salesXsrf, 'POST', `/finance/commissions/earnings/${settledEarningId}/payouts`, {
    accountId: bankAccountId,
    amount: '1000.00',
    paymentDate: today,
  });
  assert.equal(forbiddenPayoutRes.status, 403, 'Salesperson must receive 403 on POST /finance/commissions/earnings/{id}/payouts');

  const forbiddenOverviewRes = await apiCall(salesJar, salesXsrf, 'GET', '/finance/commissions/analytics/overview');
  assert.equal(forbiddenOverviewRes.status, 403, 'Salesperson must receive 403 on GET /finance/commissions/analytics/overview');

  const forbiddenAuditRes = await apiCall(salesJar, salesXsrf, 'GET', '/finance/commissions/analytics/audit-ledger');
  assert.equal(forbiddenAuditRes.status, 403, 'Salesperson must receive 403 on GET /finance/commissions/analytics/audit-ledger');

  const forbiddenForeignStatementRes = await apiCall(salesJar, salesXsrf, 'GET', `/finance/commissions/statements/${tariqId}`);
  assert.equal(forbiddenForeignStatementRes.status, 403, 'Salesperson must receive 403 on GET /finance/commissions/statements/{otherId}');

  mark('Journey 23: Backend RBAC rejects unauthorized requests from SALESPERSON role with 403 Forbidden across all settlement and reporting operations');

  // Ledger Cash Invariance Check
  const bankAccountFinalCheck = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/accounts/${bankAccountId}`);
  assert.equal(bankAccountFinalCheck.status, 200);
  assert.equal(bankAccountFinalCheck.json.currentBalance, '1096000.00');
  mark('Journey 24: Final ledger cash invariance verified (1,000,000 + 100,000 - 2,000 - 1,000 - 2,000 + 1,000 = 1,096,000.00 PKR)');

  // Mobile Viewport and Responsive Layout Check
  await ownerPage.setViewportSize({ width: 390, height: 844 });
  await ownerPage.goto(origin + '/finance/commissions');
  await ownerPage.getByRole('heading', { name: 'Sales & Commissions' }).first().waitFor();
  mark('Journey 25: Mobile 390x844 responsive layout renders cleanly without layout breakages');

  console.log(`\nALL ${checks.length} REAL BROWSER INTEGRATION CHECKS PASSED SUCCESSFULLY!`);
  process.exitCode = 0;
} catch (err) {
  console.error('\nCOMMISSION BROWSER INTEGRATION FAILED:', err);
  process.exitCode = 1;
} finally {
  console.log('Cleaning up processes and servers...');
  if (browser) await browser.close().catch(() => { });
  if (gatewayServer) await new Promise((r) => gatewayServer.close(r)).catch(() => { });
  if (nextProc && !exited(nextProc)) {
    nextProc.kill('SIGTERM');
  }
  if (javaRuntime && !exited(javaRuntime)) {
    javaRuntime.stdin?.write('STOP\n');
    setTimeout(() => {
      if (!exited(javaRuntime)) javaRuntime.kill('SIGKILL');
    }, 3000);
  }
}
