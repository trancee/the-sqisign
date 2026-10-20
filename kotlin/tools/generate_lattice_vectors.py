#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Independent Python model of the dimension-2 lattice reduction behind Cornacchia's algorithm.

The Lehmer-style partial extended GCD runs on Python integers with explicit 64-bit and 32-bit
machine-word semantics, and every Cornacchia result is checked against x^2 + y^2 = p. Pinned C
src/quaternion/ref/lvlx/lll/lehmer_xgcd.c and src/quaternion/ref/lvlx/integers.c check every record.
"""

import argparse
from math import isqrt

from generate_fixed_point_vectors import ct_shift
from generate_ibz_vectors import CAPACITIES, MASK64, ROOT, Z, add, checked, divide, mul, mul_by_int, operands, picks, \
    sample, set_bound, sign_extend
from generate_number_theory_vectors import is_prime, prime, sqrt_minus_one_verified, tight


OUTPUT = ROOT / "src/commonTest/kotlin/ch/trancee/sqisign/quaternion/LatticeVectors.kt"
# DIM2_* of lll_config.h.
THRESHOLD = 30
WINDOW = 2 * THRESHOLD + 3
WORK_HEADROOM = 32
WINDOW_FLOOR = -48
SQRT_SHIFT_CAP = 16
MASK32 = (1 << 32) - 1
# A product of primes 1 mod 4, so -1 has square roots without the modulus being prime.
COMPOSITE = 5 * 13 * 17 * 29


def value_bits(det_bits):
    return det_bits + 1


def work_bits(det_bits):
    return value_bits(det_bits) + WORK_HEADROOM


def leading_zeros(word):
    return 64 - word.bit_length()


def window(x, offset):
    return (((abs(x.value) >> max(offset, 0)) & MASK64) << max(-offset, 0)) & MASK64


def msb_transform(a, b, sqrt_a, swap):
    """The 2x2 int32 transform of partial_xgcd_msb on 64-bit windows; swaps only when allowed."""
    u = [[1, 0], [0, 1]]
    valid = True
    for _ in range(THRESHOLD + 1):
        valid &= a != 0 and b != 0
        if valid and a < b:
            if not swap:
                valid = False
            else:
                a, b = b, a
                u[0], u[1] = u[1], u[0]
        valid &= b >= sqrt_a
        valid &= leading_zeros(a | 1) <= THRESHOLD and leading_zeros(b | 1) <= THRESHOLD
        if valid:
            k = leading_zeros(b | 1) - leading_zeros(a | 1)
            if (b << k) & MASK64 > a:
                k -= 1
            a -= b << k
            for column in range(2):
                u[0][column] = sign_extend(u[0][column] - ((u[1][column] << k) & MASK32), 32)
    return u


def apply_transform(x, y, m, extra, det_bits, capacity):
    vbits, wbits = value_bits(det_bits), work_bits(det_bits)
    t1 = mul_by_int(x, m[1][0], wbits)
    t2 = mul_by_int(ct_shift(y, extra, max(vbits - THRESHOLD, 0), wbits, capacity), m[0][1], wbits)
    x = set_bound(add(mul_by_int(x, m[0][0], wbits), t2, capacity, checked=False), vbits)
    y = set_bound(add(mul_by_int(y, m[1][1], wbits), t1, capacity, checked=False), vbits)
    return x, y


def negate_if_negative(x, companion):
    return (Z(x.bitlen, -x.value), Z(companion.bitlen, -companion.value)) if x.value < 0 else (x, companion)


def partial_xgcd(a, b, c, d, det_bits, capacity):
    """partial_xgcd_lehmer on the column-HNF basis [[a, b], [c, d]] with det = a * d."""
    det = set_bound(mul(a, d, capacity), value_bits(det_bits))
    assert abs(det.value).bit_length() <= det_bits
    shift_2k = (abs(det.value).bit_length() - THRESHOLD) & ~1
    sqrt_a = isqrt(window(det, shift_2k) & MASK32)
    for _ in range(det_bits // (THRESHOLD - 1) + 1):
        if a.value < b.value:
            a, b, c, d = b, a, d, c
        bits_a, bits_b = abs(a.value).bit_length(), abs(b.value).bit_length()
        rho = bits_a - bits_b + 1
        large = rho >= THRESHOLD
        from_a = bits_a - WINDOW
        from_b = bits_b - (WINDOW - THRESHOLD + 1) if large else from_a
        extra = rho - THRESHOLD if large else 0
        offset = from_b - shift_2k // 2
        bound = MASK64 if offset <= WINDOW_FLOOR else \
            (sqrt_a >> min(SQRT_SHIFT_CAP, max(offset, 0))) << max(-offset, 0)
        m = msb_transform(window(a, from_a), window(b, from_b), bound, not large)
        a, b = apply_transform(a, b, m, extra, det_bits, capacity)
        c, d = apply_transform(c, d, m, extra, det_bits, capacity)
        a, c = negate_if_negative(a, c)
        b, d = negate_if_negative(b, d)
        for entry in (a, b, c, d):
            assert abs(entry.value).bit_length() < value_bits(det_bits)
    return a, b, c, d


def sum_of_squares(p, r, capacity):
    det_bits = max(p.bitlen, r.bitlen)
    assert work_bits(det_bits) + 1 <= capacity
    vbits = value_bits(det_bits)
    _, b, _, d = partial_xgcd(set_bound(p, vbits), set_bound(r, vbits), Z(vbits, 0), Z(vbits, 1), det_bits,
                              capacity)
    return Z(b.bitlen, abs(b.value)), Z(d.bitlen, abs(d.value))


def cornacchia(p, capacity):
    if p.value == 2:
        return True, Z(2, 1), Z(2, 1)
    if p.value == 1:
        return True, Z(1, 0), Z(2, 1)
    if p.value % 4 != 1:
        return False, None, None
    root = sqrt_minus_one_verified(p)
    if root is None:
        return False, None, None
    x, y = sum_of_squares(p, root, capacity)
    if x.value > y.value:
        x, y = y, x
    found = x.value ** 2 + y.value ** 2 == p.value
    assert not is_prime(p.value) or found
    return found, x, y


def rounded_div(a, b, capacity):
    q, r = divide(a, b)
    if 2 * abs(r.value) > abs(b.value):
        return add(q, Z(2, 1 if (a.value >= 0) == (b.value >= 0) else -1), capacity)
    return add(q, Z(2, 0), capacity)


def sum_two_squares(a, b, capacity):
    return add(mul(a, a, capacity), mul(b, b, capacity), capacity)


def roots_of(p):
    root = sqrt_minus_one_verified(p)
    return [root, Z(p.bitlen, p.value - root.value)] if root else []


def square_records(level, capacity, full):
    sizes = (5, 33, 62, 63, 64, 65, 100, 128, 200, 330, 512, 700, 1000) if full else (64, 330, 700)
    limit = capacity - work_bits(0) - 2
    cases = [(tight(value), None) for value in ((5, 13, 17) if full else ())]
    cases += [(tight(prime(f"{level}-cornacchia-{bits}", bits, 1)), None) for bits in sizes]
    composite = tight(COMPOSITE)
    for root in [r for r in range(COMPOSITE) if (r * r + 1) % COMPOSITE == 0][:(4 if full else 1)]:
        cases.append((composite, root))
    # a^2 + 1 has the square root a of -1 at any size, so the widest case needs no prime search.
    root = sample(f"{level}-cornacchia-top", (limit - 2) // 2) | 1 << ((limit - 2) // 2 - 1)
    cases.append((tight(root * root + 1), root))
    for index, (p, root) in enumerate(cases):
        for slack in ((0, 1, 64, 200) if full or index == len(cases) - 1 else (0,)):
            wide = set_bound(p, min(p.bitlen + slack, limit))
            for r in roots_of(wide) if root is None else [Z(wide.bitlen, root)]:
                x, y = sum_of_squares(wide, r, capacity)
                assert root is not None or x.value ** 2 + y.value ** 2 == p.value
                yield f"squares {level} {wide.text()} {r.text()} = {x.text()} {y.text()}"


def cornacchia_records(level, capacity, full):
    values = [1, 2, 3, 5, 7, 9, 13, 21, 65, COMPOSITE, 16901, 70229, 2 ** 89 - 1] if full else [5, 65]
    values += [prime(f"{level}-cornacchia-direct-{bits}", bits, 1) for bits in ((64, 255, 600) if full else (300,))]
    values += [prime(f"{level}-cornacchia-direct-3mod4", 200, 3)]
    for value in values:
        found, x, y = cornacchia(tight(value), capacity)
        results = f"1 {x.text()} {y.text()}" if found else "0"
        yield f"cornacchia {level} {tight(value).text()} = {results}"


def integer_records(level, capacity, full):
    pool = [z for z in operands(capacity, f"{level}-lattice") if z.bitlen <= capacity // 2 - 1]
    for a, b in picks(pool, f"{level}-rdiv", 30 if full else 8):
        if b.value:
            yield f"rdiv {level} {a.text()} {b.text()} = {rounded_div(a, b, capacity).text()}"
    for a, b in ((Z(8, 7), Z(3, 2)), (Z(8, -7), Z(3, 2)), (Z(8, 7), Z(3, -2)), (Z(8, -7), Z(3, -2)),
                 (Z(8, 5), Z(4, 3)), (Z(8, -5), Z(4, 3)), (Z(1, 0), Z(4, 3)), (Z(4, 3), Z(3, 2))):
        yield f"rdiv {level} {a.text()} {b.text()} = {rounded_div(a, b, capacity).text()}"
    for a, b in picks(pool, f"{level}-sq2", 20 if full else 6):
        yield f"sq2 {level} {a.text()} {b.text()} = {checked(sum_two_squares, a, b, capacity)}"


def records():
    for level, capacity in CAPACITIES.items():
        full = level == "p324_3"
        yield from square_records(level, capacity, full)
        yield from cornacchia_records(level, capacity, full)
        yield from integer_records(level, capacity, full)


def content():
    lines = "\n".join(f'        "{record}",' for record in records())
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_lattice_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.quaternion\n\n"
        "internal object LatticeVectors {\n"
        "    internal val records: List<String> = listOf(\n"
        f"{lines}\n"
        "    )\n"
        "}\n"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    expected = content()
    if arguments.check:
        if not OUTPUT.exists() or OUTPUT.read_text() != expected:
            raise SystemExit("Lattice vectors are stale; run tools/generate_lattice_vectors.py")
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(expected)
    print(f"Verified {expected.count(chr(10) + '        \"')} lattice records")


if __name__ == "__main__":
    main()
