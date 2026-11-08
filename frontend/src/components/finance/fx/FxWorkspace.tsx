"use client";
import { useEffect, useState, useTransition, useCallback } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select, type SelectOption } from '@/components/ui/Select';
import { useLanguage } from '@/lib/i18n/context';
import { fxCopy } from '@/lib/i18n/translations/fx';
import { useSession } from '@/components/auth/SessionProvider';
import {
  type CurrencyInfo,
  type FxDirection,
  type FxPreviewResponse,
  type FxSettlementReconciliationResponse,
  type FxPeriodSummaryResponse,
  type FxAnalyticsOverviewResponse,
  fetchSupportedCurrencies,
  previewFxConversion,
  fxApi,
} from '@/lib/api/dev-a/fx';
import { FxPreviewCard } from './FxPreviewCard';
import { FxScenarioVarianceCard } from './FxScenarioVarianceCard';
import { FxCurrencyGuide } from './FxCurrencyGuide';
import { FxSettlementForm } from './FxSettlementForm';
import { FxSettlementHistoryTable } from './FxSettlementHistoryTable';
import { FxPeriodSummaryCard } from './FxPeriodSummaryCard';
import { FxReconciliationTable } from './FxReconciliationTable';
import { FxCurrencyExposureTable } from './FxCurrencyExposureTable';
import { FxPeriodicTrendCard } from './FxPeriodicTrendCard';
import { FxSettlementReportView } from './FxSettlementReportView';

const DEFAULT_CURRENCIES: CurrencyInfo[] = [
  { code: 'PKR', displayName: 'Pakistani Rupee', symbol: '₨', minorUnit: 2, active: true },
  { code: 'USD', displayName: 'US Dollar', symbol: '$', minorUnit: 2, active: true },
  { code: 'EUR', displayName: 'Euro', symbol: '€', minorUnit: 2, active: true },
  { code: 'GBP', displayName: 'British Pound', symbol: '£', minorUnit: 2, active: true },
  { code: 'AED', displayName: 'UAE Dirham', symbol: 'AED', minorUnit: 2, active: true },
  { code: 'SAR', displayName: 'Saudi Riyal', symbol: 'SAR', minorUnit: 2, active: true },
  { code: 'JPY', displayName: 'Japanese Yen', symbol: '¥', minorUnit: 0, active: true },
  { code: 'KWD', displayName: 'Kuwaiti Dinar', symbol: 'KD', minorUnit: 3, active: true },
  { code: 'BHD', displayName: 'Bahraini Dinar', symbol: 'BD', minorUnit: 3, active: true },
  { code: 'OMR', displayName: 'Omani Rial', symbol: 'OMR', minorUnit: 3, active: true },
];

export function FxWorkspace() {
  const { locale } = useLanguage();
  const copy = fxCopy(locale);
  const session = useSession();
  const isAuthorized = session.status === 'authenticated' && session.can('FINANCIAL_DATA_READ');

  const [activeTab, setActiveTab] = useState<'calculator' | 'settlements' | 'reconciliation' | 'analytics'>('calculator');
  const [settlementRefreshTrigger, setSettlementRefreshTrigger] = useState(0);

  const [reconciliations, setReconciliations] = useState<FxSettlementReconciliationResponse[]>([]);
  const [summary, setSummary] = useState<FxPeriodSummaryResponse | null>(null);
  const [analyticsOverview, setAnalyticsOverview] = useState<FxAnalyticsOverviewResponse | null>(null);
  const [analyticsLoading, setAnalyticsLoading] = useState(false);

  const [currencies, setCurrencies] = useState<CurrencyInfo[]>(DEFAULT_CURRENCIES);
  const [baseCurrency, setBaseCurrency] = useState('USD');
  const [quoteCurrency, setQuoteCurrency] = useState('PKR');
  const [direction, setDirection] = useState<FxDirection>('OUTGOING');
  const [principalAmount, setPrincipalAmount] = useState('100.00');
  const [rate, setRate] = useState('278.50');
  const [fixedFee, setFixedFee] = useState('150.00');
  const [percentageFee, setPercentageFee] = useState('0');
  const [comparisonRate, setComparisonRate] = useState('282.00');

  const [preview, setPreview] = useState<FxPreviewResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  useEffect(() => {
    let mounted = true;
    fetchSupportedCurrencies()
      .then((data) => {
        if (mounted && data.length > 0) setCurrencies(data);
      })
      .catch(() => {
        // Fallback to DEFAULT_CURRENCIES
      });
    return () => {
      mounted = false;
    };
  }, []);

  const loadReconciliationData = () => {
    fxApi.listReconciliations().then(setReconciliations).catch(() => {});
    fxApi.getPeriodSummary().then(setSummary).catch(() => {});
  };

  const loadAnalytics = useCallback(() => {
    setAnalyticsLoading(true);
    fxApi
      .getAnalyticsOverview()
      .then((data) => setAnalyticsOverview(data))
      .catch(() => {})
      .finally(() => setAnalyticsLoading(false));
  }, []);

  useEffect(() => {
    if (activeTab === 'reconciliation') {
      loadReconciliationData();
    } else if (activeTab === 'analytics') {
      loadAnalytics();
    }
  }, [activeTab, settlementRefreshTrigger, loadAnalytics]);

  const handleCalculate = () => {
    setError(null);
    if (!rate || parseFloat(rate) <= 0) {
      setError(copy.positiveAmount);
      return;
    }
    if (!principalAmount || parseFloat(principalAmount) <= 0) {
      setError(copy.positiveAmount);
      return;
    }
    if (baseCurrency === quoteCurrency) {
      setError(copy.differentCurrencies);
      return;
    }

    startTransition(async () => {
      try {
        const res = await previewFxConversion({
          baseCurrency,
          quoteCurrency,
          rate,
          direction,
          principalAmount,
          principalCurrency: baseCurrency,
          fixedFee: fixedFee || '0',
          percentageFeePercent: percentageFee || '0',
          comparisonRate: comparisonRate || undefined,
        });
        setPreview(res);
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : copy.calculationFailed);
      }
    });
  };

  const loadPreset = (preset: 'caseA' | 'caseB' | 'caseC1' | 'caseC2' | 'caseD') => {
    setError(null);
    switch (preset) {
      case 'caseA':
        setBaseCurrency('USD');
        setQuoteCurrency('PKR');
        setDirection('OUTGOING');
        setPrincipalAmount('100.00');
        setRate('278.50');
        setFixedFee('150.00');
        setPercentageFee('0');
        setComparisonRate('');
        break;
      case 'caseB':
        setBaseCurrency('USD');
        setQuoteCurrency('PKR');
        setDirection('INCOMING');
        setPrincipalAmount('100.00');
        setRate('278.50');
        setFixedFee('150.00');
        setPercentageFee('0');
        setComparisonRate('');
        break;
      case 'caseC1':
        setBaseCurrency('USD');
        setQuoteCurrency('JPY');
        setDirection('OUTGOING');
        setPrincipalAmount('100.00');
        setRate('155.3456');
        setFixedFee('0');
        setPercentageFee('0');
        setComparisonRate('');
        break;
      case 'caseC2':
        setBaseCurrency('USD');
        setQuoteCurrency('KWD');
        setDirection('OUTGOING');
        setPrincipalAmount('100.00');
        setRate('0.307255');
        setFixedFee('0');
        setPercentageFee('0');
        setComparisonRate('');
        break;
      case 'caseD':
        setBaseCurrency('USD');
        setQuoteCurrency('PKR');
        setDirection('OUTGOING');
        setPrincipalAmount('100.00');
        setRate('278.50');
        setFixedFee('250.00');
        setPercentageFee('0.5');
        setComparisonRate('282.00');
        break;
    }
  };

  const currencyOptions: SelectOption[] = currencies.map((c) => ({
    label: `${c.code} - ${c.displayName} (${c.symbol})`,
    value: c.code,
  }));

  const directionOptions: SelectOption[] = [
    { label: copy.outgoing, value: 'OUTGOING' },
    { label: copy.incoming, value: 'INCOMING' },
  ];

  if (!isAuthorized) {
    return (
      <div className="p-8 text-center" data-testid="fx-unauthorized">
        <h2 className="text-xl font-semibold text-destructive">Unauthorized Access</h2>
        <p className="text-sm text-muted-foreground mt-2">
          You do not have permission to view foreign exchange intelligence. Role FINANCIAL_DATA_READ is required.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6" data-testid="fx-workspace">
      {/* Workspace Header */}
      <div className="border-b border-border/60 pb-5">
        <h1 className="text-2xl font-bold tracking-tight text-foreground">{copy.title}</h1>
        <p className="text-sm text-muted-foreground mt-1">{copy.subtitle}</p>
        <p className="text-xs text-muted-foreground mt-2 italic" data-testid="fx-disclaimer-notice">
          {copy.disclaimer}
        </p>
      </div>

      {/* Workspace Tabs */}
      <div className="flex border-b border-border space-x-6 rtl:space-x-reverse" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'calculator'}
          onClick={() => setActiveTab('calculator')}
          className={`pb-3 px-1 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'calculator'
              ? 'border-primary text-primary font-semibold'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
          data-testid="tab-fx-calculator"
        >
          {copy.tabConverter}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'settlements'}
          onClick={() => setActiveTab('settlements')}
          className={`pb-3 px-1 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'settlements'
              ? 'border-primary text-primary font-semibold'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
          data-testid="tab-fx-settlements"
        >
          {copy.tabSettlements}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'reconciliation'}
          onClick={() => setActiveTab('reconciliation')}
          className={`pb-3 px-1 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'reconciliation'
              ? 'border-primary text-primary font-semibold'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
          data-testid="tab-fx-reconciliation"
        >
          {copy.tabReconciliation}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'analytics'}
          onClick={() => setActiveTab('analytics')}
          className={`pb-3 px-1 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'analytics'
              ? 'border-primary text-primary font-semibold'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
          data-testid="tab-fx-analytics"
        >
          {copy.tabAnalytics}
        </button>
      </div>

      {activeTab === 'analytics' ? (
        <div className="space-y-6" data-testid="fx-analytics-workspace">
          {analyticsLoading && !analyticsOverview ? (
            <div className="py-12 text-center text-xs text-muted-foreground">
              Loading multicurrency intelligence...
            </div>
          ) : (
            <>
              {analyticsOverview?.performanceSummary && (
                <FxPeriodSummaryCard summary={analyticsOverview.performanceSummary} copy={copy} />
              )}
              <FxCurrencyExposureTable
                exposures={analyticsOverview?.currencyExposures || []}
                copy={copy}
              />
              <FxPeriodicTrendCard
                trends={analyticsOverview?.periodicTrends || []}
                reportingCurrency={analyticsOverview?.reportingCurrency || 'PKR'}
                copy={copy}
              />
              <FxSettlementReportView
                copy={copy}
                currencies={currencies.map((c) => c.code)}
              />
            </>
          )}
        </div>
      ) : activeTab === 'reconciliation' ? (
        <div className="space-y-6">
          {summary && <FxPeriodSummaryCard summary={summary} copy={copy} />}
          <FxReconciliationTable
            reconciliations={reconciliations}
            copy={copy}
            onRefresh={loadReconciliationData}
          />
        </div>
      ) : activeTab === 'settlements' ? (
        <div className="space-y-6">
          <FxSettlementForm onSettlementPosted={() => setSettlementRefreshTrigger((t) => t + 1)} />
          <FxSettlementHistoryTable refreshTrigger={settlementRefreshTrigger} />
        </div>
      ) : (
        <>
          {/* Preset Buttons for Quick Testing */}
          <Card className="p-4 bg-muted/40 border-muted">
            <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-2">
              {copy.loadPreset}
            </span>
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                variant="secondary"
                className="text-xs py-1 px-2.5 h-auto"
                onClick={() => loadPreset('caseA')}
                data-testid="preset-case-a"
              >
                {copy.caseA}
              </Button>
              <Button
                type="button"
                variant="secondary"
                className="text-xs py-1 px-2.5 h-auto"
                onClick={() => loadPreset('caseB')}
                data-testid="preset-case-b"
              >
                {copy.caseB}
              </Button>
              <Button
                type="button"
                variant="secondary"
                className="text-xs py-1 px-2.5 h-auto"
                onClick={() => loadPreset('caseC1')}
                data-testid="preset-case-c1"
              >
                USD → JPY (0 Decimals)
              </Button>
              <Button
                type="button"
                variant="secondary"
                className="text-xs py-1 px-2.5 h-auto"
                onClick={() => loadPreset('caseC2')}
                data-testid="preset-case-c2"
              >
                USD → KWD (3 Decimals)
              </Button>
              <Button
                type="button"
                variant="secondary"
                className="text-xs py-1 px-2.5 h-auto"
                onClick={() => loadPreset('caseD')}
                data-testid="preset-case-d"
              >
                {copy.caseD}
              </Button>
            </div>
          </Card>

          {/* Calculator Configuration Form */}
          <Card className="p-6">
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
              {/* Base Currency */}
              <div>
                <Select
                  label={copy.baseCurrency}
                  options={currencyOptions}
                  value={baseCurrency}
                  onChange={(e) => setBaseCurrency(e.target.value)}
                  data-testid="fx-base-currency-select"
                />
              </div>

              {/* Quote Currency */}
              <div>
                <Select
                  label={copy.quoteCurrency}
                  options={currencyOptions}
                  value={quoteCurrency}
                  onChange={(e) => setQuoteCurrency(e.target.value)}
                  data-testid="fx-quote-currency-select"
                />
              </div>

              {/* Cash Flow Direction */}
              <div>
                <Select
                  label={copy.direction}
                  options={directionOptions}
                  value={direction}
                  onChange={(e) => setDirection(e.target.value as FxDirection)}
                  data-testid="fx-direction-select"
                />
              </div>

              {/* Principal Amount */}
              <div>
                <Input
                  label={`${copy.principalAmount} (${baseCurrency})`}
                  type="number"
                  step="0.01"
                  value={principalAmount}
                  onChange={(e) => setPrincipalAmount(e.target.value)}
                  data-testid="fx-principal-amount-input"
                />
              </div>

              {/* Exchange Rate */}
              <div>
                <Input
                  label={`${copy.exchangeRate} (1 ${baseCurrency} = X ${quoteCurrency})`}
                  type="number"
                  step="0.000001"
                  value={rate}
                  onChange={(e) => setRate(e.target.value)}
                  data-testid="fx-rate-input"
                />
              </div>

              {/* Fixed Bank Fee */}
              <div>
                <Input
                  label={`${copy.fixedFee} (${quoteCurrency})`}
                  type="number"
                  step="0.01"
                  value={fixedFee}
                  onChange={(e) => setFixedFee(e.target.value)}
                  data-testid="fx-fixed-fee-input"
                />
              </div>

              {/* Percentage Fee */}
              <div>
                <Input
                  label={copy.percentageFee}
                  type="number"
                  step="0.01"
                  value={percentageFee}
                  onChange={(e) => setPercentageFee(e.target.value)}
                  data-testid="fx-percent-fee-input"
                />
              </div>

              {/* Hypothetical Comparison Rate */}
              <div>
                <Input
                  label={`${copy.comparisonRate} (Optional)`}
                  type="number"
                  step="0.000001"
                  value={comparisonRate}
                  onChange={(e) => setComparisonRate(e.target.value)}
                  data-testid="fx-comparison-rate-input"
                />
              </div>
            </div>

            {error && (
              <div
                className="mt-4 p-3 rounded-lg bg-destructive/10 border border-destructive/20 text-destructive text-sm"
                role="alert"
                data-testid="fx-error-banner"
              >
                {error}
              </div>
            )}

            <div className="mt-6 flex justify-end">
              <Button
                type="button"
                variant="primary"
                onClick={handleCalculate}
                disabled={isPending}
                data-testid="fx-calculate-btn"
              >
                {isPending ? copy.calculating : copy.calculate}
              </Button>
            </div>
          </Card>

          {/* Calculated Preview Results */}
          {preview && (
            <div className="space-y-6">
              <FxPreviewCard preview={preview} />
              {preview.scenarioVariance && (
                <FxScenarioVarianceCard
                  variance={preview.scenarioVariance}
                  settlementCurrency={preview.settlementCurrency}
                />
              )}
            </div>
          )}

          {/* Central Currency Metadata Precision Guide */}
          <FxCurrencyGuide currencies={currencies} />
        </>
      )}
    </div>
  );
}
