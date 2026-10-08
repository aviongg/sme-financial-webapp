import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Combines conditional class names with Tailwind class merging to prevent collision.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
