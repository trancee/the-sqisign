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
    ] {
        println!("cargo:rerun-if-changed={}", root.join(dir).display());
    }

    for level in LEVELS {
        let lvl_out = out.join(level);
        std::fs::create_dir_all(&lvl_out).expect("create level out dir");
        cc::Build::new()
            .out_dir(&lvl_out)
            .files(sources(&src, level))
            .includes(include_dirs(&root, level))
            .define("SQISIGN_BUILD_TYPE_REF", None)
            .define("SQISIGN_GF_IMPL_REF", None)
            .define("SQISIGN_NAMESPACE_GENERIC_AS_VARIANT", None)
            .define("SQISIGN_VARIANT", level)
            .define("RADIX_64", None)
            .define("NDEBUG", None)
            .define(target, None)
            .std("c11")
            .warnings(false)
            .compile(&format!("sqisign_ref_{level}"));
    }
}

fn sources(src: &Path, level: &str) -> Vec<PathBuf> {
    let mut files = vec![
        src.join("gf/ref/lvlx/fp.c"),
        src.join("gf/ref/lvlx/fp2.c"),
        src.join(format!("gf/ref/{level}/fp_{level}.c")),
        src.join("mp/ref/lvlx/mp.c"),
        src.join("mp/ref/lvlx/modqx.c"),
        src.join(format!("precomp/ref/{level}/e0_basis.c")),
        src.join(format!("precomp/ref/{level}/ec_params.c")),
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
    files
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
        format!("src/precomp/ref/{level}/include"),
    ]
    .iter()
    .map(|d| root.join(d))
    .collect()
}
