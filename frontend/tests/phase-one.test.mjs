import assert from "node:assert/strict";
import { after, afterEach, beforeEach, mock, test } from "node:test";
import { mkdtempSync, readFileSync, writeFileSync, unlinkSync, rmdirSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import ts from "typescript";

// Exercise the actual TypeScript API modules using Node's test runner, without a test framework.
const sourceDirectory = fileURLToPath(new URL("../src/lib/api/", import.meta.url));
const compiledDirectory = mkdtempSync(path.join(tmpdir(), "finsight-api-tests-"));
const modules = ["client", "session", "contracts", "phase-one", "config"];
for (const name of modules) {
  const source = readFileSync(path.join(sourceDirectory, `${name}.ts`), "utf8");
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  });
  writeFileSync(path.join(compiledDirectory, `${name}.js`), outputText);
}
const require = createRequire(import.meta.url);
const { apiClient, ApiError } = require(path.join(compiledDirectory, "client.js"));
const { phaseOneApi } = require(path.join(compiledDirectory, "phase-one.js"));
const session = require(path.join(compiledDirectory, "session.js"));
const userId = "389b6bc4-efc4-4d06-9267-a41a2fae9a27";
const otherId = "4ceafcb6-703c-4e79-ab4b-9f69c0fbb581";
const record = {
  userId, month: "2026-09", cashInflow: 0, cashOutflow: 0, revenue: 0,
  operatingExpenses: 0, cashBalanceEom: 0, cogs: null,
  receivablesOutstanding: null, payablesOutstanding: null, inventoryValue: null,
  loanOutstanding: null, interestExpense: null, financingType: "none",
};
const originalWindow = globalThis.window;
let storedValues;

beforeEach(() => {
  storedValues = new Map();
  globalThis.window = { dispatchEvent: () => true, localStorage: {
    getItem: (key) => storedValues.get(key) ?? null,
    setItem: (key, value) => storedValues.set(key, value),
    removeItem: (key) => storedValues.delete(key),
  } };
});

afterEach(() => {
  mock.restoreAll();
  if (originalWindow === undefined) delete globalThis.window;
  else globalThis.window = originalWindow;
});

after(() => {
  // Remove only the known generated files, then the now-empty temporary directory.
  for (const name of modules) unlinkSync(path.join(compiledDirectory, `${name}.js`));
  rmdirSync(compiledDirectory);
});

test("reads use the same-origin API and the backend's user/month routes", async () => {
  const calls = [];
  mock.method(globalThis, "fetch", async (url, options) => {
    calls.push({ url, options });
    return Response.json({ userId });
  });
  await phaseOneApi.getProfile(userId);
  await phaseOneApi.getMonthlyRecords(userId);
  await phaseOneApi.getMonthlyRecord(userId, "2026-09");
  assert.deepEqual(calls.map(({ url }) => url), [
    `/api/profile/${userId}`, `/api/records/monthly/${userId}`,
    `/api/records/monthly/${userId}/2026-09`,
  ]);
  assert.ok(calls.every(({ options }) => options.cache === "no-store"));
});

test("record creation preserves zero values and explicit missing optional fields", async () => {
  mock.method(globalThis, "fetch", async (url, options) => {
    assert.equal(url, "/api/records/monthly");
    assert.equal(options.method, "POST");
    assert.equal(options.headers.get("Content-Type"), "application/json");
    assert.deepEqual(JSON.parse(options.body), record);
    return Response.json({ ...record, id: "saved", updatedAt: "2026-09-23T12:00:00" }, { status: 201 });
  });
  const saved = await phaseOneApi.createMonthlyRecord(record);
  assert.equal(saved.cogs, null);
  assert.equal(saved.cashInflow, 0);
  assert.equal(saved.userId, userId);
});

test("record updates POST an upsert for the selected identity and original month", async () => {
  mock.method(globalThis, "fetch", async (url, options) => {
    assert.equal(url, "/api/records/monthly");
    assert.equal(options.method, "POST");
    assert.deepEqual(JSON.parse(options.body), record);
    return Response.json(record, { status: 201 });
  });
  await phaseOneApi.updateMonthlyRecord(userId, "2026-09", { ...record, userId: otherId, month: "2026-08" });
});

test("profile creation and language update send only their backend request contracts", async () => {
  const calls = [];
  mock.method(globalThis, "fetch", async (url, options) => {
    calls.push({ url, method: options.method, body: JSON.parse(options.body) });
    return Response.json({ userId, businessType: "services", languagePreference: "ur" });
  });
  const profile = { userId, businessType: "services", languagePreference: "en", whatsappNumber: null, whatsappOptIn: false };
  await phaseOneApi.createProfile(profile);
  await phaseOneApi.updateLanguage(userId, "ur");
  assert.deepEqual(calls, [
    { url: "/api/profile", method: "POST", body: profile },
    { url: `/api/profile/${userId}/language`, method: "PATCH", body: { languagePreference: "ur" } },
  ]);
});

test("server validation remains an error, including the current COGS restriction", async () => {
  mock.method(globalThis, "fetch", async (_url, options) => {
    assert.equal(JSON.parse(options.body).cogs, null);
    return Response.json({ message: "COGS is required", errors: { cogs: "COGS is required" } }, { status: 400 });
  });
  await assert.rejects(phaseOneApi.createMonthlyRecord(record), (error) => {
    assert.ok(error instanceof ApiError);
    assert.equal(error.status, 400);
    assert.equal(error.message, "COGS is required");
    assert.deepEqual(error.fieldErrors, { cogs: "COGS is required" });
    return true;
  });
});

test("empty-body not-found responses retain the HTTP status", async () => {
  mock.method(globalThis, "fetch", async () => new Response(null, { status: 404, statusText: "Not Found" }));
  await assert.rejects(phaseOneApi.getMonthlyRecord(userId, "2026-09"), { name: "ApiError", status: 404 });
});

test("network failures do not return sample data and cancellation is preserved", async () => {
  mock.method(globalThis, "fetch", async () => { throw new TypeError("fetch failed"); });
  await assert.rejects(phaseOneApi.getMonthlyRecords(userId), { name: "ApiError", status: 0 });
  mock.restoreAll();
  const aborted = new DOMException("Cancelled", "AbortError");
  mock.method(globalThis, "fetch", async () => { throw aborted; });
  await assert.rejects(apiClient("/profile", { signal: AbortSignal.abort() }), (error) => error === aborted);
});

test("invalid successful JSON is reported as a backend response error", async () => {
  mock.method(globalThis, "fetch", async () => new Response("<html>proxy error</html>", { status: 200 }));
  await assert.rejects(phaseOneApi.getProfile(userId), { name: "ApiError", status: 200, message: "The backend returned an invalid response." });
});

test("Headers objects supplied by callers are retained", async () => {
  mock.method(globalThis, "fetch", async (_url, options) => {
    assert.equal(options.headers.get("X-Request-ID"), "request-1");
    assert.equal(options.headers.get("Accept"), "application/json");
    return new Response(null, { status: 204 });
  });
  assert.equal(await apiClient("/profile", { headers: new Headers({ "X-Request-ID": "request-1" }) }), undefined);
});

test("invalid identities and months are rejected before sending a request", () => {
  const fetchMock = mock.method(globalThis, "fetch", async () => Response.json({}));
  assert.throws(() => phaseOneApi.getProfile("user-001"), /UUID/);
  assert.throws(() => phaseOneApi.getMonthlyRecord(userId, "2026-13"), /YYYY-MM/);
  assert.throws(() => phaseOneApi.getMonthlyRecord(userId, "2026-9"), /YYYY-MM/);
  assert.equal(fetchMock.mock.callCount(), 0);
});

test("browser profile UUIDs survive retries, while cached mock identities are rejected", () => {
  storedValues.set("finsight_user_id", "user-001");
  assert.equal(session.getUserId(), null);
  const generated = session.getOrCreateUserId();
  assert.ok(session.isValidUserId(generated));
  assert.equal(session.getOrCreateUserId(), generated);
  assert.equal(session.getUserId(), null);
  session.setUserId(userId.toUpperCase());
  assert.equal(session.getUserId(), userId);
  assert.throws(() => session.setUserId("user-001"), /UUID/);
  session.clearUserId();
  assert.equal(session.getUserId(), null);
});

test("creating a new profile never reuses or replaces the active profile before success", () => {
  const notifications = [];
  globalThis.window.dispatchEvent = (event) => { notifications.push(event.type); return true; };
  session.setUserId(userId);
  const candidate = session.getOrCreateUserId();
  assert.notEqual(candidate, userId);
  assert.equal(session.getUserId(), userId);
  assert.equal(session.getOrCreateUserId(), candidate);
  assert.equal(storedValues.get("finsight_pending_user_id"), candidate);
  session.setUserId(candidate);
  assert.equal(session.getUserId(), candidate);
  assert.equal(storedValues.has("finsight_pending_user_id"), false);
  assert.deepEqual(notifications, ["storage", "storage"]);
});

test("failed profile creation keeps the candidate retryable without activating it", async () => {
  const candidate = session.getOrCreateUserId();
  mock.method(globalThis, "fetch", async () => Response.json({ message: "Validation failed" }, { status: 400 }));
  await assert.rejects(phaseOneApi.createProfile({ userId: candidate, businessType: "services" }), { status: 400 });
  assert.equal(session.getUserId(), null);
  assert.equal(session.getOrCreateUserId(), candidate);
});

test("server rendering and unavailable storage never invent an ephemeral identity", () => {
  delete globalThis.window;
  assert.equal(session.getUserId(), null);
  assert.throws(() => session.getOrCreateUserId(), /browser/);
  globalThis.window = { localStorage: {
    getItem() { throw new Error("denied"); },
    setItem() { throw new Error("denied"); },
  } };
  assert.equal(session.getUserId(), null);
  assert.throws(() => session.getOrCreateUserId(), /browser storage/);
});

test("live mode is the default; demo mode requires explicit configuration", () => {
  const previous = process.env.NEXT_PUBLIC_DATA_MODE;
  const configPath = path.join(compiledDirectory, "config.js");
  try {
    delete process.env.NEXT_PUBLIC_DATA_MODE;
    delete require.cache[configPath];
    assert.equal(require(configPath).isDemoMode, false);
    process.env.NEXT_PUBLIC_DATA_MODE = "demo";
    delete require.cache[configPath];
    assert.equal(require(configPath).isDemoMode, true);
  } finally {
    if (previous === undefined) delete process.env.NEXT_PUBLIC_DATA_MODE;
    else process.env.NEXT_PUBLIC_DATA_MODE = previous;
  }
});
