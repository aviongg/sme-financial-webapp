/**
 * Routes an anonymous visitor may load without a session or an active business.
 * Exact paths only: nested or similar-looking paths stay behind IntegrationBoundary.
 */
export const PUBLIC_ROUTES: readonly string[] = ['/landing'];

export function isPublicRoute(pathname: string | null | undefined): boolean {
  return typeof pathname === 'string' && PUBLIC_ROUTES.includes(pathname);
}
