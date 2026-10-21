#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Independent Python model of the dimension-2 Gram kernel of the dimension-4 lattice reduction.

The block materialisation and the Lehmer Lagrange-Gauss reduction run on Python integers with
explicit 64-bit and 32-bit machine-word semantics. Every reduction is checked to be unimodular and
to transform the input form exactly. Pinned C src/quaternion/ref/lvlx/lll/lll_lg2.c checks every
record.
"""

import argparse

from generate_fixed_point_vectors import ct_shift, highmul_p
from generate_ibz_vectors import CAPACITIES, MASK64, ROOT, Z, add, mul_by_int, sample, set_bound, sign_extend

OUTPUT = ROOT / "src/commonTest/kotlin/ch/trancee/sqisign/quaternion/GramVectors.kt"
# (SECURITY_BITS, QUAT_P_BITS) of each level.
LEVELS = {"p324_3": (128, 326), "p500_27": (192, 505), "p664_17": (256, 669)}
THRESHOLD = 29
WINDOW = 2 * THRESHOLD + 3
MSB_STEPS = THRESHOLD // 2 + 2
PRECISION_CLZ = 64 - WINDOW + THRESHOLD
OUTER_MIN = 4
OUTER_HALVE_ROUNDS = 3
MASK32 = (1 << 32) - 1


def limb_align(bits):
    return (bits + 63) // 64 * 64


class Config:
    """The lll_config.h constants of one level."""

    def __init__(self, level):
        security, prime = LEVELS[level]
        norm = prime + security
        gap = 2 * norm - prime
        self.u_bits = limb_align(gap // 4 + security // 2)
        self.p_bits = limb_align(2 * self.u_bits + security)
        self.l_bits = limb_align(self.p_bits + self.u_bits)
        self.e_bits = limb_align(2 * norm) - (prime - security - security // 2)
        self.outer_its = limb_align(gap // 4 + security // 2) // 8
        self.gram_tot = limb_align(max(gap + security, 0))
        self.gram_work = self.gram_tot + 32
        self.ldl_bits = self.e_bits + 16
        self.extra_max = (self.gram_tot - THRESHOLD + 2) // 2


def outer_its_for_round(config, round_index):
    shift = (round_index - 1 if round_index > 1 else 0) // OUTER_HALVE_ROUNDS
    return max(config.outer_its >> min(shift, 24), OUTER_MIN)


def bound_add(x, y, bits, capacity):
    return set_bound(add(x, y, capacity, checked=False), bits)


def bitsize(x):
    return abs(x.value).bit_length()


def materialise(config, dk, dk1, mu, capacity):
    width = config.ldl_bits
    b_full = highmul_p(mu, dk, config.p_bits, width, capacity)
    c_full = bound_add(dk1, highmul_p(mu, b_full, config.p_bits, width, capacity), width, capacity)
    total = bound_add(dk, c_full, width + 1, capacity)
    e = max(bitsize(total) - (config.gram_tot - 1), 0)
    a, b, c = (ct_shift(x, -e, width + 1, config.gram_tot, capacity) for x in (dk, b_full, c_full))
    assert a.value > 0 and c.value > 0
    return a, b, c, e


def leading_zeros(word):
    return 64 - word.bit_length()


def log2_ratio(a, b, clz_b):
    clz_a = leading_zeros(a | 1)
    norm_a = ((a << clz_a) & MASK64) >> 2
    three_b = 3 * (((b << clz_b) & MASK64) >> 2)
    return clz_b - clz_a + ((norm_a << 1) >= three_b) - ((norm_a << 2) < three_b)


def partial_gram_msb(a, b, c, swap):
    """partial_gram_msb on 64-bit windows; once a step is invalid the transform is final."""
    u00, u01, u10, u11 = 1, 0, 0, 1
    for _ in range(MSB_STEPS):
        if a == 0 or c == 0:
            break
        b_abs = abs(b) & MASK64
        if b < 0:
            u00, u10 = s32(-u00), s32(-u10)
        b = sign_extend(b_abs, 64)
        if c < a:
            if not swap:
                break
            a, c, u00, u01, u10, u11 = c, a, u01, u00, u11, u10
        clz_a = leading_zeros(a | 1)
        if (2 * b_abs) & MASK64 <= a or clz_a >= PRECISION_CLZ or leading_zeros(c | 1) >= PRECISION_CLZ:
            break
        k = max(0, log2_ratio(b_abs, a, clz_a))
        c = (c - (((b_abs << (k + 1)) - (a << (2 * k))) & MASK64)) & MASK64
        b = sign_extend(b - (a << k), 64)
        u01 = s32(u01 - ((u00 << k) & MASK32))
        u11 = s32(u11 - ((u10 << k) & MASK32))
    return u00, u01, u10, u11


def window_unsigned(x, offset):
    return ((abs(x.value) >> max(offset, 0)) << max(-offset, 0)) & MASK64


def window_signed(x, offset):
    magnitude = window_unsigned(x, offset) & ((1 << 63) - 1)
    return -magnitude if x.value < 0 else magnitude


def combination(terms, bits, capacity):
    total = None
    for value, factor in terms:
        product = mul_by_int(value, factor, bits)
        total = product if total is None else bound_add(total, product, bits, capacity)
    return total


def s32(value):
    return sign_extend(value, 32)


def apply_form(config, a, b, c, m, extra, capacity):
    m00, m01, m10, m11 = m
    w = config.gram_work
    a_shifted = ct_shift(a, extra, config.extra_max, w, capacity)
    b_shifted = ct_shift(b, extra, config.extra_max, w, capacity)
    a_shifted2 = ct_shift(a, 2 * extra, 2 * config.extra_max, w, capacity)
    na = combination(((a, s32(m00 * m00)), (b, s32(2 * m00 * m10)), (c, s32(m10 * m10))), w, capacity)
    nb = combination(((a_shifted, s32(m00 * m01)), (b, s32(m00 * m11 + m01 * m10)), (c, s32(m10 * m11))), w,
                     capacity)
    nc = combination(((a_shifted2, s32(m01 * m01)), (b_shifted, s32(2 * m01 * m11)), (c, s32(m11 * m11))), w,
                     capacity)
    return na, nb, nc


def apply_transform(config, u, m, extra, capacity):
    u00, u01, u10, u11 = u
    m00, m01, m10, m11 = m
    bits = config.u_bits
    shifted00 = ct_shift(u00, extra, config.extra_max, bits, capacity)
    shifted10 = ct_shift(u10, extra, config.extra_max, bits, capacity)
    return (combination(((u00, m00), (u01, m10)), bits, capacity),
            combination(((shifted00, m01), (u01, m11)), bits, capacity),
            combination(((u10, m00), (u11, m10)), bits, capacity),
            combination(((shifted10, m01), (u11, m11)), bits, capacity))


def gram_lehmer(config, a, b, c, iterations, capacity):
    u = tuple(Z(config.u_bits, v) for v in (1, 0, 0, 1))
    a, b, c = (set_bound(x, config.gram_work) for x in (a, b, c))
    for _ in range(iterations):
        if c.value < a.value:
            a, c = c, a
            u = (u[1], u[0], u[3], u[2])
        bits_a, bits_c = bitsize(a), bitsize(c)
        rho = bits_c - bits_a
        large = rho >= THRESHOLD
        from_c = max(bits_c - WINDOW, 0)
        extra = (rho - THRESHOLD + 2) // 2 if large else 0
        m = partial_gram_msb(window_unsigned(a, from_c - 2 * extra), window_signed(b, from_c - extra),
                             window_unsigned(c, from_c), not large)
        a, b, c = apply_form(config, a, b, c, m, extra, capacity)
        u = apply_transform(config, u, m, extra, capacity)
    return a, b, c, u


def check_reduction(original, reduced, u):
    a0, b0, c0 = (x.value for x in original)
    u00, u01, u10, u11 = (x.value for x in u)
    assert abs(u00 * u11 - u01 * u10) == 1
    a, b, c = (x.value for x in reduced)
    assert a == u00 * u00 * a0 + 2 * u00 * u10 * b0 + u10 * u10 * c0
    assert b == u00 * u01 * a0 + (u00 * u11 + u01 * u10) * b0 + u10 * u11 * c0
    assert c == u01 * u01 * a0 + 2 * u01 * u11 * b0 + u11 * u11 * c0


def ldl_inputs(level, config, full):
    """(D_k, D_{k+1}, mu) of exact bit sizes, with |mu| <= 1/2 at scale 2^P."""
    sizes = ((1, 1, 1), (40, 40, 0), (200, 600, 1), (600, 200, 1), (config.e_bits - 1, 64, 0),
             (config.e_bits - config.gram_tot + 40, config.e_bits - 1, 1), (config.e_bits - 1, config.e_bits - 1, 1), (300, 300, 0),
             (config.gram_tot - 3, config.gram_tot - 3, 1), (100, 100 + THRESHOLD + 40, 1))
    for index, (dk_bits, dk1_bits, negative) in enumerate(sizes if full else sizes[2:6]):
        label = f"{level}-ldl-{index}"
        dk = sample(label + "-dk", dk_bits - 1) | 1 << (dk_bits - 1)
        dk1 = sample(label + "-dk1", dk1_bits - 1) | 1 << (dk1_bits - 1)
        mu = sample(label + "-mu", config.p_bits - 1)
        if index == 1:
            mu = 1 << (config.p_bits - 1)
        yield (Z(config.e_bits, dk), Z(config.e_bits, dk1), Z(config.l_bits, -mu if negative else mu))


def basis_forms(level, config, full):
    """Gram forms of skewed bases whose reducing transform fits FP_U_BITS, the C domain."""
    shapes = ((20, 20, 180), (100, 5, 150), (150, 150, 40), (60, 60, 5), (10, 200, 0)) if full else \
        ((100, 5, 150), (150, 150, 40))
    for index, (first, second, skew) in enumerate(shapes):
        label = f"{level}-basis-{index}"
        x1, y1 = (sample(f"{label}-{n}", first) + 1 for n in "xy")
        x2, y2 = (sample(f"{label}-{n}2", second) + 1 for n in "xy")
        q = sample(f"{label}-q", skew) + 1
        x2, y2 = x2 + q * x1, y2 - q * y1
        a, b, c = x1 * x1 + y1 * y1, x1 * x2 + y1 * y2, x2 * x2 + y2 * y2
        if max(a, c).bit_length() < config.gram_tot - 1:
            yield tuple(Z(config.gram_tot, v) for v in (a, b, c))


def msb_records(level):
    cases = ((0, 5, 1 << 60, 1), (1 << 60, 5, 0, 1), (1 << 60, -(1 << 63), 1 << 61, 1), (1 << 61, 3, 1 << 60, 0),
             (1 << 61, 3, 1 << 60, 1), (1 << 40, (1 << 60) + 12345, (1 << 62) + 999, 1),
             (1 << 33, -(1 << 50), (1 << 60) + 7, 1), (5, 1, 1 << 62, 0), ((1 << 62) - 3, 1 << 61, 1 << 62, 1),
             (12345 << 30, -(98765 << 40), (1 << 62) + 77, 0))
    for a, b, c, swap in cases:
        m = partial_gram_msb(a, b, c, swap)
        yield f"gmsb {level} {a:x} {b & MASK64:x} {c:x} {swap} = {' '.join(str(v) for v in m)}"


def records():
    for level, capacity in CAPACITIES.items():
        full = level == "p324_3"
        config = Config(level)
        for round_index in range(0, 40 if full else 8):
            yield f"outer {level} {round_index} = {outer_its_for_round(config, round_index)}"
        if full:
            yield from msb_records(level)
        forms = []
        for dk, dk1, mu in ldl_inputs(level, config, full):
            a, b, c, e = materialise(config, dk, dk1, mu, capacity)
            forms.append((a, b, c))
            yield f"block {level} {dk.text()} {dk1.text()} {mu.text()} = {a.text()} {b.text()} {c.text()} {e}"
        forms += list(basis_forms(level, config, full))
        rounds = (0, 1, 4, 7, 10) if full else (1, 10)
        for index, form in enumerate(forms):
            for iterations in sorted({outer_its_for_round(config, r) for r in rounds} | ({0} if index == 0 else set())):
                a, b, c, u = gram_lehmer(config, *form, iterations, capacity)
                check_reduction(form, (a, b, c), u)
                results = " ".join(x.text() for x in (a, b, c, *u))
                yield f"gram {level} {' '.join(x.text() for x in form)} {iterations} = {results}"


def content():
    lines = "\n".join(f'        "{record}",' for record in records())
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_gram_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.quaternion\n\n"
        "internal object GramVectors {\n"
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
            raise SystemExit("Gram vectors are stale; run tools/generate_gram_vectors.py")
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(expected)
    print(f"Verified {expected.count(chr(10) + '        \"')} Gram records")


if __name__ == "__main__":
    main()
