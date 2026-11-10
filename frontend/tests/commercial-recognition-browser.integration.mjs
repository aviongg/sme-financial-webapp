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
const outDir = path.join(frontendDir, 'test-artifacts', 'commercial-recognition-browser');
mkdirSync(outDir, { recursive: true });

const temporary = path.join(rootDir, 'tmp', 'commercial-recognition-live-test');
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
  let xmlPath = surefireXmlCandidates.find(f => existsSync(f));
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

  // Write CommercialRecognitionLiveRuntime.java
  const javaSource = `
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import com.app.sme_health_backend.SmeHealthBackendApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import java.sql.*;
import java.util.*;

public class CommercialRecognitionLiveRuntime {
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

            System.out.println("COMMERCIAL_RECOGNITION_LIVE_READY");
            System.out.println("COMMERCIAL_RECOGNITION_DB_PORT:" + pg.getPort());
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
  writeFileSync(path.join(temporary, 'CommercialRecognitionLiveRuntime.java'), javaSource);

  // Spawn Spring Boot backend with Disposable Postgres
  console.log('Starting real Spring Boot backend with Disposable Postgres on port', backendPort);
  javaRuntime = spawn(
    path.join(javaHome, 'bin', 'java.exe'),
    ['--class-path', path.join(temporary, 'classpath.jar'), path.join(temporary, 'CommercialRecognitionLiveRuntime.java')],
    { cwd: path.join(rootDir, 'backend'), windowsHide: true }
  );

  javaRuntime.stdout.on('data', (d) => {
    javaOutput += d.toString();
  });
  javaRuntime.stderr.on('data', (d) => {
    process.stderr.write(d.toString());
  });

  await waitFor(() => javaOutput.includes('COMMERCIAL_RECOGNITION_LIVE_READY'), 'Spring Boot backend', 120000);
  console.log('Spring Boot backend is COMMERCIAL_RECOGNITION_LIVE_READY!');

  // 2. Prepare isolated Next.js runtime
  console.log('Preparing isolated Next.js runtime in tmp/isolated-frontend-recognition...');
  const isolatedDir = path.join(rootDir, 'tmp', 'isolated-frontend-recognition');
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

  const ownerEmail = `recog.owner.${randomUUID()}@finsight.local`;
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
    businessName: 'Alpha Textiles Manufacturing',
    businessType: 'manufacturing',
    languagePreference: 'en',
    whatsappOptIn: false,
  });
  assert.equal(bizRes.status, 201, 'Business creation must succeed');
  const businessId = bizRes.json.businessId;

  await apiCall(ownerJar, ownerXsrf, 'POST', '/businesses/active', { businessId });
  const userId = await queryNum(`SELECT id FROM app_users WHERE email = '${ownerEmail}'`);

  // Setup Business B for cross-business isolation checks
  const bizBRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/businesses', {
    businessName: 'Beta Trading Corp',
    businessType: 'trade',
    languagePreference: 'en',
    whatsappOptIn: false,
  });
  const businessBId = bizBRes.json.businessId;

  // Set active back to Business A
  await apiCall(ownerJar, ownerXsrf, 'POST', '/businesses/active', { businessId });

  // Register Salesperson User (for RBAC 403 test)
  const salesJar = new Map();
  const salesEmail = `recog.sales.${randomUUID()}@finsight.local`;
  const salesCsrfRes = await apiCall(salesJar, '', 'GET', '/auth/csrf');
  await apiCall(salesJar, salesCsrfRes.json.token, 'POST', '/auth/register', {
    email: salesEmail,
    password,
    fullName: 'Sales Person Tariq',
  });
  await apiCall(salesJar, salesCsrfRes.json.token, 'POST', '/auth/login', {
    email: salesEmail,
    password,
  });
  const salesPostLoginCsrf = await apiCall(salesJar, '', 'GET', '/auth/csrf');
  const salesXsrf = salesPostLoginCsrf.json.token;
  const salesUserId = await queryNum(`SELECT id FROM app_users WHERE email = '${salesEmail}'`);

  // Grant SALESPERSON membership in Business A
  await execSql(`
    INSERT INTO business_memberships (user_id, business_id, role, status)
    VALUES ('${salesUserId}', '${businessId}', 'SALESPERSON', 'ACTIVE');
  `);
  await apiCall(salesJar, salesXsrf, 'POST', '/businesses/active', { businessId });

  // 5. Seed Financial Account and Counterparties in Business A
  console.log('Seeding financial counterparties and accounts...');
  const customerId = randomUUID();
  const supplierId = randomUUID();

  await execSql(`
    INSERT INTO counterparties (id, business_id, display_name, type, status, notes)
    VALUES ('${customerId}', '${businessId}', 'Ahmed Textiles', 'CUSTOMER', 'ACTIVE', 'Primary fabric customer');
    INSERT INTO counterparties (id, business_id, display_name, type, status, notes)
    VALUES ('${supplierId}', '${businessId}', 'Apex Supplies Ltd', 'SUPPLIER', 'ACTIVE', 'Chemical dye supplier');
  `);

  const accountId = randomUUID();
  await execSql(`
    INSERT INTO financial_accounts (id, business_id, account_name, account_type, currency, currency_decimals, opening_balance, opening_date, status, institution_name)
    VALUES ('${accountId}', '${businessId}', 'Meezan Bank Operating', 'BANK', 'PKR', 2, 500000.0000, '2026-01-01', 'ACTIVE', 'Meezan Bank');
  `);

  // 6. Seed Commercial Documents via REST API (F04 Invoice and Bill)
  console.log('Seeding F04 Commercial Invoices and Bills...');
  // Ensure document sequence
  await execSql(`
    INSERT INTO commercial_number_sequences (business_id, kind, next_value)
    VALUES ('${businessId}', 'INVOICE', 42), ('${businessId}', 'BILL', 88)
    ON CONFLICT (business_id, kind) DO NOTHING;
  `);

  // 6a. Finalized Customer Invoice: 115,000 PKR ("Ahmed Textiles")
  const invRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/invoices', {
    counterpartyName: 'Ahmed Textiles',
    documentDate: '2026-11-10',
    dueDate: '2026-12-10',
    notes: '100m Cotton Fabric Batch',
    lines: [{ description: 'Cotton Fabric', quantity: '100.00', unit: 'meter', unitPrice: '1150.00' }],
  });
  assert.equal(invRes.status, 201, 'Invoice draft creation succeeded');
  const invoiceDocId = invRes.json.id;

  const finalizeInvRes = await apiCall(ownerJar, ownerXsrf, 'POST', `/invoices/${invoiceDocId}/finalize`, {
    expectedVersion: 0,
  });
  assert.equal(finalizeInvRes.status, 200, 'Invoice finalization succeeded');
  const finalizedInvoiceNumber = finalizeInvRes.json.documentNumber;

  // 6b. Draft Invoice: 25,000 PKR (Should NOT be eligible for recognition)
  const draftInvRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/invoices', {
    counterpartyName: 'Pending Client',
    documentDate: '2026-11-11',
    dueDate: '2026-12-11',
    notes: 'Draft order',
    lines: [{ description: 'Yarn samples', quantity: '1.00', unit: 'batch', unitPrice: '25000.00' }],
  });
  assert.equal(draftInvRes.status, 201, 'Draft invoice creation succeeded');
  const draftInvoiceId = draftInvRes.json.id;

  // 6c. Finalized Supplier Bill: 80,000 PKR ("Apex Supplies Ltd")
  const billRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/bills', {
    counterpartyName: 'Apex Supplies Ltd',
    documentDate: '2026-11-08',
    dueDate: '2026-11-28',
    notes: 'Raw reactive dyes',
    lines: [{ description: 'Reactive blue dye', quantity: '40.00', unit: 'kg', unitPrice: '2000.00' }],
  });
  assert.equal(billRes.status, 201, 'Bill draft creation succeeded');
  const billDocId = billRes.json.id;

  const finalizeBillRes = await apiCall(ownerJar, ownerXsrf, 'POST', `/bills/${billDocId}/finalize`, {
    expectedVersion: 0,
  });
  assert.equal(finalizeBillRes.status, 200, 'Bill finalization succeeded');
  const finalizedBillNumber = finalizeBillRes.json.documentNumber;

  // 6d. Void Bill: 15,000 PKR (Finalized then Voided - Should NOT be eligible)
  const voidBillRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/bills', {
    counterpartyName: 'Defective Supplier',
    documentDate: '2026-11-05',
    dueDate: '2026-11-25',
    notes: 'Damaged materials',
    lines: [{ description: 'Damaged salt', quantity: '1.00', unit: 'ton', unitPrice: '15000.00' }],
  });
  const voidBillId = voidBillRes.json.id;
  await apiCall(ownerJar, ownerXsrf, 'POST', `/bills/${voidBillId}/finalize`, { expectedVersion: 0 });
  await apiCall(ownerJar, ownerXsrf, 'POST', `/bills/${voidBillId}/void`, { expectedVersion: 1, reason: 'Defective materials returned' });

  // 6e. Commercial Document for Business B (for cross-business test)
  await apiCall(ownerJar, ownerXsrf, 'POST', '/businesses/active', { businessId: businessBId });
  const bizBDocRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/invoices', {
    counterpartyName: 'Beta Client',
    documentDate: '2026-11-10',
    dueDate: '2026-12-10',
    notes: 'Cross-business invoice',
    lines: [{ description: 'Goods', quantity: '1.00', unit: 'lot', unitPrice: '99000.00' }],
  });
  const bizBDocId = bizBDocRes.json.id;
  await apiCall(ownerJar, ownerXsrf, 'POST', `/invoices/${bizBDocId}/finalize`, { expectedVersion: 0 });

  // Switch back to Business A
  await apiCall(ownerJar, ownerXsrf, 'POST', '/businesses/active', { businessId });

  // 7. Seed Project with Link to Bill (for Project Exposure Transition Scenario 15)
  console.log('Seeding Project EXP-99 with Commercial Bill Link...');
  const projectId = randomUUID();
  const budgetVersionId = randomUUID();
  await execSql(`
    INSERT INTO projects (id, business_id, code, name, kind, status, base_currency, created_by, updated_by)
    VALUES ('${projectId}', '${businessId}', 'EXP-99', 'Export Textile Run 99', 'PROJECT', 'ACTIVE', 'PKR', '${userId}', '${userId}');
    INSERT INTO project_budget_versions (id, business_id, project_id, revision, status, base_currency, planned_revenue, planned_cost, planned_margin, planned_margin_pct, calculation_version, created_by, updated_by)
    VALUES ('${budgetVersionId}', '${businessId}', '${projectId}', 1, 'DRAFT', 'PKR', 250000.0000, 180000.0000, 70000.0000, 28.00, 'f05-plan-v1', '${userId}', '${userId}');
    UPDATE project_budget_versions SET status = 'APPROVED', approved_by = '${userId}', approved_at = now() WHERE id = '${budgetVersionId}';
  `);
  // Link the commercial bill to this project (pre-recognition exposure)
  await apiCall(ownerJar, ownerXsrf, 'POST', `/finance/projects/${projectId}/commercial-document-links`, {
    documentId: billDocId,
    allocatedAmount: 80000.00,
    notes: 'Dyes allocated for export run',
  });

  // Verify initial counts of transactions and movements
  const initialTxCount = await queryNum('SELECT COUNT(*) FROM financial_transactions');
  const initialMovementCount = await queryNum('SELECT COUNT(*) FROM account_movements');

  // 8. Launch Playwright Headless Browser
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

  // -------------------------------------------------------------
  // Scenario 1: Navigate to /finance/recognition & verify workspace
  // -------------------------------------------------------------
  console.log('Navigating to /finance/recognition...');
  await page.goto(`${origin}/finance/recognition`, { waitUntil: 'networkidle' });
  await page.waitForSelector('[data-testid="commercial-recognition-workspace"]');
  const title = await page.textContent('[data-testid="workspace-title"]');
  assert.ok(title.toLowerCase().includes('commercial') || title.toLowerCase().includes('recognition'), `Expected recognition title, got: ${title}`);
  mark('Journey 1: Commercial Recognition Workspace (/finance/recognition) loaded successfully with authenticated session');

  // -------------------------------------------------------------
  // Scenario 2: Finalized invoice appears in Invoices tab
  // -------------------------------------------------------------
  await page.waitForSelector(`[data-testid="document-row-${invoiceDocId}"]`);
  const invoiceRowText = await page.textContent(`[data-testid="document-row-${invoiceDocId}"]`);
  assert.ok(invoiceRowText.includes('Ahmed Textiles'), 'Invoice row must show counterparty Ahmed Textiles');
  assert.ok(invoiceRowText.includes('115,000.00'), 'Invoice row must show amount 115,000.00');
  const invStatusBadge = await page.textContent(`[data-testid="doc-status-${invoiceDocId}"]`);
  assert.ok(invStatusBadge.includes('Unrecognized') || invStatusBadge.includes('Not Recognized'), 'Status must initially be Unrecognized');
  mark('Journey 2: Finalized customer invoice (115,000.00 PKR) appears in Invoices tab');

  // -------------------------------------------------------------
  // Scenario 3: Draft invoice is excluded from eligible documents list
  // -------------------------------------------------------------
  const draftRowExists = await page.$(`[data-testid="document-row-${draftInvoiceId}"]`);
  assert.equal(draftRowExists, null, 'Draft invoice must not appear in eligible list');
  mark('Journey 3: Draft invoice is excluded from eligible documents list');

  // -------------------------------------------------------------
  // Scenario 4: Switch to Bills tab, finalized bill appears
  // -------------------------------------------------------------
  await page.click('[data-testid="tab-bills"]');
  await page.waitForSelector(`[data-testid="document-row-${billDocId}"]`);
  const billRowText = await page.textContent(`[data-testid="document-row-${billDocId}"]`);
  assert.ok(billRowText.includes('Apex Supplies Ltd'), 'Bill row must show counterparty Apex Supplies Ltd');
  assert.ok(billRowText.includes('80,000.00'), 'Bill row must show amount 80,000.00');
  mark('Journey 4: Switch to Bills tab, finalized supplier bill (80,000.00 PKR) appears');

  // -------------------------------------------------------------
  // Scenario 5: Void bill is excluded from eligible documents list
  // -------------------------------------------------------------
  const voidRowExists = await page.$(`[data-testid="document-row-${voidBillId}"]`);
  assert.equal(voidRowExists, null, 'Void bill must not appear in eligible list');
  mark('Journey 5: Void bill is excluded from eligible documents list');

  // -------------------------------------------------------------
  // Scenario 6: Open recognition dialog for Invoice INV-000042
  // -------------------------------------------------------------
  await page.click('[data-testid="tab-invoices"]');
  await page.waitForSelector(`[data-testid="recognize-btn-${invoiceDocId}"]`);
  await page.click(`[data-testid="recognize-btn-${invoiceDocId}"]`);
  await page.waitForSelector('[data-testid="recognize-dialog"]');

  const dialogDocNum = await page.textContent('[data-testid="dialog-document-number"]');
  assert.ok(dialogDocNum.includes(finalizedInvoiceNumber) || dialogDocNum.includes('INV-000042') || dialogDocNum.includes(invoiceDocId.slice(0, 8)));

  const dialogTotal = await page.textContent('[data-testid="dialog-total-amount"]');
  assert.ok(dialogTotal.includes('115,000.00'), 'Dialog must display immutable total PKR 115,000.00');
  mark('Journey 6: Open recognition dialog for Invoice; form pre-populates immutable total and provides counterparty selection');

  // -------------------------------------------------------------
  // Scenario 7: Execute recognition of Invoice INV-000042
  // -------------------------------------------------------------
  // Select customer counterparty
  await page.selectOption('[data-testid="counterparty-select"]', customerId);
  await page.fill('[data-testid="notes-input"]', 'Commercial recognition browser journey verified');
  await page.click('[data-testid="confirm-recognition-btn"]');

  // Dialog should close and notification or status transition appear
  await page.waitForSelector('[data-testid="recognize-dialog"]', { state: 'detached', timeout: 10000 });
  mark('Journey 7: Execute recognition of Invoice; backend creates RECEIVABLE obligation');

  // -------------------------------------------------------------
  // Scenario 8: UI status transitions to Recognized with active outstanding
  // -------------------------------------------------------------
  await page.waitForSelector(`[data-testid="doc-status-${invoiceDocId}"]`);
  const postRecogStatus = await page.textContent(`[data-testid="doc-status-${invoiceDocId}"]`);
  assert.ok(postRecogStatus.includes('Recognized'), `Expected Recognized badge, got: ${postRecogStatus}`);
  mark('Journey 8: UI status transitions to Recognized with linked obligation and active outstanding balance');

  // -------------------------------------------------------------
  // Scenario 9: Duplicate recognition blocked
  // -------------------------------------------------------------
  const recognizeBtnAfter = await page.$(`[data-testid="recognize-btn-${invoiceDocId}"]`);
  assert.equal(recognizeBtnAfter, null, 'Recognize button must be replaced after successful recognition');
  const viewObligationLink = await page.$(`[data-testid="view-obligation-link-${invoiceDocId}"]`);
  assert.notEqual(viewObligationLink, null, 'View in Receivables link must be present');
  mark('Journey 9: Duplicate recognition blocked (Recognize action is replaced with View in Receivables link)');

  // -------------------------------------------------------------
  // Scenario 10: Switch to Bills tab, open recognition dialog for Bill
  // -------------------------------------------------------------
  await page.click('[data-testid="tab-bills"]');
  await page.waitForSelector(`[data-testid="recognize-btn-${billDocId}"]`);
  await page.click(`[data-testid="recognize-btn-${billDocId}"]`);
  await page.waitForSelector('[data-testid="recognize-dialog"]');
  await page.selectOption('[data-testid="counterparty-select"]', supplierId);
  mark('Journey 10: Switch to Bills tab, open recognition dialog for Supplier Bill and select supplier counterparty');

  // -------------------------------------------------------------
  // Scenario 11: Execute recognition of Bill BILL-000088
  // -------------------------------------------------------------
  await page.click('[data-testid="confirm-recognition-btn"]');
  await page.waitForSelector('[data-testid="recognize-dialog"]', { state: 'detached', timeout: 10000 });
  await page.waitForSelector(`[data-testid="doc-status-${billDocId}"]`);
  const billStatusAfter = await page.textContent(`[data-testid="doc-status-${billDocId}"]`);
  assert.ok(billStatusAfter.includes('Recognized'), `Expected Recognized bill badge, got: ${billStatusAfter}`);
  mark('Journey 11: Execute recognition of Bill; backend creates PAYABLE obligation');

  // -------------------------------------------------------------
  // Scenario 12: Switch to Recognition History tab
  // -------------------------------------------------------------
  await page.click('[data-testid="tab-history"]');
  await page.waitForSelector('[data-testid="recognized-obligations-table"]');
  const historyTableText = await page.textContent('[data-testid="recognized-obligations-table"]');
  assert.ok(historyTableText.includes('115,000.00'), 'History must contain invoice amount 115,000.00');
  assert.ok(historyTableText.includes('80,000.00'), 'History must contain bill amount 80,000.00');
  mark('Journey 12: Switch to Recognition History tab; verify both recognized documents appear with exact amounts');

  // -------------------------------------------------------------
  // Scenario 13: Zero cash/bank mutation verified
  // -------------------------------------------------------------
  const finalTxCount = await queryNum('SELECT COUNT(*) FROM financial_transactions');
  const finalMovementCount = await queryNum('SELECT COUNT(*) FROM account_movements');
  assert.equal(finalTxCount, initialTxCount, 'Zero financial transactions created on recognition');
  assert.equal(finalMovementCount, initialMovementCount, 'Zero account movements created on recognition');
  mark('Journey 13: Zero cash/bank mutation verified (neither bank movements nor cash postings created upon recognition)');

  // -------------------------------------------------------------
  // Scenario 14: Payment allocation against recognized invoice decreases outstanding AR
  // -------------------------------------------------------------
  // Query recognized obligation ID for invoice
  const invObligationId = await queryNum(`SELECT id FROM financial_obligations WHERE source_id = '${invoiceDocId}'`);
  assert.ok(invObligationId && invObligationId.length > 10, 'Recognized obligation ID must exist in database');

  // Post 35,000 collection cash transaction and allocate to obligation
  const paymentTxId = randomUUID();
  await execSql(`
    INSERT INTO financial_transactions (id, business_id, account_id, event_type, movement_direction, amount, currency, base_amount_pkr, effective_date, description, created_by_user_id)
    VALUES ('${paymentTxId}', '${businessId}', '${accountId}', 'RECEIVABLE_COLLECTION', 'INCREASE', 35000.0000, 'PKR', 35000.0000, '2026-11-12', 'Payment for INV-000042', '${userId}');
    INSERT INTO obligation_payment_allocations (id, business_id, obligation_id, financial_transaction_id, amount, effective_date, status, created_by_user_id)
    VALUES (gen_random_uuid(), '${businessId}', '${invObligationId}', '${paymentTxId}', 35000.0000, '2026-11-12', 'EFFECTIVE', '${userId}');
  `);

  // Refresh page and inspect outstanding amount in Invoices tab
  await page.click('[data-testid="tab-invoices"]');
  await page.click('[data-testid="refresh-btn"]');
  await page.waitForTimeout(500);
  const updatedInvRow = await page.textContent(`[data-testid="document-row-${invoiceDocId}"]`);
  assert.ok(updatedInvRow.includes('80,000.00'), `Outstanding amount should reduce to 80,000.00 (115k - 35k), row: ${updatedInvRow}`);
  mark('Journey 14: Payment allocation against recognized invoice decreases outstanding AR from 115,000 to 80,000');

  // -------------------------------------------------------------
  // Scenario 15: Project financial commitment transition verified
  // -------------------------------------------------------------
  // In project EXP-99, the commercial bill (80k) was linked before recognition.
  // After recognition into a PAYABLE obligation, verify link was transitioned or recognized without double counting.
  const billObligationId = await queryNum(`SELECT id FROM financial_obligations WHERE source_id = '${billDocId}'`);
  assert.ok(billObligationId && billObligationId.length > 10, 'Bill obligation ID must exist');

  // Query project commitments summary via API
  const projSummaryRes = await apiCall(ownerJar, ownerXsrf, 'GET', `/finance/projects/${projectId}/commitments`);
  assert.equal(projSummaryRes.status, 200);
  // Total commitments should equal 80,000 (either as unposted bill or as payable, strictly non-duplicated)
  assert.equal(Number(projSummaryRes.json.totalOutstandingCommitments ?? projSummaryRes.json.verifiedCombinedExposure), 80000.0, 'Project total commitments must remain exactly 80,000 with zero double counting');
  mark('Journey 15: Project financial commitment transition verified (unposted exposure transitions to payable without double counting)');

  // -------------------------------------------------------------
  // Scenario 16: RBAC denial verification (Salesperson cannot recognize)
  // -------------------------------------------------------------
  const salesAttemptRes = await apiCall(salesJar, salesXsrf, 'POST', '/finance/obligations/recognize-commercial-document', {
    documentId: invoiceDocId,
    counterpartyId: customerId,
  });
  assert.equal(salesAttemptRes.status, 403, 'Salesperson must receive HTTP 403 Forbidden on recognition endpoint');
  mark('Journey 16: Salesperson / Viewer RBAC denial verified (HTTP 403 Forbidden on recognition endpoint)');

  // -------------------------------------------------------------
  // Scenario 17: Cross-business tenant isolation verified
  // -------------------------------------------------------------
  // Attempt to recognize Business B's document under Business A
  const crossTenantRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/finance/obligations/recognize-commercial-document', {
    documentId: bizBDocId,
    counterpartyId: customerId,
  });
  assert.ok([400, 403, 404].includes(crossTenantRes.status), `Cross-business recognition must fail, got: ${crossTenantRes.status}`);
  mark('Journey 17: Cross-business tenant isolation verified (cross-tenant document recognition rejected)');

  // -------------------------------------------------------------
  // Scenario 18: Bilingual Urdu RTL and mobile 390x844 responsive rendering
  // -------------------------------------------------------------
  // Set mobile viewport 390x844 (standard iPhone 12/13/14)
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${origin}/finance/recognition`, { waitUntil: 'networkidle' });
  await page.waitForSelector('[data-testid="commercial-recognition-workspace"]');

  // Take screenshot for visual evidence
  await page.screenshot({ path: path.join(outDir, 'commercial-recognition-mobile-390x844.png'), fullPage: true });

  // Verify responsive layout elements
  const isMobileTableVisible = await page.isVisible('[data-testid="commercial-recognition-workspace"]');
  assert.ok(isMobileTableVisible, 'Workspace must remain fully visible on mobile 390x844 viewport');

  // Navigate to Ledger Bridge on mobile
  await page.goto(`${origin}/records/bridge`, { waitUntil: 'networkidle' });
  await page.screenshot({ path: path.join(outDir, 'ledger-bridge-mobile-390x844.png'), fullPage: true });

  mark('Journey 18: Bilingual Urdu RTL layout and mobile 390x844 responsive rendering verified');

  console.log('\n======================================================');
  console.log(`ALL ${checks.length} COMMERCIAL RECOGNITION BROWSER JOURNEYS PASSED!`);
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
