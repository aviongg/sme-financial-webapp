// Independent v3 retest of the two original v2 DEF-08 assertions. Real application
// and disposable PostgreSQL; only the explicitly labelled Copilot reply is injected.
import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

export async function providerJourneys({ origin, browser, cookies, outDir, testedSHA }) {
  const output = path.join(outDir, 'independent-provider-qa');
  mkdirSync(output, { recursive: true });
  const results = [], contexts = [];
  const check = async (id, run) => {
    try { await run(); results.push({ id, status: 'PASS' }); }
    catch (error) { results.push({ id, status: 'FAIL', error: String(error) }); }
    console.log('PROVIDER QA', results.at(-1).status, id);
  };
  try {
    await check('DEF-08-WHATSAPP-EMPTY-HISTORY', async () => {
      const context = await browser.newContext(); contexts.push(context);
      await context.addCookies(cookies);
      const page = await context.newPage();
      await page.goto(origin + '/settings');
      const heading = page.getByRole('heading', { name: 'WhatsApp updates', exact: true });
      await heading.waitFor();
      await page.waitForLoadState('networkidle');
      const text = await heading.locator('..').innerText();
      const deliveries = await context.request.get(origin + '/api/whatsapp/deliveries');
      assert.equal(deliveries.status(), 200);
      const history = await deliveries.json(); assert.deepEqual(history, []);
      writeFileSync(path.join(output, 'whatsapp-empty-history.json'), JSON.stringify({
        runtimeProvider: 'mock; task-owned launcher disables the scheduler', outboundCard: text,
        deliveryHistory: history, scope: 'Real Spring settings; no provider send invoked',
      }, null, 2));
      await page.screenshot({ path: path.join(output, 'whatsapp-empty-history.png'), fullPage: true });
      // Preserve the exact independent-v2 assertion. Per-delivery disclosure does
      // not satisfy the separate original outbound settings-card expectation.
      assert.match(text, /mock|simulat|not live|provider.*disabled|delivery.*disabled/i,
        'Outbound card does not identify its mock provider');
    });
    await check('DEF-08-COPILOT', async () => {
      const context = await browser.newContext(); contexts.push(context);
      await context.addCookies(cookies);
      await context.route('**/api/copilot/status', route => route.fulfill({ json: {
        version: 'copilot-v1', moduleEnabled: true, mode: 'openai', providerReason: '', tools: ['health_evidence'],
      } }));
      const response = { version: 'copilot-v1', language: 'en', answer: 'ledgerCashInflow: 1234.00.',
        evidence: [{ id: 'health_evidence.ledgerCashInflow', tool: 'health_evidence', label: 'ledgerCashInflow', value: '1234.00' }],
        tools: [{ name: 'health_evidence', status: 'OK', message: null }], mode: 'deterministic', model: null, reasonCode: 'provider_unavailable' };
      await context.route('**/api/copilot/ask', route => route.fulfill({ json: response }));
      const page = await context.newPage(); await page.goto(origin + '/ask-finsight');
      await page.getByTestId('copilot-question').fill('Explain cash'); await page.getByTestId('copilot-send').click();
      await page.getByTestId('copilot-answer').waitFor();
      const displayed = await page.getByTestId('copilot').innerText();
      writeFileSync(path.join(output, 'copilot-fault.json'), JSON.stringify({
        scope: 'Provider response fault injection only; no OpenAI call', response, displayed,
      }, null, 2));
      await page.screenshot({ path: path.join(output, 'copilot-fallback.png'), fullPage: true });
      assert.match(displayed, /Offline assistant|fallback|not AI-generated|provider unavailable/i);
      assert.equal(await page.getByTestId('copilot-answer').innerText(), response.answer);
    });
  } finally {
    writeFileSync(path.join(output, 'results.json'), JSON.stringify({ testedSHA, results }, null, 2));
    for (const context of contexts) await context.close();
  }
  assert.equal(results.length, 2, 'Both historical slices must run');
  assert.ok(results.every(result => result.status === 'PASS'), 'Unresolved independent DEF-08 slices: ' +
    results.filter(result => result.status === 'FAIL').map(result => result.id).join(', '));
}
