#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check affine Python curve vectors against the checksum-pinned C reference."""

import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile

from check_unsigned_reference import REFERENCE_COMMIT, REFERENCE_FILES
from generate_curve_vectors import PRIME, ROOT, curve_records, encode_field


CURVE_REFERENCE_FILES = {
    "src/ec/ref/lvlx/ec.c": "44074c5fd519249aaf15666154a6a2f390c316cc7de5075bc596aec2fa93a32f",
    "src/ec/ref/include/ec.h": "b152e9c7778a9209e6ef48afeef487369239ecf47792cc26d95034a0e509710c",
    "src/precomp/ref/p324_3/include/ec_params.h": "851c7148cc1c106c922e961ca21ff9777129330b8889a17b9779edf23d4caf61",
    "src/common/generic/include/tools.h": "c3472971869c8e5911d762b64aaadbdca051b801bb4ec6e87afdeec2c907c6e0",
    "include/ct_testing.h": "0c546fc703780cf5d86ddead5c89e7391c4b832d5709f6f04782b3cfec2d2cb5",
}


def check_invalid_inputs(binary, valid_fields):
    cases = [
        ({0: "g" * 164}, "Invalid or incomplete canonical curve operands"),
        ({0: (PRIME.to_bytes(41, "little") + bytes(41)).hex()},
         "Invalid or incomplete canonical curve operands"),
        ({8: "00"}, "Invalid or incomplete unsigned 328-bit scalar"),
        ({1: encode_field((0, 0))}, "Invalid zero curve denominator"),
        ({0: encode_field((2, 0)), 1: encode_field((1, 0))}, "Invalid singular curve"),
        ({2: encode_field((0, 0)), 3: encode_field((0, 0))},
         "Invalid projective or exceptional differential operands"),
        ({4: encode_field((0, 0)), 5: encode_field((0, 0))},
         "Invalid projective or exceptional differential operands"),
        ({6: encode_field((0, 0))}, "Invalid projective or exceptional differential operands"),
        ({7: encode_field((0, 0))}, "Invalid projective or exceptional differential operands"),
    ]
    for replacements, reason in cases:
        fields = valid_fields.copy()
        for index, value in replacements.items():
            fields[index] = value
        result = subprocess.run([str(binary)], input=" ".join(fields) + "\n",
                                capture_output=True, text=True)
        if result.returncode == 0 or reason not in result.stderr or result.stdout:
            raise SystemExit(f"C curve adapter did not reject invalid input: {reason}")
    for length in (1, 8):
        result = subprocess.run([str(binary)], input=" ".join(valid_fields[:length]),
                                capture_output=True, text=True)
        if result.returncode == 0 or "Invalid or incomplete" not in result.stderr or result.stdout:
            raise SystemExit("C curve adapter did not reject truncated input")


def main():
    repository = ROOT.parent
    for path, expected in (REFERENCE_FILES | CURVE_REFERENCE_FILES).items():
        actual = hashlib.sha256((repository / path).read_bytes()).hexdigest()
        if actual != expected:
            raise SystemExit(f"{path} differs from pinned C reference {REFERENCE_COMMIT}; review before rebasing")
    subprocess.run([sys.executable, "-B", str(ROOT / "tools/generate_curve_vectors.py"), "--check"],
                   check=True)
    records = [record.split(":") for record in curve_records()]
    inputs = "\n".join(" ".join(fields[:9]) for fields in records) + "\n"
    includes = (
        "include", "src/mp/ref/include", "src/common/generic/include",
        "src/gf/ref/include", "src/precomp/ref/p324_3/include", "src/ec/ref/include",
    )
    with tempfile.TemporaryDirectory(prefix="sqisign-curve-") as directory:
        binary = Path(directory) / "curve-reference"
        subprocess.run([
            "cc", "-std=c11", "-O2", "-ffunction-sections", "-fdata-sections",
            "-DRADIX_64", "-DSQISIGN_VARIANT=p324_3", "-DSQISIGN_BUILD_TYPE_REF",
            *(f"-I{repository / include}" for include in includes),
            str(ROOT / "tools/curve_reference.c"),
            "-Wl,-dead_strip" if sys.platform == "darwin" else "-Wl,--gc-sections",
            "-o", str(binary),
        ], check=True)
        check_invalid_inputs(binary, records[0][:9])
        result = subprocess.run([str(binary)], input=inputs, capture_output=True,
                                text=True, check=True)
    expected = [":".join(fields[9:]) for fields in records]
    if result.stdout.splitlines() != expected:
        raise SystemExit("C/Python p324_3 curve differential mismatch")
    print(f"Cross-checked {len(records)} affine curve records against C {REFERENCE_COMMIT}")


if __name__ == "__main__":
    main()
