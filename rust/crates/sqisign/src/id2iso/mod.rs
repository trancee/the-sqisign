//! Translation between quaternion ideals and isogenies (vendor `id2iso`):
//! the action of `O0` on `E0[2^f]`, kernels to ideals, changes of basis and
//! the dimension-2 ideal-to-isogeny translation (`id2iso.c`, `dim2id2iso.c`).

mod params;

pub use params::Id2isoParams;

use crate::ec::{Basis, Curve, Point};
use crate::gf::FpParams;
use crate::hd::{CoupleCurve, CouplePoint, KernelCouplePoints};
use crate::mp::Ibz;
use crate::prng::Domain;
use crate::quat::{dim2, dim4, Alg, AlgElem, Ideal, Lattice, Mat2, Vec2};

/// `E0` with the action of its endomorphism ring on the basis of `E0[2^T]`
/// (C `curve_with_endomorphism_ring_t`).
pub struct CurveWithEndomorphisms<P: Id2isoParams> {
    /// `E0` (C `CURVE_E0`).
    pub curve: Curve<P>,
    /// The basis of `E0[2^T]` (C `BASIS_EVEN`).
    pub basis_even: Basis<P>,
    /// The action of `i` (C `ACTION_I`).
    pub action_i: Mat2<P>,
    /// The action of `j` (C `ACTION_J`).
    pub action_j: Mat2<P>,
    /// The action of `k` (C `ACTION_K`).
    pub action_k: Mat2<P>,
    /// The action of the second `O0` basis element (C `ACTION_GEN2`).
    pub action_gen2: Mat2<P>,
    /// The action of the third `O0` basis element (C `ACTION_GEN3`).
    pub action_gen3: Mat2<P>,
    /// The action of the fourth `O0` basis element (C `ACTION_GEN4`).
    pub action_gen4: Mat2<P>,
}

/// The `ORDER_WORDS` little-endian words of a non-negative `x` (C
/// `ibz_to_digit_array`).
pub(crate) fn digits<P: Id2isoParams>(x: &Ibz<P>) -> Vec<u64> {
    let mut out = vec![0; <P as FpParams>::ORDER_WORDS];
    x.to_digits(&mut out);
    out
}

/// Replaces the basis `(P, Q)` of `E[2^f]` by `([a]P + [b]Q, [c]P + [d]Q)`
/// for `mat = [[a, c], [b, d]]`, and `P - Q` accordingly when `last_point`;
/// reduces `mat` modulo `2^f` in place (C
/// `id2iso_matrix_application_even_basis`). A point whose multiplication
/// fails (an invalid basis) is left unchanged, as in C.
pub fn matrix_application_even_basis<P: Id2isoParams>(
    bas: &mut Basis<P>,
    e: &Curve<P>,
    mat: &mut Mat2<P>,
    f: usize,
    last_point: bool,
) {
    let pow_two = Ibz::ONE.mul_2exp(f);
    let tmp_bas = *bas;
    for x in mat.iter_mut().flatten() {
        *x = x.modulo(&pow_two);
    }
    let mul = |k: &Ibz<P>, l: &Ibz<P>, old: Point<P>| {
        e.biscalar_mul(&digits(k), &digits(l), f, &tmp_bas)
            .unwrap_or(old)
    };
    bas.p = mul(&mat[0][0], &mat[1][0], bas.p);
    bas.q = mul(&mat[0][1], &mat[1][1], bas.q);
    if last_point {
        let a = (mat[0][0] - mat[0][1]).modulo(&pow_two);
        let b = (mat[1][0] - mat[1][1]).modulo(&pow_two);
        bas.pmq = mul(&a, &b, bas.pmq);
    }
}

/// Applies the endomorphism `theta` of `E0` to the basis `bas` of `E[2^f]`,
/// through the precomputed action matrices (C
/// `id2iso_endomorphism_application_even_basis`).
pub fn endomorphism_application_even_basis<P: Id2isoParams>(
    bas: &mut Basis<P>,
    e: &Curve<P>,
    theta: &AlgElem<P>,
    f: usize,
    last_point: bool,
) {
    let endo = &P::CURVE_WITH_ENDOMORPHISMS;
    let two_e = Ibz::ONE.mul_2exp(f);
    let (coeffs, content) = theta.make_primitive(&Lattice::o0());
    let coeffs = coeffs.map(|c| c.modulo(&two_e));
    let content = content.modulo(&two_e);
    let actions = [&endo.action_gen2, &endo.action_gen3, &endo.action_gen4];
    let mut mat: Mat2<P> = Default::default();
    for i in 0..2 {
        mat[i][i] = mat[i][i] + coeffs[0];
        for j in 0..2 {
            for (action, c) in actions.iter().zip(&coeffs[1..]) {
                mat[i][j] = (mat[i][j] + action[i][j] * *c).modulo(&two_e);
            }
            mat[i][j] = (mat[i][j] * content).modulo(&two_e);
        }
    }
    matrix_application_even_basis(bas, e, &mut mat, f, last_point);
}

/// The ideal of norm `2^f` (or `2^(f-1)`) whose kernel on `E0` is generated
/// by `vec2[0] P + vec2[1] Q` for the basis `(P, Q)` of `E0[2^f]`, and the
/// split factor `1 - i` or `1` of the kernel generator (C
/// `id2iso_kernel_dlogs_to_ideal_even`, where `TWO_TO_CHALLENGE_BITS` is
/// `2^f` with the same bound).
///
/// # Panics
///
/// If the kernel does not define a cyclic ideal (C asserts).
pub fn kernel_dlogs_to_ideal_even<P: Id2isoParams>(
    vec2: &Vec2<P>,
    f: usize,
) -> (Ideal<P>, AlgElem<P>) {
    let endo = &P::CURVE_WITH_ENDOMORPHISMS;
    let mut two_pow = Ibz::ONE.mul_2exp(f);
    let j = dim2::mat_eval(&endo.action_j, vec2);
    let g4 = dim2::mat_eval(&endo.action_gen4, vec2);
    let mat = [
        [vec2[0], (j[0] + g4[0]).modulo(&two_pow)],
        [vec2[1], (j[1] + g4[1]).modulo(&two_pow)],
    ];
    let (inv, _) = dim2::mat_inv_mod(&mat, &two_pow);
    let vec = dim2::mat_eval(&inv, &dim2::mat_eval(&endo.action_i, vec2));
    let vec = vec.map(|v| v.modulo(&two_pow));

    // a - i + b (j + (1 + k) / 2)
    let mut gen = AlgElem {
        denom: Ibz::from_i64(2, 3),
        coord: [
            vec[0] + vec[0] + vec[1],
            Ibz::from_i64(-2, 3),
            vec[1] + vec[1],
            vec[1],
        ],
    };
    for c in &mut gen.coord {
        c.set_bound(f + 10);
    }

    // Removes a factor (1 + i) of the generator, recording 1 - i as split.
    let mut split_test = Alg::pinfty().mul(&gen, &AlgElem::from_i32(1, [1, 1, 0, 0]));
    let (_, n) = split_test.make_primitive(&Lattice::o0());
    let split = if n.is_odd() {
        AlgElem::from_i32(1, [1, 0, 0, 0])
    } else {
        split_test.coord = dim4::vec_scalar_div(&n, &split_test.coord).0;
        gen = split_test;
        two_pow = Ibz::ONE.mul_2exp(f - 1);
        AlgElem::from_i32(1, [1, -1, 0, 0])
    };
    let ideal = Ideal::create_o0_pow_two(&gen, &two_pow).expect("a cyclic kernel ideal");
    (ideal, split)
}

/// The matrix of `B1` in the basis `B2` of `E[2^f]`, or of `B2` in `B1` when
/// `invert`, from Tate pairing discrete logarithms (C
/// `id2iso_internal_change_of_basis_matrix_tate_impl`).
fn change_of_basis_matrix_tate_impl<P: Id2isoParams>(
    b1: &Basis<P>,
    b2: &Basis<P>,
    e: &mut Curve<P>,
    f: usize,
    invert: bool,
) -> Mat2<P> {
    let (pq, rs) = if invert { (b1, b2) } else { (b2, b1) };
    let d = e.dlog_2_tate(pq, rs, f);
    let w = <P as FpParams>::ORDER_WORDS;
    let m = [
        [Ibz::from_digits(&d.r1, w), Ibz::from_digits(&d.s1, w)],
        [Ibz::from_digits(&d.r2, w), Ibz::from_digits(&d.s2, w)],
    ];
    if invert {
        let ([[r1, r2], [s1, s2]], _) = Ibz::invmat(&[[m[0][0], m[1][0]], [m[0][1], m[1][1]]], f);
        [[r1, s1], [r2, s2]]
    } else {
        m
    }
}

/// The matrix `M` with `(M v) . B2 = v . B1` on `E[2^f]`, where `B2` has
/// full order `2^T` (C `id2iso_change_of_basis_matrix_tate`).
pub fn change_of_basis_matrix_tate<P: Id2isoParams>(
    b1: &Basis<P>,
    b2: &Basis<P>,
    e: &mut Curve<P>,
    f: usize,
) -> Mat2<P> {
    change_of_basis_matrix_tate_impl(b1, b2, e, f, false)
}

/// As [`change_of_basis_matrix_tate`] with `B1` of full order, inverting the
/// matrix of `B2` in `B1` (C `id2iso_change_of_basis_matrix_tate_invert`).
pub fn change_of_basis_matrix_tate_invert<P: Id2isoParams>(
    b1: &Basis<P>,
    b2: &Basis<P>,
    e: &mut Curve<P>,
    f: usize,
) -> Mat2<P> {
    change_of_basis_matrix_tate_impl(b1, b2, e, f, true)
}

/// The dimension-2 translation of an ideal: qlapoty's `beta1` and `d1`, the
/// codomain of the ideal's isogeny from `E0` and the image of `BASIS_EVEN`.
pub struct Dim2Id2Iso<P: Id2isoParams> {
    /// `beta1`.
    pub beta1: AlgElem<P>,
    /// `d1`.
    pub d1: Ibz<P>,
    /// The codomain.
    pub codomain: Curve<P>,
    /// The image of `BASIS_EVEN`.
    pub basis: Basis<P>,
}

/// Evaluates the isogeny of `ideal` on `BASIS_EVEN` through a 2-dimensional
/// isogeny chain, or `None` when the chain fails (C
/// `dim2id2iso_ideal_to_isogeny_qlapoty`, which leaves its curve and basis
/// outputs unwritten then). Every draw is from the `default` domain.
///
/// # Panics
///
/// If qlapoty fails (C aborts; it never happens).
pub fn ideal_to_isogeny_qlapoty<P: Id2isoParams>(
    ideal: &Ideal<P>,
    default: &mut Domain,
) -> Option<Dim2Id2Iso<P>> {
    let endo = &P::CURVE_WITH_ENDOMORPHISMS;
    let t = <P as FpParams>::T as usize;
    let q = ideal.qlapoty(default).expect(
        "Qlapoty failed. This should never happen. Please report this failure to the developers.",
    );
    let two_e = Ibz::ONE.mul_2exp(t);
    let (d1_inv, _) = q.d1.invmod(&two_e);

    let mut theta_basis = endo.basis_even;
    let theta = q.theta.scalar_mul(&d1_inv);
    endomorphism_application_even_basis(&mut theta_basis, &endo.curve, &theta, t, false);
    let ker = KernelCouplePoints::from_bases(&theta_basis, &endo.basis_even);

    let mut basis = endo.basis_even;
    let beta1 = q.beta1.scalar_mul(&d1_inv);
    endomorphism_application_even_basis(&mut basis, &endo.curve, &beta1, t, true);
    let mut push = [basis.p, basis.q, basis.pmq].map(|p1| CouplePoint {
        p1,
        p2: Point::IDENTITY,
    });

    let mut e0e0 = CoupleCurve {
        e1: endo.curve,
        e2: endo.curve,
    };
    e0e0.chain_compute_and_eval_e2(P::QLAPOTY_POWER_OF_TWO, &ker, &mut push)
        .map(|codomain| {
            let [p, q2, pmq] = push.map(|c| c.p2);
            Dim2Id2Iso {
                beta1: q.beta1,
                d1: q.d1,
                codomain,
                basis: Basis { p, q: q2, pmq },
            }
        })
}

/// The codomain of the isogeny of `ideal` and the image of `BASIS_EVEN`, or
/// `None` (C `dim2id2iso_arbitrary_isogeny_evaluation`).
pub fn arbitrary_isogeny_evaluation<P: Id2isoParams>(
    ideal: &Ideal<P>,
    default: &mut Domain,
) -> Option<(Basis<P>, Curve<P>)> {
    ideal_to_isogeny_qlapoty(ideal, default).map(|r| (r.basis, r.codomain))
}
