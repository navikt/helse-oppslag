package no.nav.helse.ws.organisasjon

import io.prometheus.client.CollectorRegistry
import no.nav.helse.Failure
import no.nav.helse.Success
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class ComponentTest {

    private val metricsRegistry = CollectorRegistry.defaultRegistry

    @AfterEach
    fun afterEach() {
        metricsRegistry.clear()
    }

    @Test
    fun stubbedLookup() {
        val organisasjonClient = OrganisasjonClient(OrganisasjonV5Stub())
        val expected = OrganisasjonClient.OrganisasjonResponse("fornavn, mellomnavn, etternavn")
        val actual = organisasjonClient.orgNavn("12345")
        when (actual) {
            is Success<*> -> {
                assertEquals(1.0, metricsRegistry.getSampleValue(
                        "oppslag_organisasjon", arrayOf("status"), arrayOf("success")))
                assertEquals(expected, actual.data)
            }
            is Failure -> fail { "This lookup was expected to succeed, but it didn't" }
        }
    }

    @Test
    fun stubbedLookupWithError() {
        val organisasjonClient = OrganisasjonClient(OrganisasjonV5MisbehavingStub())
        val expected = Failure(listOf("SOAPy stuff got besmirched"))
        val actual = organisasjonClient.orgNavn("12345")
        when (actual) {
            is Success<*> -> fail { "This lookup was expected to fail, but it didn't" }
            is Failure -> {
                assertEquals(1.0, metricsRegistry.getSampleValue(
                        "oppslag_organisasjon", arrayOf("status"), arrayOf("failure")))
                assertEquals(expected, actual)
            }
        }
    }

}
