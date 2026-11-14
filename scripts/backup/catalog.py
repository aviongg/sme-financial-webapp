"""V52 structural/grant inventory queries. No domain rows or credentials are returned."""
import hashlib
import json
import copy
from pathlib import Path

MANIFEST = Path(__file__).with_name('manifests') / 'v52.json'
CATALOG_SQL = MANIFEST.with_name('catalog.sql').read_text(encoding='utf-8')


def load_manifest():
    return json.loads(MANIFEST.read_text(encoding='utf-8'))


def compare_catalog(actual, expected=None, ignore_owners=False):
    """Exact known public objects, column/constraint/index/trigger definitions and grants."""
    expected = (expected or load_manifest())['catalog']
    if ignore_owners:
        actual, expected = copy.deepcopy(actual), copy.deepcopy(expected)
        for catalog in (actual, expected):
            catalog.pop('schema_owner', None)
            catalog.get('schema_grants', {}).pop('finsight_migrator', None)
            for section in ('tables', 'sequences', 'functions'):
                for obj in catalog.get(section, {}).values():
                    obj.pop('owner', None)
                    obj.get('grants', {}).pop('finsight_migrator', None)
    problems = []
    for field in ('schema_owner', 'schema_grants', 'extensions', 'tables', 'sequences', 'functions'):
        if ignore_owners and field == 'schema_owner':
            continue
        if field in ('tables', 'sequences', 'functions'):
            got, wanted = actual.get(field, {}), expected[field]
            for name in sorted(set(got) | set(wanted)):
                if got.get(name) != wanted.get(name):
                    problems.append(f'{field}:{name}')
        elif actual.get(field) != expected[field]:
            problems.append(field)
    return problems


def migration_digest(directory):
    return {p.name: hashlib.sha256(p.read_text(encoding='utf-8-sig').replace('\r\n','\n').encode()).hexdigest()
            for p in sorted(directory.glob('V*__*.sql'))}
