//! Per-level quaternion constants (vendor `quaternion_constants.h`,
//! `SECURITY_BITS`).

use crate::gf::{P324, P500, P664};
use crate::mp::{Ibz, MpParams};

/// The quaternion parameters of a level.
pub trait QuatParams: MpParams + Copy {
    /// The security parameter `lambda` (C `SECURITY_BITS`).
    const SECURITY_BITS: usize;
    /// Bits of `p` (C `QUAT_P_BITS`).
    const P_BITS: usize = Self::BITS as usize;
    /// Bits of `isqrt(p)` (C `QUAT_SQRT_P_BITS`).
    const SQRT_P_BITS: usize = Self::P_BITS.div_ceil(2);
    /// Primality test rounds (C `QUAT_primality_num_iter`).
    const PRIMALITY_NUM_ITER: u32;
    /// Coefficient bound of the equivalent-ideal enumeration (C
    /// `QUAT_equiv_bound_coeff`).
    const EQUIV_BOUND_COEFF: i32;
    /// `(e, c, bound)` of the prime cofactor `2^e + c` (C
    /// `QUAT_prime_cofactor`, bound `bound`).
    const PRIME_COFACTOR: (usize, i64, usize);
    /// The exponent `e` of the norm `2^e` qlapoty targets (C
    /// `QUAT_qlapoty_used_power_of_two`).
    const QLAPOTY_POWER_OF_TWO: usize;

    /// The prime cofactor (C `QUAT_prime_cofactor`).
    fn prime_cofactor() -> Ibz<Self> {
        let (e, c, bound) = Self::PRIME_COFACTOR;
        let mut r = Ibz::ONE.mul_2exp(e) + Ibz::from_i64(c, 32);
        r.set_bound(bound);
        r
    }
}

impl QuatParams for P324 {
    const SECURITY_BITS: usize = 128;
    const PRIMALITY_NUM_ITER: u32 = 64;
    const EQUIV_BOUND_COEFF: i32 = 15;
    const PRIME_COFACTOR: (usize, i64, usize) = (146, 85, 148);
    const QLAPOTY_POWER_OF_TWO: usize = 322;
}

impl QuatParams for P500 {
    const SECURITY_BITS: usize = 192;
    const PRIMALITY_NUM_ITER: u32 = 96;
    const EQUIV_BOUND_COEFF: i32 = 19;
    const PRIME_COFACTOR: (usize, i64, usize) = (220, 217, 222);
    const QLAPOTY_POWER_OF_TWO: usize = 498;
}

impl QuatParams for P664 {
    const SECURITY_BITS: usize = 256;
    const PRIMALITY_NUM_ITER: u32 = 128;
    const EQUIV_BOUND_COEFF: i32 = 22;
    const PRIME_COFACTOR: (usize, i64, usize) = (286, 43, 288);
    const QLAPOTY_POWER_OF_TWO: usize = 662;
}
