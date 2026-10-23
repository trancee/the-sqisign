//! dudect-style timing test of the constant-time field and integer code.
//!
//! `cargo run --release -p sqisign --example dudect -- [samples [name]]`
//! times each operation (or those whose name contains `name`) on batches of
//! about 20 µs of fixed inputs (class 0) or of random inputs (class 1), the
//! class drawn at random per batch, and prints Welch's t statistic of the two
//! timing distributions, uncropped and cropped at the 95th percentile, as in
//! dudect (Reparaz, Balasch, Verbauwhede, 2017). A `|t|` above 4.5 is evidence
//! of input-dependent timing; below, there is no evidence at this sample
//! size, which is not proof of constant time. Run it on the target, on an idle
//! machine, with the shipped profile (fat LTO, one codegen unit), and with
//! more samples for a stronger statement.

use sqisign::gf::{Fp, Fp2, FpParams, P324, P500, P664};
use sqisign::mp::{Ibz, MpParams};
use std::hint::black_box;
use std::time::Instant;

/// Time per batch, so that each sample spans many timer ticks.
const BATCH_NANOS: u128 = 20_000;

struct Rng(u64);

impl Rng {
    fn next(&mut self) -> u64 {
        self.0 ^= self.0 << 13;
        self.0 ^= self.0 >> 7;
        self.0 ^= self.0 << 17;
        self.0
    }

    fn bytes(&mut self, n: usize) -> Vec<u8> {
        (0..n).map(|_| self.next() as u8).collect()
    }
}

#[derive(Default)]
struct Welch {
    n: [f64; 2],
    mean: [f64; 2],
    m2: [f64; 2],
}

impl Welch {
    fn push(&mut self, class: usize, x: f64) {
        self.n[class] += 1.0;
        let d = x - self.mean[class];
        self.mean[class] += d / self.n[class];
        self.m2[class] += d * (x - self.mean[class]);
    }

    fn t(&self) -> f64 {
        let var = |c: usize| self.m2[c] / (self.n[c] - 1.0);
        (self.mean[0] - self.mean[1]) / (var(0) / self.n[0] + var(1) / self.n[1]).sqrt()
    }
}

/// Times `op` on `samples` batches; `input(class, rng)` makes one input.
fn test<T>(
    name: &str,
    samples: usize,
    rng: &mut Rng,
    input: impl Fn(usize, &mut Rng) -> T,
    op: impl Fn(&T) -> u64,
) {
    if std::env::args().nth(2).is_some_and(|f| !name.contains(&f)) {
        return;
    }
    let probe: Vec<T> = (0..64).map(|_| input(1, rng)).collect();
    let start = Instant::now();
    for x in &probe {
        black_box(op(black_box(x)));
    }
    let size = (BATCH_NANOS * 64 / start.elapsed().as_nanos().max(1)).clamp(1, 4096) as usize;
    let mut times = Vec::with_capacity(samples);
    for _ in 0..samples {
        let class = (rng.next() >> 63) as usize;
        let batch: Vec<T> = (0..size).map(|_| input(class, rng)).collect();
        let start = Instant::now();
        for x in &batch {
            black_box(op(black_box(x)));
        }
        times.push((class, start.elapsed().as_nanos() as f64));
    }
    let mut sorted: Vec<f64> = times.iter().map(|&(_, t)| t).collect();
    sorted.sort_by(f64::total_cmp);
    let cut = sorted[sorted.len() * 95 / 100];
    let (mut all, mut cropped) = (Welch::default(), Welch::default());
    for &(class, t) in &times {
        all.push(class, t);
        if t <= cut {
            cropped.push(class, t);
        }
    }
    let (t0, t1) = (all.t(), cropped.t());
    let verdict = if t0.abs().max(t1.abs()) > 4.5 {
        "LEAK?"
    } else {
        "ok"
    };
    println!("{name:24} batch {size:5}  t = {t0:8.2}  t(p95) = {t1:8.2}  {verdict}");
}

fn fp<P: FpParams>(class: usize, rng: &mut Rng) -> Fp<P> {
    if class == 0 {
        Fp::ZERO
    } else {
        Fp::decode_reduce(&rng.bytes(2 * Fp::<P>::ENCODED_BYTES))
    }
}

fn fp2<P: FpParams>(class: usize, rng: &mut Rng) -> Fp2<P> {
    Fp2 {
        re: if class == 0 { Fp::ONE } else { fp(1, rng) },
        im: fp(class, rng),
    }
}

/// A signed integer below `2^bits`, of bound `bits + 1` in both classes (the
/// bound is public). Class 0 is one of two fixed dense values: on an Apple
/// M1, batches that repeat a single value ran faster through the
/// branch-free `ct_shift`, which disappeared with two alternating values.
fn ibz<P: MpParams>(class: usize, rng: &mut Rng, bits: usize) -> Ibz<P> {
    let pick = (rng.next() & 1) as usize;
    let fixed = &mut Rng([0x2545_f491_4f6c_dd1d, 0x1234_5678_9abc_def1][pick]);
    let r = if class == 0 { fixed } else { rng };
    let words: Vec<u64> = (0..bits.div_ceil(64)).map(|_| r.next()).collect();
    let x = Ibz::from_bits(&words, bits);
    if r.next() & 1 == 1 {
        -x
    } else {
        x
    }
}

fn digest<P: FpParams>(x: &Fp<P>) -> u64 {
    let mut b = vec![0u8; Fp::<P>::ENCODED_BYTES];
    x.encode(&mut b);
    u64::from(b[0])
}

fn run<P: MpParams>(set: &str, samples: usize, rng: &mut Rng) {
    let t = |op: &str| format!("{set} {op}");
    let pair = |c: usize, r: &mut Rng| (fp::<P>(c, r), fp::<P>(c, r));
    test(&t("fp add"), samples, rng, pair, |(a, b)| {
        digest(&(*a + *b))
    });
    test(&t("fp sub"), samples, rng, pair, |(a, b)| {
        digest(&(*a - *b))
    });
    test(&t("fp mul"), samples, rng, pair, |(a, b)| {
        digest(&(*a * *b))
    });
    test(
        &t("fp select"),
        samples,
        rng,
        |c, r| (fp::<P>(c, r), c as u32 * u32::MAX),
        |(a, m)| digest(&Fp::select(&Fp::ONE, a, *m)),
    );
    test(&t("fp2 inv"), samples / 8, rng, fp2::<P>, |a| {
        digest(&a.inv().re)
    });
    test(&t("fp2 sqrt"), samples / 8, rng, fp2::<P>, |a| {
        digest(&a.sqrt().re)
    });
    let bits = 256;
    let ints = |c: usize, r: &mut Rng| (ibz::<P>(c, r, bits), ibz::<P>(c, r, bits));
    test(&t("ibz ct_lt_mask"), samples, rng, ints, |(a, b)| {
        a.ct_lt_mask(b)
    });
    test(&t("ibz ct_highmul"), samples, rng, ints, |(a, b)| {
        Ibz::ct_highmul(a, b, -(bits as i32), bits as u32, 2 * bits).limbs()[0]
    });
    test(
        &t("ibz ct_shift value"),
        samples,
        rng,
        |c, r| (ibz::<P>(c, r, bits), 5),
        |(a, s)| a.ct_shift(*s, 64, bits + 64).limbs()[0],
    );
    test(
        &t("ibz ct_shift amount"),
        samples,
        rng,
        |c, r| {
            (
                ibz::<P>(1, r, bits),
                // Two fixed amounts, as in `ibz`: a single amount of 0 makes
                // every masked store rewrite an unchanged word.
                if c == 0 {
                    if r.next() & 1 == 0 {
                        37
                    } else {
                        -21
                    }
                } else {
                    (r.next() % 128) as i32 - 64
                },
            )
        },
        |(a, s)| a.ct_shift(*s, 64, bits + 64).limbs()[0],
    );
    test(
        &t("ibz cswap"),
        samples,
        rng,
        |c, r| (ibz::<P>(c, r, bits), c as u64 * u64::MAX),
        |(a, m)| {
            let (mut x, mut y) = (*a, Ibz::<P>::from_bits(&[0; 4], bits));
            Ibz::cswap(&mut x, &mut y, *m);
            y.limbs()[0]
        },
    );
}

fn main() {
    let samples = std::env::args()
        .nth(1)
        .map_or(100_000, |s| s.parse().expect("samples"));
    let mut rng = Rng(0x9e37_79b9_7f4a_7c15);
    run::<P324>("p324", samples, &mut rng);
    run::<P500>("p500", samples, &mut rng);
    run::<P664>("p664", samples, &mut rng);
}
