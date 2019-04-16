package no.nav.helse.ws.arbeidsforhold

import arrow.core.Either
import arrow.core.Try
import io.mockk.every
import io.mockk.mockk
import no.nav.helse.Feilårsak
import no.nav.helse.common.toLocalDate
import no.nav.helse.common.toXmlGregorianCalendar
import no.nav.helse.ws.AktørId
import no.nav.helse.ws.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.helse.ws.arbeidsforhold.domain.Arbeidsgiver
import no.nav.helse.ws.arbeidsforhold.domain.Permisjon
import no.nav.helse.ws.organisasjon.OrganisasjonService
import no.nav.helse.ws.organisasjon.domain.Organisasjonsnummer
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.binding.FinnArbeidsforholdPrArbeidstakerSikkerhetsbegrensning
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.binding.FinnArbeidsforholdPrArbeidstakerUgyldigInput
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.feil.Sikkerhetsbegrensning
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.feil.UgyldigInput
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.informasjon.arbeidsforhold.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class ArbeidsforholdServiceTest {

    @Test
    fun `skal returnere liste over arbeidsforhold`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        val organisasjonService = mockk<OrganisasjonService>()

        val aktørId = AktørId("123456789")
        val fom = LocalDate.parse("2019-01-01")
        val tom = LocalDate.parse("2019-02-01")

        val expected = listOf(
                forventet_arbeidsforhold_uten_sluttdato,
                forventet_avsluttet_arbeidsforhold_med_permittering
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns Try.Success(listOf(arbeidsforhold_uten_sluttdato, avsluttet_arbeidsforhold_med_permittering))

        every {
            arbeidsforholdClient.finnHistoriskeArbeidsavtaler(arbeidsforholdID_for_arbeidsforhold_1)
        } returns Try.Success(arbeidsforhold_uten_sluttdato_avtaler)

        every {
            arbeidsforholdClient.finnHistoriskeArbeidsavtaler(arbeidsforholdID_for_arbeidsforhold_2)
        } returns Try.Success(avsluttet_arbeidsforhold_med_permittering_avtaler)

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService)
                .finnArbeidsforhold(aktørId, fom, tom)

        when (actual) {
            is Either.Right -> assertEquals(expected, actual.b)
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }

    @Test
    fun `ukjent arbeidsgivertype skal merkes som ukjent`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        val organisasjonService = mockk<OrganisasjonService>()

        val aktørId = AktørId("123456789")
        val fom = LocalDate.parse("2019-01-01")
        val tom = LocalDate.parse("2019-02-01")

        val expected = listOf(
                forventet_arbeidsforhold_med_person_som_arbeidsgiver
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns Try.Success(listOf(arbeidsforhold_med_person_som_arbeidsgiver,
                arbeidsforhold_med_historisk_arbeidsgiver))

        every {
            arbeidsforholdClient.finnHistoriskeArbeidsavtaler(arbeidsforholdID_for_arbeidsforhold_1)
        } returns Try.Success(arbeidsforhold_med_person_som_arbeidsgiver_avtaler)

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService)
                .finnArbeidsforhold(aktørId, fom, tom)

        when (actual) {
            is Either.Right -> assertEquals(expected, actual.b)
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }

    @Test
    fun `skal mappe sikkerhetsbegrensning til feilårsak`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        every {
            arbeidsforholdClient.finnArbeidsforhold(any(), any(), any())
        } returns Try.Failure(FinnArbeidsforholdPrArbeidstakerSikkerhetsbegrensning("Fault", Sikkerhetsbegrensning()))

        val actual = ArbeidsforholdService(arbeidsforholdClient, mockk()).finnArbeidsforhold(
                AktørId("11987654321"), LocalDate.now(), LocalDate.now())

        when (actual) {
            is Either.Left -> assertEquals(Feilårsak.FeilFraTjeneste, actual.a)
            is Either.Right -> fail { "Expected Either.Left to be returned" }
        }
    }

    @Test
    fun `skal mappe ugyldig input til feilårsak`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        every {
            arbeidsforholdClient.finnArbeidsforhold(any(), any(), any())
        } returns Try.Failure(FinnArbeidsforholdPrArbeidstakerUgyldigInput("Fault", UgyldigInput()))

        val actual = ArbeidsforholdService(arbeidsforholdClient, mockk()).finnArbeidsforhold(
                AktørId("11987654321"), LocalDate.now(), LocalDate.now())

        when (actual) {
            is Either.Left -> assertEquals(Feilårsak.FeilFraTjeneste, actual.a)
            is Either.Right -> fail { "Expected Either.Left to be returned" }
        }
    }

    @Test
    fun `skal mappe exceptions til feilårsak`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        every {
            arbeidsforholdClient.finnArbeidsforhold(any(), any(), any())
        } returns Try.Failure(Exception("Fault"))

        val actual = ArbeidsforholdService(arbeidsforholdClient, mockk()).finnArbeidsforhold(
                AktørId("11987654321"), LocalDate.now(), LocalDate.now())

        when (actual) {
            is Either.Left -> assertEquals(Feilårsak.UkjentFeil, actual.a)
            is Either.Right -> fail { "Expected Either.Left to be returned" }
        }
    }

    @Test
    fun `skal returnere feil når arbeidsforholdoppslag gir feil`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        val organisasjonService = mockk<OrganisasjonService>()

        val aktørId = AktørId("123456789")
        val fom = LocalDate.parse("2019-01-01")
        val tom = LocalDate.parse("2019-02-01")

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns Try.Failure(Exception("SOAP fault"))

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService)
                .finnArbeidsgivere(aktørId, fom, tom)

        when (actual) {
            is Either.Left -> assertTrue(actual.a is Feilårsak.UkjentFeil)
            is Either.Right -> fail { "Expected Either.Left to be returned" }
        }
    }

    @Test
    fun `skal returnere en liste over organisasjoner`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        val organisasjonService = mockk<OrganisasjonService>()

        val aktørId = AktørId("123456789")
        val fom = LocalDate.parse("2019-01-01")
        val tom = LocalDate.parse("2019-02-01")

        val expected = listOf(
                no.nav.helse.ws.organisasjon.domain.Organisasjon.Virksomhet(Organisasjonsnummer(arbeidsgiver_organisasjon_1.orgnummer),
                        arbeidsgiver_organisasjon_1.navn),
                no.nav.helse.ws.organisasjon.domain.Organisasjon.Virksomhet(Organisasjonsnummer(arbeidsgiver_organisasjon_2.orgnummer),
                        arbeidsgiver_organisasjon_2.navn)
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns Try.Success(listOf(arbeidsforhold_uten_sluttdato, avsluttet_arbeidsforhold_med_permittering))

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService)
                .finnArbeidsgivere(aktørId, fom, tom)

        when (actual) {
            is Either.Right -> assertEquals(expected, actual.b)
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }

    @Test
    fun `skal slå opp navn på organisasjon`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        val organisasjonService = mockk<OrganisasjonService>()

        val aktørId = AktørId("123456789")
        val fom = LocalDate.parse("2019-01-01")
        val tom = LocalDate.parse("2019-02-01")

        val expected = listOf(
                no.nav.helse.ws.organisasjon.domain.Organisasjon.Virksomhet(Organisasjonsnummer(arbeidsgiver_organisasjon_3.orgnummer),
                        arbeidsgiver_organisasjon_3_navn)
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns Try.Success(listOf(arbeidsforhold_med_arbeidsgiver_uten_navn))

        every {
            organisasjonService.hentOrganisasjon(Organisasjonsnummer(arbeidsgiver_organisasjon_3.orgnummer))
        } returns Either.Right(no.nav.helse.ws.organisasjon.domain.Organisasjon.Virksomhet(Organisasjonsnummer(arbeidsgiver_organisasjon_3.orgnummer),
                arbeidsgiver_organisasjon_3_navn))

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService)
                .finnArbeidsgivere(aktørId, fom, tom)

        when (actual) {
            is Either.Right -> assertEquals(expected, actual.b)
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }

    @Test
    fun `skal returnere feil når oppslag av arbeidsforhold feiler`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        val organisasjonService = mockk<OrganisasjonService>()

        val aktørId = AktørId("123456789")
        val fom = LocalDate.parse("2019-01-01")
        val tom = LocalDate.parse("2019-02-01")

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns Try.Failure(Exception("SOAP fault"))

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService)
                .finnArbeidsgivere(aktørId, fom, tom)

        when (actual) {
            is Either.Left -> assertTrue(actual.a is Feilårsak.UkjentFeil)
            is Either.Right -> fail { "Expected Either.Left to be returned" }
        }
    }

    @Test
    fun `skal returnere tomt organisasjonnavn når oppslag av organisasjonnavn feiler`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        val organisasjonService = mockk<OrganisasjonService>()

        val aktørId = AktørId("123456789")
        val fom = LocalDate.parse("2019-01-01")
        val tom = LocalDate.parse("2019-02-01")

        val expected = listOf(
                no.nav.helse.ws.organisasjon.domain.Organisasjon.Virksomhet(Organisasjonsnummer(arbeidsgiver_organisasjon_3.orgnummer), null)
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns Try.Success(listOf(arbeidsforhold_med_arbeidsgiver_uten_navn))

        every {
            organisasjonService.hentOrganisasjon(any())
        } returns Either.Left(Feilårsak.FeilFraTjeneste)

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService)
                .finnArbeidsgivere(aktørId, fom, tom)

        when (actual) {
            is Either.Right -> assertEquals(expected, actual.b)
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }

    @Test
    fun `skal fjerne duplikater ved flere arbeidsforhold i samme virksomhet`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        val organisasjonService = mockk<OrganisasjonService>()

        val aktørId = AktørId("123456789")
        val fom = LocalDate.parse("2019-01-01")
        val tom = LocalDate.parse("2019-02-01")

        val expected = listOf(
                no.nav.helse.ws.organisasjon.domain.Organisasjon.Virksomhet(Organisasjonsnummer(arbeidsgiver_organisasjon_1.orgnummer),
                        arbeidsgiver_organisasjon_1.navn)
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns Try.Success(listOf(arbeidsforhold_uten_sluttdato, arbeidsforhold_uten_sluttdato))

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService)
                .finnArbeidsgivere(aktørId, fom, tom)

        when (actual) {
            is Either.Right -> assertEquals(expected, actual.b)
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }

    @Test
    fun `skal mappe sikkerhetsbegrensning til feilårsak for arbeidsgiveroppslag`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        every {
            arbeidsforholdClient.finnArbeidsforhold(any(), any(), any())
        } returns Try.Failure(FinnArbeidsforholdPrArbeidstakerSikkerhetsbegrensning("Fault", Sikkerhetsbegrensning()))


        val actual = ArbeidsforholdService(arbeidsforholdClient, mockk()).finnArbeidsgivere(
                AktørId("11987654321"), LocalDate.now(), LocalDate.now())

        when (actual) {
            is Either.Left -> assertEquals(Feilårsak.FeilFraTjeneste, actual.a)
            is Either.Right -> fail { "Expected Either.Left to be returned" }
        }
    }

    @Test
    fun `skal mappe ugyldig input til feilårsak for arbeidsgiveroppslag`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        every {
            arbeidsforholdClient.finnArbeidsforhold(any(), any(), any())
        } returns Try.Failure(FinnArbeidsforholdPrArbeidstakerUgyldigInput("Fault", UgyldigInput()))

        val actual = ArbeidsforholdService(arbeidsforholdClient, mockk()).finnArbeidsgivere(
                AktørId("11987654321"), LocalDate.now(), LocalDate.now())

        when (actual) {
            is Either.Left -> assertEquals(Feilårsak.FeilFraTjeneste, actual.a)
            is Either.Right -> fail { "Expected Either.Left to be returned" }
        }
    }

    @Test
    fun `skal mappe exceptions til feilårsak for arbeidsgiveroppslag`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        every {
            arbeidsforholdClient.finnArbeidsforhold(any(), any(), any())
        } returns Try.Failure(Exception("Fault"))

        val actual = ArbeidsforholdService(arbeidsforholdClient, mockk()).finnArbeidsgivere(
                AktørId("11987654321"), LocalDate.now(), LocalDate.now())

        when (actual) {
            is Either.Left -> assertEquals(Feilårsak.UkjentFeil, actual.a)
            is Either.Right -> fail { "Expected Either.Left to be returned" }
        }
    }
}

private val arbeidsgiver_organisasjon_1 = Organisasjon().apply {
    orgnummer = "889640782"
    navn = "S. VINDEL & SØNN"
}

private val arbeidsgiver_organisasjon_2 = Organisasjon().apply {
    orgnummer = "995298775"
    navn = "MATBUTIKKEN AS"
}

private val arbeidsgiver_organisasjon_3 = Organisasjon().apply {
    orgnummer = "912998827"
    navn = null
}

private val arbeidsgiver_organisasjon_3_navn = "MATBUTIKKEN AS"

private val arbeidsforholdID_for_arbeidsforhold_1 = 1234L
private val arbeidsforholdID_for_arbeidsforhold_2 = 5678L
private val arbeidsforholdID_for_arbeidsforhold_3 = 9123L

private val arbeidsforhold_uten_sluttdato get() = Arbeidsforhold().apply {
    arbeidsgiver = arbeidsgiver_organisasjon_1
    arbeidsforholdIDnav = arbeidsforholdID_for_arbeidsforhold_1
    ansettelsesPeriode = AnsettelsesPeriode().apply {
        periode = Gyldighetsperiode().apply {
            this.fom = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
        }
    }
    with(arbeidsavtale) {
        add(arbeidsforhold_uten_sluttdato_avtale)
    }
}

private val arbeidsforhold_uten_sluttdato_avtale get() = Arbeidsavtale().apply {
    fomGyldighetsperiode = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
    yrke = Yrker().apply {
        value = "Butikkmedarbeider"
    }
    stillingsprosent = BigDecimal.valueOf(100)
}

private val arbeidsforhold_uten_sluttdato_avtaler = listOf(arbeidsforhold_uten_sluttdato_avtale)

private val avsluttet_arbeidsforhold_med_permittering get() = Arbeidsforhold().apply {
    arbeidsgiver = arbeidsgiver_organisasjon_2
    arbeidsforholdIDnav = arbeidsforholdID_for_arbeidsforhold_2
    ansettelsesPeriode = AnsettelsesPeriode().apply {
        periode = Gyldighetsperiode().apply {
            this.fom = LocalDate.parse("2015-01-01").toXmlGregorianCalendar()
            this.tom = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
        }
    }
    with(arbeidsavtale) {
        add(avsluttet_arbeidsforhold_med_permittering_avtale)
    }
    with(permisjonOgPermittering) {
        add(permittering_for_avsluttet_arbeidsforhold_med_permittering)
    }
}

private val permittering_for_avsluttet_arbeidsforhold_med_permittering get() = PermisjonOgPermittering().apply {
    permisjonsPeriode = Gyldighetsperiode().apply {
        this.fom = LocalDate.parse("2016-01-01").toXmlGregorianCalendar()
        this.tom = LocalDate.parse("2016-01-02").toXmlGregorianCalendar()
        permisjonsprosent = BigDecimal.valueOf(100)
        permisjonOgPermittering = PermisjonsOgPermitteringsBeskrivelse().apply {
            value = "velferdspermisjon"
        }
    }
}

private val avsluttet_arbeidsforhold_med_permittering_avtale get() = Arbeidsavtale().apply {
    fomGyldighetsperiode = LocalDate.parse("2017-01-01").toXmlGregorianCalendar()
    tomGyldighetsperiode = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
    yrke = Yrker().apply {
        value = "Butikkmedarbeider"
    }
    stillingsprosent = BigDecimal.valueOf(100)
}

private val avsluttet_arbeidsforhold_med_permittering_avtaler = listOf(
        avsluttet_arbeidsforhold_med_permittering_avtale,
        Arbeidsavtale().apply {
            fomGyldighetsperiode = LocalDate.parse("2016-01-01").toXmlGregorianCalendar()
            tomGyldighetsperiode = LocalDate.parse("2016-12-31").toXmlGregorianCalendar()
            yrke = Yrker().apply {
                value = "Butikkmedarbeider"
            }
            stillingsprosent = BigDecimal.valueOf(80)
        }, Arbeidsavtale().apply {
            fomGyldighetsperiode = LocalDate.parse("2015-01-01").toXmlGregorianCalendar()
            tomGyldighetsperiode = LocalDate.parse("2015-12-31").toXmlGregorianCalendar()
            yrke = Yrker().apply {
                value = "Butikkmedarbeider"
            }
            stillingsprosent = BigDecimal.valueOf(60)
        }
)

private val arbeidsforhold_med_arbeidsgiver_uten_navn get() = Arbeidsforhold().apply {
    arbeidsgiver = arbeidsgiver_organisasjon_3
    arbeidsforholdIDnav = arbeidsforholdID_for_arbeidsforhold_3
    ansettelsesPeriode = AnsettelsesPeriode().apply {
        periode = Gyldighetsperiode().apply {
            this.fom = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
        }
    }
    with(arbeidsavtale) {
        add(arbeidsforhold_uten_sluttdato_avtale)
    }
}

private val arbeidsforhold_med_person_som_arbeidsgiver get() = Arbeidsforhold().apply {
    arbeidsgiver = Person().apply {
        ident = NorskIdent().apply {
            ident = "12345678911"
        }
    }
    arbeidsforholdIDnav = arbeidsforholdID_for_arbeidsforhold_1
    ansettelsesPeriode = AnsettelsesPeriode().apply {
        periode = Gyldighetsperiode().apply {
            this.fom = LocalDate.parse("2019-02-01").toXmlGregorianCalendar()
        }
    }
}

private val arbeidsforhold_med_person_som_arbeidsgiver_avtaler = listOf(Arbeidsavtale().apply {
    fomGyldighetsperiode = LocalDate.parse("2019-02-01").toXmlGregorianCalendar()
    yrke = Yrker().apply {
        value = "Butikkmedarbeider"
    }
    stillingsprosent = BigDecimal.valueOf(100)
})

private val arbeidsforhold_med_historisk_arbeidsgiver get() = Arbeidsforhold().apply {
    arbeidsgiver = HistoriskArbeidsgiverMedArbeidsgivernummer().apply {
        arbeidsgivernummer = "12345"
        navn = "S. VINDEL & SØNN"
    }
    arbeidsforholdIDnav = arbeidsforholdID_for_arbeidsforhold_2
    ansettelsesPeriode = AnsettelsesPeriode().apply {
        periode = Gyldighetsperiode().apply {
            this.fom = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
        }
    }
}

private val forventet_arbeidsforhold_uten_sluttdato = no.nav.helse.ws.arbeidsforhold.domain.Arbeidsforhold(
        arbeidsgiver = Arbeidsgiver.Virksomhet(Organisasjonsnummer((arbeidsforhold_uten_sluttdato.arbeidsgiver as Organisasjon).orgnummer)),
        startdato = arbeidsforhold_uten_sluttdato.ansettelsesPeriode.periode.fom.toLocalDate(),
        arbeidsforholdId = arbeidsforholdID_for_arbeidsforhold_1,
        arbeidsavtaler = listOf(
                no.nav.helse.ws.arbeidsforhold.domain.Arbeidsavtale(
                        yrke = arbeidsforhold_uten_sluttdato.arbeidsavtale[0].yrke.value,
                        stillingsprosent = arbeidsforhold_uten_sluttdato.arbeidsavtale[0].stillingsprosent,
                        fom = arbeidsforhold_uten_sluttdato.arbeidsavtale[0].fomGyldighetsperiode.toLocalDate(),
                        tom = null)
        ))

private val forventet_avsluttet_arbeidsforhold_med_permittering = no.nav.helse.ws.arbeidsforhold.domain.Arbeidsforhold(
        arbeidsgiver = Arbeidsgiver.Virksomhet(Organisasjonsnummer((avsluttet_arbeidsforhold_med_permittering.arbeidsgiver as Organisasjon).orgnummer)),
        startdato = avsluttet_arbeidsforhold_med_permittering.ansettelsesPeriode.periode.fom.toLocalDate(),
        sluttdato = avsluttet_arbeidsforhold_med_permittering.ansettelsesPeriode.periode.tom.toLocalDate(),
        arbeidsforholdId = arbeidsforholdID_for_arbeidsforhold_2,
        arbeidsavtaler = listOf(
                no.nav.helse.ws.arbeidsforhold.domain.Arbeidsavtale(
                        yrke = avsluttet_arbeidsforhold_med_permittering.arbeidsavtale[0].yrke.value,
                        stillingsprosent = avsluttet_arbeidsforhold_med_permittering.arbeidsavtale[0].stillingsprosent,
                        fom = avsluttet_arbeidsforhold_med_permittering.arbeidsavtale[0].fomGyldighetsperiode.toLocalDate(),
                        tom = avsluttet_arbeidsforhold_med_permittering.arbeidsavtale[0].tomGyldighetsperiode.toLocalDate()),
                no.nav.helse.ws.arbeidsforhold.domain.Arbeidsavtale(
                        yrke = avsluttet_arbeidsforhold_med_permittering_avtaler[1].yrke.value,
                        stillingsprosent = avsluttet_arbeidsforhold_med_permittering_avtaler[1].stillingsprosent,
                        fom = avsluttet_arbeidsforhold_med_permittering_avtaler[1].fomGyldighetsperiode.toLocalDate(),
                        tom = avsluttet_arbeidsforhold_med_permittering_avtaler[1].tomGyldighetsperiode.toLocalDate()),
                no.nav.helse.ws.arbeidsforhold.domain.Arbeidsavtale(
                        yrke = avsluttet_arbeidsforhold_med_permittering_avtaler[2].yrke.value,
                        stillingsprosent = avsluttet_arbeidsforhold_med_permittering_avtaler[2].stillingsprosent,
                        fom = avsluttet_arbeidsforhold_med_permittering_avtaler[2].fomGyldighetsperiode.toLocalDate(),
                        tom = avsluttet_arbeidsforhold_med_permittering_avtaler[2].tomGyldighetsperiode.toLocalDate())

        ),
        permisjon = listOf(
                Permisjon(
                        fom = avsluttet_arbeidsforhold_med_permittering.permisjonOgPermittering[0].permisjonsPeriode.fom.toLocalDate(),
                        tom = avsluttet_arbeidsforhold_med_permittering.permisjonOgPermittering[0].permisjonsPeriode.tom.toLocalDate(),
                        permisjonsprosent = avsluttet_arbeidsforhold_med_permittering.permisjonOgPermittering[0].permisjonsprosent,
                        årsak = avsluttet_arbeidsforhold_med_permittering.permisjonOgPermittering[0].permisjonOgPermittering.value
                )
        )
)

private val forventet_arbeidsforhold_med_person_som_arbeidsgiver = no.nav.helse.ws.arbeidsforhold.domain.Arbeidsforhold(
        arbeidsgiver = Arbeidsgiver.Person((arbeidsforhold_med_person_som_arbeidsgiver.arbeidsgiver as Person).ident.ident),
        startdato = arbeidsforhold_med_person_som_arbeidsgiver.ansettelsesPeriode.periode.fom.toLocalDate(),
        arbeidsforholdId = arbeidsforholdID_for_arbeidsforhold_1,
        arbeidsavtaler = listOf(
                no.nav.helse.ws.arbeidsforhold.domain.Arbeidsavtale(
                        yrke = arbeidsforhold_med_person_som_arbeidsgiver_avtaler[0].yrke.value,
                        stillingsprosent = arbeidsforhold_med_person_som_arbeidsgiver_avtaler[0].stillingsprosent,
                        fom = arbeidsforhold_med_person_som_arbeidsgiver_avtaler[0].fomGyldighetsperiode.toLocalDate(),
                        tom = null)
        ))
