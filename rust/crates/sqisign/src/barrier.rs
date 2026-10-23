//! The optimisation barrier of the constant-time masks (vendor `mp_ct.h`
//! `ct_barrier_*`).

/// `x`, opaque to the optimiser, so that a mask derived from secret data
/// is not compiled back into a branch. `black_box` is best effort; the CI
/// job `Constant-time branches` checks the shipped build for such branches.
#[inline(always)]
pub(crate) fn barrier<T>(x: T) -> T {
    core::hint::black_box(x)
}
