//! The key generation and signing protocols (vendor `keygen.c`, `sign.c`).
//!
//! Signing fails only on events of negligible probability; C reports some
//! of them and continues past others with unwritten outputs, which is
//! undefined behaviour. Here every one of them makes signing fail, through
//! `Option` combinators.

use super::{SecretKey, SignParams};
use crate::ec::{Basis, Curve, Point};
use crate::hd::{CoupleCurve, CouplePoint, KernelCouplePoints};
use crate::id2iso::{
    arbitrary_isogeny_evaluation, change_of_basis_matrix_tate, change_of_basis_matrix_tate_invert,
    digits, endomorphism_application_even_basis, ideal_to_isogeny_qlapoty,
    kernel_dlogs_to_ideal_even, matrix_application_even_basis,
};
use crate::mp::Ibz;
use crate::prng::{Prng, Separator, SEED_BYTES};
use crate::quat::{dim2, Alg, AlgElem, Draw, Ideal};
use crate::verify::{PublicKey, Scalar, Signature, EC_EXTRA_TORSION, HD_EXTRA_TORSION};

/// A curve with a basis of its `2^f`-torsion.
struct CurveWithBasis<P: SignParams> {
    curve: Curve<P>,
    basis: Basis<P>,
}

fn coprime<P: SignParams>(a: &Ibz<P>, b: &Ibz<P>) -> bool {
    a.gcd(b).is_one()
}

/// A random ideal of prime norm `degree`, replaced by a small equivalent
/// ideal of odd norm prime (`coprime_to = 0`) or arbitrary (`2`), and its
/// isogeny from `E0` with the image of `BASIS_EVEN`, if that succeeds.
fn random_isogeny<P: SignParams>(
    prng: &mut Prng,
    sep: Separator,
    degree: &Ibz<P>,
    coprime_to: &Ibz<P>,
) -> Option<(Ideal<P>, CurveWithBasis<P>)> {
    let mut stream = prng.domain(sep);
    let ideal =
        Ideal::random_given_prime_norm(degree, &mut Draw::new(&mut stream, prng.default_domain()));
    let (_, ideal) = ideal.small_equivalent_coprime(
        coprime_to,
        &mut Draw::new(&mut stream, prng.default_domain()),
    );
    arbitrary_isogeny_evaluation(&ideal, prng.default_domain())
        .map(|(basis, curve)| (ideal, CurveWithBasis { curve, basis }))
}

/// The `(2^n, 2^n)`-isogeny `E_com x E_aux -> E_aux' x E_chall'` with
/// kernel `<(P_com, [deg_inv] P_aux), (Q_com, [deg_inv] Q_aux)>` for the
/// bases of order `2^(n+2)`, `n = RESPONSE_BITS`, and the images of the
/// commitment basis on `E_chall'` and `E_aux'` as `(E_aux', E_chall')`
/// (C `compute_dim2_isogeny_challenge`).
fn dim2_challenge<P: SignParams>(
    com: &CurveWithBasis<P>,
    aux: &CurveWithBasis<P>,
    deg_inv: &Ibz<P>,
) -> Option<(CurveWithBasis<P>, CurveWithBasis<P>)> {
    let reduced = P::RESPONSE_BITS + HD_EXTRA_TORSION;
    let mut e12 = CoupleCurve {
        e1: com.curve,
        e2: aux.curve,
    };
    let mut ker = KernelCouplePoints::from_bases(&com.basis, &aux.basis);
    let s = digits(deg_inv);
    ker.t1.p2 = e12.e2.mul(&s, reduced, &ker.t1.p2);
    ker.t2.p2 = e12.e2.mul(&s, reduced, &ker.t2.p2);
    let b = &com.basis;
    let mut push = [b.p, b.q, b.pmq].map(|p1| CouplePoint {
        p1,
        p2: Point::IDENTITY,
    });
    e12.chain_compute_and_eval(P::RESPONSE_BITS, &ker, &mut push)
        .map(|cod| {
            let [t1, t2, t12] = push;
            let aux = CurveWithBasis {
                curve: cod.e2,
                basis: Basis {
                    p: t1.p2,
                    q: t2.p2,
                    pmq: t12.p2,
                },
            };
            let chall = CurveWithBasis {
                curve: cod.e1,
                basis: Basis {
                    p: t1.p1,
                    q: t2.p1,
                    pmq: t12.p1,
                },
            };
            (aux, chall)
        })
}

/// `x` as the words of a signature entry (C `ibz_to_digit_array`).
fn scalar<P: SignParams>(x: &Ibz<P>) -> Scalar {
    let mut s = Scalar::default();
    x.to_digits(&mut s);
    s
}

impl<P: SignParams> SecretKey<P> {
    /// A key pair from the seed `seed` (C `protocols_keygen`).
    pub fn generate(seed: &[u8; SEED_BYTES]) -> Self {
        let mut prng = Prng::new(seed);
        // Every attempt continues the keygen stream, as in C.
        let mut stream = prng.domain(Separator::Keygen);
        let (secret_ideal, b0) = core::iter::repeat_with(|| {
            let ideal = Ideal::random_given_prime_norm(
                &P::SEC_DEGREE,
                &mut Draw::new(&mut stream, prng.default_domain()),
            );
            let (_, ideal) = ideal.small_equivalent_coprime(
                &Ibz::ZERO,
                &mut Draw::new(&mut stream, prng.default_domain()),
            );
            arbitrary_isogeny_evaluation(&ideal, prng.default_domain()).map(|b| (ideal, b))
        })
        .find_map(|x| x)
        .expect("an endless search finds a key");
        let (basis, mut curve) = b0;

        let f = P::CHALLENGE_BITS + EC_EXTRA_TORSION;
        let (canonical, hint) = curve.basis_2f_to_hint(f, 0);
        let mut mat = change_of_basis_matrix_tate(&canonical, &basis, &mut curve, f);
        for x in mat.iter_mut().flatten() {
            *x = x.mod_2exp(P::CHALLENGE_BITS);
        }
        dim2::mat_normalize(&mut mat, P::CHALLENGE_BITS);

        let mut secret_ideal = secret_ideal;
        for x in [
            &mut secret_ideal.norm,
            &mut secret_ideal.x,
            &mut secret_ideal.y,
        ] {
            x.set_bound(8 * P::ENCODED_BYTES + 1);
        }
        for x in mat.iter_mut().flatten() {
            x.set_bound(8 * P::CHALLENGE_BYTES + 1);
        }
        let pk = PublicKey {
            curve: Curve {
                a24_normalized: false,
                ..curve
            },
            hint,
        };
        Self {
            pk,
            secret_ideal,
            mat,
            curve,
        }
    }

    /// The challenge ideal of `chall` pulled back through the secret ideal,
    /// and its split part (C `compute_challenge_ideal_signature`).
    fn challenge_ideal(&self, chall: &Scalar) -> (Ideal<P>, AlgElem<P>) {
        let n = P::ORDER_WORDS;
        let v = [Ibz::from_i64(1, 2), Ibz::from_digits(&chall[..n], n)];
        let v = dim2::mat_eval(&self.mat, &v);
        kernel_dlogs_to_ideal_even(&v, P::CHALLENGE_BITS)
    }

    /// The signature of `msg` from the seed `seed`, or `None` if signing
    /// fails (C `protocols_sign`).
    pub fn sign(&self, msg: &[u8], seed: &[u8; SEED_BYTES]) -> Option<Signature<P>> {
        let mut prng = Prng::new(seed);
        // C asserts the commitment succeeds and continues with unwritten
        // outputs otherwise.
        random_isogeny(&mut prng, Separator::Commitment, &P::COM_DEGREE, &Ibz::TWO)
            .and_then(|(ideal_commit, com)| self.respond(&mut prng, msg, &ideal_commit, com))
    }

    /// The signature for the commitment `ideal_commit` with isogeny `com`.
    fn respond(
        &self,
        prng: &mut Prng,
        msg: &[u8],
        ideal_commit: &Ideal<P>,
        com: CurveWithBasis<P>,
    ) -> Option<Signature<P>> {
        let t = P::T as usize;
        let reduced = P::RESPONSE_BITS + HD_EXTRA_TORSION;
        let chall = self.pk.hash_to_challenge(&com.curve, msg);
        let (chall_two, chall_split) = self.challenge_ideal(&chall);

        let mut stream = prng.domain(Separator::Response);
        let r = self.secret_ideal.response_element(
            &chall_two,
            &chall_split,
            ideal_commit,
            &mut stream,
            prng.default_domain(),
            P::RESPONSE_BITS - 1,
        );
        let pow = Ibz::ONE.mul_2exp(P::RESPONSE_BITS);
        let mut aux_norm = pow - r.norm;
        aux_norm.set_bound(P::RESPONSE_BITS + 1);
        let remain = pow.mul_2exp(HD_EXTRA_TORSION);

        // C fails on non-coprime response norms before sampling; sampling
        // first draws only from streams the failure discards.
        Ideal::random_given_odd_norm(
            &aux_norm,
            &r.sk_chall_quat,
            &mut Draw::new(&mut stream, prng.default_domain()),
        )
        // C keeps the zero ideal of `quat_ideal_init` when the inert part is
        // unwritten, whose norm then fails the coprimality check.
        .map(|(aux, split)| (aux.unwrap_or_default(), split))
        .filter(|(aux, _)| {
            coprime(&r.sk_chall_ideal.norm, &ideal_commit.norm)
                && coprime(&aux.norm, &r.sk_chall_ideal.norm)
                && coprime(&aux.norm, &self.secret_ideal.norm)
        })
        .and_then(|(aux, aux_split)| {
            let skchall_aux = aux.intersect_o0(&r.sk_chall_ideal, Some(&aux_split));
            // C ignores a failure here and continues with unwritten outputs.
            ideal_to_isogeny_qlapoty(&skchall_aux, prng.default_domain()).map(|d| (d, aux_split))
        })
        .and_then(|(d, mut aux_split)| {
            let (inv, _) = r.sk_chall_ideal.norm.invmod(&remain);
            for c in &mut aux_split.coord[..2] {
                *c = (*c * inv).modulo(&remain);
            }
            for c in &mut aux_split.coord[2..] {
                c.set_bound(remain.bound());
            }
            let resp_aux = Alg::pinfty().mul(&aux_split, &r.resp_quat);
            let mut aux = CurveWithBasis {
                curve: d.codomain,
                basis: d.basis,
            };
            endomorphism_application_even_basis(&mut aux.basis, &aux.curve, &resp_aux, t, false);

            let mut com = com;
            com.basis = com.curve.dbl_iter_basis(&com.basis, t - reduced);
            aux.basis = aux.curve.dbl_iter_basis(&aux.basis, t - reduced);
            let (inv, _) = r.norm.invmod(&remain);
            dim2_challenge(&com, &aux, &inv)
        })
        .map(|(aux, chall_basis)| Self::signature(aux, chall_basis, chall))
    }

    /// The signature from the codomain bases `aux` and `chall_b` (C
    /// `set_aux_curve_signature`, `compute_and_set_basis_change_matrix`).
    fn signature(
        mut aux: CurveWithBasis<P>,
        mut ch: CurveWithBasis<P>,
        chall: Scalar,
    ) -> Signature<P> {
        let t = P::T as usize;
        let f = P::RESPONSE_BITS + HD_EXTRA_TORSION;
        aux.curve.normalize();
        let aux_a = aux.curve.a;
        let (can_chall, hint_chall) = ch.curve.basis_2f_to_hint(t, f);
        let (can_aux, hint_aux) = aux.curve.basis_2f_to_hint(t, f);
        let mut m = change_of_basis_matrix_tate_invert(&can_aux, &aux.basis, &mut aux.curve, f);
        matrix_application_even_basis(&mut ch.basis, &ch.curve, &mut m, f, true);
        let mut m = change_of_basis_matrix_tate(&ch.basis, &can_chall, &mut ch.curve, f);
        dim2::mat_normalize(&mut m, f);
        Signature {
            aux_a,
            mat: m.map(|row| row.map(|x| scalar(&x))),
            chall,
            hint_aux,
            hint_chall,
        }
    }
}
