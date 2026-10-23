package no.nav.helse.riskmock

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tools.jackson.databind.JsonNode
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class RiskMockApiTest {
    private companion object {
        const val FNR = "12345678910"
        const val ANNET_FNR = "10987654321"
        const val ORGNR_A = "111111111"
        const val ORGNR_B = "222222222"
        const val ORGNR_C = "333333333"
    }

    private val testRapid = TestRapid()
    private val svar = MockedeRisikovurderinger()
    private val toggles = Toggles()

    init {
        RiskMockRiver(testRapid, svar, toggles)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        svar.fjernAlle()
        toggles.svarMedAndreVedtaksperioder = false
    }

    @Test
    fun `default svar er OK når ingenting er mocket`() =
        riskMockTest {
            sendBehov(FNR, ORGNR_A).assertOk()
        }

    @Test
    fun `svar mocket for fnr gjelder uavhengig av orgnr`() =
        riskMockTest {
            postRisikovurdering("/risikovurdering/$FNR", ikkeOk("fnr-funn"))

            sendBehov(FNR, ORGNR_A).assertIkkeOk("fnr-funn")
            sendBehov(FNR, ORGNR_B).assertIkkeOk("fnr-funn")
            sendBehov(ANNET_FNR, ORGNR_A).assertOk()
        }

    @Test
    fun `to ulike orgnr for samme fnr gir ulike svar`() =
        riskMockTest {
            postRisikovurdering("/risikovurdering/$FNR/$ORGNR_A", ikkeOk("funn-a"))
            postRisikovurdering("/risikovurdering/$FNR/$ORGNR_B", ikkeOk("funn-b"))

            sendBehov(FNR, ORGNR_A).assertIkkeOk("funn-a")
            sendBehov(FNR, ORGNR_B).assertIkkeOk("funn-b")
        }

    @Test
    fun `svar mocket for fnr og orgnr gjelder kun for det orgnr`() =
        riskMockTest {
            postRisikovurdering("/risikovurdering/$FNR/$ORGNR_A", ikkeOk("funn-a"))

            sendBehov(FNR, ORGNR_A).assertIkkeOk("funn-a")
            sendBehov(FNR, ORGNR_B).assertOk()
            sendBehov(ANNET_FNR, ORGNR_A).assertOk()
        }

    @Test
    fun `svar for fnr og orgnr har forrang foran svar for kun fnr`() =
        riskMockTest {
            postRisikovurdering("/risikovurdering/$FNR", ikkeOk("fnr-funn"))
            postRisikovurdering("/risikovurdering/$FNR/$ORGNR_A", ok())

            sendBehov(FNR, ORGNR_A).assertOk()
            sendBehov(FNR, ORGNR_B).assertIkkeOk("fnr-funn")
        }

    @Test
    fun `rekkefølgen på POST-kallene påvirker ikke forrang`() =
        riskMockTest {
            postRisikovurdering("/risikovurdering/$FNR/$ORGNR_A", ikkeOk("funn-a"))
            postRisikovurdering("/risikovurdering/$FNR", ikkeOk("fnr-funn"))

            sendBehov(FNR, ORGNR_A).assertIkkeOk("funn-a")
            sendBehov(FNR, ORGNR_B).assertIkkeOk("fnr-funn")
        }

    @Test
    fun `ny POST for samme fnr og orgnr overskriver forrige svar`() =
        riskMockTest {
            postRisikovurdering("/risikovurdering/$FNR/$ORGNR_A", ikkeOk("første"))
            postRisikovurdering("/risikovurdering/$FNR/$ORGNR_A", ikkeOk("andre"))

            sendBehov(FNR, ORGNR_A).assertIkkeOk("andre")
        }

    @Test
    fun `reset-fnr fjerner både fnr-svar og fnr+orgnr-svar for fnr`() =
        riskMockTest {
            postRisikovurdering("/risikovurdering/$FNR", ikkeOk("fnr-funn"))
            postRisikovurdering("/risikovurdering/$FNR/$ORGNR_A", ikkeOk("funn-a"))
            postRisikovurdering("/risikovurdering/$ANNET_FNR/$ORGNR_A", ikkeOk("annet-fnr-funn"))

            assertEquals(HttpStatusCode.OK, client.post("/reset-fnr/$FNR").status)

            sendBehov(FNR, ORGNR_A).assertOk()
            sendBehov(FNR, ORGNR_B).assertOk()
            sendBehov(ANNET_FNR, ORGNR_A).assertIkkeOk("annet-fnr-funn")
        }

    @Test
    fun `reset fjerner alle mockede svar`() =
        riskMockTest {
            postRisikovurdering("/risikovurdering/$FNR", ikkeOk("fnr-funn"))
            postRisikovurdering("/risikovurdering/$ANNET_FNR/$ORGNR_A", ikkeOk("funn-a"))

            assertEquals(HttpStatusCode.OK, client.post("/reset").status)

            sendBehov(FNR, ORGNR_B).assertOk()
            sendBehov(ANNET_FNR, ORGNR_A).assertOk()
        }

    @Test
    fun `ugyldig payload gir BadRequest`() =
        riskMockTest {
            val response =
                client.post("/risikovurdering/$FNR/$ORGNR_A") {
                    contentType(ContentType.Application.Json)
                    setBody("""{ "tull": true }""")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            sendBehov(FNR, ORGNR_A).assertOk()
        }

    @Test
    fun `svarer ikke med andreVedtaksperioder når toggle er av`() =
        riskMockTest {
            postRisikovurdering("/risikovurdering/$FNR/$ORGNR_B", ikkeOk("funn-b"))

            val løsning = sendBehovMedParallellePerioder(FNR, egenPeriode(ORGNR_A), annenPeriode(ORGNR_B))

            løsning.assertOk()
            assertTrue(løsning.path("andreVedtaksperioder").isMissingNode)
        }

    @Test
    fun `svarer med mocket risikovurdering for andre vedtaksperioder når toggle er på`() =
        riskMockTest {
            toggle("på")
            postRisikovurdering("/risikovurdering/$FNR/$ORGNR_B", ikkeOk("funn-b"))
            val egen = egenPeriode(ORGNR_A)
            val annenB = annenPeriode(ORGNR_B)
            val annenC = annenPeriode(ORGNR_C)

            val løsning = sendBehovMedParallellePerioder(FNR, egen, annenB, annenC)

            løsning.assertOk()
            val andre = løsning["andreVedtaksperioder"].associateBy { it["vedtaksperiodeId"].asString() }
            assertEquals(setOf(annenB.vedtaksperiodeId.toString(), annenC.vedtaksperiodeId.toString()), andre.keys)
            andre.getValue(annenB.vedtaksperiodeId.toString()).assertIkkeOk("funn-b")
            andre.getValue(annenC.vedtaksperiodeId.toString()).assertOk()
            assertEquals(0, andre.getValue(annenC.vedtaksperiodeId.toString())["kontrollertOk"].size())
        }

    @Test
    fun `svar mocket for kun fnr gjelder også andre vedtaksperioder`() =
        riskMockTest {
            toggle("på")
            postRisikovurdering("/risikovurdering/$FNR", ikkeOk("fnr-funn"))
            postRisikovurdering("/risikovurdering/$FNR/$ORGNR_A", ok())
            val annenB = annenPeriode(ORGNR_B)
            val selvstendig = annenPeriode(organisasjonsnummer = null)

            val løsning = sendBehovMedParallellePerioder(FNR, egenPeriode(ORGNR_A), annenB, selvstendig)

            løsning.assertOk()
            val andre = løsning["andreVedtaksperioder"].associateBy { it["vedtaksperiodeId"].asString() }
            andre.getValue(annenB.vedtaksperiodeId.toString()).assertIkkeOk("fnr-funn")
            andre.getValue(selvstendig.vedtaksperiodeId.toString()).assertIkkeOk("fnr-funn")
        }

    @Test
    fun `tom liste med andreVedtaksperioder når det ikke finnes parallelle perioder`() =
        riskMockTest {
            toggle("på")

            val løsning = sendBehovMedParallellePerioder(FNR, egenPeriode(ORGNR_A))

            løsning.assertOk()
            assertEquals(0, løsning["andreVedtaksperioder"].size())
        }

    @Test
    fun `toggle kan skrus av og på via API`() =
        riskMockTest {
            assertEquals("false", client.get("/toggle/andre-vedtaksperioder").bodyAsText())
            toggle("på")
            assertEquals("true", client.get("/toggle/andre-vedtaksperioder").bodyAsText())
            toggle("av")
            assertEquals("false", client.get("/toggle/andre-vedtaksperioder").bodyAsText())
            assertEquals(HttpStatusCode.BadRequest, client.post("/toggle/andre-vedtaksperioder/kanskje").status)
        }

    private data class Periode(
        val vedtaksperiodeId: UUID,
        val organisasjonsnummer: String?,
    )

    private fun egenPeriode(organisasjonsnummer: String) = Periode(UUID.randomUUID(), organisasjonsnummer)

    private fun annenPeriode(organisasjonsnummer: String?) = Periode(UUID.randomUUID(), organisasjonsnummer)

    private suspend fun ApplicationTestBuilder.toggle(verdi: String) {
        assertEquals(HttpStatusCode.OK, client.post("/toggle/andre-vedtaksperioder/$verdi").status)
    }

    private fun sendBehovMedParallellePerioder(
        fødselsnummer: String,
        egen: Periode,
        vararg andre: Periode,
    ): JsonNode {
        val perioder =
            (listOf(egen) + andre).joinToString(",") {
                val orgnr = it.organisasjonsnummer?.let { o -> "\"$o\"" } ?: "null"
                val type = if (it.organisasjonsnummer == null) "SELVSTENDIG" else "ARBEIDSTAKER"
                """
                {
                    "fom": "2020-01-01",
                    "tom": "2020-01-31",
                    "vedtaksperiodeId": "${it.vedtaksperiodeId}",
                    "yrkesaktivitetstype": "$type",
                    "organisasjonsnummer": $orgnr,
                    "inntekt": null
                }
                """
            }
        testRapid.sendTestMessage(
            """
            {
                "@event_name" : "behov",
                "@behov" : [ "Risikovurdering" ],
                "@id" : "${UUID.randomUUID()}",
                "@opprettet" : "2020-05-18T00:00:00",
                "vedtaksperiodeId" : "${egen.vedtaksperiodeId}",
                "fødselsnummer" : "$fødselsnummer",
                "Risikovurdering" : {
                    "vedtaksperiodeId" : "${egen.vedtaksperiodeId}",
                    "organisasjonsnummer" : "${egen.organisasjonsnummer}",
                    "perioderMedSammeSkjæringstidspunkt" : [ $perioder ]
                }
            }
            """,
        )
        val løsning = testRapid.inspektør.løsning("Risikovurdering")
        testRapid.reset()
        return løsning
    }

    private fun riskMockTest(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { riskMockApi(svar, toggles) }
            block()
        }

    private suspend fun ApplicationTestBuilder.postRisikovurdering(
        path: String,
        body: String,
    ) {
        val response =
            client.post(path) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun sendBehov(
        fødselsnummer: String,
        organisasjonsnummer: String,
    ): JsonNode {
        testRapid.sendTestMessage(
            """
            {
                "@event_name" : "behov",
                "@behov" : [ "Risikovurdering" ],
                "@id" : "${UUID.randomUUID()}",
                "@opprettet" : "2020-05-18T00:00:00",
                "vedtaksperiodeId" : "${UUID.randomUUID()}",
                "fødselsnummer" : "$fødselsnummer",
                "Risikovurdering" : {
                    "organisasjonsnummer" : "$organisasjonsnummer"
                }
            }
            """,
        )
        val løsning = testRapid.inspektør.løsning("Risikovurdering")
        testRapid.reset()
        return løsning
    }

    private fun JsonNode.assertOk() {
        assertEquals(true, this["kanGodkjennesAutomatisk"].booleanValue())
        assertEquals(0, this["funn"].size())
    }

    private fun JsonNode.assertIkkeOk(forventetBeskrivelse: String) {
        assertEquals(false, this["kanGodkjennesAutomatisk"].booleanValue())
        assertEquals(1, this["funn"].size())
        assertEquals(forventetBeskrivelse, this["funn"][0]["beskrivelse"].asString())
    }

    private fun ok() =
        """
        {
            "kanGodkjennesAutomatisk": true,
            "funn": [],
            "kontrollertOk": []
        }
        """

    private fun ikkeOk(beskrivelse: String) =
        """
        {
            "kanGodkjennesAutomatisk": false,
            "funn": [ { "kategori": [], "beskrivelse": "$beskrivelse" } ],
            "kontrollertOk": []
        }
        """
}
