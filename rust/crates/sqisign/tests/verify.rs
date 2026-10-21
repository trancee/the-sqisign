//! Differential tests: Rust verification, encodings and SHAKE256 against
//! the C reference and the NIST KAT files.

#[macro_use]
mod support;

use support::{kat, Rng, SAMPLES};

#[test]
fn shake256_matches_reference() {
    let mut rng = Rng::new(110);
    let lens = [0, 1, 135, 136, 137, 271, 272, 273, 600];
    for (i, &inlen) in lens.iter().enumerate() {
        for &outlen in &lens {
            let input = rng.bytes(inlen + i);
            let expect = sqisign_reference::fips202::shake256(&input, outlen);
            // Split absorption and squeezing at random points.
            let mut h = sqisign::shake::Shake256::new();
            let cut = rng.next_u64() as usize % (input.len() + 1);
            h.absorb(&input[..cut]);
            h.absorb(&input[cut..]);
            let mut r = h.finalize();
            let mut out = vec![0; outlen];
            let cut = rng.next_u64() as usize % (outlen + 1);
            r.squeeze(&mut out[..cut]);
            r.squeeze(&mut out[cut..]);
            assert_eq!(out, expect, "inlen {} outlen {outlen}", input.len());
        }
    }
}

macro_rules! verify_suite {
    ($module:ident, $params:ty, $kat:literal) => {
        #[allow(dead_code, unused_imports)]
        mod $module {
            ec_helpers!($module, $params);
            use super::{kat, SAMPLES};
            use sqisign::verify::{self, PublicKey, Signature, SqisignParams};
            use sqisign_reference::$module::verify as cv;

            type P = $params;
            const SIG: usize = <P as SqisignParams>::SIGNATURE_BYTES;
            const PK: usize = <P as SqisignParams>::PUBLICKEY_BYTES;

            fn kats() -> Vec<kat::Kat> {
                kat::load($kat)
            }

            /// Rust and C agree on `sig`, and both reject it unless `valid`.
            fn check(sig: &[u8], msg: &[u8], pk: &[u8], valid: bool) {
                let r = verify::verify::<P>(sig, msg, pk);
                let c = cv::protocols_verify(
                    &cv::signature_from_bytes(sig),
                    &cv::public_key_from_bytes(pk),
                    msg,
                );
                assert_eq!((r, c), (valid, valid));
            }

            #[test]
            fn kat_signatures_verify() {
                let kats = kats();
                assert_eq!(kats.len(), 100);
                for k in &kats {
                    let (sig, msg) = k.sm.split_at(SIG);
                    assert_eq!(msg, k.msg);
                    check(sig, msg, &k.pk, true);
                    assert_eq!(verify::open::<P>(&k.sm, &k.pk), Some(msg));
                    assert_eq!(cv::sqisign_open(&k.sm, &k.pk).as_deref(), Some(msg));
                    let pk = PublicKey::<P>::decode(&k.pk).expect("pk length");
                    let s = Signature::<P>::decode(sig).expect("sig length");
                    assert_eq!(pk.to_bytes(), k.pk);
                    assert_eq!(s.to_bytes(), sig);
                }
            }

            #[test]
            fn encodings_match_reference() {
                let mut rng = Rng::new(111);
                for _ in 0..SAMPLES {
                    // Random bytes: field elements are often not canonical.
                    let (pk, sig) = (rng.bytes(PK), rng.bytes(SIG));
                    let r = PublicKey::<P>::decode(&pk).expect("pk length").to_bytes();
                    let c = cv::public_key_to_bytes(&cv::public_key_from_bytes(&pk), PK);
                    assert_eq!(r, c);
                    let r = Signature::<P>::decode(&sig).expect("sig length").to_bytes();
                    let c = cv::signature_to_bytes(&cv::signature_from_bytes(&sig), SIG);
                    assert_eq!(r, c);
                }
                // A projective key is encoded as `A / C`.
                let (rc, cc) = curve(&mut rng);
                let pk = PublicKey { curve: rc, hint: 7 };
                let c = cv::PublicKey { curve: cc, hint: 7 };
                assert_eq!(pk.to_bytes(), cv::public_key_to_bytes(&c, PK));
            }

            #[test]
            fn challenge_hash_matches_reference() {
                let mut rng = Rng::new(112);
                for i in 0..SAMPLES {
                    let ((pk_curve, c_pk_curve), (com, c_com)) = (curve(&mut rng), curve(&mut rng));
                    let hint = rng.next_u64() as u8;
                    let msg = rng.bytes(i * 7);
                    let r = PublicKey {
                        curve: pk_curve,
                        hint,
                    }
                    .hash_to_challenge(&com, &msg);
                    let c = cv::hash_to_challenge(
                        &cv::PublicKey {
                            curve: c_pk_curve,
                            hint,
                        },
                        &c_com,
                        &msg,
                    );
                    assert_eq!(r[..WORDS], c[..]);
                    assert!(r[WORDS..].iter().all(|&w| w == 0));
                }
            }

            #[test]
            #[should_panic(expected = "C = 0")]
            fn singular_key_does_not_encode() {
                let mut rng = Rng::new(113);
                let curve = Cv {
                    a: curve(&mut rng).0.a,
                    c: F2::ZERO,
                    ..Cv::default()
                };
                PublicKey { curve, hint: 0 }.to_bytes();
            }

            #[test]
            fn wrong_lengths_are_rejected() {
                let k = &kats()[0];
                let (sig, msg) = k.sm.split_at(SIG);
                assert!(!verify::verify::<P>(&sig[1..], msg, &k.pk));
                assert!(!verify::verify::<P>(&k.sm[..SIG + 1], &msg[1..], &k.pk));
                assert!(!cv::sqisign_verify(&k.sm[..SIG + 1], &msg[1..], &k.pk));
                assert!(!verify::verify::<P>(sig, msg, &k.pk[1..]));
                let longer = [k.pk.as_slice(), &[0]].concat();
                assert!(!verify::verify::<P>(sig, msg, &longer));
                let longer = [sig, &[0]].concat();
                assert!(!verify::verify::<P>(&longer, msg, &k.pk));
                assert!(!cv::sqisign_verify(&longer, msg, &k.pk));
                let mut sm = k.sm.clone();
                sm[0] ^= 1;
                assert!(verify::open::<P>(&sm, &k.pk).is_none());
                assert!(cv::sqisign_open(&sm, &k.pk).is_none());
                assert!(verify::open::<P>(&k.sm[..SIG - 1], &k.pk).is_none());
                assert!(cv::sqisign_open(&k.sm[..SIG - 1], &k.pk).is_none());
            }
        }
    };
}

verify_suite!(
    p324_3,
    sqisign::gf::P324,
    "PQCsignKAT_270_SQIsign_p324_3.rsp"
);
verify_suite!(
    p500_27,
    sqisign::gf::P500,
    "PQCsignKAT_417_SQIsign_p500_27.rsp"
);
verify_suite!(
    p664_17,
    sqisign::gf::P664,
    "PQCsignKAT_549_SQIsign_p664_17.rsp"
);
