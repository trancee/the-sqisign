#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Isogeny checks using the external binary built by check_curve_reference.py."""

import re
import subprocess

from generate_isogeny_vectors import chain_records, four_isogeny_records, normalization_records


REJECTION = "Invalid isogeny operands, kernel or length"
DEBUG_PRINT = re.compile(r"warning: .*?, file \S+, line \d+, function \S+\(\)\.\n", re.S)


def run(binary, mode, inputs):
    return subprocess.run([str(binary), mode], input="\n".join(inputs) + "\n", text=True, capture_output=True)


def compare(binary, mode, inputs, expected, name, oracle):
    result = run(binary, mode, inputs)
    if result.returncode != 0:
        raise SystemExit(f"C {name} adapter failed: {result.stderr.strip()}")
    if result.stdout.splitlines() != expected:
        raise SystemExit(f"C/affine {oracle.identifier} {name} mismatch")


def require_rejections(binary, mode, rejected_inputs, name):
    for fields in rejected_inputs:
        result = run(binary, mode, [" ".join(fields)])
        # Assert-enabled reference builds report special kernels through debug_print on stdout.
        output = DEBUG_PRINT.sub("", result.stdout)
        if result.returncode != 1 or output or REJECTION not in result.stderr:
            raise SystemExit(f"C {name} adapter accepted invalid input {fields}")


def malformed(oracle, valid):
    noncanonical = (oracle.prime.to_bytes(oracle.width, "little") + bytes(oracle.width)).hex()
    singular = oracle.encode_field((2, 0))
    return [valid[:1], ["00", *valid[1:]], [noncanonical, *valid[1:]], [singular, *valid[1:]],
            [valid[0], "g" * (4 * oracle.width), *valid[2:]]]


def check_isogenies(binary, oracle):
    normalizations = [record.split(":") for record in normalization_records(oracle)]
    accepted = [fields for fields in normalizations if fields[3] != "-"]
    compare(binary, "--normalize", [f"{fields[1]} {fields[2]}" for fields in accepted],
            [fields[3] for fields in accepted], "normalization", oracle)
    valid = accepted[0][1:3]
    require_rejections(binary, "--normalize",
                       [fields[1:3] for fields in normalizations if fields[3] == "-"] + malformed(oracle, valid),
                       "normalization")

    fours = [record.split(":") for record in four_isogeny_records(oracle)]
    compare(binary, "--four-isogeny", [" ".join(fields[:3]) for fields in fours],
            [":".join(fields[3:]) for fields in fours], "four-isogeny", oracle)
    valid = fours[0][:3]
    require_rejections(binary, "--four-isogeny",
                       [[valid[0], fields[2], valid[2]] for fields in normalizations if fields[3] == "-"]
                       + malformed(oracle, valid) + [valid[:2]], "four-isogeny")

    chains = [record.split(":") for record in chain_records(oracle)]
    accepted = [fields for fields in chains if fields[4] != "-"]
    compare(binary, "--isogeny-chain", [" ".join(fields[1:4]) for fields in accepted],
            [fields[4] for fields in accepted], "two-isogeny chain", oracle)
    valid = accepted[0][1:4]
    lengths = [[*valid[:2], value] for value in ("0", "7", str(oracle.exponent + 2), "invalid")]
    require_rejections(binary, "--isogeny-chain",
                       [fields[1:4] for fields in chains if fields[4] == "-"] + malformed(oracle, valid)
                       + lengths + [valid[:2]], "two-isogeny chain")
    print(f"Checked {oracle.identifier}: {len(normalizations)} normalizations, {len(fours)} four-isogeny "
          f"images and {len(chains)} two-isogeny chains against C")
