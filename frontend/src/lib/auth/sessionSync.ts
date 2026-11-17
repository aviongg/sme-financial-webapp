import { AuthUser, BusinessSummary, sameSessionBoundary } from '../api/session';

export interface PendingBusinessSwitch {
  userId: string;
  previousBusiness: BusinessSummary;
  currentBusiness: BusinessSummary | null;
  businesses: BusinessSummary[];
  user: AuthUser;
  route: string;
  previousUnavailable: boolean;
  error: '' | 'unavailable' | 'network';
  busy: boolean;
}

export interface RecentBusinessSwitch {
  previousBusiness: BusinessSummary;
  currentBusiness: BusinessSummary;
  route: string;
  at: number;
}

export const RECENT_SWITCH_TTL_MS = 5 * 60 * 1000;

export function isBusinessStillAccessible(businesses: BusinessSummary[], businessId: string): boolean {
  return businesses.some(b => b.businessId === businessId && b.membershipStatus === 'ACTIVE');
}

export function isRecentBusinessSwitchActive(
  recent: RecentBusinessSwitch | null,
  currentBusinessId: string | null | undefined,
  currentRoute: string,
  now: number = Date.now(),
): boolean {
  if (!recent || !currentBusinessId) return false;
  if (recent.currentBusiness.businessId !== currentBusinessId) return false;
  if (recent.route !== currentRoute) return false;
  return now - recent.at >= 0 && now - recent.at <= RECENT_SWITCH_TTL_MS;
}

export interface BoundarySnapshot {
  user: AuthUser | null;
  business: BusinessSummary | null;
}

export type BoundarySyncDecision =
  | { outcome: 'TERMINATE_SESSION' }
  | { outcome: 'AUTO_REVERT_PENDING'; business: BusinessSummary }
  | { outcome: 'UPDATE_PENDING'; pending: PendingBusinessSwitch }
  | { outcome: 'UNCHANGED'; metadataChanged: boolean }
  | { outcome: 'INTERCEPT_DIRTY'; pending: PendingBusinessSwitch }
  | { outcome: 'COMMIT_CLEAN_SWITCH'; recentSwitch: RecentBusinessSwitch | null };

export function evaluateBoundarySync(params: {
  snapshot: BoundarySnapshot;
  serverUser: AuthUser;
  serverBusiness: BusinessSummary | null;
  serverBusinesses: BusinessSummary[];
  pending: PendingBusinessSwitch | null;
  hasDirtyWork: boolean;
  currentRoute: string;
  now?: number;
}): BoundarySyncDecision {
  const { snapshot, serverUser, serverBusiness, serverBusinesses, pending, hasDirtyWork, currentRoute } = params;
  const now = params.now ?? Date.now();

  if (serverUser.authStage !== 'FULLY_AUTHENTICATED') {
    return { outcome: 'TERMINATE_SESSION' };
  }

  if (pending) {
    if (serverUser.id !== pending.userId) {
      return { outcome: 'TERMINATE_SESSION' };
    }
    if (serverBusiness && serverBusiness.businessId === pending.previousBusiness.businessId) {
      return { outcome: 'AUTO_REVERT_PENDING', business: serverBusiness };
    }
    const previousAccessible = isBusinessStillAccessible(serverBusinesses, pending.previousBusiness.businessId);
    return {
      outcome: 'UPDATE_PENDING',
      pending: {
        ...pending,
        user: serverUser,
        currentBusiness: serverBusiness,
        businesses: serverBusinesses,
        previousUnavailable: !previousAccessible,
        error: !previousAccessible && pending.error === '' ? 'unavailable' : pending.error,
        busy: false,
      },
    };
  }

  if (snapshot.user && snapshot.user.id !== serverUser.id) {
    return { outcome: 'TERMINATE_SESSION' };
  }

  if (sameSessionBoundary(snapshot.user, serverUser, snapshot.business, serverBusiness)) {
    const metadataChanged =
      JSON.stringify(serverUser) !== JSON.stringify(snapshot.user) ||
      JSON.stringify(serverBusiness) !== JSON.stringify(snapshot.business);
    return { outcome: 'UNCHANGED', metadataChanged };
  }

  if (hasDirtyWork && snapshot.business) {
    const previousAccessible = isBusinessStillAccessible(serverBusinesses, snapshot.business.businessId);
    return {
      outcome: 'INTERCEPT_DIRTY',
      pending: {
        userId: serverUser.id,
        previousBusiness: snapshot.business,
        currentBusiness: serverBusiness,
        businesses: serverBusinesses,
        user: serverUser,
        route: currentRoute,
        previousUnavailable: !previousAccessible,
        error: previousAccessible ? '' : 'unavailable',
        busy: false,
      },
    };
  }

  const recentSwitch =
    snapshot.business && serverBusiness && snapshot.business.businessId !== serverBusiness.businessId
      ? {
          previousBusiness: snapshot.business,
          currentBusiness: serverBusiness,
          route: currentRoute,
          at: now,
        }
      : null;

  return { outcome: 'COMMIT_CLEAN_SWITCH', recentSwitch };
}
