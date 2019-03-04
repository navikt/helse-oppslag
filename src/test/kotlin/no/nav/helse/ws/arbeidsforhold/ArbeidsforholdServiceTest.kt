package no.nav.helse.ws.arbeidsforhold

import io.mockk.every
import io.mockk.mockk
import no.nav.helse.Either
import no.nav.helse.Feilårsak
import no.nav.helse.common.toXmlGregorianCalendar
import no.nav.helse.ws.AktørId
import no.nav.helse.ws.organisasjon.OrganisasjonResponse
import no.nav.helse.ws.organisasjon.OrganisasjonService
import no.nav.helse.ws.organisasjon.OrganisasjonsAttributt
import no.nav.helse.ws.organisasjon.OrganisasjonsNummer
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.binding.FinnArbeidsforholdPrArbeidstakerSikkerhetsbegrensning
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.binding.FinnArbeidsforholdPrArbeidstakerUgyldigInput
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.feil.Sikkerhetsbegrensning
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.feil.UgyldigInput
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.informasjon.arbeidsforhold.AnsettelsesPeriode
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.informasjon.arbeidsforhold.Arbeidsforhold
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.informasjon.arbeidsforhold.Gyldighetsperiode
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.informasjon.arbeidsforhold.HistoriskArbeidsgiverMedArbeidsgivernummer
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.informasjon.arbeidsforhold.NorskIdent
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.informasjon.arbeidsforhold.Organisasjon
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.informasjon.arbeidsforhold.Person
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
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
                no.nav.helse.ws.arbeidsforhold.Arbeidsforhold(Arbeidsgiver.Organisasjon("22334455", "S. VINDEL & SØNN"), LocalDate.parse("2019-01-01")),
                no.nav.helse.ws.arbeidsforhold.Arbeidsforhold(Arbeidsgiver.Organisasjon("66778899", "MATBUTIKKEN AS"), LocalDate.parse("2015-01-01"), LocalDate.parse("2019-01-01"))
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns listOf(Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "22334455"
                navn = "S. VINDEL & SØNN"
            }
            ansettelsesPeriode = AnsettelsesPeriode().apply {
                periode = Gyldighetsperiode().apply {
                    this.fom = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
                }
            }
        }, Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "66778899"
                navn = "MATBUTIKKEN AS"
            }
            ansettelsesPeriode = AnsettelsesPeriode().apply {
                periode = Gyldighetsperiode().apply {
                    this.fom = LocalDate.parse("2015-01-01").toXmlGregorianCalendar()
                    this.tom = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
                }
            }
        }).let {
            Either.Right(it)
        }

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService).finnArbeidsforhold(aktørId, fom, tom)

        when (actual) {
            is Either.Right -> {
                assertEquals(expected.size, actual.right.size)
                expected.forEachIndexed { index, value ->
                    assertEquals(value, actual.right[index])
                }
            }
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }

    @Test
    fun `skal hente navn på arbeidsgiver når navn er tomt`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        val organisasjonService = mockk<OrganisasjonService>()

        val aktørId = AktørId("123456789")
        val fom = LocalDate.parse("2019-01-01")
        val tom = LocalDate.parse("2019-02-01")

        val expected = listOf(
                no.nav.helse.ws.arbeidsforhold.Arbeidsforhold(Arbeidsgiver.Organisasjon("22334455", "MATBUTIKKEN AS"), LocalDate.parse("2019-01-01"))
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns listOf(Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "22334455"
            }
            ansettelsesPeriode = AnsettelsesPeriode().apply {
                periode = Gyldighetsperiode().apply {
                    this.fom = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
                }
            }
        }).let {
            Either.Right(it)
        }

        every {
            organisasjonService.hentOrganisasjon(OrganisasjonsNummer("22334455"), listOf(OrganisasjonsAttributt("navn")))
        } returns Either.Right(OrganisasjonResponse("MATBUTIKKEN AS"))

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService).finnArbeidsforhold(aktørId, fom, tom)

        when (actual) {
            is Either.Right -> {
                assertEquals(expected.size, actual.right.size)
                expected.forEachIndexed { index, value ->
                    assertEquals(value, actual.right[index])
                }
            }
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }

    @Test
    fun `feil ved henting av navn skal ikke medføre feil`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        val organisasjonService = mockk<OrganisasjonService>()

        val aktørId = AktørId("123456789")
        val fom = LocalDate.parse("2019-01-01")
        val tom = LocalDate.parse("2019-02-01")

        val expected = listOf(
                no.nav.helse.ws.arbeidsforhold.Arbeidsforhold(Arbeidsgiver.Organisasjon("22334455", "FEIL VED HENTING AV NAVN"), LocalDate.parse("2019-01-01"))
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns listOf(Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "22334455"
            }
            ansettelsesPeriode = AnsettelsesPeriode().apply {
                periode = Gyldighetsperiode().apply {
                    this.fom = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
                }
            }
        }).let {
            Either.Right(it)
        }

        every {
            organisasjonService.hentOrganisasjon(OrganisasjonsNummer("22334455"), listOf(OrganisasjonsAttributt("navn")))
        } returns Either.Left(Feilårsak.FeilFraTjeneste)

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService).finnArbeidsforhold(aktørId, fom, tom)

        when (actual) {
            is Either.Right -> {
                assertEquals(expected.size, actual.right.size)
                expected.forEachIndexed { index, value ->
                    assertEquals(value, actual.right[index])
                }
            }
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
                no.nav.helse.ws.arbeidsforhold.Arbeidsforhold(Arbeidsgiver.Organisasjon("0000000000", "UKJENT ARBEIDSGIVERTYPE"), LocalDate.parse("2019-02-01")),
                no.nav.helse.ws.arbeidsforhold.Arbeidsforhold(Arbeidsgiver.Organisasjon("0000000000", "UKJENT ARBEIDSGIVERTYPE"), LocalDate.parse("2019-01-01"))
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns listOf(Arbeidsforhold().apply {
            arbeidsgiver = Person().apply {
                ident = NorskIdent().apply {
                    ident = "12345678911"
                }
            }
            ansettelsesPeriode = AnsettelsesPeriode().apply {
                periode = Gyldighetsperiode().apply {
                    this.fom = LocalDate.parse("2019-02-01").toXmlGregorianCalendar()
                }
            }
        }, Arbeidsforhold().apply {
            arbeidsgiver = HistoriskArbeidsgiverMedArbeidsgivernummer().apply {
                arbeidsgivernummer = "12345"
                navn = "S. VINDEL & SØNN"
            }
            ansettelsesPeriode = AnsettelsesPeriode().apply {
                periode = Gyldighetsperiode().apply {
                    this.fom = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
                }
            }
        }).let {
            Either.Right(it)
        }

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService).finnArbeidsforhold(aktørId, fom, tom)

        when (actual) {
            is Either.Right -> {
                assertEquals(expected.size, actual.right.size)
                expected.forEachIndexed { index, value ->
                    assertEquals(value, actual.right[index])
                }
            }
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }

    @Test
    fun `skal mappe sikkerhetsbegrensning til feilårsak`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        every {
            arbeidsforholdClient.finnArbeidsforhold(any(), any(), any())
        } returns Either.Left(FinnArbeidsforholdPrArbeidstakerSikkerhetsbegrensning("Fault", Sikkerhetsbegrensning()))

        val actual = ArbeidsforholdService(arbeidsforholdClient, mockk()).finnArbeidsforhold(
                AktørId("11987654321"), LocalDate.now(), LocalDate.now())

        when (actual) {
            is Either.Left -> assertEquals(Feilårsak.FeilFraTjeneste, actual.left)
            is Either.Right -> fail { "Expected Either.Left to be returned" }
        }
    }

    @Test
    fun `skal mappe ugyldig input til feilårsak`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        every {
            arbeidsforholdClient.finnArbeidsforhold(any(), any(), any())
        } returns Either.Left(FinnArbeidsforholdPrArbeidstakerUgyldigInput("Fault", UgyldigInput()))

        val actual = ArbeidsforholdService(arbeidsforholdClient, mockk()).finnArbeidsforhold(
                AktørId("11987654321"), LocalDate.now(), LocalDate.now())

        when (actual) {
            is Either.Left -> assertEquals(Feilårsak.FeilFraTjeneste, actual.left)
            is Either.Right -> fail { "Expected Either.Left to be returned" }
        }
    }

    @Test
    fun `skal mappe exceptions til feilårsak`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        every {
            arbeidsforholdClient.finnArbeidsforhold(any(), any(), any())
        } returns Either.Left(Exception("Fault"))

        val actual = ArbeidsforholdService(arbeidsforholdClient, mockk()).finnArbeidsforhold(
                AktørId("11987654321"), LocalDate.now(), LocalDate.now())

        when (actual) {
            is Either.Left -> assertEquals(Feilårsak.UkjentFeil, actual.left)
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
        } returns Either.Left(Exception("SOAP fault"))

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService).finnArbeidsgivere(aktørId, fom, tom)

        when (actual) {
            is Either.Left -> assertTrue(actual.left is Feilårsak.UkjentFeil)
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
                Arbeidsgiver.Organisasjon("22334455", "S. VINDEL & SØNN"),
                Arbeidsgiver.Organisasjon("66778899", "MATBUTIKKEN AS")
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns listOf(Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "22334455"
                navn = "S. VINDEL & SØNN"
            }
        }, Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "66778899"
                navn = "MATBUTIKKEN AS"
            }
        }).let {
            Either.Right(it)
        }

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService).finnArbeidsgivere(aktørId, fom, tom)

        when (actual) {
            is Either.Right -> {
                assertEquals(expected.size, actual.right.size)
                expected.forEachIndexed { index, value ->
                    assertEquals(value, actual.right[index])
                }
            }
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
                Arbeidsgiver.Organisasjon("22334455", "S. VINDEL & SØNN"),
                Arbeidsgiver.Organisasjon("66778899", "MATBUTIKKEN AS")
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns listOf(Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "22334455"
                navn = "S. VINDEL & SØNN"
            }
        }, Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "66778899"
                navn = null
            }
        }).let {
            Either.Right(it)
        }

        every {
            organisasjonService.hentOrganisasjon(OrganisasjonsNummer("66778899"), listOf(OrganisasjonsAttributt("navn")))
        } returns OrganisasjonResponse("MATBUTIKKEN AS").let {
            Either.Right(it)
        }

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService).finnArbeidsgivere(aktørId, fom, tom)

        when (actual) {
            is Either.Right -> {
                assertEquals(expected.size, actual.right.size)
                expected.forEachIndexed { index, value ->
                    assertEquals(value, actual.right[index])
                }
            }
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
        } returns Either.Left(Exception("SOAP fault"))

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService).finnArbeidsgivere(aktørId, fom, tom)

        when (actual) {
            is Either.Left -> assertTrue(actual.left is Feilårsak.UkjentFeil)
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
                Arbeidsgiver.Organisasjon("22334455", null)
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns listOf(Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "22334455"
                navn = null
            }
        }).let {
            Either.Right(it)
        }

        every {
            organisasjonService.hentOrganisasjon(any(), any())
        } returns Either.Left(Feilårsak.FeilFraTjeneste)

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService).finnArbeidsgivere(aktørId, fom, tom)

        when (actual) {
            is Either.Right -> {
                assertEquals(expected.size, actual.right.size)
                assertEquals(expected[0], actual.right[0])
            }
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }

    @Test
    fun `skal fjerne duplikater`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        val organisasjonService = mockk<OrganisasjonService>()

        val aktørId = AktørId("123456789")
        val fom = LocalDate.parse("2019-01-01")
        val tom = LocalDate.parse("2019-02-01")

        val expected = listOf(
                Arbeidsgiver.Organisasjon("22334455", "S. VINDEL & SØNN")
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns listOf(Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "22334455"
                navn = "S. VINDEL & SØNN"
            }
        }, Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "22334455"
                navn = "S. VINDEL & SØNN"
            }
        }).let {
            Either.Right(it)
        }

        val actual = ArbeidsforholdService(arbeidsforholdClient, organisasjonService).finnArbeidsgivere(aktørId, fom, tom)

        when (actual) {
            is Either.Right -> {
                assertEquals(expected.size, actual.right.size)
                expected.forEachIndexed { index, value ->
                    assertEquals(value, actual.right[index])
                }
            }
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }

    @Test
    fun `skal mappe sikkerhetsbegrensning til feilårsak for arbeidsgiveroppslag`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        every {
            arbeidsforholdClient.finnArbeidsforhold(any(), any(), any())
        } returns Either.Left(FinnArbeidsforholdPrArbeidstakerSikkerhetsbegrensning("Fault", Sikkerhetsbegrensning()))


        val actual = ArbeidsforholdService(arbeidsforholdClient, mockk()).finnArbeidsgivere(
                AktørId("11987654321"), LocalDate.now(), LocalDate.now())

        when (actual) {
            is Either.Left -> assertEquals(Feilårsak.FeilFraTjeneste, actual.left)
            is Either.Right -> fail { "Expected Either.Left to be returned" }
        }
    }

    @Test
    fun `skal mappe ugyldig input til feilårsak for arbeidsgiveroppslag`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        every {
            arbeidsforholdClient.finnArbeidsforhold(any(), any(), any())
        } returns Either.Left(FinnArbeidsforholdPrArbeidstakerUgyldigInput("Fault", UgyldigInput()))

        val actual = ArbeidsforholdService(arbeidsforholdClient, mockk()).finnArbeidsgivere(
                AktørId("11987654321"), LocalDate.now(), LocalDate.now())

        when (actual) {
            is Either.Left -> assertEquals(Feilårsak.FeilFraTjeneste, actual.left)
            is Either.Right -> fail { "Expected Either.Left to be returned" }
        }
    }

    @Test
    fun `skal mappe exceptions til feilårsak for arbeidsgiveroppslag`() {
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        every {
            arbeidsforholdClient.finnArbeidsforhold(any(), any(), any())
        } returns Either.Left(Exception("Fault"))

        val actual = ArbeidsforholdService(arbeidsforholdClient, mockk()).finnArbeidsgivere(
                AktørId("11987654321"), LocalDate.now(), LocalDate.now())

        when (actual) {
            is Either.Left -> assertEquals(Feilårsak.UkjentFeil, actual.left)
            is Either.Right -> fail { "Expected Either.Left to be returned" }
        }
    }
}