//! The NIST KAT files in the repository's `KAT/` directory.

/// One KAT entry.
pub struct Kat {
    /// The seed of the NIST DRBG.
    pub seed: Vec<u8>,
    /// The message.
    pub msg: Vec<u8>,
    /// The encoded public key.
    pub pk: Vec<u8>,
    /// The encoded secret key.
    pub sk: Vec<u8>,
    /// The signed message `sig || msg`.
    pub sm: Vec<u8>,
}

fn unhex(s: &str) -> Vec<u8> {
    (0..s.len())
        .step_by(2)
        .map(|i| u8::from_str_radix(&s[i..i + 2], 16).expect("hex"))
        .collect()
}

fn field(k: &str, entry: &str) -> Option<String> {
    entry
        .lines()
        .find_map(|l| l.strip_prefix(k).map(|v| v.trim().to_owned()))
}

/// The entries of `KAT/<name>`, checking `count` and `smlen`.
pub fn load(name: &str) -> Vec<Kat> {
    let path = format!("{}/../../../KAT/{name}", env!("CARGO_MANIFEST_DIR"));
    let text = std::fs::read_to_string(&path).expect("KAT file");
    let mut out = Vec::new();
    for entry in text.split("\n\n").filter(|e| e.contains("count = ")) {
        let count: usize = field("count = ", entry)
            .expect("count")
            .parse()
            .expect("count");
        assert_eq!(count, out.len(), "KAT entries in order");
        let kat = Kat {
            seed: unhex(&field("seed = ", entry).expect("seed")),
            msg: unhex(&field("msg = ", entry).expect("msg")),
            pk: unhex(&field("pk = ", entry).expect("pk")),
            sk: unhex(&field("sk = ", entry).expect("sk")),
            sm: unhex(&field("sm = ", entry).expect("sm")),
        };
        let smlen: usize = field("smlen = ", entry)
            .expect("smlen")
            .parse()
            .expect("smlen");
        assert_eq!(kat.sm.len(), smlen, "smlen");
        out.push(kat);
    }
    out
}
