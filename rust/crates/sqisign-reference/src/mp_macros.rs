//! Wrapper generators for the one-result C `mp` functions.

macro_rules! mp_unary {
    ($($c:ident => $name:ident),*) => {$(
        #[doc = concat!("C `", stringify!($c), "`.")]
        pub fn $name(a: &Ibz) -> Ibz {
            let mut r = Ibz::default();
            // SAFETY: valid values.
            unsafe { ffi::$c(&mut r, a) };
            r
        }
    )*};
}

macro_rules! mp_binary {
    ($($c:ident => $name:ident),*) => {$(
        #[doc = concat!("C `", stringify!($c), "`.")]
        pub fn $name(a: &Ibz, b: &Ibz) -> Ibz {
            let mut r = Ibz::default();
            // SAFETY: valid values.
            unsafe { ffi::$c(&mut r, a, b) };
            r
        }
    )*};
}

macro_rules! mp_query {
    ($ret:ty; $($c:ident => $name:ident),*) => {$(
        #[doc = concat!("C `", stringify!($c), "`.")]
        pub fn $name(a: &Ibz) -> $ret {
            // SAFETY: valid value.
            unsafe { ffi::$c(a) }
        }
    )*};
}
