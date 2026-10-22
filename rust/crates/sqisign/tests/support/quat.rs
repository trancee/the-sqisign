//! Shared `quat` differential-test helpers, expanded inside a level module
//! after `mp_helpers!()`, with `R` (the Rust `quat` module) and `c` in scope.

#![allow(unused_macros)]

macro_rules! quat_helpers {
    () => {
        /// The C value of `r`.
        fn to_c(r: &I) -> c::mp::Ibz {
            let mut x = c::mp::Ibz::default();
            x.bitlen = r.bound() as i32;
            x.limbs[..r.limbs().len()].copy_from_slice(r.limbs());
            x
        }

        /// Asserts Rust and C vectors agree.
        fn same_vec<const N: usize>(r: &[I; N], x: &[c::mp::Ibz; N]) {
            r.iter().zip(x).for_each(|(r, x)| same(r, x));
        }

        /// Asserts Rust and C matrices agree.
        fn same_mat<const N: usize>(r: &[[I; N]; N], x: &[[c::mp::Ibz; N]; N]) {
            r.iter().zip(x).for_each(|(r, x)| same_vec(r, x));
        }

        /// The C vector of `r`.
        fn vec_c<const N: usize>(r: &[I; N]) -> [c::mp::Ibz; N] {
            r.each_ref().map(to_c)
        }

        /// The C matrix of `r`.
        fn mat_c<const N: usize>(r: &[[I; N]; N]) -> [[c::mp::Ibz; N]; N] {
            r.each_ref().map(vec_c)
        }

        /// Boundary samples of bound at most `max_bits`, small integers of
        /// every sign and random values of a few sizes.
        fn pool(seed: u64, max_bits: usize) -> Vec<I> {
            let mut rng = Rng::new(seed);
            let mut v: Vec<I> = samples(seed)
                .into_iter()
                .map(|(r, _)| r)
                .filter(|r| r.bound() <= max_bits)
                .collect();
            v.extend((-9..=9).map(|k| I::from_i64(k, 5)));
            for bits in [20, 64, 130, max_bits / 2, max_bits - 1] {
                for _ in 0..4 {
                    let w: Vec<u64> = (0..c::mp::IBZ_NLIMBS).map(|_| rng.next_u64()).collect();
                    let x = I::from_bits(&w, bits);
                    v.push(if rng.next_u64() & 1 == 0 { x } else { -x });
                }
            }
            v
        }

        /// A random element of `pool`.
        fn pick(pool: &[I], rng: &mut Rng) -> I {
            pool[rng.next_u64() as usize % pool.len()]
        }

        /// A random non-zero element of `pool`.
        fn pick_nonzero(pool: &[I], rng: &mut Rng) -> I {
            loop {
                let x = pick(pool, rng);
                if !x.is_zero() {
                    return x;
                }
            }
        }

        /// A vector of random elements of `pool`.
        fn pick_vec<const N: usize>(pool: &[I], rng: &mut Rng) -> [I; N] {
            core::array::from_fn(|_| pick(pool, rng))
        }

        /// A matrix of random elements of `pool`.
        fn pick_mat<const N: usize>(pool: &[I], rng: &mut Rng) -> [[I; N]; N] {
            core::array::from_fn(|_| pick_vec(pool, rng))
        }
    };
}

/// Shared ideal differential-test helpers, expanded after `quat_helpers!()`
/// with `P` (the level), `I`, `Rng` and `c` in scope.
macro_rules! ideal_helpers {
    () => {
        use sqisign::prng::{Prng, Separator, SEED_BYTES};
        use sqisign::quat::{Alg, AlgElem, Draw, Ideal, Lattice, QuatParams};

        /// The Rust value of a C value, bound included.
        fn from_c(x: &c::mp::Ibz) -> I {
            let b = x.bitlen as usize;
            let mut r = I::from_bits(&x.limbs, b.min(CAP - 1));
            r.set_bound(b);
            r
        }

        /// The Rust element of a C element.
        fn elem(x: &c::quat::AlgElem) -> AlgElem<P> {
            AlgElem {
                denom: from_c(&x.denom),
                coord: x.coord.each_ref().map(from_c),
            }
        }

        /// The C element of a Rust element.
        fn elem_c(x: &AlgElem<P>) -> c::quat::AlgElem {
            c::quat::AlgElem {
                denom: to_c(&x.denom),
                coord: vec_c(&x.coord),
            }
        }

        /// The Rust ideal of a C ideal.
        fn ideal(x: &c::ideal::Ideal) -> Ideal<P> {
            Ideal {
                x: from_c(&x.x),
                y: from_c(&x.y),
                norm: from_c(&x.norm),
            }
        }

        /// Asserts Rust and C elements agree.
        fn same_elem(r: &AlgElem<P>, x: &c::quat::AlgElem) {
            same(&r.denom, &x.denom);
            same_vec(&r.coord, &x.coord);
        }

        /// Asserts Rust and C ideals agree.
        fn same_ideal(r: &Ideal<P>, x: &c::ideal::Ideal) {
            same(&r.x, &x.x);
            same(&r.y, &x.y);
            same(&r.norm, &x.norm);
        }

        /// Asserts a Rust ideal agrees with C's, `None` matching an output C
        /// left as initialised.
        fn same_opt_ideal(r: &Option<Ideal<P>>, x: &c::ideal::Ideal) {
            same_ideal(&r.unwrap_or_default(), x);
            assert_eq!(r.is_none(), c::mp::cmp_int32(&x.norm, 0) == 0);
        }

        /// Asserts Rust and C lattices agree.
        fn same_lattice(r: &Lattice<P>, x: &c::lattice::Lattice) {
            same(&r.denom, &x.denom);
            same_mat(&r.basis, &x.basis);
        }

        /// The Rust and C PRNGs seeded from the same DRBG entropy.
        fn seeded(seed: u64) -> Prng {
            let entropy: [u8; SEED_BYTES] = Rng::new(seed).bytes(SEED_BYTES).try_into().unwrap();
            Prng::new(c::mp::prng_seed(&entropy))
        }

        /// The C stream of `sep`.
        fn c_domain(sep: Separator) -> c::mp::Domain {
            c::mp::Domain::new(sep.bytes()).unwrap()
        }

        /// Asserts both default streams are at the same position.
        fn in_step(p: &mut Prng) {
            let r = I::from_i64(i32::MAX.into(), 32);
            let got = I::rand_interval(&I::ZERO, &r, p.default_domain());
            same(
                &got,
                &c::mp::rand_interval(&c::mp::set(0, 1), &to_c(&r), None).0,
            );
        }

        /// `n` pairs of C ideals of distinct random primes of `bits` bits,
        /// drawn from C's default stream (desynchronising it).
        fn ideal_pairs(bits: i32, n: usize, seed: u8) -> Vec<[(Ideal<P>, c::ideal::Ideal); 2]> {
            c::mp::prng_seed(&[seed; 48]);
            let mut d = c::mp::Domain::new(b"tst").unwrap();
            let mut out = Vec::new();
            while out.len() < n {
                let na = c::ideal::random_prime(false, bits);
                let nb = c::ideal::random_prime(false, bits);
                if c::mp::cmp(&na, &nb) == 0 {
                    continue;
                }
                let a = c::ideal::random_ideal_given_prime_norm(&na, &mut d);
                let b = c::ideal::random_ideal_given_prime_norm(&nb, &mut d);
                out.push([(ideal(&a), a), (ideal(&b), b)]);
            }
            out
        }

        /// Norm-bit sizes from tiny to the signing path's
        /// `QUAT_P_BITS / 2 + 15`.
        fn norm_bits() -> [i32; 5] {
            let resp = (P::P_BITS / 2 + 15) as i32;
            [3, 17, 64, resp / 2, resp]
        }
    };
}
