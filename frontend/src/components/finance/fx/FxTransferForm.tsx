"use client";
import React, { useState, useEffect, useTransition } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select, type SelectOption } from '@/components/ui/Select';
import { useLanguage } from '@/lib/i18n/context';
import { fxCopy } from '@/lib/i18n/translations/fx';
import {
  type FxTransferPreviewResponse,
  type FxTransferFeePlacement,
  previewFxTransfer,
  postFxTransfer,
} from '@/lib/api/dev-a/fx';
import { accountsApi } from '@/lib/api/dev-a/accounts';
import type { Account } from '@/lib/api/dev-a/account-contracts';

interface FxTransferFormProps {
  onTransferPosted?: () => void;
}

export function FxTransferForm({ onTransferPosted }: FxTransferFormProps) {
  const { locale } = useLanguage();
  const copy = fxCopy(locale);

  const [accounts, setAccounts] = useState<Account[]>([]);
  const [sourceAccountId, setSourceAccountId] = useState('');
  const [destinationAccountId, setDestinationAccountId] = useState('');
  const [sourceAmount, setSourceAmount] = useState('100.00');
  const [exchangeRate, setExchangeRate] = useState('278.50');
  const [feeAmount, setFeeAmount] = useState('150.00');
  const [feePlacement, setFeePlacement] = useState<FxTransferFeePlacement>('DESTINATION_DEDUCTED');
  const [transferDate, setTransferDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [notes, setNotes] = useState('');

  const [preview, setPreview] = useState<FxTransferPreviewResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const loadAccounts = () => {
    accountsApi.list({ status: 'ACTIVE' })
      .then((res) => {
        if (res && res.items && res.items.length > 0) {
          setAccounts(res.items);
          if (!sourceAccountId) {
            const usd = res.items.find((a: Account) => a.currency === 'USD') || res.items[0];
            setSourceAccountId(usd.id);
            const pkr = res.items.find((a: Account) => a.currency === 'PKR' && a.id !== usd.id) ||
              res.items.find((a: Account) => a.id !== usd.id);
            if (pkr) {
              setDestinationAccountId(pkr.id);
            }
          }
        }
      })
      .catch(() => { });
  };

  useEffect(() => {
    loadAccounts();
  }, []);

  const sourceAccount = accounts.find((a) => a.id === sourceAccountId);
  const destinationAccount = accounts.find((a) => a.id === destinationAccountId);

  // Auto trigger preview when key fields change
  useEffect(() => {
    if (!sourceAccountId || !destinationAccountId || !exchangeRate || parseFloat(exchangeRate) <= 0) {
      setPreview(null);
      return;
    }

    if (sourceAccount && destinationAccount && sourceAccount.currency === destinationAccount.currency) {
      setPreview(null);
      return;
    }

    if (!sourceAmount || parseFloat(sourceAmount) <= 0) {
      setPreview(null);
      return;
    }

    let active = true;
    previewFxTransfer({
      sourceAccountId,
      destinationAccountId,
      sourceAmount,
      exchangeRate,
      feeAmount: feeAmount || '0',
      feePlacement,
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
          setError(err instanceof Error ? err.message : copy.calculationFailed);
        }
      });

    return () => {
      active = false;
    };
  }, [sourceAccountId, destinationAccountId, sourceAmount, exchangeRate, feeAmount, feePlacement, sourceAccount, destinationAccount, copy.calculationFailed]);

  const handlePostTransfer = () => {
    setError(null);
    setSuccess(null);

    if (!sourceAccountId || !destinationAccountId) {
      setError(copy.requiredField);
      return;
    }
    if (sourceAccount && destinationAccount && sourceAccount.currency === destinationAccount.currency) {
      setError(copy.differentAccountCurrenciesRequired);
      return;
    }
    if (!sourceAmount || parseFloat(sourceAmount) <= 0) {
      setError(copy.positiveAmount);
      return;
    }
    if (!exchangeRate || parseFloat(exchangeRate) <= 0) {
      setError(copy.positiveAmount);
      return;
    }

    startTransition(async () => {
      try {
        const idempKey = 'fx-xfer-' + crypto.randomUUID();
        await postFxTransfer(
          {
            sourceAccountId,
            destinationAccountId,
            sourceAmount,
            exchangeRate,
            feeAmount: feeAmount || '0',
            feePlacement,
            transferDate,
            notes: notes || undefined,
          },
          idempKey
        );

        setSuccess(copy.transferSuccess);
        loadAccounts();
        if (onTransferPosted) {
          onTransferPosted();
        }
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'Transfer posting failed');
      }
    });
  };

  const loadCaseAPreset = () => {
    const usd = accounts.find((a) => a.currency === 'USD');
    const pkr = accounts.find((a) => a.currency === 'PKR');
    if (usd) setSourceAccountId(usd.id);
    if (pkr) setDestinationAccountId(pkr.id);
    setSourceAmount('100.00');
    setExchangeRate('278.50');
    setFeeAmount('150.00');
    setFeePlacement('DESTINATION_DEDUCTED');
    setNotes('Case A: USD 100 to PKR @ 278.50 with PKR 150 fee deducted');
  };

  const accountOptions: SelectOption[] = accounts.map((a) => {
    const bal = a.balanceStatus === 'AVAILABLE' ? `(${a.currentBalance} ${a.currency})` : `(${a.currency})`;
    return {
      label: `${a.accountName} - ${bal}`,
      value: a.id,
    };
  });

  const feePlacementOptions: SelectOption[] = [
    { label: copy.feePlacementDestDeducted, value: 'DESTINATION_DEDUCTED' },
    { label: copy.feePlacementSourceCharged, value: 'SOURCE_CHARGED' },
    { label: copy.feePlacementNoFee, value: 'NO_FEE' },
  ];

  const sameCurrencyWarning =
    sourceAccount && destinationAccount && sourceAccount.currency === destinationAccount.currency;

  return (
    <Card className="p-6 bg-card text-card-foreground border rounded-xl shadow-sm space-y-6" data-testid="fx-transfer-form-card">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3">
        <div>
          <h2 className="text-xl font-semibold tracking-tight">{copy.transfersTitle}</h2>
          <p className="text-sm text-muted-foreground">{copy.transfersSubtitle}</p>
        </div>
        <Button
          variant="secondary"
          size="sm"
          onClick={loadCaseAPreset}
          data-testid="btn-load-preset-transfer-case-a"
        >
          {copy.caseA}
        </Button>
      </div>

      {error && (
        <div
          className="p-3 text-sm rounded-lg bg-destructive/10 border border-destructive/20 text-destructive"
          role="alert"
          data-testid="fx-transfer-error"
        >
          {error}
        </div>
      )}

      {sameCurrencyWarning && (
        <div
          className="p-3 text-sm rounded-lg bg-amber-500/10 border border-amber-500/20 text-amber-700 dark:text-amber-400"
          role="alert"
          data-testid="fx-same-currency-warning"
        >
          {copy.differentAccountCurrenciesRequired}
        </div>
      )}

      {success && (
        <div
          className="p-3 text-sm rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-600 dark:text-emerald-400"
          role="status"
          data-testid="fx-transfer-success"
        >
          {success}
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Source Account */}
        <div className="space-y-1">
          <Select
            label={copy.sourceAccount}
            options={accountOptions}
            value={sourceAccountId}
            onChange={(e) => setSourceAccountId(e.target.value)}
            data-testid="select-transfer-source-account"
          />
          {sourceAccount && (
            <p className="text-xs text-muted-foreground mt-0.5">
              {copy.balanceLabel}: <span className="font-medium text-foreground">{sourceAccount.balanceStatus === 'AVAILABLE' ? sourceAccount.currentBalance : 'N/A'} {sourceAccount.currency}</span>
            </p>
          )}
        </div>

        {/* Destination Account */}
        <div className="space-y-1">
          <Select
            label={copy.destinationAccount}
            options={accountOptions}
            value={destinationAccountId}
            onChange={(e) => setDestinationAccountId(e.target.value)}
            data-testid="select-transfer-destination-account"
          />
          {destinationAccount && (
            <p className="text-xs text-muted-foreground mt-0.5">
              {copy.balanceLabel}: <span className="font-medium text-foreground">{destinationAccount.balanceStatus === 'AVAILABLE' ? destinationAccount.currentBalance : 'N/A'} {destinationAccount.currency}</span>
            </p>
          )}
        </div>

        {/* Source Amount */}
        <div>
          <Input
            label={`${copy.sourceAmount} (${sourceAccount ? sourceAccount.currency : 'Source'})`}
            type="number"
            step="any"
            value={sourceAmount}
            onChange={(e) => setSourceAmount(e.target.value)}
            placeholder="100.00"
            data-testid="input-transfer-source-amount"
          />
        </div>

        {/* Locked Exchange Rate */}
        <div className="space-y-1">
          <Input
            label={`${copy.lockedExchangeRate} (${sourceAccount?.currency || 'Source'} → ${destinationAccount?.currency || 'Destination'})`}
            type="number"
            step="any"
            value={exchangeRate}
            onChange={(e) => setExchangeRate(e.target.value)}
            placeholder="278.50"
            data-testid="input-transfer-rate"
          />
          <p className="text-xs text-muted-foreground mt-0.5">{copy.rateConventionTransfer}</p>
        </div>

        {/* Bank Fee Amount */}
        <div>
          <Input
            label={`${copy.feeAmount} (${feePlacement === 'SOURCE_CHARGED' ? (sourceAccount?.currency || 'Source') : (destinationAccount?.currency || 'Destination')})`}
            type="number"
            step="any"
            value={feeAmount}
            onChange={(e) => setFeeAmount(e.target.value)}
            placeholder="150.00"
            data-testid="input-transfer-fee"
          />
        </div>

        {/* Fee Placement */}
        <div className="space-y-1">
          <Select
            label={copy.feePlacement}
            options={feePlacementOptions}
            value={feePlacement}
            onChange={(e) => setFeePlacement(e.target.value as FxTransferFeePlacement)}
            data-testid="select-transfer-fee-placement"
          />
          <p className="text-xs text-muted-foreground mt-0.5">{copy.feePlacementHelp}</p>
        </div>

        {/* Transfer Date */}
        <div>
          <Input
            label={copy.dateCol}
            type="date"
            value={transferDate}
            onChange={(e) => setTransferDate(e.target.value)}
            data-testid="input-transfer-date"
          />
        </div>

        {/* Notes */}
        <div>
          <Input
            label={copy.notes}
            type="text"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Optional reference"
            data-testid="input-transfer-notes"
          />
        </div>
      </div>

      {/* Live Preview Breakdown */}
      {preview && (
        <div
          className="p-4 rounded-lg bg-muted/40 border border-border/80 space-y-3"
          data-testid="fx-transfer-preview-card"
        >
          <h3 className="text-sm font-semibold text-foreground">{copy.previewTransferTitle}</h3>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
            <div className="bg-card p-2.5 rounded-md border border-border/60">
              <span className="text-xs text-muted-foreground block">{copy.grossDestinationAmount}</span>
              <span className="font-semibold text-foreground" data-testid="preview-gross-destination">
                {preview.convertedDestinationPrincipal} {preview.destinationCurrency}
              </span>
            </div>
            <div className="bg-card p-2.5 rounded-md border border-border/60">
              <span className="text-xs text-muted-foreground block">{copy.feeAmount}</span>
              <span className="font-semibold text-foreground" data-testid="preview-fee-amount">
                {preview.feeAmount} {preview.feeCurrency}
              </span>
              <span className="text-xs text-muted-foreground block mt-0.5" data-testid="preview-fee-placement">
                ({preview.feePlacement})
              </span>
            </div>
            <div className="bg-card p-2.5 rounded-md border border-border/60">
              <span className="text-xs text-muted-foreground block">{copy.netDestinationAmount}</span>
              <span className="font-bold text-emerald-600 dark:text-emerald-400" data-testid="preview-net-destination">
                +{preview.netDestinationAmount} {preview.destinationCurrency}
              </span>
            </div>
            <div className="bg-card p-2.5 rounded-md border border-border/60">
              <span className="text-xs text-muted-foreground block">{copy.totalSourceDebit}</span>
              <span className="font-bold text-rose-600 dark:text-rose-400" data-testid="preview-total-source">
                -{preview.totalSourceDebit} {preview.sourceCurrency}
              </span>
            </div>
          </div>
          <p className="text-xs text-muted-foreground italic mt-1" data-testid="preview-effective-rate">
            {preview.explanation}
          </p>
        </div>
      )}

      {/* Submit Button */}
      <div className="flex justify-end pt-2">
        <Button
          type="button"
          onClick={handlePostTransfer}
          disabled={isPending || sameCurrencyWarning || !preview}
          data-testid="btn-post-fx-transfer"
        >
          {isPending ? copy.postingTransfer : copy.postTransferBtn}
        </Button>
      </div>
    </Card>
  );
}
