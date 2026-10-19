#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Cross-check public unsigned fixtures against pinned C raw arithmetic and Python integers."""

import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile

from generate_test_vectors import (
    ROOT, unsigned_cases, unsigned_record, wide_division_cases, wide_division_record,
    sqrt_floor_cases, sqrt_floor_record,
    gcd_cases, gcd_record,
    inverse_cases, inverse_record,
    fp324_cases, fp324_record, fp324_nonlinear_cases, fp324_nonlinear_record,
    fp2_324_cases, fp2_324_record, fp2_324_nonlinear_cases, fp2_324_nonlinear_record,
    fp2_prime_field_cases,     fp2_prime_field_record, fp2_prime_field_control_record, fp2_prime_field_nonlinear_cases,
    fp2_prime_field_nonlinear_record,
    prime_field_cases, prime_field_record, prime_field_control_record, prime_field_nonlinear_cases,
    prime_field_nonlinear_record,
)


REFERENCE_COMMIT = "f417ebd4c2c336ad6a9feb6f9255f95fc03acc41"
REFERENCE_FILES = {
    "src/mp/ref/lvlx/mp.c": "08da97fe30cd2c2801f580502557013015722c1897f18c5db58dbcbca99ed318",
    "src/mp/ref/lvlx/modqx.c": "9edf9e0ddeef7dc5a9d55df9395da18e9e48ebefed8c3d0fee017047f2008915",
    "src/mp/ref/include/mp.h": "07317c61c7106eaad6083ce4599a74afe1848bf9764b6ea3944cbc18d4e68e9b",
    "src/common/generic/include/tutil.h": "de9d1972630f5dde4ff1bdb29916dc879cb35f6f45614d6fe81addaa8210af77",
    "include/sqisign_namespace.h": "bb33f192914bf74d008ab25019c53a6b2c62ef8c7cac92bafc1162637472f584",
    "src/mp/ref/include/mp_internal.h": "76ab7a9695c00fe16cb208a2ad638f2d3918473c1aa18131ebf803e2b7da2dad",
    "src/precomp/ref/p324_3/include/fp_constants.h": "59fc4fc79203e217b19d2cb89f672fd708c82290bd962d307fa060da9eccce2b",
    "src/precomp/ref/p324_3/include/encoded_sizes.h": "caeb092b3ee1158f36b7fe6acbbede91bd3ac79f531d272349c0b36550750b6b",
    "src/mp/ref/include/modqx.h": "f232ec80ba2cdd52d5e2944bfeb962045104536f2e730494587734c0310cad52",
    "src/common/generic/include/prng.h": "8b0f4514ae036446f61a217d5b5323b82d8a74c5216454139ecd85c1e139fc76",
    "src/common/generic/include/fips202.h": "e3d925c6b53505dde795bd4798a8535d23bda2d15d72bdd5a96b83dc29a4949c",
    "src/mp/ref/include/primality_test.h": "7d9c1e34f0a13bf4a7ceb5baef8af01509ef9ed66591f237df7cbe12b02fba4a",
    "src/mp/ref/include/mp_ct.h": "6c0f0805a5fb98861314d49125936a5b604ca0e12d54c927fd1dd4875d8670a7",
    "src/mp/ref/include/mp_carry.h": "3214c48aed34b5275fd1175573336fb3881b351b7d865b477d16d3aa5e01b03d",
    "src/gf/ref/p324_3/fp_p324_3.c": "0810ab0dbb109197a511c84334b408d889ffbb2085cfde25fa448d35d7066b00",
    "src/gf/ref/p324_3/fp_p324_3_64.inc": "b6e96fc9537e81207656a0165f30b1150433b83db39a2a7477dfe88f1441a0cc",
    "src/gf/ref/include/fp.h": "d0d7ce4c30b41064679f2eceb5a5b67ed10a05c7370653a2a5cfe6588ce81e01",
    "src/gf/ref/include/fp2.h": "3e4f03fff8c25b5a4da8ca32533124b2fccf5f77128ec74df7d97132be10acd4",
    "src/gf/ref/lvlx/fp.c": "bf196dde5b4179f59240222af0c52d94b0878dfbc8ef074109e82a4f056d7da1",
    "src/gf/ref/lvlx/fp2.c": "e08ce4b2fa3935ba6230a29829c1834864dce0fc1be7f6462870d734fd12704b",
    "src/gf/ref/p500_27/fp_p500_27.c": "16c62689aef70a58d0f46dc3aad9fd62af9c6f00f5096dec635eb85116cd397b",
    "src/gf/ref/p500_27/fp_p500_27_64.inc": "f9e1fead3c1a708d09159dd7477f690d6400f48f1d3feb4cfa4c20cfddf00b17",
    "src/precomp/ref/p500_27/include/fp_constants.h": "dce6b7a045de86fcf3200a85006d189be5b54114f0d3dd4eef23fb4e88134073",
    "src/precomp/ref/p500_27/include/encoded_sizes.h": "5e6b6527a6f6a9a46001593e851f34e5c16963cf534d1a373ad9a6a0d21722f3",
    "src/gf/ref/p664_17/fp_p664_17.c": "8db8e143b33f496c19bade11996d1f516fa454f3634922ab6a7e754391307d33",
    "src/gf/ref/p664_17/fp_p664_17_64.inc": "771e50af61c1899705bb218f975f947d3478ad9f1e0247f4783595172f4a5587",
    "src/precomp/ref/p664_17/include/fp_constants.h": "621e5f3f0759169cfd6ed0d002961d3b889d6357810741b071e477630b444948",
    "src/precomp/ref/p664_17/include/encoded_sizes.h": "37f1242729df34d395d7ac0f4dd1273300111b0d9b8386ae05075c389f64855d",
    "scripts/add_parameter_set.py": "5741efaaf207215836a381416aa870278d33481f11da6bedfda4595d23fab4f3",
}
INCLUDES = (
    "include",
    "src/mp/ref/include",
    "src/common/generic/include",
    "src/precomp/ref/p324_3/include",
    "src/gf/ref/include",
)


def main():
    repository = ROOT.parent
    for path, expected_digest in REFERENCE_FILES.items():
        digest = hashlib.sha256((repository / path).read_bytes()).hexdigest()
        if digest != expected_digest:
            raise SystemExit(f"{path} differs from pinned C reference {REFERENCE_COMMIT}; review before rebasing")
    cases = unsigned_cases()
    wide_cases = wide_division_cases()
    square_root_cases = sqrt_floor_cases()
    common_divisor_cases = gcd_cases()
    modular_inverse_cases = inverse_cases()
    field_cases = fp324_cases()
    field_nonlinear_values = fp324_nonlinear_cases()
    fp2_cases = fp2_324_cases()
    fp2_nonlinear_values = fp2_324_nonlinear_cases()
    p500_cases = prime_field_cases(27, 500, 64, "p500_27")
    p500_nonlinear_values = prime_field_nonlinear_cases(27, 500, 64, "p500_27")
    p664_cases = prime_field_cases(17, 664, 84, "p664_17")
    p664_nonlinear_values = prime_field_nonlinear_cases(17, 664, 84, "p664_17")
    p500_fp2_cases = fp2_prime_field_cases(27, 500, 64, "p500_27")
    p500_fp2_nonlinear_values = fp2_prime_field_nonlinear_cases(27, 500, 64, "p500_27")
    p664_fp2_cases = fp2_prime_field_cases(17, 664, 84, "p664_17")
    p664_fp2_nonlinear_values = fp2_prime_field_nonlinear_cases(17, 664, 84, "p664_17")
    inputs = "\n".join(f"{left.to_bytes(41, 'little').hex()} {right.to_bytes(41, 'little').hex()}"
                       for left, right in cases) + "\n"
    with tempfile.TemporaryDirectory(prefix="sqisign-unsigned-") as directory:
        binary = Path(directory) / "unsigned-reference"
        command = [
            "cc", "-std=c11", "-O2", "-ffunction-sections", "-fdata-sections",
            "-DRADIX_64", "-DSQISIGN_VARIANT=p324_3", "-DSQISIGN_BUILD_TYPE_REF",
            *(f"-I{repository / include}" for include in INCLUDES),
            str(ROOT / "tools/unsigned_reference.c"),
            "-Wl,-dead_strip" if sys.platform == "darwin" else "-Wl,--gc-sections",
            "-o", str(binary),
        ]
        field324_binary = Path(directory) / "field-reference-p324_3"
        field324_includes = (
            "include",
            "src/mp/ref/include",
            "src/common/generic/include",
            "src/gf/ref/include",
            "src/gf/ref/p324_3",
            "src/precomp/ref/p324_3/include",
        )
        field324_command = [
            "cc", "-std=c11", "-O2", "-DRADIX_64", "-DSQISIGN_VARIANT=p324_3",
            "-DSQISIGN_BUILD_TYPE_REF", "-DSQISIGN_FIELD_P324_3",
            *(f"-I{repository / include}" for include in field324_includes),
            str(ROOT / "tools/field_reference.c"),
            "-o", str(field324_binary),
        ]
        field_binary = Path(directory) / "field-reference-p500_27"
        field_includes = (
            "include",
            "src/mp/ref/include",
            "src/common/generic/include",
            "src/gf/ref/include",
            "src/gf/ref/p500_27",
            "src/precomp/ref/p500_27/include",
        )
        field_command = [
            "cc", "-std=c11", "-O2", "-DRADIX_64", "-DSQISIGN_VARIANT=p500_27",
            "-DSQISIGN_BUILD_TYPE_REF", "-DSQISIGN_FIELD_P500_27",
            *(f"-I{repository / include}" for include in field_includes),
            str(ROOT / "tools/field_reference.c"),
            "-o", str(field_binary),
        ]
        field664_binary = Path(directory) / "field-reference-p664_17"
        field664_includes = (
            "include",
            "src/mp/ref/include",
            "src/common/generic/include",
            "src/gf/ref/include",
            "src/gf/ref/p664_17",
            "src/precomp/ref/p664_17/include",
        )
        field664_command = [
            "cc", "-std=c11", "-O2", "-DRADIX_64", "-DSQISIGN_VARIANT=p664_17",
            "-DSQISIGN_BUILD_TYPE_REF", "-DSQISIGN_FIELD_P664_17",
            *(f"-I{repository / include}" for include in field664_includes),
            str(ROOT / "tools/field_reference.c"),
            "-o", str(field664_binary),
        ]
        subprocess.run(field324_command, check=True)
        subprocess.run(field_command, check=True)
        subprocess.run(field664_command, check=True)
        subprocess.run(command, check=True)
        result = subprocess.run([str(binary)], input=inputs, capture_output=True, text=True, check=True)
        wide_inputs = "\n".join(
            f"{dividend.to_bytes(82, 'little').hex()} {divisor.to_bytes(41, 'little').hex()}"
            for dividend, divisor in wide_cases) + "\n"
        wide_result = subprocess.run([str(binary), "--wide-division"], input=wide_inputs,
                                     capture_output=True, text=True, check=True)
        field_inputs = "\n".join(
            f"{left.to_bytes(41, 'little').hex()} {right.to_bytes(41, 'little').hex()}"
            for left, right in field_cases) + "\n"
        field_result = subprocess.run(
            [str(binary), "--field"], input=field_inputs, capture_output=True, text=True, check=True)
        field_control_cases = "\n".join(
            f"{left.to_bytes(41, 'little').hex()} {right.to_bytes(41, 'little').hex()} {control}"
            for left, right in field_cases for control in (0, 1)
        ) + "\n"
        field_control_result = subprocess.run(
            [str(field324_binary), "--field-control"], input=field_control_cases,
            capture_output=True, text=True, check=True)
        field_nonlinear_inputs = "\n".join(
            value.to_bytes(41, "little").hex() for value in field_nonlinear_values) + "\n"
        field_nonlinear_result = subprocess.run(
            [str(binary), "--field-nonlinear"], input=field_nonlinear_inputs,
            capture_output=True, text=True, check=True)
        fp2_inputs = "\n".join(
            f"{a.to_bytes(41, 'little').hex()}{b.to_bytes(41, 'little').hex()} "
            f"{c.to_bytes(41, 'little').hex()}{d.to_bytes(41, 'little').hex()}"
            for a, b, c, d in fp2_cases) + "\n"
        fp2_result = subprocess.run(
            [str(binary), "--fp2"], input=fp2_inputs, capture_output=True, text=True, check=True)
        fp2_control_inputs = "\n".join(
            f"{a.to_bytes(41, 'little').hex()}{b.to_bytes(41, 'little').hex()} "
            f"{c.to_bytes(41, 'little').hex()}{d.to_bytes(41, 'little').hex()} {control}"
            for a, b, c, d in fp2_cases for control in (0, 1)
        ) + "\n"
        fp2_control_result = subprocess.run(
            [str(field324_binary), "--fp2-control"], input=fp2_control_inputs,
            capture_output=True, text=True, check=True)
        fp2_nonlinear_inputs = "\n".join(
            f"{real.to_bytes(41, 'little').hex()}{imaginary.to_bytes(41, 'little').hex()}"
            for real, imaginary in fp2_nonlinear_values) + "\n"
        fp2_nonlinear_result = subprocess.run(
            [str(binary), "--fp2-nonlinear"], input=fp2_nonlinear_inputs,
            capture_output=True, text=True, check=True)
        p500_inputs = "\n".join(
            f"{left.to_bytes(64, 'little').hex()} {right.to_bytes(64, 'little').hex()}"
            for left, right in p500_cases) + "\n"
        p500_result = subprocess.run(
            [str(field_binary), "--field"], input=p500_inputs,
            capture_output=True, text=True, check=True)
        p500_control_inputs = "\n".join(
            f"{left.to_bytes(64, 'little').hex()} {right.to_bytes(64, 'little').hex()} {control}"
            for left, right in p500_cases for control in (0, 1)
        ) + "\n"
        p500_control_result = subprocess.run(
            [str(field_binary), "--field-control"], input=p500_control_inputs,
            capture_output=True, text=True, check=True)
        p500_nonlinear_inputs = "\n".join(
            value.to_bytes(64, "little").hex() for value in p500_nonlinear_values) + "\n"
        p500_nonlinear_result = subprocess.run(
            [str(field_binary), "--field-nonlinear"], input=p500_nonlinear_inputs,
            capture_output=True, text=True, check=True)
        p500_fp2_inputs = "\n".join(
            f"{a.to_bytes(64, 'little').hex()}{b.to_bytes(64, 'little').hex()} "
            f"{c.to_bytes(64, 'little').hex()}{d.to_bytes(64, 'little').hex()}"
            for a, b, c, d in p500_fp2_cases) + "\n"
        p500_fp2_result = subprocess.run(
            [str(field_binary), "--fp2"], input=p500_fp2_inputs,
            capture_output=True, text=True, check=True)
        p500_fp2_control_inputs = "\n".join(
            f"{a.to_bytes(64, 'little').hex()}{b.to_bytes(64, 'little').hex()} "
            f"{c.to_bytes(64, 'little').hex()}{d.to_bytes(64, 'little').hex()} {control}"
            for a, b, c, d in p500_fp2_cases for control in (0, 1)
        ) + "\n"
        p500_fp2_control_result = subprocess.run(
            [str(field_binary), "--fp2-control"], input=p500_fp2_control_inputs,
            capture_output=True, text=True, check=True)
        p500_fp2_nonlinear_inputs = "\n".join(
            f"{real.to_bytes(64, 'little').hex()}{imaginary.to_bytes(64, 'little').hex()}"
            for real, imaginary in p500_fp2_nonlinear_values) + "\n"
        p500_fp2_nonlinear_result = subprocess.run(
            [str(field_binary), "--fp2-nonlinear"], input=p500_fp2_nonlinear_inputs,
            capture_output=True, text=True, check=True)
        p664_inputs = "\n".join(
            f"{left.to_bytes(84, 'little').hex()} {right.to_bytes(84, 'little').hex()}"
            for left, right in p664_cases) + "\n"
        p664_result = subprocess.run(
            [str(field664_binary), "--field"], input=p664_inputs,
            capture_output=True, text=True, check=True)
        p664_control_inputs = "\n".join(
            f"{left.to_bytes(84, 'little').hex()} {right.to_bytes(84, 'little').hex()} {control}"
            for left, right in p664_cases for control in (0, 1)
        ) + "\n"
        p664_control_result = subprocess.run(
            [str(field664_binary), "--field-control"], input=p664_control_inputs,
            capture_output=True, text=True, check=True)
        p664_nonlinear_inputs = "\n".join(
            value.to_bytes(84, "little").hex() for value in p664_nonlinear_values) + "\n"
        p664_nonlinear_result = subprocess.run(
            [str(field664_binary), "--field-nonlinear"], input=p664_nonlinear_inputs,
            capture_output=True, text=True, check=True)
        p664_fp2_inputs = "\n".join(
            f"{a.to_bytes(84, 'little').hex()}{b.to_bytes(84, 'little').hex()} "
            f"{c.to_bytes(84, 'little').hex()}{d.to_bytes(84, 'little').hex()}"
            for a, b, c, d in p664_fp2_cases) + "\n"
        p664_fp2_result = subprocess.run(
            [str(field664_binary), "--fp2"], input=p664_fp2_inputs,
            capture_output=True, text=True, check=True)
        p664_fp2_control_inputs = "\n".join(
            f"{a.to_bytes(84, 'little').hex()}{b.to_bytes(84, 'little').hex()} "
            f"{c.to_bytes(84, 'little').hex()}{d.to_bytes(84, 'little').hex()} {control}"
            for a, b, c, d in p664_fp2_cases for control in (0, 1)
        ) + "\n"
        p664_fp2_control_result = subprocess.run(
            [str(field664_binary), "--fp2-control"], input=p664_fp2_control_inputs,
            capture_output=True, text=True, check=True)
        p664_fp2_nonlinear_inputs = "\n".join(
            f"{real.to_bytes(84, 'little').hex()}{imaginary.to_bytes(84, 'little').hex()}"
            for real, imaginary in p664_fp2_nonlinear_values) + "\n"
        p664_fp2_nonlinear_result = subprocess.run(
            [str(field664_binary), "--fp2-nonlinear"], input=p664_fp2_nonlinear_inputs,
            capture_output=True, text=True, check=True)
        square_root_inputs = "\n".join(
            value.to_bytes(41, "little").hex() for value in square_root_cases) + "\n"
        square_root_result = subprocess.run(
            [str(binary), "--sqrt-floor"], input=square_root_inputs,
            capture_output=True, text=True, check=True)
        gcd_inputs = "\n".join(
            f"{left.to_bytes(41, 'little').hex()} {right.to_bytes(41, 'little').hex()}"
            for left, right in common_divisor_cases) + "\n"
        gcd_result = subprocess.run([str(binary), "--gcd"], input=gcd_inputs,
                                    capture_output=True, text=True, check=True)
        inverse_inputs = "\n".join(
            f"{value.to_bytes(41, 'little').hex()} {modulus.to_bytes(41, 'little').hex()}"
            for value, modulus in modular_inverse_cases) + "\n"
        inverse_result = subprocess.run([str(binary), "--inverse"], input=inverse_inputs,
                                        capture_output=True, text=True, check=True)
    actual = result.stdout.splitlines()
    expected = [unsigned_record(left, right) for left, right in cases]
    if actual != expected:
        raise SystemExit("C/Python unsigned arithmetic differential mismatch")
    wide_expected = [wide_division_record(dividend, divisor) for dividend, divisor in wide_cases]
    if wide_result.stdout.splitlines() != wide_expected:
        raise SystemExit("C/Python wide division differential mismatch")
    field_expected = [fp324_record(left, right) for left, right in field_cases]
    if field_result.stdout.splitlines() != field_expected:
        raise SystemExit("C/Python p324_3 field arithmetic differential mismatch")
    field_control_expected = [
        prime_field_control_record(left, right, control, 3, 324, 41)
        for left, right in field_cases for control in (0, 1)
    ]
    if field_control_result.stdout.splitlines() != field_control_expected:
        raise SystemExit("C/Python p324_3 field control differential mismatch")
    field_nonlinear_expected = [
        fp324_nonlinear_record(value) for value in field_nonlinear_values
    ]
    field_nonlinear_actual = field_nonlinear_result.stdout.splitlines()
    prime = 3 * (1 << 324) - 1
    if len(field_nonlinear_actual) != len(field_nonlinear_expected):
        raise SystemExit("C p324_3 nonlinear field result count differs from Python")
    for index, (actual, expected) in enumerate(
        zip(field_nonlinear_actual, field_nonlinear_expected)
    ):
        actual_fields = actual.split(":")
        expected_fields = expected.split(":")
        if len(actual_fields) != 4 or actual_fields[:3] != expected_fields[:3]:
            raise SystemExit(f"C/Python p324_3 inverse/residue mismatch at case {index}")
        if expected_fields[2] == "1":
            value = int.from_bytes(bytes.fromhex(actual_fields[0]), "little")
            root = int.from_bytes(bytes.fromhex(actual_fields[3]), "little")
            if root * root % prime != value:
                raise SystemExit(f"C p324_3 square-root identity mismatch at case {index}")
    fp2_expected = [fp2_324_record(a, b, c, d) for a, b, c, d in fp2_cases]
    if fp2_result.stdout.splitlines() != fp2_expected:
        raise SystemExit("C/Python p324_3 extension-field arithmetic differential mismatch")
    fp2_control_expected = [
        fp2_prime_field_control_record(a, b, c, d, control, 3, 324, 41)
        for a, b, c, d in fp2_cases for control in (0, 1)
    ]
    if fp2_control_result.stdout.splitlines() != fp2_control_expected:
        raise SystemExit("C/Python p324_3 extension-field control differential mismatch")
    fp2_nonlinear_expected = [
        fp2_324_nonlinear_record(real, imaginary)
        for real, imaginary in fp2_nonlinear_values
    ]
    if fp2_nonlinear_result.stdout.splitlines() != fp2_nonlinear_expected:
        raise SystemExit("C/Python p324_3 extension-field nonlinear differential mismatch")
    p500_expected = [
        prime_field_record(left, right, 27, 500, 64) for left, right in p500_cases
    ]
    if p500_result.stdout.splitlines() != p500_expected:
        raise SystemExit("C/Python p500_27 field arithmetic differential mismatch")
    p500_control_expected = [
        prime_field_control_record(left, right, control, 27, 500, 64)
        for left, right in p500_cases for control in (0, 1)
    ]
    if p500_control_result.stdout.splitlines() != p500_control_expected:
        raise SystemExit("C/Python p500_27 field control differential mismatch")
    p500_nonlinear_expected = [
        prime_field_nonlinear_record(value, 27, 500, 64)
        for value in p500_nonlinear_values
    ]
    if p500_nonlinear_result.stdout.splitlines() != p500_nonlinear_expected:
        raise SystemExit("C/Python p500_27 nonlinear field differential mismatch")
    p664_expected = [
        prime_field_record(left, right, 17, 664, 84) for left, right in p664_cases
    ]
    if p664_result.stdout.splitlines() != p664_expected:
        raise SystemExit("C/Python p664_17 field arithmetic differential mismatch")
    p664_control_expected = [
        prime_field_control_record(left, right, control, 17, 664, 84)
        for left, right in p664_cases for control in (0, 1)
    ]
    if p664_control_result.stdout.splitlines() != p664_control_expected:
        raise SystemExit("C/Python p664_17 field control differential mismatch")
    p664_nonlinear_expected = [
        prime_field_nonlinear_record(value, 17, 664, 84)
        for value in p664_nonlinear_values
    ]
    if p664_nonlinear_result.stdout.splitlines() != p664_nonlinear_expected:
        raise SystemExit("C/Python p664_17 nonlinear field differential mismatch")
    p500_fp2_expected = [
        fp2_prime_field_record(a, b, c, d, 27, 500, 64)
        for a, b, c, d in p500_fp2_cases
    ]
    if p500_fp2_result.stdout.splitlines() != p500_fp2_expected:
        raise SystemExit("C/Python p500_27 extension-field arithmetic differential mismatch")
    p500_fp2_control_expected = [
        fp2_prime_field_control_record(a, b, c, d, control, 27, 500, 64)
        for a, b, c, d in p500_fp2_cases for control in (0, 1)
    ]
    if p500_fp2_control_result.stdout.splitlines() != p500_fp2_control_expected:
        raise SystemExit("C/Python p500_27 extension-field control differential mismatch")
    p500_fp2_nonlinear_expected = [
        fp2_prime_field_nonlinear_record(real, imaginary, 27, 500, 64)
        for real, imaginary in p500_fp2_nonlinear_values
    ]
    if p500_fp2_nonlinear_result.stdout.splitlines() != p500_fp2_nonlinear_expected:
        raise SystemExit("C/Python p500_27 extension-field nonlinear differential mismatch")
    p664_fp2_expected = [
        fp2_prime_field_record(a, b, c, d, 17, 664, 84)
        for a, b, c, d in p664_fp2_cases
    ]
    if p664_fp2_result.stdout.splitlines() != p664_fp2_expected:
        raise SystemExit("C/Python p664_17 extension-field arithmetic differential mismatch")
    p664_fp2_control_expected = [
        fp2_prime_field_control_record(a, b, c, d, control, 17, 664, 84)
        for a, b, c, d in p664_fp2_cases for control in (0, 1)
    ]
    if p664_fp2_control_result.stdout.splitlines() != p664_fp2_control_expected:
        raise SystemExit("C/Python p664_17 extension-field control differential mismatch")
    p664_fp2_nonlinear_expected = [
        fp2_prime_field_nonlinear_record(real, imaginary, 17, 664, 84)
        for real, imaginary in p664_fp2_nonlinear_values
    ]
    if p664_fp2_nonlinear_result.stdout.splitlines() != p664_fp2_nonlinear_expected:
        raise SystemExit("C/Python p664_17 extension-field nonlinear differential mismatch")
    square_root_expected = [sqrt_floor_record(value) for value in square_root_cases]
    if square_root_result.stdout.splitlines() != square_root_expected:
        raise SystemExit("C/Python floor square-root differential mismatch")
    gcd_expected = [gcd_record(left, right) for left, right in common_divisor_cases]
    if gcd_result.stdout.splitlines() != gcd_expected:
        raise SystemExit("C/Python GCD differential mismatch")
    inverse_expected = [inverse_record(value, modulus) for value, modulus in modular_inverse_cases]
    if inverse_result.stdout.splitlines() != inverse_expected:
        raise SystemExit("C/Python modular inverse differential mismatch")
    print(f"Verified {len(cases)} UInt328, {len(wide_cases)} wide division, "
          f"{len(square_root_cases)} floor square root, "
          f"{len(common_divisor_cases)} GCD and {len(modular_inverse_cases)} modular inverse "
          f"public cases against "
          f"C {REFERENCE_COMMIT} and Python integers; "
          f"{len(field_cases)} p324_3 field and {len(field_nonlinear_values)} nonlinear cases; "
          f"{len(fp2_cases)} p324_3 extension-field and "
          f"{len(fp2_nonlinear_values)} extension nonlinear cases; "
          f"{len(p500_cases)} p500_27 field and "
          f"{len(p500_nonlinear_values)} p500_27 nonlinear cases; "
          f"{len(p664_cases)} p664_17 field and "
          f"{len(p664_nonlinear_values)} p664_17 nonlinear cases; "
          f"{len(p500_fp2_cases)} p500_27 extension and "
          f"{len(p500_fp2_nonlinear_values)} extension nonlinear cases; "
          f"{len(p664_fp2_cases)} p664_17 extension and "
          f"{len(p664_fp2_nonlinear_values)} extension nonlinear cases")


if __name__ == "__main__":
    main()
