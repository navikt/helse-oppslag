package no.nav.helse.ws.sykepenger

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.helse.Failure
import no.nav.helse.OppslagResult
import no.nav.helse.Success
import no.nav.helse.http.aktør.AktørregisterClient
import no.nav.helse.ws.Fødselsnummer
import java.time.LocalDate

fun Route.sykepengeListe(factory: () -> SykepengerClient,
                         aktørregisterClientFactory: () -> AktørregisterClient) {
    val sykepenger by lazy(factory)
    val aktørregisterClient by lazy(aktørregisterClientFactory)

    get("api/sykepengevedtak/{aktorId}") {
        if (!call.request.queryParameters.contains("fom") || !call.request.queryParameters.contains("tom")) {
            call.respond(HttpStatusCode.BadRequest, "you need to supply query parameter fom and tom")
        } else {
            val fom = LocalDate.parse(call.request.queryParameters["fom"]!!)
            val tom = LocalDate.parse(call.request.queryParameters["tom"]!!)

            val fnr = Fødselsnummer(aktørregisterClient.gjeldendeNorskIdent(call.parameters["aktorId"]!!))

            val lookupResult: OppslagResult = sykepenger.finnSykepengeVedtak(fnr, fom, tom)
            when (lookupResult) {
                is Success<*> -> call.respond(lookupResult.data!!)
                is Failure -> call.respond(HttpStatusCode.InternalServerError, "that didn't go so well...")
            }
        }
    }
}
