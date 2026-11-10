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
const outDir = path.join(frontendDir, 'test-artifacts', 'funding-browser');
mkdirSync(outDir, { recursive: true });

const temporary = path.join(rootDir, 'tmp', 'funding-live-test');
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
let acceptancePage;
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
      'TEST-com.app.sme_health_backend.funding.FundingScenarioPostgreSqlIT.xml'
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

  // Write FundingLiveRuntime.java
  const javaSource = `
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import com.app.sme_health_backend.SmeHealthBackendApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import java.sql.*;
import java.util.*;

public class FundingLiveRuntime {
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

            try(var c=pg.getPostgresDatabase().getConnection();var st=c.createStatement();var rr=st.executeQuery("SELECT current_database(), inet_server_port(), inet_server_addr(), (SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1)")) { rr.next();System.out.println("LIVE_IDENTITY:"+rr.getString(1)+":"+rr.getString(2)+":"+rr.getString(3)+":V"+rr.getString(4)); }
            System.out.println("LIVE_READY");
            System.out.println("LIVE_DB_PORT:" + pg.getPort());
            System.out.flush();

            var input = new Scanner(System.in);
            while (input.hasNextLine()) {
                String line = input.nextLine().trim();
                if (line.equals("STOP")) {
                    break;
                } else if (line.startsWith("ZERO_MUTATION_CHECK ")) {
                    String tag = line.substring("ZERO_MUTATION_CHECK ".length());
                    StringBuilder sb = new StringBuilder();
                    try (var c = pg.getPostgresDatabase().getConnection()) {
                        for (String table : List.of("financial_transactions", "account_movements", "financial_accounts", "monthly_records", "score_results", "financial_obligations", "counterparties")) {
                            try (var q = c.createStatement(); var r = q.executeQuery("SELECT count(*) FROM " + table)) {
                                r.next();
                                sb.append(table).append("=").append(r.getLong(1)).append(";");
                            }
                        }
                    }
                    System.out.println("LIVE_MUTATION_" + tag + ":" + sb);
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
  writeFileSync(path.join(temporary, 'FundingLiveRuntime.java'), javaSource);

  // Spawn Spring Boot runtime with Disposable Postgres
  console.log('Starting real Spring Boot backend with Disposable Postgres on port', backendPort);
  javaRuntime = spawn(
    path.join(javaHome, 'bin', 'java.exe'),
    ['--class-path', path.join(temporary, 'classpath.jar'), path.join(temporary, 'FundingLiveRuntime.java')],
    { cwd: path.join(rootDir, 'backend'), windowsHide: true }
  );

  javaRuntime.stdout.on('data', (d) => {
    javaOutput += d.toString();
  });
  javaRuntime.stderr.on('data', (d) => {
    process.stderr.write(d.toString());
  });

  await waitFor(() => javaOutput.includes('LIVE_READY'), 'Spring Boot backend', 120000);
  console.log('Spring Boot backend is LIVE_READY!');writeFileSync(path.join(outDir,'runtime-identity.txt'),javaOutput.match(/LIVE_IDENTITY:[^\r\n]+/)?.[0]??'Identity unavailable');

  async function queryMutationCounts(tag) {
    const offset = javaOutput.length;
    javaRuntime.stdin.write(`ZERO_MUTATION_CHECK ${tag}\n`);
    await waitFor(() => javaOutput.slice(offset).includes(`LIVE_MUTATION_${tag}:`), 'Zero mutation check ' + tag);
    const match = javaOutput.slice(offset).match(new RegExp(`LIVE_MUTATION_${tag}:([^\\r\\n]+)`));
    return match[1];
  }

  // 2. Prepare isolated Next.js build output directory
  console.log('Preparing isolated Next.js runtime in tmp/isolated-frontend-funding...');
  const isolatedDir = path.join(rootDir, 'tmp', 'isolated-frontend-funding');
  const isolatedNext = path.join(isolatedDir, '.next');
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

  const publicJunction = path.join(isolatedDir, 'public');
  if (!existsSync(publicJunction)) {
    try {
      symlinkSync(path.join(frontendDir, 'public'), publicJunction, 'junction');
    } catch {}
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
    const targetPort = nextPort; // All API calls pass through the production Next rewrite to Spring.
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

  // 5. Seed synthetic user, businesses, and accounts via real HTTP API through the gateway
  console.log('Seeding authentication and business fixtures via real backend REST API...');
  const cookieJar = new Map();
  let xsrfToken = '';

  function storeCookies(headers) {
    const setCookie = headers['set-cookie'];
    if (!setCookie) return;
    const cookieArray = Array.isArray(setCookie) ? setCookie : [setCookie];
    for (const cookieStr of cookieArray) {
      const parts = cookieStr.split(';')[0].split('=');
      if (parts.length >= 2) {
        cookieJar.set(parts[0].trim(), parts.slice(1).join('=').trim());
      }
    }
  }

  function getCookieHeader() {
    return Array.from(cookieJar.entries())
      .map(([k, v]) => `${k}=${v}`)
      .join('; ');
  }

  async function apiCall(method, route, data = null, headers = {}) {
    return new Promise((resolve, reject) => {
      const payload = data ? JSON.stringify(data) : null;
      const reqHeaders = {
        ...headers,
        cookie: getCookieHeader(),
      };
      if (payload) {
        reqHeaders['Content-Type'] = 'application/json';
        reqHeaders['Content-Length'] = Buffer.byteLength(payload);
      }
      if (method !== 'GET' && xsrfToken) {
        reqHeaders['X-XSRF-TOKEN'] = xsrfToken;
      }
      const req = http.request(
        `${origin}/api${route}`,
        {
          method,
          headers: reqHeaders,
        },
        (res) => {
          storeCookies(res.headers);
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

  // Fetch CSRF
  const csrfRes = await apiCall('GET', '/auth/csrf');
  assert.equal(csrfRes.status, 200, 'CSRF endpoint must succeed');
  xsrfToken = csrfRes.json.token;

  // Register user
  const email = 'live.owner@finsight.local';
  const password = 'Synthetic-Owner-2026!';
  const regRes = await apiCall('POST', '/auth/register', {
    email,
    password,
    fullName: 'Live Funding Owner',
  });
  assert.equal(regRes.status, 201, 'Registration must succeed');

  // Login
  const loginRes = await apiCall('POST', '/auth/login', { email, password });
  assert.equal(loginRes.status, 200, 'Login must succeed');
  assert.equal(loginRes.json.authStage, 'FULLY_AUTHENTICATED');

  // Create Business A: Apex Industrial Ltd
  const bizARes = await apiCall('POST', '/businesses', {
    businessName: 'Apex Industrial Ltd',
    businessType: 'manufacturing',
    languagePreference: 'en',
    whatsappOptIn: false,
  });
  assert.equal(bizARes.status, 201, 'Business A creation must succeed');
  const businessAId = bizARes.json.businessId;

  // Activate Business A
  const actARes = await apiCall('POST', '/businesses/active', { businessId: businessAId });
  assert.equal(actARes.status, 200, 'Activate Business A must succeed');

  // Create Financial Account in Business A: CASH with opening PKR 105,000.00
  const accARes = await apiCall(
    'POST',
    '/finance/accounts',
    {
      accountName: 'Main Operating Cash',
      accountType: 'CASH',
      currency: 'PKR',
      openingBalance: '105000.00',
      openingDate: '2026-11-01',
    },
    { 'Idempotency-Key': randomUUID() }
  );
  assert.equal(accARes.status, 201, 'Account creation in Business A must succeed');

  // Create Business B: Zenith Commercial Ltd
  const bizBRes = await apiCall('POST', '/businesses', {
    businessName: 'Zenith Commercial Ltd',
    businessType: 'retail',
    languagePreference: 'en',
    whatsappOptIn: false,
  });
  assert.equal(bizBRes.status, 201, 'Business B creation must succeed');
  const businessBId = bizBRes.json.businessId;

  // Activate Business B
  const actBRes = await apiCall('POST', '/businesses/active', { businessId: businessBId });
  assert.equal(actBRes.status, 200, 'Activate Business B must succeed');

  // Create Financial Account in Business B: BANK with opening PKR 50,000.00
  const accBRes = await apiCall(
    'POST',
    '/finance/accounts',
    {
      accountName: 'Zenith Operating Bank',
      accountType: 'BANK',
      currency: 'PKR',
      openingBalance: '50000.00',
      openingDate: '2026-11-01',
    },
    { 'Idempotency-Key': randomUUID() }
  );
  assert.equal(accBRes.status, 201, 'Account creation in Business B must succeed');

  // Switch back to Business A
  await apiCall('POST', '/businesses/active', { businessId: businessAId });

  const emptyBiz=await apiCall('POST','/businesses',{businessName:'Funding no-account test',businessType:'services',languagePreference:'en',whatsappOptIn:false});
  assert.equal(emptyBiz.status,201);const emptyBusinessId=emptyBiz.json.businessId;
  await apiCall('POST','/businesses/active',{businessId:businessAId});
  // Baseline mutation counts
  const baselineMutationCounts = await queryMutationCounts('baseline');
  console.log('Baseline database row counts:', baselineMutationCounts);

  // 6. Launch Microsoft Edge / Chromium browser
  console.log('Launching browser for real HTTP acceptance testing...');
  browser = await chromium.launch({
    channel: 'msedge',
    headless: true,
  });

  const browserContext = await browser.newContext({
    viewport: { width: 1440, height: 900 },
  });

  // Inject real session and CSRF cookies into browser context
  const cookiesToInject = Array.from(cookieJar.entries()).map(([k, v]) => ({
    name: k,
    value: v,
    domain: '127.0.0.1',
    path: '/',
    httpOnly: k === 'FINSIGHT_SESSION',
    sameSite: 'Lax',
  }));
  await browserContext.addCookies(cookiesToInject);

  const page = await browserContext.newPage();acceptancePage=page;
  const browserErrors = [];
  page.on('pageerror', (e) => {
    console.log('PAGE ERROR:', e.message);
    browserErrors.push(e.message);
  });
  page.on('console', (m) => {
    if (m.type() === 'error' && /Content Security Policy|Refused to|Hydration/.test(m.text())) {
      browserErrors.push(m.text());
    }
  });

  // --- SCENARIO 1: Access /finance/funding ---
  console.log('Navigating to ' + origin + '/finance/funding...');
  await page.goto(origin + '/finance/funding');
  await page.getByRole('heading', { name: 'Funding & scenarios' }).first().waitFor({ timeout: 15000 });
  mark('Scenario 1: Access /finance/funding loads workspace with authorized header');

  // --- SCENARIO 2: MANUAL_ASSUMPTION scenario submission ---
  const startDateInput = page.getByLabel('Forecast starts');
  await startDateInput.fill('2026-11-01');

  const horizonSelect = page.getByLabel('Forecast horizon');
  await horizonSelect.selectOption('30');

  const sourceSelect = page.getByLabel('Opening liquidity source');
  await sourceSelect.selectOption('MANUAL');

  const openingInput = page.getByLabel('Assumed opening liquidity');
  await openingInput.fill('0');
  mark('Scenario 2: MANUAL_ASSUMPTION scenario configured with 2026-11-01 start and 0.00 assumed opening cash');

  // --- SCENARIO 3: Canonical PKR 60,000 funding deficit verified ---
  const baselineCard = page.locator('div', { has: page.getByRole('heading', { name: 'Baseline', exact: true }) }).last();

  // Event 1: Day 1 (2026-11-02) Outflow 60,000.00 PKR
  await baselineCard.getByRole('button', { name: 'Add planned outflow' }).click();
  const baseOutflowDate = baselineCard.getByLabel('Event date').nth(0);
  await baseOutflowDate.fill('2026-11-02');
  const baseOutflowAmt = baselineCard.getByLabel('Amount (PKR)').nth(0);
  await baseOutflowAmt.fill('60000');
  const baseOutflowDesc = baselineCard.getByLabel('Description').nth(0);
  await baseOutflowDesc.fill('Materials bulk purchase');

  // Event 2: Day 5 (2026-11-06) Inflow 20,000.00 PKR
  await baselineCard.getByRole('button', { name: 'Add planned inflow' }).click();
  const baseInflowDate1 = baselineCard.getByLabel('Event date').nth(1);
  await baseInflowDate1.fill('2026-11-06');
  const baseInflowAmt1 = baselineCard.getByLabel('Amount (PKR)').nth(1);
  await baseInflowAmt1.fill('20000');
  const baseInflowDesc1 = baselineCard.getByLabel('Description').nth(1);
  await baseInflowDesc1.fill('Customer payment installment 1');

  // Event 3: Day 10 (2026-11-11) Inflow 50,000.00 PKR
  await baselineCard.getByRole('button', { name: 'Add planned inflow' }).click();
  const baseInflowDate2 = baselineCard.getByLabel('Event date').nth(2);
  await baseInflowDate2.fill('2026-11-11');
  const baseInflowAmt2 = baselineCard.getByLabel('Amount (PKR)').nth(2);
  await baseInflowAmt2.fill('50000');
  const baseInflowDesc2 = baselineCard.getByLabel('Description').nth(2);
  await baseInflowDesc2.fill('Customer payment installment 2');

  const comparisonResponses=[];page.on('response',async r=>{if(r.url().endsWith('/api/finance/funding/scenarios/compare')&&r.request().method()==='POST'&&r.status()===200)comparisonResponses.push(await r.json());});
  // Submit Compare
  await page.getByRole('button', { name: 'Compare scenarios' }).click();
  await page.getByRole('heading', { name: 'Scenario comparison' }).waitFor({ timeout: 15000 });

  // Verify Peak Funding Requirement = 60,000.00 PKR
  const peakMatches = page.locator('dd', { hasText: '60,000.00 PKR' });
  await peakMatches.first().waitFor();
  assert.ok((await peakMatches.count()) >= 1);

  // First Deficit Date = 2026-11-02
  const firstDeficitMatches = page.locator('dd', { hasText: '2026-11-02' });
  await firstDeficitMatches.first().waitFor();
  mark('Scenario 3: Canonical PKR 60,000 funding deficit verified on 2026-11-02');

  // --- SCENARIO 4: Correct Day 10 recovery verified (2026-11-11) ---
  const recoveryMatches = page.locator('dd', { hasText: '2026-11-11' });
  await recoveryMatches.first().waitFor();
  assert.ok((await recoveryMatches.count()) >= 1);
  mark('Scenario 4: Day 10 recovery date verified on 2026-11-11');

  // --- SCENARIO 5: Closing projected cash of PKR 10,000 verified ---
  const closingMatches = page.locator('dd', { hasText: '10,000.00 PKR' });
  await closingMatches.first().waitFor();
  mark('Scenario 5: Closing projected position verified at 10,000.00 PKR');

  // --- SCENARIO 6: Baseline versus alternative comparison ---
  await page.getByRole('button', { name: 'Copy baseline to alternative' }).click();
  await page.getByText('Baseline copied. Edit the alternative to explore another outcome.').waitFor();

  await page.getByRole('button', { name: 'Compare scenarios' }).click();
  await page.getByRole('heading', { name: 'Scenario comparison' }).waitFor({ timeout: 15000 });

  const alternativeEditor=page.getByRole('form',{name:'Scenario assumptions',exact:true}).locator('div',{has:page.getByRole('heading',{name:'Alternative',exact:true})}).last();
  await alternativeEditor.getByLabel('Amount (PKR)').first().fill('30000');
  const changedResponse=page.waitForResponse(r=>r.url().endsWith('/api/finance/funding/scenarios/compare')&&r.request().method()==='POST');
  await page.getByRole('button',{name:'Compare scenarios',exact:true}).click();
  const changed=await changedResponse;assert.equal(changed.status(),200);const comparison=await changed.json();
  assert.equal(comparison.baseline.peakFundingRequirement,'60000.00');assert.equal(comparison.alternative.peakFundingRequirement,'30000.00');
  assert.equal(comparison.comparison.peakFundingDifference,'-30000.00');assert.equal(comparison.alternative.closingProjectedLiquidity,'40000.00');
  await page.getByRole('heading',{name:'Scenario comparison',exact:true}).waitFor();
  writeFileSync(path.join(outDir,'actual-c06-response.json'),JSON.stringify(comparison,null,2));
  // Verify SVG Chart renders 2 polylines (baseline + alternative)
  const svg = page.locator('svg[role="img"]').first();
  await svg.waitFor();
  const polylines = svg.locator('polyline');
  assert.equal(await polylines.count(), 2, 'Must render 2 polylines (baseline and alternative)');
  mark('Scenario 6: Baseline versus alternative comparison rendered with actual -30000.00 funding delta, 40000.00 alternative closing and SVG curves');
  await page.getByRole('button',{name:'Copy baseline to alternative',exact:true}).click();
  const replaceDialog=page.getByRole('dialog',{name:'Replace the alternative?',exact:true});await replaceDialog.waitFor();await page.keyboard.press('Escape');await replaceDialog.waitFor({state:'hidden'});
  assert.equal(await alternativeEditor.getByLabel('Amount (PKR)').first().inputValue(),'30000');
  mark('Funding keyboard: cancel replacement dialog preserves alternative edits');
  await page.getByText('Daily numerical values',{exact:true}).click();await page.getByText('-60,000.00 PKR',{exact:true}).first().waitFor();
  await page.screenshot({path:path.join(outDir,'funding-manual-comparison.png'),fullPage:true});
  await page.getByText('Daily numerical values',{exact:true}).click();


  // --- SCENARIO 7: LEDGER_CASH sourced from real posted financial accounts in PostgreSQL ---
  await sourceSelect.selectOption('LEDGER');
  await page.getByRole('button', { name: 'Compare scenarios' }).click();
  await page.getByRole('heading', { name: 'Scenario comparison' }).waitFor({ timeout: 15000 });

  // Opening liquidity displayed must be PKR 105,000.00 from PostgreSQL
  const openingMatches = page.locator('p', { hasText: '105,000.00 PKR' });
  await openingMatches.first().waitFor();
  await page.locator('strong', { hasText: 'Posted account cash' }).waitFor();
  mark('Scenario 7: LEDGER_CASH sourced from real posted financial accounts in PostgreSQL (PKR 105,000.00)');

  // --- SCENARIO 8: Correct provenance labels verified ---
  // Verify direct real Spring Boot API response provenance
  const compareProbeRes = await apiCall('POST', '/finance/funding/scenarios/compare', {
    currency: 'PKR',
    asOfDate: '2026-11-01',
    horizonDays: 30,
    openingLiquidity: { source: 'LEDGER_CASH' },
    baselineScenario: { scenarioName: 'Baseline Plan', events: [] },
  });
  assert.equal(compareProbeRes.status, 200);
  assert.equal(compareProbeRes.json.openingLiquidity.source, 'POSTED_ACTUAL');
  assert.equal(compareProbeRes.json.provenance.openingLiquiditySource, 'POSTED_ACTUAL');
  assert.equal(compareProbeRes.json.provenance.planningMode, 'HYPOTHETICAL_SIMULATION');

  const manualProbeRes = await apiCall('POST', '/finance/funding/scenarios/compare', {
    currency: 'PKR',
    asOfDate: '2026-11-01',
    horizonDays: 30,
    openingLiquidity: { source: 'MANUAL_ASSUMPTION', manualAmount: '0.00' },
    baselineScenario: { scenarioName: 'Baseline Plan', events: [] },
  });
  assert.equal(manualProbeRes.status, 200);
  assert.equal(manualProbeRes.json.openingLiquidity.source, 'MANUAL_ASSUMPTION');
  assert.equal(manualProbeRes.json.provenance.openingLiquiditySource, 'MANUAL_ASSUMPTION');
  assert.equal(manualProbeRes.json.provenance.planningMode, 'HYPOTHETICAL_SIMULATION');
  mark('Scenario 8: Provenance labels verified (POSTED_ACTUAL, MANUAL_ASSUMPTION, HYPOTHETICAL_SIMULATION)');

  // --- SCENARIO 9: CSRF and session handling ---
  // POST without CSRF token
  const noCsrfRes = await new Promise((resolve) => {
    const payload = JSON.stringify({
      currency: 'PKR',
      asOfDate: '2026-11-01',
      horizonDays: 30,
      openingLiquidity: { source: 'MANUAL_ASSUMPTION', manualAmount: '0.00' },
      baselineScenario: { scenarioName: 'Baseline', events: [] },
    });
    const req = http.request(
      `${origin}/api/finance/funding/scenarios/compare`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(payload),
          cookie: getCookieHeader(), // Has session cookie, but NO X-XSRF-TOKEN
        },
      },
      (res) => resolve(res.statusCode)
    );
    req.write(payload);
    req.end();
  });
  assert.equal(noCsrfRes, 403, 'POST without CSRF token must return HTTP 403');

  // POST without session cookie
  const noSessionRes = await new Promise((resolve) => {
    const payload = JSON.stringify({
      currency: 'PKR',
      asOfDate: '2026-11-01',
      horizonDays: 30,
      openingLiquidity: { source: 'MANUAL_ASSUMPTION', manualAmount: '0.00' },
      baselineScenario: { scenarioName: 'Baseline', events: [] },
    });
    const req = http.request(
      `${origin}/api/finance/funding/scenarios/compare`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(payload),
          'X-XSRF-TOKEN': xsrfToken,
          // NO session cookie
        },
      },
      (res) => resolve(res.statusCode)
    );
    req.write(payload);
    req.end();
  });
  assert.ok(
    [401, 403].includes(noSessionRes),
    `POST without session must return HTTP 401 or 403, got ${noSessionRes}`
  );
  mark('Scenario 9: CSRF and session handling verified (403 without valid CSRF/session)');

  // --- SCENARIO 10: Business switching and cross-business isolation ---
  // Switch to Business B
  await apiCall('POST', '/businesses/active', { businessId: businessBId });
  const bizBCompareRes = await apiCall('POST', '/finance/funding/scenarios/compare', {
    currency: 'PKR',
    asOfDate: '2026-11-01',
    horizonDays: 30,
    openingLiquidity: { source: 'LEDGER_CASH' },
    baselineScenario: { scenarioName: 'Baseline Plan', events: [] },
  });
  assert.equal(bizBCompareRes.status, 200);
  // Business B opening cash is PKR 50,000.00 (from its own account), NOT Business A's 105,000.00
  assert.equal(bizBCompareRes.json.openingLiquidity.amount, '50000.00');

  // Switch back to Business A
  await apiCall('POST', '/businesses/active', { businessId: businessAId });
  const bizAReverifyRes = await apiCall('POST', '/finance/funding/scenarios/compare', {
    currency: 'PKR',
    asOfDate: '2026-11-01',
    horizonDays: 30,
    openingLiquidity: { source: 'LEDGER_CASH' },
    baselineScenario: { scenarioName: 'Baseline Plan', events: [] },
  });
  assert.equal(bizAReverifyRes.status, 200);
  assert.equal(bizAReverifyRes.json.openingLiquidity.amount, '105000.00');
  mark('Scenario 10: Business switching and cross-business isolation verified (Business A accounts isolated from Business B)');

  // --- SCENARIO 11: English/Urdu RTL and mobile presentation ---
  // Click Urdu button
  await page.getByRole('button', { name: 'اردو' }).click();
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  await page.getByRole('heading', { name: 'فنڈنگ اور مالی منظرنامے' }).first().waitFor();
  await page.getByText('متوقع فنڈنگ کی ضرورت').first().waitFor();
  await page.getByRole('heading', { name: 'بنیادی منظرنامہ' }).first().waitFor();
  await page.getByRole('heading', { name: 'متبادل منظرنامہ' }).first().waitFor();

  // Mobile viewport 390x844
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(500);
  await page.screenshot({ path: path.join(outDir, 'funding-urdu-mobile.png'), fullPage: true });

  const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
  const innerWidth = await page.evaluate(() => window.innerWidth);
  assert.ok(
    scrollWidth <= innerWidth + 1,
    `Mobile layout must fit 390px viewport without horizontal overflow (scrollWidth=${scrollWidth}, innerWidth=${innerWidth})`
  );

  // Restore Desktop viewport and capture screenshot
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.getByRole('button', { name: 'English' }).click();
  await page.waitForFunction(() => document.documentElement.dir === 'ltr');
  await page.getByRole('heading', { name: 'Funding & scenarios' }).first().waitFor();
  await page.screenshot({ path: path.join(outDir, 'funding-desktop.png'), fullPage: true });

  assert.deepEqual(browserErrors, [], `Expected zero browser errors, got: ${JSON.stringify(browserErrors)}`);
  mark('Scenario 11: English/Urdu RTL and mobile 390x844 presentation verified with zero horizontal overflow');

  async function switchBrowserBusiness(businessId){
   const csrf=await (await browserContext.request.get(origin+'/api/auth/csrf')).json();
   const response=await browserContext.request.post(origin+'/api/businesses/active',{data:{businessId},headers:{[csrf.headerName]:csrf.token}});assert.equal(response.status(),200);
   await page.evaluate(()=>window.dispatchEvent(new Event('finsight-business-changed')));
  }
  // Real F13 dashboard evidence; no layout/preferences writes.
  await page.setViewportSize({width:1440,height:900});
  if(await page.locator('html').getAttribute('dir')==='rtl')await page.getByRole('button',{name:'English',exact:true}).click();
  const requests=[];page.on('request',r=>{if(new URL(r.url()).pathname.startsWith('/api/'))requests.push({method:r.method(),path:new URL(r.url()).pathname});});
  await page.goto(origin+'/');
  const finance=page.getByTestId('dashboard-live-finance');await finance.waitFor();
  await finance.getByRole('heading',{name:'Cash position by currency',exact:true}).waitFor();
  await finance.getByText('105,000.00 PKR',{exact:true}).first().waitFor();
  const cash=await apiCall('GET','/finance/query/cash-position');
  assert.equal(cash.status,200);assert.equal(cash.json.data.currencySummaries.find(c=>c.currency==='PKR').currentBalance,'105000.00');
  const forecast=await apiCall('GET','/finance/forecast/daily?currency=PKR&horizonDays=30');assert.equal(forecast.status,200);
  const metric=forecast.json.summary.maximumExpectedShortfall;await finance.getByText(metric.replace(/\B(?=(\d{3})+(?!\d))/g,',')+' PKR',{exact:true}).first().waitFor();
  await page.screenshot({path:path.join(outDir,'dashboard-desktop-en.png'),fullPage:true});
  assert.equal(requests.some(r=>r.path==='/api/workspace/preferences'&&r.method!=='GET'),false);
  mark('Dashboard: F13 financial-summary renders posted cash, forecast funding and source labels without changing saved preferences');
  await page.getByRole('button',{name:'اردو',exact:true}).click();await page.waitForFunction(()=>document.documentElement.dir==='rtl');await page.getByRole('heading',{name:'تازہ مالی جائزہ',exact:true}).waitFor();
  await page.setViewportSize({width:390,height:844});await page.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth);await page.screenshot({path:path.join(outDir,'dashboard-mobile-ur.png'),fullPage:true});
  mark('Dashboard: Urdu RTL and 390x844 layout without horizontal overflow');
  await page.setViewportSize({width:1440,height:900});
  await page.getByRole('button',{name:'English',exact:true}).click();
  // Switch the real authenticated context, notify the established provider and verify new cash replaces old cash.
  await switchBrowserBusiness(businessBId);
  await page.getByTestId('dashboard-live-finance').getByText('50,000.00 PKR',{exact:true}).first().waitFor();
  assert.equal(await page.getByTestId('dashboard-live-finance').getByText('105,000.00 PKR',{exact:true}).count(),0);
  await switchBrowserBusiness(businessAId);
  await page.getByTestId('dashboard-live-finance').getByText('105,000.00 PKR',{exact:true}).first().waitFor();
  mark('Dashboard: real business transition discards old financial evidence');
  writeFileSync(path.join(outDir,'dashboard-api-evidence.json'),JSON.stringify({cash:cash.json,forecast:forecast.json.summary,requests,financialMocks:false},null,2));

  // True backend validation and missing-evidence states; no fabricated responses.
  const invalidRequest={currency:'USD',asOfDate:'2026-11-01',horizonDays:30,openingLiquidity:{source:'MANUAL_ASSUMPTION',manualAmount:'0.00'},baselineScenario:{scenarioName:'Baseline',events:[]}};
  assert.equal((await apiCall('POST','/finance/funding/scenarios/compare',invalidRequest)).status,400);
  assert.equal((await apiCall('POST','/finance/funding/scenarios/compare',{...invalidRequest,currency:'PKR',horizonDays:91})).status,400);
  await switchBrowserBusiness(emptyBusinessId);
  await page.goto(origin+'/finance/funding');
  const missingResponse=page.waitForResponse(r=>r.url().endsWith('/api/finance/funding/scenarios/compare')&&r.request().method()==='POST');
  await page.getByRole('button',{name:'Compare scenarios',exact:true}).click();assert.equal((await missingResponse).status(),400);
  await page.getByText('Posted cash evidence is unavailable. Review your accounts or choose an explicit user assumption.',{exact:true}).waitFor();
  assert.equal(await page.getByRole('heading',{name:'Scenario comparison',exact:true}).count(),0);
  await switchBrowserBusiness(businessAId);
  mark('Real error states: unsupported currency/horizon 400; unavailable ledger evidence is visible and never zero');
  // Isolated account-free manual case genuinely never recovers; not a fabricated result fixture.
  await page.goto(origin+'/finance/funding');
  await page.getByLabel('Opening liquidity source',{exact:true}).selectOption('MANUAL');await page.getByLabel('Assumed opening liquidity',{exact:true}).fill('0');
  const noRecoveryEditor=page.getByRole('form',{name:'Scenario assumptions',exact:true}).locator('div',{has:page.getByRole('heading',{name:'Baseline',exact:true})}).last();
  await noRecoveryEditor.getByRole('button',{name:'Add planned outflow',exact:true}).click();await noRecoveryEditor.getByLabel('Amount (PKR)',{exact:true}).fill('100');await noRecoveryEditor.getByLabel('Description',{exact:true}).fill('No recovery test');
  const nonRecovering=page.waitForResponse(r=>r.url().endsWith('/api/finance/funding/scenarios/compare')&&r.request().method()==='POST');await page.getByRole('button',{name:'Compare scenarios',exact:true}).click();
  const deficitResponse=await nonRecovering;assert.equal(deficitResponse.status(),200);assert.equal((await deficitResponse.json()).baseline.recoveryDate,null);await page.getByText('No recovery within this horizon',{exact:true}).waitFor();
  mark('Funding real no-recovery state: 100.00 payment and no receipts remains a projected deficit');


  // --- SCENARIO 12: Zero financial database mutations ---
  const closureMutationCounts = await queryMutationCounts('closure');
  console.log('Closure database row counts:', closureMutationCounts);
  assert.equal(
    closureMutationCounts,
    baselineMutationCounts,
    'Database counts of financial tables before and after simulation must be identical'
  );
  mark('Scenario 12: Zero financial database mutations verified before and after all scenario runs');

  assert.deepEqual(browserErrors,[], 'No browser exceptions or CSP failures after dashboard/error journeys');
  writeFileSync(
    path.join(outDir, 'results.json'),
    JSON.stringify({ checks, comparisonResponses, errors: browserErrors, status: 'SUCCESS' }, null, 2)
  );

  console.log('\n===============================================================');
  console.log('--- ALL '+checks.length+' REAL FULL-STACK CHECKS PASSED ---');
  console.log('===============================================================\n');
} catch(error) {
  const failure={status:'FAILED',checks,error:error.message,mockedFinancialAPIs:false};
  writeFileSync(path.join(outDir,'failure.json'),JSON.stringify(failure,null,2));
  writeFileSync(path.join(outDir,'results.json'),JSON.stringify(failure,null,2));
  if(acceptancePage)await acceptancePage.screenshot({path:path.join(outDir,'failure.png'),fullPage:true}).catch(()=>{});
  throw error;
} finally {
  console.log('Cleaning up processes and resources...');
  if (browser) {
    try {
      await browser.close();
    } catch {}
  }
  if (gatewayServer) {
    try {
      gatewayServer.closeAllConnections?.();
      await new Promise((r) => gatewayServer.close(r));
    } catch {}
  }
  if (nextProc && !exited(nextProc)) {
    try {
      nextProc.kill('SIGTERM');
    } catch {}
  }
  if (javaRuntime && !exited(javaRuntime)) {
    try {
      javaRuntime.stdin.write('STOP\n');
      await waitFor(() => exited(javaRuntime), 'Java shutdown', 10000);
    } catch {
      javaRuntime.kill('SIGKILL');
    }
  }
}
