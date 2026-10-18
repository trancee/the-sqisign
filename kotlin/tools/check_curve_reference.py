#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check affine Python curve vectors against the checksum-pinned C reference."""

import hashlib
from itertools import permutations
from pathlib import Path
import subprocess
import sys
import tempfile

from check_unsigned_reference import REFERENCE_COMMIT, REFERENCE_FILES
from generate_curve_vectors import ONE, ORACLES, ROOT, ZERO
from generate_basis_vectors import e0_records, general_basis_records, reconstruction_records
from check_pairing_reference import check_discrete_logs, check_tate_pairings
from check_basis_transform_reference import check_basis_actions, check_basis_changes, check_correspondences


CURVE_REFERENCE_FILES = {
    "src/ec/ref/include/biextension.h": "6f291dc964bd7645e2d823bafcee10c675dc8a6768fc272ff052f78c3300a020",
    "src/precomp/ref/p324_3/ec_params.c": "0b21ea624239408a1f093f9e4923cf5abe5d609ada6d3d255adf61be04352230",
    "src/precomp/ref/p500_27/ec_params.c": "990b2f5701e10a6ab81dcdb2de406f8d536366029b2ec259715e469b754d20b1",
    "src/precomp/ref/p664_17/ec_params.c": "e1218026d4df90aa89ca0424faf712dfeb39a638068e516dd8296b7b80daa986",
    "scripts/precomp/torsion_basis.py": "6bedf4e5e2b9837e8352160c4b2b38e13f285c84391e70c838dbf10667d9e6cc",
    "src/precomp/ref/p324_3/e0_basis.c": "7d634fc3a0d482ed0acce33ffa2d0c0f9406b51436a8bfa52288d0cafa330690",
    "src/precomp/ref/p500_27/e0_basis.c": "a66affb7a6917981976c40dc8d44ac4f5f11cdddda1b847af8a444907313fd73",
    "src/precomp/ref/p664_17/e0_basis.c": "51330ad7944f9aaf3dbed609e24eaf841e2841e11efab045ea64eddee33e8fbc",
    "src/ec/ref/lvlx/biextension.c": "c0a9a5b6eb6d08922802f596729a95417c7a3435000aea11b523cc7aa08520e0",
    "src/ec/ref/lvlx/basis.c": "d3741b10f3aa2660226c302db034f583e7f10775ec31370eb381657048ff9989",
    "src/precomp/ref/p324_3/include/e0_basis.h": "cc68b12e6c4e2cfc4e8b7e02cbedfb4950d7f5c27bc9afca720f8ea30c882b94",
    "src/ec/ref/lvlx/ec.c": "44074c5fd519249aaf15666154a6a2f390c316cc7de5075bc596aec2fa93a32f",
    "src/ec/ref/include/ec.h": "b152e9c7778a9209e6ef48afeef487369239ecf47792cc26d95034a0e509710c",
    "src/precomp/ref/p324_3/include/ec_params.h": "851c7148cc1c106c922e961ca21ff9777129330b8889a17b9779edf23d4caf61",
    "src/precomp/ref/p500_27/include/ec_params.h": "db5d1e13de4ea535b7421e3d48b1967cd600dc6f854bf35ace2aaff8aed1905a",
    "src/precomp/ref/p664_17/include/ec_params.h": "33cf69a3623d093e2de2e3ba9ebfce30a0d1bcdcf53e1025c238896d91d11ef7",
    "src/precomp/ref/p500_27/include/e0_basis.h": "cc68b12e6c4e2cfc4e8b7e02cbedfb4950d7f5c27bc9afca720f8ea30c882b94",
    "src/precomp/ref/p664_17/include/e0_basis.h": "cc68b12e6c4e2cfc4e8b7e02cbedfb4950d7f5c27bc9afca720f8ea30c882b94",
    "src/common/generic/include/tools.h": "c3472971869c8e5911d762b64aaadbdca051b801bb4ec6e87afdeec2c907c6e0",
    "include/ct_testing.h": "0c546fc703780cf5d86ddead5c89e7391c4b832d5709f6f04782b3cfec2d2cb5",
}


def check_invalid_inputs(binary, valid_fields, oracle):
    width = oracle.width
    encode_field = oracle.encode_field
    cases = [
        ({0: "g" * (4 * width)}, "Invalid or incomplete canonical curve operands"),
        ({0: "0" * (4 * width + 2)}, "Invalid or incomplete canonical curve operands"),
        ({0: (oracle.prime.to_bytes(width, "little") + bytes(width)).hex()},
         "Invalid or incomplete canonical curve operands"),
        ({8: "00"}, f"Invalid or incomplete unsigned {8 * width}-bit scalar"),
        ({9: "00"}, f"Invalid or incomplete unsigned {8 * width}-bit scalar"),
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
    for length in (1, 8, 9):
        result = subprocess.run([str(binary)], input=" ".join(valid_fields[:length]),
                                capture_output=True, text=True)
        if result.returncode == 0 or "Invalid or incomplete" not in result.stderr or result.stdout:
            raise SystemExit("C curve adapter did not reject truncated input")


def check_two_torsion(binary, oracle):
    inputs, expected = [], []
    encode_field = oracle.encode_field
    for left, right, difference in permutations(((0, 0), (0, 1), (0, oracle.prime - 1))):
        for odd_left, odd_right in ((0, 0), (0, 1), (1, 0), (1, 1)):
            values = ((0, 0), (1, 0), left, (1, 0), right, (1, 0), difference, (1, 0))
            scalars = [((1 << (8 * oracle.width)) - 1 if odd else 0)
                       .to_bytes(oracle.width, "little").hex()
                       for odd in (odd_left, odd_right)]
            inputs.append(" ".join([*(encode_field(value) for value in values), *scalars]))
            result = difference if odd_left and odd_right else left if odd_left else right
            expected.append(encode_field(result) if odd_left or odd_right else "-")
    actual = subprocess.run([str(binary), "--two-torsion"], input="\n".join(inputs) + "\n",
                            capture_output=True, text=True, check=True)
    if actual.stdout.splitlines() != expected:
        raise SystemExit("C two-torsion basis/parity mismatch")


def check_shared_differences(binary, oracle):
    records = [record.split(":") for record in oracle.shared_difference_records()]
    result = subprocess.run([str(binary), "--shared-difference"],
                            input="\n".join(" ".join(fields[:10]) for fields in records) + "\n",
                            capture_output=True, text=True, check=True)
    if result.stdout.splitlines() != [fields[10] for fields in records]:
        raise SystemExit(f"C/Python {oracle.identifier} shared-difference mismatch")
    valid = records[0][:10]
    cases = [
        ({0: "gg"}, "Invalid or incomplete shared-difference operands"),
        ({0: "0" * (4 * oracle.width + 2)}, "Invalid or incomplete shared-difference operands"),
        ({0: (oracle.prime.to_bytes(oracle.width, "little") + bytes(oracle.width)).hex()},
         "Invalid or incomplete shared-difference operands"),
        ({1: oracle.encode_field(ZERO)}, "Invalid shared-difference curve"),
        ({0: oracle.encode_field((2, 0)), 1: oracle.encode_field(ONE)},
         "Invalid shared-difference curve"),
        ({2: oracle.encode_field(ZERO), 3: oracle.encode_field(ZERO)},
         "Invalid shared-difference point"),
    ]
    for replacements, reason in cases:
        fields = valid.copy()
        for index, value in replacements.items():
            fields[index] = value
        rejected = subprocess.run([str(binary), "--shared-difference"],
                                  input=" ".join(fields) + "\n", capture_output=True, text=True)
        if rejected.returncode == 0 or rejected.stdout or reason not in rejected.stderr:
            raise SystemExit(f"C shared-difference adapter did not reject: {reason}")
    for length in (1, 5, 9):
        rejected = subprocess.run([str(binary), "--shared-difference"],
                                  input=" ".join(valid[:length]), capture_output=True, text=True)
        if rejected.returncode == 0 or rejected.stdout or "Invalid or incomplete" not in rejected.stderr:
            raise SystemExit("C shared-difference adapter did not reject truncated input")


def check_e0_bases(binary, oracle):
    records = [record.split(":") for record in e0_records(oracle)]
    result = subprocess.run([str(binary), "--e0-basis"],
                            input="\n".join(fields[0] for fields in records) + "\n",
                            capture_output=True, text=True, check=True)
    if result.stdout.splitlines() != [":".join(fields[1:]) for fields in records]:
        raise SystemExit(f"C/Python {oracle.identifier} canonical E0 basis mismatch")
    for invalid in ("0", "-1", str(oracle.exponent + 1), "invalid", "9" * 40):
        rejected = subprocess.run([str(binary), "--e0-basis"], input=invalid + "\n",
                                  capture_output=True, text=True)
        if rejected.returncode == 0 or rejected.stdout or "Invalid canonical E0 torsion exponent" not in rejected.stderr:
            raise SystemExit("C E0 adapter did not reject invalid exponent")


def check_general_bases(binary, oracle):
    records = [record.split(":") for record in general_basis_records(oracle)]
    for mode in ("--canonical-basis", "--basis-from-hint"):
        selected = records if mode == "--canonical-basis" else [
            record.split(":") for record in reconstruction_records(oracle)
        ]
        inputs = [" ".join((fields[0], fields[1], fields[2]))
                  for fields in selected]
        result = subprocess.run([str(binary), mode], input="\n".join(inputs) + "\n",
                                capture_output=True, text=True, check=True)
        result_start = 3 if mode == "--canonical-basis" else 2
        if result.stdout.splitlines() != [":".join(fields[result_start:]) for fields in selected]:
            raise SystemExit(f"C/Python {oracle.identifier} {mode} mismatch")
        valid = inputs[0].split()
        invalid = [
            ["gg", *valid[1:]], [oracle.encode_field((2, 0)), *valid[1:]],
            [valid[0], "0", valid[2]], [valid[0], str(oracle.exponent + 1), valid[2]],
            [valid[0], valid[1], "-1"], [valid[0], "9" * 40, valid[2]],
            [valid[0], valid[1], str(oracle.exponent if mode == "--canonical-basis" else 256)],
            [valid[0], valid[1], "invalid"], valid[:1], valid[:2],
        ]
        for fields in invalid:
            rejected = subprocess.run([str(binary), mode], input=" ".join(fields) + "\n",
                                      capture_output=True, text=True)
            if rejected.returncode == 0 or rejected.stdout or "Invalid canonical basis" not in rejected.stderr:
                raise SystemExit("C basis/hint adapter did not reject invalid/truncated input")


def check_parameter(repository, oracle, level):
    records = [record.split(":") for record in oracle.curve_records()]
    inputs = "\n".join(" ".join(fields[:9] + [fields[14]]) for fields in records) + "\n"
    includes = (
        "include", "src/mp/ref/include", "src/common/generic/include",
        "src/gf/ref/include", f"src/precomp/ref/{oracle.identifier}/include", "src/ec/ref/include",
    )
    with tempfile.TemporaryDirectory(prefix="sqisign-curve-") as directory:
        binary = Path(directory) / "curve-reference"
        subprocess.run([
            "cc", "-std=c11", "-O2", "-ffunction-sections", "-fdata-sections",
            "-DRADIX_64", f"-DSQISIGN_VARIANT={oracle.identifier}", "-DSQISIGN_BUILD_TYPE_REF",
            f"-DREFERENCE_LEVEL={level}",
            *(f"-I{repository / include}" for include in includes),
            str(ROOT / "tools/curve_reference.c"),
            "-Wl,-dead_strip" if sys.platform == "darwin" else "-Wl,--gc-sections",
            "-o", str(binary),
        ], check=True)
        check_invalid_inputs(binary, records[0][:9] + [records[0][14]], oracle)
        check_two_torsion(binary, oracle)
        check_shared_differences(binary, oracle)
        check_e0_bases(binary, oracle)
        check_general_bases(binary, oracle)
        check_discrete_logs(binary, oracle)
        check_tate_pairings(binary, oracle)
        check_basis_actions(binary, oracle)
        check_basis_changes(binary, oracle)
        check_correspondences(binary, oracle)
        roots = [record.split(":") for record in oracle.root_records()]
        root_result = subprocess.run([str(binary), "--roots"],
                                     input="\n".join(fields[0] for fields in roots) + "\n",
                                     capture_output=True, text=True, check=True)
        if root_result.stdout.splitlines() != [fields[1] for fields in roots]:
            raise SystemExit("C/Python reference-sign square root mismatch")
        residues = [record.split(":") for record in oracle.base_residue_records()]
        residue_result = subprocess.run([str(binary), "--base-residue"],
                                        input="\n".join(fields[0] for fields in residues) + "\n",
                                        capture_output=True, text=True, check=True)
        if residue_result.stdout.splitlines() != [fields[1] for fields in residues]:
            raise SystemExit("C/Python base-field residue mismatch")
        rejected = subprocess.run([str(binary), "--base-residue"],
                                  input=oracle.encode_field((0, 1)) + "\n",
                                  capture_output=True, text=True)
        if rejected.returncode == 0 or rejected.stdout or "zero imaginary component" not in rejected.stderr:
            raise SystemExit("C base residue adapter did not reject a non-base operand")
        for invalid in ("00", "g" * (4 * oracle.width), "0" * (4 * oracle.width + 2),
                        (oracle.prime.to_bytes(oracle.width, "little") + bytes(oracle.width)).hex()):
            rejected = subprocess.run([str(binary), "--roots"], input=invalid + "\n",
                                      capture_output=True, text=True)
            if rejected.returncode == 0 or rejected.stdout or "Invalid canonical root operand" not in rejected.stderr:
                raise SystemExit("C root adapter did not reject malformed/noncanonical input")
        result = subprocess.run([str(binary)], input=inputs, capture_output=True,
                                text=True, check=True)
    expected = [":".join(fields[9:14] + fields[15:]) for fields in records]
    if result.stdout.splitlines() != expected:
        raise SystemExit(f"C/Python {oracle.identifier} curve differential mismatch")
    print(f"Cross-checked {oracle.identifier}: {len(records)} curve records, {len(roots)} exact-sign roots and "
          f"24 two-torsion basis/parity cases, 8 shared differences, 24 base residues, 4 E0 bases, "
          f"10 general bases and {len(reconstruction_records(oracle))} direct hints against C {REFERENCE_COMMIT}")


def main():
    repository = ROOT.parent
    for path, expected in (REFERENCE_FILES | CURVE_REFERENCE_FILES).items():
        actual = hashlib.sha256((repository / path).read_bytes()).hexdigest()
        if actual != expected:
            raise SystemExit(f"{path} differs from pinned C reference {REFERENCE_COMMIT}; review before rebasing")
    subprocess.run([sys.executable, "-B", str(ROOT / "tools/generate_curve_vectors.py"), "--check"],
                   check=True)
    subprocess.run([sys.executable, "-B", str(ROOT / "tools/generate_basis_vectors.py"), "--check"],
                   check=True)
    subprocess.run([sys.executable, "-B", str(ROOT / "tools/generate_pairing_vectors.py"), "--check"],
                   check=True)
    subprocess.run([sys.executable, "-B", str(ROOT / "tools/generate_basis_change_vectors.py"), "--check"],
                   check=True)
    for oracle, level in zip(ORACLES, (1, 3, 5), strict=True):
        check_parameter(repository, oracle, level)


if __name__ == "__main__":
    main()
