//! Quaternion algebra over the integers (vendor `quaternion/ref`).
//!
//! Elements of the algebra `B_{p,∞}` ramified at `p = 3 mod 4` and `∞` are
//! represented in the basis `(1, i, j, ij)` with `i^2 = -1` and `j^2 = -p`.
//! Every function follows the C operation order, so results agree with C in
//! value and in [`Ibz`] bound, including wrap-around at the capacity.

pub mod algebra;
pub mod dim2;
pub mod dim4;
mod draw;
mod ideal;
mod integers;
pub mod lattice;
pub mod lll;
mod params;
pub mod qlapoty;
mod stdorder;

pub use algebra::{Alg, AlgElem};
pub use draw::Draw;
pub use ideal::{Ideal, Response};
pub use lattice::Lattice;
pub use params::QuatParams;

use crate::mp::Ibz;

/// A vector of 2 integers (C `ibz_vec_2_t`).
pub type Vec2<P> = [Ibz<P>; 2];
/// A vector of 4 integers (C `ibz_vec_4_t`).
pub type Vec4<P> = [Ibz<P>; 4];
/// A 2x2 integer matrix, row-major (C `ibz_mat_2x2_t`).
pub type Mat2<P> = [[Ibz<P>; 2]; 2];
/// A 4x4 integer matrix, row-major (C `ibz_mat_4x4_t`).
pub type Mat4<P> = [[Ibz<P>; 4]; 4];
