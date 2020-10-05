package no.nav.helse.riskmock

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle

@TestInstance(Lifecycle.PER_CLASS)
internal class RiskMockRiverTest {

    private val testrapid = TestRapid()

    init {
        RiskMockRiver(testrapid, mutableMapOf())
    }

    @Test
    fun `løser behov`() {
        testrapid.sendTestMessage(enkeltBehov())
        val løsning = testrapid.inspektør.løsning("Risikovurdering")
        assertFalse(løsning.isMissingOrNull())
        assertEquals(false, løsning["ufullstendig"].booleanValue())
        assertEquals(0, løsning["begrunnelserSomAleneKreverManuellBehandling"].size())
    }

    private fun enkeltBehov() =
        """
        {
            "@event_name" : "behov",
            "@behov" : [ "Risikovurdering" ],
            "@id" : "id",
            "@opprettet" : "2020-05-18",
            "vedtaksperiodeId" : "vedtaksperiodeId",
            "fødselsnummer" : "fnr"
        }
        """
}

fun TestRapid.RapidInspector.meldinger() =
    (0 until size).map { index -> message(index) }

fun TestRapid.RapidInspector.hendelser(type: String) =
    meldinger().filter { it.path("@event_name").asText() == type }

fun TestRapid.RapidInspector.løsning(behov: String) =
    hendelser("behov")
        .filter { it.hasNonNull("@løsning") }
        .last { it.path("@behov").map(JsonNode::asText).contains(behov) }
        .path("@løsning").path(behov)
