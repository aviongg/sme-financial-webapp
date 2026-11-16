// Java RiskAssessmentResponse wire DTO, not the frontend display model.
// Fixture: Risk Assessment Response with insights
export const buildRiskAssessmentResponse = (horizonDays = 30, categoryFilter = '', severityFilter = '') => {
  const allInsights = [
    {
      id: 'RISK-R01-DEFICIT-PKR-2026-11-04',
      riskCode: 'R01_PROJECTED_CASH_DEFICIT',
      category: 'LIQUIDITY_FORECAST',
      severity: 'CRITICAL',
      title: 'Projected Cash Shortfall of PKR 45,000.00 by 2026-11-04',
      summary: 'Authoritative daily forecasting projects your cash balance will fall below zero to PKR -45,000.00 on 2026-11-04.',
      whyItMatters: 'Without timely collections or delayed disbursements, the business will face a cash liquidity shortfall.',
      currency: 'PKR',
      affectedAmount: '45000.00',
      affectedPercentage: null,
      timeframe: 'By 2026-11-04',
      startDate: '2026-11-04',
      endDate: '2026-11-04',
      evidence: [
        {
          evidenceType: 'DAILY_FORECAST',
          referenceId: '2026-11-04',
          description: 'Projected balance first turns negative at PKR -45,000.00 on 2026-11-04',
          amount: '-45000.00',
          eventDate: '2026-11-04',
          counterpartyName: null,
          metadata: { projectedBalance: '-45000.00' },
        },
      ],
      recommendation: {
        actionCode: 'EXPEDITE_COLLECTIONS_AND_DEFER_PAYABLES',
        title: 'Review payment schedule and expedite customer collections',
        description: 'Examine upcoming supplier disbursements before 2026-11-04 to arrange extensions.',
        urgency: 'IMMEDIATE',
        targetRoute: '/finance/forecast',
      },
      completeness: 'COMPLETE',
    },
    {
      id: 'RISK-R02-PAYMENT-PRESSURE-7D',
      riskCode: 'R02_UPCOMING_PAYMENT_PRESSURE',
      category: 'UPCOMING_PAYMENTS',
      severity: 'HIGH',
      title: 'Upcoming 7-Day Payment Pressure: PKR 80,000.00 Due',
      summary: 'Supplier obligations of PKR 80,000.00 are due within the next 7 days, against current cash of PKR 30,000.00.',
      whyItMatters: 'A high concentration of payments due in the immediate 7-day window exposes the business to payment default risk.',
      currency: 'PKR',
      affectedAmount: '50000.00',
      affectedPercentage: null,
      timeframe: 'Next 7 days (2026-10-15 to 2026-10-22)',
      startDate: '2026-10-15',
      endDate: '2026-10-22',
      evidence: [
        {
          evidenceType: 'FINANCIAL_OBLIGATION',
          referenceId: 'ob-pay-1',
          description: 'Payable obligation due 2026-10-20 (BILL-101)',
          amount: '80000.00',
          eventDate: '2026-10-20',
          counterpartyName: 'Major Supplier Ltd',
          metadata: { originalAmount: '80000.00' },
        },
      ],
      recommendation: {
        actionCode: 'NEGOTIATE_SUPPLIER_EXTENSION',
        title: 'Prioritize upcoming supplier disbursements and arrange liquidity',
        description: 'Contact suppliers with payments due in the next 7 days to request staggered terms.',
        urgency: 'IMMEDIATE',
        targetRoute: '/records?view=payables',
      },
      completeness: 'COMPLETE',
    },
    {
      id: 'RISK-R04-CONCENTRATION-CP1',
      riskCode: 'R04_CUSTOMER_CONCENTRATION',
      category: 'COUNTERPARTY_CONCENTRATION',
      severity: 'HIGH',
      title: 'High Customer Concentration: Alpha Traders (62.50% of receivables)',
      summary: 'Alpha Traders accounts for PKR 50,000.00 (62.50%) of your total outstanding receivables (PKR 80,000.00).',
      whyItMatters: 'Over-reliance on a single debtor creates severe business vulnerability if that counterparty delays payment.',
      currency: 'PKR',
      affectedAmount: '50000.00',
      affectedPercentage: 62.5,
      timeframe: 'As of 2026-10-15',
      startDate: '2026-10-15',
      endDate: '2026-10-15',
      evidence: [
        {
          evidenceType: 'COUNTERPARTY',
          referenceId: 'cp-alpha',
          description: 'Counterparty Alpha Traders holds PKR 50,000.00 of PKR 80,000.00 total receivables',
          amount: '50000.00',
          eventDate: '2026-10-15',
          counterpartyName: 'Alpha Traders',
          metadata: { concentrationPercentage: 62.5 },
        },
      ],
      recommendation: {
        actionCode: 'DIVERSIFY_RECEIVABLES_EXPOSURE',
        title: 'Mitigate single-customer credit concentration',
        description: 'Consider advance payment milestones or reduced credit windows on major customer contracts.',
        urgency: 'SHORT_TERM',
        targetRoute: '/records?view=contacts',
      },
      completeness: 'COMPLETE',
    },
    {
      id: 'RISK-R07-DATA-COMPLETENESS',
      riskCode: 'R07_DATA_COMPLETENESS',
      category: 'DATA_INTEGRITY',
      severity: 'MEDIUM',
      title: 'Data Completeness Risk: 2 Unscheduled Obligations, 1 Pending Drafts',
      summary: 'Authoritative risk evaluation is constrained by missing data: 2 open obligations lack due dates, and 1 drafts remain unreviewed.',
      whyItMatters: 'Without complete obligation schedules, risk models may underestimate exposure. Missing data is not low risk.',
      currency: 'PKR',
      affectedAmount: null,
      affectedPercentage: null,
      timeframe: 'Data hygiene status',
      startDate: null,
      endDate: null,
      evidence: [
        {
          evidenceType: 'FINANCIAL_OBLIGATION',
          referenceId: 'ob-unsched-1',
          description: 'Open obligation without due date: BILL-UNSCHED',
          amount: '12000.00',
          eventDate: '2026-10-10',
          counterpartyName: null,
          metadata: { missingDueDate: true },
        },
      ],
      recommendation: {
        actionCode: 'COMPLETE_FINANCIAL_RECORDS',
        title: 'Assign due dates and complete pending draft reviews',
        description: 'Review unconfirmed drafts in Review Drafts and set maturity dates on unscheduled invoices.',
        urgency: 'SHORT_TERM',
        targetRoute: '/records?view=review',
      },
      completeness: 'PARTIAL',
    },
  ];

  let filtered = allInsights;
  if (categoryFilter) {
    filtered = filtered.filter((i) => i.category === categoryFilter);
  }
  if (severityFilter) {
    filtered = filtered.filter((i) => i.severity === severityFilter);
  }

  const categories = [
    { category: 'LIQUIDITY_FORECAST', categoryName: 'Projected Cash Deficit', status: 'ASSESSED', insightCount: 1, highestSeverity: 'CRITICAL', reason: null },
    { category: 'UPCOMING_PAYMENTS', categoryName: 'Upcoming Payment Pressure', status: 'ASSESSED', insightCount: 1, highestSeverity: 'HIGH', reason: null },
    { category: 'RECEIVABLES_COLLECTION', categoryName: 'Overdue Receivables', status: 'ASSESSED', insightCount: 0, highestSeverity: 'LOW', reason: 'No overdue receivables detected.' },
    { category: 'COUNTERPARTY_CONCENTRATION', categoryName: 'Customer Concentration', status: 'ASSESSED', insightCount: 1, highestSeverity: 'HIGH', reason: null },
    { category: 'RECURRING_OPERATIONS', categoryName: 'Recurring Operating Cash Pressure', status: 'ASSESSED', insightCount: 0, highestSeverity: 'LOW', reason: 'Recurring expenses comfortably covered.' },
    { category: 'CASH_POSITION', categoryName: 'Negative Actual Cash Position', status: 'ASSESSED', insightCount: 0, highestSeverity: 'LOW', reason: 'All account balances are positive.' },
    { category: 'DATA_INTEGRITY', categoryName: 'Financial Data Completeness', status: 'DATA_LIMITED', insightCount: 1, highestSeverity: 'MEDIUM', reason: 'Constrained by unscheduled obligations.' },
    { category: 'BANK_RECONCILIATION', categoryName: 'Bank Reconciliation Coverage', status: 'NOT_ASSESSED', insightCount: 0, highestSeverity: 'NOT_ASSESSED', reason: 'No active bank accounts registered.' },
  ];

  return {
    version: '1.0',
    businessId: '11111111-1111-4111-8111-111111111111',
    asOfDate: '2026-10-15',
    horizonDays,
    currency: 'PKR',
    summary: {
      criticalCount: allInsights.filter((i) => i.severity === 'CRITICAL').length,
      highCount: allInsights.filter((i) => i.severity === 'HIGH').length,
      mediumCount: allInsights.filter((i) => i.severity === 'MEDIUM').length,
      lowCount: 0,
      notAssessedCount: 1,
      totalAssessedCategories: 7,
      totalCategories: 8,
      asOfDate: '2026-10-15',
      horizonDays,
      currency: 'PKR',
      highestSeverity: 'CRITICAL',
      hasDataCompletenessIssues: true,
    },
    categories,
    insights: filtered,
    evaluatedAt: '2026-10-15T12:00:00Z',
  };
};
