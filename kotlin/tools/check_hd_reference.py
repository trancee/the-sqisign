#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check dimension-two chain vectors against the checksum-pinned C reference.

The C adapter replays every public KAT signature through `protocols_verify`, captures each
verification chain, and confirms the checked-in kernel fixture. Chain records are then run through
the same C chain entry points, comparing codomains, images and the first rejection diagnostic.
"""

import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import sys
import tempfile

from check_curve_reference import CURVE_REFERENCE_FILES
from check_unsigned_reference import REFERENCE_COMMIT, REFERENCE_FILES
from generate_curve_vectors import ORACLES, ROOT
import generate_hd_vectors
from generate_hd_vectors import FIXTURE, KAT_FILES, chain_records


HD_REFERENCE_FILES = {
    "src/hd/ref/include/hd.h": "7dd3fa944281726e52ecde0fb687442a6bf2ad5b324b01cfa79d4980dc86e550",
    "src/hd/ref/lvlx/gluing.c": "4e33062d338ed81073aaeb110326e45becc81f1499fd116480639c51cc99b2a5",
    "src/hd/ref/lvlx/gluing.h": "124e51a8a6ed9d7bc4fe6a0f880f062b1e301c8c0f01fdab1afa9295cb4c9fec",
    "src/hd/ref/lvlx/hd.c": "dbef443e3c5c815895d14daa308304e7b82fb1589a09df7fa97cf31126a06184",
    "src/hd/ref/lvlx/splitting.c": "6e56d1345e01dde28b5c24e186331419902724cdadac00fa28218885fccd838e",
    "src/hd/ref/lvlx/splitting.h": "6156d586470cf1f87f3cb6399f255775d79662cc894ef4e06f6cf2aaf3bd4795",
    "src/hd/ref/lvlx/theta_isogenies.c": "6cfd8e5e397cc611e62786183038a2214ac4bb9bd125f97623c517ef6248cf9f",
    "src/hd/ref/lvlx/theta_structure.c": "c24ee2f1aec7e95fd80d42fc7d6e9a55dced3c0b5e71e4ec9b8b22b065f2679d",
    "src/hd/ref/lvlx/theta_structure.h": "32726fb8eca1b2ff5db8b98746f0770a5c7a042bf910cfda1595fd11a48e2efb",
    "src/verification/ref/include/verification.h": "09f721a6cc7fec3b871f9bbc86f90659746a05a30bd48d37a492a645bb4d719c",
    "src/verification/ref/lvlx/common.c": "e4095504dc9976b347f80d1a9c9fcf498bb2b4042e5647e4fe7ec26a3a037dbf",
    "src/verification/ref/lvlx/encode_public.c": "2065548280633e7205af563094790d8a8e7c5a75cfe2417e5f81d24fd6c1f1cf",
    "src/verification/ref/lvlx/verify.c": "b1f3f7ea681cd1ed25d122bcb753a215bef147c4e8756edc0da0e2c55423a665",
    "src/common/generic/fips202.c": "ea22d7663ddf47cd83ed04365a21e68fb569ada7e3b044cc9382e299e3b7b303",
    "src/common/generic/include/fips202.h": "e3d925c6b53505dde795bd4798a8535d23bda2d15d72bdd5a96b83dc29a4949c",
    "src/precomp/ref/p324_3/include/encoded_sizes.h": "caeb092b3ee1158f36b7fe6acbbede91bd3ac79f531d272349c0b36550750b6b",
    "src/precomp/ref/p500_27/include/encoded_sizes.h": "5e6b6527a6f6a9a46001593e851f34e5c16963cf534d1a373ad9a6a0d21722f3",
    "src/precomp/ref/p664_17/include/encoded_sizes.h": "37f1242729df34d395d7ac0f4dd1273300111b0d9b8386ae05075c389f64855d",
}
# Assert-enabled reference builds print debug diagnostics to stdout; the first non-order one is the cause.
WARNING = re.compile(r"warning: (.*?), file \S+, line \d+, function \S+\(\)\.")
WARNING_LINE = re.compile(WARNING.pattern + r"\n")
ORDER_DIAGNOSTIC = re.compile(r"does not have (correct )?order")
REASONS = {
    "Not all kernel points are of the right torsion": "gluing-torsion",
    "Not all kernel points are independant": "gluing-torsion",
    "gluing failed TT1.t or TT2.t is not zero": "gluing-theta",
    "unexpected zeroes in gluing theta point.": "gluing-zero",
    "evaluating at a couple_point with no zero coordinate": "gluing-evaluation",
    "failure of the codomain computation during the HD chain": "generic",
    "a coordinate of the splitting coordinates is zero": "splitting-zero",
    "kernel did not generate an isogeny between elliptic products": "splitting-product",
    "pushed points are not a product of elliptic curve points ": "point-product",
    "failure during computation of the normal isomorphism of curve E1": "first-curve",
    "failure during computation of the normal isomorphism of curve E2": "second-curve",
}
INVALID = "Invalid or incomplete chain operands"


def kat_records(oracle):
    records, current = [], {}
    for line in (ROOT.parent / "KAT" / KAT_FILES[oracle.identifier]).read_text().splitlines():
        if " = " in line:
            key, value = line.split(" = ")
            current[key] = value
            if key == "sm":
                records.append(current)
                current = {}
    return records


def compile_reference(repository, oracle, level, directory):
    includes = ("include", "src/mp/ref/include", "src/common/generic/include", "src/gf/ref/include",
                f"src/precomp/ref/{oracle.identifier}/include", "src/ec/ref/include", "src/hd/ref/include",
                "src/verification/ref/include")
    binary = Path(directory) / f"hd-reference-{oracle.identifier}"
    subprocess.run([
        "cc", "-std=c11", "-O2", "-ffunction-sections", "-fdata-sections", "-DRADIX_64",
        f"-DSQISIGN_VARIANT={oracle.identifier}", "-DSQISIGN_BUILD_TYPE_REF", f"-DREFERENCE_LEVEL={level}",
        *(f"-I{repository / include}" for include in includes), str(ROOT / "tools/hd_reference.c"),
        "-Wl,-dead_strip" if sys.platform == "darwin" else "-Wl,--gc-sections", "-o", str(binary),
    ], check=True)
    return binary


def capture(binary, oracle):
    """Replays all KAT signatures; every one must verify through exactly one captured chain."""
    lines = []
    for record in kat_records(oracle):
        signed, length = bytes.fromhex(record["sm"]), int(record["mlen"])
        message = signed[len(signed) - length:].hex() or "-"
        lines.append(f"{record['pk']} {signed[:len(signed) - length].hex()} {message}")
    result = subprocess.run([str(binary), "--verify"], input="\n".join(lines) + "\n", text=True,
                            capture_output=True, check=True)
    output = WARNING_LINE.sub("", result.stdout).split("\n")
    chains = [line.split() for line in output if line.startswith("chain ")]
    if [line for line in output if line.startswith("verify")] != ["verify 1"] * len(lines) or \
            len(chains) != len(lines) or any(fields[8:] != ["1", fields[8 + 1], "-"] for fields in chains):
        raise SystemExit(f"C {oracle.identifier} KAT verification capture failed")
    return {index: fields[1:8] + [fields[9]] for index, fields in enumerate(chains)}


def fixture_lines(captures):
    return [f"{identifier} {index} {' '.join(chains[index])}"
            for identifier, chains in captures.items()
            for index in generate_hd_vectors.FIXTURE_RECORDS[identifier]]


def reason(stdout):
    for message in WARNING.findall(stdout):
        if not ORDER_DIAGNOSTIC.search(message):
            return REASONS[message]
    return None


def check_chains(binary, oracle):
    records = [record.split(":") for record in chain_records(oracle)]
    for fields in records:
        mode, length, inputs, points, outcome = fields[1], fields[2], fields[3:9], fields[9], fields[10]
        pairs = [pair.split("/") for pair in points.split(";")] if points else []
        line = " ".join([mode, length, *inputs, str(len(pairs)), *(x for pair in pairs for x in pair)])
        result = subprocess.run([str(binary), "--chain"], input=line + "\n", text=True, capture_output=True)
        if result.returncode != 0:
            raise SystemExit(f"C chain adapter failed: {result.stderr.strip()}")
        verdict = WARNING_LINE.sub("", result.stdout).split()
        if "," in outcome or outcome == "":
            expected = ["1", *outcome.split(",")]
            if verdict != expected or reason(result.stdout) is not None:
                raise SystemExit(f"C/oracle {oracle.identifier} {fields[0]} mode {mode} chain mismatch")
        elif outcome == "degenerate-image":
            if verdict[0] != "1" or "*" not in verdict:
                raise SystemExit(f"C {oracle.identifier} {fields[0]} did not return a degenerate image")
        elif verdict != ["0"] or reason(result.stdout) != outcome:
            raise SystemExit(f"C/oracle {oracle.identifier} {fields[0]} mode {mode} rejection mismatch: "
                             f"{reason(result.stdout)} != {outcome}")
    valid = next(fields for fields in records if "," in fields[10])
    base = [valid[1], valid[2], *valid[3:9], "0"]
    invalid = [["3", *base[1:]], ["-2", *base[1:]], [base[0], "1", *base[2:]],
               [base[0], str(oracle.exponent - 1), *base[2:]], [*base[:2], "gg", *base[3:]],
               [*base[:2], (oracle.prime.to_bytes(oracle.width, "little") + bytes(oracle.width)).hex(), *base[3:]],
               [*base[:-1], "9"], [*base[:-1], "-1"], [*base[:-1], "1"], [*base[:-1], "1", "-"], base[:4]]
    for fields in invalid:
        result = subprocess.run([str(binary), "--chain"], input=" ".join(fields) + "\n", text=True,
                                capture_output=True)
        if result.returncode != 1 or result.stdout or INVALID not in result.stderr:
            raise SystemExit(f"C chain adapter accepted invalid input {fields[:3]}")
    for arguments, code in (([], 2), (["--verify"], 1)):
        stdin = "00 00 -\n" if arguments else ""
        result = subprocess.run([str(binary), *arguments], input=stdin, text=True, capture_output=True)
        if result.returncode != code or result.stdout:
            raise SystemExit("C adapter accepted invalid usage or verification records")
    return len(records)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write-fixture", action="store_true")
    arguments = parser.parse_args()
    repository = ROOT.parent
    for path, expected in (REFERENCE_FILES | CURVE_REFERENCE_FILES | HD_REFERENCE_FILES).items():
        if hashlib.sha256((repository / path).read_bytes()).hexdigest() != expected:
            raise SystemExit(f"{path} differs from pinned C reference {REFERENCE_COMMIT}; review before rebasing")
    with tempfile.TemporaryDirectory(prefix="sqisign-hd-") as directory:
        binaries = {oracle.identifier: compile_reference(repository, oracle, level, directory)
                    for oracle, level in zip(ORACLES, (1, 3, 5), strict=True)}
        captures = {oracle.identifier: capture(binaries[oracle.identifier], oracle) for oracle in ORACLES}
        expected = "\n".join(fixture_lines(captures)) + "\n"
        if arguments.write_fixture:
            FIXTURE.write_text(expected)
            return
        if FIXTURE.read_text() != expected:
            raise SystemExit("HD kernel fixture differs from the C KAT capture")
        generate_hd_vectors.check()
        for oracle in ORACLES:
            count = check_chains(binaries[oracle.identifier], oracle)
            print(f"Checked {oracle.identifier}: {len(captures[oracle.identifier])} KAT verification chains and "
                  f"{count} chain records against C {REFERENCE_COMMIT}")


if __name__ == "__main__":
    main()
