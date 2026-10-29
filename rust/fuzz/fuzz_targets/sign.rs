//! Signing with arbitrary seeds and messages: `level || keygen seed || sign
//! seed || msg`, where `level % 3` selects the parameter set and seeds cut
//! short by the end of the input are zero-padded. Checks that
//! nothing panics, that the secret key re-encodes to itself, and that every
//! signature verifies and opens to its message.
#![no_main]

use libfuzzer_sys::fuzz_target;
use sqisign::gf::{P324, P500, P664};
use sqisign::prng::SEED_BYTES;
use sqisign::sign::{self, SecretKey, SignParams};
use sqisign::verify;

/// The first `SEED_BYTES` of `data`, zero-padded, and the rest.
fn seed(data: &[u8]) -> ([u8; SEED_BYTES], &[u8]) {
    let (head, rest) = data.split_at(data.len().min(SEED_BYTES));
    let mut seed = [0; SEED_BYTES];
    seed[..head.len()].copy_from_slice(head);
    (seed, rest)
}

fn run<P: SignParams>(data: &[u8]) {
    let (kseed, rest) = seed(data);
    let (sseed, msg) = seed(rest);
    let (pk, sk) = sign::keypair::<P>(&kseed);
    let decoded = SecretKey::<P>::decode(&sk).expect("sk length");
    assert_eq!(decoded.to_bytes(), sk, "secret key re-encoding");
    let sm = sign::sign_attached::<P>(&sk, msg, &sseed).expect("signing failed");
    assert_eq!(verify::open::<P>(&sm, &pk), Some(msg), "signature rejected");
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
