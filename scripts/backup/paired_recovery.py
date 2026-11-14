#!/usr/bin/env python3
"""Capture a quiescent V52 recovery set, or restore it into fresh staging DB/volume."""
import argparse
import json
import os
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from backup.recovery_set import capture, restore_pair
from backup.restore_database import require_password


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=['capture', 'restore'])
    parser.add_argument('--container', default='finsight-postgres-prod')
    parser.add_argument('--no-container', action='store_true')
    parser.add_argument('--host', default='postgres.finsight.internal')
    parser.add_argument('--port', type=int, default=5432)
    parser.add_argument('--admin-user', default='finsight_dba')
    parser.add_argument('--admin-password-file', required=True)
    parser.add_argument('--backup-password-file')
    parser.add_argument('--database')
    parser.add_argument('--document-root')
    parser.add_argument('--secrets-dir')
    parser.add_argument('--recipient-file')
    parser.add_argument('--output-parent')
    parser.add_argument('--stopped-writers-ticket')
    parser.add_argument('--recovery-set')
    parser.add_argument('--identity-file')
    parser.add_argument('--document-output-parent')
    args = parser.parse_args()
    connection = dict(container=None if args.no_container else args.container, host=args.host,
                      port=args.port, admin_user=args.admin_user,
                      admin_password=require_password(None, args.admin_password_file))
    try:
        if args.operation == 'capture':
            if not all((args.recipient_file, args.output_parent, args.document_root, args.secrets_dir,
                        args.database, args.backup_password_file, args.stopped_writers_ticket)):
                parser.error('capture needs recipient/output/document/secrets/database/backup-password/ticket arguments')
            result = capture(Path(args.recipient_file).read_text().strip(), args.output_parent,
                             args.document_root, args.secrets_dir, args.stopped_writers_ticket,
                             dbname=args.database, backup_password=require_password(
                                 None, args.backup_password_file, 'BACKUP_DB_PASSWORD'), **connection)
        else:
            if not all((args.recovery_set, args.identity_file, args.document_output_parent)):
                parser.error('restore needs recovery-set/identity/document-output-parent arguments')
            result = restore_pair(args.recovery_set, Path(args.identity_file).read_text().strip(),
                                  args.document_output_parent, **connection)
        print(json.dumps(result, indent=2))
        return 0
    except Exception as error:
        # SQL, key, document and driver diagnostics can contain confidential values.
        print(json.dumps({'status': 'FAILED', 'error_type': type(error).__name__,
                          'message': 'Paired recovery did not pass; inspect trusted inputs and acceptance gates'}), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
