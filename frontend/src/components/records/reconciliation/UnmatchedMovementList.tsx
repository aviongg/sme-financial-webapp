"use client";

import { useEffect, useState } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { reconciliationCopyEn, reconciliationCopyUr } from './reconciliation-copy';
import { reconciliationApi, UnmatchedMovement } from '@/lib/api/dev-a/reconciliation';

interface Props {
  accountId: string;
}

export function UnmatchedMovementList({ accountId }: Props) {
  const { locale, direction } = useLanguage();
  const copy = locale === 'ur' ? reconciliationCopyUr : reconciliationCopyEn;

  const [loading, setLoading] = useState<boolean>(true);
  const [movements, setMovements] = useState<UnmatchedMovement[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      try {
        const data = await reconciliationApi.getUnmatchedMovements(accountId);
        if (!cancelled) {
          setMovements(data);
          setError(null);
        }
      } catch (err: unknown) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load movements');
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    load();
    return () => { cancelled = true; };
  }, [accountId]);

  if (loading) {
    return <div className="p-8 text-center text-sm text-gray-500">Loading unmatched movements...</div>;
  }

  if (error) {
    return (
      <div className="p-4 bg-red-50 text-red-700 border border-red-200 rounded-lg text-sm" role="alert">
        {error}
      </div>
    );
  }

  if (movements.length === 0) {
    return (
      <div className="p-8 text-center text-sm text-emerald-800 bg-emerald-50 rounded-xl border border-emerald-200">
        ✓ {copy.emptyUnmatchedLedger}
      </div>
    );
  }

  return (
    <div dir={direction} className="space-y-4">
      <p className="text-xs text-gray-600">
        These are internal FinSight transactions posted to this bank account that have not yet been matched to an imported bank statement row.
      </p>

      {/* Desktop Table */}
      <div className="hidden sm:block border border-gray-200 rounded-xl overflow-hidden bg-white shadow-sm">
        <table className="w-full text-sm text-left border-collapse">
          <thead className="bg-gray-50 text-gray-600 border-b border-gray-200 text-xs uppercase tracking-wider">
            <tr>
              <th className="p-3 font-semibold">{copy.colDate}</th>
              <th className="p-3 font-semibold">Event Type</th>
              <th className="p-3 font-semibold">{copy.colDirection}</th>
              <th className="p-3 font-semibold">{copy.colAmount}</th>
              <th className="p-3 font-semibold">{copy.colDescription}</th>
              <th className="p-3 font-semibold">Counterparty</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {movements.map((m) => (
              <tr key={m.accountMovementId} className="hover:bg-gray-50/80 transition-colors">
                <td className="p-3 whitespace-nowrap font-mono text-xs">{m.effectiveDate}</td>
                <td className="p-3 whitespace-nowrap">
                  <span className="px-2 py-0.5 rounded text-xs font-bold bg-gray-100 text-gray-800">
                    {m.eventType}
                  </span>
                </td>
                <td className="p-3 whitespace-nowrap">
                  <span
                    className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold ${
                      m.movementDirection === 'INCREASE'
                        ? 'bg-emerald-50 text-emerald-800 border border-emerald-200'
                        : 'bg-red-50 text-red-800 border border-red-200'
                    }`}
                  >
                    {m.movementDirection === 'INCREASE' ? 'IN (+)' : 'OUT (-)'}
                  </span>
                </td>
                <td className="p-3 whitespace-nowrap font-mono font-bold">
                  {m.amount.toLocaleString('en-US', { minimumFractionDigits: 2 })} PKR
                </td>
                <td className="p-3 truncate max-w-xs">{m.description || '—'}</td>
                <td className="p-3 whitespace-nowrap text-gray-600">{m.counterpartyName || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Mobile Stacked Cards */}
      <div className="sm:hidden space-y-3">
        {movements.map((m) => (
          <div key={m.accountMovementId} className="p-4 bg-white rounded-xl border border-gray-200 shadow-sm space-y-2">
            <div className="flex justify-between items-center">
              <span className="font-mono text-xs text-gray-500">{m.effectiveDate}</span>
              <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-gray-100 text-gray-800">
                {m.eventType}
              </span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-sm font-semibold truncate max-w-[200px]">{m.description || 'Transaction'}</span>
              <span className="font-mono font-bold text-sm">
                {m.amount.toLocaleString('en-US', { minimumFractionDigits: 2 })} PKR
              </span>
            </div>
            {m.counterpartyName && (
              <p className="text-xs text-gray-500">Party: {m.counterpartyName}</p>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
