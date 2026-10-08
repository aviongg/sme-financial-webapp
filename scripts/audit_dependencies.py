"""Read-only OSV lookup of exact resolved package metadata, never credentials/code."""
import collections
import datetime
import json
import os
import pathlib
import urllib.request
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[1]
output = root / 'backend/target/closure-evidence'
output.mkdir(parents=True, exist_ok=True)
packages = set()
lock = json.loads((root / 'frontend/package-lock.json').read_text())
for path, item in lock.get('packages', {}).items():
    if 'node_modules/' in path and item.get('version'):
        name = path.rsplit('node_modules/', 1)[1]
        packages.add(('npm', name, item['version']))
for line in (root / 'ai-service/requirements-tested.txt').read_text().splitlines():
    if '==' in line and not line.startswith('#'):
        name, version = line.strip().split('==', 1)
        packages.add(('PyPI', name, version))
report = max((root / 'backend/target/surefire-reports').glob('TEST-*.xml'), key=lambda p: p.stat().st_mtime)
tree = ET.parse(report)
classpath = next(p.get('value') for p in tree.findall('.//property') if p.get('name') == 'java.class.path')
for path in classpath.split(os.pathsep):
    normalized = path.replace('\\', '/')
    if '/.m2/repository/' not in normalized or not normalized.endswith('.jar'):
        continue
    parts = normalized.split('/.m2/repository/', 1)[1].split('/')
    group, artifact, version = '.'.join(parts[:-3]), parts[-3], parts[-2]
    packages.add(('Maven', group + ':' + artifact, version))

ordered = sorted(packages)
results = []
errors = []
for start in range(0, len(ordered), 100):
    batch = ordered[start:start+100]
    payload = {'queries': [{'package': {'ecosystem': eco, 'name': name}, 'version': version} for eco, name, version in batch]}
    req = urllib.request.Request('https://api.osv.dev/v1/querybatch', data=json.dumps(payload).encode(), headers={'Content-Type': 'application/json'}, method='POST')
    try:
        with urllib.request.urlopen(req, timeout=45) as response:
            data = json.load(response)
        if len(data['results']) != len(batch):
            raise ValueError('OSV returned an incomplete batch')
        for package, result in zip(batch, data['results']):
            if result.get('vulns'):
                results.append({'ecosystem': package[0], 'name': package[1], 'version': package[2], 'ids': [v['id'] for v in result['vulns']]})
    except Exception as exc:
        errors.append({'batch': start, 'error': str(exc)})

details = []
for vuln_id in sorted({v for r in results for v in r['ids']}):
    try:
        with urllib.request.urlopen('https://api.osv.dev/v1/vulns/' + vuln_id, timeout=30) as response:
            vulnerability = json.load(response)
        details.append(vulnerability)
    except Exception as exc:
        errors.append({'id': vuln_id, 'error': str(exc)})

summary = {'source': 'OSV API', 'checked_at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'scope': 'npm lock including dev/optional; pinned Python; Maven resolved test classpath including runtime and test dependencies. Not reachability or container scanning.', 'queried_counts': dict(collections.Counter(p[0] for p in ordered)), 'package_matches': results, 'errors': errors}
(output / 'dependency-audit.json').write_text(json.dumps(summary, indent=2))
(output / 'dependency-advisories.json').write_text(json.dumps(details, indent=2))
print(json.dumps(summary, indent=2))
raise SystemExit(2 if errors else 1 if results else 0)
