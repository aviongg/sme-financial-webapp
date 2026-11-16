// DEF-18 real-build regression (Codex candidate-v2 QA): launch the actual production standalone server on a task-owned
// loopback port, first exactly as built (reproducing root 200 with 404 CSS/JS), then after the demo launcher's packaging
// step, and require HTTP 200 for every referenced asset. Prerequisite: pnpm build:release. No backend or network access.
import assert from 'node:assert/strict';
import net from 'node:net';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { existsSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { checkFrontend, packageStandalone, waitForHttp } from '../../scripts/demo-runtime.mjs';

const frontendDir = path.resolve(fileURLToPath(new URL('..', import.meta.url)));
const standalone = path.join(frontendDir, '.next', 'standalone');
const out = path.resolve(process.env.BROWSER_ARTIFACT_DIR || path.join(frontendDir, 'test-artifacts', 'demo-standalone'));
mkdirSync(out, { recursive: true });
assert.ok(existsSync(path.join(standalone, 'server.js')), 'Run pnpm build:release first');

async function freePort() {
  const probe = net.createServer();
  await new Promise(resolve => probe.listen(0, '127.0.0.1', resolve));
  const { port } = probe.address();
  await new Promise(resolve => probe.close(resolve));
  return port;
}

async function withServer(run) {
  const port = await freePort();
  const child = spawn(process.execPath, [path.join(standalone, 'server.js')], { cwd: standalone, windowsHide: true,
    env: { ...process.env, PORT: String(port), HOSTNAME: '127.0.0.1', BACKEND_API_URL: 'http://127.0.0.1:9', NEXT_TELEMETRY_DISABLED: '1' } });
  let output = ''; child.stdout.on('data', d => { output += d; }); child.stderr.on('data', d => { output += d; });
  try {
    return await run(`http://127.0.0.1:${port}`);
  } catch (error) {
    error.message += `\n${output.slice(-2000)}`; throw error;
  } finally {
    if (child.exitCode === null) { const exited = once(child, 'exit'); child.kill(); await exited; }
  }
}

const results = {};
// 1. Exactly as built: standalone output carries no static assets or public files.
rmSync(path.join(standalone, '.next', 'static'), { recursive: true, force: true });
rmSync(path.join(standalone, 'public'), { recursive: true, force: true });
results.unpackaged = await withServer(async url => {
  const error = await waitForHttp(ms => checkFrontend(url, ms), 20).then(() => null, e => e);
  assert.ok(error, 'An unpackaged standalone server must not pass readiness');
  assert.match(error.message, /static assets not served: .*HTTP 404/, error.message);
  return { ready: false, reason: error.message.split('\n')[0] };
});
console.log('PASS unpackaged standalone build is detected: root served, CSS/JS assets 404');

// 2. After the launcher's packaging step every referenced CSS/JS asset is served.
results.package = packageStandalone(frontendDir);
results.packaged = await withServer(url => waitForHttp(ms => checkFrontend(url, ms), 60));
assert.ok(results.packaged.css > 0 && results.packaged.js > 0);
console.log(`PASS packaged standalone build serves all ${results.packaged.css} CSS and ${results.packaged.js} JS assets (HTTP 200)`);
writeFileSync(path.join(out, 'results.json'), JSON.stringify({ scope: 'Production standalone server on task-owned loopback port; no backend', ...results }, null, 2));
