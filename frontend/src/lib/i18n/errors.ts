import type { Translations } from './translations/en';
import { ApiError } from '../api/client';
import { liveLabel } from './labels';
/** Localize failures without exposing internal server details, secrets or identifiers. */
export function localizedError(error: unknown, t: Translations, context?: 'authentication' | 'mfa'): string {
  if (error instanceof ApiError) {
    if (error.status === 0) return t.live.connectionError;
    if (error.status === 401) return context === 'mfa' ? t.live.invalidMfa : context === 'authentication' ? t.live.invalidCredentials : t.live.sessionExpired;
    if (error.status === 403) return t.live.accessDenied;
    if (error.status === 404) return t.live.notFound;
    if (error.status === 409) return t.live.conflict;
    if (error.status === 429) return t.live.rateLimited;
    if (error.status === 400 || error.status === 422) {
      const labels = Object.keys(error.fieldErrors || {}).map(key => liveLabel(t, key)).filter(label => label !== t.live.unavailable);
      return t.live.invalidInput + (labels.length ? ' ' + labels.join(' · ') : '');
    }
    return t.live.requestFailed;
  }
  return error instanceof Error && Object.values(t.live).includes(error.message) ? error.message : t.live.requestFailed;
}

export function localizedSessionError(message: string, t: Translations): string {
 if (!message) return '';
 if (/invalid.*(email|password)|bad credentials/i.test(message)) return t.live.invalidCredentials;
 if (/invalid.*(mfa|verification|code)/i.test(message)) return t.live.invalidMfa;
 if (message.includes('Sign-out')) return t.live.signOutFailed;
 if (message.includes('expired')) return t.live.sessionExpired;
 if (message.includes('authentication stage')) return t.live.sessionUnsupported;
 return t.live.sessionVerifyFailed;
}
