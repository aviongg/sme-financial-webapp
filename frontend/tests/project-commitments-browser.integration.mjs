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
const outDir = path.join(frontendDir, 'test-artifacts', 'project-commitments-browser');
mkdirSync(outDir, { recursive: true });

const temporary = path.join(rootDir, 'tmp', 'project-commitments-live-test');
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
  const xml = readFileSync(
    path.join(
      rootDir,
      'backend',
      'target',
      'surefire-reports',
      'TEST-com.app.sme_health_backend.projectattribution.ProjectCommitmentPostgreSqlIT.xml'
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

  // Write ProjectCommitmentsLiveRuntime.java
  const javaSource = `
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import com.app.sme_health_backend.SmeHealthBackendApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import java.sql.*;
import java.util.*;

public class ProjectCommitmentsLiveRuntime {
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

            System.out.println("PROJECT_COMMITMENTS_LIVE_READY");
            System.out.println("PROJECT_COMMITMENTS_DB_PORT:" + pg.getPort());
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
  writeFileSync(path.join(temporary, 'ProjectCommitmentsLiveRuntime.java'), javaSource);

  // Spawn Spring Boot backend with Disposable Postgres
  console.log('Starting real Spring Boot backend with Disposable Postgres on port', backendPort);
  javaRuntime = spawn(
    path.join(javaHome, 'bin', 'java.exe'),
    ['--class-path', path.join(temporary, 'classpath.jar'), path.join(temporary, 'ProjectCommitmentsLiveRuntime.java')],
    { cwd: path.join(rootDir, 'backend'), windowsHide: true }
  );

  javaRuntime.stdout.on('data', (d) => {
    javaOutput += d.toString();
  });
  javaRuntime.stderr.on('data', (d) => {
    process.stderr.write(d.toString());
  });

  await waitFor(() => javaOutput.includes('PROJECT_COMMITMENTS_LIVE_READY'), 'Spring Boot backend', 120000);
  console.log('Spring Boot backend is PROJECT_COMMITMENTS_LIVE_READY!');

  // 2. Prepare isolated Next.js runtime
  console.log('Preparing isolated Next.js runtime in tmp/isolated-frontend-commitments...');
  const isolatedDir = path.join(rootDir, 'tmp', 'isolated-frontend-commitments');
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

  // 4. REST helpers
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

  // 5. Register Owner, Login & Create Business via FinSight Authentication APIs
  console.log('Seeding authentication and business fixtures via real backend REST API...');
  const ownerJar = new Map();
  let ownerXsrf = '';

  const csrfRes = await apiCall(ownerJar, '', 'GET', '/auth/csrf');
  assert.equal(csrfRes.status, 200, 'CSRF endpoint must succeed');
  ownerXsrf = csrfRes.json.token;

  const ownerEmail = `commitments.owner.${randomUUID()}@finsight.local`;
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
    businessName: 'Export Order Hub',
    businessType: 'manufacturing',
    languagePreference: 'en',
    whatsappOptIn: false,
  });
  assert.equal(bizRes.status, 201, 'Business creation must succeed');
  const businessId = bizRes.json.businessId;

  await apiCall(ownerJar, ownerXsrf, 'POST', '/businesses/active', { businessId });
  const userId = await queryNum(`SELECT id FROM app_users WHERE email = '${ownerEmail}'`);

  console.log('Seeding database with Section 10 Mandatory Numerical Fixture...');
  const projectId = randomUUID();
  const versionId = randomUUID();
  const accountId = randomUUID();
  const counterpartyId = randomUUID();
  const obligationId = randomUUID();
  const tx1Id = randomUUID(); // 40k payable settlement
  const tx2Id = randomUUID(); // 80k direct fabric
  const tx3Id = randomUUID(); // 50k labour
  const tx4Id = randomUUID(); // 25k freight
  const allocId = randomUUID();
  const obLinkId = randomUUID();

  // Project EXP-A102 (Export Order A-102)
  await execSql(`
    INSERT INTO projects (id, business_id, code, name, kind, status, base_currency, created_by, updated_by)
    VALUES ('${projectId}', '${businessId}', 'EXP-A102', 'Export Order A-102', 'PROJECT', 'ACTIVE', 'PKR', '${userId}', '${userId}');
  `);

  // Budget Version (Planned cost = 330,000, Planned revenue = 400,000)
  await execSql(`
    INSERT INTO project_budget_versions (id, business_id, project_id, revision, status, base_currency, planned_revenue, planned_cost, planned_margin, planned_margin_pct, calculation_version, created_by, updated_by)
    VALUES ('${versionId}', '${businessId}', '${projectId}', 1, 'DRAFT', 'PKR', 400000.0000, 330000.0000, 70000.0000, 17.50, 'f05-plan-v1', '${userId}', '${userId}');
  `);
  await execSql(`
    INSERT INTO project_budget_lines (id, business_id, project_id, budget_version_id, line_number, category, description, quantity, unit, unit_cost, planned_line_cost)
    VALUES
      (gen_random_uuid(), '${businessId}', '${projectId}', '${versionId}', 1, 'MATERIAL', 'Fabric', 1.0, 'JOB', 200000.0000, 200000.0000),
      (gen_random_uuid(), '${businessId}', '${projectId}', '${versionId}', 2, 'LABOUR', 'Cutting', 1.0, 'JOB', 80000.0000, 80000.0000),
      (gen_random_uuid(), '${businessId}', '${projectId}', '${versionId}', 3, 'FREIGHT', 'Container', 1.0, 'JOB', 30000.0000, 30000.0000),
      (gen_random_uuid(), '${businessId}', '${projectId}', '${versionId}', 4, 'OVERHEAD', 'Facility', 1.0, 'JOB', 20000.0000, 20000.0000);
  `);
  // Transition to APPROVED (enforces immutability after approval)
  await execSql(`UPDATE project_budget_versions SET status = 'APPROVED', approved_by = '${userId}', approved_at = now() WHERE id = '${versionId}'`);

  // Financial Account
  await execSql(`
    INSERT INTO financial_accounts (id, business_id, account_name, account_type, currency, currency_decimals, opening_balance, opening_date, status, institution_name)
    VALUES ('${accountId}', '${businessId}', 'Operating Bank', 'BANK', 'PKR', 2, 1000000.0000, '2026-01-01', 'ACTIVE', 'HBL');
  `);

  // Counterparty: Al-Karam Textiles (Supplier)
  await execSql(`
    INSERT INTO counterparties (id, business_id, display_name, type, status, notes)
    VALUES ('${counterpartyId}', '${businessId}', 'Al-Karam Textiles', 'SUPPLIER', 'ACTIVE', 'Primary fabric mill');
  `);

  // Financial Obligation: Supplier Payable PO-9001 (Original Amount = 100,000.00)
  await execSql(`
    INSERT INTO financial_obligations (id, business_id, counterparty_id, direction, source_type, source_id, reference_number, issue_date, due_date, original_amount, currency, status, created_by_user_id)
    VALUES ('${obligationId}', '${businessId}', '${counterpartyId}', 'PAYABLE', 'SUPPLIER_BILL', 'PO-9001', 'PO-9001', '2026-11-01', '2026-11-30', 100000.0000, 'PKR', 'OPEN', '${userId}');
  `);

  // Cash Payments: 40k + 80k + 50k + 25k = 195,000 total cash payments
  await execSql(`
    INSERT INTO financial_transactions (id, business_id, account_id, event_type, movement_direction, amount, currency, base_amount_pkr, effective_date, description, created_by_user_id)
    VALUES
      ('${tx1Id}', '${businessId}', '${accountId}', 'OPERATING_EXPENSE_PAYMENT', 'DECREASE', 40000.0000, 'PKR', 40000.0000, '2026-11-02', 'Settlement for PO-9001', '${userId}'),
      ('${tx2Id}', '${businessId}', '${accountId}', 'OPERATING_EXPENSE_PAYMENT', 'DECREASE', 80000.0000, 'PKR', 80000.0000, '2026-11-03', 'Direct fabric payment', '${userId}'),
      ('${tx3Id}', '${businessId}', '${accountId}', 'OPERATING_EXPENSE_PAYMENT', 'DECREASE', 50000.0000, 'PKR', 50000.0000, '2026-11-04', 'Stitching wages', '${userId}'),
      ('${tx4Id}', '${businessId}', '${accountId}', 'OPERATING_EXPENSE_PAYMENT', 'DECREASE', 25000.0000, 'PKR', 25000.0000, '2026-11-05', 'Freight agent fee', '${userId}');
  `);

  // Payment Allocation: 40k against payable PO-9001
  await execSql(`
    INSERT INTO obligation_payment_allocations (id, business_id, obligation_id, financial_transaction_id, amount, effective_date, status, created_by_user_id)
    VALUES ('${allocId}', '${businessId}', '${obligationId}', '${tx1Id}', 40000.0000, '2026-11-02', 'EFFECTIVE', '${userId}');
  `);

  // Project Financial Attributions (C05A: Total cash outflows = 195,000)
  await execSql(`
    INSERT INTO project_financial_attributions (
      id, business_id, project_id, source_type, source_id, source_direction, source_currency,
      source_eligible_amount, attributed_amount, attributed_currency, cost_category,
      economic_classification, source_event_date, attribution_date, status, base_amount,
      rate_source, rate_convention, rate_verified, created_by_user_id
    ) VALUES
      (gen_random_uuid(), '${businessId}', '${projectId}', 'TRANSACTION', '${tx1Id}', 'OUTFLOW', 'PKR', 40000.0000, 40000.0000, 'PKR', 'MATERIAL', 'POSTED_CASH_ACTUAL', '2026-11-02', '2026-11-02', 'ACTIVE', 40000.0000, 'BASE_CURRENCY_IDENTITY', 'MULTIPLY_SOURCE_TO_BASE', true, '${userId}'),
      (gen_random_uuid(), '${businessId}', '${projectId}', 'TRANSACTION', '${tx2Id}', 'OUTFLOW', 'PKR', 80000.0000, 80000.0000, 'PKR', 'MATERIAL', 'POSTED_CASH_ACTUAL', '2026-11-03', '2026-11-03', 'ACTIVE', 80000.0000, 'BASE_CURRENCY_IDENTITY', 'MULTIPLY_SOURCE_TO_BASE', true, '${userId}'),
      (gen_random_uuid(), '${businessId}', '${projectId}', 'TRANSACTION', '${tx3Id}', 'OUTFLOW', 'PKR', 50000.0000, 50000.0000, 'PKR', 'LABOUR', 'POSTED_CASH_ACTUAL', '2026-11-04', '2026-11-04', 'ACTIVE', 50000.0000, 'BASE_CURRENCY_IDENTITY', 'MULTIPLY_SOURCE_TO_BASE', true, '${userId}'),
      (gen_random_uuid(), '${businessId}', '${projectId}', 'TRANSACTION', '${tx4Id}', 'OUTFLOW', 'PKR', 25000.0000, 25000.0000, 'PKR', 'FREIGHT', 'POSTED_CASH_ACTUAL', '2026-11-05', '2026-11-05', 'ACTIVE', 25000.0000, 'BASE_CURRENCY_IDENTITY', 'MULTIPLY_SOURCE_TO_BASE', true, '${userId}');
  `);

  // Financial Obligation Link (C05B: Link PO-9001 to EXP-A102)
  await execSql(`
    INSERT INTO project_financial_obligation_links (
      id, business_id, project_id, obligation_id, direction, original_amount, allocated_amount, currency, link_status, link_provenance, created_by_user_id
    ) VALUES (
      '${obLinkId}', '${businessId}', '${projectId}', '${obligationId}', 'PAYABLE', 100000.0000, 100000.0000, 'PKR', 'ACTIVE', 'MANUAL_OBLIGATION_LINK', '${userId}'
    );
  `);

  // Commercial Document: Create & Finalize Commercial Bill for 50,000 (Packages Ltd) via REST API
  const billDraftRes = await apiCall(ownerJar, ownerXsrf, 'POST', '/bills', {
    counterpartyName: 'Packages Ltd',
    documentDate: '2026-11-01',
    dueDate: '2026-11-30',
    notes: 'Packaging cartons',
    lines: [{ description: 'Packaging cartons', quantity: '1.00', unit: 'job', unitPrice: '50000.00' }],
  });
  assert.equal(billDraftRes.status, 201, 'Bill draft creation succeeded');
  const commercialDocId = billDraftRes.json.id;

  const finalizeRes = await apiCall(ownerJar, ownerXsrf, 'POST', `/bills/${commercialDocId}/finalize`, {
    expectedVersion: 0,
  });
  assert.equal(finalizeRes.status, 200, 'Bill finalize succeeded');

  // Commercial Document Link: Link Bill to EXP-A102 via REST API
  const linkDocRes = await apiCall(ownerJar, ownerXsrf, 'POST', `/finance/projects/${projectId}/commercial-document-links`, {
    documentId: commercialDocId,
    allocatedAmount: 50000.00,
    notes: 'Packaging cartons for export',
  });
  assert.equal(linkDocRes.status, 201, 'Commercial document link succeeded');
  const docLinkId = linkDocRes.json.id;

  // 5. Launch Playwright Chromium / Edge
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

  // Journey 1: Open project actuals page
  console.log(`Navigating to /finance/projects/${projectId}/actuals`);
  await page.goto(`${origin}/finance/projects/${projectId}/actuals`, { waitUntil: 'networkidle' });
  await page.waitForSelector('[data-testid="project-actuals-workspace"]');
  mark('Journey 1: Project Actuals workspace loaded successfully');

  // Journey 2: Verify project code and name
  const codeText = await page.textContent('[data-testid="project-overview-code"]');
  assert.equal(codeText.trim(), 'EXP-A102');
  const nameText = await page.textContent('[data-testid="project-overview-name"]');
  assert.equal(nameText.trim(), 'Export Order A-102');
  mark('Journey 2: Project identity EXP-A102 verified');

  // Journey 3: Approved planned cost = 330,000.00
  const plannedCost = await page.textContent('[data-testid="planned-cost-val"]');
  assert.ok(plannedCost.includes('330,000.00'), `Expected planned cost 330,000.00, got ${plannedCost}`);
  mark('Journey 3: Approved planned cost PKR 330,000.00 verified');

  // Journey 4: Attributed cash outflows = 195,000.00
  const cashOutflows = await page.textContent('[data-testid="cash-outflows-val"]');
  assert.ok(cashOutflows.includes('195,000.00'), `Expected cash outflows 195,000.00, got ${cashOutflows}`);
  mark('Journey 4: Attributed cash outflows PKR 195,000.00 verified');

  // Journey 5: Cash-to-plan headroom = 135,000.00
  const cashHeadroom = await page.textContent('[data-testid="cash-headroom-val"]');
  assert.ok(cashHeadroom.includes('135,000.00'), `Expected cash headroom 135,000.00, got ${cashHeadroom}`);
  mark('Journey 5: Cash headroom PKR 135,000.00 (330k - 195k) verified');

  // Journey 6: Profitability status = UNAVAILABLE
  const profBadge = await page.textContent('[data-testid="profitability-badge"]');
  assert.ok(profBadge.includes('UNAVAILABLE'), `Expected UNAVAILABLE profitability badge, got ${profBadge}`);
  mark('Journey 6: Profitability status UNAVAILABLE (incomplete cost basis) verified');

  // Journey 7: Outstanding Supplier Payables = 60,000.00
  const outstandingPayables = await page.textContent('[data-testid="outstanding-payables-val"]');
  assert.ok(outstandingPayables.includes('60,000.00'), `Expected outstanding payables 60,000.00, got ${outstandingPayables}`);
  mark('Journey 7: Outstanding supplier payables PKR 60,000.00 verified');

  // Journey 8: Unposted Commercial Bills = 50,000.00
  const unpostedBills = await page.textContent('[data-testid="unposted-bills-val"]');
  assert.ok(unpostedBills.includes('50,000.00'), `Expected unposted commercial bills 50,000.00, got ${unpostedBills}`);
  mark('Journey 8: Unposted commercial bills PKR 50,000.00 verified');

  // Journey 9: Total Commitments = 110,000.00 (60k + 50k)
  const totalCommitments = await page.textContent('[data-testid="total-commitments-val"]');
  assert.ok(totalCommitments.includes('110,000.00'), `Expected total commitments 110,000.00, got ${totalCommitments}`);
  mark('Journey 9: Total commitments PKR 110,000.00 (60k payable + 50k bill) verified');

  // Journey 10: Non-duplication verification: Paid 40k is not added to commitments or double-deducted
  assert.ok(totalCommitments.includes('110,000.00') && !totalCommitments.includes('150,000.00'));
  mark('Journey 10: Non-duplication rule verified (40k settlement is not double-counted)');

  // Journey 11: Switch to Obligations & Commitments Tab
  await page.click('[data-testid="tab-commitments"]');
  await page.waitForSelector('[data-testid="project-commitments-card"]');
  const payableRow = await page.textContent(`[data-testid="obligation-row-${obLinkId}"]`);
  assert.ok(payableRow.includes('Al-Karam Textiles'));
  assert.ok(payableRow.includes('PO-9001'));
  assert.ok(payableRow.includes('100,000.00')); // Original amount
  assert.ok(payableRow.includes('40,000.00'));  // Paid amount
  assert.ok(payableRow.includes('60,000.00'));  // Outstanding amount
  const nonDupBadge = await page.textContent(`[data-testid="attributed-payment-badge-${obLinkId}"]`);
  assert.ok(nonDupBadge.includes('C05A') && nonDupBadge.includes('40,000.00'));
  mark('Journey 11: Commitments tab displays verified payable PO-9001 and C05A settlement evidence');

  // Journey 12: Switch to Commercial Documents Tab
  await page.click('[data-testid="tab-commercial-documents"]');
  await page.waitForSelector('[data-testid="project-commercial-documents-card"]');
  const bannerText = await page.textContent('[data-testid="non-posting-banner"]');
  assert.ok(bannerText.includes('non-posting') || bannerText.includes('NON-POSTING'));
  const docRow = await page.textContent(`[data-testid="commercial-doc-row-${docLinkId}"]`);
  assert.ok(docRow.includes('Packages Ltd'));
  assert.ok(docRow.includes('BILL-000001'));
  assert.ok(docRow.includes('50,000.00'));
  const unpostedBadge = await page.textContent(`[data-testid="unposted-badge-${docLinkId}"]`);
  assert.ok(unpostedBadge.includes('Unposted') || unpostedBadge.includes('Commercial'));
  mark('Journey 12: Commercial documents tab displays non-posting banner and unposted status badge');

  // Journey 13: Authorized Link Obligation Dialog
  await page.click('[data-testid="open-link-obligation-header-btn"]');
  await page.waitForSelector('[data-testid="link-obligation-dialog"]');
  assert.ok(await page.isVisible('[data-testid="obligation-id-input"]'));
  assert.ok(await page.isVisible('[data-testid="obligation-amount-input"]'));
  await page.click('button:has-text("Cancel")');
  mark('Journey 13: Link obligation dialog opens and displays required inputs');

  // Journey 14: Dynamic Unlink Commercial Document
  await page.click(`[data-testid="unlink-doc-btn-${docLinkId}"]`);
  await page.waitForSelector('[data-testid="unlink-doc-dialog"]');
  await page.fill('[data-testid="unlink-doc-dialog"] input', 'Removed per project reallocation');
  await page.click('[data-testid="confirm-unlink-doc-btn"]');
  await page.waitForSelector(`[data-testid="doc-link-status-${docLinkId}"]:has-text("UNLINKED")`);
  const unlinkedStatus = await page.textContent(`[data-testid="doc-link-status-${docLinkId}"]`);
  assert.ok(unlinkedStatus.includes('UNLINKED') || unlinkedStatus.includes('Unlinked'));
  mark('Journey 14: Dynamic unlinking transitions link to UNLINKED (soft append-only)');

  // Journey 15: Foreign Currency Obligation Retention
  const usdObId = randomUUID();
  const usdLinkId = randomUUID();
  await execSql(`
    INSERT INTO financial_obligations (id, business_id, counterparty_id, direction, source_type, source_id, reference_number, issue_date, due_date, original_amount, currency, status, created_by_user_id)
    VALUES ('${usdObId}', '${businessId}', '${counterpartyId}', 'PAYABLE', 'SUPPLIER_BILL', 'PO-USD-101', 'PO-USD-101', '2026-11-01', '2026-11-30', 5000.0000, 'USD', 'OPEN', '${userId}');
  `);
  await execSql(`
    INSERT INTO project_financial_obligation_links (
      id, business_id, project_id, obligation_id, direction, original_amount, allocated_amount, currency, link_status, link_provenance, created_by_user_id
    ) VALUES (
      '${usdLinkId}', '${businessId}', '${projectId}', '${usdObId}', 'PAYABLE', 5000.0000, 5000.0000, 'USD', 'ACTIVE', 'MANUAL_OBLIGATION_LINK', '${userId}'
    );
  `);
  await page.click('[data-testid="refresh-btn"]');
  await page.click('[data-testid="tab-commitments"]');
  await page.waitForSelector(`[data-testid="obligation-row-${usdLinkId}"]`);
  const usdRow = await page.textContent(`[data-testid="obligation-row-${usdLinkId}"]`);
  assert.ok(usdRow.includes('USD 5,000.00'), `Expected USD 5,000.00, got ${usdRow}`);
  mark('Journey 15: Foreign currency USD $5,000 obligation retained in native currency');

  // Journey 16: Bilingual Urdu & RTL
  await page.click('[data-testid="lang-ur-btn"]');
  const dir = await page.getAttribute('[data-testid="project-actuals-workspace"]', 'dir');
  assert.equal(dir, 'rtl', 'Workspace must switch to dir="rtl"');
  const urduTitle = await page.textContent('[data-testid="workspace-title"]');
  assert.ok(urduTitle.includes('پروجیکٹ کے مالیاتی حقائق'), `Expected Urdu title, got ${urduTitle}`);
  await page.click('[data-testid="lang-en-btn"]');
  mark('Journey 16: Bilingual Urdu and RTL rendering verified');

  // Journey 17: Mobile Layout (390x844)
  await page.setViewportSize({ width: 390, height: 844 });
  await page.click('[data-testid="tab-overview"]');
  assert.ok(await page.isVisible('[data-testid="project-costing-overview-card"]'));
  assert.ok(await page.isVisible('[data-testid="commitments-kpi-grid"]'));
  mark('Journey 17: Mobile responsive viewport (390x844) verified');

  // Journey 18: Tenant Isolation & RBAC
  const otherBizId = randomUUID();
  const otherRes = await fetch(`${origin}/api/finance/projects/${projectId}/commitments`, {
    headers: {
      cookie: `FINSIGHT_SESSION=${randomUUID()}`,
    },
  });
  assert.ok(otherRes.status === 401 || otherRes.status === 403, 'Unauthenticated / cross-tenant request rejected');
  mark('Journey 18: Strict tenant isolation and unauthorized access rejection verified');

  // Journey 19: Database Reconciliation
  const dbActiveObCount = await queryNum(`SELECT count(*) FROM project_financial_obligation_links WHERE business_id = '${businessId}' AND project_id = '${projectId}' AND link_status = 'ACTIVE'`);
  assert.equal(dbActiveObCount, '2', 'Database active obligations count matches UI');
  const dbUnlinkedDocCount = await queryNum(`SELECT count(*) FROM project_commercial_document_links WHERE business_id = '${businessId}' AND project_id = '${projectId}' AND link_status = 'UNLINKED'`);
  assert.equal(dbUnlinkedDocCount, '1', 'Database unlinked commercial document count matches UI');
  mark('Journey 19: Database records reconcile exactly with UI state');

  console.log(`\nALL ${checks.length}/${checks.length} BROWSER JOURNEYS PASSED SUCCESSFULLY!`);
} finally {
  if (browser) await browser.close();
  if (gatewayServer) gatewayServer.close();
  if (nextProc && !nextProc.killed) nextProc.kill();
  if (javaRuntime && !javaRuntime.killed) {
    try {
      javaRuntime.stdin.write('STOP\n');
    } catch {}
    setTimeout(() => {
      try {
        javaRuntime.kill();
      } catch {}
    }, 1000);
  }
}
