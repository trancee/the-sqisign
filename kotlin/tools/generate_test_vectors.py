#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Regenerate common tests' public fixtures using CPython SHAKE256 and upstream KATs."""

import argparse
import hashlib
import json
import math
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/commonTest/kotlin/ch/trancee/sqisign/TestVectors.kt"
PARAMETERS = (
    ("NIST_I", "p324_3", 270, 41, 16, 200),
    ("NIST_III", "p500_27", 417, 64, 24, 306),
    ("NIST_V", "p664_17", 549, 84, 32, 406),
)


def literal(value, indent=8):
    chunks = [value[i:i + 96] for i in range(0, len(value), 96)] or [""]
    return (" +\n" + " " * indent).join(json.dumps(chunk) for chunk in chunks)


def unsigned_cases():
    """Public deterministic operands; independent Python integers are the oracle."""
    modulus = 1 << 328
    maximum = modulus - 1
    prime = 3 * (1 << 324) - 1
    boundaries = (0, 1, maximum, prime - 1, prime, prime + 1,
                  int.from_bytes(b"\x55" * 41, "little"),
                  int.from_bytes(b"\xaa" * 41, "little"))
    cases = [(left, right) for left in boundaries for right in boundaries]
    for bit in range(15, 328, 15):
        power = 1 << bit
        cases.extend(((power - 1, 1), (power, 1), (1, power), (power - 1, power)))
    for index in range(128):
        block = hashlib.shake_256(f"SQIsign UInt328 public vector {index}".encode()).digest(82)
        cases.append((int.from_bytes(block[:41], "little"), int.from_bytes(block[41:], "little")))
    for index in range(64):
        block = hashlib.shake_256(f"SQIsign UInt328 public division vector {index}".encode()).digest(82)
        divisor_mask = (1 << (1 + 5 * index)) - 1
        cases.append((int.from_bytes(block[:41], "little"),
                      (int.from_bytes(block[41:], "little") & divisor_mask) | 1))
    return cases


def unsigned_record(left, right):
    modulus = 1 << 328
    encode = lambda value: (value % modulus).to_bytes(41, "little").hex()
    quotient, remainder = (encode(left // right), encode(left % right)) if right else ("-", "-")
    return ":".join((encode(left), encode(right), encode(left + right),
                     str((left + right) // modulus), encode(left - right),
                     str(int(left < right)), str((left > right) - (left < right)),
                     (left * right).to_bytes(82, "little").hex(), quotient, remainder))


def wide_division_cases():
    """Public full-width dividends and bounded divisors, independent of Kotlin arithmetic."""
    narrow_maximum = (1 << 328) - 1
    wide_maximum = (1 << 656) - 1
    dividends = (0, 1, wide_maximum, narrow_maximum, narrow_maximum + 1,
                 narrow_maximum ** 2, int.from_bytes(b"\x55" * 82, "little"),
                 int.from_bytes(b"\xaa" * 82, "little"))
    divisors = (0, 1, narrow_maximum, 1 << 327, (1 << 327) + 1,
                3 * (1 << 324) - 1, int.from_bytes(b"\x55" * 41, "little"),
                int.from_bytes(b"\xaa" * 41, "little"))
    cases = [(dividend, divisor) for dividend in dividends for divisor in divisors]
    for bit in range(1, 328, 15):
        divisor = (1 << bit) + 1
        cases.extend(((2 * (divisor - 1) + 1, divisor),
                      (wide_maximum, divisor), ((divisor + 1) ** 2, divisor)))
    for index in range(128):
        block = hashlib.shake_256(f"SQIsign UInt656 public division vector {index}".encode()).digest(123)
        cases.append((int.from_bytes(block[:82], "little"), int.from_bytes(block[82:], "little")))
    for index in range(64):
        block = hashlib.shake_256(f"SQIsign UInt656 public divisor widths {index}".encode()).digest(123)
        divisor_mask = (1 << (1 + 5 * index)) - 1
        cases.append((int.from_bytes(block[:82], "little"),
                      (int.from_bytes(block[82:], "little") & divisor_mask) | 1))
    return cases


def wide_division_record(dividend, divisor):
    quotient = (dividend // divisor).to_bytes(82, "little").hex() if divisor else "-"
    remainder = (dividend % divisor).to_bytes(41, "little").hex() if divisor else "-"
    return ":".join((dividend.to_bytes(82, "little").hex(),
                     divisor.to_bytes(41, "little").hex(), quotient, remainder))


def gcd_cases():
    """Unsigned fixtures plus shared factors and long Euclidean chains."""
    cases = unsigned_cases()
    for index in range(64):
        block = hashlib.shake_256(f"SQIsign UInt328 public gcd vector {index}".encode()).digest(82)
        shared = (1 << (1 + 5 * index)) - 1
        limit = ((1 << 328) - 1) // shared
        left = 1 + int.from_bytes(block[:41], "little") % limit
        right = 1 + int.from_bytes(block[41:], "little") % limit
        cases.append((shared * left, shared * right))
    previous, current = 0, 1
    index = 0
    while previous + current < (1 << 328):
        previous, current = current, previous + current
        index += 1
        if index % 32 == 0:
            cases.append((current, previous))
    cases.append((current, previous))
    for bit in range(0, 326, 15):
        cases.append((3 << bit, 5 << bit))
    return cases


def gcd_record(left, right):
    encode = lambda value: value.to_bytes(41, "little").hex()
    return ":".join((encode(left), encode(right), encode(math.gcd(left, right))))


def inverse_cases():
    """GCD operands plus invertible/non-invertible pairs for full-width even moduli."""
    cases = gcd_cases()
    maximum = (1 << 328) - 1
    cases.extend(((0, 1), (maximum, 1), (maximum - 1, maximum), (maximum, 1 << 327)))
    for bit in range(1, 328, 5):
        modulus = 1 << bit
        cases.extend(((modulus - 1, modulus), (modulus, modulus)))
    return cases


def inverse_record(value, modulus):
    encode = lambda integer: integer.to_bytes(41, "little").hex()
    if modulus < 2:
        outcome = "invalid_modulus"
    elif math.gcd(value, modulus) != 1:
        outcome = "not_coprime"
    else:
        outcome = encode(pow(value, -1, modulus))
    return ":".join((encode(value), encode(modulus), outcome))


def signed_cases():
    """Signed boundaries, radix transitions and deterministic full-width public operands."""
    minimum = -(1 << 327)
    maximum = (1 << 327) - 1
    boundaries = (minimum, minimum + 1, -2, -1, 0, 1, 2, maximum - 1, maximum)
    cases = [(left, right) for left in boundaries for right in boundaries]
    for bit in range(15, 327, 15):
        power = 1 << bit
        cases.extend(((power - 1, 1), (-power, -1), (power, -power),
                      (minimum, power), (maximum, -power)))
    for index in range(128):
        block = hashlib.shake_256(f"SQIsign Int328 public vector {index}".encode()).digest(82)
        cases.append((int.from_bytes(block[:41], "little", signed=True),
                      int.from_bytes(block[41:], "little", signed=True)))
    return cases


def signed_record(left, right):
    minimum = -(1 << 327)
    maximum = (1 << 327) - 1
    encode = lambda value: value.to_bytes(41, "little", signed=True).hex()
    checked = lambda value: encode(value) if minimum <= value <= maximum else "overflow"
    return ":".join((encode(left), encode(right), str((left > right) - (left < right)),
                     checked(left + right), checked(left - right)))


def generate():
    lines = [
        "// SPDX-License-Identifier: Apache-2.0",
        "// Generated by tools/generate_test_vectors.py; do not edit manually.",
        "// SHAKE vectors: CPython hashlib/OpenSSL; public wire fixtures: upstream KAT count=0.",
        "",
        "package ch.trancee.sqisign",
        "",
        "internal val shakeVectors: List<Pair<Int, String>> = listOf(",
    ]
    for length in (0, 1, 135, 136, 137, 272, 4096):
        data = bytes(index % 256 for index in range(length))
        lines.append(f"    {length} to {literal(hashlib.shake_256(data).hexdigest(512))},")
    lines += [")", "", "internal val prngVectors: List<String> = listOf("]
    for domain in (b"key", b"com", b"res", b"def"):
        lines.append(f"    {literal(hashlib.shake_256(bytes(range(48)) + domain).hexdigest(64))},")
    lines += [")", "", "internal val challengeVectors: List<String> = listOf("]
    for _, _, _, field_bytes, challenge_bytes, _ in PARAMETERS:
        pk = b"\x01" + bytes(2 * field_bytes - 1) + b"\xff"
        commitment = b"\x02" + bytes(2 * field_bytes - 1)
        transcript = b"SQI" + pk + commitment + b"Kotlin SQIsign\x00\xff"
        lines.append(f"    {literal(hashlib.shake_256(transcript).hexdigest(challenge_bytes))},")
    lines += [")", "", "internal val referenceVectors: List<ReferenceVector> = listOf("]
    for name, variant, secret_bytes, _, _, signature_bytes in PARAMETERS:
        path = ROOT.parent / "KAT" / f"PQCsignKAT_{secret_bytes}_SQIsign_{variant}.rsp"
        public_fields = {}
        with path.open() as source:
            for line in source:
                if line.strip() == "count = 1":
                    break
                key, separator, value = line.strip().partition(" = ")
                if separator and key in ("pk", "msg", "sm"):
                    public_fields[key] = bytes.fromhex(value)
        signed = public_fields["sm"]
        if signed[signature_bytes:] != public_fields["msg"]:
            raise ValueError(f"Inconsistent signed-message fixture: {path}")
        lines += [
            f"    // {path.relative_to(ROOT.parent)}, count = 0",
            "    ReferenceVector(",
            f"        ParameterSet.{name},",
            f"        {literal(public_fields['pk'].hex(), 12)},",
            f"        {literal(signed[:signature_bytes].hex(), 12)},",
            "    ),",
        ]
    lines += [
        ")", "",
        "// UInt328: Python integer oracle; C-reference cross-check: tools/check_unsigned_reference.py.",
        "// Record: left:right:sum:carry:difference:borrow:comparison:product:quotient:remainder.",
        "// Hex is little-endian; quotient/remainder are '-' for an invalid zero divisor.",
        "internal val unsignedVectors: List<String> = listOf(",
    ]
    for left, right in unsigned_cases():
        lines.append(f"    {literal(unsigned_record(left, right))},")
    lines += [
        ")", "",
        "// UInt656 / UInt328: Python integer oracle, cross-checked against pinned C division.",
        "// Record: dividend:divisor:quotient:remainder; little-endian hex or '-' for zero divisor.",
        "internal val wideDivisionVectors: List<String> = listOf(",
    ]
    for dividend, divisor in wide_division_cases():
        lines.append(f"    {literal(wide_division_record(dividend, divisor))},")
    lines += [
        ")", "",
        "// UInt328 GCD: Python math.gcd oracle, cross-checked against pinned C ibz_gcd.",
        "// Record: left:right:gcd; exact 41-byte little-endian hex.",
        "internal val gcdVectors: List<String> = listOf(",
    ]
    for left, right in gcd_cases():
        lines.append(f"    {literal(gcd_record(left, right))},")
    lines += [
        ")", "",
        "// UInt328 inverse: Python pow(value, -1, modulus), checked against pinned C ibz_invmod.",
        "// Record: value:modulus:inverse; little-endian hex or invalid_modulus/not_coprime.",
        "internal val inverseVectors: List<String> = listOf(",
    ]
    for value, modulus in inverse_cases():
        lines.append(f"    {literal(inverse_record(value, modulus))},")
    lines += [
        ")", "",
        "// Int328: Python signed integer oracle, cross-checked against pinned C ibz operations.",
        "// Record: left:right:comparison:sum:difference; two's-complement LE hex or overflow.",
        "internal val signedVectors: List<String> = listOf(",
    ]
    for left, right in signed_cases():
        lines.append(f"    {literal(signed_record(left, right))},")
    lines += [")", ""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Fail if committed vectors differ")
    args = parser.parse_args()
    generated = generate()
    if args.check:
        if OUTPUT.read_text() != generated:
            parser.exit(1, "Test vectors differ; run tools/generate_test_vectors.py\n")
    else:
        OUTPUT.write_text(generated)


if __name__ == "__main__":
    main()
