// Run the full financial/presentation journey and cross-tab tenant-safety gate
// sequentially, so their disposable services never overlap reserved ports.
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';

for (const [driver, args] of [
  ['product-completion-browser.integration.mjs', process.argv.slice(2)],
  ['def11-browser.integration.mjs', []],
]) {
  const child = spawn(process.execPath, [fileURLToPath(new URL(driver, import.meta.url)), ...args], {
    cwd: fileURLToPath(new URL('..', import.meta.url)), env: process.env, stdio: 'inherit', windowsHide: true,
  });
  const [code, signal] = await once(child, 'exit');
  if (code !== 0 || signal) { process.exitCode = code || 1; break; }
}
