import assert from 'node:assert/strict';
import { after, afterEach, beforeEach, mock, test } from 'node:test';
import { mkdtempSync, readFileSync, writeFileSync, unlinkSync, rmdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import ts from 'typescript';

const directory = mkdtempSync(path.join(tmpdir(), 'finsight-commercial-contracts-'));
const files = {
  client: '../src/lib/api/client.ts',
  form: '../src/lib/commercial/form.ts',
  commercial: '../src/lib/api/commercial.ts',
  copy: '../src/lib/i18n/translations/commercial.ts',
  navigation: '../src/lib/api/navigation.ts',
};
for (const [name, file] of Object.entries(files)) {
  const raw = readFileSync(new URL(file, import.meta.url), 'utf8').replace(/from '@\/lib\/commercial\/form'/g, "from './form'");
  writeFileSync(
    path.join(directory, `${name}.js`),
    ts.transpileModule(raw, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
  );
}
const require = createRequire(import.meta.url);
const client = require(path.join(directory, 'client.js'));
const form = require(path.join(directory, 'form.js'));
const { commercialApi, isTrustedCommercialDocument } = require(path.join(directory, 'commercial.js'));
const { commercialEn, commercialUr, commercialCopy } = require(path.join(directory, 'copy.js'));
const { routePermission } = require(path.join(directory, 'navigation.js'));

let calls = [];
function backend(handler = () => Response.json({ ok: true })) {
  mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options });
    return url === '/api/auth/csrf'
      ? Response.json({ token: 'synthetic-commercial-csrf', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' })
      : handler(url, options);
  });
}

beforeEach(() => {
  calls = [];
  client.invalidatePrivateRequests();
  client.allowPrivateRequests();
});
afterEach(() => {
  mock.restoreAll();
  client.invalidatePrivateRequests();
});
after(() => {
  for (const name of Object.keys(files)) unlinkSync(path.join(directory, `${name}.js`));
  rmdirSync(directory);
});

const line = (overrides = {}) => ({ id: 'l1', description: 'Design work', quantity: '2', unit: 'day', unitPrice: '500.00', ...overrides });
const draft = (overrides = {}) => ({ counterpartyName: 'Acme', documentDate: '2026-10-28', dueDate: '', notes: '', lines: [line()], ...overrides });

test('payloads send only client-owned fields with exact decimal strings and strip spoofed authority/totals', () => {
  const spoofed = {
    ...draft({ counterpartyName: '  Acme Traders ', notes: '   ', dueDate: '2026-11-27' }),
    businessId: 'foreign', actorUserId: 'foreign', status: 'FINALIZED', documentNumber: 'INV-000999',
    subtotal: '1.00', total: '1.00', financialEffect: 'POSTED',
    lines: [{ ...line({ description: ' Cabling ', quantity: ' 3.5 ', unit: '  ', unitPrice: '120.40' }), lineAmount: '9.99', total: '9.99' }],
  };
  const expectedLines = [{ description: 'Cabling', quantity: '3.5', unit: null, unitPrice: '120.40' }];
  assert.deepEqual(form.commercialCreatePayload(spoofed), {
    counterpartyName: 'Acme Traders', documentDate: '2026-10-28', dueDate: '2026-11-27', notes: null, lines: expectedLines,
  });
  assert.deepEqual(Object.keys(form.commercialUpdatePayload(spoofed, 4)).sort(),
    ['counterpartyName', 'documentDate', 'dueDate', 'expectedVersion', 'lines', 'notes']);
  assert.equal(form.commercialUpdatePayload(spoofed, 4).expectedVersion, 4);
  assert.deepEqual(form.commercialPreviewPayload(spoofed), { lines: expectedLines });
  assert.deepEqual(form.commercialFinalizePayload(2), { expectedVersion: 2 });
  assert.deepEqual(form.commercialVoidPayload(3, '  Duplicate  '), { expectedVersion: 3, reason: 'Duplicate' });
  assert.deepEqual(form.commercialCreatePayload(draft({ counterpartyName: ' ', documentDate: '', lines: [] })),
    { counterpartyName: null, documentDate: null, dueDate: null, notes: null, lines: [] });
});

test('client validation mirrors backend formats without computing authoritative amounts', () => {
  assert.deepEqual(form.validateCommercialDraft(draft()), {});
  const check = (overrides) => form.validateCommercialLines([line(overrides)]);
  assert.equal(check({ quantity: '0' })['lines[0].quantity'], 'positive');
  assert.equal(check({ quantity: '0.000000' })['lines[0].quantity'], 'positive');
  assert.equal(check({ quantity: '1.0000001' })['lines[0].quantity'], 'decimalPlaces');
  assert.equal(check({ quantity: '1000000000' })['lines[0].quantity'], 'range');
  assert.equal(check({ quantity: '0001' })['lines[0].quantity'], undefined);
  for (const bad of ['1e3', '-1', '1,000', '.5', '١']) assert.equal(check({ unitPrice: bad })['lines[0].unitPrice'], 'decimal', bad);
  assert.equal(check({ unitPrice: '1.001' })['lines[0].unitPrice'], 'decimalPlaces');
  assert.equal(check({ unitPrice: '1000000000000' })['lines[0].unitPrice'], 'range');
  assert.equal(check({ unitPrice: '0' })['lines[0].unitPrice'], undefined, 'Zero unit price is allowed');
  assert.equal(check({ description: ' ' })['lines[0].description'], 'required');
  // 1.5 x 333.33 is not exact; only the server decides that, so the client accepts the formats.
  assert.deepEqual(check({ quantity: '1.5', unitPrice: '333.33' }), {});
  assert.equal(form.validateCommercialDraft(draft({ dueDate: '2026-10-01' })).dueDate, 'dateOrder');
  assert.equal(form.validateCommercialDraft(draft({ documentDate: '2026-02-30' })).documentDate, 'date');
  assert.equal(form.validateCommercialDraft(draft({ counterpartyName: 'A\nB' })).counterpartyName, 'text');
  assert.equal(form.validateCommercialDraft(draft({ notes: 'Line one\nLine two' })).notes, undefined);
  assert.equal(form.validateCommercialDraft(draft({ counterpartyName: 'ب'.repeat(160) })).counterpartyName, undefined);
  assert.equal(form.validateCommercialDraft(draft({ counterpartyName: 'ب'.repeat(161) })).counterpartyName, 'tooLong');
  assert.equal(form.validateVoidReason('  '), 'required');
  assert.equal(form.validateVoidReason('x'.repeat(501)), 'tooLong');
  assert.equal(form.validateVoidReason('Entered twice'), null);
});

test('line editing helpers keep ids, cap at 100 lines and rebuild drafts from server documents', () => {
  let current = form.initialCommercialDraft('2026-10-28', 'a');
  assert.equal(current.documentDate, '2026-10-28');
  assert.equal(current.lines.length, 1);
  current = form.changeCommercialLine(current, 'a', 'unitPrice', '10');
  current = form.addCommercialLine(current, 'b');
  assert.deepEqual(current.lines.map(item => item.id), ['a', 'b']);
  assert.equal(current.lines[0].unitPrice, '10');
  current = form.removeCommercialLine(current, 'a');
  assert.deepEqual(current.lines.map(item => item.id), ['b']);
  let full = form.initialCommercialDraft('2026-10-28', 'x0');
  for (let index = 1; index < 105; index++) full = form.addCommercialLine(full, `x${index}`);
  assert.equal(full.lines.length, form.MAX_COMMERCIAL_LINES);
  const rebuilt = form.draftFromDocument({
    counterpartyName: null, documentDate: '2026-10-28', dueDate: null, notes: null,
    lines: [{ id: 's1', description: 'Work', quantity: '3.5', unit: null, unitPrice: '120.40', lineAmount: '421.40' }],
  });
  assert.deepEqual(rebuilt, { counterpartyName: '', documentDate: '2026-10-28', dueDate: '', notes: '',
    lines: [{ id: 's1', description: 'Work', quantity: '3.5', unit: '', unitPrice: '120.40' }] });
});

test('server codes map to localized issues, conflicts and finalize blockers; actions follow status and role', () => {
  assert.deepEqual(form.mapCommercialFieldErrors({
    'lines[1].lineAmount': 'line_amount_not_exact', 'lines[0].quantity': 'quantity_must_be_positive',
    dueDate: 'due_date_before_document_date', total: 'total_out_of_range', mystery: 'something_new',
  }), { 'lines[1].lineAmount': 'notExact', 'lines[0].quantity': 'positive', dueDate: 'dateOrder', total: 'range', mystery: 'invalid' });
  for (const code of ['stale_document_version', 'document_not_draft', 'document_already_finalized', 'document_already_void', 'invalid_document_transition', 'document_delete_forbidden']) {
    assert.equal(form.isCommercialConflictCode(code), true, code);
  }
  assert.equal(form.isCommercialConflictCode('commercial_document_invalid'), false);
  assert.equal(form.isCommercialConflictCode(undefined), false);
  assert.equal(form.isCommercialFinalizeBlocker('total_must_be_positive'), true);
  assert.deepEqual(form.commercialActions('DRAFT', true), { edit: true, remove: true, finalize: true, void: false });
  assert.deepEqual(form.commercialActions('FINALIZED', true), { edit: false, remove: false, finalize: false, void: true });
  assert.deepEqual(form.commercialActions('VOID', true), { edit: false, remove: false, finalize: false, void: false });
  assert.deepEqual(form.commercialActions('DRAFT', false), { edit: false, remove: false, finalize: false, void: false });
  assert.deepEqual(form.commercialActions('FINALIZED', false), { edit: false, remove: false, finalize: false, void: false });
});

test('commercialApi uses kind-scoped /api paths, CSRF on every mutation, and preserves conflict codes', async () => {
  backend((url, options) => {
    if (url.includes('/finalize')) return Response.json({ status: 409, error: 'stale_document_version', message: 'stale' }, { status: 409 });
    if (options?.method === 'DELETE') return new Response(null, { status: 204 });
    return Response.json({ ok: true, url });
  });
  const invoices = commercialApi('INVOICE');
  const bills = commercialApi('BILL');
  await invoices.list('DRAFT');
  await bills.get('b/1');
  await invoices.preview({ lines: [] });
  await invoices.create({ counterpartyName: null, documentDate: null, dueDate: null, notes: null, lines: [] });
  await bills.update('id-2', { counterpartyName: 'S', documentDate: null, dueDate: null, notes: null, lines: [], expectedVersion: 3 });
  await invoices.remove('id-3', 0);
  await bills.voidDocument('id-4', { expectedVersion: 1, reason: 'r' });
  await assert.rejects(invoices.finalize('id-5', { expectedVersion: 7 }), error => error.status === 409 && error.code === 'stale_document_version');
  const requests = calls.filter(call => call.url !== '/api/auth/csrf').map(call => [call.options.method, call.url]);
  assert.deepEqual(requests, [
    ['GET', '/api/invoices?status=DRAFT'],
    ['GET', '/api/bills/b%2F1'],
    ['POST', '/api/invoices/preview'],
    ['POST', '/api/invoices'],
    ['PUT', '/api/bills/id-2'],
    ['DELETE', '/api/invoices/id-3?expectedVersion=0'],
    ['POST', '/api/bills/id-4/void'],
    ['POST', '/api/invoices/id-5/finalize'],
  ]);
  for (const call of calls.filter(item => item.url !== '/api/auth/csrf' && item.options.method !== 'GET')) {
    assert.equal(call.options.headers.get('X-XSRF-TOKEN'), 'synthetic-commercial-csrf', call.url);
    assert.equal(call.options.credentials, 'same-origin');
  }
  assert.deepEqual(JSON.parse(calls.find(call => call.url === '/api/bills/id-2').options.body).expectedVersion, 3);
});

test('responses from another business, kind, currency or with a financial effect are not trusted', () => {
  const document = { businessId: 'b1', kind: 'INVOICE', baseCurrency: 'PKR', financialEffect: 'NONE' };
  assert.equal(isTrustedCommercialDocument(document, 'b1', 'INVOICE'), true);
  assert.equal(isTrustedCommercialDocument(document, 'b2', 'INVOICE'), false);
  assert.equal(isTrustedCommercialDocument(document, 'b1', 'BILL'), false);
  assert.equal(isTrustedCommercialDocument({ ...document, financialEffect: 'POSTED' }, 'b1', 'INVOICE'), false);
  assert.equal(isTrustedCommercialDocument({ ...document, baseCurrency: 'USD' }, 'b1', 'INVOICE'), false);
});

test('navigation keeps reads on FINANCIAL_DATA_READ and creation on RECORD_CREATE_UPDATE', () => {
  for (const route of ['/invoices', '/bills', '/invoices/abc', '/bills/abc']) assert.equal(routePermission(route), 'FINANCIAL_DATA_READ', route);
  for (const route of ['/invoices/new', '/bills/new', '/projects/new', '/records/new']) assert.equal(routePermission(route), 'RECORD_CREATE_UPDATE', route);
  assert.equal(routePermission('/projects'), 'FINANCIAL_DATA_READ');
  const sidebar = readFileSync(new URL('../src/components/layout/Sidebar.tsx', import.meta.url), 'utf8');
  for (const href of ['/estimates', '/reports', '/projects', '/invoices', '/bills', '/records/new', '/scan']) assert.ok(sidebar.includes(`'${href}'`) || sidebar.includes(`"${href}"`), href);
  const mobile = readFileSync(new URL('../src/components/layout/MobileNav.tsx', import.meta.url), 'utf8');
  for (const href of ['/projects', '/estimates', '/reports', '/invoices', '/bills']) assert.ok(mobile.includes(`allowed('${href}')`), href);
});

test('money is formatted from exact server strings in EN and UR without floating-point coercion', () => {
  assert.equal(form.formatCommercialMoney('1421.40', 'en'), 'PKR 1,421.40');
  assert.equal(form.formatCommercialMoney('9999999999999999.99', 'en'), 'PKR 9,999,999,999,999,999.99');
  assert.equal(form.formatCommercialMoney('0.00', 'ur'), '0.00 روپے');
  assert.equal(form.formatCommercialMoney(null, 'en'), '—');
  assert.equal(form.formatCommercialMoney('NaN', 'en'), '—');
  assert.equal(form.todayIso(new Date(2026, 0, 5)), '2026-01-05');
  const instant = new Date(2026, 9, 28, 2, 52).toISOString();
  assert.equal(form.localDateOf(instant), '2026-10-28', 'UTC instants display in the local calendar');
  assert.equal(form.localDateOf(null), null);
  assert.equal(form.localDateOf('not-a-date'), null);
  for (const file of ['../src/lib/commercial/form.ts', '../src/lib/api/commercial.ts', '../src/components/commercial/CommercialDocumentWorkspace.tsx',
    '../src/components/commercial/CommercialLineEditor.tsx', '../src/components/commercial/CommercialDocumentList.tsx']) {
    const source = readFileSync(new URL(file, import.meta.url), 'utf8');
    assert.doesNotMatch(source, /parseFloat|Number\(|toFixed|Math\.round/, file);
    assert.doesNotMatch(source, /amountPaid|amountDue|outstandingBalance|paymentStatus/, file);
  }
});

test('English and Urdu copy share one structure and the finalize dialog states every non-effect', () => {
  const shape = value => Array.isArray(value) ? value.length : value && typeof value === 'object'
    ? Object.fromEntries(Object.entries(value).map(([key, inner]) => [key, shape(inner)])) : typeof value;
  assert.deepEqual(shape(commercialUr), shape(commercialEn));
  assert.equal(commercialCopy('ur'), commercialUr);
  assert.equal(commercialCopy('en'), commercialEn);
  assert.equal(commercialEn.finalize.body, 'Finalizing records and freezes this commercial document only.');
  assert.deepEqual(commercialEn.finalize.notList, ['post money', 'create a receivable or payable', 'record a payment', 'alter financial health calculations']);
  assert.match(commercialEn.finalize.notIntro, /does NOT/);
  for (const value of [commercialUr.finalize.body, commercialUr.kinds.INVOICE.title, commercialUr.kinds.BILL.title, commercialUr.recordOnlyBody]) {
    assert.match(value, /[؀-ۿ]/);
  }
  assert.equal(commercialUr.finalize.notList.length, 4);
  assert.match(commercialEn.settlementUnavailable, /not available/);
});
