package no.nav.helse.domene.organisasjon

import arrow.core.Either
import arrow.core.flatMap
import no.nav.helse.Feilårsak
import no.nav.helse.oppslag.organisasjon.OrganisasjonClient
import no.nav.helse.domene.organisasjon.domain.Organisasjonsnummer
import no.nav.tjeneste.virksomhet.organisasjon.v5.binding.HentOrganisasjonOrganisasjonIkkeFunnet
import no.nav.tjeneste.virksomhet.organisasjon.v5.binding.HentOrganisasjonUgyldigInput
import org.slf4j.LoggerFactory
import java.time.LocalDate

class OrganisasjonService(private val organisasjonsClient: OrganisasjonClient) {

    companion object {
        private val log = LoggerFactory.getLogger(OrganisasjonService::class.java)
    }

    fun hentOrganisasjon(orgnr: Organisasjonsnummer) =
            organisasjonsClient.hentOrganisasjon(orgnr).toEither { err ->
                log.error("Error during organisasjon lookup", err)

                when (err) {
                    is HentOrganisasjonOrganisasjonIkkeFunnet -> Feilårsak.IkkeFunnet
                    is HentOrganisasjonUgyldigInput -> Feilårsak.FeilFraBruker
                    else -> Feilårsak.UkjentFeil
                }
            }.flatMap {
                OrganisasjonsMapper.fraOrganisasjon(it)?.let {
                    Either.Right(it)
                } ?: Either.Left(Feilårsak.UkjentFeil)
            }

    fun hentVirksomhetForJuridiskOrganisasjonsnummer(orgnr: Organisasjonsnummer, dato: LocalDate = LocalDate.now()) =
            organisasjonsClient.hentVirksomhetForJuridiskOrganisasjonsnummer(orgnr, dato).toEither { err ->
                log.error("Error during organisasjon lookup", err)
                Feilårsak.UkjentFeil
            }.flatMap {
                it.unntakForOrgnrListe.firstOrNull()?.let {
                    // example unntaksmeldinger:
                    // - <orgnr> er opphørt eller eksisterer ikke på dato <dato>
                    // - <orgnr> er et ugyldig organisasjonsnummer
                    // - <orgnr> har flere enn en aktiv virksomhet på dato <dato>
                    log.warn("Unntaksmelding for organisasjonsnummer ${orgnr.value}: ${it.unntaksmelding}")
                    Either.Left(Feilårsak.IkkeFunnet)
                } ?: it.orgnrForOrganisasjonListe.firstOrNull {
                    it.juridiskOrganisasjonsnummer == orgnr.value
                }?.let { organisasjon ->
                    Either.Right(Organisasjonsnummer(organisasjon.organisasjonsnummer))
                } ?: Feilårsak.IkkeFunnet.let {
                    log.error("did not find virksomhet for juridisk orgnr ${orgnr.value}")
                    Either.Left(it)
                }
            }
}