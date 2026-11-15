// Synthetic transport state, not a projector or substitute for PostgreSQL acceptance.
// The amounts are fixed observations: saving financing must never rewrite them.
export function monthlyFixture(records) {
  const financing = new Map([...records.keys()].map(id => [id, []]));
  return {
    financing,
    handle({ url, method, payload, businessId, canWrite }) {
      const months = records.get(businessId);
      if (url === '/api/records/monthly' && method === 'POST') {
        return { status: canWrite ? 409 : 403, data: { error: canWrite ? 'manual_monthly_write_retired' : 'access_denied' } };
      }
      if (url === '/api/records/monthly/projection-status') return { data: {
        projectionVersion: 'monthly-projection-v1', dirty: false, stale: false,
        projectedThrough: months.at(-1)?.month ?? null, currentMonth: '2026-11',
        lastProjectedAt: '2026-11-16T00:00:00Z', lastError: null,
        principle: 'Monthly financial health is derived automatically from authoritative daily activity.',
      } };
      if (url === '/api/records/monthly/financing' && method === 'GET') return { data: financing.get(businessId) };
      const match = /^\/api\/records\/monthly\/financing\/(\d{4}-(?:0[1-9]|1[0-2]))$/.exec(url);
      if (match && method === 'PUT') {
        if (!canWrite) return { status: 403, data: { error: 'access_denied' } };
        const rows = financing.get(businessId), previous = rows.find(row => row.month === match[1]);
        if ((previous ? previous.version : null) !== payload.expectedVersion) return { status: 409, data: { error: 'stale_financing_input' } };
        const row = { month: match[1], loanOutstanding: payload.loanOutstanding, interestExpense: payload.interestExpense,
          financingType: payload.financingType, origin: 'USER', version: previous ? previous.version + 1 : 0, updatedAt: '2026-11-16T00:00:00Z' };
        financing.set(businessId, [...rows.filter(item => item.month !== row.month), row]);
        return { data: row };
      }
      if (url === '/api/records/monthly/2026-09/sources' && method === 'GET' && months.length) return { data: {
        month: '2026-09', projectionVersion: 'monthly-projection-v1', periodComplete: true,
        fields: [
          { field: 'cashInflow', value: '1000.00', basis: 'CASH', source: 'Synthetic posted transactions' },
          { field: 'cashOutflow', value: '400.00', basis: 'CASH', source: 'Synthetic posted transactions' },
          { field: 'revenue', value: '1500.00', basis: 'ACCRUAL', source: 'Synthetic P&L observation' },
          { field: 'cogs', value: null, basis: 'ACCRUAL', source: 'No cost observation in fixture' },
        ], financingType: 'none', notes: [], storedSource: 'DERIVED', storedMatchesDerivation: true,
        profitAndLoss: { netProfit: '1200.00', basis: 'pnl-accrual-v1' },
      } };
      return null;
    },
  };
}
