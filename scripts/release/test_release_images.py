"""Release input regression: a mutable/missing base must never masquerade as a digest lock."""
from pathlib import Path
import unittest
from unittest.mock import patch
from build_release_image import declared_bases, resolve_bases, ROOT, IMAGES


class ReleaseImageTests(unittest.TestCase):
    def test_every_dockerfile_has_digest_injection_for_every_external_stage(self):
        for image in IMAGES:
            with self.subTest(image=image):
                self.assertTrue(declared_bases((ROOT / ('docker/Dockerfile.' + image)).read_text()))

    def test_mutable_and_missing_locks_are_rejected_without_registry_queries(self):
        for locked in ({}, {'NODE_IMAGE': 'node:22-alpine'}, {'OTHER_IMAGE': 'node@sha256:' + 'a'*64}):
            with self.subTest(locked=locked), self.assertRaises(ValueError):
                resolve_bases({'NODE_IMAGE': 'node:22-alpine'}, locked)

    def test_existing_digest_lock_is_reused_without_resolving_a_mutable_tag(self):
        locked = {'NODE_IMAGE': 'node:22-alpine@sha256:' + 'a'*64}
        with patch('build_release_image.command', side_effect=AssertionError('Unexpected registry lookup')):
            self.assertEqual(locked, resolve_bases({'NODE_IMAGE': 'node:22-alpine'}, locked))
