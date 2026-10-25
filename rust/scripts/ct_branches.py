#!/usr/bin/env python3
"""Fail if the shipped build has a conditional branch in constant-time code.

Builds `sqisign-capi` as the Kotlin build ships it (`kotlin/native/rust.cmake`:
fat LTO, one codegen unit) for the host or a given target, disassembles it
with the pinned toolchain's `llvm-objdump`, and attributes every conditional
branch to its source line. A branch on a line of `GATED` fails the check;
standard library arithmetic inlined there counts for the gated line.

Usage (from `rust/`): `python3 scripts/ct_branches.py [--target TRIPLE]`.
Recognises AArch64, 32-bit ARM/Thumb and x86 code.
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

# Loops the compiler keeps rolled on a target, by triple prefix: the back-edge
# (a backward conditional branch on the public loop counter) is attributed to
# the body. Accepted only here, after inspecting the disassembly.
BACK_EDGES = {
    # Limb loop: compares the byte offset with the constant length.
    "armv7": ["mp/mod.rs::cswap"],
}

# AArch64 `b.cond`, `cbz`, `tbz`; ARM/Thumb `bcond` (with `.w`/`.n` width;
# a bare `b.w` is unconditional); x86 `jcc`.
BRANCH = re.compile(
    r"^\s*[0-9a-f]+:\s+(cbn?z|tbn?z"
    r"|b\.?(eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le)(\.[wn])?"
    r"|j(?!mp\b)[a-z]+)\s"
)
TARGET = re.compile(r"^\s*([0-9a-f]+):\s+\S+\s+(?:\S+,\s*)?0x([0-9a-f]+)\b")
LOCATION = re.compile(r"^; (.*\.rs):(\d+)")
# Standard library integer arithmetic (`carrying_add`, `overflowing_sub`...)
# as the pinned toolchain's line tables name it. Its other inlined code
# (iterators, slices) is loop control over public bounds.
STD_ARITH = re.compile(r"^/rustc/[0-9a-f]+/library/core/src/num/")
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


def spec_lines(spec):
    """The path and lines (`None`: whole file) of a `GATED` entry."""
    rel, _, name = spec.partition("::")
    path = SRC / rel
    if not path.is_file():
        sys.exit(f"ct_branches: no file {path}")
    return str(path), function_lines(path, name) if name else None


def gated_lines():
    return dict(spec_lines(spec) for spec in GATED)


def back_edge_lines(triple):
    """The `(path, line)` pairs whose backward branches are accepted."""
    lines = set()
    for prefix, specs in BACK_EDGES.items():
        if triple.startswith(prefix):
            for spec in specs:
                path, numbers = spec_lines(spec)
                lines |= {(path, n) for n in numbers}
    return lines


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
    back_edges = back_edge_lines(args.target or rustc("--print", "host-tuple"))
    lib = build(args.target)
    disassembly = subprocess.run(
        [objdump(), "-d", "-l", "--no-show-raw-insn", lib],
        capture_output=True,
        text=True,
        check=True,
    ).stdout

    hits = collections.Counter()
    accepted = 0
    checked = collections.Counter()
    location = None
    for line in disassembly.splitlines():
        match = LOCATION.match(line)
        if match:
            # Standard library arithmetic carries its own line, so a branch the
            # backend makes of it (on the carry of a `carrying_add`) counts for
            # the crate line it was inlined into, the last one seen.
            if location is None or not STD_ARITH.match(match[1]):
                location = (os.path.realpath(match[1]), int(match[2]))
            continue
        if re.match(r"^[0-9a-f]+ <", line):
            location = None
        elif location and location[0] in gated:
            lines = gated[location[0]]
            if lines is None or location[1] in lines:
                checked[location[0]] += 1
                if not BRANCH.match(line):
                    continue
                jump = TARGET.match(line)
                if location in back_edges and jump and int(jump[2], 16) < int(jump[1], 16):
                    accepted += 1
                else:
                    hits[location] += 1

    missing = [os.path.relpath(f, ROOT) for f in gated if not checked[f]]
    if missing:
        sys.exit("ct_branches: no code attributed to " + ", ".join(missing))
    for (path, number), count in sorted(hits.items()):
        print(f"{os.path.relpath(path, ROOT)}:{number}: {count} conditional branch(es)")
    if accepted:
        print(f"ct_branches: {accepted} accepted loop back-edge(s) (BACK_EDGES)")
    if hits:
        sys.exit(f"ct_branches: {len(hits)} gated line(s) branch in {lib}")
    print(f"ct_branches: no conditional branch on gated lines in {lib}")


if __name__ == "__main__":
    main()
