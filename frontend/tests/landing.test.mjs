import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { existsSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import ts from 'typescript';

// Public landing page: route exemption, brand, sections, links, bilingual copy and claim boundaries.
// Rendered geometry, anchors, mobile menu and RTL are verified separately in the real browser harness.
const directory = mkdtempSync(path.join(tmpdir(), 'finsight-landing-'));
after(() => rmSync(directory, { recursive: true, force: true }));
const source = (file) => readFileSync(new URL(file, import.meta.url), 'utf8');
const load = (file, name) => {
  writeFileSync(path.join(directory, `${name}.js`), ts.transpileModule(source(file), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText);
  return createRequire(import.meta.url)(path.join(directory, `${name}.js`));
};
const { PUBLIC_ROUTES, isPublicRoute } = load('../src/lib/routing/public-routes.ts', 'public-routes');
const { landingEn, landingUr, landingCopy, LANDING_LINKS, LANDING_SECTIONS } = load('../src/lib/i18n/translations/landing.ts', 'landing');

const marketing = '../src/components/marketing/';
const marketingFiles = readdirSync(new URL(marketing, import.meta.url)).filter((file) => file.endsWith('.tsx'));
const marketingSource = marketingFiles.map((file) => source(`${marketing}${file}`)).join('\n');
const sections = source(`${marketing}LandingSections.tsx`);

test('only the exact / and /landing paths are public', () => {
  assert.deepEqual([...PUBLIC_ROUTES].sort(), ['/', '/landing'].sort());
  assert.equal(isPublicRoute('/landing'), true);
  assert.equal(isPublicRoute('/'), true);
  for (const pathname of ['/landing/', '/landing/admin', '/landingx', '/LANDING', '/records', '/settings', '/onboarding', '/reset-password', '', null, undefined]) {
    assert.equal(isPublicRoute(pathname), false, String(pathname));
  }
});

test('IntegrationBoundary exempts public routes first and keeps every private branch unchanged', () => {
  const boundary = source('../src/components/layout/IntegrationBoundary.tsx');
  const publicCheck = boundary.indexOf('if(isPublicRoute(pathname))return <>{children}</>;');
  assert.ok(publicCheck > 0, 'explicit allow-list check');
  // The exemption is the first branch, so no session/business state is needed for /landing.
  for (const later of ['if(isDemoMode)', "if(pathname==='/reset-password')", "session.status==='loading'", "session.status==='anonymous'", '!session.business']) {
    assert.ok(boundary.indexOf(later) > publicCheck, later);
  }
  // No prefix/pattern matching and no other public bypass.
  assert.doesNotMatch(boundary, /startsWith|includes\(|RegExp|\.test\(/);
  assert.equal(boundary.match(/return <>\{children\}<\/>/g).length, 1);
  // Private routes still require authentication, an active business, configuration and AI providers.
  assert.match(boundary, /session\.status==='anonymous'\|\|session\.status==='challenge'\)return <AuthScreen/);
  assert.match(boundary, /if\(!session\.business\|\|pathname==='\/onboarding'\)return <BusinessSetup\/>/);
  assert.match(boundary, /<ConfigurationProvider><AiProvider>\{children\}<\/AiProvider><\/ConfigurationProvider>/);
});

test('the landing route exists, has landing metadata and leaves the application entry and metadata unchanged', () => {
  const page = source('../src/app/landing/page.tsx');
  assert.match(page, /title: 'FinSight PK — Financial Clarity for SMEs'/);
  assert.match(page, /FinSight helps SMEs organise financial information/);
  assert.match(page, /<LandingPage \/>/);
  assert.doesNotMatch(page, /"use client"/, 'metadata requires a server page');
  const layout = source('../src/app/layout.tsx');
  assert.match(layout, /title: "FinSight — SME Financial Health Platform"/);
  assert.match(layout, /<SessionProvider><IntegrationBoundary>\{children\}<\/IntegrationBoundary><\/SessionProvider>/);
  assert.match(source('../src/app/page.tsx'), /<LiveDashboard\/>/);
});

test('the FinSight PK brand lockup is reusable, accessible and direction-stable', () => {
  const brand = source(`${marketing}BrandLockup.tsx`);
  assert.match(brand, /dir="ltr" lang="en" data-brand="finsight-pk"/);
  assert.match(brand, /FinSight\s*<span\s+data-brand-marker="pk"/);
  // PK is a small regional micro-badge attached at cap height, not a mathematical superscript.
  assert.match(brand, /text-\[0\.45em\] font-extrabold ring-1 ring-inset/, 'PK is visibly smaller than the wordmark');
  assert.match(brand, /rounded-\[4px\] px-\[4px\] py-\[2px\]/);
  assert.match(brand, /bg-\[#EEF2FF\] text-\[#3B54E6\] ring-\[#C7D2FE\]/, 'light badge tint');
  assert.match(brand, /bg-white\/10 text-\[#C7D2FE\] ring-white\/25/, 'dark badge tint');
  assert.doesNotMatch(brand, /<sup|align-super|vertical-align/);
  // Growth bars rise left to right; the last (with its dot) is the tallest.
  assert.match(brand, /h-\[38%\][\s\S]*h-\[64%\][\s\S]*flex-1 rounded-full/);
  assert.match(brand, /<span className="sr-only">FinSight PK<\/span>/);
  assert.match(brand, /const headingFont = 'var\(--font-heading\)[\s\S]*style=\{\{ fontFamily: headingFont[\s\S]*style=\{\{ fontFamily: headingFont/, 'inline font keeps the wordmark Latin in Urdu');
  // Used by the navigation and footer instead of duplicated markup.
  assert.match(source(`${marketing}LandingNav.tsx`), /<BrandLockup \/>/);
  assert.match(source(`${marketing}LandingFooter.tsx`), /<BrandLockup tone="dark" \/>/);
  assert.equal(marketingSource.match(/data-brand-marker/g).length, 1);
  // Application branding is intentionally not changed in this slice.
  assert.doesNotMatch(source('../src/components/layout/Sidebar.tsx'), /BrandLockup|FinSight PK/);
});

test('the page renders every required section once, in order, with one h1 and labelled sections', () => {
  const page = source(`${marketing}LandingPage.tsx`);
  const order = ['HeroSection', 'ProblemSection', 'WorkspaceSection', 'CapabilitiesSection', 'HowSection', 'CashSection', 'PlanningSection',
    'IntelligenceSection', 'SmesSection', 'LanguageSection', 'SecuritySection', 'FaqSection', 'AccessSection'];
  let position = -1;
  for (const name of order) {
    const next = page.indexOf(`<${name} `);
    assert.ok(next > position, name);
    position = next;
  }
  assert.ok(page.indexOf('<LandingNav ') < page.indexOf('<main id="main"'));
  assert.ok(page.indexOf('</main>') < page.indexOf('<LandingFooter '));
  assert.match(page, /href="#main"/, 'skip link');
  assert.equal(marketingSource.match(/<h1\b/g).length, 1);
  assert.equal(sections.match(/<section /g).length, order.length);
  assert.equal(sections.match(/aria-labelledby="landing-[a-z]+-title"/g).length, order.length);
});

test('every in-page link targets a rendered section and every route link is an existing application entry', () => {
  const rendered = new Set([...sections.matchAll(/id=\{LANDING_SECTIONS\.(\w+)\}/g)].map((match) => LANDING_SECTIONS[match[1]]));
  rendered.add('top').add('main');
  assert.deepEqual(new Set(Object.values(LANDING_SECTIONS)), new Set([...rendered].filter((id) => id !== 'top' && id !== 'main')));
  // Every section key referenced by navigation, footer or CTAs (template hrefs or nav item ids) must exist and be rendered.
  const linking = ['LandingNav.tsx', 'LandingFooter.tsx'].map((file) => source(`${marketing}${file}`)).join('\n')
    + [...sections.matchAll(/href=\{`#\$\{LANDING_SECTIONS\.(\w+)\}`\}/g)].map((match) => `LANDING_SECTIONS.${match[1]}`).join('\n');
  const referenced = [...linking.matchAll(/LANDING_SECTIONS\.(\w+)/g)].map((match) => LANDING_SECTIONS[match[1]]);
  const literal = [...marketingSource.matchAll(/href="#([\w-]+)"/g)].map((match) => match[1]);
  assert.ok(referenced.length >= 10);
  for (const id of [...referenced, ...literal]) assert.ok(rendered.has(id), `#${id}`);
  // Route links only lead into the existing app/auth entry; no invented legal, pricing or checkout pages.
  assert.deepEqual({ ...LANDING_LINKS }, { signIn: '/?auth=login', getStarted: '/?auth=register', app: '/' });
  assert.ok(existsSync(new URL('../src/app/page.tsx', import.meta.url)));
  assert.doesNotMatch(marketingSource, /href="\/(?!\?)/, 'no hard-coded internal route literals');
  assert.doesNotMatch(marketingSource, /privacy|terms|pricing|checkout/i);
  assert.doesNotMatch(marketingSource, /https?:\/\//, 'no external links or assets');
});

test('Get Started opens account creation in the existing auth screen without granting anything', () => {
  const auth = source('../src/components/auth/AuthScreen.tsx');
  assert.match(auth, /const searchParams = useSearchParams\(\);/);
  assert.match(auth, /const registerRequested = !reset && searchParams\.get\('auth'\) === 'register';/);
  assert.match(auth, /useState<'login' \| 'register' \| 'forgot'>\(reset \? 'forgot' : registerRequested \? 'register' : 'login'\)/);
  assert.match(auth, /if \(registerRequested\)\s+history\.replaceState\(null, '', window\.location\.pathname\);/);
  // Registration still uses the unchanged existing endpoint and returns to sign-in afterwards.
  assert.match(auth, /apiClient\('\/auth\/register', \{ method: 'POST', body: JSON\.stringify\(\{ email, password, fullName: name \}\) \}\);\s+setMode\('login'\);/);
});

test('English and Urdu landing copy have identical structure with no empty text', () => {
  const shape = (value) => Array.isArray(value) ? value.map(shape) : value && typeof value === 'object'
    ? Object.fromEntries(Object.entries(value).map(([key, inner]) => [key, shape(inner)])) : typeof value;
  assert.deepEqual(shape(landingUr), shape(landingEn));
  const strings = (value) => typeof value === 'string' ? [value] : Object.values(value).flatMap(strings);
  for (const text of [...strings(landingEn), ...strings(landingUr)]) assert.ok(text.trim().length > 0);
  assert.equal(landingCopy('en'), landingEn);
  assert.equal(landingCopy('ur'), landingUr);
  assert.equal(landingEn.hero.title, 'Turn scattered business finances into clear decisions.');
  // Machine identifiers and amounts stay identical across languages.
  assert.deepEqual(landingUr.capabilities.groups.map((group) => group.key), landingEn.capabilities.groups.map((group) => group.key));
  assert.deepEqual(landingUr.preview.items.map((item) => [item.amount, item.direction]), landingEn.preview.items.map((item) => [item.amount, item.direction]));
});

test('Urdu copy is written in Urdu script rather than English sentences', () => {
  const arabic = /[؀-ۿ]/;
  const prose = [landingUr.hero.title, landingUr.hero.body, landingUr.problem.title, landingUr.workspace.title, landingUr.cash.title,
    landingUr.planning.title, landingUr.intelligence.disclaimer, landingUr.security.title, landingUr.access.title, landingUr.hero.anchor,
    ...landingUr.planning.items.map((item) => item.body), ...landingUr.intelligence.items.map((item) => item.title),
    ...landingUr.faq.items.flatMap((item) => [item.q, item.a]), ...landingUr.how.steps.map((step) => step.body)];
  for (const text of prose) {
    assert.match(text, arabic, text);
    // Only product names/roles may remain Latin inside an Urdu sentence.
    const latin = (text.match(/[A-Za-z]+/g) || []).filter((word) => !['FinSight', 'Ask', 'Owner', 'Accountant', 'Manager', 'Viewer', 'English', 'PK'].includes(word));
    assert.deepEqual(latin, [], text);
  }
  assert.equal(landingEn.nav.language, 'اردو');
  assert.equal(landingUr.nav.language, 'English');
});

test('the copy makes no pricing, checkout, certification or overstated automation claims', () => {
  const all = JSON.stringify([landingEn, landingUr]);
  for (const forbidden of [/free trial/i, /per month|\/month|monthly fee|annual/i, /checkout|stripe|credit card|card details/i, /subscription|pricing plan/i,
    /SOC ?2|ISO ?27001|PCI|HIPAA|GDPR/i, /encrypt/i, /guarantee/i, /fully automat|automatic(ally)? (tax|accounting|bookkeeping|COGS)/i,
    /tax filing|file your tax/i, /real-time bank|live bank feed|bank feed/i, /one-click/i, /audit compliance|compliant/i, /perpetual/i, /AI (advisor|adviser)/i]) {
    assert.doesNotMatch(all, forbidden, String(forbidden));
  }
  assert.doesNotMatch(all, /\bF\d{2}\b|Phase [AB]|V\d{2}\b|dev\/|migration|contract/i, 'no internal engineering vocabulary');
  // No commerce UI anywhere on the page.
  assert.doesNotMatch(marketingSource, /<form|<input|type="password"|apiClient|fetch\(/);
  // Grounded restraint statements remain.
  assert.match(landingEn.intelligence.disclaimer, /not a substitute for professional advice/);
  assert.ok(landingEn.capabilities.groups.find((group) => group.key === 'commercial').bullets.includes('Inventory items and valuation scenarios'));
  assert.match(landingEn.security.title, /stays within your authorised workspace/);
});

test('illustrative visuals are labelled as sample figures', () => {
  const visuals = source(`${marketing}LandingVisuals.tsx`);
  assert.equal(visuals.match(/<figure /g).length, 5);
  assert.equal(visuals.match(/<SampleTag/g).length, 5);
  for (const label of [landingEn.preview.caption, landingEn.cash.chartCaption, landingEn.planning.estimateCaption, landingEn.intelligence.previewCaption]) {
    assert.match(label, /Illustrative/);
  }
  for (const label of [landingUr.preview.caption, landingUr.cash.chartCaption, landingUr.planning.estimateCaption, landingUr.intelligence.previewCaption]) {
    assert.match(label, /نمونہ/);
  }
  assert.doesNotMatch(visuals, /useSession|apiClient|useLiveResource/);
});

test('mobile navigation is an accessible disclosure that closes on Escape, link choice and desktop resize', () => {
  const nav = source(`${marketing}LandingNav.tsx`);
  assert.match(nav, /aria-expanded=\{open\}/);
  assert.match(nav, /aria-controls=\{MENU_ID\}/);
  assert.match(nav, /aria-label=\{open \? copy\.nav\.closeMenu : copy\.nav\.openMenu\}/);
  assert.match(nav, /event\.key === 'Escape'\) close\(true\)/);
  assert.match(nav, /toggleRef\.current\?\.focus\(\)/);
  assert.match(nav, /const DESKTOP_QUERY = '\(min-width: 1280px\)';/);
  assert.match(nav, /window\.matchMedia\(DESKTOP_QUERY\)/);
  assert.equal(nav.match(/onClick=\{\(\) => close\(false\)\}/g).length, 4);
  assert.match(nav, /max-h-\[calc\(100dvh-4rem\)\] overflow-y-auto/);
  for (const key of ['product', 'features', 'how', 'smes', 'security', 'faq']) assert.match(nav, new RegExp(`LANDING_SECTIONS\\.${key}`));
  // Anonymous language switching is local only and never calls the business settings API.
  assert.match(nav, /setLocale\(locale === 'en' \? 'ur' : 'en'\)/);
  assert.doesNotMatch(nav, /LanguageControl|phaseOneApi/);
});

test('landing motion is subtle and disabled for reduced-motion users', () => {
  const css = source(`${marketing}landing.module.css`);
  assert.match(css, /@media \(prefers-reduced-motion: reduce\)[\s\S]*\.rise, \.rise2, \.rise3, \.rise4, \.float, \.menu \{ animation: none !important; \}/);
  assert.match(css, /overflow-x: clip;/);
  assert.doesNotMatch(css, /@import|url\(/, 'no external assets');
});

test('the 1024px layout guards keep navigation on one line and the hero preview wide enough', () => {
  const nav = source(`${marketing}LandingNav.tsx`);
  // Section links appear only from xl, never wrap, and the disclosure menu covers everything below.
  assert.match(nav, /aria-label=\{copy\.nav\.label\} className="hidden xl:block"/);
  assert.match(nav, /px-2\.5 2xl:px-3 rounded-\[var\(--radius-lg\)\] text-\[14px\] font-medium whitespace-nowrap/);
  assert.match(nav, /className="xl:hidden inline-flex items-center justify-center w-11 h-11/);
  assert.match(nav, /<div className="hidden md:flex items-center gap-2">/, 'CTAs stay visible from md');
  const hero = sections.slice(sections.indexOf('export function HeroSection'), sections.indexOf('const problemIcons'));
  // The hero only splits into two columns at xl, so preview cards are never squeezed to ~195px.
  assert.match(hero, /xl:grid-cols-\[1\.05fr_1fr\]/);
  assert.doesNotMatch(hero, /lg:grid-cols-/);
});

test('currency tokens never wrap apart and the planned margin follows page alignment in RTL', () => {
  const visuals = source(`${marketing}LandingVisuals.tsx`);
  assert.match(visuals, /export const money = \(amount: string\) => <span dir="ltr" className="whitespace-nowrap tabular-nums">PKR \{amount\}<\/span>;/);
  assert.doesNotMatch(visuals, /PKR \{(?!amount)/, 'all amounts go through money()');
  assert.match(sections, /<span dir="ltr" className="shrink-0 whitespace-nowrap text-\[13px\] font-semibold text-\[var\(--color-health-strong\)\] tabular-nums">\+ PKR 410,000<\/span>/);
  assert.match(sections, /<span dir="ltr" className="whitespace-nowrap tabular-nums">PKR 1,842,500<\/span>/);
  // The margin block is not itself LTR; only the numeric token is.
  assert.match(visuals, /<dd data-testid="planned-margin" className="mt-1 font-heading[^"]*"><span dir="ltr" className="whitespace-nowrap tabular-nums">\{e\.marginValue\}<\/span><\/dd>/);
  assert.doesNotMatch(visuals, /dir="ltr"[^>]*text-start/);
});

test('Urdu Nastaliq text gets a landing-scoped line-height guard that English does not', () => {
  const css = source(`${marketing}landing.module.css`);
  assert.match(css, /\.root\[lang="ur"\] :is\(h1, h2, h3, p, li, summary, dt, dd\):not\(\[dir="ltr"\], \[lang="en"\] \*\) \{\s+line-height: 1\.9;/);
  // English hero keeps its tight display leading.
  assert.match(sections, /'text-\[38px\] leading-\[1\.08\] tracking-\[-0\.03em\]/);
  assert.match(source(`${marketing}LandingPage.tsx`), /lang=\{locale\} dir=\{direction\} data-landing-root className=\{cn\(styles\.root/);
});

test('there is exactly one closing CTA, after the FAQ, and the footer carries navigation only', () => {
  const footer = source(`${marketing}LandingFooter.tsx`);
  assert.doesNotMatch(footer, /CtaLink|copy\.final/);
  assert.equal('final' in landingEn, false);
  assert.equal(sections.match(/variant="onDark"/g).length, 1);
  const page = source(`${marketing}LandingPage.tsx`);
  assert.ok(page.indexOf('<FaqSection ') < page.indexOf('<AccessSection '));
  assert.ok(page.indexOf('<AccessSection ') < page.indexOf('</main>'));
  // Footer stays substantial: brand, tagline and three link groups.
  assert.match(footer, /<BrandLockup tone="dark" \/>/);
  assert.equal(footer.match(/title: f\.(explore|trust|account)/g).length, 3);
});

test('later sections tell three focused outcomes instead of repeating the capability grid', () => {
  assert.equal(landingEn.capabilities.groups.length, 6);
  assert.equal(landingEn.planning.items.length, 3);
  assert.equal(landingEn.intelligence.items.length, 3);
  assert.deepEqual(landingEn.planning.items.map((item) => item.title), ['Cost and price before quoting', 'Keep every revision', 'Move approved estimates into project planning']);
  assert.match(landingEn.planning.items[2].body, /existing project, job or order/, 'conversion targets an existing project only');
  assert.match(landingEn.intelligence.items[2].title, /where enabled/);
  assert.match(sections, /<OutcomeList items=\{p\.items\} icons=\{planningIcons\} \/>/);
  assert.match(sections, /<OutcomeList items=\{i\.items\} icons=\{intelligenceIcons\} \/>/);
});

test('the security visual shows the real role model without certification or encryption claims', () => {
  const visuals = source(`${marketing}LandingVisuals.tsx`);
  assert.match(visuals, /export function AccessVisual/);
  assert.match(sections, /<AccessVisual copy=\{copy\} \/>/);
  assert.doesNotMatch(sections, /ShieldCheck|radial-gradient\(circle_at_center/, 'placeholder glow removed');
  const roleType = source('../src/lib/api/session.ts').match(/export type Role = ([^;]+);/)[1];
  landingEn.security.roles.forEach((role) => {
    assert.ok(roleType.includes(`'${role.role.toUpperCase()}'`), `${role.role} in Role`);
  });
  assert.deepEqual(landingUr.security.roles.map((role) => role.role), landingEn.security.roles.map((role) => role.role));
  assert.match(landingEn.security.roles.find((role) => role.role === 'Viewer').access, /Read-only/);
  assert.doesNotMatch(JSON.stringify(landingEn.security), /encrypt|certif|bank-grade|military/i);
  assert.doesNotMatch(JSON.stringify(landingEn.security), /SOC ?2|ISO|PCI/);
});

test('figures expose one accessible name each without duplicate descriptions', () => {
  const visuals = source(`${marketing}LandingVisuals.tsx`);
  assert.doesNotMatch(visuals, /<figure[^>]*aria-label=/);
  assert.equal(visuals.match(/<figcaption className="sr-only">/g).length, 2, 'pictorial figures');
  assert.equal(visuals.match(/<figure aria-labelledby="lp-(estimate|insight|access)-title lp-\1-caption"/g).length, 3, 'readable figures');
  for (const id of ['lp-estimate-title', 'lp-estimate-caption', 'lp-insight-title', 'lp-insight-caption', 'lp-access-title', 'lp-access-caption']) {
    assert.equal(visuals.match(new RegExp(`id="${id}"`, 'g')).length, 1, id);
  }
  // Pictorial drawings are hidden so the caption is not repeated by their inner text.
  assert.match(visuals, /<figcaption className="sr-only">\{p\.summary\}<\/figcaption>[\s\S]*?<div aria-hidden="true" className=\{cn\('relative rounded-\[20px\]/);
  assert.match(visuals, /<figcaption className="sr-only">\{c\.chartSummary\}<\/figcaption>\s+<div aria-hidden="true">/);
});

test('the mobile menu closes on outside click and when focus leaves it, without trapping focus', () => {
  const nav = source(`${marketing}LandingNav.tsx`);
  assert.match(nav, /data-testid="landing-menu-backdrop"[\s\S]*onMouseDown=\{event => event\.preventDefault\(\)\}[\s\S]*onClick=\{\(\) => close\(true\)\}/);
  assert.match(nav, /<header ref=\{headerRef\} onBlur=\{onHeaderBlur\}/);
  assert.match(nav, /event\.relatedTarget instanceof Node && !headerRef\.current\?\.contains\(event\.relatedTarget\)\) close\(false\)/);
  assert.match(nav, /tabIndex=\{-1\}\s+data-testid="landing-mobile-menu"/);
  assert.doesNotMatch(nav, /role="dialog"|aria-modal|focus-trap/);
});

test('the identity probe defers anonymous public routes and probes private routes or a session hint', () => {
  const provider = source('../src/components/auth/SessionProvider.tsx');
  assert.match(provider, /const publicRoute = isPublicRoute\(usePathname\(\)\);/);
  assert.match(provider, /const hasSessionHint = typeof document !== 'undefined' && document\.cookie\.includes\('finsight_session_hint=1'\);/);
  assert.match(provider, /if \(isDemoMode \|\| \(publicRoute && !hasSessionHint\) \|\| probed\.current\)\s+return;\s+probed\.current = true;\s+queueMicrotask\(\(\) => void refresh\(\)\);\s+\}, \[publicRoute, refresh\]\);/);
  // The probe used to run unconditionally from the listener effect; it is now only in the route-aware effect.
  assert.equal(provider.match(/queueMicrotask\(/g).length, 1);
  assert.match(provider, /revision\.current\+\+; probed\.current = false;/, 'remount (StrictMode) probes again');
  // Initial status stays loading (grants nothing); IntegrationBoundary still blocks private routes until the probe resolves.
  assert.match(provider, /const initial: State = \{ user: null, business: null, businesses: \[\], status: 'loading'/);
  const boundary = source('../src/components/layout/IntegrationBoundary.tsx');
  assert.match(boundary, /if\(session\.status==='loading'\)return <main className="p-10" role="status">/);
  // The same exact allow-list drives both the probe deferral and the boundary exemption.
  assert.deepEqual([...PUBLIC_ROUTES].sort(), ['/', '/landing'].sort());
  for (const pathname of ['/records', '/landing/x', '/settings']) assert.equal(isPublicRoute(pathname), false);
});

test('a single subtle Pakistan-market anchor sits in the hero', () => {
  const all = JSON.stringify(landingEn);
  assert.equal(all.match(/Pakistan/g).length, 1);
  assert.equal(landingEn.hero.anchor, 'Built with the realities of Pakistani SMEs in mind.');
  assert.match(sections, /\{h\.anchor\}/);
});

test('the workspace chips use an even two-column grid on small screens', () => {
  assert.match(sections, /data-testid="workspace-inputs" className="mt-4 grid grid-cols-2 gap-2\.5 lg:flex lg:flex-col"/);
  assert.match(sections, /index === w\.inputs\.length - 1 && 'col-span-2'/);
});
