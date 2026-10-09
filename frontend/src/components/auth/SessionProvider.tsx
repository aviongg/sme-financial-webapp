"use client";
import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { apiClient, ApiError, allowPrivateRequests, invalidatePrivateRequests, resetCsrf } from '@/lib/api/client';
import { AuthUser, BusinessSummary, Permission, hasPermission } from '@/lib/api/session';
import { BusinessProfileRequest } from '@/lib/api/contracts';
import { isDemoMode } from '@/lib/api/config';
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
    login: (email: string, password: string) => Promise<void>;
    acceptUser: (user: AuthUser) => Promise<void>;
    logout: () => Promise<void>;
    switchBusiness: (id: string) => Promise<void>;
    createBusiness: (input: BusinessProfileRequest) => Promise<void>;
    can: (permission: Permission) => boolean;
}
const initial: State = { user: null, business: null, businesses: [], status: 'loading', epoch: 0, error: '' };
const Context = createContext<Session | null>(null);
export function SessionProvider({ children }: {
    children: React.ReactNode;
}) {
    const [state, setState] = useState<State>(initial);
    const revision = useRef(0);
    const channel = useRef<BroadcastChannel | null>(null);
    const stateRef = useRef(state);
    useEffect(() => { stateRef.current = state; }, [state]);
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
        }
        catch (e) {
            if (!(e instanceof ApiError && e.status === 404))
                throw e;
        }
        if (ticket !== revision.current)
            return;
        allowPrivateRequests();
        setState(s => ({ ...s, user, business, businesses, status: 'authenticated', error: '' }));
    }, []);
    const refresh = useCallback(async () => {
        const ticket = block();
        try {
            await load(await apiClient<AuthUser>('/auth/me'), ticket);
        }
        catch (e) {
            if (ticket === revision.current)
                setState(s => ({ ...s, status: e instanceof ApiError && e.status === 401 ? 'anonymous' : 'error', error: e instanceof ApiError && e.status === 401 ? '' : e instanceof Error ? e.message : 'Unable to load your session.' }));
        }
    }, [block, load]);
    const checkingFocus = useRef(false);
    // A routine focus check must not unmount a form or discard unsaved inputs.
    // Clear the private tree only if the server context changed or cannot be verified.
    const revalidate = useCallback(async () => {
        const snapshot = stateRef.current;
        if (snapshot.status !== 'authenticated' || checkingFocus.current)
            return;
        checkingFocus.current = true;
        const ticket = revision.current;
        try {
            const user = await apiClient<AuthUser>('/auth/me');
            let business: BusinessSummary | null = null;
            try {
                business = await apiClient<BusinessSummary>('/businesses/active');
            }
            catch (e) {
                if (!(e instanceof ApiError && e.status === 404))
                    throw e;
            }
            if (ticket !== revision.current)
                return;
            if (JSON.stringify(user) !== JSON.stringify(snapshot.user) || JSON.stringify(business) !== JSON.stringify(snapshot.business)) {
                await refresh();
            }
        }
        catch (e) {
            if (ticket !== revision.current)
                return;
            block();
            const expired = e instanceof ApiError && e.status === 401;
            setState(s => ({ ...s, status: expired ? 'anonymous' : 'error', error: expired ? 'Your session expired. Please sign in again.' : 'Unable to verify your session. Retry to continue.' }));
        }
        finally {
            checkingFocus.current = false;
        }
    }, [block, refresh]);
    const acceptUser = useCallback(async (user: AuthUser) => {
        const ticket = block();
        try {
            await load(user, ticket);
        }
        catch (e) {
            if (ticket === revision.current)
                setState(s => ({ ...s, status: 'error', error: e instanceof Error ? e.message : 'Unable to load your business.' }));
            throw e;
        }
    }, [block, load]);
    const announce = (phase: 'start' | 'done') => channel.current?.postMessage({ phase });
    const login = async (email: string, password: string) => {
        const ticket = block();
        announce('start');
        try {
            const user = await apiClient<AuthUser>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
            await load(user, ticket);
        }
        catch (e) {
            if (ticket === revision.current)
                setState(s => ({ ...s, status: 'anonymous', error: e instanceof Error ? e.message : 'Unable to sign in.' }));
            throw e;
        }
        finally {
            announce('done');
        }
    };
    const logout = async () => {
        block();
        announce('start');
        try {
            await apiClient('/auth/logout', { method: 'POST' });
            resetCsrf();
            setState(s => ({ ...initial, status: 'anonymous', epoch: s.epoch }));
        }
        catch (e) {
            setState(s => ({ ...s, status: 'error', error: 'Sign-out could not be confirmed. Retry before leaving this device.' }));
            throw e;
        }
        finally {
            announce('done');
        }
    };
    const changeBusiness = async (path: string, input: unknown) => {
        const user = state.user;
        if (!user)
            return;
        const ticket = block();
        announce('start');
        try {
            await apiClient(path, { method: 'POST', body: JSON.stringify(input) });
            await load(user, ticket);
        }
        catch (e) {
            if (ticket === revision.current)
                setState(s => ({ ...s, status: 'error', error: e instanceof Error ? e.message : 'Could not switch businesses.' }));
            throw e;
        }
        finally {
            announce('done');
        }
    };
    useEffect(() => {
        if (isDemoMode)
            return;
        // Remove obsolete development selectors; never read identity from browser storage.
        try {
            localStorage.removeItem('finsight_user_id');
            localStorage.removeItem('finsight_pending_user_id');
        }
        catch { }
        const initialize = () => void refresh();
        queueMicrotask(initialize);
        const expired = () => { block(); setState(s => ({ ...s, status: 'anonymous', error: 'Your session expired. Please sign in again.' })); };
        const focus = () => void revalidate();
        const pageShow = (event: PageTransitionEvent) => {
            if (event.persisted)
                void refresh();
        };
        window.addEventListener('finsight-business-changed', initialize);
        window.addEventListener('finsight-session-expired', expired);
        window.addEventListener('focus', focus);
        window.addEventListener('pageshow', pageShow);
        if (typeof BroadcastChannel !== 'undefined') {
            const bus = new BroadcastChannel('finsight-session');
            channel.current = bus;
            bus.onmessage = (event) => {
                if (event.data?.phase === 'start')
                    block();
                else if (event.data?.phase === 'done')
                    void refresh();
            };
        }
        return () => { revision.current++; invalidatePrivateRequests(); window.removeEventListener('finsight-business-changed', initialize); window.removeEventListener('finsight-session-expired', expired); window.removeEventListener('focus', focus); window.removeEventListener('pageshow', pageShow); channel.current?.close(); channel.current = null; };
    }, [refresh, block, revalidate]);
    return <Context.Provider value={{ ...state, refresh, login, acceptUser, logout, switchBusiness: id => changeBusiness('/businesses/active', { businessId: id }), createBusiness: input => changeBusiness('/businesses', input), can: p => hasPermission(state.business, p) }}>{children}</Context.Provider>;
}
export function useSession() {
    const session = useContext(Context);
    if (!session)
        throw new Error('SessionProvider is required.');
    return session;
}
