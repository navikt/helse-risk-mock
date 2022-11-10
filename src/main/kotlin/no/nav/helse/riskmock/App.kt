package no.nav.helse.riskmock

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.JacksonConverter
import io.ktor.request.ContentTransformationException
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.LoggerFactory

internal val objectMapper = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .registerModule(JavaTimeModule())


fun main() {
    val applicationBuilder = ApplicationBuilder()
    applicationBuilder.start()
}

private val log = LoggerFactory.getLogger("RiskMockApi")
private val svar = mutableMapOf<String, Risikovurdering>()

class ApplicationBuilder : RapidsConnection.StatusListener {
    private val rapidsConnection =
        RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(System.getenv())).withKtorModule {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter(objectMapper))
            }
            routing {
                post("/reset") {
                    log.info("Fjerner alle konfigurerte risikovurderinger")
                    svar.clear()
                    call.respond(HttpStatusCode.OK)
                }
                post("/reset-fnr/{fødselsnummer}") {
                    val fødselsnummer = call.parameters["fødselsnummer"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        "Requesten mangler fødselsnummer"
                    )
                    svar.remove(fødselsnummer)
                    log.info("Fjernet risikovurdering for fnr: ${fødselsnummer.substring(0, 4)}*******")
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
                    log.info("Oppdatererte mocket risikovurdering for fnr: ${fødselsnummer.substring(0, 4)}*******")
                    call.respond(HttpStatusCode.OK)
                }
            }
        }.build()

    init {
        rapidsConnection.register(this)
        RiskMockRiver(rapidsConnection, svar)
    }

    fun start() {
        rapidsConnection.start()
    }
}
