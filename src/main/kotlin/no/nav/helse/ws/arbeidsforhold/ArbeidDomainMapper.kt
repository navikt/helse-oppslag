package no.nav.helse.ws.arbeidsforhold

import no.nav.helse.common.toLocalDate
import no.nav.helse.ws.arbeidsforhold.domain.Arbeidsavtale
import no.nav.helse.ws.arbeidsforhold.domain.Arbeidsforhold
import no.nav.helse.ws.arbeidsforhold.domain.Arbeidsgiver.*
import no.nav.helse.ws.arbeidsforhold.domain.Permisjon
import no.nav.helse.ws.organisasjon.domain.Organisasjonsnummer
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.informasjon.arbeidsforhold.Organisasjon
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.informasjon.arbeidsforhold.Person
import org.slf4j.LoggerFactory

object ArbeidDomainMapper {

    private val log = LoggerFactory.getLogger(ArbeidDomainMapper::class.java)

    fun toArbeidsforhold(arbeidsforhold: no.nav.tjeneste.virksomhet.arbeidsforhold.v3.informasjon.arbeidsforhold.Arbeidsforhold) =
            when (arbeidsforhold.arbeidsgiver) {
                is Organisasjon -> Virksomhet(Organisasjonsnummer((arbeidsforhold.arbeidsgiver as Organisasjon).orgnummer))
                is Person -> Person((arbeidsforhold.arbeidsgiver as Person).ident.ident)
                else -> {
                    log.error("unknown arbeidsgivertype: ${arbeidsforhold.arbeidsgiver}")
                    null
                }
            }?.let { arbeidsgiver ->
                Arbeidsforhold(
                        arbeidsgiver = arbeidsgiver,
                        startdato = arbeidsforhold.ansettelsesPeriode.periode.fom.toLocalDate(),
                        sluttdato = arbeidsforhold.ansettelsesPeriode.periode.tom?.toLocalDate(),
                        arbeidsforholdId = arbeidsforhold.arbeidsforholdIDnav,
                        arbeidsavtaler = arbeidsforhold.arbeidsavtale.map(::toArbeidsavtale),
                        permisjon = arbeidsforhold.permisjonOgPermittering.map { permisjonOgPermittering ->
                            Permisjon(
                                    fom = permisjonOgPermittering.permisjonsPeriode.fom.toLocalDate(),
                                    tom = permisjonOgPermittering.permisjonsPeriode.tom.toLocalDate(),
                                    permisjonsprosent = permisjonOgPermittering.permisjonsprosent,
                                    årsak = permisjonOgPermittering.permisjonOgPermittering.value
                            )
                        }
                )
            }

    fun toArbeidsavtale(arbeidsavtale: no.nav.tjeneste.virksomhet.arbeidsforhold.v3.informasjon.arbeidsforhold.Arbeidsavtale) =
            Arbeidsavtale(
                    yrke = arbeidsavtale.yrke.value,
                    stillingsprosent = arbeidsavtale.stillingsprosent,
                    fom = arbeidsavtale.fomGyldighetsperiode.toLocalDate(),
                    tom = arbeidsavtale.tomGyldighetsperiode?.toLocalDate()
            )
}
