// Real-browser acceptance for the public FinSight PK landing page (/landing).
// Uses the production Next build on FRONTEND_TEST_ORIGIN (default http://127.0.0.1:3100) whose /api rewrite points at a
// synthetic session fixture on 127.0.0.1:8080. It does not test Spring, PostgreSQL, email, OCR or AI providers.
import http from 'node:http';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync } from 'node:fs';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const out = process.env.BROWSER_ARTIFACT_DIR || 'test-artifacts/browser-landing';
mkdirSync(out, { recursive: true });
const origin = process.env.FRONTEND_TEST_ORIGIN || 'http://127.0.0.1:3100';
const backendPort = Number(process.env.FINSIGHT_BROWSER_BACKEND_PORT || 8080);
const businessId = '11111111-1111-4111-8111-111111111111';

// ---- Synthetic session fixture: anonymous by default; the private-route checks switch it explicitly. ----
let mode = 'anonymous';
const apiCalls = [];
const json = (res, body, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(body)); };
const user = { id: 'synthetic-user', email: 'owner@example.invalid', fullName: 'Synthetic Owner', authStage: 'FULLY_AUTHENTICATED', mustChangePassword: false, accountStatus: 'ACTIVE', platformRole: null };
const business = { businessId, businessName: 'Synthetic landing fixture', businessType: 'trade', languagePreference: 'en', role: 'OWNER', membershipStatus: 'ACTIVE', active: true };
const server = http.createServer((req, res) => {
  const url = req.url.split('?')[0];
  apiCalls.push({ mode, method: req.method, url });
  if (url === '/api/auth/csrf') return json(res, { token: 'fixture-csrf', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' });
  if (url === '/api/auth/me') return mode === 'anonymous' ? json(res, { message: 'Sign in required', error: 'unauthorized' }, 401) : json(res, user);
  if (url === '/api/businesses') return json(res, mode === 'business' ? [business] : []);
  if (url === '/api/businesses/active') return mode === 'business' ? json(res, business) : json(res, { message: 'No active business' }, 404);
  if (mode === 'anonymous') return json(res, { message: 'Sign in required' }, 401);
  return json(res, { message: 'Not provided by the landing fixture' }, 404);
});
await new Promise((resolve, reject) => { server.once('error', reject); server.listen(backendPort, '127.0.0.1', resolve); });

const results = [];
let step = '';
const check = async (name, fn) => {
  step = '';
  try { await fn(); results.push({ name, ok: true }); console.log(`ok - ${name}`); }
  catch (error) { results.push({ name, ok: false, step, error: String(error?.stack || error) }); console.log(`not ok - ${name} [step: ${step}]\n  ${String(error?.message || error).split('\n').join('\n  ')}`); }
};

const viewports = [
  { name: 'desktop-1440', width: 1440, height: 900 },
  { name: 'laptop-1024', width: 1024, height: 768 },
  { name: 'mobile-390x844', width: 390, height: 844 },
  { name: 'mobile-390x600', width: 390, height: 600 },
  { name: 'mobile-360x800', width: 360, height: 800 },
];
const sectionIds = ['product', 'features', 'how-it-works', 'cash', 'planning', 'intelligence', 'for-smes', 'language', 'security', 'access', 'faq'];
const heroTitle = { en: 'Turn scattered business finances into clear decisions.', ur: 'بکھرے ہوئے کاروباری مالیات کو واضح فیصلوں میں بدلیں۔' };
const allowedRoutes = new Set(['/', '/?auth=register']);

const browser = await chromium.launch({ headless: true, channel: process.env.FINSIGHT_BROWSER_CHANNEL || 'msedge' });

async function openContext(viewport, locale, extra = {}) {
  const context = await browser.newContext({ viewport: { width: viewport.width, height: viewport.height }, ...extra });
  // Seed the starting language once per tab, so a reload keeps whatever the visitor chose on the page.
  await context.addInitScript((value) => { try { if (!sessionStorage.getItem('landing-seeded')) { localStorage.setItem('finsight_locale', value); sessionStorage.setItem('landing-seeded', '1'); } } catch { /* storage unavailable */ } }, locale);
  const page = await context.newPage();
  const log = { console: [], pageErrors: [], external: [], failed: [] };
  page.on('console', (message) => {
    if (message.type() === 'error' || message.type() === 'warning') log.console.push({ type: message.type(), text: message.text(), url: message.location()?.url || '' });
  });
  page.on('pageerror', (error) => log.pageErrors.push(String(error)));
  page.on('request', (request) => { if (!request.url().startsWith(origin) && !request.url().startsWith('data:') && !request.url().startsWith('blob:')) log.external.push(request.url()); });
  page.on('requestfailed', (request) => log.failed.push(`${request.url()} ${request.failure()?.errorText}`));
  return { context, page, log };
}

// Tolerated, pre-existing and app-wide (recorded in results.json, not caused by this feature):
// the anonymous SessionProvider probe (/api/auth/me -> 401) and the browser's automatic /favicon.ico request (the app ships no favicon).
const knownConsole = [];
function featureConsoleErrors(log) {
  return log.console.filter((entry) => {
    const known = entry.type === 'error' && ((/status of 401/.test(entry.text) && /\/api\/auth\/me$/.test(entry.url)) || (/status of 404/.test(entry.text) && /\/favicon\.ico$/.test(entry.url)));
    if (known) knownConsole.push(entry.url);
    return !known;
  });
}

function assertCleanLog(log, label) {
  assert.deepEqual(log.pageErrors, [], `${label}: page errors`);
  assert.deepEqual(log.external, [], `${label}: external requests`);
  const bad = featureConsoleErrors(log);
  assert.deepEqual(bad, [], `${label}: console errors/warnings`);
  assert.ok(!log.console.some((entry) => /hydrat|Content Security Policy|Refused to/i.test(entry.text)), `${label}: hydration/CSP`);
}

async function noHorizontalOverflow(page, label) {
  const metrics = await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, client: document.documentElement.clientWidth, body: document.body.scrollWidth }));
  assert.ok(metrics.scroll <= metrics.client && metrics.body <= metrics.client, `${label}: horizontal overflow ${JSON.stringify(metrics)}`);
  // Any element whose box escapes the viewport horizontally (decorative aria-hidden glows are clipped by the root and excluded).
  const offenders = await page.evaluate(() => {
    const width = document.documentElement.clientWidth;
    return [...document.querySelectorAll('[data-landing-root] *')].filter((element) => {
      if (element.closest('[aria-hidden="true"]') || element.closest('.sr-only')) return false;
      const box = element.getBoundingClientRect();
      return box.width > 0 && (box.right > width + 1 || box.left < -1);
    }).slice(0, 5).map((element) => `${element.tagName}.${String(element.className).slice(0, 60)}`);
  });
  assert.deepEqual(offenders, [], `${label}: elements outside viewport`);
}

async function brandGeometry(page, label) {
  const scope = page.locator('header [data-brand="finsight-pk"]').first();
  await scope.waitFor({ state: 'visible' });
  const geometry = await scope.evaluate((root) => {
    const marker = root.querySelector('[data-brand-marker="pk"]');
    const word = marker.parentElement;
    const range = document.createRange();
    range.setStart(word.firstChild, 0); range.setEnd(word.firstChild, word.firstChild.textContent.length);
    const wordBox = range.getBoundingClientRect(), markerBox = marker.getBoundingClientRect();
    return {
      dir: getComputedStyle(root).direction, wordFont: parseFloat(getComputedStyle(word).fontSize), markerFont: parseFloat(getComputedStyle(marker).fontSize),
      family: getComputedStyle(word).fontFamily, wordLeft: wordBox.left, wordRight: wordBox.right, wordTop: wordBox.top, wordBottom: wordBox.bottom,
      markerLeft: markerBox.left, markerTop: markerBox.top, markerBottom: markerBox.bottom, name: root.textContent,
    };
  });
  assert.equal(geometry.dir, 'ltr', `${label}: wordmark direction`);
  assert.ok(geometry.markerFont / geometry.wordFont < 0.5, `${label}: PK smaller ${JSON.stringify(geometry)}`);
  assert.ok(geometry.markerLeft >= geometry.wordRight - 1, `${label}: PK sits after FinSight ${JSON.stringify(geometry)}`);
  assert.ok(geometry.markerTop <= geometry.wordTop + (geometry.wordBottom - geometry.wordTop) * 0.25, `${label}: PK is superscript ${JSON.stringify(geometry)}`);
  assert.ok(geometry.markerBottom < (geometry.wordTop + geometry.wordBottom) / 2 + 1, `${label}: PK above the wordmark midline`);
  assert.doesNotMatch(geometry.family, /Nastaliq/i, `${label}: wordmark keeps Latin type`);
  assert.match(geometry.name, /FinSight PK/);
}

async function linksResolve(page, label) {
  const links = await page.$$eval('a[href]', (anchors) => anchors.map((anchor) => anchor.getAttribute('href')));
  assert.ok(links.length >= 20, `${label}: link count ${links.length}`);
  for (const href of links) {
    if (href.startsWith('#')) assert.ok(await page.locator(`[id="${href.slice(1)}"]`).count() === 1, `${label}: dead anchor ${href}`);
    else assert.ok(allowedRoutes.has(href), `${label}: unexpected route ${href}`);
  }
}

// ---- 1. Anonymous landing at every viewport, in English and Urdu ----
for (const locale of ['en', 'ur']) {
  for (const viewport of viewports) {
    const label = `${viewport.name}-${locale}`;
    await check(`anonymous /landing renders cleanly (${label})`, async () => {
      mode = 'anonymous';
      const { context, page, log } = await openContext(viewport, locale);
      try {
        const response = await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
        assert.equal(response.status(), 200);
        await page.waitForFunction((title) => document.querySelector('h1')?.textContent === title, heroTitle[locale]);
        assert.equal(await page.title(), 'FinSight PK — Financial Clarity for SMEs');
        assert.match(await page.locator('meta[name="description"]').getAttribute('content'), /FinSight helps SMEs/);
        assert.equal(await page.evaluate(() => document.documentElement.dir), locale === 'ur' ? 'rtl' : 'ltr');
        assert.equal(await page.evaluate(() => document.documentElement.lang), locale);
        assert.equal(await page.locator('h1').count(), 1);
        assert.equal(await page.locator('main#main').count(), 1);
        assert.equal(await page.locator('header').count(), 1);
        assert.equal(await page.locator('footer').count(), 1);
        for (const id of sectionIds) assert.equal(await page.locator(`section#${id}`).count(), 1, id);
        // No authenticated chrome, business setup or auth form on the public page.
        assert.equal(await page.locator('aside[aria-label="Primary Navigation"]').count(), 0);
        assert.equal(await page.locator('form, input').count(), 0);
        await brandGeometry(page, label);
        await linksResolve(page, label);
        await noHorizontalOverflow(page, label);
        const mobile = viewport.width < 1024;
        assert.equal(await page.getByRole('button', { name: locale === 'ur' ? 'مینو کھولیں' : 'Open menu' }).isVisible(), mobile, `${label}: menu toggle`);
        assert.equal(await page.locator('header nav').first().isVisible(), !mobile, `${label}: desktop nav`);
        // Hero CTAs remain usable (>=44px) and on screen.
        for (const name of locale === 'ur' ? ['شروع کریں', 'FinSight کیسے کام کرتا ہے، دیکھیں'] : ['Get Started', 'See how FinSight works']) {
          const box = await page.locator('section[aria-labelledby="landing-hero-title"]').getByRole('link', { name, exact: true }).boundingBox();
          assert.ok(box && box.height >= 44 && box.x >= 0 && box.x + box.width <= viewport.width + 1, `${label}: CTA ${name} ${JSON.stringify(box)}`);
        }
        await page.screenshot({ path: `${out}/${label}-viewport.png` });
        await page.screenshot({ path: `${out}/${label}-full.png`, fullPage: true });
        assertCleanLog(log, label);
      } finally { await context.close(); }
    });
  }
}

// ---- 2. Desktop anchor navigation, keyboard focus and language toggle ----
await check('desktop anchors scroll each section below the sticky header and keyboard focus is visible', async () => {
  mode = 'anonymous';
  const { context, page, log } = await openContext(viewports[0], 'en');
  try {
    await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
    await page.keyboard.press('Tab');
    const skip = page.getByRole('link', { name: 'Skip to main content' });
    assert.ok(await skip.isVisible(), 'skip link appears on focus');
    for (const [name, id] of [['Product', 'product'], ['Features', 'features'], ['How It Works', 'how-it-works'], ['For SMEs', 'for-smes'], ['Security', 'security'], ['FAQ', 'faq']]) {
      step = `anchor ${id}`;
      await page.locator('header nav').getByRole('link', { name, exact: true }).click();
      await page.waitForFunction((hash) => location.hash === hash, `#${id}`);
      await page.waitForTimeout(150);
      const top = await page.locator(`section#${id}`).evaluate((element) => element.getBoundingClientRect().top);
      const header = await page.locator('header').evaluate((element) => element.getBoundingClientRect().bottom);
      assert.ok(top >= header - 2 && top <= header + 80, `${id}: top ${top}, header ${header}`);
    }
    step = 'focus outline';
    const navLink = page.locator('header nav a').first();
    await navLink.focus();
    await page.keyboard.press('Shift+Tab'); await page.keyboard.press('Tab');
    const outline = await page.evaluate(() => { const style = getComputedStyle(document.activeElement); return { style: style.outlineStyle, width: style.outlineWidth }; });
    assert.notEqual(outline.style, 'none'); assert.notEqual(outline.width, '0px');
    // "See how FinSight works" lands on the how-it-works section.
    await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
    step = 'hero secondary CTA';
    await page.getByRole('link', { name: 'See how FinSight works' }).click();
    await page.waitForFunction(() => location.hash === '#how-it-works');
    // FAQ disclosures open with the keyboard.
    step = 'faq disclosure';
    const summary = page.locator('#faq summary').first();
    await summary.focus(); await page.keyboard.press('Enter');
    assert.equal(await page.locator('#faq details').first().getAttribute('open'), '');
    // Language toggle switches to RTL Urdu, persists across reload, and switches back.
    step = 'language toggle';
    await page.getByRole('button', { name: 'Switch to Urdu' }).click();
    await page.waitForFunction(() => document.documentElement.dir === 'rtl');
    assert.equal(await page.locator('h1').textContent(), heroTitle.ur);
    step = 'language persists';
    await page.reload({ waitUntil: 'networkidle' });
    await page.waitForFunction(() => document.documentElement.dir === 'rtl' && document.querySelector('[data-landing-root]')?.getAttribute('dir') === 'rtl');
    step = 'language section switch';
    await page.locator('#language').getByRole('button').click();
    await page.waitForFunction(() => document.documentElement.dir === 'ltr');
    assert.equal(await page.locator('h1').textContent(), heroTitle.en);
    assertCleanLog(log, 'desktop-anchors');
  } finally { await context.close(); }
});

// ---- 3. Mobile menu behaviour (EN + UR, short and standard heights) ----
for (const [viewport, locale] of [[viewports[2], 'en'], [viewports[3], 'ur'], [viewports[4], 'en'], [viewports[4], 'ur']]) {
  const label = `${viewport.name}-${locale}`;
  await check(`mobile menu opens, traps nothing, closes on Escape and link choice (${label})`, async () => {
    mode = 'anonymous';
    const { context, page, log } = await openContext(viewport, locale);
    const t = locale === 'ur' ? { open: 'مینو کھولیں', close: 'مینو بند کریں', faq: 'سوالات', lang: 'انگریزی میں دیکھیں' } : { open: 'Open menu', close: 'Close menu', faq: 'FAQ', lang: 'Switch to Urdu' };
    try {
      await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
      const toggle = page.getByRole('button', { name: t.open });
      assert.equal(await toggle.getAttribute('aria-expanded'), 'false');
      await toggle.click();
      const menu = page.getByTestId('landing-mobile-menu');
      await menu.waitFor({ state: 'visible' });
      assert.equal(await page.getByRole('button', { name: t.close }).getAttribute('aria-expanded'), 'true');
      assert.equal(await page.getByRole('button', { name: t.close }).getAttribute('aria-controls'), 'landing-mobile-menu');
      assert.equal(await page.evaluate(() => document.activeElement?.closest('[data-testid="landing-mobile-menu"]') !== null), true, 'focus moves into menu');
      const box = await menu.boundingBox();
      assert.ok(box.y + box.height <= viewport.height + 1 && box.width <= viewport.width + 1, `menu fits ${JSON.stringify(box)}`);
      // Every action reachable inside the (scrollable) panel.
      for (const link of await menu.locator('a, button').all()) { await link.scrollIntoViewIfNeeded(); assert.ok(await link.isVisible()); }
      await page.screenshot({ path: `${out}/${label}-menu-open.png` });
      await noHorizontalOverflow(page, `${label}-menu`);
      await page.keyboard.press('Escape');
      await menu.waitFor({ state: 'detached' });
      assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('aria-controls')), 'landing-mobile-menu', 'focus returns to toggle');
      await page.getByRole('button', { name: t.open }).click();
      await menu.getByRole('link', { name: t.faq, exact: true }).click();
      await menu.waitFor({ state: 'detached' });
      await page.waitForFunction(() => location.hash === '#faq');
      // Language toggle from the menu flips direction without closing the page state.
      await page.getByRole('button', { name: t.open }).click();
      await menu.getByRole('button', { name: t.lang }).click();
      await page.waitForFunction((dir) => document.documentElement.dir === dir, locale === 'ur' ? 'ltr' : 'rtl');
      await noHorizontalOverflow(page, `${label}-after-toggle`);
      assertCleanLog(log, label);
    } finally { await context.close(); }
  });
}

// ---- 4. CTA destinations lead into the existing auth experience ----
await check('Get Started opens account creation and Sign In opens sign-in in the existing auth screen', async () => {
  mode = 'anonymous';
  const { context, page, log } = await openContext(viewports[0], 'en');
  try {
    await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
    await page.locator('section[aria-labelledby="landing-hero-title"]').getByRole('link', { name: 'Get Started' }).click();
    await page.getByRole('heading', { level: 1, name: 'Create your account' }).waitFor();
    await page.waitForFunction(() => location.pathname === '/' && location.search === '');
    assert.equal(await page.getByLabel('Full name').count() + await page.locator('input[autocomplete="name"]').count() > 0, true);
    await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
    await page.locator('header').getByRole('link', { name: 'Sign In' }).click();
    await page.getByRole('heading', { level: 1, name: 'Sign in to FinSight' }).waitFor();
    // Footer and access-section CTAs share the same destinations.
    await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
    assert.deepEqual(await page.locator('footer a[href^="/"]').evaluateAll((anchors) => anchors.map((a) => a.getAttribute('href'))), ['/?auth=register', '/', '/', '/?auth=register']);
    assert.deepEqual(await page.locator('#access a').evaluateAll((anchors) => anchors.map((a) => a.getAttribute('href'))), ['/?auth=register', '/']);
    assertCleanLog(log, 'cta');
  } finally { await context.close(); }
});

// ---- 5. Private routes stay private; the public page never mounts the business tree ----
await check('private routes remain protected exactly as before', async () => {
  const { context, page, log } = await openContext(viewports[0], 'en');
  try {
    mode = 'anonymous';
    for (const route of ['/', '/records', '/settings', '/reports', '/landing/extra']) {
      await page.goto(`${origin}${route}`, { waitUntil: 'networkidle' });
      if (route === '/landing/extra') {
        assert.equal(await page.locator('[data-landing-root]').count(), 0, 'nested landing path is not public');
      }
      await page.getByRole('heading', { level: 1, name: 'Sign in to FinSight' }).waitFor({ timeout: 10000 });
      assert.equal(await page.locator('aside[aria-label="Primary Navigation"]').count(), 0, route);
    }
    // Authenticated without a business: private routes require business setup, /landing stays public.
    mode = 'no-business';
    await page.goto(`${origin}/`, { waitUntil: 'networkidle' });
    await page.getByRole('heading', { name: 'Create business' }).waitFor();
    await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
    await page.locator('h1').filter({ hasText: heroTitle.en }).waitFor();
    assert.equal(await page.locator('form').count(), 0);
    // Authenticated with an active business: the private shell mounts at /, and /landing offers the app instead of sign-in.
    mode = 'business';
    await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
    await page.locator('header').getByRole('link', { name: 'Open FinSight' }).waitFor();
    assert.equal(await page.locator('header').getByRole('link', { name: 'Sign In' }).count(), 0);
    const landingCalls = apiCalls.filter((call) => call.mode === 'business').map((call) => call.url);
    assert.deepEqual([...new Set(landingCalls)].sort(), ['/api/auth/me', '/api/businesses', '/api/businesses/active'], 'public page calls only the session probe');
    await page.locator('header').getByRole('link', { name: 'Open FinSight' }).click();
    await page.locator('aside[aria-label="Primary Navigation"]').waitFor({ timeout: 15000 });
    // Console noise from the private dashboard against this minimal fixture is outside the landing scope.
    assert.deepEqual(log.pageErrors.filter((error) => /hydrat/i.test(error)), []);
    assert.deepEqual(log.external, []);
  } finally { await context.close(); mode = 'anonymous'; }
});

// ---- 6. Reduced motion ----
await check('reduced-motion users get no landing animation', async () => {
  mode = 'anonymous';
  const { context, page, log } = await openContext(viewports[0], 'en', { reducedMotion: 'reduce' });
  try {
    await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
    const animations = await page.evaluate(() => [...document.querySelectorAll('[data-landing-root] *')].filter((element) => {
      const style = getComputedStyle(element);
      return style.animationName !== 'none' && parseFloat(style.animationDuration) > 0.01 && !element.closest('[aria-hidden="true"]');
    }).length);
    assert.equal(animations, 0);
    assertCleanLog(log, 'reduced-motion');
  } finally { await context.close(); }
});

await browser.close();
await new Promise((resolve) => server.close(resolve));
const toleratedConsole = Object.fromEntries([...new Set(knownConsole)].map((url) => [url, knownConsole.filter((entry) => entry === url).length]));
const summary = { origin, artifacts: out, total: results.length, passed: results.filter((r) => r.ok).length, failed: results.filter((r) => !r.ok).length, toleratedConsole, results };
writeFileSync(`${out}/results.json`, JSON.stringify(summary, null, 2));
console.log(`\n${summary.passed}/${summary.total} landing browser checks passed`);
process.exit(summary.failed ? 1 : 0);
