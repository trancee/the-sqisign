//! Differential tests: Rust `mp` gcds, inverses, CRT, Jacobi symbols and
//! matrix inversion against the vendor C reference.

#[macro_use]
mod support;

macro_rules! mp_gcd_suite {
    ($module:ident, $params:ty) => {
        mod $module {
            use super::support::Rng;
            use sqisign::mp::Ibz;
            use sqisign_reference::$module as c;

            type I = Ibz<$params>;
            mp_helpers!();

            /// A random value in `[0, 2^bits)`.
            fn positive(bits: usize, rng: &mut Rng) -> (I, c::mp::Ibz) {
                let w: Vec<u64> = (0..c::mp::IBZ_NLIMBS)
                    .map(|i| match bits.saturating_sub(64 * i) {
                        0 => 0,
                        k if k < 64 => rng.next_u64() & ((1 << k) - 1),
                        _ => rng.next_u64(),
                    })
                    .collect();
                pair(&w, bits + 1, rng)
            }

            fn mul(a: &(I, c::mp::Ibz), b: &(I, c::mp::Ibz)) -> (I, c::mp::Ibz) {
                (a.0 * b.0, c::mp::mul(&a.1, &b.1))
            }

            /// Samples with headroom, multiples of a common factor, consecutive
            /// Fibonacci numbers (all quotients 1) and operands one word
            /// apart whose shorter top word is small.
            fn operands(seed: u64) -> Vec<(I, c::mp::Ibz)> {
                let mut rng = Rng::new(seed);
                let mut v: Vec<_> = samples(seed)
                    .into_iter()
                    .filter(|(r, _)| r.bound() <= CAP - 64)
                    .collect();
                for bits in [64, 200, CAP / 4] {
                    let g = positive(bits, &mut rng);
                    for _ in 0..3 {
                        let x = positive(CAP / 2, &mut rng);
                        v.push(mul(&g, &x));
                    }
                }
                let (mut f0, mut f1) = ((I::ZERO, c::mp::set(0, 1)), (I::ONE, c::mp::set(1, 2)));
                while f1.0.bitsize() < CAP - 70 {
                    (f0, f1) = (f1, (f0.0 + f1.0, c::mp::add(&f0.1, &f1.1)));
                }
                v.extend([f0, f1]);
                let mut w = vec![0; c::mp::IBZ_NLIMBS];
                (w[0], w[1], w[2]) = (rng.next_u64(), rng.next_u64(), 3);
                v.push(pair(&w, 3 * 64, &mut rng));
                (w[1], w[2]) = (1, 0);
                v.push(pair(&w, 2 * 64, &mut rng));
                v
            }

            #[test]
            fn gcd_matches_reference() {
                let s: Vec<_> = samples(30).into_iter().chain(operands(30)).collect();
                for (ra, xa) in &s {
                    for (rb, xb) in &s {
                        same(&ra.gcd(rb), &c::mp::gcd(xa, xb));
                    }
                }
            }

            #[test]
            fn xgcd_matches_reference() {
                let s = operands(31);
                for (ra, xa) in &s {
                    for (rb, xb) in &s {
                        let (g, u, v) = ra.xgcd(rb);
                        let (cg, cu, cv) = c::mp::xgcd(xa, xb);
                        same(&g, &cg);
                        same(&u, &cu);
                        same(&v, &cv);
                    }
                }
            }

            #[test]
            fn invmod_matches_reference() {
                let s = operands(32);
                let moduli = s.iter().filter(|(r, _)| r.is_positive() && !r.is_zero());
                for (rm, xm) in moduli {
                    for (ra, xa) in &s {
                        let (inv, ok) = ra.invmod(rm);
                        let (cinv, cok) = c::mp::invmod(xa, xm);
                        same(&inv, &cinv);
                        assert_eq!(-i32::from(ok), cok, "{ra:?} {rm:?}");
                    }
                }
            }

            #[test]
            fn invmod_with_a_two_word_gcd_ending_in_one() {
                let mut rng = Rng::new(36);
                let g = pair(&[1, 1, 0], 129, &mut rng);
                let (a, m) = (
                    mul(&g, &positive(200, &mut rng)),
                    mul(&g, &positive(200, &mut rng)),
                );
                let (inv, ok) = a.0.invmod(&m.0);
                let (cinv, cok) = c::mp::invmod(&a.1, &m.1);
                same(&inv, &cinv);
                assert_eq!((ok, cok), (false, 0));
            }

            #[test]
            fn crt_matches_reference() {
                let mut rng = Rng::new(33);
                for bits in [1, 2, 63, 64, 65, 200, CAP / 4] {
                    for _ in 0..8 {
                        let (m1, m2) = (
                            positive(bits, &mut rng),
                            positive(CAP / 4 - bits / 2, &mut rng),
                        );
                        if m1.0.is_zero() || m2.0.is_zero() || !m1.0.gcd(&m2.0).is_one() {
                            continue;
                        }
                        let w: Vec<u64> = (0..c::mp::IBZ_NLIMBS).map(|_| rng.next_u64()).collect();
                        let (a1, a2) = (pair(&w, CAP / 4, &mut rng), pair(&w[1..], bits, &mut rng));
                        same(
                            &I::crt(&a1.0, &a2.0, &m1.0, &m2.0),
                            &c::mp::crt(&a1.1, &a2.1, &m1.1, &m2.1),
                        );
                    }
                }
            }

            #[test]
            fn legendre_matches_reference() {
                let mut rng = Rng::new(34);
                for bits in [1, 2, 3, 64, 65, 200, CAP / 2, CAP - 64] {
                    for _ in 0..16 {
                        let (p, xp) = positive(bits, &mut rng);
                        let p1 = (
                            p.mul_2exp(1) + I::ONE,
                            c::mp::add(&c::mp::shift(&xp, 1, true), &c::mp::set(1, 2)),
                        );
                        for (a, xa) in [
                            positive(bits, &mut rng),
                            positive((bits + 70).min(CAP - 2), &mut rng),
                            (I::ZERO, c::mp::set(0, 1)),
                        ] {
                            if a.is_zero() || a.gcd(&p1.0).is_one() {
                                assert_eq!(
                                    a.legendre(&p1.0),
                                    c::mp::legendre(&xa, &p1.1),
                                    "{a:?} {p1:?}"
                                );
                            } else {
                                assert_eq!(a.legendre(&p1.0), 0);
                            }
                        }
                    }
                }
            }

            #[test]
            fn legendre_of_known_symbols() {
                let s = |a: i64, p: i64| I::from_i64(a, 32).legendre(&I::from_i64(p, 32));
                assert_eq!(
                    [s(2, 7), s(3, 7), s(6, 9), s(1, 3), s(0, 5), s(10, 3)],
                    [1, -1, 0, 1, 0, 1]
                );
            }

            #[test]
            fn invmat_matches_reference() {
                let mut rng = Rng::new(35);
                for e in [0, 1, 2, 63, 64, 65, 127, 200, CAP / 3 - 2] {
                    for bits in [1, e, e + 1, e + 2, 2 * e + 2, CAP / 3] {
                        for _ in 0..4 {
                            let mut m = [[I::ZERO; 2]; 2];
                            let mut x = [[c::mp::Ibz::default(); 2]; 2];
                            for k in 0..4 {
                                let w: Vec<u64> =
                                    (0..c::mp::IBZ_NLIMBS).map(|_| rng.next_u64()).collect();
                                (m[k / 2][k % 2], x[k / 2][k % 2]) =
                                    pair(&w, bits.clamp(1, CAP), &mut rng);
                            }
                            let (inv, ok) = I::invmat(&m, e);
                            let cok = c::mp::invmat(&mut x, e as i32);
                            assert_eq!(-i32::from(ok), cok);
                            for k in 0..4 {
                                same(&inv[k / 2][k % 2], &x[k / 2][k % 2]);
                            }
                        }
                    }
                }
            }
        }
    };
}

mp_gcd_suite!(p324_3, sqisign::gf::P324);
mp_gcd_suite!(p500_27, sqisign::gf::P500);
mp_gcd_suite!(p664_17, sqisign::gf::P664);
