import { apiClient } from './client';
import type { LanguagePreference } from './contracts';

export interface AiStatus {
  enabled: boolean; available: boolean; mode: 'disabled' | 'simulated' | 'openai'; reasonCode: string | null;
  model: string | null; maxQuestionLength: number; maxConversationTurns: number;
}
export interface ConversationMessage { role: 'user' | 'assistant'; content: string }
export interface AiRequest {
  type: 'dashboard' | 'insight' | 'recommendation' | 'ask'; language: LanguagePreference;
  question?: string; sourceId?: string; month?: string; conversation?: ConversationMessage[];
}
export interface AiEvidence { id: string; kind: string; label: string; value: string | null; unit: string; period: string | null; sourceMode: 'SNAPSHOT' | 'PLAN' }
export interface AiExplanation {
  schemaVersion: 'ai-explanation-v1'; language: LanguagePreference; summary: string;
  interpretation: string[]; considerations: string[]; limitations: string[]; evidence: AiEvidence[];
  provenance: { mode: 'simulated' | 'openai' | 'deterministic'; model: string | null; promptVersion: string; evidenceFingerprint: string; cached: boolean };
  reasonCode: string | null;
}
export const MAX_CONVERSATION_MESSAGES = 8;
export const MAX_QUESTION_LENGTH = 1000;
export function aiPayload(input: AiRequest): AiRequest {
  const question = input.question?.trim();
  if (question && question.length > MAX_QUESTION_LENGTH) throw new Error('Question is too long.');
  if (input.type === 'ask' && !question) throw new Error('A question is required.');
  if (input.conversation && (input.conversation.length > MAX_CONVERSATION_MESSAGES || input.conversation.some(item => !['user', 'assistant'].includes(item.role) || item.content.length > MAX_QUESTION_LENGTH))) throw new Error('Conversation limit exceeded.');
  return { type: input.type, language: input.language, ...(question ? { question } : {}),
    ...(input.sourceId ? { sourceId: input.sourceId } : {}), ...(input.month ? { month: input.month } : {}),
    ...(input.conversation ? { conversation: input.conversation.map(item => ({ role: item.role, content: item.content })) } : {}) };
}
export function conversationHistory(messages: ConversationMessage[]) {
  // The current question is sent separately. Never forward arbitrary UI/provenance fields.
  return messages.slice(-MAX_CONVERSATION_MESSAGES).map(item => ({ role: item.role, content: item.content.slice(0, MAX_QUESTION_LENGTH) }));
}
export const aiApi = {
  status: (signal?: AbortSignal) => apiClient<AiStatus>('/ai/status', { signal }),
  explain: (input: AiRequest, signal?: AbortSignal) => apiClient<AiExplanation>('/ai/explain', { method: 'POST', body: JSON.stringify(aiPayload(input)), signal }),
};
