package no.nav.helse.domene.inntekt.dto

import java.math.BigDecimal
import java.time.YearMonth

data class InntektDTO(val arbeidsgiver: VirksomhetDTO, val utbetalingsperiode: YearMonth, val beløp: BigDecimal, val ytelse: Boolean, val kode: String?)
