/** Cookie-only, same-origin transport. Mutations are never automatically replayed. */
export class ApiError extends Error {
    constructor(public status: number, message: string, public fieldErrors?: Record<string, string>, public code?: string, public retryAfter?: string | null) {
        super(message);
        this.name = "ApiError";
    }
}
type Csrf = {
    token: string;
    headerName: string;
    parameterName: string;
};
let csrf: Promise<Csrf> | null = null;
let generation = 0;
let blocked = true;
const pending = new Set<AbortController>();
export function resetCsrf() { csrf = null; }
export function invalidatePrivateRequests() {
    generation++;
    blocked = true;
    for (const controller of pending)
        controller.abort();
    pending.clear();
    resetCsrf();
}
export function allowPrivateRequests() { blocked = false; }
const isSessionEndpoint = (path: string) => path.startsWith('/auth/') || path === '/businesses' || path === '/businesses/active';
async function decode<T>(response: Response): Promise<T> {
    if (response.status === 204)
        return undefined as T;
    const payload: unknown = await response.json().catch(() => null);
    if (!response.ok) {
        const body = payload && typeof payload === 'object' ? payload as Record<string, unknown> : {};
        const fields = body.errors ?? body.fieldErrors;
        const fieldErrors = fields && typeof fields === 'object' && !Array.isArray(fields)
            ? Object.fromEntries(Object.entries(fields).filter((entry): entry is [
                string,
                string
            ] => typeof entry[1] === 'string')) : undefined;
        throw new ApiError(response.status, typeof body.message === 'string' ? body.message :
            response.status === 429 ? 'Too many attempts. Please wait before trying again.' : 'The request could not be completed.', fieldErrors, typeof body.error === 'string' ? body.error : undefined, response.headers.get('Retry-After'));
    }
    if (payload === null)
        throw new ApiError(response.status, 'The service returned an invalid response.');
    return payload as T;
}
async function csrfToken(): Promise<Csrf> {
    const epoch = generation;
    if (!csrf)
        csrf = fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'application/json' } })
            .then(decode<Csrf>).then(value => {
            if (!value.token || value.headerName !== 'X-XSRF-TOKEN')
                throw new ApiError(0, 'Unable to establish request protection.');
            return value;
        }).catch(error => { if (epoch === generation)
            csrf = null; throw error; });
    return csrf;
}
export async function apiClient<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
    if (!/^\/(?!\/)/.test(endpoint) || endpoint.includes('\\') || endpoint.includes('://') || endpoint.includes('#'))
        throw new Error('A same-origin API path is required.');
    const privateRequest = !isSessionEndpoint(endpoint);
    if (privateRequest && blocked)
        throw new DOMException('Session context is changing.', 'AbortError');
    const current = generation;
    const controller = new AbortController();
    pending.add(controller);
    const abort = () => controller.abort();
    options.signal?.addEventListener('abort', abort, { once: true });
    if (options.signal?.aborted)
        controller.abort();
    try {
        const method = (options.method || 'GET').toUpperCase();
        const headers = new Headers(options.headers);
        headers.set('Accept', 'application/json');
        if (options.body != null && !(options.body instanceof FormData))
            headers.set('Content-Type', 'application/json');
        if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && !endpoint.startsWith('/auth/password-reset/')) {
            const token = await csrfToken();
            headers.set(token.headerName, token.token);
        }
        if (current !== generation || controller.signal.aborted)
            throw new DOMException('Request context changed.', 'AbortError');
        const response = await fetch(`/api${endpoint}`, { ...options, method, headers, credentials: 'same-origin', cache: 'no-store', signal: controller.signal, redirect: 'error' });
        const result = await decode<T>(response);
        if (current !== generation || controller.signal.aborted)
            throw new DOMException('Request context changed.', 'AbortError');
        return result;
    }
    catch (error) {
        if (current !== generation || controller.signal.aborted)
            throw new DOMException('Request context changed.', 'AbortError');
        if (error instanceof ApiError) {
            if (error.status === 403)
                resetCsrf();
            if (error.code === 'active_business_required') {
                invalidatePrivateRequests();
                if (typeof window !== 'undefined')
                    window.dispatchEvent(new Event('finsight-business-changed'));
            }
            if ((error.status === 401 && endpoint !== '/auth/login' && endpoint !== '/auth/me' && !((endpoint === '/auth/mfa/challenge' || endpoint === '/auth/mfa/recovery') && error.code === 'Unauthorized')) || error.code === 'pre_auth_required') {
                invalidatePrivateRequests();
                if (typeof window !== 'undefined')
                    window.dispatchEvent(new Event('finsight-session-expired'));
            }
            throw error;
        }
        if (error instanceof Error && error.name === 'AbortError')
            throw error;
        throw new ApiError(0, 'Unable to connect. Check your connection and try again.');
    }
    finally {
        pending.delete(controller);
        options.signal?.removeEventListener('abort', abort);
    }
}
export const errorMessage = (error: unknown) => error instanceof Error ? error.message : 'Please try again.';
