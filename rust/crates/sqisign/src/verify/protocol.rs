//! The verification protocol (vendor `verify.c`, `common.c`).

use core::cmp::Ordering;

use super::{
    PublicKey, Scalar, Signature, SqisignParams, EC_EXTRA_TORSION, HD_EXTRA_TORSION,
    MAX_ORDER_WORDS,
};
use crate::ec::{Basis, Curve, IsogEven};
use crate::hd::{CoupleCurve, KernelCouplePoints};
use crate::shake::Shake256;

/// The first `ORDER_WORDS` words of `x` with the top bit cleared, the
/// non-negative value C reads with `ibz_copy_digits`.
fn order_value<P: SqisignParams>(x: &Scalar) -> Scalar {
    let n = P::ORDER_WORDS;
    let mut v = Scalar::default();
    v[..n].copy_from_slice(&x[..n]);
    v[n - 1] &= u64::MAX >> 1;
    v
}

/// `2^k`.
fn pow2(k: usize) -> Scalar {
    let mut v = Scalar::default();
    v[k / 64] = 1 << (k % 64);
    v
}

fn cmp(a: &Scalar, b: &Scalar) -> Ordering {
    a.iter().rev().cmp(b.iter().rev())
}

/// Whether the entries of `mat` are canonical residues modulo `2^f` and the
/// first entry not in `{0, 2^(f-1)}` is below `2^(f-1)`, with
/// `f = RESPONSE_BITS + 2`, so that `M` and `-M` do not both pass (C
/// `check_canonical_basis_change_matrix`).
fn canonical_matrix<P: SqisignParams>(mat: &[[Scalar; 2]; 2]) -> bool {
    let f = P::RESPONSE_BITS + HD_EXTRA_TORSION;
    let (mid, bound) = (pow2(f - 1), pow2(f));
    let mut normal = false;
    for x in mat.iter().flatten().map(order_value::<P>) {
        if cmp(&x, &bound) != Ordering::Less {
            return false;
        }
        if normal {
            continue;
        }
        match cmp(&x, &mid) {
            Ordering::Greater => return false,
            Ordering::Less => normal = x != Scalar::default(),
            Ordering::Equal => {}
        }
    }
    true
}

/// The codomain of the isogeny with kernel `P + [chall] Q` for the basis
/// `b` of `e[2^f]` (C `compute_challenge_verify` after the basis).
fn challenge_from_basis<P: SqisignParams>(
    e: &Curve<P>,
    b: &Basis<P>,
    chall: &Scalar,
    f: usize,
) -> Option<Curve<P>> {
    let kernel = e.ladder3pt(chall, &b.p, &b.q, &b.pmq)?;
    IsogEven {
        curve: *e,
        kernel,
        length: f,
    }
    .codomain()
}

/// `(R, S) = ([a] P + [b] Q, [c] P + [d] Q)` for `M = [[a, c], [b, d]]`
/// (C `matrix_scalar_application_even_basis`); `P - Q` is left unchanged.
fn apply_matrix<P: SqisignParams>(
    b: &Basis<P>,
    e: &mut Curve<P>,
    mat: &[[Scalar; 2]; 2],
    f: usize,
) -> Option<Basis<P>> {
    let p = e.biscalar_mul_verif(&mat[0][0], &mat[1][0], f, b)?;
    let q = e.biscalar_mul_verif(&mat[0][1], &mat[1][1], f, b)?;
    Some(Basis { p, q, pmq: b.pmq })
}

impl<P: SqisignParams> PublicKey<P> {
    /// The challenge `SHAKE256("SQI" || pk || A(E_com) || msg)`, truncated to
    /// `CHALLENGE_BYTES` (C `hash_to_challenge`).
    pub fn hash_to_challenge(&self, com: &Curve<P>, msg: &[u8]) -> Scalar {
        let mut a = vec![0; 2 * P::ENCODED_BYTES];
        com.a.encode(&mut a);
        let mut h = Shake256::new();
        for part in [b"SQI".as_slice(), &self.to_bytes(), &a, msg] {
            h.absorb(part);
        }
        let mut out = [0u8; 8 * MAX_ORDER_WORDS];
        h.finalize().squeeze(&mut out[..P::CHALLENGE_BYTES]);
        core::array::from_fn(|i| u64::from_le_bytes(core::array::from_fn(|j| out[8 * i + j])))
    }

    /// Whether `sig` signs `msg` under this key (C `protocols_verify`).
    pub fn verify(&self, sig: &Signature<P>, msg: &[u8]) -> bool {
        self.commitment(sig).is_some_and(|com| {
            let chall = self.hash_to_challenge(&com, msg);
            let n = P::ORDER_WORDS;
            sig.chall[..n] == chall[..n]
        })
    }

    /// The commitment curve the signature leads to, or `None` if `sig` is
    /// malformed.
    fn commitment(&self, sig: &Signature<P>) -> Option<Curve<P>> {
        if !canonical_matrix::<P>(&sig.mat) || !Curve::verify_a(&self.curve.a) {
            return None;
        }
        let mut aux = Curve::from_a(&sig.aux_a)?;
        let mut pk = self.curve;
        let f = P::CHALLENGE_BITS + EC_EXTRA_TORSION;
        let b = pk.basis_2f_from_hint(f, self.hint);
        let mut chall = challenge_from_basis(&pk, &b, &sig.chall, f)?;

        let f = P::RESPONSE_BITS + HD_EXTRA_TORSION;
        let b_chall = chall.basis_2f_from_hint(f, sig.hint_chall);
        let b_aux = aux.basis_2f_from_hint(f, sig.hint_aux);
        let b_chall = apply_matrix(&b_chall, &mut chall, &sig.mat, f)?;

        let mut e12 = CoupleCurve { e1: chall, e2: aux };
        let ker = KernelCouplePoints::from_bases(&b_chall, &b_aux);
        e12.chain_compute_and_eval_verify(P::RESPONSE_BITS, &ker, &mut [])
    }
}

#[cfg(test)]
mod tests {
    use super::{canonical_matrix, challenge_from_basis, pow2, Scalar};
    use crate::ec::{Basis, Curve, Point};
    use crate::gf::{Fp2, P324};
    use crate::verify::SqisignParams;

    const F: usize = <P324 as SqisignParams>::RESPONSE_BITS + 2;

    fn mat(e: [Scalar; 4]) -> [[Scalar; 2]; 2] {
        [[e[0], e[1]], [e[2], e[3]]]
    }

    fn plus(x: Scalar, k: u64) -> Scalar {
        let mut v = x;
        v[0] += k;
        v
    }

    #[test]
    fn canonical_matrix_boundaries() {
        let (zero, one, mid, bound) = (
            Scalar::default(),
            plus(Scalar::default(), 1),
            pow2(F - 1),
            pow2(F),
        );
        let ok = |e| canonical_matrix::<P324>(&mat(e));
        assert!(ok([one, mid, plus(mid, 1), plus(mid, 2)]));
        assert!(ok([zero, mid, one, plus(mid, 1)]));
        assert!(ok([zero, zero, zero, zero]));
        assert!(!ok([plus(mid, 1), one, one, one]));
        assert!(!ok([zero, mid, plus(mid, 1), one]));
        assert!(!ok([one, one, one, bound]));
        // The bit C's signed reading drops, 2^(64 ORDER_WORDS - 1), is ignored.
        let mut top = one;
        top[<P324 as crate::gf::FpParams>::ORDER_WORDS - 1] = 1 << 63;
        assert!(ok([top, one, one, one]));
    }

    /// A ladder on a basis with `P - Q = (0 : 1)` fails.
    #[test]
    fn challenge_needs_a_basis() {
        let mut e = Curve::<P324>::default();
        e.normalize_with_a24();
        let p = Point::new(Fp2::ONE, Fp2::ONE);
        let b = Basis {
            p,
            q: p,
            pmq: Point::new(Fp2::ZERO, Fp2::ONE),
        };
        assert!(challenge_from_basis(&e, &b, &Scalar::default(), 130).is_none());
        let (b, _) = e.basis_2f_to_hint(130, 324);
        assert!(challenge_from_basis(&e, &b, &Scalar::default(), 130).is_some());
    }
}
