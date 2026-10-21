#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Independent Python model of the dimension-4 dual lattice reduction of quaternion ideals.

The closed-form dual LDL initialisation, the odd-even block tours over the dimension-2 Gram kernel,
the Minkowski candidate selection and the ideal-level basis materialisation run on bounded (bitlen,
value) integers. Inputs are honest products of two inert O0-ideals. Every result is checked to be a
unimodular change of basis whose fixed-point Gram tracks the exact dual Gram and whose dual norms
are reduced. Pinned C src/quaternion/ref/lvlx/lll/lll_dim4.c checks every record.
"""

import argparse
from fractions import Fraction

from generate_fixed_point_vectors import NEWTON_ROUNDS, ct_shift, fp_recip, highmul_p, highmul_s, round_shift_limb
from generate_gram_vectors import LEVELS, Config, gram_lehmer, limb_align, materialise, outer_its_for_round
from generate_ibz_vectors import CAPACITIES, OVERFLOW, ROOT, Z, add, mul, mul_by_int, sample, set_bound, shift_left
from generate_number_theory_vectors import is_prime, xgcd

OUTPUT = ROOT / "src/commonTest/kotlin/ch/trancee/sqisign/quaternion/DualVectors.kt"
BENCHMARK_OUTPUT = ROOT / "src/jvmBenchmark/kotlin/ch/trancee/sqisign/benchmarks/DualInputs.kt"
PARAMETER_SETS = {"p324_3": "NIST_I", "p500_27": "NIST_III", "p664_17": "NIST_V"}
# (torsion exponent, cofactor) of each level's prime p = cofactor * 2^exponent - 1.
PRIMES = {"p324_3": (324, 3), "p500_27": (500, 27), "p664_17": (664, 17)}
SIGMA = (2, 3, 0, 1)
T_SUB = ((0, 0, 1), (0, 1, -1), (0, 1, 0), (0, 1, 1), (1, -1, -1), (1, -1, 0), (1, -1, 1), (1, 0, -1), (1, 0, 0),
         (1, 0, 1), (1, 1, -1), (1, 1, 0), (1, 1, 1))
CANDIDATES = 27
SLOTS = 32
# Minkowski-reduced dimension-4 bases satisfy prod |b_i|^2 <= gamma_4^4 det = 4 det.
HADAMARD_BOUND = 4
LEVEL_SECURITY = {level: security for level, (security, _) in LEVELS.items()}


class DualConfig(Config):
    """The dimension-4 lll_config.h constants of one level on top of the Gram kernel's."""

    def __init__(self, level):
        super().__init__(level)
        security, prime = LEVELS[level]
        norm = prime + security
        gap = 2 * norm - prime
        self.level = level
        self.capacity = CAPACITIES[level]
        self.d_shift = prime - security - security // 2
        self.head = self.u_bits
        self.tours = gap.bit_length() - 1 + 4
        self.hnf_bits = norm + 2
        self.ainv_bits = self.u_bits + 32
        self.basis_bits = limb_align(self.hnf_bits + self.ainv_bits + 2)
        self.norm_bits = self.e_bits + 40
        self.t0_bits = self.l_bits - self.p_bits + 16


def zero(bits):
    return set_bound(Z(2, 0), bits)


def bounded_sum(x, y, bits, capacity, negate=False):
    return set_bound(add(x, y, capacity, checked=False, negate=negate), bits)


def bounded_product(x, y, bits, capacity):
    return set_bound(mul(x, y, capacity), bits)


def negated(x):
    return Z(x.bitlen, -x.value)


class Ldl:
    """fp_ldl_t: diagonal D, unit lower-triangular L at scale 2^P, accumulated inverse transform."""

    def __init__(self, d, lower, ainv):
        self.d, self.lower, self.ainv = d, lower, ainv

    def fields(self):
        return [*self.d, *(self.lower[i][j] for i in range(4) for j in range(i)),
                *(x for row in self.ainv for x in row)]


def init_dual(config, two_n, b, c, dq, e, p):
    capacity, precision, lbits = config.capacity, config.p_bits, config.l_bits
    lower = [[zero(lbits) for _ in range(4)] for _ in range(4)]
    for i in range(4):
        lower[i][i] = set_bound(shift_left(Z(2, 1), precision, capacity), lbits)
    square = ct_shift(mul(two_n, two_n, capacity), -config.d_shift, config.d_shift, config.e_bits, capacity)
    prime = ct_shift(p, -config.d_shift, config.d_shift, config.e_bits, capacity)
    reciprocal, scale = fp_recip(two_n, precision, NEWTON_ROUNDS, capacity)

    def neg_ratio(x):
        ratio = highmul_s(x, reciprocal, -scale, two_n.bitlen, config.e_bits, capacity)
        return set_bound(negated(ratio), config.e_bits)

    lower[2][0], lower[2][1], lower[3][0], lower[3][1] = neg_ratio(e), neg_ratio(dq), neg_ratio(c), neg_ratio(b)
    ainv = [[set_bound(Z(2, int(i == j)), config.u_bits) for j in range(4)] for i in range(4)]
    state = Ldl([square, square, prime, prime], lower, ainv)
    size_reduce_from(config, state, 0)
    return state


def size_reduce_from(config, state, k):
    capacity, precision, lbits, lower, ainv = config.capacity, config.p_bits, config.l_bits, state.lower, state.ainv
    for r in range(k, 4):
        for j in range(r - 1, -1, -1):
            q = round_shift_limb(lower[r][j], precision, config.head, capacity)
            for m in range(j):
                assert abs(lower[j][m].value).bit_length() <= precision
                lower[j][m] = set_bound(lower[j][m], config.p_bits)
                product = bounded_product(lower[j][m], q, lbits, capacity)
                lower[r][m] = bounded_sum(lower[r][m], product, lbits, capacity, negate=True)
            product = set_bound(shift_left(q, precision, capacity), lbits)
            lower[r][j] = bounded_sum(lower[r][j], product, lbits, capacity, negate=True)
            for i in range(4):
                product = bounded_product(ainv[i][r], q, config.u_bits + config.head, capacity)
                ainv[i][j] = bounded_sum(ainv[i][j], product, config.u_bits, capacity)


def block_gram(config, state, k, u, mu_old):
    """STEP 1/2: the block's local Gram in orthogonalised space, then the new D and mu."""
    capacity, precision, ebits, lbits, d = config.capacity, config.p_bits, config.e_bits, config.l_bits, state.d
    u00, u01, u10, u11 = u
    eta0 = add(shift_left(u00, precision, capacity), mul(mu_old, u10, capacity), capacity, checked=False)
    assert abs(eta0.value).bit_length() <= precision + 1
    eta0 = set_bound(eta0, precision + 2)
    first = set_bound(shift_left(u01, precision, capacity), lbits)
    eta1 = bounded_sum(first, mul(mu_old, u11, capacity), lbits, capacity)
    assert abs(eta1.value).bit_length() <= precision + 1
    eta0_sq = highmul_p(eta0, eta0, precision, precision + 2, capacity)
    assert abs(eta0_sq.value).bit_length() <= precision + 1

    def tail(left, right):
        return bounded_product(bounded_product(d[k + 1], left, ebits, capacity), right, ebits, capacity)

    g00 = bounded_sum(highmul_p(eta0_sq, d[k], precision, ebits, capacity), tail(u10, u10), ebits, capacity)
    cross = highmul_p(eta0, eta1, precision, ebits, capacity)
    g01 = bounded_sum(highmul_p(cross, d[k], precision, ebits, capacity), tail(u10, u11), ebits, capacity)
    inverse, scale = fp_recip(g00, precision, NEWTON_ROUNDS, capacity)
    new_mu = highmul_s(g01, inverse, -scale, lbits, lbits, capacity)
    ratio = highmul_s(d[k + 1], inverse, -scale, ebits, ebits, capacity)
    d[k + 1] = highmul_p(ratio, d[k], precision, ebits, capacity)
    d[k] = set_bound(g00, ebits)
    assert d[k].value > 0 and d[k + 1].value > 0
    state.lower[k + 1][k] = set_bound(new_mu, lbits)
    return new_mu


def left_tail(config, state, k, u):
    """STEP 3: the columns j < k of rows k and k+1."""
    capacity, lbits, lower = config.capacity, config.l_bits, state.lower
    u00, u01, u10, u11 = u
    for j in range(k):
        old_k, old_k1 = lower[k][j], lower[k + 1][j]
        lower[k][j] = bounded_sum(bounded_product(old_k, u00, lbits, capacity),
                                  bounded_product(old_k1, u10, lbits, capacity), lbits, capacity)
        lower[k + 1][j] = bounded_sum(bounded_product(old_k, u01, lbits, capacity),
                                      bounded_product(old_k1, u11, lbits, capacity), lbits, capacity)


def right_tail(config, state, k, u, mu_old, new_mu, negate):
    """STEP 4: the rows i > k+1 of columns k and k+1."""
    capacity, precision, lbits, lower = config.capacity, config.p_bits, config.l_bits, state.lower
    u00, u01, u10, u11 = u
    for i in range(k + 2, 4):
        old_k, old_k1 = lower[i][k], lower[i][k + 1]
        projected = highmul_p(mu_old, old_k1, precision, lbits, capacity)
        tail = mul(projected, u10, capacity)
        assert abs(tail.value).bit_length() < lbits
        eta0 = bounded_sum(bounded_product(old_k1, u00, lbits, capacity), set_bound(tail, lbits), lbits, capacity)
        eta1 = bounded_sum(bounded_product(old_k1, u01, lbits, capacity),
                           bounded_product(projected, u11, lbits, capacity), lbits, capacity)
        next_k1 = bounded_sum(eta0, bounded_product(old_k, u10, lbits, capacity), lbits, capacity, negate=True)
        next_k1 = negated(next_k1) if negate else next_k1
        term = bounded_sum(bounded_product(old_k, u11, lbits, capacity), eta1, lbits, capacity, negate=True)
        term = negated(term) if negate else term
        lower[i][k] = bounded_sum(term, highmul_p(new_mu, next_k1, precision, lbits, capacity), lbits, capacity)
        lower[i][k + 1] = set_bound(next_k1, lbits)


def inverse_columns(config, state, k, u, negate):
    capacity, ubits = config.capacity, config.u_bits
    u00, u01, u10, u11 = u
    for row in state.ainv:
        x, y = row[k], row[k + 1]
        first = bounded_sum(set_bound(mul(u11, x, capacity), 2 * ubits), mul(u01, y, capacity), ubits, capacity,
                            negate=True)
        second = bounded_sum(mul(u00, y, capacity), mul(u10, x, capacity), ubits, capacity, negate=True)
        row[k] = negated(first) if negate else first
        row[k + 1] = negated(second) if negate else second


def update_block(config, state, k, transform):
    capacity, ubits = config.capacity, config.u_bits
    mu_old = set_bound(state.lower[k + 1][k], config.l_bits)
    u = tuple(set_bound(x, ubits) for x in transform)
    det = bounded_sum(bounded_product(u[0], u[3], 2 * ubits, capacity),
                      bounded_product(u[1], u[2], 2 * ubits, capacity), 32, capacity, negate=True)
    assert det.value in (1, -1)
    new_mu = block_gram(config, state, k, u, mu_old)
    left_tail(config, state, k, u)
    right_tail(config, state, k, u, mu_old, new_mu, det.value < 0)
    inverse_columns(config, state, k, u, det.value < 0)


def reduce_block(config, state, k, round_index):
    a, b, c, _ = materialise(config, state.d[k], state.d[k + 1], state.lower[k + 1][k], config.capacity)
    *_, transform = gram_lehmer(config, a, b, c, outer_its_for_round(config, round_index), config.capacity)
    update_block(config, state, k, transform)
    size_reduce_from(config, state, k)


def tours(config, state):
    for t in range(config.tours):
        for offset, k in enumerate((1, 0, 2)):
            reduce_block(config, state, k, 3 * t + offset)


def build_gram(config, state):
    capacity, precision, bits = config.capacity, config.p_bits, config.norm_bits
    d, lower = state.d, state.lower
    scaled = [[None] * 4 for _ in range(4)]
    for i in range(4):
        for k in range(i):
            scaled[k][i] = highmul_p(d[k], lower[i][k], precision, bits, capacity)
        scaled[i][i] = set_bound(d[i], bits)
    gram = [[None] * 4 for _ in range(4)]
    for i in range(4):
        for j in range(i + 1):
            total = set_bound(scaled[j][i], bits)
            for k in range(j):
                total = bounded_sum(total, highmul_p(scaled[k][i], lower[j][k], precision, bits, capacity), bits,
                                    capacity)
            gram[i][j] = total
    return gram


def form(config, gram, t, u):
    total = zero(config.norm_bits)
    for i in range(4):
        for j in range(i + 1):
            coefficient = t[i] * u[i] if i == j else t[i] * u[j] + t[j] * u[i]
            term = mul_by_int(gram[i][j], coefficient, config.norm_bits)
            total = bounded_sum(total, term, config.norm_bits, config.capacity)
    return total


def t0_floor(config, state, t123):
    capacity, work = config.capacity, config.l_bits
    shift = zero(work)
    for j in range(1, 4):
        shift = bounded_sum(shift, mul_by_int(state.lower[j][0], t123[j - 1], work), work, capacity)
    floor = ct_shift(set_bound(negated(shift), work), -config.p_bits, work, config.t0_bits, capacity)
    t0 = (floor.value & 0xFFFFFFFF) - ((floor.value & 0x80000000) << 1)
    assert -128 <= t0 < 127
    return t0


def candidates(config, state, gram):
    vectors = [(1, 0, 0, 0)]
    for t123 in T_SUB:
        t0 = t0_floor(config, state, t123)
        vectors += [(t0, *t123), (t0 + 1, *t123)]
    keys = [form(config, gram, t, t) for t in vectors]
    key_bits = config.norm_bits
    assert all(abs(key.value).bit_length() < key_bits - 4 for key in keys)
    sentinel = set_bound(shift_left(set_bound(Z(2, 1), key_bits), key_bits - 4, config.capacity), key_bits)
    return keys + [sentinel] * (SLOTS - CANDIDATES), vectors + [(0, 0, 0, 0)] * (SLOTS - CANDIDATES)


def bitonic_sort(keys, vectors):
    keys, vectors = list(keys), list(vectors)
    k = 2
    while k <= SLOTS:
        j = k >> 1
        while j > 0:
            for i in range(SLOTS):
                partner = i ^ j
                if partner > i:
                    less = keys[partner].value < keys[i].value
                    if less == ((i & k) == 0):
                        keys[i], keys[partner] = keys[partner], keys[i]
                        vectors[i], vectors[partner] = vectors[partner], vectors[i]
            j >>= 1
        k <<= 1
    return vectors


def rank(rows):
    matrix = [[Fraction(x) for x in row] for row in rows]
    found = 0
    for column in range(4):
        pivot = next((r for r in range(found, len(matrix)) if matrix[r][column]), None)
        if pivot is None:
            continue
        matrix[found], matrix[pivot] = matrix[pivot], matrix[found]
        for r in range(len(matrix)):
            if r != found and matrix[r][column]:
                factor = matrix[r][column] / matrix[found][column]
                matrix[r] = [a - factor * b for a, b in zip(matrix[r], matrix[found])]
        found += 1
    return found


def select_basis(vectors):
    chosen = []
    for vector in vectors:
        if len(chosen) < 4 and rank(chosen + [vector]) == len(chosen) + 1:
            chosen.append(vector)
    assert len(chosen) == 4
    return chosen


def determinant(matrix):
    if len(matrix) == 1:
        return matrix[0][0]
    return sum((-1) ** c * matrix[0][c] * determinant([row[:c] + row[c + 1:] for row in matrix[1:]])
               for c in range(len(matrix)))


def inverse_transpose(t):
    """(T^-1)^T = cofactor(T) / det(T) for unimodular T."""
    det = determinant(t)
    assert det in (1, -1)
    return [[(-1) ** (i + j) * determinant([row[:j] + row[j + 1:] for r, row in enumerate(t) if r != i]) * det
             for j in range(4)] for i in range(4)]


def minkowski(config, state):
    gram = build_gram(config, state)
    keys, vectors = candidates(config, state, gram)
    chosen = [list(t) for t in select_basis(bitonic_sort(keys, vectors))]
    for i in range(1, 4):
        if form(config, gram, chosen[0], chosen[i]).value < 0:
            chosen[i] = [-x for x in chosen[i]]
    v = inverse_transpose(chosen)
    bits = config.ainv_bits
    total = [[None] * 4 for _ in range(4)]
    for i in range(4):
        for j in range(4):
            acc = zero(bits)
            for c in range(4):
                acc = bounded_sum(acc, mul_by_int(state.ainv[i][c], v[j][c], bits), bits, config.capacity)
            total[i][j] = acc
    return chosen, total, gram


def dual_reduce(config, two_n, e, dq, c, b, p):
    """quat_lll_dual_reduce_ideal on the HNF entries: (reduced basis, Gram diagonal, transform)."""
    capacity = config.capacity
    hnf = [[two_n, zero(2), e, dq], [zero(2), two_n, c, b], [zero(2), zero(2), Z(2, 1), zero(2)],
           [zero(2), zero(2), zero(2), Z(2, 1)]]
    state = init_dual(config, two_n, b, c, dq, e, p)
    tours(config, state)
    chosen, total, gram = minkowski(config, state)
    out = [[None] * 4 for _ in range(4)]
    for i in range(4):
        for c_index in range(4):
            out[i][3 - c_index] = set_bound(total[SIGMA[i]][c_index], config.ainv_bits)
    basis = [[None] * 4 for _ in range(4)]
    for r in range(4):
        for c_index in range(4):
            acc = zero(config.basis_bits)
            for m in range(4):
                term = mul(set_bound(hnf[r][m], config.hnf_bits), out[m][c_index], capacity)
                acc = bounded_sum(acc, term, config.basis_bits, capacity)
            basis[r][c_index] = acc
    diagonal = [None] * 4
    for i in range(4):
        norm = set_bound(form(config, gram, chosen[i], chosen[i]), config.norm_bits)
        diagonal[3 - i] = shift_left(norm, config.d_shift, capacity)
    return state, basis, diagonal, out, hnf


def check_reduction(config, hnf, p, basis, diagonal, out):
    """Unimodular transform, exact basis product, faithful dual Gram diagonal and reduced dual norms."""
    transform = [[x.value for x in row] for row in out]
    assert determinant(transform) in (1, -1)
    values = [[x.value for x in row] for row in basis]
    hnf_values = [[x.value for x in row] for row in hnf]
    for r in range(4):
        for c in range(4):
            assert values[r][c] == sum(hnf_values[r][m] * transform[m][c] for m in range(4))
    # Primal Gram of the reduced basis columns / 2 under nrd = diag(1, 1, p, p); its scaled inverse is
    # the dual Gram whose diagonal the reduction returns.
    weights = (1, 1, p.value, p.value)
    gram = [[Fraction(sum(values[m][i] * weights[m] * values[m][j] for m in range(4)), 4) for j in range(4)]
            for i in range(4)]
    norm = hnf_values[0][0] // 2
    dual = [[x * norm * norm * p.value for x in row] for row in rational_inverse(gram)]
    product = Fraction(1)
    for i in range(4):
        exact = dual[i][i]
        assert abs(exact - diagonal[i].value) <= exact / (1 << LEVEL_SECURITY[config.level])
        product *= exact
    assert all(diagonal[i + 1].value <= diagonal[i].value for i in range(3))
    assert product <= HADAMARD_BOUND * determinant(dual)


def rational_inverse(matrix):
    size = len(matrix)
    work = [[Fraction(x) for x in row] + [Fraction(int(i == j)) for j in range(size)] for i, row in enumerate(matrix)]
    for column in range(size):
        pivot = next(r for r in range(column, size) if work[r][column])
        work[column], work[pivot] = work[pivot], work[column]
        lead = work[column][column]
        work[column] = [x / lead for x in work[column]]
        for r in range(size):
            if r != column and work[r][column]:
                factor = work[r][column]
                work[r] = [a - factor * b for a, b in zip(work[r], work[column])]
    return [row[size:] for row in work]


def prime_3mod4(label, bits):
    candidate = sample(label, bits - 1) | (1 << (bits - 1)) | 3
    while not is_prime(candidate):
        candidate += 4
    return candidate


def inert_ideal(label, bits, p):
    """(x, y, N) with N | nrd((2x + (2y+1)i + j) / 2), N a prime = 3 mod 4."""
    n = prime_3mod4(label, bits)
    attempt = 0
    while True:
        x = sample(f"{label}-x-{attempt}", bits + 8) % n
        target = (-p - 4 * x * x) % n
        root = pow(target, (n + 1) // 4, n)
        attempt += 1
        if root * root % n == target:
            s = root if root % 2 else n - root  # odd representative of 2y+1 modulo N
            y = (s - 1) // 2 % n
            assert (4 * x * x + (2 * y + 1) ** 2 + p) % (4 * n) == 0
            return x, y, n


def mul_o0(first, second):
    """quat_ideal_mul_O0: the HNF entries (2N, E, Dq, C, B) of conjugate(a) b."""
    x1, y1, n1 = first
    x2, y2, n2 = second
    d, u, v = xgcd(Z(n1.bit_length() + 1, n1), Z(n2.bit_length() + 1, n2))
    u, v = u.value, v.value
    assert d.value == 1 and u * n1 + v * n2 == 1
    big_x1 = (-v * 2 * x1) % (2 * n1) * n2
    big_x2 = (u * 2 * x2) % (2 * n2) * n1
    big_y1 = (-v * (2 * y1 + 1)) % (2 * n1) * n2
    big_y2 = (u * (2 * y2 + 1)) % (2 * n2) * n1
    return 2 * n1 * n2, big_x2 + big_x1, -(big_y2 + big_y1), big_y2 - big_y1, big_x2 - big_x1


def ideal_inputs(level, full):
    security, prime_bits = LEVELS[level]
    exponent, cofactor = PRIMES[level]
    p = cofactor * (1 << exponent) - 1
    half = prime_bits // 2
    shapes = ((half + 15, half + 15), (security, half + 15), (security, security), (half, 20),
              (half + security // 2 - 3, half + security // 2 - 3), (half + 15, half + 14))
    for index, (first, second) in enumerate(shapes if full else shapes[:2]):
        label = f"{level}-ideal-{index}"
        yield p, mul_o0(inert_ideal(f"{label}-a", first, p), inert_ideal(f"{label}-b", second, p))


def text(values):
    return " ".join(x.text() for x in values)


def records():
    for level, capacity in CAPACITIES.items():
        full = level == "p324_3"
        config = DualConfig(level)
        for p_value, (two_n, e, dq, c, b) in ideal_inputs(level, full):
            inputs = [Z(config.hnf_bits, v) for v in (two_n, e, dq, c, b, p_value)]
            z_two_n, z_e, z_dq, z_c, z_b, p = inputs
            OVERFLOW[0] = False
            initial = init_dual(config, z_two_n, z_b, z_c, z_dq, z_e, p)
            yield f"ldl {level} {text(inputs)} = {text(initial.fields())}"
            tours(config, initial)
            yield f"tour {level} {text(inputs)} = {text(initial.fields())}"
            state, basis, diagonal, out, hnf = dual_reduce(config, z_two_n, z_e, z_dq, z_c, z_b, p)
            assert not OVERFLOW[0]
            check_reduction(config, hnf, p, basis, diagonal, out)
            fields = [x for row in basis for x in row] + diagonal + [x for row in out for x in row]
            yield f"dual {level} {text(inputs)} = {text(fields)}"


def content():
    lines = "\n".join(f'        "{record}",' for record in records())
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_dual_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.quaternion\n\n"
        "internal object DualVectors {\n"
        "    internal val records: List<String> = listOf(\n"
        f"{lines}\n"
        "    )\n"
        "}\n"
    )


def hex_text(value):
    return f"-{-value:x}" if value < 0 else f"{value:x}"


def benchmark_content():
    branches = []
    for level, parameter in PARAMETER_SETS.items():
        p_value, quadruple = next(ideal_inputs(level, False))
        values = "\n".join(f'                "{hex_text(v)}",' for v in (*quadruple, p_value))
        branches.append(f"        ParameterSet.{parameter} ->\n            listOf(\n{values}\n            )")
    body = "\n".join(branches)
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_dual_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.benchmarks\n\n"
        "import ch.trancee.sqisign.ParameterSet\n\n"
        "/** Hexadecimal `2N`, `E`, `Dq`, `C`, `B` and `p` of an honest product of two inert O0-ideals. */\n"
        "internal fun dualInputs(parameter: ParameterSet): List<String> =\n"
        "    when (parameter) {\n"
        f"{body}\n"
        "    }\n"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    expected = content()
    outputs = ((OUTPUT, expected), (BENCHMARK_OUTPUT, benchmark_content()))
    for path, text_content in outputs:
        if arguments.check:
            if not path.exists() or path.read_text() != text_content:
                raise SystemExit("Dual vectors are stale; run tools/generate_dual_vectors.py")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text_content)
    print(f"Verified {expected.count(chr(10) + '        \"')} dual reduction records")


if __name__ == "__main__":
    main()
