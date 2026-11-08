"use client";

import React, { useEffect, useState, useCallback } from 'react';
import { useSession } from '@/components/auth/SessionProvider';
import { useLanguage } from '@/lib/i18n/context';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import { Button } from '@/components/ui/Button';
import {
  listSalespersons,
  listAssignments,
  listRules,
  listEligibleCollections,
  listEarnings,
  listPayouts,
  listPendingApprovals,
  getCommissionSummary,
  updateSalespersonStatus,
  updateRuleStatus,
  terminateAssignment,
  recordEarning,
  type Salesperson,
  type SalesAssignment,
  type SalespersonStatus,
  type CommissionRule,
  type CommissionRuleStatus,
  type EligibleCollection,
  type CommissionEarning,
  type CommissionPayout,
  type CommissionSummary,
} from '@/lib/api/dev-a/commission';
import { SalespersonDirectory } from './SalespersonDirectory';
import { CustomerAssignmentsView } from './CustomerAssignmentsView';
import { SalespersonSelfView } from './SalespersonSelfView';
import { CreateSalespersonDialog } from './CreateSalespersonDialog';
import { AssignCustomerDialog } from './AssignCustomerDialog';
import { CommissionSummaryCards } from './CommissionSummaryCards';
import { CommissionRulesView } from './CommissionRulesView';
import { CreateRuleDialog } from './CreateRuleDialog';
import { EligibleCollectionsView } from './EligibleCollectionsView';
import { EarnedCommissionsView } from './EarnedCommissionsView';
import { PendingApprovalsView } from './PendingApprovalsView';
import { AdvancesView } from './AdvancesView';
import { SettlementHistoryView } from './SettlementHistoryView';
import { ApplyAdvanceDialog } from './ApplyAdvanceDialog';
import { PayoutDialog } from './PayoutDialog';
import { CommissionAnalyticsView } from './CommissionAnalyticsView';
import { SalespersonStatementView } from './SalespersonStatementView';
import { CommissionAuditLedgerView } from './CommissionAuditLedgerView';
import {
  UserPlus,
  Users,
  Link2,
  TrendingUp,
  Sparkles,
  SlidersHorizontal,
  Plus,
  CheckCircle2,
  Banknote,
  CreditCard,
  BarChart3,
  FileText,
  History,
} from 'lucide-react';

export function CommissionWorkspace() {
  const session = useSession();
  const { locale } = useLanguage();
  const copy = commissionCopy(locale);

  const role = session.business?.role;
  const isSalesperson = role === 'SALESPERSON';
  const canManage = role === 'OWNER' || role === 'ACCOUNTANT';

  // If active user is a salesperson, render the restricted self-portal
  if (isSalesperson) {
    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
            {copy.tabs.selfPortal}
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            {copy.self.exclusiveCustomersIntro}
          </p>
        </div>
        <SalespersonSelfView locale={locale} />
      </div>
    );
  }

  return (
    <CommissionManagementWorkspace
      canManage={canManage}
      locale={locale}
    />
  );
}

function CommissionManagementWorkspace({
  canManage,
  locale,
}: {
  canManage: boolean;
  locale: string;
}) {
  const copy = commissionCopy(locale);

  const [activeTab, setActiveTab] = useState<
    | 'overview'
    | 'analytics'
    | 'statements'
    | 'audit'
    | 'approvals'
    | 'settlements'
    | 'advances'
    | 'eligible'
    | 'rules'
    | 'salespersons'
    | 'assignments'
  >('overview');

  // Data states
  const [salespersons, setSalespersons] = useState<Salesperson[]>([]);
  const [allAssignments, setAllAssignments] = useState<SalesAssignment[]>([]);
  const [rules, setRules] = useState<CommissionRule[]>([]);
  const [eligibleCollections, setEligibleCollections] = useState<EligibleCollection[]>([]);
  const [earnings, setEarnings] = useState<CommissionEarning[]>([]);
  const [payouts, setPayouts] = useState<CommissionPayout[]>([]);
  const [pendingApprovalsCount, setPendingApprovalsCount] = useState<number>(0);
  const [summary, setSummary] = useState<CommissionSummary | null>(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Dialog states
  const [isCreateSalespersonOpen, setIsCreateSalespersonOpen] = useState(false);
  const [isCreateRuleOpen, setIsCreateRuleOpen] = useState(false);
  const [assigningSalesperson, setAssigningSalesperson] = useState<Salesperson | null>(null);
  const [settlingEarningForAdvance, setSettlingEarningForAdvance] = useState<CommissionEarning | null>(null);
  const [settlingEarningForPayout, setSettlingEarningForPayout] = useState<CommissionEarning | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [persons, rulesData, eligibleData, earningsData, summaryData, pendingData, payoutsData] = await Promise.all([
        listSalespersons().catch(() => [] as Salesperson[]),
        listRules().catch(() => [] as CommissionRule[]),
        listEligibleCollections().catch(() => [] as EligibleCollection[]),
        listEarnings().catch(() => [] as CommissionEarning[]),
        getCommissionSummary().catch(() => null),
        listPendingApprovals().catch(() => [] as CommissionEarning[]),
        listPayouts().catch(() => [] as CommissionPayout[]),
      ]);

      setSalespersons(persons);
      setRules(rulesData);
      setEligibleCollections(eligibleData);
      setEarnings(earningsData);
      setSummary(summaryData);
      setPendingApprovalsCount(pendingData.length);
      setPayouts(payoutsData);

      // Collect all assignments for these salespersons
      const assignmentPromises = persons.map((p) =>
        listAssignments(p.id).catch(() => [] as SalesAssignment[])
      );
      const assignmentArrays = await Promise.all(assignmentPromises);
      setAllAssignments(assignmentArrays.flat());
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : copy.common.error);
    } finally {
      setLoading(false);
    }
  }, [copy.common.error]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    if (activeTab === 'eligible') {
      listEligibleCollections().then(setEligibleCollections).catch(() => {});
    } else if (activeTab === 'overview') {
      Promise.all([
        listEarnings().catch(() => [] as CommissionEarning[]),
        getCommissionSummary().catch(() => null),
      ]).then(([e, s]) => {
        setEarnings(e);
        setSummary(s);
      });
    } else if (activeTab === 'settlements') {
      listPayouts().then(setPayouts).catch(() => {});
    } else if (activeTab === 'approvals') {
      listPendingApprovals().then((p) => setPendingApprovalsCount(p.length)).catch(() => {});
    } else if (activeTab === 'rules') {
      listRules().then(setRules).catch(() => {});
    }
  }, [activeTab]);

  const handleStatusChange = async (person: Salesperson, newStatus: SalespersonStatus) => {
    try {
      const updated = await updateSalespersonStatus(person.id, newStatus);
      setSalespersons((prev) =>
        prev.map((p) => (p.id === updated.id ? updated : p))
      );
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : copy.common.error);
    }
  };

  const handleRuleStatusChange = async (rule: CommissionRule, newStatus: CommissionRuleStatus) => {
    try {
      const updated = await updateRuleStatus(rule.id, newStatus);
      setRules((prev) =>
        prev.map((r) => (r.id === updated.id ? updated : r))
      );
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : copy.common.error);
    }
  };

  const handleRecordEarning = async (col: EligibleCollection) => {
    try {
      await recordEarning({
        sourceType: 'OBLIGATION_ALLOCATION',
        sourceId: col.allocationId,
        salespersonId: col.salespersonId,
        ruleId: col.applicableRuleId ?? col.ruleId,
        saleAmount: col.collectionAmount ?? col.allocatedAmount,
        earningDate: col.collectionDate,
        notes: `Earned on ${col.counterpartyName} payment`,
      });

      // Reload earnings, eligible collections, approvals, and summary
      loadData();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : copy.common.error);
    }
  };

  const handleTerminateAssignment = async (salespersonId: string, assignmentId: string) => {
    try {
      const terminated = await terminateAssignment(salespersonId, assignmentId);
      setAllAssignments((prev) =>
        prev.map((a) => (a.id === terminated.id ? terminated : a))
      );
      // Refresh salesperson list to update active customer counts
      const persons = await listSalespersons();
      setSalespersons(persons);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : copy.common.error);
    }
  };

  const handleSalespersonCreated = (newSalesperson: Salesperson) => {
    setSalespersons((prev) => [...prev, newSalesperson]);
  };

  const handleRuleCreated = (newRule: CommissionRule) => {
    setRules((prev) => [newRule, ...prev]);
    // Refresh eligible collections because new rule might apply
    listEligibleCollections().then(setEligibleCollections).catch(() => {});
  };

  const handleAssigned = (newAssignment: SalesAssignment) => {
    setAllAssignments((prev) => [newAssignment, ...prev]);
    // Refresh salesperson list to update counts
    listSalespersons().then(setSalespersons).catch(() => {});
    // Refresh eligible collections as new assignment might match collections
    listEligibleCollections().then(setEligibleCollections).catch(() => {});
  };

  const handleSettlementActionCompleted = () => {
    loadData();
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
            {copy.title}
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            {copy.intro}
          </p>
        </div>
        {canManage && (
          <div className="flex items-center gap-2">
            <Button variant="secondary" onClick={() => setIsCreateRuleOpen(true)}>
              <Plus className="h-4 w-4 mr-1.5 rtl:ml-1.5" />
              {copy.rules.createTitle}
            </Button>
            <Button variant="primary" onClick={() => setIsCreateSalespersonOpen(true)}>
              <UserPlus className="h-4 w-4 mr-1.5 rtl:ml-1.5" />
              {copy.salesperson.createTitle}
            </Button>
          </div>
        )}
      </div>

      {error && (
        <div
          role="alert"
          className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-400"
        >
          {error}
        </div>
      )}

      {/* Navigation Tabs */}
      <div className="border-b border-slate-200 dark:border-slate-800">
        <nav className="flex space-x-6 rtl:space-x-reverse overflow-x-auto" aria-label="Tabs">
          <button
            type="button"
            onClick={() => setActiveTab('overview')}
            className={`inline-flex items-center gap-2 py-3 px-1 border-b-2 font-medium text-sm whitespace-nowrap transition-colors ${
              activeTab === 'overview'
                ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300 dark:text-slate-400'
            }`}
          >
            <TrendingUp className="h-4 w-4" />
            {copy.tabs.overview}
            <span className="ml-1 rtl:mr-1 px-1.5 py-0.5 rounded-full text-xs bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 font-semibold">
              {earnings.length}
            </span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('approvals')}
            className={`inline-flex items-center gap-2 py-3 px-1 border-b-2 font-medium text-sm whitespace-nowrap transition-colors ${
              activeTab === 'approvals'
                ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300 dark:text-slate-400'
            }`}
          >
            <CheckCircle2 className="h-4 w-4" />
            {copy.tabs.approvals}
            {pendingApprovalsCount > 0 && (
              <span className="ml-1 rtl:mr-1 px-1.5 py-0.5 rounded-full text-xs bg-amber-100 dark:bg-amber-950/50 text-amber-700 dark:text-amber-300 font-semibold">
                {pendingApprovalsCount}
              </span>
            )}
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('settlements')}
            className={`inline-flex items-center gap-2 py-3 px-1 border-b-2 font-medium text-sm whitespace-nowrap transition-colors ${
              activeTab === 'settlements'
                ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300 dark:text-slate-400'
            }`}
          >
            <Banknote className="h-4 w-4" />
            {copy.tabs.settlements}
            <span className="ml-1 rtl:mr-1 px-1.5 py-0.5 rounded-full text-xs bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 font-semibold">
              {payouts.length}
            </span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('advances')}
            className={`inline-flex items-center gap-2 py-3 px-1 border-b-2 font-medium text-sm whitespace-nowrap transition-colors ${
              activeTab === 'advances'
                ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300 dark:text-slate-400'
            }`}
          >
            <CreditCard className="h-4 w-4" />
            {copy.tabs.advances}
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('eligible')}
            className={`inline-flex items-center gap-2 py-3 px-1 border-b-2 font-medium text-sm whitespace-nowrap transition-colors ${
              activeTab === 'eligible'
                ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300 dark:text-slate-400'
            }`}
          >
            <Sparkles className="h-4 w-4" />
            {copy.tabs.eligible}
            <span className="ml-1 rtl:mr-1 px-1.5 py-0.5 rounded-full text-xs bg-indigo-100 dark:bg-indigo-950/50 text-indigo-700 dark:text-indigo-300 font-semibold">
              {eligibleCollections.filter((c) => !c.alreadyEarned).length}
            </span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('rules')}
            className={`inline-flex items-center gap-2 py-3 px-1 border-b-2 font-medium text-sm whitespace-nowrap transition-colors ${
              activeTab === 'rules'
                ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300 dark:text-slate-400'
            }`}
          >
            <SlidersHorizontal className="h-4 w-4" />
            {copy.tabs.rules}
            <span className="ml-1 rtl:mr-1 px-1.5 py-0.5 rounded-full text-xs bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 font-semibold">
              {rules.length}
            </span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('salespersons')}
            className={`inline-flex items-center gap-2 py-3 px-1 border-b-2 font-medium text-sm whitespace-nowrap transition-colors ${
              activeTab === 'salespersons'
                ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300 dark:text-slate-400'
            }`}
          >
            <Users className="h-4 w-4" />
            {copy.tabs.directory}
            <span className="ml-1 rtl:mr-1 px-1.5 py-0.5 rounded-full text-xs bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 font-semibold">
              {salespersons.length}
            </span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('assignments')}
            className={`inline-flex items-center gap-2 py-3 px-1 border-b-2 font-medium text-sm whitespace-nowrap transition-colors ${
              activeTab === 'assignments'
                ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300 dark:text-slate-400'
            }`}
          >
            <Link2 className="h-4 w-4" />
            {copy.tabs.assignments}
            <span className="ml-1 rtl:mr-1 px-1.5 py-0.5 rounded-full text-xs bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 font-semibold">
              {allAssignments.filter((a) => a.status === 'ACTIVE').length}
            </span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('analytics')}
            className={`inline-flex items-center gap-2 py-3 px-1 border-b-2 font-medium text-sm whitespace-nowrap transition-colors ${
              activeTab === 'analytics'
                ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300 dark:text-slate-400'
            }`}
            data-testid="tab-analytics"
          >
            <BarChart3 className="h-4 w-4" />
            {copy.tabs.analytics}
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('statements')}
            className={`inline-flex items-center gap-2 py-3 px-1 border-b-2 font-medium text-sm whitespace-nowrap transition-colors ${
              activeTab === 'statements'
                ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300 dark:text-slate-400'
            }`}
            data-testid="tab-statements"
          >
            <FileText className="h-4 w-4" />
            {copy.tabs.statements}
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('audit')}
            className={`inline-flex items-center gap-2 py-3 px-1 border-b-2 font-medium text-sm whitespace-nowrap transition-colors ${
              activeTab === 'audit'
                ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300 dark:text-slate-400'
            }`}
            data-testid="tab-audit"
          >
            <History className="h-4 w-4" />
            {copy.tabs.audit}
          </button>
        </nav>
      </div>

      {/* Tab Panels */}
      {activeTab === 'overview' && (
        <div className="space-y-6">
          <CommissionSummaryCards summary={summary} loading={loading} locale={locale} />
          <EarnedCommissionsView
            earnings={earnings}
            loading={loading}
            locale={locale}
            onApplyAdvance={(e) => setSettlingEarningForAdvance(e)}
            onPayout={(e) => setSettlingEarningForPayout(e)}
          />
        </div>
      )}

      {activeTab === 'approvals' && (
        <PendingApprovalsView
          locale={locale}
          onApprovalsChanged={handleSettlementActionCompleted}
        />
      )}

      {activeTab === 'settlements' && (
        <SettlementHistoryView
          payouts={payouts}
          loading={loading}
          locale={locale}
          onSettlementReversed={handleSettlementActionCompleted}
        />
      )}

      {activeTab === 'advances' && (
        <AdvancesView
          locale={locale}
          onAdvancesChanged={handleSettlementActionCompleted}
        />
      )}

      {activeTab === 'eligible' && (
        <EligibleCollectionsView
          collections={eligibleCollections}
          loading={loading}
          canManage={canManage}
          onRecordEarning={handleRecordEarning}
          locale={locale}
        />
      )}

      {activeTab === 'rules' && (
        <CommissionRulesView
          rules={rules}
          loading={loading}
          canManage={canManage}
          onCreateRule={() => setIsCreateRuleOpen(true)}
          onStatusChange={handleRuleStatusChange}
          locale={locale}
        />
      )}

      {activeTab === 'salespersons' && (
        <SalespersonDirectory
          salespersons={salespersons}
          loading={loading}
          canManage={canManage}
          onAssignCustomer={(person) => setAssigningSalesperson(person)}
          onStatusChange={handleStatusChange}
          locale={locale}
        />
      )}

      {activeTab === 'assignments' && (
        <CustomerAssignmentsView
          assignments={allAssignments}
          loading={loading}
          canManage={canManage}
          onTerminate={handleTerminateAssignment}
          locale={locale}
        />
      )}

      {activeTab === 'analytics' && (
        <CommissionAnalyticsView
          salespersons={salespersons}
          locale={locale}
        />
      )}

      {activeTab === 'statements' && (
        <SalespersonStatementView
          salespersons={salespersons}
          locale={locale}
        />
      )}

      {activeTab === 'audit' && (
        <CommissionAuditLedgerView
          salespersons={salespersons}
          locale={locale}
        />
      )}

      {/* Dialogs */}
      {canManage && (
        <>
          <CreateSalespersonDialog
            isOpen={isCreateSalespersonOpen}
            onClose={() => setIsCreateSalespersonOpen(false)}
            onSuccess={handleSalespersonCreated}
            locale={locale}
          />
          <CreateRuleDialog
            isOpen={isCreateRuleOpen}
            onClose={() => setIsCreateRuleOpen(false)}
            salespersons={salespersons}
            onSuccess={handleRuleCreated}
            locale={locale}
          />
          <AssignCustomerDialog
            isOpen={!!assigningSalesperson}
            onClose={() => setAssigningSalesperson(null)}
            salesperson={assigningSalesperson}
            onSuccess={handleAssigned}
            locale={locale}
          />
          <ApplyAdvanceDialog
            isOpen={!!settlingEarningForAdvance}
            onClose={() => setSettlingEarningForAdvance(null)}
            earning={settlingEarningForAdvance}
            onApplied={handleSettlementActionCompleted}
            locale={locale}
          />
          <PayoutDialog
            isOpen={!!settlingEarningForPayout}
            onClose={() => setSettlingEarningForPayout(null)}
            earning={settlingEarningForPayout}
            onPaid={handleSettlementActionCompleted}
            locale={locale}
          />
        </>
      )}
    </div>
  );
}
