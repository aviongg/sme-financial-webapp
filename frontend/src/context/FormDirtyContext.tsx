"use client";

import { createContext, useCallback, useContext, useRef } from 'react';

export interface DirtyFormRegistration {
  id: string;
  businessId: string;
  route: string;
  dirty: boolean;
  /** Persists recoverable form state into sessionStorage before unmounting stale context. */
  captureDraft?: () => boolean;
  /** Discards locally captured or stored draft state when user abandons changes. */
  discardDraft?: () => void;
}

export interface FormDirtyController {
  registerForm: (registration: DirtyFormRegistration) => () => void;
  hasDirtyWork: () => boolean;
  captureAllDrafts: () => boolean;
  discardAllDrafts: () => void;
}

const FormDirtyContext = createContext<FormDirtyController | null>(null);

export function FormDirtyProvider({ children }: { children: React.ReactNode }) {
  const registrations = useRef<Map<string, DirtyFormRegistration>>(new Map());

  const registerForm = useCallback((registration: DirtyFormRegistration) => {
    registrations.current.set(registration.id, registration);
    return () => {
      const current = registrations.current.get(registration.id);
      if (current === registration) {
        registrations.current.delete(registration.id);
      }
    };
  }, []);

  const hasDirtyWork = useCallback(() => {
    for (const reg of registrations.current.values()) {
      if (reg.dirty) return true;
    }
    return false;
  }, []);

  const captureAllDrafts = useCallback(() => {
    let capturedAny = false;
    for (const reg of registrations.current.values()) {
      if (reg.dirty && reg.captureDraft) {
        if (reg.captureDraft()) {
          capturedAny = true;
        }
      }
    }
    return capturedAny;
  }, []);

  const discardAllDrafts = useCallback(() => {
    for (const reg of registrations.current.values()) {
      if (reg.discardDraft) {
        reg.discardDraft();
      }
    }
  }, []);

  return (
    <FormDirtyContext.Provider value={{ registerForm, hasDirtyWork, captureAllDrafts, discardAllDrafts }}>
      {children}
    </FormDirtyContext.Provider>
  );
}

export function useOptionalFormDirty(): FormDirtyController | null {
  return useContext(FormDirtyContext);
}
