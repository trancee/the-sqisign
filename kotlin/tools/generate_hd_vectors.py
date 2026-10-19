#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Generate dimension-two (2,2)-isogeny chain fixtures from the independent theta oracle.

Whole chains use kernels captured from the C reference while it verifies public KAT signatures
(`tools/hd_kernels.txt`, confirmed by check_hd_reference.py). Synthetic p324 records cover the
gluing matrix special cases, theta-to-Montgomery selection and every splitting rejection.
"""

import argparse
from functools import lru_cache

from generate_curve_vectors import ONE, ORACLES, ROOT, ZERO
from hd_oracle import ChainFailure, I, gluing_matrix, lift, push, split, theta_to_montgomery
from isogeny_oracle import curve_rhs


FIXTURE = ROOT / "tools/hd_kernels.txt"
OUTPUT = ROOT / "src/commonTest/kotlin/ch/trancee/sqisign/hd/ThetaChainVectors.kt"
BENCHMARK_OUTPUT = ROOT / "src/jvmBenchmark/kotlin/ch/trancee/sqisign/benchmarks/ThetaChainInputs.kt"
KAT_FILES = {
    "p324_3": "PQCsignKAT_270_SQIsign_p324_3.rsp",
    "p500_27": "PQCsignKAT_417_SQIsign_p500_27.rsp",
    "p664_17": "PQCsignKAT_549_SQIsign_p664_17.rsp",
}
FIXTURE_RECORDS = {"p324_3": (0, 1), "p500_27": (0,), "p664_17": (0,)}
MODES = (0, 1, 2, -1)


def decode(oracle, encoded):
    raw = bytes.fromhex(encoded)
    return tuple(int.from_bytes(raw[offset:offset + oracle.width], "little") for offset in (0, oracle.width))


def encode(oracle, value):
    return "-" if value is None else oracle.encode_field(value)


@lru_cache
def kernels(oracle):
    """(count, length, (E1, E2), kernel x-coordinates, E_com) for each captured KAT chain."""
    result = []
    for line in FIXTURE.read_text().splitlines():
        identifier, count, length, *fields = line.split()
        if identifier == oracle.identifier:
            values = [decode(oracle, field) for field in fields]
            result.append((int(count), int(length), tuple(values[:2]), values[2:6], values[6]))
    return result


def multiple(oracle, coefficient, x, scalar):
    point = oracle.affine_multiply(coefficient, lift(oracle, coefficient, x), scalar)
    return None if point is None else point[0]


def twist_x(oracle, coefficient):
    return next(x for x in (oracle.sample_field(f"hd twist {counter}") for counter in range(64))
                if oracle.reference_root(curve_rhs(oracle, coefficient, x)) is None)


def pushed_points(oracle, curves, kernel):
    first = oracle.sample_point(curves[0], "hd first")[0]
    second = oracle.sample_point(curves[1], "hd second")[0]
    return [(first, None), (None, second), (kernel[0], None), (twist_x(oracle, curves[0]), None), (None, None)]


def outcome(oracle, null, images, mode):
    try:
        curves, mapped = split(oracle, null, images, mode)
    except ChainFailure as failure:
        return failure.reason
    return ",".join(encode(oracle, value) for value in (*curves, *(x for pair in mapped for x in pair)))


def chain_outcome(oracle, length, curves, kernel, points, mode):
    try:
        return outcome(oracle, *push(oracle, length, curves, kernel, points, mode == -1), mode)
    except ChainFailure as failure:
        return failure.reason


def chain_record(oracle, label, mode, length, curves, kernel, points, result):
    encoded_points = ";".join(f"{encode(oracle, a)}/{encode(oracle, b)}" for a, b in points)
    return ":".join((label, str(mode), str(length), *(encode(oracle, value) for value in (*curves, *kernel)),
                     encoded_points, result))


def rejection_cases(oracle, length, curves, kernel):
    first, second = curves
    shorter = [multiple(oracle, curves[index % 2], x, 2) for index, x in enumerate(kernel)]
    mixed = [*kernel[:3], multiple(oracle, second, kernel[3], 3)]
    return [
        ("shorter", 0, length - 1, kernel, []), ("shorter", -1, length - 1, kernel, []),
        ("longer", -1, length + 1, kernel, []),
        ("duplicate", 0, length, kernel[:2] * 2, []), ("duplicate", -1, length, kernel[:2] * 2, []),
        ("swapped", -1, length, [kernel[0], kernel[3], kernel[2], kernel[1]], []),
        ("mixed", 0, length, mixed, []), ("mixed", -1, length, mixed, []),
        ("halved", 0, length - 1, shorter, []),
        ("degenerate", 0, length, kernel, [(ONE, None)]),
    ]


@lru_cache
def chain_records(oracle):
    records = []
    for count, length, curves, kernel, commitment in kernels(oracle):
        points = pushed_points(oracle, curves, kernel)
        null, images = push(oracle, length, curves, kernel, points, True)
        for mode in MODES:
            result = outcome(oracle, null, images, mode)
            records.append(chain_record(oracle, f"kat{count}", mode, length, curves, kernel, points, result))
        if outcome(oracle, null, [], -1).split(",")[0] != oracle.encode_field(commitment):
            raise SystemExit(f"{oracle.identifier} KAT {count} oracle codomain differs from the C commitment")
    if oracle.identifier == "p324_3":
        _, length, curves, kernel, _ = kernels(oracle)[0]
        for label, mode, chain_length, chain_kernel, points in rejection_cases(oracle, length, curves, kernel):
            result = chain_outcome(oracle, chain_length, curves, chain_kernel, points, mode)
            records.append(chain_record(oracle, label, mode, chain_length, curves, chain_kernel, points, result))
    return records


def affine(x):
    return (x, ONE)


def special_case_inputs(oracle):
    """K1.P1, K1.P2, K2.P1, K2.P2 for each symmetric-element case pair of Algorithm 4.115."""
    o = oracle
    sample = lambda label: o.sample_field(f"hd matrix {label}")  # noqa: E731
    minus_one = o.negate(ONE)
    first = sample("first")
    rotated, difference = o.multiply(I, o.add(first, ONE)), o.subtract(first, ONE)
    case_four = o.multiply(o.subtract(rotated, difference), o.inverse(o.add(rotated, difference)))
    other = sample("other")
    rotated_other, difference_other = o.multiply(I, o.add(other, ONE)), o.subtract(other, ONE)
    case_five = o.multiply(o.add(rotated_other, difference_other), o.inverse(o.subtract(rotated_other, difference_other)))
    return [
        ("generic", [sample(index) for index in range(4)]),
        ("firstSecond", [ONE, sample("a"), sample("b"), ONE]),
        ("negated", [minus_one, sample("c"), sample("d"), minus_one]),
        ("sums", [first, other, case_four, case_five]),
    ]


@lru_cache
def matrix_records(oracle):
    records = []
    for label, xs in special_case_inputs(oracle):
        k1, k2 = [affine(xs[0]), affine(xs[1])], [affine(xs[2]), affine(xs[3])]
        entries = [value for row in gluing_matrix(oracle, k1, k2) for value in row]
        records.append(":".join((label, *(oracle.encode_field(x) for x in xs), ",".join(map(oracle.encode_field, entries)))))
    return records


@lru_cache
def model_records(oracle):
    records, seen, counter = [], set(), 0
    while len(seen) < 6:
        theta = (oracle.sample_field(f"hd model {counter} a"), oracle.sample_field(f"hd model {counter} b"))
        counter += 1
        a, matrix, index = theta_to_montgomery(oracle, theta)
        if index in seen:
            continue
        seen.add(index)
        records.append(":".join((*map(oracle.encode_field, theta), ",".join(map(oracle.encode_field, (a, *matrix))))))
    for theta in ((ONE, ONE), (I, ONE)):
        records.append(":".join((*map(oracle.encode_field, theta), "-")))
    return records


def product(oracle, first, second):
    """Theta point with splitting factors (a1 : b1) and (a2 : b2), before the change of coordinates."""
    o = oracle
    (a1, b1), (a2, b2) = first, second
    big = (o.multiply(a1, a2), o.multiply(a1, b2), o.multiply(b1, a2), o.multiply(b1, b2))
    half = o.inverse((2, 0))
    scale = lambda value: o.multiply(value, half)  # noqa: E731
    return (scale(o.add(big[0], big[3])), scale(o.add(big[1], big[2])), scale(o.subtract(big[0], big[3])),
            scale(o.subtract(big[1], big[2])))


@lru_cache
def split_records(oracle):
    o = oracle
    sample = lambda label: o.sample_field(f"hd split {label}")  # noqa: E731
    pair = lambda label: (sample(f"{label} x"), sample(f"{label} z"))  # noqa: E731
    null = product(o, pair("first"), pair("second"))
    points = [product(o, pair("p"), pair("q")), product(o, (ONE, ZERO), pair("r")), product(o, pair("s"), (ONE, ZERO))]
    noise = tuple(sample(f"noise {index}") for index in range(4))
    cases = [(f"valid{mode}", null, points, mode) for mode in MODES] + [
        ("zero", product(o, (ZERO, ONE), pair("t")), [], 0),
        ("nonProduct", noise, [], 0),
        ("pointProduct", null, [points[0], noise], 1),
        ("invalid", product(o, pair("u"), (ONE, ONE)), [], -1),
        ("secondCurve", product(o, pair("u"), (ONE, ONE)), [], 0),
        ("firstCurve", product(o, (ONE, ONE), pair("v")), [], 1),
        ("degenerate", null, [(ZERO, ZERO, ZERO, ZERO)], 2),
    ]
    records = []
    for label, theta, pushed, mode in cases:
        encoded = ",".join(o.encode_field(x) for point in (theta, *pushed) for x in point)
        records.append(":".join((label, str(mode), encoded, outcome(o, theta, pushed, mode))))
    return records


def content():
    sections = []
    for oracle in ORACLES:
        prefix = f"p{oracle.exponent}"
        groups = [("Chains", chain_records(oracle))]
        if oracle.identifier == "p324_3":
            groups += [("Matrices", matrix_records(oracle)), ("Models", model_records(oracle)),
                       ("Splits", split_records(oracle))]
        for suffix, records in groups:
            lines = "\n".join(f'        "{record}",' for record in records)
            sections.append(f"    internal val {prefix}{suffix}: List<String> = listOf(\n{lines}\n    )\n")
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_hd_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.hd\n\n"
        "internal object ThetaChainVectors {\n" + "\n".join(sections) + "}\n"
    )


def benchmark_content():
    cases = []
    for oracle, parameter in zip(ORACLES, ("NIST_I", "NIST_III", "NIST_V"), strict=True):
        _, length, curves, kernel, _ = kernels(oracle)[0]
        encodings = "\n".join(f'                "{oracle.encode_field(value)}",' for value in (*curves, *kernel))
        cases.append(f"        ParameterSet.{parameter} ->\n            {length} to\n                listOf(\n"
                     f"{encodings}\n                )")
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_hd_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.benchmarks\n\n"
        "import ch.trancee.sqisign.ParameterSet\n\n"
        "/** KAT verification chain length, E1 and E2 coefficients, then T1.P1, T1.P2, T2.P1, T2.P2. */\n"
        "internal fun thetaChainInputs(parameter: ParameterSet): Pair<Int, List<String>> =\n"
        "    when (parameter) {\n" + "\n".join(cases) + "\n    }\n"
    )


def outputs():
    return ((OUTPUT, content()), (BENCHMARK_OUTPUT, benchmark_content()))


def check():
    for output, expected in outputs():
        if not output.exists() or output.read_text() != expected:
            raise SystemExit("HD chain vectors are stale; run tools/generate_hd_vectors.py")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    if arguments.check:
        check()
    else:
        for output, expected in outputs():
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(expected)
    for oracle in ORACLES:
        print(f"{oracle.identifier}: {len(chain_records(oracle))} chain records")


if __name__ == "__main__":
    main()
