package no.nav.helse.riskmock

import com.fasterxml.jackson.databind.JsonNode

data class Risikovurdering(
    val kanGodkjennesAutomatisk:  Boolean,
    val funn: List<JsonNode>,
    val kontrollertOk: List<JsonNode>
)
