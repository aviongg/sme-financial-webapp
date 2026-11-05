"use client";
import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { BusinessConfiguration, configurationApi, moduleAvailable, routeModule } from '@/lib/api/configuration';
import { useSession } from '@/components/auth/SessionProvider';

interface ConfigurationContext {
  data: BusinessConfiguration | null; loading: boolean; failed: boolean;
  reload: () => void; accept: (configuration: BusinessConfiguration) => void;
  available: (key: string) => boolean; allowsRoute: (path: string) => boolean;
}
const Context = createContext<ConfigurationContext | null>(null);
export function ConfigurationProvider({ children }: { children: React.ReactNode }) {
  const { business } = useSession();
  const [data, setData] = useState<BusinessConfiguration | null>(null);
  const [loading, setLoading] = useState(true), [failed, setFailed] = useState(false), [attempt, setAttempt] = useState(0);
  const revision = useRef(0);
  const channel = useRef<BroadcastChannel | null>(null);
  const reload = useCallback(() => setAttempt(value => value + 1), []);
  useEffect(() => {
    const controller = new AbortController(), ticket = ++revision.current;
    queueMicrotask(() => { if (!controller.signal.aborted) { setLoading(true); setFailed(false); } });
    configurationApi.get(controller.signal).then(value => {
      if (ticket !== revision.current || controller.signal.aborted) return;
      if (!Array.isArray(value.modules) || !Array.isArray(value.requestedModules)) throw new Error('Invalid configuration response.');
      setData(value); setLoading(false);
    }).catch(error => { if (ticket === revision.current && error?.name !== 'AbortError') { setData(null); setFailed(true); setLoading(false); } });
    return () => { controller.abort(); };
  }, [attempt]);
  useEffect(() => {
    window.addEventListener('focus', reload);
    if (typeof BroadcastChannel !== 'undefined') {
      const bus = new BroadcastChannel('finsight-configuration'); channel.current = bus;
      bus.onmessage = event => { if (event.data?.businessId === business?.businessId) reload(); };
    }
    return () => { window.removeEventListener('focus', reload); channel.current?.close(); channel.current = null; };
  }, [business?.businessId, reload]);
  const accept = useCallback((value: BusinessConfiguration) => {
    revision.current++; setData(value); setLoading(false); setFailed(false);
    channel.current?.postMessage({ businessId: business?.businessId });
  }, [business?.businessId]);
  const available = (key: string) => !failed && moduleAvailable(data, key);
  return <Context.Provider value={{ data, loading, failed, reload, accept, available,
    allowsRoute: path => { const feature = routeModule(path); return !feature || available(feature); } }}>{children}</Context.Provider>;
}
/** Demo navigation has no live configuration context and cannot synthesize live capabilities. */
export function useConfiguration() { return useContext(Context); }
