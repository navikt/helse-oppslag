package no.nav.helse.ws.inntekt

import io.mockk.every
import io.mockk.mockk
import no.nav.helse.Feilårsak
import no.nav.helse.OppslagResult
import no.nav.helse.common.toXmlGregorianCalendar
import no.nav.helse.ws.AktørId
import no.nav.tjeneste.virksomhet.inntekt.v3.informasjon.inntekt.AktoerId
import no.nav.tjeneste.virksomhet.inntekt.v3.informasjon.inntekt.ArbeidsInntektIdent
import no.nav.tjeneste.virksomhet.inntekt.v3.informasjon.inntekt.ArbeidsInntektInformasjon
import no.nav.tjeneste.virksomhet.inntekt.v3.informasjon.inntekt.ArbeidsInntektMaaned
import no.nav.tjeneste.virksomhet.inntekt.v3.informasjon.inntekt.Inntekt
import no.nav.tjeneste.virksomhet.inntekt.v3.meldinger.HentInntektListeBolkResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.math.BigDecimal
import java.time.YearMonth

class InntektServiceTest {

    @Test
    fun `skal gi feil når oppslag gir feil`() {
        val aktør = AktørId("11987654321")

        val fom = YearMonth.parse("2019-01")
        val tom = YearMonth.parse("2019-02")

        val inntektClient = mockk<InntektClient>()
        every {
            inntektClient.hentBeregningsgrunnlag(aktør, fom, tom)
        } returns OppslagResult.Feil(Exception("SOAP fault"))

        val actual = InntektService(inntektClient).hentBeregningsgrunnlag(aktør, fom, tom)

        when (actual) {
            is OppslagResult.Feil -> assertTrue(actual.feil is Feilårsak.UkjentFeil)
            else -> fail { "Expected OppslagResult.Feil to be returned" }
        }
    }

    @Test
    fun `skal returnere liste over inntekter`() {
        val aktør = AktørId("11987654321")

        val fom = YearMonth.parse("2019-01")
        val tom = YearMonth.parse("2019-02")

        val expected = HentInntektListeBolkResponse().apply {
            with (arbeidsInntektIdentListe) {
                add(ArbeidsInntektIdent().apply {
                    ident = AktoerId().apply {
                        aktoerId = aktør.aktor
                    }
                    with (arbeidsInntektMaaned) {
                        add(ArbeidsInntektMaaned().apply {
                            aarMaaned = fom.toXmlGregorianCalendar()
                            arbeidsInntektInformasjon = ArbeidsInntektInformasjon().apply {
                                with (inntektListe) {
                                    add(Inntekt().apply {
                                        beloep = BigDecimal.valueOf(2500)
                                    })
                                }
                            }
                        })
                    }
                })
            }
        }

        val inntektClient = mockk<InntektClient>()
        every {
            inntektClient.hentBeregningsgrunnlag(aktør, fom, tom)
        } returns HentInntektListeBolkResponse().apply {
            with (arbeidsInntektIdentListe) {
                add(ArbeidsInntektIdent().apply {
                    ident = AktoerId().apply {
                        aktoerId = aktør.aktor
                    }
                    with (arbeidsInntektMaaned) {
                        add(ArbeidsInntektMaaned().apply {
                            aarMaaned = fom.toXmlGregorianCalendar()
                            arbeidsInntektInformasjon = ArbeidsInntektInformasjon().apply {
                                with (inntektListe) {
                                    add(Inntekt().apply {
                                        beloep = BigDecimal.valueOf(2500)
                                    })
                                }
                            }
                        })
                    }
                })
            }
        }.let {
            OppslagResult.Ok(it)
        }

        val actual = InntektService(inntektClient).hentBeregningsgrunnlag(aktør, fom, tom)

        when (actual) {
            is OppslagResult.Ok -> {
                assertEquals(expected.arbeidsInntektIdentListe.size, actual.data.arbeidsInntektIdentListe.size)
                expected.arbeidsInntektIdentListe.forEachIndexed { index, arbeidsInntektIdent ->
                    val actualArbeidsInntektIdent = actual.data.arbeidsInntektIdentListe[index]

                    assertTrue(actualArbeidsInntektIdent.ident is AktoerId)
                    assertEquals((arbeidsInntektIdent.ident as AktoerId).aktoerId, (actualArbeidsInntektIdent.ident as AktoerId).aktoerId)

                    assertEquals(arbeidsInntektIdent.arbeidsInntektMaaned.size, actualArbeidsInntektIdent.arbeidsInntektMaaned.size)

                    arbeidsInntektIdent.arbeidsInntektMaaned.forEachIndexed { index, arbeidsInntektMaaned ->
                        val actualArbeidsInntektMaaned = actualArbeidsInntektIdent.arbeidsInntektMaaned[index]

                        assertEquals(arbeidsInntektMaaned.aarMaaned, actualArbeidsInntektMaaned.aarMaaned)
                        assertEquals(arbeidsInntektMaaned.arbeidsInntektInformasjon.inntektListe.size, actualArbeidsInntektMaaned.arbeidsInntektInformasjon.inntektListe.size)

                        arbeidsInntektMaaned.arbeidsInntektInformasjon.inntektListe.forEachIndexed { index, inntekt ->
                            val actualInntekt = actualArbeidsInntektMaaned.arbeidsInntektInformasjon.inntektListe[index]

                            assertEquals(inntekt.beloep, actualInntekt.beloep)
                        }
                    }
                }
            }
            is OppslagResult.Feil -> fail { "Expected OppslagResult.Ok to be returned" }
        }

    }

    @Test
    fun `skal gi feil når oppslag gir feil for sammenligningsgrunnlag`() {
        val aktør = AktørId("11987654321")

        val fom = YearMonth.parse("2019-01")
        val tom = YearMonth.parse("2019-02")

        val inntektClient = mockk<InntektClient>()
        every {
            inntektClient.hentSammenligningsgrunnlag(aktør, fom, tom)
        } returns OppslagResult.Feil(Exception("SOAP fault"))

        val actual = InntektService(inntektClient).hentSammenligningsgrunnlag(aktør, fom, tom)

        when (actual) {
            is OppslagResult.Feil -> assertTrue(actual.feil is Feilårsak.UkjentFeil)
            else -> fail { "Expected OppslagResult.Feil to be returned" }
        }
    }

    @Test
    fun `skal returnere liste over inntekter for sammenligningsgrunnlag`() {
        val aktør = AktørId("11987654321")

        val fom = YearMonth.parse("2019-01")
        val tom = YearMonth.parse("2019-02")

        val expected = HentInntektListeBolkResponse().apply {
            with (arbeidsInntektIdentListe) {
                add(ArbeidsInntektIdent().apply {
                    ident = AktoerId().apply {
                        aktoerId = aktør.aktor
                    }
                    with (arbeidsInntektMaaned) {
                        add(ArbeidsInntektMaaned().apply {
                            aarMaaned = fom.toXmlGregorianCalendar()
                            arbeidsInntektInformasjon = ArbeidsInntektInformasjon().apply {
                                with (inntektListe) {
                                    add(Inntekt().apply {
                                        beloep = BigDecimal.valueOf(2500)
                                    })
                                }
                            }
                        })
                    }
                })
            }
        }

        val inntektClient = mockk<InntektClient>()
        every {
            inntektClient.hentSammenligningsgrunnlag(aktør, fom, tom)
        } returns HentInntektListeBolkResponse().apply {
            with (arbeidsInntektIdentListe) {
                add(ArbeidsInntektIdent().apply {
                    ident = AktoerId().apply {
                        aktoerId = aktør.aktor
                    }
                    with (arbeidsInntektMaaned) {
                        add(ArbeidsInntektMaaned().apply {
                            aarMaaned = fom.toXmlGregorianCalendar()
                            arbeidsInntektInformasjon = ArbeidsInntektInformasjon().apply {
                                with (inntektListe) {
                                    add(Inntekt().apply {
                                        beloep = BigDecimal.valueOf(2500)
                                    })
                                }
                            }
                        })
                    }
                })
            }
        }.let {
            OppslagResult.Ok(it)
        }

        val actual = InntektService(inntektClient).hentSammenligningsgrunnlag(aktør, fom, tom)

        when (actual) {
            is OppslagResult.Ok -> {
                assertEquals(expected.arbeidsInntektIdentListe.size, actual.data.arbeidsInntektIdentListe.size)
                expected.arbeidsInntektIdentListe.forEachIndexed { index, arbeidsInntektIdent ->
                    val actualArbeidsInntektIdent = actual.data.arbeidsInntektIdentListe[index]

                    assertTrue(actualArbeidsInntektIdent.ident is AktoerId)
                    assertEquals((arbeidsInntektIdent.ident as AktoerId).aktoerId, (actualArbeidsInntektIdent.ident as AktoerId).aktoerId)

                    assertEquals(arbeidsInntektIdent.arbeidsInntektMaaned.size, actualArbeidsInntektIdent.arbeidsInntektMaaned.size)

                    arbeidsInntektIdent.arbeidsInntektMaaned.forEachIndexed { index, arbeidsInntektMaaned ->
                        val actualArbeidsInntektMaaned = actualArbeidsInntektIdent.arbeidsInntektMaaned[index]

                        assertEquals(arbeidsInntektMaaned.aarMaaned, actualArbeidsInntektMaaned.aarMaaned)
                        assertEquals(arbeidsInntektMaaned.arbeidsInntektInformasjon.inntektListe.size, actualArbeidsInntektMaaned.arbeidsInntektInformasjon.inntektListe.size)

                        arbeidsInntektMaaned.arbeidsInntektInformasjon.inntektListe.forEachIndexed { index, inntekt ->
                            val actualInntekt = actualArbeidsInntektMaaned.arbeidsInntektInformasjon.inntektListe[index]

                            assertEquals(inntekt.beloep, actualInntekt.beloep)
                        }
                    }
                }
            }
            is OppslagResult.Feil -> fail { "Expected OppslagResult.Ok to be returned" }
        }

    }
}
