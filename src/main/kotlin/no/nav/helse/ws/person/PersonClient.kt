package no.nav.helse.ws.person

import io.prometheus.client.*
import no.nav.helse.*
import no.nav.helse.ws.*
import no.nav.tjeneste.virksomhet.person.v3.binding.*
import no.nav.tjeneste.virksomhet.person.v3.informasjon.*
import no.nav.tjeneste.virksomhet.person.v3.meldinger.*
import org.slf4j.*
import java.time.*

class PersonClient(private val personV3: PersonV3) {

    private val counter = Counter.build()
            .name("oppslag_person")
            .labelNames("status")
            .help("Antall registeroppslag av personer")
            .register()

    private val log = LoggerFactory.getLogger("PersonClient")

    fun personInfo(id: AktørId): OppslagResult {
        val aktør = AktoerId().apply {
            aktoerId = id.aktor
        }

        val request = HentPersonRequest().apply {
            aktoer = aktør
        }

        return try {
            val tpsResponse = personV3.hentPerson(request)
            counter.labels("success").inc()
            Success(PersonMapper.toPerson(tpsResponse))
        } catch (ex: Exception) {
            log.error("Error while doing person lookup", ex)
            counter.labels("failure").inc()
            Failure(listOf(ex.message ?: "unknown error"))
        }
    }

    fun personHistorikk(id: AktørId, fom: LocalDate, tom: LocalDate): OppslagResult {
        val aktør = AktoerId().apply {
            aktoerId = id.aktor
        }

        val request = HentPersonhistorikkRequest().apply {
            aktoer = aktør
            periode = Periode().apply {
                this.fom = fom.toXmlGregorianCalendar()
                this.tom = tom.toXmlGregorianCalendar()
            }
        }

        return try {
            val tpsResponse = personV3.hentPersonhistorikk(request)
            counter.labels("success").inc()
            Success(PersonhistorikkMapper.toPersonhistorikk(tpsResponse))
        } catch (ex: Exception) {
            log.error("Error while doing personhistorikk lookup", ex)
            counter.labels("failure").inc()
            Failure(listOf(ex.message ?: "unknown error"))
        }

    }
}







