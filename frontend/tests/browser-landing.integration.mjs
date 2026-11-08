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
const only = process.env.LANDING_ONLY ? new RegExp(process.env.LANDING_ONLY) : null;
const check = async (name, fn) => {
  if (only && !only.test(name)) return;
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
const DESKTOP_NAV_MIN = 1280;
const sectionIds = ['product', 'features', 'how-it-works', 'cash', 'planning', 'intelligence', 'for-smes', 'language', 'security', 'faq', 'access'];
const heroTitle = { en: 'Turn scattered business finances into clear decisions.', ur: 'بکھرے ہوئے کاروباری مالیات کو واضح فیصلوں میں بدلیں۔' };
const allowedRoutes = new Set(['/', '/?auth=register']);
const menuText = {
  en: { open: 'Open menu', close: 'Close menu', faq: 'FAQ', lang: 'Switch to Urdu' },
  ur: { open: 'مینو کھولیں', close: 'مینو بند کریں', faq: 'سوالات', lang: 'انگریزی میں دیکھیں' },
};

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

// The only tolerated console error on the public page is the browser's automatic /favicon.ico request: the app ships
// no favicon (pre-existing, app-wide). The anonymous /api/auth/me 401 is no longer tolerated on /landing: the probe
// is deferred there. Private-route checks, which still probe, pass allowSessionProbe.
const knownConsole = [];
function featureConsoleErrors(log, allowSessionProbe) {
  return log.console.filter((entry) => {
    const favicon = entry.type === 'error' && /status of 404/.test(entry.text) && /\/favicon\.ico$/.test(entry.url);
    const probe = allowSessionProbe && entry.type === 'error' && /status of 401/.test(entry.text) && /\/api\/auth\/me$/.test(entry.url);
    if (favicon || probe) knownConsole.push(entry.url);
    return !favicon && !probe;
  });
}

function assertCleanLog(log, label, allowSessionProbe = false) {
  assert.deepEqual(log.pageErrors, [], `${label}: page errors`);
  assert.deepEqual(log.external, [], `${label}: external requests`);
  assert.deepEqual(featureConsoleErrors(log, allowSessionProbe), [], `${label}: console errors/warnings`);
  assert.ok(!log.console.some((entry) => /hydrat|Content Security Policy|Refused to/i.test(entry.text)), `${label}: hydration/CSP`);
}

async function noHorizontalOverflow(page, label) {
  const metrics = await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, client: document.documentElement.clientWidth, body: document.body.scrollWidth }));
  assert.ok(metrics.scroll <= metrics.client && metrics.body <= metrics.client, `${label}: horizontal overflow ${JSON.stringify(metrics)}`);
  // Any element whose box escapes the viewport horizontally (decorative glows are clipped by the root and excluded).
  const offenders = await page.evaluate(() => {
    const width = document.documentElement.clientWidth;
    return [...document.querySelectorAll('[data-landing-root] *')].filter((element) => {
      if (element.closest('.sr-only') || element.matches('[class*="blur"]') || element.closest('[class*="blur-2xl"]')) return false;
      const box = element.getBoundingClientRect();
      return box.width > 0 && (box.right > width + 1 || box.left < -1);
    }).slice(0, 5).map((element) => `${element.tagName}.${String(element.className).slice(0, 60)}`);
  });
  assert.deepEqual(offenders, [], `${label}: elements outside viewport`);
}

async function brandGeometry(page, label) {
  for (const scope of await page.locator('[data-brand="finsight-pk"]').all()) {
    if (!(await scope.isVisible())) continue;
    const geometry = await scope.evaluate((root) => {
      const marker = root.querySelector('[data-brand-marker="pk"]');
      const word = marker.parentElement;
      const range = document.createRange();
      range.setStart(word.firstChild, 0); range.setEnd(word.firstChild, word.firstChild.textContent.length);
      const wordBox = range.getBoundingClientRect(), markerBox = marker.getBoundingClientRect();
      const markerStyle = getComputedStyle(marker);
      const bars = [...root.querySelectorAll('[data-brand-bar]')].map((bar) => bar.getBoundingClientRect().height);
      return {
        dir: getComputedStyle(root).direction, wordFont: parseFloat(getComputedStyle(word).fontSize), markerFont: parseFloat(markerStyle.fontSize),
        radius: parseFloat(markerStyle.borderTopLeftRadius), background: markerStyle.backgroundColor, paddingX: parseFloat(markerStyle.paddingLeft),
        family: getComputedStyle(word).fontFamily, wordRight: wordBox.right, wordTop: wordBox.top, wordBottom: wordBox.bottom,
        markerLeft: markerBox.left, markerTop: markerBox.top, markerBottom: markerBox.bottom, markerHeight: markerBox.height, name: root.textContent, bars,
      };
    });
    const wordHeight = geometry.wordBottom - geometry.wordTop;
    assert.equal(geometry.dir, 'ltr', `${label}: wordmark direction`);
    assert.ok(geometry.markerFont / geometry.wordFont <= 0.46, `${label}: PK smaller ${JSON.stringify(geometry)}`);
    assert.ok(geometry.markerLeft >= geometry.wordRight - 1 && geometry.markerLeft - geometry.wordRight < 8, `${label}: PK attached after FinSight ${JSON.stringify(geometry)}`);
    assert.ok(geometry.markerTop <= geometry.wordTop + wordHeight * 0.25, `${label}: PK sits at cap height ${JSON.stringify(geometry)}`);
    assert.ok((geometry.markerTop + geometry.markerBottom) / 2 < (geometry.wordTop + geometry.wordBottom) / 2, `${label}: PK centred above the midline`);
    assert.ok(geometry.markerHeight < wordHeight * 0.8, `${label}: PK badge not dominant`);
    assert.ok(geometry.radius >= 3 && geometry.paddingX >= 3 && geometry.background !== 'rgba(0, 0, 0, 0)', `${label}: PK is a tinted micro-badge ${JSON.stringify(geometry)}`);
    assert.ok(geometry.bars[0] < geometry.bars[1] && geometry.bars[1] < geometry.bars[2], `${label}: growth bars rise ${geometry.bars}`);
    assert.doesNotMatch(geometry.family, /Nastaliq/i, `${label}: wordmark keeps Latin type`);
    assert.match(geometry.name, /FinSight PK/);
  }
}

async function linksResolve(page, label) {
  const links = await page.$$eval('a[href]', (anchors) => anchors.map((anchor) => anchor.getAttribute('href')));
  assert.ok(links.length >= 20, `${label}: link count ${links.length}`);
  for (const href of links) {
    if (href.startsWith('#')) assert.ok(await page.locator(`[id="${href.slice(1)}"]`).count() === 1, `${label}: dead anchor ${href}`);
    else assert.ok(allowedRoutes.has(href), `${label}: unexpected route ${href}`);
  }
}

// Text, amount and Urdu typography measurements taken from the rendered layout.
async function layoutQuality(page, label, locale) {
  const report = await page.evaluate((ur) => {
    // Counts rendered text lines from text nodes only (icons excluded); boxes on one line may differ slightly in top
    // because Latin and Nastaliq glyph boxes differ, so a new line starts only past ~60% of the font size.
    const lines = (element) => {
      const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
      const tops = [];
      for (let node = walker.nextNode(); node; node = walker.nextNode()) {
        if (!node.textContent.trim()) continue;
        const range = document.createRange(); range.selectNodeContents(node);
        for (const rect of range.getClientRects()) if (rect.width > 0) tops.push(rect.top);
      }
      const tolerance = parseFloat(getComputedStyle(element).fontSize) * 0.6;
      return tops.sort((a, b) => a - b).reduce((count, top, index) => (index && top - tops[index - 1] > tolerance ? count + 1 : count), tops.length ? 1 : 0);
    };
    const overlap = (a, b) => a.left < b.right - 0.5 && b.left < a.right - 0.5 && a.top < b.bottom - 0.5 && b.top < a.bottom - 0.5;
    const visible = (element) => { const box = element.getBoundingClientRect(); return box.width > 0 && box.height > 0; };
    const problems = [];
    for (const link of document.querySelectorAll('header nav a, header a[href^="/"], header button')) {
      if (visible(link) && lines(link) > 1) problems.push(`wrapped header item: ${link.textContent.trim()}`);
    }
    for (const token of document.querySelectorAll('[data-landing-root] span.whitespace-nowrap.tabular-nums')) {
      if (visible(token) && lines(token) > 1) problems.push(`amount wrapped: ${token.textContent}`);
    }
    for (const row of document.querySelectorAll('[data-testid="hero-obligation"]')) {
      const label = row.querySelector('[data-part="label"]').getBoundingClientRect(), amount = row.querySelector('[data-part="amount"]').getBoundingClientRect();
      if (overlap(label, amount)) problems.push(`obligation collision: ${row.textContent}`);
    }
    for (const card of document.querySelectorAll('[data-testid="hero-preview-grid"] > div')) {
      if (card.getBoundingClientRect().width < 230) problems.push(`preview card too narrow: ${Math.round(card.getBoundingClientRect().width)}px`);
    }
    const margin = document.querySelector('[data-testid="planned-margin"]');
    const marginBox = margin.getBoundingClientRect(), marginNumber = margin.firstElementChild.getBoundingClientRect();
    const marginAligned = ur ? Math.abs(marginBox.right - marginNumber.right) < 2 : Math.abs(marginBox.left - marginNumber.left) < 2;
    if (!marginAligned) problems.push(`planned margin misaligned: box ${marginBox.left}-${marginBox.right}, number ${marginNumber.left}-${marginNumber.right}`);
    let minimumUrduRatio = null;
    if (ur) {
      for (const element of document.querySelectorAll('[data-landing-root] :is(h1, h2, h3, p, li, summary, dt, dd)')) {
        if (!visible(element) || element.closest('[dir="ltr"], [lang="en"], .sr-only')) continue;
        const style = getComputedStyle(element);
        const ratio = parseFloat(style.lineHeight) / parseFloat(style.fontSize);
        if (!Number.isFinite(ratio)) continue;
        minimumUrduRatio = minimumUrduRatio === null ? ratio : Math.min(minimumUrduRatio, ratio);
        if (ratio < 1.8) problems.push(`tight Urdu line-height ${ratio.toFixed(2)} on ${element.tagName}: ${element.textContent.trim().slice(0, 40)}`);
      }
    } else {
      const h1 = document.querySelector('h1'), style = getComputedStyle(h1);
      if (parseFloat(style.lineHeight) / parseFloat(style.fontSize) > 1.2) problems.push('English hero leading became loose');
    }
    return { problems: problems.slice(0, 8), minimumUrduRatio };
  }, locale === 'ur');
  assert.deepEqual(report.problems, [], `${label}: layout quality`);
  return report;
}

const measurements = {};

// ---- 1. Anonymous landing at every viewport, in English and Urdu ----
for (const locale of ['en', 'ur']) {
  for (const viewport of viewports) {
    const label = `${viewport.name}-${locale}`;
    await check(`anonymous /landing renders cleanly (${label})`, async () => {
      mode = 'anonymous';
      const before = apiCalls.length;
      const { context, page, log } = await openContext(viewport, locale);
      try {
        step = 'load';
        const response = await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
        assert.equal(response.status(), 200);
        await page.waitForFunction((title) => document.querySelector('h1')?.textContent === title, heroTitle[locale]);
        assert.equal(await page.title(), 'FinSight PK — Financial Clarity for SMEs');
        assert.match(await page.locator('meta[name="description"]').getAttribute('content'), /FinSight helps SMEs/);
        assert.equal(await page.evaluate(() => document.documentElement.dir), locale === 'ur' ? 'rtl' : 'ltr');
        assert.equal(await page.evaluate(() => document.documentElement.lang), locale);
        step = 'structure';
        assert.equal(await page.locator('h1').count(), 1);
        assert.equal(await page.locator('main#main').count(), 1);
        assert.equal(await page.locator('header').count(), 1);
        assert.equal(await page.locator('footer').count(), 1);
        assert.deepEqual(await page.locator('main > section[id]').evaluateAll((nodes) => nodes.map((node) => node.id)), sectionIds, 'section order');
        assert.equal(await page.locator('footer a[href="/?auth=register"]').count(), 1, 'footer keeps a plain link, no CTA banner');
        assert.equal(await page.locator('footer h2').count(), 3);
        // No authenticated chrome, business setup or auth form on the public page; no identity probe either.
        assert.equal(await page.locator('aside[aria-label="Primary Navigation"]').count(), 0);
        assert.equal(await page.locator('form, input').count(), 0);
        assert.deepEqual(apiCalls.slice(before), [], `${label}: anonymous landing makes no API request`);
        step = 'brand'; await brandGeometry(page, label);
        step = 'links'; await linksResolve(page, label);
        step = 'overflow'; await noHorizontalOverflow(page, label);
        step = 'layout quality'; measurements[label] = await layoutQuality(page, label, locale);
        step = 'responsive navigation';
        const desktopNav = viewport.width >= DESKTOP_NAV_MIN;
        assert.equal(await page.getByRole('button', { name: menuText[locale].open }).isVisible(), !desktopNav, `${label}: menu toggle`);
        assert.equal(await page.locator('header nav').first().isVisible(), desktopNav, `${label}: desktop nav`);
        assert.equal(await page.locator('header').getByRole('link', { name: locale === 'ur' ? 'شروع کریں' : 'Get Started' }).first().isVisible(), viewport.width >= 768, `${label}: header CTA`);
        // Hero CTAs remain usable (>=44px) and on screen.
        for (const name of locale === 'ur' ? ['شروع کریں', 'FinSight کیسے کام کرتا ہے، دیکھیں'] : ['Get Started', 'See how FinSight works']) {
          const box = await page.locator('section[aria-labelledby="landing-hero-title"]').getByRole('link', { name, exact: true }).boundingBox();
          assert.ok(box && box.height >= 44 && box.x >= 0 && box.x + box.width <= viewport.width + 1, `${label}: CTA ${name} ${JSON.stringify(box)}`);
        }
        if (viewport.width < 768) {
          step = 'workspace chips';
          const chips = await page.getByTestId('workspace-inputs').locator('li').evaluateAll((items) => items.map((item) => Math.round(item.getBoundingClientRect().width)));
          assert.ok(Math.abs(chips[0] - chips[1]) <= 1 && Math.abs(chips[2] - chips[3]) <= 1 && chips[4] > chips[0] * 1.8, `${label}: even chip grid ${chips}`);
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
    await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
    step = 'hero secondary CTA';
    await page.getByRole('link', { name: 'See how FinSight works' }).click();
    await page.waitForFunction(() => location.hash === '#how-it-works');
    step = 'faq disclosure';
    const summary = page.locator('#faq summary').first();
    await summary.focus(); await page.keyboard.press('Enter');
    assert.equal(await page.locator('#faq details').first().getAttribute('open'), '');
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

// ---- 3. Menu behaviour below the desktop-nav breakpoint (EN + UR, laptop, short and standard phones) ----
for (const [viewport, locale] of [[viewports[1], 'en'], [viewports[1], 'ur'], [viewports[2], 'en'], [viewports[3], 'ur'], [viewports[4], 'en'], [viewports[4], 'ur']]) {
  const label = `${viewport.name}-${locale}`;
  const t = menuText[locale];
  await check(`menu opens, closes on Escape, outside tap, focus leaving and link choice (${label})`, async () => {
    mode = 'anonymous';
    const { context, page, log } = await openContext(viewport, locale);
    try {
      await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
      const menu = page.getByTestId('landing-mobile-menu');
      const open = async () => { await page.getByRole('button', { name: t.open }).click(); await menu.waitFor({ state: 'visible' }); };
      step = 'open';
      assert.equal(await page.getByRole('button', { name: t.open }).getAttribute('aria-expanded'), 'false');
      await open();
      assert.equal(await page.getByRole('button', { name: t.close }).getAttribute('aria-expanded'), 'true');
      assert.equal(await page.getByRole('button', { name: t.close }).getAttribute('aria-controls'), 'landing-mobile-menu');
      assert.equal(await page.evaluate(() => document.activeElement?.closest('[data-testid="landing-mobile-menu"]') !== null), true, 'focus moves into menu');
      const box = await menu.boundingBox();
      assert.ok(box.y + box.height <= viewport.height + 1 && box.width <= viewport.width + 1, `menu fits ${JSON.stringify(box)}`);
      // Every action shown in the panel (the language/CTA block is md:hidden because the header shows it from md).
      const shown = menu.locator('a:visible, button:visible');
      assert.ok(await shown.count() >= (viewport.width < 768 ? 9 : 6));
      for (const item of await shown.all()) { await item.scrollIntoViewIfNeeded(); assert.ok(await item.isVisible()); }
      await page.screenshot({ path: `${out}/${label}-menu-open.png` });
      await noHorizontalOverflow(page, `${label}-menu`);
      step = 'escape';
      await page.keyboard.press('Escape');
      await menu.waitFor({ state: 'detached' });
      assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('aria-controls')), 'landing-mobile-menu', 'focus returns to toggle');
      step = 'outside tap';
      await open();
      const hashBefore = await page.evaluate(() => location.hash);
      await page.mouse.click(viewport.width / 2, viewport.height - 8);
      await menu.waitFor({ state: 'detached' });
      assert.equal(await page.evaluate(() => location.hash), hashBefore, 'outside tap does not activate content underneath');
      assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('aria-controls')), 'landing-mobile-menu', 'focus returns to toggle after outside tap');
      step = 'focus leaves';
      await open();
      const items = await menu.locator('a:visible, button:visible').count();
      for (let index = 0; index < items + 2 && await menu.count(); index += 1) await page.keyboard.press('Tab');
      await menu.waitFor({ state: 'detached' });
      assert.equal(await page.evaluate(() => document.activeElement?.closest('header') === null), true, 'focus moved on into the page with the menu closed');
      step = 'link choice';
      await open();
      await menu.getByRole('link', { name: t.faq, exact: true }).click();
      await menu.waitFor({ state: 'detached' });
      await page.waitForFunction(() => location.hash === '#faq');
      step = 'language from menu or header';
      if (viewport.width < 768) {
        await open();
        await menu.getByRole('button', { name: t.lang }).click();
      } else {
        await page.locator('header').getByRole('button', { name: t.lang }).click();
      }
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
    step = 'get started';
    await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
    await page.locator('section[aria-labelledby="landing-hero-title"]').getByRole('link', { name: 'Get Started' }).click();
    await page.getByRole('heading', { level: 1, name: 'Create your account' }).waitFor();
    await page.waitForFunction(() => location.pathname === '/' && location.search === '');
    assert.ok(await page.locator('input[autocomplete="name"]').count() > 0);
    step = 'sign in';
    await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
    await page.locator('header').getByRole('link', { name: 'Sign In' }).click();
    await page.getByRole('heading', { level: 1, name: 'Sign in to FinSight' }).waitFor();
    step = 'destinations';
    await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
    assert.deepEqual(await page.locator('footer a[href^="/"]').evaluateAll((anchors) => anchors.map((a) => a.getAttribute('href'))), ['/', '/?auth=register']);
    assert.deepEqual(await page.locator('#access a').evaluateAll((anchors) => anchors.map((a) => a.getAttribute('href'))), ['/?auth=register', '/']);
    // Leaving /landing for / runs the deferred identity probe, so the anonymous 401 is expected here.
    assertCleanLog(log, 'cta', true);
  } finally { await context.close(); }
});

// ---- 5. Private routes stay private; the public page never mounts the business tree or probes identity ----
await check('private routes remain protected exactly as before', async () => {
  const { context, page, log } = await openContext(viewports[0], 'en');
  try {
    mode = 'anonymous';
    for (const route of ['/', '/records', '/settings', '/reports', '/landing/extra']) {
      step = `anonymous ${route}`;
      const before = apiCalls.length;
      await page.goto(`${origin}${route}`, { waitUntil: 'networkidle' });
      if (route === '/landing/extra') assert.equal(await page.locator('[data-landing-root]').count(), 0, 'nested landing path is not public');
      await page.getByRole('heading', { level: 1, name: 'Sign in to FinSight' }).waitFor({ timeout: 10000 });
      assert.equal(await page.locator('aside[aria-label="Primary Navigation"]').count(), 0, route);
      assert.ok(apiCalls.slice(before).some((call) => call.url === '/api/auth/me'), `${route} still probes identity`);
    }
    step = 'no business';
    mode = 'no-business';
    await page.goto(`${origin}/`, { waitUntil: 'networkidle' });
    await page.getByRole('heading', { name: 'Create business' }).waitFor();
    await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
    await page.locator('h1').filter({ hasText: heroTitle.en }).waitFor();
    assert.equal(await page.locator('form').count(), 0);
    step = 'business landing';
    mode = 'business';
    const before = apiCalls.length;
    await page.goto(`${origin}/landing`, { waitUntil: 'networkidle' });
    await page.locator('h1').filter({ hasText: heroTitle.en }).waitFor();
    assert.deepEqual(apiCalls.slice(before), [], 'a fresh /landing load makes no session or business request, even when signed in');
    assert.equal(await page.locator('aside[aria-label="Primary Navigation"]').count(), 0);
    step = 'business sign in';
    // Leaving the public page runs the probe; a signed-in visitor goes straight into the private shell.
    await page.locator('header').getByRole('link', { name: 'Sign In' }).click();
    await page.locator('aside[aria-label="Primary Navigation"]').waitFor({ timeout: 15000 });
    assert.ok(apiCalls.slice(before).some((call) => call.url === '/api/auth/me'));
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
      return style.animationName !== 'none' && parseFloat(style.animationDuration) > 0.01;
    }).length);
    assert.equal(animations, 0);
    assertCleanLog(log, 'reduced-motion');
  } finally { await context.close(); }
});

await browser.close();
await new Promise((resolve) => server.close(resolve));
const toleratedConsole = Object.fromEntries([...new Set(knownConsole)].map((url) => [url, knownConsole.filter((entry) => entry === url).length]));
const summary = { origin, artifacts: out, total: results.length, passed: results.filter((r) => r.ok).length, failed: results.filter((r) => !r.ok).length, toleratedConsole, measurements, results };
writeFileSync(`${out}/results.json`, JSON.stringify(summary, null, 2));
console.log(`\n${summary.passed}/${summary.total} landing browser checks passed`);
process.exit(summary.failed ? 1 : 0);
