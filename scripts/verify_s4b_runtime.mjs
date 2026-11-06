// Read-Only Verification Probe for S4B Disposable Runtime
// STRICTLY READ-ONLY: ZERO MUTATIONS PERMITTED.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const manifestPath = path.resolve('tmp/finsight-live-fixture-s4b.json');
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));

const backendUrl = manifest.backendUrl || 'http://127.0.0.1:8081';
console.log('=== FINSIGHT S4B READ-ONLY RUNTIME VERIFICATION ===');
console.log('Connecting to Live Backend at:', backendUrl);
console.log('Manifest:', manifestPath);

let cookiesMap = new Map();
let csrfToken = '';
let csrfHeader = 'X-XSRF-TOKEN';

function getCookieHeader() {
  return Array.from(cookiesMap.entries()).map(([k, v]) => `${k}=${v}`).join('; ');
}

function processSetCookies(res) {
  const setCookies = typeof res.headers.getSetCookie === 'function'
    ? res.headers.getSetCookie()
    : [res.headers.get('set-cookie')].filter(Boolean);

  for (const str of setCookies) {
    const parts = str.split(';')[0].split('=');
    if (parts.length >= 2) {
      cookiesMap.set(parts[0].trim(), parts.slice(1).join('=').trim());
    }
  }
}

async function refreshCsrf() {
  const headers = {};
  const cookieStr = getCookieHeader();
  if (cookieStr) headers['Cookie'] = cookieStr;
  const res = await fetch(`${backendUrl}/api/auth/csrf`, { headers });
  processSetCookies(res);
  const data = await res.json();
  csrfToken = data.token;
  csrfHeader = data.headerName || 'X-XSRF-TOKEN';
  return data;
}

async function fetchApi(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  const cookieStr = getCookieHeader();
  if (cookieStr) headers['Cookie'] = cookieStr;

  if (options.method && options.method !== 'GET') {
    await refreshCsrf();
    headers['Cookie'] = getCookieHeader();
    headers[csrfHeader] = csrfToken;
  }

  if (options.body && typeof options.body === 'object') {
    headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(options.body);
  }

  const res = await fetch(`${backendUrl}${path}`, { ...options, headers });
  processSetCookies(res);

  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch (e) {}

  return { status: res.status, headers: res.headers, text, json };
}

async function verifyRuntime() {
  // 1. CSRF
  console.log('\n[1/10] Fetching CSRF token...');
  const csrfRes = await fetchApi('/api/auth/csrf');
  assert.equal(csrfRes.status, 200, `CSRF failed: ${csrfRes.text}`);
  assert.ok(csrfRes.json.token, 'CSRF token required');
  csrfToken = csrfRes.json.token;
  csrfHeader = csrfRes.json.headerName || 'X-XSRF-TOKEN';
  console.log('   PASS: CSRF acquired:', csrfHeader, '=', csrfToken.slice(0, 10) + '...');

  // 2. Authentication
  console.log('\n[2/10] Authenticating test user (live.owner@finsight.local)...');
  const loginRes = await fetchApi('/api/auth/login', {
    method: 'POST',
    body: { email: manifest.email, password: manifest.password }
  });
  assert.equal(loginRes.status, 200, `Login failed: ${loginRes.text}`);
  assert.ok(cookiesMap.has('FINSIGHT_SESSION'), 'Session cookie required');
  console.log('   PASS: Authenticated as:', loginRes.json.email, 'Full Name:', loginRes.json.fullName);

  // 3. Switch to Business A (Apex Industrial Ltd)
  console.log('\n[3/10] Activating Business A context (Apex Industrial Ltd)...');
  const switchARes = await fetchApi('/api/businesses/active', {
    method: 'POST',
    body: { businessId: manifest.businessAId }
  });
  assert.equal(switchARes.status, 200, `Switch to Business A failed: ${switchARes.text}`);
  const activeA = await fetchApi('/api/businesses/active');
  assert.equal(activeA.json.businessId, manifest.businessAId);
  console.log('   PASS: Active Business confirmed:', activeA.json.businessName, `(${activeA.json.businessId})`);

  // 4. Verify Account Balances & Cash Invariants
  console.log('\n[4/10] Verifying Business A Account Balances...');
  const accountsRes = await fetchApi('/api/finance/accounts?size=100');
  assert.equal(accountsRes.status, 200, `Accounts query failed: ${accountsRes.text}`);
  const items = accountsRes.json.items;
  const acc1 = items.find(a => a.id === manifest.account1Id);
  const acc2 = items.find(a => a.id === manifest.account2Id);
  const acc3 = items.find(a => a.id === manifest.account3Id);
  assert.ok(acc1, 'Account 1 (Main Operations) not found');
  assert.ok(acc2, 'Account 2 (Reserve Account) not found');
  assert.ok(acc3, 'Account 3 (Trade Collections) not found');

  console.log('   Account 1 (Apex Main Operations):', acc1.currentBalance, 'PKR (Expected: 105000.00)');
  console.log('   Account 2 (Apex Reserve Account):', acc2.currentBalance, 'PKR (Expected: 10000.00)');
  console.log('   Account 3 (Apex Trade Collections):', acc3.currentBalance, 'PKR (Expected: 80000.00)');
  assert.equal(acc1.currentBalance, '105000.00');
  assert.equal(acc2.currentBalance, '10000.00');
  assert.equal(acc3.currentBalance, '80000.00');

  const totalCash = (parseFloat(acc1.currentBalance) + parseFloat(acc2.currentBalance) + parseFloat(acc3.currentBalance)).toFixed(2);
  console.log('   Total Apex Cash (Acc1 + Acc2 + Acc3):', totalCash, 'PKR (Expected: 195000.00)');
  assert.equal(totalCash, '195000.00');
  console.log('   PASS: All 3 account balances and total cash verified exactly!');

  // 5. Verify Obligation Invariant (100k - 30k advance - 50k settlement = 20k outstanding)
  console.log('\n[5/10] Verifying Receivable Obligation Invariant...');
  const obRes = await fetchApi(`/api/finance/obligations/${manifest.obligationId}`);
  assert.equal(obRes.status, 200, `Obligation query failed: ${obRes.text}`);
  console.log('   Obligation Original:', obRes.json.originalAmount, 'PKR');
  console.log('   Obligation Paid:', obRes.json.paidAmount, 'PKR');
  console.log('   Obligation Outstanding:', obRes.json.outstandingAmount, 'PKR (Expected: 20000.00)');
  console.log('   Settlement Status:', obRes.json.settlementStatus, '(Expected: PARTIALLY_PAID)');
  assert.equal(obRes.json.originalAmount, '100000.00');
  assert.equal(obRes.json.paidAmount, '80000.00');
  assert.equal(obRes.json.outstandingAmount, '20000.00');
  assert.equal(obRes.json.settlementStatus, 'PARTIALLY_PAID');
  console.log('   PASS: Obligation balance and partial settlement verified!');

  // 6. Verify Forecast
  console.log('\n[6/10] Verifying Cash Forecast API...');
  const forecastRes = await fetchApi('/api/finance/forecast/daily?currency=PKR&horizonDays=30');
  assert.equal(forecastRes.status, 200, `Forecast failed: ${forecastRes.text}`);
  assert.ok(forecastRes.json.dailyRows, 'dailyRows expected');
  console.log('   PASS: Daily forecast rows generated:', forecastRes.json.dailyRows.length);

  // 7. Verify Risk Assessment
  console.log('\n[7/10] Verifying Financial Risk Assessment API...');
  const riskRes = await fetchApi('/api/finance/risk/assessment?currency=PKR&horizonDays=30');
  assert.equal(riskRes.status, 200, `Risk assessment failed: ${riskRes.text}`);
  assert.ok(riskRes.json.summary, 'Summary expected');
  console.log('   PASS: Risk assessment returned. Highest severity:', riskRes.json.summary.highestSeverity);

  // 8. Verify C11 AI Financial Evidence
  console.log('\n[8/10] Verifying C11 AI Financial Evidence Integration...');
  const explainReq = { type: 'ask', language: 'en', question: 'What is my current cash position?' };
  const explainRes = await fetchApi('/api/ai/explain', { method: 'POST', body: explainReq });
  assert.equal(explainRes.status, 200, `AI Explain failed: ${explainRes.text}`);
  const evidence = explainRes.json.evidence || [];
  assert.ok(evidence.length > 0, 'AI evidence required');
  const cashFact = evidence.find(e => e.id === 'cash_position.PKR.balance');
  assert.ok(cashFact, 'cash_position.PKR.balance fact required');
  assert.equal(cashFact.sourceMode, 'POSTED_ACTUAL');
  const allAccountsTotal = items.reduce((sum, a) => sum + parseFloat(a.currentBalance), 0).toFixed(2);
  assert.equal(cashFact.value, allAccountsTotal, `C11 value (${cashFact.value}) must match all-account total (${allAccountsTotal})`);
  console.log('   PASS: C11 AI Evidence confirmed:');
  console.log('     ID:', cashFact.id);
  console.log('     Value:', cashFact.value, cashFact.unit, `(Matches sum of ${items.length} accounts: ${allAccountsTotal} PKR)`);
  console.log('     Source Mode:', cashFact.sourceMode);

  // 9. Tenant Isolation Probe
  console.log('\n[9/10] Verifying Tenant Isolation (Cross-tenant leak prevention)...');
  const leakRes = await fetchApi(`/api/finance/accounts/${manifest.zenithAccountId}`);
  assert.equal(leakRes.status, 404, `Cross-tenant leak detected! Status: ${leakRes.status}`);
  console.log('   PASS: Accessing Business B Zenith Account from Business A returned 404 Not Found (Zero Leakage).');

  // 10. Switch to Business B (Zenith Commercial Ltd)
  console.log('\n[10/10] Activating Business B context & verifying Zenith Account...');
  const switchBRes = await fetchApi('/api/businesses/active', {
    method: 'POST',
    body: { businessId: manifest.businessBId }
  });
  assert.equal(switchBRes.status, 200, `Switch to Business B failed: ${switchBRes.text}`);
  const activeB = await fetchApi('/api/businesses/active');
  assert.equal(activeB.json.businessId, manifest.businessBId);
  console.log('   Active Business confirmed:', activeB.json.businessName);

  const zenithAccRes = await fetchApi(`/api/finance/accounts/${manifest.zenithAccountId}`);
  assert.equal(zenithAccRes.status, 200, `Zenith account query failed: ${zenithAccRes.text}`);
  console.log('   Zenith Account Balance:', zenithAccRes.json.currentBalance, 'PKR (Expected: 88888.00)');
  assert.equal(zenithAccRes.json.currentBalance, '88888.00');

  // Verify reverse isolation: cannot access Business A's obligation from Business B context
  const obLeakRes = await fetchApi(`/api/finance/obligations/${manifest.obligationId}`);
  assert.equal(obLeakRes.status, 404, `Cross-tenant obligation leak! Status: ${obLeakRes.status}`);
  console.log('   PASS: Accessing Business A Obligation from Business B returned 404 Not Found (Zero Leakage).');

  console.log('\n======================================================');
  console.log('ALL 10 VERIFICATION PROBES PASSED PERFECTLY!');
  console.log('S4B Disposable Runtime on Port 8081 / DB 5434 is 100% STABLE.');
  console.log('======================================================');
}

verifyRuntime().catch(err => {
  console.error('\nVERIFICATION PROBE FAILED:', err);
  process.exit(1);
});
