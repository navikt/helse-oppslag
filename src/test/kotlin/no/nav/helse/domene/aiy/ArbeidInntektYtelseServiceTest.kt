package no.nav.helse.domene.aiy

import arrow.core.Either
import io.mockk.every
import io.mockk.mockk
import io.prometheus.client.CollectorRegistry
import no.nav.helse.Feilårsak
import no.nav.helse.domene.AktørId
import no.nav.helse.domene.aiy.domain.ArbeidInntektYtelse
import no.nav.helse.domene.arbeid.ArbeidsforholdService
import no.nav.helse.domene.arbeid.domain.Arbeidsavtale
import no.nav.helse.domene.arbeid.domain.Arbeidsforhold
import no.nav.helse.domene.inntekt.InntektService
import no.nav.helse.domene.inntekt.domain.Inntekt
import no.nav.helse.domene.inntekt.domain.Virksomhet
import no.nav.helse.domene.organisasjon.OrganisasjonService
import no.nav.helse.domene.organisasjon.domain.Organisasjon
import no.nav.helse.domene.organisasjon.domain.Organisasjonsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month.*
import java.time.YearMonth

class ArbeidInntektYtelseServiceTest {

    lateinit var arbeidsforholdService: ArbeidsforholdService
    lateinit var inntektService: InntektService
    lateinit var organisasjonService: OrganisasjonService

    lateinit var aktiveArbeidsforholdService: ArbeidInntektYtelseService

    var foreløpigArbeidsforholdAvviksCounterBefore = 0.0
    var arbeidsforholdAvviksCounterBefore = 0.0
    var foreløpigInntektAvviksCounterBefore = 0.0
    var inntektAvviksCounterBefore = 0.0

    val foreløpigArbeidsforholdAvviksCounterAfter get() = CollectorRegistry.defaultRegistry.getSampleValue("forelopig_arbeidsforhold_avvik_totals", arrayOf("type"), arrayOf("Arbeidstaker")) ?: 0.0
    val arbeidsforholdAvviksCounterAfter get() = CollectorRegistry.defaultRegistry.getSampleValue("arbeidsforhold_avvik_totals", arrayOf("type"), arrayOf("Arbeidstaker")) ?: 0.0
    val foreløpigInntektAvviksCounterAfter get() = CollectorRegistry.defaultRegistry.getSampleValue("forelopig_inntekt_avvik_totals")
    val inntektAvviksCounterAfter get() = CollectorRegistry.defaultRegistry.getSampleValue("inntekt_avvik_totals")

    val aktørId = AktørId("123456789")
    val fom = LocalDate.parse("2019-01-01")
    val tom = LocalDate.parse("2019-03-01")

    @BeforeEach
    fun `initialize mocks and counters`() {
        arbeidsforholdService = mockk()
        inntektService = mockk()
        organisasjonService = mockk()

        aktiveArbeidsforholdService = ArbeidInntektYtelseService(arbeidsforholdService, inntektService, organisasjonService)

        foreløpigArbeidsforholdAvviksCounterBefore = CollectorRegistry.defaultRegistry.getSampleValue("forelopig_arbeidsforhold_avvik_totals", arrayOf("type"), arrayOf("Arbeidstaker")) ?: 0.0
        arbeidsforholdAvviksCounterBefore = CollectorRegistry.defaultRegistry.getSampleValue("arbeidsforhold_avvik_totals", arrayOf("type"), arrayOf("Arbeidstaker")) ?: 0.0
        foreløpigInntektAvviksCounterBefore = CollectorRegistry.defaultRegistry.getSampleValue("forelopig_inntekt_avvik_totals")
        inntektAvviksCounterBefore = CollectorRegistry.defaultRegistry.getSampleValue("inntekt_avvik_totals")
    }

    @Test
    fun `skal sammenstille arbeidsforhold og inntekter`() {
        every {
            arbeidsforholdService.finnArbeidsforhold(aktørId, fom, tom)
        } returns Either.Right(listOf(
                aktivt_arbeidstakerforhold,
                avsluttet_arbeidstakerforhold,
                aktivt_frilansforhold
        ))

        every {
            inntektService.hentInntekter(aktørId, YearMonth.from(fom), YearMonth.from(tom), "ForeldrepengerA-Inntekt")
        } returns Either.Right(inntekter_fra_tre_virksomheter)

        val actual = aktiveArbeidsforholdService.finnArbeidInntekterOgYtelser(aktørId, fom, tom)

        assertEquals(0.0, foreløpigArbeidsforholdAvviksCounterAfter - foreløpigArbeidsforholdAvviksCounterBefore)
        assertEquals(0.0, arbeidsforholdAvviksCounterAfter - arbeidsforholdAvviksCounterBefore)
        assertEquals(0.0, foreløpigInntektAvviksCounterAfter - foreløpigInntektAvviksCounterBefore)
        assertEquals(0.0, inntektAvviksCounterAfter - inntektAvviksCounterBefore)

        when (actual) {
            is Either.Right -> assertEquals(forventet_resultat_uten_avvik, actual.b)
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }

    @Test
    fun `skal ikke slå sammen to arbeidsforhold under samme virksomhet`() {
        every {
            arbeidsforholdService.finnArbeidsforhold(aktørId, fom, tom)
        } returns Either.Right(listOf(
                aktivt_arbeidstakerforhold,
                aktivt_arbeidstakerforhold_i_samme_virksomhet
        ))

        every {
            inntektService.hentInntekter(aktørId, YearMonth.from(fom), YearMonth.from(tom), "ForeldrepengerA-Inntekt")
        } returns Either.Right(inntekter_fra_samme_virksomhet_med_juridisk_nummer)

        every {
            organisasjonService.hentOrganisasjon(virksomhet2.organisasjonsnummer)
        } returns Either.Right(Organisasjon.JuridiskEnhet(virksomhet2.organisasjonsnummer))

        every {
            organisasjonService.hentVirksomhetForJuridiskOrganisasjonsnummer(virksomhet2.organisasjonsnummer, mars_2019.atDay(1))
        } returns Either.Right(virksomhet1.organisasjonsnummer)

        val actual = aktiveArbeidsforholdService.finnArbeidInntekterOgYtelser(aktørId, fom, tom)

        assertEquals(1.0, foreløpigArbeidsforholdAvviksCounterAfter - foreløpigArbeidsforholdAvviksCounterBefore)
        assertEquals(1.0, arbeidsforholdAvviksCounterAfter - arbeidsforholdAvviksCounterBefore)
        assertEquals(1.0, foreløpigInntektAvviksCounterAfter - foreløpigInntektAvviksCounterBefore)
        assertEquals(0.0, inntektAvviksCounterAfter - inntektAvviksCounterBefore)

        when (actual) {
            is Either.Right -> assertEquals(forventet_resultat_med_flere_arbeidsforhold_i_samme_virksomhet, actual.b)
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }

    @Test
    fun `skal gruppere ytelser og trygd`() {
        every {
            arbeidsforholdService.finnArbeidsforhold(aktørId, fom, tom)
        } returns Either.Right(emptyList())

        every {
            inntektService.hentInntekter(aktørId, YearMonth.from(fom), YearMonth.from(tom), "ForeldrepengerA-Inntekt")
        } returns Either.Right(inntekter_med_ytelser_og_trygd)

        val actual = aktiveArbeidsforholdService.finnArbeidInntekterOgYtelser(aktørId, fom, tom)

        when (actual) {
            is Either.Right -> assertEquals(forventet_resultat_med_ytelser_og_trygd, actual.b)
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }

    @Test
    fun `skal telle inntekter som ikke har et tilhørende arbeidsforhold`() {
        every {
            arbeidsforholdService.finnArbeidsforhold(aktørId, fom, tom)
        } returns Either.Right(listOf(
                aktivt_arbeidstakerforhold
        ))

        every {
            inntektService.hentInntekter(aktørId, YearMonth.from(fom), YearMonth.from(tom), "ForeldrepengerA-Inntekt")
        } returns Either.Right(inntekter_fra_tre_virksomheter)

        every {
            organisasjonService.hentOrganisasjon(virksomhet2.organisasjonsnummer)
        } returns Either.Right(Organisasjon.JuridiskEnhet(virksomhet2.organisasjonsnummer))

        every {
            organisasjonService.hentOrganisasjon(virksomhet3.organisasjonsnummer)
        } returns Either.Right(Organisasjon.JuridiskEnhet(virksomhet3.organisasjonsnummer))

        every {
            organisasjonService.hentVirksomhetForJuridiskOrganisasjonsnummer(virksomhet2.organisasjonsnummer, match { dato ->
                dato == oktober_2018.atDay(1) || dato == november_2018.atDay(1) || dato == desember_2018.atDay(1)
            })
        } returns Either.Left(Feilårsak.IkkeFunnet)

        every {
            organisasjonService.hentVirksomhetForJuridiskOrganisasjonsnummer(virksomhet3.organisasjonsnummer, match { dato ->
                dato == februar_2019.atDay(1)
            })
        } returns Either.Left(Feilårsak.IkkeFunnet)

        val actual = aktiveArbeidsforholdService.finnArbeidInntekterOgYtelser(aktørId, fom, tom)

        assertEquals(0.0, foreløpigArbeidsforholdAvviksCounterAfter - foreløpigArbeidsforholdAvviksCounterBefore)
        assertEquals(0.0, arbeidsforholdAvviksCounterAfter - arbeidsforholdAvviksCounterBefore)
        assertEquals(4.0, foreløpigInntektAvviksCounterAfter - foreløpigInntektAvviksCounterBefore)
        assertEquals(4.0, inntektAvviksCounterAfter - inntektAvviksCounterBefore)

        when (actual) {
            is Either.Right -> assertEquals(forventet_resultat_med_inntekter_uten_arbeidsforhold, actual.b)
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }

    @Test
    fun `skal telle arbeidsforhold som ikke har tilhørende inntekter`() {
        every {
            arbeidsforholdService.finnArbeidsforhold(aktørId, fom, tom)
        } returns Either.Right(listOf(
                aktivt_arbeidstakerforhold,
                avsluttet_arbeidstakerforhold
        ))

        every {
            inntektService.hentInntekter(aktørId, YearMonth.from(fom), YearMonth.from(tom), "ForeldrepengerA-Inntekt")
        } returns Either.Right(listOf(
                lønn_virksomhet2_oktober,
                lønn_virksomhet2_november,
                lønn_virksomhet2_desember
        ))

        val actual = aktiveArbeidsforholdService.finnArbeidInntekterOgYtelser(aktørId, fom, tom)

        assertEquals(1.0, foreløpigArbeidsforholdAvviksCounterAfter - foreløpigArbeidsforholdAvviksCounterBefore)
        assertEquals(1.0, arbeidsforholdAvviksCounterAfter - arbeidsforholdAvviksCounterBefore)
        assertEquals(0.0, foreløpigInntektAvviksCounterAfter - foreløpigInntektAvviksCounterBefore)
        assertEquals(0.0, inntektAvviksCounterAfter - inntektAvviksCounterBefore)

        when (actual) {
            is Either.Right -> assertEquals(forventet_resultat_med_arbeidsforhold_uten_inntekter, actual.b)
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }

    @Test
    fun `skal sammenstille inntekter rapportert på jurdisk nummer med arbeidsforhold rapporter på virksomhetsnummer`() {
        every {
            arbeidsforholdService.finnArbeidsforhold(aktørId, fom, tom)
        } returns Either.Right(listOf(
                aktivt_arbeidstakerforhold,
                aktivt_arbeidstakerforhold_i_annen_virksomhet
        ))

        every {
            inntektService.hentInntekter(aktørId, YearMonth.from(fom), YearMonth.from(tom), "ForeldrepengerA-Inntekt")
        } returns Either.Right(listOf(
                lønn_virksomhet1_oktober,
                lønn_virksomhet2_november,
                lønn_virksomhet2_desember,
                lønn_virksomhet4_desember
        ))

        every {
            organisasjonService.hentOrganisasjon(virksomhet2.organisasjonsnummer)
        } returns Either.Right(Organisasjon.JuridiskEnhet(virksomhet2.organisasjonsnummer, "ARBEIDS- OG VELFERDSETATEN"))

        every {
            organisasjonService.hentOrganisasjon(virksomhet4.organisasjonsnummer)
        } returns Either.Right(Organisasjon.JuridiskEnhet(virksomhet4.organisasjonsnummer, "STORTINGET"))

        every {
            organisasjonService.hentVirksomhetForJuridiskOrganisasjonsnummer(virksomhet2.organisasjonsnummer, match { dato ->
                dato == november_2018.atDay(1) || dato == desember_2018.atDay(1)
            })
        } returns Either.Right(virksomhet1.organisasjonsnummer)

        every {
            organisasjonService.hentVirksomhetForJuridiskOrganisasjonsnummer(virksomhet4.organisasjonsnummer, match { dato ->
                dato == desember_2018.atDay(1)
            })
        } returns Either.Right(virksomhet3.organisasjonsnummer)

        val actual = aktiveArbeidsforholdService.finnArbeidInntekterOgYtelser(aktørId, fom, tom)

        assertEquals(1.0, foreløpigArbeidsforholdAvviksCounterAfter - foreløpigArbeidsforholdAvviksCounterBefore)
        assertEquals(0.0, arbeidsforholdAvviksCounterAfter - arbeidsforholdAvviksCounterBefore)
        assertEquals(3.0, foreløpigInntektAvviksCounterAfter - foreløpigInntektAvviksCounterBefore)
        assertEquals(0.0, inntektAvviksCounterAfter - inntektAvviksCounterBefore)

        when (actual) {
            is Either.Right -> assertEquals(forventet_resultat_inntekter_slått_sammen_med_arbeidsforhold, actual.b)
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }

    @Test
    fun `skal telle avvik når oppslag for virksomhetsnr for juridisk enhet ender i feil, og vi ikke kan mappe til arbeidsforhold`() {
        every {
            arbeidsforholdService.finnArbeidsforhold(aktørId, fom, tom)
        } returns Either.Right(listOf(
                aktivt_arbeidstakerforhold
        ))

        every {
            inntektService.hentInntekter(aktørId, YearMonth.from(fom), YearMonth.from(tom), "ForeldrepengerA-Inntekt")
        } returns Either.Right(listOf(
                lønn_virksomhet2_oktober,
                lønn_virksomhet2_november,
                lønn_virksomhet2_desember
        ))

        every {
            organisasjonService.hentOrganisasjon(virksomhet2.organisasjonsnummer)
        } returns Either.Right(Organisasjon.JuridiskEnhet(virksomhet2.organisasjonsnummer, "ARBEIDS- OG VELFERDSETATEN"))

        every {
            organisasjonService.hentVirksomhetForJuridiskOrganisasjonsnummer(virksomhet2.organisasjonsnummer, match { dato ->
                dato == oktober_2018.atDay(1) || dato == november_2018.atDay(1) || dato == desember_2018.atDay(1)
            })
        } returns Either.Left(Feilårsak.FeilFraTjeneste)

        val actual = aktiveArbeidsforholdService.finnArbeidInntekterOgYtelser(aktørId, fom, tom)

        assertEquals(1.0, foreløpigArbeidsforholdAvviksCounterAfter - foreløpigArbeidsforholdAvviksCounterBefore)
        assertEquals(1.0, arbeidsforholdAvviksCounterAfter - arbeidsforholdAvviksCounterBefore)
        assertEquals(3.0, foreløpigInntektAvviksCounterAfter - foreløpigInntektAvviksCounterBefore)
        assertEquals(3.0, inntektAvviksCounterAfter - inntektAvviksCounterBefore)

        when (actual) {
            is Either.Right -> assertEquals(forventet_resultat_med_avvik_på_arbeidsforhold_og_inntekter, actual.b)
            is Either.Left -> fail { "Expected Either.Right to be returned" }
        }
    }
}

private val orgnr1 = Organisasjonsnummer("995298775")
private val orgnr2 = Organisasjonsnummer("889640782")
private val orgnr3 = Organisasjonsnummer("971524960")
private val orgnr4 = Organisasjonsnummer("912998827")

private val virksomhet1 = Virksomhet.Organisasjon(orgnr1)
private val virksomhet2 = Virksomhet.Organisasjon(orgnr2)
private val virksomhet3 = Virksomhet.Organisasjon(orgnr3)
private val virksomhet4 = Virksomhet.Organisasjon(orgnr4)

private val arbeidsforholdId1 = 1234L
private val arbeidsforholdId2 = 5678L
private val arbeidsforholdId3 = 4321L

private val aktivt_arbeidstakerforhold_startdato = LocalDate.parse("2019-01-01")
private val aktivt_arbeidstakerforhold = Arbeidsforhold.Arbeidstaker(
        arbeidsgiver = virksomhet1,
        startdato = aktivt_arbeidstakerforhold_startdato,
        arbeidsforholdId = arbeidsforholdId1,
        arbeidsavtaler = listOf(
                Arbeidsavtale("Butikkmedarbeider", BigDecimal(100), aktivt_arbeidstakerforhold_startdato, null)
        ))

private val aktivt_arbeidstakerforhold_i_samme_virksomhet_startdato = LocalDate.parse("2018-12-01")
private val aktivt_arbeidstakerforhold_i_samme_virksomhet = Arbeidsforhold.Arbeidstaker(
        arbeidsgiver = virksomhet1,
        startdato = aktivt_arbeidstakerforhold_i_samme_virksomhet_startdato,
        arbeidsforholdId = arbeidsforholdId2,
        arbeidsavtaler = listOf(
                Arbeidsavtale("Butikkmedarbeider", BigDecimal(100), aktivt_arbeidstakerforhold_i_samme_virksomhet_startdato, null)
        ))

private val aktivt_arbeidstakerforhold_i_annen_virksomhet_startdato = LocalDate.parse("2019-01-01")
private val aktivt_arbeidstakerforhold_i_annen_virksomhet = Arbeidsforhold.Arbeidstaker(
        arbeidsgiver = virksomhet3,
        startdato = aktivt_arbeidstakerforhold_i_annen_virksomhet_startdato,
        arbeidsforholdId = arbeidsforholdId2,
        arbeidsavtaler = listOf(
                Arbeidsavtale("Butikkmedarbeider", BigDecimal(100), aktivt_arbeidstakerforhold_i_annen_virksomhet_startdato, null)
        ))

private val avsluttet_arbeidstakerforhold_startdato = LocalDate.parse("2018-01-01")
private val avsluttet_arbeidstakerforhold_sluttdato = LocalDate.parse("2018-12-31")
private val avsluttet_arbeidstakerforhold = Arbeidsforhold.Arbeidstaker(
        arbeidsgiver = virksomhet2,
        startdato = avsluttet_arbeidstakerforhold_startdato,
        sluttdato = avsluttet_arbeidstakerforhold_sluttdato,
        arbeidsforholdId = arbeidsforholdId3,
        arbeidsavtaler = listOf(
                Arbeidsavtale("Butikkmedarbeider", BigDecimal(100), avsluttet_arbeidstakerforhold_startdato, avsluttet_arbeidstakerforhold_sluttdato)
        ))

private val aktivt_frilansforhold_startdato = LocalDate.parse("2019-01-01")
private val aktivt_frilansforhold = Arbeidsforhold.Frilans(
        arbeidsgiver = virksomhet3,
        startdato = aktivt_frilansforhold_startdato,
        sluttdato = null,
        yrke = "Butikkmedarbeider")

private val januar_2019 = YearMonth.of(2019, JANUARY)
private val februar_2019 = YearMonth.of(2019, FEBRUARY)
private val mars_2019 = YearMonth.of(2019, MARCH)
private val oktober_2018 = YearMonth.of(2018, OCTOBER)
private val november_2018 = YearMonth.of(2018, NOVEMBER)
private val desember_2018 = YearMonth.of(2018, DECEMBER)

private val lønn_virksomhet1_januar = Inntekt.Lønn(virksomhet1, januar_2019, BigDecimal(20000))
private val lønn_virksomhet1_februar = Inntekt.Lønn(virksomhet1, februar_2019, BigDecimal(25000))
private val lønn_virksomhet1_oktober = Inntekt.Lønn(virksomhet1, oktober_2018, BigDecimal(15000))
private val lønn_virksomhet2_oktober = Inntekt.Lønn(virksomhet2, oktober_2018, BigDecimal(15000))
private val lønn_virksomhet1_november = Inntekt.Lønn(virksomhet1, november_2018, BigDecimal(16000))
private val lønn_virksomhet2_november = Inntekt.Lønn(virksomhet2, november_2018, BigDecimal(16000))
private val lønn_virksomhet1_desember = Inntekt.Lønn(virksomhet1, desember_2018, BigDecimal(17000))
private val lønn_virksomhet2_desember = Inntekt.Lønn(virksomhet2, desember_2018, BigDecimal(17000))
private val lønn_virksomhet3_desember = Inntekt.Lønn(virksomhet3, desember_2018, BigDecimal(18000))
private val lønn_virksomhet4_desember = Inntekt.Lønn(virksomhet4, desember_2018, BigDecimal(18000))
private val lønn_virksomhet3_februar = Inntekt.Lønn(virksomhet3, februar_2019, BigDecimal(30000))

private val inntekter_fra_tre_virksomheter = listOf(
        lønn_virksomhet1_januar,
        lønn_virksomhet1_februar,
        lønn_virksomhet2_oktober,
        lønn_virksomhet2_november,
        lønn_virksomhet2_desember,
        lønn_virksomhet3_februar
)

private val lønn_virksomhet1_mars = Inntekt.Lønn(virksomhet1, mars_2019, BigDecimal(30000))
private val lønn_virksomhet2_mars = Inntekt.Lønn(virksomhet2, mars_2019, BigDecimal(30000))

private val inntekter_fra_samme_virksomhet_med_juridisk_nummer = listOf(
        lønn_virksomhet1_januar,
        lønn_virksomhet1_februar,
        lønn_virksomhet2_mars
)

private val inntekter_med_ytelser_og_trygd = listOf(
        Inntekt.Ytelse(virksomhet1, YearMonth.of(2019, 1), BigDecimal(20000), "foreldrepenger"),
        Inntekt.Ytelse(virksomhet1, YearMonth.of(2019, 2), BigDecimal(25000), "sykepenger"),
        Inntekt.PensjonEllerTrygd(virksomhet2, YearMonth.of(2018, 10), BigDecimal(15000), "ufoerepensjonFraAndreEnnFolketrygden"),
        Inntekt.PensjonEllerTrygd(virksomhet2, YearMonth.of(2018, 11), BigDecimal(16000), "ufoerepensjonFraAndreEnnFolketrygden")
)

private val forventet_resultat_uten_avvik = ArbeidInntektYtelse(
        arbeidsforhold = mapOf(
                aktivt_arbeidstakerforhold to mapOf(
                        januar_2019 to listOf(lønn_virksomhet1_januar),
                        februar_2019 to listOf(lønn_virksomhet1_februar)
                ),
                avsluttet_arbeidstakerforhold to mapOf(
                        oktober_2018 to listOf(lønn_virksomhet2_oktober),
                        november_2018 to listOf(lønn_virksomhet2_november),
                        desember_2018 to listOf(lønn_virksomhet2_desember)
                ),
                aktivt_frilansforhold to mapOf(
                        februar_2019 to listOf(lønn_virksomhet3_februar)
                )
        ),
        inntekterUtenArbeidsforhold = emptyList(),
        arbeidsforholdUtenInntekter = emptyList(),
        ytelser = emptyList(),
        pensjonEllerTrygd = emptyList(),
        næringsinntekt = emptyList()
)

private val forventet_resultat_med_flere_arbeidsforhold_i_samme_virksomhet = ArbeidInntektYtelse(
        arbeidsforhold = mapOf(
                aktivt_arbeidstakerforhold to mapOf(
                        januar_2019 to listOf(
                                lønn_virksomhet1_januar
                        ),
                        februar_2019 to listOf(
                                lønn_virksomhet1_februar
                        ),
                        mars_2019 to listOf(
                                lønn_virksomhet1_mars
                        )
                )
        ),
        arbeidsforholdUtenInntekter = listOf(
                aktivt_arbeidstakerforhold_i_samme_virksomhet
        )
)

private val forventet_resultat_med_ytelser_og_trygd = ArbeidInntektYtelse(
        ytelser = listOf(
                Inntekt.Ytelse(virksomhet1, YearMonth.of(2019, 1), BigDecimal(20000), "foreldrepenger"),
                Inntekt.Ytelse(virksomhet1, YearMonth.of(2019, 2), BigDecimal(25000), "sykepenger")
        ),
        pensjonEllerTrygd = listOf(
                Inntekt.PensjonEllerTrygd(virksomhet2, YearMonth.of(2018, 10), BigDecimal(15000), "ufoerepensjonFraAndreEnnFolketrygden"),
                Inntekt.PensjonEllerTrygd(virksomhet2, YearMonth.of(2018, 11), BigDecimal(16000), "ufoerepensjonFraAndreEnnFolketrygden")
        )
)

private val forventet_resultat_med_inntekter_uten_arbeidsforhold = ArbeidInntektYtelse(
        arbeidsforhold = mapOf(
                aktivt_arbeidstakerforhold to mapOf(
                        januar_2019 to listOf(
                                lønn_virksomhet1_januar
                        ),
                        februar_2019 to listOf(
                                lønn_virksomhet1_februar
                        )
                )
        ),
        inntekterUtenArbeidsforhold = listOf(
                lønn_virksomhet2_oktober,
                lønn_virksomhet2_november,
                lønn_virksomhet2_desember,
                lønn_virksomhet3_februar
        )
)

private val forventet_resultat_med_arbeidsforhold_uten_inntekter = ArbeidInntektYtelse(
        arbeidsforhold = mapOf(
                avsluttet_arbeidstakerforhold to mapOf(
                        oktober_2018 to listOf(
                                lønn_virksomhet2_oktober
                        ),
                        november_2018 to listOf(
                                lønn_virksomhet2_november
                        ),
                        desember_2018 to listOf(
                                lønn_virksomhet2_desember
                        )
                )
        ),
        arbeidsforholdUtenInntekter = listOf(
                aktivt_arbeidstakerforhold
        )
)

private val forventet_resultat_inntekter_slått_sammen_med_arbeidsforhold = ArbeidInntektYtelse(
        arbeidsforhold = mapOf(
                aktivt_arbeidstakerforhold to mapOf(
                        oktober_2018 to listOf(
                                lønn_virksomhet1_oktober
                        ),
                        november_2018 to listOf(
                                lønn_virksomhet1_november
                        ),
                        desember_2018 to listOf(
                                lønn_virksomhet1_desember
                        )
                ),
                aktivt_arbeidstakerforhold_i_annen_virksomhet to mapOf(
                        desember_2018 to listOf(
                                lønn_virksomhet3_desember
                        )
                )
        )
)

private val forventet_resultat_med_avvik_på_arbeidsforhold_og_inntekter = ArbeidInntektYtelse(
        arbeidsforhold = emptyMap(),
        inntekterUtenArbeidsforhold = listOf(
                lønn_virksomhet2_oktober,
                lønn_virksomhet2_november,
                lønn_virksomhet2_desember
        ),
        arbeidsforholdUtenInntekter = listOf(
                aktivt_arbeidstakerforhold
        )
)