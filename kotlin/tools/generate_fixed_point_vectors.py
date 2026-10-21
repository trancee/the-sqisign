#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Independent Python model of the ibz_t fixed-point layer used by the quaternion lattice reduction.

Shifts and products are exact on Python integers and then sign-truncated to the requested bound;
the reciprocal is Newton-Raphson from a 64-bit seed. Pinned C src/mp/ref/lvlx/mp.c checks every record.
"""

import argparse

from generate_ibz_vectors import CAPACITIES, MASK64, ROOT, Z, add, limbs, operands, picks, sample, set_bound, sign_extend


OUTPUT = ROOT / "src/commonTest/kotlin/ch/trancee/sqisign/arithmetic/IbzFixedPointVectors.kt"
# FP_P_BITS of lll_config.h at each level, and FP_NR_ROUNDS.
LEVEL_PRECISION = {"p324_3": 640, "p500_27": 832, "p664_17": 1152}
NEWTON_ROUNDS = 5
SEED_NUMERATOR = (1 << 127) - 1


def signed_shift(value, shift):
    return value << shift if shift >= 0 else value >> -shift


def ct_shift(a, shift, max_shift, out, capacity):
    assert abs(shift) <= max_shift and 1 <= out <= capacity and a.bitlen <= capacity
    return Z(out, sign_extend(signed_shift(a.value, shift), out))


def product_words(a, b, out, capacity):
    assert limbs(out) <= limbs(a.bitlen) + limbs(b.bitlen) + 1 and 1 <= out <= capacity


def highmul_p(a, b, precision, out, capacity):
    product_words(a, b, out, capacity)
    return Z(out, sign_extend((a.value * b.value) >> precision, out))


def highmul_s(a, b, shift, max_shift, out, capacity):
    assert abs(shift) <= max_shift
    product_words(a, b, out, capacity)
    return Z(out, sign_extend(signed_shift(a.value * b.value, shift), out))


def fp_recip(d, precision, rounds, capacity):
    assert d.value > 0 and precision > 63 and precision + 6 <= capacity
    size = d.value.bit_length()
    width = precision + 4
    normalised = ct_shift(d, precision - size, max(precision, d.bitlen), width, capacity)
    top = (normalised.value >> (precision - 64)) & MASK64
    w = ct_shift(Z(65, SEED_NUMERATOR // top), precision - 63, precision - 63, width, capacity)
    two = set_bound(Z(precision + 3, 1 << (precision + 1)), width + 2)
    for _ in range(rounds):
        t = set_bound(highmul_p(normalised, w, precision, width, capacity), width + 2)
        w = highmul_p(w, add(two, t, capacity, negate=True), precision, width, capacity)
    return set_bound(w, width), size


def round_shift_limb(x, precision, out, capacity):
    assert precision % 64 == 0 and out > 0 and out % 64 == 0 and precision + out <= capacity
    words = set_bound(x, precision + out).value % (1 << (precision + out))
    round_bit = (words >> (precision - 1)) & 1 if precision else 0
    return Z(out, ((words >> precision) + round_bit) % (1 << out))


def shift_records(level, capacity, pool, full):
    shapes = [(0, 0, 1), (5, 5, 64), (-5, 5, 64), (63, 64, 130), (-64, 64, 70), (-130, 200, 129),
              (200, 200, 400), (-1000, 1000, 64), (1, 1, capacity), (-1, 1, capacity - 1)]
    for index, a in enumerate(pool[::(1 if full else 3)]):
        shift, max_shift, out = shapes[index % len(shapes)]
        yield f"cshift {level} {a.text()} {shift} {max_shift} {out} = " \
              f"{ct_shift(a, shift, max_shift, out, capacity).text()}"


def product_records(level, capacity, pool, precision, full):
    small = [z for z in pool if z.bitlen <= capacity // 2]
    shapes = [(0, 64), (1, 64), (63, 65), (64, 128), (130, 200), (precision, precision + 4), (precision, 1)]
    for index, (a, b) in enumerate(picks(small, f"{level}-high", 40 if full else 14)):
        p, out = shapes[index % len(shapes)]
        out = min(out, 64 * (limbs(a.bitlen) + limbs(b.bitlen) + 1))
        yield f"highp {level} {a.text()} {b.text()} {p} {out} = {highmul_p(a, b, p, out, capacity).text()}"
        shift = (-p, p, 0)[index % 3]
        max_shift = abs(shift) + index % 2
        yield f"highs {level} {a.text()} {b.text()} {shift} {max_shift} {out} = " \
              f"{highmul_s(a, b, shift, max_shift, out, capacity).text()}"
    top = Z(capacity, (1 << (capacity - 1)) - 1)
    yield f"highp {level} {top.text()} {top.text()} {capacity} {capacity} = " \
          f"{highmul_p(top, top, capacity, capacity, capacity).text()}"
    yield f"highs {level} {top.text()} {Z(2, -1).text()} -1 1 {capacity} = " \
          f"{highmul_s(top, Z(2, -1), -1, 1, capacity, capacity).text()}"


def reciprocal_records(level, capacity, precision, full):
    divisors = [Z(2, 1), Z(3, 3), Z(65, 1 << 63), Z(65, MASK64), Z(precision + 1, (1 << precision) - 1)]
    divisors += [Z(precision + 1, 1 << (precision - 1)), Z(2 * precision, sample(f"{level}-wide", 2 * precision - 1))]
    divisors += [Z(bits + 1, sample(f"{level}-recip-{bits}", bits) | 1 << (bits - 1))
                 for bits in (40, 130, precision // 2, precision - 3, precision + 70)]
    for index, d in enumerate(divisors[::(1 if full else 2)]):
        p = (precision, 64, 200)[index % 3] if full else precision
        rounds = (NEWTON_ROUNDS, 0, 2)[index % 3] if full else NEWTON_ROUNDS
        w, size = fp_recip(d, p, rounds, capacity)
        yield f"recip {level} {d.text()} {p} {rounds} = {w.text()} {size}"


def rounding_records(level, capacity, pool, precision, full):
    shapes = [(0, 64), (64, 64), (128, 64), (precision, 128), (64, 192), (capacity - 64, 64)]
    for index, x in enumerate(pool[::(2 if full else 5)]):
        p, out = shapes[index % len(shapes)]
        yield f"rshift {level} {x.text()} {p} {out} = {round_shift_limb(x, p, out, capacity).text()}"
    for value in (1 << 63, -(1 << 63), (1 << 127) - 1, -(1 << 64) - (1 << 63)):
        x = Z(192, value)
        yield f"rshift {level} {x.text()} 64 64 = {round_shift_limb(x, 64, 64, capacity).text()}"


def comparison_records(level, capacity, pool, full):
    for a, b in picks(pool, f"{level}-lt", 24 if full else 8):
        yield f"ltmask {level} {a.text()} {b.text()} = {-1 if a.value < b.value else 0}"
    yield f"ltmask {level} {Z(64, -1).text()} {Z(capacity, 0).text()} = -1"


def records():
    for level, capacity in CAPACITIES.items():
        full = level == "p324_3"
        pool = operands(capacity, f"{level}-fixed")
        precision = LEVEL_PRECISION[level]
        yield from shift_records(level, capacity, pool, full)
        yield from product_records(level, capacity, pool, precision, full)
        yield from reciprocal_records(level, capacity, precision, full)
        yield from rounding_records(level, capacity, pool, precision, full)
        yield from comparison_records(level, capacity, pool, full)


def content():
    lines = "\n".join(f'        "{record}",' for record in records())
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_fixed_point_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.arithmetic\n\n"
        "internal object IbzFixedPointVectors {\n"
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
            raise SystemExit("Fixed-point vectors are stale; run tools/generate_fixed_point_vectors.py")
    else:
        OUTPUT.write_text(expected)
    print(f"Verified {expected.count(chr(10) + '        \"')} fixed-point records")


if __name__ == "__main__":
    main()
