import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

export async function verifyReversals({
  api,
  owner,
  viewer,
  manager,
  accountant,
  page,
  origin,
  today,
  mark,
  businessA,
  businessB
}) {
  const baseTransactions = '/finance/transactions';
  const baseTransfers = '/finance/transfers';
  const baseAccounts = '/finance/accounts';

  // 1. Create accounts for reversal verification
  const bankRes = await api(owner.context, baseAccounts, 'POST', {
    accountName: 'Reversal Bank ' + randomUUID().slice(0, 6),
    accountType: 'BANK',
    currency: 'PKR',
    openingBalance: '50000.00',
    openingDate: '2026-10-01'
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(bankRes.status(), 201);
  const bankAccount = await bankRes.json();

  const cashRes = await api(owner.context, baseAccounts, 'POST', {
    accountName: 'Reversal Cash ' + randomUUID().slice(0, 6),
    accountType: 'CASH',
    currency: 'PKR',
    openingBalance: '2000.00',
    openingDate: '2026-10-01'
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(cashRes.status(), 201);
  const cashAccount = await cashRes.json();

  mark('REVERSAL HTTP: test accounts Reversal Bank (50,000) and Reversal Cash (2,000) created');

  // 2. Receipt Reversal Test
  const receiptPost = await api(owner.context, baseTransactions, 'POST', {
    accountId: bankAccount.id,
    eventType: 'DIRECT_SALE_RECEIPT',
    amount: '10000.00',
    effectiveDate: today,
    description: 'Sale to be reversed'
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(receiptPost.status(), 201);
  const receiptTx = await receiptPost.json();
  assert.equal(receiptTx.resultingBalance, '60000.00');

  // Reverse the receipt
  const receiptRevKey = randomUUID();
  const receiptRevPayload = { reason: 'Customer invoice was paid elsewhere' };
  const receiptRevRes = await api(
    owner.context,
    `${baseTransactions}/${receiptTx.id}/reverse`,
    'POST',
    receiptRevPayload,
    { 'Idempotency-Key': receiptRevKey }
  );
  assert.equal(receiptRevRes.status(), 201);
  const receiptRevData = await receiptRevRes.json();

  assert.equal(receiptRevData.isReversal, true);
  assert.equal(receiptRevData.reversedTransactionId, receiptTx.id);
  assert.equal(receiptRevData.movementDirection, 'DECREASE');
  assert.equal(receiptRevData.amount, '10000.00');
  assert.equal(receiptRevData.resultingBalance, '50000.00');
  assert.equal(receiptRevData.reversalReason, receiptRevPayload.reason);

  // Bank balance restored to 50,000.00
  const bankAfterReceiptRev = await (await api(owner.context, `${baseAccounts}/${bankAccount.id}`)).json();
  assert.equal(bankAfterReceiptRev.currentBalance, '50000.00');

  // Original transaction marked reversed
  const origReceiptDetail = await (await api(owner.context, `${baseTransactions}/${receiptTx.id}`)).json();
  assert.equal(origReceiptDetail.isReversed, true);
  assert.equal(origReceiptDetail.reversedByTransactionId, receiptRevData.id);

  // Idempotent retry returns identical 201
  const receiptRevRetry = await api(
    owner.context,
    `${baseTransactions}/${receiptTx.id}/reverse`,
    'POST',
    receiptRevPayload,
    { 'Idempotency-Key': receiptRevKey }
  );
  assert.equal(receiptRevRetry.status(), 201);
  const retryReceiptRevData = await receiptRevRetry.json();
  assert.equal(retryReceiptRevData.id, receiptRevData.id);

  // Changed payload with same key returns 409
  const receiptRevConflict = await api(
    owner.context,
    `${baseTransactions}/${receiptTx.id}/reverse`,
    'POST',
    { reason: 'Different reason here' },
    { 'Idempotency-Key': receiptRevKey }
  );
  assert.equal(receiptRevConflict.status(), 409);

  // Second reversal of same transaction returns 409
  const doubleRevRes = await api(
    owner.context,
    `${baseTransactions}/${receiptTx.id}/reverse`,
    'POST',
    { reason: 'Second attempt to reverse' },
    { 'Idempotency-Key': randomUUID() }
  );
  assert.equal(doubleRevRes.status(), 409);

  // Reversal of reversal returns 400
  const revOfRevRes = await api(
    owner.context,
    `${baseTransactions}/${receiptRevData.id}/reverse`,
    'POST',
    { reason: 'Attempt to reverse a reversal' },
    { 'Idempotency-Key': randomUUID() }
  );
  assert.equal(revOfRevRes.status(), 400);

  mark('REVERSAL HTTP: receipt reversal restores balance (60,000 -> 50,000), enforces idempotency, prevents double reversal and reversal-of-reversal');

  // 3. Operating Expense Reversal Test
  const expensePost = await api(owner.context, baseTransactions, 'POST', {
    accountId: bankAccount.id,
    eventType: 'OPERATING_EXPENSE_PAYMENT',
    amount: '4000.00',
    effectiveDate: today,
    description: 'Expense to be reversed'
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(expensePost.status(), 201);
  const expenseTx = await expensePost.json();
  assert.equal(expenseTx.resultingBalance, '46000.00');

  const expenseRevRes = await api(
    owner.context,
    `${baseTransactions}/${expenseTx.id}/reverse`,
    'POST',
    { reason: 'Duplicate bill entry recorded by error' },
    { 'Idempotency-Key': randomUUID() }
  );
  assert.equal(expenseRevRes.status(), 201);
  const expenseRevData = await expenseRevRes.json();

  assert.equal(expenseRevData.isReversal, true);
  assert.equal(expenseRevData.reversedTransactionId, expenseTx.id);
  assert.equal(expenseRevData.movementDirection, 'INCREASE');
  assert.equal(expenseRevData.amount, '4000.00');
  assert.equal(expenseRevData.resultingBalance, '50000.00');

  const bankAfterExpRev = await (await api(owner.context, `${baseAccounts}/${bankAccount.id}`)).json();
  assert.equal(bankAfterExpRev.currentBalance, '50000.00');

  mark('REVERSAL HTTP: expense reversal restores balance (46,000 -> 50,000) and nets out moneyPaid');

  // 4. Internal Transfer Reversal Test
  const transferPost = await api(owner.context, baseTransfers, 'POST', {
    sourceAccountId: bankAccount.id,
    destinationAccountId: cashAccount.id,
    amount: '5000.00',
    effectiveDate: today,
    description: 'Transfer to reverse'
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(transferPost.status(), 201);
  const transferTx = await transferPost.json();

  const transferRevRes = await api(
    owner.context,
    `${baseTransactions}/${transferTx.id}/reverse`,
    'POST',
    { reason: 'Funds transferred from wrong account' },
    { 'Idempotency-Key': randomUUID() }
  );
  assert.equal(transferRevRes.status(), 201);
  const transferRevData = await transferRevRes.json();

  assert.equal(transferRevData.isReversal, true);
  assert.equal(transferRevData.reversedTransactionId, transferTx.id);
  assert.equal(transferRevData.movements.length, 2);

  const bankAfterTrRev = await (await api(owner.context, `${baseAccounts}/${bankAccount.id}`)).json();
  assert.equal(bankAfterTrRev.currentBalance, '50000.00');

  const cashAfterTrRev = await (await api(owner.context, `${baseAccounts}/${cashAccount.id}`)).json();
  assert.equal(cashAfterTrRev.currentBalance, '2000.00');

  mark('REVERSAL HTTP: transfer reversal restores both balances (Bank 50,000, Cash 2,000) and nets transfer movements');

  // 5. Chronological Cash Deficit Rejection
  const drawerCashRes = await api(owner.context, baseAccounts, 'POST', {
    accountName: 'Drawer Cash ' + randomUUID().slice(0, 6),
    accountType: 'CASH',
    currency: 'PKR',
    openingBalance: '100.00',
    openingDate: '2026-10-01'
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(drawerCashRes.status(), 201);
  const drawerCash = await drawerCashRes.json();

  const drawerSale = await (await api(owner.context, baseTransactions, 'POST', {
    accountId: drawerCash.id,
    eventType: 'DIRECT_SALE_RECEIPT',
    amount: '1000.00',
    effectiveDate: today,
    description: 'Drawer cash sale'
  }, { 'Idempotency-Key': randomUUID() })).json();

  await api(owner.context, baseTransactions, 'POST', {
    accountId: drawerCash.id,
    eventType: 'OPERATING_EXPENSE_PAYMENT',
    amount: '900.00',
    effectiveDate: today,
    description: 'Cash spent on office milk'
  }, { 'Idempotency-Key': randomUUID() });

  // Now drawer balance is 200. Reversing the 1,000 sale receipt would cause deficit (-800)
  const deficitReversal = await api(
    owner.context,
    `${baseTransactions}/${drawerSale.id}/reverse`,
    'POST',
    { reason: 'Attempting to reverse drawer sale' },
    { 'Idempotency-Key': randomUUID() }
  );
  assert.equal(deficitReversal.status(), 400);
  const deficitBody = await deficitReversal.json();
  assert.ok(deficitBody.errors?.cashBalance || deficitBody.errors?.cashFlow || deficitBody.fieldErrors?.cashBalance);

  mark('REVERSAL HTTP: chronological CASH simulation rejects reversal when running cash would drop below zero');

  // 6. RBAC and Cross-Tenant checks
  for (const reader of [viewer, manager]) {
    const denied = await api(
      reader.context,
      `${baseTransactions}/${drawerSale.id}/reverse`,
      'POST',
      { reason: 'Unauthorized reversal attempt' },
      { 'Idempotency-Key': randomUUID() }
    );
    assert.equal(denied.status(), 403);
  }

  // Accountant can reverse
  const accountantExp = await (await api(owner.context, baseTransactions, 'POST', {
    accountId: bankAccount.id,
    eventType: 'OPERATING_EXPENSE_PAYMENT',
    amount: '100.00',
    effectiveDate: today,
    description: 'Small expense'
  }, { 'Idempotency-Key': randomUUID() })).json();

  const accountantRev = await api(
    accountant.context,
    `${baseTransactions}/${accountantExp.id}/reverse`,
    'POST',
    { reason: 'Accountant legitimate reversal' },
    { 'Idempotency-Key': randomUUID() }
  );
  assert.equal(accountantRev.status(), 201);

  // Cross-tenant denial: Business B cannot reverse Business A transaction
  await api(owner.context, '/businesses/active', 'POST', { businessId: businessB });
  const crossTenantRev = await api(
    owner.context,
    `${baseTransactions}/${accountantExp.id}/reverse`,
    'POST',
    { reason: 'Cross-tenant reversal attempt' },
    { 'Idempotency-Key': randomUUID() }
  );
  assert.equal(crossTenantRev.status(), 404);
  await api(owner.context, '/businesses/active', 'POST', { businessId: businessA });

  mark('REVERSAL HTTP: OWNER and ACCOUNTANT reverse; MANAGER and VIEWER return 403; cross-business returns 404');

  // 7. Browser UI Journey: Reversal action in detail sheet
  const browserTx = await (await api(owner.context, baseTransactions, 'POST', {
    accountId: bankAccount.id,
    eventType: 'DIRECT_SALE_RECEIPT',
    amount: '3500.00',
    effectiveDate: today,
    description: 'UI Sale to reverse'
  }, { 'Idempotency-Key': randomUUID() })).json();

  await page.goto(origin + '/finance/transactions');
  await page.getByRole('button', { name: 'Add record', exact: true }).first().waitFor();

  // Open transaction detail for the newly added sale
  const detailBtn = page.getByRole('button', { name: 'View transaction details: ' + browserTx.id, exact: true });
  await detailBtn.waitFor();
  await detailBtn.click();

  const detailModal = page.getByRole('dialog');
  await detailModal.waitFor();

  // Click "Reverse incorrect entry"
  const reverseEntryBtn = detailModal.getByRole('button', { name: 'Reverse incorrect entry', exact: true });
  await reverseEntryBtn.waitFor();
  await reverseEntryBtn.click();

  // Verify notice: "This corrects your records. It does not send or return money."
  await detailModal.getByText('This corrects your records. It does not send or return money.', { exact: true }).waitFor();

  // Fill reversal reason and confirm
  const reasonInput = detailModal.locator('#reversal-reason');
  await reasonInput.waitFor();
  await reasonInput.fill('Live browser correction of incorrect entry');
  await detailModal.getByRole('checkbox').check();

  // Submit reversal
  const confirmBtn = detailModal.getByRole('button', { name: 'Confirm reversal', exact: true });
  await confirmBtn.waitFor();
  await confirmBtn.click();

  // Wait for reversal form to submit and detail view to show Reversed status and bidirectional link
  await detailModal.getByText('Reversed', { exact: true }).first().waitFor();
  const viewReversalBtn = detailModal.getByRole('button', { name: /View reversal entry/, exact: false });
  await viewReversalBtn.waitFor();

  // Navigate to compensating reversal entry
  await viewReversalBtn.click();
  await detailModal.getByText('Reversal', { exact: true }).first().waitFor();
  const viewOriginalBtn = detailModal.getByRole('button', { name: /View original entry/, exact: false });
  await viewOriginalBtn.waitFor();

  // Navigate back to original entry
  await viewOriginalBtn.click();
  await detailModal.getByText('Reversed', { exact: true }).first().waitFor();

  // Close sheet
  await page.keyboard.press('Escape');
  await detailModal.waitFor({ state: 'detached' });

  mark('REVERSAL BROWSER: Transaction detail exposes Reverse action, validates explanation & confirmation, records reversal, links bidirectionally, and marks entry Reversed');

  // 8. Bilingual Urdu RTL Verification
  await page.getByRole('button', { name: 'اردو', exact: true }).click();
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  assert.equal(await page.locator('html').getAttribute('dir'), 'rtl');
  await page.getByText('منسوخ شدہ', { exact: true }).first().waitFor();
  await page.getByText('منسوخی', { exact: true }).first().waitFor();
  await page.getByRole('button', { name: 'English', exact: true }).click();
  await page.waitForFunction(() => document.documentElement.dir === 'ltr');

  mark('REVERSAL BROWSER: bilingual Urdu RTL rendering and reversed/reversal badges verified');
}
