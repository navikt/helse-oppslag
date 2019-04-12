package no.nav.helse.ws.arbeidsforhold

import io.mockk.every
import io.mockk.mockk
import no.nav.helse.Either
import no.nav.helse.Feilårsak
import no.nav.helse.common.toXmlGregorianCalendar
import no.nav.helse.ws.AktørId
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
                no.nav.helse.ws.arbeidsforhold.domain.Arbeidsforhold(
                        arbeidsgiver = Arbeidsgiver.Virksomhet(Organisasjonsnummer("889640782")),
                        startdato = LocalDate.parse("2019-01-01"),
                        arbeidsavtaler = listOf(
                                no.nav.helse.ws.arbeidsforhold.domain.Arbeidsavtale("Butikkmedarbeider", BigDecimal.valueOf(100), LocalDate.parse("2019-01-01"), null)
                        )),
                no.nav.helse.ws.arbeidsforhold.domain.Arbeidsforhold(
                        arbeidsgiver = Arbeidsgiver.Virksomhet(Organisasjonsnummer("995298775")),
                        startdato = LocalDate.parse("2015-01-01"),
                        sluttdato = LocalDate.parse("2019-01-01"),
                        arbeidsavtaler = listOf(
                                no.nav.helse.ws.arbeidsforhold.domain.Arbeidsavtale("Butikkmedarbeider", BigDecimal.valueOf(100), LocalDate.parse("2015-01-01"), LocalDate.parse("2019-01-01"))
                        ),
                        permisjon = listOf(
                                Permisjon(
                                        fom = LocalDate.parse("2016-01-01"),
                                        tom = LocalDate.parse("2016-01-02"),
                                        permisjonsprosent = BigDecimal.valueOf(100),
                                        årsak = "velferdspermisjon"
                                )
                        )
                )
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns listOf(Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "889640782"
                navn = "S. VINDEL & SØNN"
            }
            ansettelsesPeriode = AnsettelsesPeriode().apply {
                periode = Gyldighetsperiode().apply {
                    this.fom = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
                }
            }
            with (arbeidsavtale) {
                add(Arbeidsavtale().apply {
                    fomGyldighetsperiode = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
                    yrke = Yrker().apply {
                        value = "Butikkmedarbeider"
                    }
                    stillingsprosent = BigDecimal.valueOf(100)
                })
            }
        }, Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "995298775"
                navn = "MATBUTIKKEN AS"
            }
            ansettelsesPeriode = AnsettelsesPeriode().apply {
                periode = Gyldighetsperiode().apply {
                    this.fom = LocalDate.parse("2015-01-01").toXmlGregorianCalendar()
                    this.tom = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
                }
            }
            with (arbeidsavtale) {
                add(Arbeidsavtale().apply {
                    fomGyldighetsperiode = LocalDate.parse("2015-01-01").toXmlGregorianCalendar()
                    tomGyldighetsperiode = LocalDate.parse("2019-01-01").toXmlGregorianCalendar()
                    yrke = Yrker().apply {
                        value = "Butikkmedarbeider"
                    }
                    stillingsprosent = BigDecimal.valueOf(100)
                })
            }
            with(permisjonOgPermittering) {
                add(PermisjonOgPermittering().apply {
                    permisjonsPeriode = Gyldighetsperiode().apply {
                        this.fom = LocalDate.parse("2016-01-01").toXmlGregorianCalendar()
                        this.tom = LocalDate.parse("2016-01-02").toXmlGregorianCalendar()
                        permisjonsprosent = BigDecimal.valueOf(100)
                        permisjonOgPermittering = PermisjonsOgPermitteringsBeskrivelse().apply {
                            value = "velferdspermisjon"
                        }
                    }
                })
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
                no.nav.helse.ws.arbeidsforhold.domain.Arbeidsforhold(Arbeidsgiver.Virksomhet(Organisasjonsnummer("889640782")), LocalDate.parse("2019-01-01"))
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns listOf(Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "889640782"
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
            organisasjonService.hentOrganisasjon(Organisasjonsnummer("889640782"))
        } returns Either.Right(no.nav.helse.ws.organisasjon.domain.Organisasjon.Virksomhet(Organisasjonsnummer("889640782"), "MATBUTIKKEN AS"))

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
                no.nav.helse.ws.arbeidsforhold.domain.Arbeidsforhold(Arbeidsgiver.Virksomhet(Organisasjonsnummer("889640782")), LocalDate.parse("2019-01-01"))
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns listOf(Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "889640782"
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
            organisasjonService.hentOrganisasjon(Organisasjonsnummer("889640782"))
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
                no.nav.helse.ws.arbeidsforhold.domain.Arbeidsforhold(Arbeidsgiver.Person("12345678911"), LocalDate.parse("2019-02-01"))
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
                no.nav.helse.ws.organisasjon.domain.Organisasjon.Virksomhet(Organisasjonsnummer("889640782"), "S. VINDEL & SØNN"),
                no.nav.helse.ws.organisasjon.domain.Organisasjon.Virksomhet(Organisasjonsnummer("995298775"), "MATBUTIKKEN AS")
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns listOf(Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "889640782"
                navn = "S. VINDEL & SØNN"
            }
        }, Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "995298775"
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
                no.nav.helse.ws.organisasjon.domain.Organisasjon.Virksomhet(Organisasjonsnummer("995298775"), "S. VINDEL & SØNN"),
                no.nav.helse.ws.organisasjon.domain.Organisasjon.Virksomhet(Organisasjonsnummer("889640782"), "MATBUTIKKEN AS")
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns listOf(Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "995298775"
                navn = "S. VINDEL & SØNN"
            }
        }, Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "889640782"
                navn = null
            }
        }).let {
            Either.Right(it)
        }

        every {
            organisasjonService.hentOrganisasjon(Organisasjonsnummer("889640782"))
        } returns no.nav.helse.ws.organisasjon.domain.Organisasjon.Virksomhet(Organisasjonsnummer("889640782"), "MATBUTIKKEN AS").let {
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
                no.nav.helse.ws.organisasjon.domain.Organisasjon.Virksomhet(Organisasjonsnummer("889640782"), null)
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns listOf(Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "889640782"
                navn = null
            }
        }).let {
            Either.Right(it)
        }

        every {
            organisasjonService.hentOrganisasjon(any())
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
                no.nav.helse.ws.organisasjon.domain.Organisasjon.Virksomhet(Organisasjonsnummer("889640782"), "S. VINDEL & SØNN")
        )

        every {
            arbeidsforholdClient.finnArbeidsforhold(aktørId, fom, tom)
        } returns listOf(Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "889640782"
                navn = "S. VINDEL & SØNN"
            }
        }, Arbeidsforhold().apply {
            arbeidsgiver = Organisasjon().apply {
                orgnummer = "889640782"
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
