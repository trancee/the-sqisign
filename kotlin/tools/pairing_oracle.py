#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Independent Python extension-field exponentiation for pairing/logarithm evidence."""

from generate_curve_vectors import ONE, ZERO


class EvaluationIntersection(ValueError):
    """The chosen offline evaluation divisor meets a Miller zero or pole."""


def power(oracle, value, exponent):
    result = ONE
    while exponent:
        if exponent & 1:
            result = oracle.multiply(result, value)
        value = oracle.multiply(value, value)
        exponent >>= 1
    return result


def primitive_root(oracle):
    for index in range(64):
        sample = oracle.sample_field(f"public torsion root {oracle.identifier} {index}")
        root = power(oracle, sample, (oracle.prime * oracle.prime - 1) >> oracle.exponent)
        if power(oracle, root, 1 << (oracle.exponent - 1)) == oracle.negate(ONE):
            return root
    raise RuntimeError("Offline primitive-root search bound exhausted")


def miller_evaluate(oracle, coefficient, point, exponent, evaluation):
    value, current = ONE, point
    for _ in range(exponent):
        value = oracle.multiply(value, value)
        if current is None:
            continue
        doubled = oracle.affine_add(coefficient, current, current)
        if current[1] == ZERO:
            factor = oracle.subtract(evaluation[0], current[0])
        else:
            slope = oracle.multiply(
                oracle.add(oracle.add(
                    oracle.multiply((3, 0), oracle.multiply(current[0], current[0])),
                    oracle.multiply((2, 0), oracle.multiply(coefficient, current[0]))), ONE),
                oracle.inverse(oracle.multiply((2, 0), current[1])),
            )
            line = oracle.subtract(
                oracle.subtract(evaluation[1], current[1]),
                oracle.multiply(slope, oracle.subtract(evaluation[0], current[0])),
            )
            vertical = oracle.subtract(evaluation[0], doubled[0])
            if vertical == ZERO:
                raise EvaluationIntersection("Miller evaluation meets a pole")
            factor = oracle.multiply(line, oracle.inverse(vertical))
        if factor == ZERO:
            raise EvaluationIntersection("Miller evaluation meets a zero")
        value = oracle.multiply(value, factor)
        current = doubled
    if current is not None:
        raise ValueError("Miller first argument is not killed by the requested power")
    return value


def tate(oracle, coefficient, exponent, first, second):
    """Independent affine Miller evaluation on a shifted degree-zero divisor."""
    if first is None or second is None:
        return ONE
    for index in range(32):
        shift = oracle.sample_point(coefficient, f"public Miller evaluation shift {index}")
        translated = oracle.affine_add(coefficient, second, shift)
        if translated is None:
            continue
        try:
            numerator = miller_evaluate(oracle, coefficient, first, exponent, translated)
            denominator = miller_evaluate(oracle, coefficient, first, exponent, shift)
        except EvaluationIntersection:
            continue
        ratio = oracle.multiply(numerator, oracle.inverse(denominator))
        return power(oracle, ratio, (oracle.prime * oracle.prime - 1) >> exponent)
    raise RuntimeError("Offline Miller evaluation-shift bound exhausted")
