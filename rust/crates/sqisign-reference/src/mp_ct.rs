//! Bindings for the C constant-time integer layer (`ibz_ct_*`), expanded
//! inside each level's `mp` module.

macro_rules! mp_ct_api {
    ($lvl:literal) => {
        mod ct_ffi {
            use super::{c_int, Ibz};
            ext!($lvl; ibz_ct_shift(r: *mut Ibz, a: *const Ibz, s: i32, m: i32, o: c_int));
            ext!($lvl; ibz_ct_shl(r: *mut Ibz, a: *const Ibz, s: i32, m: i32, o: c_int));
            ext!($lvl; ibz_ct_highmul_p(r: *mut Ibz, a: *const Ibz, b: *const Ibz, p: c_int,
                o: c_int));
            ext!($lvl; ibz_ct_highmul_s(r: *mut Ibz, a: *const Ibz, b: *const Ibz, s: i32,
                m: i32, o: c_int));
            ext!($lvl; ibz_ct_lt_mask(a: *const Ibz, b: *const Ibz) -> u64);
            ext!($lvl; ibz_ct_nonzero_mask(a: *const Ibz) -> u64);
            ext!($lvl; ibz_ct_fp_recip(w: *mut Ibz, s: *mut i32, d: *const Ibz, p: c_int,
                n: c_int));
            ext!($lvl; ibz_ct_round_shift_limb(q: *mut Ibz, x: *const Ibz, p: c_int, o: c_int));
        }

        /// C `ibz_ct_shl` (`left_only`) or `ibz_ct_shift`.
        pub fn ct_shift(a: &Ibz, shift: i32, max_shift: i32, out: c_int, left_only: bool) -> Ibz {
            let mut r = Ibz::default();
            // SAFETY: valid values.
            unsafe {
                if left_only {
                    ct_ffi::ibz_ct_shl(&mut r, a, shift, max_shift, out)
                } else {
                    ct_ffi::ibz_ct_shift(&mut r, a, shift, max_shift, out)
                }
            };
            r
        }

        /// C `ibz_ct_highmul_p`.
        pub fn ct_highmul_p(a: &Ibz, b: &Ibz, p: c_int, out: c_int) -> Ibz {
            let mut r = Ibz::default();
            // SAFETY: valid values.
            unsafe { ct_ffi::ibz_ct_highmul_p(&mut r, a, b, p, out) };
            r
        }

        /// C `ibz_ct_highmul_s`.
        pub fn ct_highmul_s(a: &Ibz, b: &Ibz, shift: i32, max_shift: i32, out: c_int) -> Ibz {
            let mut r = Ibz::default();
            // SAFETY: valid values.
            unsafe { ct_ffi::ibz_ct_highmul_s(&mut r, a, b, shift, max_shift, out) };
            r
        }

        /// C `ibz_ct_lt_mask`.
        pub fn ct_lt_mask(a: &Ibz, b: &Ibz) -> u64 {
            // SAFETY: valid values.
            unsafe { ct_ffi::ibz_ct_lt_mask(a, b) }
        }

        /// C `ibz_ct_nonzero_mask`.
        pub fn ct_nonzero_mask(a: &Ibz) -> u64 {
            // SAFETY: valid value.
            unsafe { ct_ffi::ibz_ct_nonzero_mask(a) }
        }

        /// C `ibz_ct_fp_recip`: `(W, s)`.
        pub fn ct_fp_recip(d: &Ibz, p: c_int, nrounds: c_int) -> (Ibz, i32) {
            let (mut w, mut s) = (Ibz::default(), 0);
            // SAFETY: valid values.
            unsafe { ct_ffi::ibz_ct_fp_recip(&mut w, &mut s, d, p, nrounds) };
            (w, s)
        }

        /// C `ibz_ct_round_shift_limb`.
        pub fn ct_round_shift_limb(x: &Ibz, p: c_int, out: c_int) -> Ibz {
            let mut r = Ibz::default();
            // SAFETY: valid values.
            unsafe { ct_ffi::ibz_ct_round_shift_limb(&mut r, x, p, out) };
            r
        }
    };
}
