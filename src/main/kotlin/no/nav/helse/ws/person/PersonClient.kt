package no.nav.helse.ws.person

import io.prometheus.client.Counter
import no.nav.helse.Failure
import no.nav.helse.OppslagResult
import no.nav.helse.Success
import no.nav.helse.ws.*
import no.nav.helse.ws.person.Kjønn.KVINNE
import no.nav.helse.ws.person.Kjønn.MANN
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.*
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonRequest
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse
import org.slf4j.LoggerFactory
import java.time.LocalDate
import javax.xml.datatype.XMLGregorianCalendar

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
}

object PersonMapper {
    fun toPerson(response: HentPersonResponse): Person {
        val tpsPerson = response.person
        response.person.foedselsdato.foedselsdato

        return Person(
                AktørId((tpsPerson.aktoer as AktoerId).aktoerId),
                tpsPerson.personnavn.fornavn,
                tpsPerson.personnavn.mellomnavn,
                tpsPerson.personnavn.etternavn,
                toLocalDate(tpsPerson.foedselsdato.foedselsdato),
                if (response.person.kjoenn.kjoenn.value == "M") MANN else KVINNE
        )
    }

    private fun toLocalDate(cal: XMLGregorianCalendar): LocalDate {
        return LocalDate.of(cal.year, cal.month, cal.day)
    }
}

enum class Kjønn {
    MANN, KVINNE
}

data class Person(
        val id: AktørId,
        val fornavn: String,
        val mellomnavn: String? = null,
        val etternavn: String,
        val fdato: LocalDate,
        val kjønn: Kjønn
)



