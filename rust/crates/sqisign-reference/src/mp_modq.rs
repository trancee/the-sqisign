//! Bindings for the C primality test and modular square roots, expanded
//! inside each level's `mp` module. All draw from the default PRNG domain.

macro_rules! mp_modq_api {
    ($lvl:literal) => {
        mod modq_ffi {
            use super::{c_int, Ibz};
            ext!($lvl; ibz_probab_prime(n: *const Ibz, reps: c_int) -> c_int);
            ext!($lvl; ibz_sqrt_m1_mod_verified(r: *mut Ibz, n: *const Ibz) -> c_int);
            ext!($lvl; ibz_sqrt_mod_p(r: *mut Ibz, a: *const Ibz, p: *const Ibz) -> c_int);
        }

        /// C `ibz_probab_prime`.
        pub fn probab_prime(n: &Ibz, reps: c_int) -> c_int {
            // SAFETY: a valid value.
            unsafe { modq_ffi::ibz_probab_prime(n, reps) }
        }

        /// C `ibz_sqrt_m1_mod_verified`.
        pub fn sqrt_m1_mod_verified(n: &Ibz) -> (Ibz, c_int) {
            let mut r = Ibz::default();
            // SAFETY: valid values.
            let ret = unsafe { modq_ffi::ibz_sqrt_m1_mod_verified(&mut r, n) };
            (r, ret)
        }

        /// C `ibz_sqrt_mod_p`.
        pub fn sqrt_mod_p(a: &Ibz, p: &Ibz) -> (Ibz, c_int) {
            let mut r = Ibz::default();
            // SAFETY: valid values.
            let ret = unsafe { modq_ffi::ibz_sqrt_mod_p(&mut r, a, p) };
            (r, ret)
        }
    };
}
