#!/usr/bin/env python3
"""Summarizes XCTest measure() output (xcodebuild or swift test log) as a Markdown table in milliseconds.

Usage: python3 benchmarks/xctest_summary.py <log> [--raw <out.txt>]

--raw writes the matched measurement lines, so the summary can be recomputed from the committed file.
"""
import re
import statistics
import sys

PATTERN = re.compile(r"(test\w+)\]' measured \[Clock Monotonic Time, s\] .*?values: \[([0-9., ]+)\]")


def main(argv: list[str]) -> int:
    if len(argv) not in (2, 4) or (len(argv) == 4 and argv[2] != "--raw"):
        print(__doc__, file=sys.stderr)
        return 2
    with open(argv[1], encoding="utf-8") as log:
        lines = [line.strip() for line in log if PATTERN.search(line)]
    if not lines:
        print("no XCTest measurements found", file=sys.stderr)
        return 1
    if len(argv) == 4:
        with open(argv[3], "w", encoding="utf-8") as raw:
            raw.write("\n".join(lines) + "\n")
    print("| Benchmark | n | Median ms | Mean ms | Min ms | Max ms |")
    print("|---|---|---|---|---|---|")
    for line in lines:
        name, values = PATTERN.search(line).groups()
        ms = [float(v) * 1000 for v in values.split(",")]
        print(
            f"| `{name}` | {len(ms)} | {statistics.median(ms):.1f} | {statistics.mean(ms):.1f} "
            f"| {min(ms):.1f} | {max(ms):.1f} |"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
