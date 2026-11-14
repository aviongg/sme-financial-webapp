import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync } from 'node:fs';
import { test } from 'node:test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const fonts = path.join(root, 'src/app/fonts');

test('release fonts and OFL licenses match the immutable source inventory', () => {
  const manifest = JSON.parse(readFileSync(path.join(fonts, 'manifest.json'), 'utf8'));
  assert.match(manifest.commit, /^[a-f0-9]{40}$/);
  assert.equal(manifest.files.filter(f => f.file.endsWith('.ttf')).length, 3);
  for (const asset of manifest.files) {
    const bytes = readFileSync(path.join(fonts, asset.file));
    assert.equal(bytes.length, asset.bytes, asset.file);
    assert.equal(createHash('sha256').update(bytes).digest('hex'), asset.sha256, asset.file);
    assert.ok(asset.source.startsWith(`https://raw.githubusercontent.com/google/fonts/${manifest.commit}/`));
    assert.equal(asset.license, 'OFL-1.1');
  }
});

test('application font imports cannot depend on live Google Fonts during a build', () => {
  for (const entry of readdirSync(path.join(root, 'src'), { recursive: true, withFileTypes: true })) {
    if (entry.isFile() && /\.[jt]sx?$/.test(entry.name)) {
      const source = readFileSync(path.join(entry.parentPath, entry.name), 'utf8');
      assert.ok(!source.includes('next/font/google'), entry.name);
    }
  }
});
