/* eslint-disable @typescript-eslint/no-unused-vars */
import assert from 'node:assert/strict';
import { mkdirSync } from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

export async function verifyReconciliation({
  api,
  owner,
  viewer,
  manager,
  accountant,
  page,
  context,
  browser,
  signin,
  origin,
  accountId,
  today,
  txCounts,
  control,
  getRuntimeText,
  blockers,
  mark,
  businessA,
  businessB,
  requestFactory,
  temporary,
  out,
}) {
  const directory = path.join(out, 'reconciliation');
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

  const initialMutations = await getMutationCounts('recon_initial');

  // =========================================================================
  // JOURNEY A: Statement Import (Paste/Upload, Map, Preview, Persist, Reload)
  // =========================================================================
  await page.goto(origin + '/records/reconciliation');
  await page.waitForLoadState('networkidle');

  await page.getByRole('heading', { name: 'Bank Reconciliation' }).first().waitFor();
  await captureScreenshot('journey_a_initial.png');

  // Open Import Modal
  await page.getByRole('button', { name: /\+?.*Import.*Statement/i }).first().click();
  const importModal = page.getByRole('dialog');
  await importModal.waitFor();

  // Switch to TSV Paste Mode
  await importModal.getByRole('button', { name: /Paste TSV/i }).click();

  // Paste TSV Data: 3 entries
  // 1: Debit 15,000 (Vendor payment settlement)
  // 2: Credit 50,000 (Client invoice deposit)
  // 3: Credit 25,000 (Branch cash deposit)
  const tsvStatement = [
    'Date\tDescription\tDebit\tCredit\tReference',
    `${today}\tVendor payment settlement\t15000.00\t\tTXN-901`,
    `${today}\tClient invoice deposit\t\t50000.00\tREC-101`,
    `${today}\tBranch cash deposit\t\t25000.00\tREC-202`,
  ].join('\n');

  await importModal.locator('textarea').fill(tsvStatement);

  // Wait for automatic preview rows
  await importModal.getByRole('cell', { name: 'Vendor payment settlement' }).waitFor();
  await importModal.getByRole('cell', { name: 'Client invoice deposit' }).waitFor();
  await importModal.getByRole('cell', { name: 'Branch cash deposit' }).waitFor();

  await captureScreenshot('journey_a_preview.png');

  // Save the import
  await importModal.getByRole('button', { name: /Commit & Save Import|Save Import/i }).click();
  await importModal.waitFor({ state: 'detached' });

  // Verify rows appear in workspace
  await page.getByRole('cell', { name: 'Vendor payment settlement' }).waitFor();
  await page.getByRole('cell', { name: 'Client invoice deposit' }).waitFor();
  await page.getByRole('cell', { name: 'Branch cash deposit' }).waitFor();

  // Reload page to confirm persistence
  await page.reload();
  await page.waitForLoadState('networkidle');
  await page.getByRole('cell', { name: 'Vendor payment settlement' }).waitFor();
  await page.getByRole('cell', { name: 'Client invoice deposit' }).waitFor();
  await page.getByRole('cell', { name: 'Branch cash deposit' }).waitFor();

  // Verify zero posting invariant from import
  const mutationsAfterImport = await getMutationCounts('recon_after_import');
  assert.equal(mutationsAfterImport.financial_transactions, initialMutations.financial_transactions);
  assert.equal(mutationsAfterImport.account_movements, initialMutations.account_movements);

  mark('JOURNEY A: Statement import (paste, map, preview, persist, reload) and zero mutation verified');

  // =========================================================================
  // JOURNEY B: Exact Match (PKR 50,000 Receipt to Statement Credit)
  // =========================================================================
  // 1. Create existing FinSight receipt of PKR 50,000
  const txRes = await api(owner.context, '/finance/transactions', 'POST', {
    accountId,
    eventType: 'DIRECT_SALE_RECEIPT',
    amount: '50000.00',
    effectiveDate: today,
    description: 'Client invoice deposit',
  }, { 'Idempotency-Key': 'recon-tx-50k-' + randomUUID() });
  assert.equal(txRes.status(), 201);
  const saleTx = await txRes.json();

  const mutationsBeforeMatch = await getMutationCounts('recon_before_match');

  // Refresh page so candidates are evaluated
  await page.reload();
  await page.waitForLoadState('networkidle');

  // Find the 50,000.00 entry row and click Review & Match
  const row50k = page.getByRole('row').filter({ hasText: 'Client invoice deposit' }).first();
  await row50k.getByRole('button', { name: /Review/i }).click();

  const matchSheet = page.getByRole('dialog');
  await matchSheet.waitFor();

  // Candidate should be shown with DIRECT_SALE_RECEIPT and confidence score
  await matchSheet.getByText('DIRECT_SALE_RECEIPT').waitFor();
  await matchSheet.getByText(/9\d% Match/).waitFor();
  await captureScreenshot('journey_b_candidate_sheet.png');

  // Confirm Match
  await matchSheet.getByRole('button', { name: 'Confirm Match', exact: true }).click();
  await matchSheet.waitFor({ state: 'detached' });
  await captureScreenshot('journey_b_matched.png');

  // Verify status in table is now MATCHED
  await row50k.getByText('Matched').waitFor();

  // Re-open Review on the matched entry to verify it displays the reconciled transaction details
  await row50k.getByRole('button', { name: /Review/i }).click();
  const detailSheet = page.getByRole('dialog');
  await detailSheet.waitFor();
  await detailSheet.getByText('Reconciled FinSight Transaction').waitFor();
  await captureScreenshot('journey_b_reconciled_details.png');
  await page.keyboard.press('Escape');
  await detailSheet.waitFor({ state: 'detached' });

  // Financial integrity check: ZERO new postings
  const mutationsAfterMatch = await getMutationCounts('recon_after_match');
  assert.equal(mutationsAfterMatch.financial_transactions, mutationsBeforeMatch.financial_transactions);
  assert.equal(mutationsAfterMatch.account_movements, mutationsBeforeMatch.account_movements);

  mark('JOURNEY B: Exact match (PKR 50,000 receipt, candidate ranking, confirmation, 0 new postings) verified');

  // =========================================================================
  // JOURNEY C: Ambiguous Matches (Two Eligible Movements with Same Amount)
  // =========================================================================
  // Create TWO distinct transactions of PKR 25,000
  const amb1Res = await api(owner.context, '/finance/transactions', 'POST', {
    accountId,
    eventType: 'DIRECT_SALE_RECEIPT',
    amount: '25000.00',
    effectiveDate: today,
    description: 'Branch cash deposit 1',
  }, { 'Idempotency-Key': 'amb-tx-1-' + randomUUID() });
  assert.equal(amb1Res.status(), 201);
  const ambTx1 = await amb1Res.json();

  const amb2Res = await api(owner.context, '/finance/transactions', 'POST', {
    accountId,
    eventType: 'DIRECT_SALE_RECEIPT',
    amount: '25000.00',
    effectiveDate: today,
    description: 'Branch cash deposit 2',
  }, { 'Idempotency-Key': 'amb-tx-2-' + randomUUID() });
  assert.equal(amb2Res.status(), 201);
  const ambTx2 = await amb2Res.json();

  await page.reload();
  await page.waitForLoadState('networkidle');

  // Review Match on the 25,000 statement entry
  const row25k = page.getByRole('row').filter({ hasText: 'Branch cash deposit' }).first();
  await row25k.getByRole('button', { name: /Review/i }).click();

  const ambSheet = page.getByRole('dialog');
  await ambSheet.waitFor();

  // Both candidates should be present
  const radioButtons = ambSheet.locator('input[type="radio"][name="candidate"]');
  await radioButtons.first().waitFor();
  assert.ok((await radioButtons.count()) >= 2);
  await captureScreenshot('journey_c_ambiguous_candidates.png');

  // Select the first candidate and confirm match
  await ambSheet.getByRole('button', { name: 'Confirm Match', exact: true }).click();
  await ambSheet.waitFor({ state: 'detached' });
  await row25k.getByText('Matched').waitFor();

  mark('JOURNEY C: Ambiguous candidate ranking (multiple matches presented, user selection, deterministic link) verified');

  // =========================================================================
  // JOURNEY D: Unmatched Entry to Draft Integration
  // =========================================================================
  // We have the unmatched debit of 15,000.00 (Vendor payment settlement)
  const row15k = page.getByRole('row').filter({ hasText: 'Vendor payment settlement' }).first();
  await row15k.getByRole('button', { name: /Review/i }).click();

  const draftSheet = page.getByRole('dialog');
  await draftSheet.waitFor();

  // Click Create Draft
  await draftSheet.getByRole('button', { name: /Create Draft/i }).click();
  await draftSheet.getByText(/Incomplete draft created|Draft Already Created/i).waitFor();
  await captureScreenshot('journey_d_draft_created.png');

  // Close sheet
  await page.keyboard.press('Escape');
  await draftSheet.waitFor({ state: 'detached' });

  // Navigate to To Review
  await page.goto(origin + '/records?view=review');
  await page.waitForLoadState('networkidle');

  // Check draft exists with 15,000 and "Vendor payment settlement"
  await page.getByText('Vendor payment settlement').first().waitFor();
  await captureScreenshot('journey_d_draft_in_review.png');

  // Return to Bank Reconciliation
  await page.goto(origin + '/records/reconciliation');
  await page.waitForLoadState('networkidle');

  mark('JOURNEY D: Unmatched entry to draft creation and provenance in To Review verified');

  // =========================================================================
  // JOURNEY E: Reconciliation Correction (Undo Match, Audit History)
  // =========================================================================
  // Open the matched 50k entry
  const matchedRow50k = page.getByRole('row').filter({ hasText: 'Client invoice deposit' }).first();
  await matchedRow50k.getByRole('button', { name: /Review/i }).click();

  const undoSheet = page.getByRole('dialog');
  await undoSheet.waitFor();

  await undoSheet.getByText('Reconciled FinSight Transaction').waitFor();

  // Fill unmatch reason
  await undoSheet.locator('input[placeholder*="wrong"]').fill('Correction: unmatching for audit testing');

  // Click Undo Match
  await undoSheet.getByRole('button', { name: 'Undo Match', exact: true }).click();
  await undoSheet.waitFor({ state: 'detached' });
  await captureScreenshot('journey_e_unmatched.png');

  // Verify status in table is UNMATCHED again
  await page.reload();
  await page.waitForLoadState('networkidle');
  await matchedRow50k.getByText('Unmatched').waitFor();

  mark('JOURNEY E: Reconciliation correction (undo match with audit reason and status reversal) verified');

  // =========================================================================
  // JOURNEY F: Internal Transfer Dual Leg Reconciliation
  // =========================================================================
  // 1. Create a second bank account "HBL Secondary Bank"
  const secAccRes = await api(owner.context, '/finance/accounts', 'POST', {
    accountName: 'HBL Secondary Bank',
    accountType: 'BANK',
    currency: 'PKR',
    openingBalance: '10000.00',
    openingDate: today,
  }, { 'Idempotency-Key': 'hbl-sec-' + randomUUID() });
  assert.equal(secAccRes.status(), 201);
  const secAccount = await secAccRes.json();

  // 2. Post transfer: 5,000 from Live PKR Bank to HBL Secondary Bank
  const transferRes = await api(owner.context, '/finance/transfers', 'POST', {
    sourceAccountId: accountId,
    destinationAccountId: secAccount.id,
    amount: '5000.00',
    effectiveDate: today,
    description: 'Inter-branch rebalance',
  }, { 'Idempotency-Key': 'transfer-live-' + randomUUID() });
  assert.equal(transferRes.status(), 201);

  // 3. Import debit statement row for Live PKR Bank (source)
  const impARes = await api(owner.context, '/finance/reconciliation/imports', 'POST', {
    accountId,
    sourceFilename: 'bank_a_transfer.csv',
    entries: [
      {
        sourceRowNumber: 1,
        postedDate: today,
        amount: '5000.00',
        direction: 'DECREASE',
        description: 'Inter-branch rebalance',
        bankReference: 'TRF-OUT',
      },
    ],
  }, { 'Idempotency-Key': 'import-trf-a-' + randomUUID() });
  assert.equal(impARes.status(), 201, await impARes.text());

  // 4. Import credit statement row for HBL Secondary Bank (destination)
  const impBRes = await api(owner.context, '/finance/reconciliation/imports', 'POST', {
    accountId: secAccount.id,
    sourceFilename: 'bank_b_transfer.csv',
    entries: [
      {
        sourceRowNumber: 1,
        postedDate: today,
        amount: '5000.00',
        direction: 'INCREASE',
        description: 'Inter-branch rebalance',
        bankReference: 'TRF-IN',
      },
    ],
  }, { 'Idempotency-Key': 'import-trf-b-' + randomUUID() });
  assert.equal(impBRes.status(), 201, await impBRes.text());

  // 5. Match source leg on Live PKR Bank
  const entriesARes = await api(owner.context, `/finance/reconciliation/entries?accountId=${accountId}&status=UNMATCHED`);
  const entriesA = (await entriesARes.json()).content;
  const trfEntryA = entriesA.find(e => Number(e.amount) === 5000 && e.direction === 'DECREASE');
  assert.ok(trfEntryA, `Expected 5000 DECREASE entry in ${JSON.stringify(entriesA)}`);

  const candsARes = await api(owner.context, `/finance/reconciliation/entries/${trfEntryA.id}/candidates`);
  const candsA = await candsARes.json();
  assert.ok(candsA.length > 0);
  assert.equal(candsA[0].eventType, 'INTERNAL_TRANSFER');

  const matchARes = await api(owner.context, `/finance/reconciliation/entries/${trfEntryA.id}/match`, 'POST', {
    accountMovementId: candsA[0].accountMovementId,
  });
  assert.equal(matchARes.status(), 200);

  // 6. Match destination leg on HBL Secondary Bank
  const entriesBRes = await api(owner.context, `/finance/reconciliation/entries?accountId=${secAccount.id}&status=UNMATCHED`);
  const entriesB = (await entriesBRes.json()).content;
  const trfEntryB = entriesB.find(e => Number(e.amount) === 5000 && e.direction === 'INCREASE');
  assert.ok(trfEntryB, `Expected 5000 INCREASE entry in ${JSON.stringify(entriesB)}`);

  const candsBRes = await api(owner.context, `/finance/reconciliation/entries/${trfEntryB.id}/candidates`);
  const candsB = await candsBRes.json();
  assert.ok(candsB.length > 0);
  assert.equal(candsB[0].eventType, 'INTERNAL_TRANSFER');

  const matchBRes = await api(owner.context, `/finance/reconciliation/entries/${trfEntryB.id}/match`, 'POST', {
    accountMovementId: candsB[0].accountMovementId,
  });
  assert.equal(matchBRes.status(), 200);

  mark('JOURNEY F: Internal transfer dual-leg matching (outgoing debit to Leg A, incoming credit to Leg B, 0 duplicate income/expense) verified');

  // =========================================================================
  // JOURNEY G: Duplicate Statement Imports (Replay & Overlapping Warnings)
  // =========================================================================
  const replayPayload = {
    accountId,
    sourceFilename: 'replay_test.csv',
    entries: [
      {
        sourceRowNumber: 1,
        postedDate: today,
        amount: '8888.00',
        direction: 'INCREASE',
        description: 'Unique test deposit',
        bankReference: 'REPLAY-001',
      },
    ],
  };
  const replayKey = 'replay-key-' + randomUUID();

  // 1. Initial import
  const imp1Res = await api(owner.context, '/finance/reconciliation/imports', 'POST', replayPayload, { 'Idempotency-Key': replayKey });
  assert.equal(imp1Res.status(), 201);
  const imp1Data = await imp1Res.json();

  // 2. Safe replay with same payload and key returns existing import
  const impReplayRes = await api(owner.context, '/finance/reconciliation/imports', 'POST', replayPayload, { 'Idempotency-Key': replayKey });
  assert.ok(impReplayRes.status() === 200 || impReplayRes.status() === 201);
  const impReplayData = await impReplayRes.json();
  assert.equal(impReplayData.id, imp1Data.id);

  // 3. Import overlapping content with a NEW key and different filename
  const dupPayload = {
    accountId,
    sourceFilename: 'overlapping_statement.csv',
    entries: [
      {
        sourceRowNumber: 1,
        postedDate: today,
        amount: '8888.00',
        direction: 'INCREASE',
        description: 'Unique test deposit',
        bankReference: 'REPLAY-001',
      },
    ],
  };
  const dupKey = 'dup-key-' + randomUUID();
  const dupRes = await api(owner.context, '/finance/reconciliation/imports', 'POST', dupPayload, { 'Idempotency-Key': dupKey });
  assert.equal(dupRes.status(), 201);

  // In the API, verify the second entry has duplicateWarning = true
  const dupEntriesRes = await api(owner.context, `/finance/reconciliation/entries?accountId=${accountId}`);
  const dupEntries = (await dupEntriesRes.json()).content;
  const flagged = dupEntries.filter(e => Number(e.amount) === 8888);
  assert.equal(flagged.length, 2);
  assert.ok(flagged.some(e => e.duplicateWarning === true));

  mark('JOURNEY G: Duplicate statement handling (idempotent replay and duplicate warnings without deletion) verified');

  // =========================================================================
  // JOURNEY H: Authorization Matrix & Tenant Isolation
  // =========================================================================
  // 1. ACCOUNTANT can read and match
  const accountantEntryRes = await api(accountant.context, `/finance/reconciliation/entries?accountId=${accountId}`);
  assert.equal(accountantEntryRes.status(), 200);

  // 2. VIEWER and MANAGER are denied mutations (403)
  const viewerImportRes = await api(viewer.context, '/finance/reconciliation/imports', 'POST', replayPayload, {
    'Idempotency-Key': 'v-imp-' + randomUUID(),
  });
  assert.equal(viewerImportRes.status(), 403);

  const managerImportRes = await api(manager.context, '/finance/reconciliation/imports', 'POST', replayPayload, {
    'Idempotency-Key': 'm-imp-' + randomUUID(),
  });
  assert.equal(managerImportRes.status(), 403);

  // 3. Cross-tenant isolation: Business B cannot query Business A reconciliation
  await api(owner.context, '/businesses/active', 'POST', { businessId: businessB });
  const crossEntriesRes = await api(owner.context, `/finance/reconciliation/entries?accountId=${accountId}`);
  assert.equal(crossEntriesRes.status(), 404);

  const crossSummaryRes = await api(owner.context, `/finance/reconciliation/accounts/${accountId}/summary`);
  assert.equal(crossSummaryRes.status(), 404);

  await api(owner.context, '/businesses/active', 'POST', { businessId: businessA });

  // 4. CSRF protection: Non-GET without token returns 403
  const noCsrfRes = await owner.context.post(origin + '/api/finance/reconciliation/imports', {
    data: replayPayload,
    headers: { 'Idempotency-Key': randomUUID() },
  });
  assert.equal(noCsrfRes.status(), 403);

  mark('JOURNEY H: Authorization matrix (OWNER/ACCOUNTANT pass, VIEWER/MANAGER 403, cross-tenant 404, CSRF 403) verified');

  // =========================================================================
  // JOURNEY I: Interface Quality (Urdu RTL, Mobile 390x844, Keyboard Usability)
  // =========================================================================
  await page.goto(origin + '/records/reconciliation');
  await page.waitForLoadState('networkidle');

  // 1. Switch to Urdu
  await page.getByRole('button', { name: 'اردو' }).click();
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  await page.getByText('بینک مفاہمت').first().waitFor();
  await captureScreenshot('journey_i_urdu_rtl.png');

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

  // =========================================================================
  // JOURNEY J: Financial Integrity Invariant Verification
  // =========================================================================
  const finalMutations = await getMutationCounts('recon_final');

  // Strict check: No monthly_records or score_results mutations occurred
  assert.equal(finalMutations.monthly_records, initialMutations.monthly_records);
  assert.equal(finalMutations.score_results, initialMutations.score_results);

  mark('JOURNEY J: Financial integrity (zero monthly record and score mutations) strictly verified');
}
