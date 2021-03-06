package no.nav.helse.domene.aiy.domain

import no.nav.helse.domene.aiy.organisasjon.domain.Organisasjonsnummer

sealed class Virksomhet(val identifikator: String) {
    data class Organisasjon(val organisasjonsnummer: Organisasjonsnummer): Virksomhet(organisasjonsnummer.value)
    data class Person(val personnummer: String): Virksomhet(personnummer)
    data class NavAktør(val aktørId: String): Virksomhet(aktørId)

    fun type() = when (this) {
        is Organisasjon -> "Organisasjon"
        is Person -> "Person"
        is NavAktør -> "NavAktør"
    }
}
