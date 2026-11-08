# FinSight PK public landing page — implementation record

**Date:** 9 November 2026 (Asia/Karachi). **Status:** local only. One local commit on branch `claude/finsight-pk-landing` in worktree `C:/Users/Hp/FYP/wt-finsight-landing`. **Not pushed and not integrated.**

* Implementation base: `dev/fatima` `228e593cda645a5203073ff7e4241a72ff09473c`, verified after `git fetch origin`.
* Reference only: `origin/dev/suleman` `cca8de3ce6f1f6f7dbfefdb506f211495707fd14`. It was inspected read-only with `git show` and `git ls-tree`. It was never checked out or modified.

## Scope

This is a frontend-only public marketing page at **`/landing`**. **`/` is still the authenticated application entry.** It still shows the auth screen, then business setup, then the dashboard.

This change does not touch:

* backend code, APIs or migrations
* financial calculations, `health-score-v1` or AI provider behaviour
* inventory accounting or payment logic

**Pricing, billing and checkout are not implemented.** The page uses an honest "Early access" call to action, with no prices, trials or card forms.

## Route and public-access boundary

* `frontend/src/lib/routing/public-routes.ts` defines an exact-match allow-list, `PUBLIC_ROUTES = ['/landing']`. Nested or similar-looking paths such as `/landing/x`, `/landingx` and `/LANDING` are not public.
* `IntegrationBoundary` now checks `isPublicRoute(pathname)` as its first branch and renders children directly. A public page needs no session or business, and never mounts `ConfigurationProvider` or `AiProvider`. Every other branch is unchanged: demo, reset-password, loading, error, anonymous/challenge → `AuthScreen`, no business → `BusinessSetup`, and the private tree.
* `SessionProvider` still runs its existing `/api/auth/me` probe on `/landing`. When the visitor is signed in, the navigation shows "Open FinSight" instead of "Sign In" and "Get Started". The page calls no business API.
* Call-to-action destinations:
  * **Sign In** goes to `/`, the existing auth screen.
  * **Get Started** goes to `/?auth=register`. `AuthScreen` reads this optional hint to open the existing "Create your account" form, then removes it from the URL. The hint grants nothing, and registration still uses the unchanged `/auth/register` flow.
  * No new auth backend or social login was added.
* `/landing` has its own metadata: title `FinSight PK — Financial Clarity for SMEs` and a description. The root layout metadata is unchanged.

## Brand lockup

`components/marketing/BrandLockup.tsx` is reusable and used by both the navigation and the footer.

* It uses the existing blue growth-bars mark and a navy `FinSight` wordmark (Plus Jakarta Sans 800), followed by a `PK` marker.
* The `PK` marker is a top-aligned brand marker at 0.4em in the logo blue, not a mathematical `<sup>`.
* It is always `dir="ltr"` and `lang="en"`. An inline font family stops the global Urdu heading rule from switching it to Nastaliq.
* The visual is `aria-hidden`. The accessible name is the screen-reader text "FinSight PK".
* The authenticated app's branding (Sidebar and AuthScreen) was deliberately **not** changed.

## Section structure, in order

1. Sticky navigation: Product, Features, How It Works, For SMEs, Security, FAQ, the language toggle, Sign In and Get Started. On screens narrower than 1024px it becomes an accessible disclosure menu.
2. Hero, with an h1 and an illustrative product preview: health score, cash position, a 30-day outlook and upcoming obligations.
3. The SME problem (six items).
4. One financial workspace: a visual of scattered inputs flowing into five pillars (`#product`).
5. Capability showcase: six outcome groups (`#features`).
6. How FinSight works: four steps (`#how-it-works`).
7. Cash and obligations, on a dark band with an illustrative daily forecast chart (`#cash`).
8. Planning and operations, with an illustrative estimate (`#planning`).
9. Insight and reporting, with illustrative score reasons, an Ask FinSight exchange and a disclaimer (`#intelligence`).
10. Built for different SMEs: four business types (`#for-smes`).
11. English and Urdu: a live toggle plus side-by-side LTR and RTL cards (`#language`).
12. Security and control (`#security`).
13. Early access call to action (`#access`).
14. FAQ: seven native `<details>` items (`#faq`).
15. Final call to action and a dark footer. The footer links are only in-page anchors, Sign In and Get Started. Privacy and Terms are omitted because no such routes exist.

Every visual is real DOM text inside a `<figure>` and is visibly tagged "Illustrative · sample figures". No visual reads live data.

## Feature-claim boundaries

Claims are limited to what current `dev/fatima` or current `dev/suleman` actually implements.

**Claims used:**

* From `dev/suleman`: accounts and transactions, transfers, multi-currency accounts, receivables and payables, payment cycles and advances, recurring activity, daily cash forecast (7–90 days), funding scenarios, spreadsheet import with review, contacts, bank reconciliation and salesperson commissions.
* From `dev/fatima`:
  * project, job and order planning
  * estimates with revisions and approval, and conversion of an approved estimate into a planned project budget
  * costing and pricing
  * invoice and bill records
  * sample and production cost planning
  * inventory items with "explore valuation scenarios"
  * health score with evidence, explanations and recommendations
  * trends
  * reports that can be printed or exported (CSV)
  * Ask FinSight, worded "where enabled" because the provider is disabled by default
  * document upload and scan, with extracted details reviewed before use
  * EN/UR with RTL
  * authenticated sessions, MFA (authenticator app), Owner/Accountant/Manager/Viewer roles, business isolation and logged sign-in and record changes

**Claims deliberately avoided:**

* pricing, trials, checkout or subscriptions
* fully automated accounting, tax filing, COGS or perpetual inventory
* real-time bank feeds
* certifications such as SOC 2, ISO or PCI
* encryption standards
* guaranteed or autonomous AI advice
* industry compliance modules
* one-click audit
* internal vocabulary (feature IDs, phases, migrations, branches)

`tests/landing.test.mjs` guards these restrictions.

## English and Urdu

The copy lives in `lib/i18n/translations/landing.ts` as typed `landingEn` and `landingUr` objects with identical structure, which a test checks. The page uses the existing `useLanguage()` locale, shares the `finsight_locale` storage, and lets `LanguageProvider` set `<html dir/lang>`. The landing root also sets `dir` and `lang`.

The language toggle on the anonymous page changes the locale only on the device. It does not call the business-settings API that `LanguageControl` uses.

The Urdu copy is written as Urdu prose. Only product names (FinSight, Ask FinSight) and role names stay in Latin script. Amounts are `dir="ltr"` with tabular figures, and charts keep a left-to-right time axis. Arrow icons mirror in RTL.

## Files changed

* Modified:
  * `frontend/src/components/layout/IntegrationBoundary.tsx`
  * `frontend/src/components/auth/AuthScreen.tsx`
  * `docs/phase2/IMPLEMENTATION_STATE.md` (reservation record)
* Added:
  * `frontend/src/app/landing/page.tsx`
  * `frontend/src/components/marketing/{BrandLockup,LandingNav,LandingPrimitives,LandingVisuals,LandingSections,LandingFooter,LandingPage}.tsx` and `landing.module.css`
  * `frontend/src/lib/routing/public-routes.ts`
  * `frontend/src/lib/i18n/translations/landing.ts`
  * `frontend/tests/landing.test.mjs`
  * `frontend/tests/browser-landing.integration.mjs`
  * this document

## Verification (final tree)

| Gate | Result |
|---|---|
| New targeted tests (`node --test tests/landing.test.mjs`) | 13/13 |
| Full frontend suite (`node --test tests/*.test.mjs`) | **171/171** (158 existing + 13 new), 0 skipped |
| TypeScript (`tsc --noEmit --incremental false`) | pass |
| ESLint | 0 errors, 1 existing `SessionProvider` warning |
| Production build (`next build --webpack`) | pass; `/landing` is listed as a dynamic route. The Edge-runtime warnings come from Next internals in the existing middleware |
| Real browser (`tests/browser-landing.integration.mjs`) | **18/18** |

The browser run used a production `next start` server on `127.0.0.1:3100`, headless Edge, and a synthetic session fixture on `127.0.0.1:8080`. That is the existing `/api` rewrite target. The fixture covers anonymous, signed in without a business, and signed in with a business. **Spring, PostgreSQL and providers were not used.**

The browser checks covered:

* **Viewports:** 1440, 1024, 390×844, 390×600 and 360×800, each in EN and UR, all with HTTP 200.
* **Page structure:**
  * correct title and description
  * `<html dir/lang>` set correctly
  * one h1, every section present
  * no auth form or app shell on the page
* **Layout and links:**
  * the PK marker is measured as smaller than the wordmark, after it, above its midline, and in Latin type
  * no horizontal overflow and no element outside the viewport
  * every anchor resolves, and every route link is `/` or `/?auth=register`
  * hero buttons are at least 44px tall
* **Interaction:**
  * Anchor navigation places each section directly below the sticky header.
  * The skip link works, keyboard focus is visible, and FAQ items open with the keyboard.
  * The language toggle switches to RTL and back, and the choice persists across a reload.
  * The mobile menu sets `aria-expanded` and `aria-controls`, moves focus into the menu, fits short viewports, closes with Escape and returns focus, closes after a link is chosen, and offers a language toggle.
* **Routing and access:**
  * Get Started opens "Create your account" and removes `?auth` from the URL. Sign In opens "Sign in to FinSight".
  * Anonymous `/`, `/records`, `/settings`, `/reports` and `/landing/extra` all show sign-in.
  * Signed in without a business, `/` shows business setup while `/landing` stays public.
  * Signed in with a business, `/landing` shows "Open FinSight", calls only the session endpoints, and leads to the private shell.
* **Motion:** with reduced motion enabled, no animation runs.
* **Errors:** no page errors, hydration errors, CSP violations or external requests.
* **Tolerated, existing app-wide console errors** (recorded in `results.json`):
  * the anonymous `/api/auth/me` 401 from the session probe
  * one automatic `/favicon.ico` 404, because the repository ships no favicon

**Screenshots and artifacts:** `C:/Users/Hp/FYP/wt-finsight-landing/tmp/landing-browser/`. This folder holds viewport, full-page and menu-open PNGs for each viewport and locale, per-section captures in `sections/`, and `results.json`. These are local artifacts and are not committed.

To re-run the browser checks, run `next build --webpack`, start `next start -p 3100 -H 127.0.0.1`, keep port 8080 free, then run `PLAYWRIGHT_MODULE=<playwright path> BROWSER_ARTIFACT_DIR=<dir> node tests/browser-landing.integration.mjs`.

## Before `/landing` becomes `/`

1. Complete the A+B application integration, then decide where the authenticated app entry lives (for example `/app` or `/dashboard`). Update the Sidebar and Home links, the AuthScreen and BusinessSetup redirects, and any `href="/"` that assumes the dashboard.
2. Move the page, or add `/` to the public allow-list. Because the current `/` is the protected dashboard, this needs the route split above first. It is not a one-line change.
3. Re-check every claim against the merged product, especially FX, commissions, forecast and reconciliation, which come from Developer-A work.
4. Reconcile app-wide branding (FinSight vs FinSight PK), add a favicon, and resolve the logo-blue vs app-teal button colour.
5. Add Privacy and Terms routes, with real legal text, before linking to them.
6. Wire pricing or checkout only after commercial decisions and a real billing provider exist.
7. Re-run this harness against the merged tree, and add the route to the shared browser suites.
