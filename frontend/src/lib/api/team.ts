import { apiClient } from './client';
import type { BusinessSummary, Role } from './session';
export type MemberRole = Exclude<Role, 'OWNER'>;
export interface Member { id: string; email: string; role: Role; status: 'ACTIVE' | 'INVITED' | 'SUSPENDED'; createdAt: string; }
export interface Invitation { id: string; businessName: string; role: MemberRole; createdAt: string; }
const reference = (id: string) => { if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) throw new Error('Invalid membership reference.'); return id; };
export const teamApi = {
  members: (signal?: AbortSignal) => apiClient<Member[]>('/memberships', { signal }),
  invite: (email: string, role: MemberRole) => apiClient<Member>('/memberships', { method: 'POST', body: JSON.stringify({ email: email.trim(), role }) }),
  update: (id: string, input: { role?: MemberRole; status?: 'ACTIVE' | 'SUSPENDED' }) => apiClient<Member>(`/memberships/${reference(id)}`, { method: 'PATCH', body: JSON.stringify({ role: input.role, status: input.status }) }),
  remove: (id: string) => apiClient<void>(`/memberships/${reference(id)}`, { method: 'DELETE' }),
  invitations: (signal?: AbortSignal) => apiClient<Invitation[]>('/invitations', { signal }),
  respond: (id: string, accept: boolean) => apiClient<void>(`/invitations/${reference(id)}/${accept ? 'accept' : 'decline'}`, { method: 'POST' }),
  rename: (businessName: string) => apiClient<BusinessSummary>('/businesses/active/name', { method: 'PATCH', body: JSON.stringify({ businessName: businessName.trim() }) }),
};
