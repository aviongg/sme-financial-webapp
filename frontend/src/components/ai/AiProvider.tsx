"use client";
import { createContext, useCallback, useContext } from 'react';
import { aiApi, AiStatus } from '@/lib/api/ai';
import { useLiveResource } from '@/components/integration/useLiveResource';
import { useConfiguration } from '@/components/configuration/ConfigurationProvider';

interface AiContext { status: AiStatus | null; loading: boolean; error: string; retry: () => void; available: boolean }
const Context = createContext<AiContext | null>(null);
const disabled: AiStatus = { enabled: false, available: false, mode: 'disabled', reasonCode: 'module_disabled', model: null, maxQuestionLength: 1000, maxConversationTurns: 8 };
export function AiProvider({ children }: { children: React.ReactNode }) {
  const configuration = useConfiguration(); const allowed = configuration?.available('ai') === true;
  const resource = useLiveResource(useCallback((signal: AbortSignal) => allowed ? aiApi.status(signal) : Promise.resolve(disabled), [allowed]));
  // /status is metadata only. Explanations always require an explicit user action.
  return <Context.Provider value={{ status: allowed ? resource.data : disabled, loading: resource.loading, error: resource.error, retry: resource.retry, available: allowed && resource.data?.available === true }}>{children}</Context.Provider>;
}
export function useAi() { return useContext(Context); }
