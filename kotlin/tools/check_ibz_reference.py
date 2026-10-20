#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Cross-check the independent ibz, number theory, fixed-point and lattice fixtures against pinned C at every level."""

import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile

from check_unsigned_reference import REFERENCE_COMMIT, REFERENCE_FILES
from generate_ibz_vectors import CAPACITIES, ROOT, records
from generate_fixed_point_vectors import OUTPUT as FIXED_POINT_OUTPUT
from generate_lattice_vectors import OUTPUT as LATTICE_OUTPUT
from generate_number_theory_vectors import OUTPUT as NUMBER_THEORY_OUTPUT


INVALID_INPUTS = ("add\n", "add 9:1\n", "add 0:1 2:1\n", "add 2:x 2:1\n", "nope 2:1\n", "rand 1:0 2:1 00\n", "gcd 2:1\n",
                  "invmat 2:1 2:1 2:1 2:1\n", "prime 2:1 1\n",
                  "shr 2:1\n", "set 1\n", "rand 2:1\n", "parse\n", "modui 2:1\n", "bits 3\n")


def compile_adapter(repository, directory, level):
    binary = Path(directory) / f"ibz-reference-{level}"
    includes = ("include", "src/mp/ref/include", "src/common/generic/include", f"src/precomp/ref/{level}/include",
                "src/quaternion/ref/include", "src/quaternion/ref/lvlx/internal_quaternion_headers")
    subprocess.run([
        "cc", "-std=c11", "-O2", "-ftrivial-auto-var-init=pattern", "-ffunction-sections", "-fdata-sections",
        "-DRADIX_64", f"-DSQISIGN_VARIANT={level}", "-DSQISIGN_BUILD_TYPE_REF",
        *(f"-I{repository / include}" for include in includes),
        str(ROOT / "tools/ibz_reference.c"),
        "-Wl,-dead_strip" if sys.platform == "darwin" else "-Wl,--gc-sections",
        "-o", str(binary),
    ], check=True)
    return binary


def check_invalid_inputs(binary):
    for index, data in enumerate(INVALID_INPUTS):
        result = subprocess.run([str(binary)], input=data, text=True, capture_output=True)
        if result.returncode == 0 or "Invalid ibz adapter input" not in result.stderr:
            raise SystemExit(f"C ibz adapter did not reject malformed input {index}")


def fixture_records(path):
    return [line.strip()[1:-2] for line in path.read_text().splitlines() if line.startswith('        "')]


def main():
    repository = ROOT.parent
    for path, expected in REFERENCE_FILES.items():
        if hashlib.sha256((repository / path).read_bytes()).hexdigest() != expected:
            raise SystemExit(f"{path} differs from pinned C reference {REFERENCE_COMMIT}")
    for generator in ("generate_ibz_vectors.py", "generate_number_theory_vectors.py",
                      "generate_fixed_point_vectors.py", "generate_lattice_vectors.py"):
        subprocess.run([sys.executable, "-B", str(ROOT / "tools" / generator), "--check"], check=True)
    grouped = {level: [] for level in CAPACITIES}
    for record in list(records()) + fixture_records(NUMBER_THEORY_OUTPUT) + fixture_records(FIXED_POINT_OUTPUT) + \
            fixture_records(LATTICE_OUTPUT):
        query, expected = record.split(" = ")
        op, level, *arguments = query.split(" ")
        grouped[level].append((" ".join((op, *arguments)), expected.removeprefix("! ")))
    with tempfile.TemporaryDirectory(prefix="sqisign-ibz-") as directory:
        for level, cases in grouped.items():
            binary = compile_adapter(repository, directory, level)
            result = subprocess.run([str(binary)], input="\n".join(query for query, _ in cases) + "\n",
                                    text=True, capture_output=True)
            actual = result.stdout.splitlines()
            if result.returncode != 0 or len(actual) != len(cases):
                raise SystemExit(f"C ibz oracle stopped after {len(actual)} of {len(cases)} {level} records: "
                                 f"{cases[len(actual)][0][:80] if len(actual) < len(cases) else ''}\n"
                                 f"{result.stderr[-400:]}")
            for (query, wanted), observed in zip(cases, actual):
                if observed != wanted:
                    raise SystemExit(f"C/Python ibz mismatch at {level}: {query[:80]}\n  C: {observed[:200]}\n"
                                     f"  Python: {wanted[:200]}")
            check_invalid_inputs(binary)
    print(f"Checked {sum(len(cases) for cases in grouped.values())} ibz, number theory, fixed-point and lattice records against pinned C")

if __name__ == "__main__":
    main()
