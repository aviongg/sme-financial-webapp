import test from 'node:test';
import assert from 'node:assert/strict';

import { fxEn, fxUr, fxCopy } from '../src/lib/i18n/translations/fx.ts';
import { unifiedEn, unifiedUr } from '../src/lib/i18n/translations/unified.ts';
import { financeWorkspaces } from '../src/lib/navigation/workspaces.ts';

test('F11A/F11B FX Translation Parity: English and Urdu keys match completely', () => {
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

  compareKeys(fxEn, fxUr);
});

test('fxCopy returns correct locale bundle', () => {
  assert.equal(fxCopy('ur').title, 'غیر ملکی زرمبادلہ اور کثیر کرنسیاں');
  assert.equal(fxCopy('en').title, 'Foreign Exchange & Multicurrency');
  assert.equal(fxCopy('fr').title, 'Foreign Exchange & Multicurrency'); // fallback to English
});

test('Unified navigation copy includes fx entry in both languages', () => {
  assert.ok(unifiedEn.fx, 'unifiedEn must define fx');
  assert.ok(unifiedUr.fx, 'unifiedUr must define fx');
  assert.equal(unifiedEn.fx, 'Currency & exchange');
  assert.equal(unifiedUr.fx, 'کرنسی اور زرمبادلہ');
});

test('financeWorkspaces includes fx route', () => {
  const route = financeWorkspaces.find(([id, path]) => id === 'fx' && path === '/finance/fx');
  assert.ok(route, 'financeWorkspaces must register [fx, /finance/fx]');
});

test('Case A Mathematical Verification: Outgoing USD 100 with PKR 150 Fee -> PKR 28,000 debit', () => {
  const principal = 100.0;
  const rate = 278.5;
  const fee = 150.0;

  const grossSettlement = principal * rate;
  assert.equal(grossSettlement, 27850.0);

  const totalDebited = grossSettlement + fee;
  assert.equal(totalDebited, 28000.0);

  const effectiveRate = totalDebited / principal;
  assert.equal(effectiveRate, 280.0);
});

test('Case B Mathematical Verification: Incoming USD 100 with PKR 150 Fee -> PKR 27,700 net receipt', () => {
  const principal = 100.0;
  const rate = 278.5;
  const fee = 150.0;

  const grossSettlement = principal * rate;
  assert.equal(grossSettlement, 27850.0);

  const netReceived = grossSettlement - fee;
  assert.equal(netReceived, 27700.0);

  const effectiveRate = netReceived / principal;
  assert.equal(effectiveRate, 277.0);
});

test('Case C Minor Unit Precision: JPY (0 decimals) and KWD (3 decimals)', () => {
  const jpyVal = Math.round(100.0 * 155.3456);
  assert.equal(jpyVal, 15535);

  const kwdVal = Number((100.0 * 0.307255).toFixed(3));
  assert.equal(kwdVal, 30.726);
});

test('Case D Rate Scenario Variance: USD/PKR 278.50 vs 282.00', () => {
  const baselineGross = 100.0 * 278.5;
  const compGross = 100.0 * 282.0;
  const variance = compGross - baselineGross;

  assert.equal(variance, 350.0);
  const variancePercent = (variance / baselineGross) * 100;
  assert.equal(variancePercent.toFixed(4), '1.2567');
});

test('F11B Partial Cross-Currency Settlement Math: USD 40 and USD 60 distinct locked rates', () => {
  const totalObligationUsd = 100.0;

  // Tranche 1: USD 40 @ 278.50 + 0 fee
  const tranche1Usd = 40.0;
  const rate1 = 278.50;
  const pkrMovement1 = tranche1Usd * rate1;
  assert.equal(pkrMovement1, 11140.0);
  const remainingAfterTranche1 = totalObligationUsd - tranche1Usd;
  assert.equal(remainingAfterTranche1, 60.0);

  // Tranche 2: USD 60 @ 280.00 + 100 PKR fee
  const tranche2Usd = 60.0;
  const rate2 = 280.00;
  const fee2 = 100.0;
  const pkrMovement2 = (tranche2Usd * rate2) + fee2;
  assert.equal(pkrMovement2, 16900.0);
  const remainingAfterTranche2 = remainingAfterTranche1 - tranche2Usd;
  assert.equal(remainingAfterTranche2, 0.0);
});

test('F11C Realized FX Gain Math: Receivable USD 100 @ 275 recognized, settled @ 278.50 with 150 fee', () => {
  const recognitionRate = 275.0;
  const settleRate = 278.5;
  const fee = 150.0;
  const usdAmount = 100.0;

  const bookValue = usdAmount * recognitionRate; // 27,500.0
  const convertedPrincipal = usdAmount * settleRate; // 27,850.0
  const realizedFxDifference = convertedPrincipal - bookValue; // +350.0 GAIN
  const totalAccountMovement = convertedPrincipal - fee; // 27,700.0 CREDITED
  const netEconomicVariance = totalAccountMovement - bookValue; // +200.0

  assert.equal(bookValue, 27500.0);
  assert.equal(convertedPrincipal, 27850.0);
  assert.equal(realizedFxDifference, 350.0);
  assert.equal(totalAccountMovement, 27700.0);
  assert.equal(netEconomicVariance, 200.0);
});

test('F11C Realized FX Loss Math: Payable USD 100 @ 275 recognized, settled @ 278.50 with 150 fee', () => {
  const recognitionRate = 275.0;
  const settleRate = 278.5;
  const fee = 150.0;
  const usdAmount = 100.0;

  const bookValue = usdAmount * recognitionRate; // 27,500.0
  const convertedPrincipal = usdAmount * settleRate; // 27,850.0
  const realizedFxDifference = convertedPrincipal - bookValue; // +350.0 LOSS
  const totalAccountMovement = convertedPrincipal + fee; // 28,000.0 DEBITED
  const netEconomicVariance = totalAccountMovement - bookValue; // +500.0

  assert.equal(bookValue, 27500.0);
  assert.equal(convertedPrincipal, 27850.0);
  assert.equal(realizedFxDifference, 350.0);
  assert.equal(totalAccountMovement, 28000.0);
  assert.equal(netEconomicVariance, 500.0);
});

test('F11C Partial Settlements Residual Allocation Math: USD 40 @ 278.50 then USD 60 @ 280.00', () => {
  const totalBookValue = 100.0 * 275.0; // 27,500.0

  // Installment 1: USD 40 @ 278.50
  const allocBook1 = 40.0 * 275.0; // 11,000.0
  const principal1 = 40.0 * 278.50; // 11,140.0
  const fxLoss1 = principal1 - allocBook1; // 140.0
  const cashDebit1 = principal1 + 50.0; // 11,190.0

  assert.equal(allocBook1, 11000.0);
  assert.equal(fxLoss1, 140.0);
  assert.equal(cashDebit1, 11190.0);

  // Installment 2 (Final): USD 60 @ 280.00
  const allocBook2 = totalBookValue - allocBook1; // 16,500.0 exact residual
  const principal2 = 60.0 * 280.00; // 16,800.0
  const fxLoss2 = principal2 - allocBook2; // 300.0
  const cashDebit2 = principal2 + 100.0; // 16,900.0

  assert.equal(allocBook2, 16500.0);
  assert.equal(fxLoss2, 300.0);
  assert.equal(cashDebit2, 16900.0);

  // Totals verification
  assert.equal(allocBook1 + allocBook2, 27500.0); // Exact book value
  assert.equal(fxLoss1 + fxLoss2, 440.0); // Total realized loss
  assert.equal(cashDebit1 + cashDebit2, 28090.0); // Total cash debited
});

test('F11D Section 7 Mandatory Reporting Fixture: USD 100 rec gain 350, USD 100 pay loss 350, Net FX 0, Fees 300', () => {
  // 1. USD 100 Receivable: recognized @ 275.00, settled @ 278.50 with 150 PKR fee
  const recGross = 100.0 * 278.50; // 27,850.00
  const recBook = 100.0 * 275.00; // 27,500.00
  const recGain = recGross - recBook; // +350.00
  const recFee = 150.00;
  const recCashMovement = recGross - recFee; // 27,700.00 credited

  // 2. USD 100 Payable: recognized @ 275.00, settled @ 278.50 with 150 PKR fee
  const payGross = 100.0 * 278.50; // 27,850.00
  const payBook = 100.0 * 275.00; // 27,500.00
  const payLoss = payGross - payBook; // +350.00 loss
  const payFee = 150.00;
  const payCashMovement = payGross + payFee; // 28,000.00 debited

  // 3. Performance Summary Totals
  const totalConvertedPrincipal = recGross + payGross; // 55,700.00
  const totalRealizedGain = recGain; // 350.00
  const totalRealizedLoss = payLoss; // 350.00
  const netRealizedFx = totalRealizedGain - totalRealizedLoss; // 0.00
  const totalBankFees = recFee + payFee; // 300.00
  const netCashImpact = recCashMovement - payCashMovement; // -300.00

  assert.equal(totalConvertedPrincipal, 55700.00);
  assert.equal(totalRealizedGain, 350.00);
  assert.equal(totalRealizedLoss, 350.00);
  assert.equal(netRealizedFx, 0.00);
  assert.equal(totalBankFees, 300.00);
  assert.equal(netCashImpact, -300.00);
});

test('F11D Native Currency Exposure Separation: Never conflate or sum currencies directly', () => {
  const usdExposure = {
    currency: 'USD',
    receivables: 250.0,
    payables: 100.0,
    netExposure: 250.0 - 100.0, // +150.0 USD
  };

  const jpyExposure = {
    currency: 'JPY',
    receivables: 0,
    payables: 50000,
    netExposure: 0 - 50000, // -50000 JPY
  };

  assert.equal(usdExposure.netExposure, 150.0);
  assert.equal(jpyExposure.netExposure, -50000);
  // Separate currencies cannot be added directly without exchange rate conversions
  assert.notEqual(usdExposure.netExposure + jpyExposure.netExposure, 150.0);
});

test('F11D CSV Sanitization Invariant: Escape spreadsheet formula triggers', () => {
  const escapeCsv = (val) => {
    if (!val) return '';
    let sanitized = String(val);
    if (/^[=+\-@\t\r]/.test(sanitized)) {
      sanitized = "'" + sanitized;
    }
    if (sanitized.includes('"') || sanitized.includes(',') || sanitized.includes('\n')) {
      sanitized = `"${sanitized.replace(/"/g, '""')}"`;
    }
    return sanitized;
  };

  assert.equal(escapeCsv('=cmd|/C calc'), "'=cmd|/C calc");
  assert.equal(escapeCsv('+@SUM(1,2)'), '"\'+@SUM(1,2)"');
  assert.equal(escapeCsv('-123'), "'-123");
  assert.equal(escapeCsv('@danger'), "'@danger");
  assert.equal(escapeCsv('Normal Company Ltd'), 'Normal Company Ltd');
  assert.equal(escapeCsv('Company, LLC'), '"Company, LLC"');
});

test('F11E Internal FX Transfer Math: Case A Destination-Deducted Fee (USD 100 @ 278.50, Fee PKR 150)', () => {
  const sourceAmount = 100.0;
  const rate = 278.50;
  const fee = 150.0;

  const grossDestination = sourceAmount * rate; // 27,850.0
  const netDestination = grossDestination - fee; // 27,700.0
  const totalSourceDebit = sourceAmount; // 100.0
  const effectiveRate = netDestination / sourceAmount; // 277.0

  assert.equal(grossDestination, 27850.0);
  assert.equal(netDestination, 27700.0);
  assert.equal(totalSourceDebit, 100.0);
  assert.equal(effectiveRate, 277.0);
});

test('F11E Internal FX Transfer Math: Fee Placement B Source-Charged Fee (USD 100 @ 278.50, Fee USD 5)', () => {
  const sourcePrincipal = 100.0;
  const rate = 278.50;
  const fee = 5.0; // charged in USD to source account

  const grossDestination = sourcePrincipal * rate; // 27,850.0
  const netDestination = grossDestination; // 27,850.0
  const totalSourceDebit = sourcePrincipal + fee; // 105.0

  assert.equal(grossDestination, 27850.0);
  assert.equal(netDestination, 27850.0);
  assert.equal(totalSourceDebit, 105.0);
});

test('F11E Internal Transfer Reversal Symmetry: Absolute balance restoration without secondary deductions', () => {
  const initialUsd = 1000.0;
  const initialPkr = 50000.0;

  // Case A transfer
  const usdDebit = 100.0;
  const pkrCredit = 27700.0;

  const postTransferUsd = initialUsd - usdDebit; // 900.0
  const postTransferPkr = initialPkr + pkrCredit; // 77,700.0

  assert.equal(postTransferUsd, 900.0);
  assert.equal(postTransferPkr, 77700.0);

  // Compensating reversal: Exactly mirror the primary movements
  const postReversalUsd = postTransferUsd + usdDebit; // 1,000.0
  const postReversalPkr = postTransferPkr - pkrCredit; // 50,000.0

  assert.equal(postReversalUsd, initialUsd);
  assert.equal(postReversalPkr, initialPkr);
});

test('F11E Minor Unit Scale Support: JPY (0 decimals) and KWD (3 decimals) transfers', () => {
  // Transfer to JPY: USD 100 @ 155.35 -> JPY 15,535
  const usdSource = 100.0;
  const jpyRate = 155.35;
  const jpyGross = Math.round(usdSource * jpyRate);
  assert.equal(jpyGross, 15535);

  // Transfer from KWD: KWD 10.125 @ 907.25 -> PKR 9,185.91
  const kwdSource = 10.125;
  const kwdRate = 907.25;
  const pkrGross = Number((kwdSource * kwdRate).toFixed(2));
  assert.equal(pkrGross, 9185.91);
});
