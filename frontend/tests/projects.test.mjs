import assert from 'node:assert/strict';
import { after, afterEach, beforeEach, mock, test } from 'node:test';
import { mkdtempSync, readFileSync, writeFileSync, unlinkSync, rmdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import ts from 'typescript';

const directory = mkdtempSync(path.join(tmpdir(), 'finsight-project-contracts-'));
const files = {
  client: '../src/lib/api/client.ts',
  form: '../src/lib/projects/form.ts',
  projects: '../src/lib/api/projects.ts',
  copy: '../src/lib/i18n/translations/projects.ts',
  navigation: '../src/lib/api/navigation.ts',
  configuration: '../src/lib/api/configuration.ts',
};
for (const [name, file] of Object.entries(files)) {
  const raw = readFileSync(new URL(file, import.meta.url), 'utf8').replace(
    /from '@\/lib\/projects\/form'/g,
    "from './form'"
  );
  writeFileSync(
    path.join(directory, `${name}.js`),
    ts.transpileModule(raw, {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    }).outputText
  );
}
const require = createRequire(import.meta.url);
const client = require(path.join(directory, 'client.js'));
const {
  initialProjectMetadata,
  metadataFromProject,
  initialBudgetDraft,
  draftFromBudgetVersion,
  addBudgetLine,
  changeBudgetLine,
  removeBudgetLine,
  validateProjectMetadata,
  validateProjectBudget,
  mapProjectFieldErrors,
  projectCreatePayload,
  projectUpdatePayload,
  projectTransitionPayload,
  projectBudgetCreatePayload,
  projectBudgetUpdatePayload,
  projectBudgetApprovePayload,
  previewBudgetCalculation,
  allowedProjectTransitions,
  canMutateProjectPlanning,
  isBudgetVersionReadOnly,
  isTransitionBlockedByOpenDraft,
  isProjectConflictCode,
  formatProjectMoney,
  formatProjectPercent,
} = require(path.join(directory, 'form.js'));
const { projectsApi } = require(path.join(directory, 'projects.js'));
const { projectsEn, projectsUr, projectsCopy } = require(path.join(directory, 'copy.js'));
const { routePermission } = require(path.join(directory, 'navigation.js'));
const { routeModule } = require(path.join(directory, 'configuration.js'));

let calls = [];
function backend(handler = () => Response.json({ ok: true })) {
  mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options });
    return url === '/api/auth/csrf'
      ? Response.json({ token: 'synthetic-project-csrf', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' })
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

test('project metadata payload normalizes uppercase code, trims text, converts blank optional fields to null and strips spoofed tenant fields', () => {
  const draft = {
    ...initialProjectMetadata('ORDER'),
    code: '  ord-2026/09  ',
    name: '  Custom Textile Batch  ',
    description: '   ',
    startDate: '2026-11-01',
    targetEndDate: '2026-12-15',
    businessId: 'foreign-business',
    actorUserId: 'foreign-actor',
    status: 'APPROVED',
  };
  assert.deepEqual(projectCreatePayload(draft), {
    code: 'ORD-2026/09',
    name: 'Custom Textile Batch',
    kind: 'ORDER',
    description: null,
    startDate: '2026-11-01',
    targetEndDate: '2026-12-15',
  });
  assert.deepEqual(projectUpdatePayload(draft, 2), {
    code: 'ORD-2026/09',
    name: 'Custom Textile Batch',
    kind: 'ORDER',
    description: null,
    startDate: '2026-11-01',
    targetEndDate: '2026-12-15',
    expectedVersion: 2,
  });
  assert.deepEqual(projectTransitionPayload('ACTIVE', '  Signed PO ', 3), {
    targetStatus: 'ACTIVE',
    reason: 'Signed PO',
    expectedVersion: 3,
  });
});

test('project metadata validation enforces code format, date order and required fields', () => {
  assert.equal(validateProjectMetadata(initialProjectMetadata()).code, 'required');
  assert.equal(validateProjectMetadata(initialProjectMetadata()).name, 'required');
  assert.equal(
    validateProjectMetadata({
      ...initialProjectMetadata(),
      code: 'BAD CODE',
      name: 'Valid Name',
    }).code,
    'code'
  );
  assert.equal(
    validateProjectMetadata({
      ...initialProjectMetadata(),
      code: 'PRJ-01',
      name: 'Valid Name',
      startDate: '2026-12-10',
      targetEndDate: '2026-12-01',
    }).targetEndDate,
    'dateOrder'
  );
});

test('project budget payload submits exact decimal strings and strips browser-supplied totals, line costs and tenant authority', () => {
  let draft = initialBudgetDraft();
  draft = {
    ...draft,
    plannedRevenue: '1200.00',
    plannedCost: '800.00',
    plannedMargin: '400.00',
    plannedMarginPct: '0.3333',
    businessId: 'other-business',
  };
  draft = changeBudgetLine(draft, '0', {
    category: 'MATERIAL',
    description: '  Raw cotton yarn  ',
    quantity: '2.5',
    unit: '  bale ',
    unitCost: '320.00',
    plannedLineCost: '800.00',
  });

  assert.deepEqual(projectBudgetCreatePayload(draft, 1), {
    expectedProjectVersion: 1,
    plannedRevenue: '1200.00',
    lines: [
      {
        category: 'MATERIAL',
        description: 'Raw cotton yarn',
        quantity: '2.5',
        unit: 'bale',
        unitCost: '320.00',
      },
    ],
  });

  assert.deepEqual(projectBudgetUpdatePayload(draft, 0), {
    expectedBudgetVersion: 0,
    plannedRevenue: '1200.00',
    lines: [
      {
        category: 'MATERIAL',
        description: 'Raw cotton yarn',
        quantity: '2.5',
        unit: 'bale',
        unitCost: '320.00',
      },
    ],
  });

  assert.deepEqual(projectBudgetApprovePayload(4, 1), {
    expectedProjectVersion: 4,
    expectedBudgetVersion: 1,
  });
});

test('budget line limits and decimal validation preserve zero planned revenue while rejecting malformed decimals', () => {
  let draft = {
    plannedRevenue: '0',
    lines: [
      {
        id: '0',
        category: 'LABOUR',
        description: 'Setup labour',
        quantity: '1',
        unit: 'day',
        unitCost: '500.00',
      },
    ],
  };
  assert.deepEqual(validateProjectBudget(draft), {});

  for (const invalid of ['-1', '1e3', 'NaN', '1,000', '.5', '1.', ' 1', '1000000000000', '0.1234567']) {
    assert.equal(validateProjectBudget({ ...draft, plannedRevenue: invalid }).plannedRevenue, 'decimal', invalid);
    const badLine = changeBudgetLine(draft, '0', { quantity: invalid, unitCost: invalid });
    assert.equal(validateProjectBudget(badLine)['lines[0].quantity'], 'decimal', invalid);
    assert.equal(validateProjectBudget(badLine)['lines[0].unitCost'], 'decimal', invalid);
  }
  assert.equal(validateProjectBudget({ ...draft, plannedRevenue: '100.12345' }).plannedRevenue, 'decimal');
  assert.equal(
    validateProjectBudget(changeBudgetLine(draft, '0', { quantity: '1000000000', unitCost: '10.123456' }))['lines[0].quantity'],
    'decimal'
  );
  assert.deepEqual(
    validateProjectBudget({
      ...draft,
      plannedRevenue: '999999999999.9999',
      lines: [
        {
          id: '0',
          category: 'LABOUR',
          description: 'Setup labour',
          quantity: '999999999.999999',
          unit: 'day',
          unitCost: '999999999999.999999',
        },
      ],
    }),
    {}
  );

  for (let i = 1; i < 100; i++) {
    draft = addBudgetLine(draft, String(i));
  }
  assert.equal(draft.lines.length, 100);
  assert.equal(addBudgetLine(draft, '100'), draft);
  const empty = removeBudgetLine({ ...initialBudgetDraft() }, '0');
  assert.equal(validateProjectBudget(empty).lines, 'lines');
});

test('UX preview calculation computes exact line costs, category subtotals, negative margin and zero-revenue unavailable margin %', () => {
  const positiveDraft = {
    plannedRevenue: '250000',
    lines: [
      { id: '0', category: 'MATERIAL', description: 'Inverter', quantity: '1', unit: 'unit', unitCost: '135000' },
      { id: '1', category: 'LABOUR', description: 'Crew', quantity: '40', unit: 'hour', unitCost: '1000' },
    ],
  };
  const positivePreview = previewBudgetCalculation(positiveDraft);
  assert.equal(positivePreview.previewOnly, true);
  assert.equal(positivePreview.valid, true);
  assert.equal(positivePreview.plannedRevenue, '250000.00');
  assert.equal(positivePreview.plannedCost, '175000.00');
  assert.equal(positivePreview.plannedMargin, '75000.00');
  assert.equal(positivePreview.plannedMarginPct, '0.3');
  assert.equal(positivePreview.zeroPlannedRevenue, false);
  assert.deepEqual(positivePreview.lineCosts, ['135000.00', '40000.00']);
  assert.equal(positivePreview.categorySubtotals.MATERIAL, '135000.00');
  assert.equal(positivePreview.categorySubtotals.LABOUR, '40000.00');

  const negativeDraft = {
    plannedRevenue: '100000',
    lines: [
      { id: '0', category: 'MATERIAL', description: 'Heavy parts', quantity: '1', unit: 'lot', unitCost: '125000' },
    ],
  };
  const negativePreview = previewBudgetCalculation(negativeDraft);
  assert.equal(negativePreview.plannedMargin, '-25000.00');
  assert.equal(negativePreview.plannedMarginPct, '-0.25');

  const zeroRevDraft = {
    plannedRevenue: '0',
    lines: [
      { id: '0', category: 'OVERHEAD', description: 'Internal tooling', quantity: '2', unit: 'unit', unitCost: '15000' },
    ],
  };
  const zeroPreview = previewBudgetCalculation(zeroRevDraft);
  assert.equal(zeroPreview.valid, true);
  assert.equal(zeroPreview.plannedRevenue, '0.00');
  assert.equal(zeroPreview.plannedCost, '30000.00');
  assert.equal(zeroPreview.plannedMargin, '-30000.00');
  assert.equal(zeroPreview.plannedMarginPct, null);
  assert.equal(zeroPreview.zeroPlannedRevenue, true);
});

test('lifecycle transitions, read-only history rules, draft cloning and conflict mapping behave deterministically', () => {
  assert.deepEqual(allowedProjectTransitions('DRAFT'), ['ACTIVE', 'CANCELLED', 'ARCHIVED']);
  assert.deepEqual(allowedProjectTransitions('ACTIVE'), ['COMPLETED', 'CANCELLED', 'ARCHIVED']);
  assert.deepEqual(allowedProjectTransitions('COMPLETED'), ['ARCHIVED']);
  assert.deepEqual(allowedProjectTransitions('CANCELLED'), ['ARCHIVED']);
  assert.deepEqual(allowedProjectTransitions('ARCHIVED'), []);

  assert.equal(canMutateProjectPlanning('DRAFT', true), true);
  assert.equal(canMutateProjectPlanning('ACTIVE', true), true);
  assert.equal(canMutateProjectPlanning('COMPLETED', true), false);
  assert.equal(canMutateProjectPlanning('ARCHIVED', true), false);
  assert.equal(canMutateProjectPlanning('DRAFT', false), false);

  assert.equal(isBudgetVersionReadOnly('DRAFT'), false);
  assert.equal(isBudgetVersionReadOnly('APPROVED'), true);
  assert.equal(isBudgetVersionReadOnly('SUPERSEDED'), true);

  assert.equal(isTransitionBlockedByOpenDraft('COMPLETED', true), true);
  assert.equal(isTransitionBlockedByOpenDraft('ARCHIVED', true), true);
  assert.equal(isTransitionBlockedByOpenDraft('ACTIVE', true), false);
  assert.equal(isTransitionBlockedByOpenDraft('COMPLETED', false), false);

  assert.equal(isProjectConflictCode('stale_project_version'), true);
  assert.equal(isProjectConflictCode('open_draft_budget_exists'), true);
  assert.equal(isProjectConflictCode('unknown_error'), false);

  const mappedErrors = mapProjectFieldErrors({
    code: 'code_invalid',
    targetEndDate: 'date_order_invalid',
    'lines[0].quantity': 'decimal_invalid',
  });
  assert.deepEqual(mappedErrors, {
    code: 'code',
    targetEndDate: 'dateOrder',
    'lines[0].quantity': 'decimal',
  });

  const cloned = draftFromBudgetVersion({
    plannedRevenue: '250000.00',
    lines: [
      { category: 'MATERIAL', description: 'Inverter', quantity: '1.000000', unit: 'unit', unitCost: '135000.00' },
    ],
  });
  assert.equal(cloned.plannedRevenue, '250000');
  assert.equal(cloned.lines[0].quantity, '1');
  assert.equal(cloned.lines[0].unitCost, '135000');

  const meta = metadataFromProject({
    code: 'PRJ-1',
    name: 'Name',
    kind: 'PROJECT',
    description: null,
    startDate: null,
    targetEndDate: '2026-12-31',
  });
  assert.equal(meta.description, '');
  assert.equal(meta.startDate, '');
  assert.equal(meta.targetEndDate, '2026-12-31');
});

test('projectsApi submits authenticated CSRF requests, preserves conflict codes and aborts on business/session switch', async () => {
  backend((url) => {
    if (url === '/api/projects?status=ACTIVE&kind=JOB&q=solar&includeArchived=true') {
      return Response.json({ businessId: 'b1', baseCurrency: 'PKR', currencyBasis: 'APPLICATION_FIXED_PKR', planningOnly: true, items: [] });
    }
    return Response.json({ ok: true });
  });

  await projectsApi.list({ status: 'ACTIVE', kind: 'JOB', q: ' solar ', includeArchived: true });
  assert.equal(calls.at(-1).url, '/api/projects?status=ACTIVE&kind=JOB&q=solar&includeArchived=true');

  await projectsApi.create({
    code: 'PRJ-10',
    name: 'Test Project',
    kind: 'PROJECT',
    description: null,
    startDate: null,
    targetEndDate: null,
  });
  const createCall = calls.at(-1);
  assert.equal(createCall.url, '/api/projects');
  assert.equal(createCall.options.method, 'POST');
  assert.equal(createCall.options.headers.get('X-XSRF-TOKEN'), 'synthetic-project-csrf');

  // 409 conflict preserves code and does not replay
  backend(() => Response.json({ status: 409, error: 'stale_project_version', message: 'Stale' }, { status: 409 }));
  await assert.rejects(
    () => projectsApi.approveBudget('proj-1', 'bud-1', { expectedProjectVersion: 1, expectedBudgetVersion: 0 }),
    error => error.status === 409 && error.code === 'stale_project_version'
  );

  // Business/session invalidation aborts pending request
  let finish;
  let announce;
  const reached = new Promise(resolve => { announce = resolve; });
  backend(() => new Promise(resolve => { finish = resolve; announce(); }));
  const pending = projectsApi.get('proj-1');
  await reached;
  client.invalidatePrivateRequests();
  client.allowPrivateRequests();
  finish(Response.json({ id: 'proj-1', businessId: 'old-business' }));
  await assert.rejects(() => pending, error => error.name === 'AbortError');
});

test('navigation permissions and module gating keep F05 routes properly scoped without activating F02/F13 actuals', () => {
  assert.equal(routePermission('/projects'), 'FINANCIAL_DATA_READ');
  assert.equal(routePermission('/projects/new'), 'RECORD_CREATE_UPDATE');
  assert.equal(routePermission('/projects/123'), 'FINANCIAL_DATA_READ');
  assert.equal(routeModule('/projects'), null);
  assert.equal(routeModule('/projects/new'), null);
});

test('money and percentage formatters preserve all server precision and handle null/negative values in EN and UR', () => {
  assert.equal(formatProjectMoney('1200.00', 'en'), 'PKR 1,200.00');
  assert.equal(formatProjectMoney('1200.00', 'ur'), '1,200.00 روپے');
  assert.equal(formatProjectMoney('-400.00', 'en'), '- PKR 400.00');
  assert.equal(formatProjectMoney(null, 'en'), '—');
  assert.equal(formatProjectPercent('0.2'), '20%');
  assert.equal(formatProjectPercent('0.166666666666666666666667'), '16.6666666666666666666667%');
  assert.equal(formatProjectPercent('-1'), '-100%');
  assert.equal(formatProjectPercent(null), '—');
});

test('English and Urdu project planning translations have identical structure and explicit Planned disclosures', () => {
  function verifyKeys(en, ur) {
    assert.deepEqual(Object.keys(en).sort(), Object.keys(ur).sort());
    for (const key of Object.keys(en)) {
      if (typeof en[key] === 'object') {
        verifyKeys(en[key], ur[key]);
      } else {
        assert.ok(typeof ur[key] === 'string' && ur[key].trim().length > 0, key);
      }
    }
  }
  verifyKeys(projectsEn, projectsUr);
  assert.equal(projectsCopy('en'), projectsEn);
  assert.equal(projectsCopy('ur'), projectsUr);
  assert.match(projectsEn.summaryCards.plannedRevenue, /Planned/);
  assert.match(projectsEn.summaryCards.plannedCost, /Planned/);
  assert.match(projectsEn.summaryCards.plannedMargin, /Planned/);
  assert.match(projectsEn.summaryCards.plannedMarginPct, /Planned/);
  assert.match(projectsUr.planningDisclosure, /[\u0600-\u06ff]/);
  assert.deepEqual(Object.keys(projectsUr.categories), ['MATERIAL', 'LABOUR', 'OVERHEAD', 'FREIGHT', 'OTHER']);
});

test('project planning frontend code never invokes IEEE-754 floating-point number coercion', () => {
  const sources = [
    '../src/lib/projects/form.ts',
    '../src/lib/api/projects.ts',
    '../src/lib/i18n/translations/projects.ts',
    '../src/components/projects/ProjectList.tsx',
    '../src/components/projects/ProjectForm.tsx',
    '../src/components/projects/ProjectDetailWorkspace.tsx',
  ];
  for (const file of sources) {
    const source = ts.createSourceFile(
      file,
      readFileSync(new URL(file, import.meta.url), 'utf8'),
      ts.ScriptTarget.Latest,
      true,
      file.endsWith('tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS
    );
    const violations = [];
    function visit(node) {
      if (ts.isCallExpression(node) && /^(Number|parseFloat|parseInt)$/.test(node.expression.getText(source))) {
        violations.push(node.expression.getText(source));
      }
      if (ts.isPropertyAccessExpression(node) && ['toFixed', 'toPrecision', 'valueAsNumber'].includes(node.name.text)) {
        violations.push(node.getText(source));
      }
      ts.forEachChild(node, visit);
    }
    visit(source);
    assert.deepEqual(violations, [], file);
  }
});
