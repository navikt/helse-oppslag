package no.nav.helse.ws.inntekt

import io.prometheus.client.Counter
import no.nav.helse.Either
import no.nav.helse.Feilårsak
import no.nav.helse.bimap
import no.nav.helse.common.toLocalDate
import no.nav.helse.flatMap
import no.nav.helse.map
import no.nav.helse.sequenceU
import no.nav.helse.ws.AktørId
import no.nav.helse.ws.inntekt.domain.*
import no.nav.helse.ws.organisasjon.OrganisasjonService
import no.nav.helse.ws.organisasjon.domain.Organisasjonsnummer
import no.nav.tjeneste.virksomhet.inntekt.v3.binding.HentInntektListeBolkHarIkkeTilgangTilOensketAInntektsfilter
import no.nav.tjeneste.virksomhet.inntekt.v3.binding.HentInntektListeBolkUgyldigInput
import no.nav.tjeneste.virksomhet.inntekt.v3.informasjon.inntekt.AktoerId
import no.nav.tjeneste.virksomhet.inntekt.v3.informasjon.inntekt.ArbeidsInntektIdent
import no.nav.tjeneste.virksomhet.inntekt.v3.informasjon.inntekt.Organisasjon
import no.nav.tjeneste.virksomhet.inntekt.v3.informasjon.inntekt.PersonIdent
import no.nav.tjeneste.virksomhet.inntekt.v3.meldinger.HentInntektListeBolkResponse
import org.slf4j.LoggerFactory
import java.time.YearMonth

class InntektService(private val inntektClient: InntektClient, private val organisasjonService: OrganisasjonService) {

    companion object {
        private val log = LoggerFactory.getLogger("InntektService")

        private val virksomhetsCounter = Counter.build()
                .name("inntekt_virksomheter_totals")
                .labelNames("type")
                .help("antall inntekter fordelt på ulike virksomhetstyper (juridisk enhet eller virksomhet)")
                .register()
    }

    fun hentBeregningsgrunnlag(aktørId: AktørId, fom: YearMonth, tom: YearMonth) = hentInntekt(aktørId, fom, tom) {
        inntektClient.hentBeregningsgrunnlag(aktørId, fom, tom)
    }

    fun hentSammenligningsgrunnlag(aktørId: AktørId, fom: YearMonth, tom: YearMonth) = hentInntekt(aktørId, fom, tom) {
        inntektClient.hentSammenligningsgrunnlag(aktørId, fom, tom)
    }

    fun hentInntekter(aktørId: AktørId, fom: YearMonth, tom: YearMonth) = hentInntekt(aktørId, fom, tom) {
        inntektClient.hentInntekter(aktørId, fom, tom)
    }

    private fun hentInntekt(aktørId: AktørId, fom: YearMonth, tom: YearMonth, f: InntektService.() -> Either<Exception, HentInntektListeBolkResponse>) =
            f().bimap({
                when (it) {
                    is HentInntektListeBolkHarIkkeTilgangTilOensketAInntektsfilter -> Feilårsak.FeilFraTjeneste
                    is HentInntektListeBolkUgyldigInput -> Feilårsak.FeilFraTjeneste
                    else -> Feilårsak.UkjentFeil
                }
            }, {
                InntektMapper.mapToInntekt(aktørId, fom, tom, it.arbeidsInntektIdentListe)
            }).flatMap { inntekter ->
                inntekter.map {  inntekt ->
                    when (inntekt.virksomhet) {
                        is Virksomhet.Organisasjon -> {
                            organisasjonService.hentOrganisasjon(Organisasjonsnummer(inntekt.virksomhet.identifikator)).flatMap { organisasjon ->
                                virksomhetsCounter.labels(organisasjon.type()).inc()

                                when (organisasjon) {
                                    is no.nav.helse.ws.organisasjon.domain.Organisasjon.JuridiskEnhet -> organisasjonService.hentVirksomhetForJuridiskOrganisasjonsnummer(organisasjon.orgnr).map { virksomhetsnummer ->
                                        inntekt.copy(
                                                virksomhet = Virksomhet.Organisasjon(virksomhetsnummer)
                                        )
                                    }
                                    is no.nav.helse.ws.organisasjon.domain.Organisasjon.Virksomhet -> Either.Right(inntekt)
                                    else -> {
                                        log.error("unknown virksomhetstype: ${organisasjon.type()}")
                                        Either.Left(Feilårsak.UkjentFeil)
                                    }
                                }
                            }
                        }
                        else -> {
                            log.error("unknown virksomhetstype: ${inntekt.virksomhet.type()}")
                            Either.Left(Feilårsak.UkjentFeil)
                        }
                    }
                }.sequenceU()
            }
}

object InntektMapper {
    private val log = LoggerFactory.getLogger("InntektMapper")

    private val inntektCounter = Counter.build()
            .name("inntekt_totals")
            .help("antall inntekter mottatt")
            .register()
    private val andreAktørerCounter = Counter.build()
            .name("inntekt_andre_aktorer_totals")
            .help("antall inntekter mottatt med andre aktører enn den det ble gjort oppslag på")
            .register()
    private val inntekterUtenforPeriodeCounter = Counter.build()
            .name("inntekt_utenfor_periode_totals")
            .help("antall inntekter med periode (enten opptjeningsperiode eller utbetaltIPeriode) utenfor søkeperioden")
            .register()
    private val inntektPeriodeCounter = Counter.build()
            .name("inntekt_periode_totals")
            .help("antall inntekter fordelt på periode (opptjeningsperiode eller utbetaltIPeriode")
            .labelNames("type")
            .register()
    private val inntektArbeidsgivertypeCounter = Counter.build()
            .name("inntekt_arbeidsgivertype_totals")
            .labelNames("type")
            .help("antall inntekter fordelt på ulike arbeidsgivertyper")
            .register()
    private val ugyldigOpptjeningsperiodeCounter = Counter.build()
            .name("inntekt_ugyldig_opptjeningsperiode_totals")
            .labelNames("type")
            .help("antall inntekter med ugyldig opptjeningsperiode")
            .register()

    fun mapToInntekt(aktørId: AktørId, fom: YearMonth, tom: YearMonth, arbeidsInntektIdentListe: List<ArbeidsInntektIdent>) =
            arbeidsInntektIdentListe.flatMap {
                it.arbeidsInntektMaaned
            }.flatMap {
                it.arbeidsInntektInformasjon.inntektListe
            }.onEach {
                inntektCounter.inc()
            }.filter {
                if (erSammeAktør(aktørId)(it)) {
                    true
                } else {
                    andreAktørerCounter.inc()
                    false
                }
            }
                    .onEach {
                        if (it.opptjeningsperiode != null) {
                            inntektPeriodeCounter.labels("opptjeningsperiode").inc()
                        } else {
                            inntektPeriodeCounter.labels("utbetaltIPeriode").inc()
                        }
                    }
                    .onEach {
                        inntektArbeidsgivertypeCounter.labels(when (it.virksomhet) {
                            is Organisasjon -> "organisasjon"
                            is PersonIdent -> "personIdent"
                            is AktoerId -> "aktoerId"
                            else -> "ukjent"
                        }).inc()
                    }
                    .filter {
                        if (erUtbetalingsperiodeInnenforPeriode(fom, tom, it)) {
                            true
                        } else {
                            inntekterUtenforPeriodeCounter.inc()
                            false
                        }
                    }
                    .onEach {
                        if (it.opptjeningsperiode != null) {
                            val opptjeningsperiodeFom = it.opptjeningsperiode.startDato.toLocalDate()
                            val opptjeningsperiodeTom = it.opptjeningsperiode.sluttDato.toLocalDate()

                            if (opptjeningsperiodeFom.withDayOfMonth(1) != opptjeningsperiodeTom.withDayOfMonth(1)) {
                                ugyldigOpptjeningsperiodeCounter.labels("periode_over_en_kalendermåned").inc()
                            }
                            if (opptjeningsperiodeTom < opptjeningsperiodeFom) {
                                ugyldigOpptjeningsperiodeCounter.labels("fom_er_større_enn_tom").inc()
                            }
                        }
                    }
                    .map { inntekt ->
                        when (inntekt.virksomhet) {
                            is Organisasjon -> Virksomhet.Organisasjon(Organisasjonsnummer((inntekt.virksomhet as Organisasjon).orgnummer))
                            is PersonIdent -> Virksomhet.Person((inntekt.virksomhet as PersonIdent).personIdent)
                            is AktoerId -> Virksomhet.NavAktør((inntekt.virksomhet as AktoerId).aktoerId)
                            else -> {
                                log.warn("ukjent virksomhet: ${inntekt.virksomhet.javaClass.name}")
                                null
                            }
                        }?.let { virksomhet ->
                            Inntekt(
                                    virksomhet = virksomhet,
                                    utbetalingsperiode = YearMonth.of(inntekt.utbetaltIPeriode.year, inntekt.utbetaltIPeriode.month),
                                    beløp = inntekt.beloep)
                        }
                    }.filterNotNull()

    private fun erSammeAktør(aktørId: AktørId) = { inntekt: no.nav.tjeneste.virksomhet.inntekt.v3.informasjon.inntekt.Inntekt ->
        if (inntekt.inntektsmottaker is AktoerId) {
            if ((inntekt.inntektsmottaker as AktoerId).aktoerId == aktørId.aktor) {
                true
            } else {
                log.warn("Inntekt gjelder for annen aktør (${(inntekt.inntektsmottaker as AktoerId).aktoerId}) enn det vi forventet (${aktørId.aktor})")
                false
            }
        } else {
            log.warn("inntektsmottaker er ikke en AktørId")
            false
        }
    }

    private fun erUtbetalingsperiodeInnenforPeriode(fom: YearMonth, tom: YearMonth, inntekt: no.nav.tjeneste.virksomhet.inntekt.v3.informasjon.inntekt.Inntekt) =
            if (fom.atDay(1) <= inntekt.utbetaltIPeriode.toLocalDate()
                    && tom.atEndOfMonth() >= inntekt.utbetaltIPeriode.toLocalDate()) {
                true
            } else {
                log.warn("utbetaltIPeriode (${inntekt.utbetaltIPeriode.toLocalDate()}) er utenfor perioden")
                false
            }
}
