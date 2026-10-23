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
    private val svar: MockedeRisikovurderinger,
    private val toggles: Toggles = Toggles(),
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
                    it.requireKey("Risikovurdering.organisasjonsnummer")
                    it.interestedIn("vedtaksperiodeId")
                    it.interestedIn("Risikovurdering.vedtaksperiodeId")
                    it.interestedIn("Risikovurdering.perioderMedSammeSkjæringstidspunkt")
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
        val organisasjonsnummer = packet["$BEHOV.organisasjonsnummer"].asString()
        val risikovurdering = finnRisikovurdering(fødselsnummer, organisasjonsnummer)
        val løsning =
            if (toggles.svarMedAndreVedtaksperioder) {
                risikovurdering.tilLøsning() + ("andreVedtaksperioder" to andreVedtaksperioder(packet, fødselsnummer))
            } else {
                risikovurdering.tilLøsning()
            }
        packet["@løsning"] = mapOf(BEHOV to løsning)
        context.publish(packet.toJson())
    }

    private fun andreVedtaksperioder(
        packet: JsonMessage,
        fødselsnummer: String,
    ): List<Map<String, Any>> {
        val egenVedtaksperiodeId =
            packet["$BEHOV.vedtaksperiodeId"].takeUnless { it.isMissingNode || it.isNull }?.asString()
                ?: packet["vedtaksperiodeId"].takeUnless { it.isMissingNode || it.isNull }?.asString()
        return packet["$BEHOV.perioderMedSammeSkjæringstidspunkt"]
            .filter { it.path("vedtaksperiodeId").isString }
            .filter { it["vedtaksperiodeId"].asString() != egenVedtaksperiodeId }
            .distinctBy { it["vedtaksperiodeId"].asString() }
            .map { periode ->
                val vedtaksperiodeId = periode["vedtaksperiodeId"].asString()
                val organisasjonsnummer = periode.path("organisasjonsnummer").takeIf { it.isString }?.asString()
                mapOf("vedtaksperiodeId" to vedtaksperiodeId) +
                    finnRisikovurdering(fødselsnummer, organisasjonsnummer).tilLøsning()
            }.also { log.info("Svarer ut risikovurdering for {} andre vedtaksperioder", it.size) }
    }

    private fun finnRisikovurdering(
        fødselsnummer: String,
        organisasjonsnummer: String?,
    ): Risikovurdering =
        svar.finn(fødselsnummer, organisasjonsnummer) ?: Risikovurdering(
            kanGodkjennesAutomatisk = true,
            funn = emptyList(),
            kontrollertOk = emptyList(),
        ).also { log.info("Fant ikke forhåndskonfigurert risikovurdering. Defaulter til en som er OK!") }

    private fun Risikovurdering.tilLøsning(): Map<String, Any> =
        mapOf(
            "kanGodkjennesAutomatisk" to kanGodkjennesAutomatisk,
            "funn" to funn,
            "kontrollertOk" to kontrollertOk,
        )
}
