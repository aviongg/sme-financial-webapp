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
const outDir = path.join(frontendDir, 'test-artifacts', 'project-ocr-evidence-browser');
mkdirSync(outDir, { recursive: true });

const temporary = path.join(rootDir, 'tmp', 'project-ocr-evidence-live-test');
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
      'TEST-com.app.sme_health_backend.projectattribution.ProjectOcrEvidencePostgreSqlIT.xml'
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

  // Write ProjectOcrEvidenceLiveRuntime.java
  const javaSource = `
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import com.app.sme_health_backend.SmeHealthBackendApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import java.sql.*;
import java.util.*;

public class ProjectOcrEvidenceLiveRuntime {
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
                "--finsight.crypto.allow-legacy-plaintext=true",
                "--app.security.cors.allowed-origins=${origin}", "--logging.level.root=WARN",
                "--logging.level.com.app.sme_health_backend=INFO");

            System.out.println("PROJECT_OCR_EVIDENCE_LIVE_READY");
            System.out.println("PROJECT_OCR_EVIDENCE_DB_PORT:" + pg.getPort());
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
  writeFileSync(path.join(temporary, 'ProjectOcrEvidenceLiveRuntime.java'), javaSource);

  // Spawn Spring Boot backend with Disposable Postgres
  console.log('Starting real Spring Boot backend with Disposable Postgres on port', backendPort);
  javaRuntime = spawn(
    path.join(javaHome, 'bin', 'java.exe'),
    ['--class-path', path.join(temporary, 'classpath.jar'), path.join(temporary, 'ProjectOcrEvidenceLiveRuntime.java')],
    { cwd: path.join(rootDir, 'backend'), windowsHide: true }
  );

  javaRuntime.stdout.on('data', (d) => {
    const s = d.toString();
    javaOutput += s;
    if (s.includes('ERROR') || s.includes('Exception') || s.includes('Internal Server Error')) {
      process.stderr.write(s);
    }
  });
  javaRuntime.stderr.on('data', (d) => {
    process.stderr.write(d.toString());
  });

  await waitFor(() => javaOutput.includes('PROJECT_OCR_EVIDENCE_LIVE_READY'), 'Spring Boot backend', 120000);
  console.log('Spring Boot backend is PROJECT_OCR_EVIDENCE_LIVE_READY!');

  // 2. Prepare isolated Next.js runtime
  console.log('Preparing isolated Next.js runtime in tmp/isolated-frontend-ocr-evidence...');
  const isolatedDir = path.join(rootDir, 'tmp', 'isolated-frontend-ocr-evidence');
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

  const ownerEmail = `ocr.owner.${randomUUID()}@finsight.local`;
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

  // Ensure business_profiles row exists for foreign keys to uploaded_documents
  await execSql(`
    INSERT INTO business_profiles (user_id, business_type)
    VALUES ('${businessId}', 'manufacturing')
    ON CONFLICT (user_id) DO NOTHING;
  `);

  console.log('Seeding database with Section 10 Mandatory Numerical Fixture...');
  const projectId = randomUUID();
  const project2Id = randomUUID();
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
  const attr1Id = randomUUID();
  const attr2Id = randomUUID();
  const attr3Id = randomUUID();
  const attr4Id = randomUUID();

  // Project 1 EXP-A102 (Export Order A-102)
  await execSql(`
    INSERT INTO projects (id, business_id, code, name, kind, status, base_currency, created_by, updated_by)
    VALUES ('${projectId}', '${businessId}', 'EXP-A102', 'Export Order A-102', 'PROJECT', 'ACTIVE', 'PKR', '${userId}', '${userId}');
  `);

  // Project 2 PRJ-B (Local Wholesale B-101) for split testing
  await execSql(`
    INSERT INTO projects (id, business_id, code, name, kind, status, base_currency, created_by, updated_by)
    VALUES ('${project2Id}', '${businessId}', 'PRJ-B', 'Local Wholesale B-101', 'PROJECT', 'ACTIVE', 'PKR', '${userId}', '${userId}');
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
      ('${attr1Id}', '${businessId}', '${projectId}', 'TRANSACTION', '${tx1Id}', 'OUTFLOW', 'PKR', 40000.0000, 40000.0000, 'PKR', 'MATERIAL', 'POSTED_CASH_ACTUAL', '2026-11-02', '2026-11-02', 'ACTIVE', 40000.0000, 'BASE_CURRENCY_IDENTITY', 'MULTIPLY_SOURCE_TO_BASE', true, '${userId}'),
      ('${attr2Id}', '${businessId}', '${projectId}', 'TRANSACTION', '${tx2Id}', 'OUTFLOW', 'PKR', 80000.0000, 80000.0000, 'PKR', 'MATERIAL', 'POSTED_CASH_ACTUAL', '2026-11-03', '2026-11-03', 'ACTIVE', 80000.0000, 'BASE_CURRENCY_IDENTITY', 'MULTIPLY_SOURCE_TO_BASE', true, '${userId}'),
      ('${attr3Id}', '${businessId}', '${projectId}', 'TRANSACTION', '${tx3Id}', 'OUTFLOW', 'PKR', 50000.0000, 50000.0000, 'PKR', 'LABOUR', 'POSTED_CASH_ACTUAL', '2026-11-04', '2026-11-04', 'ACTIVE', 50000.0000, 'BASE_CURRENCY_IDENTITY', 'MULTIPLY_SOURCE_TO_BASE', true, '${userId}'),
      ('${attr4Id}', '${businessId}', '${projectId}', 'TRANSACTION', '${tx4Id}', 'OUTFLOW', 'PKR', 25000.0000, 25000.0000, 'PKR', 'FREIGHT', 'POSTED_CASH_ACTUAL', '2026-11-05', '2026-11-05', 'ACTIVE', 25000.0000, 'BASE_CURRENCY_IDENTITY', 'MULTIPLY_SOURCE_TO_BASE', true, '${userId}');
  `);

  // Financial Obligation Link (C05B: Link PO-9001 to EXP-A102)
  await execSql(`
    INSERT INTO project_financial_obligation_links (
      id, business_id, project_id, obligation_id, direction, original_amount, allocated_amount, currency, link_status, link_provenance, created_by_user_id
    ) VALUES (
      '${obLinkId}', '${businessId}', '${projectId}', '${obligationId}', 'PAYABLE', 100000.0000, 100000.0000, 'PKR', 'ACTIVE', 'MANUAL_OBLIGATION_LINK', '${userId}'
    );
  `);

  // Uploaded Documents Seeding:
  // Doc 1: 100,000.00 PKR receipt for Al-Karam Fabric
  const doc1Id = randomUUID();
  const doc1Json = JSON.stringify({
    amount: 100000.00,
    currency: 'PKR',
    date: '2026-11-02',
    vendor_or_party: 'Al-Karam Fabric',
    category: 'materials',
    confidence: 0.98,
    document_type_detected: 'receipt',
  });
  await execSql(`
    INSERT INTO uploaded_documents (
      id, user_id, file_url, original_filename, processing_status,
      extracted_data, reviewed_data, confirmed_data, document_type_hint
    ) VALUES (
      '${doc1Id}', '${businessId}', '/api/documents/${doc1Id}/file', 'receipt-fabric-100k.pdf', 'confirmed',
      '${doc1Json}'::jsonb, '${doc1Json}'::jsonb, '${doc1Json}'::jsonb, 'receipt'
    );
  `);

  // Doc 2: 25,000.00 PKR receipt for Office Stationery
  const doc2Id = randomUUID();
  const doc2Json = JSON.stringify({
    amount: 25000.00,
    currency: 'PKR',
    date: '2026-11-05',
    vendor_or_party: 'Office Stationery',
    category: 'overhead',
    confidence: 0.95,
    document_type_detected: 'receipt',
  });
  await execSql(`
    INSERT INTO uploaded_documents (
      id, user_id, file_url, original_filename, processing_status,
      extracted_data, reviewed_data, confirmed_data, document_type_hint
    ) VALUES (
      '${doc2Id}', '${businessId}', '/api/documents/${doc2Id}/file', 'receipt-stationery-25k.pdf', 'confirmed',
      '${doc2Json}'::jsonb, '${doc2Json}'::jsonb, '${doc2Json}'::jsonb, 'receipt'
    );
  `);

  // Doc 3: 85,000.00 PKR receipt for Cutting Workshop (discrepancy candidate against 80,000 tx2Id)
  const doc3Id = randomUUID();
  const doc3Json = JSON.stringify({
    amount: 85000.00,
    currency: 'PKR',
    date: '2026-11-03',
    vendor_or_party: 'Cutting Workshop',
    category: 'labour',
    confidence: 0.92,
    document_type_detected: 'receipt',
  });
  await execSql(`
    INSERT INTO uploaded_documents (
      id, user_id, file_url, original_filename, processing_status,
      extracted_data, reviewed_data, confirmed_data, document_type_hint
    ) VALUES (
      '${doc3Id}', '${businessId}', '/api/documents/${doc3Id}/file', 'receipt-cutting-85k.pdf', 'confirmed',
      '${doc3Json}'::jsonb, '${doc3Json}'::jsonb, '${doc3Json}'::jsonb, 'receipt'
    );
  `);

  // 6. Launch Playwright Chromium / Edge
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
  assert.ok(await page.isVisible('[data-testid="tab-ocr-evidence"]'));
  assert.ok(await page.isVisible('[data-testid="open-link-ocr-header-btn"]'));
  mark('Journey 1: Project Actuals workspace loaded successfully with OCR tab & header button');

  // Journey 2: Verify project code and name
  const codeText = await page.textContent('[data-testid="project-overview-code"]');
  assert.equal(codeText.trim(), 'EXP-A102');
  const nameText = await page.textContent('[data-testid="project-overview-name"]');
  assert.equal(nameText.trim(), 'Export Order A-102');
  const plannedCost = await page.textContent('[data-testid="planned-cost-val"]');
  assert.ok(plannedCost.includes('330,000.00'), `Expected planned cost 330,000.00, got ${plannedCost}`);
  mark('Journey 2: Project identity EXP-A102 and approved planned cost PKR 330,000.00 verified');

  // Journey 3: Attributed cash outflows PKR 195,000.00 and headroom PKR 135,000.00
  const cashOutflows = await page.textContent('[data-testid="cash-outflows-val"]');
  assert.ok(cashOutflows.includes('195,000.00'), `Expected cash outflows 195,000.00, got ${cashOutflows}`);
  const cashHeadroom = await page.textContent('[data-testid="cash-headroom-val"]');
  assert.ok(cashHeadroom.includes('135,000.00'), `Expected cash headroom 135,000.00, got ${cashHeadroom}`);
  mark('Journey 3: Attributed cash outflows PKR 195,000.00 & cash headroom PKR 135,000.00 verified');

  // Journey 4: Central OCR financial rule banner displayed
  const ruleBannerText = await page.textContent('[data-testid="ocr-rule-banner"]');
  assert.ok(
    ruleBannerText.toLowerCase().includes('documentary proof') && ruleBannerText.toLowerCase().includes('not cash authority'),
    `Expected OCR rule banner explaining documentary proof vs cash authority, got ${ruleBannerText}`
  );
  mark('Journey 4: Central OCR financial rule banner ("documentary proof, NOT cash authority") verified');

  // Journey 5: Link reviewed OCR receipt (PKR 100,000.00, Al-Karam Fabric) via REST API and verify in overview card
  const link1Res = await apiCall(ownerJar, ownerXsrf, 'POST', `/finance/projects/${projectId}/ocr-evidence`, {
    documentId: doc1Id,
    attributionId: attr1Id,
    financialSourceId: tx1Id,
    notes: 'Al-Karam fabric invoice receipt proof',
  });
  if (link1Res.status !== 201) {
    console.error('link1Res failed with status:', link1Res.status, 'body:', link1Res.body);
  }
  assert.equal(link1Res.status, 201, 'Link OCR evidence via API must return 201');
  const link1Id = link1Res.json.id;

  await page.click('[data-testid="refresh-btn"]');
  await page.waitForSelector(`[data-testid="ocr-evidence-row-${link1Id}"]`);
  const row1Text = await page.textContent(`[data-testid="ocr-evidence-row-${link1Id}"]`);
  assert.ok(row1Text.includes('receipt-fabric-100k.pdf'), 'Must display filename');
  assert.ok(row1Text.includes('Al-Karam Fabric'), 'Must display vendor');
  assert.ok(row1Text.includes('100,000.00'), 'Must display OCR amount');
  mark('Journey 5: Reviewed OCR receipt (PKR 100,000.00) linked and rendered in overview card');

  // Journey 6: Verify status badge ATTRIBUTED with matched financial transaction source
  const status1Text = await page.textContent(`[data-testid="ocr-status-${link1Id}"]`);
  assert.ok(status1Text.includes('ATTRIBUTED'), `Expected ATTRIBUTED status badge, got ${status1Text}`);
  assert.ok(row1Text.includes('TRANSACTION:'), 'Row must indicate matched financial transaction');
  mark('Journey 6: Status badge ATTRIBUTED with matched financial transaction source verified');

  // Journey 7: Confirm OCR receipt did NOT create any additional cash transactions or alter bank balances
  const txCount = await queryNum(`SELECT count(*) FROM financial_transactions WHERE business_id = '${businessId}'`);
  assert.equal(txCount, '4', 'Transaction count must remain exactly 4 — zero ledger mutations from OCR');
  const cashOutflowsAfter = await page.textContent('[data-testid="cash-outflows-val"]');
  assert.ok(cashOutflowsAfter.includes('195,000.00'), 'Cash outflows must remain 195,000.00 without any inflation');
  mark('Journey 7: Cardinal Rule verified — 0 cash transactions created, cash actuals completely unaffected');

  // Journey 8: Switch to OCR Evidence Tab (tab-ocr-evidence) and verify dedicated view
  await page.click('[data-testid="tab-ocr-evidence"]');
  await page.waitForSelector('[data-testid="project-ocr-evidence-card"]');
  assert.ok(await page.isVisible(`[data-testid="ocr-evidence-row-${link1Id}"]`));
  mark('Journey 8: Switched to dedicated OCR Evidence Tab and verified card presentation');

  // Journey 9: Open Link OCR Evidence dialog via open-link-ocr-header-btn and verify candidate dropdown
  await page.click('[data-testid="open-link-ocr-header-btn"]');
  await page.waitForSelector('[data-testid="link-ocr-dialog"]');
  const selectHtml = await page.innerHTML('[data-testid="ocr-document-select"]');
  assert.ok(selectHtml.includes('Office Stationery'), 'Candidate dropdown must list Office Stationery');
  assert.ok(selectHtml.includes('25000'), 'Candidate dropdown must display 25000 amount');
  mark('Journey 9: Link OCR Evidence dialog opened with candidate dropdown options');

  // Journey 10: Link unmatched reviewed receipt (PKR 25,000.00, Office Stationery) to project
  await page.selectOption('[data-testid="ocr-document-select"]', doc2Id);
  await page.fill('[data-testid="ocr-notes-input"]', 'Stationery receipt pending payment match');
  await page.click('[data-testid="submit-link-ocr-btn"]');
  await page.waitForSelector('[data-testid="link-ocr-dialog"]', { state: 'hidden' });
  mark('Journey 10: Unmatched reviewed receipt linked to project via modal submission');

  // Retrieve link2Id from API or page
  const listRes = await apiCall(ownerJar, '', 'GET', `/finance/projects/${projectId}/ocr-evidence`);
  const link2Item = listRes.json.find((item) => item.documentId === doc2Id);
  assert.ok(link2Item, 'Linked document 2 must be present in evidence list');
  const link2Id = link2Item.id;

  // Journey 11: Verify status REVIEWED_UNMATCHED and assert cash actuals remain 195,000.00 (0 cash effect)
  await page.waitForSelector(`[data-testid="ocr-evidence-row-${link2Id}"]`);
  const status2Text = await page.textContent(`[data-testid="ocr-status-${link2Id}"]`);
  assert.ok(status2Text.includes('REVIEWED UNMATCHED'), `Expected REVIEWED UNMATCHED badge, got ${status2Text}`);

  await page.click('[data-testid="tab-overview"]');
  const cashOutflowsAfterUnmatched = await page.textContent('[data-testid="cash-outflows-val"]');
  assert.ok(cashOutflowsAfterUnmatched.includes('195,000.00'), 'Unmatched receipt must have 0 effect on cash actuals');
  mark('Journey 11: Status REVIEWED_UNMATCHED verified and cash actuals strictly preserved (0 effect)');

  // Journey 12: Link OCR receipt with amount discrepancy (PKR 85,000.00 receipt vs PKR 80,000.00 transaction)
  const link3Res = await apiCall(ownerJar, ownerXsrf, 'POST', `/finance/projects/${projectId}/ocr-evidence`, {
    documentId: doc3Id,
    attributionId: attr2Id,
    financialSourceId: tx2Id, // tx2 is 80,000 PKR; doc3 is 85,000 PKR -> 5,000 discrepancy
    notes: 'Cutting workshop invoice discrepancy test',
  });
  assert.equal(link3Res.status, 201, 'Link OCR evidence with discrepancy must succeed');
  const link3Id = link3Res.json.id;
  mark('Journey 12: OCR receipt linked with amount discrepancy vs transaction');

  // Journey 13: Verify discrepancy warning banner: "Discrepancy: PKR 5,000.00" displayed prominently
  await page.click('[data-testid="refresh-btn"]');
  await page.click('[data-testid="tab-ocr-evidence"]');
  await page.waitForSelector(`[data-testid="discrepancy-badge-${link3Id}"]`);
  const discText = await page.textContent(`[data-testid="discrepancy-badge-${link3Id}"]`);
  assert.ok(discText.includes('5,000.00'), `Expected 5,000.00 discrepancy badge, got ${discText}`);
  const row3Text = await page.textContent(`[data-testid="ocr-evidence-row-${link3Id}"]`);
  assert.ok(row3Text.includes('85,000.00'), 'Receipt amount must remain 85,000.00 unmutated');
  assert.ok(row3Text.includes('80,000.00'), 'Transaction amount must remain 80,000.00 unmutated');
  mark('Journey 13: Discrepancy warning banner PKR 5,000.00 verified with non-mutating integrity');

  // Journey 14: Two-project split verification: verify multi-project split badge on shared receipt
  // Link doc 1 to project 2 (PRJ-B) as well
  const linkSplitRes = await apiCall(ownerJar, ownerXsrf, 'POST', `/finance/projects/${project2Id}/ocr-evidence`, {
    documentId: doc1Id,
    notes: 'Shared fabric receipt portion for project B',
  });
  assert.equal(linkSplitRes.status, 201, 'Linking same receipt to second project must succeed');

  await page.click('[data-testid="refresh-btn"]');
  await page.waitForSelector(`[data-testid="split-badge-${link1Id}"]`);
  const splitText = await page.textContent(`[data-testid="split-badge-${link1Id}"]`);
  assert.ok(splitText.includes('2'), `Expected split badge indicating 2 projects, got ${splitText}`);
  mark('Journey 14: Two-project split badge verified for shared receipt across multiple projects');

  // Journey 15: Confirm Match workflow: open Confirm Match dialog for unmatched receipt and match to transaction
  await page.click(`[data-testid="confirm-match-btn-${link2Id}"]`);
  await page.waitForSelector('[data-testid="confirm-match-dialog"]');
  await page.fill('[data-testid="confirm-match-transaction-id-input"]', tx4Id);
  await page.fill('[data-testid="confirm-match-notes-input"]', 'Matched to 25k payment tx4');
  await page.click('[data-testid="submit-confirm-match-btn"]');
  await page.waitForSelector('[data-testid="confirm-match-dialog"]', { state: 'hidden' });

  // Status transitions from REVIEWED_UNMATCHED to MATCHED TO SOURCE
  await page.waitForSelector(`[data-testid="ocr-status-${link2Id}"]:has-text("MATCHED TO SOURCE")`);
  const newStatus2 = await page.textContent(`[data-testid="ocr-status-${link2Id}"]`);
  assert.ok(newStatus2.includes('MATCHED TO SOURCE'), `Expected MATCHED TO SOURCE status, got ${newStatus2}`);
  mark('Journey 15: Confirm Match workflow successfully transitions evidence to MATCHED TO SOURCE status');

  // Journey 16: Invalidate OCR evidence link workflow: enter reason and confirm invalidation
  await page.click(`[data-testid="invalidate-ocr-btn-${link3Id}"]`);
  await page.waitForSelector('[data-testid="invalidate-confirm-dialog"]');
  await page.fill('[data-testid="invalidate-reason-input"]', 'Receipt attached to incorrect project');
  await page.click('[data-testid="confirm-invalidate-btn"]');
  await page.waitForSelector('[data-testid="invalidate-confirm-dialog"]', { state: 'hidden' });

  await page.waitForSelector(`[data-testid="ocr-status-${link3Id}"]:has-text("INVALIDATED")`);
  const invStatus = await page.textContent(`[data-testid="ocr-status-${link3Id}"]`);
  assert.ok(invStatus.includes('INVALIDATED'), `Expected INVALIDATED status, got ${invStatus}`);
  mark('Journey 16: Dynamic soft-invalidation transition to INVALIDATED verified');

  // Journey 17: Source reversal provenance: reverse underlying transaction and verify linked OCR evidence transitions to REVERSED
  const revRes = await apiCall(ownerJar, ownerXsrf, 'POST', `/finance/transactions/${tx1Id}/reverse`, {
    reason: 'Settlement cancelled by vendor request',
  }, {
    'Idempotency-Key': `rev-${randomUUID()}`,
  });
  assert.equal(revRes.status, 201, 'Transaction reversal must succeed');

  await page.click('[data-testid="refresh-btn"]');
  await page.waitForSelector(`[data-testid="ocr-status-${link1Id}"]:has-text("REVERSED")`);
  const revOcrStatus = await page.textContent(`[data-testid="ocr-status-${link1Id}"]`);
  assert.ok(revOcrStatus.includes('REVERSED'), `Expected REVERSED status on linked OCR evidence, got ${revOcrStatus}`);
  mark('Journey 17: Source transaction reversal cascades provenance to linked OCR evidence (REVERSED)');

  // Journey 18: Bilingual Urdu and RTL rendering verified
  await page.click('[data-testid="lang-ur-btn"]');
  const dir = await page.getAttribute('[data-testid="project-actuals-workspace"]', 'dir');
  assert.equal(dir, 'rtl', 'Workspace must switch to dir="rtl"');
  const urduOcrTitle = await page.textContent('[data-testid="ocr-evidence-title"]');
  assert.ok(urduOcrTitle.includes('او سی آر') || urduOcrTitle.includes('شواہد'), `Expected Urdu title, got ${urduOcrTitle}`);
  const urduBanner = await page.textContent('[data-testid="ocr-rule-banner"]');
  assert.ok(urduBanner.includes('اصول') || urduBanner.includes('دستاویزی'), `Expected Urdu rule banner, got ${urduBanner}`);
  await page.click('[data-testid="lang-en-btn"]');
  mark('Journey 18: Bilingual Urdu and RTL rendering verified across OCR cards and dialogs');

  // Journey 19: Mobile responsive viewport (390x844) verified
  await page.setViewportSize({ width: 390, height: 844 });
  await page.click('[data-testid="tab-overview"]');
  assert.ok(await page.isVisible('[data-testid="project-ocr-evidence-card"]'));
  assert.ok(await page.isVisible('[data-testid="ocr-rule-banner"]'));
  await page.setViewportSize({ width: 1280, height: 800 });
  mark('Journey 19: Mobile responsive viewport (390x844) verified without layout breakage');

  // Journey 20: Database reconciliation: verify project_ocr_evidence_links row counts and statuses match UI state exactly
  const countReversed = await queryNum(`SELECT count(*) FROM project_ocr_evidence_links WHERE business_id = '${businessId}' AND project_id = '${projectId}' AND evidence_status = 'REVERSED'`);
  assert.equal(countReversed, '1', 'Exactly 1 REVERSED link in database');

  const countInvalidated = await queryNum(`SELECT count(*) FROM project_ocr_evidence_links WHERE business_id = '${businessId}' AND project_id = '${projectId}' AND evidence_status = 'INVALIDATED'`);
  assert.equal(countInvalidated, '1', 'Exactly 1 INVALIDATED link in database');

  const countMatched = await queryNum(`SELECT count(*) FROM project_ocr_evidence_links WHERE business_id = '${businessId}' AND project_id = '${projectId}' AND evidence_status = 'MATCHED_TO_POSTED_SOURCE'`);
  assert.equal(countMatched, '1', 'Exactly 1 MATCHED_TO_POSTED_SOURCE link in database');

  mark('Journey 20: Database records reconcile exactly with UI state (1 REVERSED, 1 INVALIDATED, 1 MATCHED_TO_POSTED_SOURCE)');

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
