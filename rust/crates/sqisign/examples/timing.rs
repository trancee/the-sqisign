//! Release timing and profiling harness for key generation, signing and verification.
//!
//! `cargo run --release -p sqisign --example timing -- [p324|p500|p664] [keygen|sign|verify|all] [iterations]`
//! prints the median and minimum time per operation. Seeds are fixed, so runs are repeatable.

use sqisign::gf::{P324, P500, P664};
use sqisign::prng::SEED_BYTES;
use sqisign::sign::{self, SignParams};
use std::time::{Duration, Instant};

fn seed(i: usize) -> [u8; SEED_BYTES] {
    let mut s = [0u8; SEED_BYTES];
    s[..8].copy_from_slice(&(i as u64).to_le_bytes());
    s
}

fn report(name: &str, mut times: Vec<Duration>) {
    times.sort();
    let ms = |d: Duration| d.as_secs_f64() * 1e3;
    println!(
        "{name:8} median {:9.3} ms  min {:9.3} ms  (n = {})",
        ms(times[times.len() / 2]),
        ms(times[0]),
        times.len()
    );
}

fn time(n: usize, mut f: impl FnMut(usize)) -> Vec<Duration> {
    (0..n)
        .map(|i| {
            let t = Instant::now();
            f(i);
            t.elapsed()
        })
        .collect()
}

fn run<P: SignParams>(op: &str, n: usize) {
    let msg = [0x5au8; 32];
    let (pk, sk) = sign::keypair::<P>(seed(0));
    let sig = sign::sign::<P>(&sk, &msg, seed(1)).expect("signing failed");
    if matches!(op, "keygen" | "all") {
        report("keygen", time(n, |i| drop(sign::keypair::<P>(seed(i + 2)))));
    }
    if matches!(op, "sign" | "all") {
        report(
            "sign",
            time(n, |i| drop(sign::sign::<P>(&sk, &msg, seed(i + 2)))),
        );
    }
    if matches!(op, "verify" | "all") {
        report(
            "verify",
            time(n, |_| {
                assert!(sqisign::verify::verify::<P>(&sig, &msg, &pk))
            }),
        );
    }
}

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let level = args.first().map_or("p324", String::as_str);
    let op = args.get(1).map_or("all", String::as_str);
    let n = args.get(2).map_or(20, |s| s.parse().expect("iterations"));
    match level {
        "p324" => run::<P324>(op, n),
        "p500" => run::<P500>(op, n),
        "p664" => run::<P664>(op, n),
        _ => panic!("unknown level {level}"),
    }
}
