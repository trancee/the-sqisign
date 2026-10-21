#!/usr/bin/env python3
"""Compares a kotlinx-benchmark JSON report with the committed baseline (Constitution P4).

Usage: compare.py BASELINE.json CANDIDATE.json [--max-regression PERCENT]

Exits 1 if any benchmark in the baseline is missing from the candidate or is slower than the baseline score by more
than the allowed regression (default 10 %). Results are compared only when unit and mode match.
"""

import argparse
import json
import sys


def load(path):
    with open(path, encoding="utf-8") as report:
        entries = json.load(report)
    results = {}
    for entry in entries:
        params = ",".join(f"{key}={value}" for key, value in sorted(entry.get("params", {}).items()))
        name = ".".join(entry["benchmark"].rsplit(".", 2)[-2:]) + f"[{params}]"
        metric = entry["primaryMetric"]
        results[name] = (entry["mode"], metric["scoreUnit"], metric["score"], metric.get("scoreError"))
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("baseline")
    parser.add_argument("candidate")
    parser.add_argument("--max-regression", type=float, default=10.0, help="allowed slowdown in percent")
    arguments = parser.parse_args()

    baseline = load(arguments.baseline)
    candidate = load(arguments.candidate)
    failures = 0
    print(f"{'benchmark':<52} {'baseline':>10} {'candidate':>10} {'change':>8}")
    for name, (mode, unit, base_score, _) in sorted(baseline.items()):
        if name not in candidate:
            print(f"{name:<52} {base_score:>10.3f} {'missing':>10}")
            failures += 1
            continue
        candidate_mode, candidate_unit, score, error = candidate[name]
        if (candidate_mode, candidate_unit) != (mode, unit):
            print(f"{name:<52} incomparable: {mode} {unit} vs {candidate_mode} {candidate_unit}")
            failures += 1
            continue
        change = (score - base_score) / base_score * 100
        regressed = change > arguments.max_regression
        failures += regressed
        error_text = f" ±{error:.3f}" if isinstance(error, float) else ""
        verdict = "  REGRESSION" if regressed else ""
        print(f"{name:<52} {base_score:>10.3f} {score:>10.3f} {change:>+7.1f}%{error_text} {unit}{verdict}")
    if failures:
        print(f"{failures} benchmark(s) exceed the {arguments.max_regression:g} % budget or are missing")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
