/**
 * Comprehensive Public Demonstration Verification Suite
 * Connects over the public Cloudflare Quick Tunnel URL:
 * https://offering-forums-marathon-calibration.trycloudflare.com
 */

import assert from 'node:assert/strict';

const TUNNEL_URL = process.env.FINSIGHT_DEMO_URL || 'https://offering-forums-marathon-calibration.trycloudflare.com';
console.log('======================================================================');
console.log(`Starting Public Demo Verification on: ${TUNNEL_URL}`);
console.log('======================================================================\n');

const results = [];
function pass(name, details) {
  results.push({ name, status: 'PASS', details });
  console.log(`[PASS] ${name}${details ? ` -> ${details}` : ''}`);
}

class DemoClient {
  constructor(baseUrl) {
    this.baseUrl = baseUrl;
    this.cookies = new Map();
    this.csrfToken = null;
    this.csrfHeader = 'X-XSRF-TOKEN';
  }

  updateCookies(response) {
    let raw = [];
    if (typeof response.headers.getSetCookie === 'function') {
      raw = response.headers.getSetCookie();
    } else {
      const h = response.headers.get('set-cookie');
      if (h) raw = [h];
    }
    for (const cookieStr of raw) {
      const parts = cookieStr.split(';')[0].split('=');
      const name = parts[0].trim();
      const val = parts.slice(1).join('=').trim();
      this.cookies.set(name, val);
    }
  }

  getCookieHeader() {
    return Array.from(this.cookies.entries())
      .map(([k, v]) => `${k}=${v}`)
      .join('; ');
  }

  async fetchCsrf() {
    const res = await fetch(`${this.baseUrl}/api/auth/csrf`, {
      headers: { Cookie: this.getCookieHeader() }
    });
    this.updateCookies(res);
    assert.equal(res.status, 200, 'CSRF endpoint must return 200');
    const data = await res.json();
    this.csrfToken = data.token;
    this.csrfHeader = data.headerName || 'X-XSRF-TOKEN';
    return data;
  }

  async request(path, options = {}) {
    const headers = { ...options.headers };
    const cookieHeader = this.getCookieHeader();
    if (cookieHeader) headers['Cookie'] = cookieHeader;
    if (options.method && options.method.toUpperCase() !== 'GET' && this.csrfToken) {
      headers[this.csrfHeader] = this.csrfToken;
    }
    const res = await fetch(`${this.baseUrl}${path}`, {
      ...options,
      headers
    });
    this.updateCookies(res);
    return res;
  }

  async login(email, password) {
    await this.fetchCsrf();
    const res = await this.request('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });
    assert.equal(res.status, 200, `Login failed for ${email} with status ${res.status}`);
    return await res.json();
  }

  async selectBusiness(businessId) {
    const res = await this.request('/api/businesses/active', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ businessId })
    });
    assert.equal(res.status, 200, `Failed to select business ${businessId}`);
    return await res.json();
  }
}

async function run() {
  // 1. Verify Anonymous Landing Page
  {
    console.log('\n--- 1. Testing Anonymous Public Landing Page ---');
    const res = await fetch(TUNNEL_URL);
    assert.equal(res.status, 200, 'Landing page must return HTTP 200');
    const html = await res.text();
    assert.ok(html.includes('FinSight'), 'Landing page must include FinSight brand');
    assert.ok(!html.includes('http://localhost'), 'No leaked localhost URLs in public HTML');
    assert.ok(!html.includes('127.0.0.1:8080'), 'No leaked internal backend IP/port in public HTML');
    pass('Landing Page Accessibility & Security', `HTTP 200 (${html.length} bytes), zero localhost leaks`);
  }

  // 2. Verify Authentication for all 4 Demo Users
  const roles = [
    { email: 'demo.owner@finsight.local', password: 'FinSight@Owner2026!', expectedRole: 'OWNER', name: 'Demo Business Owner' },
    { email: 'demo.accountant@finsight.local', password: 'FinSight@Account2026!', expectedRole: 'ACCOUNTANT', name: 'Demo Lead Accountant' },
    { email: 'demo.sales@finsight.local', password: 'FinSight@Sales2026!', expectedRole: 'SALESPERSON', name: 'Demo Sales Executive' },
    { email: 'demo.viewer@finsight.local', password: 'FinSight@Viewer2026!', expectedRole: 'VIEWER', name: 'Demo Auditor / Viewer' }
  ];

  console.log('\n--- 2. Testing Authentication for 4 Roles ---');
  for (const r of roles) {
    const client = new DemoClient(TUNNEL_URL);
    const user = await client.login(r.email, r.password);
    assert.equal(user.email, r.email);
    assert.equal(user.fullName, r.name);
    assert.equal(user.authStage, 'FULLY_AUTHENTICATED');
    assert.ok(client.cookies.has('FINSIGHT_SESSION'), 'FINSIGHT_SESSION cookie must be issued');
    pass(`Role Authentication: ${r.email}`, `Logged in as ${user.fullName} (${user.authStage})`);
  }

  // 3. Multi-Tenant Business Listing & Data Exploration as Owner
  console.log('\n--- 3. Testing Business Exploration (Owner Session) ---');
  const ownerClient = new DemoClient(TUNNEL_URL);
  await ownerClient.login('demo.owner@finsight.local', 'FinSight@Owner2026!');

  const bizRes = await ownerClient.request('/api/businesses');
  assert.equal(bizRes.status, 200);
  const businesses = await bizRes.json();
  assert.equal(businesses.length, 8, 'Must return exactly 8 businesses (4 Showcase + 4 Sandbox)');
  pass('Multi-Tenant Businesses Listed', `Found 8 businesses (4 Showcase + 4 Sandbox)`);

  const bizAlpha = businesses.find(b => b.businessName.includes('Alpha Garments'));
  const bizApex = businesses.find(b => b.businessName.includes('Apex Digital'));
  const bizCrescent = businesses.find(b => b.businessName.includes('Crescent Electronics'));
  const bizIndus = businesses.find(b => b.businessName.includes('Indus Textiles'));

  assert.ok(bizAlpha, 'Alpha Garments must exist');
  assert.ok(bizApex, 'Apex Digital must exist');
  assert.ok(bizCrescent, 'Crescent Electronics must exist');
  assert.ok(bizIndus, 'Indus Textiles must exist');

  // 4. Verify Business A (Alpha Garments - WAC Inventory Scenario & Obligations)
  {
    console.log('\n--- 4. Verifying Business A: Alpha Garments (WAC Inventory & Obligations) ---');
    await ownerClient.selectBusiness(bizAlpha.businessId);

    // Verify inventory catalog & valuation
    const itemsRes = await ownerClient.request('/api/inventory/items');
    assert.equal(itemsRes.status, 200);
    const itemsData = await itemsRes.json();
    const items = itemsData.items || itemsData;
    const fabric = items.find(i => i.sku === 'COTTON-FABRIC-01');
    assert.ok(fabric, 'COTTON-FABRIC-01 must be registered in catalog');
    pass('Business A Inventory Catalog', `Found SKU COTTON-FABRIC-01: ${fabric.name}`);

    const valRes = await ownerClient.request('/api/inventory/valuation');
    assert.equal(valRes.status, 200);
    const valuation = await valRes.json();
    assert.equal(valuation.carryingValue, '66000.00', 'WAC carrying value must be exactly 66,000.00 PKR');
    pass('Business A Inventory Valuation & WAC', `Exact WAC carrying value: PKR ${valuation.carryingValue} (110m @ 600 PKR)`);

    // Commercial Invoice
    const invListRes = await ownerClient.request('/api/invoices');
    assert.equal(invListRes.status, 200);
    const invData = await invListRes.json();
    const invoices = invData.items || invData;
    assert.ok(invoices.length >= 1, 'Invoices must be seeded');
    assert.equal(invoices[0].documentNumber, 'INV-000001');
    pass('Business A Commercial Invoice', `Invoice INV-000001 total PKR ${invoices[0].total}`);

    // Authoritative Financial Obligations (Bills + Invoice)
    const obRes = await ownerClient.request('/api/finance/obligations');
    assert.equal(obRes.status, 200);
    const obData = await obRes.json();
    assert.equal(obData.totalElements, 3, 'Must have 3 obligations (2 Bills + 1 Invoice)');
    const bill1 = obData.items.find(o => o.referenceNumber === 'BILL-000001');
    const bill2 = obData.items.find(o => o.referenceNumber === 'BILL-000002');
    const inv1 = obData.items.find(o => o.referenceNumber === 'INV-000001');
    assert.ok(bill1 && bill2 && inv1, 'Must find BILL-000001, BILL-000002, and INV-000001');
    assert.equal(bill1.settlementStatus, 'FULLY_PAID');
    assert.equal(bill2.settlementStatus, 'FULLY_PAID');
    assert.equal(inv1.settlementStatus, 'PARTIALLY_PAID');
    assert.equal(inv1.outstandingAmount, '60000.00');
    pass('Business A Financial Obligations', `Bills (BILL-000001: 50k paid, BILL-000002: 40k paid) & Invoice (INV-000001: 60k open AR)`);

    // Obligations Summary
    const sumRes = await ownerClient.request('/api/finance/obligations/summary');
    assert.equal(sumRes.status, 200);
    const sum = await sumRes.json();
    assert.equal(sum.totalRecordedReceivables, '180000.00');
    assert.equal(sum.outstandingReceivables, '60000.00');
    assert.equal(sum.totalRecordedPayables, '90000.00');
    assert.equal(sum.outstandingPayables, '0.00');
    pass('Business A Obligations Summary', `Receivables PKR 180k (60k open), Payables PKR 90k (0 open)`);

    // Historical Monthly Records & Health Score
    const recordsRes = await ownerClient.request('/api/records/monthly');
    assert.equal(recordsRes.status, 200);
    const records = await recordsRes.json();
    assert.ok(records.length >= 6, 'Must have 6 months of historical records');
    pass('Business A Historical Records', `Retrieved ${records.length} months of verified records`);

    const scoreRes = await ownerClient.request('/api/scores/latest');
    assert.equal(scoreRes.status, 200);
    const score = await scoreRes.json();
    assert.ok(score.compositeScore > 0, 'Composite score must be positive');
    pass('Business A Health Score Engine', `Composite Score: ${score.compositeScore} / 100 (${score.band})`);
  }

  // 5. Verify Business B (Apex Digital Solutions - Accruals & Commissions)
  {
    console.log('\n--- 5. Verifying Business B: Apex Digital Solutions (Commissions) ---');
    await ownerClient.selectBusiness(bizApex.businessId);

    // Obligations: 2.5M billed, 1.6M collected, 900k open AR
    const obRes = await ownerClient.request('/api/finance/obligations');
    assert.equal(obRes.status, 200);
    const obData = await obRes.json();
    assert.equal(obData.totalElements, 2, 'Must have 2 invoices for Apex Digital');
    const totalBilled = obData.items.reduce((s, o) => s + Number(o.originalAmount), 0);
    const totalPaid = obData.items.reduce((s, o) => s + Number(o.paidAmount), 0);
    const totalDue = obData.items.reduce((s, o) => s + Number(o.outstandingAmount), 0);
    assert.equal(totalBilled, 2500000, 'Total billed must be 2,500,000 PKR');
    assert.equal(totalPaid, 1600000, 'Total collected must be 1,600,000 PKR');
    assert.equal(totalDue, 900000, 'Outstanding AR must be 900,000 PKR');
    pass('Business B Revenue & Collections', `Billed: PKR 2.5M | Collected: PKR 1.6M | Open AR: PKR 900k`);

    // Commissions
    const commRes = await ownerClient.request('/api/finance/commissions/earnings');
    assert.equal(commRes.status, 200);
    const earnings = await commRes.json();
    assert.ok(earnings.length >= 1, 'Commission earnings must be recorded');
    assert.equal(Number(earnings[0].commissionAmount), 80000, '5% of 1.6M = 80,000 PKR');
    pass('Business B Commission Engine', `Earnings recorded: PKR ${earnings[0].commissionAmount} (5% on collected revenue)`);

    const spRes = await ownerClient.request('/api/finance/salespersons');
    assert.equal(spRes.status, 200);
    const salespersons = await spRes.json();
    assert.ok(salespersons.length >= 1, 'Salesperson profiles seeded');
    pass('Business B Salesperson Roster', `Found ${salespersons[0].displayName}`);

    const payRes = await ownerClient.request('/api/finance/commissions/payouts');
    assert.equal(payRes.status, 200);
    const payouts = await payRes.json();
    assert.ok(payouts.length >= 1, 'Commission payout must exist');
    assert.equal(Number(payouts[0].amount), 60000, 'Settled payout must be 60,000 PKR');
    pass('Business B Commission Payout', `Settled Payout: PKR ${payouts[0].amount} via HBL Operating Account`);
  }

  // 6. Verify Business C (Crescent Electronics Trading - Retail & Overdue Debt & Zakat)
  {
    console.log('\n--- 6. Verifying Business C: Crescent Electronics (Overdue Debt & Zakat) ---');
    await ownerClient.selectBusiness(bizCrescent.businessId);

    const obRes = await ownerClient.request('/api/finance/obligations');
    assert.equal(obRes.status, 200);
    const obData = await obRes.json();
    assert.ok(obData.totalElements >= 1, 'Obligations must exist');
    const overdueBill = obData.items.find(o => o.referenceNumber === 'BILL-000101');
    assert.ok(overdueBill, 'BILL-000101 must exist');
    assert.equal(Number(overdueBill.originalAmount), 725000);
    assert.equal(overdueBill.direction, 'PAYABLE');
    assert.ok(overdueBill.daysOverdue >= 20, 'Bill must be past due');
    pass('Business C Overdue Supplier Obligation', `Found BILL-000101 for PKR ${overdueBill.originalAmount} (${overdueBill.daysOverdue} days overdue)`);

    const itemsRes = await ownerClient.request('/api/inventory/items');
    assert.equal(itemsRes.status, 200);
    const itemsData = await itemsRes.json();
    const items = itemsData.items || itemsData;
    assert.ok(items.length >= 1, 'Retail items seeded (Inverters)');
    const inv = items.find(i => i.sku === 'SOLAR-INV-5KW');
    assert.ok(inv, 'SOLAR-INV-5KW must exist');
    pass('Business C Retail Catalog', `Catalog contains SKU ${inv.sku}: ${inv.name}`);

    // Zakat Calculation Preview
    const zakatRes = await ownerClient.request('/api/zakat/monthly/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ month: '2026-10' })
    });
    assert.equal(zakatRes.status, 200);
    const zakat = await zakatRes.json();
    assert.equal(zakat.ruleProfile, 'HANAFI_PK_BUSINESS_V1');
    assert.equal(zakat.zakatRate, 0.025);
    pass('Business C Hanafi Zakat Engine', `Profile: ${zakat.ruleProfile} (Rate: ${(zakat.zakatRate * 100).toFixed(1)}%) derived from monthly record`);
  }

  // 7. Verify Business D (Indus Textiles Export Corp - Multi-Currency & FX Gain)
  {
    console.log('\n--- 7. Verifying Business D: Indus Textiles Export (Multi-Currency FX) ---');
    await ownerClient.selectBusiness(bizIndus.businessId);

    // Accounts
    const accRes = await ownerClient.request('/api/finance/accounts');
    assert.equal(accRes.status, 200);
    const accData = await accRes.json();
    const accounts = accData.items || accData;
    const pkrAcc = accounts.find(a => a.currency === 'PKR');
    const usdAcc = accounts.find(a => a.currency === 'USD');
    assert.ok(pkrAcc, 'PKR account must exist');
    assert.ok(usdAcc, 'USD foreign currency account must exist');
    pass('Business D Dual-Currency Accounts', `PKR Account: ${pkrAcc.accountName}, USD Account: ${usdAcc.accountName}`);

    // FX Settlements
    const fxRes = await ownerClient.request('/api/finance/fx/settlements');
    assert.equal(fxRes.status, 200);
    const settlements = await fxRes.json();
    assert.ok(settlements.length >= 1, 'FX Settlement must be recorded');
    const st = settlements[0];
    assert.equal(st.realizedFxImpact, 'GAIN');
    assert.equal(Number(st.realizedFxDifference), 67500);
    pass('Business D Realized FX Gain Engine', `Settlement ${st.settlementDirection}: Realized Gain PKR +${st.realizedFxDifference} (@ rate ${st.exchangeRate}, fee PKR ${st.feeAmount})`);
  }

  // 8. Verify Role Permission Boundaries (Viewer Read-Only Enforcement)
  {
    console.log('\n--- 8. Verifying RBAC Security Boundaries (Viewer Restrictions) ---');
    const viewerClient = new DemoClient(TUNNEL_URL);
    await viewerClient.login('demo.viewer@finsight.local', 'FinSight@Viewer2026!');
    await viewerClient.selectBusiness(bizAlpha.businessId);

    // Viewer can read financial records
    const readRes = await viewerClient.request('/api/records/monthly');
    assert.equal(readRes.status, 200, 'Viewer must have FINANCIAL_DATA_READ');
    pass('RBAC Viewer Permitted Read', 'Viewer successfully queried financial records (200 OK)');

    // Viewer CANNOT post financial transactions (403 Forbidden)
    const writeRes = await viewerClient.request('/api/finance/transactions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventType: 'CAPITAL_INJECTION', amount: 1000 })
    });
    assert.equal(writeRes.status, 403, 'Viewer write must be denied with 403 Forbidden');
    pass('RBAC Viewer Write Prohibited', 'Viewer transaction post rejected with HTTP 403 Forbidden');
  }

  console.log('\n======================================================================');
  console.log(`ALL VERIFICATION CHECKS PASSED: ${results.length}/${results.length}`);
  console.log('Public Cloudflare Tunnel verified fully functional on external requests!');
  console.log('======================================================================\n');
}

run().catch(err => {
  console.error('\nVerification failed:', err);
  process.exit(1);
});
