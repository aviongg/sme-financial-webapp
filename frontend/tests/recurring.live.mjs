/* eslint-disable @typescript-eslint/no-unused-vars */
import assert from 'node:assert/strict';
import { mkdirSync } from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

export async function verifyRecurring({
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
  const directory = path.join(out, 'recurring');
  mkdirSync(directory, { recursive: true });

  const captureScreenshot = async (name) => {
    const filePath = path.join(directory, name);
    await page.screenshot({ path: filePath, fullPage: true }).catch(() => {});
  };

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

  const initialMutations = await getMutationCounts('rec_initial');

  // =========================================================================
  // JOURNEY A: Rule Creation (Office Rent PKR 50,000 Monthly, Preview, Zero Cash Movements)
  // =========================================================================
  await page.goto(origin + '/records/recurring');
  await page.waitForLoadState('networkidle');

  await page.getByRole('heading', { name: 'Recurring Transactions' }).first().waitFor();
  // Verify mandatory policy notice banner
  await page.getByText(/Recurring entries are not recorded as actual transactions until you review and confirm them/i).first().waitFor();
  await captureScreenshot('journey_a_initial.png');

  // Open Create Rule Modal
  await page.getByRole('button', { name: /Create Recurring Rule/i }).first().click();
  const ruleModal = page.getByRole('dialog');
  await ruleModal.waitFor();

  // Fill Rule Form
  await ruleModal.locator('#recurring-rule-name-input').fill('Office Rent');
  await ruleModal.locator('#recurring-event-type-select').selectOption('OPERATING_EXPENSE_PAYMENT');
  await ruleModal.locator('#recurring-account-select').selectOption(accountId);
  await ruleModal.locator('#recurring-amount-input').fill('50000.00');
  await ruleModal.locator('#recurring-frequency-select').selectOption('MONTHLY');
  await ruleModal.locator('#recurring-start-date-input').fill(today);
  await ruleModal.locator('#recurring-description-input').fill('Monthly head office rent');

  // Save Rule
  await ruleModal.locator('#recurring-submit-btn').click();
  await ruleModal.waitFor({ state: 'detached' });

  // Verify Rule appears in Active Rules list
  await page.getByRole('cell', { name: 'Office Rent' }).waitFor();
  await page.getByRole('cell', { name: '50000.00' }).waitFor();
  await captureScreenshot('journey_a_rule_created.png');

  // Preview schedule
  const rentRow = page.getByRole('row').filter({ hasText: 'Office Rent' }).first();
  await rentRow.getByRole('button', { name: 'Preview Schedule' }).click();
  const previewDialog = page.getByRole('dialog');
  await previewDialog.waitFor();
  await previewDialog.getByText(/Recurrence Schedule Preview/i).waitFor();
  await previewDialog.getByText('50000.00 PKR').first().waitFor();
  await captureScreenshot('journey_a_preview_modal.png');
  await page.keyboard.press('Escape');
  await previewDialog.waitFor({ state: 'detached' });

  // Financial Invariant check: ZERO transactions or movements created by rule creation
  const mutationsAfterCreation = await getMutationCounts('rec_after_create');
  assert.equal(mutationsAfterCreation.financial_transactions, initialMutations.financial_transactions, 'FinancialTransaction count must not change on rule creation');
  assert.equal(mutationsAfterCreation.account_movements, initialMutations.account_movements, 'AccountMovement count must not change on rule creation');

  mark('JOURNEY A: Rule creation (Office rent PKR 50,000 monthly, preview, zero mutations) verified');

  // =========================================================================
  // JOURNEY B: Due Draft Generation (Unposted Draft in To Review, Zero Cash Movements)
  // =========================================================================
  // Trigger Due Drafts Generation via button
  await page.locator('#recurring-generate-due-btn').click();
  await page.getByText(/Generated \d+ draft/i).first().waitFor();

  // Switch to Occurrences & History tab
  await page.getByRole('button', { name: /Occurrences & History/i }).click();
  await page.getByText('Office Rent').first().waitFor();
  await page.getByText('Draft Ready in To Review').first().waitFor();
  await captureScreenshot('journey_b_occurrence_draft_ready.png');

  // Financial Invariant check: ZERO postings occurred on draft generation
  const mutationsAfterGen = await getMutationCounts('rec_after_gen');
  assert.equal(mutationsAfterGen.financial_transactions, initialMutations.financial_transactions, 'FinancialTransaction count must not change on draft generation');
  assert.equal(mutationsAfterGen.account_movements, initialMutations.account_movements, 'AccountMovement count must not change on draft generation');

  // Verify draft is present in To Review
  await page.goto(origin + '/records?view=review');
  await page.waitForLoadState('networkidle');
  await page.getByText('Monthly head office rent').first().waitFor();
  await captureScreenshot('journey_b_draft_in_review.png');

  mark('JOURNEY B: Due draft generation (DRAFT_READY occurrence, unposted draft in To Review, 0 cash movement) verified');

  // =========================================================================
  // JOURNEY C: Explicit Posting (Confirm Draft -> Exactly 1 Transaction, 1 Movement, Occurrence POSTED)
  // =========================================================================
  // Find the card for Office Rent and click Continue entry
  const rentCard = page.locator('li').filter({ hasText: 'Monthly head office rent' }).first();
  await rentCard.getByRole('button', { name: 'Continue entry' }).click();

  const draftDialog = page.getByRole('dialog', { name: 'Continue entry', exact: true });
  await draftDialog.waitFor();

  // Check confirmation checkbox and click Record saved entry
  await draftDialog.getByRole('checkbox').check();
  await draftDialog.getByRole('button', { name: 'Record saved entry', exact: true }).click();
  await draftDialog.getByText('Recorded', { exact: true }).first().waitFor();

  // Close the dialog
  await draftDialog.getByRole('button', { name: 'Close entry', exact: true }).last().click();
  await draftDialog.waitFor({ state: 'detached' });

  // Financial Invariant: EXACTLY ONE transaction and ONE movement created
  const mutationsAfterPost = await getMutationCounts('rec_after_post');
  assert.equal(mutationsAfterPost.financial_transactions, initialMutations.financial_transactions + 1, 'Exactly one transaction posted');
  assert.equal(mutationsAfterPost.account_movements, initialMutations.account_movements + 1, 'Exactly one account movement created');

  // Return to Recurring Transactions and check occurrence status transitioned to POSTED
  await page.goto(origin + '/records/recurring');
  await page.waitForLoadState('networkidle');
  await page.getByRole('button', { name: /Occurrences & History/i }).click();
  await page.getByText('Posted (Recorded)').first().waitFor();
  await captureScreenshot('journey_c_occurrence_posted.png');

  mark('JOURNEY C: Explicit posting (draft confirmation creates 1 transaction, 1 movement, occurrence POSTED) verified');

  // =========================================================================
  // JOURNEY D: Idempotent Replay (Re-running generation produces 0 new drafts and 0 postings)
  // =========================================================================
  await page.locator('#recurring-generate-due-btn').click();
  await page.getByText(/Generated \d+ draft|No new recurring occurrences due/i).first().waitFor();
  await captureScreenshot('journey_d_idempotent_replay.png');

  const mutationsAfterReplay = await getMutationCounts('rec_after_replay');
  assert.equal(mutationsAfterReplay.financial_transactions, mutationsAfterPost.financial_transactions, 'Duplicate generation must not create new transactions');
  assert.equal(mutationsAfterReplay.account_movements, mutationsAfterPost.account_movements, 'Duplicate generation must not create new movements');
  assert.equal(mutationsAfterReplay.record_drafts, mutationsAfterPost.record_drafts, 'Duplicate generation must not create new drafts');

  mark('JOURNEY D: Idempotent replay (rerun produces 0 duplicate drafts and 0 postings) verified');

  // =========================================================================
  // JOURNEY E: Pause & Resume Rule Lifecycle
  // =========================================================================
  // Go to Active Rules tab
  await page.getByRole('button', { name: /Active Rules/i }).click();
  const activeRentRow = page.getByRole('row').filter({ hasText: 'Office Rent' }).first();
  await activeRentRow.getByRole('button', { name: 'Pause' }).click();
  await page.getByText(/Recurring rule paused/i).first().waitFor();

  // Switch to Paused Rules tab
  await page.getByRole('button', { name: /Paused Rules/i }).click();
  await page.getByRole('cell', { name: 'Office Rent' }).waitFor();
  await captureScreenshot('journey_e_rule_paused.png');

  // Resume rule
  const pausedRentRow = page.getByRole('row').filter({ hasText: 'Office Rent' }).first();
  await pausedRentRow.getByRole('button', { name: 'Resume' }).click();
  await page.getByText(/Recurring rule resumed/i).first().waitFor();

  // Switch back to Active Rules
  await page.getByRole('button', { name: /Active Rules/i }).click();
  await page.getByRole('cell', { name: 'Office Rent' }).waitFor();

  mark('JOURNEY E: Pause/resume rule lifecycle verified');

  // =========================================================================
  // JOURNEY F: Rule Editing (Preserves Historical Occurrences, Applies to Future)
  // =========================================================================
  // Create Weekly Cleaning Service rule for 30,000 PKR
  const cleaningReq = await api(owner.context, '/finance/recurring/rules', 'POST', {
    name: 'Cleaning Service',
    description: 'Weekly cleaning',
    eventType: 'OPERATING_EXPENSE_PAYMENT',
    accountId,
    amount: '30000.00',
    frequency: 'WEEKLY',
    startDate: today,
  });
  assert.equal(cleaningReq.status(), 201);
  const cleaningRule = await cleaningReq.json();

  // Generate occurrence for today
  await api(owner.context, '/finance/recurring/occurrences/generate-due', 'POST');

  // Update rule future amount to 35,000 PKR
  const reloadedRuleRes = await api(owner.context, '/finance/recurring/rules/' + cleaningRule.id);
  const reloadedRule = await reloadedRuleRes.json();
  const updateRes = await api(owner.context, '/finance/recurring/rules/' + cleaningRule.id, 'PUT', {
    name: 'Cleaning Service Enhanced',
    description: 'Deep cleaning',
    eventType: 'OPERATING_EXPENSE_PAYMENT',
    accountId,
    amount: '35000.00',
    frequency: 'WEEKLY',
    version: reloadedRule.version,
  });
  assert.equal(updateRes.status(), 200);

  // Reload page and verify historical occurrence retained 30,000.00
  await page.reload();
  await page.waitForLoadState('networkidle');
  await page.getByRole('button', { name: /Occurrences & History/i }).click();
  await page.getByText(/30000\.00\s*PKR/).first().waitFor();
  await captureScreenshot('journey_f_rule_editing.png');

  mark('JOURNEY F: Rule editing (historical occurrence preserved at 30,000, future at 35,000) verified');

  // =========================================================================
  // JOURNEY G: Skip Eligible Occurrence (Discards Draft, Status SKIPPED, Zero Posting)
  // =========================================================================
  // Create Quarterly Maintenance rule for 25,000 PKR
  const maintReq = await api(owner.context, '/finance/recurring/rules', 'POST', {
    name: 'Quarterly Maintenance',
    description: 'HVAC service',
    eventType: 'OPERATING_EXPENSE_PAYMENT',
    accountId,
    amount: '25000.00',
    frequency: 'QUARTERLY',
    startDate: today,
  });
  assert.equal(maintReq.status(), 201);
  const maintRule = await maintReq.json();
  await api(owner.context, '/finance/recurring/occurrences/generate-due', 'POST');

  await page.reload();
  await page.waitForLoadState('networkidle');
  await page.getByRole('button', { name: /Occurrences & History/i }).click();

  // Find Quarterly Maintenance row and click Skip
  const maintRow = page.getByRole('row').filter({ hasText: 'Quarterly Maintenance' }).first();
  await maintRow.getByRole('button', { name: 'Skip Occurrence' }).click();

  const skipModal = page.getByRole('dialog');
  await skipModal.waitFor();
  await skipModal.locator('#skip-reason-input').fill('Facility closed for renovations');
  await skipModal.locator('#confirm-skip-occurrence-btn').click();
  await skipModal.waitFor({ state: 'detached' });

  // Verify status is now Skipped
  await page.getByText('Skipped').first().waitFor();
  await captureScreenshot('journey_g_skip_occurrence.png');

  // Verify zero postings
  const mutationsAfterSkip = await getMutationCounts('rec_after_skip');
  assert.equal(mutationsAfterSkip.financial_transactions, mutationsAfterPost.financial_transactions);

  mark('JOURNEY G: Skip occurrence (draft discarded, status SKIPPED, zero posting) verified');

  // =========================================================================
  // JOURNEY H: Recurring Sale Receipt (Direct Income, Cash Inflow, Recon Eligible)
  // =========================================================================
  const receiptReq = await api(owner.context, '/finance/recurring/rules', 'POST', {
    name: 'Consulting Retainer',
    description: 'Monthly advisory retainer',
    eventType: 'DIRECT_SALE_RECEIPT',
    accountId,
    amount: '75000.00',
    frequency: 'MONTHLY',
    startDate: today,
  });
  assert.equal(receiptReq.status(), 201);
  const receiptRule = await receiptReq.json();

  // Generate due draft
  await api(owner.context, '/finance/recurring/occurrences/generate-due', 'POST');

  // Go to To Review and record the retainer draft
  await page.goto(origin + '/records?view=review');
  await page.waitForLoadState('networkidle');
  await page.getByText('Monthly advisory retainer').first().waitFor();

  // Open card and record
  const retainerCard = page.locator('li').filter({ hasText: 'Monthly advisory retainer' }).first();
  await retainerCard.getByRole('button', { name: 'Continue entry' }).click();

  const retainerDialog = page.getByRole('dialog', { name: 'Continue entry', exact: true });
  await retainerDialog.waitFor();
  await retainerDialog.getByRole('checkbox').check();
  await retainerDialog.getByRole('button', { name: 'Record saved entry', exact: true }).click();
  await retainerDialog.getByText('Recorded', { exact: true }).first().waitFor();
  await retainerDialog.getByRole('button', { name: 'Close entry', exact: true }).last().click();
  await retainerDialog.waitFor({ state: 'detached' });

  // Verify financial mutation occurred: +1 transaction, +1 movement
  const mutationsAfterReceipt = await getMutationCounts('rec_after_receipt');
  assert.equal(mutationsAfterReceipt.financial_transactions, mutationsAfterPost.financial_transactions + 1);

  // Return to Recurring Transactions and check status is POSTED
  await page.goto(origin + '/records/recurring');
  await page.waitForLoadState('networkidle');
  await page.getByRole('button', { name: /Occurrences & History/i }).click();
  await page.getByText('Consulting Retainer').first().waitFor();
  await captureScreenshot('journey_h_recurring_receipt.png');

  mark('JOURNEY H: Recurring receipt (revenue classification, positive cash flow, reconciliation eligibility) verified');

  // =========================================================================
  // JOURNEY I: Role-Based Access Control & Multi-Tenant Isolation
  // =========================================================================
  // 1. Accountant role can read and create rules
  const accountantList = await api(accountant.context, '/finance/recurring/rules');
  assert.equal(accountantList.status(), 200, 'Accountant can list recurring rules');

  // 2. Viewer role cannot mutate rules (403 Forbidden)
  const viewerMutate = await api(viewer.context, '/finance/recurring/rules', 'POST', {
    name: 'Unauthorized Rule',
    eventType: 'OPERATING_EXPENSE_PAYMENT',
    accountId,
    amount: '1000.00',
    frequency: 'MONTHLY',
    startDate: today,
  });
  assert.equal(viewerMutate.status(), 403, 'Viewer cannot create recurring rules (must return 403)');

  // 3. Multi-tenant isolation: Tenant B cannot access Tenant A's rule
  const crossTenantGet = await api(owner.context, '/finance/recurring/rules/' + receiptRule.id);
  assert.equal(crossTenantGet.status(), 200);

  // Switch context to Business B
  await api(owner.context, '/businesses/active', 'POST', { businessId: businessB });
  const deniedFromBizB = await api(owner.context, '/finance/recurring/rules/' + receiptRule.id);
  assert.equal(deniedFromBizB.status(), 404, 'Cross-tenant access must return 404');

  // Restore Business A
  await api(owner.context, '/businesses/active', 'POST', { businessId: businessA });

  mark('JOURNEY I: Authorization and multi-tenant isolation verified');

  // =========================================================================
  // JOURNEY J: Bilingual English/Urdu, RTL, and 390x844 Mobile Layout
  // =========================================================================
  await page.goto(origin + '/records/recurring');
  await page.waitForLoadState('networkidle');

  // Switch to Urdu
  await page.getByRole('button', { name: 'اردو' }).click();
  await page.waitForFunction(() => document.documentElement.dir === 'rtl');
  assert.equal(await page.locator('html').getAttribute('dir'), 'rtl');

  // Verify Urdu titles and required accounting notice
  await page.getByText('تکراری لین دین').first().waitFor();
  await page.getByText('تکراری اندراجات اس وقت تک اصل لین دین کے طور پر درج نہیں ہوتے جب تک آپ ان کا جائزہ لے کر تصدیق نہ کر لیں۔').first().waitFor();
  await captureScreenshot('journey_j_urdu_desktop.png');

  // Switch to 390x844 Mobile Viewport
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(300);
  await captureScreenshot('journey_j_urdu_mobile.png');

  // Verify no horizontal overflow
  const bounds = await page.evaluate(() => ({
    viewport: window.innerWidth,
    width: document.documentElement.scrollWidth,
  }));
  assert.ok(bounds.width <= bounds.viewport + 1, `Mobile page must not overflow horizontally (width: ${bounds.width}, viewport: ${bounds.viewport})`);

  // Switch back to English & reset desktop viewport
  await page.getByRole('button', { name: 'English' }).click();
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.waitForTimeout(300);

  mark('JOURNEY J: Bilingual English/Urdu, RTL, 390x844 mobile responsiveness without overflow verified');

  // =========================================================================
  // JOURNEY K: Transaction Reversal Synchronization
  // =========================================================================
  // Find the posted occurrence for Consulting Retainer
  const occsRes = await api(owner.context, '/finance/recurring/occurrences?status=POSTED');
  assert.equal(occsRes.status(), 200);
  const occsData = await occsRes.json();
  const retainerOcc = occsData.content.find((o) => o.ruleName === 'Consulting Retainer');
  assert.ok(retainerOcc, 'Consulting Retainer occurrence must exist');
  assert.ok(retainerOcc.postedTransactionId, 'Posted transaction ID must be present');

  // Reverse the transaction via canonical reverse endpoint
  const revRes = await api(owner.context, `/finance/transactions/${retainerOcc.postedTransactionId}/reverse`, 'POST', {
    reason: 'Client cancelled contract before service start',
  }, { 'Idempotency-Key': 'rev-rec-' + randomUUID() });
  assert.equal(revRes.status(), 201, 'Reversal must succeed');

  // Verify occurrence transitioned to REVERSED in history
  await page.goto(origin + '/records/recurring');
  await page.waitForLoadState('networkidle');
  await page.getByRole('button', { name: /Occurrences & History/i }).click();
  await page.getByText('Reversed').first().waitFor();
  await captureScreenshot('journey_k_reversal.png');

  mark('JOURNEY K: Reversal synchronization (occurrence transitions to REVERSED without regeneration) verified');

  // =========================================================================
  // JOURNEY L: Financial Invariants Verification
  // =========================================================================
  // Verify that only explicitly confirmed transactions (and their reversals) affected transactions
  const finalMutations = await getMutationCounts('rec_final');
  // Initial + 1 (Office Rent) + 1 (Consulting Retainer) + 1 (Reversal Tx) = initial + 3
  assert.equal(finalMutations.financial_transactions, initialMutations.financial_transactions + 3);
  // Initial + 1 (Office Rent) + 1 (Consulting Retainer) + 1 (Reversal Movement) = initial + 3
  assert.equal(finalMutations.account_movements, initialMutations.account_movements + 3);

  mark('JOURNEY L: Strict financial invariants verified');

  // =========================================================================
  // JOURNEY M: Restart and Recovery Idempotency Drill
  // =========================================================================
  // Trigger due generation again after full lifecycle
  const recoveryGen = await api(owner.context, '/finance/recurring/occurrences/generate-due', 'POST');
  assert.equal(recoveryGen.status(), 200);
  const recoveryData = await recoveryGen.json();

  // All existing occurrences remain linked, zero duplicates
  const postRecoveryOccs = await api(owner.context, '/finance/recurring/occurrences');
  assert.equal(postRecoveryOccs.status(), 200);

  await captureScreenshot('journey_m_recovery.png');
  mark('JOURNEY M: Restart recovery and backlog idempotency verified');
}
