#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Cross-check independent torsion-ring fixtures against pinned C integer/matrix operations."""

import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile

from check_unsigned_reference import REFERENCE_COMMIT, REFERENCE_FILES
from generate_torsion_ring_vectors import (
    ROOT, encode, matrix_cases, matrix_record, scalar_cases, scalar_record,
)


RING_REFERENCE_FILES = {
    "src/quaternion/ref/lvlx/dim2.c": "5c85f4d579f6f1f962ea657f08268f174ade22160799fb61303966727db9b73c",
    "src/quaternion/ref/include/quaternion.h": "8338ee13b480aa92b4c156ba2ea7124e6936a98220cfeb553a5699ad308ebd3f",
    "src/common/generic/include/tools.h": "c3472971869c8e5911d762b64aaadbdca051b801bb4ec6e87afdeec2c907c6e0",
    "src/quaternion/ref/lvlx/internal_quaternion_headers/internal.h": "ff11b05b8687071c4e3233ce7a69d04ae025207efb3cdebc5d7eeacd81af1b9e",
    "src/precomp/ref/p664_17/include/quaternion_data.h": "ba37603b3a2f5f46b90bc4a33d6d25ca37347e52a307056a24a9e3a61958556a",
    "src/precomp/ref/p664_17/include/quaternion_constants.h": "bfd50a0767c92bf4d3613a9b8d5c1c17127756c1f1a42343edb92e446b945ba4",
}
INCLUDES = (
    "include", "src/mp/ref/include", "src/common/generic/include",
    "src/precomp/ref/p664_17/include", "src/quaternion/ref/include",
    "src/quaternion/ref/lvlx/internal_quaternion_headers",
)
INVALID_REASON = "Invalid or incomplete canonical torsion ring operands"


def check_invalid_inputs(binary):
    invalid = (
        "\n", "S\n", "S 9\n", "S 9 0100\n", "M 9 0100 0000 0000\n",
        "X 9 0100 0000\n", "SS 9 0100 0000\n",
        "S -1 01 00\n", "S 0 01 00\n", "S 665 01 00\n",
        "S 99999999999999999999 01 00\n", "S q 01 00\n", "S +9 0100 0000\n",
        "S 9 01 0000\n", "S 9 010000 0000\n", "S 9 gg00 0000\n",
        "S 9 0002 0000\n", "S 9 0100 0002\n", "S 9 0100 0000 extra\n",
        "M 9 0100 0000 0000 0002\n", "M 9 0100 0000 0000 0100 extra\n",
        "S 664 " + "ff" * 84 + " " + "00" * 83 + "\n",
    )
    for data in invalid:
        result = subprocess.run([str(binary)], input=data, text=True, capture_output=True)
        if result.returncode == 0 or INVALID_REASON not in result.stderr or result.stdout:
            raise SystemExit(f"C torsion adapter did not reject malformed input {invalid.index(data)}")


def main():
    repository = ROOT.parent
    for path, expected in (REFERENCE_FILES | RING_REFERENCE_FILES).items():
        if hashlib.sha256((repository / path).read_bytes()).hexdigest() != expected:
            raise SystemExit(f"{path} differs from pinned C reference {REFERENCE_COMMIT}")
    subprocess.run([sys.executable, "-B", str(ROOT / "tools/generate_torsion_ring_vectors.py"),
                    "--check"], check=True)
    scalars = list(scalar_cases())
    matrices = list(matrix_cases())
    inputs = [
        f"S {e} {encode(e, left)} {encode(e, right)}" for e, left, right in scalars
    ] + [
        f"M {e} " + " ".join(encode(e, value) for value in matrix) for e, matrix in matrices
    ]
    expected = [scalar_record(*case) for case in scalars] + [
        matrix_record(*case) for case in matrices
    ]
    with tempfile.TemporaryDirectory(prefix="sqisign-torsion-ring-") as directory:
        binary = Path(directory) / "torsion-ring-reference"
        subprocess.run([
            "cc", "-std=c11", "-O2", "-ffunction-sections", "-fdata-sections",
            "-DRADIX_64", "-DSQISIGN_VARIANT=p664_17", "-DSQISIGN_BUILD_TYPE_REF",
            *(f"-I{repository / include}" for include in INCLUDES),
            str(ROOT / "tools/torsion_ring_reference.c"),
            "-Wl,-dead_strip" if sys.platform == "darwin" else "-Wl,--gc-sections",
            "-o", str(binary),
        ], check=True)
        result = subprocess.run([str(binary)], input="\n".join(inputs) + "\n",
                                text=True, capture_output=True, check=True)
        actual = result.stdout.splitlines()
        if len(actual) != len(expected):
            raise SystemExit("C torsion ring oracle returned an incomplete record set")
        for index, (observed, wanted) in enumerate(zip(actual, expected)):
            if observed != wanted:
                raise SystemExit(f"C/Python torsion ring mismatch at record {index}")
        check_invalid_inputs(binary)
    print(f"Checked {len(scalars)} scalar and {len(matrices)} matrix records against pinned C")


if __name__ == "__main__":
    main()
