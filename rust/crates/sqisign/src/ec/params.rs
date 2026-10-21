//! Curve constants of each parameter set (vendor `precomp/ref/<level>`).

use crate::gf::{Fp, Fp2, FpParams, P324, P500, P664};

/// Precomputed curve data of a parameter set.
pub trait EcParams: FpParams + Sized {
    /// `x(P)` of the fixed basis of `E0[2^T]` (C `BASIS_E0_PX`).
    const BASIS_E0_PX: Fp2<Self>;
    /// `x(Q)` of the fixed basis of `E0[2^T]` (C `BASIS_E0_QX`).
    const BASIS_E0_QX: Fp2<Self>;
}

/// The odd cofactor `(p + 1) / 2^T` (C `p_cofactor_for_2f`).
pub(super) fn cofactor<P: FpParams>() -> [u64; 1] {
    [P::C]
}

/// The bit length of [`cofactor`] (C `P_COFACTOR_FOR_2F_BITLENGTH`).
pub(super) fn cofactor_bits<P: FpParams>() -> usize {
    (64 - P::C.leading_zeros()) as usize
}

macro_rules! fp2 {
    ([$($re:literal),*], [$($im:literal),*]) => {
        Fp2 {
            re: Fp::from_montgomery([$($re),*]),
            im: Fp::from_montgomery([$($im),*]),
        }
    };
}

// `src/precomp/ref/p324_3/e0_basis.c`, RADIX 64 `SQISIGN_GF_IMPL_SAT64` branch.
impl EcParams for P324 {
    const BASIS_E0_PX: Fp2<Self> = fp2!(
        [
            0x15429ebdd6acc6c8,
            0x1bb9d347e507e96f,
            0x345b16e8f3d297c5,
            0x216fa909d6954b0f,
            0x34f99a0a51ded6ca,
            0xe
        ],
        [
            0xf8d03b133d9ee6ef,
            0xdd0cdd882713e200,
            0x7aab08998f08531b,
            0x13785435a9b9f681,
            0x20f45f7e1ba8367d,
            0x9
        ]
    );
    const BASIS_E0_QX: Fp2<Self> = fp2!(
        [
            0x92aa928d45684b84,
            0x6287060c7e5e212c,
            0x1818aad55dfc3fb0,
            0x2c34acd0ee182e1,
            0x83223a491fa840d9,
            0x21
        ],
        [
            0x934ae51618b44717,
            0x543fa24873173bce,
            0x43046078cb60d862,
            0x3fa561a4701041fe,
            0xfb2a0dfe18c42e16,
            0x13
        ]
    );
}

// `src/precomp/ref/p500_27/e0_basis.c`, RADIX 64 `SQISIGN_GF_IMPL_SAT64` branch.
impl EcParams for P500 {
    const BASIS_E0_PX: Fp2<Self> = fp2!(
        [
            0x27537a7c2a7e132e,
            0x7dba4c8b84aa5fb4,
            0xedcce6613445eff1,
            0xc7221dbc8c9b08c2,
            0x8972044718bb803f,
            0x24d280435ee3227e,
            0x6bc9cbd0a1a5ee78,
            0x1011f6d423f7ab6
        ],
        [
            0xbc6f683dde3c9631,
            0xd9af1e1b26dec9ce,
            0x926df5808cc89856,
            0x5155bf5c3e9e0734,
            0x53a15b6a90e0807b,
            0x504f540858432684,
            0xdbcba2df25bb0a9a,
            0x18f00d974092fe5
        ]
    );
    const BASIS_E0_QX: Fp2<Self> = fp2!(
        [
            0x457b9d7b21942b84,
            0x7814f149be2f088d,
            0x22f4b19a4c272bd4,
            0xc4810348e947c63d,
            0x7d4cfad791043c68,
            0x75e503b6a5dde2b,
            0xe1ad04e3ddd539f9,
            0x1326f58f1fc53b2
        ],
        [
            0x349263d0978d58f,
            0xb87c037608c2f01a,
            0x7dc946b571f2b99e,
            0xd8545a92678c9424,
            0x439fe829e61baf78,
            0xe3140d3203de7185,
            0xc68986b45b756727,
            0x32b9cbc60e42c5
        ]
    );
}

// `src/precomp/ref/p664_17/e0_basis.c`, RADIX 64 `SQISIGN_GF_IMPL_SAT64` branch.
impl EcParams for P664 {
    const BASIS_E0_PX: Fp2<Self> = fp2!(
        [
            0x9ee7b1cddd3559f0,
            0xc12d626b16e7f831,
            0x6e8a9c37304471ac,
            0x3a91511d582f6788,
            0x2af77787c35d2ebe,
            0xd2a2d68eddeb735,
            0xb8cdde3aa33c88e0,
            0x6a413264423297e7,
            0xf2929004b9a981a9,
            0x3a8a8b56e560ae9c,
            0x10612794
        ],
        [
            0x9674481708b16d15,
            0x7de13a9378bd9a1a,
            0xf4f9dbb17ec86c32,
            0xbfb000d851c583ab,
            0xc1b3af1fb0f09461,
            0xd380edebc28eda15,
            0xe094ccedcd2b2497,
            0xcbb4874482e31a51,
            0x2c37af3de3fb6672,
            0x16ffcbfc980ad048,
            0x597a4ed
        ]
    );
    const BASIS_E0_QX: Fp2<Self> = fp2!(
        [
            0xf9f502a4264444c7,
            0x9de2b170711d43ae,
            0xdd0743dadf5fadff,
            0xa0cd4480412a9bb4,
            0x2a362b94c8508596,
            0x4b1904c2df414af3,
            0xfe3982b7659ec94,
            0x2cbf157bf4c645b9,
            0x9e29887df0952b37,
            0x86c29bc4a96070e3,
            0x3532a6a
        ],
        [
            0x66bf5a7add5ed882,
            0xe45221e4b95cf21f,
            0x5d8cfe2aabb831d0,
            0xd3071a7beb935b78,
            0xafa5d3597c154ce8,
            0xfa623421e7ced432,
            0xa495fdb6e1c5c261,
            0x2aa681bbc2376c2,
            0x9c5214fb65e0f597,
            0xa985d1b959b9834a,
            0x525e053
        ]
    );
}
