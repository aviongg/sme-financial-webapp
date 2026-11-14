"""Review V52 ACL expectations from frozen migrations and the documented bootstrap defaults.

Never connects to a database, edits migrations, or automatically accepts observed privileges.
"""
import json
from pathlib import Path
import re
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
from backup.catalog import load_manifest, migration_digest

PRIVILEGES={'SELECT','INSERT','UPDATE','DELETE','TRUNCATE','REFERENCES','TRIGGER'}


def declared_grants(tables):
    expected={name:{'finsight_app':{'SELECT','INSERT','UPDATE','DELETE'},
                    'finsight_backup':{'SELECT'},'finsight_migrator':set(PRIVILEGES),'PUBLIC':set()}
              for name in tables}
    migration_dir=ROOT/'backend/src/main/resources/db/migration'
    sources={}
    for file in sorted(migration_dir.glob('V*__*.sql'), key=lambda p:int(p.name.split('__')[0][1:])):
        sql=re.sub(r'--[^\n]*','',file.read_text(encoding='utf-8-sig'))
        for table in re.findall(r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:public\.)?([a-z_]+)',sql,re.I):
            sources[table]=file.name
        pattern=r'\b(GRANT|REVOKE)\s+([A-Z,\s]+?)\s+ON\s+(?:TABLE\s+)?([a-z_.,\s]+?)\s+(?:TO|FROM)\s+((?:finsight_app|finsight_backup|PUBLIC)(?:\s*,\s*(?:finsight_app|finsight_backup|PUBLIC))*)\s*;'
        for operation,permissions,names,roles in re.findall(pattern,sql,re.I):
            perms=PRIVILEGES if permissions.strip().upper() in ('ALL','ALL PRIVILEGES') else {p.strip().upper() for p in permissions.split(',')}
            names=[n.strip().removeprefix('public.') for n in names.split(',')]
            if not all(n in expected for n in names):
                continue # Explicit non-table grants (schema/functions) are covered separately by the catalog.
            if not perms.issubset(PRIVILEGES):
                raise ValueError('Unsupported table ACL statement in '+file.name)
            for role in roles.split(','):
                role=role.strip().upper() if role.strip().upper()=='PUBLIC' else role.strip().lower()
                for name in names:
                    if operation.upper()=='GRANT': expected[name][role].update(perms)
                    else: expected[name][role].difference_update(perms)
    sources['flyway_schema_history']='Flyway; V15 explicitly revokes runtime access; backup SELECT retained'
    return expected,sources


def review():
    manifest=load_manifest()
    migrations=ROOT/'backend/src/main/resources/db/migration'
    assert manifest['migration_sha256']==migration_digest(migrations),'Frozen migration sources differ'
    catalog=manifest['catalog']
    expected,sources=declared_grants(catalog['tables'])
    findings=[]
    lines=['# V52 production grant acceptance inventory','',
           '84 tables are checked against explicit V1–V52 GRANT/REVOKE statements plus the existing '
           'bootstrap default runtime DML and backup SELECT. This documents current migration behavior, '
           'not a new accounting policy. Migrator owns the schema/domain objects. Backup has no mutation/DDL privileges. '
           'Trigger guards are verified separately; an ACL permitting UPDATE does not override an append-only trigger.', '',
           '| Table | Source migration | Runtime ACL | Backup ACL | Trigger count |','|---|---|---|---|---|']
    for name,table in sorted(catalog['tables'].items()):
        for role in ('finsight_app','finsight_backup','finsight_migrator'):
            if set(table['grants'][role]) != expected[name][role]:findings.append((name,role))
        if set(table['public_grants'])!=expected[name]['PUBLIC']:findings.append((name,'PUBLIC'))
        lines.append(f"| {name} | {sources.get(name,'UNREVIEWED')} | {', '.join(table['grants']['finsight_app']) or 'none'} | {', '.join(table['grants']['finsight_backup'])} | {len(table['triggers'])} |")
    lines += ['', '**Owner decision retained:** V49 `estimate_commercial_links` inherits direct DELETE from '
              'the default runtime DML grants: its explicit REVOKE removes UPDATE/TRUNCATE, not DELETE. '
              'V49 documents draft-deletion cascading, but does not explicitly settle direct lineage deletion. '
              'No SQL is changed; Claude/product owner should confirm the intended direct-DELETE contract '
              'before any V53+ proposal. The acceptance inventory records the actual declared grants rather '
              'than silently inventing a stronger policy.', '',
              'Fresh disposable catalog agrees with the explicit migration/default ACL model: '+str(not findings)+'. '
              'Production PostgreSQL 17/TLS and actual operator provisioning remain separate acceptance gates.']
    if findings:raise AssertionError('Observed grant defects versus migration contract: '+str(findings))
    output=ROOT/'docs/release/V52_GRANT_INVENTORY.md'
    output.write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print('Reviewed all',len(catalog['tables']),'table ACLs against frozen migration statements; no ACL mismatch')


if __name__=='__main__': review()
