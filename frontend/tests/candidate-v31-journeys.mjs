import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import path from 'node:path';

// Additive to all original financial and candidate-v2 assertions. Real API, disposable data only.
export async function candidateV31Journeys({ origin, browser, call, cookies, mark, invoiceId, itemId, today, outDir }) {
  const anonymous = await browser.newContext();
  try {
    const page = await anonymous.newPage();
    const privateRequests = [];
    page.on('request', r => { if (/\/api\/(finance|dashboard|documents|records)/.test(r.url())) privateRequests.push(r.url()); });
    await page.goto(origin);
    await page.locator('[data-landing-root]').waitFor();
    await page.locator('header').getByRole('link', { name: 'Sign In', exact: true }).click();
    await page.getByRole('heading', { name: 'Sign in to Zarwia', exact: true }).waitFor();
    await page.goBack();
    await page.locator('[data-landing-root]').waitFor();
    assert.equal(await page.getByRole('heading', { name: 'Sign in to Zarwia', exact: true }).count(), 0);
    await page.goForward();
    await page.getByRole('heading', { name: 'Sign in to Zarwia', exact: true }).waitFor();
    assert.deepEqual(privateRequests, [], 'anonymous Back/Forward never requests financial data');
    mark('DEF-19 anonymous Sign In -> Back -> Forward follows URL, preserves private-route boundary');
  } finally { await anonymous.close(); }

  const supplier = await call('POST', '/finance/counterparties', { displayName: 'V31 synthetic supplier', type: 'SUPPLIER' });
  assert.equal(supplier.status, 201, supplier.body);
  const bill = await call('POST', '/bills', { counterpartyName: 'V31 synthetic supplier', documentDate: today, dueDate: today,
    lines: [{ description: 'V31 demonstration service', quantity: '1', unit: 'job', unitPrice: '37.50' }] });
  assert.equal(bill.status, 201, bill.body);
  assert.equal((await call('POST', `/bills/${bill.json.id}/finalize`, { expectedVersion: bill.json.version })).status, 200);
  const recognition = await call('POST', '/finance/obligations/recognize-commercial-document', { documentId: bill.json.id, documentKind: 'BILL', counterpartyId: supplier.json.id }, { 'Idempotency-Key': randomUUID() });
  assert.equal(recognition.status, 201, recognition.body);
  const before = (await call('GET', '/finance/transactions')).json;
  const documents = (await call('GET', '/documents')).json;
  const reviewed = documents.find(d => d.processingStatus === 'confirmed');
  assert.ok(reviewed, 'unchanged DEF-01 journey created confirmed evidence without an accounting posting');

  for (const locale of ['en', 'ur']) {
    const context = await browser.newContext({ viewport: { width: 390, height: 600 } });
    await context.addCookies(cookies);
    await context.addInitScript(l => localStorage.setItem('finsight_locale', l), locale);
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', e => errors.push(String(e)));
    try {
      const query = locale === 'ur' ? 'سپلائر & نقد' : 'Rice & supplier';
      await page.goto(`${origin}/search?q=${encodeURIComponent(query)}`);
      const search = page.locator('input[maxlength="200"]');
      await search.waitFor();
      assert.equal(await search.inputValue(), query);
      const business = page.getByTestId('header-business-name');
      await business.waitFor({ state: 'visible' });
      assert.equal(await business.innerText(), 'Lahore Traders');
      assert.equal(await business.getAttribute('title'), 'Lahore Traders');
      await page.goto(`${origin}/search?q=second%20query`);
      assert.equal(await search.inputValue(), 'second query');
      mark(`DEF-16/20 ${locale}: decoded deep-link prefill updates; active business remains visible at 390x600`);

      await page.goto(origin + '/reports');
      await page.getByTestId('pack-from').fill(today.slice(0, 7) + '-01');
      await page.getByTestId('pack-to').fill(today);
      await page.getByTestId('pack-generate').click();
      const cash = page.getByTestId('pack-section-cash');
      await cash.waitFor();
      await cash.getByText(locale === 'ur' ? 'نقد فروخت کی وصولی' : 'Cash sale receipt', { exact: true }).waitFor();
      assert.doesNotMatch(await cash.innerText(), /DIRECT_SALE_RECEIPT|OPERATING_EXPENSE_PAYMENT|INTERNAL_TRANSFER|RECEIVABLE_COLLECTION|PAYABLE_SETTLEMENT/);
      mark(`DEF-09 ${locale}: real cash activity uses translated labels; exact backend amounts retained`);

      await page.goto(`${origin}/bills/${bill.json.id}`);
      await page.getByTestId('commercial-lifecycle-panel').waitFor();
      if (locale === 'en') {
        const select = page.getByTestId('lifecycle-evidence-id');
        await select.waitFor();
        assert.equal(await select.evaluate(e => e.tagName), 'SELECT');
        const options = await select.locator('option').evaluateAll(nodes => nodes.map(n => ({ value: n.value, text: n.textContent })));
        assert.ok(options.some(o => o.value === reviewed.id && o.text.includes(reviewed.originalFilename)));
        assert.deepEqual(options.filter(o => o.value).map(o => o.value).sort(), documents.filter(d => d.processingStatus === 'confirmed').map(d => d.id).sort());
        await select.selectOption(reviewed.id);
        const [linked] = await Promise.all([
          page.waitForResponse(r => r.url().endsWith(`/commercial/documents/${bill.json.id}/evidence`) && r.request().method() === 'POST'),
          page.getByTestId('lifecycle-evidence-submit').click(),
        ]);
        assert.equal(linked.status(), 200, await linked.text());
        await page.getByTestId('lifecycle-evidence').getByRole('link', { name: reviewed.originalFilename, exact: true }).waitFor();
      }
      const obligation = page.getByTestId('lifecycle-obligation-link');
      const target = await obligation.getAttribute('href');
      assert.match(target, /^\/records\/payables\?obligationId=[0-9a-f-]+$/);
      await obligation.click();
      await page.getByRole('dialog').waitFor();
      assert.match(page.url(), /\/records\/payables\?obligationId=/);
      assert.ok((await page.getByRole('dialog').innerText()).includes('V31 synthetic supplier'));
      await page.goto(`${origin}/operations/relationships?kind=DOCUMENT&id=${bill.json.id}`);
      await page.getByTestId('relationships-table').getByRole('button', { name: locale === 'ur' ? 'کھولیں' : 'Open', exact: true }).first().click();
      await page.getByRole('dialog').waitFor();
      assert.match(page.url(), /\/records\/payables\?obligationId=/);
      await page.goto(`${origin}/operations/relationships?kind=DOCUMENT&id=${bill.json.id}`);
      await page.getByTestId('relationships-table').locator(`a[href="/upload/${reviewed.id}"]`).click();
      await page.getByRole('heading', { name: reviewed.originalFilename, exact: true }).waitFor();
      await page.goto(`${origin}/bills/${bill.json.id}`);
      await page.getByTestId('lifecycle-evidence').getByRole('link', { name: reviewed.originalFilename, exact: true }).click();
      await page.getByRole('heading', { name: reviewed.originalFilename, exact: true }).waitFor();
      assert.equal((await call('GET', `/documents/${reviewed.id}`)).json.processingStatus, 'confirmed');
      assert.ok(page.url().includes(`/upload/${reviewed.id}`));
      await page.goto(`${origin}/invoices/${invoiceId}`);
      assert.match(await page.getByTestId('lifecycle-obligation-link').getAttribute('href'), /^\/records\/receivables\?obligationId=/);
      const invoice = (await call('GET', `/commercial/documents/${invoiceId}/lifecycle`)).json;
      await page.goto(`${origin}/operations/relationships?kind=ITEM&id=${itemId}`);
      await page.getByTestId('relationships-table').getByRole('row').filter({ hasText: invoice.documentNumber })
        .getByRole('button', { name: locale === 'ur' ? 'کھولیں' : 'Open', exact: true }).click();
      await page.getByTestId('commercial-lifecycle-panel').waitFor();
      assert.ok(page.url().endsWith(`/invoices/${invoiceId}`));
      mark(`DEF-12/14 ${locale}: reviewed filename selector, bill->payable detail, invoice->receivable, and linked OCR evidence navigate correctly`);

      await page.goto(origin + '/upload');
      const ordinary = page.getByTestId('document-file-input');
      const camera = page.getByTestId('document-camera-input');
      await ordinary.waitFor();
      assert.equal(await ordinary.getAttribute('accept'), 'application/pdf,image/jpeg,image/png');
      assert.equal(await ordinary.getAttribute('capture'), null, 'normal picker retains PDF/file workflow');
      assert.equal(await camera.getAttribute('capture'), 'environment');
      const uploads = [];
      page.on('request', r => { if (r.method() === 'POST' && r.url().endsWith('/documents/upload')) uploads.push(r.url()); });
      await ordinary.setInputFiles({ name: 'synthetic.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 synthetic selection only') });
      await page.getByTestId('document-selected-file').getByText('synthetic.pdf', { exact: true }).waitFor();
      await camera.setInputFiles({ name: 'synthetic-camera.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJvYAAAAASUVORK5CYII=', 'base64') });
      await page.getByTestId('document-selected-file').getByText('synthetic-camera.png', { exact: true }).waitFor();
      assert.equal(await ordinary.inputValue(), '');
      assert.deepEqual(uploads, [], 'selection and camera entry never auto-upload or post');
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
      await page.screenshot({ path: path.join(outDir, `v31-mobile-${locale}.png`), fullPage: false });
      assert.deepEqual(errors, []);
      mark(`DEF-15 ${locale}: separate rear-camera entry preserves PDF picker and explicit submission; hardware behavior not simulated`);
    } finally { await context.close(); }
  }
  const after = (await call('GET', '/finance/transactions')).json;
  const { observationAt: beforeClock, ...beforeFacts } = before;
  const { observationAt: afterClock, ...afterFacts } = after;
  assert.ok(Date.parse(beforeClock) && Date.parse(afterClock));
  assert.deepEqual(afterFacts, beforeFacts, 'evidence linking and navigation never create ledger transactions');
  mark('V3.1 evidence linking, reporting, search, navigation and camera selection leave financial transactions unchanged');
}
