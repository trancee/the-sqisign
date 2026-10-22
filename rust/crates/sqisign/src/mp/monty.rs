//! Montgomery arithmetic modulo an odd positive integer, the role of the
//! vendor `modqx.c`. C works in a 61-bit radix with lazily reduced residues
//! in `[0, 2n)`; every value it returns or compares is reduced first, so
//! Rust keeps residues fully reduced over saturated 64-bit words and gets the
//! same results. Variable time, like C.

use core::marker::PhantomData;

use super::div::{Words, W};
use super::{Ibz, MpParams};

/// `a * b + c + d` as (low, high) words.
fn mac(a: u64, b: u64, c: u64, d: u64) -> (u64, u64) {
    let t = u128::from(a) * u128::from(b) + u128::from(c) + u128::from(d);
    (t as u64, (t >> 64) as u64)
}

/// Residues modulo `n` in Montgomery form, `x * 2^(64k) mod n`.
pub(super) struct Monty<P> {
    modulus: Ibz<P>,
    n: Words,
    k: usize,
    ninv: u64,
    r2: Words,
    /// Montgomery 1.
    pub one: Words,
    _p: PhantomData<P>,
}

impl<P: MpParams> Monty<P> {
    /// The context of the odd `n > 1`.
    pub fn new(modulus: &Ibz<P>) -> Self {
        let n = modulus.magnitude();
        let k = super::div::len(&n);
        let mut inv = 1u64;
        for _ in 0..6 {
            inv = inv.wrapping_mul(2u64.wrapping_sub(n[0].wrapping_mul(inv)));
        }
        let mut m = Self {
            modulus: *modulus,
            n,
            k,
            ninv: inv.wrapping_neg(),
            r2: [0; W],
            one: [0; W],
            _p: PhantomData,
        };
        let mut x = [0; W];
        x[0] = 1;
        for i in 0..128 * k {
            x = m.add(&x, &x);
            if i + 1 == 64 * k {
                m.one = x;
            }
        }
        m.r2 = x;
        m
    }

    /// `a + b mod n`.
    pub fn add(&self, a: &Words, b: &Words) -> Words {
        let mut r = [0; W];
        let mut carry = false;
        for i in 0..=self.k {
            (r[i], carry) = a[i].carrying_add(b[i], carry);
        }
        self.reduce_once(r)
    }

    /// `a - b mod n`.
    pub fn sub(&self, a: &Words, b: &Words) -> Words {
        let neg = self.sub_raw(&self.n, b);
        self.add(a, &self.reduce_once(neg))
    }

    /// `a - b` over `k + 1` words, wrapping.
    fn sub_raw(&self, a: &Words, b: &Words) -> Words {
        let mut r = [0; W];
        let mut borrow = false;
        for i in 0..=self.k {
            (r[i], borrow) = a[i].borrowing_sub(b[i], borrow);
        }
        r
    }

    /// `a - n` if `a >= n`, for `a < 2n`.
    fn reduce_once(&self, a: Words) -> Words {
        let d = self.sub_raw(&a, &self.n);
        if d[self.k] >> 63 == 0 {
            d
        } else {
            a
        }
    }

    /// `a * b / 2^(64k) mod n` (CIOS).
    pub fn mul(&self, a: &Words, b: &Words) -> Words {
        let k = self.k;
        let mut t = [0u64; W + 1];
        for &ai in &a[..k] {
            let mut c = 0;
            for j in 0..k {
                (t[j], c) = mac(ai, b[j], t[j], c);
            }
            let (s, o) = t[k].overflowing_add(c);
            t[k] = s;
            t[k + 1] = u64::from(o);
            let m = t[0].wrapping_mul(self.ninv);
            let (_, mut c) = mac(m, self.n[0], t[0], 0);
            for j in 1..k {
                (t[j - 1], c) = mac(m, self.n[j], t[j], c);
            }
            let (s, o) = t[k].overflowing_add(c);
            t[k - 1] = s;
            t[k] = t[k + 1] + u64::from(o);
        }
        let mut r = [0; W];
        r[..=k].copy_from_slice(&t[..=k]);
        self.reduce_once(r)
    }

    /// `x mod n` in Montgomery form.
    pub fn to_mont(&self, x: &Ibz<P>) -> Words {
        self.mul(&x.modulo(&self.modulus).magnitude(), &self.r2)
    }

    /// The integer of a Montgomery residue, with the bound `bitlen`.
    pub fn redc(&self, x: &Words, bitlen: usize) -> Ibz<P> {
        let mut one = [0; W];
        one[0] = 1;
        Ibz::signed(&self.mul(x, &one), bitlen, false)
    }

    /// `x^e` for `e >= 0`.
    pub fn pow(&self, x: &Words, e: &Ibz<P>) -> Words {
        let mut r = self.one;
        for i in (0..e.bitsize()).rev() {
            r = self.mul(&r, &r);
            if (e.limbs[i / 64] >> (i % 64)) & 1 == 1 {
                r = self.mul(&r, x);
            }
        }
        r
    }

    /// `x^2`.
    pub fn sqr(&self, x: &Words) -> Words {
        self.mul(x, x)
    }

    /// `(x / 2) mod n`.
    pub fn half(&self, x: &Words) -> Words {
        let mut y = *x;
        if y[0] & 1 == 1 {
            let mut carry = false;
            for (w, &n) in y.iter_mut().zip(&self.n).take(self.k + 1) {
                (*w, carry) = w.carrying_add(n, carry);
            }
        }
        for i in 0..self.k {
            y[i] = (y[i] >> 1) | (y[i + 1] << 63);
        }
        y[self.k] >>= 1;
        y
    }

    /// The Montgomery form of the small `v`.
    pub fn small(&self, v: i32) -> Words {
        self.to_mont(&Ibz::from_i64(v.into(), 32))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::gf::P324;

    type I = Ibz<P324>;

    /// Moduli just below `2^(64k)` carry into the extra product word.
    #[test]
    fn mul_matches_integer_products_for_full_width_moduli() {
        for k in 1..=4 {
            for low in [1, 159, 0x8000_0001] {
                let mut w = vec![u64::MAX; k];
                w[0] = u64::MAX - low + 1;
                let n = I::from_digits(&w, k);
                let m = Monty::new(&n);
                for (da, db) in [(1, 1), (1, 2), (3, 7), (low, low + 2)] {
                    let a = n - I::from_digits(&[da], 1);
                    let b = n - I::from_digits(&[db], 1);
                    let got = m.redc(&m.mul(&m.to_mont(&a), &m.to_mont(&b)), n.bound());
                    assert_eq!(got, (a * b).modulo(&n), "{k} {low} {da} {db}");
                }
            }
        }
    }
}
