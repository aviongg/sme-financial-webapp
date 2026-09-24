"use client";

import React, { forwardRef, useState, useEffect } from "react";
import { Input, type InputProps } from "./Input";
import { useLanguage } from "@/lib/i18n/context";

export interface CurrencyInputProps
  extends Omit<InputProps, "value" | "onChange" | "type"> {
  value?: number | null;
  onValueChange?: (val: number | null) => void;
  allowNegative?: boolean;
}

export const CurrencyInput = forwardRef<HTMLInputElement, CurrencyInputProps>(
  (
    {
      value,
      onValueChange,
      allowNegative = false,
      error,
      className,
      ...props
    },
    ref
  ) => {
    const { locale } = useLanguage();
    const [displayString, setDisplayString] = useState<string>("");

    useEffect(() => {
      if (value === null || value === undefined) {
        setDisplayString("");
      } else {
        setDisplayString(value.toLocaleString("en-US"));
      }
    }, [value]);

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      const raw = e.target.value.replace(/,/g, "").trim();

      if (raw === "") {
        setDisplayString("");
        onValueChange?.(null);
        return;
      }

      // Check numeric
      const numericVal = Number(raw);
      if (Number.isNaN(numericVal)) {
        return; // Ignore non-numeric typing
      }

      if (!allowNegative && numericVal < 0) {
        return; // Block negative numbers per specification section 9.5
      }

      setDisplayString(raw);
      onValueChange?.(numericVal);
    };

    const handleBlur = () => {
      if (value !== null && value !== undefined && !Number.isNaN(value)) {
        setDisplayString(value.toLocaleString("en-US"));
      }
    };

    const prefix = locale === "ur" ? undefined : "PKR";
    const suffix = locale === "ur" ? "روپے" : undefined;

    return (
      <Input
        ref={ref}
        type="text"
        inputMode="decimal"
        value={displayString}
        onChange={handleInputChange}
        onBlur={handleBlur}
        error={error}
        leftAddon={prefix}
        rightAddon={suffix}
        className={`tabular-nums ${className || ""}`}
        placeholder="0"
        {...props}
      />
    );
  }
);

CurrencyInput.displayName = "CurrencyInput";
