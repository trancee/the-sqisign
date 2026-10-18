# ADR 0009: Shared typed Montgomery arithmetic

**Status:** Accepted
**Date:** 2026-10-17

## Context

Milestone 3 requires the same curve algorithms over `p324_3`, `p500_27`
and `p664_17`. The concrete arithmetic recorded in ADRs 0007-0008 couples
those algorithms to `Fp2P324` and `UInt328`; that scalar width cannot retain
the wider parameters' coefficients. The existing three extension-field
implementations already have matching immutable arithmetic contracts.

The user selected "Use one typed generic curve implementation and migrate
all existing callers" rather than maintaining three copies of cryptographic
logic. Internal field, curve and point test seams were confirmed.

## Decision

Replace the concrete curve and point types with `MontgomeryCurve<Element>`
and `MontgomeryPoint<Element>`. `QuadraticFieldElement<Element>` captures the
existing arithmetic, canonical roots, ordering and encoding contract.
`QuadraticField<Element>` captures parameter metadata, construction,
conjugation and reference-sign roots. The three existing field companions
are its adapters; their representations and arithmetic kernels are unchanged.
Self-typed elements keep different parameter sets from mixing at call sites,
without casts, erased arithmetic or runtime field selectors.

Preserve all projective arithmetic and exceptional-domain contracts from
ADRs 0007-0008. Scalars are exact-width unsigned little-endian byte arrays:
41, 64 or 84 bytes, retaining all 328, 512 or 672 bits without reduction.
All ladder entry points reject any other width before evaluation, including
exceptional points and zero coefficients. Scalar buffers are not retained or
mutated. The positive biladder's decrement uses byte differences in
`[-1, 255]` and a borrow in `{0, 1}`; positivity proves no terminal underflow.
Recoding preserves the highest scalar bit and its explicit zero lookahead.

Add reference-sign roots and conjugation to the wider fields. The shared
byte-backed root candidate uses algorithm 4.35, with exponent
`(p+1)/4 - 1 = (p-3)/4`, then verifies its square. Nonresidues return null.
The `p324_3` root implementation remains unchanged. Canonical `squareRoot()`
operations retain their previous contracts at every level.

## Alternatives

- **Three concrete curve/basis/pairing implementations:** rejected because
  each mathematical correction would need three coordinated implementations.
- **One byte-backed field for every curve:** rejected because it would
  replace the independently verified `p324_3` field representation.
- **Erased field values or unchecked casts:** rejected in favor of typed
  adapters over the three actual representations.
- **Keep the old concrete curve as an alias or forwarding layer:** rejected;
  the contract is internal, all consumers migrate, and no public deprecation
  obligation requires a second path.
- **Reduce scalars to the torsion exponent:** rejected; ordinary curve
  arithmetic does not establish the torsion-order precondition.

## Risks and evidence

All arithmetic remains variable-time and unsuitable for secret inputs.
Generic dispatch and the changed scalar input representation require
benchmark revalidation; no speedup or controlled-regression claim follows
from correctness checks. Expert review, leakage assessment, numeric budgets
and a controlled committed benchmark baseline remain absent.

The owning Python generator supplies 16 independent full-affine curve
records and 30 exact-sign root records for each parameter set. The pinned
C checker cross-checks all 48 curve records, 90 roots and 72 two-torsion
basis/parity cases, with strict malformed/noncanonical/truncated-input
checks at every width. Common tests preserve the earlier `p324_3` behavior
and check wider arithmetic and full scalar widths through the same seam.
These are mathematical differential checks, not production-readiness proof.

## Migration

All concrete curve/point callers, tests and retained `p324_3` workloads
migrate to the typed implementation; superseded production files are removed.
Generated fixtures remain owned by `tools/generate_curve_vectors.py`.
`checkCurveReference` now compiles all three reference variants and pins their
curve headers in addition to the existing field/source pins.
The retained harness is now `MontgomeryBenchmark` with all three parameter
sets and both scalar shapes: 48 curve/root cases, increasing the complete
JVM smoke matrix from 172 to 204. Timing includes workload method dispatch
and already encoded scalars; the previous scalar-export boundary is not
preserved or presented as a controlled performance comparison.

The forced clean macOS ARM64 check took 43 minutes 7 seconds locally,
including all 334 Native tests; the fresh 204-case JVM smoke passed separately.
The check-job timeout increases from 30 to 60 minutes to accommodate the
expanded all-parameter suite without dropping cases or weakening checks.
[`ADR 0016`](0016-montgomery-field-multiplication.md) later restores
30 minutes after Montgomery field multiplication.
This shared-host observation is not a controlled performance comparison or
a guarantee of CI-host duration.

No public ABI, SQIsign wire encoding, release version, runtime dependency or
toolchain changes. Milestone 3 still requires shared differences, canonical
bases/hints, pairings and change-of-basis, canonical curve normalization,
isogeny chains and their all-target evidence. The subsequent
[ADR 0010](0010-shared-differences-and-complete-basis-changes.md) records
shared differences and the approved complete basis-change strategy.
