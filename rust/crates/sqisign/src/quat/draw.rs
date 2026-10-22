//! The PRNG streams of the quaternion sampling functions.

use crate::prng::Domain;

/// A sampling stream and the default stream (C `PRNG_default_domain`), which
/// C's primality tests and modular square roots draw from whatever stream
/// the caller passes.
pub struct Draw<'a> {
    stream: Option<&'a mut Domain>,
    default: &'a mut Domain,
}

impl<'a> Draw<'a> {
    /// Samples from `stream`; primality tests and square roots use `default`.
    pub fn new(stream: &'a mut Domain, default: &'a mut Domain) -> Self {
        Self {
            stream: Some(stream),
            default,
        }
    }

    /// Draws everything from `default` (C passing `&PRNG_default_domain`).
    pub fn default_only(default: &'a mut Domain) -> Self {
        Self {
            stream: None,
            default,
        }
    }

    /// The sampling stream.
    pub(crate) fn stream(&mut self) -> &mut Domain {
        match &mut self.stream {
            Some(d) => d,
            None => self.default,
        }
    }

    /// The default stream.
    pub(crate) fn default(&mut self) -> &mut Domain {
        self.default
    }
}
