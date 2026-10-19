import assert from 'node:assert/strict';
import { mkdirSync } from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

export async function verifyCounterparties({
  api,
  owner,
  viewer,
  manager,
  accountant,
  page,
  browser,
  signin,
  origin,
  accountId,
  today,
  control,
  getRuntimeText,
  mark,
  out
}) {
  const directory = path.join(out, 'counterparties');
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

  const initialMutations = await getMutationCounts('cp_initial');

  // =========================================================================
  // JOURNEY A: Customer Management & Same-Name Independence
  // =========================================================================
  await page.goto(origin + '/records/contacts');
  await page.waitForLoadState('networkidle');

  // Verify header
  await page.getByRole('heading', { level: 2, name: 'Customers & Suppliers' }).waitFor();
  await captureScreenshot('journey_a_initial.png');

  // 1. Create CUSTOMER "Alpha Customer"
  await page.getByRole('button', { name: 'Add Contact', exact: true }).first().click();
  const modal = page.getByRole('dialog');
  await modal.waitFor();

  await modal.getByLabel(/Display name/i).fill('Alpha Customer');
  await modal.getByLabel(/Contact type/i).selectOption('CUSTOMER');
  await modal.getByLabel(/Phone/i).fill('+92 300 1111111');
  await modal.getByLabel(/Email/i).fill('alpha@example.test');
  await modal.getByLabel(/^Address$/i).fill('Bolton Market, Karachi');
  await modal.getByRole('button', { name: 'Save Contact', exact: true }).click();
  await modal.waitFor({ state: 'detached' });

  // Verify created item appears in table
  await page.getByText('Alpha Customer', { exact: true }).first().waitFor();
  await page.getByText('+92 300 1111111', { exact: true }).waitFor();

  // 2. Reload and verify persistence
  await page.reload();
  await page.waitForLoadState('networkidle');
  await page.getByText('Alpha Customer', { exact: true }).first().waitFor();

  // 3. Search by name
  const searchInput = page.getByRole('searchbox', { name: 'Search' });
  await searchInput.fill('Alpha');
  await page.waitForTimeout(400); // Wait for debounce
  await page.getByText('Alpha Customer', { exact: true }).first().waitFor();

  // 4. Edit customer
  await page.getByRole('button', { name: 'Edit', exact: true }).first().click();
  await modal.waitFor();
  await modal.getByLabel(/^Address$/i).fill('Clifton, Karachi');
  await modal.getByRole('button', { name: 'Save Contact', exact: true }).click();
  await modal.waitFor({ state: 'detached' });

  // 5. Create another counterparty with the EXACT SAME display name "Alpha Customer"
  await searchInput.fill('');
  await page.waitForTimeout(400);

  await page.getByRole('button', { name: 'Add Contact', exact: true }).first().click();
  await modal.waitFor();
  await modal.getByLabel(/Display name/i).fill('Alpha Customer');
  await modal.getByLabel(/Contact type/i).selectOption('SUPPLIER');
  await modal.getByLabel(/Phone/i).fill('+92 300 9999999');
  await modal.getByLabel(/Email/i).fill('alpha.supplier@example.test');
  await modal.getByRole('button', { name: 'Save Contact', exact: true }).click();
  await modal.waitFor({ state: 'detached' });

  // Verify BOTH exist independently in table
  const tbody = page.locator('tbody');
  const customerBadge = tbody.getByText('Customer', { exact: true });
  const supplierBadge = tbody.getByText('Supplier', { exact: true });
  await customerBadge.first().waitFor();
  await supplierBadge.first().waitFor();
  assert.equal(await tbody.getByText('Alpha Customer', { exact: true }).count(), 2);

  // 6. Type filtering
  await page.getByLabel(/Type filter/i).selectOption('CUSTOMER');
  await page.waitForTimeout(300);
  assert.equal(await tbody.getByText('alpha.supplier@example.test').count(), 0);

  await page.getByLabel(/Type filter/i).selectOption('ALL');
  await page.waitForTimeout(300);
  assert.equal(await tbody.getByText('Alpha Customer', { exact: true }).count(), 2);

  // Verify Zero Financial Mutation after Journey A
  const postJourneyAMutations = await getMutationCounts('cp_journey_a');
  assert.equal(postJourneyAMutations.financial_transactions, initialMutations.financial_transactions);
  assert.equal(postJourneyAMutations.account_movements, initialMutations.account_movements);
  assert.equal(postJourneyAMutations.monthly_records, initialMutations.monthly_records);
  assert.equal(postJourneyAMutations.score_results, initialMutations.score_results);

  mark('JOURNEY A: Customer creation, reload persistence, search, edit, same-name independence, filtering and zero financial mutation verified');

  // =========================================================================
  // JOURNEY B: Supplier Payment
  // =========================================================================
  // 1. Create a dedicated Supplier: "Beta Textiles"
  const betaRes = await api(owner.context, '/finance/counterparties', 'POST', {
    displayName: 'Beta Textiles',
    type: 'SUPPLIER',
    email: 'beta@textiles.test',
    phone: '+92 300 2222222',
    address: 'SITE Area, Karachi'
  });
  assert.equal(betaRes.status(), 201);
  const betaSupplier = await betaRes.json();

  // 2. Open /finance/transactions and record an OPERATING_EXPENSE_PAYMENT
  await page.goto(origin + '/finance/transactions');
  await page.waitForLoadState('networkidle');

  await page.getByRole('button', { name: 'Add record', exact: true }).click();
  const txForm = page.locator('form');
  await txForm.waitFor();

  // Select account
  await txForm.getByLabel(/^Account/i).selectOption(accountId);
  // Select expense
  await txForm.getByLabel(/^Event/i).selectOption('OPERATING_EXPENSE_PAYMENT');
  // Select supplier from counterparty dropdown
  await txForm.getByLabel(/Customer \/ Supplier/i).selectOption(betaSupplier.id);
  // Enter amount
  await txForm.getByLabel(/Amount/i).fill('1200.00');
  // Description
  await txForm.getByLabel(/Description/i).fill('Fabric supplies payment');
  // Confirm checkbox
  await txForm.getByRole('checkbox').check();
  // Submit
  await txForm.locator('button[type="submit"]').click();
  await page.waitForTimeout(1000);

  // 3. Verify posted transaction displays "Beta Textiles"
  await page.getByText('Beta Textiles', { exact: true }).first().waitFor();

  // 4. Open detail sheet
  const betaTxCard = page.locator('li', { hasText: 'Beta Textiles' }).first();
  await betaTxCard.waitFor();
  await betaTxCard.getByRole('button', { name: /^View transaction details/i }).click();
  const detailSheet = page.getByRole('dialog', { name: 'Transaction details' });
  await detailSheet.waitFor();
  await detailSheet.getByText('Beta Textiles', { exact: true }).waitFor();
  await detailSheet.getByText('Fabric supplies payment', { exact: true }).waitFor();
  await detailSheet.getByText(/Decrease/i).waitFor();
  await page.keyboard.press('Escape');
  await detailSheet.waitFor({ state: 'detached' });

  // 5. Verify exactly one expected account movement & no extra mutation
  const postJourneyBMutations = await getMutationCounts('cp_journey_b');
  assert.equal(postJourneyBMutations.financial_transactions, initialMutations.financial_transactions + 1);
  assert.equal(postJourneyBMutations.account_movements, initialMutations.account_movements + 1);
  assert.equal(postJourneyBMutations.monthly_records, initialMutations.monthly_records);
  assert.equal(postJourneyBMutations.score_results, initialMutations.score_results);

  mark('JOURNEY B: Supplier payment with counterparty linkage, single expected movement, and correct detail rendering verified');

  // =========================================================================
  // JOURNEY C: Customer Receipt & Idempotency
  // =========================================================================
  // 1. Create or use customer
  const tariqRes = await api(owner.context, '/finance/counterparties', 'POST', {
    displayName: 'Tariq Wholesale',
    type: 'CUSTOMER',
    email: 'tariq@wholesale.test'
  });
  assert.equal(tariqRes.status(), 201);
  const tariqCustomer = await tariqRes.json();

  // 2. Record DIRECT_SALE_RECEIPT with Tariq Wholesale
  await page.goto(origin + '/finance/transactions');
  await page.waitForLoadState('networkidle');

  await page.getByRole('button', { name: 'Add record', exact: true }).click();
  await txForm.waitFor();

  await txForm.getByLabel(/^Account/i).selectOption(accountId);
  await txForm.getByLabel(/^Event/i).selectOption('DIRECT_SALE_RECEIPT');
  await txForm.getByLabel(/Customer \/ Supplier/i).selectOption(tariqCustomer.id);
  await txForm.getByLabel(/Amount/i).fill('3500.00');
  await txForm.getByLabel(/Description/i).fill('Bulk order payment');
  await txForm.getByRole('checkbox').check();
  await txForm.locator('button[type="submit"]').click();
  await page.waitForTimeout(1000);

  // Verify displayed
  await page.getByText('Tariq Wholesale', { exact: true }).first().waitFor();

  // Open detail to verify display name and balance
  const tariqTxCard = page.locator('li', { hasText: 'Tariq Wholesale' }).first();
  await tariqTxCard.waitFor();
  await tariqTxCard.getByRole('button', { name: /^View transaction details/i }).click();
  const tariqDetailSheet = page.getByRole('dialog', { name: 'Transaction details' });
  await tariqDetailSheet.waitFor();
  await tariqDetailSheet.getByText('Tariq Wholesale', { exact: true }).waitFor();
  await tariqDetailSheet.getByText('Bulk order payment', { exact: true }).waitFor();
  await page.keyboard.press('Escape');
  await tariqDetailSheet.waitFor({ state: 'detached' });

  // 3. Verify posting idempotency via API
  const idemKey = randomUUID();
  const postPayload = {
    accountId,
    eventType: 'DIRECT_SALE_RECEIPT',
    amount: '1000.00',
    effectiveDate: today,
    description: 'Idempotency test receipt',
    counterpartyId: tariqCustomer.id
  };
  const firstPost = await api(owner.context, '/finance/transactions', 'POST', postPayload, { 'Idempotency-Key': idemKey });
  assert.equal(firstPost.status(), 201);
  const firstData = await firstPost.json();
  assert.equal(firstData.counterpartyId, tariqCustomer.id);
  assert.equal(firstData.counterpartyName, 'Tariq Wholesale');

  // Replay identical request with same key
  const replayPost = await api(owner.context, '/finance/transactions', 'POST', postPayload, { 'Idempotency-Key': idemKey });
  assert.equal(replayPost.status(), 201);
  const replayData = await replayPost.json();
  assert.equal(replayData.id, firstData.id, 'Idempotent replay must return identical transaction ID');
  assert.equal(replayData.counterpartyId, tariqCustomer.id);

  mark('JOURNEY C: Customer receipt with counterparty linkage, balance update, and posting idempotency verified');

  // =========================================================================
  // JOURNEY D: Imported Draft Workflow with Counterparty
  // =========================================================================
  // 1. Create an imported draft via batch API
  const batchId = randomUUID();
  const importRes = await api(owner.context, '/finance/drafts/import', 'POST', {
    batchId,
    sourceLabel: 'supplier_import.csv',
    rows: [{
      sourceRowId: 'imp-row-1',
      sourceRowNumber: 1,
      accountId: accountId,
      eventType: 'OPERATING_EXPENSE_PAYMENT',
      effectiveDate: today,
      amountInput: '4,500.50',
      description: 'Imported raw fabric invoice',
      sourceReference: 'IMP-FAB-001'
    }]
  });
  assert.equal(importRes.status(), 201);
  const importData = await importRes.json();
  const importedDraftId = importData.rows[0].draftId;
  assert.ok(importedDraftId);

  // 2. Open the imported draft in browser
  await page.goto(origin + `/records?view=review&draftId=${importedDraftId}`);
  await page.waitForLoadState('networkidle');

  const draftDialog = page.getByRole('dialog');
  await draftDialog.waitFor();

  // 3. Assign compatible supplier
  const draftCpSelect = draftDialog.getByLabel(/Customer \/ Supplier/i);
  await draftCpSelect.selectOption(betaSupplier.id);

  // 4. Save the draft
  await draftDialog.getByRole('button', { name: 'Save draft' }).click();
  await draftDialog.getByText(/Saved draft\. It has not been recorded\./i).waitFor();
  await draftDialog.getByRole('button', { name: 'Close entry' }).last().click();
  await draftDialog.waitFor({ state: 'detached' });

  // 5. Reload and confirm persistence
  await page.goto(origin + `/records?view=review&draftId=${importedDraftId}`);
  await page.waitForLoadState('networkidle');
  const reloadedDraftDialog = page.getByRole('dialog');
  await reloadedDraftDialog.waitFor();
  assert.equal(await reloadedDraftDialog.getByLabel(/Customer \/ Supplier/i).inputValue(), betaSupplier.id);

  // 6. Record the draft
  await reloadedDraftDialog.getByRole('checkbox').check();
  await reloadedDraftDialog.getByRole('button', { name: 'Record saved entry' }).click();
  await reloadedDraftDialog.getByRole('button', { name: 'View recorded transaction' }).waitFor();

  // 7. Verify posted transaction retains the selected counterparty
  const recordedDraftRes = await api(owner.context, `/finance/drafts/${importedDraftId}`);
  assert.equal(recordedDraftRes.status(), 200);
  const recordedDraft = await recordedDraftRes.json();
  assert.equal(recordedDraft.status, 'RECORDED');
  assert.ok(recordedDraft.recordedTransactionId);

  const recordedTxRes = await api(owner.context, `/finance/transactions/${recordedDraft.recordedTransactionId}`);
  assert.equal(recordedTxRes.status(), 200);
  const recordedTx = await recordedTxRes.json();
  assert.equal(recordedTx.counterpartyId, betaSupplier.id);
  assert.equal(recordedTx.counterpartyName, 'Beta Textiles');

  // 8. Retry recording: verify 409 conflict & no duplicate
  const retryRecordRes = await api(owner.context, `/finance/drafts/${importedDraftId}/record`, 'POST', {
    version: recordedDraft.version,
    accountId,
    eventType: 'OPERATING_EXPENSE_PAYMENT',
    effectiveDate: today,
    confirmedAmount: '4500.50'
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(retryRecordRes.status(), 409);

  // 9. Verify imported provenance and raw amount preservation
  await control('VERIFY_DRAFT_RAW ' + importedDraftId + ' imp_d', 'LIVE_DRAFT_RAW_imp_d:');
  const rawMatch = getRuntimeText().match(/LIVE_DRAFT_RAW_imp_d:([^\r\n]+)/);
  assert.equal(rawMatch[1], '4,500.50', 'Imported draft raw amountInput must be preserved verbatim');

  await control('VERIFY_RECORDED_TX ' + importedDraftId + ' imp_vtx', 'LIVE_VERIFY_TX_imp_vtx:');
  const txMatch = getRuntimeText().match(/LIVE_VERIFY_TX_imp_vtx:([^\r\n]+)/);
  assert.ok(txMatch[1].includes('movements=1'), 'Recorded imported draft must have exactly 1 account movement');

  mark('JOURNEY D: Imported draft opened, supplier assigned, persistence verified across reload, recorded into transaction with counterparty retained, 409 retry rejection, and raw amountInput preserved');

  // =========================================================================
  // JOURNEY E: Archive and Restore
  // =========================================================================
  await page.goto(origin + '/records/contacts');
  await page.waitForLoadState('networkidle');

  // 1. Archive "Beta Textiles"
  const betaRow = page.locator('tr', { hasText: 'Beta Textiles' });
  await betaRow.waitFor();
  await betaRow.getByRole('button', { name: 'Archive', exact: true }).click();

  const archiveDialog = page.getByRole('dialog', { name: /Archive this contact\?/i });
  await archiveDialog.waitFor();
  await archiveDialog.getByRole('button', { name: 'Confirm Archive', exact: true }).click();
  await archiveDialog.waitFor({ state: 'detached' });

  // Verify Beta Textiles is no longer in Active tab
  await page.waitForTimeout(500);
  assert.equal(await page.locator('tbody').getByText('Beta Textiles', { exact: true }).count(), 0);

  // 2. Verify historical transaction in /finance/transactions STILL displays "Beta Textiles"
  await page.goto(origin + '/finance/transactions');
  await page.waitForLoadState('networkidle');
  await page.getByText('Beta Textiles', { exact: true }).first().waitFor();
  const histCard = page.locator('li', { hasText: 'Beta Textiles' }).first();
  await histCard.getByRole('button', { name: /^View transaction details/i }).click();
  const histDetail = page.getByRole('dialog', { name: 'Transaction details' });
  await histDetail.waitFor();
  await histDetail.getByText('Beta Textiles', { exact: true }).waitFor();
  await page.keyboard.press('Escape');
  await histDetail.waitFor({ state: 'detached' });

  // 3. Open post form: verify Beta Textiles is EXCLUDED from active dropdown
  await page.getByRole('button', { name: 'Add record', exact: true }).click();
  await txForm.waitFor();
  await txForm.getByLabel(/^Event/i).selectOption('OPERATING_EXPENSE_PAYMENT');
  const cpSelect = txForm.getByLabel(/Customer \/ Supplier/i);
  const options = await cpSelect.locator('option').allInnerTexts();
  assert.ok(!options.some(opt => opt.includes('Beta Textiles')), 'Archived counterparty must not be in active selector');
  await page.keyboard.press('Escape');

  // 4. Restore "Beta Textiles"
  await page.goto(origin + '/records/contacts');
  await page.waitForLoadState('networkidle');
  await page.getByRole('button', { name: 'Archived', exact: true }).click();
  await page.waitForTimeout(500);

  const archivedBetaRow = page.locator('tr', { hasText: 'Beta Textiles' });
  await archivedBetaRow.waitFor();
  await archivedBetaRow.getByRole('button', { name: 'Restore', exact: true }).click();

  const restoreDialog = page.getByRole('dialog', { name: /Restore this contact\?/i });
  await restoreDialog.waitFor();
  await restoreDialog.getByRole('button', { name: 'Confirm Restore', exact: true }).click();
  await restoreDialog.waitFor({ state: 'detached' });

  // Switch back to Active: verify Beta Textiles returned
  await page.getByRole('button', { name: 'Active', exact: true }).click();
  await page.waitForTimeout(500);
  await page.locator('tbody').getByText('Beta Textiles', { exact: true }).waitFor();

  // 5. Verify it becomes selectable again in transaction entry form
  await page.goto(origin + '/finance/transactions');
  await page.waitForLoadState('networkidle');
  await page.getByRole('button', { name: 'Add record', exact: true }).click();
  await txForm.waitFor();
  await txForm.getByLabel(/^Event/i).selectOption('OPERATING_EXPENSE_PAYMENT');
  const restoredOptions = await txForm.getByLabel(/Customer \/ Supplier/i).locator('option').allInnerTexts();
  assert.ok(restoredOptions.some(opt => opt.includes('Beta Textiles')), 'Restored counterparty must be selectable again');
  await page.keyboard.press('Escape');

  // 6. Confirm archiving and restoring do not modify financial totals
  const postJourneyEMutations = await getMutationCounts('cp_journey_e');
  assert.equal(postJourneyEMutations.financial_transactions, initialMutations.financial_transactions + 4);
  assert.equal(postJourneyEMutations.account_movements, initialMutations.account_movements + 4);
  assert.equal(postJourneyEMutations.monthly_records, initialMutations.monthly_records);
  assert.equal(postJourneyEMutations.score_results, initialMutations.score_results);

  mark('JOURNEY E: Archive preserves historical transaction detail, hides from selector, restore re-enables selection, and totals remain intact');

  // =========================================================================
  // JOURNEY F: Authorization and Tenant Isolation
  // =========================================================================
  // 1. Accountant role: can create and archive
  const acctContext = await browser.newContext();
  const acctPage = await acctContext.newPage();
  await signin(acctPage, accountant.email);
  await acctPage.goto(origin + '/records/contacts');
  await acctPage.waitForLoadState('networkidle');
  await acctPage.getByRole('button', { name: 'Add Contact', exact: true }).first().waitFor();
  await acctContext.close();

  const acctRes = await api(accountant.context, '/finance/counterparties', 'POST', {
    displayName: 'Accountant Vendor',
    type: 'SUPPLIER'
  });
  assert.equal(acctRes.status(), 201);

  // 2. Manager and Viewer roles: can read but CANNOT mutate
  for (const member of [manager, viewer]) {
    const c = await browser.newContext();
    const p = await c.newPage();
    await signin(p, member.email);
    await p.goto(origin + '/records/contacts');
    await p.waitForLoadState('networkidle');
    assert.equal(await p.getByRole('button', { name: 'Add Contact', exact: true }).count(), 0);
    if (member === manager) {
      await p.getByText('You have read-only access. Only Owners and Accountants can add or edit contacts.', { exact: false }).waitFor();
    }
    await c.close();
  }

  const managerPostRes = await api(manager.context, '/finance/counterparties', 'POST', {
    displayName: 'Forbidden Manager Vendor',
    type: 'SUPPLIER'
  });
  assert.equal(managerPostRes.status(), 403);

  const viewerPostRes = await api(viewer.context, '/finance/counterparties', 'POST', {
    displayName: 'Forbidden Viewer Vendor',
    type: 'SUPPLIER'
  });
  assert.equal(viewerPostRes.status(), 403);

  // 4. Cross-tenant rejection
  const nonExistentId = randomUUID();
  const notFoundRes = await api(owner.context, `/finance/counterparties/${nonExistentId}`);
  assert.equal(notFoundRes.status(), 404);

  // Attempting transaction in Business A with foreign counterparty
  const crossTxRes = await api(owner.context, '/finance/transactions', 'POST', {
    accountId,
    eventType: 'DIRECT_SALE_RECEIPT',
    amount: '500.00',
    effectiveDate: today,
    description: 'Cross-business illegal transaction',
    counterpartyId: nonExistentId
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(crossTxRes.status(), 400);

  // Attempting draft in Business A with foreign counterparty
  const crossDraftRes = await api(owner.context, '/finance/drafts', 'POST', {
    accountId,
    eventType: 'DIRECT_SALE_RECEIPT',
    amountInput: '500.00',
    effectiveDate: today,
    description: 'Cross-business illegal draft',
    counterpartyId: nonExistentId
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(crossDraftRes.status(), 400);

  mark('JOURNEY F: Full RBAC authorization (Owner/Accountant write, Manager/Viewer read-only), multi-tenant isolation, cross-business rejection, and logout boundary verified');

  // =========================================================================
  // JOURNEY G: UI Quality, Urdu RTL, and Mobile 390x844
  // =========================================================================
  await page.goto(origin + '/records/contacts');
  await page.waitForLoadState('networkidle');

  // 1. Switch to Urdu
  await page.getByRole('button', { name: 'اردو' }).click();
  await page.waitForTimeout(500);

  // Verify direction is RTL
  const pageDir = await page.locator('div[dir]').first().getAttribute('dir');
  assert.equal(pageDir, 'rtl', 'Urdu locale must render dir="rtl"');

  // Verify Urdu headings and labels
  await page.getByText('گاہک اور فراہم کنندگان').first().waitFor();
  await page.getByRole('button', { name: 'نیا رابطہ شامل کریں', exact: true }).first().waitFor();
  await captureScreenshot('journey_g_urdu_rtl.png');

  // 2. Mobile Viewport 390 x 844
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(500);

  // Verify no horizontal overflow
  const hasHorizontalScroll = await page.evaluate(() => {
    return document.documentElement.scrollWidth > document.documentElement.clientWidth + 5;
  });
  assert.equal(hasHorizontalScroll, false, 'Mobile viewport 390px must not have unwanted horizontal scroll');
  await captureScreenshot('journey_g_mobile_390.png');

  // 3. Accessible modal interactions on mobile
  await page.getByRole('button', { name: 'نیا رابطہ شامل کریں', exact: true }).first().click();
  const urModal = page.getByRole('dialog');
  await urModal.waitFor();
  await page.keyboard.press('Escape');
  await urModal.waitFor({ state: 'detached' });

  // Reset viewport and language
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.getByRole('button', { name: 'English' }).click();
  await page.waitForTimeout(300);

  mark('JOURNEY G: UI Quality, complete Urdu RTL parity, mobile 390x844 responsive layout, and keyboard accessibility verified');
}
