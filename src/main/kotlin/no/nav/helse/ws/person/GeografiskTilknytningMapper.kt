package no.nav.helse.ws.person

import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bydel
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Kommune
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Land
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningResponse

object GeografiskTilknytningMapper {
    fun tilGeografiskTilknytning(response : HentGeografiskTilknytningResponse) : GeografiskTilknytning {
        var diskresjonskode : Diskresjonskode? = null
        var geografiskOmraade : GeografiskOmraade? = null

        if (response.diskresjonskode != null && response.diskresjonskode.value != null) {
            diskresjonskode = kjenteDiskresjonsKoder[response.diskresjonskode.value] ?: Diskresjonskode(forkortelse = response.diskresjonskode.value)
        }

        if (response.geografiskTilknytning != null && response.geografiskTilknytning.geografiskTilknytning != null) {
            val type = when (response.geografiskTilknytning) {
                is Bydel -> "BYDEL"
                is Land -> "LAND"
                is Kommune -> "KOMMUNE"
                else -> "UKJENT"
            }
            geografiskOmraade = GeografiskOmraade(type = type, kode = response.geografiskTilknytning.geografiskTilknytning)
        }

        return GeografiskTilknytning(
                diskresjonskode = diskresjonskode,
                geografiskOmraade = geografiskOmraade
        )
    }
}

data class GeografiskTilknytning(
    val diskresjonskode: Diskresjonskode?,
    val geografiskOmraade: GeografiskOmraade?
)

data class GeografiskOmraade (
    val type : String,
    val kode : String
)


data class Diskresjonskode(
    val forkortelse : String,
    val beskrivelse : String? = null,
    val kode : Int? = null
)

private val SPSF = Diskresjonskode(forkortelse = "SPSF", beskrivelse = "Sperret adresse, strengt fortrolig", kode = 6)
private val SPFO = Diskresjonskode(forkortelse = "SPFO", beskrivelse = "Sperret adresse, fortrolig", kode = 7)


private val kjenteDiskresjonsKoder = mapOf(
        Pair(SPSF.forkortelse, SPSF),
        Pair(SPFO.forkortelse, SPFO)
)

