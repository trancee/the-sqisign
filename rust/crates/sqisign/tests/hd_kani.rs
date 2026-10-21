//! Differential tests: Rust `hd` chains on splitting Kani kernels against the
//! C reference (p664 has no 3-isogeny from `E0`, so no such kernel).

#[macro_use]
mod support;

macro_rules! hd_kani_suite {
    ($module:ident, $params:ty) => {
        #[allow(dead_code, unused_imports)]
        mod $module {
            ec_helpers!($module, $params);
            hd_helpers!($module, $params);
            kani_helpers!($params);
            hd_chain_checks!();

            #[test]
            fn kani_chains_split_and_match_reference() {
                let mut rng = Rng::new(70);
                for idx in 0..KANI.len() {
                    let (e12, ker, b, b1) = kani_kernel(&mut rng, idx);
                    let n = KANI[idx].0;
                    let pts = [
                        Cp {
                            p1: b1.p,
                            p2: Pt::IDENTITY,
                        },
                        Cp {
                            p1: Pt::IDENTITY,
                            p2: b.q,
                        },
                    ];
                    for mode in [0, 1, 2, -1] {
                        assert!(
                            check_chain(mode, n, &e12, &ker, &pts).is_some(),
                            "n = {n}, mode {mode}"
                        );
                    }
                }
            }
        }
    };
}

hd_kani_suite!(p324_3, sqisign::gf::P324);
hd_kani_suite!(p500_27, sqisign::gf::P500);
