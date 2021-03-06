package no.nav.helse.oppslag.arbeidsfordeling

import arrow.core.Try
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.ContainsPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import no.nav.helse.domene.person.domain.GeografiskTilknytning
import no.nav.helse.oppslag.*
import no.nav.helse.oppslag.sts.STS_SAML_POLICY_NO_TRANSPORT_BINDING
import no.nav.helse.oppslag.sts.configureFor
import no.nav.helse.oppslag.sts.stsClient
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.FinnBehandlendeEnhetListeUgyldigInput
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Organisasjonsenhet
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.*

class ArbeidsfordelingIntegrationTest {

    companion object {
        val server: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun start() {
            server.start()
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            server.stop()
        }
    }

    @BeforeEach
    fun configure() {
        val client = WireMock.create().port(server.port()).build()
        WireMock.configureFor(client)
        client.resetMappings()
    }

    @Test
    fun `skal svare med gyldig enhet`() {
        val tema = "OMS"

        val enhetId = "4432"
        val enhetNavn = "NAV Arbeid og ytelser Follo"

        val request = WireMock.post(WireMock.urlPathEqualTo("/arbeidsfordeling"))
                .withSoapAction("http://nav.no/tjeneste/virksomhet/arbeidsfordeling/v1/Arbeidsfordeling_v1/finnBehandlendeEnhetListeRequest")
                .withRequestBody(ContainsPattern("<tema>$tema</tema>"))

        arbeidsfordelingStub(
                server = server,
                scenario = "arbeidsfordeling_finn_behandlende_enhet",
                response = WireMock.okXml(finnBehandlendeEnhetListe_response(enhetId, enhetNavn)),
                request = request
        ) { arbeidsfordelingClient ->
            val expected = listOf(Organisasjonsenhet().apply {
                this.enhetId = enhetId
                this.enhetNavn = enhetNavn
            })
            val actual = arbeidsfordelingClient.getBehandlendeEnhet(GeografiskTilknytning(null, null), Tema(tema))

            when (actual) {
                is Try.Success -> {
                    assertEquals(expected.size, actual.value.size)
                    expected.forEachIndexed { key, value ->
                        assertEquals(value.enhetId, actual.value[key].enhetId)
                        assertEquals(value.enhetNavn, actual.value[key].enhetNavn)
                    }
                }
                else -> fail { "Expected Try.Success to be returned" }
            }
        }
    }

    @Test
    fun `skal svare med feil dersom tema er ugyldig`() {
        val tema = "OMS2"

        val request = WireMock.post(WireMock.urlPathEqualTo("/arbeidsfordeling"))
                .withSoapAction("http://nav.no/tjeneste/virksomhet/arbeidsfordeling/v1/Arbeidsfordeling_v1/finnBehandlendeEnhetListeRequest")
                .withRequestBody(ContainsPattern("<tema>$tema</tema>"))

        arbeidsfordelingStub(
                server = server,
                scenario = "arbeidsfordeling_finn_behandlende_enhet",
                response = WireMock.serverError().withBody(finnBehandlendeEnhetListe_ugyldig_response(tema)),
                request = request
        ) { arbeidsfordelingClient ->
            val actual = arbeidsfordelingClient.getBehandlendeEnhet(GeografiskTilknytning(null, null), Tema(tema))

            when (actual) {
                is Try.Failure -> {
                    when (actual.exception) {
                        is FinnBehandlendeEnhetListeUgyldigInput -> assertEquals("'$tema' er en ugyldig kode for kodeverket: 'Tema'", actual.exception.message)
                        else -> fail { "Expected FinnBehandlendeEnhetListeUgyldigInput to be returned" }
                    }
                }
                else -> fail { "Expected Try.Failure to be returned" }
            }
        }
    }
}

fun arbeidsfordelingStub(server: WireMockServer, scenario: String, response: ResponseDefinitionBuilder, request: MappingBuilder, test: (ArbeidsfordelingClient) -> Unit) {
    val stsUsername = "stsUsername"
    val stsPassword = "stsPassword"

    val tokenSubject = "srvtestapp"
    val tokenIssuer = "Certificate Authority Inc"
    val tokenIssuerName = "CN=Certificate Authority Inc, DC=example, DC=com"
    val tokenDigest = "a random string"
    val tokenSignature = "yet another random string"
    val tokenCertificate = "one last random string"

    WireMock.stubFor(stsStub(stsUsername, stsPassword)
            .willReturn(samlAssertionResponse(tokenSubject, tokenIssuer, tokenIssuerName,
                    tokenDigest, tokenSignature, tokenCertificate))
            .inScenario(scenario)
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("security_token_service_called"))

    val stsClientWs = stsClient(server.baseUrl().plus("/sts"), stsUsername to stsPassword)
    val callId = UUID.randomUUID().toString()

    WireMock.stubFor(request
            .withSamlAssertion(tokenSubject, tokenIssuer, tokenIssuerName,
                    tokenDigest, tokenSignature, tokenCertificate)
            .withCallId(callId)
            .willReturn(response)
            .inScenario(scenario)
            .whenScenarioStateIs("security_token_service_called")
            .willSetStateTo("arbeidsfordeling_stub_called"))

    test(ArbeidsfordelingClient(ArbeidsfordelingFactory.create(server.baseUrl().plus("/arbeidsfordeling"), outInterceptors = listOf(CallIdInterceptor {
        callId
    })).apply {
        stsClientWs.configureFor(this, STS_SAML_POLICY_NO_TRANSPORT_BINDING)
    }))

    WireMock.listAllStubMappings().mappings.forEach {
        WireMock.verify(RequestPatternBuilder.like(it.request))
    }
}

fun finnBehandlendeEnhetListe_response(enhetId: String, enhetNavn: String) = """
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
    <SOAP-ENV:Header xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"/>
    <soap:Body>
        <ns2:finnBehandlendeEnhetListeResponse xmlns:ns2="http://nav.no/tjeneste/virksomhet/arbeidsfordeling/v1/">
            <response>
                <behandlendeEnhetListe>
                    <enhetId>$enhetId</enhetId>
                    <enhetNavn>$enhetNavn</enhetNavn>
                    <status>AKTIV</status>
                    <type>YTA</type>
                </behandlendeEnhetListe>
            </response>
        </ns2:finnBehandlendeEnhetListeResponse>
    </soap:Body>
</soap:Envelope>
""".trimIndent()

fun finnBehandlendeEnhetListe_ugyldig_response(tema: String) = """
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
    <SOAP-ENV:Header xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"/>
    <soap:Body>
        <soap:Fault>
            <faultcode>soap:Server</faultcode>
            <faultstring>'$tema' er en ugyldig kode for kodeverket: 'Tema'</faultstring>
            <detail>
                <ns2:finnBehandlendeEnhetListeugyldigInput xmlns:ns2="http://nav.no/tjeneste/virksomhet/arbeidsfordeling/v1/">
                    <feilkilde>NORG2.ArbeidsfordelingV1:finnBehandlendeEnhetListe</feilkilde>
                    <feilaarsak>[message="'$tema' er en ugyldig kode for kodeverket: 'Tema'",logged=true,handled=false]</feilaarsak>
                    <feilmelding>'$tema' er en ugyldig kode for kodeverket: 'Tema'</feilmelding>
                    <tidspunkt>2019-02-20T15:04:32.095+01:00</tidspunkt>
                </ns2:finnBehandlendeEnhetListeugyldigInput>
            </detail>
        </soap:Fault>
    </soap:Body>
</soap:Envelope>
""".trimIndent()
