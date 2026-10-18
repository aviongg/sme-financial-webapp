import { verifyRecordDrafts, verifyReplaySyntax } from './record-drafts.live.mjs';
import { verifyTransfers } from './transfers.live.mjs';
import { verifyReversals } from './reversals.live.mjs';
// Real Spring/PostgreSQL acceptance. No synthetic HTTP API and no silent skip.
// Requires current backend target/classes/test-classes + Surefire classpath evidence,
// installed JDK, pnpm, Playwright/Edge, and a live Next build targeting port 8280.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';
import { readFileSync, writeFileSync, mkdtempSync, mkdirSync, rmSync, statSync, openSync, closeSync, existsSync } from 'node:fs';
import path from 'node:path';
import { tmpdir } from 'node:os';
import { pathToFileURL, fileURLToPath } from 'node:url';
import net from 'node:net';
import { createHash, randomBytes, randomUUID } from 'node:crypto';

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
const playwrightPath = process.env.PLAYWRIGHT_MODULE || (existsSync('C:/Users/PC/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright') ? 'C:/Users/PC/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright' : 'playwright');
const { chromium, request } = require(playwrightPath || 'playwright');
const temporary = mkdtempSync(path.join(tmpdir(), 'finsight-accounts-live-'));
const backendPort = Number(process.env.TEST_BACKEND_PORT || 8680), nextPort = Number(process.env.TEST_NEXT_PORT || 3581), gatewayPort = Number(process.env.TEST_GATEWAY_PORT || 3580);
const expectedDraftHash='595b0f69705cce3d21022e6ad67e8be4105575ebdce89781500b0a939bc50b4a';
const origin = 'http://127.0.0.1:'+gatewayPort;
// Parallel acceptance preserves the existing 3180/8280 review app and its data.
// Transparent gateway routes real API traffic to fresh Spring 8380 and page/assets to production Next 3281.
const manifest = JSON.parse(readFileSync(path.join(frontend,'.next/routes-manifest.json'),'utf8'));
const rewrite = Object.values(manifest.rewrites).flat().find(rule => rule.source === '/api/:path*');
if (rewrite?.destination !== 'http://127.0.0.1:8280/api/:path*') throw new Error('Live harness requires a build with process-only BACKEND_API_URL=http://127.0.0.1:8280. No environment file should be changed.');
const demoOnly = process.argv.includes('--demo');
const reviewOnly = process.argv.includes('--review');
const parityOnly = process.argv.includes('--parity');
const draftSyntaxOnly=process.argv.includes('--draft-syntax');
const captureOldParity = process.argv.includes('--capture-old-parity');
let leaveReview = false;
let runtime, next, browser, gateway;
let gatewayText = "";
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
        "--server.port=${backendPort}", "--server.address=127.0.0.1",
        "--spring.datasource.url="+DisposablePostgres.URL,
        "--spring.datasource.username=finsight_app", "--spring.datasource.password="+DisposablePostgres.PASSWORD,
        "--spring.flyway.enabled=false", "--app.documents.storage-dir="+DisposablePostgres.DOCUMENTS,
        "--app.whatsapp.scheduler.enabled=false", "--finsight.crypto.keys.k1="+key,
        "--app.security.cors.allowed-origins=${origin}", "--logging.level.root=ERROR");
      try(var c=pg.getPostgresDatabase().getConnection(); var s=c.createStatement(); var r=s.executeQuery("SELECT version(), (SELECT max(version::int) FROM flyway_schema_history WHERE success)")) { r.next(); System.out.println("LIVE_ENV:"+r.getString(1).split(",")[0]+";schema="+r.getInt(2)); }
      var accountReadClass=com.app.sme_health_backend.accounting.service.AccountReadService.class;
      try(var bytes=accountReadClass.getResourceAsStream("AccountReadService.class")) {
        System.out.println("LIVE_ACCOUNT_READ_CLASS:"+accountReadClass.getProtectionDomain().getCodeSource().getLocation()+";sha256="+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes())));
      }
      var draftClass=com.app.sme_health_backend.accounting.draft.RecordDraftService.class;
      try(var bytes=draftClass.getResourceAsStream("RecordDraftService.class")){System.out.println("LIVE_DRAFT_CLASS:"+draftClass.getProtectionDomain().getCodeSource().getLocation()+";sha256="+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes())));}
      System.out.println("LIVE_READY"); System.out.flush();
      System.out.println("LIVE_DB_PORT:"+pg.getPort()); System.out.flush();
      if(args.length==2 && args[0].equals("--review")) { while(!Files.exists(Path.of(args[1]))) Thread.sleep(500); return; }
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
          else if(parts[0].equals("TXCOUNTS")) {
            StringBuilder counts=new StringBuilder();
            for(String sql:List.of("SELECT count(*) FROM financial_transactions","SELECT count(*) FROM account_movements","SELECT count(*) FROM financial_idempotency_records WHERE operation='FINANCIAL_TRANSACTION'","SELECT count(*) FROM security_audit_events WHERE event_type='FINANCIAL_TRANSACTION_POSTED'")) {
              try(var q=c.createStatement(); var r=q.executeQuery(sql)) { r.next(); counts.append(r.getLong(1)).append(":"); }
            }
            System.out.println("LIVE_TXCOUNTS_"+parts[1]+":"+counts);
          } else if(parts[0].equals("AUDIT")) {
            for(int i=1;i<4;i++) UUID.fromString(parts[i]);
            try(var q=c.prepareStatement("SELECT count(*) FROM security_audit_events WHERE event_type='FINANCIAL_TRANSACTION_POSTED' AND target_id=? AND actor_user_id=?::uuid AND business_id=?::uuid")) { q.setString(1,parts[1]);q.setString(2,parts[2]);q.setString(3,parts[3]);try(var r=q.executeQuery()){r.next();System.out.println("LIVE_AUDIT_"+parts[1]+":"+r.getLong(1));} }
          }
          else if(parts[0].equals("CORRUPT_OPENING")) {
            UUID.fromString(parts[1]);
            try(var q=c.prepareStatement("UPDATE financial_accounts SET opening_balance=999999999999.99 WHERE id=?::uuid")){q.setString(1,parts[1]);if(q.executeUpdate()!=1)throw new IllegalStateException("Missing isolated integrity fixture");}
            System.out.println("LIVE_CORRUPT_OPENING");
          }
          else if(parts[0].equals("EXPIRE")) { try(var s=c.createStatement()) { s.executeUpdate("DELETE FROM spring_session"); } System.out.println("LIVE_EXPIRED"); }
        }
        System.out.flush();
      }
    } finally { if(app!=null) app.close(); pg.close(); }
  }
}`;
function accountReadClassFreshness() {
  const base='com/app/sme_health_backend/accounting/';
  assert.equal(createHash('sha256').update(readFileSync(path.join(root,'backend/target/classes',base+'draft/RecordDraftService.class'))).digest('hex'),expectedDraftHash,'Expected replay-syntax class is not compiled');
  for(const name of ['service/AccountReadService','service/TransactionPostService','service/TransactionReadService','controller/AccountController','controller/TransactionController','dto/AccountListItemResponse','dto/AccountDetailResponse','draft/RecordDraftService','draft/RecordDraftController','draft/DraftDetailResponse','service/TransferService','controller/TransferController','dto/TransferPostRequest']) {
    const source=path.join(root,'backend/src/main/java',base+name+'.java'),compiled=path.join(root,'backend/target/classes',base+name+'.class');
    assert.ok(statSync(compiled).mtimeMs>=statSync(source).mtimeMs,'Backend compilation is stale: '+name);
  }
  return createHash('sha256').update(readFileSync(path.join(root,'backend/target/classes',base+'service/AccountReadService.class'))).digest('hex');
}
let accountParser;
function consumerAccountParser() {
  if(accountParser)return accountParser;
  const ts=require('typescript');
  for(const name of ['lib/utils/decimal-display','lib/api/dev-a/account-contracts']){
    const output=path.join(temporary,'consumer',name+'.js');mkdirSync(path.dirname(output),{recursive:true});
    writeFileSync(output,ts.transpileModule(readFileSync(path.join(frontend,'src',name+'.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText);
  }
  accountParser=require(path.join(temporary,'consumer/lib/api/dev-a/account-contracts.js'));return accountParser;
}
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
async function startGateway(reviewDirectory) {
 await free(gatewayPort);
 const source = `import http from 'node:http';
 const server=http.createServer((req,res)=>{
  const port=(req.url.startsWith('/api/')||req.url==='/api')?${backendPort}:${nextPort};
  const upstream=http.request({hostname:'127.0.0.1',port,path:req.url,method:req.method,headers:req.headers},reply=>{res.writeHead(reply.statusCode,reply.headers);reply.pipe(res);});
  upstream.on('error',()=>{if(!res.headersSent)res.writeHead(502,{'content-type':'text/plain'});res.end('Upstream unavailable');});
  req.on('aborted',()=>upstream.destroy());res.on('close',()=>upstream.destroy());req.pipe(upstream);
 });server.listen(${gatewayPort},'127.0.0.1',()=>console.log('GATEWAY_READY'));
 process.on('SIGTERM',()=>{server.closeAllConnections();server.close(()=>process.exit(0));});`;
 const file=path.join(temporary,'gateway.mjs');writeFileSync(file,source);
 if(reviewDirectory){const log=path.join(reviewDirectory,'gateway.log'),fd=openSync(log,'w');gateway=spawn(process.execPath,[file],{windowsHide:true,detached:true,stdio:['ignore',fd,fd]});closeSync(fd);await waitFor(()=>readFileSync(log,'utf8').includes('GATEWAY_READY'),'Review gateway');}
 else{gateway=spawn(process.execPath,[file],{windowsHide:true});gateway.stdout.on('data',data=>gatewayText+=data);gateway.stderr.on('data',data=>gatewayText+=data);await waitFor(()=>gatewayText.includes('GATEWAY_READY'),'Acceptance gateway');}
}
async function stopNext() {
  if(gateway&&!exited(gateway)){gateway.kill();await waitFor(()=>exited(gateway),'Task-owned gateway shutdown',15000);}
  if (!next || exited(next)) return;
  next.kill(); await waitFor(() => exited(next), 'Task-owned Next shutdown', 15000);
}
async function startNext() {
  await free(nextPort);
  nextText = '';
  next = spawn(process.execPath, [path.join(frontend, 'node_modules/next/dist/bin/next'), 'start', '--hostname', '127.0.0.1', '--port', String(nextPort)], { cwd: frontend, windowsHide: true });
  next.stdout.on('data', data => nextText += data); next.stderr.on('data', data => nextText += data);
  await waitFor(() => /Ready in/.test(nextText), 'Task-owned Next');
  await startGateway();
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
const isTransactionPost = req => req.method() === 'POST' && new URL(req.url()).pathname === '/api/finance/transactions';
const transactionPost = (context, data, key = randomUUID()) => api(context, '/finance/transactions', 'POST', data, { 'Idempotency-Key': key });
async function txCounts() {
  const tag = randomBytes(6).toString('hex'); await control('TXCOUNTS ' + tag, 'LIVE_TXCOUNTS_' + tag + ':');
  return runtimeText.match(new RegExp('LIVE_TXCOUNTS_' + tag + ':([^\\r\\n]+)'))[1];
}
async function audit(transaction, actor, business) {
  await control('AUDIT ' + transaction + ' ' + actor + ' ' + business, 'LIVE_AUDIT_' + transaction + ':');
  assert.equal(runtimeText.match(new RegExp('LIVE_AUDIT_' + transaction + ':(\\d+)'))[1], '1');
}
async function fillTransaction(page, data, urdu = false) {
  await page.getByRole('button', { name: urdu ? 'ریکارڈ شامل کریں' : 'Add record', exact: true }).click();
  const form = page.getByRole('form', { name: urdu ? 'ریکارڈ شامل کریں' : 'Add record', exact: true });
  const select = form.getByLabel(urdu ? /^اکاؤنٹ/ : /^Account/);
  await page.waitForFunction(id => document.getElementById(id) === document.activeElement, await select.getAttribute('id'));
  await select.selectOption(data.accountId);
  await form.getByLabel(urdu ? /^واقعہ/ : /^Event/).selectOption(data.eventType);
  await form.getByLabel(urdu ? /^رقم/ : /^Amount/).fill(data.amount);
  await form.getByLabel(urdu ? /^مؤثر تاریخ/ : /^Effective date/).fill(data.effectiveDate);
  if (data.description) await form.getByLabel(urdu ? /^تفصیل/ : /^Description/).fill(data.description);
  await form.getByRole('checkbox').check(); return form;
}
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
  if (reviewOnly) {
    const expectedReviewClassHash = accountReadClassFreshness();
    // Independent Java lifetime: not tied to this setup process or acceptance teardown.
    const reviewDirectory = process.env.FINSIGHT_REVIEW_DIRECTORY;
    if (!reviewDirectory || !path.isAbsolute(reviewDirectory)) throw new Error('Absolute private review directory required.');
    mkdirSync(reviewDirectory, { recursive: true });
    await free(backendPort); await free(nextPort); await free(gatewayPort);
    const manifestValue = classpath.split(';').filter(Boolean).map(entry => pathToFileURL(entry).href + (statSync(entry).isDirectory() ? '/' : '')).join(' ');
    const lines = ('Class-Path: ' + manifestValue).match(/.{1,70}/g);
    writeFileSync(path.join(temporary, 'MANIFEST.MF'), 'Manifest-Version: 1.0\r\n' + lines.map((line, index) => (index ? ' ' : '') + line).join('\r\n') + '\r\n\r\n');
    await command(path.join(javaHome, 'bin/jar.exe'), ['cfm', path.join(temporary, 'classpath.jar'), path.join(temporary, 'MANIFEST.MF')]);
    writeFileSync(path.join(temporary, 'AccountsLiveRuntime.java'), javaSource);
    const stopFile = path.join(temporary, 'review.stop'), backendLog = path.join(reviewDirectory, 'backend.log'), frontendLog = path.join(reviewDirectory, 'frontend.log');
    const backendFd = openSync(backendLog, 'w');
    runtime = spawn(path.join(javaHome, 'bin/java.exe'), ['--class-path', path.join(temporary, 'classpath.jar'), path.join(temporary, 'AccountsLiveRuntime.java'), '--review', stopFile], { cwd: path.join(root, 'backend'), windowsHide: true, detached: true, stdio: ['ignore', backendFd, backendFd] }); closeSync(backendFd);
    await waitFor(() => { runtimeText = readFileSync(backendLog, 'utf8'); return runtimeText.includes('LIVE_READY'); }, 'Independent review backend');
    assert.match(runtimeText, /schema=(?:21|22|23)/);
  assert.ok(runtimeText.includes('LIVE_DRAFT_CLASS:') && runtimeText.includes(';sha256='+createHash('sha256').update(readFileSync(path.join(root,'backend/target/classes/com/app/sme_health_backend/accounting/draft/RecordDraftService.class'))).digest('hex')));
    assert.ok(runtimeText.includes('LIVE_DRAFT_CLASS:') && runtimeText.includes(';sha256='+createHash('sha256').update(readFileSync(path.join(root,'backend/target/classes/com/app/sme_health_backend/accounting/draft/RecordDraftService.class'))).digest('hex')));
    assert.ok(runtimeText.includes(';sha256=' + expectedReviewClassHash), 'Review runtime class differs from verified current target/classes');
    const frontendFd = openSync(frontendLog, 'w');
    next = spawn(process.execPath, [path.join(frontend, 'node_modules/next/dist/bin/next'), 'start', '--hostname', '127.0.0.1', '--port', String(nextPort)], { cwd: frontend, windowsHide: true, detached: true, stdio: ['ignore', frontendFd, frontendFd] }); closeSync(frontendFd);
    await waitFor(() => /Ready in/.test(readFileSync(frontendLog, 'utf8')), 'Independent review frontend');
    await startGateway(reviewDirectory);
    const owner = await identity('local-review@finsight.example.test');
    const b = await api(owner.context, '/businesses', 'POST', { businessName: 'LOCAL REVIEW — isolated sample business', businessType: 'retail', languagePreference: 'en', whatsappOptIn: false }); assert.equal(b.status(), 201);
    await api(owner.context, '/businesses/active', 'POST', { businessId: (await b.json()).businessId });
    const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Karachi', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
    const a = await accountPost(owner.context, { accountName: 'LOCAL REVIEW — PKR operating bank', accountType: 'BANK', currency: 'PKR', openingBalance: '100000.00', openingDate: today }); assert.equal(a.status(), 201); const accountId = (await a.json()).id;
    for (const [eventType, amount] of [['DIRECT_SALE_RECEIPT', '20000.00'], ['OPERATING_EXPENSE_PAYMENT', '5000.00']]) assert.equal((await transactionPost(owner.context, { accountId, eventType, amount, effectiveDate: today, description: 'LOCAL REVIEW synthetic sample only' })).status(), 201);
    for (const [currency, openingBalance, name] of [['JPY', '125000', 'JPY wallet'], ['BHD', '1250.375', 'BHD bank'], ['PKR', '5000.00', 'PKR cash']]) assert.equal((await accountPost(owner.context, { accountName: 'LOCAL REVIEW — ' + name, accountType: name.includes('cash') ? 'CASH' : name.includes('wallet') ? 'WALLET' : 'BANK', currency, openingBalance, openingDate: today })).status(), 201);
    assert.equal((await (await api(owner.context, '/finance/accounts/' + accountId)).json()).currentBalance, '115000.00');
    const reviewDraft = await api(owner.context,'/finance/drafts','POST',{amountInput:'1500.',description:'LOCAL REVIEW — unfinished draft; no financial effect'},{'Idempotency-Key':randomUUID()});assert.equal(reviewDraft.status(),201);
    const reviewMonth = today.slice(0, 7);
    assert.equal((await api(owner.context, '/records/monthly', 'POST', { month: reviewMonth, cashInflow: 20000, cashOutflow: 5000, revenue: 20000, operatingExpenses: 5000, cashBalanceEom: 115000, cogs: 4000, financingType: 'none' })).status(), 201);
    assert.equal((await api(owner.context, '/scores/calculate', 'POST', { month: reviewMonth })).status(), 200);
    browser = await chromium.launch({ channel: 'msedge', headless: true });
    const reviewBrowserContext = await browser.newContext({ viewport: { width: 390, height: 844 } });
    const reviewPage = await reviewBrowserContext.newPage();
    await reviewPage.goto(origin + '/records');
    await reviewPage.getByRole('heading', { name: 'Sign in to FinSight' }).waitFor();
    await reviewPage.getByLabel(/^Email/).fill(owner.email); await reviewPage.getByLabel(/^Password/).fill(password);
    await reviewPage.getByRole('button', { name: 'Continue', exact: true }).click();
    await reviewPage.getByRole('button', { name: /^View transaction details:/ }).first().waitFor();
    await reviewPage.getByRole('button', { name: /^Money received/ }).getByText('20,000.00 PKR', { exact: true }).waitFor();
    await reviewPage.getByRole('button', { name: 'اردو', exact: true }).click();
    await reviewPage.getByRole('button', { name: /^لین دین کی تفصیل دیکھیں:/ }).first().waitFor();
    assert.equal(await reviewPage.locator('html').getAttribute('dir'), 'rtl');
    assert.equal(await reviewPage.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await reviewPage.screenshot({ path: path.join(out, 'records-review-urdu-mobile.png'), fullPage: true });
    await reviewPage.getByRole('button', { name: 'ریکارڈ شامل کریں', exact: true }).click();
    const reviewForm = reviewPage.getByRole('form', { name: 'ریکارڈ شامل کریں', exact: true });
    await reviewForm.getByLabel(/^رقم/).fill('۱,۵۰۰.۵');
    await reviewForm.getByText('1500.50 PKR', { exact: true }).waitFor();
    await reviewForm.getByRole('button', { name: 'منسوخ کریں', exact: true }).click();await reviewForm.getByRole('button',{name:'غیر محفوظ ترامیم چھوڑ دیں',exact:true}).click();
    await reviewPage.getByRole('button', { name: 'English', exact: true }).click();
    await reviewPage.getByRole('button', { name: /^View transaction details:/ }).first().waitFor();
    await reviewBrowserContext.close();
    console.log('REVIEW_BROWSER_PASS loaded EN/UR mobile Records and natural amount preview; no smoke-test posting');
    writeFileSync(path.join(reviewDirectory, 'credentials.txt'), 'Local isolated review only\nEmail: ' + owner.email + '\nPassword: ' + password + '\nSign in through the normal app. Complete any security/MFA prompts.\n');
    const metadata = { frontendUrl: origin, frontendPid: next.pid, backendPid: runtime.pid, databasePort: Number(runtimeText.match(/LIVE_DB_PORT:(\d+)/)[1]), migration: Number(runtimeText.match(/schema=(\d+)/)?.[1] || 23), backendPort, nextPort, gatewayPort, gatewayPid: gateway.pid, gatewayCommandPath: path.join(temporary,'gateway.mjs'), draftClassSha256: createHash('sha256').update(readFileSync(path.join(root,'backend/target/classes/com/app/sme_health_backend/accounting/draft/RecordDraftService.class'))).digest('hex'), backendClassSha256: expectedReviewClassHash, stopFile, runtimeDirectory: temporary, frontendCommandPath: path.join(frontend, 'node_modules/next/dist/bin/next'), javaSource: path.join(temporary, 'AccountsLiveRuntime.java') };
    writeFileSync(path.join(reviewDirectory, 'processes.json'), JSON.stringify(metadata, null, 2));
    leaveReview = true; runtime.unref(); next.unref(); gateway.unref();
    console.log('REVIEW_READY ' + origin + ' backendPid=' + runtime.pid + ' frontendPid=' + next.pid + ' databasePort=' + metadata.databasePort);
  } else if (demoOnly) {
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
    await page.goto(origin + '/finance/transactions'); await page.getByText('ڈیمو موڈ میں لین دین کا پیش منظر دستیاب نہیں ہے۔', { exact: true }).waitFor(); assert.deepEqual(privateRequests, []);
    await page.goto(origin + '/records'); await page.getByText('ڈیمو موڈ میں سرگرمی اور رقم کے اکاؤنٹس کا پیش منظر دستیاب نہیں۔', {exact:true}).waitFor(); assert.deepEqual(privateRequests, []);
    await page.getByRole('button', {name:'Switch interface language'}).click(); await page.getByText('Activity and money accounts preview is not available in demo mode.', {exact:true}).waitFor(); assert.deepEqual(privateRequests, []);
    assert.equal(await page.getByRole('button', {name:'Add record',exact:true}).count(),0);
    await page.goto(origin+'/records?view=review');await page.getByText('Activity and money accounts preview is not available in demo mode.',{exact:true}).waitFor();assert.deepEqual(privateRequests,[]);
    mark('demo browser accounts/transactions/Records English/Urdu preview unavailable; zero private API requests');
    await context.close();
  } else {
  const expectedAccountReadHash=accountReadClassFreshness();
  await free(backendPort); await free(nextPort); await free(gatewayPort);
  const manifestValue = classpath.split(';').filter(Boolean).map(entry => pathToFileURL(entry).href + (statSync(entry).isDirectory() ? '/' : '')).join(' ');
  const lines = ('Class-Path: ' + manifestValue).match(/.{1,70}/g);
  writeFileSync(path.join(temporary, 'MANIFEST.MF'), 'Manifest-Version: 1.0\r\n' + lines.map((line, index) => (index ? ' ' : '') + line).join('\r\n') + '\r\n\r\n');
  await command(path.join(javaHome, 'bin/jar.exe'), ['cfm', path.join(temporary, 'classpath.jar'), path.join(temporary, 'MANIFEST.MF')]);
  writeFileSync(path.join(temporary, 'AccountsLiveRuntime.java'), javaSource);
  runtime = spawn(path.join(javaHome, 'bin/java.exe'), ['--class-path', path.join(temporary, 'classpath.jar'), path.join(temporary, 'AccountsLiveRuntime.java')], { cwd: path.join(root, 'backend'), windowsHide: true });
  runtime.stdout.on('data', data => runtimeText += data); runtime.stderr.on('data', data => runtimeText += data);
  await waitFor(() => runtimeText.includes('LIVE_READY'), 'Disposable Spring/PostgreSQL runtime', 60000);
  assert.match(runtimeText, /schema=(?:21|22|23)/);
  assert.ok(runtimeText.includes('LIVE_DRAFT_CLASS:') && runtimeText.includes(';sha256='+createHash('sha256').update(readFileSync(path.join(root,'backend/target/classes/com/app/sme_health_backend/accounting/draft/RecordDraftService.class'))).digest('hex')));
  assert.ok(runtimeText.includes(';sha256='+expectedAccountReadHash),'Running backend class differs from verified current target/classes');
  mark('fresh runtime loads verified current AccountReadService class bytes; source/class timestamps agree');
  mark('real disposable PostgreSQL ' + (runtimeText.match(/LIVE_ENV:[^;]+;schema=(\d+)/)?.[1] ? 'V' + runtimeText.match(/LIVE_ENV:[^;]+;schema=(\d+)/)[1] : 'V23') + '; verified syntax-fix Spring '+backendPort+', gateway '+gatewayPort+', production Next '+nextPort);
  await startNext();
  if(draftSyntaxOnly){
    const owner=await identity('syntax-parity@example.test');
    const b=await api(owner.context,'/businesses','POST',{businessName:'SYNTAX PARITY isolated business',businessType:'retail',languagePreference:'en',whatsappOptIn:false});assert.equal(b.status(),201);const businessId=(await b.json()).businessId;await api(owner.context,'/businesses/active','POST',{businessId});
    const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Karachi',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
    const a=await accountPost(owner.context,{accountName:'SYNTAX PARITY bank',accountType:'BANK',currency:'PKR',openingBalance:'0.00',openingDate:today});assert.equal(a.status(),201);
    await verifyReplaySyntax({api,owner,accountId:(await a.json()).id,today,txCounts,mark,requestFactory:request});
  } else if(parityOnly) {
    const owner=await identity('parity-owner@example.test');
    const b=await api(owner.context,'/businesses','POST',{businessName:'PARITY — disposable synthetic business',businessType:'retail',languagePreference:'en',whatsappOptIn:false});assert.equal(b.status(),201);
    await api(owner.context,'/businesses/active','POST',{businessId:(await b.json()).businessId});
    const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Karachi',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
    const response=await accountPost(owner.context,{accountName:'PARITY current-only overflow',accountType:'BANK',currency:'PKR',openingBalance:'0.00',openingDate:today});assert.equal(response.status(),201);const id=(await response.json()).id;
    assert.equal((await transactionPost(owner.context,{accountId:id,eventType:'DIRECT_SALE_RECEIPT',amount:'1.00',effectiveDate:today})).status(),201);
    await control('CORRUPT_OPENING '+id,'LIVE_CORRUPT_OPENING');
    const read=await api(owner.context,'/finance/accounts/'+id);assert.equal(read.status(),200);const payload=await read.json();
    writeFileSync(path.join(out,captureOldParity?'parity-before.json':'parity-after.json'),JSON.stringify(payload,null,2));
    console.log('PARITY_RESPONSE '+JSON.stringify(payload));
    const parser=consumerAccountParser();
    if(captureOldParity) {
      assert.throws(()=>parser.parseAccountDetail(payload,id),parser.InvalidAccountResponse);
      assert.ok(payload.openingBalance!==null && payload.balanceUnavailableReason!=='INTEGRITY_CHECK_FAILED');
      console.log('CAPTURED_OLD_FAILURE parseAccountDetail => InvalidAccountResponse; old harness condition openingBalance!==null && reason!==INTEGRITY_CHECK_FAILED evaluates true');
    } else {
      assert.equal(parser.parseAccountDetail(payload,id).openingBalance,'999999999999.99');
      assert.equal(payload.currentBalance,null);assert.equal(payload.balanceStatus,'UNAVAILABLE');assert.equal(payload.balanceUnavailableReason,'CAPACITY_OVERFLOW');
      mark('targeted real current-only overflow accepted with exact preserved opening and unavailable current');
    }
  } else {
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
  await page.getByLabel('Currency', { exact: true }).selectOption('');
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
  assert.equal(new URL(page.url()).searchParams.get('currency'), 'PKR');
  await page.getByLabel('کرنسی', { exact: true }).selectOption('BHD');
  await page.getByRole('button', { name: 'اکاؤنٹ کی تفصیل دیکھیں: UI Uncertain BHD', exact: true }).click();
  await page.getByRole('dialog').getByText('BHD - 12.375', { exact: true }).first().waitFor(); await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'English', exact: true }).click(); await page.getByText('Financial accounts', { exact: true }).first().waitFor();
  await page.getByLabel('Currency', { exact: true }).selectOption('');
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
  // Transaction acceptance uses independently specified expected balances, never UI formulas.
  const day = offset => new Date(Date.parse(cutoff + 'T00:00:00Z') + offset * 86400000).toISOString().slice(0, 10);
  const createTxAccount = async (name, opening = '100000.00', type = 'BANK') => {
    const response = await accountPost(owner.context, payload(name, { accountType: type, openingBalance: opening, openingDate: day(-10) }));
    assert.equal(response.status(), 201); return (await response.json()).id;
  };
  const mainAccount = await createTxAccount('TX Verified Main'), concurrentAccount = await createTxAccount('TX Concurrent', '1000.00');
  const roleAccount = await createTxAccount('TX Role Journeys', '1000.00'), cashAccount = await createTxAccount('TX CASH Sequence', '100.00', 'CASH');
  const tx = (accountId, amount = '1.00', eventType = 'DIRECT_SALE_RECEIPT', effectiveDate = cutoff) => ({ accountId, eventType, amount, effectiveDate });
  const reject = async (data, expected = 400) => {
    const before = await txCounts(), response = await transactionPost(owner.context, data), after = await txCounts();
    if (response.status() !== expected || before !== after) blockers.push('BE-TX-REJECTION: expected ' + expected + ' with no writes; actual ' + response.status() + ', counts ' + before + ' -> ' + after + '; input=' + JSON.stringify(data));
    return response;
  };
  const txCsrf = await (await owner.context.get(origin + '/api/auth/csrf')).json();
  const beforeCsrf = await txCounts();
  for (const headers of [{ 'Idempotency-Key': randomUUID() }, { 'Idempotency-Key': randomUUID(), [txCsrf.headerName]: 'invalid' }]) assert.equal((await owner.context.post(origin + '/api/finance/transactions', { data: tx(concurrentAccount), headers })).status(), 403);
  for (const member of [viewer, manager]) assert.equal((await transactionPost(member.context, tx(concurrentAccount))).status(), 403);
  assert.equal(await txCounts(), beforeCsrf);
  await reject(tx(ids.other), 404); await reject(tx(ids.corrupt)); await reject(tx(ids.jpy));
  await reject(tx(mainAccount, '1.00', 'DIRECT_SALE_RECEIPT', day(-11))); await reject(tx(mainAccount, '1.00', 'DIRECT_SALE_RECEIPT', day(1)));
  for (const amount of ['0.00', '-1.00', '1.001', '1000000000000.00', 'not-a-number']) await reject(tx(mainAccount, amount));
  mark('HTTP transactions: actual CSRF and role denial; foreign/inactive/non-PKR/date/amount rejection checked for atomic no-write behavior');
  const sameKey = randomUUID(), samePayload = tx(concurrentAccount);
  const sameResponses = await Promise.all(Array.from({ length: 8 }, () => transactionPost(owner.context, samePayload, sameKey)));
  assert.equal(sameResponses.every(r => r.status() === 201), true); const sameBodies = await Promise.all(sameResponses.map(r => r.json()));
  assert.equal(new Set(sameBodies.map(t => t.id)).size, 1); assert.equal(new Set(sameBodies.map(t => t.movements[0].id)).size, 1);
  const replayTx = await transactionPost(owner.context, samePayload, sameKey); assert.equal(replayTx.status(), 201); assert.deepEqual(await replayTx.json(), sameBodies[0]);
  assert.equal((await transactionPost(owner.context, { ...samePayload, amount: '2.00' }, sameKey)).status(), 409);
  const distinct = await Promise.all(Array.from({ length: 8 }, () => transactionPost(owner.context, tx(concurrentAccount))));
  assert.equal(distinct.every(r => r.status() === 201), true); const distinctBodies = await Promise.all(distinct.map(r => r.json()));
  assert.equal(new Set(distinctBodies.map(t => t.id)).size, 8);
  assert.equal((await (await api(owner.context, '/finance/accounts/' + concurrentAccount)).json()).currentBalance, '1009.00');
  const concurrentHistory = await (await api(owner.context, '/finance/transactions?accountId=' + concurrentAccount + '&size=100')).json();
  assert.equal(concurrentHistory.totalElements, 9);
  await audit(sameBodies[0].id, owner.user.id, businessA);
  mark('HTTP/DB transactions: concurrent same-key one event/movement; eight distinct requests preserved; replay contributes nothing; audit actor/business verified');
  assert.equal((await transactionPost(owner.context, tx(cashAccount, '80.00', 'OPERATING_EXPENSE_PAYMENT', day(-2)))).status(), 201);
  assert.equal((await transactionPost(owner.context, tx(cashAccount, '100.00'))).status(), 201);
  const cashRejected = await reject(tx(cashAccount, '30.00', 'OPERATING_EXPENSE_PAYMENT', day(-3)));
  assert.ok((await cashRejected.json()).errors.cashBalance);
  assert.equal((await (await api(owner.context, '/finance/accounts/' + cashAccount)).json()).currentBalance, '120.00');
  mark('HTTP CASH: backdated outflow rejected for later intermediate deficit even though final balance would be positive');
  // Keep destructive integrity experiments away from functioning A/B browser fixtures.
  const integrityBusiness = await create('Live Integrity Experiments'); await api(owner.context, '/businesses/active', 'POST', { businessId: integrityBusiness });
  const capacityAccount = await createTxAccount('TX Capacity Boundary', '999999999999.99');
  const beforeCapacity = await txCounts(), capacityResponse = await transactionPost(owner.context, tx(capacityAccount, '0.01')), afterCapacity = await txCounts();
  if (capacityResponse.status() !== 400 || beforeCapacity !== afterCapacity) blockers.push('BE-TX-CAPACITY: aggregate overflow must reject atomically; actual HTTP ' + capacityResponse.status() + ', event/movement/idempotency/audit counts ' + beforeCapacity + ' -> ' + afterCapacity + '.');
  const capacityRead = await (await api(owner.context, '/finance/accounts/' + capacityAccount)).json();
  for (const amount of ['1e2', '+1.00', '01.00']) await reject(tx(capacityAccount, amount));
  assert.equal(capacityRead.openingBalance,'999999999999.99');assert.equal(capacityRead.currentBalance,'999999999999.99');assert.equal(capacityRead.balanceStatus,'AVAILABLE');
  mark('HTTP/DB original regression: capacity overflow and malformed exponent/plus/leading-zero amounts rejected atomically; available account unchanged');
  const corruptCurrentAccount=await createTxAccount('TX Pre-existing Current Overflow','0.00');
  assert.equal((await transactionPost(owner.context,tx(corruptCurrentAccount,'1.00'))).status(),201);
  await control('CORRUPT_OPENING '+corruptCurrentAccount,'LIVE_CORRUPT_OPENING');
  const corruptCurrentRead=await (await api(owner.context,'/finance/accounts/'+corruptCurrentAccount)).json();
  const parsedCurrentOverflow=consumerAccountParser().parseAccountDetail(corruptCurrentRead,corruptCurrentAccount);
  assert.equal(parsedCurrentOverflow.openingBalance,'999999999999.99');assert.equal(parsedCurrentOverflow.currentBalance,null);
  assert.equal(parsedCurrentOverflow.balanceStatus,'UNAVAILABLE');assert.equal(parsedCurrentOverflow.balanceUnavailableReason,'CAPACITY_OVERFLOW');
  await page.goto(origin+'/settings');await page.getByLabel(/^Active business/).selectOption(integrityBusiness);
  await page.goto(origin+'/finance/accounts');await page.getByRole('button',{name:'View account details: TX Pre-existing Current Overflow',exact:true}).click();
  const overflowDialog=page.getByRole('dialog');
  await overflowDialog.getByText('PKR 999,999,999,999.99',{exact:true}).waitFor();
  await overflowDialog.getByText('Current balance',{exact:true}).locator('..').getByText('Unavailable',{exact:true}).waitFor();
  assert.equal(await overflowDialog.getByText('PKR 0.00',{exact:true}).count(),0);await page.keyboard.press('Escape');
  await page.goto(origin+'/settings');await page.getByLabel(/^Active business/).selectOption(businessA);
  mark('HTTP/browser current-only capacity overflow: strict parser accepts exact preserved opening; detail shows unavailable current, never zero');
  await api(owner.context, '/businesses/active', 'POST', { businessId: businessA });
  // Browser OWNER sale, ACCOUNTANT expense: independent 100000 + 20000 - 5000 = 115000.
  await page.goto(origin + '/finance/transactions');
  const notice = 'Transactions update account balances. Monthly records and health scores still use your existing monthly entries until ledger cutover is enabled.';
  await page.getByText(notice, { exact: true }).waitFor();
  const txPosts = []; page.on('request', req => { if (isTransactionPost(req)) txPosts.push({ key: req.headers()['idempotency-key'], body: req.postData() }); });
  let txForm = await fillTransaction(page, tx(mainAccount, '20000.00'));
  await txForm.evaluate(element => { element.requestSubmit(); element.requestSubmit(); }); await txForm.waitFor({ state: 'detached' });
  await page.getByText('PKR 120,000.00', { exact: true }).waitFor(); assert.equal(txPosts.length, 1);
  const salePost = JSON.parse(txPosts[0].body), saleResponse = await transactionPost(owner.context, salePost, txPosts[0].key); const saleBody = await saleResponse.json();
  await audit(saleBody.id, owner.user.id, businessA);
  const txAccountantContext = await browser.newContext(); const txAccountantPage = await txAccountantContext.newPage(); await signin(txAccountantPage, accountant.email); await txAccountantPage.goto(origin + '/finance/transactions');
  txForm = await fillTransaction(txAccountantPage, tx(mainAccount, '5000.00', 'OPERATING_EXPENSE_PAYMENT'));
  await txForm.getByRole('button', { name: 'Add record', exact: true }).click(); await txForm.waitFor({ state: 'detached' }); await txAccountantPage.getByText('PKR 115,000.00', { exact: true }).waitFor();
  txForm = await fillTransaction(txAccountantPage, tx(roleAccount, '0.50')); await txForm.getByRole('button', { name: 'Add record', exact: true }).click(); await txForm.waitFor({ state: 'detached' });
  await txAccountantContext.close();
  const expectedRead = await (await api(owner.context, '/finance/accounts/' + mainAccount)).json(); assert.equal(expectedRead.openingBalance, '100000.00'); assert.equal(expectedRead.currentBalance, '115000.00'); assert.equal(expectedRead.balanceStatus, 'AVAILABLE');
  assert.equal((await transactionPost(owner.context, salePost, txPosts[0].key)).status(), 201);
  assert.equal((await (await api(owner.context, '/finance/accounts/' + mainAccount)).json()).currentBalance, '115000.00');
  const mainHistory = await (await api(owner.context, '/finance/transactions?accountId=' + mainAccount + '&size=100')).json(); assert.equal(mainHistory.totalElements, 2);
  const expenseBody = mainHistory.content.find(t => t.eventType === 'OPERATING_EXPENSE_PAYMENT'); await audit(expenseBody.id, accountant.user.id, businessA);
  mark('BROWSER/HTTP: OWNER sale and ACCOUNTANT expense refresh real balances 100000 -> 120000 -> 115000; retry adds nothing');
  await page.reload(); await page.getByLabel('Transactions per page', { exact: true }).selectOption('10'); await page.getByRole('button', { name: 'Next page', exact: true }).click();
  await page.getByLabel('Account', { exact: true }).selectOption(mainAccount); assert.equal(await page.getByRole('button', { name: 'Previous page', exact: true }).isDisabled(), true);
  await page.getByLabel('Event', { exact: true }).selectOption('DIRECT_SALE_RECEIPT'); await page.getByText('PKR 20,000.00', { exact: true }).waitFor();
  await page.getByRole('button', { name: 'View transaction details: ' + saleBody.id, exact: true }).click();
  await page.getByRole('dialog').getByText('PKR 20,000.00', { exact: true }).waitFor(); assert.equal(await page.evaluate(() => document.querySelector('[role=dialog]').contains(document.activeElement)), true); await page.keyboard.press('Escape');
  await page.getByLabel('Event', { exact: true }).selectOption(''); await page.getByLabel('Through effective date', { exact: true }).fill(day(-1)); await page.getByText('No transactions match these filters.', { exact: true }).waitFor();
  await page.getByLabel('Through effective date', { exact: true }).fill('');
  let txDelayed = false; await page.route('**/api/finance/transactions/' + saleBody.id, async route => { const response = await route.fetch(); txDelayed = true; await new Promise(resolve => setTimeout(resolve, 750)); try { await route.fulfill({ response }); } catch { /* canceled selection */ } });
  await page.getByRole('button', { name: 'View transaction details: ' + saleBody.id, exact: true }).click(); await waitFor(() => txDelayed, 'Real delayed transaction detail'); await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'View transaction details: ' + expenseBody.id, exact: true }).click(); await page.getByRole('dialog').getByText('PKR 5,000.00', { exact: true }).waitFor(); await page.waitForTimeout(900); assert.equal(await page.getByRole('dialog').getByText('PKR 20,000.00', { exact: true }).count(), 0); await page.keyboard.press('Escape'); await page.unroute('**/api/finance/transactions/' + saleBody.id);
  mark('BROWSER transaction history: persisted filters/page reset/date-empty/detail, keyboard focus and canceled selection');
  for (const member of [viewer, manager]) { const c = await browser.newContext(); const p = await c.newPage(); await signin(p, member.email); await p.goto(origin + '/finance/transactions'); await p.getByText(notice, { exact: true }).waitFor(); assert.equal(await p.getByRole('button', { name: 'Add record', exact: true }).count(), 0); await c.close(); }
  await page.getByRole('button', { name: 'اردو', exact: true }).click(); await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  await page.getByRole('note').waitFor();
  let txDropped = false; const txRoute = /\/api\/finance\/transactions$/;
  await page.route(txRoute, async route => { if (!isTransactionPost(route.request()) || txDropped) { await route.continue(); return; } const response = await route.fetch(); assert.equal(response.status(), 201); txDropped = true; await route.abort('failed'); });
  const beforeTxLost = txPosts.length; txForm = await fillTransaction(page, tx(roleAccount, '0.50', 'OPERATING_EXPENSE_PAYMENT'), true); await page.screenshot({ path: path.join(out, 'transactions-urdu-mobile.png'), fullPage: true }); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  await txForm.getByRole('button', { name: 'ریکارڈ شامل کریں', exact: true }).click(); await txForm.getByRole('button', { name: 'اصل درخواست دوبارہ بھیجیں', exact: true }).waitFor(); assert.equal(txPosts.length - beforeTxLost, 1);
  await txForm.getByRole('button', { name: 'اصل درخواست دوبارہ بھیجیں', exact: true }).click(); await txForm.waitFor({ state: 'detached' }); assert.deepEqual(txPosts[beforeTxLost], txPosts[beforeTxLost + 1]); await page.unroute(txRoute);
  await page.getByRole('button', { name: 'English', exact: true }).click(); await page.getByText(notice, { exact: true }).waitFor();
  mark('BROWSER transactions: MANAGER/VIEWER read-only; bilingual cutover notice, RTL/mobile and real lost-response identical-key recovery');
  const txSettings = await context.newPage(); await txSettings.goto(origin + '/settings'); let txSwitchFetched = false;
  await page.route(txRoute, async route => { if (!isTransactionPost(route.request())) { await route.continue(); return; } const response = await route.fetch(); txSwitchFetched = true; await new Promise(resolve => setTimeout(resolve, 1500)); try { await route.fulfill({ response }); } catch { /* old business */ } });
  txForm = await fillTransaction(page, tx(roleAccount, '0.50')); await txForm.getByRole('button', { name: 'Add record', exact: true }).click(); await waitFor(() => txSwitchFetched, 'Real transaction before business switch');
  await txSettings.getByLabel(/^Active business/).selectOption(businessB); await page.getByText('No transactions match these filters.', { exact: true }).waitFor(); await page.waitForTimeout(1700); assert.equal(await page.getByText('TX Role Journeys', { exact: true }).count(), 0); await page.unroute(txRoute);
  await txSettings.getByLabel(/^Active business/).selectOption(businessA); await page.getByText(notice, { exact: true }).waitFor(); await txSettings.close();
  mark('BROWSER transactions: business switch discards stale real posting result');
  await page.goto(origin + '/finance/accounts'); await page.getByText('Live PKR Bank', { exact: true }).waitFor();
  await control('SNAPSHOT AFTER', 'LIVE_SNAPSHOT_AFTER:');
  assert.equal(runtimeText.match(/LIVE_SNAPSHOT_AFTER:([^\r\n]+)/)[1], beforeFinancial);
  // Records workflow against real persisted Spring endpoints, never synthetic summary responses.
  const today = cutoff;
  await api(owner.context, '/businesses/active', 'POST', {businessId:businessA});
  await page.goto(origin + '/records?period=today');
  await page.getByRole('link',{name:'Historical summaries',exact:true}).click();
  await page.getByRole('heading',{name:'Monthly summaries',exact:true}).waitFor();
  await page.getByRole('link',{name:'Back to Records',exact:true}).click();
  await page.waitForURL(url => url.pathname === '/records' && url.searchParams.get('period') === 'today');
  await page.getByRole('button',{name:'Add record',exact:true}).waitFor();
  const wholeTotals=await (await api(owner.context,'/finance/transactions/summary?startDate='+today+'&endDate='+today)).json();
  const oneRow=await (await api(owner.context,'/finance/transactions?startDate='+today+'&endDate='+today+'&size=1')).json();
  assert.equal(wholeTotals.transactionCount,oneRow.totalElements);assert.equal(oneRow.content.length,1);assert.ok(wholeTotals.transactionCount>1);
  let recordForm=await fillTransaction(page,{...tx(mainAccount,'1500'),description:'Preserved contextual entry'});
  await page.getByRole('button',{name:'Create money account',exact:true}).click();
  let inline=page.getByRole('form',{name:'Add account',exact:true});
  await inline.getByRole('button',{name:'Cancel',exact:true}).click();
  recordForm=page.getByRole('form',{name:'Add record',exact:true});
  assert.equal(await recordForm.getByLabel(/^Amount/).inputValue(),'1500');
  assert.equal(await recordForm.getByLabel(/^Description/).inputValue(),'Preserved contextual entry');
  assert.equal(await recordForm.getByLabel(/^Effective date/).inputValue(),today);
  assert.equal(await recordForm.getByRole('checkbox').isChecked(),true);
  await page.getByRole('button',{name:'Create money account',exact:true}).click();
  inline=page.getByRole('form',{name:'Add account',exact:true});
  await inline.getByLabel(/^Account name/).fill('Records Context Bank');
  await inline.getByLabel(/^Opening balance/).fill('1,000.555');
  await inline.getByLabel(/^Opening effective date/).fill(today);
  await inline.getByText('1000.56 PKR',{exact:true}).waitFor();
  await inline.getByRole('button',{name:'Create account',exact:true}).click();
  await inline.waitFor({state:'detached'});
  recordForm=page.getByRole('form',{name:'Add record',exact:true});
  await recordForm.getByLabel(/^Account/).getByRole('option',{name:'Records Context Bank · PKR',exact:true}).waitFor({state:'attached'});
  const contextualId=await recordForm.getByLabel(/^Account/).inputValue();assert.ok(contextualId);
  assert.equal(await recordForm.getByLabel(/^Amount/).inputValue(),'1500');assert.equal(await recordForm.getByLabel(/^Description/).inputValue(),'Preserved contextual entry');
  await recordForm.getByRole('checkbox').check();
  await recordForm.getByRole('button',{name:'Add record',exact:true}).click();await recordForm.waitFor({state:'detached'});
  assert.equal((await (await api(owner.context,'/finance/accounts/'+contextualId)).json()).currentBalance,'2500.56');
  for(const [raw,expected] of [['1500.5','1500.50'],['۱,۵۰۰.۵۵۵','1500.56']]) {
    recordForm=await fillTransaction(page,{...tx(contextualId,raw),description:'Natural input regression'});
    await recordForm.getByText(expected+' PKR',{exact:true}).waitFor();
    assert.equal(await recordForm.getByLabel(/^Amount/).inputValue(),raw);
    await recordForm.getByRole('button',{name:'Add record',exact:true}).click();await recordForm.waitFor({state:'detached'});
    const rows=await (await api(owner.context,'/finance/transactions?accountId='+contextualId+'&size=100')).json();assert.ok(rows.content.some(t=>t.amount===expected));
  }
  assert.equal((await (await api(owner.context,'/finance/accounts/'+contextualId)).json()).currentBalance,'5501.62');
  const summaryResponse=await api(owner.context,'/finance/transactions/summary?accountId='+contextualId+'&startDate='+today+'&endDate='+today);assert.equal(summaryResponse.status(),200);
  const expectedSummary=await summaryResponse.json();assert.equal(expectedSummary.moneyReceived,'4501.06');assert.equal(expectedSummary.moneyPaid,'0.00');assert.equal(expectedSummary.netCashMovement,'4501.06');assert.equal(expectedSummary.transactionCount,3);
  await page.getByLabel('Account',{exact:true}).selectOption(contextualId);
  await page.getByRole('button',{name:/^Money received/}).getByText('4,501.06 PKR',{exact:true}).waitFor();
  await page.getByRole('button',{name:/^Money received/}).click();await page.waitForURL(/eventType=DIRECT_SALE_RECEIPT/);
  assert.equal(await page.getByLabel('Event',{exact:true}).inputValue(),'DIRECT_SALE_RECEIPT');
  await page.getByRole('button',{name:/^Money paid/}).click();await page.waitForURL(/eventType=OPERATING_EXPENSE_PAYMENT/);await page.getByText('No transactions match these filters.',{exact:true}).waitFor();
  await page.getByRole('button',{name:/^Net cash movement/}).click();await page.waitForURL(url=>!url.searchParams.has('eventType'));
  const summaryNetwork = /\/api\/finance\/transactions\/summary(?:\?|$)/;
  await page.route(summaryNetwork, async route => { await route.fetch(); await route.abort('connectionfailed'); });
  await page.getByLabel('Period', { exact: true }).selectOption('week');
  await page.getByText('Period totals are unavailable. Activity below remains usable.', { exact: true }).waitFor();
  await page.getByRole('button', { name: /^View transaction details:/ }).first().waitFor();
  await page.unroute(summaryNetwork); await page.getByRole('button', { name: 'Try again', exact: true }).click();
  await page.getByRole('button', { name: /^Money received/ }).getByText('4,501.06 PKR', { exact: true }).waitFor();
  for(const period of ['week','month','custom','today']) {await page.getByLabel('Period',{exact:true}).selectOption(period);await page.waitForURL(url=>url.searchParams.get('period')===period);}
  const beforeDetailUrl=page.url();await page.getByRole('button',{name:/^View transaction details:/}).first().click();await page.getByRole('dialog').waitFor();await page.keyboard.press('Escape');await page.getByRole('dialog').waitFor({state:'detached'});assert.equal(page.url(),beforeDetailUrl);
  await page.getByRole('link',{name:'Scan/upload',exact:true}).click();await page.getByRole('link',{name:'Back to Records',exact:true}).waitFor();await page.getByText('Document confirmation updates monthly summaries. Uploaded receipts are not yet linked to transaction activity.',{exact:true}).waitFor();await page.getByRole('link',{name:'Back to Records',exact:true}).click();await page.waitForURL(url=>url.pathname==='/records');await page.waitForFunction(()=>document.activeElement?.id==='records-scan');assert.equal(new URL(page.url()).searchParams.get('accountId'),contextualId);
  await page.setViewportSize({width:390,height:844});await page.getByRole('button',{name:'اردو',exact:true}).click();await page.getByRole('link',{name:'تاریخی خلاصے',exact:true}).waitFor();assert.equal(await page.locator('html').getAttribute('dir'),'rtl');assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await page.screenshot({path:path.join(out,'records-urdu-mobile.png'),fullPage:true});await page.getByRole('button',{name:'English',exact:true}).click();await page.setViewportSize({width:1280,height:900});
  for (const member of [viewer,manager]) {const c=await browser.newContext(),p=await c.newPage();await signin(p,member.email);await p.goto(origin+'/records');await p.getByRole('note').waitFor();assert.equal(await p.getByRole('button',{name:'Add record',exact:true}).count(),0);await c.close();}
  mark('Browser Records: historical access, preserved/cancelled inline account creation, natural/grouped/Urdu HALF_UP amounts, real summary drilldowns, URL periods/details/OCR return and RTL/mobile');
  mark('HTTP Records summary: independent 1500.00 + 1500.50 + 1500.56 = 4501.06 across complete matching dataset; refreshed bank balance 5501.62');
  await verifyRecordDrafts({api,owner,viewer,manager,accountant,page,context,browser,signin,origin,accountId:mainAccount,today,txCounts,blockers,mark,businessA,businessB,requestFactory:request});
  await verifyTransfers({api,owner,viewer,manager,accountant,page,context,browser,signin,origin,today,txCounts,blockers,mark,businessA,businessB,requestFactory:request});
  await verifyReversals({api,owner,viewer,manager,accountant,page,context,browser,signin,origin,today,txCounts,blockers,mark,businessA,businessB,requestFactory:request});
  await control('SNAPSHOT RECORDS', 'LIVE_SNAPSHOT_RECORDS:');assert.equal(runtimeText.match(/LIVE_SNAPSHOT_RECORDS:([^\r\n]+)/)[1],beforeFinancial);
  mark('HTTP/DB: nonempty monthly records, scores, insights and recommendations unchanged by accounts, transactions and Records entry');
  await page.goto(origin + '/finance/accounts');await page.getByText('Live PKR Bank',{exact:true}).waitFor();
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
  const expiryTransactions = await context.newPage(); await expiryTransactions.goto(origin + '/finance/transactions'); let expiryTxFetched = false;
  await expiryTransactions.route(txRoute, async route => { if (!isTransactionPost(route.request())) { await route.continue(); return; } const response = await route.fetch(); expiryTxFetched = true; await new Promise(resolve => setTimeout(resolve, 1500)); try { await route.fulfill({ response }); } catch { /* session expired */ } });
  txForm = await fillTransaction(expiryTransactions, tx(ids.other, '0.01')); await txForm.getByRole('button', { name: 'Add record', exact: true }).click(); await waitFor(() => expiryTxFetched, 'Actual transaction before expiry');
  const expiryDraft=await context.newPage();await expiryDraft.goto(origin+'/records?view=review');await expiryDraft.getByRole('button',{name:'Start an unfinished entry',exact:true}).click();await expiryDraft.getByRole('dialog').getByLabel('Description',{exact:true}).fill('Private draft before expiry');
  await control('EXPIRE', 'LIVE_EXPIRED'); await page.reload(); await page.getByRole('heading', { name: 'Sign in to FinSight' }).waitFor();
  await expiryDraft.reload();await expiryDraft.getByRole('heading',{name:'Sign in to FinSight'}).waitFor();assert.equal(await expiryDraft.getByText('Private draft before expiry',{exact:true}).count(),0);await expiryDraft.close();
  await expiryTransactions.reload(); await expiryTransactions.getByRole('heading', { name: 'Sign in to FinSight' }).waitFor(); await expiryTransactions.close();
  await page.waitForTimeout(1700); await page.unroute(postRoute);
  assert.equal(await page.getByText('UI Stale Session Creation', { exact: true }).count(), 0);
  assert.equal(await page.getByText('Other Business Bank', { exact: true }).count(), 0);
  mark('actual JDBC session removal closes private read view and rejects stale creation success');
  await control('SNAPSHOT FINAL', 'LIVE_SNAPSHOT_FINAL:');
  assert.equal(runtimeText.match(/LIVE_SNAPSHOT_FINAL:([^\r\n]+)/)[1], beforeFinancial);
  await signin(page, viewer.email);
  const outageTransactions = await context.newPage(); await outageTransactions.goto(origin + '/finance/transactions'); await outageTransactions.getByText(notice, { exact: true }).waitFor();
  await control('STOP_SPRING', 'LIVE_STOPPED_SPRING'); await page.reload();
  await outageTransactions.reload(); await outageTransactions.getByRole('alert').first().waitFor(); assert.equal(await outageTransactions.getByText('No transactions match these filters.', { exact: true }).count(), 0); await outageTransactions.close();
  await page.getByRole('alert').first().waitFor(); assert.equal(await page.getByText('No accounts match these filters.', { exact: true }).count(), 0);
  await page.goto(origin + '/records');
  await page.getByText('Unable to verify your session. Retry to continue.', { exact: true }).waitFor();
  assert.equal(await page.getByText('No transactions match these filters.', { exact: true }).count(), 0);
  mark('stopped task-owned Spring produces visible failure, never mock or empty financial data');
  assert.deepEqual(errors, []);
  await context.close();
  }
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
  if (!leaveReview) await stopNext().catch(error => { blockers.push(error.message); process.exitCode = 1; });
  if (!leaveReview && runtime && !exited(runtime)) {
    if (reviewOnly) { writeFileSync(path.join(temporary, 'review.stop'), 'stop'); } else runtime.stdin.write('STOP\n');
    await waitFor(() => exited(runtime), 'Task-owned PostgreSQL/Spring shutdown', 15000).catch(() => { runtime.kill(); process.exitCode = 1; });
  }
  if (!reviewOnly) writeFileSync(path.join(out, draftSyntaxOnly ? 'draft-syntax-results.json' : parityOnly ? (captureOldParity ? 'parity-before-results.json' : 'parity-after-results.json') : demoOnly ? 'demo-results.json' : 'results.json'), JSON.stringify({ checks, blockers, environment: runtimeText.match(/LIVE_ENV:([^\r\n]+)/)?.[1] ?? null, draftBackendClass: runtimeText.match(/LIVE_DRAFT_CLASS:([^\r\n]+)/)?.[1] ?? null, backendClass: runtimeText.match(/LIVE_ACCOUNT_READ_CLASS:([^\r\n]+)/)?.[1] ?? null, integrated: !demoOnly && !parityOnly && !draftSyntaxOnly && blockers.length === 0 }, null, 2));
  if (blockers.length) {
    for (const blocker of blockers) console.error('BLOCKER ' + blocker);
    process.exitCode = 1;
  }
  const resolved = path.resolve(temporary);
  if (!leaveReview && resolved.startsWith(path.resolve(tmpdir()) + path.sep) && path.basename(resolved).startsWith('finsight-accounts-live-')) rmSync(resolved, { recursive: true });
}
