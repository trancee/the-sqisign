#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Independent Python model of the rank-2 Gaussian-integer reduction of O0-ideals.

The Lehmer-style Lagrange-Gauss reduction over Z[i] runs on bounded (bitlen, value) integers with
explicit 64-bit and 32-bit machine-word semantics. Inputs are honest products of two inert
O0-ideals. Every result is checked to be a unimodular change of basis whose Z[i]-norms equal those
of an exact rational Lagrange-Gauss reduction, oriented canonically. Pinned C
src/quaternion/ref/lvlx/lll/gaussian_xgcd.c checks every record.
"""

import argparse
from fractions import Fraction
from math import isqrt

from generate_dual_vectors import PARAMETER_SETS, PRIMES, hex_text, inert_ideal, rational_inverse
from generate_fixed_point_vectors import ct_shift
from generate_gram_vectors import LEVELS, window_signed
from generate_ibz_vectors import CAPACITIES, MASK64, OVERFLOW, ROOT, Z, add, mul, mul_by_int, set_bound, sign_extend

OUTPUT = ROOT / "src/commonTest/kotlin/ch/trancee/sqisign/quaternion/GaussianVectors.kt"
BENCHMARK_OUTPUT = ROOT / "src/jvmBenchmark/kotlin/ch/trancee/sqisign/benchmarks/GaussianInputs.kt"
# DIM2I_* of lll_config.h.
THRESHOLD = 27
WINDOW = 2 * THRESHOLD + 5
UP_SHIFT = 3
INNER_ITS = THRESHOLD + 1
STOP_SHIFT = THRESHOLD + (64 - UP_SHIFT - WINDOW)
MASK32 = (1 << 32) - 1
# QUAT_SQRT_P_BITS of each level's quaternion_constants.h.
SQRT_P_BITS = {"p324_3": 163, "p500_27": 253, "p664_17": 335}
INNER_SAMPLES = 3


def s64(value):
    return sign_extend(value & MASK64, 64)


def s32(value):
    return sign_extend(value & MASK32, 32)


def clz(word):
    assert 0 <= word <= MASK64
    return 64 - word.bit_length()


def abs64(x):
    return abs(x) & MASK64


def shift64(x, k):
    assert 0 <= k < 64
    return s64(x << k)


def shift32(x, k):
    assert 0 <= k < 32
    return s32(x << k)


def mul_high(a, b):
    return (a * b) >> 64


def sqr_high(a):
    return ((a * a) >> 64) & MASK64


def shr_sat(x, s):
    return x >> s if s < 64 else 0


def ceil_log2_diff(a, b):
    d = clz(b | 1) - clz(a | 1)
    shifted_b = (b << d) & MASK64 if d >= 0 else b
    shifted_a = a if d >= 0 else (a << -d) & MASK64
    return d + (((shifted_b - shifted_a) & MASK64) >> 63)


def round_ratio(a, b):
    magnitude = abs64(a)
    half = b >> 1
    rounded = int(magnitude > half) + int(magnitude > ((b + half) & MASK64))
    return -rounded if a < 0 else rounded


def complex_mul32(x, y):
    return s32(x[0] * y[0] - x[1] * y[1]), s32(x[0] * y[1] + x[1] * y[0])


def gaussian_inner(a, b, c, d, swap, bits_v1, bits_v2):
    """LG_gaussian_inner on signed 64-bit complex windows; returns the int32 complex transform."""
    u = [[(1, 0), (0, 0)], [(0, 0), (1, 0)]]
    valid = True
    for _ in range(INNER_ITS):
        if not valid:
            break
        shift_v1 = max(min(clz(abs64(a[0]) | abs64(a[1]) | 1), clz(abs64(c[0]) | abs64(c[1]) | 1)) - UP_SHIFT, 0)
        shift_v2 = max(min(clz(abs64(b[0]) | abs64(b[1]) | 1), clz(abs64(d[0]) | abs64(d[1]) | 1)) - UP_SHIFT, 0)
        if (shift_v1 > STOP_SHIFT and bits_v1 > 0) or (shift_v2 > STOP_SHIFT and bits_v2 > 0):
            break
        a_base, c_base = [tuple(shift64(x, shift_v1) for x in v) for v in (a, c)]
        b_base, d_base = [tuple(shift64(x, shift_v2) for x in v) for v in (b, d)]
        na = sum(sqr_high(x) for x in (*a_base, *c_base)) & MASK64
        nb = sum(sqr_high(x) for x in (*b_base, *d_base)) & MASK64
        relative = shift_v2 - shift_v1
        right_v1 = -2 * relative if relative < 0 else 0
        right_v2 = 2 * relative if relative > 0 else 0
        if ((shr_sat(na, right_v1) - shr_sat(nb, right_v2)) & MASK64) >> 63:
            if not swap:
                break
            na, nb, a, b, c, d = nb, na, b, a, d, c
            u[0], u[1] = u[1], u[0]
            a_base, b_base, c_base, d_base = b_base, a_base, d_base, c_base
            shift_v1, shift_v2 = shift_v2, shift_v1
            bits_v1, bits_v2 = bits_v2, bits_v1
        relative = shift_v2 - shift_v1
        x = s64(mul_high(a_base[0], b_base[0]) + mul_high(a_base[1], b_base[1]) + mul_high(c_base[0], d_base[0]) +
                mul_high(c_base[1], d_base[1]))
        y = s64(mul_high(a_base[1], b_base[0]) - mul_high(a_base[0], b_base[1]) + mul_high(c_base[1], d_base[0]) -
                mul_high(c_base[0], d_base[1]))
        s = ceil_log2_diff(max(abs64(x), abs64(y)), nb) - 1
        k = max(relative + s, 0)
        b_shifted = tuple(shift64(v, k) for v in b)
        d_shifted = tuple(shift64(v, k) for v in d)
        difference = relative - k
        nb_adjusted = (nb << max(-difference, 0)) & MASK64
        x_round = round_ratio(s64(x << max(difference, 0)), nb_adjusted)
        y_round = round_ratio(s64(y << max(difference, 0)), nb_adjusted)
        if x_round == 0 and y_round == 0:
            break

        def reduce(v, w):
            return (s64(v[0] - (x_round * w[0] - y_round * w[1])), s64(v[1] - (x_round * w[1] + y_round * w[0])))

        a, c = reduce(a, b_shifted), reduce(c, d_shifted)
        for column in range(2):
            step = complex_mul32((x_round, y_round), tuple(shift32(v, k) for v in u[1][column]))
            u[0][column] = (s32(u[0][column][0] - step[0]), s32(u[0][column][1] - step[1]))
    return u


class Gaussian:
    """The public widths of quat_lll_reduce_O0_ideal at one level for a public input bound."""

    def __init__(self, level, bound):
        self.level = level
        self.capacity = CAPACITIES[level]
        self.prime_bits = LEVELS[level][1]
        exponent, cofactor = PRIMES[level]
        self.sqrt_p = isqrt(cofactor * (1 << exponent) - 1)
        assert self.sqrt_p.bit_length() == SQRT_P_BITS[level]
        self.in_bits = max(bound, SQRT_P_BITS[level] + 1)
        self.vbits = self.in_bits + 1
        self.wbits = self.vbits + 32
        self.hbits = 2 * self.vbits + self.prime_bits + 3
        self.shift_max = max(self.vbits - THRESHOLD, 0)
        drop = (max(self.in_bits - SQRT_P_BITS[level], 0) + 1) // 2 + 1
        self.outer_its = (drop + THRESHOLD - 2) // (THRESHOLD - 1) + 4
        assert self.wbits + 1 <= self.capacity and self.hbits <= self.capacity


def bounded_sum(x, y, bits, capacity, negate=False):
    return set_bound(add(x, y, capacity, checked=False, negate=negate), bits)


def complex_scale(config, value, scalar):
    """ct_complex_mul_si: value * (scalar.re + i scalar.im), products at the working width."""
    w, capacity = config.wbits, config.capacity
    re = bounded_sum(mul_by_int(value[0], scalar[0], w), mul_by_int(value[1], -scalar[1], w), w, capacity)
    im = bounded_sum(mul_by_int(value[0], scalar[1], w), mul_by_int(value[1], scalar[0], w), w, capacity)
    return re, im


def apply_matrix(config, x, y, m, extra):
    capacity, v = config.capacity, config.vbits
    t1 = complex_scale(config, x, m[1][0])
    shifted = tuple(ct_shift(part, extra, config.shift_max, config.wbits, capacity) for part in y)
    t2 = complex_scale(config, shifted, m[0][1])
    x = complex_scale(config, x, m[0][0])
    x = tuple(bounded_sum(p, q, v, capacity) for p, q in zip(x, t2))
    y = complex_scale(config, y, m[1][1])
    y = tuple(bounded_sum(p, q, v, capacity) for p, q in zip(y, t1))
    return x, y


def size_in_base2(value):
    return max(abs(part.value).bit_length() for part in value)


def orient(key, *others):
    """orient_canonical: multiply every complex value by the unit making Re(key), Im(key) >= 0."""
    values = [key, *others]
    if (key[0].value < 0) != (key[1].value < 0):
        values = [(Z(im.bitlen, -im.value), re) for re, im in values]
    if values[0][0].value < 0 or values[0][1].value < 0:
        values = [tuple(Z(part.bitlen, -part.value) for part in v) for v in values]
    return values


def lehmer_gaussian(config, aa, bb, cc, dd, ee, ff, trace):
    for _ in range(config.outer_its):
        bits_v1 = max(size_in_base2(aa), size_in_base2(cc))
        bits_v2 = max(size_in_base2(bb), size_in_base2(dd))
        if bits_v1 < bits_v2:
            aa, bb, cc, dd, ee, ff = bb, aa, dd, cc, ff, ee
            bits_v1, bits_v2 = bits_v2, bits_v1
        rho = bits_v1 - bits_v2 + 1
        from_v1 = bits_v1 - WINDOW if bits_v1 >= WINDOW else 0
        large = rho >= THRESHOLD
        from_v2 = max(bits_v2 - (WINDOW - THRESHOLD + 1), 0) if large else from_v1
        extra = rho - THRESHOLD if large else 0
        windows = [tuple(window_signed(part, offset) for part in value)
                   for value, offset in ((aa, from_v1), (bb, from_v2), (cc, from_v1), (dd, from_v2))]
        arguments = (*windows, not large, from_v1, from_v2)
        m = gaussian_inner(*arguments)
        trace.append((arguments, m))
        aa, bb = apply_matrix(config, aa, bb, m, extra)
        cc, dd = apply_matrix(config, cc, dd, m, extra)
        ee, ff = apply_matrix(config, ee, ff, m, extra)
    # The kernel's last effective step leaves the larger vector first, so the swap is unconditional.
    aa, bb, cc, dd, ee, ff = bb, aa, dd, cc, ff, ee
    aa, cc, ee = orient(aa, cc, ee)
    bb, dd, ff = orient(bb, dd, ff)
    return aa, bb, cc, dd, ee, ff


def hermitian(config, v1, v2, w1, w2, p):
    """ct_zi_hermitian: conj(v1) w1 + p conj(v2) w2 with every product bounded to the Hermitian width."""
    h, capacity = config.hbits, config.capacity

    def product(x, y):
        return set_bound(mul(x, y, capacity), h)

    def part(first, second, negate):
        return bounded_sum(product(*first), product(*second), h, capacity, negate=negate)

    re = bounded_sum(part((v1[0], w1[0]), (v1[1], w1[1]), False),
                     product(part((v2[0], w2[0]), (v2[1], w2[1]), False), p), h, capacity)
    im = bounded_sum(part((v1[0], w1[1]), (v1[1], w1[0]), True),
                     product(part((v2[0], w2[1]), (v2[1], w2[0]), True), p), h, capacity)
    return re, im


def reduce_o0_ideal(config, hnf, p, trace):
    """quat_lll_reduce_O0_ideal on the numerator of an inert HNF with denominator 2."""
    capacity, w = config.capacity, config.vbits
    s = set_bound(Z(SQRT_P_BITS[config.level] + 1, config.sqrt_p), SQRT_P_BITS[config.level] + 1)
    assert s.value ** 2 <= p.value < (s.value + 1) ** 2

    def column(c, first):
        return tuple(set_bound(hnf[first + r][c], w) for r in range(2))

    aa, ee, bb, ff = column(0, 0), column(0, 2), column(2, 0), column(2, 2)
    cc = tuple(set_bound(mul(part, s, capacity), w) for part in ee)
    dd = tuple(set_bound(mul(part, s, capacity), w) for part in ff)
    aa, bb, cc, dd, ee, ff = lehmer_gaussian(config, aa, bb, cc, dd, ee, ff, trace)
    key = hermitian(config, aa, ee, bb, ff, p)
    _, bb, dd, ff = orient(key, bb, dd, ff)
    check = hermitian(config, aa, ee, bb, ff, p)
    assert check[0].value >= 0 and check[1].value >= 0
    for value in (aa, bb, ee, ff):
        assert size_in_base2(value) <= w
    aa, bb, ee, ff = [tuple(set_bound(part, w) for part in value) for value in (aa, bb, ee, ff)]
    basis = [[None] * 4 for _ in range(4)]
    for c, (z1, z2) in ((0, (aa, ee)), (2, (bb, ff))):
        entries = (z1[0], z1[1], z2[0], z2[1])
        rotated = (Z(z1[1].bitlen, -z1[1].value), z1[0], Z(z2[1].bitlen, -z2[1].value), z2[0])
        for r in range(4):
            basis[r][c] = set_bound(entries[r], w)
            basis[r][c + 1] = set_bound(rotated[r], w)
    return basis


def weighted_norm(z1, z2, weight):
    return z1[0] ** 2 + z1[1] ** 2 + weight * (z2[0] ** 2 + z2[1] ** 2)


def gaussian_round(numerator, denominator):
    return (2 * numerator + denominator) // (2 * denominator)


def exact_lagrange(v1, v2, weight):
    """Textbook Lagrange-Gauss over Z[i] on exact integers with Gaussian rounding."""

    def norm(v):
        return weighted_norm(*v, weight)

    def inner(v, w):
        re = v[0][0] * w[0][0] + v[0][1] * w[0][1] + weight * (v[1][0] * w[1][0] + v[1][1] * w[1][1])
        im = v[0][0] * w[0][1] - v[0][1] * w[0][0] + weight * (v[1][0] * w[1][1] - v[1][1] * w[1][0])
        return re, im

    def minus(v, w, mu):
        return tuple((x[0] - (mu[0] * y[0] - mu[1] * y[1]), x[1] - (mu[0] * y[1] + mu[1] * y[0]))
                     for x, y in zip(v, w))

    if norm(v2) < norm(v1):
        v1, v2 = v2, v1
    while True:
        h, n = inner(v1, v2), norm(v1)
        v2 = minus(v2, v1, (gaussian_round(h[0], n), gaussian_round(h[1], n)))
        if norm(v2) >= norm(v1):
            return norm(v1), norm(v2)
        v1, v2 = v2, v1


def check_reduction(config, hnf, p, basis):
    values = [[x.value for x in row] for row in hnf]
    reduced = [[x.value for x in row] for row in basis]
    inverse = rational_inverse(values)
    transform = [[sum(inverse[i][m] * reduced[m][j] for m in range(4)) for j in range(4)] for i in range(4)]
    assert all(x.denominator == 1 for row in transform for x in row)
    assert abs(rational_determinant(transform)) == 1

    def zi(matrix, c):
        return (matrix[0][c], matrix[1][c]), (matrix[2][c], matrix[3][c])

    weight = config.sqrt_p ** 2
    first, second = zi(reduced, 0), zi(reduced, 2)
    expected = exact_lagrange(zi(values, 0), zi(values, 2), weight)
    # The 64-bit windows cannot see a residual size-reduction step that changes the second norm by less
    # than the window precision (pinned C does the same), so lambda_2 is only matched to 2^-64 relative.
    first_norm, second_norm = weighted_norm(*first, weight), weighted_norm(*second, weight)
    assert first_norm == expected[0]
    assert 0 <= second_norm - expected[1] <= expected[1] >> 64
    assert reduced[0][0] >= 0 and reduced[1][0] >= 0
    assert p.value > 0


def rational_determinant(matrix):
    work = [[Fraction(x) for x in row] for row in matrix]
    size, result = len(work), Fraction(1)
    for column in range(size):
        pivot = next((r for r in range(column, size) if work[r][column]), None)
        if pivot is None:
            return Fraction(0)
        if pivot != column:
            work[column], work[pivot] = work[pivot], work[column]
            result = -result
        result *= work[column][column]
        for r in range(column + 1, size):
            factor = work[r][column] / work[column][column]
            work[r] = [a - factor * b for a, b in zip(work[r], work[column])]
    return result


def text(values):
    return " ".join(x.text() for x in values)


def word(value):
    return f"{value & MASK64:x}"


def inner_record(level, arguments, m):
    a, b, c, d, swap, from_v1, from_v2 = arguments
    windows = " ".join(word(x) for value in (a, b, c, d) for x in value)
    transform = " ".join(str(x) for row in m for value in row for x in value)
    return f"ginner {level} {windows} {int(swap)} {from_v1} {from_v2} = {transform}"


def crt(residues, moduli):
    total, modulus = 0, 1
    for residue, factor in zip(residues, moduli):
        total += (residue - total) * modulus * pow(modulus, -1, factor) % (modulus * factor)
        modulus *= factor
    return total % modulus


def left_ideal(level, label, sizes, p):
    """quat_to_lattice of the inert left O0-ideal (x, y, N) whose norm is a product of inert primes."""
    factors = [inert_ideal(f"{label}-{index}", bits, p) for index, bits in enumerate(sizes)]
    n = 1
    for _, _, factor in factors:
        n *= factor
    x = crt([fx for fx, _, _ in factors], [f for _, _, f in factors])
    s = crt([2 * fy + 1 for _, fy, _ in factors], [f for _, _, f in factors])
    s = s if s % 2 else s + n
    assert (4 * x * x + s * s + p) % (4 * n) == 0
    return n, 2 * x, s, 2 * n - s, 2 * x


def o0_ideals(level, full):
    security, prime_bits = LEVELS[level]
    exponent, cofactor = PRIMES[level]
    p = cofactor * (1 << exponent) - 1
    half = prime_bits // 2
    shapes = ((half + security // 2, half + security // 2), (security,), (half + 15,), (20,), (half - 20,),
              (security, security, security))
    for index, sizes in enumerate(shapes if full else shapes[:2]):
        yield p, left_ideal(level, f"{level}-left-{index}", sizes, p)


def records():
    for level in CAPACITIES:
        full = level == "p324_3"
        traces = []
        for index, (p_value, (n, e, c, dq, b)) in enumerate(o0_ideals(level, full)):
            norm_bound = n.bit_length() + 1
            for bound in (norm_bound + 2,) if index else (norm_bound + 2, norm_bound + 40):
                config = Gaussian(level, bound)
                two_n = 2 * n
                hnf_values = [[two_n, 0, e, dq], [0, two_n, c, b], [0, 0, 1, 0], [0, 0, 0, 1]]
                hnf = [[Z(max(abs(v).bit_length() + 1, 2), v) for v in row] for row in hnf_values]
                p = Z(p_value.bit_length() + 1, p_value)
                OVERFLOW[0] = False
                trace = []
                basis = reduce_o0_ideal(config, hnf, p, trace)
                assert not OVERFLOW[0]
                check_reduction(config, hnf, p, basis)
                traces.append(trace)
                inputs = (hnf[0][0], hnf[0][2], hnf[0][3], hnf[1][2], hnf[1][3], p)
                yield f"gauss {level} {text(inputs)} {bound} = {text(x for row in basis for x in row)}"
        yield from inner_records(level, traces, full)


def inner_records(level, traces, full):
    seen = set()
    for trace in traces:
        picks = [trace[0], trace[-1], *(step for step in trace if not step[0][4])][:INNER_SAMPLES]
        for arguments, m in picks:
            record = inner_record(level, arguments, m)
            if record not in seen:
                seen.add(record)
                yield record
    if full:
        zero = (0, 0)
        cases = (
            (zero, zero, zero, zero, True, 0, 0),
            ((1 << 61, 5), (1 << 61, 3), zero, zero, False, 4, 4),
            ((1 << 61, 0), (1, 0), zero, zero, True, 4, 4),
            ((1 << 40, 0), (1 << 61, 0), zero, zero, False, 4, 4),
        )
        for arguments in cases:
            yield inner_record(level, arguments, gaussian_inner(*arguments))


def content():
    lines = "\n".join(f'        "{record}",' for record in records())
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_gaussian_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.quaternion\n\n"
        "internal object GaussianVectors {\n"
        "    internal val records: List<String> = listOf(\n"
        f"{lines}\n"
        "    )\n"
        "}\n"
    )


def benchmark_content():
    branches = []
    for level, parameter in PARAMETER_SETS.items():
        p_value, (n, e, c, dq, b) = next(o0_ideals(level, False))
        values = "\n".join(f'                "{hex_text(v)}",' for v in (2 * n, e, dq, c, b, p_value))
        branches.append(f"        ParameterSet.{parameter} ->\n            listOf(\n{values}\n            )")
    body = "\n".join(branches)
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_gaussian_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.benchmarks\n\n"
        "import ch.trancee.sqisign.ParameterSet\n\n"
        "/** Hexadecimal `2N`, `E`, `Dq`, `C`, `B` and `p` of an honest inert left O0-ideal. */\n"
        "internal fun gaussianInputs(parameter: ParameterSet): List<String> =\n"
        "    when (parameter) {\n"
        f"{body}\n"
        "    }\n"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    expected = content()
    for path, text_content in ((OUTPUT, expected), (BENCHMARK_OUTPUT, benchmark_content())):
        if arguments.check:
            if not path.exists() or path.read_text() != text_content:
                raise SystemExit("Gaussian vectors are stale; run tools/generate_gaussian_vectors.py")
        else:
            path.write_text(text_content)
    print(f"Verified {expected.count(chr(10) + '        \"')} Gaussian reduction records")


if __name__ == "__main__":
    main()
