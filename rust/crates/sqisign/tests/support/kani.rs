//! Kernels of (2^n, 2^n)-isogenies on `E1 x E0` from Kani's lemma.
//!
//! With `phi1 : E0 -> E1` of degree 3 and `alpha = x + y i` of degree
//! `x^2 + y^2 = 2^n - 3`, the kernel `{(phi1(P), alpha(P)) : P in E0[2^n]}`
//! gives a chain that splits into a product of elliptic curves.

#![allow(unused_macros)]

/// Defines `kani_kernel`; expects `ec_helpers!` and `hd_helpers!` first.
macro_rules! kani_helpers {
    ($params:ty) => {
        const COFACTOR: u64 = <$params as FpParams>::C;

        /// `(n, x, y)` with `x^2 + y^2 = 2^n - 3`.
        const KANI: [(usize, u64, u64); 9] = [
            (3, 1, 2),
            (4, 2, 3),
            (5, 2, 5),
            (6, 5, 6),
            (7, 2, 11),
            (16, 142, 213),
            (20, 422, 933),
            (33, 54133, 75230),
            (40, 577_082, 875_493),
        ];

        /// A point of order exactly 3 on `E0` (the cofactor must be divisible by 3).
        fn three_torsion(rng: &mut Rng) -> Pt {
            let mut e = e0();
            loop {
                let x = rand_f2(rng);
                if (x * x * x + x).is_square() == 0 {
                    continue;
                }
                let p = e.dbl_iter(&Pt::new(x, F2::ONE), T);
                let k = e.mul(&small_scalar(COFACTOR / 3), 8, &p);
                let k2 = e.dbl(&k);
                if k.is_zero() == 0 && k.xadd(&k2, &k).is_zero() != 0 {
                    return k;
                }
            }
        }

        /// The x-only 3-isogeny with kernel `<K>`: its codomain and evaluator.
        fn isogeny3(k: &Pt) -> (Cv, impl Fn(&Pt) -> Pt) {
            let (k1, k2) = (k.x - k.z, k.x + k.z);
            let t0 = k1 * k1;
            let t1 = k2 * k2;
            let t2 = t0 + t1;
            let s = k1 + k2;
            let t3 = s * s - t2;
            let t2 = t1 + t3;
            let t3 = t3 + t0;
            let t4 = t3 + t0;
            let t4 = t4 + t4 + t1;
            let minus = t2 * t4;
            let t4 = t1 + t2;
            let t4 = t4 + t4 + t0;
            let plus = t3 * t4;
            let a = plus + minus;
            let mut e = Cv {
                a: a + a,
                c: plus - minus,
                ..Cv::default()
            };
            e.normalize_with_a24();
            let eval = move |q: &Pt| {
                let t0 = k1 * (q.x + q.z);
                let t1 = k2 * (q.x - q.z);
                let (s, d) = (t0 + t1, t1 - t0);
                Pt::new(q.x * s * s, q.z * d * d)
            };
            (e, eval)
        }

        /// `(r mod 2^e)` as scalar words.
        fn low(v: &[u64], e: usize) -> u64 {
            v[0] & ((1u64 << e) - 1)
        }

        /// `E1 x E0` and the kernel `(T1, T2)` of order `2^(n+2)` for `KANI[idx]`,
        /// plus the basis `P, Q` of `E0[2^(n+2)]` and `phi1`.
        fn kani_kernel(rng: &mut Rng, idx: usize) -> (Cc, Kr, Bs, Bs) {
            let (n, x, y) = KANI[idx];
            let e = n + 2;
            let mask = (1u64 << e) - 1;
            let mut e0c = e0();
            let full = e0c.basis_2f_from_hint(T, 0);
            let b = e0c.dbl_iter_basis(&full, T - e);
            let iota = |p: &Pt| Pt::new(-p.x, p.z);
            let ib = Bs {
                p: iota(&b.p),
                q: iota(&b.q),
                pmq: iota(&b.pmq),
            };
            let d = e0c.dlog_2_tate(&full, &ib, e);
            let (r1, r2, s1, s2) = (low(&d.r1, e), low(&d.r2, e), low(&d.s1, e), low(&d.s2, e));
            let ap = e0c
                .biscalar_mul(
                    &small_scalar(x.wrapping_add(y.wrapping_mul(r1)) & mask),
                    &small_scalar(y.wrapping_mul(r2) & mask),
                    e,
                    &b,
                )
                .expect("basis");
            let aq = e0c
                .biscalar_mul(
                    &small_scalar(y.wrapping_mul(s1) & mask),
                    &small_scalar(x.wrapping_add(y.wrapping_mul(s2)) & mask),
                    e,
                    &b,
                )
                .expect("basis");
            let (e1, phi1) = isogeny3(&three_torsion(rng));
            let b1 = Bs {
                p: phi1(&b.p),
                q: phi1(&b.q),
                pmq: phi1(&b.pmq),
            };
            let b2 = Bs {
                p: ap,
                q: aq,
                pmq: Pt::IDENTITY,
            };
            let e12 = Cc { e1, e2: e0() };
            (e12, Kr::from_bases(&b1, &b2), b, b1)
        }
    };
}
