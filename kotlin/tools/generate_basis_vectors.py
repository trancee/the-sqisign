#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Generate canonical coordinates and torsion bases with independent affine Python arithmetic."""

import argparse
from functools import lru_cache

from generate_curve_vectors import ONE, ORACLES, OUTPUT as CURVE_OUTPUT, ZERO
from generate_test_vectors import ROOT, fp2_prime_field_square_root, literal


OUTPUT = CURVE_OUTPUT.with_name("TorsionVectors.kt")
COORDINATES = ROOT / "src/commonMain/kotlin/ch/trancee/sqisign/ec/E0BasisCoordinates.kt"
BENCHMARK_INPUTS = ROOT / "src/jvmBenchmark/kotlin/ch/trancee/sqisign/benchmarks/CanonicalBasisInputs.kt"


@lru_cache
def e0_basis(oracle):
    """Appendix B.1 / torsion_basis.py, using actual odd order and two-torsion independence."""
    cofactor = (oracle.prime + 1) >> oracle.exponent
    divisors = [value for value in range(1, cofactor + 1) if cofactor % value == 0]
    first = first_top = None
    for counter in range(1, 1024):
        x = (1, counter)
        rhs = oracle.multiply(x, oracle.add(oracle.multiply(x, x), ONE))
        y = fp2_prime_field_square_root(*rhs, oracle.prime)
        if y is None:
            continue
        point = x, min(y, oracle.negate(y))
        odd_part = oracle.affine_multiply(ZERO, point, 1 << oracle.exponent)
        actual_odd_order = next(
            divisor for divisor in divisors
            if oracle.affine_multiply(ZERO, odd_part, divisor) is None
        )
        point = oracle.affine_multiply(ZERO, point, actual_odd_order)
        top = oracle.affine_multiply(ZERO, point, 1 << (oracle.exponent - 1))
        if top is None:
            continue
        if first is None:
            first, first_top = point, top
            continue
        if top == first_top:
            continue
        second = point
        if top[0] == ZERO:
            pass
        elif first_top[0] == ZERO:
            first, second = second, first
        else:
            second = oracle.affine_add(ZERO, second, first)
        return first, second
    raise RuntimeError(f"Canonical E0 basis search exhausted the offline fixture bound: {oracle.identifier}")


def e0_records(oracle):
    first, second = e0_basis(oracle)
    records = []
    for exponent in (1, 2, 8, oracle.exponent):
        scalar = 1 << (oracle.exponent - exponent)
        left = oracle.affine_multiply(ZERO, first, scalar)
        right = oracle.affine_multiply(ZERO, second, scalar)
        difference = oracle.reference_difference(ZERO, left, right)
        records.append(":".join((
            str(exponent), oracle.encode_field(left[0]), oracle.encode_field(right[0]),
            oracle.encode_field(difference),
        )))
    return records


def negate_point(oracle, point):
    return point[0], oracle.negate(point[1])


def coefficient_is_square(oracle, coefficient):
    norm = sum(component * component for component in coefficient) % oracle.prime
    return pow(norm, (oracle.prime - 1) // 2, oracle.prime) in (0, 1)


def canonical_basis(oracle, coefficient, exponent, reconstruction_exponent=0, start_index=1):
    square = coefficient_is_square(oracle, coefficient)
    for counter in range(start_index, 1024):
        if square:
            if pow(1 + counter * counter, (oracle.prime - 1) // 2, oracle.prime) != oracle.prime - 1:
                continue
            x = oracle.multiply(oracle.negate(coefficient), oracle.inverse((1, counter)))
        else:
            x = oracle.multiply((counter, 0), coefficient)
        rhs = oracle.multiply(x, oracle.add(oracle.add(
            oracle.multiply(x, x), oracle.multiply(coefficient, x)), ONE))
        root = fp2_prime_field_square_root(*rhs, oracle.prime)
        if root is not None:
            break
    else:
        raise RuntimeError("Offline canonical basis search exhausted")
    other_x = oracle.negate(oracle.add(x, coefficient))
    other_rhs = oracle.multiply(other_x, oracle.add(oracle.add(
        oracle.multiply(other_x, other_x), oracle.multiply(coefficient, other_x)), ONE))
    other_root = fp2_prime_field_square_root(*other_rhs, oracle.prime)
    if other_root is None:
        raise RuntimeError("Entangled partner unexpectedly lies off the curve")
    scalar = (oracle.prime + 1) >> exponent
    first = oracle.affine_multiply(coefficient, (x, root), scalar)
    other = oracle.affine_multiply(coefficient, (other_x, other_root), scalar)
    difference_x = oracle.reference_difference(coefficient, first, other)
    difference = oracle.affine_add(coefficient, first, negate_point(oracle, other))
    if difference[0] != difference_x:
        other = negate_point(oracle, other)
        difference = oracle.affine_add(coefficient, first, negate_point(oracle, other))
    if reconstruction_exponent:
        scale = 1 << (exponent - reconstruction_exponent)
        plus = oracle.affine_add(coefficient, first, other)
        low_minus = oracle.affine_multiply(coefficient, difference, scale)
        low_plus = oracle.affine_multiply(coefficient, plus, scale)
        delta = oracle.subtract(low_minus[0], low_plus[0])
        if oracle.reference_root(oracle.multiply(delta, delta)) == delta:
            difference = plus
    third = oracle.affine_add(coefficient, first, negate_point(oracle, difference))
    tops = [oracle.affine_multiply(coefficient, point, 1 << (exponent - 1))
            for point in (first, difference)]
    if any(point is None for point in tops) or tops[0] == tops[1]:
        raise RuntimeError("Independent affine order/independence check failed")
    if any(oracle.affine_add(coefficient, point, point) is not None for point in tops):
        raise RuntimeError("Independent affine torsion-order check failed")
    return (first, difference, third), 0 if counter >= 128 else 2 * counter + int(square)


def four_quotient_coefficient(oracle, point):
    fourth = oracle.multiply(oracle.multiply(point[0], point[0]),
                             oracle.multiply(point[0], point[0]))
    return oracle.subtract((2, 0), oracle.multiply((4, 0), fourth))


@lru_cache
def basis_curves(oracle):
    first = e0_basis(oracle)[0]
    kernel = oracle.affine_multiply(ZERO, first, 1 << (oracle.exponent - 2))
    square_curve = four_quotient_coefficient(oracle, kernel)
    if not coefficient_is_square(oracle, square_curve):
        raise RuntimeError("Expected the first E0 quotient coefficient to be square")
    current = square_curve
    for step in range(32):
        if not coefficient_is_square(oracle, current):
            return square_curve, current
        for candidate in range(32):
            point = oracle.sample_point(current, f"public basis curve {step} {candidate}")
            point = oracle.affine_multiply(
                current, point, (oracle.prime + 1) >> oracle.exponent)
            top = oracle.affine_multiply(current, point, 1 << (oracle.exponent - 1))
            if top is not None and top[0] != ZERO:
                kernel = oracle.affine_multiply(current, point, 1 << (oracle.exponent - 2))
                current = four_quotient_coefficient(oracle, kernel)
                break
        else:
            raise RuntimeError("Offline full-order kernel search exhausted")
    raise RuntimeError("Offline nonsquare-coefficient curve search exhausted")


def general_basis_records(oracle):
    records = []
    cases = [(coefficient, exponent, reconstruction)
             for coefficient in basis_curves(oracle)
             for exponent, reconstruction in ((1, 0), (8, 0), (oracle.exponent, 0),
                                              (oracle.exponent, 8))]
    cases.extend((coefficient, oracle.exponent, 1) for coefficient in basis_curves(oracle))
    for coefficient, exponent, reconstruction in cases:
        points, hint = canonical_basis(oracle, coefficient, exponent, reconstruction)
        records.append(":".join((
            oracle.encode_field(coefficient), str(exponent), str(reconstruction), str(hint),
            *(oracle.encode_affine(point) for point in points),
        )))
    return records


def reconstruction_records(oracle):
    records = [":".join(record.split(":")[:2] + record.split(":")[3:])
               for record in general_basis_records(oracle) if record.split(":")[2] == "0"]
    for coefficient in basis_curves(oracle):
        _, first_hint = canonical_basis(oracle, coefficient, 8)
        points, hint = canonical_basis(oracle, coefficient, 8, start_index=(first_hint >> 1) + 1)
        records.append(":".join((oracle.encode_field(coefficient), "8", str(hint),
                                 *(oracle.encode_affine(point) for point in points))))
    for coefficient in basis_curves(oracle):
        points, hint = canonical_basis(oracle, coefficient, 8, start_index=127)
        if hint:
            records.append(":".join((oracle.encode_field(coefficient), "8", str(hint),
                                     *(oracle.encode_affine(point) for point in points))))
    return records


def hinted_e0_records(oracle):
    first, second = e0_basis(oracle)
    records = []
    for exponent, target in ((8, 2), (oracle.exponent, oracle.exponent - 1)):
        scalar = 1 << (oracle.exponent - exponent)
        left = oracle.affine_multiply(ZERO, first, scalar)
        right = oracle.affine_multiply(ZERO, second, scalar)
        difference = oracle.affine_add(ZERO, left, negate_point(oracle, right))
        if difference[0] != oracle.reference_difference(ZERO, left, right):
            right = negate_point(oracle, right)
            difference = oracle.affine_add(ZERO, left, negate_point(oracle, right))
        reduction = 1 << (exponent - target)
        desired = oracle.reference_difference(
            ZERO, oracle.affine_multiply(ZERO, left, reduction),
            oracle.affine_multiply(ZERO, right, reduction))
        if oracle.affine_multiply(ZERO, difference, reduction)[0] != desired:
            difference = oracle.affine_add(ZERO, left, right)
        if oracle.affine_multiply(ZERO, difference, reduction)[0] != desired:
            raise RuntimeError("Independent E0 reduction orientation check failed")
        records.append(":".join((str(exponent), str(target),
                                 *(oracle.encode_affine(point) for point in (left, right, difference)))))
    return records


def render_vectors():
    sections = []
    for oracle in ORACLES:
        records = ",\n".join(f"    {literal(record)}" for record in e0_records(oracle))
        sections.append(
            f"internal val e0Basis{oracle.fixture_name}Vectors: List<String> = listOf(\n{records},\n)\n"
        )
        general = ",\n".join(f"    {literal(record)}" for record in general_basis_records(oracle))
        sections.append(
            f"internal val canonicalBasis{oracle.fixture_name}Vectors: List<String> = listOf(\n{general},\n)\n"
        )
        for name, values in (("reconstruction", reconstruction_records(oracle)),
                             ("hintedE0", hinted_e0_records(oracle))):
            records = ",\n".join(f"    {literal(record)}" for record in values)
            sections.append(
                f"internal val {name}{oracle.fixture_name}Vectors: List<String> = listOf(\n{records},\n)\n"
            )
    return ("// SPDX-License-Identifier: Apache-2.0\n"
            "// Generated by tools/generate_basis_vectors.py; do not edit.\n\n"
            "package ch.trancee.sqisign.ec\n\n" + "\n".join(sections))


def render_coordinates():
    sections = []
    for oracle, parameter in zip(ORACLES, ("NIST_I", "NIST_III", "NIST_V"), strict=True):
        points = e0_basis(oracle)
        values = ",\n".join(f"            {literal(oracle.encode_field(point[0]))}" for point in points)
        sections.append(f"        ParameterSet.{parameter} -> listOf(\n{values},\n        )")
    return ("// SPDX-License-Identifier: Apache-2.0\n"
            "// Generated by tools/generate_basis_vectors.py; do not edit.\n\n"
            "package ch.trancee.sqisign.ec\n\n"
            "import ch.trancee.sqisign.ParameterSet\n\n"
            "internal fun e0BasisCoordinates(parameter: ParameterSet): List<String> =\n"
            "    when (parameter) {\n" + "\n".join(sections) + "\n    }\n")


def render_benchmark_inputs():
    sections = []
    for oracle, parameter in zip(ORACLES, ("NIST_I", "NIST_III", "NIST_V"), strict=True):
        records = [general_basis_records(oracle)[index].split(":") for index in (0, 4)]
        values = ",\n".join(f"            {literal(record[0] + ':' + record[3])}" for record in records)
        sections.append(f"        ParameterSet.{parameter} -> listOf(\n{values},\n        )")
    return ("// SPDX-License-Identifier: Apache-2.0\n"
            "// Generated by tools/generate_basis_vectors.py; do not edit.\n\n"
            "package ch.trancee.sqisign.benchmarks\n\n"
            "import ch.trancee.sqisign.ParameterSet\n\n"
            "internal fun canonicalBasisInputs(parameter: ParameterSet): List<String> =\n"
            "    when (parameter) {\n" + "\n".join(sections) + "\n    }\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    for path, expected in ((OUTPUT, render_vectors()), (COORDINATES, render_coordinates()),
                           (BENCHMARK_INPUTS, render_benchmark_inputs())):
        if arguments.check:
            if not path.exists() or path.read_text() != expected:
                raise SystemExit(f"{path.name} differs; run python3 tools/generate_basis_vectors.py")
        else:
            path.write_text(expected)
    if arguments.check:
        print("Validated three canonical E0 pairs, 12 E0 bases, 30 general bases, 27 reconstructions and 6 hinted E0 bases")


if __name__ == "__main__":
    main()
