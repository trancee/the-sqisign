#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Independent affine basis action and integer-matrix correspondence oracle."""

from functools import lru_cache

from generate_basis_vectors import canonical_basis, negate_point
from generate_curve_vectors import ZERO
from generate_pairing_vectors import pairing_curves
from generate_torsion_ring_vectors import encode


MATRICES = (
    ("identity", (1, 0, 0, 1)),
    ("negativeIdentity", (-1, 0, 0, -1)),
    ("swap", (0, 1, 1, 0)),
    ("upperShear", (1, 1, 0, 1)),
    ("lowerShear", (1, 0, 1, 1)),
    ("dense", (3, 4, 2, 3)),
    ("negativeDense", (-1, -2, -2, -1)),
    ("reflectSecond", (1, 0, 0, -1)),
)


def act_on_affine_basis(oracle, coefficient, source, entries):
    left = oracle.affine_add(
        coefficient, oracle.affine_multiply(coefficient, source[0], entries[0]),
        oracle.affine_multiply(coefficient, source[1], entries[2]))
    right = oracle.affine_add(
        coefficient, oracle.affine_multiply(coefficient, source[0], entries[1]),
        oracle.affine_multiply(coefficient, source[1], entries[3]))
    difference = oracle.affine_add(coefficient, left, negate_point(oracle, right))
    if any(point is None for point in (left, right, difference)):
        raise ValueError("An invertible matrix unexpectedly lost a basis generator")
    return left, right, difference


def canonical_reference(oracle, curve_index, exponent):
    coefficient, full = pairing_curves(oracle)[curve_index]
    if coefficient != ZERO:
        return canonical_basis(oracle, coefficient, oracle.exponent,
                               exponent if exponent < oracle.exponent else 0)
    first, second, difference = full
    scale = 1 << (oracle.exponent - exponent)
    lower = [oracle.affine_multiply(coefficient, point, scale) for point in (first, second)]
    desired = oracle.reference_difference(coefficient, *lower)
    if oracle.affine_multiply(coefficient, difference, scale)[0] != desired:
        second = negate_point(oracle, second)
        difference = oracle.affine_add(coefficient, first, negate_point(oracle, second))
    if oracle.affine_multiply(coefficient, difference, scale)[0] != desired:
        raise ValueError("Canonical E0 reference did not align with its hint target")
    return (first, second, difference), 0


@lru_cache
def correspondence_records(oracle):
    records = []
    cases = (
        (0, 0, 1, "twoTorsion", MATRICES[2][1], MATRICES[4][1]),
        (1, 2, 8, "mixedBranches", MATRICES[5][1], MATRICES[3][1]),
        (2, 0, oracle.response_bits + 2, "responseWidth", MATRICES[6][1], MATRICES[7][1]),
        (0, 1, oracle.exponent, "fullOrder", MATRICES[3][1], MATRICES[5][1]),
    )
    for first_index, second_index, exponent, label, first_matrix, second_matrix in cases:
        modulus = 1 << exponent
        points = []
        hints = []
        for index, matrix in ((first_index, first_matrix), (second_index, second_matrix)):
            coefficient = pairing_curves(oracle)[index][0]
            full, hint = canonical_reference(oracle, index, exponent)
            lower = [oracle.affine_multiply(coefficient, point, 1 << (oracle.exponent - exponent))
                     for point in full]
            points.extend(act_on_affine_basis(oracle, coefficient, lower,
                                             tuple(value % modulus for value in matrix)))
            hints.append(hint)
        a, b, c, d = first_matrix
        inverse_factor = pow((a * d - b * c) % modulus, -1, modulus)
        inverse = tuple(value * inverse_factor % modulus for value in (d, -b, -c, a))
        a, b, c, d = second_matrix
        entries = tuple(value % modulus for value in (
            a * inverse[0] + b * inverse[2], a * inverse[1] + b * inverse[3],
            c * inverse[0] + d * inverse[2], c * inverse[1] + d * inverse[3],
        ))
        normalized = min(entries, tuple(-value % modulus for value in entries))
        records.append(":".join((
            str(first_index), str(second_index), str(exponent), label,
            *(oracle.encode_affine(point) for point in points),
            *(str(hint) for hint in hints),
            *(encode(exponent, value) for value in normalized),
        )))
    return records
