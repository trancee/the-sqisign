#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Independent Python model of 2x2 matrices, Gaussian integers and quaternion lattice membership.

The 2x2 integer matrices and Gaussian integers of the reference dim2.c, and quat_lattice_contains
and quat_alg_make_primitive of lattice.c and algebra.c, are stated on bounded (bitlen, value)
integers in the order the reference evaluates them, so result bit lengths follow the ibz_t
contract. Every value is also checked exactly: products, adjugates and modular inverses against
direct integer arithmetic, Gaussian gcds against an exact Euclid up to units, and lattice
coordinates against exact rational back-substitution. Pinned C checks every record.
"""

import argparse
from fractions import Fraction

from generate_algebra_vectors import ZERO, Stream, checked, content, prime, scalar_div, text
from generate_dual_vectors import PARAMETER_SETS, PRIMES, hex_text
from generate_ibz_vectors import CAPACITIES, OVERFLOW, ROOT, Z, divide, ibz_set, mod, mul, set_bound, shift_left
from generate_lattice_vectors import rounded_div
from generate_number_theory_vectors import invmod

OUTPUT = ROOT / "src/commonTest/kotlin/ch/trancee/sqisign/quaternion/Dim2LatticeVectors.kt"
BENCHMARK_OUTPUT = ROOT / "src/jvmBenchmark/kotlin/ch/trancee/sqisign/benchmarks/Dim2LatticeInputs.kt"
ONE = Z(2, 1)
CHALLENGE_BITS = {"p324_3": 128, "p500_27": 192, "p664_17": 256}
# MAXORD_O0 = <1, i, (i + j) / 2, (1 + ij) / 2> with the bit lengths of the precomputed constant.
O0 = (Z(3, 2), [[Z(3, 2), Z(1, 0), Z(1, 0), Z(2, 1)],
                [Z(1, 0), Z(3, 2), Z(2, 1), Z(1, 0)],
                [Z(1, 0), Z(1, 0), Z(2, 1), Z(1, 0)],
                [Z(1, 0), Z(1, 0), Z(1, 0), Z(2, 1)]])


def negate(a):
    assert a.value != -(1 << (a.bitlen - 1))
    return Z(a.bitlen, -a.value)


def sub(a, b, capacity):
    return checked(capacity, a, b, True)


def eval2(m, v, capacity):
    return [checked(capacity, mul(row[0], v[0], capacity), mul(row[1], v[1], capacity)) for row in m]


def mul2(a, b, capacity):
    result = [[ZERO, ZERO], [ZERO, ZERO]]
    for i in range(2):
        for j in range(2):
            for k in range(2):
                result[i][j] = checked(capacity, result[i][j], mul(a[i][k], b[k][j], capacity))
    return result


def adjugate(m):
    return [[m[1][1], negate(m[0][1])], [negate(m[1][0]), m[0][0]]]


def inverse2(m, capacity):
    det = sub(mul(m[0][0], m[1][1], capacity), mul(m[0][1], m[1][0], capacity), capacity)
    return det, adjugate(m) if det.value else None


def inverse_mod2(m, modulus, capacity):
    det = mod(mul(m[0][0], m[1][1], capacity), modulus, capacity)
    det = mod(sub(det, mul(m[0][1], m[1][0], capacity), capacity), modulus, capacity)
    exists, det = invmod(det, modulus, capacity)
    det = mul(det, ibz_set(int(exists), 2), capacity)
    return exists, [[mod(mul(x, det, capacity), modulus, capacity) for x in row] for row in adjugate(m)]


def normalize2(m, e, capacity):
    half = shift_left(ONE, e - 1, capacity)
    for row in m:
        for entry in row:
            assert entry.value >= 0
            if entry.value == 0:
                continue
            if entry.value < half.value:
                return m
            if entry.value > half.value:
                full = shift_left(half, 1, capacity)
                return [[sub(full, x, capacity) for x in r] for r in m]
    return m


def gaussian_mul(a, b, capacity):
    r = mul(checked(capacity, a[0], a[1]), b[0], capacity)
    s = mul(sub(a[0], a[1], capacity), b[1], capacity)
    t = mul(checked(capacity, b[0], b[1]), a[1], capacity)
    return [sub(r, t, capacity), checked(capacity, t, s)]


def gaussian_remainder(a, b, capacity):
    n = checked(capacity, mul(b[0], b[0], capacity), mul(b[1], b[1], capacity))
    q = gaussian_mul([b[0], negate(b[1])], a, capacity)
    q = [rounded_div(q[0], n, capacity), rounded_div(q[1], n, capacity)]
    product = gaussian_mul(q, b, capacity)
    r = [sub(a[0], product[0], capacity), sub(a[1], product[1], capacity)]
    bounded = [set_bound(r[0], b[0].bitlen), set_bound(r[1], b[1].bitlen)]
    assert [x.value for x in bounded] == [x.value for x in r], "remainder exceeds the divisor bounds"
    assert r[0].value ** 2 + r[1].value ** 2 < n.value
    return bounded


def gaussian_gcd(a, b, capacity):
    q, r = (b, a) if not (a[0].value or a[1].value) else (a, b)
    while r[0].value or r[1].value:
        q, r = r, gaussian_remainder(q, r, capacity)
    return q


def gaussian_divides(g, a):
    n = g[0] ** 2 + g[1] ** 2
    return n and (a[0] * g[0] + a[1] * g[1]) % n == 0 and (a[1] * g[0] - a[0] * g[1]) % n == 0


def contains(lattice, element, capacity):
    denominator, basis = lattice
    assert all(not basis[i][j].value for i in range(4) for j in range(i))
    assert all(basis[i][i].value for i in range(4))
    remaining = [mul(denominator, x, capacity) for x in element[1]]
    scaled = [[mul(element[0], x, capacity) for x in row] for row in basis]
    member, coordinates = True, [None] * 4
    for i in range(3, -1, -1):
        quotient, remainder = divide(remaining[i], scaled[i][i])
        member = member and not remainder.value
        coordinates[i] = quotient
        column = [mul(quotient, scaled[k][i], capacity) for k in range(4)]
        remaining = [sub(x, y, capacity) for x, y in zip(remaining, column)]
    exact = solve(lattice, element)
    assert member == all(x.denominator == 1 for x in exact)
    assert not member or [x.value for x in coordinates] == exact
    return member, coordinates


def solve(lattice, element):
    denominator, basis = lattice
    target = [Fraction(x.value * denominator.value, element[0].value) for x in element[1]]
    solution = [Fraction(0)] * 4
    for i in range(3, -1, -1):
        rest = target[i] - sum(basis[i][j].value * solution[j] for j in range(i + 1, 4))
        solution[i] = rest / basis[i][i].value
    return solution


def make_primitive(lattice, element, capacity):
    member, coordinates = contains(lattice, element, capacity)
    assert member
    g = content(coordinates)
    return g, scalar_div(g, coordinates) if g.value else coordinates


def pair(stream, bits):
    a, b = stream.ibz(bits), stream.ibz(bits)
    width = max(a.bitlen, b.bitlen)
    return [Z(width, a.value), Z(width, b.value)]


def matrix(stream, bits):
    return [pair(stream, bits), pair(stream, bits)]


def flat(m):
    return [x for row in m for x in row]


def check_integer_matrices(a, b, v, capacity):
    product = mul2(a, b, capacity)
    assert [[x.value for x in row] for row in product] == [
        [sum(a[i][k].value * b[k][j].value for k in range(2)) for j in range(2)] for i in range(2)]
    image = eval2(a, v, capacity)
    assert [x.value for x in image] == [row[0].value * v[0].value + row[1].value * v[1].value for row in a]
    return product, image


def matrix_records(level, capacity, stream, count, bits):
    for _ in range(count):
        a, b, v = matrix(stream, bits), matrix(stream, bits), pair(stream, bits)
        product, image = check_integer_matrices(a, b, v, capacity)
        yield f"m2eval {level} {text(flat(a))} {text(v)} = {text(image)}"
        yield f"m2mul {level} {text(flat(a))} {text(flat(b))} = {text(flat(product))}"
        yield inverse_record(level, a, capacity)
    singular = [[Z(9, 6), Z(9, -4)], [Z(9, 9), Z(9, -6)]]
    yield inverse_record(level, singular, capacity)


def inverse_record(level, m, capacity):
    det, inverse = inverse2(m, capacity)
    exact = m[0][0].value * m[1][1].value - m[0][1].value * m[1][0].value
    assert det.value == exact
    if inverse is None:
        return f"m2inv {level} {text(flat(m))} = 0 {det.text()}"
    product = [[sum(m[i][k].value * inverse[k][j].value for k in range(2)) for j in range(2)] for i in range(2)]
    assert product == [[exact, 0], [0, exact]]
    return f"m2inv {level} {text(flat(m))} = 1 {det.text()} {text(flat(inverse))}"


def modular_records(level, capacity, stream, count):
    exponents = (1, 8, 64, CHALLENGE_BITS[level], PRIMES[level][0])
    for index in range(count):
        exponent = exponents[index % len(exponents)]
        modulus = Z(exponent + 2 + (index % 3) * 31, 1 << exponent)
        if index % 4 == 3:
            modulus = Z(exponent + 2, (1 << exponent) + 1 + 2 * stream.take(1))
        m = matrix(stream, exponent + 1 + index % 2 * 9)
        if index % 5 == 1:
            m[1][1] = Z(m[1][1].bitlen, 0)
            m[0][0] = Z(m[0][0].bitlen, 2)
            m[0][1] = Z(m[0][1].bitlen, 2 * (m[0][1].value // 2))
        exists, inverse = inverse_mod2(m, modulus, capacity)
        det = m[0][0].value * m[1][1].value - m[0][1].value * m[1][0].value
        if exists:
            product = [[sum(m[i][k].value * inverse[k][j].value for k in range(2)) % modulus.value
                        for j in range(2)] for i in range(2)]
            assert product == [[1 % modulus.value, 0], [0, 1 % modulus.value]]
        else:
            assert det % 2 == 0 or modulus.value % 2 == 1
            assert all(not x.value for x in flat(inverse))
        yield f"m2invmod {level} {text(flat(m))} {modulus.text()} = {int(exists)} {text(flat(inverse))}"


def normalize_records(level, capacity, stream):
    for e in (1, 2, 8, CHALLENGE_BITS[level]):
        half = 1 << (e - 1)
        cases = [
            [stream.take(e) % half if half > 1 else 0, 0, 0, 0],
            [half + 1 + stream.take(e) % half if half > 1 else 1, 0, 1, 0],
            [0, half, half, 0],
            [half, half, half, half],
            [0, 0, 0, 0],
            [half, 0, half + 1 if half > 1 else 0, stream.take(e) % (2 * half)],
        ]
        for values in cases:
            values = [v % (2 * half) for v in values]
            m = [[Z(e + 1 + i, values[2 * r + i]) for i in range(2)] for r in range(2)]
            result = normalize2(m, e, capacity)
            for x, y in zip(flat(m), flat(result)):
                assert y.value in (x.value, (2 * half - x.value))
            yield f"m2norm {level} {e} {text(flat(m))} = {text(flat(result))}"


def gaussian_records(level, capacity, stream, count, bits):
    for index in range(count):
        a, b = pair(stream, bits), pair(stream, bits)
        product = gaussian_mul(a, b, capacity)
        assert product[0].value == a[0].value * b[0].value - a[1].value * b[1].value
        assert product[1].value == a[0].value * b[1].value + a[1].value * b[0].value
        yield f"gmul {level} {text(a)} {text(b)} = {text(product)}"
        if index % 2:
            g = pair(stream, bits // 3 + 1)
            a, b = gaussian_mul(a, g, capacity), gaussian_mul(b, g, capacity)
            width = max(a[0].bitlen, b[0].bitlen)
            a, b = [Z(width, x.value) for x in a], [Z(width, x.value) for x in b]
        yield gcd_record(level, a, b, capacity)


def gcd_record(level, a, b, capacity):
    g = gaussian_gcd(a, b, capacity)
    values = [x.value for x in g]
    assert gaussian_divides(values, [x.value for x in a]) or not any(x.value for x in a)
    assert gaussian_divides(values, [x.value for x in b]) or not any(x.value for x in b)
    assert values[0] ** 2 + values[1] ** 2 == small_gcd_norm(a, b)
    return f"ggcd {level} {text(a)} {text(b)} = {text(g)}"


def small_gcd_norm(a, b):
    if not any(x.value for x in a + b):
        return 0
    return exact_gaussian_norm([x.value for x in a], [x.value for x in b])


def exact_gaussian_norm(a, b):
    def remainder(x, y):
        n = y[0] * y[0] + y[1] * y[1]
        re, im = x[0] * y[0] + x[1] * y[1], x[1] * y[0] - x[0] * y[1]
        q = (nearest(re, n), nearest(im, n))
        return [x[0] - (q[0] * y[0] - q[1] * y[1]), x[1] - (q[0] * y[1] + q[1] * y[0])]

    x, y = a, b
    while y[0] or y[1]:
        x, y = y, remainder(x, y)
    return x[0] * x[0] + x[1] * x[1]


def nearest(numerator, denominator):
    return (2 * numerator + denominator) // (2 * denominator)


def lattice_elements(stream, lattice, bits, capacity, count):
    denominator, basis = lattice
    for index in range(count):
        x = [stream.ibz(bits) for _ in range(4)]
        values = [sum(basis[i][j].value * x[j].value for j in range(4)) for i in range(4)]
        coordinates = [Z(abs(v).bit_length() + 2 + index % 3 * 9, v) for v in values]
        scale = 1 + stream.take(2) % 1000 if index % 3 == 1 else 1
        element = (Z(denominator.bitlen + 11, denominator.value * scale),
                   [Z(c.bitlen + 11, c.value * scale) for c in coordinates])
        yield element
        if index % 2:
            yield element[0], [element[1][0], element[1][1], element[1][2],
                               checked(capacity, element[1][3], ONE)]


def random_lattice(stream, bits):
    denominator = stream.nonzero(bits)
    basis = [[stream.nonzero(bits) if i == j else (stream.ibz(bits) if j > i else ZERO) for j in range(4)]
             for i in range(4)]
    return denominator, basis


def lattice_records(level, capacity, stream, bits, full):
    lattices = [O0, random_lattice(stream, min(bits, 64)), random_lattice(stream, 8)]
    for lattice in lattices:
        prefix = f"{lattice[0].text()} {text(flat(lattice[1]))}"
        elements = list(lattice_elements(stream, lattice, bits, capacity, 4 if full else 2))
        elements.append((Z(4, 3), [Z(1, 0)] * 4))
        for element in elements:
            member, coordinates = contains(lattice, element, capacity)
            yield f"lcontains {level} {prefix} {element[0].text()} {text(element[1])} = " \
                  f"{int(member)} {text(coordinates)}"
            if member:
                g, primitive = make_primitive(lattice, element, capacity)
                yield f"primitive {level} {prefix} {element[0].text()} {text(element[1])} = " \
                      f"{g.text()} {text(primitive)}"


def records():
    for level, capacity in CAPACITIES.items():
        full = level == "p324_3"
        stream = Stream(f"dim2-lattice-{level}")
        bits = prime(level).bitlen
        for size in (8, 64, bits):
            yield from matrix_records(level, capacity, stream, 3 if full else 1, size)
            yield from gaussian_records(level, capacity, stream, 4 if full else 2, size)
        yield from modular_records(level, capacity, stream, 10 if full else 5)
        yield from normalize_records(level, capacity, stream)
        yield from lattice_records(level, capacity, stream, bits, full)
    assert not OVERFLOW[0]


def content_text():
    lines = "\n".join(f'        "{record}",' for record in records())
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_dim2_lattice_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.quaternion\n\n"
        "internal object Dim2LatticeVectors {\n"
        "    internal val records: List<String> = listOf(\n"
        f"{lines}\n"
        "    )\n"
        "}\n"
    )


def benchmark_values(level):
    """Two Gaussian integers with a common factor, an O0 element and a 2x2 matrix modulo 2^e."""
    stream = Stream(f"dim2-lattice-benchmark-{level}")
    bits = prime(level).value.bit_length()
    common = [stream.signed(bits // 3), stream.signed(bits // 3)]
    a = [stream.signed(bits), stream.signed(bits)]
    b = [stream.signed(bits), stream.signed(bits)]
    a = [a[0] * common[0] - a[1] * common[1], a[0] * common[1] + a[1] * common[0]]
    b = [b[0] * common[0] - b[1] * common[1], b[0] * common[1] + b[1] * common[0]]
    x = [stream.signed(bits) * 7 for _ in range(4)]
    element = [sum(O0[1][i][j].value * x[j] for j in range(4)) for i in range(4)]
    exponent = PRIMES[level][0]
    matrix_entries = [stream.take(exponent // 8) for _ in range(4)]
    matrix_entries[0] |= 1
    matrix_entries[1] &= ~1
    matrix_entries[3] |= 1
    return [*a, *b, *element, *matrix_entries, exponent]


def benchmark_content():
    branches = []
    for level, parameter in PARAMETER_SETS.items():
        values = "\n".join(f'                "{hex_text(v)}",' for v in benchmark_values(level))
        branches.append(f"        ParameterSet.{parameter} ->\n            listOf(\n{values}\n            )")
    body = "\n".join(branches)
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_dim2_lattice_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.benchmarks\n\n"
        "import ch.trancee.sqisign.ParameterSet\n\n"
        "/**\n"
        " * Hexadecimal Gaussian integers `a` and `b` with a common factor, the numerator of an\n"
        " * element of `O0` over 2, four 2x2 matrix entries and the exponent of a `2^e` modulus.\n"
        " */\n"
        "internal fun dim2LatticeInputs(parameter: ParameterSet): List<String> =\n"
        "    when (parameter) {\n"
        f"{body}\n"
        "    }\n"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    expected = content_text()
    for path, text_content in ((OUTPUT, expected), (BENCHMARK_OUTPUT, benchmark_content())):
        if arguments.check:
            if not path.exists() or path.read_text() != text_content:
                raise SystemExit("Dim2 lattice vectors are stale; run tools/generate_dim2_lattice_vectors.py")
        else:
            path.write_text(text_content)
    print(f"Verified {expected.count(chr(10) + '        \"')} 2x2 matrix, Gaussian integer and lattice records")


if __name__ == "__main__":
    main()
