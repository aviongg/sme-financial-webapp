"""Evidence logging must not turn a failing process or skipped gate into success."""
from pathlib import Path
import subprocess
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[2]


class EvidenceTests(unittest.TestCase):
    def test_v3_requires_normal_and_historical_gates(self):
        branch = 'integration/finsight-production-candidate-v3'
        for filename in ('validation.yml', 'security.yml', 'historical-browser.yml'):
            workflow = yaml.safe_load((ROOT / '.github/workflows' / filename).read_text())
            # PyYAML's YAML 1.1 loader treats the unquoted GitHub `on` key as True.
            trigger = workflow.get('on', workflow.get(True))
            self.assertIn(branch, trigger['push']['branches'], filename)
            self.assertIn('pull_request', trigger, filename)
        normal = yaml.safe_load((ROOT / '.github/workflows/validation.yml').read_text())
        browser = normal['jobs']['browser-smoke']['steps']
        self.assertTrue(any(s.get('run') == 'pnpm test:browser:provider-disclosure' for s in browser))
        self.assertTrue(any(s.get('run') == 'pnpm test:demo-standalone' for s in browser))
        product = next(s for s in browser if s.get('run', '').startswith('pnpm test:browser:product'))
        self.assertIn(branch, product['if'])
        self.assertIn(branch, product['run'])
        self.assertIn('--candidate-v2', product['run'])
        historical = yaml.safe_load((ROOT / '.github/workflows/historical-browser.yml').read_text())
        self.assertEqual('bash', historical['defaults']['run']['shell'])
        steps = historical['jobs']['historical-browser']['steps']
        self.assertTrue(any(s.get('run') == 'node tests/run-historical-browser-reconciliation.mjs' for s in steps))
        for step in steps:
            self.assertFalse(step.get('continue-on-error', False))

    def test_logged_ci_pipelines_use_pipefail_and_have_no_continue_on_error(self):
        workflow = yaml.safe_load((ROOT / '.github/workflows/validation.yml').read_text())
        self.assertEqual('bash', workflow['defaults']['run']['shell'])
        for job in workflow['jobs'].values():
            self.assertFalse(job.get('continue-on-error', False))
            for step in job['steps']:
                self.assertFalse(step.get('continue-on-error', False))
                if '| tee ' in step.get('run', ''):
                    self.assertEqual('bash', step.get('shell', 'bash'))

    def test_every_uploaded_release_evidence_artifact_retains_ninety_days(self):
        for file in (ROOT / '.github/workflows').glob('*.yml'):
            workflow = yaml.safe_load(file.read_text())
            for job in workflow['jobs'].values():
                for step in job['steps']:
                    if step.get('uses', '').startswith('actions/upload-artifact@'):
                        self.assertEqual(90, step['with']['retention-days'], str(file))
                        self.assertEqual('always()', step['if'])
