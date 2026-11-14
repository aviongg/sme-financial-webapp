"""Report-gate regressions: stale output and nested skips must never manufacture green CI."""
import os
from pathlib import Path
import tempfile
import time
import unittest
from postgres_gate import read_reports


class ReportGateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)

    def report(self, name, tests=1, skipped=0, failures=0):
        path = self.directory / f"TEST-example.{name}.xml"
        path.write_text(f'<testsuite name="A display name" tests="{tests}" skipped="{skipped}" failures="{failures}" errors="0"/>')
        return path

    def test_fresh_named_class_is_covered_without_relying_on_display_name(self):
        self.report("ExampleIT", tests=3)
        totals, covered = read_reports(self.directory, ["ExampleIT"], 0)
        self.assertEqual({"ExampleIT"}, covered)
        self.assertEqual(3, totals["tests"])

    def test_stale_green_report_cannot_cover_a_missing_execution(self):
        old = self.report("ExampleIT")
        os.utime(old, (0, 0))
        totals, covered = read_reports(self.directory, ["ExampleIT"], time.time_ns())
        self.assertEqual(set(), covered)
        self.assertEqual(0, totals["tests"])

    def test_nested_skip_remains_visible_when_parent_has_no_tests(self):
        self.report("ExampleIT", tests=0)
        self.report("ExampleIT$Cases", tests=4, skipped=1)
        totals, covered = read_reports(self.directory, ["ExampleIT"], 0)
        self.assertEqual({"ExampleIT"}, covered)
        self.assertEqual(4, totals["tests"])
        self.assertEqual(1, totals["skipped"])

    def test_unselected_reports_cannot_inflate_coverage_or_totals(self):
        self.report("OtherIT", tests=20)
        self.report("ExampleIT", tests=1, failures=1)
        totals, covered = read_reports(self.directory, ["ExampleIT", "MissingIT"], 0)
        self.assertEqual({"ExampleIT"}, covered)
        self.assertEqual(1, totals["tests"])
        self.assertEqual(1, totals["failures"])
