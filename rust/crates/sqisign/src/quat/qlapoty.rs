//! The norm equation of the Clapotis ideal-to-isogeny translation (vendor
//! `qlapoty.c`). Every random draw is from the default stream.

use super::{dim2, lll, Alg, AlgElem, Ideal, Mat2, QuatParams, Vec2};
use crate::mp::Ibz;
use crate::prng::Domain;

/// `ibz_bitsize(p)`.
fn p_bits<P: QuatParams>() -> usize {
    Alg::<P>::pinfty().p.bitsize()
}

fn bounded<P: QuatParams>(mut x: Ibz<P>, bound: usize) -> Ibz<P> {
    x.set_bound(bound);
    x
}

/// The first two coordinates of `e`.
fn head<P: QuatParams>(e: &AlgElem<P>) -> Vec2<P> {
    [e.coord[0], e.coord[1]]
}

/// The Gaussian conjugate `k[0] - k[1] i`.
fn conj2<P: QuatParams>(k: &Vec2<P>) -> Vec2<P> {
    [k[0], -k[1]]
}

/// `ipt` with its second column reduced against its first, so that the
/// first column of a reduced lattice is a first minimum (C
/// `quat_qlapoty_dim2_reduce_last_step`).
///
/// # Panics
///
/// If the first column of `ipt` is zero.
pub fn dim2_reduce_last_step<P: QuatParams>(ipt: &Mat2<P>) -> Mat2<P> {
    let n0 = Ibz::sum_two_squares(&ipt[0][0], &ipt[1][0]);
    let q = (ipt[0][0] * ipt[0][1] + ipt[1][0] * ipt[1][1]).rounded_div(&n0);
    [
        [ipt[0][0], ipt[0][1] - q * ipt[0][0]],
        [ipt[1][0], ipt[1][1] - q * ipt[1][0]],
    ]
}

/// The basis (by columns) of the lattice of `(s, t)` with `a s + b t = 0 mod
/// 4N` for the generator `gen = (a + b i + ...) / 2`, and the target `k (u, v)
/// mod 4N` with `u a + v b = g`, `k g + l 4N = 1` (C
/// `quat_qlapoty_gen_to_dim2_lattice`, which skips the target for `NULL`).
///
/// `gen` must have denominator 2 and `gcd(a, b, 4N) = 1`.
///
/// # Panics
///
/// If `n` is zero.
pub fn gen_to_dim2_lattice<P: QuatParams>(gen: &AlgElem<P>, n: &Ibz<P>) -> (Mat2<P>, Vec2<P>) {
    let [a, b] = head(gen);
    let n4 = n.mul_2exp(2);
    let gcd = a.gcd(&n4);
    let n4div = n4.div_rem(&gcd).0;
    let c = a.div_rem(&gcd).0.invmod(&n4div).0;
    let c = (-(c * b)).modulo(&n4div);
    let (g, u, v) = a.xgcd(&b);
    let (_, k, _) = g.xgcd(&n4);
    let target = [(k * u).modulo(&n4), (k * v).modulo(&n4)];
    ([[c, n4div], [gcd, Ibz::ZERO]], target)
}

/// The columns of `li` swapped and reduced, for lattices of determinant at
/// most `4N` (C `quat_qlapoty_dim2_ct_reduce`).
fn dim2_ct_reduce<P: QuatParams>(li: &Mat2<P>, n: &Ibz<P>) -> Mat2<P> {
    let swapped = [[li[0][1], li[0][0]], [li[1][1], li[1][0]]];
    lll::dim2_short_basis(&swapped, n.bound() + 2)
}

/// A basis of the lattice of `alpha0` whose first column is `st`, reduced
/// against it, and the lattice target (C `quat_qlapoty_get_short_basis`).
///
/// `st` must be a primitive vector of the lattice.
///
/// # Panics
///
/// If `n` is zero.
pub fn get_short_basis<P: QuatParams>(
    st: &Vec2<P>,
    alpha0: &AlgElem<P>,
    n: &Ibz<P>,
) -> (Mat2<P>, Vec2<P>) {
    let n4 = n.mul_2exp(2);
    let (basis, target) = gen_to_dim2_lattice(alpha0, n);
    let (c, gcd) = (basis[0][0], basis[1][0]);
    let x00 = st[1].div_rem(&gcd).0;
    let x10 = (gcd * st[0] - c * st[1]).div_rem(&n4).0;
    let (_, x11, x01) = x00.xgcd(&x10);
    let xy = [[x00, -x01], [x10, x11]];
    let nb = n.bound() + 10;
    let basis = dim2::mat_mul(&basis, &xy).map(|row| row.map(|x| bounded(x, nb)));
    (dim2_reduce_last_step(&basis), target)
}

/// The sampling bounds of the first qlapoty loop.
#[derive(Clone, Copy, Debug)]
pub struct Bounds<P: QuatParams> {
    /// The bounds `(a, b)` on the squared norm of the multiplier.
    pub ab: Vec2<P>,
    /// The short lattice vector, divided by `(2 ± i)^f`.
    pub omega: Vec2<P>,
    /// The generator, multiplied by the removed `(2 ± i)` factor.
    pub gen: AlgElem<P>,
    /// The number of factors 5 removed from `omega`.
    pub f: u32,
    /// Whether `b - a` exceeds `bitsize(N)^2`.
    pub big_interval: bool,
}

/// The bounds of the first loop from the reduced lattice `li` of the
/// canonical generator `gen` of norm `n`, or `None` if `n` is too small
/// for `twoe = 2^e` (C `quat_qlapoty_initialize_bounds`, which returns 0).
///
/// C multiplies `gen` by `(2 ± i)^(2^(f - 1))` and `omega` by its inverse
/// times 5^f, squaring rather than multiplying in its power loop.
///
/// # Panics
///
/// If the first column of `li` is zero.
pub fn initialize_bounds<P: QuatParams>(
    gen: &AlgElem<P>,
    n: &Ibz<P>,
    li: &Mat2<P>,
    twoe: &Ibz<P>,
) -> Option<Bounds<P>> {
    let alg = Alg::<P>::pinfty();
    let mut omega = [li[0][0], li[1][0]];
    let l = Ibz::sum_two_squares(&omega[0], &omega[1]);
    let b1 = (*n * Ibz::from_i64(5, 4)).div_2exp(1);
    let b0 = n.div_2exp(1);
    let ba = ((twoe.mul_2exp(3) - n.mul_2exp(4)) * *n).div_rem(&alg.p).0;
    if l * ba <= b0.mul_2exp(1) {
        return None;
    }
    let (q, r) = b0.div_rem(&l);
    let mut a = if r.is_zero() { q } else { q + Ibz::ONE };
    let mut b = b1.div_rem(&l).0;
    let half = ba.div_2exp(1);
    if b > half {
        b = half;
    }
    let cst = Ibz::from_i64(n.bitsize() as i64, 32);
    let cst = cst * cst;
    let mut big_interval = b - a > cst;
    let mut f = 0;
    let mut gen = *gen;
    if !big_interval {
        let five = Ibz::from_i64(5, 4);
        let mut gcd = omega[0].gcd(&omega[1]);
        let mut fivef = Ibz::from_i64(1, 2);
        let cst2 = cst * cst;
        while gcd.divides(&five) && fivef < cst2 {
            gcd = gcd.div_rem(&five).0;
            fivef = fivef * five;
            f += 1;
        }
        if f != 0 {
            omega = omega.map(|x| x.div_rem(&fivef).0);
            let mut fivevec = AlgElem::from_i32(1, [2, 1, 0, 0]);
            if (omega[0].mul_2exp(1) - omega[1]).divides(&five) {
                fivevec.coord[1] = -fivevec.coord[1];
            }
            let mut g = head(&fivevec);
            for _ in 1..f {
                g = dim2::gaussian_mul(&g, &g);
            }
            [fivevec.coord[0], fivevec.coord[1]] = g;
            omega = dim2::gaussian_mul(&g, &omega);
            gen = alg.mul(&fivevec, &gen);
        }
        a = fivef * a;
        b = fivef * b;
        big_interval = b - a > cst;
    }
    Some(Bounds {
        ab: [a, b],
        omega,
        gen,
        f,
        big_interval,
    })
}

/// A random `alpha0 = (k0 + k1 i) gen` of norm coprime to `n` and the short
/// solution `(k0 + k1 i) omega` (C `quat_qlapoty_loop_one`).
///
/// For an upper bound `sqrt(absq[1])` of at most 11, C fixes `k` instead of
/// sampling and loops forever if that `k` is rejected.
pub fn loop_one<P: QuatParams>(
    gen: &AlgElem<P>,
    n: &Ibz<P>,
    absq: &Vec2<P>,
    omega: &Vec2<P>,
    f: u32,
    big_interval: bool,
    default: &mut Domain,
) -> (AlgElem<P>, Vec2<P>) {
    let alg = Alg::<P>::pinfty();
    let gcdomega = omega[0].gcd(&omega[1]);
    let half = absq[0].div_2exp(1);
    let mut lo = half.sqrt_floor();
    if lo * lo != half && absq[0].is_even() {
        lo = lo + Ibz::ONE;
    }
    let hi = absq[1].sqrt_floor();
    let mut k = [Ibz::ZERO; 2];
    let mut sample = true;
    for (limit, [k0, k1], kb) in [(11, [4, 3], 4), (4, [2, 1], 3), (2, [1, 0], 2)] {
        if hi.cmp_i32(limit).is_le() {
            k = [Ibz::from_i64(k0, kb), Ibz::from_i64(k1, kb)];
            sample = false;
        }
    }
    let kb = (n.bound() + p_bits::<P>()) / 2;
    let work = loop {
        if sample {
            k[0] = Ibz::rand_interval(&lo, &hi, default);
            let ksq = k[0] * k[0];
            let lo2 = if absq[0] > ksq {
                let t = absq[0] - ksq;
                let s = t.sqrt_floor();
                if s * s < t {
                    s + Ibz::ONE
                } else {
                    s
                }
            } else {
                Ibz::from_i64(0, 0)
            };
            let hi2 = (absq[1] - ksq).sqrt_floor();
            if lo2 > hi2 {
                continue;
            }
            k[1] = Ibz::rand_interval(&lo2, &hi2, default);
        }
        if k[0].is_even() == k[1].is_even() || !k[1].gcd(&k[0]).is_one() {
            continue;
        }
        let nk = k[0] * k[0] + k[1] * k[1];
        if !nk.gcd(if big_interval { n } else { &gcdomega }).is_one() {
            continue;
        }
        if k[0].is_odd() {
            k.swap(0, 1);
        }
        if f & 1 == 1 {
            k.swap(0, 1);
            k[0] = -k[0];
        }
        let mut gamma = dim2::gaussian_gcd(&conj2(&k), omega);
        let mut ngamma = Ibz::sum_two_squares(&gamma[0], &gamma[1]);
        let size_k = 2 * k[0].bound().max(k[1].bound());
        loop {
            let done = ngamma.is_one();
            gamma = dim2::gaussian_mul(&gamma, &gamma);
            k = dim2::gaussian_mul(&k, &gamma).map(|x| x.div_rem(&ngamma).0);
            if done {
                break;
            }
            k = k.map(|x| bounded(x, size_k));
            gamma = dim2::gaussian_gcd(&conj2(&k), omega);
            ngamma = Ibz::sum_two_squares(&gamma[0], &gamma[1]);
        }
        k = k.map(|x| bounded(x, kb));
        let mut work = AlgElem::from_i32(1, [0; 4]);
        [work.coord[0], work.coord[1]] = k;
        let work = alg.mul(&work, gen);
        if n.gcd(&work.coord[0].gcd(&work.coord[1])).is_one() {
            break work;
        }
    };
    (work, dim2::gaussian_mul(&k, omega))
}

/// A solution of the second qlapoty loop.
#[derive(Clone, Copy, Debug)]
pub struct LoopTwo<P: QuatParams> {
    /// The odd multiplier `lambda` of `alpha0`.
    pub lambda: Ibz<P>,
    /// The lattice solution `s`.
    pub s: Ibz<P>,
    /// The lattice solution `t`.
    pub t: Ibz<P>,
    /// `sc` with `sc^2 + tc^2 = z` (Cornacchia).
    pub sc: Ibz<P>,
    /// `tc` with `sc^2 + tc^2 = z` (Cornacchia).
    pub tc: Ibz<P>,
}

/// The first odd `lambda` up to `sqrt(2^e / r)` with a lattice solution
/// `(s, t)` and a prime `z = 2k - s^2 - t^2` that is a sum of two squares,
/// or `None` (C `quat_qlapoty_loop_two`, which returns 0).
///
/// # Panics
///
/// If `n` or `r` is zero, or `alpha0` gives a singular lattice.
pub fn loop_two<P: QuatParams>(
    alpha0: &AlgElem<P>,
    r: &Ibz<P>,
    st0: &Vec2<P>,
    n: &Ibz<P>,
    twoe: &Ibz<P>,
    default: &mut Domain,
) -> Option<LoopTwo<P>> {
    let delta_n = Ibz::from_i64(1 + 2 * i64::from(r.is_even()), 3) * *n;
    let (basis, target0) = get_short_basis(st0, alpha0, n);
    let (inv, det) = dim2::mat_inv_with_det(&basis);
    let inv = inv.expect("a lattice basis is invertible");
    let nb = n.bound();
    let e = twoe.bound();
    let target0 = target0.map(|x| bounded(-x, 2 * nb + 10));
    let mut lambda = -Ibz::from_i64(1, 2);
    let rtwo = r.mul_2exp(1);
    let twoemdn = *twoe - delta_n;
    let quadn = n.mul_2exp(2);
    let bound = twoe.div_rem(r).0.sqrt_floor();
    while lambda <= bound {
        lambda = bounded(lambda + Ibz::TWO, 50);
        if !lambda.gcd(&quadn).is_one() {
            continue;
        }
        let sum = bounded(twoemdn - rtwo * lambda * lambda, e);
        let tmp = bounded((sum * lambda.invmod(&quadn).0).modulo(&quadn), nb + 10);
        let targetl = target0.map(|x| (-(x * tmp)).modulo(&quadn));
        let close = dim2::mat_eval(&inv, &targetl).map(|x| x.rounded_div(&det));
        let close = dim2::mat_eval(&basis, &close);
        let target = dim2::vec_sub(&targetl, &close).map(|x| bounded(x, 2 * nb + 10));
        if target[0].is_even() == target[1].is_even() {
            continue;
        }
        let tmp = (target[0] * alpha0.coord[0] + target[1] * alpha0.coord[1]) * lambda;
        let k = bounded((sum + delta_n - bounded(tmp, 2 * nb + 20)).div_rem(n).0, e);
        let z = k + k - target[1] * target[1] - target[0] * target[0];
        let z = bounded(z, 2 * nb + 10);
        if z < Ibz::ZERO {
            continue;
        }
        let found = (z.get() & 3 == 1 && z.probab_prime(P::PRIMALITY_NUM_ITER, default))
            .then(|| z.cornacchia_prime())
            .flatten();
        if let Some((sc, tc)) = found {
            return Some(LoopTwo {
                lambda,
                s: bounded(target[0], nb + 10),
                t: bounded(target[1], nb + 10),
                sc,
                tc,
            });
        }
    }
    None
}

/// A solution `mu1`, `mu2` of `N(mu1) + N(mu2) = 2^e N` in the shortest
/// equivalent ideal of norm `N`.
#[derive(Clone, Copy, Debug)]
pub struct NormEq<P: QuatParams> {
    /// `mu1`.
    pub mu1: AlgElem<P>,
    /// `mu2`.
    pub mu2: AlgElem<P>,
    /// `mu2 conj(mu1) / N`.
    pub theta: AlgElem<P>,
    /// The shortest element of the input ideal.
    pub smallest: AlgElem<P>,
}

/// The qlapoty output.
#[derive(Clone, Copy, Debug)]
pub struct Qlapoty<P: QuatParams> {
    /// `beta1`, in lowest terms.
    pub beta1: AlgElem<P>,
    /// The degree `d1`.
    pub d1: Ibz<P>,
    /// `theta`, in lowest terms.
    pub theta: AlgElem<P>,
}

impl<P: QuatParams> Ideal<P> {
    /// Solves the qlapoty norm equation in the shortest equivalent ideal of
    /// `self`, or `None` (C `quat_qlapoty_normeq`, which returns 0).
    pub fn qlapoty_normeq(&self, default: &mut Domain) -> Option<NormEq<P>> {
        let alg = Alg::<P>::pinfty();
        let (smallest, mut small) = self.shortest_equivalent(default);
        small.norm.set_bound(p_bits::<P>() / 2 + 10);
        let norm = small.norm;
        let mut gen = small.odd_inert_gen();
        for (c, b) in gen.coord.iter_mut().zip([norm.bound(), norm.bound(), 2, 1]) {
            c.set_bound(b);
        }
        gen.denom.set_bound(3);
        let li = dim2_ct_reduce(&gen_to_dim2_lattice(&gen, &norm).0, &norm);
        let twoe = Ibz::ONE.mul_2exp(P::QLAPOTY_POWER_OF_TWO);
        let b = initialize_bounds(&gen, &norm, &li, &twoe)?;
        let (alpha0, st0) = loop_one(&b.gen, &norm, &b.ab, &b.omega, b.f, b.big_interval, default);
        let r = alg.norm(&alpha0).0.div_rem(&norm).0;
        let l = loop_two(&alpha0, &r, &st0, &norm, &twoe, default)?;
        let (mut sc, mut tc) = (l.sc, l.tc);
        if l.s.is_even() != sc.is_even() {
            core::mem::swap(&mut sc, &mut tc);
        }
        let mut mu1 = AlgElem::from_i32(2, [0; 4]);
        let mut mu2 = mu1;
        [mu1.coord[0], mu1.coord[1]] = [l.s + sc, l.t + tc];
        [mu2.coord[0], mu2.coord[1]] = [l.s - sc, l.t - tc];
        let temp = alpha0.scalar_mul(&l.lambda);
        let mu1 = AlgElem::add(&mu1.scalar_mul(&norm), &temp);
        let mu2 = AlgElem::add(&mu2.scalar_mul(&norm), &temp);
        let mut c = mu1.conj();
        c.denom = c.denom * norm;
        Some(NormEq {
            mu1,
            mu2,
            theta: alg.mul(&mu2, &c),
            smallest,
        })
    }

    /// `beta1`, `d1` and `theta` for the dimension-2 translation of `self`,
    /// or `None` (C `quat_qlapoty`, which returns 0).
    pub fn qlapoty(&self, default: &mut Domain) -> Option<Qlapoty<P>> {
        let alg = Alg::<P>::pinfty();
        let ne = self.qlapoty_normeq(default)?;
        let mut theta = ne.theta;
        theta.normalize();
        let mut theta = theta.conj();
        let n = alg.norm(&ne.smallest).0.div_rem(&self.norm).0;
        let n = bounded(n, p_bits::<P>() / 2 + 1);
        let d1 = bounded(
            alg.norm(&ne.mu1).0.div_rem(&n).0,
            P::QLAPOTY_POWER_OF_TWO + 2,
        );
        let mut beta1 = alg.mul(&ne.mu1, &ne.smallest);
        beta1.denom = beta1.denom * n;
        beta1.normalize();
        let bb = (self.norm.bound() + d1.bound()) / 2 + 3;
        beta1.coord.iter_mut().for_each(|c| c.set_bound(bb));
        beta1.denom.set_bound(3);
        let tb = 2 * (beta1.coord[0].bound() + 1);
        theta.coord.iter_mut().for_each(|c| c.set_bound(tb));
        theta.denom.set_bound(4);
        Some(Qlapoty { beta1, d1, theta })
    }
}
