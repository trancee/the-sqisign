#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Generate independent field-power fixtures for the prepared torsion logarithm seam."""

import argparse
from functools import lru_cache

from generate_basis_vectors import basis_curves, canonical_basis, e0_basis, negate_point
from generate_curve_vectors import ORACLES, ROOT, ZERO
from generate_torsion_ring_vectors import encode, sample
from pairing_oracle import power, primitive_root, tate


OUTPUT = ROOT / "src/commonTest/kotlin/ch/trancee/sqisign/ec/PairingVectors.kt"
BENCHMARK_OUTPUT = ROOT / "src/jvmBenchmark/kotlin/ch/trancee/sqisign/benchmarks/DiscreteLogInputs.kt"
TATE_BENCHMARK_OUTPUT = ROOT / "src/jvmBenchmark/kotlin/ch/trancee/sqisign/benchmarks/TatePairingInputs.kt"


def discrete_log_records(oracle):
    root = primitive_root(oracle)
    records = []
    for exponent in (1, 2, 8, 9, oracle.exponent - 1, oracle.exponent):
        generator = power(oracle, root, 1 << (oracle.exponent - exponent))
        maximum = (1 << exponent) - 1
        if exponent < 10:
            coefficients = (0, 1 << (exponent - 1), maximum)
        elif exponent < oracle.exponent:
            coefficients = (0, maximum, sample(exponent, f"reduced dlog {oracle.identifier}"))
        else:
            coefficients = (0, 1 << (exponent - 1), maximum,
                            sample(exponent, f"full dlog {oracle.identifier}"))
        for coefficient in coefficients:
            value = power(oracle, generator, coefficient)
            records.append(":".join((str(exponent), oracle.encode_field(generator),
                                     oracle.encode_field(value), encode(exponent, coefficient))))
    return records


@lru_cache
def pairing_curves(oracle):
    first, second = e0_basis(oracle)
    difference = oracle.affine_add(ZERO, first, negate_point(oracle, second))
    if difference[0] != oracle.reference_difference(ZERO, first, second):
        second = negate_point(oracle, second)
        difference = oracle.affine_add(ZERO, first, negate_point(oracle, second))
    curves = [(ZERO, (first, second, difference))]
    curves.extend((coefficient, canonical_basis(oracle, coefficient, oracle.exponent)[0])
                  for coefficient in basis_curves(oracle))
    return curves


def pairing_curve_records(oracle):
    return [":".join((oracle.encode_field(coefficient),
                     *(oracle.encode_affine(point) for point in basis)))
            for coefficient, basis in pairing_curves(oracle)]


def tate_records(oracle):
    records = []
    for index, (coefficient, basis) in enumerate(pairing_curves(oracle)):
        first, second = basis[:2]
        for exponent in (1, 8, oracle.exponent - 1, oracle.exponent):
            reduced = oracle.affine_multiply(
                coefficient, first, 1 << (oracle.exponent - exponent))
            cases = [("generic", reduced, second)]
            if exponent == 8:
                cases.append(("inverse", reduced, negate_point(oracle, second)))
            if index == 0:
                other = oracle.affine_multiply(
                    coefficient, second, 1 << (oracle.exponent - exponent))
                if exponent in (8, oracle.exponent):
                    cases.append(("swapped", other, first))
                if exponent != oracle.exponent:
                    cases.append(("restricted", reduced, other))
                cases.extend((
                    ("self", reduced, reduced),
                    ("opposite", reduced, negate_point(oracle, reduced)),
                    ("firstT0", (ZERO, ZERO), first),
                    ("secondT0", reduced, (ZERO, ZERO)),
                    ("smallerOrder", (ZERO, ZERO), second),
                    ("infinityFirst", None, second),
                    ("infinitySecond", reduced, None),
                ))
                if exponent == oracle.exponent:
                    cases.extend((("secondT0Square", second, (ZERO, ZERO)),
                                  ("bothT0", (ZERO, ZERO), (ZERO, ZERO))))
            elif exponent == oracle.exponent:
                cases.extend((("self", reduced, reduced),
                              ("firstT0", (ZERO, ZERO), first),
                              ("secondT0", reduced, (ZERO, ZERO))))
            elif exponent == 8:
                smaller = oracle.affine_multiply(
                    coefficient, first, 1 << (oracle.exponent - 2))
                cases.extend((("smallerOrder", smaller, second),
                              ("arbitrarySecond", reduced,
                               oracle.sample_point(coefficient, "public arbitrary pairing point"))))
            for label, left, right in cases:
                addition = oracle.affine_add(coefficient, left, right)
                value = tate(oracle, coefficient, exponent, left, right)
                records.append(":".join((
                    str(index), str(exponent), label,
                    *(oracle.encode_affine(point) for point in (left, right, addition)),
                    oracle.encode_field(value))))
    return records


def content():
    sections = []
    for oracle in ORACLES:
        for suffix, records in (("DiscreteLogs", discrete_log_records(oracle)),
                                ("PairingCurves", pairing_curve_records(oracle)),
                                ("TatePairings", tate_records(oracle))):
            name = f"p{oracle.exponent}{suffix}"
            lines = "\n".join(f'        "{record}",' for record in records)
            sections.append(f"    internal val {name}: List<String> = listOf(\n{lines}\n    )\n")
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_pairing_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.ec\n\n"
        "internal object PairingVectors {\n" + "\n".join(sections) + "}\n"
    )


def benchmark_content():
    cases = []
    for oracle, parameter in zip(ORACLES, ("NIST_I", "NIST_III", "NIST_V"), strict=True):
        encoded = oracle.encode_field(primitive_root(oracle))
        cases.append(f'    ParameterSet.{parameter} -> "{encoded}"')
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_pairing_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.benchmarks\n\n"
        "import ch.trancee.sqisign.ParameterSet\n\n"
        "internal fun discreteLogGenerator(parameter: ParameterSet): String = when (parameter) {\n"
        + "\n".join(cases) + "\n}\n"
    )


def tate_benchmark_content():
    cases = []
    for oracle, parameter in zip(ORACLES, ("NIST_I", "NIST_III", "NIST_V"), strict=True):
        coefficient, basis = pairing_curves(oracle)[0]
        encodings = []
        for exponent in (oracle.exponent, 1):
            first = oracle.affine_multiply(
                coefficient, basis[0], 1 << (oracle.exponent - exponent))
            addition = oracle.affine_add(coefficient, first, basis[1])
            inputs = (*basis, first, basis[1], addition)
            encodings.append("listOf(\n" + "\n".join(
                f'            "{oracle.encode_affine(point)}",' for point in inputs) + "\n        )")
        cases.append(f"    ParameterSet.{parameter} -> if (fullOrder) {encodings[0]} else {encodings[1]}")
    return (
        "// SPDX-License-Identifier: Apache-2.0\n"
        "// Generated by tools/generate_pairing_vectors.py; do not edit.\n\n"
        "package ch.trancee.sqisign.benchmarks\n\n"
        "import ch.trancee.sqisign.ParameterSet\n\n"
        "internal fun tatePairingInputs(parameter: ParameterSet, fullOrder: Boolean): List<String> =\n"
        "    when (parameter) {\n" + "\n".join(cases) + "\n}\n"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    for output, expected in ((OUTPUT, content()), (BENCHMARK_OUTPUT, benchmark_content()),
                             (TATE_BENCHMARK_OUTPUT, tate_benchmark_content())):
        if arguments.check:
            if not output.exists() or output.read_text() != expected:
                raise SystemExit("Pairing vectors are stale; run tools/generate_pairing_vectors.py")
        else:
            output.write_text(expected)
    print("Verified 57 independent logarithms and 180 shifted-Miller Tate pairings")


if __name__ == "__main__":
    main()
