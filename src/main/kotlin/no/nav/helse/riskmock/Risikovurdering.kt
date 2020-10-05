package no.nav.helse.riskmock

data class Risikovurdering(
    val samletScore: Double,
    val begrunnelser: List<String>,
    val ufullstendig: Boolean,
    val begrunnelserSomAleneKreverManuellBehandling: List<String>
)
