package no.nav.helse.riskmock

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory

internal class RiskMockRiver(
    rapidsConnection: RapidsConnection,
    private val svar: Map<String, Risikovurdering>,
) : River.PacketListener {
    private val log = LoggerFactory.getLogger("RiskMockRiver")
    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

    companion object {
        const val BEHOV = "Risikovurdering"
    }

    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireAll("@behov", listOf(BEHOV))
                    it.forbid("@løsning")
                }
                validate {
                    it.requireKey("@id")
                    it.requireKey("fødselsnummer")
                }
            }.register(this)
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        sikkerlogg.error("forstod ikke $BEHOV med melding\n${problems.toExtendedReport()}")
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        sikkerlogg.info("mottok melding: ${packet.toJson()}")
        log.info("besvarer behov for risikovurdering på id: {}", packet["@id"].asString())
        val fødselsnummer = packet["fødselsnummer"].asString()
        val risikovurdering =
            svar.getOrElse(fødselsnummer) {
                Risikovurdering(
                    kanGodkjennesAutomatisk = true,
                    funn = emptyList(),
                    kontrollertOk = emptyList(),
                ).also { log.info("Fant ikke forhåndskonfigurert risikovurdering. Defaulter til en som er OK!") }
            }
        packet["@løsning"] = mapOf(BEHOV to risikovurdering)
        context.publish(packet.toJson())
    }
}
