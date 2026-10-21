//! Generic (2,2)-isogenies between theta structures and the full chain
//! (`theta_isogenies.c`).

use super::splitting::{self, Mode};
use super::start::{start_chain, ChainState};
use super::{CoupleCurve, CouplePoint, KernelCouplePoints, ThetaPoint, ThetaStructure};
use crate::ec::Curve;
use crate::gf::FpParams;

/// A (2,2)-isogeny given by its codomain theta structure.
struct ThetaIsogeny<P: FpParams> {
    codomain: ThetaStructure<P>,
}

/// The squared theta coordinates of `p`, after a Hadamard transform if the
/// domain is in dual coordinates.
fn squared<P: FpParams>(p: &ThetaPoint<P>, dual_domain: bool) -> ThetaPoint<P> {
    if dual_domain {
        p.hadamard().to_squared()
    } else {
        p.to_squared()
    }
}

impl<P: FpParams> ThetaIsogeny<P> {
    /// The isogeny with kernel `[4](T1_8, T2_8)` (C `theta_isogeny_compute`),
    /// or `None` if `verify` and the kernel is inconsistent.
    fn compute(
        t1_8: &ThetaPoint<P>,
        t2_8: &ThetaPoint<P>,
        dual_domain: bool,
        verify: bool,
    ) -> Option<Self> {
        let tt1 = squared(t1_8, dual_domain);
        let tt2 = squared(t2_8, dual_domain);
        let zero = tt2.x.is_zero()
            | tt2.y.is_zero()
            | tt2.z.is_zero()
            | tt2.t.is_zero()
            | tt1.x.is_zero()
            | tt1.y.is_zero();
        if verify && zero != 0 {
            return None;
        }
        let t1 = tt1.y * tt2.t;
        let t2 = tt1.x * tt2.z;
        let inv = ThetaPoint::new(t1 * tt2.z, t2 * tt2.t, t1 * tt2.x, t2 * tt2.y);
        if verify && (tt1.z * inv.z).ct_eq(&(tt1.t * inv.t)) == 0 {
            return None;
        }
        Some(Self {
            codomain: ThetaStructure {
                inv_dual_null_point: inv,
                dbl_data: ThetaPoint::new(tt2.x, tt1.x, tt1.y, tt2.y),
                inv_sqr_null_point: ThetaPoint::ZERO,
                precomputation: false,
            },
        })
    }

    /// The image of `p` (C `theta_isogeny_eval`).
    fn eval(&self, p: &ThetaPoint<P>, dual_domain: bool) -> ThetaPoint<P> {
        squared(p, dual_domain).pmul(&self.codomain.inv_dual_null_point)
    }
}

/// C `theta_chain_compute_impl`.
fn chain<P: FpParams>(
    n: usize,
    e12: &mut CoupleCurve<P>,
    ker: &KernelCouplePoints<P>,
    p12: &mut [CouplePoint<P>],
    mode: Mode,
) -> Option<CoupleCurve<P>> {
    e12.e1.normalize_a24();
    e12.e2.normalize_a24();
    // C leaves n < 2 undefined (the generic loop never initialises its step).
    if n < 2 {
        return None;
    }
    let space = 1 + n.next_power_of_two().trailing_zeros() as usize;
    let mut st = ChainState {
        q1: vec![ThetaPoint::ZERO; space],
        q2: vec![ThetaPoint::ZERO; space],
        todo: vec![0; space],
        pts: Vec::new(),
    };
    st.todo[0] = n;
    let (first, mut top) = start_chain(&mut st, e12, p12, ker, mode == Mode::Verify)?;

    let mut step = ThetaIsogeny {
        codomain: ThetaStructure {
            inv_dual_null_point: first.codomain.invert(),
            dbl_data: ThetaPoint::ZERO,
            inv_sqr_null_point: first.codomain.to_squared().invert(),
            precomputation: true,
        },
    };
    let mut i = 1;
    while top > 0 {
        let mut cur = top - 1;
        let not_step2 = i != 1;
        while st.todo[cur] != 1 {
            cur += 1;
            let num_dbls = st.todo[cur - 1] / 2;
            st.q1[cur] = step.codomain.dbl_iter(&st.q1[cur - 1], num_dbls);
            st.q2[cur] = step.codomain.dbl_iter(&st.q2[cur - 1], num_dbls);
            st.todo[cur] = st.todo[cur - 1] - num_dbls;
        }
        let verify = i != n - 1 && mode == Mode::Verify;
        step = ThetaIsogeny::compute(&st.q1[cur], &st.q2[cur], not_step2, verify)?;
        for p in &mut st.pts {
            *p = step.eval(p, not_step2);
        }
        for j in 0..cur {
            st.q1[j] = step.eval(&st.q1[j], not_step2);
            st.q2[j] = step.eval(&st.q2[j], not_step2);
            st.todo[j] -= 1;
        }
        top = cur;
        i += 1;
    }
    let dual = step.codomain.dual_null_point();
    splitting::to_elliptic_product(p12, &dual, &st.pts, mode)
}

impl<P: FpParams> CoupleCurve<P> {
    /// Computes the (2^n, 2^n)-isogeny with kernel `[4]<T1, T2>` (points of
    /// order `2^(n+2)`) and evaluates `p12` through it; each point must have
    /// a zero component. Returns the codomain (C `theta_chain_compute_and_eval`).
    ///
    /// Normalises the `A24` of both domain curves, as the C reference does.
    pub fn chain_compute_and_eval(
        &mut self,
        n: usize,
        ker: &KernelCouplePoints<P>,
        p12: &mut [CouplePoint<P>],
    ) -> Option<CoupleCurve<P>> {
        chain(n, self, ker, p12, Mode::Both)
    }

    /// As [`Self::chain_compute_and_eval`], computing only the first codomain
    /// curve (C `theta_chain_compute_and_eval_E1`).
    pub fn chain_compute_and_eval_e1(
        &mut self,
        n: usize,
        ker: &KernelCouplePoints<P>,
        p12: &mut [CouplePoint<P>],
    ) -> Option<Curve<P>> {
        chain(n, self, ker, p12, Mode::E1).map(|e| e.e1)
    }

    /// As [`Self::chain_compute_and_eval`], computing only the second codomain
    /// curve (C `theta_chain_compute_and_eval_E2`).
    pub fn chain_compute_and_eval_e2(
        &mut self,
        n: usize,
        ker: &KernelCouplePoints<P>,
        p12: &mut [CouplePoint<P>],
    ) -> Option<Curve<P>> {
        chain(n, self, ker, p12, Mode::E2).map(|e| e.e2)
    }

    /// As [`Self::chain_compute_and_eval_e1`], rejecting malformed kernels
    /// (C `theta_chain_compute_and_eval_verify`, used by verification).
    pub fn chain_compute_and_eval_verify(
        &mut self,
        n: usize,
        ker: &KernelCouplePoints<P>,
        p12: &mut [CouplePoint<P>],
    ) -> Option<Curve<P>> {
        chain(n, self, ker, p12, Mode::Verify).map(|e| e.e1)
    }
}
