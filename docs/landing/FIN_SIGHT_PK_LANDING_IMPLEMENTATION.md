# FinSight PK public landing page — implementation record

**Date:** 9 November 2026 (Asia/Karachi). **Status:** local only. Two local commits on branch `claude/finsight-pk-landing` in worktree `C:/Users/Hp/FYP/wt-finsight-landing`: the original page `6e5a20ab6433f649d8a3f6c1a3b2a64eae1b0e7c`, then the audit polish follow-up described in [Audit polish follow-up](#audit-polish-follow-up) (its SHA is reported externally). **Not pushed and not integrated.** Where the follow-up section differs from earlier sections, the follow-up is current.

> **Deployment caveat (unified A+B product only).** This page advertises capabilities that today exist only on Developer A's `dev/suleman`: accounts and transactions, transfers, multi-currency, receivables and payables, payment cycles, recurring activity, daily cash forecast and scenarios, spreadsheet import, contacts, bank reconciliation and commissions. **It must not be publicly deployed against standalone `dev/fatima`.** Ship it only with the unified A+B application. Those claims were deliberately kept, not softened, because the page is meant for the merged product.

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
* *(Updated in the follow-up.)* `SessionProvider` **defers** its initial `/api/auth/me` probe while the pathname is an exact public route. A fresh load of `/landing` therefore makes no API request at all. The probe runs the first time the visitor reaches any other route. "Open FinSight" now appears only when the session is already known from earlier client navigation; a fresh load shows "Sign In", which takes a signed-in visitor straight into the app.
* Call-to-action destinations:
  * **Sign In** goes to `/`, the existing auth screen.
  * **Get Started** goes to `/?auth=register`. `AuthScreen` reads this optional hint to open the existing "Create your account" form, then removes it from the URL. The hint grants nothing, and registration still uses the unchanged `/auth/register` flow.
  * No new auth backend or social login was added.
* `/landing` has its own metadata: title `FinSight PK — Financial Clarity for SMEs` and a description. The root layout metadata is unchanged.

## Brand lockup

`components/marketing/BrandLockup.tsx` is reusable and used by both the navigation and the footer.

* It uses the existing blue growth-bars mark and a navy `FinSight` wordmark (Plus Jakarta Sans 800), followed by a `PK` marker.
* *(Updated in the follow-up.)* `PK` is a regional micro-badge: 0.45em, extra-bold, 0.08em tracking, 4px×2px padding, 4px radius, attached at cap height. On light backgrounds it is an indigo tint with a ring; on dark backgrounds it is translucent white. It is not a `<sup>`. The growth bars rise 38% → 64% → full height, so the third bar (with its dot) clearly reads as the highest.
* It is always `dir="ltr"` and `lang="en"`. An inline font family stops the global Urdu heading rule from switching it to Nastaliq.
* The visual is `aria-hidden`. The accessible name is the screen-reader text "FinSight PK".
* The authenticated app's branding (Sidebar and AuthScreen) was deliberately **not** changed.

## Section structure, in order

1. Sticky navigation: Product, Features, How It Works, For SMEs, Security, FAQ, the language toggle, Sign In and Get Started. *(Follow-up)* The section links show from 1280px. Below that they move into an accessible disclosure menu. The language toggle and call-to-action buttons stay in the header from 768px.
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
13. FAQ: seven native `<details>` items (`#faq`).
14. *(Follow-up)* The single closing call to action, "Early access" (`#access`).
15. Dark footer with the brand, tagline and three link groups (navigation only, no second CTA banner). The footer links are only in-page anchors, Sign In and Get Started. Privacy and Terms are omitted because no such routes exist.

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

## Audit polish follow-up

**Antigravity read-only audit:** rated 7.8/10, "Ship with fixes". It found:

* desktop navigation wrapping at 1024px
* hero preview cards squeezed to about 195px at 1024px, with label and amount collisions and `PKR 1,842,500` splitting
* Nastaliq collisions, because utility line-heights overrode the global Urdu rule
* a PK marker that read like a mathematical exponent
* currency tokens wrapping
* the planned margin on the wrong side in RTL
* feature repetition across sections
* a double closing CTA
* a placeholder security visual
* ragged mobile workspace chips
* missing outside-click close on the menu
* duplicate figure descriptions
* an unnecessary anonymous `/api/auth/me` 401 on `/landing`

All approved fixes were applied without changing the allow-list architecture, private-route protection, the `?auth=register` handoff, the DOM/SVG preview approach, the design system, claim restrictions, backend, migrations or Developer-A code.

| Fix | Result |
|---|---|
| 1024px navigation | Every item is `whitespace-nowrap`. After testing in the browser, section links show from `xl` (1280px), because six links plus language and both CTAs do not fit cleanly at 1024px, especially in Urdu. Below 1280px the header keeps the language toggle, Sign In and Get Started (from 768px) plus the menu button. Item padding is `px-2.5`, or `px-3` from 1536px, so 1440px stays airy |
| 1024px hero | The outer hero becomes two columns only from `xl`. At 1024px the text sits above a preview up to 52rem wide, so the preview cards are about 390px wide. Obligation rows put label and amount on one line and the timing below. The business name is no longer truncated |
| Urdu line-height | A landing-scoped guard, `.root[lang="ur"] :is(h1,h2,h3,p,li,summary,dt,dd):not([dir="ltr"],[lang="en"] *) { line-height: 1.9 }`, covers the hero h1, every section heading, outcome lists, chat bubbles, the access heading and footer headings. English keeps its own leading (the hero stays at 1.08) |
| PK badge | Micro-badge as described under Brand lockup. The browser checks that it is ≤0.46× the wordmark size, attached within 8px, at cap height, centred above the midline, less than 0.8× the wordmark height, tinted with a radius, and that the bars rise in order |
| Amounts | The `money()` token is `dir="ltr"`, `whitespace-nowrap` and `tabular-nums`. The language card's `+ PKR 410,000` and `PKR 1,842,500` also don't wrap |
| RTL margin | The planned-margin block follows the page direction; only the `18%` token is LTR. The browser checks that it is right-aligned in Urdu and left-aligned in English |
| Repetition | The six capability cards stay as the overview. Planning now has three outcomes: cost and price before quoting; keep every revision; move approved estimates into planning for an **existing** project, job or order. Intelligence now has three: understand what changed; see the evidence behind it; ask and explore where enabled. All the visuals are kept |
| Single CTA | Order is FAQ → Early access CTA → footer. The footer's duplicate CTA block and its `final` copy were removed |
| Security visual | The glow-and-shield placeholder is replaced by an "Illustrative" team-access card. It shows Owner, Accountant, Manager and Viewer with access summaries that match the frontend permission table in `lib/api/session.ts`, plus signed-in session / MFA / activity-logged cues. No certification or encryption claims |
| Mobile chips | Below `lg`, the inputs use an even two-column grid with the fifth chip spanning both columns. The staggered column is kept on desktop. Checked in LTR and RTL |
| Menu | A lightweight backdrop closes the menu on an outside tap. Its mousedown is prevented, so the tap does not activate content underneath, and focus returns to the toggle. Focus moving past the menu into the page closes it. Escape still closes it and restores focus, and choosing a link closes it. No dialog framework or focus trap was added |
| Figures | Pictorial figures (hero preview, forecast) hide their drawing and carry one sr-only `figcaption`. Readable figures (estimate, insight, team access) are named by their visible heading and sample tag via `aria-labelledby`. There is no `aria-label` duplication |
| Pakistan anchor | One hero line: "Built with the realities of Pakistani SMEs in mind." (with an Urdu equivalent). "Pakistan" appears exactly once in the English copy |
| Forecast | The pressure window is now a soft amber gradient with dashed boundary guides and a small "Pressure window" pill, replacing the flat block |
| Early-access pill | A neutral badge icon replaces the banknote icon, so it does not suggest pricing |

### Auth-probe result

This was resolved narrowly and safely.

* `SessionProvider` now uses the same exact `isPublicRoute()` allow-list as the boundary. Its initial probe moved into a route-aware effect: it is skipped while the pathname is public and fires once the visitor reaches any other route.
* Status stays `loading` meanwhile, which grants nothing. The private boundary still shows its loading state until the probe resolves.
* All other session behaviour is unchanged: the focus revalidation (which only runs when authenticated), the business-change and session-expiry listeners, the bfcache refresh, the cross-tab channel, login/logout and the challenge stages.
* `probed` is reset in the listener effect's cleanup, so a remount (StrictMode) probes again.

What was verified:

* A fresh `/landing` load, signed in or not, makes **zero** API requests and logs no 401.
* `/`, `/records`, `/settings`, `/reports` and `/landing/extra` still probe and show sign-in when signed out.
* Signed in without a business, `/` shows business setup.
* Signed in, Sign In from `/landing` leads into the private shell.
* The only remaining tolerated console error on the public page is the app-wide `/favicon.ico` 404.

Trade-off: a freshly loaded `/landing` no longer knows the visitor is signed in, so it shows "Sign In" rather than "Open FinSight". Sign In still takes a signed-in user straight into the app.

### Files changed in the follow-up

* `frontend/src/components/marketing/{BrandLockup,LandingNav,LandingSections,LandingVisuals,LandingFooter,LandingPage}.tsx` and `landing.module.css`
* `frontend/src/lib/i18n/translations/landing.ts`
* `frontend/src/components/auth/SessionProvider.tsx` (deferred public-route probe only)
* `frontend/tests/landing.test.mjs`
* `frontend/tests/browser-landing.integration.mjs`
* this document

No backend, API, migration or Developer-A file changed.

### Final verification (follow-up tree)

| Gate | Result |
|---|---|
| Landing tests | **24/24** |
| Full frontend suite | **182/182** (158 existing + 24 landing), 0 skipped |
| TypeScript | pass |
| ESLint | 0 errors, 1 existing `SessionProvider` warning (the `revision.current` cleanup it already had) |
| `next build --webpack` | pass; `/landing` is dynamic |
| Real browser | **20/20**: 10 anonymous-render checks (1440/1024/390×844/390×600/360×800 × EN/UR), desktop anchors/focus/FAQ/language persistence, 6 menu checks (1024 EN/UR, 390×844 EN, 390×600 UR, 360×800 EN/UR), CTAs, private routes, reduced motion |

Each anonymous-render check covers:

* the `main > section[id]` order, ending with FAQ then access
* one footer `/?auth=register` link and three footer h2 headings
* zero API requests
* PK badge and bar geometry
* links that resolve
* no horizontal overflow
* no wrapped header items or amounts, and no label/amount collisions
* preview cards at least 230px wide
* the RTL margin side
* a minimum Urdu line-height ratio of **1.90** at every Urdu viewport, while the English hero keeps its tight leading
* the expected breakpoint behaviour for the header and menu
* hero buttons at least 44px tall
* the even mobile chip grid

No page errors, hydration errors, CSP violations or external requests occurred.

`results.json` lists the tolerated console errors. One is `/favicon.ico` (app-wide). Two are `/api/auth/me` 401s that occur only in the CTA check, after leaving `/landing` for `/`, where the probe is expected.

**Accessibility result:**

* one h1, landmarks and the skip link
* visible focus and native FAQ disclosures
* reduced-motion support (no animation)
* a disclosure menu that keeps `aria-expanded`/`aria-controls`, moves focus into the panel, returns focus on close, and closes on Escape, outside tap and focus leaving
* figures with one accessible name each

**Artifacts:** `C:/Users/Hp/FYP/wt-finsight-landing/tmp/landing-browser/` (viewport, full-page and menu-open PNGs, `sections/` captures at 1440/1024/390/360 in EN and UR, `results.json`) and `tmp/landing-browser-run.log`. These are local only and not committed.

### Final production-runtime acceptance (10 November 2026)

The follow-up checks above did not record which Next runtime they ran against. This run re-ran them on the production runtime against unchanged source `a67eefa`. No source change was needed.

* **Base and product truth:** the base `origin/dev/fatima` is still `228e593`. `origin/dev/suleman` `b585dda` was inspected read-only and no landing claim is now inaccurate:
  * accounts and transfers, FX, obligations, payment cycles, recurring rules and reconciliation all exist
  * the forecast UI offers 7/30/60/90-day horizons and is built from obligations and recurring rules
  * scenarios do not modify actuals
  * the new C05 project-finance actuals are not claimed
  * the new `SALESPERSON` role is not listed in the four invited roles, but the list is not presented as exhaustive
* **Build:** `next build --webpack` with `BACKEND_API_URL=http://127.0.0.1:8080` and `NEXT_PUBLIC_DATA_MODE=live`. The full suite passed 182/182, TypeScript passed, and ESLint reported 0 errors and the 1 existing warning.
* **Runtime:** the Docker-equivalent standalone server (`.next/standalone/server.js` with `.next/static` and `public` copied alongside), started with `NODE_ENV=production`, `PORT=3100` and `HOSTNAME=127.0.0.1`. It is not `next dev`. The served CSP is nonce-based with no `unsafe-eval`. Headless Edge used Playwright 1.62.1 against the synthetic session fixture on 8080. **No Spring, PostgreSQL or providers were used.**
* **`tests/browser-landing.integration.mjs`:** **20/20**.
* **Supplementary read-only probes** were run from an uncommitted script, at 10 viewport/locale combinations:
  * HTTP 200 and zero `/api` requests on anonymous `/landing`
  * cumulative layout shift of at most 0.028
  * no 4xx/5xx responses (other than `/favicon.ico`), failed requests, external requests, console or page errors
  * one h1 with no skipped heading levels
  * 5 figures, each with one accessible name
  * no dead anchors, and route links limited to `/` and `/?auth=register`
  * exactly one `#access` closing CTA
  * zero horizontal overflow, correct `dir`, and no private UI
  * anonymous `/`, `/records` and `/settings` each probe the session and show "Sign in to FinSight" without the app shell
* **Artifacts:** `C:/Users/Hp/FYP/wt-finsight-landing/tmp/landing-production-browser/` (harness PNGs and `results.json`, plus `sections/` hero, section, footer and menu captures and `extra-results.json`) and `tmp/landing-production-browser-run.log`. These are local only and not committed.

### Still for visual/product approval

* The logo blue on this page vs the teal app buttons.
* A native-speaker review of the Urdu copy.
* Whether "Early access" matches commercial plans.
* An app-wide favicon.
* The `/landing` → `/` route split (see "Before `/landing` becomes `/`" above).
