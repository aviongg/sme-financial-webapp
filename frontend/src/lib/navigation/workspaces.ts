export const financeWorkspaces = [
    ['accounts', '/finance/accounts'], ['transactions', '/finance/transactions'], ['imports', '/records/import'],
    ['contacts', '/records/contacts'], ['receivables', '/records/receivables'], ['payables', '/records/payables'],
    ['advances', '/records?view=advances'], ['calendar', '/records?view=calendar'], ['reconciliation', '/records/reconciliation'],
    ['recurring', '/records/recurring'], ['forecast', '/finance/forecast'], ['funding', '/finance/funding'], ['commissions', '/finance/commissions'], ['fx', '/finance/fx'], ['risks', '/finance/risks'], ['recognition', '/finance/recognition'], ['bridge', '/records/bridge'], ['history', '/records/history'],
] as const;
/** A query action is never active based on pathname alone. Match complete path segments. */
export function routeIsActive(pathname: string, href: string): boolean {
    if (href.includes('?')) return false;
    return pathname === href || (href !== '/' && pathname.startsWith(href + '/'));
}

/** Called with the browser's own path; setup entry routes deliberately return to the dashboard. */
export function businessSelectionReturn(pathname: string, search: string, destination?: string): string {
    return destination ?? (['/onboarding', '/business-setup'].includes(pathname) ? '/' : pathname + search);
}
