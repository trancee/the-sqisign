//! Safe wrappers over the C reference integers (`mp/ref`).

macro_rules! mp_api {
    ($lvl:literal, $nlimbs:literal) => {
        /// C reference bounded integers.
        pub mod mp {
            use core::ffi::c_int;

            /// C `IBZ_NLIMBS`.
            pub const IBZ_NLIMBS: usize = $nlimbs;

            /// C `ibz_t`.
            #[repr(C)]
            #[derive(Clone, Copy, Debug)]
            pub struct Ibz {
                /// The bound, sign bit included.
                pub bitlen: c_int,
                /// Two's-complement limbs; only `ceil(bitlen / 64)` are
                /// significant.
                pub limbs: [u64; $nlimbs],
            }

            impl Default for Ibz {
                fn default() -> Self {
                    Self {
                        bitlen: 1,
                        limbs: [0; $nlimbs],
                    }
                }
            }

            mod ffi {
                use super::{c_int, Ibz};
                ext!($lvl; ibz_set(a: *mut Ibz, x: i64, bitlen: c_int));
                ext!($lvl; ibz_cswap(a: *mut Ibz, b: *mut Ibz, cond: u64));
                ext!($lvl; ibz_set_bound(a: *mut Ibz, bitlen: c_int));
                ext!($lvl; ibz_set_bound_ct(a: *mut Ibz, bitlen: c_int));
                ext!($lvl; ibz_copy_bits(t: *mut Ibz, src: *const u64, bitlen: c_int));
                ext!($lvl; ibz_copy_digits(t: *mut Ibz, src: *const u64, n: c_int));
                ext!($lvl; ibz_to_digits(t: *mut u64, src: *const Ibz));
                ext!($lvl; ibz_get(a: *const Ibz) -> i32);
                ext!($lvl; ibz_add(o: *mut Ibz, a: *const Ibz, b: *const Ibz));
                ext!($lvl; ibz_sub(o: *mut Ibz, a: *const Ibz, b: *const Ibz));
                ext!($lvl; ibz_mul(o: *mut Ibz, a: *const Ibz, b: *const Ibz));
                ext!($lvl; ibz_neg(o: *mut Ibz, a: *const Ibz));
                ext!($lvl; ibz_cneg(o: *mut Ibz, a: *const Ibz, mask: u64));
                ext!($lvl; ibz_abs(o: *mut Ibz, a: *const Ibz));
                ext!($lvl; ibz_mul_2exp(o: *mut Ibz, a: *const Ibz, e: u32));
                ext!($lvl; ibz_div_2exp(o: *mut Ibz, a: *const Ibz, e: u32));
                ext!($lvl; ibz_two_adic(a: *const Ibz) -> c_int);
                ext!($lvl; ibz_cmp(a: *const Ibz, b: *const Ibz) -> c_int);
                ext!($lvl; ibz_cmp_int32(a: *const Ibz, y: i32) -> c_int);
                ext!($lvl; ibz_is_zero(a: *const Ibz) -> c_int);
                ext!($lvl; ibz_is_one(a: *const Ibz) -> c_int);
                ext!($lvl; ibz_is_positive(a: *const Ibz) -> c_int);
                ext!($lvl; ibz_is_even(a: *const Ibz) -> c_int);
                ext!($lvl; ibz_is_odd(a: *const Ibz) -> c_int);
                ext!($lvl; ibz_bitsize(a: *const Ibz) -> c_int);
                ext!($lvl; ibz_bitsize_ct(a: *const Ibz) -> c_int);
                ext!($lvl; ibz_mul_by_int_and_set_bound(o: *mut Ibz, a: *const Ibz, b: i32,
                                                        bound: c_int));
                ext!($lvl; ibz_add_and_set_bound(o: *mut Ibz, a: *const Ibz, b: *const Ibz,
                                                 bound: c_int));
                ext!($lvl; ibz_add_int_and_set_bound(o: *mut Ibz, a: *const Ibz, b: i32,
                                                     bound: c_int));
                ext!($lvl; ibz_extract_u64(a: *const Ibz, offset: c_int) -> u64);
                ext!($lvl; ibz_extract_i64(a: *const Ibz, offset: c_int) -> i64);
                ext!($lvl; ibz_div(q: *mut Ibz, r: *mut Ibz, a: *const Ibz, b: *const Ibz));
                ext!($lvl; ibz_mod(r: *mut Ibz, a: *const Ibz, b: *const Ibz));
                ext!($lvl; ibz_mod2exp(r: *mut Ibz, a: *const Ibz, e: u32));
                ext!($lvl; ibz_divides(a: *const Ibz, b: *const Ibz) -> c_int);
                ext!($lvl; ibz_sqrt_floor(r: *mut Ibz, a: *const Ibz));
                ext!($lvl; ibz_gcd(g: *mut Ibz, a: *const Ibz, b: *const Ibz));
                ext!($lvl; ibz_xgcd(g: *mut Ibz, u: *mut Ibz, v: *mut Ibz, a: *const Ibz, b: *const Ibz));
                ext!($lvl; ibz_invmod(r: *mut Ibz, a: *const Ibz, m: *const Ibz) -> c_int);
                ext!($lvl; ibz_crt(x: *mut Ibz, a1: *const Ibz, a2: *const Ibz, m1: *const Ibz, m2: *const Ibz));
                ext!($lvl; ibz_legendre(a: *const Ibz, p: *const Ibz) -> c_int);
                ext!($lvl; ibz_invmat(r1: *mut Ibz, r2: *mut Ibz, s1: *mut Ibz, s2: *mut Ibz, e: c_int) -> c_int);
            }

            mp_unary!(ibz_neg => neg, ibz_abs => abs, ibz_sqrt_floor => sqrt_floor);
            mp_binary!(ibz_add => add, ibz_sub => sub, ibz_mul => mul, ibz_mod => modulo, ibz_gcd => gcd);
            mp_query!(c_int; ibz_two_adic => two_adic, ibz_is_zero => is_zero,
                      ibz_is_one => is_one, ibz_is_positive => is_positive,
                      ibz_is_even => is_even, ibz_is_odd => is_odd, ibz_bitsize => bitsize,
                      ibz_bitsize_ct => bitsize_ct);
            mp_query!(i32; ibz_get => get);

            /// C `ibz_set`.
            pub fn set(x: i64, bitlen: c_int) -> Ibz {
                let mut r = Ibz::default();
                // SAFETY: `r` is a valid `ibz_t`.
                unsafe { ffi::ibz_set(&mut r, x, bitlen) };
                r
            }

            /// C `ibz_cswap`, returning the swapped pair.
            pub fn cswap(a: &Ibz, b: &Ibz, cond: u64) -> (Ibz, Ibz) {
                let (mut x, mut y) = (*a, *b);
                // SAFETY: distinct valid values.
                unsafe { ffi::ibz_cswap(&mut x, &mut y, cond) };
                (x, y)
            }

            /// C `ibz_set_bound` (`ct == false`) or `ibz_set_bound_ct`.
            pub fn set_bound(a: &Ibz, bitlen: c_int, ct: bool) -> Ibz {
                let mut r = *a;
                // SAFETY: `r` is a valid `ibz_t`.
                unsafe {
                    if ct {
                        ffi::ibz_set_bound_ct(&mut r, bitlen)
                    } else {
                        ffi::ibz_set_bound(&mut r, bitlen)
                    }
                };
                r
            }

            /// C `ibz_copy_bits` (`digits == false`) or `ibz_copy_digits`
            /// over `n` bits or words of `src`, starting from `init`.
            pub fn copy_bits(init: &Ibz, src: &[u64], n: c_int, digits: bool) -> Ibz {
                let mut r = *init;
                // SAFETY: `src` holds the words read; `r` is valid.
                unsafe {
                    if digits {
                        ffi::ibz_copy_digits(&mut r, src.as_ptr(), n)
                    } else {
                        ffi::ibz_copy_bits(&mut r, src.as_ptr(), n)
                    }
                };
                r
            }

            /// C `ibz_to_digits` over `out`, which must hold the significant
            /// words.
            pub fn to_digits(a: &Ibz, out: &mut [u64]) {
                // SAFETY: the caller sizes `out`.
                unsafe { ffi::ibz_to_digits(out.as_mut_ptr(), a) };
            }

            /// C `ibz_cneg`.
            pub fn cneg(a: &Ibz, mask: u64) -> Ibz {
                let mut r = *a;
                // SAFETY: valid values.
                unsafe { ffi::ibz_cneg(&mut r, a, mask) };
                r
            }

            /// C `ibz_mul_2exp` (`left`) or `ibz_div_2exp`.
            pub fn shift(a: &Ibz, e: u32, left: bool) -> Ibz {
                let mut r = Ibz::default();
                // SAFETY: valid values.
                unsafe {
                    if left {
                        ffi::ibz_mul_2exp(&mut r, a, e)
                    } else {
                        ffi::ibz_div_2exp(&mut r, a, e)
                    }
                };
                r
            }

            /// C `ibz_cmp`.
            pub fn cmp(a: &Ibz, b: &Ibz) -> c_int {
                // SAFETY: valid values.
                unsafe { ffi::ibz_cmp(a, b) }
            }

            /// C `ibz_cmp_int32`.
            pub fn cmp_int32(a: &Ibz, y: i32) -> c_int {
                // SAFETY: valid value.
                unsafe { ffi::ibz_cmp_int32(a, y) }
            }

            /// C `ibz_mul_by_int_and_set_bound`.
            pub fn mul_by_int_and_set_bound(a: &Ibz, b: i32, bound: c_int) -> Ibz {
                let mut r = Ibz::default();
                // SAFETY: valid values.
                unsafe { ffi::ibz_mul_by_int_and_set_bound(&mut r, a, b, bound) };
                r
            }

            /// C `ibz_add_and_set_bound`.
            pub fn add_and_set_bound(a: &Ibz, b: &Ibz, bound: c_int) -> Ibz {
                let mut r = Ibz::default();
                // SAFETY: valid values.
                unsafe { ffi::ibz_add_and_set_bound(&mut r, a, b, bound) };
                r
            }

            /// C `ibz_add_int_and_set_bound`.
            pub fn add_int_and_set_bound(a: &Ibz, b: i32, bound: c_int) -> Ibz {
                let mut r = Ibz::default();
                // SAFETY: valid values.
                unsafe { ffi::ibz_add_int_and_set_bound(&mut r, a, b, bound) };
                r
            }

            /// C `ibz_extract_u64`.
            pub fn extract_u64(a: &Ibz, offset: c_int) -> u64 {
                // SAFETY: valid value.
                unsafe { ffi::ibz_extract_u64(a, offset) }
            }

            /// C `ibz_div`: quotient and remainder.
            pub fn div(a: &Ibz, b: &Ibz) -> (Ibz, Ibz) {
                let (mut q, mut r) = (Ibz::default(), Ibz::default());
                // SAFETY: distinct valid values.
                unsafe { ffi::ibz_div(&mut q, &mut r, a, b) };
                (q, r)
            }

            /// C `ibz_mod2exp`.
            pub fn mod2exp(a: &Ibz, e: u32) -> Ibz {
                let mut r = Ibz::default();
                // SAFETY: valid values.
                unsafe { ffi::ibz_mod2exp(&mut r, a, e) };
                r
            }

            /// C `ibz_xgcd`: `(g, u, v)`.
            pub fn xgcd(a: &Ibz, b: &Ibz) -> (Ibz, Ibz, Ibz) {
                let (mut g, mut u, mut v) = (Ibz::default(), Ibz::default(), Ibz::default());
                // SAFETY: distinct valid values.
                unsafe { ffi::ibz_xgcd(&mut g, &mut u, &mut v, a, b) };
                (g, u, v)
            }

            /// C `ibz_invmod`: the inverse and the status.
            pub fn invmod(a: &Ibz, m: &Ibz) -> (Ibz, c_int) {
                let mut r = Ibz::default();
                // SAFETY: valid values.
                let ok = unsafe { ffi::ibz_invmod(&mut r, a, m) };
                (r, ok)
            }

            /// C `ibz_crt`.
            pub fn crt(a1: &Ibz, a2: &Ibz, m1: &Ibz, m2: &Ibz) -> Ibz {
                let mut r = Ibz::default();
                // SAFETY: valid values.
                unsafe { ffi::ibz_crt(&mut r, a1, a2, m1, m2) };
                r
            }

            /// C `ibz_legendre`.
            pub fn legendre(a: &Ibz, p: &Ibz) -> c_int {
                // SAFETY: valid values.
                unsafe { ffi::ibz_legendre(a, p) }
            }

            /// C `ibz_invmat`, in place on `[[r1, r2], [s1, s2]]`.
            pub fn invmat(m: &mut [[Ibz; 2]; 2], e: c_int) -> c_int {
                let [[r1, r2], [s1, s2]] = m;
                // SAFETY: distinct valid values.
                unsafe { ffi::ibz_invmat(r1, r2, s1, s2, e) }
            }

            /// C `ibz_divides`.
            pub fn divides(a: &Ibz, b: &Ibz) -> c_int {
                // SAFETY: valid values.
                unsafe { ffi::ibz_divides(a, b) }
            }

            /// C `ibz_extract_i64`.
            pub fn extract_i64(a: &Ibz, offset: c_int) -> i64 {
                // SAFETY: valid value.
                unsafe { ffi::ibz_extract_i64(a, offset) }
            }

            mp_ct_api!($lvl);
            prng_api!($lvl);
        }
    };
}

macro_rules! mp_unary {
    ($($c:ident => $name:ident),*) => {$(
        #[doc = concat!("C `", stringify!($c), "`.")]
        pub fn $name(a: &Ibz) -> Ibz {
            let mut r = Ibz::default();
            // SAFETY: valid values.
            unsafe { ffi::$c(&mut r, a) };
            r
        }
    )*};
}

macro_rules! mp_binary {
    ($($c:ident => $name:ident),*) => {$(
        #[doc = concat!("C `", stringify!($c), "`.")]
        pub fn $name(a: &Ibz, b: &Ibz) -> Ibz {
            let mut r = Ibz::default();
            // SAFETY: valid values.
            unsafe { ffi::$c(&mut r, a, b) };
            r
        }
    )*};
}

macro_rules! mp_query {
    ($ret:ty; $($c:ident => $name:ident),*) => {$(
        #[doc = concat!("C `", stringify!($c), "`.")]
        pub fn $name(a: &Ibz) -> $ret {
            // SAFETY: valid value.
            unsafe { ffi::$c(a) }
        }
    )*};
}
