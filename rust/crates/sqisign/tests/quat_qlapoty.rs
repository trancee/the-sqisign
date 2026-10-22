//! Differential tests: Rust qlapoty norm equation against the vendor C
//! reference, on paired PRNGs.

#[macro_use]
mod support;

macro_rules! quat_qlapoty_suite {
    ($module:ident, $params:ty) => {
        #[allow(dead_code, unused_imports)]
        mod $module {
            use super::support::{Rng, SAMPLES};
            use c::qlapoty as C;
            use sqisign::mp::Ibz;
            use sqisign::quat::qlapoty as R;
            use sqisign::quat::Vec2;
            use sqisign_reference::$module as c;

            type P = $params;
            type I = Ibz<P>;
            mp_helpers!();
            quat_helpers!();
            ideal_helpers!();

            const N: usize = SAMPLES / 16;

            /// A random value of `1..=bits` bits from the test RNG.
            fn rand_bits(bits: usize, rng: &mut Rng) -> I {
                let w: Vec<u64> = (0..c::mp::IBZ_NLIMBS).map(|_| rng.next_u64()).collect();
                I::from_bits(&w, 1 + rng.next_u64() as usize % bits)
            }

            /// `2^QUAT_qlapoty_used_power_of_two`.
            fn twoe() -> I {
                I::ONE.mul_2exp(P::QLAPOTY_POWER_OF_TWO)
            }

            /// The canonical generator of the shortest equivalent ideal of
            /// `a` with normeq's bounds, its norm and the reduced lattice,
            /// checked against C on the paired default streams.
            fn reduced(a: &Ideal<P>, ca: &c::ideal::Ideal, p: &mut Prng) -> (AlgElem<P>, I, Mat2) {
                let (e, mut small) = a.shortest_equivalent(p.default_domain());
                let (ce, cs) = c::ideal::shortest_equivalent(ca);
                same_elem(&e, &ce);
                same_ideal(&small, &cs);
                small
                    .norm
                    .set_bound(Alg::<P>::pinfty().p.bitsize() / 2 + 10);
                let n = small.norm;
                let mut gen = small.odd_inert_gen();
                for (c, b) in gen.coord.iter_mut().zip([n.bound(), n.bound(), 2, 1]) {
                    c.set_bound(b);
                }
                gen.denom.set_bound(3);
                let (lat, target) = R::gen_to_dim2_lattice(&gen, &n);
                let (clat, ctarget) = C::gen_to_dim2_lattice(&elem_c(&gen), &to_c(&n));
                same_mat(&lat, &clat);
                same_vec(&target, &ctarget);
                let swapped = [[lat[0][1], lat[0][0]], [lat[1][1], lat[1][0]]];
                let li = sqisign::quat::lll::dim2_short_basis(&swapped, n.bound() + 2);
                (gen, n, li)
            }

            type Mat2 = sqisign::quat::Mat2<P>;

            /// C `initialize_bounds` outputs.
            type CBounds = (
                c::quat::Vec2,
                c::quat::Vec2,
                c::quat::AlgElem,
                [u32; 2],
                i32,
            );

            /// Asserts Rust and C bounds agree.
            fn same_bounds(r: &Option<R::Bounds<P>>, (ab, o, g, f, ok): &CBounds) {
                assert_eq!(r.is_some(), *ok != 0);
                if let Some(b) = r {
                    same_vec(&b.ab, ab);
                    same_vec(&b.omega, o);
                    same_elem(&b.gen, g);
                    assert_eq!([b.f, b.big_interval.into()], *f);
                }
            }

            /// C `initialize_bounds` on Rust inputs.
            fn c_bounds(gen: &AlgElem<P>, n: &I, li: &Mat2, twoe: &I) -> CBounds {
                C::initialize_bounds(&elem_c(gen), &to_c(n), &mat_c(li), &to_c(twoe))
            }

            /// Asserts Rust and C loop-one outputs agree.
            fn same_loop_one(
                gen: &AlgElem<P>,
                n: &I,
                ab: &Vec2<P>,
                omega: &Vec2<P>,
                f: u32,
                big: bool,
                p: &mut Prng,
            ) -> (AlgElem<P>, Vec2<P>) {
                let (a, st) = R::loop_one(gen, n, ab, omega, f, big, p.default_domain());
                let (ca, cst) =
                    C::loop_one(&elem_c(gen), &to_c(n), &vec_c(ab), &vec_c(omega), f, big);
                same_elem(&a, &ca);
                same_vec(&st, &cst);
                (a, st)
            }

            /// Asserts Rust and C loop-two outputs agree.
            fn same_loop_two(
                alpha0: &AlgElem<P>,
                r: &I,
                st0: &Vec2<P>,
                n: &I,
                twoe: &I,
                p: &mut Prng,
            ) -> bool {
                let got = R::loop_two(alpha0, r, st0, n, twoe, p.default_domain());
                let (o, ok) = C::loop_two(
                    &elem_c(alpha0),
                    &to_c(r),
                    &vec_c(st0),
                    &to_c(n),
                    &to_c(twoe),
                );
                assert_eq!(got.is_some(), ok != 0);
                if let Some(l) = got {
                    same_vec(&[l.lambda, l.s, l.t, l.sc, l.tc], &o);
                }
                got.is_some()
            }

            /// Ideals of prime norms from tiny to the response size, which
            /// `shortest_equivalent` handles like C (see `quat_response`).
            fn ideals() -> Vec<(Ideal<P>, c::ideal::Ideal)> {
                norm_bits()
                    .into_iter()
                    .enumerate()
                    .flat_map(|(k, bits)| ideal_pairs(bits, N, 70 + k as u8))
                    .map(|[a, _]| a)
                    .collect()
            }

            #[test]
            fn reduce_last_steps_match_reference() {
                let pool = pool(1, 200);
                let mut rng = Rng::new(1);
                for _ in 0..SAMPLES {
                    let mut m: Mat2 = pick_mat(&pool, &mut rng);
                    m[0][0] = pick_nonzero(&pool, &mut rng);
                    same_mat(
                        &R::dim2_reduce_last_step(&m),
                        &C::dim2_reduce_last_step(&mat_c(&m)),
                    );
                }
            }

            #[test]
            fn pipelines_match_reference() {
                let mut solved = 0;
                for (s, (a, ca)) in ideals().into_iter().enumerate() {
                    let mut p = seeded(s as u64);
                    let (gen, n, li) = reduced(&a, &ca, &mut p);
                    let b = R::initialize_bounds(&gen, &n, &li, &twoe());
                    same_bounds(&b, &c_bounds(&gen, &n, &li, &twoe()));
                    let Some(b) = b else {
                        // Small norms at higher levels.
                        in_step(&mut p);
                        continue;
                    };
                    let (alpha0, st0) =
                        same_loop_one(&b.gen, &n, &b.ab, &b.omega, b.f, b.big_interval, &mut p);
                    let (basis, target) = R::get_short_basis(&st0, &alpha0, &n);
                    let (cb, ct) = C::get_short_basis(&vec_c(&st0), &elem_c(&alpha0), &to_c(&n));
                    same_mat(&basis, &cb);
                    same_vec(&target, &ct);
                    let r = Alg::pinfty().norm(&alpha0).0.div_rem(&n).0;
                    solved += usize::from(same_loop_two(&alpha0, &r, &st0, &n, &twoe(), &mut p));
                    // Too small a power of two leaves no lambda.
                    let tiny = I::ONE.mul_2exp(n.bitsize());
                    assert!(!same_loop_two(&alpha0, &r, &st0, &n, &tiny, &mut p));
                    in_step(&mut p);
                }
                assert!(solved > 0);
            }

            #[test]
            fn crafted_bounds_match_reference() {
                let mut rng = Rng::new(2);
                let five = I::from_i64(5, 4);
                for (s, (a, ca)) in ideals().into_iter().enumerate().skip(2 * N) {
                    let mut p = seeded(s as u64);
                    let (gen, n, li) = reduced(&a, &ca, &mut p);
                    let half = n.bitsize() / 2;
                    for j in 0..4 {
                        // A short vector `5^j (u, v)` of squared norm about
                        // `n`, or a tiny one for a big interval.
                        let mut l = li;
                        let mut scale = I::ONE;
                        (0..j).for_each(|_| scale = scale * five);
                        let bits = (half - 3 * j).max(2);
                        l[0][0] = scale * rand_bits(bits, &mut rng);
                        l[1][0] = scale * rand_bits(bits, &mut rng);
                        for twoe in [twoe(), n.mul_2exp(1)] {
                            let b = R::initialize_bounds(&gen, &n, &l, &twoe);
                            same_bounds(&b, &c_bounds(&gen, &n, &l, &twoe));
                        }
                        let tiny = [[I::from_i64(j as i64 + 1, 4), l[0][1]], [I::ONE, l[1][1]]];
                        let b = R::initialize_bounds(&gen, &n, &tiny, &twoe());
                        same_bounds(&b, &c_bounds(&gen, &n, &tiny, &twoe()));
                    }
                    in_step(&mut p);
                }
            }

            #[test]
            fn crafted_first_loops_match_reference() {
                let mut rng = Rng::new(3);
                for (s, (a, ca)) in ideals().into_iter().enumerate().skip(3 * N) {
                    let mut p = seeded(s as u64);
                    let (gen, n, li) = reduced(&a, &ca, &mut p);
                    let Some(b) = R::initialize_bounds(&gen, &n, &li, &twoe()) else {
                        continue;
                    };
                    for (k, absq) in [
                        // Sampled, with interval gaps and both parities.
                        [I::from_i64(2, 8), I::from_i64(400, 16)],
                        [I::from_i64(301, 16), I::from_i64(400, 16)],
                        [I::from_i64(9_000, 16), I::from_i64(9_050, 16)],
                        [I::ONE, rand_bits(40, &mut rng).mul_2exp(8)],
                        // Fixed `k` for each threshold.
                        [I::ONE, I::from_i64(121, 16)],
                        [I::ONE, I::from_i64(16, 16)],
                    ]
                    .into_iter()
                    .enumerate()
                    {
                        let f = k as u32 % 3;
                        let big = k % 2 == 0;
                        let five = I::from_i64(5, 4);
                        let m = if big { n } else { b.omega[0].gcd(&b.omega[1]) };
                        if k >= 4 && m.divides(&five) {
                            // C loops forever on a rejected fixed `k`.
                            continue;
                        }
                        same_loop_one(&gen, &n, &absq, &b.omega, f, big, &mut p);
                    }
                    // Small split primes in the modulus reject some `k` and, on the
                    // small interval, some products with `gen`.
                    let smooth = n * I::from_i64(5 * 13 * 17 * 29 * 37 * 41, 32);
                    for big in [true, false] {
                        for _ in 0..8 {
                            let absq = [I::ONE, rand_bits(40, &mut rng).mul_2exp(8)];
                            same_loop_one(&gen, &smooth, &absq, &b.omega, 0, big, &mut p);
                        }
                    }
                    in_step(&mut p);
                }
            }

            #[test]
            fn boundary_bounds_match_reference() {
                let (a, ca) = &ideals()[N];
                let mut p = seeded(0);
                let (gen, n, li) = reduced(a, ca, &mut p);
                // `l * ba` equal to `2 floor(n / 2)` for `omega = (1, 0)`:
                // the smallest `2^e` with `ba = 2 floor(n / 2)`, and one less.
                let unit = [[I::ONE, li[0][1]], [I::ZERO, li[1][1]]];
                let x = n.div_2exp(1).mul_2exp(1) * Alg::<P>::pinfty().p + (n * n).mul_2exp(4);
                let (q, r) = x.div_rem(&n.mul_2exp(3));
                let edge = if r.is_zero() { q } else { q + I::ONE };
                for t in [edge - I::ONE, edge, edge + I::ONE] {
                    let b = R::initialize_bounds(&gen, &n, &unit, &t);
                    same_bounds(&b, &c_bounds(&gen, &n, &unit, &t));
                }
                assert!(R::initialize_bounds(&gen, &n, &unit, &edge).is_none());
                // `5^f` reaching `bitsize(n)^4 = 25^2` stops removing fives
                // (`2^e = p` keeps `ba` positive for so small an `n`).
                let n5 = I::from_i64(17, 6);
                let pe = Alg::<P>::pinfty().p;
                for (w, f) in [(3_125, 4), (625, 4), (125, 3)] {
                    let l = [[I::from_i64(w, 14), li[0][1]], [I::ZERO, li[1][1]]];
                    let b = R::initialize_bounds(&gen, &n5, &l, &pe);
                    same_bounds(&b, &c_bounds(&gen, &n5, &l, &pe));
                    assert_eq!(b.map(|b| b.f), Some(f));
                }
            }

            #[test]
            fn normeqs_match_reference() {
                for (s, (a, ca)) in ideals().into_iter().enumerate() {
                    let mut p = seeded(s as u64);
                    let got = a.qlapoty_normeq(p.default_domain());
                    let (o, ok) = C::normeq(&ca);
                    assert_eq!(got.is_some(), ok != 0);
                    if let Some(e) = got {
                        same_elem(&e.mu1, &o[0]);
                        same_elem(&e.mu2, &o[1]);
                        same_elem(&e.theta, &o[2]);
                        same_elem(&e.smallest, &o[3]);
                    }
                    in_step(&mut p);
                }
            }

            #[test]
            fn qlapotys_match_reference() {
                for (s, (a, ca)) in ideals().into_iter().enumerate() {
                    let mut p = seeded(s as u64);
                    let got = a.qlapoty(p.default_domain());
                    let (b, d, th, ok) = C::qlapoty(&ca);
                    assert_eq!(got.is_some(), ok != 0);
                    if let Some(q) = got {
                        same_elem(&q.beta1, &b);
                        same(&q.d1, &d);
                        same_elem(&q.theta, &th);
                    }
                    in_step(&mut p);
                }
            }
        }
    };
}

quat_qlapoty_suite!(p324_3, sqisign::gf::P324);
quat_qlapoty_suite!(p500_27, sqisign::gf::P500);
quat_qlapoty_suite!(p664_17, sqisign::gf::P664);
