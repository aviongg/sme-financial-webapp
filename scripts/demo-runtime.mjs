// Local demo runtime helpers (DEF-18 / Codex candidate-v2 QA section 5). Node built-ins only.
//   package <frontendDir> [standaloneDir]   copy .next/static and public into the standalone server tree
//   verify-backend <baseUrl> <timeoutSec>   HTTP readiness of the Spring API (GET /api/auth/csrf JSON token)
//   verify-frontend <baseUrl> <timeoutSec>  HTTP readiness of Next plus every referenced /_next/static CSS/JS asset
// A listening TCP port is never treated as readiness: only real HTTP application responses count.
import { cpSync, existsSync, readFileSync, rmSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/** Next's standalone output omits .next/static and public; without them every page loads with 404 CSS/JS. */
export function packageStandalone(frontendDir, standaloneDir = path.join(frontendDir, '.next', 'standalone')) {
  const build = path.join(frontendDir, '.next');
  const required = [path.join(standaloneDir, 'server.js'), path.join(build, 'static'), path.join(build, 'BUILD_ID'), path.join(frontendDir, 'public')];
  const missing = required.filter(file => !existsSync(file));
  if (missing.length) throw new Error(`Frontend build incomplete (run the production build first); missing: ${missing.join(', ')}`);
  const buildId = readFileSync(path.join(build, 'BUILD_ID'), 'utf8').trim();
  const standaloneBuildId = path.join(standaloneDir, '.next', 'BUILD_ID');
  if (existsSync(standaloneBuildId) && readFileSync(standaloneBuildId, 'utf8').trim() !== buildId) {
    throw new Error(`Standalone server (${readFileSync(standaloneBuildId, 'utf8').trim()}) does not match build ${buildId}; rebuild the frontend`);
  }
  // Replace, never merge: stale chunks from an older build must not be served.
  const targetStatic = path.join(standaloneDir, '.next', 'static');
  const targetPublic = path.join(standaloneDir, 'public');
  rmSync(targetStatic, { recursive: true, force: true });
  rmSync(targetPublic, { recursive: true, force: true });
  cpSync(path.join(build, 'static'), targetStatic, { recursive: true });
  cpSync(path.join(frontendDir, 'public'), targetPublic, { recursive: true });
  return { buildId, standaloneDir, static: targetStatic, public: targetPublic, builtAt: statSync(path.join(build, 'BUILD_ID')).mtime.toISOString() };
}

async function fetchWithTimeout(url, ms) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), ms);
  try {
    return await fetch(url, { signal: controller.signal, redirect: 'manual', headers: { 'Cache-Control': 'no-store' } });
  } finally {
    clearTimeout(timer);
  }
}

/** Polls an HTTP check until it passes or the deadline expires; reports the last real failure. */
export async function waitForHttp(check, timeoutSec, { intervalMs = 500, requestMs = 5000 } = {}) {
  const deadline = Date.now() + timeoutSec * 1000;
  let last = 'not attempted';
  do {
    try {
      return await check(requestMs);
    } catch (error) {
      last = error?.cause?.code || error?.name === 'AbortError' ? `${error.message} (${error?.cause?.code || 'timeout'})` : error.message;
    }
    if (Date.now() + intervalMs > deadline) break;
    await new Promise(resolve => setTimeout(resolve, intervalMs));
  } while (Date.now() < deadline);
  throw new Error(`Not ready within ${timeoutSec}s: ${last}`);
}

export async function checkBackend(baseUrl, requestMs = 5000) {
  const response = await fetchWithTimeout(new URL('/api/auth/csrf', baseUrl), requestMs);
  if (response.status !== 200) throw new Error(`GET /api/auth/csrf returned HTTP ${response.status}`);
  const body = await response.json().catch(() => null);
  if (!body || typeof body.token !== 'string' || !body.token) throw new Error('GET /api/auth/csrf did not return a FinSight CSRF token');
  return { csrf: 200 };
}

/** Root page, health route and every referenced static CSS/JS asset must be served with the right type. */
export async function checkFrontend(baseUrl, requestMs = 5000) {
  const health = await fetchWithTimeout(new URL('/internal/health', baseUrl), requestMs);
  if (health.status !== 200 || (await health.json().catch(() => ({}))).status !== 'UP') throw new Error(`GET /internal/health returned HTTP ${health.status}`);
  const root = await fetchWithTimeout(new URL('/', baseUrl), requestMs);
  if (root.status !== 200) throw new Error(`GET / returned HTTP ${root.status}`);
  const html = await root.text();
  const assets = [...new Set([...html.matchAll(/(?:src|href)="(\/_next\/static\/[^"?#]+\.(?:css|js))(?:[?#][^"]*)?"/g)].map(match => match[1]))];
  const css = assets.filter(asset => asset.endsWith('.css')), js = assets.filter(asset => asset.endsWith('.js'));
  if (!css.length || !js.length) throw new Error(`GET / referenced ${css.length} CSS and ${js.length} JS assets; expected both`);
  const failures = [];
  for (const asset of assets) {
    const response = await fetchWithTimeout(new URL(asset, baseUrl), requestMs);
    const type = response.headers.get('content-type') || '';
    const expected = asset.endsWith('.css') ? 'text/css' : 'javascript';
    if (response.status !== 200 || !type.includes(expected)) failures.push(`${asset} -> HTTP ${response.status} ${type}`);
    await response.arrayBuffer().catch(() => null);
  }
  if (failures.length) throw new Error(`${failures.length}/${assets.length} static assets not served: ${failures.slice(0, 5).join('; ')}`);
  return { root: 200, css: css.length, js: js.length };
}

async function main([command, ...args]) {
  if (command === 'package') {
    console.log(JSON.stringify(packageStandalone(path.resolve(args[0]), args[1] ? path.resolve(args[1]) : undefined)));
  } else if (command === 'verify-backend' || command === 'verify-frontend') {
    const check = command === 'verify-backend' ? checkBackend : checkFrontend;
    const result = await waitForHttp(ms => check(args[0], ms), Number(args[1] || 60));
    console.log(JSON.stringify({ url: args[0], ...result }));
  } else {
    throw new Error('Usage: demo-runtime.mjs package <frontendDir> [standaloneDir] | verify-backend <url> <timeoutSec> | verify-frontend <url> <timeoutSec>');
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main(process.argv.slice(2)).catch(error => { console.error(`FAILED: ${error.message}`); process.exit(1); });
}
