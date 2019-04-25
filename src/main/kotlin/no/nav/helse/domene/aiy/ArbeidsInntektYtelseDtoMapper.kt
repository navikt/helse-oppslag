package no.nav.helse.domene.aiy

import no.nav.helse.domene.aiy.domain.ArbeidInntektYtelse
import no.nav.helse.domene.aiy.dto.*
import no.nav.helse.domene.arbeid.ArbeidDtoMapper
import no.nav.helse.domene.inntekt.InntektDtoMapper
import java.math.BigDecimal

object ArbeidsInntektYtelseDtoMapper {

    fun toDto(arbeidInntektYtelse: ArbeidInntektYtelse) =
            arbeidInntektYtelse.arbeidsforhold.map { arbeidsforhold ->
                arbeidsforhold.value.mapValues { periode ->
                    periode.value.map {
                        InntektDTO(it.beløp)
                    }.let { inntekter ->
                        periode.value.fold(BigDecimal.ZERO) { acc, inntekt ->
                            acc.add(inntekt.beløp)
                        }.let { sumForPeriode ->
                            InntektsperiodeDTO(
                                    sum = sumForPeriode,
                                    inntekter = inntekter
                            )
                        }
                    }
                }.let {
                    ArbeidsforholdMedInntektDTO(
                            arbeidsforhold = ArbeidDtoMapper.toDto(arbeidsforhold.key),
                            perioder = it
                    )
                }
            }.let { arbeidsforhold ->
                arbeidInntektYtelse.inntekterUtenArbeidsforhold.map { inntekt ->
                    InntektMedArbeidsgiverDTO(
                            arbeidsgiver = InntektDtoMapper.toDto(inntekt.virksomhet),
                            beløp = inntekt.beløp
                    )
                }.let { inntekterUtenArbeidsforhold ->
                    arbeidInntektYtelse.arbeidsforholdUtenInntekter.map(ArbeidDtoMapper::toDto).let { arbeidsforholdUtenInntekter ->
                        arbeidInntektYtelse.ytelser.map {
                            YtelseDTO(it.virksomhet, it.utbetalingsperiode, it.beløp, it.kode)
                        }.let { ytelser ->
                            arbeidInntektYtelse.pensjonEllerTrygd.map {
                                PensjonEllerTrygdDTO(it.virksomhet, it.utbetalingsperiode, it.beløp, it.kode)
                            }.let { pensjonEllerTrygd ->
                                arbeidInntektYtelse.næringsinntekt.map {
                                    NæringDTO(it.virksomhet, it.utbetalingsperiode, it.beløp, it.kode)
                                }.let { næring ->
                                    ArbeidInntektYtelseDTO(arbeidsforhold, inntekterUtenArbeidsforhold, arbeidsforholdUtenInntekter, ytelser, pensjonEllerTrygd, næring)
                                }
                            }
                        }
                    }
                }
            }

}