// Real Spring/PostgreSQL acceptance. No synthetic HTTP API and no silent skip.
// Requires current backend target/classes/test-classes + Surefire classpath evidence,
// installed JDK, pnpm, Playwright/Edge, and a live Next build targeting port 8180.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';
import { readFileSync, writeFileSync, mkdtempSync, mkdirSync, rmSync, statSync } from 'node:fs';
import path from 'node:path';
import { tmpdir } from 'node:os';
import { pathToFileURL, fileURLToPath } from 'node:url';
import net from 'node:net';
import { randomBytes, randomUUID } from 'node:crypto';

const require = createRequire(import.meta.url);
const frontend = path.resolve(fileURLToPath(new URL('..', import.meta.url)));
const root = path.dirname(frontend);
const out = path.join(frontend, 'test-artifacts', 'accounts-live');
mkdirSync(out, { recursive: true });
const checks = [], blockers = [];
const mark = label => { checks.push(label); console.log('PASS ' + label); };
const xml = readFileSync(path.join(root, 'backend/target/surefire-reports/TEST-com.app.sme_health_backend.accounting.FinancialAccountPostgreSqlIT.xml'), 'utf8');
const property = name => {
  const escaped = name.replaceAll('.', '\\.');
  const match = xml.match(new RegExp('<property name="' + escaped + '" value="([^"]*)"'));
  if (!match) throw new Error('Required compiled backend classpath evidence is unavailable.');
  return match[1].replaceAll('&amp;', '&').replaceAll('&quot;', '"');
};
const javaHome = property('java.home');
const classpath = property('surefire.test.class.path');
const playwrightPath = process.env.PLAYWRIGHT_MODULE;
const { chromium, request } = require(playwrightPath || 'playwright');
const temporary = mkdtempSync(path.join(tmpdir(), 'finsight-accounts-live-'));
const origin = 'http://127.0.0.1:3180';
const demoOnly = process.argv.includes('--demo');
let runtime, next, browser;
const apiContexts = [];
const exited = child => child.exitCode !== null || child.signalCode !== null;
let runtimeText = '', nextText = '';
const ids = {
  pkr: 'ffffffff-ffff-4fff-8fff-ffffffffffff', jpy: '11111111-1111-4111-8111-111111111111',
  bhd: '22222222-2222-4222-8222-222222222222', future: '33333333-3333-4333-8333-333333333333',
  other: '44444444-4444-4444-8444-444444444444', unavailable: '55555555-5555-4555-8555-555555555555',
  corrupt: '66666666-6666-4666-8666-666666666666',
  metadata: '77777777-7777-4777-8777-777777777777',
};
const javaSource = String.raw`
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import com.app.sme_health_backend.SmeHealthBackendApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.nio.file.*;
public class AccountsLiveRuntime {
  static void insert(Connection c, String id, String business, String name, String type, String currency, int scale, String amount, LocalDate date, String status, Instant created) throws Exception {
    try(var s=c.prepareStatement("INSERT INTO financial_accounts (id,business_id,account_name,account_type,currency,currency_decimals,opening_balance,opening_date,status,institution_name,account_identifier,notes,created_at,updated_at) VALUES (?::uuid,?::uuid,?,?,?,?,?, ?,?,?,?, ?,?,?)")) {
      s.setString(1,id); s.setString(2,business); s.setString(3,name); s.setString(4,type); s.setString(5,currency); s.setInt(6,scale); s.setBigDecimal(7,new java.math.BigDecimal(amount)); s.setObject(8,date); s.setString(9,status);
      s.setString(10,type.equals("CASH")?null:"Synthetic institution"); s.setString(11,type.equals("CASH")?null:"SYNTHETIC-1234567890"); s.setString(12,"Synthetic integration fixture only"); s.setTimestamp(13,Timestamp.from(created)); s.setTimestamp(14,Timestamp.from(created)); s.executeUpdate();
    }
  }
  public static void main(String[] args) throws Exception {
    ConfigurableApplicationContext app=null;
    var pg=DisposablePostgres.POSTGRES;
    try {
      String key=Base64.getEncoder().encodeToString(java.security.SecureRandom.getSeed(32));
      app=SpringApplication.run(SmeHealthBackendApplication.class,
        "--server.port=8180", "--server.address=127.0.0.1",
        "--spring.datasource.url="+DisposablePostgres.URL,
        "--spring.datasource.username=finsight_app", "--spring.datasource.password="+DisposablePostgres.PASSWORD,
        "--spring.flyway.enabled=false", "--app.documents.storage-dir="+DisposablePostgres.DOCUMENTS,
        "--app.whatsapp.scheduler.enabled=false", "--finsight.crypto.keys.k1="+key,
        "--app.security.cors.allowed-origins=http://127.0.0.1:3180", "--logging.level.root=ERROR");
      try(var c=pg.getPostgresDatabase().getConnection(); var s=c.createStatement(); var r=s.executeQuery("SELECT version(), (SELECT max(version::int) FROM flyway_schema_history WHERE success)")) { r.next(); System.out.println("LIVE_ENV:"+r.getString(1).split(",")[0]+";schema="+r.getInt(2)); }
      System.out.println("LIVE_READY"); System.out.flush();
      var input=new Scanner(System.in);
      while(input.hasNextLine()) {
        String[] parts=input.nextLine().split(" ");
        if(parts[0].equals("STOP")) break;
        if(parts[0].equals("STOP_SPRING")) { app.close(); app=null; System.out.println("LIVE_STOPPED_SPRING"); System.out.flush(); continue; }
        try(var c=pg.getPostgresDatabase().getConnection()) {
          if(parts[0].equals("SEED")) {
            for(int i=1;i<6;i++) UUID.fromString(parts[i]);
            String a=parts[1], b=parts[2]; LocalDate today=LocalDate.now(ZoneId.of("Asia/Karachi")); Instant base=Instant.now().minusSeconds(120).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            insert(c,"ffffffff-ffff-4fff-8fff-ffffffffffff",a,"Live PKR Bank","BANK","PKR",2,"-2500.50",today.minusDays(1),"ACTIVE",base);
            insert(c,"11111111-1111-4111-8111-111111111111",a,"Live JPY Wallet","WALLET","JPY",0,"999999999999",today.minusDays(1),"ACTIVE",base.plusMillis(100));
            insert(c,"22222222-2222-4222-8222-222222222222",a,"Live BHD Bank","BANK","BHD",3,"1250.375",today.minusDays(1),"ACTIVE",base.plusSeconds(2));
            insert(c,"33333333-3333-4333-8333-333333333333",a,"Live Future Opening","CASH","PKR",2,"500.00",today.plusDays(1),"ACTIVE",base.plusSeconds(3));
            insert(c,"44444444-4444-4444-8444-444444444444",b,"Other Business Bank","BANK","PKR",2,"321.00",today.minusDays(1),"ACTIVE",base);
            insert(c,"55555555-5555-4555-8555-555555555555",a,"Live Unavailable Currency","BANK","XYZ",2,"100.00",today.minusDays(1),"ARCHIVED",base.plusSeconds(4));
            insert(c,"66666666-6666-4666-8666-666666666666",a,"Live Invalid Precision","BANK","PKR",2,"1.0001",today.minusDays(1),"INACTIVE",base.plusSeconds(5));
            insert(c,"77777777-7777-4777-8777-777777777777",a,"Live Invalid Metadata","BANK","PKR",3,"100.00",today.minusDays(1),"INACTIVE",base.plusSeconds(6));
            for(int i=0;i<21;i++) insert(c,UUID.randomUUID().toString(),a,"Live Filler "+i,"CASH","PKR",2,"10.00",today.minusDays(1),"ACTIVE",base.plusSeconds(10+i));
            for(int i=3;i<6;i++) try(var s=c.prepareStatement("INSERT INTO business_memberships (user_id,business_id,role,status) VALUES (?::uuid,?::uuid,?,'ACTIVE')")) { s.setString(1,parts[i]); s.setString(2,a); s.setString(3,i==3?"VIEWER":i==4?"MANAGER":"ACCOUNTANT"); s.executeUpdate(); }
            insert(c,"88888888-8888-4888-8888-888888888888",a,"Live Valid Inactive","BANK","PKR",2,"12.50",today.minusDays(1),"INACTIVE",base.plusSeconds(7));
            System.out.println("LIVE_SEEDED");
          } else if(parts[0].equals("TRIM")) { try(var s=c.createStatement()) { s.executeUpdate("DELETE FROM financial_accounts WHERE account_name LIKE 'Live Filler %'"); } System.out.println("LIVE_TRIMMED"); }
          else if(parts[0].equals("ROLE")) {
            UUID.fromString(parts[1]); UUID.fromString(parts[2]); if(!Set.of("ACCOUNTANT","VIEWER").contains(parts[3])) throw new IllegalArgumentException("Fixture role");
            try(var q=c.prepareStatement("UPDATE business_memberships SET role=? WHERE user_id=?::uuid AND business_id=?::uuid")) { q.setString(1,parts[3]); q.setString(2,parts[1]); q.setString(3,parts[2]); if(q.executeUpdate()!=1) throw new IllegalStateException("Fixture membership missing"); }
            System.out.println("LIVE_ROLE_CHANGED");
          } else if(parts[0].equals("SNAPSHOT")) {
            StringBuilder snapshot=new StringBuilder();
            for(String table:List.of("monthly_records","score_results","insights","recommendations")) {
              try(var q=c.createStatement(); var r=q.executeQuery("SELECT count(*),md5(coalesce(string_agg(row_to_json(t)::text,'|' ORDER BY id),'')) FROM "+table+" t")) { r.next(); snapshot.append(table).append(":").append(r.getLong(1)).append(":").append(r.getString(2)).append(";"); }
            }
            System.out.println("LIVE_SNAPSHOT_"+parts[1]+":"+snapshot);
          }
          else if(parts[0].equals("EXPIRE")) { try(var s=c.createStatement()) { s.executeUpdate("DELETE FROM spring_session"); } System.out.println("LIVE_EXPIRED"); }
        }
        System.out.flush();
      }
    } finally { if(app!=null) app.close(); pg.close(); }
  }
}`;
async function waitFor(predicate, label, timeout = 60000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) { if (predicate()) return; await new Promise(resolve => setTimeout(resolve, 200)); }
  throw new Error(label + ' did not become ready.');
}
async function free(port) {
  const server = net.createServer();
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(port, '127.0.0.1', resolve); });
  await new Promise(resolve => server.close(resolve));
}
async function command(executable, args, options = {}) {
  const child = spawn(executable, args, { cwd: frontend, windowsHide: true, ...options });
  let output = ''; child.stdout?.on('data', data => output += data); child.stderr?.on('data', data => output += data);
  await new Promise((resolve, reject) => { child.once('error', reject); child.once('exit', code => code === 0 ? resolve() : reject(new Error('Task command failed: ' + path.basename(executable) + ' (exit ' + code + ')'))); });
  return output;
}
async function stopNext() {
  if (!next || exited(next)) return;
  next.kill(); await waitFor(() => exited(next), 'Task-owned Next shutdown', 15000);
}
async function startNext() {
  await free(3180);
  nextText = '';
  next = spawn(process.execPath, [path.join(frontend, 'node_modules/next/dist/bin/next'), 'start', '--hostname', '127.0.0.1', '--port', '3180'], { cwd: frontend, windowsHide: true });
  next.stdout.on('data', data => nextText += data); next.stderr.on('data', data => nextText += data);
  await waitFor(() => /Ready in/.test(nextText), 'Task-owned Next');
}
async function control(action, marker) { const offset = runtimeText.length; runtime.stdin.write(action + '\n'); await waitFor(() => runtimeText.slice(offset).includes(marker), marker); }
async function api(context, route, method = 'GET', data, extraHeaders = {}) {
  const headers = { ...extraHeaders };
  if (method !== 'GET') {
    const csrf = await context.get(origin + '/api/auth/csrf'); assert.equal(csrf.status(), 200);
    const token = await csrf.json(); headers[token.headerName] = token.token;
  }
  const response = await context.fetch(origin + '/api' + route, { method, data, headers });
  return response;
}
const isAccountPost = req => req.method() === 'POST' && new URL(req.url()).pathname === '/api/finance/accounts';
const accountPost = (context, payload, key = randomUUID()) => api(context, '/finance/accounts', 'POST', payload, { 'Idempotency-Key': key });
async function accountsNamed(context, name) {
  const response = await api(context, '/finance/accounts?size=100'); assert.equal(response.status(), 200);
  return (await response.json()).items.filter(account => account.accountName === name);
}
async function fillCreate(page, payload, urdu = false) {
  await page.getByRole('button', { name: urdu ? 'اکاؤنٹ شامل کریں' : 'Add account', exact: true }).click();
  const form = page.getByRole('form', { name: urdu ? 'اکاؤنٹ شامل کریں' : 'Add account', exact: true });
  const nameInput = form.getByLabel(urdu ? /^اکاؤنٹ کا نام/ : /^Account name/);
  await page.waitForFunction(id => document.getElementById(id) === document.activeElement, await nameInput.getAttribute('id'));
  await page.keyboard.press('Tab');
  assert.equal(await form.getByLabel(urdu ? /^اکاؤنٹ کی قسم/ : /^Account type/).evaluate(element => element === document.activeElement), true);
  await form.getByLabel(urdu ? /^اکاؤنٹ کا نام/ : /^Account name/).fill(payload.accountName);
  await form.getByLabel(urdu ? /^اکاؤنٹ کی قسم/ : /^Account type/).selectOption(payload.accountType);
  await form.getByLabel(urdu ? /^کرنسی/ : /^Currency/).selectOption(payload.currency);
  await form.getByLabel(urdu ? /^ابتدائی بیلنس/ : /^Opening balance/).first().fill(payload.openingBalance);
  await form.getByLabel(urdu ? /^ابتدائی بیلنس کی مؤثر تاریخ/ : /^Opening effective date/).fill(payload.openingDate);
  return form;
}
async function submitCreate(form) { await form.getByRole('button', { name: /^(Create account|اکاؤنٹ بنائیں)$/ }).click(); }
const password = randomBytes(24).toString('base64url');
async function identity(email) {
  const context = await request.newContext();
  apiContexts.push(context);
  const registered = await api(context, '/auth/register', 'POST', { email, password, fullName: 'Synthetic integration user' }); assert.equal(registered.status(), 201, 'real POST /api/auth/register must succeed');
  const login = await api(context, '/auth/login', 'POST', { email, password }); assert.equal(login.status(), 200, 'real POST /api/auth/login must succeed');
  const user = await login.json(); assert.equal(user.authStage, 'FULLY_AUTHENTICATED');
  return { context, user, email };
}
async function signin(page, email) {
  await page.goto(origin + '/finance/accounts');
  await page.getByRole('heading', { name: 'Sign in to FinSight' }).waitFor();
  await page.getByLabel(/^Email/).fill(email); await page.getByLabel(/^Password/).fill(password);
  await page.getByRole('button', { name: 'Continue', exact: true }).click();
  await page.waitForFunction(() => document.body.textContent.includes('Live PKR Bank') || document.body.textContent.includes('Live Accounts A'));
  const choose = page.getByRole('button', { name: /Live Accounts A/ });
  if (await choose.count()) { await choose.click(); await page.goto(origin + '/finance/accounts'); }
  await page.getByText('Live PKR Bank', { exact: true }).waitFor();
}
try {
  if (demoOnly) {
    await startNext();
    browser = await chromium.launch({ channel: 'msedge', headless: true });
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
    const page = await context.newPage(); const privateRequests = [];
    page.on('request', request => { if (new URL(request.url()).pathname.startsWith('/api/')) privateRequests.push(request.url()); });
    await page.goto(origin + '/finance/accounts');
    await page.getByText('Accounts preview is not available in demo mode.', { exact: true }).waitFor();
    await page.getByRole('button', { name: 'Switch interface language' }).click();
    await page.getByText('ڈیمو موڈ میں اکاؤنٹس کا پیش منظر دستیاب نہیں ہے۔', { exact: true }).waitFor();
    assert.equal(await page.locator('html').getAttribute('dir'), 'rtl');
    assert.deepEqual(privateRequests, []);
    await page.screenshot({ path: path.join(out, 'demo-urdu-mobile.png'), fullPage: true });
    mark('demo browser English/Urdu preview unavailable; zero private API requests');
    await context.close();
  } else {
  await free(8180); await free(3180);
  const manifestValue = classpath.split(';').filter(Boolean).map(entry => pathToFileURL(entry).href + (statSync(entry).isDirectory() ? '/' : '')).join(' ');
  const lines = ('Class-Path: ' + manifestValue).match(/.{1,70}/g);
  writeFileSync(path.join(temporary, 'MANIFEST.MF'), 'Manifest-Version: 1.0\r\n' + lines.map((line, index) => (index ? ' ' : '') + line).join('\r\n') + '\r\n\r\n');
  await command(path.join(javaHome, 'bin/jar.exe'), ['cfm', path.join(temporary, 'classpath.jar'), path.join(temporary, 'MANIFEST.MF')]);
  writeFileSync(path.join(temporary, 'AccountsLiveRuntime.java'), javaSource);
  runtime = spawn(path.join(javaHome, 'bin/java.exe'), ['--class-path', path.join(temporary, 'classpath.jar'), path.join(temporary, 'AccountsLiveRuntime.java')], { cwd: path.join(root, 'backend'), windowsHide: true });
  runtime.stdout.on('data', data => runtimeText += data); runtime.stderr.on('data', data => runtimeText += data);
  await waitFor(() => runtimeText.includes('LIVE_READY'), 'Disposable Spring/PostgreSQL runtime', 60000);
  assert.match(runtimeText, /schema=19/);
  mark('real disposable PostgreSQL migrations through V19 and Spring on reserved port 8180');
  await startNext();
  const owner = await identity('owner-accounts@example.test');
  const create = async name => { const response = await api(owner.context, '/businesses', 'POST', { businessName: name, businessType: 'retail', languagePreference: 'en', whatsappOptIn: false }); assert.equal(response.status(), 201, 'real POST /api/businesses must succeed'); return (await response.json()).businessId; };
  const businessA = await create('Live Accounts A'), businessB = await create('Live Accounts B');
  const viewer = await identity('viewer-accounts@example.test'), manager = await identity('manager-accounts@example.test'), accountant = await identity('accountant-accounts@example.test');
  await control('SEED ' + businessA + ' ' + businessB + ' ' + viewer.user.id + ' ' + manager.user.id + ' ' + accountant.user.id, 'LIVE_SEEDED');
  await api(owner.context, '/businesses/active', 'POST', { businessId: businessA });
  for (const member of [viewer, manager, accountant]) await api(member.context, '/businesses/active', 'POST', { businessId: businessA });
  const liveList = await api(owner.context, '/finance/accounts'); assert.equal(liveList.status(), 200);
  const liveData = await liveList.json(); assert.equal(liveData.totalElements, 25);
  assert.equal(liveData.items.find(a => a.id === ids.future).currentBalance, '0.00');
  assert.equal(liveData.items.find(a => a.id === ids.pkr).currentBalance, '-2500.50');
  const badPrecision = await api(owner.context, '/finance/accounts/' + ids.corrupt);
  assert.equal(badPrecision.status(), 200); const precisionData = await badPrecision.json();
  assert.equal(precisionData.balanceStatus, 'UNAVAILABLE'); assert.equal(precisionData.balanceUnavailableReason, 'PRECISION_OVERFLOW');
  assert.equal(precisionData.openingBalance, null); assert.equal(precisionData.currentBalance, null);
  const badMetadata = await api(owner.context, '/finance/accounts/' + ids.metadata);
  assert.equal(badMetadata.status(), 200); const metadataData = await badMetadata.json();
  assert.equal(metadataData.currency, 'PKR'); assert.equal(metadataData.currencyDecimals, 3); assert.equal(metadataData.balanceStatus, 'UNAVAILABLE');
  assert.equal(metadataData.balanceUnavailableReason, 'UNSUPPORTED_CURRENCY'); assert.equal(metadataData.openingBalance, null); assert.equal(metadataData.currentBalance, null);
  mark('v1.3 original defects fixed: stored PKR scale 3 and opening precision overflow return null balances with exact reasons');
  const cross = await api(owner.context, '/finance/accounts/' + ids.other); assert.equal(cross.status(), 404); assert.equal((await cross.json()).message, 'Financial account not found');
  for (const member of [viewer, manager]) { const response = await api(member.context, '/finance/accounts'); assert.equal(response.status(), 200); }
  const outside = await api(owner.context, '/finance/accounts?page=5&size=20'); assert.equal(outside.status(), 200); assert.equal((await outside.json()).items.length, 0);
  const invalid = await api(owner.context, '/finance/accounts?page=abc'); assert.equal(invalid.status(), 400); assert.equal((await invalid.json()).error, 'Validation Failed');
  mark('real cookies/CSRF, opening-only values, Viewer/Manager read grants and cross-business detail 404');
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage(); const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await signin(page, owner.email);
  await page.getByText('PKR - 2,500.50', { exact: true }).waitFor();
  await page.getByText('JPY 999,999,999,999', { exact: true }).waitFor();
  await page.getByText('BHD 1,250.375', { exact: true }).waitFor();
  await page.getByRole('button', { name: 'View account details: Live Future Opening', exact: true }).click();
  await page.getByRole('dialog').getByText('PKR 500.00', { exact: true }).waitFor();
  await page.getByRole('dialog').getByText('PKR 0.00', { exact: true }).waitFor();
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'View account details: Live PKR Bank', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Account details' }); await dialog.waitFor();
  await dialog.getByText('****7890', { exact: true }).waitFor();
  await dialog.getByText('Opening effective date', { exact: true }).waitFor();
  assert.equal(await page.evaluate(() => document.querySelector('[role=dialog]').contains(document.activeElement)), true);
  await page.keyboard.press('Shift+Tab'); assert.equal(await page.evaluate(() => document.querySelector('[role=dialog]').contains(document.activeElement)), true);
  await page.keyboard.press('Escape'); await dialog.waitFor({ state: 'detached' });
  assert.equal(await page.getByRole('button', { name: 'View account details: Live PKR Bank', exact: true }).evaluate(element => element === document.activeElement), true);
  await page.screenshot({ path: path.join(out, 'desktop.png'), fullPage: true });
  mark('desktop exact PKR/JPY/BHD, actual detail response, masked identifier and Sheet keyboard focus');
  await page.getByLabel('Accounts per page', { exact: true }).selectOption('10');
  await page.getByRole('button', { name: 'Next page', exact: true }).click(); await page.getByText('Live Filler 6', { exact: true }).waitFor();
  await page.getByLabel('Currency', { exact: true }).selectOption('JPY'); await page.getByText('Live JPY Wallet', { exact: true }).waitFor();
  assert.equal(await page.getByRole('button', { name: 'Previous page', exact: true }).isDisabled(), true);
  await page.getByLabel('Account type', { exact: true }).selectOption('CASH'); await page.getByText('No accounts match these filters.', { exact: true }).waitFor();
  await page.getByLabel('Account type', { exact: true }).selectOption(''); await page.getByLabel('Currency', { exact: true }).selectOption('');
  await page.getByRole('button', { name: 'Next page', exact: true }).click(); await page.getByText('Live Filler 6', { exact: true }).waitFor();
  const pageTwo = /\/api\/finance\/accounts\?.*page=2(?:&|$)/;
  await page.route(pageTwo, async route => {
    await control('TRIM', 'LIVE_TRIMMED');
    const response = await route.fetch(); await route.fulfill({ response });
  });
  await page.getByRole('button', { name: 'Next page', exact: true }).click();
  await page.getByText('This page has no accounts. Return to the first page.', { exact: true }).waitFor();
  await page.unroute(pageTwo);
  await page.getByRole('button', { name: 'First page', exact: true }).click(); await page.getByText('Live PKR Bank', { exact: true }).waitFor();
  await page.getByRole('button', { name: 'اردو', exact: true }).click();
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  await page.getByText('مالی اکاؤنٹس', { exact: true }).first().waitFor();
  await page.getByText('Live PKR Bank', { exact: true }).waitFor();
  assert.equal(await page.locator('html').getAttribute('dir'), 'rtl');
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(350); // Existing AppShell margin transition is 200ms.
  await page.screenshot({ path: path.join(out, 'urdu-mobile.png'), fullPage: true });
  const bounds = await page.evaluate(() => ({ viewport: window.innerWidth, width: document.documentElement.scrollWidth,
    offenders: Array.from(document.querySelectorAll('body *')).filter(element => { const box = element.getBoundingClientRect(); return box.width > window.innerWidth || box.left < -1 || box.right > window.innerWidth + 1; }).slice(0, 8).map(element => ({ tag: element.tagName, classes: element.className })) }));
  if (bounds.width > bounds.viewport) console.error('MOBILE_BOUNDS ' + JSON.stringify(bounds));
  assert.equal(bounds.width <= bounds.viewport, true, 'mobile document must not overflow horizontally');
  await page.getByRole('button', { name: 'اکاؤنٹ کی تفصیل دیکھیں: Live BHD Bank', exact: true }).click();
  await page.getByRole('dialog').getByText('Live BHD Bank', { exact: true }).waitFor();
  await page.screenshot({ path: path.join(out, 'urdu-mobile-sheet.png'), fullPage: true });
  assert.equal(await page.evaluate(() => document.querySelector('[role=dialog]').contains(document.activeElement)), true);
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'English', exact: true }).click(); await page.getByText('Financial accounts', { exact: true }).first().waitFor();
  mark('real filters/page reset/empty/out-of-range response; Urdu RTL and mobile page bounds');
  await page.getByLabel('Account status', { exact: true }).selectOption('ARCHIVED');
  await page.getByText('Currency metadata is unsupported.', { exact: true }).waitFor();
  await page.getByRole('button', { name: 'View account details: Live Unavailable Currency', exact: true }).click();
  await page.getByRole('dialog').getByText('Unavailable', { exact: true }).first().waitFor(); await page.keyboard.press('Escape');
  mark('genuine persisted unsupported-currency UNAVAILABLE response, no guessed opening scale');
  await page.getByLabel('Account status', { exact: true }).selectOption('INACTIVE');
  await page.getByText('Live Valid Inactive', { exact: true }).waitFor();
  await page.getByText('PKR 12.50', { exact: true }).waitFor();
  for (const name of ['Live Invalid Precision', 'Live Invalid Metadata']) {
    await page.getByRole('button', { name: 'View account details: ' + name, exact: true }).click();
    const unavailableDialog = page.getByRole('dialog');
    await unavailableDialog.getByText(name, { exact: true }).waitFor();
    assert.equal(await unavailableDialog.getByText('Unavailable', { exact: true }).count() >= 2, true, 'opening and current unavailable, never zero');
    await page.keyboard.press('Escape');
  }
  mark('v1.3 mixed valid/unavailable persisted list and both null-opening detail Sheets');
  await page.getByLabel('Account status', { exact: true }).selectOption('ACTIVE'); await page.getByText('Live PKR Bank', { exact: true }).waitFor();
  const memberContext = await browser.newContext(); const memberPage = await memberContext.newPage(); await signin(memberPage, viewer.email); assert.equal(await memberPage.getByRole('button', { name: 'Add account', exact: true }).count(), 0); await memberContext.close();
  const managerContext = await browser.newContext(); const managerPage = await managerContext.newPage(); await signin(managerPage, manager.email); assert.equal(await managerPage.getByRole('button', { name: 'Add account', exact: true }).count(), 0); await managerContext.close();
  mark('Viewer and Manager real browser sign-in/read journeys');
  // Delay an actual Spring response to exercise close cancellation. No invented API data.
  let delayed = false;
  await page.route('**/api/finance/accounts/' + ids.pkr, async route => {
    const response = await route.fetch(); delayed = true; await new Promise(resolve => setTimeout(resolve, 750));
    try { await route.fulfill({ response }); } catch { /* caller legitimately canceled */ }
  });
  await page.getByRole('button', { name: 'View account details: Live PKR Bank', exact: true }).click();
  await waitFor(() => delayed, 'Real delayed detail response'); await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'View account details: Live BHD Bank', exact: true }).click();
  await page.getByRole('dialog').getByText('Live BHD Bank', { exact: true }).waitFor();
  await new Promise(resolve => setTimeout(resolve, 900)); assert.equal(await page.getByRole('dialog').getByText('Live PKR Bank', { exact: true }).count(), 0);
  await page.keyboard.press('Escape'); await page.unroute('**/api/finance/accounts/' + ids.pkr);
  mark('closing/changing detail discards a delayed response fetched from actual Spring');
  // Creation HTTP checks use actual cookie sessions and Spring responses, not browser mocks.
  const cutoff = liveData.items[0].effectiveCutoffDate;
  const tomorrow = new Date(Date.parse(cutoff + 'T00:00:00Z') + 86400000).toISOString().slice(0, 10);
  const payload = (accountName, patch = {}) => ({ accountName, accountType: 'BANK', currency: 'PKR', openingBalance: '-2500.50', openingDate: cutoff, ...patch });
  const monthly = { month: cutoff.slice(0, 7), cashInflow: 20000, cashOutflow: 10000, revenue: 20000, operatingExpenses: 5000, cashBalanceEom: 10000, cogs: 4000, financingType: 'none' };
  assert.equal((await api(owner.context, '/records/monthly', 'POST', monthly)).status(), 201);
  assert.equal((await api(owner.context, '/scores/calculate', 'POST', { month: monthly.month })).status(), 200);
  await control('SNAPSHOT BEFORE', 'LIVE_SNAPSHOT_BEFORE:');
  const beforeFinancial = runtimeText.match(/LIVE_SNAPSHOT_BEFORE:([^\r\n]+)/)[1];
  assert.match(beforeFinancial, /monthly_records:1:/); assert.match(beforeFinancial, /score_results:1:/);
  const csrfResponse = await owner.context.get(origin + '/api/auth/csrf'); const csrf = await csrfResponse.json();
  assert.equal(csrf.headerName, 'X-XSRF-TOKEN');
  for (const headers of [{ 'Idempotency-Key': randomUUID() }, { 'Idempotency-Key': randomUUID(), [csrf.headerName]: 'invalid-token' }]) {
    const response = await owner.context.post(origin + '/api/finance/accounts', { data: payload('HTTP CSRF denied'), headers });
    assert.equal(response.status(), 403);
  }
  assert.equal((await accountsNamed(owner.context, 'HTTP CSRF denied')).length, 0);
  const firstKey = randomUUID(), firstPayload = payload('HTTP Owner PKR');
  const firstResponse = await accountPost(owner.context, firstPayload, firstKey); assert.equal(firstResponse.status(), 201);
  const firstBody = await firstResponse.json();
  assert.equal(firstBody.openingBalance, '-2500.50'); assert.equal(firstBody.currentBalance, '-2500.50');
  assert.equal(firstBody.currencyDecimals, 2); assert.equal(firstBody.accountIdentifier, null);
  const replay = await accountPost(owner.context, firstPayload, firstKey); assert.equal(replay.status(), 201); assert.deepEqual(await replay.json(), firstBody);
  const conflict = await accountPost(owner.context, { ...firstPayload, openingBalance: '-1.00' }, firstKey);
  assert.equal(conflict.status(), 409); assert.match((await conflict.json()).message, /has already been used with a different request payload$/);
  assert.equal((await accountsNamed(owner.context, firstPayload.accountName)).length, 1);
  const accountantKey = randomUUID(), accountantPayload = payload('HTTP Accountant BHD', { accountType: 'WALLET', currency: 'BHD', openingBalance: '-1250.375' });
  const accountantResponse = await accountPost(accountant.context, accountantPayload, accountantKey); assert.equal(accountantResponse.status(), 201);
  const accountantBody = await accountantResponse.json(); assert.equal(accountantBody.currentBalance, '-1250.375'); assert.equal(accountantBody.currencyDecimals, 3);
  const futureResponse = await accountPost(owner.context, payload('HTTP Future JPY', { currency: 'JPY', openingBalance: '999999999999', openingDate: tomorrow }));
  assert.equal(futureResponse.status(), 201); const futureBody = await futureResponse.json();
  assert.equal(futureBody.openingBalance, '999999999999'); assert.equal(futureBody.currentBalance, '0'); assert.equal(futureBody.balanceStatus, 'AVAILABLE');
  for (const member of [viewer, manager]) {
    const denied = await accountPost(member.context, firstPayload, firstKey); assert.equal(denied.status(), 403);
    assert.equal((await denied.text()).includes(firstBody.id), false);
  }
  mark('HTTP creation: actual valid/absent/invalid CSRF, OWNER/ACCOUNTANT, MANAGER/VIEWER denial, exact signed PKR/BHD and future JPY');
  for (const [patch, field] of [[{ accountName: '' }, 'accountName'], [{ accountName: 'n'.repeat(101) }, 'accountName'],
    [{ openingDate: '2026-02-30' }, 'openingDate'], [{ openingBalance: '1.001' }, 'openingBalance'], [{ openingBalance: '1000000000000.00' }, 'openingBalance'],
    [{ accountType: 'CASH', openingBalance: '-1.00' }, 'openingBalance']]) {
    const response = await accountPost(owner.context, payload('HTTP Invalid', patch)); assert.equal(response.status(), 400);
    const error = await response.json(); assert.equal(error.error, 'Validation Failed'); assert.ok(error.errors[field]);
  }
  const duplicate = await accountPost(owner.context, firstPayload); assert.equal(duplicate.status(), 409);
  assert.match((await duplicate.json()).message, /^An account with name /);
  mark('HTTP validation: name/date/precision/capacity/negative CASH and duplicate-name conflicts');
  await control('ROLE ' + accountant.user.id + ' ' + businessA + ' VIEWER', 'LIVE_ROLE_CHANGED');
  const demotedReplay = await accountPost(accountant.context, accountantPayload, accountantKey); assert.equal(demotedReplay.status(), 403);
  assert.equal((await demotedReplay.text()).includes(accountantBody.id), false);
  await control('ROLE ' + accountant.user.id + ' ' + businessA + ' ACCOUNTANT', 'LIVE_ROLE_CHANGED');
  await api(owner.context, '/businesses/active', 'POST', { businessId: businessB });
  const otherReplay = await accountPost(owner.context, firstPayload, firstKey); assert.equal(otherReplay.status(), 201);
  const otherBody = await otherReplay.json(); assert.notEqual(otherBody.id, firstBody.id);
  assert.equal((await api(owner.context, '/finance/accounts/' + firstBody.id)).status(), 404);
  await api(owner.context, '/businesses/active', 'POST', { businessId: businessA });
  mark('HTTP replay authorization rechecked after demotion; identical key scoped to business, no cross-business response leak');
  const racePayload = payload('HTTP Concurrent Same Key'), raceKey = randomUUID();
  const raceToken = await (await owner.context.get(origin + '/api/auth/csrf')).json();
  const races = await Promise.all(Array.from({ length: 8 }, () => owner.context.post(origin + '/api/finance/accounts', {
    data: racePayload, headers: { 'Idempotency-Key': raceKey, [raceToken.headerName]: raceToken.token },
  })));
  const statuses = races.map(response => response.status()), raceBodies = await Promise.all(races.map(response => response.json()));
  assert.equal((await accountsNamed(owner.context, racePayload.accountName)).length, 1);
  if (statuses.every(status => status === 201) && new Set(raceBodies.map(body => body.id)).size === 1) {
    mark('HTTP concurrency: eight simultaneous same-key requests return one persisted account and original 201 response');
  } else {
    blockers.push('BE-CREATE-CONCURRENCY: eight actual same-key HTTP statuses ' + statuses.join(',') + '; one account persisted, but every authorized replay must return the same 201 body.');
  }
  const afterRace = await accountPost(owner.context, racePayload, raceKey); assert.equal(afterRace.status(), 201);
  // Browser creation: actual submitted POSTs, list refresh and detail fetches.
  await page.getByLabel('Accounts per page', { exact: true }).selectOption('100');
  await page.getByLabel('Currency', { exact: true }).selectOption('JPY');
  const posts = []; page.on('request', req => { if (isAccountPost(req)) posts.push({ key: req.headers()['idempotency-key'], body: req.postData() }); });
  const ownerUiPayload = payload('UI Owner PKR', { openingBalance: '-45.50' });
  let form = await fillCreate(page, ownerUiPayload);
  assert.equal(await form.getByLabel(/^Account name/).evaluate(element => element === document.activeElement), false); // Date is the last filled control.
  const beforeDouble = posts.length;
  await form.evaluate(element => { element.requestSubmit(); element.requestSubmit(); });
  await form.waitFor({ state: 'detached' }); assert.equal(posts.length - beforeDouble, 1);
  await page.waitForFunction(() => document.activeElement?.textContent === 'Add account');
  await page.getByRole('status').filter({ hasText: 'Account created. The list uses your current filters, so the new account may not appear.' }).waitFor();
  assert.equal(await page.getByRole('button', { name: 'View account details: UI Owner PKR', exact: true }).count(), 0);
  await page.getByLabel('Currency', { exact: true }).selectOption('PKR');
  await page.getByRole('button', { name: 'View account details: UI Owner PKR', exact: true }).click();
  await page.getByRole('dialog').getByText('PKR - 45.50', { exact: true }).first().waitFor(); await page.keyboard.press('Escape');
  assert.equal((await accountsNamed(owner.context, ownerUiPayload.accountName)).length, 1);
  mark('BROWSER OWNER creation: double submit sends one POST, real filtered list refresh and exact opening detail');
  // Real duplicate response must preserve form input and allow correction.
  form = await fillCreate(page, firstPayload); await submitCreate(form);
  await form.getByText('An account with this name already exists in this business. Choose another name.', { exact: true }).first().waitFor();
  assert.equal(await form.getByLabel(/^Account name/).inputValue(), firstPayload.accountName);
  await form.getByRole('button', { name: 'Cancel', exact: true }).click();
  const accountantContext = await browser.newContext(); const accountantPage = await accountantContext.newPage(); await signin(accountantPage, accountant.email);
  form = await fillCreate(accountantPage, payload('UI Accountant JPY Future', { currency: 'JPY', openingBalance: '123456789012', openingDate: tomorrow }));
  await submitCreate(form); await form.waitFor({ state: 'detached' });
  await accountantPage.getByRole('button', { name: 'View account details: UI Accountant JPY Future', exact: true }).click();
  await accountantPage.getByRole('dialog').getByText('JPY 123,456,789,012', { exact: true }).waitFor();
  await accountantPage.getByRole('dialog').getByText('JPY 0', { exact: true }).waitFor(); await accountantContext.close();
  mark('BROWSER duplicate preserves inputs; ACCOUNTANT creates future JPY with exact planned value and zero current');
  await page.getByRole('button', { name: 'اردو', exact: true }).click(); await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  await page.getByText('مالی اکاؤنٹس', { exact: true }).first().waitFor();
  const uncertainPayload = payload('UI Uncertain BHD', { accountType: 'WALLET', currency: 'BHD', openingBalance: '-12.375' });
  form = await fillCreate(page, uncertainPayload, true);
  assert.equal(await form.locator('input[inputmode="decimal"]').getAttribute('dir'), 'ltr');
  await page.screenshot({ path: path.join(out, 'create-urdu-mobile.png'), fullPage: true });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  let dropped = false;
  const postRoute = /\/api\/finance\/accounts$/;
  await page.route(postRoute, async route => {
    if (!isAccountPost(route.request()) || dropped) { await route.continue(); return; }
    const response = await route.fetch(); assert.equal(response.status(), 201); dropped = true; await route.abort('failed');
  });
  const beforeUncertain = posts.length; await submitCreate(form);
  await form.getByText('نتیجہ غیر یقینی ہے۔ ان معلومات کو تبدیل کرنے سے پہلے اصل درخواست کا نتیجہ معلوم کریں۔ دوبارہ کوشش میں وہی کلید اور عین وہی معلومات استعمال ہوں گی۔', { exact: true }).waitFor();
  assert.equal(await form.getByLabel(/^اکاؤنٹ کا نام/).isDisabled(), true);
  assert.equal((await accountsNamed(owner.context, uncertainPayload.accountName)).length, 1);
  assert.equal(posts.length - beforeUncertain, 1, 'no automatic mutation replay');
  await form.getByRole('button', { name: 'اصل درخواست دوبارہ بھیجیں', exact: true }).click(); await form.waitFor({ state: 'detached' });
  assert.deepEqual(posts[beforeUncertain], posts[beforeUncertain + 1]);
  assert.equal((await accountsNamed(owner.context, uncertainPayload.accountName)).length, 1);
  await page.unroute(postRoute);
  await page.getByRole('button', { name: 'اکاؤنٹ کی تفصیل دیکھیں: UI Uncertain BHD', exact: true }).click();
  await page.getByRole('dialog').getByText('BHD - 12.375', { exact: true }).first().waitFor(); await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'English', exact: true }).click(); await page.getByText('Financial accounts', { exact: true }).first().waitFor();
  mark('BROWSER Urdu RTL/mobile exact BHD, real committed/lost response resolved by explicit same-key identical-payload retry with one account');
  // Delayed real creation response is discarded when another tab switches business.
  const switchSettings = await context.newPage(); await switchSettings.goto(origin + '/settings');
  let creationFetched = false;
  await page.route(postRoute, async route => {
    if (!isAccountPost(route.request())) { await route.continue(); return; }
    const response = await route.fetch(); creationFetched = true; await new Promise(resolve => setTimeout(resolve, 1500));
    try { await route.fulfill({ response }); } catch { /* stale business caller canceled */ }
  });
  form = await fillCreate(page, payload('UI Stale Business Creation')); await submitCreate(form);
  await waitFor(() => creationFetched, 'Actual committed creation before business switch');
  await switchSettings.getByLabel(/^Active business/).selectOption(businessB);
  await page.getByText('Other Business Bank', { exact: true }).waitFor(); await page.waitForTimeout(1700);
  assert.equal(await page.getByText('UI Stale Business Creation', { exact: true }).count(), 0);
  await page.unroute(postRoute);
  await switchSettings.getByLabel(/^Active business/).selectOption(businessA); await page.getByText('Live PKR Bank', { exact: true }).waitFor();
  await switchSettings.close();
  mark('BROWSER business switch invalidates a pending real creation and discards stale success');
  await control('SNAPSHOT AFTER', 'LIVE_SNAPSHOT_AFTER:');
  assert.equal(runtimeText.match(/LIVE_SNAPSHOT_AFTER:([^\r\n]+)/)[1], beforeFinancial);
  mark('HTTP/DB: nonempty monthly records, scores, insights and recommendations unchanged by all creation/retry operations');
  const settings = await context.newPage(); await settings.goto(origin + '/settings');
  await settings.getByLabel(/^Active business/).waitFor();
  let oldListFetched = false;
  const listRoute = /\/api\/finance\/accounts(?:\?|$)/;
  await page.route(listRoute, async route => {
    const response = await route.fetch(); oldListFetched = true;
    await new Promise(resolve => setTimeout(resolve, 1500));
    try { await route.fulfill({ response }); } catch { /* business switch canceled the caller */ }
  });
  await page.reload(); await waitFor(() => oldListFetched, 'Real in-flight old-business list');
  await settings.getByLabel(/^Active business/).selectOption(businessB);
  await page.getByText('Other Business Bank', { exact: true }).waitFor();
  await new Promise(resolve => setTimeout(resolve, 1700));
  assert.equal(await page.getByText('Live PKR Bank', { exact: true }).count(), 0);
  await page.unroute(listRoute); await settings.close();
  const deniedAfterSwitch = await api(context.request, '/finance/accounts/' + ids.pkr); assert.equal(deniedAfterSwitch.status(), 404);
  mark('cross-tab business switch rejects in-flight old data and actual old-business detail returns 404');
  let sessionCreationFetched = false;
  await page.route(postRoute, async route => {
    if (!isAccountPost(route.request())) { await route.continue(); return; }
    const response = await route.fetch(); sessionCreationFetched = true; await new Promise(resolve => setTimeout(resolve, 1500));
    try { await route.fulfill({ response }); } catch { /* expired session caller canceled */ }
  });
  form = await fillCreate(page, payload('UI Stale Session Creation')); await submitCreate(form);
  await waitFor(() => sessionCreationFetched, 'Actual creation before session expiry');
  await control('EXPIRE', 'LIVE_EXPIRED'); await page.reload(); await page.getByRole('heading', { name: 'Sign in to FinSight' }).waitFor();
  await page.waitForTimeout(1700); await page.unroute(postRoute);
  assert.equal(await page.getByText('UI Stale Session Creation', { exact: true }).count(), 0);
  assert.equal(await page.getByText('Other Business Bank', { exact: true }).count(), 0);
  mark('actual JDBC session removal closes private read view and rejects stale creation success');
  await control('SNAPSHOT FINAL', 'LIVE_SNAPSHOT_FINAL:');
  assert.equal(runtimeText.match(/LIVE_SNAPSHOT_FINAL:([^\r\n]+)/)[1], beforeFinancial);
  await signin(page, viewer.email);
  await control('STOP_SPRING', 'LIVE_STOPPED_SPRING'); await page.reload();
  await page.getByRole('alert').first().waitFor(); assert.equal(await page.getByText('No accounts match these filters.', { exact: true }).count(), 0);
  mark('stopped task-owned Spring produces visible failure, never mock or empty financial data');
  assert.deepEqual(errors, []);
  await context.close();
  }
} catch (error) {
  console.error(error.stack);
  if (browser) {
    const failurePage = browser.contexts()[0]?.pages()[0];
    if (failurePage) await failurePage.screenshot({ path: path.join(out, 'failure.png'), fullPage: true }).catch(() => {});
  }
  const diagnostic = runtimeText.split(/\r?\n/).filter(line => /error:|Exception in thread|Caused by:|APPLICATION FAILED|Description:|cannot find symbol|Could not find/.test(line)).slice(-8).map(line => line.slice(0, 240));
  for (const line of diagnostic) console.error('RUNTIME_DIAGNOSTIC ' + line);
  blockers.push(error.message); console.error('FAIL ' + error.message); process.exitCode = 1;
} finally {
  if (browser) await browser.close();
  for (const context of apiContexts) await context.dispose();
  await stopNext().catch(error => { blockers.push(error.message); process.exitCode = 1; });
  if (runtime && !exited(runtime)) {
    runtime.stdin.write('STOP\n');
    await waitFor(() => exited(runtime), 'Task-owned PostgreSQL/Spring shutdown', 15000).catch(() => { runtime.kill(); process.exitCode = 1; });
  }
  writeFileSync(path.join(out, demoOnly ? 'demo-results.json' : 'results.json'), JSON.stringify({ checks, blockers, environment: runtimeText.match(/LIVE_ENV:([^\r\n]+)/)?.[1] ?? null, integrated: !demoOnly && blockers.length === 0 }, null, 2));
  if (blockers.length) {
    for (const blocker of blockers) console.error('BLOCKER ' + blocker);
    process.exitCode = 1;
  }
  const resolved = path.resolve(temporary);
  if (resolved.startsWith(path.resolve(tmpdir()) + path.sep) && path.basename(resolved).startsWith('finsight-accounts-live-')) rmSync(resolved, { recursive: true });
}
