//! Differential tests: Rust and C verification reject forged, malleated
//! and malformed signatures alike.

#[macro_use]
mod support;

use support::kat;

/// `2^f - x` (for `x < 2^f`), little-endian words.
fn neg_mod_pow2(x: &[u64; 11], f: usize) -> [u64; 11] {
    let mut out = [0u64; 11];
    let mut borrow = 0u64;
    for (i, (o, &w)) in out.iter_mut().zip(x).enumerate() {
        let top = if i == f / 64 { 1u64 << (f % 64) } else { 0 };
        let (d, b1) = top.overflowing_sub(w);
        let (d, b2) = d.overflowing_sub(borrow);
        *o = d;
        borrow = u64::from(b1 || b2);
    }
    out
}

macro_rules! reject_suite {
    ($module:ident, $params:ty, $kat:literal) => {
        #[allow(dead_code, unused_imports)]
        mod $module {
            ec_helpers!($module, $params);
            use super::{kat, neg_mod_pow2};
            use sqisign::verify::{self, Signature, SqisignParams};
            use sqisign_reference::$module::verify as cv;

            type P = $params;
            const SIG: usize = <P as SqisignParams>::SIGNATURE_BYTES;
            const F: usize = <P as SqisignParams>::RESPONSE_BITS as usize + 2;

            /// Rust and C give `valid` on `sig`.
            fn check(sig: &[u8], msg: &[u8], pk: &[u8], valid: bool, what: &str) {
                let r = verify::verify::<P>(sig, msg, pk);
                let c = cv::protocols_verify(
                    &cv::signature_from_bytes(sig),
                    &cv::public_key_from_bytes(pk),
                    msg,
                );
                assert_eq!((r, c), (valid, valid), "{what}");
            }

            fn entry() -> (Vec<u8>, Vec<u8>, Vec<u8>) {
                let k = kat::load($kat).swap_remove(3);
                let (sig, msg) = k.sm.split_at(SIG);
                (sig.to_vec(), msg.to_vec(), k.pk)
            }

            fn tweak(sig: &[u8], f: impl FnOnce(&mut Signature<P>)) -> Vec<u8> {
                let mut s = Signature::<P>::decode(sig).expect("sig length");
                f(&mut s);
                s.to_bytes()
            }

            /// Bit `bit` of signature byte `i` is one of the top two bits
            /// (`2^(f-1)`, `2^(f-2)`) of a matrix entry: SQIsign is not
            /// strongly unforgeable (upstream #9, #18), and both accept some
            /// flips of those.
            fn malleable(i: usize, bit: usize) -> bool {
                let resp = <P as SqisignParams>::RESPONSE_BYTES;
                let start = SIG - 4 * resp - <P as SqisignParams>::CHALLENGE_BYTES - 2;
                let pos = i.wrapping_sub(start) % resp * 8 + bit;
                (start..start + 4 * resp).contains(&i) && pos + 2 >= F
            }

            /// Every signature bit flip is rejected by both, except
            /// `malleable` bits. Optimised builds flip every bit; unoptimised
            /// ones (the coverage job), where verification is slow, bit
            /// `i % 8` of byte `i`.
            #[test]
            fn bit_flips_are_rejected() {
                let (sig, msg, pk) = entry();
                for i in 0..SIG {
                    let bits = if cfg!(debug_assertions) {
                        i % 8..i % 8 + 1
                    } else {
                        0..8
                    };
                    for bit in bits {
                        let mut s = sig.clone();
                        s[i] ^= 1 << bit;
                        let what = format!("sig byte {i} bit {bit}");
                        let r = verify::verify::<P>(&s, &msg, &pk);
                        check(&s, &msg, &pk, r, &what);
                        assert!(!r || malleable(i, bit), "{what}");
                    }
                }
                for i in 0..msg.len().min(16) {
                    let mut m = msg.clone();
                    m[i] ^= 0x80 >> (i % 8);
                    check(&sig, &m, &pk, false, &format!("msg byte {i}"));
                }
                let mut m = msg.clone();
                m.push(0);
                check(&sig, &m, &pk, false, "longer msg");
            }

            /// Every undersized signature and one or sixteen appended bytes
            /// are rejected before decoding; `open` reads trailing bytes as
            /// the message (upstream #23). Rust only: C reads past short input.
            #[test]
            fn wrong_lengths_are_rejected() {
                let (sig, msg, pk) = entry();
                for len in 0..SIG {
                    assert!(
                        !verify::verify::<P>(&sig[..len], &msg, &pk),
                        "sig len {len}"
                    );
                }
                for extra in [1, 16] {
                    let mut s = sig.clone();
                    s.extend(&msg[..extra]);
                    assert!(!verify::verify::<P>(&s, &msg, &pk), "sig + {extra}");
                }
                for len in [0, pk.len() - 1, pk.len() + 1] {
                    let mut k = pk.clone();
                    k.resize(len, 0);
                    assert!(!verify::verify::<P>(&sig, &msg, &k), "pk len {len}");
                }
                let sm = [&sig[..], &msg[..]].concat();
                assert_eq!(verify::open::<P>(&sm, &pk), Some(&msg[..]));
                assert_eq!(verify::open::<P>(&sm[..SIG - 1], &pk), None);
            }

            /// Flipping bit 0 of any `E_aux` byte of KAT entry 0 makes the
            /// gluing fail, which C ignores and reads uninitialised memory
            /// (upstream #26). Rust only: the C run is undefined behaviour.
            #[test]
            fn failed_gluing_is_rejected() {
                let k = kat::load($kat).swap_remove(0);
                let (sig, msg) = k.sm.split_at(SIG);
                for i in 0..BYTES {
                    let mut s = sig.to_vec();
                    s[i] ^= 1;
                    assert!(!verify::verify::<P>(&s, msg, &k.pk), "aux byte {i}");
                }
            }

            /// Random runs of 1 to 8 bytes overwritten in KAT signatures
            /// (upstream #15): no panic, Rust agrees with C, and a signature is
            /// accepted only if every changed bit is `malleable`. Runs starting
            /// in `E_aux` are Rust only, as in `failed_gluing_is_rejected`.
            #[test]
            fn random_mutations_are_rejected() {
                let kats = kat::load($kat);
                let mut rng = super::support::Rng::new(15);
                let n = if cfg!(debug_assertions) { 24 } else { 300 };
                for t in 0..n {
                    let k = &kats[t % kats.len()];
                    let (sig, msg) = k.sm.split_at(SIG);
                    let at = rng.next_u64() as usize % SIG;
                    let end = (at + 1 + rng.next_u64() as usize % 8).min(SIG);
                    let mut s = sig.to_vec();
                    s[at..end].copy_from_slice(&rng.bytes(end - at));
                    let what = format!("mutation {t}: bytes {at}..{end}");
                    let r = verify::verify::<P>(&s, msg, &k.pk);
                    if at >= BYTES {
                        check(&s, msg, &k.pk, r, &what);
                    }
                    let benign = (at..end)
                        .all(|i| (0..8).all(|b| (s[i] ^ sig[i]) >> b & 1 == 0 || malleable(i, b)));
                    assert!(!r || benign, "{what}");
                }
            }

            #[test]
            fn malleated_matrices_are_rejected() {
                let (sig, msg, pk) = entry();
                check(&sig, &msg, &pk, true, "original");
                let neg = tweak(&sig, |s| {
                    for x in s.mat.iter_mut().flatten() {
                        if x.iter().any(|&w| w != 0) {
                            *x = neg_mod_pow2(x, F);
                        }
                    }
                });
                check(&neg, &msg, &pk, false, "-M");
                if 8 * <P as SqisignParams>::RESPONSE_BYTES > F {
                    for (i, j) in [(0, 0), (0, 1), (1, 0), (1, 1)] {
                        let big = tweak(&sig, |s| s.mat[i][j][F / 64] |= 1 << (F % 64));
                        check(&big, &msg, &pk, false, &format!("M[{i}][{j}] + 2^f"));
                    }
                }
                let zero = tweak(&sig, |s| {
                    s.mat[0][0] = [0; 11];
                    s.mat[1][0] = [0; 11];
                });
                check(&zero, &msg, &pk, false, "zero column");
                let zero = tweak(&sig, |s| {
                    s.mat[0][1] = [0; 11];
                    s.mat[1][1] = [0; 11];
                });
                check(&zero, &msg, &pk, false, "zero second column");
                let zero = tweak(&sig, |s| s.mat = [[[0; 11]; 2]; 2]);
                check(&zero, &msg, &pk, false, "zero matrix");
            }

            #[test]
            fn wrong_keys_and_curves_are_rejected() {
                let (sig, msg, pk) = entry();
                let other = kat::load($kat).swap_remove(4).pk;
                check(&sig, &msg, &other, false, "other key");
                for a in [F2::from_small(2), -F2::from_small(2)] {
                    let mut bad = enc(&a);
                    bad.push(pk[pk.len() - 1]);
                    check(&sig, &msg, &bad, false, "singular pk");
                    let s = tweak(&sig, |s| s.aux_a = a);
                    check(&s, &msg, &pk, false, "singular aux");
                }
                for h in 0..=u8::MAX {
                    let mut k = pk.clone();
                    *k.last_mut().expect("hint") = h;
                    check(&sig, &msg, &k, k == pk, &format!("pk hint {h}"));
                }
                for h in 0..=u8::MAX {
                    let s = tweak(&sig, |s| s.hint_aux = h);
                    check(&s, &msg, &pk, s == sig, &format!("aux hint {h}"));
                    let s = tweak(&sig, |s| s.hint_chall = h);
                    check(&s, &msg, &pk, s == sig, &format!("chall hint {h}"));
                }
            }
        }
    };
}

reject_suite!(
    p324_3,
    sqisign::gf::P324,
    "PQCsignKAT_270_SQIsign_p324_3.rsp"
);
reject_suite!(
    p500_27,
    sqisign::gf::P500,
    "PQCsignKAT_417_SQIsign_p500_27.rsp"
);
reject_suite!(
    p664_17,
    sqisign::gf::P664,
    "PQCsignKAT_549_SQIsign_p664_17.rsp"
);
