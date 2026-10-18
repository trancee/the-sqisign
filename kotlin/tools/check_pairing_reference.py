#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Pairing/logarithm checks using the external binary built by check_curve_reference.py."""

import subprocess

from generate_pairing_vectors import discrete_log_records, pairing_curve_records, tate_records


def check_discrete_logs(binary, oracle):
    records = [record.split(":") for record in discrete_log_records(oracle)]
    inputs = [f"{fields[1]} {fields[2]} {fields[0]}" for fields in records]
    result = subprocess.run([str(binary), "--dlog"], input="\n".join(inputs) + "\n",
                            text=True, capture_output=True)
    if result.returncode != 0:
        raise SystemExit(f"C logarithm adapter failed: {result.stderr.strip()}")
    if result.stdout.splitlines() != [fields[3] for fields in records]:
        raise SystemExit(f"C/Python {oracle.identifier} discrete logarithm mismatch")
    valid = inputs[0].split()
    noncanonical = (oracle.prime.to_bytes(oracle.width, "little") + bytes(oracle.width)).hex()
    zero = oracle.encode_field((0, 0))
    one = oracle.encode_field((1, 0))
    invalid = [
        ["00", *valid[1:]], [noncanonical, *valid[1:]],
        [zero, *valid[1:]], [one, *valid[1:]],
        [valid[0], zero, valid[2]], [valid[0], oracle.encode_field((2, 0)), valid[2]],
        [valid[0], valid[1], "0"], [valid[0], valid[1], str(oracle.exponent + 1)],
        [valid[0], valid[1], "9" * 40], [valid[0], valid[1], "invalid"],
        [records[-1][1], valid[1], str(oracle.exponent - 1)],
        [valid[0], valid[1], str(oracle.exponent)], valid[:1], valid[:2],
    ]
    for fields in invalid:
        rejected = subprocess.run([str(binary), "--dlog"], input=" ".join(fields) + "\n",
                                  text=True, capture_output=True)
        if rejected.returncode != 1 or rejected.stdout or (
                "Invalid discrete logarithm operands or order" not in rejected.stderr):
            raise SystemExit("C discrete logarithm adapter did not reject malformed/nonmember input")
    print(f"Checked {oracle.identifier}: {len(records)} Python/C discrete logarithms")


def check_tate_pairings(binary, oracle):
    curves = pairing_curve_records(oracle)
    zero = oracle.encode_field((0, 0))
    inputs, expected = [], []
    for record in tate_records(oracle):
        curve, exponent, label, first, second, addition, value = record.split(":")
        if label not in ("generic", "inverse", "restricted", "swapped"):
            continue
        if first in ("-", zero) or second in ("-", zero) or addition == "-" or first == second:
            continue
        coefficient = curves[int(curve)].split(":")[0]
        inputs.append(" ".join((coefficient, first, second, addition, exponent)))
        expected.append(value)
    result = subprocess.run([str(binary), "--tate"], input="\n".join(inputs) + "\n",
                            text=True, capture_output=True)
    if result.returncode != 0:
        raise SystemExit(f"C Tate adapter failed: {result.stderr.strip()}")
    if result.stdout.splitlines() != expected:
        raise SystemExit(f"C/shifted-Miller {oracle.identifier} Tate mismatch")
    valid = inputs[0].split()
    noncanonical = (oracle.prime.to_bytes(oracle.width, "little") + bytes(oracle.width)).hex()
    singular = oracle.encode_field((2, 0))
    full_first = curves[0].split(":")[1]
    invalid = [
        valid[:1], valid[:4], [*valid[:4], "0"], [*valid[:4], str(oracle.exponent + 1)],
        [*valid[:4], "invalid"], [*valid[:4], "9" * 40],
        ["00", *valid[1:]], [noncanonical, *valid[1:]], [singular, *valid[1:]],
        [valid[0], zero, *valid[2:]], [*valid[:2], zero, *valid[3:]],
        [valid[0], full_first, *valid[2:]], [*valid[:3], valid[1], valid[4]],
        [*valid[:2], valid[1], *valid[3:]],
    ]
    for fields in invalid:
        rejected = subprocess.run([str(binary), "--tate"], input=" ".join(fields) + "\n",
                                  text=True, capture_output=True)
        if rejected.returncode != 1 or rejected.stdout or (
                "Invalid generic Tate operands or order" not in rejected.stderr):
            raise SystemExit("C Tate adapter did not reject malformed/out-of-domain input")
    print(f"Checked {oracle.identifier}: {len(expected)} C/shifted-Miller Tate pairings")
