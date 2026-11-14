"""Build one candidate from a clean commit and recorded base digests; never push/deploy.

On first use resolves each unchanged Dockerfile base tag into a digest lock. Use
--base-lock on subsequent builds to reuse the exact reviewed base selection.
Package-manager OS indexes and remote fonts still prevent a bit-identical claim.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
IMAGES = ('backend', 'frontend', 'ai', 'nginx', 'postgres', 'ocr-egress')
DIGEST = re.compile(r'[a-z0-9./:_-]+@sha256:[0-9a-f]{64}\Z')


def command(args, capture=True, env=None):
    result = subprocess.run(args, cwd=ROOT, check=True, text=True,
                            capture_output=capture, env=env)
    return result.stdout.strip() if capture else None


def declared_bases(text):
    bases = dict(re.findall(r'^ARG ([A-Z_]+_IMAGE)=([^\s]+)$', text, re.M))
    references = set(re.findall(r'^FROM \$\{([A-Z_]+_IMAGE)\}', text, re.M))
    if not bases or references != set(bases):
        raise ValueError('Every external FROM must use a declared base image argument')
    if any(not line.startswith('FROM ${') for line in text.splitlines() if line.startswith('FROM ')):
        raise ValueError('Unrecorded external base image')
    return bases


def resolve_bases(bases, locked=None):
    if locked is not None:
        if (set(locked) != set(bases) or any(not DIGEST.fullmatch(v) for v in locked.values())
                or any(not locked[name].startswith(tag + '@') for name, tag in bases.items())):
            raise ValueError('Invalid/missing digest-locked base image')
        return locked
    result = {}
    for argument, tag in bases.items():
        descriptor = json.loads(command(['docker', 'buildx', 'imagetools', 'inspect', tag,
                                          '--format', '{{json .Manifest}}']))
        value = tag + '@' + descriptor['digest']
        if not DIGEST.fullmatch(value):
            raise ValueError('Registry did not return an immutable base digest')
        result[argument] = value
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('image', choices=IMAGES)
    parser.add_argument('--base-lock', type=Path)
    parser.add_argument('--output', type=Path, help='Default: backend/target/release-images/SHA/IMAGE')
    args = parser.parse_args()
    sha = command(['git', 'rev-parse', 'HEAD'])
    if not re.fullmatch('[0-9a-f]{40}', sha) or command(['git', 'status', '--porcelain']):
        raise ValueError('Release image builds require a clean exact commit')
    if os.environ.get('GITHUB_SHA', sha) != sha:
        raise ValueError('Workflow SHA does not equal checked-out source')
    args.output = args.output or ROOT / 'backend/target/release-images' / sha / args.image
    dockerfile = 'docker/Dockerfile.' + args.image
    bases = declared_bases((ROOT / dockerfile).read_text())
    locked = json.loads(args.base_lock.read_text()) if args.base_lock else None
    bases = resolve_bases(bases, locked)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / 'base-images.json').write_text(json.dumps(bases, indent=2))
    tag = f'finsight-{args.image}:{sha}'
    epoch = command(['git', 'show', '-s', '--format=%ct', sha])
    build = ['docker', 'buildx', 'build', '--load', '--platform', 'linux/amd64',
             '--metadata-file', str(args.output / 'build-metadata.json'),
             '--iidfile', str(args.output / 'image-id.txt'), '-f', dockerfile, '-t', tag,
             '--label', 'org.opencontainers.image.revision=' + sha,
             '--label', 'org.opencontainers.image.source=https://github.com/aviongg/sme-financial-webapp',
             '--build-arg', 'SOURCE_DATE_EPOCH=' + epoch]
    for name, value in sorted(bases.items()):
        build += ['--build-arg', name + '=' + value]
    build.append('.')
    env = {**os.environ, 'BUILDX_METADATA_PROVENANCE': 'max'}
    # Evidence is written before execution so failed builds retain reviewed inputs.
    (args.output / 'build-inputs.json').write_text(json.dumps({
        'source_sha': sha, 'tag': tag, 'platform': 'linux/amd64', 'source_date_epoch': epoch,
        'base_images': bases, 'docker_version': command(['docker', 'version', '--format', '{{json .}}']),
        'buildx_version': command(['docker', 'buildx', 'version']),
        'production_release_accepted': False}, indent=2))
    command(build, capture=False, env=env)
    (args.output / 'image-inspect.json').write_text(command(['docker', 'image', 'inspect', tag]))
    print('Candidate built with recorded base digests; no registry push or deployment: ' + tag)


if __name__ == '__main__':
    main()
