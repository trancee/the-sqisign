#!/usr/bin/env python3
"""Fail if the shipped build has a conditional branch in constant-time code.

Builds `sqisign-capi` as the Kotlin build ships it (`kotlin/native/rust.cmake`:
fat LTO, one codegen unit) for the host or a given target, disassembles it
with the pinned toolchain's `llvm-objdump`, and attributes every conditional
branch to its source line. A branch on a line of `GATED` fails the check.

Usage (from `rust/`): `python3 scripts/ct_branches.py [--target TRIPLE]`.
Recognises AArch64 and x86-64 code only.
"""

import argparse
import collections
import os
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "crates" / "sqisign" / "src"

# Code whose control flow C keeps independent of secret data (ADR 0004 item
# 16): whole files, or `file::function` bodies whose loop headers run over
# public bounds.
GATED = [
    "gf/fp.rs",
    "gf/limbs.rs",
    "mp/bits.rs",
    "mp/ct.rs::div128",
    "mp/mod.rs::cswap",
    "quat/lll/gram.rs",
    "quat/lll/lehmer.rs",
    "quat/lll/mk_select.rs",
    "quat/lll/word.rs",
    "quat/lll/zi_inner.rs",
]

BRANCH = re.compile(r"^\s*[0-9a-f]+:\s+(b\.\w+|cbn?z|tbn?z|j(?!mp\b)[a-z]+)\s")
LOCATION = re.compile(r"^; (.*\.rs):(\d+)")
LOOP = re.compile(r"^\s*(for|while|loop)\b")


def function_lines(path, name):
    """The lines of `fn name`'s body, without loop headers."""
    lines = path.read_text().splitlines()
    for start, line in enumerate(lines):
        if re.match(rf"^(\s*)(pub(\([^)]*\))? )?fn {name}\b", line):
            indent = len(line) - len(line.lstrip())
            for end in range(start + 1, len(lines)):
                if lines[end] == " " * indent + "}":
                    return {
                        n + 1
                        for n in range(start + 1, end)
                        if not LOOP.match(lines[n])
                    }
    sys.exit(f"ct_branches: no fn {name} in {path}")


def gated_lines():
    gated = {}
    for spec in GATED:
        rel, _, name = spec.partition("::")
        path = SRC / rel
        if not path.is_file():
            sys.exit(f"ct_branches: no file {path}")
        gated[str(path)] = function_lines(path, name) if name else None
    return gated


def build(target):
    env = dict(
        os.environ,
        CARGO_PROFILE_RELEASE_LTO="fat",
        CARGO_PROFILE_RELEASE_CODEGEN_UNITS="1",
        CARGO_PROFILE_RELEASE_DEBUG="line-tables-only",
    )
    out = ROOT / "target" / "ct-branches"
    cmd = ["cargo", "build", "--release", "--locked", "-p", "sqisign-capi"]
    cmd += ["--target-dir", str(out)]
    if target:
        cmd += ["--target", target]
    subprocess.run(cmd, cwd=ROOT, env=env, check=True)
    lib = out / (target or "") / "release" / "libsqisign_capi.a"
    if not lib.is_file():
        sys.exit(f"ct_branches: no {lib}")
    return lib


def rustc(*args):
    return subprocess.run(
        ["rustc", *args], cwd=ROOT, capture_output=True, text=True, check=True
    ).stdout.strip()


def objdump():
    sysroot = pathlib.Path(rustc("--print", "sysroot"))
    tool = sysroot / "lib" / "rustlib" / rustc("--print", "host-tuple") / "bin" / "llvm-objdump"
    if not tool.is_file():
        sys.exit(f"ct_branches: no {tool}; install the llvm-tools-preview component")
    return tool


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--target", help="target triple (default: host)")
    args = parser.parse_args()

    gated = gated_lines()
    lib = build(args.target)
    disassembly = subprocess.run(
        [objdump(), "-d", "-l", "--no-show-raw-insn", lib],
        capture_output=True,
        text=True,
        check=True,
    ).stdout

    hits = collections.Counter()
    checked = collections.Counter()
    location = None
    for line in disassembly.splitlines():
        match = LOCATION.match(line)
        if match:
            location = (os.path.realpath(match[1]), int(match[2]))
            continue
        if re.match(r"^[0-9a-f]+ <", line):
            location = None
        elif location and location[0] in gated:
            lines = gated[location[0]]
            if lines is None or location[1] in lines:
                checked[location[0]] += 1
                if BRANCH.match(line):
                    hits[location] += 1

    missing = [os.path.relpath(f, ROOT) for f in gated if not checked[f]]
    if missing:
        sys.exit("ct_branches: no code attributed to " + ", ".join(missing))
    for (path, number), count in sorted(hits.items()):
        print(f"{os.path.relpath(path, ROOT)}:{number}: {count} conditional branch(es)")
    if hits:
        sys.exit(f"ct_branches: {len(hits)} gated line(s) branch in {lib}")
    print(f"ct_branches: no conditional branch on gated lines in {lib}")


if __name__ == "__main__":
    main()
