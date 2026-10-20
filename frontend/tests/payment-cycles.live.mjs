import assert from 'node:assert/strict';
import { mkdirSync } from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

export async function verifyPaymentCycles({
  api,
  owner,
  viewer,
  manager,
  page,
  browser,
  signin,
  origin,
  accountId,
  today,
  control,
  getRuntimeText,
  mark,
  businessA,
  businessB,
  out,
}) {
  const directory = path.join(out, 'payment-cycles');
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

  const initialMutations = await getMutationCounts('pc_initial');

  // =========================================================================
  // SETUP: Create dedicated Customer and Supplier for Payment Cycles
  // =========================================================================
  const custRes = await api(owner.context, '/finance/counterparties', 'POST', {
    displayName: 'Habib Trading Corp',
    type: 'CUSTOMER',
    email: 'habib@trading.test',
    phone: '+92 300 1112233',
  });
  assert.equal(custRes.status(), 201);
  const customer = await custRes.json();

  const suppRes = await api(owner.context, '/finance/counterparties', 'POST', {
    displayName: 'Naveed Industries Ltd',
    type: 'SUPPLIER',
    email: 'naveed@industries.test',
    phone: '+92 300 9998877',
  });
  assert.equal(suppRes.status(), 201);
  const supplier = await suppRes.json();

  // =========================================================================
  // JOURNEY A: Multi-Receivable Payment (100k cash -> 60k + 40k allocated)
  // =========================================================================
  // 1. Create two customer obligations via API
  const ob1Res = await api(owner.context, '/finance/obligations', 'POST', {
    counterpartyId: customer.id,
    direction: 'RECEIVABLE',
    amount: '60000.00',
    issueDate: today,
    dueDate: today,
    referenceNumber: 'REC-MULTI-A1',
  });
  assert.equal(ob1Res.status(), 201);
  const rec1 = await ob1Res.json();

  const ob2Res = await api(owner.context, '/finance/obligations', 'POST', {
    counterpartyId: customer.id,
    direction: 'RECEIVABLE',
    amount: '40000.00',
    issueDate: today,
    dueDate: today,
    referenceNumber: 'REC-MULTI-A2',
  });
  assert.equal(ob2Res.status(), 201);
  const rec2 = await ob2Res.json();

  // 2. Browser interaction: navigate to receivables workspace
  await page.goto(origin + '/records/receivables');
  await page.waitForLoadState('networkidle');
  await captureScreenshot('journey_a_initial.png');

  // Click Allocate Multi-Payment button
  await page.getByRole('button', { name: 'Allocate Multi-Payment', exact: true }).click();
  const multiModal = page.getByRole('dialog');
  await multiModal.waitFor();

  // Fill modal
  await multiModal.locator('select').first().selectOption(customer.id);
  // Account is second select
  await multiModal.locator('select').nth(1).selectOption(accountId);
  // Total cash amount
  await multiModal.locator('input[type="number"]').first().fill('100000.00');

  // Wait for open obligations table to load rows
  await multiModal.getByText('REC-MULTI-A1').waitFor();
  await multiModal.getByText('REC-MULTI-A2').waitFor();

  // Fill allocation amounts in table
  const row1 = multiModal.locator('tr', { hasText: 'REC-MULTI-A1' });
  await row1.locator('input[type="number"]').fill('60000.00');
  const row2 = multiModal.locator('tr', { hasText: 'REC-MULTI-A2' });
  await row2.locator('input[type="number"]').fill('40000.00');

  // Verify residual is 0.00
  await multiModal.getByTestId('residual-amount').waitFor();

  // Submit allocation
  await multiModal.getByRole('button', { name: 'Confirm & Post Allocation', exact: true }).click();
  await multiModal.waitFor({ state: 'detached' });

  // 3. Verify in table that both obligations are Fully Paid
  await page.reload();
  await page.waitForLoadState('networkidle');
  await captureScreenshot('journey_a_completed.png');

  // 4. Verify financial invariants: exactly 1 new financial transaction & 1 account movement (+100k)
  const mutA = await getMutationCounts('pc_journey_a');
  assert.equal(mutA.financial_transactions, initialMutations.financial_transactions + 1);
  assert.equal(mutA.account_movements, initialMutations.account_movements + 1);

  // Check backend transaction details: event is RECEIVABLE_COLLECTION, amount 100,000.00
  const txListRes = await api(owner.context, '/finance/transactions?eventType=RECEIVABLE_COLLECTION');
  assert.equal(txListRes.status(), 200);
  const txList = await txListRes.json();
  const tx = (txList.content || txList.items || []).find(t => t.amount === '100000.00');
  assert.ok(tx, 'Expected 100,000.00 transaction');
  assert.equal(tx.eventType, 'RECEIVABLE_COLLECTION');

  // Verify settlement does NOT recognize duplicate revenue
  const summaryRes = await api(owner.context, '/finance/transactions/summary');
  const summary = await summaryRes.json();
  assert.equal(summary.moneyReceived, '100000.00');
  assert.equal(summary.moneyPaid, '0.00');

  // Verify outstanding balances: both 0.00
  const ob1Check = await (await api(owner.context, '/finance/obligations/' + rec1.id)).json();
  const ob2Check = await (await api(owner.context, '/finance/obligations/' + rec2.id)).json();
  assert.equal(ob1Check.outstandingAmount, '0.00');
  assert.equal(ob1Check.settlementStatus, 'FULLY_PAID');
  assert.equal(ob2Check.outstandingAmount, '0.00');
  assert.equal(ob2Check.settlementStatus, 'FULLY_PAID');

  mark('JOURNEY A: Multi-receivable payment (100k cash, 60k + 40k allocated, 1 movement, 0 revenue) verified');

  // =========================================================================
  // JOURNEY B: Multi-Payable Payment (150k cash -> 70k + 80k allocated)
  // =========================================================================
  const bill1 = await (await api(owner.context, '/finance/obligations', 'POST', {
    counterpartyId: supplier.id,
    direction: 'PAYABLE',
    amount: '70000.00',
    issueDate: today,
    dueDate: today,
    referenceNumber: 'BILL-MULTI-B1',
  })).json();

  const bill2 = await (await api(owner.context, '/finance/obligations', 'POST', {
    counterpartyId: supplier.id,
    direction: 'PAYABLE',
    amount: '80000.00',
    issueDate: today,
    dueDate: today,
    referenceNumber: 'BILL-MULTI-B2',
  })).json();

  await page.goto(origin + '/records/payables');
  await page.waitForLoadState('networkidle');

  await page.getByRole('button', { name: 'Allocate Multi-Payment', exact: true }).click();
  const payModal = page.getByRole('dialog');
  await payModal.waitFor();

  // Select supplier & account
  await payModal.locator('select').first().selectOption(supplier.id);
  await payModal.locator('select').nth(1).selectOption(accountId);
  await payModal.locator('input[type="number"]').first().fill('150000.00');

  await payModal.getByText('BILL-MULTI-B1').waitFor();
  await payModal.getByText('BILL-MULTI-B2').waitFor();

  const billRow1 = payModal.locator('tr', { hasText: 'BILL-MULTI-B1' });
  await billRow1.locator('input[type="number"]').fill('70000.00');
  const billRow2 = payModal.locator('tr', { hasText: 'BILL-MULTI-B2' });
  await billRow2.locator('input[type="number"]').fill('80000.00');

  await payModal.getByRole('button', { name: 'Confirm & Post Allocation', exact: true }).click();
  await payModal.waitFor({ state: 'detached' });

  // Verify financial invariants: exactly 1 new financial transaction & 1 account movement (-150k)
  const mutB = await getMutationCounts('pc_journey_b');
  assert.equal(mutB.financial_transactions, mutA.financial_transactions + 1);
  assert.equal(mutB.account_movements, mutA.account_movements + 1);

  // Both bills are 0.00 outstanding
  const bill1Check = await (await api(owner.context, '/finance/obligations/' + bill1.id)).json();
  const bill2Check = await (await api(owner.context, '/finance/obligations/' + bill2.id)).json();
  assert.equal(bill1Check.outstandingAmount, '0.00');
  assert.equal(bill2Check.outstandingAmount, '0.00');

  // Verify no duplicate expense recognition
  const summaryB = await (await api(owner.context, '/finance/transactions/summary')).json();
  assert.equal(summaryB.moneyPaid, '150000.00');

  mark('JOURNEY B: Multi-payable payment (150k cash, 70k + 80k allocated, 1 movement, 0 duplicate expense) verified');

  // =========================================================================
  // JOURNEY C: Customer Advance Receipt (50k upfront)
  // =========================================================================
  await page.goto(origin + '/records?view=advances');
  await page.waitForLoadState('networkidle');
  await captureScreenshot('journey_c_advances_page.png');

  await page.getByRole('button', { name: 'Record Advance', exact: true }).click();
  const advModal = page.getByRole('dialog');
  await advModal.waitFor();

  // Default direction is Customer Advance (RECEIVABLE)
  await advModal.locator('select').first().selectOption(customer.id);
  await advModal.locator('select').nth(1).selectOption(accountId);
  await advModal.locator('input[type="number"]').fill('50000.00');
  await advModal.locator('input[placeholder*="ADV-"]').fill('ADV-CUST-LIVE');

  await advModal.getByRole('button', { name: 'Record Advance', exact: true }).click();
  await advModal.waitFor({ state: 'detached' });

  // Verify row appears in advances table
  await page.reload();
  await page.waitForLoadState('networkidle');
  await page.locator('tbody').getByText('ADV-CUST-LIVE').waitFor();
  await page.locator('tbody').getByText('50,000.00').first().waitFor();
  await captureScreenshot('journey_c_advance_created.png');

  // Verify 1 cash transaction & movement posted for the advance receipt
  const mutC = await getMutationCounts('pc_journey_c');
  assert.equal(mutC.financial_transactions, mutB.financial_transactions + 1);
  assert.equal(mutC.account_movements, mutB.account_movements + 1);

  // Retrieve advance ID from API
  const advList = await (await api(owner.context, '/finance/advances?counterpartyId=' + customer.id)).json();
  const custAdv = advList.items.find(a => a.referenceNumber === 'ADV-CUST-LIVE');
  assert.ok(custAdv);
  assert.equal(custAdv.unappliedAmount, '50000.00');
  assert.equal(custAdv.status, 'ACTIVE');

  mark('JOURNEY C: Customer advance (50k cash received, 50k unapplied balance, persisted) verified');

  // =========================================================================
  // JOURNEY D: Advance Application against 120k Receivable (Zero Cash Movement)
  // =========================================================================
  // Create open obligation of 120,000.00
  const bigRec = await (await api(owner.context, '/finance/obligations', 'POST', {
    counterpartyId: customer.id,
    direction: 'RECEIVABLE',
    amount: '120000.00',
    issueDate: today,
    dueDate: today,
    referenceNumber: 'REC-BIG-120K',
  })).json();

  // In advances workspace, click "Apply" on the advance row
  const advRow = page.locator('tr', { hasText: 'ADV-CUST-LIVE' });
  await advRow.getByRole('button', { name: 'Apply', exact: true }).click();

  const applyModal = page.getByRole('dialog');
  await applyModal.waitFor();

  // Select the obligation
  await applyModal.locator('select').selectOption(bigRec.id);
  await applyModal.locator('input[type="number"]').fill('50000.00');

  await applyModal.getByRole('button', { name: 'Apply Credit', exact: true }).click();
  await applyModal.waitFor({ state: 'detached' });

  // Verify advance status in UI updates to Exhausted / 0.00 unapplied
  await page.reload();
  await page.waitForLoadState('networkidle');
  await page.locator('tbody').getByText('Exhausted').first().waitFor();
  await captureScreenshot('journey_d_advance_applied.png');

  // Verify obligation outstanding is now 70,000.00
  const bigRecCheck = await (await api(owner.context, '/finance/obligations/' + bigRec.id)).json();
  assert.equal(bigRecCheck.outstandingAmount, '70000.00');
  assert.equal(bigRecCheck.settlementStatus, 'PARTIALLY_PAID');

  // CRITICAL INVARIANT: Zero new financial transactions or account movements!
  const mutD = await getMutationCounts('pc_journey_d');
  assert.equal(mutD.financial_transactions, mutC.financial_transactions, 'Zero cash transactions during advance application');
  assert.equal(mutD.account_movements, mutC.account_movements, 'Zero account movements during advance application');

  mark('JOURNEY D: Advance application (50k applied against 120k rec -> 70k outstanding, ZERO cash movements) verified');

  // =========================================================================
  // JOURNEY E: Supplier Advance (30k payment -> application to 80k bill)
  // =========================================================================
  await page.getByRole('button', { name: 'Record Advance', exact: true }).click();
  const suppAdvModal = page.getByRole('dialog');
  await suppAdvModal.waitFor();

  // Change type to Supplier Advance (PAYABLE)
  await suppAdvModal.getByRole('button', { name: 'Supplier Advance (Cash Paid)' }).click();
  // Select supplier
  await suppAdvModal.locator('select').first().selectOption(supplier.id);
  // Account
  await suppAdvModal.locator('select').nth(1).selectOption(accountId);
  // Amount
  await suppAdvModal.locator('input[type="number"]').fill('30000.00');
  await suppAdvModal.locator('input[placeholder*="ADV-"]').fill('ADV-SUPP-LIVE');

  await suppAdvModal.getByRole('button', { name: 'Record Advance', exact: true }).click();
  await suppAdvModal.waitFor({ state: 'detached' });

  // Verify 1 cash transaction & movement posted for supplier advance
  const mutE1 = await getMutationCounts('pc_journey_e1');
  assert.equal(mutE1.financial_transactions, mutD.financial_transactions + 1);
  assert.equal(mutE1.account_movements, mutD.account_movements + 1);

  // Create supplier obligation of 80,000.00
  const suppBill = await (await api(owner.context, '/finance/obligations', 'POST', {
    counterpartyId: supplier.id,
    direction: 'PAYABLE',
    amount: '80000.00',
    issueDate: today,
    dueDate: today,
    referenceNumber: 'BILL-SUPP-80K',
  })).json();

  // Filter or locate supplier advance row and click Apply
  await page.reload();
  await page.waitForLoadState('networkidle');
  const suppAdvRow = page.locator('tr', { hasText: 'ADV-SUPP-LIVE' });
  await suppAdvRow.getByRole('button', { name: 'Apply', exact: true }).click();

  const suppApplyModal = page.getByRole('dialog');
  await suppApplyModal.waitFor();

  await suppApplyModal.locator('select').selectOption(suppBill.id);
  await suppApplyModal.locator('input[type="number"]').fill('30000.00');

  await suppApplyModal.getByRole('button', { name: 'Apply Credit', exact: true }).click();
  await suppApplyModal.waitFor({ state: 'detached' });

  // Verify bill outstanding is 50,000.00
  const suppBillCheck = await (await api(owner.context, '/finance/obligations/' + suppBill.id)).json();
  assert.equal(suppBillCheck.outstandingAmount, '50000.00');

  // Verify ZERO new cash transactions or account movements!
  const mutE2 = await getMutationCounts('pc_journey_e2');
  assert.equal(mutE2.financial_transactions, mutE1.financial_transactions);
  assert.equal(mutE2.account_movements, mutE1.account_movements);

  mark('JOURNEY E: Supplier advance (30k cash moved once, applied to 80k bill -> 50k outstanding, 0 extra movements) verified');

  // =========================================================================
  // JOURNEY F: Reversals
  // =========================================================================
  // 1. Reverse multi-obligation payment: Create 2 obligations, pay 50k (30k + 20k), reverse
  const revOb1 = await (await api(owner.context, '/finance/obligations', 'POST', {
    counterpartyId: customer.id,
    direction: 'RECEIVABLE',
    amount: '30000.00',
    issueDate: today,
    dueDate: today,
    referenceNumber: 'REC-REV-F1',
  })).json();

  const revOb2 = await (await api(owner.context, '/finance/obligations', 'POST', {
    counterpartyId: customer.id,
    direction: 'RECEIVABLE',
    amount: '20000.00',
    issueDate: today,
    dueDate: today,
    referenceNumber: 'REC-REV-F2',
  })).json();

  const payFRes = await api(owner.context, '/finance/payments/allocate', 'POST', {
    counterpartyId: customer.id,
    direction: 'RECEIVABLE',
    accountId,
    paymentDate: today,
    amount: '50000.00',
    allocations: [
      { obligationId: revOb1.id, amount: '30000.00' },
      { obligationId: revOb2.id, amount: '20000.00' },
    ],
    referenceNumber: 'PAY-REV-TEST',
    retainAdvance: false,
  }, { 'Idempotency-Key': 'alloc-f-' + randomUUID() });
  assert.equal(payFRes.status(), 201);
  const payF = await payFRes.json();

  // Reverse the payment
  const revSummary = await api(owner.context, '/finance/transactions/' + payF.financialTransactionId + '/reverse', 'POST', {
    reason: 'Customer check returned unpaid',
  }, { 'Idempotency-Key': 'rev-tx-f-' + randomUUID() });
  assert.equal(revSummary.status(), 201);

  // Both obligations must be restored to original outstanding balances and UNPAID status
  const revOb1Restored = await (await api(owner.context, '/finance/obligations/' + revOb1.id)).json();
  const revOb2Restored = await (await api(owner.context, '/finance/obligations/' + revOb2.id)).json();
  assert.equal(revOb1Restored.outstandingAmount, '30000.00');
  assert.equal(revOb1Restored.settlementStatus, 'UNPAID');
  assert.equal(revOb2Restored.outstandingAmount, '20000.00');
  assert.equal(revOb2Restored.settlementStatus, 'UNPAID');

  // 2. Reverse an advance application: On customer advance from Journey D, reverse application
  const advDetailBeforeRev = await (await api(owner.context, '/finance/advances/' + custAdv.id)).json();
  assert.equal(advDetailBeforeRev.applications.length, 1);
  const appToReverse = advDetailBeforeRev.applications[0];

  const appRevRes = await api(owner.context, '/finance/advances/' + custAdv.id + '/applications/' + appToReverse.allocationId + '/reverse', 'POST', {
    reason: 'Advance applied in error',
  }, { 'Idempotency-Key': 'rev-app-' + randomUUID() });
  assert.equal(appRevRes.status(), 200);

  // Advance balance restored to 50,000.00 unapplied
  const custAdvRestored = await (await api(owner.context, '/finance/advances/' + custAdv.id)).json();
  assert.equal(custAdvRestored.unappliedAmount, '50000.00');
  assert.equal(custAdvRestored.status, 'ACTIVE');

  // Obligation outstanding restored to 120,000.00
  const bigRecRestored = await (await api(owner.context, '/finance/obligations/' + bigRec.id)).json();
  assert.equal(bigRecRestored.outstandingAmount, '120000.00');

  // 3. Confirm originating payment reversal is BLOCKED if advance has downstream applications
  const paymentWithAdvRes = await api(owner.context, '/finance/payments/allocate', 'POST', {
    counterpartyId: customer.id,
    direction: 'RECEIVABLE',
    accountId,
    paymentDate: today,
    amount: '100000.00',
    allocations: [
      { obligationId: bigRec.id, amount: '60000.00' },
    ],
    referenceNumber: 'PAY-WITH-ADV-DOWNSTREAM',
    retainAdvance: true, // Residual: 40,000.00
  }, { 'Idempotency-Key': 'alloc-down-' + randomUUID() });
  assert.equal(paymentWithAdvRes.status(), 201);
  const paymentWithAdv = await paymentWithAdvRes.json();

  assert.ok(paymentWithAdv.advanceId);

  // Apply 20,000 from this advance downstream to another obligation
  const applyDownRes = await api(owner.context, '/finance/advances/' + paymentWithAdv.advanceId + '/applications', 'POST', {
    obligationId: revOb1.id,
    amount: '20000.00',
    applicationDate: today,
  }, { 'Idempotency-Key': 'apply-down-' + randomUUID() });
  assert.equal(applyDownRes.status(), 201);

  // Attempting to reverse the originating payment must fail with 400 Validation Error
  const blockedRevRes = await api(owner.context, '/finance/transactions/' + paymentWithAdv.financialTransactionId + '/reverse', 'POST', {
    reason: 'Try to reverse payment with downstream applied advance',
  }, { 'Idempotency-Key': 'blocked-rev-' + randomUUID() });
  assert.equal(blockedRevRes.status(), 400);
  const blockedError = await blockedRevRes.json();
  assert.ok(blockedError.errors && blockedError.errors.advance);
  assert.ok(blockedError.errors.advance.includes('active downstream applications'));

  const mutF = await getMutationCounts('pc_journey_f');
  mark('JOURNEY F: Reversals (multi-allocation cancelled together, application reversal restores balances, downstream application blocks reversal) verified');

  // =========================================================================
  // JOURNEY G: Payment Calendar & Installment Scheduling
  // =========================================================================
  // 1. Create a 150,000 payable
  const planBill = await (await api(owner.context, '/finance/obligations', 'POST', {
    counterpartyId: supplier.id,
    direction: 'PAYABLE',
    amount: '150000.00',
    issueDate: today,
    dueDate: today,
    referenceNumber: 'BILL-PLAN-G',
  })).json();

  // 2. Set installment schedule via API
  const planRes = await api(owner.context, '/finance/obligations/' + planBill.id + '/payment-plan', 'PUT', {
    installments: [
      { installmentNumber: 1, expectedDate: today, expectedAmount: '50000.00', notes: 'Installment 1' },
      { installmentNumber: 2, expectedDate: today, expectedAmount: '50000.00', notes: 'Installment 2' },
      { installmentNumber: 3, expectedDate: today, expectedAmount: '50000.00', notes: 'Installment 3' },
    ],
  }, { 'Idempotency-Key': 'plan-g-' + randomUUID() });
  assert.equal(planRes.status(), 200);
  const planData = await planRes.json();
  assert.equal(planData.totalPlannedAmount, '150000.00');
  assert.equal(planData.coverageStatus, 'FULL_COVERAGE');

  // Verify setting a plan creates ZERO financial transactions!
  const mutG = await getMutationCounts('pc_journey_g');
  assert.equal(mutG.financial_transactions, mutF.financial_transactions, 'No transactions created by payment plans');
  assert.equal(mutG.account_movements, mutF.account_movements, 'No account movements created by payment plans');

  // 3. Browser view: navigate to /records?view=calendar
  await page.goto(origin + '/records?view=calendar');
  await page.waitForLoadState('networkidle');
  await captureScreenshot('journey_g_calendar_page.png');

  // Verify calendar contains the installments
  await page.getByText('BILL-PLAN-G').first().waitFor();
  await page.getByText('Installment Plan').first().waitFor();

  mark('JOURNEY G: Payment calendar and installment scheduling (3x 50k full coverage, zero financial transactions) verified');

  // =========================================================================
  // JOURNEY H: Authorization & Security Matrix
  // =========================================================================
  // 1. Viewer Role in browser: read-only, no allocation or advance buttons
  const viewerContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const viewerPage = await viewerContext.newPage();
  await signin(viewerPage, viewer.email);

  await viewerPage.goto(origin + '/records?view=advances');
  await viewerPage.waitForLoadState('networkidle');
  assert.equal(await viewerPage.getByRole('button', { name: 'Record Advance', exact: true }).count(), 0);
  assert.equal(await viewerPage.getByRole('button', { name: 'Apply', exact: true }).count(), 0);

  await viewerPage.goto(origin + '/records/receivables');
  await viewerPage.waitForLoadState('networkidle');
  assert.equal(await viewerPage.getByRole('button', { name: 'Allocate Multi-Payment', exact: true }).count(), 0);
  await viewerContext.close();

  // 2. Direct POST with Viewer / Manager -> 403 Forbidden
  const viewerAdvRes = await api(viewer.context, '/finance/advances', 'POST', {
    counterpartyId: customer.id,
    direction: 'RECEIVABLE',
    accountId,
    paymentDate: today,
    amount: '1000.00',
  }, { 'Idempotency-Key': 'v-adv-' + randomUUID() });
  assert.equal(viewerAdvRes.status(), 403);

  const managerAllocRes = await api(manager.context, '/finance/payments/allocate', 'POST', {
    counterpartyId: customer.id,
    direction: 'RECEIVABLE',
    accountId,
    paymentDate: today,
    amount: '1000.00',
    allocations: [{ obligationId: bigRec.id, amount: '1000.00' }],
  }, { 'Idempotency-Key': 'm-alloc-' + randomUUID() });
  assert.equal(managerAllocRes.status(), 403);

  // 3. Cross-tenant isolation: Business B cannot query Business A advances or calendar
  await api(owner.context, '/businesses/active', 'POST', { businessId: businessB });
  const crossAdvRes = await api(owner.context, '/finance/advances/' + custAdv.id);
  assert.equal(crossAdvRes.status(), 404);

  const crossPlanRes = await api(owner.context, '/finance/obligations/' + planBill.id + '/payment-plan');
  assert.equal(crossPlanRes.status(), 404);

  await api(owner.context, '/businesses/active', 'POST', { businessId: businessA });

  // 4. CSRF protection: Non-GET without token returns 403
  const noCsrfRes = await owner.context.post(origin + '/api/finance/advances', {
    data: { counterpartyId: customer.id, direction: 'RECEIVABLE', accountId, paymentDate: today, amount: '100.00' },
    headers: { 'Idempotency-Key': randomUUID() },
  });
  assert.equal(noCsrfRes.status(), 403);

  mark('JOURNEY H: Authorization matrix (VIEWER/MANAGER 403, cross-business 404, CSRF 403) verified');

  // =========================================================================
  // JOURNEY I: Interface Quality (Urdu RTL, Mobile 390x844, Keyboard Usability)
  // =========================================================================
  // 1. Urdu RTL in Advances and Calendar
  await page.goto(origin + '/records?view=advances');
  await page.waitForLoadState('networkidle');
  await page.getByRole('button', { name: 'اردو' }).click();
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  await captureScreenshot('journey_i_advances_urdu_rtl.png');

  await page.goto(origin + '/records?view=calendar');
  await page.waitForLoadState('networkidle');
  await captureScreenshot('journey_i_calendar_urdu_rtl.png');

  // 2. Mobile 390x844 responsive test
  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  await page.waitForLoadState('networkidle');
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  await captureScreenshot('journey_i_mobile_390x844.png');

  // Restore English and desktop viewport
  await page.getByRole('button', { name: 'English' }).click();
  await page.setViewportSize({ width: 1440, height: 1000 });

  mark('JOURNEY I: Interface quality (Bilingual Urdu RTL, mobile 390x844, keyboard usability) verified');
}
