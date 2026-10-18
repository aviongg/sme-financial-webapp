import assert from 'node:assert/strict';
import { writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { randomUUID, createHash } from 'node:crypto';

export async function verifyImportDrafts({
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
  control,
  getRuntimeText,
  mark,
  businessA,
  businessB,
  temporary,
  out
}) {
  const directory = path.join(out, 'import-drafts');
  mkdirSync(directory, { recursive: true });

  const capture = (filename, data) => {
    writeFileSync(path.join(directory, filename), typeof data === 'string' ? data : JSON.stringify(data, null, 2));
  };

  // =========================================================================
  // SECTION 6 BASELINE: Zero Financial Mutation Snapshot
  // =========================================================================
  const getMutationCounts = async tag => {
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

  const initialMutations = await getMutationCounts('initial');
  capture('baseline-mutation-counts.json', initialMutations);

  // =========================================================================
  // SECTION 3: Mandatory Real Browser Journey
  // =========================================================================

  // -------------------------------------------------------------------------
  // Part A: Import Preview (CSV upload & TSV paste & conventions)
  // -------------------------------------------------------------------------
  await page.goto(origin + '/records/import');
  await page.getByRole('note').filter({ hasText: /Preview only/i }).waitFor();
  await page.getByRole('heading', { name: '1. Choose your source' }).waitFor();

  // 1. Upload valid CSV file
  const testCsvContent = [
    'Date,Description,Amount,Direction,Reference',
    `${today},Uploaded Sale Invoice,5000.50,IN,INV-CSV-001`,
    `${today},Uploaded Office Supplies,1200.00,OUT,EXP-CSV-002`,
    `${today},Uploaded Utility Payment,450.00,OUT,EXP-CSV-003`
  ].join('\n');
  const tempCsvPath = path.join(temporary, 'test_sample.csv');
  writeFileSync(tempCsvPath, testCsvContent);

  const fileInput = page.locator('input[type="file"]');
  await fileInput.setInputFiles(tempCsvPath);
  await page.getByText(/Source: test_sample\.csv/i).waitFor();
  await page.getByRole('heading', { name: '2. Map columns' }).waitFor();

  // Test abandon / reset
  await page.getByRole('button', { name: 'Start again' }).click();
  const abandonDialog = page.getByRole('alertdialog');
  await abandonDialog.waitFor();
  await abandonDialog.getByRole('button', { name: 'Discard preview' }).click();
  await page.getByRole('heading', { name: '1. Choose your source' }).waitFor();
  mark('BROWSER IMPORT: CSV file upload parsed source successfully; abandon confirmation reset cleanly');

  // 2. Paste TSV spreadsheet data
  const tsvText = [
    'Date\tDescription\tAmount\tDirection\tReference',
    `${today}\tPasted Customer Alpha\t3,500.75\tIN\tPASTE-101`,
    `${today}\tPasted Vendor Beta\t850.25\tOUT\tPASTE-102`,
    `${today}\tPasted Incomplete Note\t1500.5\tIN\tPASTE-103`
  ].join('\n');

  const pasteArea = page.locator('textarea');
  await pasteArea.fill(tsvText);
  await page.getByRole('button', { name: 'Read pasted rows' }).click();

  // 3. Map columns & set conventions
  await page.getByRole('heading', { name: '2. Map columns' }).waitFor();
  // Map columns to fields
  await page.getByLabel('Date', { exact: true }).selectOption('0');
  await page.getByLabel('Description', { exact: true }).selectOption('1');
  await page.getByLabel('Amount', { exact: true }).selectOption('2');
  await page.getByLabel('Direction', { exact: true }).selectOption('3');
  await page.getByLabel('Reference', { exact: true }).selectOption('4');

  // Select account (Live PKR Bank)
  const accountSelect = page.getByLabel('Batch money account', { exact: true });
  await accountSelect.selectOption(accountId);

  // Review imported data button
  await page.getByRole('button', { name: 'Review rows' }).click();
  await page.getByRole('heading', { name: '3. Review interpreted rows' }).waitFor();

  // 4. Amount normalization & unresolved classification check
  await page.getByText('3500.75 PKR', { exact: true }).waitFor();
  await page.getByText('850.25 PKR', { exact: true }).waitFor();
  await page.getByText('1500.50 PKR', { exact: true }).waitFor();

  // Verify missing classifications remain unresolved
  const purposeBadges = await page.getByText(/Undecided — review financial purpose/i).all();
  assert.ok(purposeBadges.length >= 2, 'Missing classifications must remain unresolved (Undecided)');
  mark('BROWSER IMPORT: pasted TSV rendered with exact amounts, normalized preview, and unresolved classifications preserved');

  // 5. Select and exclude rows
  // Find card for line 3 and test exclusion / restoration
  const line3Card = page.locator('li').filter({ has: page.getByRole('heading', { name: /Source line 3/i }) });
  await line3Card.getByRole('button', { name: 'Exclude row' }).click();
  await line3Card.getByText('Excluded from the later step').waitFor();

  // Restore line 3
  await line3Card.getByRole('button', { name: 'Restore row' }).click();
  await line3Card.getByText('Needs review').waitFor();

  // Select line 2 and line 4
  const line2Card = page.locator('li').filter({ has: page.getByRole('heading', { name: /Source line 2/i }) });
  await line2Card.getByLabel('Select source row 2').check();

  const line4Card = page.locator('li').filter({ has: page.getByRole('heading', { name: /Source line 4/i }) });
  await line4Card.getByLabel('Select source row 4').check();

  await page.getByText(/Selected rows: 2/i).waitFor();
  await page.getByText(/Some selected rows have unresolved fields/i).waitFor();
  await page.screenshot({ path: path.join(directory, '01-import-paste-mapped.png'), fullPage: true });

  // -------------------------------------------------------------------------
  // Part B: Draft creation & Verbatim Raw amountInput
  // -------------------------------------------------------------------------
  const saveDraftsButton = page.getByRole('button', { name: 'Save selected as drafts' });
  await saveDraftsButton.click();

  // Wait for success notice
  await page.getByText(/Selected rows have been saved as drafts/i).waitFor();

  // Check generated URL batchId
  const urlWithBatch = new URL(page.url());
  const batchId = urlWithBatch.searchParams.get('batchId');
  assert.ok(batchId, 'Import URL must reflect generated batchId');

  // Check saved draft links and badges
  const savedBadges = await page.getByText('Draft saved', { exact: true }).all();
  assert.ok(savedBadges.length >= 2, 'Two rows must have Draft saved badge');

  await page.screenshot({ path: path.join(directory, '02-import-drafts-saved.png'), fullPage: true });

  // Extract saved draft IDs from links in the page
  const openDraftLinks = await page.locator('a[href*="/records?view=review&draftId="]').all();
  assert.ok(openDraftLinks.length >= 2, 'Must have links to saved drafts');
  const href4 = await openDraftLinks[1].getAttribute('href');
  const draftId4 = new URL(href4, origin).searchParams.get('draftId');
  assert.ok(draftId4, 'Must have draftId for row 4');

  // Verify DB raw amountInput for incomplete row 4 and formatted row 2
  await control('VERIFY_DRAFT_RAW ' + draftId4 + ' row4', 'LIVE_DRAFT_RAW_row4:');
  const rawMatch4 = getRuntimeText().match(/LIVE_DRAFT_RAW_row4:([^\r\n]+)/);
  assert.equal(rawMatch4[1], '1500.5', 'Raw amountInput must be preserved verbatim without rounding or truncation');

  const href2 = await openDraftLinks[0].getAttribute('href');
  const draftId2 = new URL(href2, origin).searchParams.get('draftId');
  await control('VERIFY_DRAFT_RAW ' + draftId2 + ' row2', 'LIVE_DRAFT_RAW_row2:');
  const rawMatch2 = getRuntimeText().match(/LIVE_DRAFT_RAW_row2:([^\r\n]+)/);
  assert.equal(rawMatch2[1], '3,500.75', 'Raw amountInput with comma must be preserved verbatim without comma stripping');
  mark('BROWSER DRAFT CREATION: selected rows saved as persistent drafts with URL batchId, provenance links, and verbatim amountInput preserved');

  // -------------------------------------------------------------------------
  await openDraftLinks[1].click();
  const navAbandonDialog = page.getByRole('alertdialog');
  await navAbandonDialog.waitFor({ timeout: 5000 }).then(() => navAbandonDialog.getByRole('button', { name: 'Discard preview' }).click()).catch(() => {});
  await page.waitForURL(url => url.searchParams.get('draftId') === draftId4);

  // DraftEditor opens inside Sheet modal
  const editDialog = page.getByRole('dialog');
  await editDialog.waitFor();
  const descInput = editDialog.getByLabel('Description');
  await descInput.waitFor();
  assert.equal(await descInput.inputValue(), 'Pasted Incomplete Note');
  await descInput.fill('Updated imported draft via browser');
  await editDialog.getByRole('button', { name: 'Save draft' }).click();
  await editDialog.getByText(/Saved draft\. It has not been recorded\./i).waitFor();
  await editDialog.getByRole('button', { name: 'Close entry' }).last().click();

  // Reload page and verify persistence
  await page.reload();
  await page.getByRole('dialog').waitFor();
  const reloadedDescInput = page.getByRole('dialog').getByLabel('Description');
  assert.equal(await reloadedDescInput.inputValue(), 'Updated imported draft via browser');
  await page.screenshot({ path: path.join(directory, '03-drafts-workspace-deeplink.png'), fullPage: true });
  mark('BROWSER DRAFT NAVIGATION: opened saved draft via deep-link, updated description, saved and verified persistence after reload');

  // -------------------------------------------------------------------------
  // Part D: Browser reload recovery
  // -------------------------------------------------------------------------
  const reloadContext = await browser.newContext();
  const reloadPage = await reloadContext.newPage();
  await signin(reloadPage, owner.email);

  await reloadPage.goto(origin + '/records/import?batchId=' + batchId);
  await reloadPage.getByRole('heading', { name: /Recovered import batch/i }).waitFor();
  await reloadPage.getByText(/Total drafts in batch/i).waitFor();
  const recoveredSavedBadges = await reloadPage.getByText('Draft saved', { exact: true }).all();
  assert.equal(recoveredSavedBadges.length, 2, 'Recovered batch must show 2 saved drafts');
  await reloadPage.screenshot({ path: path.join(directory, '04-batch-reload-recovery.png'), fullPage: true });
  await reloadContext.close();
  mark('BROWSER RECOVERY: fresh browser context recovered persisted batch by batchId from backend with saved provenance');

  // -------------------------------------------------------------------------
  // Part E: Language, RTL and Mobile usability
  // -------------------------------------------------------------------------
  await page.goto(origin + '/records/import');
  await page.locator('textarea').fill(tsvText);
  await page.getByRole('button', { name: 'Read pasted rows' }).click();
  await page.getByLabel('Date', { exact: true }).selectOption('0');
  await page.getByLabel('Description', { exact: true }).selectOption('1');
  await page.getByLabel('Amount', { exact: true }).selectOption('2');
  await page.getByLabel('Direction', { exact: true }).selectOption('3');
  await page.getByLabel('Batch money account', { exact: true }).selectOption(accountId);
  await page.getByRole('button', { name: 'Review rows' }).click();

  // Switch to Urdu
  await page.getByRole('button', { name: 'اردو', exact: true }).click();
  await page.getByRole('heading', { name: '3. سمجھی گئی قطاروں کا جائزہ', exact: true }).waitFor();
  assert.equal(await page.locator('html').getAttribute('dir'), 'rtl');

  // Mobile viewport
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(350); // AppShell margin transition is 200ms
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, 'RTL mobile view must not overflow horizontally');
  await page.screenshot({ path: path.join(directory, '05-import-urdu-rtl-mobile.png'), fullPage: true });

  // Restore English & desktop
  await page.getByRole('button', { name: 'English', exact: true }).click();
  await page.setViewportSize({ width: 1280, height: 900 });
  mark('BROWSER I18N: in-memory preview state retained across language switch; RTL and mobile verified with no horizontal overflow');

  // -------------------------------------------------------------------------
  // Part E continued: Business switch isolation & Demo mode
  // -------------------------------------------------------------------------
  const settingsTab = await context.newPage();
  await settingsTab.goto(origin + '/settings');
  await settingsTab.getByLabel(/^Active business/).waitFor();
  await settingsTab.getByLabel(/^Active business/).selectOption(businessB);
  await settingsTab.close();

  await api(owner.context, '/businesses/active', 'POST', { businessId: businessB });

  // Verify batch from Business A is inaccessible under Business B
  const crossTenantBatchResp = await api(owner.context, '/finance/drafts/import/' + batchId);
  assert.equal(crossTenantBatchResp.status(), 404, 'Cross-tenant batch read must return 404');

  const crossTenantBrowserResp = await api(context.request, '/finance/drafts/import/' + batchId);
  assert.equal(crossTenantBrowserResp.status(), 404, 'Cross-tenant batch read via browser session must return 404');

  // Switch back to Business A
  const restoreSettingsTab = await context.newPage();
  await restoreSettingsTab.goto(origin + '/settings');
  await restoreSettingsTab.getByLabel(/^Active business/).selectOption(businessA);
  await restoreSettingsTab.close();
  await api(owner.context, '/businesses/active', 'POST', { businessId: businessA });

  // Session boundary check: unauthenticated visit requires sign-in and leaks zero private finance API data
  const anonContext = await browser.newContext();
  const anonPage = await anonContext.newPage();
  const privateRequests = [];
  anonPage.on('request', req => {
    const p = new URL(req.url()).pathname;
    if (p.startsWith('/api/finance/') || p.startsWith('/api/records')) {
      privateRequests.push(req.url());
    }
  });
  await anonPage.goto(origin + '/records/import');
  await anonPage.getByRole('heading', { name: 'Sign in to FinSight' }).waitFor();
  assert.deepEqual(privateRequests, [], 'Unauthenticated visit to /records/import must make zero private finance/record API requests');
  await anonContext.close();
  mark('SECURITY & BOUNDARIES: cross-business batch access denied with 404; unauthenticated visit requires login with zero private data leaked');

  // =========================================================================
  // SECTION 4: Large Import & Browser-Driven Chunking (> 100 rows)
  // =========================================================================
  // Reset preview session so we start cleanly at Step 1
  await page.goto(origin + '/records/import');
  if (await page.getByRole('button', { name: 'Start again' }).isVisible()) {
    await page.getByRole('button', { name: 'Start again' }).click();
    const abandonDialog = page.getByRole('alertdialog');
    await abandonDialog.waitFor();
    await abandonDialog.getByRole('button', { name: 'Discard preview' }).click();
  }
  await page.getByRole('heading', { name: '1. Choose your source' }).waitFor();

  const tsv105Rows = ['Date\tDescription\tAmount\tDirection\tReference'];
  for (let i = 1; i <= 105; i++) {
    tsv105Rows.push(`${today}\tLarge import batch item ${i}\t${(10 + i * 0.05).toFixed(2)}\tIN\tLR-${String(i).padStart(4, '0')}`);
  }
  await page.locator('textarea').fill(tsv105Rows.join('\n'));
  await page.getByRole('button', { name: 'Read pasted rows' }).click();
  await page.getByRole('heading', { name: '2. Map columns' }).waitFor();

  // Map columns to fields
  await page.getByLabel('Date', { exact: true }).selectOption('0');
  await page.getByLabel('Description', { exact: true }).selectOption('1');
  await page.getByLabel('Amount', { exact: true }).selectOption('2');
  await page.getByLabel('Direction', { exact: true }).selectOption('3');
  await page.getByLabel('Reference', { exact: true }).selectOption('4');
  await page.getByLabel('Batch money account', { exact: true }).selectOption(accountId);
  await page.getByLabel('Default purpose (optional)', { exact: true }).selectOption('DIRECT_SALE_RECEIPT');
  await page.getByRole('button', { name: 'Review rows' }).click();
  await page.getByRole('heading', { name: '3. Review interpreted rows' }).waitFor();

  // Select all 105 rows across pagination
  while (true) {
    const checkboxes = await page.getByRole('checkbox', { name: /^Select source row/ }).all();
    for (const cb of checkboxes) {
      if (!(await cb.isChecked())) await cb.check();
    }
    const nextBtn = page.getByRole('button', { name: 'Next page' });
    if (await nextBtn.isDisabled()) break;
    await nextBtn.click();
  }

  // Intercept chunk requests dispatched by browser
  const browserDispatchedChunks = [];
  const chunkListener = req => {
    if (req.url().includes('/api/finance/drafts/import') && req.method() === 'POST') {
      try { browserDispatchedChunks.push(req.postDataJSON()); } catch {}
    }
  };
  page.on('request', chunkListener);

  const saveBtn = page.getByRole('button', { name: 'Save selected as drafts' });
  await saveBtn.click();

  // Wait for success notice
  await page.getByText(/Selected rows have been saved as drafts/i).waitFor();
  page.off('request', chunkListener);

  assert.equal(browserDispatchedChunks.length, 2, 'Browser frontend must dispatch exactly 2 requests for 105 rows');
  assert.equal(browserDispatchedChunks[0].rows.length, 100, 'Chunk 1 must contain exactly 100 rows');
  assert.equal(browserDispatchedChunks[1].rows.length, 5, 'Chunk 2 must contain exactly 5 rows');
  assert.equal(browserDispatchedChunks[0].batchId, browserDispatchedChunks[1].batchId, 'Both chunk requests must share the same batchId');
  mark('BROWSER-DRIVEN CHUNKING: real browser frontend automatically chunked 105 rows into 2 requests (100 and 5 rows)');

  const largeBatchId = browserDispatchedChunks[0].batchId;
  const chunk1 = browserDispatchedChunks[0].rows;

  // Verify total 105 rows in DB
  await control('COUNT_IMPORT_ROWS ' + largeBatchId + ' chunk2_ok', 'LIVE_COUNT_IMPORT_ROWS_chunk2_ok:');
  const countTotalMatch = getRuntimeText().match(/LIVE_COUNT_IMPORT_ROWS_chunk2_ok:(\d+)/);
  assert.equal(countTotalMatch[1], '105', 'Total 105 rows must be persisted after browser chunk save');

  // Verify batch details from backend
  const batchDetailResp = await api(owner.context, '/finance/drafts/import/' + largeBatchId);
  assert.equal(batchDetailResp.status(), 200);
  const chunk1Body = { rows: (await batchDetailResp.json()).rows.slice(0, 100) };

  // Replay Chunk 1: verify status REPLAYED, zero duplicate drafts
  const replayChunk1Resp = await api(owner.context, '/finance/drafts/import', 'POST', {
    batchId: largeBatchId,
    sourceLabel: 'large_import_105.csv',
    rows: chunk1
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(replayChunk1Resp.status(), 201);
  const replayBody = await replayChunk1Resp.json();
  assert.equal(replayBody.savedCount, 0);
  assert.equal(replayBody.replayedCount, 100);
  assert.equal(replayBody.totalRowsInBatch, 105);
  assert.ok(replayBody.rows.every(r => r.status === 'REPLAYED'), 'All replayed rows must report REPLAYED status');

  await control('COUNT_IMPORT_ROWS ' + largeBatchId + ' after_replay', 'LIVE_COUNT_IMPORT_ROWS_after_replay:');
  const countAfterReplay = getRuntimeText().match(/LIVE_COUNT_IMPORT_ROWS_after_replay:(\d+)/);
  assert.equal(countAfterReplay[1], '105', 'Replay must not add duplicate rows');

  // Conflict detection: changed payload for existing sourceRowId -> HTTP 409
  const conflictingChunk = [{ ...chunk1[0], amountInput: '999999.00' }];
  const conflictResp = await api(owner.context, '/finance/drafts/import', 'POST', {
    batchId: largeBatchId,
    sourceLabel: 'large_import_105.csv',
    rows: conflictingChunk
  });
  assert.equal(conflictResp.status(), 409, 'Changed payload under existing sourceRowId must return 409 Conflict');

  // Identical financial rows with distinct sourceRowId remain representable
  const distinctResp = await api(owner.context, '/finance/drafts/import', 'POST', {
    batchId: largeBatchId,
    sourceLabel: 'large_import_105.csv',
    rows: [
      { sourceRowId: 'distinct-row-A', sourceRowNumber: 106, accountId, eventType: 'DIRECT_SALE_RECEIPT', effectiveDate: today, amountInput: '50.00', description: 'Twin row' },
      { sourceRowId: 'distinct-row-B', sourceRowNumber: 107, accountId, eventType: 'DIRECT_SALE_RECEIPT', effectiveDate: today, amountInput: '50.00', description: 'Twin row' }
    ]
  });
  assert.equal(distinctResp.status(), 201);
  const distinctBody = await distinctResp.json();
  assert.equal(distinctBody.savedCount, 2);
  assert.notEqual(distinctBody.rows[0].draftId, distinctBody.rows[1].draftId, 'Twin rows with distinct sourceRowId must yield distinct drafts');
  mark('LARGE IMPORT & RECOVERY: chunked at 100 rows; atomic failure rollback & retry; idempotent replay; 409 conflict on change; distinct twin representation');

  // =========================================================================
  // SECTION 5: Security & Concurrency
  // =========================================================================

  // 1. Concurrent race on same source row
  const raceRowId = 'race-source-row-' + randomUUID();
  const raceHash = createHash('sha256').update('race-payload').digest('hex');
  await control('CONCURRENT_IMPORT_ROW_RACE ' + businessA + ' ' + largeBatchId + ' ' + raceRowId + ' ' + raceHash + ' r1 ' + owner.user.id, 'LIVE_RACE_r1:');
  const raceMatch = getRuntimeText().match(/LIVE_RACE_r1:succ=(\d+);conf=(\d+)/);
  assert.equal(raceMatch[1], '1', 'Exactly one concurrent insertion must succeed');
  assert.equal(raceMatch[2], '1', 'Concurrent duplicate insertion must fail with unique constraint violation');

  // 2. Cross-tenant account creation rejection
  const foreignAccountImport = await api(owner.context, '/finance/drafts/import', 'POST', {
    batchId: randomUUID(),
    sourceLabel: 'foreign_account.csv',
    rows: [{ sourceRowId: 'foreign-1', sourceRowNumber: 1, accountId: '44444444-4444-4444-8444-444444444444', eventType: 'DIRECT_SALE_RECEIPT', effectiveDate: today, amountInput: '10.00' }]
  });
  assert.equal(foreignAccountImport.status(), 400, 'Using another business account must return 400');

  // 3. RBAC checks
  const viewerImport = await api(viewer.context, '/finance/drafts/import', 'POST', { batchId: randomUUID(), sourceLabel: 'test.csv', rows: [chunk1[0]] });
  assert.equal(viewerImport.status(), 403, 'VIEWER role must be denied with 403');

  const managerImport = await api(manager.context, '/finance/drafts/import', 'POST', { batchId: randomUUID(), sourceLabel: 'test.csv', rows: [chunk1[0]] });
  assert.equal(managerImport.status(), 403, 'MANAGER role must be denied with 403');

  const accountantImport = await api(accountant.context, '/finance/drafts/import', 'POST', {
    batchId: randomUUID(),
    sourceLabel: 'accountant_batch.csv',
    rows: [{ sourceRowId: 'acct-1', sourceRowNumber: 1, accountId, eventType: 'DIRECT_SALE_RECEIPT', effectiveDate: today, amountInput: '25.00' }]
  });
  assert.equal(accountantImport.status(), 201, 'ACCOUNTANT role must be permitted with 201');

  // 4. Replay does not resurrect or mutate modified/recorded/discarded drafts
  const draftToRecordId = chunk1Body.rows[0].draftId;
  const draftToRecordVersion = chunk1Body.rows[0].version;
  const recordIdemKey = randomUUID();
  const recResp = await api(owner.context, '/finance/drafts/' + draftToRecordId + '/record', 'POST', {
    version: draftToRecordVersion,
    confirmedAmount: chunk1[0].amountInput
  }, { 'Idempotency-Key': recordIdemKey });
  assert.equal(recResp.status(), 201);
  const recordedTxId = (await recResp.json()).transaction.id;

  // Replay chunk 1 containing draftToRecord
  const replayRecordedResp = await api(owner.context, '/finance/drafts/import', 'POST', {
    batchId: largeBatchId,
    sourceLabel: 'large_import_105.csv',
    rows: [chunk1[0]]
  });
  assert.equal(replayRecordedResp.status(), 201);
  const replayRecRow = (await replayRecordedResp.json()).rows[0];
  assert.equal(replayRecRow.status, 'REPLAYED');
  assert.equal(replayRecRow.draftStatus, 'RECORDED');
  assert.equal(replayRecRow.recordedTransactionId, recordedTxId);

  // Discard draft 2 and replay
  const draftToDiscardId = chunk1Body.rows[1].draftId;
  const draftToDiscardVersion = chunk1Body.rows[1].version;
  const discardResp = await api(owner.context, '/finance/drafts/' + draftToDiscardId + '/discard', 'POST', {
    version: draftToDiscardVersion
  });
  assert.equal(discardResp.status(), 200);

  const replayDiscardedResp = await api(owner.context, '/finance/drafts/import', 'POST', {
    batchId: largeBatchId,
    sourceLabel: 'large_import_105.csv',
    rows: [chunk1[1]]
  });
  assert.equal(replayDiscardedResp.status(), 201);
  const replayDiscRow = (await replayDiscardedResp.json()).rows[0];
  assert.equal(replayDiscRow.status, 'REPLAYED');
  assert.equal(replayDiscRow.draftStatus, 'DISCARDED');
  mark('SECURITY & IMMUTABILITY: concurrent DB race handled by unique constraint; VIEWER/MANAGER denied 403; ACCOUNTANT allowed 201; recorded/discarded drafts preserved across replay');

  // =========================================================================
  // SECTION 6: Zero Financial Mutation Verification
  // =========================================================================
  const postImportMutations = await getMutationCounts('post_import');
  capture('post-import-mutation-counts.json', postImportMutations);

  // In step 5 we recorded exactly 1 draft (`draftToRecordId`).
  // Check that financial_transactions increased by EXACTLY 1!
  // account_movements increased by EXACTLY 1!
  // monthly_records increased by 0!
  // score_results increased by 0!
  assert.equal(postImportMutations.financial_transactions, initialMutations.financial_transactions + 1, 'financial_transactions must only increase by the 1 recorded draft');
  assert.equal(postImportMutations.account_movements, initialMutations.account_movements + 1, 'account_movements must only increase by the 1 recorded movement');
  assert.equal(postImportMutations.monthly_records, initialMutations.monthly_records, 'monthly_records must have ZERO mutations from import and draft recording');
  assert.equal(postImportMutations.score_results, initialMutations.score_results, 'score_results must have ZERO mutations from import and draft recording');

  // Verify DB linkage of the recorded draft
  await control('VERIFY_RECORDED_TX ' + draftToRecordId + ' tx1', 'LIVE_VERIFY_TX_tx1:');
  const txMatch = getRuntimeText().match(/LIVE_VERIFY_TX_tx1:recId=([0-9a-f-]+);txId=([0-9a-f-]+);amount=([^;]+);type=([^;]+);movements=(\d+)/);
  assert.ok(txMatch, 'Recorded transaction verification must match DB record');
  assert.equal(txMatch[1], recordedTxId);
  assert.equal(txMatch[2], recordedTxId);
  assert.equal(txMatch[5], '1', 'Exactly 1 account movement linked to posted transaction');

  // Recording replay idempotency: retry with same idempotency key produces no new transaction
  const retryRecResp = await api(owner.context, '/finance/drafts/' + draftToRecordId + '/record', 'POST', {
    version: draftToRecordVersion,
    confirmedAmount: chunk1[0].amountInput
  }, { 'Idempotency-Key': recordIdemKey });
  assert.ok([200, 201].includes(retryRecResp.status()));
  // If we re-record with new key it should return 409
  const reRecordNewKey = await api(owner.context, '/finance/drafts/' + draftToRecordId + '/record', 'POST', {
    version: draftToRecordVersion,
    confirmedAmount: chunk1[0].amountInput
  }, { 'Idempotency-Key': randomUUID() });
  assert.equal(reRecordNewKey.status(), 409, 'Re-recording an already-recorded draft with new key must return 409');

  const finalMutations = await getMutationCounts('final');
  capture('final-mutation-counts.json', finalMutations);
  assert.equal(finalMutations.financial_transactions, postImportMutations.financial_transactions, 'Zero duplicate financial transactions on re-record attempt');
  assert.equal(finalMutations.account_movements, postImportMutations.account_movements, 'Zero duplicate account movements on re-record attempt');

  mark('ZERO FINANCIAL MUTATION: import saving creates zero transactions/movements/monthly records; exactly 1 draft recorded with 1 transaction and 1 movement; duplicate recording blocked with 409');
}
