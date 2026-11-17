import assert from 'node:assert/strict';
import { after, beforeEach, test } from 'node:test';
import { mkdtempSync, readFileSync, writeFileSync, unlinkSync, rmdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import ts from 'typescript';

const directory = mkdtempSync(path.join(tmpdir(), 'finsight-def11-'));
const source = (relativePath) => readFileSync(new URL(relativePath, import.meta.url), 'utf8');

const compileModule = (name, relativePath, rewrite = (code) => code) => {
  const raw = rewrite(source(relativePath));
  writeFileSync(
    path.join(directory, `${name}.js`),
    ts.transpileModule(raw, {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    }).outputText,
  );
};

compileModule('form', '../src/lib/commercial/form.ts');
compileModule('client', '../src/lib/api/client.ts');
compileModule('commercial', '../src/lib/api/commercial.ts', (code) =>
  code.replace(/from '\.\/client'/g, "from './client'").replace(/from '@\/lib\/commercial\/form'/g, "from './form'"),
);
compileModule('session', '../src/lib/api/session.ts');
compileModule('draftStorage', '../src/lib/commercial/draftStorage.ts', (code) =>
  code.replace(/from '\.\/form'/g, "from './form'").replace(/from '\.\.\/api\/commercial'/g, "from './commercial'"),
);
compileModule('sessionSync', '../src/lib/auth/sessionSync.ts', (code) =>
  code.replace(/from '\.\.\/api\/session'/g, "from './session'"),
);
compileModule('sessionSwitchCopy', '../src/lib/i18n/translations/sessionSwitch.ts');

const require = createRequire(import.meta.url);
const draftStorage = require(path.join(directory, 'draftStorage.js'));
const sessionSync = require(path.join(directory, 'sessionSync.js'));
const { sessionSwitchCopy } = require(path.join(directory, 'sessionSwitchCopy.js'));

after(() => {
  for (const name of ['form', 'client', 'commercial', 'session', 'draftStorage', 'sessionSync', 'sessionSwitchCopy']) {
    try { unlinkSync(path.join(directory, `${name}.js`)); } catch { }
  }
  try { rmdirSync(directory); } catch { }
});

function createMemoryStorage() {
  const map = new Map();
  return {
    get length() { return map.size; },
    key(index) { return Array.from(map.keys())[index] ?? null; },
    getItem(key) { return map.has(key) ? map.get(key) : null; },
    setItem(key, value) { map.set(key, String(value)); },
    removeItem(key) { map.delete(key); },
    clear() { map.clear(); },
  };
}

const userA = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'owner@finsight.local',
  fullName: 'Test Owner',
  authStage: 'FULLY_AUTHENTICATED',
  mustChangePassword: false,
  accountStatus: 'ACTIVE',
  platformRole: null,
};

const bizA = {
  businessName: 'Alpha Textiles',
  businessId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  businessType: 'MANUFACTURING',
  languagePreference: 'en',
  role: 'OWNER',
  membershipStatus: 'ACTIVE',
  active: true,
};

const bizB = {
  businessName: 'Beta Trading Co',
  businessId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  businessType: 'TRADING',
  languagePreference: 'en',
  role: 'OWNER',
  membershipStatus: 'ACTIVE',
  active: true,
};

const bizC = {
  businessName: 'Gamma Services',
  businessId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
  businessType: 'SERVICES',
  languagePreference: 'ur',
  role: 'ACCOUNTANT',
  membershipStatus: 'ACTIVE',
  active: false,
};

const sampleDraft = {
  counterpartyName: 'Lahore Cotton Mills',
  documentDate: '2026-11-17',
  dueDate: '2026-12-01',
  notes: 'Unsaved invoice work',
  lines: [
    { id: 'l1', description: 'Cotton Yarn', quantity: '10', unit: 'kg', unitPrice: '1500.00' },
  ],
};

let storage;
beforeEach(() => {
  storage = createMemoryStorage();
});

test('draft storage keys isolate drafts strictly by (userId, businessId, route) with schema version & TTL', () => {
  const now = 1_700_000_000_000;
  const saved = draftStorage.saveCommercialDraft({
    userId: userA.id,
    businessId: bizA.businessId,
    route: '/invoices/new?ignored=1#hash',
    kind: 'INVOICE',
    documentId: null,
    baseServerVersion: null,
    draft: { ...sampleDraft, secretToken: 'must-not-store' },
    now,
  }, storage);
  assert.equal(saved, true);

  // Valid restoration under matching (userId, businessId, route, kind, documentId)
  const restored = draftStorage.loadCommercialDraft({
    userId: userA.id,
    businessId: bizA.businessId,
    route: '/invoices/new',
    kind: 'INVOICE',
    documentId: null,
    now: now + 60_000,
  }, storage);
  assert.ok(restored);
  assert.equal(restored.version, draftStorage.COMMERCIAL_DRAFT_SCHEMA_VERSION);
  assert.equal(restored.draft.counterpartyName, 'Lahore Cotton Mills');
  assert.equal('secretToken' in restored.draft, false, 'Must strip extra/sensitive fields');

  // Cross-business restoration MUST be blocked
  assert.equal(
    draftStorage.loadCommercialDraft({
      userId: userA.id,
      businessId: bizB.businessId,
      route: '/invoices/new',
      kind: 'INVOICE',
      documentId: null,
      now: now + 60_000,
    }, storage),
    null,
  );

  // Cross-user restoration MUST be blocked
  assert.equal(
    draftStorage.loadCommercialDraft({
      userId: '22222222-2222-4222-8222-222222222222',
      businessId: bizA.businessId,
      route: '/invoices/new',
      kind: 'INVOICE',
      documentId: null,
      now: now + 60_000,
    }, storage),
    null,
  );

  // Cross-route / cross-kind restoration MUST be blocked
  assert.equal(
    draftStorage.loadCommercialDraft({
      userId: userA.id,
      businessId: bizA.businessId,
      route: '/bills/new',
      kind: 'BILL',
      documentId: null,
      now: now + 60_000,
    }, storage),
    null,
  );

  // Expiration removes stale draft cleanly
  assert.equal(
    draftStorage.loadCommercialDraft({
      userId: userA.id,
      businessId: bizA.businessId,
      route: '/invoices/new',
      kind: 'INVOICE',
      documentId: null,
      now: now + draftStorage.COMMERCIAL_DRAFT_TTL_MS + 1,
    }, storage),
    null,
  );
  assert.equal(storage.length, 0);
});

test('corrupted/tampered drafts, unavailable storage, and server-version conflicts fail safely', () => {
  const key = draftStorage.commercialDraftStorageKey({
    userId: userA.id,
    businessId: bizA.businessId,
    route: '/invoices/doc-1',
  });
  storage.setItem(key, '{not-valid-json');
  assert.equal(
    draftStorage.loadCommercialDraft({
      userId: userA.id,
      businessId: bizA.businessId,
      route: '/invoices/doc-1',
      kind: 'INVOICE',
      documentId: 'doc-1',
    }, storage),
    null,
  );
  assert.equal(storage.getItem(key), null, 'Corrupt payload should be removed');

  // Tampered envelope businessId inside payload mismatches storage key -> rejected
  storage.setItem(key, JSON.stringify({
    version: 1,
    savedAt: Date.now(),
    userId: userA.id,
    businessId: bizB.businessId,
    route: '/invoices/doc-1',
    kind: 'INVOICE',
    documentId: 'doc-1',
    baseServerVersion: 2,
    draft: sampleDraft,
  }));
  assert.equal(
    draftStorage.loadCommercialDraft({
      userId: userA.id,
      businessId: bizA.businessId,
      route: '/invoices/doc-1',
      kind: 'INVOICE',
      documentId: 'doc-1',
    }, storage),
    null,
  );

  // Existing document stale-overwrite protection: if server version advanced, do not overwrite
  const now = 1_700_000_000_000;
  draftStorage.saveCommercialDraft({
    userId: userA.id,
    businessId: bizA.businessId,
    route: '/bills/bill-1',
    kind: 'BILL',
    documentId: 'bill-1',
    baseServerVersion: 2,
    draft: sampleDraft,
    now,
  }, storage);
  const envelope = draftStorage.loadCommercialDraft({
    userId: userA.id,
    businessId: bizA.businessId,
    route: '/bills/bill-1',
    kind: 'BILL',
    documentId: 'bill-1',
    now,
  }, storage);
  assert.ok(envelope);
  assert.equal(
    draftStorage.canRestoreCommercialDraftIntoDocument(
      envelope,
      { id: 'bill-1', businessId: bizA.businessId, kind: 'BILL', status: 'DRAFT', version: 2 },
      bizA.businessId,
    ),
    true,
  );
  assert.equal(
    draftStorage.canRestoreCommercialDraftIntoDocument(
      envelope,
      { id: 'bill-1', businessId: bizA.businessId, kind: 'BILL', status: 'DRAFT', version: 3 },
      bizA.businessId,
    ),
    false,
    'Advanced server version must block stale draft overwrite',
  );
  assert.equal(
    draftStorage.canRestoreCommercialDraftIntoDocument(
      envelope,
      { id: 'bill-1', businessId: bizA.businessId, kind: 'BILL', status: 'FINALIZED', version: 2 },
      bizA.businessId,
    ),
    false,
    'Finalized document must block draft restoration',
  );

  // Unavailable/throwing storage never throws
  const brokenStorage = {
    get length() { throw new Error('blocked'); },
    key() { throw new Error('blocked'); },
    getItem() { throw new Error('blocked'); },
    setItem() { throw new Error('quota'); },
    removeItem() { throw new Error('blocked'); },
  };
  assert.equal(
    draftStorage.saveCommercialDraft({
      userId: userA.id,
      businessId: bizA.businessId,
      route: '/invoices/new',
      kind: 'INVOICE',
      documentId: null,
      baseServerVersion: null,
      draft: sampleDraft,
    }, brokenStorage),
    false,
  );
  assert.equal(
    draftStorage.loadCommercialDraft({
      userId: userA.id,
      businessId: bizA.businessId,
      route: '/invoices/new',
      kind: 'INVOICE',
      documentId: null,
    }, brokenStorage),
    null,
  );
});

test('clearAllCommercialDrafts purges all drafts cleanly on logout/expiry', () => {
  draftStorage.saveCommercialDraft({
    userId: userA.id,
    businessId: bizA.businessId,
    route: '/invoices/new',
    kind: 'INVOICE',
    documentId: null,
    baseServerVersion: null,
    draft: sampleDraft,
  }, storage);
  draftStorage.saveCommercialDraft({
    userId: userA.id,
    businessId: bizB.businessId,
    route: '/bills/new',
    kind: 'BILL',
    documentId: null,
    baseServerVersion: null,
    draft: sampleDraft,
  }, storage);
  storage.setItem('unrelated_app_key', 'keep-me');

  draftStorage.clearAllCommercialDrafts(storage);
  assert.equal(storage.length, 1);
  assert.equal(storage.getItem('unrelated_app_key'), 'keep-me');
});

test('sessionSync evaluates clean switch, dirty intercept, repeated switches, auto-revert, and auth termination accurately', () => {
  // 1. Clean switch -> COMMIT_CLEAN_SWITCH without modal, records recentSwitch for Scenario E
  const clean = sessionSync.evaluateBoundarySync({
    snapshot: { user: userA, business: bizA },
    serverUser: userA,
    serverBusiness: bizB,
    serverBusinesses: [bizA, bizB, bizC],
    pending: null,
    hasDirtyWork: false,
    currentRoute: '/invoices/123',
    now: 5000,
  });
  assert.equal(clean.outcome, 'COMMIT_CLEAN_SWITCH');
  assert.equal(clean.recentSwitch.previousBusiness.businessId, bizA.businessId);
  assert.equal(clean.recentSwitch.currentBusiness.businessId, bizB.businessId);

  // 2. Dirty switch -> INTERCEPT_DIRTY capturing bizA as previousBusiness
  const dirtyDecision = sessionSync.evaluateBoundarySync({
    snapshot: { user: userA, business: bizA },
    serverUser: userA,
    serverBusiness: bizB,
    serverBusinesses: [bizA, bizB, bizC],
    pending: null,
    hasDirtyWork: true,
    currentRoute: '/invoices/new',
  });
  assert.equal(dirtyDecision.outcome, 'INTERCEPT_DIRTY');
  assert.equal(dirtyDecision.pending.previousBusiness.businessId, bizA.businessId);
  assert.equal(dirtyDecision.pending.currentBusiness.businessId, bizB.businessId);
  assert.equal(dirtyDecision.pending.previousUnavailable, false);

  // 3. Repeated switch (Org B -> Org C while pending modal for Org A) preserves Org A as previousBusiness
  const repeatedC = sessionSync.evaluateBoundarySync({
    snapshot: { user: userA, business: bizA },
    serverUser: userA,
    serverBusiness: bizC,
    serverBusinesses: [bizA, bizB, bizC],
    pending: dirtyDecision.pending,
    hasDirtyWork: false, // Org A was unmounted cleanly while pending!
    currentRoute: '/invoices/new',
  });
  assert.equal(repeatedC.outcome, 'UPDATE_PENDING');
  assert.equal(repeatedC.pending.previousBusiness.businessId, bizA.businessId);
  assert.equal(repeatedC.pending.currentBusiness.businessId, bizC.businessId);

  // 4. Repeated switch back to Org A -> AUTO_REVERT_PENDING
  const revertedA = sessionSync.evaluateBoundarySync({
    snapshot: { user: userA, business: bizA },
    serverUser: userA,
    serverBusiness: bizA,
    serverBusinesses: [bizA, bizB, bizC],
    pending: repeatedC.pending,
    hasDirtyWork: false,
    currentRoute: '/invoices/new',
  });
  assert.equal(revertedA.outcome, 'AUTO_REVERT_PENDING');
  assert.equal(revertedA.business.businessId, bizA.businessId);

  // 5. Previous business revoked/suspended while pending -> flags previousUnavailable
  const revokedBizA = { ...bizA, membershipStatus: 'SUSPENDED' };
  const unavailableUpdate = sessionSync.evaluateBoundarySync({
    snapshot: { user: userA, business: bizA },
    serverUser: userA,
    serverBusiness: bizB,
    serverBusinesses: [revokedBizA, bizB],
    pending: dirtyDecision.pending,
    hasDirtyWork: false,
    currentRoute: '/invoices/new',
  });
  assert.equal(unavailableUpdate.outcome, 'UPDATE_PENDING');
  assert.equal(unavailableUpdate.pending.previousUnavailable, true);
  assert.equal(unavailableUpdate.pending.error, 'unavailable');

  // 6. Auth stage downgrade / user switch takes strict precedence over pending modal
  const terminated = sessionSync.evaluateBoundarySync({
    snapshot: { user: userA, business: bizA },
    serverUser: { ...userA, authStage: 'MFA_CHALLENGE_REQUIRED' },
    serverBusiness: bizB,
    serverBusinesses: [bizA, bizB],
    pending: dirtyDecision.pending,
    hasDirtyWork: true,
    currentRoute: '/invoices/new',
  });
  assert.equal(terminated.outcome, 'TERMINATE_SESSION');

  const userSwitched = sessionSync.evaluateBoundarySync({
    snapshot: { user: userA, business: bizA },
    serverUser: { ...userA, id: '99999999-9999-4999-8999-999999999999' },
    serverBusiness: bizB,
    serverBusinesses: [bizB],
    pending: dirtyDecision.pending,
    hasDirtyWork: true,
    currentRoute: '/invoices/new',
  });
  assert.equal(userSwitched.outcome, 'TERMINATE_SESSION');
});

test('EN and UR session switch translations share exact key symmetry and required recovery strings', () => {
  const en = sessionSwitchCopy('en');
  const ur = sessionSwitchCopy('ur');
  assert.deepEqual(Object.keys(en).sort(), Object.keys(ur).sort());
  assert.deepEqual(Object.keys(en.modal).sort(), Object.keys(ur.modal).sort());
  assert.deepEqual(Object.keys(en.detailChanged).sort(), Object.keys(ur.detailChanged).sort());
  assert.equal(en.modal.title, 'Business changed in another tab');
  assert.equal(en.modal.returnAction, 'Return to previous business');
  assert.equal(en.modal.discardAction, 'Discard changes and continue');
  assert.ok(ur.modal.title.length > 0 && ur.modal.returnAction.length > 0 && ur.modal.discardAction.length > 0);
});

test('IntegrationBoundary and UnsavedBusinessSwitchModal preserve tenant isolation and accessible keyboard guards', () => {
  const boundary = source('../src/components/layout/IntegrationBoundary.tsx');
  const modal = source('../src/components/ui/UnsavedBusinessSwitchModal.tsx');
  const provider = source('../src/components/auth/SessionProvider.tsx');

  // Pending switch MUST unmount stale children before mounting modal
  const pendingIdx = boundary.indexOf('if(session.pendingBusinessSwitch)');
  const treeIdx = boundary.indexOf('<ConfigurationProvider>');
  assert.ok(pendingIdx > 0 && treeIdx > pendingIdx);

  // Syncing lock applies inert to prevent stale interactions
  assert.match(boundary, /inert=\{session\.switchLocked\|\|undefined\}/);

  // Modal uses alertdialog, modal focus trap, non-destructive Escape handler, and RTL dir support
  assert.match(modal, /role="alertdialog"/);
  assert.match(modal, /aria-modal="true"/);
  assert.match(modal, /useModalFocus\(true,\s*\(\)\s*=>\s*setEscapeHint\(true\),\s*rootRef\)/);
  assert.match(modal, /dir=\{direction\}/);

  // SessionProvider sweeps drafts on logout and session expiration
  assert.match(provider, /clearAllCommercialDrafts\(\)/);
  assert.match(provider, /invalidatePrivateRequests\(\)/);
});
