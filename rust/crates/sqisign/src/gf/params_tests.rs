//! Checks the compile-time constants against the vendor sat64 parameter headers.

use super::super::consts;
use super::{FpParams, P324, P500, P664};
use std::path::PathBuf;

/// Values of `#define <name> ...` in the vendor header, as 64-bit integers.
fn header_values(level: &str, name: &str) -> Vec<u64> {
    let path = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../../src/gf/sat64")
        .join(level)
        .join("include/fp_generic_params.h");
    let text = std::fs::read_to_string(&path).expect("vendor header");
    let start = text
        .find(&format!("#define {name} "))
        .expect("define present")
        + 9
        + name.len();
    let body = &text[start..];
    let body = &body[..body
        .find("#define")
        .or_else(|| body.find("#endif"))
        .expect("end")];
    body.split(|ch: char| !ch.is_ascii_alphanumeric())
        .filter(|tok| !tok.is_empty())
        .map(|tok| {
            let tok = tok.trim_end_matches("ULL");
            match tok.strip_prefix("0x") {
                Some(hex) => u64::from_str_radix(hex, 16).expect("hex"),
                None => tok.parse().expect("decimal"),
            }
        })
        .collect()
}

fn assert_matches_header<P: FpParams>(level: &str) {
    assert_eq!(header_values(level, "FPG_N"), [P::N as u64]);
    assert_eq!(header_values(level, "FPG_BITS"), [u64::from(P::BITS)]);
    assert_eq!(header_values(level, "FPG_C"), [P::C]);
    assert_eq!(header_values(level, "FPG_T"), [u64::from(P::T)]);
    assert_eq!(header_values(level, "FPG_P_INIT"), P::P.as_ref());
    assert_eq!(header_values(level, "FPG_ONE_INIT"), P::ONE.as_ref());
    assert_eq!(header_values(level, "FPG_R2_INIT"), P::R2.as_ref());
    assert_eq!(
        header_values(level, "FPG_TWO_INV_INIT"),
        P::TWO_INV.as_ref()
    );
    assert_eq!(
        header_values(level, "FPG_THREE_INV_INIT"),
        P::THREE_INV.as_ref()
    );
    assert!(P::ZERO.as_ref().iter().all(|&l| l == 0));
}

/// `NWORDS_ORDER` of the precomp header, radix-64 branch (the last definition).
fn order_words(level: &str) -> usize {
    let path = format!(
        "{}/../../../src/precomp/ref/{level}/include/fp_constants.h",
        env!("CARGO_MANIFEST_DIR")
    );
    let text = std::fs::read_to_string(path).expect("vendor header");
    let (_, tail) = text
        .rsplit_once("#define NWORDS_ORDER ")
        .expect("define present");
    tail.split_whitespace().next().unwrap().parse().unwrap()
}

#[test]
fn order_words_match_vendor_headers() {
    assert_eq!(P324::ORDER_WORDS, order_words("p324_3"));
    assert_eq!(P500::ORDER_WORDS, order_words("p500_27"));
    assert_eq!(P664::ORDER_WORDS, order_words("p664_17"));
}

#[test]
fn constants_match_vendor_headers() {
    assert_matches_header::<P324>("p324_3");
    assert_matches_header::<P500>("p500_27");
    assert_matches_header::<P664>("p664_17");
}

#[test]
fn derivations_also_evaluate_at_run_time() {
    let p = consts::modulus::<6>(3, 324);
    assert_eq!(p, P324::P);
    let one = consts::pow2_mod(&p, 384);
    assert_eq!(one, P324::ONE);
    assert_eq!(consts::pow2_mod(&p, 768), P324::R2);
    assert_eq!(consts::half_mod(&one, &p), P324::TWO_INV);
    assert_eq!(
        consts::half_mod(&[2, 0, 0, 0, 0, 0], &p),
        [1, 0, 0, 0, 0, 0]
    );
    assert_eq!(consts::third_mod(&one, &p), P324::THREE_INV);
    assert_eq!(
        consts::third_mod(&[6, 0, 0, 0, 0, 0], &p),
        [2, 0, 0, 0, 0, 0]
    );
    let p27 = consts::modulus::<8>(27, 500);
    assert_eq!(
        consts::third_mod(&consts::pow2_mod(&p27, 512), &p27),
        P500::THREE_INV
    );
    consts::check_shape::<6>(3, 324);
}

#[test]
#[should_panic(expected = "too many limbs")]
fn shape_rejects_too_many_limbs() {
    consts::check_shape::<12>(3, 324);
}

#[test]
#[should_panic(expected = "odd")]
fn shape_rejects_even_cofactor() {
    consts::check_shape::<6>(4, 324);
}

#[test]
#[should_panic(expected = "odd")]
fn shape_rejects_unit_cofactor() {
    consts::check_shape::<6>(1, 324);
}

#[test]
#[should_panic(expected = "multiple of 64")]
fn shape_rejects_aligned_exponent() {
    consts::check_shape::<6>(3, 320);
}

#[test]
#[should_panic(expected = "multiple of 64")]
fn shape_rejects_small_exponent() {
    consts::check_shape::<6>(3, 63);
}

#[test]
#[should_panic(expected = "fit one limb")]
fn shape_rejects_wide_cofactor() {
    consts::check_shape::<11>(u64::MAX, 330);
}

#[test]
#[should_panic(expected = "spare bits")]
fn shape_rejects_missing_spare_bits() {
    consts::check_shape::<6>(3, 381);
}

#[test]
#[should_panic(expected = "unreduced sums")]
fn shape_rejects_too_few_spare_bits_for_unreduced_sums() {
    // Six spare bits, but `(2^BITS)^2 * 4 / R` needs bitlen(31) + 2 = 7.
    consts::check_shape::<6>(31, 373);
}

#[test]
#[should_panic(expected = "top limb")]
fn shape_rejects_spare_limbs() {
    consts::check_shape::<7>(3, 324);
}
