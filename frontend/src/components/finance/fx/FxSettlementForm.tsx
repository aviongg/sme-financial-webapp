"use client";
import React, { useState, useEffect, useTransition } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select, type SelectOption } from '@/components/ui/Select';
import { useLanguage } from '@/lib/i18n/context';
import { fxCopy } from '@/lib/i18n/translations/fx';
import {
  type FxSettlementPreviewResponse,
  type FxSettlementEligibleObligation,
  previewFxSettlement,
  postFxSettlement,
  fetchSettlementEligibleObligations,
} from '@/lib/api/dev-a/fx';
import { accountsApi } from '@/lib/api/dev-a/accounts';
import type { Account } from '@/lib/api/dev-a/account-contracts';

interface FxSettlementFormProps {
  onSettlementPosted?: () => void;
}

export function FxSettlementForm({ onSettlementPosted }: FxSettlementFormProps) {
  const { locale } = useLanguage();
  const copy = fxCopy(locale);

  const [obligations, setObligations] = useState<FxSettlementEligibleObligation[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [selectedObligationId, setSelectedObligationId] = useState('');
  const [selectedAccountId, setSelectedAccountId] = useState('');
  const [settleAmount, setSettleAmount] = useState('100.00');
  const [exchangeRate, setExchangeRate] = useState('278.50');
  const [fixedFee, setFixedFee] = useState('150.00');
  const [percentageFee, setPercentageFee] = useState('0');
  const [settlementDate, setSettlementDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [recognitionRate, setRecognitionRate] = useState('');
  const [notes, setNotes] = useState('');

  const [preview, setPreview] = useState<FxSettlementPreviewResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  // Load available obligations and accounts
  useEffect(() => {
    let mounted = true;
    fetchSettlementEligibleObligations()
      .then((items) => {
        if (mounted && items && items.length > 0) {
          setObligations(items);
          if (!selectedObligationId) {
            setSelectedObligationId(items[0].id);
            setSettleAmount(items[0].outstandingAmount.toString());
          }
        }
      })
      .catch(() => {});

    accountsApi.list({ status: 'ACTIVE' })
      .then((res) => {
        if (mounted && res && res.items && res.items.length > 0) {
          setAccounts(res.items);
          if (!selectedAccountId) {
            // Prefer PKR account if available
            const pkr = res.items.find((a: Account) => a.currency === 'PKR') || res.items[0];
            setSelectedAccountId(pkr.id);
          }
        }
      })
      .catch(() => {});

    return () => {
      mounted = false;
    };
  }, []);

  // Update settle amount when obligation selection changes
  const handleObligationChange = (id: string) => {
    setSelectedObligationId(id);
    const ob = obligations.find((o) => o.id === id);
    if (ob) {
      setSettleAmount(ob.outstandingAmount.toString());
    }
  };

  // Run preview automatically when key parameters change
  useEffect(() => {
    if (!selectedObligationId || !selectedAccountId || !exchangeRate || parseFloat(exchangeRate) <= 0) {
      setPreview(null);
      return;
    }

    let active = true;
    previewFxSettlement({
      obligationId: selectedObligationId,
      accountId: selectedAccountId,
      amountInObligationCurrency: settleAmount || '0',
      exchangeRate,
      fixedFee: fixedFee || '0',
      percentageFeePercent: percentageFee || '0',
    })
      .then((data) => {
        if (active) {
          setPreview(data);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setPreview(null);
          // Only show error if inputs are populated
          if (settleAmount && parseFloat(settleAmount) > 0) {
            setError(err instanceof Error ? err.message : copy.calculationFailed);
          }
        }
      });

    return () => {
      active = false;
    };
  }, [selectedObligationId, selectedAccountId, settleAmount, exchangeRate, fixedFee, percentageFee]);

  const handlePostSettlement = () => {
    setError(null);
    setSuccess(null);

    if (!selectedObligationId) {
      setError(copy.requiredField);
      return;
    }
    if (!selectedAccountId) {
      setError(copy.requiredField);
      return;
    }
    if (!settleAmount || parseFloat(settleAmount) <= 0) {
      setError(copy.positiveAmount);
      return;
    }
    if (!exchangeRate || parseFloat(exchangeRate) <= 0) {
      setError(copy.positiveAmount);
      return;
    }

    startTransition(async () => {
      try {
        const idempKey = 'fx-set-' + crypto.randomUUID();
        await postFxSettlement(
          {
            obligationId: selectedObligationId,
            accountId: selectedAccountId,
            amountInObligationCurrency: settleAmount,
            exchangeRate,
            fixedFee: fixedFee || '0',
            percentageFeePercent: percentageFee || '0',
            settlementDate,
            notes,
            recognitionRate: recognitionRate ? parseFloat(recognitionRate) : undefined,
          },
          idempKey
        );

        setSuccess(copy.settlementSuccess);
        if (onSettlementPosted) {
          onSettlementPosted();
        }

        // Refresh obligations
        fetchSettlementEligibleObligations().then((items) => {
          if (items) setObligations(items);
        });
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'Settlement posting failed');
      }
    });
  };

  const obligationOptions: SelectOption[] = [
    { label: `-- ${copy.selectObligation} --`, value: '' },
    ...obligations.map((o) => ({
      label: `${o.referenceNumber || o.id.slice(0, 8)} (${o.direction}): ${o.outstandingAmount} ${o.currency} remaining`,
      value: o.id,
    })),
  ];

  const accountOptions: SelectOption[] = [
    { label: `-- ${copy.selectAccount} --`, value: '' },
    ...accounts.map((a) => ({
      label: `${a.accountName} (${a.currency}) - Balance: ${a.openingBalance}`,
      value: a.id,
    })),
  ];

  const selectedObligation = obligations.find((o) => o.id === selectedObligationId);

  return (
    <Card className="p-6 bg-card text-card-foreground border rounded-xl shadow-sm space-y-6" data-testid="fx-settlement-form">
      <div>
        <h2 className="text-xl font-semibold tracking-tight">{copy.settlementTitle}</h2>
        <p className="text-sm text-muted-foreground mt-1">{copy.settlementSubtitle}</p>
      </div>

      {error && (
        <div className="p-3 text-sm rounded-lg bg-destructive/10 border border-destructive/20 text-destructive" role="alert" data-testid="fx-settlement-error-alert">
          {error}
        </div>
      )}

      {success && (
        <div className="p-3 text-sm rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-600 dark:text-emerald-400" role="status" data-testid="fx-settlement-success-alert">
          {success}
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <Select
            label={copy.selectObligation}
            options={obligationOptions}
            value={selectedObligationId}
            onChange={(e) => handleObligationChange(e.target.value)}
            data-testid="select-settlement-obligation"
          />
          {selectedObligation && (
            <p className="text-xs text-muted-foreground mt-1">
              {copy.outstanding}: {selectedObligation.outstandingAmount} {selectedObligation.currency} ({selectedObligation.direction})
            </p>
          )}
        </div>

        <div>
          <Select
            label={copy.selectAccount}
            options={accountOptions}
            value={selectedAccountId}
            onChange={(e) => setSelectedAccountId(e.target.value)}
            data-testid="select-settlement-account"
          />
        </div>

        <div>
          <Input
            label={`${copy.settleAmount} (${selectedObligation ? selectedObligation.currency : 'FX'})`}
            type="number"
            step="0.01"
            value={settleAmount}
            onChange={(e) => setSettleAmount(e.target.value)}
            data-testid="input-settle-amount"
          />
        </div>

        <div>
          <Input
            label={copy.lockedRate}
            type="number"
            step="0.000001"
            value={exchangeRate}
            onChange={(e) => setExchangeRate(e.target.value)}
            data-testid="input-settle-rate"
          />
        </div>

        <div>
          <Input
            label={copy.fixedFee}
            type="number"
            step="0.01"
            value={fixedFee}
            onChange={(e) => setFixedFee(e.target.value)}
            data-testid="input-settle-fixed-fee"
          />
        </div>

        <div>
          <Input
            label={copy.percentageFee}
            type="number"
            step="0.01"
            value={percentageFee}
            onChange={(e) => setPercentageFee(e.target.value)}
            data-testid="input-settle-percentage-fee"
          />
        </div>

        <div>
          <Input
            label="Settlement Date"
            type="date"
            value={settlementDate}
            onChange={(e) => setSettlementDate(e.target.value)}
            data-testid="input-settle-date"
          />
        </div>

        <div>
          <Input
            label={`${copy.recognitionRate} (Optional)`}
            type="number"
            step="0.000001"
            value={recognitionRate}
            onChange={(e) => setRecognitionRate(e.target.value)}
            placeholder="e.g. 275.00"
            data-testid="input-settle-recognition-rate"
          />
        </div>

        <div>
          <Input
            label="Notes / Reference"
            type="text"
            placeholder="Cross-currency settlement notes"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            data-testid="input-settle-notes"
          />
        </div>
      </div>

      {/* Real-time Preview Banner */}
      {preview && (
        <div className="p-4 rounded-xl bg-primary/5 border border-primary/20 space-y-3" data-testid="fx-settlement-preview-banner">
          <h3 className="text-sm font-semibold text-primary uppercase tracking-wider">{copy.results}</h3>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
            <div>
              <span className="text-xs text-muted-foreground block">{copy.grossSettlement}</span>
              <span className="font-semibold text-foreground" data-testid="fx-settlement-preview-gross">
                {preview.convertedPrincipalInAccountCurrency.toLocaleString(undefined, { minimumFractionDigits: 2 })}{' '}
                {preview.accountCurrency}
              </span>
            </div>
            <div>
              <span className="text-xs text-muted-foreground block">{copy.totalFees}</span>
              <span className="font-semibold text-foreground" data-testid="fx-settlement-preview-fees">
                {preview.totalFeeAmount.toLocaleString(undefined, { minimumFractionDigits: 2 })}{' '}
                {preview.feeCurrency}
              </span>
            </div>
            <div>
              <span className="text-xs text-muted-foreground block">{copy.totalMovement}</span>
              <span className={`font-bold text-base ${preview.accountMovementType === 'DECREASE' ? 'text-rose-600 dark:text-rose-400' : 'text-emerald-600 dark:text-emerald-400'}`} data-testid="fx-settlement-preview-movement">
                {preview.accountMovementType === 'DECREASE' ? '-' : '+'}{preview.totalAccountMovement.toLocaleString(undefined, { minimumFractionDigits: 2 })}{' '}
                {preview.accountCurrency}
              </span>
            </div>
            <div>
              <span className="text-xs text-muted-foreground block">{copy.effectiveRateDisplay}</span>
              <span className="font-semibold text-foreground" data-testid="fx-settlement-preview-effective-rate">
                {preview.effectiveRate.toFixed(6)}
              </span>
            </div>
          </div>
          <div className="text-xs text-muted-foreground border-t border-primary/10 pt-2 flex justify-between">
            <span>{copy.outstanding} After: {preview.remainingObligationAmountAfterSettlement.toFixed(2)} {preview.obligationCurrency}</span>
            <span className="font-medium text-primary">1 {preview.obligationCurrency} = {preview.exchangeRate} {preview.accountCurrency} (Locked)</span>
          </div>
        </div>
      )}

      <div className="flex justify-end pt-2">
        <Button
          onClick={handlePostSettlement}
          disabled={isPending || !selectedObligationId || !selectedAccountId || !preview}
          className="w-full sm:w-auto"
          data-testid="btn-post-settlement"
        >
          {isPending ? copy.postingSettlement : copy.postSettlementBtn}
        </Button>
      </div>
    </Card>
  );
}
