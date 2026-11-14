"""Render every OSV match against a reviewed package decision; never changes scan outcomes."""
import argparse
import hashlib
import json
from pathlib import Path
from packaging.version import Version


def in_events(version, events):
    value = Version(version)
    active = False
    for event in events:
        if 'introduced' in event:
            active = value >= Version(event['introduced'])
        elif 'fixed' in event:
            if active and value < Version(event['fixed']):
                return True
            active = False
        elif 'last_affected' in event:
            if active and value <= Version(event['last_affected']):
                return True
            active = False
        elif 'limit' in event:
            if active and value < Version(event['limit']):
                return True
            active = False
    return active


def affected(record, ecosystem, name, version):
    normalize = lambda n: n.lower().replace('_', '-').replace('.', '-') if ecosystem == 'PyPI' else n
    matches = [a for a in record.get('affected', [])
               if a.get('package', {}).get('ecosystem') == ecosystem
               and normalize(a['package']['name']) == normalize(name)]
    if not matches:
        raise ValueError('Advisory does not describe the queried package')
    unknown = False
    for item in matches:
        if version in item.get('versions', []):
            return True
        for range_ in item.get('ranges', []):
            if range_['type'] not in ('SEMVER', 'ECOSYSTEM'):
                unknown = True
                continue
            if in_events(version, range_['events']):
                return True
    if unknown:
        raise ValueError('Non-version range needs manual review')
    return False


def render(audit_path, advisories_path, decisions_path, output):
    audit = json.loads(audit_path.read_text())
    advisories = {v['id']: v for v in json.loads(advisories_path.read_text())}
    decisions = json.loads(decisions_path.read_text())
    records = []
    for package in audit['package_matches']:
        key = package['ecosystem'] + ':' + package['name']
        decision = decisions[key]
        if decision['from'] != package['version']:
            raise ValueError('Review version differs from audit')
        for ident in package['ids']:
            record = advisories[ident]
            old = affected(record, package['ecosystem'], package['name'], package['version'])
            new = affected(record, package['ecosystem'], package['name'], decision['to'])
            if new:
                raise ValueError('Selected version remains affected: ' + ident)
            matching = [a for a in record.get('affected', []) if
                        a.get('package', {}).get('name', '').lower().replace('_', '-') == package['name'].lower().replace('_', '-')]
            ranges = [r for a in matching for r in a.get('ranges', [])]
            records.append(dict(package=key, advisory=ident, aliases=record.get('aliases', []),
                                published=record.get('published'), modified=record.get('modified'),
                                withdrawn=record.get('withdrawn'), ranges=ranges,
                                current_version=package['version'], genuinely_affected=old and not record.get('withdrawn'),
                                selected_fixed_version=decision['to'], selected_version_affected=new,
                                reachability=decision['reachability'], evidence=decision['evidence'],
                                compatibility_risk=decision['compatibility_risk'],
                                advisory_feature=record.get('summary', ''),
                                url='https://osv.dev/vulnerability/' + ident))
    result = dict(review_date='2026-11-14', authority='OSV package/range records + source call-path review',
                  suppressions=[], input_sha256={str(p.name): hashlib.sha256(p.read_bytes()).hexdigest()
                                                for p in (audit_path, advisories_path)},
                  record_count=len(records), records=records)
    output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    lines = ['# Individual advisory triage — 14 November 2026', '',
             'Every original match is retained below. Version/range matches are separate from '
             'reachability. Unsupported-feature observations do not suppress the strict scanner. '
             'Aliases may describe the same vulnerability. See `OSV_TRIAGE.json` for exact range events, '
             'aliases, timestamps, source hashes and compatibility decisions.', '',
             '| Package | Original → selected | Advisory | Version affected? | Reachability / feature |',
             '|---|---|---|---|---|']
    for r in records:
        feature = r['advisory_feature'].replace('|', '/')
        lines.append(f"| {r['package']} | {r['current_version']} → {r['selected_fixed_version']} | "
                     f"[{r['advisory']}]({r['url']}) | {'yes' if r['genuinely_affected'] else 'no / withdrawn'} | "
                     f"{r['reachability']} — {feature} |")
    output.with_suffix('.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(f'Reviewed {len(records)} advisory records; selected versions outside every affected range; no suppressions')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--audit', type=Path, required=True)
    p.add_argument('--advisories', type=Path, required=True)
    p.add_argument('--decisions', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    render(a.audit, a.advisories, a.decisions, a.output)
