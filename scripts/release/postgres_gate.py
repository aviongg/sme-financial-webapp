"""Run every disposable PostgreSQL IT; production-target suites require their own profile.

No database URL/credential is accepted. New *IT.java classes are discovered automatically.
Run from any directory: python scripts/release/postgres_gate.py [--shard 1/4]
"""
import argparse
from pathlib import Path
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
EXTERNAL = {"DatabaseRolePrivilegesPostgreSqlIT", "PostgreSqlTlsConnectionIT"}


def read_reports(directory, selected, not_before_ns):
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    covered = set()
    for report in directory.glob("TEST-*.xml"):
        if report.stat().st_mtime_ns < not_before_ns:
            continue
        # Include nested JUnit suites even when their display name is not a class name.
        name = report.stem.removeprefix("TEST-").rsplit(".", 1)[-1].split("$", 1)[0]
        if name not in selected:
            continue
        suite = ET.parse(report).getroot()
        covered.add(name)
        for field in totals:
            totals[field] += int(suite.attrib.get(field, "0"))
    return totals, covered


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--shard", default="1/1")
    parser.add_argument("--heap-mb", type=int, help="Optional test heap cap for constrained hosts")
    args = parser.parse_args()
    index, count = map(int, args.shard.split("/"))
    if not 1 <= index <= count:
        parser.error("shard must be INDEX/COUNT with 1 <= INDEX <= COUNT")
    classes = sorted(p.stem for p in (ROOT / "backend/src/test/java").rglob("*IT.java")
                     if p.stem not in EXTERNAL)
    if len(classes) != len(set(classes)):
        parser.error("duplicate IT class names require explicit fully qualified selection")
    selected = classes[index - 1::count]
    if not selected:
        parser.error("empty shard")
    print(f"Disposable PostgreSQL shard {index}/{count}: {len(selected)}/{len(classes)} classes", flush=True)
    print("External, NOT passed: " + ", ".join(sorted(EXTERNAL)), flush=True)
    wrapper = "mvnw.cmd" if os.name == "nt" else "./mvnw"
    # Explicit Maven path is an operator convenience, never required in CI.
    command = [os.environ.get("FINSIGHT_MAVEN", wrapper), "-B", "-Ppostgres-it",
               "-Dtest=NONE", "-Dsurefire.failIfNoSpecifiedTests=false",
               "-Dit.test=" + ",".join(selected), "verify"]
    if args.heap_mb is not None:
        if not 256 <= args.heap_mb <= 8192:
            parser.error("heap-mb must be between 256 and 8192")
        command.insert(-1, f"-Dfinsight.test.jvm.args=-Xmx{args.heap_mb}m -XX:+UseSerialGC")
    started_at = time.time_ns()
    result = subprocess.run(command, cwd=ROOT / "backend")
    if result.returncode:
        return result.returncode
    totals, covered = read_reports(ROOT / "backend/target/failsafe-reports", selected, started_at)
    print(f"Selected report totals: {totals}", flush=True)
    if covered != set(selected) or not totals["tests"] or any(totals[k] for k in ("failures", "errors", "skipped")):
        print("Missing, skipped or failing disposable ITs; gate refused", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
