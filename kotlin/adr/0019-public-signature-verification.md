# ADR 0019: Public signature verification

**Status:** Accepted
**Date:** 2026-10-20

## Context

Milestone 5 is specification algorithm 3.24 and the pinned
`src/verification/ref` (`protocols_verify`). The earlier milestones give every
building block:

- canonical wire parsing (`WireFormat`);
- typed Montgomery curves and hint-reconstructed torsion bases (ADR 0011);
- torsion matrices and their actions (ADRs 0012 and 0015);
- normalized two-isogeny chains (ADR 0017);
- verify-mode theta chains (ADR 0018);
- the challenge hash.

What remains is to compose them, choose the public contract, and show that the
composition agrees with C on honest and dishonest inputs.

## Decision

Add the first public cryptographic entry point:

```kotlin
SqiSign.verify(publicKey: EncodedPublicKey, message: ByteArray, signature: EncodedSignature): Boolean
```

- **Contract.**
  - Returns `true` only for a valid signature.
  - Returns `false` for every mathematically invalid key or signature, including:
    - singular or non-supersingular curves;
    - unusable hints;
    - unnormalized or non-invertible response matrices;
    - rejected isogenies;
    - challenge mismatches.
  - Throws `IllegalArgumentException` only when the key and the signature
    belong to different parameter sets. That is a caller error, not a forgery.
  - Byte-level canonicality stays in `WireFormat`, so `verify` never sees a
    non-canonical encoding.
  - All operations are variable-time.
- **Structure.**
  - `SqiSign` dispatches to the concrete `Fp2P324`, `Fp2P500` or `Fp2P664`
    field.
  - The internal generic `verifySignature` follows algorithm 3.24 and C in
    order:
    1. Reject an unnormalized response matrix (NormalizeMatrix, algorithm
       3.10). This is C `check_canonical_basis_change_matrix`; the range check
       is already part of `WireFormat.signature`.
    2. Build the public curve and its `2^(λ+2)` basis from the public hint.
    3. Compute the kernel `P + [c]Q` with the three-point ladder.
    4. Take the normalized challenge curve through `twoIsogenyChain`.
    5. Build the auxiliary curve, then reconstruct both `2^(r+2)` bases from
       the signature hints.
    6. Apply the response matrix to the challenge basis.
    7. Run the verify-mode theta chain of length `r`.
    8. Hash the public key, the first codomain coefficient and the message,
       and compare the result with the signature challenge.
- **Rejection plumbing.** Internal invariants already reject through `require`
  (`IllegalArgumentException`), and theta-chain failures arrive as
  `ThetaChainRejected`. `verifySignature` maps both to `false` and lets every
  other exception type escape as a defect. The 32-case common fuzz test and a
  one-off 600-case JVM bit-flip fuzz (200 per level over key, signature and
  message) observed only `false` or parse rejection, never another exception.
- **Departures from C.** Both are inherited from earlier ADRs and do not change
  any verdict on the public KATs or on the C-pinned mutation corpus.
  - **Hints (ADR 0011).**
    - Hint zero regenerates from search index one. C falls back to index 128.
      An honest signer emits zero only when no index below 128 works, so both
      reconstruct the same basis. Only a crafted zero hint on a curve with a
      smaller valid index can verify differently.
    - Hint one (index zero) fails explicitly.
    - Every reconstructed basis is fully validated.
  - **Matrix action (ADR 0015).**
    - `applyMatrix` uses the algorithm 4.89 biscalar ladders and requires an
      odd determinant. C uses `ec_biscalar_mul_verif` and leaves the
      difference point stale.
    - An even determinant gives a kernel that C's chain rejects too, so the
      verdict is unchanged.
    - The extra difference ladder is a small cost the theta chain does not
      use.

## Independent evidence

- **Public KATs.** The JVM-only `jvmKatTest` task verifies all 300 upstream KAT
  signatures, reading `KAT/*.rsp` directly. It runs in seven balanced classes
  across parallel forks, about 1.5 minutes on eight cores. CI splits it into
  `kat-p664` and `kat-other` shards with `-PkatTestShard`, because one
  four-vCPU Linux job took almost 9 minutes.
- **C-pinned mutation corpus.**
  - `tools/check_verify_reference.py` compiles the checksum-pinned verifier
    through the existing `tools/hd_reference.c` adapter. It derives 14 canonical
    patches from each of KAT records 0-3 at every level, records the
    `protocols_verify` verdicts in `tools/verify_mutations.txt`, and fails if
    the file is stale.
  - The patches are:
    - the unmodified record;
    - message, challenge, hint and public-hint bit flips;
    - a non-isotropic matrix (`d + 4`);
    - a negated matrix and a zero matrix;
    - auxiliary curves replaced by the public curve, `A = 2`, or a random
      coefficient;
    - public `A = -2`;
    - a seeded challenge-or-hint bit flip.
  - `jvmKatTest` replays all 168 cases through Kotlin and requires every
    verdict to match C.
- **Common tests.**
  - KAT 0 verifies at every level.
  - p324 rejection cases reach every verifier path: wire mismatch, an early
    unnormalized matrix, a `require` rejection, a theta-chain rejection and a
    hash mismatch.
  - A deterministic 32-case p324 bit-flip fuzz must return `false` without
    throwing.

## Alternatives

- **`Result` or a sealed verdict with reasons:** rejected for the public API.
  Callers act only on accept or reject, and reasons would turn internal
  diagnostics into a compatibility surface. Internal diagnostics stay
  available to tests through `ThetaChainRejected`.
- **Throw on invalid signatures:** rejected. Forgeries are expected input, not
  exceptional states.
- **Port the C hint fallback and biscalar routine exactly:** rejected. ADRs
  0011 and 0015 already chose validated, specification-shaped operations, and
  the corpus shows the same verdicts.
- **Verify all KATs in common tests:** rejected. On Node and native it would
  exceed the CI budget. Common tests keep one honest record per level, and the
  JVM task covers all 300.

## Consequences

Milestone 5 is functionally complete. The library can verify SQIsign v3
signatures for all three parameter sets through a stable public API, recorded
in the ABI dumps. Three retained `VerifyBenchmark.verify` cases time the full
algorithm and extend the smoke inventory to 363. Everything remains
variable-time and experimental: no constant-time, production-readiness or
expert-review claims are made. Signing, key generation and secret-key parsing
remain unimplemented.
