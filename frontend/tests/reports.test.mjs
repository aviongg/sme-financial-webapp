import assert from 'node:assert/strict';
import { after, afterEach, beforeEach, mock, test } from 'node:test';
import { mkdtempSync, readFileSync, writeFileSync, unlinkSync, rmdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import ts from 'typescript';

const directory = mkdtempSync(path.join(tmpdir(), 'finsight-report-contracts-'));
const files = {
  client: '../src/lib/api/client.ts', reports: '../src/lib/api/reports.ts', period: '../src/lib/reports/period.ts',
  format: '../src/lib/reports/format.ts', copy: '../src/lib/i18n/translations/reports.ts',
  navigation: '../src/lib/api/navigation.ts', configuration: '../src/lib/api/configuration.ts',
};
for (const [name, file] of Object.entries(files)) writeFileSync(path.join(directory, `${name}.js`), ts.transpileModule(
  readFileSync(new URL(file, import.meta.url), 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText);
const require = createRequire(import.meta.url), client = require(path.join(directory, 'client.js'));
const { reportsApi, reportPayload } = require(path.join(directory, 'reports.js'));
const period = require(path.join(directory, 'period.js'));
const format = require(path.join(directory, 'format.js'));
const { reportsEn, reportsUr, reportsCopy } = require(path.join(directory, 'copy.js'));
const { routePermission } = require(path.join(directory, 'navigation.js'));
const { routeModule } = require(path.join(directory, 'configuration.js'));
let calls = [];
function backend(handler = () => Response.json({ ok: true })) {
  mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options });
    return url === '/api/auth/csrf' ? Response.json({ token: 'synthetic-report-csrf', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }) : handler(url, options);
  });
}
beforeEach(() => { calls = []; client.invalidatePrivateRequests(); client.allowPrivateRequests(); });
afterEach(() => { mock.restoreAll(); client.invalidatePrivateRequests(); });
after(() => { for (const name of Object.keys(files)) unlinkSync(path.join(directory, `${name}.js`)); rmdirSync(directory); });
const source = relative => readFileSync(new URL(relative, import.meta.url), 'utf8');

test('preview sends only period and language through same-origin CSRF and never tenant, user, metric or SQL authority', async () => {
  backend();
  const hostile = { fromMonth: '2026-01', toMonth: '2026-03', language: 'ur', businessId: 'other', userId: 'actor', sql: 'select 1', metrics: ['x'], currency: 'USD' };
  await reportsApi.preview(hostile);
  const call = calls.at(-1);
  assert.equal(call.url, '/api/reports/business-financial-summary'); assert.equal(call.options.method, 'POST');
  assert.equal(call.options.credentials, 'same-origin'); assert.equal(call.options.headers.get('X-XSRF-TOKEN'), 'synthetic-report-csrf');
  assert.equal(call.options.headers.get('Accept'), 'application/json');
  assert.deepEqual(JSON.parse(call.options.body), { fromMonth: '2026-01', toMonth: '2026-03', language: 'ur' });
  assert.deepEqual(reportPayload(hostile), JSON.parse(call.options.body));
});

test('CSV export posts the same bounded request, accepts text/csv, returns the file and a safe server file name', async () => {
  backend(() => new Response('\ufeffsection,period\r\n', { headers: { 'Content-Type': 'text/csv; charset=UTF-8', 'Content-Disposition': 'attachment; filename="finsight-business-financial-summary_2026-01_2026-03.csv"' } }));
  const file = await reportsApi.exportCsv({ fromMonth: '2026-01', toMonth: '2026-03', language: 'en', businessId: 'x' });
  const call = calls.at(-1);
  assert.equal(call.url, '/api/reports/business-financial-summary.csv'); assert.equal(call.options.method, 'POST');
  assert.equal(call.options.headers.get('X-XSRF-TOKEN'), 'synthetic-report-csrf');
  assert.match(call.options.headers.get('Accept'), /^text\/csv/);
  assert.deepEqual(JSON.parse(call.options.body), { fromMonth: '2026-01', toMonth: '2026-03', language: 'en' });
  assert.equal(file.fileName, 'finsight-business-financial-summary_2026-01_2026-03.csv');
  const bytes = new Uint8Array(await file.blob.arrayBuffer());
  assert.deepEqual([...bytes.slice(0, 3)], [0xef, 0xbb, 0xbf]);
  assert.equal(await file.blob.text(), 'section,period\r\n');
});

test('an unsafe or missing Content-Disposition never becomes a download name, and server errors surface as ApiError', async () => {
  backend(() => new Response('x', { headers: { 'Content-Disposition': 'attachment; filename="../../evil.csv"' } }));
  assert.equal((await reportsApi.exportCsv({ fromMonth: '2026-01', toMonth: '2026-01', language: 'en' })).fileName, null);
  backend(() => Response.json({ error: 'report_invalid', message: 'bad', errors: { toMonth: 'range_too_long' } }, { status: 400 }));
  await assert.rejects(() => reportsApi.exportCsv({ fromMonth: '2026-01', toMonth: '2026-01', language: 'en' }),
    error => error.name === 'ApiError' && error.status === 400 && error.code === 'report_invalid');
  backend(() => Response.json({ error: 'Forbidden', message: 'no' }, { status: 403 }));
  await assert.rejects(() => reportsApi.preview({ fromMonth: '2026-01', toMonth: '2026-01', language: 'en' }), error => error.status === 403);
});

test('cancelling an export or preview aborts the request and a delayed file is never delivered', async () => {
  let finish, announce; const reached = new Promise(resolve => { announce = resolve; });
  backend(() => new Promise(resolve => { finish = resolve; announce(); }));
  const controller = new AbortController();
  const pending = reportsApi.exportCsv({ fromMonth: '2026-01', toMonth: '2026-01', language: 'en' }, controller.signal); await reached;
  controller.abort(); finish(new Response('late'));
  await assert.rejects(() => pending, error => error.name === 'AbortError');
});

test('a session or business switch blocks private report requests and aborts the pending one', async () => {
  let announce, finish; const reached = new Promise(resolve => { announce = resolve; });
  backend(() => new Promise(resolve => { finish = resolve; announce(); }));
  const pending = reportsApi.preview({ fromMonth: '2026-01', toMonth: '2026-01', language: 'en' }); await reached;
  client.invalidatePrivateRequests(); finish(Response.json({ businessId: 'previous-business' }));
  await assert.rejects(() => pending, error => error.name === 'AbortError');
  await assert.rejects(() => reportsApi.exportCsv({ fromMonth: '2026-01', toMonth: '2026-01', language: 'en' }), error => error.name === 'AbortError');
});

test('amounts keep exact decimal text, show not-recorded for null and never turn missing data into zero', () => {
  const none = 'Not recorded';
  assert.equal(format.formatReportAmount(null, 'en', none), none);
  assert.equal(format.formatReportAmount(undefined, 'ur', 'درج نہیں'), 'درج نہیں');
  assert.equal(format.formatReportAmount('0.00', 'en', none), 'PKR 0.00');
  assert.equal(format.formatReportAmount('1234567.5', 'en', none), 'PKR 1,234,567.50');
  assert.equal(format.formatReportAmount('99999999999.99', 'en', none), 'PKR 99,999,999,999.99');
  assert.equal(format.formatReportAmount('-180.00', 'en', none), '- PKR 180.00');
  assert.equal(format.formatReportAmount('1000.10', 'ur', none), '1,000.10 روپے');
  assert.equal(format.formatReportAmount('0.1234567890123', 'en', none), 'PKR 0.1234567890123');
  assert.equal(format.formatReportAmount('NaN', 'en', none), none);
  assert.equal(format.formatReportAmount('1e6', 'en', none), none);
  assert.equal(format.formatReportScore(null, none), none);
  assert.equal(format.formatReportScore('61.50', none), '61.50');
  assert.equal(format.formatReportCompleteness('0.85', none), '85%');
  assert.equal(format.formatReportCompleteness('1.00', none), '100%');
  assert.equal(format.formatReportCompleteness('0.05', none), '5%');
  assert.equal(format.formatReportCompleteness('0.00', none), '0%');
  assert.equal(format.formatReportCompleteness(null, none), none);
});

test('period selection is bounded, ordered and year-boundary safe', () => {
  assert.equal(period.validatePeriod('2026-01', '2026-01'), null);
  assert.equal(period.validatePeriod('2024-01', '2025-12'), null);
  assert.equal(period.validatePeriod('2024-01', '2026-01'), 'tooLong');
  assert.equal(period.validatePeriod('2026-02', '2026-01'), 'reversed');
  assert.equal(period.validatePeriod('', '2026-01'), 'required');
  assert.equal(period.validatePeriod('2026-13', '2026-14'), 'required');
  assert.equal(period.MAX_REPORT_MONTHS, 24);
  assert.deepEqual(period.defaultPeriod(new Date(2026, 0, 15)), { fromMonth: '2025-11', toMonth: '2026-01' });
  assert.deepEqual(period.defaultPeriod(new Date(2026, 9, 25)), { fromMonth: '2026-08', toMonth: '2026-10' });
  const options = period.monthOptions(new Date(2026, 9, 25), 'en', ['2020-02']);
  assert.equal(options[0].value, '2026-10'); assert.equal(options.at(-1).value, '2020-02');
  assert.equal(new Set(options.map(option => option.value)).size, options.length);
  assert.deepEqual(options.map(option => option.value), [...options.map(option => option.value)].sort().reverse());
  assert.match(options[0].label, /October 2026/);
  assert.ok(period.monthOptions(new Date(2026, 9, 25), 'ur').every(option => /[\u0600-\u06ff]/.test(option.label)));
  assert.equal(period.monthIndex('2026-01') - period.monthIndex('2025-12'), 1);
});

test('English and Urdu cover every report field, state, limitation and semantics disclosure', () => {
  function verify(en, ur, trail) {
    assert.deepEqual(Object.keys(en).sort(), Object.keys(ur).sort(), trail);
    for (const key of Object.keys(en)) {
      assert.equal(typeof en[key], typeof ur[key], `${trail}.${key}`);
      if (typeof en[key] === 'object') verify(en[key], ur[key], `${trail}.${key}`);
      else if (typeof en[key] === 'function') assert.ok(String(ur[key](3, 5)).trim() && String(ur[key]('2026-01')).trim(), key);
      else assert.ok(ur[key].trim(), `${trail}.${key}`);
    }
  }
  verify(reportsEn, reportsUr, 'reports');
  for (const key of ['intro', 'readOnly', 'semanticsNote', 'currencyNote', 'flowsHelp', 'balancesHelp', 'healthHelp', 'noData', 'noAccess', 'exportFailed'])
    assert.match(reportsUr[key], /[\u0600-\u06ff]/, key);
  assert.equal(reportsCopy('ur'), reportsUr); assert.equal(reportsCopy('en'), reportsEn);
  assert.match(reportsEn.latestBalances, /Latest recorded balance/);
  assert.match(reportsEn.balancesHelp, /not period totals/); assert.match(reportsEn.healthHelp, /not an average/);
  assert.match(reportsUr.balancesHelp, /مدت کے کل نہیں/); assert.match(reportsUr.healthHelp, /اوسط نہیں/);
  assert.match(reportsEn.periodIssues.tooLong(24), /24/); assert.match(reportsUr.periodIssues.tooLong(24), /24/);
});

test('every limitation code the backend can emit has English and Urdu copy and a CSV label', () => {
  const assembler = source('../../backend/src/main/java/com/app/sme_health_backend/reports/service/BusinessFinancialReportAssembler.java');
  const labels = source('../../backend/src/main/java/com/app/sme_health_backend/reports/service/ReportCsvLabels.java');
  const codes = [...assembler.matchAll(/codes\.add\("([A-Z_]+)"\)/g)].map(match => match[1]);
  assert.ok(codes.length >= 9);
  for (const code of codes) {
    assert.ok(reportsEn.limitations[code], code); assert.match(reportsUr.limitations[code], /[\u0600-\u06ff]/, code);
    assert.ok(labels.includes(`"${code}"`), `${code} CSV label`);
  }
});

test('reports use the existing financial-read permission and do not enable the future reports module', () => {
  assert.equal(routePermission('/reports'), 'FINANCIAL_DATA_READ'); assert.equal(routeModule('/reports'), null);
});

test('browser report code never converts, sums or averages financial values', () => {
  const sources = ['../src/lib/reports/format.ts', '../src/lib/api/reports.ts', '../src/components/reports/BusinessFinancialReportView.tsx'];
  for (const file of sources) {
    const tree = ts.createSourceFile(file, source(file), ts.ScriptTarget.Latest, true, file.endsWith('tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS);
    const violations = [];
    function visit(node) {
      if (ts.isCallExpression(node) && /^(Number|parseFloat|parseInt|BigInt)$/.test(node.expression.getText(tree))) violations.push(node.expression.getText(tree));
      if (ts.isBinaryExpression(node) && [ts.SyntaxKind.AsteriskToken, ts.SyntaxKind.SlashToken, ts.SyntaxKind.MinusToken, ts.SyntaxKind.PlusToken].includes(node.operatorToken.kind)
        && !ts.isStringLiteralLike(node.left) && !ts.isStringLiteralLike(node.right) && !ts.isTemplateExpression(node.left) && !ts.isTemplateExpression(node.right)
        && /(revenue|cogs|total|balance|amount|score|inflow|outflow)/i.test(node.getText(tree))) violations.push(node.getText(tree));
      if (ts.isPropertyAccessExpression(node) && ['toFixed', 'toPrecision', 'valueAsNumber', 'reduce'].includes(node.name.text)) violations.push(node.getText(tree));
      ts.forEachChild(node, visit);
    }
    visit(tree); assert.deepEqual(violations, [], file);
  }
});

test('the report view labels flows, latest balances and latest health, hides actions in print and scopes print CSS to itself', () => {
  const view = source('../src/components/reports/BusinessFinancialReportView.tsx');
  for (const marker of ['data-report-print', 'print:hidden', 'copy.latestBalances', 'copy.healthTitle', 'copy.periodFlows', 'copy.semanticsNote', 'copy.currencyNote',
    'formatGeneratedAt', 'data-null', 'report-export', 'report-print', 'window.print()', 'lg:hidden print:hidden', 'hidden lg:block print:block'])
    assert.ok(view.includes(marker), marker);
  for (const testId of ['report-controls']) assert.ok(view.includes(testId));
  const css = source('../src/app/globals.css');
  assert.match(css, /@media print/); assert.match(css, /body:has\(\[data-report-print\]\) :is\(aside, header, nav\)/);
  assert.doesNotMatch(css.slice(css.indexOf('@media print')), /^\s*(aside|header|nav)\s*[,{]/m);
});

test('the report page is wired into desktop and mobile navigation for live mode only', () => {
  const sidebar = source('../src/components/layout/Sidebar.tsx'), mobile = source('../src/components/layout/MobileNav.tsx');
  assert.match(sidebar, /href: '\/reports'/); assert.match(sidebar, /!isDemoMode \? \[\{ label: reportsCopy\(locale\)\.nav, href: '\/reports'/);
  assert.match(mobile, /allowed\('\/reports'\)/); assert.match(mobile, /handleActionSelect\('\/reports'\)/);
  assert.match(source('../src/app/reports/page.tsx'), /BusinessFinancialReportView/);
});
