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
VERIFY_INPUTS = ROOT / "src/jvmBenchmark/kotlin/ch/trancee/sqisign/benchmarks/VerifyInputs.kt"
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


def sqrt_floor_cases():
    """Public full-width inputs and near-squares, independent of Kotlin arithmetic."""
    maximum = (1 << 328) - 1
    cases = [0, 1, 2, 3, 4, maximum - 2, maximum - 1, maximum,
             int.from_bytes(b"\x55" * 41, "little")]
    for bit in range(1, 328, 5):
        power = 1 << bit
        cases.extend((power - 1, power, power + 1))
    for index in range(64):
        block = hashlib.shake_256(f"SQIsign UInt328 sqrt root vector {index}".encode()).digest(21)
        root = int.from_bytes(block, "little") & ((1 << 164) - 1)
        root = max(root, 1)
        square = root * root
        cases.extend((square - 1, square, square + 1))
    for index in range(128):
        block = hashlib.shake_256(f"SQIsign UInt328 sqrt floor public vector {index}".encode()).digest(41)
        cases.append(int.from_bytes(block, "little"))
    return list(dict.fromkeys(cases))


def sqrt_floor_record(value):
    encode = lambda integer: integer.to_bytes(41, "little").hex()
    return ":".join((encode(value), encode(math.isqrt(value))))


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


def fp324_cases():
    """Canonical p324_3 residues for independent modular operation vectors."""
    prime = 3 * (1 << 324) - 1
    boundaries = (0, 1, 2, 5, prime // 2, prime - 2, prime - 1)
    cases = [(left, right) for left in boundaries for right in boundaries]
    for index in range(64):
        block = hashlib.shake_256(f"SQIsign p324_3 field vector {index}".encode()).digest(82)
        cases.append((
            int.from_bytes(block[:41], "little") % prime,
            int.from_bytes(block[41:], "little") % prime,
        ))
    return cases


def fp324_record(left, right):
    prime = 3 * (1 << 324) - 1
    encode = lambda value: value.to_bytes(41, "little").hex()
    return ":".join((encode(left), encode(right), encode((left + right) % prime),
                     encode((left - right) % prime), encode((left * right) % prime)))


def fp324_nonlinear_cases():
    """Boundary, square and deterministic operands for field inverse and square-root checks."""
    prime = 3 * (1 << 324) - 1
    large_root = (1 << 200) + 12345
    large_square = large_root * large_root % prime
    return [0, 1, 5, 25, prime - 1, large_square]


def fp324_nonlinear_record(value):
    prime = 3 * (1 << 324) - 1
    encode = lambda integer: integer.to_bytes(41, "little").hex()
    inverse = pow(value, -1, prime) if value else "-"
    is_square = value == 0 or pow(value, (prime - 1) // 2, prime) == 1
    root = "-"
    if is_square:
        candidate = pow(value, (prime + 1) // 4, prime)
        root = min(candidate, (-candidate) % prime)
    return ":".join((encode(value), inverse if inverse == "-" else encode(int(inverse)),
                     str(int(is_square)), root if root == "-" else encode(root)))


def fp2_324_cases():
    """Canonical p324_3 extension-field operands for independent operation vectors."""
    return fp2_prime_field_cases(3, 324, 41, "p324_3")


def fp2_prime_field_cases(cofactor, torsion_exponent, width, label):
    prime = cofactor * (1 << torsion_exponent) - 1
    boundaries = (0, 1, 2, prime - 1)
    cases = [(a, b, c, d) for a in boundaries for b in boundaries
             for c, d in ((0, 1), (2, prime - 1))]
    for index in range(32):
        block = hashlib.shake_256(f"SQIsign {label} Fp2 vector {index}".encode()).digest(4 * width)
        cases.append(tuple(
            int.from_bytes(block[offset:offset + width], "little") % prime
            for offset in range(0, 4 * width, width)
        ))
    return cases


def fp2_324_record(a, b, c, d):
    return fp2_prime_field_record(a, b, c, d, 3, 324, 41)


def fp2_prime_field_record(a, b, c, d, cofactor, torsion_exponent, width):
    prime = cofactor * (1 << torsion_exponent) - 1
    encode = lambda value: value.to_bytes(width, "little").hex()
    addition = ((a + c) % prime, (b + d) % prime)
    subtraction = ((a - c) % prime, (b - d) % prime)
    multiplication = ((a * c - b * d) % prime, (a * d + b * c) % prime)
    square = ((a * a - b * b) % prime, 2 * a * b % prime)
    return ":".join(encode(value) for value in
                    (a, b, c, d, *addition, *subtraction, *multiplication, *square))


def fp2_324_square_root(real, imaginary, prime):
    return fp2_prime_field_square_root(real, imaginary, prime)


def fp2_prime_field_square_root(real, imaginary, prime):
    if real == 0 and imaginary == 0:
        return 0, 0
    norm = (real * real + imaginary * imaginary) % prime
    delta = pow(norm, (prime + 1) // 4, prime)
    inverse_two = pow(2, -1, prime)
    for candidate_delta in (delta, (-delta) % prime):
        real_square = (candidate_delta + real) * inverse_two % prime
        root_real = pow(real_square, (prime + 1) // 4, prime)
        if root_real * root_real % prime != real_square:
            continue
        if root_real == 0:
            if imaginary != 0:
                continue
            root_imaginary_square = (-real) % prime
            root_imaginary = pow(root_imaginary_square, (prime + 1) // 4, prime)
            if root_imaginary * root_imaginary % prime != root_imaginary_square:
                continue
        else:
            root_imaginary = imaginary * pow(2 * root_real, -1, prime) % prime
        if ((root_real * root_real - root_imaginary * root_imaginary) % prime,
            (2 * root_real * root_imaginary) % prime) == (real, imaginary):
            opposite = (-root_real % prime, -root_imaginary % prime)
            return min((root_real, root_imaginary), opposite, key=lambda pair: (pair[1], pair[0]))
    return None


def fp2_324_nonlinear_cases():
    """Zero, square, non-square and full-width extension-field values."""
    return fp2_prime_field_nonlinear_cases(3, 324, 41, "p324_3")


def fp2_prime_field_nonlinear_cases(cofactor, torsion_exponent, width, label):
    prime = cofactor * (1 << torsion_exponent) - 1
    nonsquare = next(
        (real, imaginary)
        for real in range(1, 16)
        for imaginary in range(1, 16)
        if pow((real * real + imaginary * imaginary) % prime, (prime - 1) // 2, prime)
        == prime - 1
    )
    root = ((1 << 200) + 12345, (1 << 190) + 54321)
    square = ((root[0] ** 2 - root[1] ** 2) % prime,
              (2 * root[0] * root[1]) % prime)
    large_square = None
    for index in range(16):
        candidate = int.from_bytes(
            hashlib.shake_256(f"{label} Fp2 branch vector {index}".encode()).digest(width),
            "little",
        ) % prime
        residue = candidate * candidate % prime
        if residue > prime // 2:
            large_square = residue
            break
    if large_square is None:
        raise RuntimeError("Unable to generate the p324_3 Fp2 second-root vector")
    return [
        (0, 0), (0, 1), (1, 0), (1, 1), (1, 2), (5, 0),
        nonsquare, (large_square, 0), square, (prime - 1, prime - 1),
    ]


def fp2_324_nonlinear_record(real, imaginary):
    return fp2_prime_field_nonlinear_record(real, imaginary, 3, 324, 41)


def fp2_prime_field_nonlinear_record(real, imaginary, cofactor, torsion_exponent, width):
    prime = cofactor * (1 << torsion_exponent) - 1
    encode = lambda value: value.to_bytes(width, "little").hex()
    if real == 0 and imaginary == 0:
        inverse = ("-", "-")
    else:
        norm_inverse = pow((real * real + imaginary * imaginary) % prime, -1, prime)
        inverse = (real * norm_inverse % prime, -imaginary * norm_inverse % prime)
    root = fp2_prime_field_square_root(real, imaginary, prime)
    is_square = root is not None
    inverse_fields = inverse if inverse == ("-", "-") else tuple(encode(value) for value in inverse)
    root_fields = ("-", "-") if root is None else tuple(encode(value) for value in root)
    return ":".join((encode(real), encode(imaginary), *inverse_fields,
                     str(int(is_square)), *root_fields))


def fp2_prime_field_control_record(real, imaginary, other_real, other_imaginary,
                                   control, cofactor, torsion_exponent, width):
    prime = cofactor * (1 << torsion_exponent) - 1
    negative = ((-real) % prime, (-imaginary) % prime)
    selected = (other_real, other_imaginary) if control else (real, imaginary)
    swapped = ((other_real, other_imaginary), (real, imaginary)) if control else (
        (real, imaginary), (other_real, other_imaginary))
    encode = lambda value: value.to_bytes(width, "little").hex()
    is_negative = (imaginary, real) > (negative[1], negative[0])
    return ":".join((str(int(is_negative)), encode(selected[0]), encode(selected[1]),
                     *(encode(value) for pair in swapped for value in pair)))


def prime_field_cases(cofactor, torsion_exponent, width, label):
    prime = cofactor * (1 << torsion_exponent) - 1
    boundaries = (0, 1, 2, prime - 1)
    cases = [(left, right) for left in boundaries for right in boundaries]
    for index in range(32):
        block = hashlib.shake_256(f"{label} field vector {index}".encode()).digest(2 * width)
        cases.append(tuple(
            int.from_bytes(block[offset:offset + width], "little") % prime
            for offset in (0, width)
        ))
    return cases


def prime_field_record(left, right, cofactor, torsion_exponent, width):
    prime = cofactor * (1 << torsion_exponent) - 1
    encode = lambda value: value.to_bytes(width, "little").hex()
    return ":".join(encode(value) for value in (
        left, right, (left + right) % prime, (left - right) % prime,
        (left * right) % prime, (left * left) % prime,
    ))


def prime_field_control_record(left, right, control, cofactor, torsion_exponent, width):
    prime = cofactor * (1 << torsion_exponent) - 1
    negative = (-left) % prime
    selected = right if control else left
    swapped_left, swapped_right = (right, left) if control else (left, right)
    encode = lambda value: value.to_bytes(width, "little").hex()
    return ":".join((str(int(left > negative)), encode(selected),
                     encode(swapped_left), encode(swapped_right)))


def prime_field_nonlinear_cases(cofactor, torsion_exponent, width, label):
    prime = cofactor * (1 << torsion_exponent) - 1
    nonsquare = next(value for value in range(2, 32)
                     if pow(value, (prime - 1) // 2, prime) == prime - 1)
    root = int.from_bytes(
        hashlib.shake_256(f"{label} nonlinear square root".encode()).digest(width),
        "little",
    ) % prime
    return [0, 1, nonsquare, prime - 1, root * root % prime]


def prime_field_nonlinear_record(value, cofactor, torsion_exponent, width):
    prime = cofactor * (1 << torsion_exponent) - 1
    encode = lambda item: item.to_bytes(width, "little").hex()
    inverse = "-" if value == 0 else encode(pow(value, -1, prime))
    root = pow(value, (prime + 1) // 4, prime)
    if root * root % prime != value:
        root = "-"
    else:
        root = encode(min(root, (-root) % prime))
    return ":".join((encode(value), inverse, str(int(root != "-")), root))


def kat_zero_records():
    """Yields `(name, path, pk, signature, msg)` for the first public KAT record of every level."""
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
        yield name, path, public_fields["pk"], signed[:signature_bytes], public_fields["msg"]


def generate_verify_inputs():
    lines = [
        "// SPDX-License-Identifier: Apache-2.0",
        "// Generated by tools/generate_test_vectors.py; do not edit.",
        "",
        "package ch.trancee.sqisign.benchmarks",
        "",
        "import ch.trancee.sqisign.ParameterSet",
        "",
        "/** Public KAT record 0 as public key, signature and message hex. */",
        "internal fun verifyInputs(parameter: ParameterSet): List<String> =",
        "    when (parameter) {",
    ]
    for name, path, public_key, signature, message in kat_zero_records():
        lines += [
            f"        // {path.relative_to(ROOT.parent)}, count = 0",
            f"        ParameterSet.{name} ->",
            "            listOf(",
            *(f"                {literal(value.hex(), 20)}," for value in (public_key, signature, message)),
            "            )",
        ]
    lines += ["    }", ""]
    return "\n".join(lines)


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
    for name, path, public_key, signature, message in kat_zero_records():
        lines += [
            f"    // {path.relative_to(ROOT.parent)}, count = 0",
            "    ReferenceVector(",
            f"        ParameterSet.{name},",
            f"        {literal(public_key.hex(), 12)},",
            f"        {literal(signature.hex(), 12)},",
            f"        {literal(message.hex(), 12)},",
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
        "// UInt328 floor square root: Python math.isqrt oracle, checked against pinned C ibz_sqrt_floor.",
        "// Record: input:floor-root; exact 41-byte unsigned little-endian hex.",
        "internal val sqrtFloorVectors: List<String> = listOf(",
    ]
    for value in sqrt_floor_cases():
        lines.append(f"    {literal(sqrt_floor_record(value))},")
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
        "// Fp324: Python modular-integer oracle, checked against the pinned p324_3 C field implementation.",
        "// Record: left:right:add:subtract:multiply; canonical 41-byte little-endian residues.",
        "internal val fp324Vectors: List<String> = listOf(",
    ]
    for left, right in fp324_cases():
        lines.append(f"    {literal(fp324_record(left, right))},")
    lines += [
        ")", "",
        "// Fp324 inverse, quadratic-residue and canonical square-root vectors from Python integers.",
        "// Record: value:inverse:is-square:smaller-root; '-' marks zero inverse or non-residue root.",
        "internal val fp324NonlinearVectors: List<String> = listOf(",
    ]
    for value in fp324_nonlinear_cases():
        lines.append(f"    {literal(fp324_nonlinear_record(value))},")
    lines += [
        ")", "",
        "// Fp2_324: Python finite-field oracle for i^2 = -1, cross-checked against pinned C.",
        "// Record: a.re:a.im:b.re:b.im:add:sub:mul:square; each pair is two 41-byte residues.",
        "internal val fp2_324Vectors: List<String> = listOf(",
    ]
    for a, b, c, d in fp2_324_cases():
        lines.append(f"    {literal(fp2_324_record(a, b, c, d))},")
    lines += [
        ")", "",
        "// Fp2_324 inverse, quadratic-residue and canonical-root records from Python integers.",
        "// Record: re:im:inverse.re:inverse.im:is-square:root.re:root.im; '-' marks failure.",
        "internal val fp2_324NonlinearVectors: List<String> = listOf(",
    ]
    for real, imaginary in fp2_324_nonlinear_cases():
        lines.append(f"    {literal(fp2_324_nonlinear_record(real, imaginary))},")
    lines += [
        ")", "",
        "// p500_27 base-field arithmetic vectors generated by Python modular integers.",
        "// Record: a:b:add:sub:mul:square; each item is a 64-byte little-endian residue.",
        "internal val fp500_27Vectors: List<String> = listOf(",
    ]
    for left, right in prime_field_cases(27, 500, 64, "p500_27"):
        lines.append(f"    {literal(prime_field_record(left, right, 27, 500, 64))},")
    lines += [
        ")", "",
        "// p500_27 inverse, quadratic-residue and canonical-root records from Python integers.",
        "// Record: value:inverse:is-square:smaller-root; '-' marks zero inverse or non-residue root.",
        "internal val fp500_27NonlinearVectors: List<String> = listOf(",
    ]
    for value in prime_field_nonlinear_cases(27, 500, 64, "p500_27"):
        lines.append(f"    {literal(prime_field_nonlinear_record(value, 27, 500, 64))},")
    lines += [
        ")", "",
        "// p664_17 base-field arithmetic vectors generated by Python modular integers.",
        "// Record: a:b:add:sub:mul:square; each item is an 84-byte little-endian residue.",
        "internal val fp664_17Vectors: List<String> = listOf(",
    ]
    for left, right in prime_field_cases(17, 664, 84, "p664_17"):
        lines.append(f"    {literal(prime_field_record(left, right, 17, 664, 84))},")
    lines += [
        ")", "",
        "// p664_17 inverse, quadratic-residue and canonical-root records from Python integers.",
        "// Record: value:inverse:is-square:smaller-root; '-' marks zero inverse or non-residue root.",
        "internal val fp664_17NonlinearVectors: List<String> = listOf(",
    ]
    for value in prime_field_nonlinear_cases(17, 664, 84, "p664_17"):
        lines.append(f"    {literal(prime_field_nonlinear_record(value, 17, 664, 84))},")
    lines += [
        ")", "",
        "// p500_27 extension-field vectors for i^2 = -1 from Python modular integers.",
        "// Operations: add, subtract, multiply and square over strict real-then-imaginary values.",
        "internal val fp2_500Vectors: List<String> = listOf(",
    ]
    for a, b, c, d in fp2_prime_field_cases(27, 500, 64, "p500_27"):
        lines.append(f"    {literal(fp2_prime_field_record(a, b, c, d, 27, 500, 64))},")
    lines += [
        ")", "",
        "// p500_27 extension-field inverse, residue and canonical-root vectors from Python.",
        "// Record: re:im:inverse.re:inverse.im:is-square:root.re:root.im; '-' marks failure.",
        "internal val fp2_500NonlinearVectors: List<String> = listOf(",
    ]
    for real, imaginary in fp2_prime_field_nonlinear_cases(27, 500, 64, "p500_27"):
        lines.append(
            f"    {literal(fp2_prime_field_nonlinear_record(real, imaginary, 27, 500, 64))},"
        )
    lines += [
        ")", "",
        "// p664_17 extension-field vectors for i^2 = -1 from Python modular integers.",
        "// Operations: add, subtract, multiply and square over strict real-then-imaginary values.",
        "internal val fp2_664Vectors: List<String> = listOf(",
    ]
    for a, b, c, d in fp2_prime_field_cases(17, 664, 84, "p664_17"):
        lines.append(f"    {literal(fp2_prime_field_record(a, b, c, d, 17, 664, 84))},")
    lines += [
        ")", "",
        "// p664_17 extension-field inverse, residue and canonical-root vectors from Python.",
        "// Record: re:im:inverse.re:inverse.im:is-square:root.re:root.im; '-' marks failure.",
        "internal val fp2_664NonlinearVectors: List<String> = listOf(",
    ]
    for real, imaginary in fp2_prime_field_nonlinear_cases(17, 664, 84, "p664_17"):
        lines.append(
            f"    {literal(fp2_prime_field_nonlinear_record(real, imaginary, 17, 664, 84))},"
        )
    lines += [")", ""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Fail if committed vectors differ")
    args = parser.parse_args()
    for output, generated in ((OUTPUT, generate()), (VERIFY_INPUTS, generate_verify_inputs())):
        if args.check:
            if output.read_text() != generated:
                parser.exit(1, "Test vectors differ; run tools/generate_test_vectors.py\n")
        else:
            output.write_text(generated)


if __name__ == "__main__":
    main()
