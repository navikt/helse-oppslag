package no.nav.helse.ws.arbeidsforhold

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.helse.Failure
import no.nav.helse.OppslagResult
import no.nav.helse.Success
import no.nav.helse.ws.Fødselsnummer

fun Route.arbeidsforhold(factory: () -> ArbeidsforholdClient) {
    val arbeidsforholdClient by lazy(factory)

    post("api/arbeidsforhold") {
        call.receiveParameters()["fnr"]?.let { fnr ->
            val lookupResult: OppslagResult = arbeidsforholdClient.finnArbeidsforholdForFnr(Fødselsnummer(fnr))
            when (lookupResult) {
                is Success<*> -> call.respond(lookupResult.data!!)
                is Failure -> call.respond(HttpStatusCode.InternalServerError, "that didn't go so well...")
            }
        } ?: call.respond(HttpStatusCode.BadRequest, "you need to supply fnr=12345678910")
    }
}
