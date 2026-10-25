//! Differential tests: Rust `mp` primality and modular square roots against
//! the vendor C reference, including the default-domain PRNG draws.

#[macro_use]
mod support;

macro_rules! mp_modq_suite {
    ($module:ident, $params:ty) => {
        mod $module {
            use super::support::Rng;
            use sqisign::mp::Ibz;
            use sqisign::prng::{Prng, SEED_BYTES};
            use sqisign_reference::$module as c;

            type I = Ibz<$params>;
            mp_helpers!();

            /// The Rust and C PRNGs seeded from the same DRBG entropy.
            fn seeded(seed: u64) -> Prng {
                let entropy: [u8; SEED_BYTES] =
                    Rng::new(seed).bytes(SEED_BYTES).try_into().unwrap();
                Prng::new(&c::mp::prng_seed(&entropy))
            }

            /// Asserts both default streams are at the same position.
            fn in_step(p: &mut Prng) {
                let (r, x) = num(&[u64::MAX]);
                let got = I::rand_interval(&I::ZERO, &r, p.default_domain());
                same(&got, &c::mp::rand_interval(&c::mp::set(0, 1), &x, None).0);
            }

            /// The non-negative value of the little-endian `w`.
            fn num(w: &[u64]) -> (I, c::mp::Ibz) {
                let mut v = vec![0; c::mp::IBZ_NLIMBS];
                v[..w.len()].copy_from_slice(w);
                let b = 64 * w.len() + 1;
                pair(&v, b.min(CAP), &mut Rng::new(0))
            }

            fn small(v: u128) -> (I, c::mp::Ibz) {
                num(&[v as u64, (v >> 64) as u64])
            }

            /// `x * 2^s + 1`-style search: the first `x + k * step` that C
            /// reports prime, `x` odd.
            fn next_prime(w: &mut [u64], step: u64) -> (I, c::mp::Ibz) {
                loop {
                    let (r, x) = num(w);
                    if c::mp::probab_prime(&x, 1) != 0 {
                        return (r, x);
                    }
                    let mut carry = step;
                    for word in w.iter_mut() {
                        let (s, o) = word.overflowing_add(carry);
                        *word = s;
                        carry = u64::from(o);
                    }
                }
            }

            /// A random odd value of `bits` bits.
            fn random_odd(bits: usize, rng: &mut Rng) -> Vec<u64> {
                let mut w: Vec<u64> = (0..bits.div_ceil(64)).map(|_| rng.next_u64()).collect();
                let top = (bits - 1) % 64;
                let last = w.len() - 1;
                w[last] &= u64::MAX >> (63 - top);
                w[last] |= 1 << top;
                w[0] |= 1;
                w
            }

            /// Primes of several sizes, `= 1 mod 4` when `one_mod_4`, some
            /// with a large power of two in `p - 1`.
            fn primes(one_mod_4: bool, rng: &mut Rng) -> Vec<(I, c::mp::Ibz)> {
                let mut v = Vec::new();
                for bits in [3, 8, 33, 64, 65, 128, 300, 700] {
                    let mut w = random_odd(bits, rng);
                    w[0] = (w[0] & !3) | if one_mod_4 { 1 } else { 3 };
                    v.push(next_prime(&mut w, 4));
                }
                if one_mod_4 {
                    for s in [20, 63, 64, 100] {
                        let mut w = random_odd(s + 30, rng);
                        w.iter_mut().take(s / 64).for_each(|x| *x = 0);
                        w[s / 64] = (w[s / 64] >> (s % 64) << (s % 64)) | 1 << (s % 64);
                        w[0] |= 1;
                        let mut step = vec![0u64; w.len()];
                        step[s / 64] = 1 << (s % 64);
                        v.push(next_prime_by(&mut w, &step));
                    }
                }
                v
            }

            fn next_prime_by(w: &mut [u64], step: &[u64]) -> (I, c::mp::Ibz) {
                loop {
                    let (r, x) = num(w);
                    if c::mp::probab_prime(&x, 1) != 0 {
                        return (r, x);
                    }
                    let mut carry = false;
                    for (word, &s) in w.iter_mut().zip(step) {
                        (*word, carry) = word.carrying_add(s, carry);
                    }
                }
            }

            /// Composites that pass weak tests: Carmichael numbers, strong
            /// base-2 pseudoprimes (squares of Wieferich primes among them),
            /// Lucas pseudoprimes (the first two past the sieve), and base-2
            /// Fermat pseudoprimes that are not strong pseudoprimes.
            const HARD: [u128; 22] = [
                324_899,
                510_479,
                1_092_547,
                2_184_571,
                8_180_461,
                4_480_477,
                561,
                1105,
                1729,
                2047,
                3277,
                4033,
                4681,
                5459,
                5777,
                10877,
                1_194_649,
                12_327_121,
                3_215_031_751,
                2_152_302_898_747,
                3_825_123_056_546_413_051,
                318_665_857_834_031_151_167_461,
            ];

            #[test]
            fn probab_prime_matches_reference() {
                let mut p = seeded(30);
                let mut rng = Rng::new(31);
                let mut cases: Vec<_> = (0..1100).map(small).collect();
                cases.extend(HARD.iter().map(|&v| small(v)));
                cases.extend((1..40).map(|i| small((509 + 2 * i) as u128 * 1021)));
                // Every residue width of `probab_prime`, from one to twelve words and wider.
                for bits in [
                    40, 64, 65, 129, 250, 300, 380, 400, 500, 550, 600, 700, 1000,
                ] {
                    for _ in 0..30 {
                        cases.push(num(&random_odd(bits, &mut rng)));
                    }
                }
                cases.extend(primes(false, &mut rng));
                cases.extend(primes(true, &mut rng));
                for (r, x) in &cases {
                    for reps in [1, 24, 25, 30] {
                        let got = r.probab_prime(reps, p.default_domain());
                        assert_eq!(got, c::mp::probab_prime(x, reps as i32) != 0, "{r:?}");
                        in_step(&mut p);
                    }
                }
                for (r, x) in samples(32).iter().step_by(5) {
                    let got = r.probab_prime(26, p.default_domain());
                    assert_eq!(got, c::mp::probab_prime(x, 26) != 0, "{r:?}");
                    in_step(&mut p);
                }
            }

            /// Primes `random_odd(bits, Rng::new(seed)) + 2k`, of the widths
            /// on either side of the C capacity cutoff at each level.
            const EDGE_PRIMES: [(usize, u64, u64); 6] = [
                (1888, 60, 23),
                (1889, 61, 96),
                (2498, 62, 204),
                (2499, 63, 680),
                (3474, 64, 1558),
                (3475, 65, 1619),
            ];

            /// With 24 rounds: extra witnesses for primes this wide would
            /// overflow the C sampling buffer (`numbits < IBZ_NLIMBS * 64`).
            #[test]
            fn probab_prime_matches_reference_at_the_capacity_cutoff() {
                let mut p = seeded(35);
                let cutoff = 61 * (CAP - 1).div_ceil(61) - 63;
                let mut tested = 0;
                for (bits, seed, k) in EDGE_PRIMES {
                    if bits + 1 < cutoff || bits > cutoff {
                        continue;
                    }
                    let mut w = random_odd(bits, &mut Rng::new(seed));
                    w[0] = w[0].checked_add(2 * k).unwrap();
                    let (r, x) = num(&w);
                    let got = r.probab_prime(24, p.default_domain());
                    assert_eq!(got, bits < cutoff, "{bits}");
                    assert_eq!(got, c::mp::probab_prime(&x, 24) != 0, "{bits}");
                    in_step(&mut p);
                    tested += 1;
                }
                assert_eq!(tested, 2);
            }

            #[test]
            fn probab_prime_rejects_values_too_wide_for_c() {
                let mut p = seeded(33);
                let mut rng = Rng::new(34);
                for bits in [CAP - 70, CAP - 64, CAP - 40, CAP - 3] {
                    let (r, x) = loop {
                        let (r, x) = num(&random_odd(bits, &mut rng));
                        if (3..510).all(|q| !r.modulo(&I::from_i64(q, 32)).is_zero()) {
                            break (r, x);
                        }
                    };
                    let got = r.probab_prime(24, p.default_domain());
                    assert_eq!(got, c::mp::probab_prime(&x, 24) != 0, "{bits}");
                    in_step(&mut p);
                }
            }

            #[test]
            fn sqrt_m1_mod_verified_matches_reference() {
                let mut rng = Rng::new(40);
                let mut cases: Vec<_> = (0..3000).filter(|v| v % 4 == 1).map(small).collect();
                cases.extend(primes(true, &mut rng));
                for bits in [64, 200, 600] {
                    for _ in 0..10 {
                        let mut w = random_odd(bits, &mut rng);
                        w[0] = (w[0] & !3) | 1;
                        cases.push(num(&w));
                    }
                }
                let mut found = 0;
                for (r, x) in &cases {
                    let got = r.sqrt_m1_mod_verified();
                    let (want, ret) = c::mp::sqrt_m1_mod_verified(x);
                    assert_eq!(got.is_some(), ret != 0, "{r:?}");
                    if let Some(g) = got {
                        same(&g, &want);
                        found += 1;
                    }
                }
                assert!(found > 100, "{found}");
            }

            #[test]
            fn sqrt_mod_p_matches_reference() {
                let mut p = seeded(50);
                let mut rng = Rng::new(51);
                let mut ps = primes(false, &mut rng);
                ps.extend(primes(true, &mut rng));
                ps.extend([small(2), small(5), small(13), small(4), small(8), small(15)]);
                let (mut roots, mut fails) = (0, 0);
                for (rp, xp) in &ps {
                    let mut cases = vec![I::ZERO, I::ONE];
                    for _ in 0..8 {
                        let (a, _) = num(&random_odd(rp.bitsize() + 64, &mut rng));
                        let a = a.modulo(rp);
                        cases.push(a);
                        cases.push((a * a).modulo(rp));
                    }
                    for a in cases {
                        let mut v = vec![0; c::mp::IBZ_NLIMBS];
                        a.to_digits(&mut v);
                        let (ra, xa) = pair(&v, rp.bound(), &mut rng);
                        let got = ra.sqrt_mod_p(rp, p.default_domain());
                        let (want, ret) = c::mp::sqrt_mod_p(&xa, xp);
                        assert_eq!(got.is_some(), ret != 0, "{ra:?} mod {rp:?}");
                        match got {
                            Some(g) => {
                                same(&g, &want);
                                roots += 1;
                            }
                            None => fails += 1,
                        }
                        in_step(&mut p);
                    }
                }
                assert!(roots > 100 && fails > 50, "{roots} {fails}");
            }
        }
    };
}

mp_modq_suite!(p324_3, sqisign::gf::P324);
mp_modq_suite!(p500_27, sqisign::gf::P500);
mp_modq_suite!(p664_17, sqisign::gf::P664);
