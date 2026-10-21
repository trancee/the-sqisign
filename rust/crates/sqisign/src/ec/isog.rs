//! Isogenies of degree `2^e` as chains of 4-isogenies (vendor `isog.c`).

use super::{Curve, Point};
use crate::gf::{Fp2, FpParams};

/// Constants to evaluate a 4-isogeny (C `ec_kps4_t`).
pub struct Kps4<P: FpParams> {
    k: [Fp2<P>; 3],
}

/// An isogeny of degree `2^length` (C `ec_isog_even_t`).
pub struct IsogEven<P: FpParams> {
    /// The domain.
    pub curve: Curve<P>,
    /// A generator of the kernel.
    pub kernel: Point<P>,
    /// The length as a walk of 2-isogenies.
    pub length: usize,
}

/// Room for the kernel point stack: `1 + ceil(log2 length)` entries.
const STACK: usize = 17;

impl<P: FpParams> Point<P> {
    /// The 4-isogeny with kernel `<self>`, for `[2] self != (0 : 1)`: its
    /// evaluation constants and the codomain as `(A + 2C : 4C)` (C `iso_xisog_4`).
    pub fn xisog_4(&self) -> (Kps4<P>, Point<P>) {
        let (t1, t2) = (self.x.square(), self.z.square());
        let b = Point::new((t2 + t1) * (t2 - t1), t2.square());
        let t2 = t2 + t2;
        let k = [t2 + t2, self.x - self.z, self.x + self.z];
        (Kps4 { k }, b)
    }
}

impl<P: FpParams> Kps4<P> {
    /// The image of `q` (C `iso_xeval_4` on one point).
    pub fn eval(&self, q: &Point<P>) -> Point<P> {
        let (t0, t1) = (q.x + q.z, q.x - q.z);
        let (x, z) = (t0 * self.k[1], t1 * self.k[2]);
        let t0 = t0 * t1 * self.k[0];
        let (t1, z) = ((x + z).square(), (x - z).square());
        Point::new((t0 + t1) * t1, z * (t0 - z))
    }
}

impl<P: FpParams> IsogEven<P> {
    /// The normalised codomain; `None` if the kernel does not have order
    /// `2^length` or lies above `(0 : 1)` (C `iso_isogeny_2chain`).
    ///
    /// # Panics
    /// If `length` is odd or exceeds `u16::MAX` (the C bookkeeping width).
    pub fn codomain(&self) -> Option<Curve<P>> {
        let len = self.length;
        assert!(
            len.is_multiple_of(2) && len <= 0xffff,
            "unsupported isogeny length"
        );
        let mut curve = self.curve;
        curve.normalize_a24();
        let mut a24 = curve.a24;

        let mut splits = [Point::IDENTITY; STACK];
        let mut todo = [0usize; STACK];
        (splits[0], todo[0]) = (self.kernel, len);
        let mut current = 0;

        for j in 0..(len / 2).saturating_sub(1) {
            // Walk down to a point of order 4, saving intermediate points.
            while todo[current] != 2 {
                current += 1;
                splits[current] = splits[current - 1];
                let dbls = todo[current - 1] / 4 * 2 + todo[current - 1] % 2;
                todo[current] = todo[current - 1] - dbls;
                for _ in 0..dbls {
                    splits[current] = splits[current].xdbl_a24(&a24, false);
                }
            }
            if j == 0 {
                if curve.is_four_torsion(&splits[current]) == 0 {
                    return None;
                }
                // Kernels above a canonical 4-torsion point are not allowed.
                if splits[current].xdbl_a24(&a24, false).x.is_zero() != 0 {
                    return None;
                }
            }
            let (kps, b) = splits[current].xisog_4();
            a24 = b;
            for (s, t) in splits.iter_mut().zip(todo.iter_mut()).take(current) {
                *s = kps.eval(s);
                *t -= 2;
            }
            current -= 1;
        }
        Curve::normalize_montgomery(&splits[current], None)
    }
}
