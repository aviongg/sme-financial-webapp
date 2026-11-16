import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import http from 'node:http';
import net from 'node:net';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { checkBackend, checkFrontend, packageStandalone, waitForHttp } from '../../scripts/demo-runtime.mjs';

// DEF-18 regressions for Codex's candidate-v2 QA: an inert TCP listener is not readiness, and a standalone frontend
// whose root returns 200 while its CSS/JS assets 404 is not ready. All servers are task-owned, ephemeral and loopback.
const scratch = mkdtempSync(path.join(tmpdir(), 'finsight-demo-runtime-'));
const servers = [], sockets = new Set();
after(async () => {
  for (const socket of sockets) socket.destroy();
  for (const server of servers) { server.closeAllConnections?.(); await new Promise(resolve => server.close(resolve)); }
  rmSync(scratch, { recursive: true, force: true });
});

async function listen(server) {
  servers.push(server);
  server.on('connection', socket => { sockets.add(socket); socket.on('close', () => sockets.delete(socket)); });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  return `http://127.0.0.1:${server.address().port}`;
}

const page = '<!DOCTYPE html><html><head><link rel="stylesheet" href="/_next/static/css/app.css" data-precedence="next"/>'
  + '<script src="/_next/static/chunks/webpack-1.js" async=""></script></head><body>FinSight</body></html>';

function frontend({ assets = true, health = true } = {}) {
  return http.createServer((req, res) => {
    if (req.url === '/internal/health' && health) { res.writeHead(200, { 'Content-Type': 'application/json' }); return res.end('{"status":"UP"}'); }
    if (req.url === '/') { res.writeHead(200, { 'Content-Type': 'text/html' }); return res.end(page); }
    if (assets && req.url === '/_next/static/css/app.css') { res.writeHead(200, { 'Content-Type': 'text/css; charset=utf-8' }); return res.end('body{}'); }
    if (assets && req.url === '/_next/static/chunks/webpack-1.js') { res.writeHead(200, { 'Content-Type': 'application/javascript; charset=UTF-8' }); return res.end(''); }
    res.writeHead(404); res.end();
  });
}

test('an inert TCP listener (accepts, never answers HTTP) is not backend or frontend readiness', async () => {
  const inert = net.createServer(socket => { socket.on('error', () => {}); /* hold the socket open, say nothing */ });
  const url = await listen(inert);
  await assert.rejects(waitForHttp(ms => checkBackend(url, ms), 1, { requestMs: 300, intervalMs: 100 }), /Not ready within 1s/);
  await assert.rejects(waitForHttp(ms => checkFrontend(url, ms), 1, { requestMs: 300, intervalMs: 100 }), /Not ready within 1s/);
});

test('a closed port fails readiness instead of passing', async () => {
  const probe = net.createServer();
  const url = await listen(probe);
  await new Promise(resolve => probe.close(resolve)); servers.pop();
  await assert.rejects(waitForHttp(ms => checkBackend(url, ms), 1, { requestMs: 300, intervalMs: 100 }), /Not ready/);
});

test('an HTTP server that is not the FinSight API is not backend readiness', async () => {
  const other = await listen(http.createServer((req, res) => { res.writeHead(200, { 'Content-Type': 'text/html' }); res.end('<html>other app</html>'); }));
  await assert.rejects(checkBackend(other), /did not return a FinSight CSRF token/);
  const api = await listen(http.createServer((req, res) => {
    if (req.url === '/api/auth/csrf') { res.writeHead(200, { 'Content-Type': 'application/json' }); return res.end('{"token":"t","headerName":"X-XSRF-TOKEN"}'); }
    res.writeHead(404); res.end();
  }));
  assert.deepEqual(await checkBackend(api), { csrf: 200 });
});

test("Codex's packaging failure: root 200 with 404 CSS/JS assets is reported as not ready", async () => {
  const broken = await listen(frontend({ assets: false }));
  await assert.rejects(checkFrontend(broken), /2\/2 static assets not served: \/_next\/static\/css\/app\.css -> HTTP 404/);
  await assert.rejects(checkFrontend(await listen(frontend({ health: false }))), /internal\/health returned HTTP 404/);
  assert.deepEqual(await checkFrontend(await listen(frontend())), { root: 200, css: 1, js: 1 });
});

function buildTree(name, { buildId = 'build-1', standaloneBuildId = buildId, withStatic = true } = {}) {
  const dir = path.join(scratch, name);
  mkdirSync(path.join(dir, '.next', 'standalone', '.next'), { recursive: true });
  mkdirSync(path.join(dir, 'public'), { recursive: true });
  writeFileSync(path.join(dir, '.next', 'BUILD_ID'), buildId);
  writeFileSync(path.join(dir, '.next', 'standalone', '.next', 'BUILD_ID'), standaloneBuildId);
  writeFileSync(path.join(dir, '.next', 'standalone', 'server.js'), '');
  writeFileSync(path.join(dir, 'public', 'logo.svg'), '<svg/>');
  if (withStatic) {
    mkdirSync(path.join(dir, '.next', 'static', 'css'), { recursive: true });
    writeFileSync(path.join(dir, '.next', 'static', 'css', 'app.css'), 'body{}');
  }
  return dir;
}

test('packaging copies .next/static and public into the standalone tree and replaces stale assets', () => {
  const dir = buildTree('ok');
  const stale = path.join(dir, '.next', 'standalone', '.next', 'static', 'css');
  mkdirSync(stale, { recursive: true }); writeFileSync(path.join(stale, 'old.css'), 'stale');
  const result = packageStandalone(dir);
  assert.equal(result.buildId, 'build-1');
  assert.equal(readFileSync(path.join(dir, '.next', 'standalone', '.next', 'static', 'css', 'app.css'), 'utf8'), 'body{}');
  assert.equal(readFileSync(path.join(dir, '.next', 'standalone', 'public', 'logo.svg'), 'utf8'), '<svg/>');
  assert.equal(existsSync(path.join(stale, 'old.css')), false, 'Stale assets from an older build are removed');
});

test('packaging refuses an incomplete build or a standalone server from a different build', () => {
  assert.throws(() => packageStandalone(buildTree('no-static', { withStatic: false })), /Frontend build incomplete/);
  assert.throws(() => packageStandalone(buildTree('mismatch', { standaloneBuildId: 'build-0' })), /does not match build build-1/);
});
