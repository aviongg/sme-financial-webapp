import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

// Called only by the disposable product harness, after its unchanged financial assertions.
export async function candidateV2Journeys({ origin, browser, call, csrf, cookies, mark, itemId, businessId, today }) {
  const initial = await call('GET', '/business-configuration');
  assert.equal(initial.status, 200);
  assert.equal(initial.json.configured, false);
  const keys = initial.json.modules.filter(m => m.implemented).map(m => m.key);
  assert.deepEqual(new Set(initial.json.requestedModules), new Set(keys));
  assert.ok(initial.json.modules.every(m => !m.implemented || (m.enabled && m.available)));
  assert.ok(!keys.includes('commercial'));
  for (const required of ['transactions', 'receivables', 'invoices', 'inventory', 'production', 'projects', 'costing', 'reports']) {
    assert.ok(keys.includes(required), required);
  }
  const save = async requestedModules => {
    const current = await call('GET', '/business-configuration');
    const result = await call('PUT', '/business-configuration', {
      operatingProfile: current.json.operatingProfile, operatingAnswers: current.json.operatingAnswers,
      requestedModules, version: current.json.version,
    });
    assert.equal(result.status, 200, result.body);
    return result.json;
  };
  const configured = await save(keys);
  assert.equal(configured.configured, true);
  assert.ok(configured.modules.every(m => !m.implemented || (m.enabled && m.available)));
  mark('DEF-02/21/23 canonical seeded-style module selection saves; unconfigured and configured availability agree');

  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await context.addCookies(cookies);
  const page = await context.newPage();
  try {
    // OCR integration is explicitly disabled in the disposable Java runtime. Exercise
    // a real validated upload -> unavailable provider -> UI manual-review contract.
    const token = (await (await context.request.get(origin + '/api/auth/csrf')).json()).token;
    const upload = await context.request.post(origin + '/api/documents/upload', {
      headers: { 'X-XSRF-TOKEN': token },
      multipart: { documentTypeHint: 'receipt', file: { name: 'candidate-v2-synthetic.png', mimeType: 'image/png',
        buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJvYAAAAASUVORK5CYII=', 'base64') } },
    });
    assert.equal(upload.status(), 201, await upload.text());
    const documentId = (await upload.json()).id;
    const deadline = Date.now() + 45000;
    let document;
    do {
      document = (await call('GET', `/documents/${documentId}`)).json;
      if (document.processingStatus === 'failed') break;
      await new Promise(resolve => setTimeout(resolve, 200));
    } while (Date.now() < deadline);
    assert.equal(document.processingStatus, 'failed');
    assert.equal(document.failureReason, 'unavailable');
    assert.equal(document.manualReviewEligible, true);
    const before = (await call('GET', '/finance/transactions')).json;
    await page.goto(`${origin}/upload/${documentId}`);
    await page.getByTestId('document-manual-review').click();
    await page.getByTestId('document-manual-entry').waitFor();
    await page.getByTestId('document-target-month').fill(today.slice(0, 7));
    await page.getByTestId('document-amount').fill('19.25');
    await page.getByTestId('document-date').fill(today);
    await page.getByTestId('document-party').fill('Candidate v2 synthetic supplier');
    const corrected = page.waitForResponse(r => r.url().endsWith(`/documents/${documentId}`) && r.request().method() === 'PATCH');
    await page.getByRole('button', { name: 'Save draft corrections', exact: true }).click();
    assert.equal((await corrected).status(), 200);
    // Saving corrections remounts the review form from persisted data. Target
    // month belongs to explicit confirmation, not the saved correction draft.
    await page.reload();
    await page.getByTestId('document-manual-entry').waitFor();
    assert.equal(await page.getByTestId('document-amount').inputValue(), '19.25');
    assert.equal(await page.getByTestId('document-party').inputValue(), 'Candidate v2 synthetic supplier');
    await page.getByTestId('document-target-month').fill(today.slice(0, 7));
    await page.getByTestId('review-document').click();
    const [confirmed] = await Promise.all([
      page.waitForResponse(r => r.url().endsWith(`/documents/${documentId}/confirm`) && r.request().method() === 'POST'),
      page.getByTestId('confirm-document').click(),
    ]);
    assert.equal(confirmed.status(), 200);
    document = (await call('GET', `/documents/${documentId}`)).json;
    assert.equal(document.processingStatus, 'confirmed');
    assert.equal(document.extractedData, null);
    assert.equal(document.failureReason, 'unavailable');
    assert.equal(JSON.parse(document.reviewedData).entry_mode, 'manual');
    const after = (await call('GET', '/finance/transactions')).json;
    // observationAt is the list request's clock, not a stored financial field.
    const { observationAt: beforeClock, ...beforeFacts } = before;
    const { observationAt: afterClock, ...afterFacts } = after;
    assert.ok(Number.isFinite(Date.parse(beforeClock)) && Number.isFinite(Date.parse(afterClock)));
    assert.deepEqual(afterFacts, beforeFacts);
    mark('DEF-01 real failed upload enters manual review, saves corrections and confirms evidence without posting transactions');

    await page.goto(origin + '/invoices/new');
    await page.getByTestId('commercial-counterparty-input').fill('Candidate v2 stock-linked draft');
    await page.getByTestId('commercial-document-date-input').fill(today);
    await page.getByTestId('commercial-line-inventory-item-0').selectOption(itemId);
    await page.getByTestId('commercial-line-description-0').fill('Candidate v2 synthetic inventory line');
    await page.getByTestId('commercial-line-quantity-0').fill('1');
    await page.getByTestId('commercial-line-unit-price-0').fill('2.00');
    const [saved] = await Promise.all([
      page.waitForResponse(r => r.url().endsWith('/api/invoices') && r.request().method() === 'POST'),
      page.getByTestId('commercial-save-draft').click(),
    ]);
    assert.equal(saved.status(), 201);
    assert.equal((await saved.json()).lines[0].inventoryItemId, itemId);
    mark('DEF-05 inventory item chosen in the commercial line editor persists through the real invoice API');

    const issued = await call('POST', '/inventory/movements/issues', { itemId, quantity: '1', purpose: 'INTERNAL' }, { 'Idempotency-Key': randomUUID() });
    assert.equal(issued.status, 201, issued.body);
    await page.goto(origin + '/inventory/stock');
    await page.getByTestId('stock-kind').selectOption('returns');
    await page.getByTestId('stock-item').selectOption(itemId);
    await page.getByTestId('stock-issue-movement').selectOption(issued.json.id);
    await page.getByTestId('stock-quantity').fill('1');
    const [returned] = await Promise.all([
      page.waitForResponse(r => r.url().endsWith('/api/inventory/movements/returns') && r.request().method() === 'POST'),
      page.getByTestId('stock-submit').click(),
    ]);
    assert.equal(returned.status(), 201);
    assert.equal(returned.request().postDataJSON().issueMovementId, issued.json.id);
    mark('DEF-06 stock return selector submits the selected prior issue to the authoritative inventory endpoint');

    await save(['core_finance']);
    for (const route of ['/inventory/movements/receipts', '/finance/counterparties', '/invoices']) {
      const denied = await call('POST', route, {});
      assert.equal(denied.status, 403, route);
      assert.equal(denied.json.error, 'module_disabled', route);
    }
    assert.equal((await call('GET', `/inventory/items/${itemId}/movements`)).status, 200);
    assert.equal((await call('GET', `/documents/${documentId}`)).status, 200);
    for (const route of ['/inventory/stock', '/invoices', '/finance/accounts', '/reports']) {
      await page.goto(origin + route);
      await page.getByTestId('module-disabled-guard').waitFor();
    }
    mark('DEF-04/23 counterparty, invoice and actual /api/inventory writes deny disabled modules; retained API history and UI route guards agree');
    await save(keys);
    await page.goto(origin + '/inventory/stock');
    await page.getByTestId('stock-register').waitFor();

    const other = await call('POST', '/businesses', { businessName: 'Candidate v2 isolation fixture', businessType: 'services', languagePreference: 'en', whatsappOptIn: false });
    assert.equal(other.status, 201);
    assert.equal((await call('POST', '/businesses/active', { businessId: other.json.businessId })).status, 200);
    await csrf();
    assert.equal((await call('GET', `/documents/${documentId}`)).status, 404);
    assert.equal((await call('POST', `/documents/${documentId}/manual-review`, {})).status, 404);
    assert.equal((await call('GET', `/inventory/items/${itemId}`)).status, 404);
    // History is a business-scoped collection: foreign/unknown item filters both
    // return the same empty collection, never the other business's movements.
    const foreignHistory = await call('GET', `/inventory/items/${itemId}/movements`);
    const unknownHistory = await call('GET', `/inventory/items/${randomUUID()}/movements`);
    assert.equal(foreignHistory.status, 200);
    assert.equal(unknownHistory.status, 200);
    assert.deepEqual(foreignHistory.json, { movements: [] });
    assert.deepEqual(foreignHistory.json, unknownHistory.json);
    assert.equal((await call('POST', '/businesses/active', { businessId })).status, 200);
    await csrf();
    mark('Candidate v2 document/manual-review and stock history remain isolated across active businesses');
  } finally {
    await context.close();
  }
}
