import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync, readdirSync } from 'node:fs';

// Source-level regression guards for the responsive navigation / RTL hardening pass.
// Real geometry is verified separately in the browser; these keep the fixes from silently regressing.
const source = (file) => readFileSync(new URL(file, import.meta.url), 'utf8');

test('sidebar collapse state is owned by AppShell and the content offset follows it in both directions', () => {
  const shell = source('../src/components/layout/AppShell.tsx');
  const sidebar = source('../src/components/layout/Sidebar.tsx');
  assert.match(shell, /const \[isSidebarCollapsed, setIsSidebarCollapsed\] = useState\(false\)/);
  assert.match(shell, /<Sidebar\s+isCollapsed=\{isSidebarCollapsed\}\s+onToggleCollapsed=/);
  assert.match(shell, /isSidebarCollapsed \? "md:ms-\[72px\]" : "md:ms-\[256px\]"/);
  // No fixed physical gutter that ignores the collapsed width.
  assert.doesNotMatch(shell, /md:(ml|mr)-\[256px\]/);
  // Sidebar no longer keeps an independent collapse state.
  assert.doesNotMatch(sidebar, /useState/);
  assert.match(sidebar, /isCollapsed \? "w-\[72px\]" : "w-\[256px\]"/);
  assert.match(sidebar, /onClick=\{onToggleCollapsed\}/);
});

test('mobile action sheet is bounded by the viewport and scrolls internally with a persistent close button', () => {
  const sheet = source('../src/components/ui/Sheet.tsx');
  assert.match(sheet, /max-h-\[calc\(100dvh-1rem\)\]/);
  assert.match(sheet, /flex flex-col/);
  assert.match(sheet, /data-testid="sheet-scroll-region"[\s\S]*overflow-y-auto overflow-x-hidden overscroll-contain/);
  assert.match(sheet, /aria-label="Close sheet"/);
  assert.match(sheet, /e\.key === "Escape"/);
  // The header (title + close) must not scroll away with the actions.
  assert.match(sheet, /justify-between gap-4 mb-4 shrink-0/);
});

test('error text uses the defined status token, never the undefined semantic-danger token', () => {
  const tokens = source('../src/styles/tokens.css');
  assert.match(tokens, /--color-status-error:/);
  assert.doesNotMatch(tokens, /--color-semantic-danger/);
  for (const file of [
    '../src/components/commercial/CommercialDocumentList.tsx',
    '../src/components/projects/ProjectDetailWorkspace.tsx',
    '../src/components/projects/ProjectForm.tsx',
    '../src/components/projects/ProjectList.tsx',
  ]) {
    const text = source(file);
    assert.doesNotMatch(text, /--color-semantic-danger/, file);
    assert.match(text, /text-\[var\(--color-status-error\)\]/, file);
  }
});

test('no component in the integrated tree uses the undefined semantic-danger token', () => {
  const root = new URL('../src/', import.meta.url);
  const files = readdirSync(root, { recursive: true }).filter((file) => /\.(tsx?|css)$/.test(file));
  assert.ok(files.length > 0);
  for (const file of files) assert.doesNotMatch(source(`../src/${file.replaceAll('\\', '/')}`), /--color-semantic-danger/, file);
});

test('sidebar keeps every feature entry and highlights nested feature routes', () => {
  const sidebar = source('../src/components/layout/Sidebar.tsx');
  for (const href of ['/estimates', '/reports', '/projects', '/samples', '/invoices', '/bills', '/inventory']) {
    assert.match(sidebar, new RegExp(`href: '${href}'`), href);
  }
  assert.match(sidebar, /\['\/projects', '\/samples', '\/estimates', '\/invoices', '\/bills', '\/inventory'\]/);
});

test('project back arrows reverse in RTL', () => {
  for (const file of ['../src/components/projects/ProjectForm.tsx', '../src/components/projects/ProjectDetailWorkspace.tsx']) {
    const arrows = source(file).match(/<ArrowLeft className="[^"]*"/g) ?? [];
    assert.ok(arrows.length > 0, file);
    for (const arrow of arrows) assert.match(arrow, /rtl:rotate-180/, `${file}: ${arrow}`);
  }
});

test('page header shows the live active business from the existing session without a new request', () => {
  const header = source('../src/components/layout/PageHeader.tsx');
  assert.match(header, /useSession\(\)/);
  assert.match(header, /isDemoMode \? "Al-Rehman Textiles" : session\.business\?\.businessName \|\| "FinSight"/);
  assert.match(header, /data-testid="header-business-name"/);
  assert.doesNotMatch(header, /apiClient|fetch\(/);
  // Settings relies on the header language control instead of rendering a duplicate.
  const settings = source('../src/components/integration/LiveSettings.tsx');
  assert.doesNotMatch(settings, /LanguageControl/);
  assert.match(source('../src/components/integration/LivePage.tsx'), /<AppShell/);
  assert.match(header, /<LanguageControl \/>/);
});

test('settings and business setup controls cannot widen the mobile page', () => {
  const settings = source('../src/components/integration/LiveSettings.tsx');
  assert.match(settings, /<Select label=\{t\.live\.activeBusiness\}/);
  assert.match(source('../src/components/ui/Select.tsx'), /w-full min-w-0 flex flex-col/);
  const team = source('../src/components/integration/TeamAccess.tsx');
  assert.match(team, /<Select label=\{t\.live\.role\}/);
  assert.match(team, /<select className="border rounded-lg p-2 min-w-0 max-w-full"/);
  const panel = source('../src/components/configuration/BusinessConfigurationPanel.tsx');
  // Fieldsets default to min-width:min-content and are a classic cause of horizontal overflow.
  for (const fieldset of panel.match(/<fieldset[^>]*>/g) ?? []) assert.match(fieldset, /min-w-0/, fieldset);
  assert.match(panel, /data-testid="operating-profile" className="[^"]*w-full min-w-0 max-w-full/);
  const setup = source('../src/components/auth/BusinessSetup.tsx');
  for (const fieldset of setup.match(/<fieldset[^>]*>/g) ?? []) assert.match(fieldset, /min-w-0/, fieldset);
  for (const select of setup.match(/<select className="[^"]*"/g) ?? []) assert.match(select, /w-full max-w-full/, select);
});
