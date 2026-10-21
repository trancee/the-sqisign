//! The first step of a chain: double the kernel down to 8-torsion, glue, and
//! push the intermediate kernel points (C `gluing_start_chain`).

use super::{gluing_ladder, CoupleCurve, CouplePoint, Gluing, KernelCouplePoints, ThetaPoint};
use crate::ec::{BaryCoords, Point};
use crate::gf::{Fp2, FpParams};

/// Scratch state shared by the gluing and the generic steps.
pub(super) struct ChainState<P: FpParams> {
    /// Images of `T1` doublings, one per pending level.
    pub q1: Vec<ThetaPoint<P>>,
    /// Images of `T2` doublings, one per pending level.
    pub q2: Vec<ThetaPoint<P>>,
    /// Remaining steps per level.
    pub todo: Vec<usize>,
    /// Images of the points to evaluate.
    pub pts: Vec<ThetaPoint<P>>,
}

/// Runs the gluing of a chain of length `todo[0]`; returns the gluing and the
/// number of pending levels (C returns that number minus one).
///
/// `None` on a malformed kernel. The C reference returns `0` there too, which
/// its caller cannot tell apart from one pending level and goes on to read
/// uninitialised memory; the port reports the failure instead.
pub(super) fn start_chain<P: FpParams>(
    st: &mut ChainState<P>,
    e12: &CoupleCurve<P>,
    p12: &[CouplePoint<P>],
    ker: &KernelCouplePoints<P>,
    verify: bool,
) -> Option<(Gluing<P>, usize)> {
    let pts = [ker.t1.p1, ker.t1.p2, ker.t2.p1, ker.t2.p2];
    let mut zs = pts.map(|p| p.z);
    Fp2::batched_inv(&mut zs);
    let base: [Point<P>; 4] = core::array::from_fn(|k| Point::new(pts[k].x * zs[k], Fp2::ONE));
    let curves = [&e12.e1, &e12.e2, &e12.e1, &e12.e2];
    // t[k][level] = ([2^a] B_k, [2^a - 1] B_k).
    let space = st.todo.len();
    let mut t = base.map(|b| vec![(b, Point::IDENTITY); space]);

    let mut cur = 0;
    while st.todo[cur] != 1 {
        cur += 1;
        let prev = st.todo[cur - 1];
        let num_dbls = if prev >= 16 { prev / 2 } else { prev - 1 };
        for (k, tk) in t.iter_mut().enumerate() {
            let (p, q) = tk[cur - 1];
            tk[cur] = gluing_ladder(&p, &q, &base[k], num_dbls, curves[k]);
        }
        st.todo[cur] = prev - num_dbls;
    }

    let k1 = CouplePoint {
        p1: t[0][cur].0,
        p2: t[1][cur].0,
    };
    let k2 = CouplePoint {
        p1: t[2][cur].0,
        p2: t[3][cur].0,
    };
    // For a gluing that passed its own checks the special evaluations cannot
    // fail, so both share one exit.
    let (first, pts) = Gluing::compute(e12, &k1, &k2, false, verify)
        .and_then(|g| g.eval_special_all(p12).map(|pts| (g, pts)))?;
    st.pts = pts;

    let bary = |k: usize, j: usize| BaryCoords::from_points(&t[k][j].0, &base[k], &t[k][j].1);
    let push = |j: usize, inv1: &ThetaPoint<P>, inv2: &ThetaPoint<P>, dual: bool| {
        (
            first.eval_bary(&bary(0, j), &bary(1, j), inv1, dual),
            first.eval_bary(&bary(2, j), &bary(3, j), inv2, dual),
        )
    };
    let (q1, q2) = push(cur, &first.inv_image_k1_8, &first.inv_image_k2_8, true);
    st.todo[0] -= 1;
    let (inv1, inv2) = (q1.invert(), q2.invert());
    st.q1[0] = q1.hadamard();
    st.q2[0] = q2.hadamard();
    for j in 1..cur {
        (st.q1[j], st.q2[j]) = push(j, &inv1, &inv2, false);
        st.todo[j] -= 1;
    }
    Some((first, cur))
}
