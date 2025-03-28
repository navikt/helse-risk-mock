package no.nav.helse.riskmock

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.rapids_rivers.RapidApplication
import org.slf4j.LoggerFactory
import kotlin.collections.set

internal val objectMapper = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .registerModule(JavaTimeModule())


fun main() {
    val applicationBuilder = ApplicationBuilder()
    applicationBuilder.start()
}

private val logger = LoggerFactory.getLogger("RiskMockApi")
private val svar = mutableMapOf<String, Risikovurdering>()

class ApplicationBuilder : RapidsConnection.StatusListener {
    private val rapidsConnection = RapidApplication.create(env = System.getenv(), builder = {
        withKtorModule {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter(objectMapper))
            }
            routing {
                post("/reset") {
                    logger.info("Fjerner alle konfigurerte risikovurderinger")
                    svar.clear()
                    call.respond(HttpStatusCode.OK)
                }
                post("/reset-fnr/{fødselsnummer}") {
                    val fødselsnummer = call.parameters["fødselsnummer"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        "Requesten mangler fødselsnummer"
                    )
                    svar.remove(fødselsnummer)
                    logger.info("Fjernet risikovurdering for fnr: ${fødselsnummer.substring(0, 4)}*******")
                    call.respond(HttpStatusCode.OK)
                }
                post("/risikovurdering/{fødselsnummer}") {
                    val fødselsnummer = call.parameters["fødselsnummer"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        "Requesten mangler fødselsnummer"
                    )

                    val risikovurdering = try {
                        call.receive<Risikovurdering>()
                    } catch (e: ContentTransformationException) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Kunne ikke parse payload")
                    }
                    svar[fødselsnummer] = risikovurdering
                    logger.info("Oppdatererte mocket risikovurdering for fnr: ${fødselsnummer.substring(0, 4)}*******")
                    call.respond(HttpStatusCode.OK)
                }
            }
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
