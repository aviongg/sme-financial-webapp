"""Paired V52 recovery: trusted encrypted DB + every document + historical key bundle.

Capture requires stopped writers and an operator maintenance ticket. Before/after
fingerprints detect drift; they do not prove there were no transient writes. Keep
the manifest owner-controlled: SHA-256 alone is not producer authentication.
"""
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import uuid

from backup.crypto_age import encrypt_bytes, decrypt_bytes, encrypt_stream, decrypt_stream
from backup.catalog import CATALOG_SQL, compare_catalog
from backup.backup_database import run_backup
from backup.backup_keyring import run_keyring_backup
from backup.restore_database import (REQUIRED_TABLES, RestoreError, run_restore,
                                     execute_sql, execute_sql_query, validate_identifier)

FORMAT = 'finsight-v52-paired-recovery-1'
EPHEMERAL = {'spring_session', 'spring_session_attributes', 'password_reset_tokens'}


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(65536), b''):
            digest.update(chunk)
    return digest.hexdigest()


def safe_relative(value):
    # Backup tooling containment only; this does not change application path semantics.
    path = PurePosixPath(value)
    if (not value or '\\' in value or ':' in value or path.is_absolute() or
            any(p in ('', '.', '..') for p in value.split('/'))):
        raise RestoreError('Unsafe document/snapshot relative path')
    return path


def document_inventory(root):
    root = Path(root)
    if not root.is_dir() or root.is_symlink():
        raise RestoreError('Document source must be a real directory')
    result = {}
    for path in sorted(root.rglob('*')):
        mode = path.lstat().st_mode
        if stat.S_ISLNK(mode) or not (stat.S_ISREG(mode) or stat.S_ISDIR(mode)):
            raise RestoreError('Document snapshot refuses links and special files')
        if stat.S_ISREG(mode):
            name = path.relative_to(root).as_posix()
            safe_relative(name)
            result[name] = {'size': path.stat().st_size, 'sha256': sha256(path)}
    return result


def fingerprints(query):
    result = {}
    for table in sorted(REQUIRED_TABLES - EPHEMERAL):
        validate_identifier(table)
        predicate = " WHERE status <> 'PENDING'" if table == 'user_mfa' else ''
        result[table] = json.loads(query(f"""SET TIME ZONE 'UTC';
            SELECT json_build_object('rows', count(*), 'sha256',
              encode(digest(COALESCE(string_agg(h, '' ORDER BY h), ''), 'sha256'), 'hex'))
            FROM (SELECT encode(digest(row_to_json(t)::text,'sha256'),'hex') h
                  FROM public."{table}" t{predicate}) rows;"""))
    return result


def verify_metadata(query, documents):
    rows = json.loads(query("""SELECT COALESCE(json_agg(json_build_object(
        'path',storage_path,'size',file_size_bytes)), '[]'::json) FROM public.uploaded_documents;"""))
    for row in rows:
        path = row['path']
        if path is None or row['size'] is None:
            raise RestoreError('Document metadata without path/size blocks paired acceptance')
        safe_relative(path)
        if path not in documents or documents[path]['size'] != row['size']:
            raise RestoreError('Document metadata has no matching snapshot bytes/size')
    return len(rows)


def capture(recipient, output_parent, document_root, secrets_dir, ticket, *, container,
            host, port, dbname, backup_password, admin_user, admin_password):
    if not ticket or not ticket.strip():
        raise RestoreError('A stopped-writers maintenance ticket/attestation is required')
    # One new immutable set; never merge/prune older backups or write into document source.
    parent, source = Path(output_parent).resolve(), Path(document_root).resolve()
    if parent == source or source in parent.parents:
        raise RestoreError('Recovery output cannot be inside the source document volume')
    bundle = parent / ('finsight_recovery_' + uuid.uuid4().hex)
    bundle.mkdir(parents=True, exist_ok=False, mode=0o700)
    query = lambda sql: execute_sql_query(container, host, port, dbname, admin_user, admin_password, sql)
    try:
        if compare_catalog(json.loads(query(CATALOG_SQL))):
            raise RestoreError('Source does not match reviewed V52 catalog/grants')
        before, files = fingerprints(query), document_inventory(source)
        metadata_count = verify_metadata(query, files)
        db = run_backup(recipient, bundle / 'database', container=container, host=host,
                        port=port, dbname=dbname, user='finsight_backup', password=backup_password)
        keys = run_keyring_backup(recipient, bundle / 'keyring', secrets_dir=Path(secrets_dir))
        documents = bundle / 'documents'
        documents.mkdir(mode=0o700)
        for number, (name, record) in enumerate(sorted(files.items())):
            archive = documents / f'{number:08d}.age'
            with (source / name).open('rb') as plaintext, archive.open('xb') as encrypted:
                encrypt_stream(plaintext, encrypted, recipient)
            record['artifact'] = archive.relative_to(bundle).as_posix()
            record['encrypted_sha256'] = sha256(archive)
        after_files = document_inventory(source)
        if before != fingerprints(query) or after_files != {
                name: {k: r[k] for k in ('size', 'sha256')} for name, r in files.items()}:
            raise RestoreError('Writers/document changes detected; recovery set is inconsistent')
        manifest = {'format': FORMAT, 'consistency_id': bundle.name, 'quiescence_ticket': ticket,
                    'data': before, 'documents': files, 'metadata_rows': metadata_count}
        for kind, record in (('database', db), ('keyring', keys)):
            path = Path(record['artifact_path'])
            manifest[kind] = {'artifact': path.relative_to(bundle).as_posix(), 'sha256': sha256(path)}
        (bundle / 'recovery-set.age').write_bytes(encrypt_bytes(json.dumps(manifest, sort_keys=True).encode(), recipient))
        return {'status': 'CAPTURED_PAIRED_SET_NOT_YET_RESTORED', 'path': str(bundle / 'recovery-set.age'),
                'consistency_id': bundle.name, 'document_count': len(files)}
    except BaseException:
        # Delete only the unique directory created by this invocation.
        shutil.rmtree(bundle)
        raise


def restore_pair(manifest_path, identity, document_output_parent, *, container, host, port,
                 admin_user, admin_password):
    manifest_path = Path(manifest_path)
    bundle = manifest_path.resolve().parent
    manifest = json.loads(decrypt_bytes(manifest_path.read_bytes(), identity))
    if manifest.get('format') != FORMAT or not manifest.get('quiescence_ticket'):
        raise RestoreError('Unsupported or unattested recovery set')
    if set(manifest['data']) != REQUIRED_TABLES - EPHEMERAL:
        raise RestoreError('Recovery set omits V52 table fingerprints')

    def artifact(record):
        path = bundle / str(safe_relative(record['artifact']))
        if path.is_symlink() or bundle not in path.resolve().parents or not path.is_file():
            raise RestoreError('Missing or unsafe paired artifact')
        if sha256(path) != record['sha256']:
            raise RestoreError('Paired artifact checksum mismatch')
        return path

    db, keys = artifact(manifest['database']), artifact(manifest['keyring'])
    for name, record in manifest['documents'].items():
        safe_relative(name)
        artifact({'artifact': record['artifact'], 'sha256': record['encrypted_sha256']})
    restored = None
    destination = None
    try:
        restored = run_restore(db, identity, container=container, host=host, port=port,
                               admin_user=admin_user, admin_password=admin_password, keyring_file=keys)
        query = lambda sql: execute_sql_query(container, host, port, restored['staging_database'],
                                               admin_user, admin_password, sql)
        if fingerprints(query) != manifest['data']:
            raise RestoreError('Restored V52 data differs from the paired consistency point')
        if verify_metadata(query, manifest['documents']) != manifest['metadata_rows']:
            raise RestoreError('Document metadata count differs from paired capture')
        parent = Path(document_output_parent).resolve()
        parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        destination = parent / ('finsight_restored_documents_' + uuid.uuid4().hex)
        destination.mkdir(mode=0o700)
        for name, record in manifest['documents'].items():
            path = destination / name
            path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            archive = bundle / record['artifact']
            with archive.open('rb') as encrypted, path.open('xb') as plaintext:
                decrypt_stream(encrypted, plaintext, identity)
            os.chmod(path, 0o600)
            if path.stat().st_size != record['size'] or sha256(path) != record['sha256']:
                raise RestoreError('Restored document bytes differ from paired snapshot')
        return {**restored, 'status': 'VERIFIED_PAIRED_STAGING_RESTORE',
                'production_recovery_accepted': False, 'document_root': str(destination),
                'consistency_id': manifest['consistency_id'], 'document_count': len(manifest['documents']),
                'external_acceptance': 'Operator must rehearse app/volume/key mounting, provider access and cutover/rollback'}
    except BaseException:
        cleanup_errors = []
        if destination is not None:
            try:
                shutil.rmtree(destination)
            except OSError:
                cleanup_errors.append('new document directory')
        if restored is not None:
            name = validate_identifier(restored['staging_database'])
            try:
                rc, _, _ = execute_sql(container, host, port, 'postgres', admin_user, admin_password,
                                        f'DROP DATABASE "{name}" WITH (FORCE);')
                if rc:
                    cleanup_errors.append('new staging database')
            except OSError:
                cleanup_errors.append('new staging database')
        if cleanup_errors:
            raise RestoreError('Paired validation failed; cleanup also failed: ' + ', '.join(cleanup_errors))
        raise
