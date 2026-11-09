import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import ts from 'typescript';

const require = createRequire(import.meta.url);
const root = fileURLToPath(new URL('../src/', import.meta.url));
const cache = new Map();

function load(file) {
  const resolved = path.resolve(root, file);
  if (cache.has(resolved)) return cache.get(resolved).exports;
  const raw = readFileSync(resolved, 'utf8');
  const m = { exports: {} };
  cache.set(resolved, m);
  const code = ts.transpileModule(raw, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
      jsx: ts.JsxEmit.ReactJSX,
      esModuleInterop: true,
    },
  }).outputText;
  const imports = (name) => {
    if (name.startsWith('@/') || name.startsWith('.')) {
      let target = name.startsWith('@/') ? path.join(root, name.slice(2)) : path.resolve(path.dirname(resolved), name);
      for (const ext of ['.ts', '.tsx']) {
        try {
          return load(target + ext);
        } catch (e) {
          if (e.code !== 'ENOENT') throw e;
        }
      }
      throw Error('Missing test import ' + name);
    }
    return require(name);
  };
  new Function('require', 'module', 'exports', code)(imports, m, m.exports);
  return m.exports;
}

const { projectActualsTranslations } = load('lib/i18n/translations/projectActuals.ts');
const { ProjectCostingOverviewCard } = load('components/finance/projects/ProjectCostingOverviewCard.tsx');
const { BudgetVsActualCard } = load('components/finance/projects/BudgetVsActualCard.tsx');
const { AttributionsTable } = load('components/finance/projects/AttributionsTable.tsx');
const { ProjectCommitmentsCard } = load('components/finance/projects/ProjectCommitmentsCard.tsx');
const { ProjectCommercialDocumentsCard } = load('components/finance/projects/ProjectCommercialDocumentsCard.tsx');
const { ProjectOcrEvidenceCard } = load('components/finance/projects/ProjectOcrEvidenceCard.tsx');
const { createElement: h } = require('react');
const { renderToStaticMarkup: render } = require('react-dom/server');

const mockSummary = {
  projectId: 'proj-102',
  projectCode: 'EXP-102',
  projectName: 'Export Order A-102',
  projectStatus: 'ACTIVE',
  baseCurrency: 'PKR',
  approvedBudgetRevision: 1,
  hasApprovedBudget: true,
  plannedRevenue: 400000,
  plannedCost: 330000,
  plannedMargin: 70000,
  plannedMarginPct: 17.5,
  attributedCashInflows: 0,
  attributedCashOutflows: 195000,
  cashHeadroom: 135000,
  cashHeadroomPct: 40.91,
  categories: [
    { category: 'MATERIAL', plannedCost: 200000, actualCashOutflow: 120000, cashHeadroom: 80000, recognizedCost: 0, nonCashEvidence: 0 },
    { category: 'LABOUR', plannedCost: 80000, actualCashOutflow: 50000, cashHeadroom: 30000, recognizedCost: 0, nonCashEvidence: 0 },
    { category: 'FREIGHT', plannedCost: 30000, actualCashOutflow: 25000, cashHeadroom: 5000, recognizedCost: 0, nonCashEvidence: 0 },
    { category: 'OVERHEAD', plannedCost: 20000, actualCashOutflow: 0, cashHeadroom: 20000, recognizedCost: 0, nonCashEvidence: 0 },
    { category: 'COMMISSION', plannedCost: 0, actualCashOutflow: 0, cashHeadroom: 0, recognizedCost: 5000, nonCashEvidence: 0 },
    { category: 'OTHER', plannedCost: 0, actualCashOutflow: 0, cashHeadroom: 0, recognizedCost: 0, nonCashEvidence: 0 },
  ],
  recognizedCommissionCost: 5000,
  nonCashFxImpact: 0,
  unattributedEligibleAmount: 105000,
  profitabilityStatus: 'UNAVAILABLE',
  unavailabilityReason: 'INCOMPLETE_COST_EVIDENCE',
  actualGrossProfit: null,
  actualGrossMarginPct: null,
  coverageNotes: 'Actual project profitability requires complete recognized revenue and recognized cost basis.',
};

const mockAttributions = [
  {
    id: 'attr-1',
    businessId: 'biz-1',
    projectId: 'proj-102',
    sourceType: 'TRANSACTION',
    sourceId: 'tx-1',
    sourceDirection: 'OUTFLOW',
    sourceCurrency: 'PKR',
    sourceEligibleAmount: 120000,
    attributedAmount: 120000,
    attributedCurrency: 'PKR',
    costCategory: 'MATERIAL',
    economicClassification: 'POSTED_CASH_ACTUAL',
    sourceEventDate: '2026-11-01',
    attributionDate: '2026-11-01',
    notes: 'Material supplier payment',
    status: 'ACTIVE',
    baseAmount: 120000,
    createdByUserId: 'user-1',
    createdAt: '2026-11-01T10:00:00Z',
  },
];

test('ProjectCostingOverviewCard renders project code, headroom, and UNAVAILABLE profitability badge in EN & UR', () => {
  for (const locale of ['en', 'ur']) {
    const copy = projectActualsTranslations[locale];
    const html = render(h(ProjectCostingOverviewCard, { summary: mockSummary, copy }));
    assert.ok(html.includes('EXP-102'));
    assert.ok(html.includes('Export Order A-102'));
    assert.ok(html.includes('330,000.00')); // Planned cost
    assert.ok(html.includes('195,000.00')); // Cash outflows
    assert.ok(html.includes('135,000.00')); // Cash headroom
    assert.ok(html.includes('40.91%')); // Headroom pct
    assert.ok(html.includes(copy.overview.profitabilityUnavailable));
  }
});

test('BudgetVsActualCard renders category breakdown matching Fixture A numerical values', () => {
  const copy = projectActualsTranslations.en;
  const html = render(
    h(BudgetVsActualCard, {
      categories: mockSummary.categories,
      baseCurrency: 'PKR',
      hasApprovedBudget: true,
      copy,
    })
  );
  assert.ok(html.includes('200,000.00')); // Material planned
  assert.ok(html.includes('120,000.00')); // Material actual
  assert.ok(html.includes('80,000.00')); // Material headroom
  assert.ok(html.includes('50,000.00')); // Labour actual
  assert.ok(html.includes('30,000.00')); // Labour headroom
  assert.ok(html.includes('25,000.00')); // Freight actual
  assert.ok(html.includes('5,000.00')); // Freight headroom
  assert.ok(html.includes('20,000.00')); // Overhead planned & headroom
});

test('AttributionsTable renders attributed items with reverse action', () => {
  const copy = projectActualsTranslations.en;
  const html = render(
    h(AttributionsTable, {
      attributions: mockAttributions,
      baseCurrency: 'PKR',
      onReverse: async () => {},
      copy,
    })
  );
  assert.ok(html.includes('TRANSACTION'));
  assert.ok(html.includes('120,000.00'));
  assert.ok(html.includes('Material'));
  assert.ok(html.includes('Material supplier payment'));
  assert.ok(html.includes('Reverse'));
});

test('English and Urdu project actuals translations have matching key structure', () => {
  const en = projectActualsTranslations.en;
  const ur = projectActualsTranslations.ur;
  assert.equal(typeof en.title, 'string');
  assert.equal(typeof ur.title, 'string');
  assert.deepEqual(Object.keys(en.overview), Object.keys(ur.overview));
  assert.deepEqual(Object.keys(en.budgetVsActual), Object.keys(ur.budgetVsActual));
  assert.deepEqual(Object.keys(en.commitments), Object.keys(ur.commitments));
  assert.deepEqual(Object.keys(en.commercialDocuments), Object.keys(ur.commercialDocuments));
  assert.deepEqual(Object.keys(en.attributions), Object.keys(ur.attributions));
  assert.deepEqual(Object.keys(en.dialog), Object.keys(ur.dialog));
  assert.deepEqual(Object.keys(en.linkObligationDialog), Object.keys(ur.linkObligationDialog));
  assert.deepEqual(Object.keys(en.linkCommercialDocDialog), Object.keys(ur.linkCommercialDocDialog));
  assert.deepEqual(Object.keys(en.categories), Object.keys(ur.categories));
  assert.deepEqual(Object.keys(en.statusLabels), Object.keys(ur.statusLabels));
});

test('ProjectCommitmentsCard renders supplier payable and non-duplication evidence', () => {
  const copy = projectActualsTranslations.en;
  const mockObligations = [
    {
      linkId: 'link-1',
      obligationId: 'ob-1',
      direction: 'PAYABLE',
      counterpartyName: 'Al-Karam Textiles',
      referenceNumber: 'PO-9001',
      issueDate: '2026-11-01',
      dueDate: '2026-11-30',
      originalAmount: 100000,
      allocatedAmount: 100000,
      paidOrCollectedAmount: 40000,
      outstandingAmount: 60000,
      currency: 'PKR',
      obligationStatus: 'OPEN',
      linkStatus: 'ACTIVE',
      paymentHistory: [],
      hasAttributedPayment: true,
      attributedCashAmount: 40000,
      notes: 'PO-9001 for Order A-102',
    },
  ];

  const html = render(
    h(ProjectCommitmentsCard, {
      obligations: mockObligations,
      baseCurrency: 'PKR',
      copy,
      onLinkClick: () => {},
      onUnlink: async () => {},
    })
  );

  assert.ok(html.includes('Al-Karam Textiles'));
  assert.ok(html.includes('PO-9001'));
  assert.ok(html.includes('100,000.00')); // Original amount
  assert.ok(html.includes('40,000.00'));  // Paid amount
  assert.ok(html.includes('60,000.00'));  // Outstanding amount
  assert.ok(html.includes('C05A PKR 40,000.00')); // Non-duplication badge
  assert.ok(html.includes('PAYABLE'));
  assert.ok(html.includes('Unlink'));
});

test('ProjectCommercialDocumentsCard renders non-posting banner and unposted status badge', () => {
  const copy = projectActualsTranslations.en;
  const mockDocs = [
    {
      id: 'doc-link-1',
      businessId: 'biz-1',
      projectId: 'proj-102',
      documentId: 'doc-1',
      documentKind: 'BILL',
      documentNumber: 'BILL-000001',
      counterpartyName: 'Packages Ltd',
      documentDate: '2026-11-01',
      dueDate: '2026-11-30',
      documentTotal: 50000,
      allocatedAmount: 50000,
      currency: 'PKR',
      linkStatus: 'ACTIVE',
      accountingStatus: 'COMMERCIAL_UNPOSTED',
      isMatchedToFinancialObligation: false,
      notes: 'Cartons for A-102',
      createdAt: '2026-11-01T10:00:00Z',
    },
  ];

  const html = render(
    h(ProjectCommercialDocumentsCard, {
      documents: mockDocs,
      baseCurrency: 'PKR',
      copy,
      onLinkClick: () => {},
      onUnlink: async () => {},
    })
  );

  assert.ok(html.includes('Packages Ltd'));
  assert.ok(html.includes('BILL-000001'));
  assert.ok(html.includes('50,000.00'));
  assert.ok(html.includes('BILL'));
  assert.ok(html.includes(copy.commercialDocuments.nonPostingBanner)); // Non-posting banner
  assert.ok(html.includes(copy.commercialDocuments.unpostedBadge));   // Commercial Unposted
});

test('ProjectOcrEvidenceCard renders financial rule banner, status badges, discrepancy, and split indicators', () => {
  const copy = projectActualsTranslations.en;
  const mockEvidence = [
    {
      id: 'ocr-link-1',
      projectId: 'proj-102',
      projectName: 'Export Order A-102',
      documentId: 'doc-1',
      originalFilename: 'receipt-fabric-100k.pdf',
      evidenceStatus: 'ATTRIBUTED',
      financialSourceType: 'TRANSACTION',
      financialSourceId: 'tx-1',
      attributionId: 'attr-1',
      matchMethod: 'MANUAL_ASSISTED',
      ocrAmount: 100000,
      ocrCurrency: 'PKR',
      ocrDate: '2026-11-01',
      counterpartyName: 'Al-Karam Fabric',
      category: 'materials',
      documentType: 'receipt',
      confidence: 0.98,
      discrepancyAmount: 0,
      hasDiscrepancy: false,
      isDuplicate: false,
      duplicateCount: 1,
      createdAt: '2026-11-01T10:00:00Z',
      matchedTransactionAmount: 100000,
    },
    {
      id: 'ocr-link-2',
      projectId: 'proj-102',
      projectName: 'Export Order A-102',
      documentId: 'doc-2',
      originalFilename: 'hardware-85k.pdf',
      evidenceStatus: 'ATTRIBUTED',
      financialSourceType: 'TRANSACTION',
      financialSourceId: 'tx-2',
      attributionId: 'attr-2',
      matchMethod: 'MANUAL_ASSISTED',
      ocrAmount: 85000,
      ocrCurrency: 'PKR',
      ocrDate: '2026-11-02',
      counterpartyName: 'Hardware Store',
      category: 'materials',
      documentType: 'receipt',
      confidence: 0.95,
      discrepancyAmount: 5000,
      discrepancyNotes: 'Discrepancy of PKR 5,000.00',
      hasDiscrepancy: true,
      isDuplicate: true,
      duplicateCount: 2,
      createdAt: '2026-11-02T10:00:00Z',
      matchedTransactionAmount: 80000,
    },
  ];

  const html = render(
    h(ProjectOcrEvidenceCard, {
      evidenceList: mockEvidence,
      baseCurrency: 'PKR',
      copy,
      onLinkClick: () => {},
      onConfirmMatchClick: () => {},
      onInvalidate: async () => {},
    })
  );

  // Central financial rule banner
  assert.ok(html.includes(copy.ocrEvidence.evidenceRuleBanner));
  assert.ok(html.includes('receipt-fabric-100k.pdf'));
  assert.ok(html.includes('Al-Karam Fabric'));
  assert.ok(html.includes('100,000.00'));
  assert.ok(html.includes('ATTRIBUTED'));

  // Discrepancy warning banner
  assert.ok(html.includes('Discrepancy: PKR 5,000.00'));
  // Split across projects badge
  assert.ok(html.includes('Split Across Projects (2)'));
});

test('ProjectOcrEvidenceCard renders bilingual Urdu content properly', () => {
  const copyUr = projectActualsTranslations.ur;
  const htmlUr = render(
    h(ProjectOcrEvidenceCard, {
      evidenceList: [],
      baseCurrency: 'PKR',
      copy: copyUr,
      onLinkClick: () => {},
      onConfirmMatchClick: () => {},
      onInvalidate: async () => {},
    })
  );

  assert.ok(htmlUr.includes(copyUr.ocrEvidence.title));
  assert.ok(htmlUr.includes(copyUr.ocrEvidence.evidenceRuleBanner));
  assert.ok(htmlUr.includes(copyUr.ocrEvidence.noEvidence));
});
