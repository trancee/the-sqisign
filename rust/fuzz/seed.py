#!/usr/bin/env python3
"""Seeds the corpora from the first KAT entries of each parameter set: corpus/open with `level || pk || sm`,
corpus/sign with `level || seed || reversed seed || msg`, so that inputs start long enough to hold both seeds."""
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent
KAT = ROOT.parent.parent / "KAT"
LEVELS = ["p324_3", "p500_27", "p664_17"]
PER_LEVEL = 4

corpus = {name: ROOT / "corpus" / name for name in ("open", "sign")}
for d in corpus.values():
    d.mkdir(parents=True, exist_ok=True)
for level, name in enumerate(LEVELS):
    (rsp,) = KAT.glob(f"PQCsignKAT_*_SQIsign_{name}.rsp")
    text = rsp.read_text()
    pks = re.findall(r"^pk = ([0-9A-F]+)$", text, re.M)
    sms = re.findall(r"^sm = ([0-9A-F]+)$", text, re.M)
    seeds = re.findall(r"^seed = ([0-9A-F]+)$", text, re.M)
    msgs = re.findall(r"^msg = ([0-9A-F]+)$", text, re.M)
    for i in range(PER_LEVEL):
        tag = bytes([level])
        (corpus["open"] / f"kat-{name}-{i}").write_bytes(tag + bytes.fromhex(pks[i]) + bytes.fromhex(sms[i]))
        seed = bytes.fromhex(seeds[i])
        (corpus["sign"] / f"kat-{name}-{i}").write_bytes(tag + seed + seed[::-1] + bytes.fromhex(msgs[i]))
for d in corpus.values():
    print(f"{len(list(d.iterdir()))} files in {d}")
