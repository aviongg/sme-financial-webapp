"use client";

import React, { createContext, useContext, useEffect, useCallback, useSyncExternalStore, useMemo } from "react";
import { en, type Translations } from "./translations/en";
import { ur } from "./translations/ur";

export type Locale = "en" | "ur";
export type Direction = "ltr" | "rtl";

interface LanguageContextType {
  locale: Locale;
  direction: Direction;
  t: Translations;
  setLocale: (locale: Locale) => void;
  toggleLocale: () => void;
}

const LanguageContext = createContext<LanguageContextType | undefined>(undefined);

const STORAGE_KEY = "finsight_locale";

const listeners = new Set<() => void>();

function subscribe(callback: () => void) {
  listeners.add(callback);
  if (typeof window !== "undefined") {
    window.addEventListener("storage", callback);
  }
  return () => {
    listeners.delete(callback);
    if (typeof window !== "undefined") {
      window.removeEventListener("storage", callback);
    }
  };
}

function getClientSnapshot(): Locale {
  if (typeof window === "undefined") return "en";
  try {
    const saved = localStorage.getItem(STORAGE_KEY) as Locale;
    if (saved === "en" || saved === "ur") return saved;
  } catch {
    // Storage unavailable
  }
  return "en";
}

function getServerSnapshot(): Locale {
  return "en";
}

export function LanguageProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const locale = useSyncExternalStore(subscribe, getClientSnapshot, getServerSnapshot);

  const direction: Direction = locale === "ur" ? "rtl" : "ltr";
  const t: Translations = locale === "ur" ? ur : en;

  useEffect(() => {
    if (typeof document !== "undefined") {
      document.documentElement.lang = locale;
      document.documentElement.dir = direction;
    }
  }, [locale, direction]);

  const setLocale = useCallback((newLocale: Locale) => {
    try {
      localStorage.setItem(STORAGE_KEY, newLocale);
    } catch {
      // Storage unavailable
    }
    listeners.forEach((listener) => listener());
  }, []);

  const toggleLocale = useCallback(() => {
    const nextLocale: Locale = locale === "en" ? "ur" : "en";
    setLocale(nextLocale);
  }, [locale, setLocale]);


  const value = useMemo(
    () => ({
      locale,
      direction,
      t,
      setLocale,
      toggleLocale,
    }),
    [locale, direction, t, setLocale, toggleLocale]
  );

  return (
    <LanguageContext.Provider value={value}>
      {children}
    </LanguageContext.Provider>
  );
}

export function useLanguage(): LanguageContextType {
  const context = useContext(LanguageContext);
  if (!context) {
    throw new Error("useLanguage must be used within a LanguageProvider");
  }
  return context;
}
