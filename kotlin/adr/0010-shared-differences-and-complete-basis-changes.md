# ADR 0010: Shared differences and complete basis changes

**Status:** Accepted
**Date:** 2026-10-18

## Context

Milestone 3 needs algorithm 4.93 before bases and pairings. Given consistent
x-only common translates, two biquadratics share the desired difference.
The elimination formula is not complete: proportional quadratics, and some
exceptional common roots, give `(0:0)` rather than a projective point.

Algorithm 3.8 permits identical full bases and exponent one. Experiments
against the pinned C implementation found that these valid cases fail its
generic difference/normalization path. `biextension.c:427-494` performs the
unguarded elimination; `338-395` normalizes even infinite cross-differences.
Therefore that path cannot define the entire high-level basis-change domain.
ADR 0007 rejected affine arithmetic for the original ladder layer.

The user approved "complete-affine-cross-differences": a narrowly scoped
complete affine helper for basis changes, retaining x-only ladders and
cubical pairings. This refines that earlier decision, not the ladder contract.

## Decision

Implement `sharedDifference(P, Q, P+T, Q+T)` at the existing internal curve
seam using algorithm 4.93. Share equation (8)'s biquadratics with the existing
deterministic difference without changing its normalization or root sign.
The caller supplies consistent common translates. Coordinate construction
still proves neither membership nor consistency.

Return the projective common root, including infinity when valid. Reject
`(0:0)` through the existing point validator, with its explicit reason;
never reinterpret it as infinity or silently select another root.
This low-level operation requires nondegenerate elimination, not merely
the existence of a mathematical difference.

For the subsequent basis-change layer, use consistent affine lifts and
complete group-law differences instead of extending this partial resultant
with sign retries or fixture-specific cases. Exceptional pairing identities
must be confined to a validated maximal-curve domain: a valid full
`E[2^f]` basis supplies that premise at these parameters. An arbitrary
nonsingular coordinate constructor does not. The high-level matrix remains
defined only up to its permitted global sign.

## Alternatives

- **Copy the unguarded C path for every basis:** rejected because valid
  coincident bases and exponent-one cases fail its normalization assumptions.
- **Retry root signs or special-case the identity matrix:** rejected; neither
  establishes complete behavior for arbitrary valid basis changes.
- **Derive complete x-only exceptional formulas:** the user selected the
  smaller complete affine helper instead; its extra inversions are explicit.
- **Replace ordinary x-only ladders with affine arithmetic:** rejected;
  the approved helper is confined to basis changes.

## Risks and evidence

The independent full-affine Python generator supplies 24 all-parameter
shared-difference records. They cover independently scaled curves and all
four points, nontrivial translations, equal/opposite points, infinity inputs
and rank-deficient elimination. Pinned C outputs independently cross-check
those records, including an explicit degeneracy marker rather than encoding
invalid coordinates. Common tests check the result and unchanged inputs.

Six retained JVM cases cover the common-root operation without affine
normalization, raising the complete smoke matrix from 204 to 210.
Operations remain variable-time and unsuitable for secret inputs. Neither
these checks nor the mathematical strategy constitute expert review,
constant-time evidence or a controlled performance comparison.

## Migration

No public ABI, wire encoding, runtime dependency, toolchain or version change.
The existing fixture generator owns the new records; the C checker additionally
pins `biextension.c`, and CI watches that input.
The complete basis-change helper, bases/hints, pairings, canonical curve
normalization and isogeny chains are subsequent implementation work;
this shared-difference slice does not complete milestone 3.
