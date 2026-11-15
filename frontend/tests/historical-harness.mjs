import assert from 'node:assert/strict';

export const historicalBackendPort = Number(process.env.FINSIGHT_BROWSER_BACKEND_PORT || 18080);
export const historicalOrigin = process.env.FRONTEND_TEST_ORIGIN || 'http://127.0.0.1:13100';
assert.ok(Number.isInteger(historicalBackendPort) && historicalBackendPort > 1024 && historicalBackendPort < 65536);
const url = new URL(historicalOrigin);
assert.equal(url.protocol, 'http:');
assert.equal(url.hostname, '127.0.0.1', 'Synthetic browser credentials must stay on disposable loopback infrastructure');
assert.ok(Number(url.port) > 1024 && Number(url.port) < 65536);
assert.equal(url.origin, historicalOrigin, 'Use a loopback origin without a path, query or credentials');
assert.notEqual(Number(url.port), historicalBackendPort);
