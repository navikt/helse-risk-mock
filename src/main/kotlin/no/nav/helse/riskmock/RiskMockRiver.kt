package no.nav.helse.riskmock

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory

internal class RiskMockRiver(
    rapidsConnection: RapidsConnection,
    private val svar: Map<String, Risikovurdering>
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
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerlogg.error("forstod ikke $behov med melding\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        sikkerlogg.info("mottok melding: ${packet.toJson()}")
        log.info("besvarer behov for risikovurdering på id: {}", packet["@id"].textValue())
        val fødselsnummer = packet["fødselsnummer"].asText()
        val risikovurdering = svar.getOrElse(fødselsnummer) {
            Risikovurdering(
                kanGodkjennesAutomatisk = true,
                funn = emptyList(),
                kontrollertOk = emptyList()
            ).also { log.info("Fant ikke forhåndskonfigurert risikovurdering. Defaulter til en som er OK!") }
        }
        packet["@løsning"] = mapOf(
            behov to objectMapper.convertValue(risikovurdering, ObjectNode::class.java)
        )
        context.publish(packet.toJson())
    }
}
