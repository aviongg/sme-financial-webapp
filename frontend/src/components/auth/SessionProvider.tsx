"use client";
import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { apiClient, ApiError, allowPrivateRequests, invalidatePrivateRequests, resetCsrf } from '@/lib/api/client';
import { AuthUser, BusinessSummary, Permission, hasPermission, sameSessionBoundary } from '@/lib/api/session';
import { BusinessProfileRequest } from '@/lib/api/contracts';
import { isDemoMode } from '@/lib/api/config';
import { usePathname } from 'next/navigation';
import { isPublicRoute } from '@/lib/routing/public-routes';
import { useOptionalFormDirty } from '@/context/FormDirtyContext';
import {
  clearAllCommercialDrafts,
  clearCommercialDraft,
  sweepExpiredCommercialDrafts,
} from '@/lib/commercial/draftStorage';
import {
  evaluateBoundarySync,
  type PendingBusinessSwitch,
  type RecentBusinessSwitch,
} from '@/lib/auth/sessionSync';

type Status = 'loading' | 'anonymous' | 'challenge' | 'authenticated' | 'error';

interface State {
  user: AuthUser | null;
  business: BusinessSummary | null;
  businesses: BusinessSummary[];
  status: Status;
  epoch: number;
  error: string;
}

interface Session extends State {
  refresh: () => Promise<void>;
  revalidate: () => Promise<void>;
  login: (email: string, password: string) => Promise<void>;
  acceptUser: (user: AuthUser) => Promise<void>;
  logout: () => Promise<void>;
  switchBusiness: (id: string) => Promise<void>;
  createBusiness: (input: BusinessProfileRequest) => Promise<void>;
  can: (permission: Permission) => boolean;
  pendingBusinessSwitch: PendingBusinessSwitch | null;
  recentBusinessSwitch: RecentBusinessSwitch | null;
  switchLocked: boolean;
  resolveReturnToPreviousBusiness: () => Promise<void>;
  discardPendingBusinessSwitch: () => void;
  clearRecentBusinessSwitch: () => void;
}

const initial: State = { user: null, business: null, businesses: [], status: 'loading', epoch: 0, error: '' };
const Context = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<State>(initial);
  const [pendingBusinessSwitch, setPendingBusinessSwitch] = useState<PendingBusinessSwitch | null>(null);
  const [recentBusinessSwitch, setRecentBusinessSwitch] = useState<RecentBusinessSwitch | null>(null);
  const [switchLocked, setSwitchLocked] = useState(false);

  const formDirty = useOptionalFormDirty();
  const revision = useRef(0);
  const channel = useRef<BroadcastChannel | null>(null);
  const stateRef = useRef(state);
  const lastBoundaryRef = useRef<{ user: AuthUser; business: BusinessSummary | null } | null>(null);
  const pendingRef = useRef(pendingBusinessSwitch);
  const switchLockedRef = useRef(switchLocked);
  const syncInFlightRef = useRef(false);
  const resyncQueuedRef = useRef(false);
  const probed = useRef(false);

  // Exact public routes (the marketing landing page) render no private data, so the initial identity probe is
  // deferred there and runs the first time the visitor is on any other route. Status stays 'loading' meanwhile,
  // which grants nothing: private routes still wait for the probe before IntegrationBoundary renders them.
  const publicRoute = isPublicRoute(usePathname());

  useEffect(() => {
    stateRef.current = state;
    if (state.status === 'authenticated' && state.user) {
      lastBoundaryRef.current = { user: state.user, business: state.business };
    }
  }, [state]);
  useEffect(() => { pendingRef.current = pendingBusinessSwitch; }, [pendingBusinessSwitch]);
  useEffect(() => { switchLockedRef.current = switchLocked; }, [switchLocked]);

  const currentRoute = () => (typeof window !== 'undefined' ? window.location.pathname || '/' : '/');

  const block = useCallback(() => {
    invalidatePrivateRequests();
    const ticket = ++revision.current;
    setState(s => ({ ...initial, epoch: s.epoch + 1 }));
    return ticket;
  }, []);

  const load = useCallback(async (user: AuthUser, ticket: number) => {
    resetCsrf();
    if (!['FULLY_AUTHENTICATED', 'PASSWORD_CHANGE_REQUIRED', 'MFA_ENROLLMENT_REQUIRED', 'MFA_CHALLENGE_REQUIRED'].includes(user.authStage))
      throw new Error('Unsupported authentication stage. Please sign in again.');
    if (user.authStage !== 'FULLY_AUTHENTICATED') {
      if (ticket === revision.current)
        setState(s => ({ ...s, user, status: 'challenge' }));
      return;
    }
    const businesses = await apiClient<BusinessSummary[]>('/businesses');
    let business: BusinessSummary | null = null;
    try {
      business = await apiClient<BusinessSummary>('/businesses/active');
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 404))
        throw e;
    }
    if (ticket !== revision.current)
      return;
    sweepExpiredCommercialDrafts();
    allowPrivateRequests();
    try {
      document.cookie = 'finsight_session_hint=1; path=/; SameSite=Lax';
    } catch { }
    setState(s => ({ ...s, user, business, businesses, status: 'authenticated', error: '' }));
  }, []);

  const refresh = useCallback(async () => {
    setPendingBusinessSwitch(null);
    setSwitchLocked(false);
    const ticket = block();
    try {
      await load(await apiClient<AuthUser>('/auth/me'), ticket);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        lastBoundaryRef.current = null;
        clearAllCommercialDrafts();
        try {
          document.cookie = 'finsight_session_hint=; path=/; max-age=0; SameSite=Lax';
        } catch { }
      }
      if (ticket === revision.current)
        setState(s => ({
          ...s,
          status: e instanceof ApiError && e.status === 401 ? 'anonymous' : 'error',
          error: e instanceof ApiError && e.status === 401 ? '' : e instanceof Error ? e.message : 'Unable to load your session.',
        }));
    }
  }, [block, load]);

  const syncWithServerBoundary = useCallback(async (allowOutsideAuthenticated: boolean) => {
    const snapshotState = stateRef.current;
    const activePending = pendingRef.current;
    const priorBoundary = snapshotState.status === 'authenticated' && snapshotState.user
      ? { user: snapshotState.user, business: snapshotState.business }
      : lastBoundaryRef.current;
    if (!allowOutsideAuthenticated && snapshotState.status !== 'authenticated' && !activePending)
      return;
    if (syncInFlightRef.current) {
      resyncQueuedRef.current = true;
      return;
    }
    syncInFlightRef.current = true;
    const ticket = revision.current;
    try {
      if (!priorBoundary && !activePending) {
        await refresh();
        return;
      }

      const user = await apiClient<AuthUser>('/auth/me');
      let business: BusinessSummary | null = null;
      try {
        business = await apiClient<BusinessSummary>('/businesses/active');
      } catch (e) {
        if (!(e instanceof ApiError && e.status === 404))
          throw e;
      }
      if (ticket !== revision.current)
        return;

      // Fast path on routine window focus when no boundary change or pending lock occurred.
      if (
        snapshotState.status === 'authenticated' &&
        !pendingRef.current &&
        !switchLockedRef.current &&
        sameSessionBoundary(snapshotState.user, user, snapshotState.business, business)
      ) {
        if (
          JSON.stringify(user) !== JSON.stringify(snapshotState.user) ||
          JSON.stringify(business) !== JSON.stringify(snapshotState.business)
        ) {
          const businesses = await apiClient<BusinessSummary[]>('/businesses');
          if (ticket === revision.current)
            setState(s => ({ ...s, user, business, businesses }));
        }
        return;
      }

      const businesses = await apiClient<BusinessSummary[]>('/businesses');
      if (ticket !== revision.current)
        return;

      const decision = evaluateBoundarySync({
        snapshot: priorBoundary ?? { user: null, business: null },
        serverUser: user,
        serverBusiness: business,
        serverBusinesses: businesses,
        pending: pendingRef.current,
        hasDirtyWork: Boolean(formDirty?.hasDirtyWork()),
        currentRoute: currentRoute(),
      });

      switch (decision.outcome) {
        case 'TERMINATE_SESSION':
          lastBoundaryRef.current = null;
          clearAllCommercialDrafts();
          setPendingBusinessSwitch(null);
          setRecentBusinessSwitch(null);
          setSwitchLocked(false);
          await refresh();
          break;

        case 'AUTO_REVERT_PENDING':
          lastBoundaryRef.current = { user, business: decision.business };
          setPendingBusinessSwitch(null);
          setRecentBusinessSwitch(null);
          setSwitchLocked(false);
          resetCsrf();
          allowPrivateRequests();
          setState(s => ({
            ...s,
            user,
            business: decision.business,
            businesses,
            status: 'authenticated',
            epoch: s.epoch + 1,
            error: '',
          }));
          break;

        case 'UPDATE_PENDING':
          invalidatePrivateRequests();
          setSwitchLocked(false);
          setPendingBusinessSwitch(decision.pending);
          break;

        case 'UNCHANGED':
          lastBoundaryRef.current = { user, business };
          setSwitchLocked(false);
          allowPrivateRequests();
          if (decision.metadataChanged || snapshotState.status !== 'authenticated') {
            setState(s => ({ ...s, user, business, businesses, status: 'authenticated', error: '' }));
          }
          break;

        case 'INTERCEPT_DIRTY':
          formDirty?.captureAllDrafts();
          invalidatePrivateRequests();
          revision.current++;
          setSwitchLocked(false);
          setPendingBusinessSwitch(decision.pending);
          setState(s => ({ ...s, epoch: s.epoch + 1 }));
          break;

        case 'COMMIT_CLEAN_SWITCH':
          lastBoundaryRef.current = { user, business };
          setRecentBusinessSwitch(decision.recentSwitch);
          setPendingBusinessSwitch(null);
          setSwitchLocked(false);
          resetCsrf();
          revision.current++;
          allowPrivateRequests();
          setState(s => ({
            ...s,
            user,
            business,
            businesses,
            status: 'authenticated',
            epoch: s.epoch + 1,
            error: '',
          }));
          break;
      }
    } catch (e) {
      if (ticket !== revision.current)
        return;
      const expired =
        (e instanceof ApiError && e.status === 401) ||
        (e instanceof ApiError && e.code === 'pre_auth_required');
      if (expired) {
        lastBoundaryRef.current = null;
        clearAllCommercialDrafts();
        setPendingBusinessSwitch(null);
        setRecentBusinessSwitch(null);
        setSwitchLocked(false);
        block();
        try {
          document.cookie = 'finsight_session_hint=; path=/; max-age=0; SameSite=Lax';
        } catch { }
        setState(s => ({
          ...s,
          status: 'anonymous',
          error: 'Your session expired. Please sign in again.',
        }));
        return;
      }
      if (pendingRef.current) {
        setSwitchLocked(false);
        setPendingBusinessSwitch(prev => (prev ? { ...prev, error: 'network', busy: false } : null));
        return;
      }
      setSwitchLocked(false);
      block();
      setState(s => ({ ...s, status: 'error', error: 'Unable to verify your session. Retry to continue.' }));
    } finally {
      syncInFlightRef.current = false;
      if (resyncQueuedRef.current) {
        resyncQueuedRef.current = false;
        void Promise.resolve().then(() => syncWithServerBoundary(allowOutsideAuthenticated));
      }
    }
  }, [block, formDirty, refresh]);

  const revalidate = useCallback(async () => {
    await syncWithServerBoundary(false);
  }, [syncWithServerBoundary]);

  const acceptUser = useCallback(async (user: AuthUser) => {
    setPendingBusinessSwitch(null);
    setRecentBusinessSwitch(null);
    setSwitchLocked(false);
    const ticket = block();
    try {
      await load(user, ticket);
    } catch (e) {
      if (ticket === revision.current)
        setState(s => ({ ...s, status: 'error', error: e instanceof Error ? e.message : 'Unable to load your business.' }));
      throw e;
    }
  }, [block, load]);

  const announce = useCallback((phase: 'start' | 'done') => channel.current?.postMessage({ phase }), []);

  const login = async (email: string, password: string) => {
    setPendingBusinessSwitch(null);
    setRecentBusinessSwitch(null);
    setSwitchLocked(false);
    const ticket = block();
    announce('start');
    try {
      const user = await apiClient<AuthUser>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
      await load(user, ticket);
    } catch (e) {
      if (ticket === revision.current)
        setState(s => ({ ...s, status: 'anonymous', error: e instanceof Error ? e.message : 'Unable to sign in.' }));
      throw e;
    } finally {
      announce('done');
    }
  };

  const logout = async () => {
    lastBoundaryRef.current = null;
    clearAllCommercialDrafts();
    setPendingBusinessSwitch(null);
    setRecentBusinessSwitch(null);
    setSwitchLocked(false);
    const ticket = block();
    announce('start');
    try {
      await apiClient('/auth/logout', { method: 'POST' });
      if (ticket !== revision.current) return;
      resetCsrf();
      try {
        document.cookie = 'finsight_session_hint=; path=/; max-age=0; SameSite=Lax';
      } catch { }
      setState(s => ({ ...initial, status: 'anonymous', epoch: s.epoch }));
    } catch (e) {
      if (ticket !== revision.current) return;
      setState(s => ({ ...s, status: 'error', error: 'Sign-out could not be confirmed. Retry before leaving this device.' }));
      throw e;
    } finally {
      announce('done');
    }
  };

  const changeBusiness = async (path: string, input: unknown) => {
    const user = state.user;
    if (!user)
      return;
    setPendingBusinessSwitch(null);
    setRecentBusinessSwitch(null);
    setSwitchLocked(false);
    const ticket = block();
    announce('start');
    try {
      await apiClient(path, { method: 'POST', body: JSON.stringify(input) });
      await load(user, ticket);
    } catch (e) {
      if (ticket === revision.current)
        setState(s => ({ ...s, status: 'error', error: e instanceof Error ? e.message : 'Could not switch businesses.' }));
      throw e;
    } finally {
      announce('done');
    }
  };

  const resolveReturnToPreviousBusiness = useCallback(async () => {
    const pending = pendingRef.current;
    if (!pending || pending.busy)
      return;
    setPendingBusinessSwitch(prev => (prev ? { ...prev, busy: true, error: '' } : null));
    const ticket = revision.current;
    announce('start');
    try {
      await apiClient('/businesses/active', {
        method: 'POST',
        body: JSON.stringify({ businessId: pending.previousBusiness.businessId }),
      });
      if (ticket !== revision.current)
        return;

      const user = await apiClient<AuthUser>('/auth/me');
      if (user.authStage !== 'FULLY_AUTHENTICATED' || user.id !== pending.userId) {
        clearAllCommercialDrafts();
        setPendingBusinessSwitch(null);
        await refresh();
        return;
      }
      const businesses = await apiClient<BusinessSummary[]>('/businesses');
      const business = await apiClient<BusinessSummary>('/businesses/active');
      if (ticket !== revision.current)
        return;

      if (business.businessId !== pending.previousBusiness.businessId) {
        setPendingBusinessSwitch(prev => (prev ? { ...prev, currentBusiness: business, businesses, user, busy: false, error: 'network' } : null));
        return;
      }

      resetCsrf();
      allowPrivateRequests();
      setPendingBusinessSwitch(null);
      setRecentBusinessSwitch(null);
      setSwitchLocked(false);
      setState(s => ({
        ...s,
        user,
        business,
        businesses,
        status: 'authenticated',
        epoch: s.epoch + 1,
        error: '',
      }));
    } catch (e) {
      if (ticket !== revision.current)
        return;
      if (e instanceof ApiError && (e.status === 401 || e.code === 'pre_auth_required')) {
        clearAllCommercialDrafts();
        setPendingBusinessSwitch(null);
        setRecentBusinessSwitch(null);
        setSwitchLocked(false);
        block();
        try {
          document.cookie = 'finsight_session_hint=; path=/; max-age=0; SameSite=Lax';
        } catch { }
        setState(s => ({ ...s, status: 'anonymous', error: 'Your session expired. Please sign in again.' }));
        return;
      }
      if (e instanceof ApiError && (e.status === 403 || e.status === 404)) {
        setPendingBusinessSwitch(prev => (prev ? { ...prev, busy: false, previousUnavailable: true, error: 'unavailable' } : null));
        return;
      }
      setPendingBusinessSwitch(prev => (prev ? { ...prev, busy: false, error: 'network' } : null));
    } finally {
      announce('done');
    }
  }, [announce, block, refresh]);

  const discardPendingBusinessSwitch = useCallback(() => {
    const pending = pendingRef.current;
    if (!pending || pending.busy)
      return;
    clearCommercialDraft({
      userId: pending.userId,
      businessId: pending.previousBusiness.businessId,
      route: pending.route,
    });
    formDirty?.discardAllDrafts();
    setPendingBusinessSwitch(null);
    setSwitchLocked(false);
    if (
      pending.currentBusiness &&
      pending.currentBusiness.businessId !== pending.previousBusiness.businessId
    ) {
      setRecentBusinessSwitch({
        previousBusiness: pending.previousBusiness,
        currentBusiness: pending.currentBusiness,
        route: pending.route,
        at: Date.now(),
      });
    } else {
      setRecentBusinessSwitch(null);
    }
    resetCsrf();
    revision.current++;
    allowPrivateRequests();
    setState(s => ({
      ...s,
      user: pending.user,
      business: pending.currentBusiness,
      businesses: pending.businesses,
      status: 'authenticated',
      epoch: s.epoch + 1,
      error: '',
    }));
  }, [formDirty]);

  const clearRecentBusinessSwitch = useCallback(() => {
    setRecentBusinessSwitch(null);
  }, []);

  useEffect(() => {
    if (isDemoMode)
      return;
    // Remove obsolete development selectors; never read identity from browser storage.
    try {
      localStorage.removeItem('finsight_user_id');
      localStorage.removeItem('finsight_pending_user_id');
    } catch { }

    const handleBusinessChangedEvent = () => {
      if (stateRef.current.status === 'authenticated' && stateRef.current.business && formDirty?.hasDirtyWork()) {
        formDirty.captureAllDrafts();
      }
      void syncWithServerBoundary(true);
    };

    const expired = () => {
      lastBoundaryRef.current = null;
      clearAllCommercialDrafts();
      setPendingBusinessSwitch(null);
      setRecentBusinessSwitch(null);
      setSwitchLocked(false);
      block();
      setState(s => ({ ...s, status: 'anonymous', error: 'Your session expired. Please sign in again.' }));
    };

    const focus = () => void revalidate();
    const pageShow = (event: PageTransitionEvent) => {
      if (event.persisted)
        void refresh();
    };

    window.addEventListener('finsight-business-changed', handleBusinessChangedEvent);
    window.addEventListener('finsight-session-expired', expired);
    window.addEventListener('focus', focus);
    window.addEventListener('pageshow', pageShow);

    if (typeof BroadcastChannel !== 'undefined') {
      const bus = new BroadcastChannel('finsight-session');
      channel.current = bus;
      bus.onmessage = (event) => {
        if (event.data?.phase === 'start') {
          invalidatePrivateRequests();
          const current = stateRef.current;
          if (pendingRef.current) {
            setSwitchLocked(true);
          } else if (current.status === 'authenticated' && current.user && current.business && formDirty?.hasDirtyWork()) {
            formDirty.captureAllDrafts();
            setSwitchLocked(true);
          } else {
            block();
          }
        } else if (event.data?.phase === 'done') {
          void syncWithServerBoundary(true);
        }
      };
    }

    return () => {
      revision.current++; probed.current = false;
      invalidatePrivateRequests();
      window.removeEventListener('finsight-business-changed', handleBusinessChangedEvent);
      window.removeEventListener('finsight-session-expired', expired);
      window.removeEventListener('focus', focus);
      window.removeEventListener('pageshow', pageShow);
      channel.current?.close();
      channel.current = null;
    };
  }, [block, formDirty, refresh, revalidate, syncWithServerBoundary]);

  useEffect(() => {
    const hasSessionHint = typeof document !== 'undefined' && document.cookie.includes('finsight_session_hint=1');
    if (isDemoMode || (publicRoute && !hasSessionHint) || probed.current)
      return;
    probed.current = true;
    queueMicrotask(() => void refresh());
  }, [publicRoute, refresh]);

  return (
    <Context.Provider
      value={{
        ...state,
        refresh,
        revalidate,
        login,
        acceptUser,
        logout,
        switchBusiness: id => changeBusiness('/businesses/active', { businessId: id }),
        createBusiness: input => changeBusiness('/businesses', input),
        can: p => hasPermission(state.business, p),
        pendingBusinessSwitch,
        recentBusinessSwitch,
        switchLocked,
        resolveReturnToPreviousBusiness,
        discardPendingBusinessSwitch,
        clearRecentBusinessSwitch,
      }}
    >
      {children}
    </Context.Provider>
  );
}

export function useSession() {
  const session = useContext(Context);
  if (!session)
    throw new Error('SessionProvider is required.');
  return session;
}
