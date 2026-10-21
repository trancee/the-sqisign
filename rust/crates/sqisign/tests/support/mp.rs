//! Shared `mp` differential-test helpers, expanded inside a level module that
//! has `I` (the Rust `Ibz`), `c` (the C level) and `Rng` in scope.

#![allow(unused_macros)]

macro_rules! mp_helpers {
    () => {
        /// Capacity in bits (C `IBZ_MAX_BITS`).
        const CAP: usize = 64 * c::mp::IBZ_NLIMBS;

        /// Asserts Rust and C agree on the bound and the significant limbs.
        fn same(r: &I, x: &c::mp::Ibz) {
            assert_eq!(r.bound(), x.bitlen as usize, "bound of {r:?}");
            assert_eq!(r.limbs(), &x.limbs[..r.limbs().len()], "limbs");
        }

        /// A C value whose every limb is garbage.
        fn garbage(rng: &mut Rng) -> c::mp::Ibz {
            let mut x = c::mp::Ibz::default();
            x.limbs.iter_mut().for_each(|w| *w = rng.next_u64());
            x
        }

        /// The `b`-bit two's-complement value of `w` (non-negative when
        /// `b == CAP`), built by both implementations.
        fn pair(w: &[u64], b: usize, rng: &mut Rng) -> (I, c::mp::Ibz) {
            let b0 = b.min(CAP - 1);
            let mut r = I::from_bits(w, b0);
            r.set_bound(b);
            let x = c::mp::copy_bits(&garbage(rng), w, b0 as i32, false);
            let x = c::mp::set_bound(&x, b as i32, false);
            same(&r, &x);
            (r, x)
        }

        /// Word patterns for a bound `b`: zero, -1, 1, the minimum, the
        /// maximum, a power of two and random words.
        fn patterns(b: usize, rng: &mut Rng) -> Vec<Vec<u64>> {
            let n = c::mp::IBZ_NLIMBS;
            let bit = |k: usize| {
                let mut w = vec![0; n];
                w[k / 64] = 1 << (k % 64);
                w
            };
            let top = b - 1;
            let mut max = vec![u64::MAX; n];
            max[top / 64] &= !(1 << (top % 64));
            let mut v = vec![vec![0; n], vec![u64::MAX; n], bit(0), bit(top), max];
            v.push(bit(rng.next_u64() as usize % b));
            for _ in 0..3 {
                v.push((0..n).map(|_| rng.next_u64()).collect());
            }
            v
        }

        /// Boundary and random samples; negatives at the capacity come from
        /// widening and doubling.
        fn samples(seed: u64) -> Vec<(I, c::mp::Ibz)> {
            let mut rng = Rng::new(seed);
            let mut v = Vec::new();
            for b in [
                1,
                2,
                3,
                32,
                63,
                64,
                65,
                127,
                128,
                129,
                200,
                CAP / 2,
                CAP - 65,
                CAP - 64,
                CAP - 1,
                CAP,
            ] {
                for w in patterns(b, &mut rng) {
                    v.push(pair(&w, b, &mut rng));
                }
            }
            let top: Vec<_> = v
                .iter()
                .filter(|(r, _)| r.bound() == CAP - 1)
                .copied()
                .collect();
            for (r, x) in top {
                let mut w = r;
                w.set_bound(CAP);
                v.push((w, c::mp::set_bound(&x, CAP as i32, false)));
                v.push((r.mul_2exp(1), c::mp::shift(&x, 1, true)));
            }
            v
        }

        /// Interesting bounds around `b`.
        fn bounds(b: usize) -> Vec<usize> {
            let mut v = vec![1, 2, 31, 32, 63, 64, 65, 128, CAP - 1, CAP];
            v.extend([b.saturating_sub(64), b - 1, b, b + 1, b + 64]);
            v.retain(|&k| (1..=CAP).contains(&k));
            v
        }
    };
}
