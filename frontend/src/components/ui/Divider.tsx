import React from "react";
import { cn } from "@/lib/utils/cn";

export interface DividerProps extends React.HTMLAttributes<HTMLHRElement> {
  orientation?: "horizontal" | "vertical";
}

export function Divider({
  orientation = "horizontal",
  className,
  ...props
}: DividerProps) {
  if (orientation === "vertical") {
    return (
      <div
        role="separator"
        aria-orientation="vertical"
        className={cn(
          "w-px self-stretch bg-[var(--color-border-subtle)] my-0 mx-2",
          className
        )}
      />
    );
  }

  return (
    <hr
      className={cn(
        "w-full border-0 border-t border-[var(--color-border-subtle)] my-4",
        className
      )}
      {...props}
    />
  );
}
