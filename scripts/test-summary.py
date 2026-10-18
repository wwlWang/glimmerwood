#!/usr/bin/env python3
"""Print JUnit and GameTest totals from the Gradle reports (used by scripts/check.sh)."""
import glob
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parent.parent
failed = False

def count(paths):
    tests = failures = skipped = 0
    names = []
    for path in paths:
        tree = ET.parse(path).getroot()
        suites = [tree] if tree.tag == "testsuite" else list(tree.iter("testsuite"))
        for suite in suites:
            for case in suite.iter("testcase"):
                tests += 1
                if case.find("failure") is not None or case.find("error") is not None:
                    failures += 1
                    names.append(f"{case.get('classname', '')}.{case.get('name')}")
                elif case.find("skipped") is not None:
                    skipped += 1
    return tests, failures, skipped, names

for label, pattern in (("JUnit", "build/test-results/test/*.xml"), ("GameTest", "build/test-results/gametest.xml")):
    paths = sorted(glob.glob(str(root / pattern)))
    if not paths:
        print(f"{label}: no report ({pattern})")
        failed = True
        continue
    tests, failures, skipped, names = count(paths)
    print(f"{label}: {tests} tests, {failures} failed, {skipped} skipped")
    for name in names:
        print(f"  FAILED {name}")
    failed |= failures > 0 or tests == 0
sys.exit(1 if failed else 0)
