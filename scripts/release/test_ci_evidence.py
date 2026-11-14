"""Evidence logging must not turn a failing process or skipped gate into success."""
from pathlib import Path
import subprocess
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[2]


class EvidenceTests(unittest.TestCase):
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
