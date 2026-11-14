"""Adversarial paired artifact tests. Real age/bytes, mocked PostgreSQL boundary."""
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from backup import recovery_set as r
from backup.catalog import load_manifest
from backup.crypto_age import generate_keypair, encrypt_bytes, decrypt_bytes


class PairedTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.public, self.identity = generate_keypair()
        self.source = self.root / 'source'
        self.source.mkdir()
        (self.source / 'fixture.pdf').write_bytes(b'Synthetic fixture, no customer data')
        self.data = {name: {'rows': 0, 'sha256': 'a' * 64} for name in r.REQUIRED_TABLES - r.EPHEMERAL}
        self.connection = dict(container=None, host='localhost', port=1,
                               admin_user='fixture_dba', admin_password='synthetic-fixture')

    def capture(self):
        def artifact(recipient, directory, **kwargs):
            directory.mkdir(parents=True)
            path = directory / 'fixture.age'
            path.write_bytes(encrypt_bytes(b'Synthetic artifact', recipient))
            return {'artifact_path': str(path)}
        with patch.object(r, 'execute_sql_query', return_value=json.dumps(load_manifest()['catalog'])), \
             patch.object(r, 'fingerprints', return_value=self.data), \
             patch.object(r, 'verify_metadata', return_value=1), \
             patch.object(r, 'run_backup', side_effect=artifact), \
             patch.object(r, 'run_keyring_backup', side_effect=artifact):
            return Path(r.capture(self.public, self.root / 'sets', self.source, self.root / 'keys',
                                  'SYNTHETIC-WRITERS-STOPPED', dbname='synthetic',
                                  backup_password='synthetic-fixture', **self.connection)['path'])

    def manifest(self, path):
        return json.loads(decrypt_bytes(path.read_bytes(), self.identity))

    def write_manifest(self, path, manifest):
        path.write_bytes(encrypt_bytes(json.dumps(manifest).encode(), self.public))

    def restore(self, path, **changes):
        with patch.object(r, 'run_restore', return_value={'staging_database': 'finsight_restore_fixture'}) as restore, \
             patch.object(r, 'execute_sql_query', return_value='[]'), \
             patch.object(r, 'fingerprints', return_value=changes.get('data', self.data)), \
             patch.object(r, 'verify_metadata', return_value=1), \
             patch.object(r, 'execute_sql', return_value=(0, '', '')) as cleanup:
            try:
                result = r.restore_pair(path, self.identity, self.root / 'restored', **self.connection)
            except Exception:
                self.cleanup = cleanup.call_args_list
                self.restore_calls = restore.call_count
                raise
            self.cleanup = cleanup.call_args_list
            return result

    def test_round_trip_encrypts_every_document_and_restores_new_volume(self):
        path = self.capture()
        self.assertNotIn(b'Synthetic fixture', path.read_bytes())
        result = self.restore(path)
        self.assertEqual('VERIFIED_PAIRED_STAGING_RESTORE', result['status'])
        self.assertFalse(result['production_recovery_accepted'])
        self.assertEqual((self.source / 'fixture.pdf').read_bytes(),
                         (Path(result['document_root']) / 'fixture.pdf').read_bytes())
        self.assertEqual([], self.cleanup)

    def test_missing_document_fails_before_any_database_creation(self):
        path = self.capture()
        (path.parent / self.manifest(path)['documents']['fixture.pdf']['artifact']).unlink()
        with self.assertRaises(r.RestoreError): self.restore(path)
        self.assertEqual(0, self.restore_calls)

    def test_cross_paired_keyring_fails_before_database_creation(self):
        path = self.capture()
        (path.parent / self.manifest(path)['keyring']['artifact']).write_bytes(encrypt_bytes(b'Other keys', self.public))
        with self.assertRaises(r.RestoreError): self.restore(path)
        self.assertEqual(0, self.restore_calls)

    def test_any_v52_data_mismatch_removes_only_created_staging(self):
        path = self.capture()
        altered = copy.deepcopy(self.data)
        altered['inventory_items']['rows'] = 1
        with self.assertRaises(r.RestoreError): self.restore(path, data=altered)
        self.assertEqual(1, len(self.cleanup))
        self.assertIn('DROP DATABASE "finsight_restore_fixture"', self.cleanup[0].args[-1])

    def test_bad_plaintext_document_hash_cleans_new_db_and_volume(self):
        path = self.capture()
        manifest = self.manifest(path)
        manifest['documents']['fixture.pdf']['sha256'] = '0' * 64
        self.write_manifest(path, manifest)
        with self.assertRaises(r.RestoreError): self.restore(path)
        self.assertEqual(1, len(self.cleanup))
        self.assertEqual([], list((self.root / 'restored').iterdir()))

    def test_missing_new_table_fingerprint_is_refused(self):
        path = self.capture()
        manifest = self.manifest(path)
        manifest['data'].pop('inventory_items')
        self.write_manifest(path, manifest)
        with self.assertRaises(r.RestoreError): self.restore(path)
        self.assertEqual(0, self.restore_calls)

    def test_relative_path_traversal_and_windows_absolute_paths_are_refused(self):
        for value in ('../outside', '/outside', 'C:/outside', 'a/../b', 'a\\b', 'a//b', ''):
            with self.subTest(value=value), self.assertRaises(r.RestoreError): r.safe_relative(value)

    def test_metadata_null_or_missing_bytes_blocks_full_claim(self):
        for row in ({'path': None, 'size': 1}, {'path': 'missing.pdf', 'size': 1},
                    {'path': 'fixture.pdf', 'size': 999}):
            with self.subTest(row=row), self.assertRaises(r.RestoreError):
                r.verify_metadata(lambda _: json.dumps([row]), r.document_inventory(self.source))

    def test_empty_or_wrong_consistency_attestation_is_refused(self):
        path = self.capture()
        manifest = self.manifest(path)
        manifest['quiescence_ticket'] = ''
        self.write_manifest(path, manifest)
        with self.assertRaises(r.RestoreError): self.restore(path)

    def test_interrupted_capture_removes_only_its_own_new_set(self):
        with patch.object(r, 'execute_sql_query', return_value=json.dumps(load_manifest()['catalog'])), \
             patch.object(r, 'fingerprints', side_effect=[self.data, {}]), \
             patch.object(r, 'verify_metadata', return_value=0), \
             patch.object(r, 'run_backup', side_effect=RuntimeError('synthetic interrupted capture')):
            with self.assertRaises(RuntimeError):
                r.capture(self.public, self.root / 'sets', self.source, self.root / 'keys', 'TEST',
                          dbname='synthetic', backup_password='fixture', **self.connection)
        self.assertEqual([], list((self.root / 'sets').iterdir()))
