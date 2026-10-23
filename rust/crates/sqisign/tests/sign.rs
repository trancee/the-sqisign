//! Rust key generation and signing against the NIST KAT files, with the
//! seeds the C DRBG draws for them.

#[macro_use]
mod support;

use support::kat;

/// The seeds `prng_seed` draws for the key pair and the signature after the
/// DRBG is seeded with `seed`, as `PQCgenKAT_sign` does.
fn seeds(seed: &[u8]) -> ([u8; 48], [u8; 48]) {
    use sqisign_reference::prng::{randombytes, randombytes_init};
    randombytes_init(seed.try_into().expect("a 48-byte seed"));
    let k = randombytes(48).try_into().expect("48 bytes");
    let s = randombytes(48).try_into().expect("48 bytes");
    (k, s)
}

macro_rules! sign_suite {
    ($module:ident, $params:ty, $kat:literal, $count:literal) => {
        mod $module {
            use super::{kat, seeds};
            use sqisign::sign::{self, SecretKey, SignParams};
            use sqisign::verify;

            type P = $params;

            #[test]
            fn kats_match() {
                for (i, k) in kat::load($kat).iter().take($count).enumerate() {
                    let (ks, ss) = seeds(&k.seed);
                    let (pk, sk) = sign::keypair::<P>(&ks);
                    assert_eq!((&pk, &sk), (&k.pk, &k.sk), "key pair {i}");
                    let sm = sign::sign_attached::<P>(&sk, &k.msg, &ss).expect("a signature");
                    assert_eq!(sm, k.sm, "signature {i}");
                    assert!(verify::verify::<P>(
                        &sm[..sm.len() - k.msg.len()],
                        &k.msg,
                        &pk
                    ));
                }
            }

            #[test]
            fn secret_keys_round_trip() {
                let k = &kat::load($kat)[0];
                let sk = SecretKey::<P>::decode(&k.sk).expect("a secret key");
                assert_eq!(sk.pk.to_bytes(), k.pk);
                assert_ne!(sk.curve.a.ct_eq(&sk.pk.curve.a), 0);
                assert_eq!(sk.to_bytes(), k.sk);
            }

            #[test]
            fn malformed_secret_keys_are_rejected() {
                let k = &kat::load($kat)[0];
                let n = <P as SignParams>::SECRETKEY_BYTES;
                assert_eq!(k.sk.len(), n);
                for len in [0, n - 1, n + 1] {
                    let sk = vec![0; len];
                    assert!(SecretKey::<P>::decode(&sk).is_none());
                    assert!(sign::sign::<P>(&sk, &k.msg, &[0; 48]).is_none());
                }
            }
        }
    };
}

sign_suite!(
    p324,
    sqisign::gf::P324,
    "PQCsignKAT_270_SQIsign_p324_3.rsp",
    100
);
sign_suite!(
    p500,
    sqisign::gf::P500,
    "PQCsignKAT_417_SQIsign_p500_27.rsp",
    20
);
sign_suite!(
    p664,
    sqisign::gf::P664,
    "PQCsignKAT_549_SQIsign_p664_17.rsp",
    10
);
