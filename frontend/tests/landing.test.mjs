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

test('only the exact /landing path is public', () => {
  assert.deepEqual([...PUBLIC_ROUTES], ['/landing']);
  assert.equal(isPublicRoute('/landing'), true);
  for (const pathname of ['/', '/landing/', '/landing/admin', '/landingx', '/LANDING', '/records', '/settings', '/onboarding', '/reset-password', '', null, undefined]) {
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
  assert.match(brand, /text-\[0\.4em\]/, 'PK is visibly smaller than the wordmark');
  assert.match(brand, /<span className="sr-only">FinSight PK<\/span>/);
  assert.match(brand, /fontFamily: 'var\(--font-heading\)/, 'inline font keeps the wordmark Latin in Urdu');
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
    'IntelligenceSection', 'SmesSection', 'LanguageSection', 'SecuritySection', 'AccessSection', 'FaqSection'];
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
  assert.deepEqual({ ...LANDING_LINKS }, { signIn: '/', getStarted: '/?auth=register', app: '/' });
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
    landingUr.planning.title, landingUr.intelligence.disclaimer, landingUr.security.title, landingUr.access.title, landingUr.final.title,
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
  assert.match(landingEn.planning.items.at(-1).body, /explore valuation scenarios/);
  assert.match(landingEn.security.title, /stays within your authorised workspace/);
});

test('illustrative visuals are labelled as sample figures', () => {
  const visuals = source(`${marketing}LandingVisuals.tsx`);
  assert.equal(visuals.match(/<figure /g).length, 4);
  assert.equal(visuals.match(/<SampleTag/g).length, 4);
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
  assert.match(nav, /window\.matchMedia\('\(min-width: 1024px\)'\)/);
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
