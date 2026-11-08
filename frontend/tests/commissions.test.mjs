import test from 'node:test';
import assert from 'node:assert/strict';

import { commissionEn, commissionUr, commissionCopy } from '../src/lib/i18n/translations/commission.ts';
import { unifiedEn, unifiedUr } from '../src/lib/i18n/translations/unified.ts';
import { financeWorkspaces } from '../src/lib/navigation/workspaces.ts';
import { hasPermission } from '../src/lib/api/session.ts';

test('F10 Commissions Translation Parity: English and Urdu keys match completely', () => {
  const compareKeys = (enObj, urObj, path = '') => {
    const enKeys = Object.keys(enObj).sort();
    const urKeys = Object.keys(urObj).sort();

    assert.deepEqual(
      enKeys,
      urKeys,
      `Keys do not match at path: ${path || 'root'}\nEN: ${enKeys.join(', ')}\nUR: ${urKeys.join(', ')}`
    );

    for (const key of enKeys) {
      const enVal = enObj[key];
      const urVal = urObj[key];
      const curPath = path ? `${path}.${key}` : key;

      if (typeof enVal === 'object' && enVal !== null) {
        assert.equal(typeof urVal, 'object', `Type mismatch at ${curPath}`);
        compareKeys(enVal, urVal, curPath);
      } else {
        assert.equal(typeof urVal, 'string', `Urdu value at ${curPath} must be string`);
        assert.ok(urVal.trim().length > 0, `Urdu value at ${curPath} must not be empty`);
      }
    }
  };

  compareKeys(commissionEn, commissionUr);
});

test('commissionCopy returns correct locale bundle', () => {
  assert.equal(commissionCopy('ur').title, 'سیلز اور کمیشن');
  assert.equal(commissionCopy('en').title, 'Sales & Commissions');
  assert.equal(commissionCopy('fr').title, 'Sales & Commissions'); // fallback to English
});

test('Unified navigation copy includes commissions entry in both languages', () => {
  assert.ok(unifiedEn.commissions, 'unifiedEn must define commissions');
  assert.ok(unifiedUr.commissions, 'unifiedUr must define commissions');
  assert.equal(unifiedEn.commissions, 'Sales & commissions');
  assert.equal(unifiedUr.commissions, 'سیلز اور کمیشن');
});

test('financeWorkspaces includes commissions route', () => {
  const route = financeWorkspaces.find(([id, path]) => id === 'commissions' && path === '/finance/commissions');
  assert.ok(route, 'financeWorkspaces must register [commissions, /finance/commissions]');
});

test('RBAC Session Permissions: SALESPERSON has restricted COMMISSION_READ only', () => {
  const makeBusiness = (role) => ({
    businessId: 'biz-1',
    businessName: 'Test Corp',
    businessType: 'retail',
    languagePreference: 'en',
    role,
    membershipStatus: 'ACTIVE',
    active: true,
  });

  const salespersonBiz = makeBusiness('SALESPERSON');
  assert.equal(hasPermission(salespersonBiz, 'COMMISSION_READ'), true);
  assert.equal(hasPermission(salespersonBiz, 'SALESPERSON_MANAGE'), false);
  assert.equal(hasPermission(salespersonBiz, 'COMMISSION_MANAGE'), false);
  assert.equal(hasPermission(salespersonBiz, 'FINANCIAL_DATA_READ'), false);
  assert.equal(hasPermission(salespersonBiz, 'RECORD_CREATE_UPDATE'), false);
  assert.equal(hasPermission(salespersonBiz, 'DOCUMENT_UPLOAD'), false);

  const ownerBiz = makeBusiness('OWNER');
  assert.equal(hasPermission(ownerBiz, 'SALESPERSON_MANAGE'), true);
  assert.equal(hasPermission(ownerBiz, 'COMMISSION_MANAGE'), true);
  assert.equal(hasPermission(ownerBiz, 'COMMISSION_READ'), true);

  const accountantBiz = makeBusiness('ACCOUNTANT');
  assert.equal(hasPermission(accountantBiz, 'SALESPERSON_MANAGE'), true);
  assert.equal(hasPermission(accountantBiz, 'COMMISSION_MANAGE'), true);
  assert.equal(hasPermission(accountantBiz, 'COMMISSION_READ'), true);

  const managerBiz = makeBusiness('MANAGER');
  assert.equal(hasPermission(managerBiz, 'COMMISSION_READ'), true);
  assert.equal(hasPermission(managerBiz, 'SALESPERSON_MANAGE'), false);

  const viewerBiz = makeBusiness('VIEWER');
  assert.equal(hasPermission(viewerBiz, 'COMMISSION_READ'), false);
  assert.equal(hasPermission(viewerBiz, 'SALESPERSON_MANAGE'), false);
});
