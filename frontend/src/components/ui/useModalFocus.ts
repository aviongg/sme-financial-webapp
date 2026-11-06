"use client";
import { useEffect, useRef, type RefObject } from 'react';
const stack: HTMLElement[] = [];
let previousOverflow = '';
/** One topmost keyboard boundary, including nested sheets/dialogs. */
export function useModalFocus(open: boolean, close: () => void, root: RefObject<HTMLDivElement | null>) {
 const latestClose = useRef(close);
 useEffect(() => { latestClose.current = close; }, [close]);
 useEffect(() => {
  const node = root.current;
  if (!open || !node) return;
  const before = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  if (!stack.length) { previousOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden'; }
  stack.push(node);
  // Child effects may mount before parent effects; DOM nesting, not effect order, defines the top dialog.
  stack.sort((a, b) => a === b ? 0 : a.contains(b) ? -1 : b.contains(a) ? 1 : 0);
  const controls = () => [...node.querySelectorAll<HTMLElement>('button, a[href], input, select, textarea, [tabindex]')].filter(el => el.tabIndex >= 0 && !el.matches(':disabled') && el.getClientRects().length > 0 && !el.closest('[inert]'));
  if (!node.contains(document.activeElement)) (controls()[0] ?? node).focus();
  const handleKeyDown = (e: KeyboardEvent) => {
   if (stack.at(-1) !== node) return;
   if (e.key === "Escape") { e.preventDefault(); e.stopImmediatePropagation(); latestClose.current(); }
   if (e.key === 'Tab') {
    const elements = controls(), first = elements[0], last = elements.at(-1);
    if (!first) { e.preventDefault(); node.focus(); }
    else if (e.shiftKey && (document.activeElement === first || !node.contains(document.activeElement))) { e.preventDefault(); last?.focus(); }
    else if (!e.shiftKey && (document.activeElement === last || !node.contains(document.activeElement))) { e.preventDefault(); first.focus(); }
   }
  };
  const containFocus = (e: FocusEvent) => { if (stack.at(-1) === node && !node.contains(e.target as Node)) (controls()[0] ?? node).focus(); };
  document.addEventListener('keydown', handleKeyDown, true); document.addEventListener('focusin', containFocus);
  return () => {
   const index = stack.indexOf(node); if (index >= 0) stack.splice(index, 1);
   document.removeEventListener('keydown', handleKeyDown, true); document.removeEventListener('focusin', containFocus);
   if (!stack.length) document.body.style.overflow = previousOverflow;
   if (before?.isConnected && (!stack.length || stack.at(-1)?.contains(before))) before.focus();
  };
 }, [open, root]);
}
