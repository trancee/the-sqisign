#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check the verification mutation corpus against the checksum-pinned C `protocols_verify`.

Each corpus line patches one public KAT record and pins the C verdict:
`<identifier> <count> <target> <offset> <replacement> <verdict>`, where target is `pk`, `sig`,
`msg`, or `-` for the unmodified record, and replacement is hex written over bytes from offset.
Every patched encoding stays canonical, so the Kotlin verifier must reach the same verdict.
"""

import argparse
import hashlib
import random
import subprocess
import tempfile

from check_curve_reference import CURVE_REFERENCE_FILES
from check_hd_reference import HD_REFERENCE_FILES, WARNING_LINE, compile_reference, kat_records
from check_unsigned_reference import REFERENCE_COMMIT, REFERENCE_FILES
from generate_curve_vectors import ORACLES, ROOT

FIXTURE = ROOT / "tools/verify_mutations.txt"
RECORDS = range(4)
SEED = 0x5157_1597
CHALLENGE_BYTES = {"p324_3": 16, "p500_27": 24, "p664_17": 32}


class Layout:
    def __init__(self, oracle):
        self.prime = oracle.prime
        self.field = oracle.width
        self.extension = 2 * oracle.width
        self.scalar_bits = oracle.response_bits + 2
        self.response = (self.scalar_bits + 7) // 8
        self.challenge = CHALLENGE_BYTES[oracle.identifier]
        self.challenge_start = self.extension + 4 * self.response
        self.hints = self.challenge_start + self.challenge

    def entry(self, signature, index):
        start = self.extension + index * self.response
        return int.from_bytes(signature[start:start + self.response], "little")

    def scalar(self, value):
        return (value % (1 << self.scalar_bits)).to_bytes(self.response, "little").hex()

    def coefficient(self, real):
        return (real % self.prime).to_bytes(self.field, "little").hex() + bytes(self.field).hex()


def mutations(layout, record, generator):
    """Yields `(target, offset, replacement)` patches that keep every encoding canonical."""
    signature = bytes.fromhex(record["sm"])[:layout.hints + 2]
    public_key = bytes.fromhex(record["pk"])
    entries = [layout.entry(signature, index) for index in range(4)]
    yield "-", 0, "-"
    yield "msg", 0, f"{bytes.fromhex(record['msg'])[0] ^ 1:02x}"
    yield "sig", layout.challenge_start, f"{signature[layout.challenge_start] ^ 1:02x}"
    yield "sig", layout.hints, f"{signature[layout.hints] ^ 1:02x}"
    yield "sig", layout.hints + 1, f"{signature[layout.hints + 1] ^ 1:02x}"
    yield "pk", layout.extension, f"{public_key[layout.extension] ^ 1:02x}"
    yield "sig", layout.extension + 3 * layout.response, layout.scalar(entries[3] + 4)
    yield "sig", layout.extension, "".join(layout.scalar(-entry) for entry in entries)
    yield "sig", layout.extension, bytes(4 * layout.response).hex()
    yield "sig", 0, public_key[:layout.extension].hex()
    yield "sig", 0, layout.coefficient(2)
    yield "sig", 0, layout.coefficient(generator.randrange(3, layout.prime))
    yield "pk", 0, layout.coefficient(-2)
    bit = generator.randrange(8 * (layout.challenge + 2))
    offset = layout.challenge_start + bit // 8
    yield "sig", offset, f"{signature[offset] ^ (1 << bit % 8):02x}"


def patched(record, layout, target, offset, replacement):
    encodings = {"pk": bytes.fromhex(record["pk"]), "sig": bytes.fromhex(record["sm"])[:layout.hints + 2],
                 "msg": bytes.fromhex(record["msg"])}
    if target != "-":
        value = bytearray(encodings[target])
        patch = bytes.fromhex(replacement)
        value[offset:offset + len(patch)] = patch
        encodings[target] = bytes(value)
    return encodings


def corpus(oracle):
    layout, generator = Layout(oracle), random.Random(f"{SEED}:{oracle.identifier}")
    records = kat_records(oracle)
    return [(count, *mutation, patched(records[count], layout, *mutation))
            for count in RECORDS for mutation in mutations(layout, records[count], generator)]


def verdicts(binary, cases):
    lines = [f"{case['pk'].hex()} {case['sig'].hex()} {case['msg'].hex() or '-'}" for *_, case in cases]
    result = subprocess.run([str(binary), "--verify"], input="\n".join(lines) + "\n", text=True,
                            capture_output=True, check=True)
    output = [line for line in WARNING_LINE.sub("", result.stdout).split("\n") if line.startswith("verify ")]
    if len(output) != len(lines):
        raise SystemExit("C verification replay returned an incomplete verdict list")
    return [line.split()[1] for line in output]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write-fixture", action="store_true")
    arguments = parser.parse_args()
    repository = ROOT.parent
    for path, expected in (REFERENCE_FILES | CURVE_REFERENCE_FILES | HD_REFERENCE_FILES).items():
        if hashlib.sha256((repository / path).read_bytes()).hexdigest() != expected:
            raise SystemExit(f"{path} differs from pinned C reference {REFERENCE_COMMIT}; review before rebasing")
    lines = []
    with tempfile.TemporaryDirectory(prefix="sqisign-verify-") as directory:
        for oracle, level in zip(ORACLES, (1, 3, 5), strict=True):
            cases = corpus(oracle)
            results = verdicts(compile_reference(repository, oracle, level, directory), cases)
            lines += [f"{oracle.identifier} {count} {target} {offset} {replacement} {verdict}"
                      for (count, target, offset, replacement, _), verdict in zip(cases, results, strict=True)]
            if any(verdict != "1" for (_, target, *_), verdict in zip(cases, results) if target == "-"):
                raise SystemExit(f"C rejected an unmodified {oracle.identifier} KAT record")
    expected = "\n".join(lines) + "\n"
    if arguments.write_fixture:
        FIXTURE.write_text(expected)
        return
    if FIXTURE.read_text() != expected:
        raise SystemExit("Verification mutation corpus differs from the C verdicts")
    print(f"Checked {len(lines)} verification corpus verdicts against C {REFERENCE_COMMIT}")


if __name__ == "__main__":
    main()
