import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import ts from 'typescript';
import { ORIGINAL_OUTBOUND_ASSERTION, capabilityFixture, copilotFixture, deliveryFixture } from './fixtures/provider-disclosure.mjs';

// DEF-08 provider transparency: per-answer Copilot provenance and WhatsApp delivery provider disclosure (EN/UR).
const directory = mkdtempSync(path.join(tmpdir(), 'finsight-provider-disclosure-'));
for (const [name, file] of Object.entries({ disclosure: '../src/lib/providerDisclosure.ts', copy: '../src/lib/i18n/translations/product.ts' })) {
  writeFileSync(path.join(directory, `${name}.js`), ts.transpileModule(readFileSync(new URL(file, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText);
}
const require = createRequire(import.meta.url);
const { copilotProvenance, deliveryProvider, outboundCapability } = require(path.join(directory, 'disclosure.js'));
const { productCopies } = require(path.join(directory, 'copy.js'));
const source = (file) => readFileSync(new URL(file, import.meta.url), 'utf8');
after(() => rmSync(directory, { recursive: true, force: true }));

test('a provider_unavailable deterministic answer under an AI status is a disclosed fallback, never "AI"', () => {
  // The exact shape Codex captured (docs/release/evidence/historical-browser-def08.json): status openai, answer deterministic.
  const response = copilotFixture({ unavailable: true });
  assert.equal(response.mode, 'deterministic');
  assert.deepEqual(copilotProvenance(response, 'openai'), { kind: 'fallback', cause: 'provider' });
  // Even if the status was not loaded or was offline, a per-answer provider failure is still a fallback.
  assert.equal(copilotProvenance(response, undefined).kind, 'fallback');
  assert.equal(copilotProvenance(response, 'deterministic').kind, 'fallback');
  for (const reason of ['provider_error', 'invalid_provider_response', 'provider_interrupted', 'provider_response_too_large', 'context_too_large']) {
    assert.deepEqual(copilotProvenance({ mode: 'deterministic', reasonCode: reason }, 'openai'), { kind: 'fallback', cause: 'provider' }, reason);
  }
  for (const reason of ['provider_circuit_open', 'daily_quota_reached', 'provider_capacity_limited', 'request_in_progress', 'capacity_limited']) {
    assert.deepEqual(copilotProvenance({ mode: 'deterministic', reasonCode: reason }, 'openai'), { kind: 'fallback', cause: 'quota' }, reason);
  }
  assert.deepEqual(copilotProvenance({ mode: 'deterministic', reasonCode: 'ungrounded_answer' }, 'openai'), { kind: 'fallback', cause: 'ungrounded' });
  assert.deepEqual(copilotProvenance({ mode: 'deterministic', reasonCode: 'tool_budget_exceeded' }, 'openai'), { kind: 'fallback', cause: 'tool_budget' });
  // Key missing at call time although the status advertised the AI assistant.
  assert.deepEqual(copilotProvenance({ mode: 'deterministic', reasonCode: 'provider_not_configured' }, 'openai'), { kind: 'fallback', cause: 'provider' });
  // No reason, but the AI assistant was expected: still not presented as AI.
  assert.equal(copilotProvenance({ mode: 'deterministic', reasonCode: null }, 'openai').kind, 'fallback');
});

test('provider-generated, offline, unsupported and limited answers keep accurate labels', () => {
  assert.deepEqual(copilotProvenance(copilotFixture(), 'openai'), { kind: 'ai', cause: null });
  assert.equal(copilotProvenance({ mode: 'openai', reasonCode: null }, 'deterministic').kind, 'ai', 'The response, not the status, decides');
  // Offline assistant by configuration (status deterministic): intended mode, not a failure.
  assert.deepEqual(copilotProvenance({ mode: 'deterministic', reasonCode: null }, 'deterministic'), { kind: 'offline', cause: null });
  for (const reason of ['ai_disabled', 'provider_not_configured', 'activation_pending', 'pilot_not_enabled', 'demo_not_enabled', 'simulation_prohibited', 'provider_not_supported']) {
    assert.equal(copilotProvenance({ mode: 'deterministic', reasonCode: reason }, 'deterministic').kind, 'offline', reason);
  }
  assert.equal(copilotProvenance(copilotFixture({ unsupported: true }), 'deterministic').kind, 'offline');
  assert.deepEqual(copilotProvenance(copilotFixture({ limited: true }), 'openai'), { kind: 'limited', cause: null });
  assert.equal(copilotProvenance(copilotFixture({ limited: true }), 'deterministic').kind, 'limited');
});

test('WhatsApp delivery providers are classified from providerName only', () => {
  assert.equal(deliveryProvider('mock'), 'mock');
  assert.equal(deliveryProvider(' MOCK '), 'mock');
  assert.equal(deliveryProvider('meta'), 'meta');
  assert.equal(deliveryProvider('disabled'), 'disabled');
  for (const value of [null, undefined, '', 'twilio']) assert.equal(deliveryProvider(value), 'unknown', String(value));
  assert.equal(deliveryProvider(deliveryFixture({ providerName: 'mock' }).providerName), 'mock');
});

test('EN and UR disclose every provenance, cause and provider; mock is explicitly not transmitted', () => {
  for (const locale of ['en', 'ur']) {
    const c = productCopies[locale];
    for (const kind of ['ai', 'fallback', 'offline', 'limited']) assert.ok(c.copilot.provenance[kind], `${locale} provenance.${kind}`);
    for (const cause of ['provider', 'quota', 'ungrounded', 'tool_budget']) assert.ok(c.copilot.fallbackCause[cause], `${locale} fallbackCause.${cause}`);
    for (const provider of ['meta', 'mock', 'disabled', 'unknown']) assert.ok(c.whatsappDelivery.providers[provider], `${locale} providers.${provider}`);
    assert.notEqual(c.copilot.provenance.fallback, c.copilot.provenance.ai);
    assert.notEqual(c.copilot.provenance.fallback, c.copilot.modes.openai, 'Fallback is not labelled with the AI assistant heading');
  }
  const en = productCopies.en;
  // Codex's DEF-08 browser assertion: /Offline assistant|not AI-generated|provider unavailable|fallback/i
  assert.match(en.copilot.provenance.fallback, /not AI-generated/);
  assert.match(en.copilot.fallbackCause.provider, /provider was unavailable/);
  assert.match(en.copilot.provenance.offline, /Offline assistant/);
  assert.doesNotMatch(en.copilot.provenance.fallback, /^AI-generated/);
  assert.match(en.whatsappDelivery.providers.mock, /Simulated/);
  assert.match(en.whatsappDelivery.providers.mock, /no WhatsApp message was transmitted/);
  const ur = productCopies.ur;
  for (const text of [ur.copilot.provenance.fallback, ur.copilot.fallbackCause.provider, ur.whatsappDelivery.providers.mock, ur.whatsappDelivery.providers.disabled]) {
    assert.match(text, /[؀-ۿ]/, 'Urdu is actually translated');
  }
  assert.match(ur.copilot.provenance.fallback, /نہیں/);
  assert.match(ur.whatsappDelivery.providers.mock, /فرضی/);
});

test('views classify each answer and each delivery from its own fields', () => {
  const ops = source('../src/components/product/OperationsViews.tsx');
  assert.match(ops, /copilotProvenance\(r, status\.data\?\.mode\)/);
  assert.match(ops, /data-testid="copilot-provenance"/);
  assert.match(ops, /c\.provenance\[p\.kind\]/);
  assert.match(ops, /c\.fallbackCause\[p\.cause\]/);
  // Answer text, evidence and exact values are rendered unchanged.
  assert.match(ops, /data-testid="copilot-answer">\{t\.r\.answer\}/);
  assert.match(ops, /<bdi dir="ltr">\{e\.value\}<\/bdi>/);
  const settings = source('../src/components/integration/LiveSettings.tsx');
  assert.match(settings, /providerName: string \| null;/);
  assert.match(settings, /deliveryProvider\(d\.providerName\)/);
  assert.match(settings, /data-testid="whatsapp-delivery-provider"/);
  assert.match(settings, /liveLabel\(t, d\.deliveryStatus\)/, 'The actual delivery status is still shown');
});

test('outbound capability comes only from the capability response; missing or unrecognised is unknown', () => {
  assert.deepEqual(outboundCapability(capabilityFixture('mock')), { provider: 'mock', automatic: false });
  assert.deepEqual(outboundCapability(capabilityFixture('mock', true)), { provider: 'mock', automatic: true });
  assert.deepEqual(outboundCapability(capabilityFixture('meta', true)), { provider: 'meta', automatic: true });
  assert.deepEqual(outboundCapability(capabilityFixture('meta', false)), { provider: 'meta', automatic: false });
  // Disabled can never send, whatever the scheduler flag says.
  assert.deepEqual(outboundCapability(capabilityFixture('disabled', true)), { provider: 'disabled', automatic: false });
  // A failed request, an empty body or an unknown transport makes no claim at all.
  for (const value of [null, undefined, {}, capabilityFixture('twilio', true), capabilityFixture(null, true)]) {
    assert.deepEqual(outboundCapability(value), { provider: 'unknown', automatic: null }, JSON.stringify(value));
  }
  assert.deepEqual(outboundCapability({ provider: 'meta', automaticWeeklySummaries: 'yes' }), { provider: 'meta', automatic: null });
});

test('the outbound card copy discloses every state in EN and UR; Meta is configuration, not proof of delivery', () => {
  for (const locale of ['en', 'ur']) {
    const c = productCopies[locale].whatsappDelivery.outbound;
    assert.ok(c.title && c.automaticOn && c.automaticOff, locale);
    for (const provider of ['meta', 'mock', 'disabled', 'unknown']) assert.ok(c.providers[provider], `${locale} outbound.${provider}`);
    assert.equal(new Set(Object.values(c.providers)).size, 4, `${locale}: every state is distinct`);
  }
  const en = productCopies.en.whatsappDelivery.outbound;
  // The unchanged independent assertion is satisfied by the mock and disabled disclosures themselves.
  assert.match(en.providers.mock, ORIGINAL_OUTBOUND_ASSERTION);
  assert.match(en.providers.mock, /No WhatsApp messages are transmitted/);
  assert.match(en.providers.disabled, ORIGINAL_OUTBOUND_ASSERTION);
  assert.match(en.providers.meta, /WhatsApp Cloud API \(Meta\)/);
  assert.match(en.providers.meta, /does not confirm that any message was transmitted or delivered/);
  assert.doesNotMatch(en.providers.meta, /(is|are|were|was) (sent|delivered)/i, 'Configured Meta never claims delivery');
  assert.doesNotMatch(en.providers.unknown, ORIGINAL_OUTBOUND_ASSERTION, 'Unknown is not presented as mock or disabled');
  assert.match(en.providers.unknown, /could not be confirmed/);
  const ur = productCopies.ur.whatsappDelivery.outbound;
  for (const text of [ur.title, ...Object.values(ur.providers), ur.automaticOn, ur.automaticOff]) assert.match(text, /[؀-ۿ]/, 'Urdu is actually translated');
  assert.match(ur.providers.mock, /فرضی/);
  assert.match(ur.providers.disabled, /بند/);
  assert.match(ur.providers.meta, /تصدیق نہیں/);
});

test('the WhatsApp updates card renders the capability inside the outbound form, independent of history and consent', () => {
  const settings = source('../src/components/integration/LiveSettings.tsx');
  assert.match(settings, /apiClient<OutboundCapabilityResponse>\('\/whatsapp\/delivery-capability'/);
  assert.match(settings, /outbound: outboundCapability\(capability\)/);
  // A failed capability request degrades to unknown instead of failing the settings page; aborts still propagate.
  assert.match(settings, /\.catch\(\(e: unknown\) => \{ if \(e instanceof Error && e\.name === 'AbortError'\) throw e; return null; \}\)/);
  // Inside the same form as the "WhatsApp updates" heading (the independent QA reads the heading's parent).
  assert.match(settings, /<h2 className="font-semibold">\{t\.live\.whatsapp\}<\/h2><div role="note" aria-label=\{outboundCopy\.title\}[^>]*data-testid="whatsapp-outbound-provider" data-provider=\{outbound\.provider\}>/);
  assert.match(settings, /outboundCopy\.providers\[outbound\.provider\]/);
  // Not derived from consent, the module toggle or delivery rows.
  const helper = source('../src/lib/providerDisclosure.ts');
  const body = helper.slice(helper.indexOf('export function outboundCapability'));
  assert.doesNotMatch(body, /optIn|whatsappOptIn|deliveries|available\(|configuration/);
  // Consent, module gating and per-delivery labels are unchanged.
  assert.match(settings, /disabled=\{!editable \|\| busy \|\| !enabled\}/);
  assert.match(settings, /deliveryProvider\(d\.providerName\)/);
});
