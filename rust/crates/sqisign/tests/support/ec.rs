//! Paired Rust / C reference values for the `ec` differential suites.

#![allow(unused_macros)]

/// Defines helpers for one parameter set inside a test module.
macro_rules! ec_helpers {
    ($module:ident, $params:ty) => {
        use super::support::Rng;
        use sqisign::ec::{BaryCoords, Basis, Curve, Point};
        use sqisign::gf::Fp2;
        use sqisign_reference::$module as c;
        use sqisign_reference::$module::ec as ce;

        type F2 = Fp2<$params>;
        type Pt = Point<$params>;
        type Cv = Curve<$params>;
        type Bs = Basis<$params>;
        const BYTES: usize = 2 * c::FP_BYTES;
        const WORDS: usize = ce::ORDER_WORDS;

        /// A random canonical GF(p^2) encoding.
        fn rand_fe(rng: &mut Rng) -> Vec<u8> {
            let re = c::Fp::decode_reduce(&rng.bytes(c::FP_BYTES + 9)).encode();
            let im = c::Fp::decode_reduce(&rng.bytes(c::FP_BYTES + 9)).encode();
            [re, im].concat()
        }

        /// Both decodings of a canonical encoding.
        fn fe(b: &[u8]) -> (F2, c::Fp2) {
            let (r, ok) = F2::decode(b);
            assert_eq!(ok, u32::MAX, "canonical input must decode");
            (r, c::Fp2::decode(b).0)
        }

        fn enc(x: &F2) -> Vec<u8> {
            let mut out = vec![0u8; BYTES];
            x.encode(&mut out);
            out
        }

        fn point_from(x: &[u8], z: &[u8]) -> (Pt, ce::Point) {
            let ((rx, cx), (rz, cz)) = (fe(x), fe(z));
            (Pt::new(rx, rz), ce::Point { x: cx, z: cz })
        }

        fn point(rng: &mut Rng) -> (Pt, ce::Point) {
            point_from(&rand_fe(rng), &rand_fe(rng))
        }

        fn penc(p: &Pt) -> Vec<u8> {
            [enc(&p.x), enc(&p.z)].concat()
        }

        fn cpenc(p: &ce::Point) -> Vec<u8> {
            [p.x.encode(), p.z.encode()].concat()
        }

        fn assert_point(r: &Pt, c: &ce::Point, what: &str) {
            assert_eq!(penc(r), cpenc(c), "{what}");
        }

        /// The curve `(A : C)` with `A24` not computed.
        fn curve_from(a: &[u8], cc: &[u8]) -> (Cv, ce::Curve) {
            let ((ra, ca), (rc, cc)) = (fe(a), fe(cc));
            let r = Cv {
                a: ra,
                c: rc,
                ..Cv::default()
            };
            let mut cv = ce::curve_init();
            cv.a = ca;
            cv.c = cc;
            (r, cv)
        }

        fn curve(rng: &mut Rng) -> (Cv, ce::Curve) {
            curve_from(&rand_fe(rng), &rand_fe(rng))
        }

        fn assert_curve(r: &Cv, c: &ce::Curve, what: &str) {
            assert_eq!(enc(&r.a), c.a.encode(), "{what}: A");
            assert_eq!(enc(&r.c), c.c.encode(), "{what}: C");
            assert_eq!(r.a24_normalized, c.a24_normalized, "{what}: flag");
            assert_point(&r.a24, &c.a24, what);
        }

        fn basis(rng: &mut Rng) -> (Bs, ce::Basis) {
            let (p, cp) = point(rng);
            let (q, cq) = point(rng);
            let (d, cd) = point(rng);
            (
                Bs { p, q, pmq: d },
                ce::Basis {
                    p: cp,
                    q: cq,
                    pmq: cd,
                },
            )
        }

        fn scalar(rng: &mut Rng) -> Vec<u64> {
            (0..WORDS).map(|_| rng.next_u64()).collect()
        }

        fn small_scalar(v: u64) -> Vec<u64> {
            let mut s = vec![0u64; WORDS];
            s[0] = v;
            s
        }

        /// The C mirror of a Rust value, through its canonical encoding.
        fn c_fe(x: &F2) -> c::Fp2 {
            c::Fp2::decode(&enc(x)).0
        }

        fn c_point(p: &Pt) -> ce::Point {
            ce::Point {
                x: c_fe(&p.x),
                z: c_fe(&p.z),
            }
        }

        fn c_basis(b: &Bs) -> ce::Basis {
            ce::Basis {
                p: c_point(&b.p),
                q: c_point(&b.q),
                pmq: c_point(&b.pmq),
            }
        }

        fn c_curve(e: &Cv) -> ce::Curve {
            ce::Curve {
                a: c_fe(&e.a),
                c: c_fe(&e.c),
                a24: c_point(&e.a24),
                a24_normalized: e.a24_normalized,
            }
        }

        fn assert_basis(r: &Bs, c: &ce::Basis, what: &str) {
            assert_point(&r.p, &c.p, what);
            assert_point(&r.q, &c.q, what);
            assert_point(&r.pmq, &c.pmq, what);
        }

        /// `E0 : y^2 = x^3 + x` with `A24` normalised.
        fn e0() -> Cv {
            let mut e = Cv {
                c: F2::ONE,
                ..Cv::default()
            };
            e.normalize_with_a24();
            e
        }
    };
}
