//! Verification of arbitrary inputs (upstream #15): `level || pk || sig ||
//! msg`, where `level % 3` selects the parameter set. Checks that nothing
//! panics, that `open` returns the message, and that decoding is idempotent:
//! re-encoding a decoded key or signature and decoding it again gives the
//! same bytes. Seeded from the KATs by `seed.py`.
#![no_main]

use libfuzzer_sys::fuzz_target;
use sqisign::gf::{P324, P500, P664};
use sqisign::verify::{self, PublicKey, Signature, SqisignParams};

fn run<P: SqisignParams>(data: &[u8]) {
    let Some((pk, sm)) = data.split_at_checked(P::PUBLICKEY_BYTES) else {
        return;
    };
    let Some((sig, msg)) = sm.split_at_checked(P::SIGNATURE_BYTES) else {
        assert!(verify::open::<P>(sm, pk).is_none(), "short sm opened");
        return;
    };
    if let Some(m) = verify::open::<P>(sm, pk) {
        assert_eq!(m, msg, "open returned another message");
    }
    let key = PublicKey::<P>::decode(pk).expect("pk length").to_bytes();
    let again = PublicKey::<P>::decode(&key).expect("pk length").to_bytes();
    assert_eq!(key, again, "public key re-encoding");
    let s = Signature::<P>::decode(sig).expect("sig length").to_bytes();
    let again = Signature::<P>::decode(&s).expect("sig length").to_bytes();
    assert_eq!(s, again, "signature re-encoding");
}

fuzz_target!(|data: &[u8]| {
    let Some((&level, rest)) = data.split_first() else {
        return;
    };
    match level % 3 {
        0 => run::<P324>(rest),
        1 => run::<P500>(rest),
        _ => run::<P664>(rest),
    }
});
