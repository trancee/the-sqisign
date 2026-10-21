#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Independent Python model of quaternion algebra elements, 4-vectors and 4x4 integer matrices.

Elements of B_{p,inf} = <1, i, j, ij> with i^2 = -1 and j^2 = -p are a denominator and four
integer coordinates. Every operation is stated on bounded (bitlen, value) integers in the order
the reference evaluates it, so result bit lengths follow the ibz_t contract; each value is also
checked against exact rational quaternion arithmetic. Pinned C src/quaternion/ref/lvlx/algebra.c
and src/quaternion/ref/lvlx/dim4.c check every record.
"""

import argparse
from fractions import Fraction

from generate_dual_vectors import PARAMETER_SETS, PRIMES, hex_text
from generate_ibz_vectors import CAPACITIES, OVERFLOW, ROOT, Z, add, divide, ibz_set, mul, random_bytes
from generate_number_theory_vectors import gcd

OUTPUT = ROOT / "src/commonTest/kotlin/ch/trancee/sqisign/quaternion/AlgebraVectors.kt"
BENCHMARK_OUTPUT = ROOT / "src/jvmBenchmark/kotlin/ch/trancee/sqisign/benchmarks/AlgebraInputs.kt"
ZERO = ibz_set(0, 0)


class Stream:
    """Deterministic SHAKE-256 integers for one labelled family of records."""

    def __init__(self, label):
        self.data = random_bytes(label, 1 << 16)
        self.offset = 0

    def take(self, count):
        chunk = self.data[self.offset:self.offset + count]
        self.offset += count
        assert len(chunk) == count
        return int.from_bytes(chunk, "little")

    def signed(self, bits):
        magnitude = self.take((bits + 7) // 8) % (1 << bits) if bits else 0
        return -magnitude if self.take(1) & 1 else magnitude

    def ibz(self, bits):
        """A signed value below 2^bits whose bit length carries up to 70 bits of slack."""
        value = self.signed(bits)
        slack = (0, 0, 1, 3, 64, 70)[self.take(1) % 6]
        return Z(abs(value).bit_length() + 1 + slack, value)

    def nonzero(self, bits):
        value = self.ibz(bits)
        return value if value.value else Z(value.bitlen, 1)


def prime(level):
    exponent, cofactor = PRIMES[level]
    value = cofactor * (1 << exponent) - 1
    return Z(value.bit_length() + 1, value)


def checked(capacity, a, b, negate=False):
    return add(a, b, capacity, negate=negate)


def coord_mul(a, b, p, capacity):
    def times(x, y):
        return mul(x, y, capacity)

    def step(total, x, y, negate=False):
        return checked(capacity, total, times(x, y), negate)

    real = step(step(ZERO, a[2], b[2], True), a[3], b[3], True)
    real = step(step(times(real, p), a[0], b[0]), a[1], b[1], True)
    first = step(step(ZERO, a[2], b[3]), a[3], b[2], True)
    first = step(step(times(first, p), a[0], b[1]), a[1], b[0])
    second = step(step(step(step(ZERO, a[0], b[2]), a[2], b[0]), a[1], b[3], True), a[3], b[1])
    third = step(step(step(step(ZERO, a[0], b[3]), a[3], b[0]), a[2], b[1], True), a[1], b[2])
    return [real, first, second, third]


def element_mul(a, b, p, capacity):
    return mul(a[0], b[0], capacity), coord_mul(a[1], b[1], p, capacity)


def equal_denominators(a, b, capacity):
    g = gcd(a[0], b[0])
    ra, rb = divide(a[0], g)[0], divide(b[0], g)[0]
    coords_a = [mul(x, rb, capacity) for x in a[1]]
    coords_b = [mul(x, ra, capacity) for x in b[1]]
    common = mul(ra, rb, capacity)
    return (mul(common, g, capacity), coords_a), (mul(common, g, capacity), coords_b)


def element_add(a, b, capacity):
    (denominator, coords_a), (_, coords_b) = equal_denominators(a, b, capacity)
    return denominator, [checked(capacity, x, y) for x, y in zip(coords_a, coords_b)]


def norm(a, p, capacity):
    d, c = a
    total = checked(capacity, mul(c[3], c[3], capacity), mul(c[2], c[2], capacity))
    total = checked(capacity, mul(total, p, capacity), mul(c[0], c[0], capacity))
    total = checked(capacity, total, mul(c[1], c[1], capacity))
    square = mul(d, d, capacity)
    g = gcd(total, square)
    numerator, denominator = divide(total, g)[0], divide(square, g)[0]
    return Z(numerator.bitlen, abs(numerator.value)), Z(denominator.bitlen, abs(denominator.value))


def content(v):
    return gcd(v[3], gcd(v[2], gcd(v[0], v[1])))


def scalar_div(scalar, v):
    return [divide(x, scalar)[0] for x in v]


def normalize(a, capacity):
    d, c = a
    g = gcd(content(c), d)
    d = divide(d, g)[0]
    c = scalar_div(g, c)
    sign = ibz_set(1 if d.value > 0 else -1, 2)
    return mul(sign, d, capacity), [mul(x, sign, capacity) for x in c]


def conjugate(a):
    d, c = a
    return d, [c[0]] + [Z(x.bitlen, -x.value) for x in c[1:]]


def element_scale(scalar, a, capacity):
    return a[0], [mul(x, scalar, capacity) for x in a[1]]


def evaluate(matrix, v, capacity, transposed=False):
    result = []
    for i in range(4):
        total = ZERO
        for j in range(4):
            entry = matrix[j][i] if transposed else matrix[i][j]
            total = checked(capacity, total, mul(entry, v[j], capacity))
        result.append(total)
    return result


def quadratic_form(matrix, v, capacity):
    image = evaluate(matrix, v, capacity)
    total = mul(image[0], v[0], capacity)
    for i in range(1, 4):
        total = checked(capacity, total, mul(image[i], v[i], capacity))
    return total


def matrix_gcd(matrix):
    total = matrix[0][0]
    for row in matrix:
        for entry in row:
            total = gcd(total, entry)
    return total


def rational(a):
    return [Fraction(x.value, a[0].value) for x in a[1]]


def quaternion_product(x, y, p):
    return [x[0] * y[0] - x[1] * y[1] - p * (x[2] * y[2] + x[3] * y[3]),
            x[0] * y[1] + x[1] * y[0] + p * (x[2] * y[3] - x[3] * y[2]),
            x[0] * y[2] + x[2] * y[0] - x[1] * y[3] + x[3] * y[1],
            x[0] * y[3] + x[3] * y[0] + x[1] * y[2] - x[2] * y[1]]


def text(values):
    return " ".join(x.text() for x in values)


def element_text(a):
    return text([a[0], *a[1]])


def element_records(level, capacity, stream, p, count, bits):
    for _ in range(count):
        a = stream.nonzero(bits), [stream.ibz(bits) for _ in range(4)]
        b = stream.nonzero(bits), [stream.ibz(bits) for _ in range(4)]
        if stream.take(1) % 4 == 0:
            a = a[0], [ZERO, ZERO, ZERO, ZERO]
        if stream.take(1) % 3 == 0:
            factor = stream.nonzero(bits // 2)
            a = mul(a[0], factor, capacity), [mul(x, factor, capacity) for x in a[1]]
        product = element_mul(a, b, p, capacity)
        assert rational(product) == quaternion_product(rational(a), rational(b), p.value)
        total = element_add(a, b, capacity)
        assert rational(total) == [x + y for x, y in zip(rational(a), rational(b))]
        numerator, denominator = norm(a, p, capacity)
        exact = sum(x * x * w for x, w in zip(rational(a), (1, 1, p.value, p.value)))
        assert Fraction(numerator.value, denominator.value) == exact and denominator.value > 0
        assert gcd(numerator, denominator).value == 1 or numerator.value == 0
        reduced = normalize(a, capacity)
        assert rational(reduced) == rational(a) and reduced[0].value > 0
        assert gcd(content(reduced[1]), reduced[0]).value == 1
        scalar = stream.ibz(bits)
        yield f"qmul {level} {p.text()} {element_text(a)} {element_text(b)} = {element_text(product)}"
        yield f"qadd {level} {element_text(a)} {element_text(b)} = {element_text(total)}"
        yield f"qnorm {level} {p.text()} {element_text(a)} = {numerator.text()} {denominator.text()}"
        yield f"qnormalize {level} {element_text(a)} = {element_text(reduced)}"
        yield f"qconj {level} {element_text(a)} = {element_text(conjugate(a))}"
        yield f"qscale {level} {scalar.text()} {element_text(a)} = {element_text(element_scale(scalar, a, capacity))}"


def vector_records(level, capacity, stream, count, bits):
    for index in range(count):
        v = [stream.ibz(bits) for _ in range(4)]
        w = [stream.ibz(bits) for _ in range(4)]
        if index % 4 == 0:
            factor = stream.nonzero(bits // 2)
            v = [mul(x, factor, capacity) for x in v]
        if index % 5 == 1:
            v = [ZERO, ZERO, ZERO, ZERO]
        g = content(v)
        assert all(x.value % g.value == 0 for x in v) if g.value else all(x.value == 0 for x in v)
        divisor = g if g.value and index % 2 else stream.nonzero(bits // 3)
        quotients = scalar_div(divisor, v)
        difference = [checked(capacity, x, y, negate=True) for x, y in zip(v, w)]
        yield f"vcontent {level} {text(v)} = {g.text()}"
        yield f"vdiv {level} {divisor.text()} {text(v)} = {text(quotients)}"
        yield f"vsub {level} {text(v)} {text(w)} = {text(difference)}"


def matrix_records(level, capacity, stream, count, bits):
    for index in range(count):
        matrix = [[stream.ibz(bits) for _ in range(4)] for _ in range(4)]
        v = [stream.ibz(bits) for _ in range(4)]
        if index % 3 == 0:
            factor = stream.nonzero(bits // 2)
            matrix = [[mul(x, factor, capacity) for x in row] for row in matrix]
        if index == 1:
            matrix = [[ZERO] * 4 for _ in range(4)]
        flat = [x for row in matrix for x in row]
        image = evaluate(matrix, v, capacity)
        assert [x.value for x in image] == [sum(m.value * y.value for m, y in zip(row, v)) for row in matrix]
        image_t = evaluate(matrix, v, capacity, transposed=True)
        form = quadratic_form(matrix, v, capacity)
        assert form.value == sum(x.value * y.value for x, y in zip(image, v))
        g = matrix_gcd(matrix)
        divisor = g if g.value and index % 2 == 0 else stream.nonzero(bits // 3)
        scalar = stream.ibz(bits)
        yield f"meval {level} {text(flat)} {text(v)} = {text(image)}"
        yield f"mevalt {level} {text(v)} {text(flat)} = {text(image_t)}"
        yield f"qfeval {level} {text(flat)} {text(v)} = {form.text()}"
        yield f"mgcd {level} {text(flat)} = {g.text()}"
        yield f"mdiv {level} {divisor.text()} {text(flat)} = {text([divide(x, divisor)[0] for x in flat])}"
        yield f"mscale {level} {scalar.text()} {text(flat)} = {text([mul(x, scalar, capacity) for x in flat])}"


def records():
    for level, capacity in CAPACITIES.items():
        full = level == "p324_3"
        stream = Stream(f"algebra-{level}")
        p = prime(level)
        bits = p.bitlen
        for size in (8, 64, bits):
            yield from element_records(level, capacity, stream, p, 6 if full else 2, size)
            yield from vector_records(level, capacity, stream, 5 if full else 2, size)
            yield from matrix_records(level, capacity, stream, 3 if full else 2, size)
    assert not OVERFLOW[0]


def content_text():
    lines = "\n".join(f'        "{record}",' for record in records())
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_algebra_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.quaternion\n\n"
        "internal object AlgebraVectors {\n"
        "    internal val records: List<String> = listOf(\n"
        f"{lines}\n"
        "    )\n"
        "}\n"
    )


def benchmark_values(level):
    """`p`, two full-width elements and a Gram-like form with a short vector, as signed hex."""
    stream = Stream(f"algebra-benchmark-{level}")
    p = prime(level).value
    bits = p.bit_length()
    elements = [stream.take(bits // 8) | 1] + [stream.signed(bits) for _ in range(4)]
    elements += [stream.take(bits // 8) | 1] + [stream.signed(bits) for _ in range(4)]
    basis = [[stream.signed(bits) for _ in range(4)] for _ in range(4)]
    gram = [[sum(basis[k][i] * basis[k][j] for k in range(4)) for j in range(4)] for i in range(4)]
    vector = [stream.signed(64) for _ in range(4)]
    return [p, *elements, *(x for row in gram for x in row), *vector]


def benchmark_content():
    branches = []
    for level, parameter in PARAMETER_SETS.items():
        values = "\n".join(f'                "{hex_text(v)}",' for v in benchmark_values(level))
        branches.append(f"        ParameterSet.{parameter} ->\n            listOf(\n{values}\n            )")
    body = "\n".join(branches)
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_algebra_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.benchmarks\n\n"
        "import ch.trancee.sqisign.ParameterSet\n\n"
        "/**\n"
        " * Hexadecimal `p`, two elements as `denominator, coordinates` and a 4x4 Gram form with a\n"
        " * 64-bit vector.\n"
        " */\n"
        "internal fun algebraInputs(parameter: ParameterSet): List<String> =\n"
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
                raise SystemExit("Algebra vectors are stale; run tools/generate_algebra_vectors.py")
        else:
            path.write_text(text_content)
    print(f"Verified {expected.count(chr(10) + '        \"')} quaternion algebra records")


if __name__ == "__main__":
    main()
