#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Independent affine group-law oracles for all three internal x-only curve fields."""

import argparse
import hashlib

from generate_test_vectors import ROOT, fp2_prime_field_square_root, literal


ZERO = (0, 0)
ONE = (1, 0)
OUTPUT = ROOT / "src/commonTest/kotlin/ch/trancee/sqisign/ec/CurveVectors.kt"


class AffineOracle:
    def __init__(self, identifier, exponent, cofactor, width, response_bits, fixture_name):
        self.identifier = identifier
        self.exponent = exponent
        self.prime = cofactor * (1 << exponent) - 1
        self.width = width
        self.response_bits = response_bits
        self.fixture_name = fixture_name

    def add(self, left, right):
        return tuple((a + b) % self.prime for a, b in zip(left, right))

    def negate(self, value):
        return tuple(-a % self.prime for a in value)

    def subtract(self, left, right):
        return self.add(left, self.negate(right))

    def multiply(self, left, right):
        a, b = left
        c, d = right
        return (a * c - b * d) % self.prime, (a * d + b * c) % self.prime

    def inverse(self, value):
        a, b = value
        norm_inverse = pow((a * a + b * b) % self.prime, -1, self.prime)
        return a * norm_inverse % self.prime, -b * norm_inverse % self.prime

    def affine_add(self, coefficient, left, right):
        """Full affine points, using slopes rather than projective x-only formulas."""
        if left is None:
            return right
        if right is None:
            return left
        x1, y1 = left
        x2, y2 = right
        if x1 == x2:
            if y1 == self.negate(y2):
                return None
            slope_numerator = self.add(
                self.add(self.multiply((3, 0), self.multiply(x1, x1)),
                         self.multiply((2, 0), self.multiply(coefficient, x1))), ONE)
            slope = self.multiply(slope_numerator, self.inverse(self.multiply((2, 0), y1)))
        else:
            slope = self.multiply(self.subtract(y2, y1), self.inverse(self.subtract(x2, x1)))
        x3 = self.subtract(self.subtract(
            self.subtract(self.multiply(slope, slope), coefficient), x1), x2)
        return x3, self.subtract(self.multiply(slope, self.subtract(x1, x3)), y1)

    def affine_multiply(self, coefficient, point, scalar):
        result = None
        while scalar:
            if scalar & 1:
                result = self.affine_add(coefficient, result, point)
            point = self.affine_add(coefficient, point, point)
            scalar >>= 1
        return result

    def sample_field(self, label):
        block = hashlib.shake_256(label.encode()).digest(2 * self.width)
        return tuple(int.from_bytes(block[offset:offset + self.width], "little") % self.prime
                     for offset in (0, self.width))

    def sample_point(self, coefficient, label):
        for counter in range(1024):
            x = self.sample_field(f"{label} {counter}")
            rhs = self.multiply(x, self.add(self.add(
                self.multiply(x, x), self.multiply(coefficient, x)), ONE))
            y = fp2_prime_field_square_root(*rhs, self.prime)
            if y is not None and y != ZERO and x != ZERO:
                return x, y
        raise RuntimeError("Unable to generate a public curve point")

    def encode_field(self, value):
        return b"".join(component.to_bytes(self.width, "little") for component in value).hex()

    def encode_affine(self, point):
        return "-" if point is None else self.encode_field(point[0])

    def reference_root(self, value):
        """Algorithm 4.35 sign, using Python modular powers, not canonical roots."""
        real, imaginary = value
        delta = (real if imaginary == 0 else
                 pow((real * real + imaginary * imaginary) % self.prime,
                     (self.prime + 1) // 4, self.prime))
        total = (real + delta) % self.prime
        twice = 2 * total % self.prime
        power = pow(twice, (self.prime - 3) // 4, self.prime)
        r, s = total * power % self.prime, imaginary * power % self.prime
        root = (r, s) if (2 * r) ** 2 % self.prime == twice else (s, -r % self.prime)
        return root if self.multiply(root, root) == value else None

    def root_records(self):
        values = [ZERO, ONE, (4, 0), (self.prime - 1, 0), (2, 0), (self.prime - 5, 12)]
        values += [self.sample_field(f"SQIsign public reference root {index}") for index in range(12)]
        values += [self.multiply(value, value) for value in values[6:]]
        return [f"{self.encode_field(value)}:"
                f"{'-' if (root := self.reference_root(value)) is None else self.encode_field(root)}"
                for value in values]

    def base_residue_records(self):
        values = [0, 1, 2, self.prime - 1]
        values += [1 + counter * counter for counter in range(1, 17)]
        values += [self.sample_field(f"SQIsign public base residue {index}")[0] for index in range(4)]
        return [f"{self.encode_field((value, 0))}:"
                f"{int(pow(value, (self.prime - 1) // 2, self.prime) in (0, 1))}"
                for value in values]

    def reference_difference(self, coefficient, left, right):
        """Affine biquadratic choice, independently checked against both group-law results."""
        product = self.multiply(left[0], right[0])
        delta = self.subtract(left[0], right[0])
        conjugate_delta = delta[0], -delta[1] % self.prime
        gamma = self.multiply(conjugate_delta, conjugate_delta)
        bxx = self.multiply(self.subtract(product, ONE), self.subtract(product, ONE))
        bxz = self.add(self.multiply(self.add(product, ONE), self.add(left[0], right[0])),
                       self.multiply((2, 0), self.multiply(coefficient, product)))
        bzz = self.multiply(delta, delta)
        bxx, bxz, bzz = (self.multiply(value, gamma) for value in (bxx, bxz, bzz))
        root = self.reference_root(self.subtract(self.multiply(bxz, bxz), self.multiply(bxx, bzz)))
        if root is None:
            raise RuntimeError("Curve points unexpectedly have a nonsquare difference discriminant")
        result = self.multiply(self.subtract(bxz, root), self.inverse(bzz))
        minus = self.affine_add(coefficient, left, (right[0], self.negate(right[1])))
        plus = self.affine_add(coefficient, left, right)
        if result not in (minus[0], plus[0]):
            raise RuntimeError("Deterministic difference disagrees with the affine group law")
        return result

    def curve_records(self):
        bits = 8 * self.width
        maximum = (1 << bits) - 1
        scalars = (0, 1, 2, 3, 7, 255, 256, 257, (1 << self.response_bits) + 1,
                   1 << (bits - 1), maximum, int.from_bytes(b"\xaa" * self.width, "little"),
                   17, maximum, 0, 5)
        other_scalars = (0, 0, 1, 2, 6, 255, 256, 257, 1 << (bits - 1), 1 << (bits - 1),
                         maximum, int.from_bytes(b"\x55" * self.width, "little"),
                         17, maximum, 3, 0)
        records = []
        for index, scalar in enumerate(scalars):
            label = f"SQIsign {self.identifier} public affine curve vector {index}"
            coefficient = ZERO if index % 3 == 0 else self.sample_field(f"{label} coefficient")
            denominator = self.sample_field(f"{label} denominator")
            numerator = self.multiply(coefficient, denominator)
            left = self.sample_point(coefficient, f"{label} left")
            right = self.sample_point(coefficient, f"{label} right")
            if index in (12, 13):
                # Retained JVM workload: A=0, x(P)=2, Q=[2]P, and P-Q=-P.
                coefficient, denominator, numerator = ZERO, ONE, ZERO
                left = ((2, 0), fp2_prime_field_square_root(10, 0, self.prime))
                right = self.affine_add(coefficient, left, left)
            difference = self.affine_add(coefficient, left, (right[0], self.negate(right[1])))
            if difference is None or difference[0] == ZERO:
                raise RuntimeError("Unexpected exceptional public differential-addition input")
            coordinates = []
            for name, point in (("left", left), ("right", right), ("difference", difference)):
                scale = ONE if index in (12, 13) else self.sample_field(f"{label} {name} scale")
                coordinates.extend((self.multiply(point[0], scale), scale))
            records.append(":".join((
                *(self.encode_field(value) for value in (numerator, denominator, *coordinates)),
                scalar.to_bytes(self.width, "little").hex(),
                self.encode_affine(self.affine_add(coefficient, left, left)),
                self.encode_affine(self.affine_add(coefficient, left, right)),
                self.encode_affine(self.affine_multiply(coefficient, left, scalar)),
                self.encode_field(coefficient),
                self.encode_affine(self.affine_add(
                    coefficient, left, self.affine_multiply(coefficient, right, scalar))),
                other_scalars[index].to_bytes(self.width, "little").hex(),
                self.encode_affine(self.affine_add(
                    coefficient, self.affine_multiply(coefficient, left, scalar),
                    self.affine_multiply(coefficient, right, other_scalars[index]))),
                self.encode_field(self.reference_difference(coefficient, left, right)),
            )))
        return records

    def shared_difference_records(self):
        records = []
        for index in range(8):
            label = f"SQIsign {self.identifier} public shared difference {index}"
            coefficient = ZERO if index == 0 else self.sample_field(f"{label} coefficient")
            denominator = self.sample_field(f"{label} denominator")
            left = self.sample_point(coefficient, f"{label} left")
            right = self.sample_point(coefficient, f"{label} right")
            auxiliary = self.sample_point(coefficient, f"{label} auxiliary")
            if index == 0:
                left = ((2, 0), fp2_prime_field_square_root(10, 0, self.prime))
                right = self.affine_add(coefficient, left, left)
                auxiliary = left
            elif index == 2:
                right = left
            elif index == 3:
                left = None
            elif index == 4:
                right = None
            elif index == 5:
                left = right = None
            elif index == 6:
                right = left[0], self.negate(left[1])
            elif index == 7:
                # Identical quadratics do not determine which of the two roots to select.
                auxiliary = None
            translates = (
                self.affine_add(coefficient, left, auxiliary),
                self.affine_add(coefficient, right, auxiliary),
            )
            coordinates = []
            for offset, point in enumerate((left, right, *translates)):
                scale = self.sample_field(f"{label} scale {offset}")
                coordinates.extend((scale, ZERO) if point is None else
                                   (self.multiply(point[0], scale), scale))
            negated_right = None if right is None else (right[0], self.negate(right[1]))
            difference = self.affine_add(coefficient, left, negated_right)
            records.append(":".join((
                *(self.encode_field(value) for value in
                  (self.multiply(coefficient, denominator), denominator, *coordinates)),
                "!" if index == 7 else self.encode_affine(difference),
            )))
        return records


ORACLES = (
    AffineOracle("p324_3", 324, 3, 41, 196, "P324"),
    AffineOracle("p500_27", 500, 27, 64, 302, "P500"),
    AffineOracle("p664_17", 664, 17, 84, 400, "P664"),
)


def render():
    sections = []
    for oracle in ORACLES:
        rows = ",\n".join(f"    {literal(record)}" for record in oracle.curve_records())
        roots = ",\n".join(f"    {literal(record)}" for record in oracle.root_records())
        root_name = ("curveReferenceRootVectors" if oracle.fixture_name == "P324"
                     else f"curve{oracle.fixture_name}ReferenceRootVectors")
        sections.append(
            f"internal val curve{oracle.fixture_name}Vectors: List<String> = listOf(\n{rows},\n)\n\n"
            f"internal val {root_name}: List<String> = listOf(\n{roots},\n)\n")
        shared = ",\n".join(f"    {literal(record)}" for record in oracle.shared_difference_records())
        sections.append(
            f"internal val sharedDifference{oracle.fixture_name}Vectors: List<String> = listOf(\n{shared},\n)\n")
        residues = ",\n".join(f"    {literal(record)}" for record in oracle.base_residue_records())
        sections.append(
            f"internal val baseResidue{oracle.fixture_name}Vectors: List<String> = listOf(\n{residues},\n)\n")
    return ("// SPDX-License-Identifier: Apache-2.0\n"
            "// Generated by tools/generate_curve_vectors.py; do not edit.\n\n"
            "package ch.trancee.sqisign.ec\n\n" + "\n".join(sections))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    expected = render()
    if arguments.check:
        if not OUTPUT.exists() or OUTPUT.read_text() != expected:
            raise SystemExit("CurveVectors.kt differs; run python3 tools/generate_curve_vectors.py")
        print("Validated 48 affine curve vectors, 90 reference-sign roots, 24 shared differences and 72 base residues")
    else:
        OUTPUT.write_text(expected)


if __name__ == "__main__":
    main()
