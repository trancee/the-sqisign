//! Chinese remaindering, Jacobi symbols and 2x2 matrix inversion modulo a
//! power of two (vendor `mp.c`: `ibz_crt`, `ibz_legendre`, `ibz_invmat`).
//! Variable time, like C.

use super::{Ibz, MpParams};

impl<P: MpParams> Ibz<P> {
    /// The `x` in `[0, m1 * m2)` with `x = a1 mod m1` and `x = a2 mod m2`,
    /// for coprime positive `m1` and `m2` (C `ibz_crt`).
    pub fn crt(a1: &Self, a2: &Self, m1: &Self, m2: &Self) -> Self {
        let (u, _) = m1.invmod(m2);
        let r = ((*a2 - *a1) * u).modulo(m2);
        (*a1 + *m1 * r).modulo(&(*m1 * *m2))
    }

    /// The Jacobi symbol `(self / p)` for odd positive `p` and non-negative
    /// `self` (C `ibz_legendre`). C does not terminate if `gcd(self, p) > 1`;
    /// Rust returns 0, the value of the symbol.
    pub fn legendre(&self, p: &Self) -> i32 {
        let (mut u, mut v) = (*self, *p);
        let mut sym = 1;
        while !u.is_one() {
            if u.is_zero() {
                return 0;
            }
            match u.limbs[0] % 4 {
                0 => u = u.div_2exp(2),
                2 => {
                    u = u.div_2exp(1);
                    if matches!(v.limbs[0] % 8, 3 | 5) {
                        sym = -sym;
                    }
                }
                _ if u >= v => u = u - v,
                u4 => {
                    if u4 == 3 && v.limbs[0] % 4 == 3 {
                        sym = -sym;
                    }
                    (u, v) = (v, u);
                }
            }
        }
        sym
    }

    /// `self^-1 mod 2^e` by Newton–Hensel lifting, for odd `self` and
    /// `e >= 1` (C `ibz_invmod_2e`, whose final reduction is a no-op then).
    fn invmod_2exp(&self, e: usize) -> Self {
        let (mut x, mut prec) = (Self::ONE, 1);
        while prec < e {
            let next = (2 * prec).min(e);
            x = (x * (Self::TWO - *self * x)).mod_2exp(next);
            prec = next;
        }
        x
    }

    /// The inverse of `[[r1, r2], [s1, s2]]` modulo `2^e`, as
    /// `[[r1, r2], [s1, s2]]` with entries in `[0, 2^e)`, and whether it exists
    /// (the determinant is odd); C `ibz_invmat`. The result depends only on
    /// the entries modulo `2^max(e, 1)`, which Rust reduces them to where C
    /// truncates their bounds to `e + 1`.
    ///
    /// # Panics
    ///
    /// If `e` is not below the capacity.
    pub fn invmat(m: &[[Self; 2]; 2], e: usize) -> ([[Self; 2]; 2], bool) {
        let [[a, b], [c, d]] = m.map(|row| row.map(|x| x.mod_2exp(e.max(1))));
        let det = a * d - b * c;
        let inv = det.invmod_2exp(e);
        let entry = |x: Self| (x * inv).mod_2exp(e);
        ([[entry(d), entry(-b)], [entry(-c), entry(a)]], det.is_odd())
    }
}
