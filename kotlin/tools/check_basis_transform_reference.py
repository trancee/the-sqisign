#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compare known affine basis actions and generic changes with the pinned C binary."""

import subprocess

from basis_transform_oracle import correspondence_records
from generate_basis_change_vectors import basis_change_records
from generate_pairing_vectors import pairing_curves
from generate_torsion_ring_vectors import encode


def run_checked(binary, mode, inputs):
    result = subprocess.run([str(binary), mode], input="\n".join(inputs) + "\n",
                            text=True, capture_output=True)
    if result.returncode != 0:
        raise SystemExit(f"C {mode} failed: {result.stderr.strip()}")
    return result.stdout.splitlines()


def reject(binary, mode, inputs, reason):
    for fields in inputs:
        result = subprocess.run([str(binary), mode], input=" ".join(fields) + "\n",
                                text=True, capture_output=True)
        if result.returncode != 1 or result.stdout or reason not in result.stderr:
            raise SystemExit(f"C {mode} did not reject malformed/out-of-domain input")


def check_basis_actions(binary, oracle):
    inputs, expected = [], []
    curves = pairing_curves(oracle)
    for record in basis_change_records(oracle):
        fields = record.split(":")
        curve_index, exponent = int(fields[0]), int(fields[1])
        coefficient, full = curves[curve_index]
        source = [oracle.affine_multiply(coefficient, point, 1 << (oracle.exponent - exponent))
                  for point in full]
        prefix = [oracle.encode_field(coefficient),
                  *(oracle.encode_affine(point) for point in source), str(exponent)]
        a, b, c, d = (int.from_bytes(bytes.fromhex(value), "little") for value in fields[3:7])
        for left, right in ((a, c), (b, d), ((a - b) % (1 << exponent), (c - d) % (1 << exponent))):
            inputs.append(" ".join((*prefix, encode(exponent, left), encode(exponent, right))))
        expected.extend(fields[7:10])
    if run_checked(binary, "--basis-combination", inputs) != expected:
        raise SystemExit(f"C/Python {oracle.identifier} basis action mismatch")
    valid = inputs[0].split()
    noncanonical = (oracle.prime.to_bytes(oracle.width, "little") + bytes(oracle.width)).hex()
    invalid = [valid[:length] for length in (1, 4, 6)]
    invalid += [
        ["00", *valid[1:]], [noncanonical, *valid[1:]],
        [oracle.encode_field((2, 0)), *valid[1:]],
        [valid[0], valid[2], *valid[2:]],
        [*valid[:3], valid[1], *valid[4:]],
        [valid[0], oracle.encode_field((1, 1)), *valid[2:]],
        [*valid[:4], "0", *valid[5:]],
        [*valid[:4], str(oracle.exponent + 1), *valid[5:]],
        [*valid[:4], "invalid", *valid[5:]],
        [*valid[:4], "9" * 40, *valid[5:]],
        [*valid[:5], "02", valid[6]], [*valid[:6], "02"],
        [*valid[:5], "gg", valid[6]], [*valid[:6], valid[6] + "00"],
    ]
    reject(binary, "--basis-combination", invalid, "Invalid checked basis combination")
    print(f"Checked {oracle.identifier}: {len(expected)} Python/C basis-action coordinates")


def check_basis_changes(binary, oracle):
    curves = pairing_curves(oracle)
    inputs, expected = [], []
    outside_domain = []
    for record in basis_change_records(oracle):
        fields = record.split(":")
        curve_index, exponent = int(fields[0]), int(fields[1])
        coefficient, full = curves[curve_index]
        line = " ".join((oracle.encode_field(coefficient),
                         *(oracle.encode_affine(point) for point in full),
                         *fields[7:10], str(exponent)))
        if exponent == 1 or (exponent == oracle.exponent and fields[2] not in ("dense", "negativeDense")):
            if curve_index == 0 and fields[2] in ("binary0", "identity"):
                outside_domain.append(line.split())
            continue
        inputs.append(line)
        expected.append(fields[10:14])
    actual = run_checked(binary, "--basis-change", inputs)
    normalized = []
    for line, source in zip(actual, inputs, strict=True):
        exponent = int(source.split()[-1])
        entries = tuple(int.from_bytes(bytes.fromhex(value), "little") for value in line.split(":"))
        if len(entries) != 4 or any(value >= 1 << exponent for value in entries):
            raise SystemExit("C basis change returned noncanonical scalar entries")
        representative = min(entries, tuple(-value % (1 << exponent) for value in entries))
        normalized.append([encode(exponent, value) for value in representative])
    if normalized != expected:
        raise SystemExit(f"C/Python {oracle.identifier} normalized basis change mismatch")
    valid = inputs[0].split()
    invalid = [valid[:length] for length in (1, 4, 7)]
    invalid += [
        ["00", *valid[1:]], [oracle.encode_field((2, 0)), *valid[1:]],
        [valid[0], valid[2], *valid[2:]],
        [*valid[:5], valid[4], *valid[6:]],
        [*valid[:6], valid[4], valid[7]],
        [*valid[:7], "0"], [*valid[:7], str(oracle.exponent + 1)],
        [*valid[:7], "invalid"], [*valid[:7], "9" * 40],
        [valid[0], *valid[4:7], *valid[4:]],
    ]
    reject(binary, "--basis-change", [*invalid, *outside_domain],
           "Invalid or nongeneric basis-change operands")
    print(f"Checked {oracle.identifier}: {len(expected)} generic Python/C basis changes")


def check_correspondences(binary, oracle):
    records = [record.split(":") for record in correspondence_records(oracle)
               if record.split(":")[3] == "mixedBranches"]
    for fields in records:
        exponent = int(fields[2])
        modulus = 1 << exponent
        canonical = []
        for index in fields[:2]:
            coefficient = pairing_curves(oracle)[int(index)][0]
            values = run_checked(binary, "--canonical-basis", [
                f"{oracle.encode_field(coefficient)} {oracle.exponent} {exponent}"])
            if len(values) != 1 or len(values[0].split(":")) != 4:
                raise SystemExit("C correspondence returned an invalid canonical basis")
            canonical.append((oracle.encode_field(coefficient), values[0].split(":")))
        first_curve, first = canonical[0]
        second_curve, second = canonical[1]
        first_change = run_checked(binary, "--basis-change", [
            " ".join((first_curve, *first[1:], *fields[4:7], str(exponent)))])
        if len(first_change) != 1 or len(first_change[0].split(":")) != 4:
            raise SystemExit("C correspondence returned an invalid first matrix")
        a, b, c, d = (
            int.from_bytes(bytes.fromhex(value), "little") for value in first_change[0].split(":"))
        factor = pow((a * d - b * c) % modulus, -1, modulus)
        a, b, c, d = (value * factor % modulus for value in (d, -b, -c, a))
        aligned = run_checked(binary, "--basis-combination", [
            " ".join((second_curve, *fields[7:10], str(exponent),
                      encode(exponent, left), encode(exponent, right)))
            for left, right in ((a, c), (b, d), ((a - b) % modulus, (c - d) % modulus))
        ])
        if len(aligned) != 3:
            raise SystemExit("C correspondence returned an incomplete aligned basis")
        final = run_checked(binary, "--basis-change", [
            " ".join((second_curve, *second[1:], *aligned, str(exponent)))])
        if len(final) != 1 or len(final[0].split(":")) != 4:
            raise SystemExit("C correspondence returned an invalid final matrix")
        entries = tuple(int.from_bytes(bytes.fromhex(value), "little")
                        for value in final[0].split(":"))
        normalized = min(entries, tuple(-value % modulus for value in entries))
        if [encode(exponent, value) for value in normalized] != fields[12:16] or (
                [first[0], second[0]] != fields[10:12]):
            raise SystemExit(f"C/Python {oracle.identifier} hinted correspondence mismatch")
    print(f"Checked {oracle.identifier}: {len(records)} generic composed C hinted correspondence")
