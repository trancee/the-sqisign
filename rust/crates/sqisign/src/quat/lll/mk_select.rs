//! Branch-free choice of four independent short candidates and the
//! inverse transpose of the chosen small transform (vendor
//! `lll/lll_dim4.c`, part 3).

use super::word::select32;

/// A candidate's coordinates in the reduced basis.
pub type T4 = [i8; 4];

/// `a` if `cond`, else `b` (C `ct_sel8`).
fn sel8(cond: bool, a: i8, b: i8) -> i8 {
    let m = -i8::from(cond);
    (a & m) | (b & !m)
}

/// The first four linearly independent rows of `sorted`, scanning in
/// order with masked rank tests on the minors (C `select_basis`).
pub fn select_basis(sorted: &[T4]) -> [T4; 4] {
    let mut out = [[0i8; 4]; 4];
    let mut slots = 0i32;
    for cand in sorted {
        let [v0, v1, v2, v3] = cand.map(i32::from);
        let [a0, a1, a2, a3] = out[0].map(i32::from);
        let [b0, b1, b2, b3] = out[1].map(i32::from);
        let [c0, c1, c2, c3] = out[2].map(i32::from);

        let t01 = a0 * v1 - a1 * v0;
        let t02 = a0 * v2 - a2 * v0;
        let t03 = a0 * v3 - a3 * v0;
        let t12 = a1 * v2 - a2 * v1;
        let t13 = a1 * v3 - a3 * v1;
        let t23 = a2 * v3 - a3 * v2;
        let test1 = (t01 | t02 | t03 | t12 | t13 | t23) != 0;

        let m01 = a0 * b1 - a1 * b0;
        let m02 = a0 * b2 - a2 * b0;
        let m03 = a0 * b3 - a3 * b0;
        let m12 = a1 * b2 - a2 * b1;
        let m13 = a1 * b3 - a3 * b1;
        let m23 = a2 * b3 - a3 * b2;
        let s012 = v0 * m12 - v1 * m02 + v2 * m01;
        let s013 = v0 * m13 - v1 * m03 + v3 * m01;
        let s023 = v0 * m23 - v2 * m03 + v3 * m02;
        let s123 = v1 * m23 - v2 * m13 + v3 * m12;
        let test2 = (s012 | s013 | s023 | s123) != 0;

        let k012 = c0 * m12 - c1 * m02 + c2 * m01;
        let k013 = c0 * m13 - c1 * m03 + c3 * m01;
        let k023 = c0 * m23 - c2 * m03 + c3 * m02;
        let k123 = c1 * m23 - c2 * m13 + c3 * m12;
        let test3 = v3 * k012 - v2 * k013 + v1 * k023 - v0 * k123 != 0;

        let test = select32(
            slots == 0,
            1,
            select32(
                slots == 1,
                test1.into(),
                select32(
                    slots == 2,
                    test2.into(),
                    select32(slots == 3, test3.into(), 0),
                ),
            ),
        );
        let accept = i32::from(slots < 4) & test;
        for (dest, row) in out.iter_mut().enumerate() {
            let take = accept & i32::from(slots == dest as i32) != 0;
            for (o, &c) in row.iter_mut().zip(cand) {
                *o = sel8(take, c, *o);
            }
        }
        slots += accept;
    }
    out
}

/// `(T^-1)^T` of a unimodular `T` with entries of magnitude at most 2 (C
/// `ct_mk_invert_transpose_int`).
pub fn invert_transpose(t: &[T4; 4]) -> [[i64; 4]; 4] {
    let u = t.map(|row| row.map(i64::from));
    let minor = |r: usize, s: usize, i: usize, j: usize| u[r][i] * u[s][j] - u[r][j] * u[s][i];
    let m = |i, j| minor(0, 1, i, j);
    let n = |i, j| minor(2, 3, i, j);
    let cof = |r: usize, a: usize, b: usize, c: usize, x: &dyn Fn(usize, usize) -> i64| {
        u[r][a] * x(b, c) - u[r][b] * x(a, c) + u[r][c] * x(a, b)
    };
    let c = [
        [
            cof(1, 1, 2, 3, &n),
            -cof(1, 0, 2, 3, &n),
            cof(1, 0, 1, 3, &n),
            -cof(1, 0, 1, 2, &n),
        ],
        [
            -cof(0, 1, 2, 3, &n),
            cof(0, 0, 2, 3, &n),
            -cof(0, 0, 1, 3, &n),
            cof(0, 0, 1, 2, &n),
        ],
        [
            cof(3, 1, 2, 3, &m),
            -cof(3, 0, 2, 3, &m),
            cof(3, 0, 1, 3, &m),
            -cof(3, 0, 1, 2, &m),
        ],
        [
            -cof(2, 1, 2, 3, &m),
            cof(2, 0, 2, 3, &m),
            -cof(2, 0, 1, 3, &m),
            cof(2, 0, 1, 2, &m),
        ],
    ];
    let det: i64 = (0..4).map(|j| u[0][j] * c[0][j]).sum();
    c.map(|row| row.map(|x| x * det))
}
