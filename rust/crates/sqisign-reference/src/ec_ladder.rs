//! Safe wrappers over the C reference `ec` doubling chains and ladders (`ec.c`).

macro_rules! ec_ladder_api {
    ($lvl:literal) => {
        mod ladder_ffi {
            use super::{c_int, Basis, Curve, Point};
            ext!($lvl; ec_dbl(r: *mut Point, p: *const Point, e: *const Curve));
            ext!($lvl; ec_dbl_iter(r: *mut Point, n: c_int, p: *const Point, e: *mut Curve));
            ext!($lvl; ec_dbl_iter_basis(r: *mut Basis, n: c_int, b: *const Basis, e: *mut Curve));
            ext!($lvl; ec_mul(r: *mut Point, k: *const u64, kbits: c_int, p: *const Point,
                              e: *mut Curve));
            ext!($lvl; ec_ladder3pt(r: *mut Point, m: *const u64, p: *const Point,
                                    q: *const Point, pq: *const Point, e: *const Curve) -> c_int);
            ext!($lvl; ec_biscalar_mul(r: *mut Point, k: *const u64, l: *const u64, kbits: c_int,
                                       b: *const Basis, e: *const Curve) -> c_int);
            ext!($lvl; ec_biscalar_mul_verif(r: *mut Point, k: *const u64, l: *const u64,
                                             kbits: c_int, b: *const Basis, e: *mut Curve)
                                             -> c_int);
        }

        fn bits(kbits: usize, words: usize) -> c_int {
            assert!(kbits <= 64 * words, "scalar shorter than kbits");
            c_int::try_from(kbits).expect("kbits fits c_int")
        }

        /// C `ec_dbl`.
        pub fn dbl(p: &Point, e: &Curve) -> Point {
            let mut r = Point::default();
            // SAFETY: all pointers reference valid values.
            unsafe { ladder_ffi::ec_dbl(&mut r, p, e) };
            r
        }

        /// C `ec_dbl_iter`; returns the point and the possibly normalised curve.
        pub fn dbl_iter(p: &Point, n: i32, e: &Curve) -> (Point, Curve) {
            let (mut r, mut c) = (Point::default(), *e);
            // SAFETY: all pointers reference valid values.
            unsafe { ladder_ffi::ec_dbl_iter(&mut r, n, p, &mut c) };
            (r, c)
        }

        /// C `ec_dbl_iter_basis`.
        pub fn dbl_iter_basis(b: &Basis, n: i32, e: &Curve) -> (Basis, Curve) {
            let (mut r, mut c) = (Basis::default(), *e);
            // SAFETY: all pointers reference valid values.
            unsafe { ladder_ffi::ec_dbl_iter_basis(&mut r, n, b, &mut c) };
            (r, c)
        }

        /// C `ec_mul`.
        pub fn mul(k: &[u64], kbits: usize, p: &Point, e: &Curve) -> (Point, Curve) {
            let kb = bits(kbits, k.len());
            let (mut r, mut c) = (Point::default(), *e);
            // SAFETY: `k` holds at least `kbits` bits; other pointers are valid.
            unsafe { ladder_ffi::ec_mul(&mut r, k.as_ptr(), kb, p, &mut c) };
            (r, c)
        }

        /// C `ec_ladder3pt` (`m` holds `ORDER_WORDS` words).
        pub fn ladder3pt(m: &[u64], p: &Point, q: &Point, pq: &Point, e: &Curve) -> (Point, i32) {
            assert_eq!(m.len(), ORDER_WORDS);
            let mut r = Point::default();
            // SAFETY: `m` holds `NWORDS_ORDER` words; other pointers are valid.
            let ok = unsafe { ladder_ffi::ec_ladder3pt(&mut r, m.as_ptr(), p, q, pq, e) };
            (r, ok)
        }

        /// C `ec_biscalar_mul`.
        pub fn biscalar_mul(k: &[u64], l: &[u64], kbits: usize, b: &Basis, e: &Curve)
            -> (Point, i32) {
            let kb = bits(kbits, k.len().min(l.len()));
            let mut r = Point::default();
            // SAFETY: `k`, `l` hold at least `kbits` bits; other pointers are valid.
            let ok = unsafe { ladder_ffi::ec_biscalar_mul(&mut r, k.as_ptr(), l.as_ptr(), kb, b, e) };
            (r, ok)
        }

        /// C `ec_biscalar_mul_verif`; also returns the possibly normalised curve.
        pub fn biscalar_mul_verif(k: &[u64], l: &[u64], kbits: usize, b: &Basis, e: &Curve)
            -> (Point, i32, Curve) {
            let kb = bits(kbits, k.len().min(l.len()));
            let (mut r, mut c) = (Point::default(), *e);
            // SAFETY: `k`, `l` hold at least `kbits` bits; other pointers are valid.
            let ok = unsafe {
                ladder_ffi::ec_biscalar_mul_verif(&mut r, k.as_ptr(), l.as_ptr(), kb, b, &mut c)
            };
            (r, ok, c)
        }
    };
}
