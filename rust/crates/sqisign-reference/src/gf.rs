//! Safe wrappers over the C reference `fp`/`fp2` API of one parameter set.

macro_rules! unary {
    ($t:ty, $($name:ident => $f:ident),*) => {$(
        #[doc = concat!("C `", stringify!($f), "`.")]
        pub fn $name(&self) -> Self {
            let mut o = <$t>::default();
            // SAFETY: both pointers reference valid, initialised values.
            unsafe { ffi::$f(&mut o, self) };
            o
        }
    )*};
}

macro_rules! binary {
    ($t:ty, $($name:ident => $f:ident),*) => {$(
        #[doc = concat!("C `", stringify!($f), "`.")]
        pub fn $name(&self, b: &Self) -> Self {
            let mut o = <$t>::default();
            // SAFETY: all pointers reference valid, initialised values.
            unsafe { ffi::$f(&mut o, self, b) };
            o
        }
    )*};
}

macro_rules! in_place {
    ($($name:ident => $f:ident),*) => {$(
        #[doc = concat!("C `", stringify!($f), "` (in place, on a copy).")]
        pub fn $name(&self) -> Self {
            let mut o = *self;
            // SAFETY: the pointer references a valid, initialised value.
            unsafe { ffi::$f(&mut o) };
            o
        }
    )*};
}

macro_rules! predicate {
    ($($name:ident => $f:ident),*) => {$(
        #[doc = concat!("C `", stringify!($f), "` (0 or 0xFFFFFFFF).")]
        pub fn $name(&self) -> u32 {
            // SAFETY: the pointer references a valid, initialised value.
            unsafe { ffi::$f(self) }
        }
    )*};
}

macro_rules! common {
    ($t:ident, $len:expr, $enc:ident, $dec:ident, $small:ident, $mul_small:ident,
     $one:ident, $eq:ident, $sel:ident, $swap:ident) => {
        /// Decodes `src`; returns the value and the C validity mask.
        pub fn decode(src: &[u8]) -> (Self, u32) {
            assert_eq!(src.len(), $len);
            let mut d = Self::default();
            // SAFETY: `src` holds exactly the encoded length.
            let ok = unsafe { ffi::$dec(&mut d, src.as_ptr()) };
            (d, ok)
        }
        /// Canonical little-endian encoding.
        pub fn encode(&self) -> Vec<u8> {
            let mut out = vec![0u8; $len];
            // SAFETY: `out` holds exactly the encoded length.
            unsafe { ffi::$enc(out.as_mut_ptr(), self) };
            out
        }
        /// C `set_small`.
        pub fn small(v: u64) -> Self {
            let mut o = Self::default();
            // SAFETY: the pointer references a valid value.
            unsafe { ffi::$small(&mut o, v) };
            o
        }
        /// C `set_one`.
        pub fn one() -> Self {
            let mut o = Self::default();
            // SAFETY: the pointer references a valid value.
            unsafe { ffi::$one(&mut o) };
            o
        }
        /// C `mul_small`.
        pub fn mul_small(&self, v: u32) -> Self {
            let mut o = Self::default();
            // SAFETY: both pointers reference valid, initialised values.
            unsafe { ffi::$mul_small(&mut o, self, v) };
            o
        }
        /// C `is_equal`.
        pub fn is_equal(&self, b: &Self) -> u32 {
            // SAFETY: both pointers reference valid, initialised values.
            unsafe { ffi::$eq(self, b) }
        }
        /// C `select`: `a0` if `ctl == 0`, `a1` if `ctl == 0xFFFFFFFF`.
        pub fn select(a0: &Self, a1: &Self, ctl: u32) -> Self {
            let mut o = Self::default();
            // SAFETY: all pointers reference valid, initialised values.
            unsafe { ffi::$sel(&mut o, a0, a1, ctl) };
            o
        }
        /// C `cswap`, returning the swapped pair.
        pub fn cswap(a: &Self, b: &Self, ctl: u32) -> (Self, Self) {
            let (mut x, mut y) = (*a, *b);
            // SAFETY: both pointers reference distinct valid values.
            unsafe { ffi::$swap(&mut x, &mut y, ctl) };
            (x, y)
        }
    };
}

macro_rules! ext {
    ($lvl:literal; $name:ident ( $($arg:ident : $ty:ty),* ) $(-> $ret:ty)?) => {
        extern "C" {
            #[link_name = concat!("sqisign_", $lvl, "_ref_", stringify!($name))]
            pub fn $name($($arg: $ty),*) $(-> $ret)?;
        }
    };
}

macro_rules! level {
    (
        $module:ident, $lvl:literal, $words:literal, $bytes:literal, $order:literal,
        $nlimbs:literal
    ) => {
        #[doc = concat!("C reference for `", $lvl, "`.")]
        pub mod $module {
            use core::ffi::c_int;

            /// Encoded length of one GF(p) element.
            pub const FP_BYTES: usize = $bytes;

            /// A GF(p) element in the C reference representation.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct Fp([u64; $words]);

            /// A GF(p^2) element in the C reference representation.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct Fp2 {
                re: Fp,
                im: Fp,
            }

            ec_api!($lvl, $order);
            hd_api!($lvl);
            verify_api!($lvl, $order);
            mp_api!($lvl, $nlimbs);
            quat_api!($lvl);
            lattice_api!($lvl);
            lll_api!($lvl);
            ideal_api!($lvl);
            qlapoty_api!($lvl);
            id2iso_api!($lvl);

            mod ffi {
                use super::{c_int, Fp, Fp2};
                ext!($lvl; fp_set_small(x: *mut Fp, v: u64));
                ext!($lvl; fp_mul_small(x: *mut Fp, a: *const Fp, v: u32));
                ext!($lvl; fp_set_one(x: *mut Fp));
                ext!($lvl; fp_is_equal(a: *const Fp, b: *const Fp) -> u32);
                ext!($lvl; fp_is_zero(a: *const Fp) -> u32);
                ext!($lvl; fp_encode(dst: *mut u8, a: *const Fp));
                ext!($lvl; fp_decode(d: *mut Fp, src: *const u8) -> u32);
                ext!($lvl; fp_decode_reduce(d: *mut Fp, src: *const u8, len: usize));
                ext!($lvl; fp_select(d: *mut Fp, a0: *const Fp, a1: *const Fp, ctl: u32));
                ext!($lvl; fp_cswap(a: *mut Fp, b: *mut Fp, ctl: u32));
                ext!($lvl; fp_add(o: *mut Fp, a: *const Fp, b: *const Fp));
                ext!($lvl; fp_sub(o: *mut Fp, a: *const Fp, b: *const Fp));
                ext!($lvl; fp_mul(o: *mut Fp, a: *const Fp, b: *const Fp));
                ext!($lvl; fp_neg(o: *mut Fp, a: *const Fp));
                ext!($lvl; fp_sqr(o: *mut Fp, a: *const Fp));
                ext!($lvl; fp_half(o: *mut Fp, a: *const Fp));
                ext!($lvl; fp_div3(o: *mut Fp, a: *const Fp));
                ext!($lvl; fp_exp3div4(o: *mut Fp, a: *const Fp));
                ext!($lvl; fp_inv(x: *mut Fp));
                ext!($lvl; fp_sqrt(x: *mut Fp));
                ext!($lvl; fp_is_square(a: *const Fp) -> u32);
                ext!($lvl; fp2_set_small(x: *mut Fp2, v: u64));
                ext!($lvl; fp2_mul_small(x: *mut Fp2, a: *const Fp2, v: u32));
                ext!($lvl; fp2_set_one(x: *mut Fp2));
                ext!($lvl; fp2_is_zero(a: *const Fp2) -> u32);
                ext!($lvl; fp2_is_one(a: *const Fp2) -> u32);
                ext!($lvl; fp2_is_equal(a: *const Fp2, b: *const Fp2) -> u32);
                ext!($lvl; fp2_add(o: *mut Fp2, a: *const Fp2, b: *const Fp2));
                ext!($lvl; fp2_add_one(o: *mut Fp2, a: *const Fp2));
                ext!($lvl; fp2_sub(o: *mut Fp2, a: *const Fp2, b: *const Fp2));
                ext!($lvl; fp2_mul(o: *mut Fp2, a: *const Fp2, b: *const Fp2));
                ext!($lvl; fp2_neg(o: *mut Fp2, a: *const Fp2));
                ext!($lvl; fp2_sqr(o: *mut Fp2, a: *const Fp2));
                ext!($lvl; fp2_half(o: *mut Fp2, a: *const Fp2));
                ext!($lvl; fp2_frob(o: *mut Fp2, a: *const Fp2));
                ext!($lvl; fp2_mul_by_i(o: *mut Fp2, a: *const Fp2, ctl: u32));
                ext!($lvl; fp2_inv(x: *mut Fp2));
                ext!($lvl; fp2_sqrt(x: *mut Fp2));
                ext!($lvl; fp2_sqrt_verify(x: *mut Fp2) -> u32);
                ext!($lvl; fp2_is_square(a: *const Fp2) -> u32);
                ext!($lvl; fp2_batched_inv(x: *mut Fp2, len: c_int));
                ext!($lvl; fp2_pow_vartime(o: *mut Fp2, x: *const Fp2, e: *const u64, n: c_int));
                ext!($lvl; fp2_encode(dst: *mut u8, a: *const Fp2));
                ext!($lvl; fp2_decode(d: *mut Fp2, src: *const u8) -> u32);
                ext!($lvl; fp2_select(d: *mut Fp2, a0: *const Fp2, a1: *const Fp2, ctl: u32));
                ext!($lvl; fp2_cswap(a: *mut Fp2, b: *mut Fp2, ctl: u32));
                ext!($lvl; fp2_less_than(a: *const Fp2, b: *const Fp2) -> u32);
            }

            impl Fp {
                common!(Fp, FP_BYTES, fp_encode, fp_decode, fp_set_small, fp_mul_small,
                        fp_set_one, fp_is_equal, fp_select, fp_cswap);
                unary!(Fp, neg => fp_neg, sqr => fp_sqr, half => fp_half, div3 => fp_div3,
                       exp3div4 => fp_exp3div4);
                binary!(Fp, add => fp_add, sub => fp_sub, mul => fp_mul);
                in_place!(inv => fp_inv, sqrt => fp_sqrt);
                predicate!(is_zero => fp_is_zero, is_square => fp_is_square);

                /// C `fp_decode_reduce` over an arbitrary-length byte string.
                pub fn decode_reduce(src: &[u8]) -> Self {
                    let mut d = Self::default();
                    // SAFETY: `src` is valid for `src.len()` bytes.
                    unsafe { ffi::fp_decode_reduce(&mut d, src.as_ptr(), src.len()) };
                    d
                }
            }

            impl Fp2 {
                common!(Fp2, 2 * FP_BYTES, fp2_encode, fp2_decode, fp2_set_small, fp2_mul_small,
                        fp2_set_one, fp2_is_equal, fp2_select, fp2_cswap);
                unary!(Fp2, neg => fp2_neg, sqr => fp2_sqr, half => fp2_half, frob => fp2_frob,
                       add_one => fp2_add_one);
                binary!(Fp2, add => fp2_add, sub => fp2_sub, mul => fp2_mul);
                in_place!(inv => fp2_inv, sqrt => fp2_sqrt);
                predicate!(is_zero => fp2_is_zero, is_one => fp2_is_one,
                           is_square => fp2_is_square);

                /// C `fp2_mul_by_i`: times `i` if `ctl == 1`, times `-i` if `ctl == 0`.
                pub fn mul_by_i(&self, ctl: u32) -> Self {
                    let mut o = Self::default();
                    // SAFETY: both pointers reference valid, initialised values.
                    unsafe { ffi::fp2_mul_by_i(&mut o, self, ctl) };
                    o
                }
                /// C `fp2_sqrt_verify`: the root and the mask.
                pub fn sqrt_verify(&self) -> (Self, u32) {
                    let mut o = *self;
                    // SAFETY: the pointer references a valid, initialised value.
                    let ok = unsafe { ffi::fp2_sqrt_verify(&mut o) };
                    (o, ok)
                }
                /// C `fp2_less_than`.
                pub fn less_than(&self, b: &Self) -> u32 {
                    // SAFETY: both pointers reference valid, initialised values.
                    unsafe { ffi::fp2_less_than(self, b) }
                }
                /// C `fp2_batched_inv` on a copy of `xs` (must be non-empty).
                pub fn batched_inv(xs: &[Self]) -> Vec<Self> {
                    let mut v = xs.to_vec();
                    let len = c_int::try_from(v.len()).expect("length fits c_int");
                    // SAFETY: `v` holds `len` initialised values.
                    unsafe { ffi::fp2_batched_inv(v.as_mut_ptr(), len) };
                    v
                }
                /// C `fp2_pow_vartime` with a little-endian limb exponent.
                pub fn pow_vartime(&self, exp: &[u64]) -> Self {
                    let mut o = Self::default();
                    let n = c_int::try_from(exp.len()).expect("length fits c_int");
                    // SAFETY: `exp` holds `n` limbs; other pointers are valid.
                    unsafe { ffi::fp2_pow_vartime(&mut o, self, exp.as_ptr(), n) };
                    o
                }
            }
        }
    };
}
