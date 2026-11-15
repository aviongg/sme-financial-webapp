// Synthetic HTTP fixtures only; never use an existing application service.
import assert from 'node:assert/strict';
import net from 'node:net';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { mkdirSync, readFileSync, writeFileSync, createWriteStream } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { historicalOrigin as origin, historicalBackendPort as backendPort } from './historical-harness.mjs';

const frontend = fileURLToPath(new URL('..', import.meta.url));
const output = path.resolve(process.env.BROWSER_ARTIFACT_DIR || path.join(frontend, 'test-artifacts/historical'));
mkdirSync(output, { recursive: true });
const drivers = ['browser-landing', 'browser', 'browser-phase-two', 'browser-extra', 'financial-risk-browser'];
const selected = process.argv.slice(2);
assert.ok(selected.every(name => drivers.includes(name)), 'Unknown historical driver');
const requested = selected.length ? selected : drivers;
const frontendPort = Number(new URL(origin).port);
assert.notEqual(frontendPort, backendPort);
assert.ok(!process.env.LANDING_ONLY, 'Full landing coverage is required');
const rewrites = JSON.stringify(JSON.parse(readFileSync(path.join(frontend, '.next/routes-manifest.json'), 'utf8')).rewrites);
assert.ok(rewrites.includes(`http://127.0.0.1:${backendPort}/api/`), 'Build with BACKEND_API_URL pointing at the disposable fixture port');

async function unused(port) {
  const probe = net.createServer();
  await new Promise((resolve, reject) => { probe.once('error', reject); probe.listen(port, '127.0.0.1', resolve); });
  await new Promise(resolve => probe.close(resolve));
}
await unused(frontendPort);
await unused(backendPort);
const env = { ...process.env, BACKEND_API_URL: `http://127.0.0.1:${backendPort}`, FRONTEND_TEST_ORIGIN: origin, FINSIGHT_BROWSER_BACKEND_PORT: String(backendPort) };
let next;
async function startNext() {
  const log = createWriteStream(path.join(output, 'next.log'));
  next = spawn(process.execPath, [path.join(frontend, 'node_modules/next/dist/bin/next'), 'start', '--hostname', '127.0.0.1', '--port', String(frontendPort)], { cwd: frontend, env, windowsHide: true });
  next.stdout.pipe(log); next.stderr.pipe(log);
  for (let attempt = 0; attempt < 60; attempt++) {
    assert.equal(next.exitCode, null, 'Disposable Next server exited');
    try { if ((await fetch(origin)).status === 200) return; } catch { /* waiting for our process */ }
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  throw new Error('Disposable Next server did not become ready');
}
async function stopNext() {
  if (!next) return;
  if (next.exitCode === null && next.signalCode === null) {
    const done = once(next, 'exit');
    next.kill();
    await done;
  }
  next = undefined;
}
const results = [];
try {
  for (const name of requested) {
    await unused(backendPort);
    if (!next) await startNext();
    const artifacts = path.join(output, name); mkdirSync(artifacts, { recursive: true });
    const log = createWriteStream(path.join(artifacts, 'driver.log'));
    const driver = spawn(process.execPath, [`tests/${name}.integration.mjs`], { cwd: frontend, env: { ...env, BROWSER_ARTIFACT_DIR: artifacts }, windowsHide: true });
    driver.stdout.pipe(log); driver.stderr.pipe(log);
    const [code, signal] = await once(driver, 'exit');
    results.push({ driver: name, code, signal, artifacts });
    console.log(`${code === 0 ? 'PASS' : 'FAIL'} ${name}: exit=${code} signal=${signal}`);
  }
} finally {
  await stopNext();
  writeFileSync(path.join(output, 'summary.json'), JSON.stringify({ scope: 'Frozen production frontend + disposable synthetic APIs; not Spring/PostgreSQL/provider acceptance', origin, backendPort, results }, null, 2));
}
process.exitCode = results.some(result => result.code !== 0) ? 1 : 0;
