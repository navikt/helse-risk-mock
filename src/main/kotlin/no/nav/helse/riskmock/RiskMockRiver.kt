package no.nav.helse.riskmock

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory

internal class RiskMockRiver(
    private val rapidsConnection: RapidsConnection
) : River.PacketListener {

    private val log = LoggerFactory.getLogger("RiskMockRiver")
    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

    companion object {
        const val behov = "Risikovurdering"
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandAll("@behov", listOf(behov)) }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("fødselsnummer") }
            validate { it.requireKey("vedtaksperiodeId") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: RapidsConnection.MessageContext) {
        sikkerlogg.error("forstod ikke $behov med melding\n${problems.toExtendedReport()}")
    }

    data class Risikovurdering(
        val samletScore: Double,
        val begrunnelser: List<String>,
        val ufullstendig: Boolean,
        val begrunnelserSomAleneKreverManuellBehandling: List<String>
    )

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        sikkerlogg.info("mottok melding: ${packet.toJson()}")
        log.info("besvarer behov for risikovurdering på vedtaksperiode: {}", packet["vedtaksperiodeId"].textValue())
        val risikovurdering = Risikovurdering(
            samletScore = 10.0,
            begrunnelser = emptyList(),
            ufullstendig = false,
            begrunnelserSomAleneKreverManuellBehandling = emptyList()
        )
        packet["@løsning"] = mapOf(
            behov to objectMapper.convertValue(risikovurdering, ObjectNode::class.java)
        )
        rapidsConnection.publish(packet.toJson())
    }
}
