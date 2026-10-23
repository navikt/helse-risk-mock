package no.nav.helse.riskmock

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.http.*
import io.ktor.serialization.jackson3.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.rapids_rivers.RapidApplication
import org.slf4j.LoggerFactory

fun main() {
    val applicationBuilder = ApplicationBuilder()
    applicationBuilder.start()
}

private val logger = LoggerFactory.getLogger("RiskMockApi")

class ApplicationBuilder : RapidsConnection.StatusListener {
    private val svar = MockedeRisikovurderinger()
    private val toggles = Toggles.fraEnv(System.getenv())

    private val rapidsConnection =
        RapidApplication.create(env = System.getenv(), builder = {
            withKtorModule {
                riskMockApi(svar, toggles)
            }
        })

    init {
        rapidsConnection.register(this)
        RiskMockRiver(rapidsConnection, svar, toggles)
        logger.info("Starter med svarMedAndreVedtaksperioder=${toggles.svarMedAndreVedtaksperioder}")
    }

    fun start() {
        rapidsConnection.start()
    }
}

internal fun Application.riskMockApi(
    svar: MockedeRisikovurderinger,
    toggles: Toggles = Toggles(),
) {
    install(ContentNegotiation) {
        jackson()
    }
    routing {
        get("/toggle/andre-vedtaksperioder") {
            call.respondText(toggles.svarMedAndreVedtaksperioder.toString())
        }
        post("/toggle/andre-vedtaksperioder/{verdi}") {
            val verdi =
                when (call.parameters["verdi"]) {
                    "på", "on", "true" -> true
                    "av", "off", "false" -> false
                    else -> return@post call.respond(HttpStatusCode.BadRequest, "Bruk på/av")
                }
            toggles.svarMedAndreVedtaksperioder = verdi
            logger.info("Satte svarMedAndreVedtaksperioder=$verdi")
            call.respondText(verdi.toString())
        }
        post("/reset") {
            logger.info("Fjerner alle konfigurerte risikovurderinger")
            svar.fjernAlle()
            call.respond(HttpStatusCode.OK)
        }
        post("/reset-fnr/{fødselsnummer}") {
            val fødselsnummer =
                call.parameters["fødselsnummer"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    "Requesten mangler fødselsnummer",
                )
            svar.fjern(fødselsnummer)
            logger.info("Fjernet risikovurdering for fnr: ${fødselsnummer.maskert()}")
            call.respond(HttpStatusCode.OK)
        }
        post("/risikovurdering/{fødselsnummer}") {
            val fødselsnummer =
                call.parameters["fødselsnummer"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    "Requesten mangler fødselsnummer",
                )
            val risikovurdering =
                try {
                    call.receive<Risikovurdering>()
                } catch (e: ContentTransformationException) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Kunne ikke parse payload")
                }
            svar.leggTil(fødselsnummer, risikovurdering)
            logger.info("Oppdatererte mocket risikovurdering for fnr: ${fødselsnummer.maskert()}")
            call.respond(HttpStatusCode.OK)
        }
        post("/risikovurdering/{fødselsnummer}/{organisasjonsnummer}") {
            val fødselsnummer =
                call.parameters["fødselsnummer"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    "Requesten mangler fødselsnummer",
                )
            val organisasjonsnummer =
                call.parameters["organisasjonsnummer"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    "Requesten mangler organisasjonsnummer",
                )
            val risikovurdering =
                try {
                    call.receive<Risikovurdering>()
                } catch (e: ContentTransformationException) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Kunne ikke parse payload")
                }
            svar.leggTil(fødselsnummer, organisasjonsnummer, risikovurdering)
            logger.info(
                "Oppdatererte mocket risikovurdering for fnr: ${fødselsnummer.maskert()} og orgnr: $organisasjonsnummer",
            )
            call.respond(HttpStatusCode.OK)
        }
    }
}

private fun String.maskert() = "${take(4)}*******"
