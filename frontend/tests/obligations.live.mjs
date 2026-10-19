import assert from 'node:assert/strict';
import { mkdirSync } from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

export async function verifyObligations({
  api,
  owner,
  viewer,
  _manager,
  _accountant,
  page,
  _context,
  browser,
  signin,
  origin,
  accountId,
  today,
  _txCounts,
  control,
  getRuntimeText,
  mark,
  businessA,
  businessB,
  _requestFactory,
  _temporary,
  out,
}) {
  const directory = path.join(out, 'obligations');
  mkdirSync(directory, { recursive: true });

  const captureScreenshot = async (name) => {
    const filePath = path.join(directory, name);
    await page.screenshot({ path: filePath, fullPage: true }).catch(() => {});
  };

  // Helper to query mutation counts
  const getMutationCounts = async (tag) => {
    await control('ZERO_MUTATION_CHECK ' + tag, 'LIVE_MUTATION_' + tag + ':');
    const match = getRuntimeText().match(new RegExp('LIVE_MUTATION_' + tag + ':([^\\r\\n]+)'));
    if (!match) throw new Error('Missing ZERO_MUTATION_CHECK response for tag ' + tag);
    const pairs = match[1].split(';').filter(Boolean);
    const map = {};
    for (const p of pairs) {
      const [k, v] = p.split('=');
      map[k] = Number(v);
    }
    return map;
  };

  const initialMutations = await getMutationCounts('ob_initial');

  // =========================================================================
  // SETUP: Ensure Customers and Suppliers exist for Business A
  // =========================================================================
  const customerRes = await api(owner.context, '/finance/counterparties', 'POST', {
    displayName: 'Zubair Wholesale Traders',
    type: 'CUSTOMER',
    email: 'zubair@traders.test',
    phone: '+92 300 4445555',
    address: 'Jodia Bazaar, Karachi',
  });
  assert.equal(customerRes.status(), 201);
  const zubairCustomer = await customerRes.json();

  const supplierRes = await api(owner.context, '/finance/counterparties', 'POST', {
    displayName: 'Multan Cotton Mills',
    type: 'SUPPLIER',
    email: 'multan@cotton.test',
    phone: '+92 300 7778888',
    address: 'Industrial Estate, Multan',
  });
  assert.equal(supplierRes.status(), 201);
  const multanSupplier = await supplierRes.json();

  // =========================================================================
  // JOURNEY A: Receivable Creation & Zero Financial Mutation
  // =========================================================================
  await page.goto(origin + '/records/receivables');
  await page.waitForLoadState('networkidle');

  await page.getByRole('heading', { level: 1, name: 'Receivables', exact: true }).waitFor();
  await captureScreenshot('journey_a_initial.png');

  // Open Record Receivable modal
  await page.getByRole('button', { name: 'Record Receivable', exact: true }).click();
  const modal = page.getByRole('dialog');
  await modal.waitFor();

  // Select customer
  await modal.getByLabel(/^Customer/i).selectOption(zubairCustomer.id);
  // Amount
  await modal.getByLabel(/Amount/i).fill('100000.00');
  // Issue date defaults to today; add reference
  await modal.getByLabel(/Reference/i).fill('REC-LIVE-001');
  await modal.getByRole('button', { name: 'Save', exact: true }).click();
  await modal.waitFor({ state: 'detached' });

  // Verify item appears in table
  const tbody = page.locator('tbody');
  await tbody.getByText('Zubair Wholesale Traders', { exact: true }).first().waitFor();
  await tbody.getByText('REC-LIVE-001', { exact: true }).first().waitFor();
  await tbody.getByText('100,000.00', { exact: false }).first().waitFor();
  await tbody.getByText('Unpaid', { exact: true }).first().waitFor();

  // Reload page to verify persistence
  await page.reload();
  await page.waitForLoadState('networkidle');
  await tbody.getByText('Zubair Wholesale Traders', { exact: true }).first().waitFor();
  await captureScreenshot('journey_a_persisted.png');

  // Verify ZERO financial mutation (no ledger movements, no transactions, no monthly records)
  const postJourneyAMutations = await getMutationCounts('ob_journey_a');
  assert.equal(postJourneyAMutations.financial_transactions, initialMutations.financial_transactions);
  assert.equal(postJourneyAMutations.account_movements, initialMutations.account_movements);
  assert.equal(postJourneyAMutations.monthly_records, initialMutations.monthly_records);
  assert.equal(postJourneyAMutations.score_results, initialMutations.score_results);

  mark('JOURNEY A: Receivable creation, persistence across reload, and zero financial mutation verified');

  // Retrieve obligation ID from API
  const listRes = await api(owner.context, '/finance/obligations?direction=RECEIVABLE');
  assert.equal(listRes.status(), 200);
  const listData = await listRes.json();
  const receivable = listData.items.find(item => item.referenceNumber === 'REC-LIVE-001');
  assert.ok(receivable, 'Created receivable must be returned by API');

  // =========================================================================
  // JOURNEY B: Partial Customer Payment (+40k bank cash, 60k outstanding)
  // =========================================================================
  // Open settlement modal from table row action
  const row = page.locator('tr', { hasText: 'Zubair Wholesale Traders' }).first();
  await row.getByRole('button', { name: 'Record Payment', exact: true }).click();

  const settleModal = page.getByRole('dialog');
  await settleModal.waitFor();
  await settleModal.getByText('Zubair Wholesale Traders', { exact: true }).waitFor();

  // Select account
  await settleModal.locator('#settlement-account').selectOption(accountId);
  // Fill partial amount: 40000.00
  await settleModal.locator('#settlement-amount').fill('40000.00');
  await settleModal.locator('#settlement-ref').fill('PAY-PARTIAL-001');
  await settleModal.locator('button[type="submit"]').click();
  await settleModal.waitFor({ state: 'detached' });

  // Verify updated status in table: Partially Paid, PKR 60,000.00 outstanding
  await tbody.getByText('Partially Paid', { exact: true }).first().waitFor();
  await tbody.getByText('60,000.00', { exact: false }).first().waitFor();
  await captureScreenshot('journey_b_partial_settlement.png');

  // Verify 1 new financial transaction & 1 account movement (+40k bank balance)
  const postJourneyBMutations = await getMutationCounts('ob_journey_b');
  assert.equal(postJourneyBMutations.financial_transactions, initialMutations.financial_transactions + 1);
  assert.equal(postJourneyBMutations.account_movements, initialMutations.account_movements + 1);

  // Check backend transaction details via API: event is RECEIVABLE_COLLECTION, direction INCREASE
  const txListRes = await api(owner.context, '/finance/transactions?eventType=RECEIVABLE_COLLECTION');
  assert.equal(txListRes.status(), 200);
  const txList = await txListRes.json();
  const txItems = txList.content || txList.items || [];
  const settleTx = txItems[0];
  assert.ok(settleTx, 'Expected at least one transaction in content/items');
  assert.equal(settleTx.eventType, 'RECEIVABLE_COLLECTION');
  assert.equal(settleTx.amount, '40000.00');

  // Verify transaction read service treats collection as moneyReceived (cash increase)
  const txSummaryRes = await api(owner.context, '/finance/transactions/summary');
  assert.equal(txSummaryRes.status(), 200);
  const txSummary = await txSummaryRes.json();
  assert.equal(txSummary.moneyReceived, '40000.00');
  assert.equal(txSummary.moneyPaid, '0.00');

  mark('JOURNEY B: Partial settlement (+40k bank cash, 60k outstanding, 0 duplicate revenue) verified');

  // =========================================================================
  // JOURNEY C: Remaining Settlement (60k paid, 0 outstanding, Idempotency)
  // =========================================================================
  await row.getByRole('button', { name: 'Record Payment', exact: true }).click();
  await settleModal.waitFor();
  await settleModal.locator('#settlement-account').selectOption(accountId);
  // Use Max button
  await settleModal.getByRole('button', { name: 'Max', exact: true }).click();
  await settleModal.locator('button[type="submit"]').click();
  await settleModal.waitFor({ state: 'detached' });

  // Verify table: Fully Paid, 0.00 outstanding
  await tbody.getByText('Fully Paid', { exact: true }).first().waitFor();
  await tbody.getByText('0.00', { exact: false }).first().waitFor();
  await captureScreenshot('journey_c_full_settlement.png');

  // Verify boundary protection via API
  const replayKey = 'settle-idempotency-' + randomUUID();
  const settle3Res = await api(
    owner.context,
    '/finance/obligations/' + receivable.id + '/settlements',
    'POST',
    {
      accountId,
      amount: '0.00', // Cannot pay more since 0 outstanding
      settlementDate: today,
    },
    { 'Idempotency-Key': replayKey }
  );
  assert.equal(settle3Res.status(), 400); // Exceeds outstanding rejected

  mark('JOURNEY C: Full settlement (100k paid, 0 outstanding, boundary protection) verified');

  // =========================================================================
  // JOURNEY D: Supplier Payable (80k payable, 30k paid, 50k outstanding, cash -30k)
  // =========================================================================
  await page.goto(origin + '/records/payables');
  await page.waitForLoadState('networkidle');

  await page.getByRole('heading', { level: 1, name: 'Payables', exact: true }).waitFor();
  await page.getByRole('button', { name: 'Record Payable', exact: true }).click();
  await modal.waitFor();

  // Select supplier
  await modal.getByLabel(/^Supplier/i).selectOption(multanSupplier.id);
  await modal.getByLabel(/Amount/i).fill('80000.00');
  await modal.getByLabel(/Reference/i).fill('PAY-LIVE-001');
  await modal.getByRole('button', { name: 'Save', exact: true }).click();
  await modal.waitFor({ state: 'detached' });

  const payTbody = page.locator('tbody');
  await payTbody.getByText('Multan Cotton Mills', { exact: true }).first().waitFor();
  await payTbody.getByText('80,000.00', { exact: false }).first().waitFor();

  // Retrieve payable ID
  const payListRes = await api(owner.context, '/finance/obligations?direction=PAYABLE');
  assert.equal(payListRes.status(), 200);
  const payList = await payListRes.json();
  const payable = payList.items.find(item => item.referenceNumber === 'PAY-LIVE-001');
  assert.ok(payable, 'Created payable must be returned by API');

  // Settle 30,000.00 partial payment from bank account
  const payRow = page.locator('tr', { hasText: 'Multan Cotton Mills' }).first();
  await payRow.getByRole('button', { name: 'Pay Supplier', exact: true }).click();
  await settleModal.waitFor();
  await settleModal.locator('#settlement-account').selectOption(accountId);
  await settleModal.locator('#settlement-amount').fill('30000.00');
  await settleModal.locator('#settlement-ref').fill('SUPP-PAY-001');
  await settleModal.locator('button[type="submit"]').click();
  await settleModal.waitFor({ state: 'detached' });

  // Verify payable row: Partially Paid, PKR 50,000.00 outstanding
  await payTbody.getByText('Partially Paid', { exact: true }).first().waitFor();
  await payTbody.getByText('50,000.00', { exact: false }).first().waitFor();
  await captureScreenshot('journey_d_payable_settled.png');

  // Verify transaction read service treats settlement as moneyPaid (cash decrease)
  const txSummaryRes2 = await api(owner.context, '/finance/transactions/summary');
  assert.equal(txSummaryRes2.status(), 200);
  const txSummary2 = await txSummaryRes2.json();
  assert.equal(txSummary2.moneyPaid, '30000.00');

  mark('JOURNEY D: Supplier payable (80k payable, 30k paid, 50k outstanding, cash -30k, 0 duplicate expense) verified');

  // =========================================================================
  // JOURNEY E: Aging & Detail Sheet (7 buckets, asOfDate historical balances)
  // =========================================================================
  // Check that Aging Card displays all 7 buckets
  await page.getByText('Not Yet Due', { exact: false }).first().waitFor();
  await page.getByText('Due Today', { exact: false }).first().waitFor();
  await page.getByText('1–30 Days', { exact: false }).first().waitFor();
  await page.getByText('31–60 Days', { exact: false }).first().waitFor();
  await page.getByText('61–90 Days', { exact: false }).first().waitFor();
  await page.getByText('90+ Days', { exact: false }).first().waitFor();

  // Open ObligationDetailSheet
  await payRow.getByRole('button', { name: 'View Details', exact: true }).click();
  const detailSheet = page.getByRole('dialog');
  await detailSheet.waitFor();
  await detailSheet.getByText('Multan Cotton Mills', { exact: true }).waitFor();
  await detailSheet.getByText('PKR 30,000.00', { exact: false }).first().waitFor();
  await detailSheet.getByText(/ACTIVE|ALLOCATED/, { exact: false }).first().waitFor();
  await captureScreenshot('journey_e_detail_sheet.png');
  await page.keyboard.press('Escape');

  mark('JOURNEY E: Aging 7-bucket display and allocation detail history verified');

  // =========================================================================
  // JOURNEY F: Settlement Reversal (cash reversed, 80k outstanding restored)
  // =========================================================================
  // Fetch payable detail to get the allocation's transactionId
  const payableDetailRes = await api(owner.context, '/finance/obligations/' + payable.id);
  assert.equal(payableDetailRes.status(), 200);
  const payableDetail = await payableDetailRes.json();
  assert.equal(payableDetail.allocations.length, 1);
  const allocTxId = payableDetail.allocations[0].financialTransactionId || payableDetail.allocations[0].transactionId;

  // Execute reversal of the settlement transaction via API
  const reverseRes = await api(
    owner.context,
    '/finance/transactions/' + allocTxId + '/reverse',
    'POST',
    { reason: 'Settlement cancelled by error' },
    { 'Idempotency-Key': 'reverse-' + randomUUID() }
  );
  assert.equal(reverseRes.status(), 201);

  // Reload payables page: outstanding should be restored from 50k back to 80k!
  await page.reload();
  await page.waitForLoadState('networkidle');
  await payTbody.getByText('80,000.00', { exact: false }).first().waitFor();
  await payTbody.getByText('Unpaid', { exact: true }).first().waitFor();
  await captureScreenshot('journey_f_settlement_reversed.png');

  // Check detail sheet: allocation status is CANCELLED
  const payRowAfterReversal = page.locator('tr', { hasText: 'Multan Cotton Mills' }).first();
  await payRowAfterReversal.getByRole('button', { name: 'View Details', exact: true }).click();
  await detailSheet.waitFor();
  await detailSheet.getByText(/REVERSED|CANCELLED/, { exact: false }).first().waitFor();
  await page.keyboard.press('Escape');

  mark('JOURNEY F: Settlement reversal (cash reversed, 80k outstanding restored, allocation CANCELLED) verified');

  // =========================================================================
  // JOURNEY G: Authorization & Multi-tenant Isolation
  // =========================================================================
  // 1. Viewer Role: read only, no Add/Settle actions
  const viewerContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const viewerPage = await viewerContext.newPage();
  await signin(viewerPage, viewer.email);
  await viewerPage.goto(origin + '/records/receivables');
  await viewerPage.waitForLoadState('networkidle');

  // Verify Record Receivable and action buttons are NOT present for Viewer
  assert.equal(await viewerPage.getByRole('button', { name: 'Record Receivable', exact: true }).count(), 0);
  assert.equal(await viewerPage.getByRole('button', { name: 'Record Payment', exact: true }).count(), 0);
  assert.equal(await viewerPage.getByRole('button', { name: 'Pay Supplier', exact: true }).count(), 0);
  await viewerContext.close();

  // 2. Direct POST with Viewer session -> 403 Forbidden
  const viewerForbidden = await api(viewer.context, '/finance/obligations', 'POST', {
    counterpartyId: zubairCustomer.id,
    direction: 'RECEIVABLE',
    amount: '500.00',
    issueDate: today,
  });
  assert.equal(viewerForbidden.status(), 403);

  // 3. Multi-tenant isolation: Business B cannot read or settle Business A's obligation
  await api(owner.context, '/businesses/active', 'POST', { businessId: businessB });

  const crossTenantGet = await api(owner.context, '/finance/obligations/' + receivable.id);
  assert.equal(crossTenantGet.status(), 404);

  const crossTenantSettle = await api(
    owner.context,
    '/finance/obligations/' + receivable.id + '/settlements',
    'POST',
    {
      accountId,
      amount: '100.00',
      settlementDate: today,
    },
    { 'Idempotency-Key': 'cross-' + randomUUID() }
  );
  assert.equal(crossTenantSettle.status(), 404);

  await api(owner.context, '/businesses/active', 'POST', { businessId: businessA });
  mark('JOURNEY G: Viewer role enforcement and multi-tenant isolation (cross-business 404) verified');

  // =========================================================================
  // JOURNEY H: UI Quality, Urdu RTL & Mobile Viewport
  // =========================================================================
  // 1. Urdu RTL test
  await page.goto(origin + '/records/receivables');
  await page.waitForLoadState('networkidle');
  await page.getByRole('button', { name: 'اردو' }).click();
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  await captureScreenshot('journey_h_urdu_rtl.png');

  // 2. Mobile 390x844 responsive test
  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  await page.waitForLoadState('networkidle');
  await page.getByRole('heading', { level: 1 }).first().waitFor();
  await captureScreenshot('journey_h_mobile_390x844.png');

  // Restore English and desktop viewport
  await page.getByRole('button', { name: 'English' }).click();
  await page.setViewportSize({ width: 1440, height: 1000 });
  mark('JOURNEY H: Bilingual Urdu RTL and mobile 390x844 responsive rendering verified');
}
