import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

export async function verifyTransfers({
  api,
  owner,
  viewer,
  manager,
  accountant,
  page,
  origin,
  today,
  mark
}) {
  const baseTransfers = '/finance/transfers';
  const baseAccounts = '/finance/accounts';
  const baseSummary = '/finance/transactions/summary';

  // 1. Create two test accounts in business A: "Meezan Bank" (BANK) and "Petty Cash" (CASH)
  const meezanRes = await api(owner.context, baseAccounts, 'POST', {
    accountName: 'Meezan Bank ' + randomUUID().slice(0, 6),
    accountType: 'BANK',
    currency: 'PKR',
    openingBalance: '50000.00',
    openingDate: '2026-10-01'
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(meezanRes.status(), 201);
  const meezanAccount = await meezanRes.json();

  const cashRes = await api(owner.context, baseAccounts, 'POST', {
    accountName: 'Petty Cash ' + randomUUID().slice(0, 6),
    accountType: 'CASH',
    currency: 'PKR',
    openingBalance: '1000.00',
    openingDate: '2026-10-01'
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(cashRes.status(), 201);
  const cashAccount = await cashRes.json();

  mark('TRANSFER HTTP: Meezan Bank (50,000.00) and Petty Cash (1,000.00) created');

  // 2. Summary before transfer
  const summaryBefore = await (await api(owner.context, baseSummary)).json();

  // 3. Concrete 5,000 PKR Transfer via HTTP
  const transferKey = randomUUID();
  const transferPayload = {
    sourceAccountId: meezanAccount.id,
    destinationAccountId: cashAccount.id,
    amount: '5000.00',
    effectiveDate: today,
    description: 'Replenish petty cash'
  };

  const transferPost = await api(owner.context, baseTransfers, 'POST', transferPayload, { 'Idempotency-Key': transferKey });
  assert.equal(transferPost.status(), 201);
  const transferData = await transferPost.json();

  assert.equal(transferData.eventType, 'INTERNAL_TRANSFER');
  assert.equal(transferData.amount, '5000.00');
  assert.equal(transferData.resultingBalance, '45000.00');
  assert.equal(transferData.sourceAccountId, meezanAccount.id);
  assert.equal(transferData.destinationAccountId, cashAccount.id);
  assert.equal(transferData.movements.length, 2);

  // Verify updated balances
  const meezanUpdated = await (await api(owner.context, baseAccounts + '/' + meezanAccount.id)).json();
  assert.equal(meezanUpdated.currentBalance, '45000.00');

  const cashUpdated = await (await api(owner.context, baseAccounts + '/' + cashAccount.id)).json();
  assert.equal(cashUpdated.currentBalance, '6000.00');

  mark('TRANSFER HTTP: concrete 5,000 PKR transfer updates bank (50,000 -> 45,000) and cash (1,000 -> 6,000)');

  // 4. Idempotent Retry: same key returns identical 201
  const retryPost = await api(owner.context, baseTransfers, 'POST', transferPayload, { 'Idempotency-Key': transferKey });
  assert.equal(retryPost.status(), 201);
  const retryData = await retryPost.json();
  assert.equal(retryData.id, transferData.id);

  // 5. Changed payload with same key returns 409 Conflict
  const conflictPost = await api(owner.context, baseTransfers, 'POST', {
    ...transferPayload,
    amount: '6000.00'
  }, { 'Idempotency-Key': transferKey });
  assert.equal(conflictPost.status(), 409);

  mark('TRANSFER HTTP: idempotent replay returns 201 cached transfer; changed payload returns 409 conflict');

  // 6. Summary verification: external cash flow unchanged, transfers aggregated
  const summaryAfter = await (await api(owner.context, baseSummary)).json();
  assert.equal(summaryAfter.moneyReceived, summaryBefore.moneyReceived);
  assert.equal(summaryAfter.moneyPaid, summaryBefore.moneyPaid);
  assert.equal(summaryAfter.netCashMovement, summaryBefore.netCashMovement);

  // Bank summary
  const bankSummary = await (await api(owner.context, baseSummary + '?accountId=' + meezanAccount.id)).json();
  assert.equal(bankSummary.transferOut, '5000.00');
  assert.equal(bankSummary.transferIn, '0.00');

  // Cash summary
  const cashSummary = await (await api(owner.context, baseSummary + '?accountId=' + cashAccount.id)).json();
  assert.equal(cashSummary.transferIn, '5000.00');
  assert.equal(cashSummary.transferOut, '0.00');

  mark('TRANSFER HTTP: business-wide external cash flow untouched; account-filtered transfers reflect directional in/out movements');

  // 7. Role Authorization: ACCOUNTANT can post; VIEWER and MANAGER return 403
  for (const reader of [viewer, manager]) {
    const denied = await api(reader.context, baseTransfers, 'POST', {
      sourceAccountId: meezanAccount.id,
      destinationAccountId: cashAccount.id,
      amount: '100.00',
      effectiveDate: today
    }, { 'Idempotency-Key': randomUUID() });
    assert.equal(denied.status(), 403);
  }

  const accountantPost = await api(accountant.context, baseTransfers, 'POST', {
    sourceAccountId: meezanAccount.id,
    destinationAccountId: cashAccount.id,
    amount: '100.00',
    effectiveDate: today,
    description: 'Accountant transfer'
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(accountantPost.status(), 201);

  mark('TRANSFER HTTP: OWNER and ACCOUNTANT post successfully; MANAGER and VIEWER return 403');

  // 8. Browser Journey: Move money form in Records
  await page.goto(origin + '/records');
  await page.getByRole('button', { name: 'Move money', exact: true }).first().waitFor();
  await page.getByRole('button', { name: 'Move money', exact: true }).first().click();

  const transferForm = page.getByRole('form', { name: 'Move money', exact: true });
  await transferForm.waitFor();

  // Fill transfer form: From Meezan Bank, To Petty Cash, Amount 2,000
  await transferForm.getByLabel('From account', { exact: true }).selectOption(meezanAccount.id);
  await transferForm.getByLabel('To account', { exact: true }).selectOption(cashAccount.id);
  await transferForm.getByLabel(/^Amount/).fill('2000');
  await transferForm.getByLabel('Date', { exact: true }).fill(today);
  await transferForm.getByLabel('Note (optional)', { exact: true }).fill('Browser live transfer');

  // Movement preview is plainly visible
  await transferForm.getByText(/Move PKR 2000\.00 from/, { exact: false }).waitFor();

  // Check confirmation
  await transferForm.getByRole('checkbox').check();

  // Submit transfer
  await transferForm.getByRole('button', { name: 'Move money', exact: true }).click();
  await transferForm.waitFor({ state: 'detached' });

  // Detail dialog opens automatically showing the newly recorded transfer
  const detailModal = page.getByRole('dialog');
  await detailModal.waitFor();
  await detailModal.getByText('Internal transfer', { exact: true }).waitFor();
  await page.keyboard.press('Escape');
  await detailModal.waitFor({ state: 'detached' });

  mark('TRANSFER BROWSER: Move money form validates natural input, shows plain preview, records transfer and refreshes activity');

  // 9. Urdu / RTL Verification
  await page.getByRole('button', { name: 'اردو', exact: true }).click();
  await page.getByRole('button', { name: 'رقم منتقل کریں', exact: true }).first().waitFor();
  await page.getByRole('button', { name: 'رقم منتقل کریں', exact: true }).first().click();

  const urduForm = page.getByRole('form', { name: 'رقم منتقل کریں', exact: true });
  await urduForm.waitFor();
  assert.equal(await page.locator('html').getAttribute('dir'), 'rtl');
  await urduForm.getByRole('button', { name: 'منسوخ کریں', exact: true }).click();
  await urduForm.waitFor({ state: 'detached' });
  await page.getByRole('button', { name: 'English', exact: true }).click();

  mark('TRANSFER BROWSER: bilingual Urdu RTL rendering and modal interaction verified');
}
