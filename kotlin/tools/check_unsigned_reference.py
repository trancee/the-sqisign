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
    gcd_cases, gcd_record,
    inverse_cases, inverse_record,
    signed_cases, signed_record,
    signed_division_cases, signed_division_record,
    signed_wide_division_cases, signed_wide_division_record,
    signed_wide_cases, signed_wide_record,
)


REFERENCE_COMMIT = "f417ebd4c2c336ad6a9feb6f9255f95fc03acc41"
REFERENCE_FILES = {
    "src/mp/ref/lvlx/mp.c": "08da97fe30cd2c2801f580502557013015722c1897f18c5db58dbcbca99ed318",
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
}
INCLUDES = (
    "include",
    "src/mp/ref/include",
    "src/common/generic/include",
    "src/precomp/ref/p324_3/include",
)


def main():
    repository = ROOT.parent
    for path, expected_digest in REFERENCE_FILES.items():
        digest = hashlib.sha256((repository / path).read_bytes()).hexdigest()
        if digest != expected_digest:
            raise SystemExit(f"{path} differs from pinned C reference {REFERENCE_COMMIT}; review before rebasing")
    cases = unsigned_cases()
    wide_cases = wide_division_cases()
    common_divisor_cases = gcd_cases()
    modular_inverse_cases = inverse_cases()
    signed_integer_cases = signed_cases()
    signed_quotient_cases = signed_division_cases()
    signed_wide_quotient_cases = signed_wide_division_cases()
    signed_wide_integer_cases = signed_wide_cases()
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
        subprocess.run(command, check=True)
        result = subprocess.run([str(binary)], input=inputs, capture_output=True, text=True, check=True)
        wide_inputs = "\n".join(
            f"{dividend.to_bytes(82, 'little').hex()} {divisor.to_bytes(41, 'little').hex()}"
            for dividend, divisor in wide_cases) + "\n"
        wide_result = subprocess.run([str(binary), "--wide-division"], input=wide_inputs,
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
        signed_inputs = "\n".join(
            f"{left.to_bytes(41, 'little', signed=True).hex()} {right.to_bytes(41, 'little', signed=True).hex()}"
            for left, right in signed_integer_cases) + "\n"
        signed_result = subprocess.run([str(binary), "--signed"], input=signed_inputs,
                                       capture_output=True, text=True, check=True)
        signed_division_inputs = "\n".join(
            f"{left.to_bytes(41, 'little', signed=True).hex()} {right.to_bytes(41, 'little', signed=True).hex()}"
            for left, right in signed_quotient_cases) + "\n"
        signed_division_result = subprocess.run(
            [str(binary), "--signed-division"], input=signed_division_inputs,
            capture_output=True, text=True, check=True)
        signed_wide_division_inputs = "\n".join(
            f"{dividend.to_bytes(82, 'little', signed=True).hex()} "
            f"{divisor.to_bytes(41, 'little', signed=True).hex()}"
            for dividend, divisor in signed_wide_quotient_cases) + "\n"
        signed_wide_division_result = subprocess.run(
            [str(binary), "--signed-wide-division"], input=signed_wide_division_inputs,
            capture_output=True, text=True, check=True)
        signed_wide_integer_inputs = "\n".join(
            f"{left.to_bytes(82, 'little', signed=True).hex()} "
            f"{right.to_bytes(82, 'little', signed=True).hex()}"
            for left, right in signed_wide_integer_cases) + "\n"
        signed_wide_integer_result = subprocess.run(
            [str(binary), "--signed-wide"], input=signed_wide_integer_inputs,
            capture_output=True, text=True, check=True)
    actual = result.stdout.splitlines()
    expected = [unsigned_record(left, right) for left, right in cases]
    if actual != expected:
        raise SystemExit("C/Python unsigned arithmetic differential mismatch")
    wide_expected = [wide_division_record(dividend, divisor) for dividend, divisor in wide_cases]
    if wide_result.stdout.splitlines() != wide_expected:
        raise SystemExit("C/Python wide division differential mismatch")
    gcd_expected = [gcd_record(left, right) for left, right in common_divisor_cases]
    if gcd_result.stdout.splitlines() != gcd_expected:
        raise SystemExit("C/Python GCD differential mismatch")
    inverse_expected = [inverse_record(value, modulus) for value, modulus in modular_inverse_cases]
    if inverse_result.stdout.splitlines() != inverse_expected:
        raise SystemExit("C/Python modular inverse differential mismatch")
    signed_expected = [signed_record(left, right) for left, right in signed_integer_cases]
    if signed_result.stdout.splitlines() != signed_expected:
        raise SystemExit("C/Python signed arithmetic differential mismatch")
    signed_division_expected = [signed_division_record(left, right) for left, right in signed_quotient_cases]
    if signed_division_result.stdout.splitlines() != signed_division_expected:
        raise SystemExit("C/Python signed division differential mismatch")
    signed_wide_division_expected = [
        signed_wide_division_record(dividend, divisor)
        for dividend, divisor in signed_wide_quotient_cases
    ]
    if signed_wide_division_result.stdout.splitlines() != signed_wide_division_expected:
        raise SystemExit("C/Python signed wide division differential mismatch")
    signed_wide_integer_expected = [
        signed_wide_record(left, right) for left, right in signed_wide_integer_cases
    ]
    if signed_wide_integer_result.stdout.splitlines() != signed_wide_integer_expected:
        raise SystemExit("C/Python signed wide arithmetic differential mismatch")
    print(f"Verified {len(cases)} UInt328, {len(wide_cases)} wide division, "
          f"{len(common_divisor_cases)} GCD, {len(modular_inverse_cases)} modular inverse, "
          f"{len(signed_integer_cases)} Int328, {len(signed_quotient_cases)} signed division and "
          f"{len(signed_wide_quotient_cases)} signed wide division and "
          f"{len(signed_wide_integer_cases)} Int656 arithmetic public cases against "
          f"C {REFERENCE_COMMIT} and Python integers")


if __name__ == "__main__":
    main()
