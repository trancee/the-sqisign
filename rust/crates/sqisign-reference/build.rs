//! Compiles the vendor SQIsign C reference (`ref` build type) once per
//! parameter set, with every symbol prefixed `sqisign_<level>_ref_`.

use std::env;
use std::path::{Path, PathBuf};

const LEVELS: [&str; 3] = ["p324_3", "p500_27", "p664_17"];

fn main() {
    let manifest = PathBuf::from(env::var("CARGO_MANIFEST_DIR").expect("CARGO_MANIFEST_DIR"));
    let root = manifest
        .join("../../..")
        .canonicalize()
        .expect("repository root");
    let src = root.join("src");
    let out = PathBuf::from(env::var("OUT_DIR").expect("OUT_DIR"));
    let target = match env::var("CARGO_CFG_TARGET_ARCH").as_deref() {
        Ok("aarch64") => "TARGET_ARM64",
        Ok("x86_64") => "TARGET_AMD64",
        _ => "TARGET_OTHER",
    };

    for dir in [
        "include",
        "src/common",
        "src/gf/ref",
        "src/precomp/ref",
        "src/mp/ref",
        "src/ec/ref",
        "src/hd/ref",
        "src/verification/ref",
        "src/quaternion/ref",
        "src/id2iso/ref",
        "src/sqisign.c",
    ] {
        println!("cargo:rerun-if-changed={}", root.join(dir).display());
    }

    for level in LEVELS {
        let lvl_out = out.join(level);
        std::fs::create_dir_all(&lvl_out).expect("create level out dir");
        cc::Build::new()
            .out_dir(&lvl_out)
            .files(sources(&src, level))
            .files(lll_shims(&src, &lvl_out, level))
            .includes(include_dirs(&root, level))
            .define("SQISIGN_BUILD_TYPE_REF", None)
            .define("SQISIGN_GF_IMPL_REF", None)
            .define("SQISIGN_NAMESPACE_GENERIC_AS_VARIANT", None)
            .define("SQISIGN_VARIANT", level)
            // The only unprefixed per-level symbol (vendor `prng.c`).
            .define(
                "PRNG_default_domain",
                format!("sqisign_{level}_ref_PRNG_default_domain").as_str(),
            )
            .define("RADIX_64", None)
            .define("NDEBUG", None)
            .define(target, None)
            .std("c11")
            .warnings(false)
            .compile(&format!("sqisign_ref_{level}"));
    }

    // After the level libraries, which use it: GNU ld resolves static
    // libraries in link order.
    let common_out = out.join("common");
    std::fs::create_dir_all(&common_out).expect("create common out dir");
    cc::Build::new()
        .out_dir(&common_out)
        .file(src.join("common/generic/fips202.c"))
        // The NIST CTR-DRBG `randombytes` of the KAT build, so tests can
        // seed the C PRNG reproducibly.
        .file(src.join("common/ref/randombytes_ctrdrbg.c"))
        .file(src.join("common/ref/aes_c.c"))
        .define("RANDOMBYTES_C", None)
        .include(src.join("common/ref/include"))
        .include(src.join("common/generic/include"))
        .include(root.join("include"))
        .std("c11")
        .warnings(false)
        .compile("sqisign_ref_common");
}

fn sources(src: &Path, level: &str) -> Vec<PathBuf> {
    let mut files = vec![
        src.join("common/generic/mem.c"),
        src.join("common/generic/prng.c"),
        src.join("gf/ref/lvlx/fp.c"),
        src.join("gf/ref/lvlx/fp2.c"),
        src.join(format!("gf/ref/{level}/fp_{level}.c")),
        src.join("mp/ref/lvlx/mp.c"),
        src.join("mp/ref/lvlx/modqx.c"),
        src.join(format!("precomp/ref/{level}/e0_basis.c")),
        src.join(format!("precomp/ref/{level}/ec_params.c")),
        src.join(format!("precomp/ref/{level}/endomorphism_action.c")),
        src.join(format!("precomp/ref/{level}/quaternion_data.c")),
        src.join(format!("precomp/ref/{level}/torsion_constants.c")),
    ];
    for f in ["ec", "isog", "basis", "biextension", "normalize"] {
        files.push(src.join(format!("ec/ref/lvlx/{f}.c")));
    }
    for f in [
        "hd",
        "theta_structure",
        "gluing",
        "splitting",
        "theta_isogenies",
    ] {
        files.push(src.join(format!("hd/ref/lvlx/{f}.c")));
    }
    for f in [
        "algebra",
        "dim2",
        "dim4",
        "finit",
        "ideal",
        "integers",
        "lattice",
        "lll_applications",
        "protocol",
        "qlapoty",
        "stdorder",
        "lll/lll_dim4",
    ] {
        files.push(src.join(format!("quaternion/ref/lvlx/{f}.c")));
    }
    for f in ["id2iso", "dim2id2iso"] {
        files.push(src.join(format!("id2iso/ref/lvlx/{f}.c")));
    }
    for f in ["common", "encode_public", "verify"] {
        files.push(src.join(format!("verification/ref/lvlx/{f}.c")));
    }
    files.push(src.join("sqisign.c"));
    files
}

/// `gaussian_xgcd.c`, `lehmer_xgcd.c` and `lll_lg2.c`, each compiled through a generated unit
/// that includes it and exports its `static` word-level steps as
/// `sqisign_<level>_ref_test_*`, for differential tests of their boundaries.
fn lll_shims(src: &Path, out: &Path, level: &str) -> Vec<PathBuf> {
    let units: [(&str, &[&str]); 3] = [
        (
            "gaussian_xgcd",
            &[
                "int TEST(LG_gaussian_inner)(const i64_complex_t v[4], i32_complex_t m[2][2], \
               uint64_t w, int32_t f1, int32_t f2) { \
               return LG_gaussian_inner(v[0], v[1], v[2], v[3], m, w, f1, f2); }",
            ],
        ),
        (
            "lehmer_xgcd",
            &[
                "int TEST(partial_xgcd_msb)(uint64_t a, uint64_t b, uint64_t s, int32_t m[2][2], \
                 uint64_t w) { return partial_xgcd_msb(a, b, s, m, w); }",
                "int32_t TEST(sizered_msb)(uint64_t a, uint64_t b, int64_t c, int64_t d) { \
                 return sizered_msb(a, b, c, d); }",
                "void TEST(partial_xgcd_lehmer)(ibz_t *a, ibz_t *b, ibz_t *c, ibz_t *d, int o, \
                 int det) { partial_xgcd_lehmer(a, b, c, d, o, det); }",
                "void TEST(sizered_lehmer)(ibz_t *a, ibz_t *b, ibz_t *c, ibz_t *d, int o, \
                 int det) { sizered_lehmer(a, b, c, d, o, det); }",
            ],
        ),
        (
            "lll_lg2",
            &[
                "int TEST(fast_log2_ratio)(uint64_t a, uint64_t b, int clz_b) { \
                 return fast_log2_ratio(a, b, clz_b); }",
                "int TEST(partial_gram_msb)(uint64_t a, int64_t b, uint64_t c, int32_t m[2][2], \
                 uint64_t w) { return partial_gram_msb(a, b, c, m, w); }",
            ],
        ),
    ];
    units
        .iter()
        .map(|(name, wrappers)| {
            let unit = out.join(format!("{name}_shim.c"));
            let body = format!(
                "#include \"{}\"\n#define TEST(f) sqisign_{level}_ref_test_##f\n{}\n",
                src.join(format!("quaternion/ref/lvlx/lll/{name}.c"))
                    .display(),
                wrappers.join("\n")
            );
            std::fs::write(&unit, body).expect("write LLL shim");
            unit
        })
        .collect()
}

fn include_dirs(root: &Path, level: &str) -> Vec<PathBuf> {
    [
        "include".to_owned(),
        "src/common/generic/include".to_owned(),
        "src/common/ref/include".to_owned(),
        "src/gf/ref/include".to_owned(),
        "src/mp/ref/include".to_owned(),
        "src/ec/ref/include".to_owned(),
        "src/hd/ref/include".to_owned(),
        "src/verification/ref/include".to_owned(),
        "src/quaternion/ref/include".to_owned(),
        "src/quaternion/ref/lvlx/internal_quaternion_headers".to_owned(),
        "src/id2iso/ref/include".to_owned(),
        format!("src/precomp/ref/{level}/include"),
    ]
    .iter()
    .map(|d| root.join(d))
    .collect()
}
