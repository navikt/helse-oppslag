package no.nav.helse.domene.ytelse.domain

sealed class Tema {

    object Sykepenger: Tema()
    object Foreldrepenger: Tema()
    object Engangsstønad: Tema()
    object PårørendeSykdom: Tema()
    object EnsligForsørger: Tema()
    data class Ukjent(val tema: String): Tema()

    fun name() = when (this) {
        is Sykepenger -> "Sykepenger"
        is Foreldrepenger -> "Foreldrepenger"
        is Engangsstønad -> "Engangsstønad"
        is PårørendeSykdom -> "PårørendeSykdom"
        is EnsligForsørger -> "EnsligForsørger"
        is Ukjent -> "Ukjent"
    }

    override fun toString(): String {
        return name()
    }

    companion object {
        fun fraKode(tema: String) =
                when (tema) {
                    "SP" -> Tema.Sykepenger
                    "FA" -> Tema.Foreldrepenger
                    "BS" -> Tema.PårørendeSykdom
                    "EF" -> Tema.EnsligForsørger
                    else -> Tema.Ukjent(tema)
                }
    }
}
