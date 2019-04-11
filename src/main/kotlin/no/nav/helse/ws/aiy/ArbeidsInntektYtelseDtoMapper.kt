package no.nav.helse.ws.aiy

import no.nav.helse.ws.aiy.domain.ArbeidInntektYtelse
import no.nav.helse.ws.aiy.domain.Arbeidsforhold
import no.nav.helse.ws.aiy.dto.*
import no.nav.helse.ws.inntekt.InntektDtoMapper

object ArbeidsInntektYtelseDtoMapper {

    fun toArbeidsforholdDto(arbeidsforhold: Arbeidsforhold) = ArbeidsforholdDTO(
            type = arbeidsforhold.type(),
            arbeidsgiver = InntektDtoMapper.toDto(arbeidsforhold.arbeidsgiver),
            startdato = arbeidsforhold.startdato,
            sluttdato = arbeidsforhold.sluttdato,
            yrke = when (arbeidsforhold) {
                is Arbeidsforhold.Frilans -> arbeidsforhold.yrke
                else -> null
            }
    )

    fun toDto(arbeidInntektYtelse: ArbeidInntektYtelse) =
            arbeidInntektYtelse.arbeidsforhold.map { arbeidsforhold ->
                arbeidsforhold.value.mapValues { inntekt ->
                    inntekt.value.map {
                        InntektDTO(it.beløp)
                    }
                }.let {
                    ArbeidsforholdMedInntektDTO(
                            arbeidsforhold = toArbeidsforholdDto(arbeidsforhold.key),
                            inntekter = it
                    )
                }
            }.let { arbeidsforhold ->
                arbeidInntektYtelse.inntekterUtenArbeidsforhold.map { inntekt ->
                    InntektMedArbeidsgiverDTO(
                            arbeidsgiver = InntektDtoMapper.toDto(inntekt.virksomhet),
                            beløp = inntekt.beløp
                    )
                }.let { inntekterUtenArbeidsforhold ->
                    arbeidInntektYtelse.arbeidsforholdUtenInntekter.map(::toArbeidsforholdDto).let { arbeidsforholdUtenInntekter ->
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
