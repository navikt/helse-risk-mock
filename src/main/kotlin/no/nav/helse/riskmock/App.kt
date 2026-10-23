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

    private val rapidsConnection =
        RapidApplication.create(env = System.getenv(), builder = {
            withKtorModule {
                riskMockApi(svar)
            }
        })

    init {
        rapidsConnection.register(this)
        RiskMockRiver(rapidsConnection, svar)
    }

    fun start() {
        rapidsConnection.start()
    }
}

internal fun Application.riskMockApi(svar: MockedeRisikovurderinger) {
    install(ContentNegotiation) {
        jackson()
    }
    routing {
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
