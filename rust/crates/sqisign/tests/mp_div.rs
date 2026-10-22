//! Differential tests: Rust `mp` division, reduction and square roots
//! against the vendor C reference.

#[macro_use]
mod support;

macro_rules! mp_div_suite {
    ($module:ident, $params:ty) => {
        mod $module {
            use super::support::Rng;
            use sqisign::mp::Ibz;
            use sqisign_reference::$module as c;

            type I = Ibz<$params>;
            mp_helpers!();

            /// Samples plus operands that make the quotient estimate of
            /// algorithm D one too large.
            fn operands(seed: u64) -> Vec<(I, c::mp::Ibz)> {
                let mut rng = Rng::new(seed);
                let mut v = samples(seed);
                for w in [[3, 0, 1 << 63, 0], [1, 0, 1 << 61, 0]] {
                    v.push(pair(&w, 256, &mut rng));
                }
                v
            }

            #[test]
            fn div_rem_matches_reference() {
                let s = operands(20);
                for (ra, xa) in &s {
                    for (rb, xb) in s.iter().filter(|(r, _)| !r.is_zero()) {
                        let (q, r) = ra.div_rem(rb);
                        let (cq, cr) = c::mp::div(xa, xb);
                        same(&q, &cq);
                        same(&r, &cr);
                    }
                }
            }

            #[test]
            fn modulo_and_divides_match_reference() {
                let s = operands(21);
                for (ra, xa) in &s {
                    for (rb, xb) in s.iter().filter(|(r, _)| !r.is_zero()) {
                        same(&ra.modulo(rb), &c::mp::modulo(xa, xb));
                        assert_eq!(
                            -i32::from(ra.divides(rb)),
                            c::mp::divides(xa, xb),
                            "{ra:?} {rb:?}"
                        );
                    }
                }
            }

            #[test]
            fn multiples_divide_exactly() {
                let s = operands(22);
                for (ra, xa) in s.iter().step_by(5) {
                    for (rb, xb) in s.iter().filter(|(r, _)| !r.is_zero()).step_by(3) {
                        let (rp, xp) = (*ra * *rb, c::mp::mul(xa, xb));
                        assert_eq!(-i32::from(rp.divides(rb)), c::mp::divides(&xp, xb));
                        same(&rp.div_rem(rb).0, &c::mp::div(&xp, xb).0);
                    }
                }
            }

            #[test]
            fn mod_2exp_matches_reference() {
                let mut rng = Rng::new(23);
                for (r, x) in samples(23) {
                    let b = r.bound();
                    let mut es = vec![0, 1, 63, 64, 65, 128, b - 1, b, b + 1, CAP - 2, CAP - 1];
                    es.push(rng.next_u64() as usize % CAP);
                    for e in es.into_iter().filter(|&e| e < CAP) {
                        same(&r.mod_2exp(e), &c::mp::mod2exp(&x, e as u32));
                    }
                }
            }

            #[test]
            #[should_panic(expected = "exponent beyond the capacity")]
            fn mod_2exp_rejects_capacity() {
                let _ = I::ONE.mod_2exp(CAP);
            }

            #[test]
            fn sqrt_floor_matches_reference() {
                let s = samples(24);
                let mut v: Vec<_> = s.iter().filter(|(r, _)| r.is_positive()).copied().collect();
                for (r, x) in s.iter().filter(|(r, _)| r.bound() <= CAP / 2) {
                    let (sq, xsq) = (*r * *r, c::mp::mul(x, x));
                    for (d, xd) in [(I::ZERO, c::mp::set(0, 1)), (I::ONE, c::mp::set(1, 2))] {
                        v.push((sq - d, c::mp::sub(&xsq, &xd)));
                        v.push((sq + d, c::mp::add(&xsq, &xd)));
                    }
                }
                for (r, x) in v.into_iter().filter(|(r, _)| r.is_positive()) {
                    same(&r.sqrt_floor(), &c::mp::sqrt_floor(&x));
                }
            }
        }
    };
}

mp_div_suite!(p324_3, sqisign::gf::P324);
mp_div_suite!(p500_27, sqisign::gf::P500);
mp_div_suite!(p664_17, sqisign::gf::P664);
