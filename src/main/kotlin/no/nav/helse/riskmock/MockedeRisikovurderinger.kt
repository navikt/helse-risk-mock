package no.nav.helse.riskmock

import java.util.concurrent.ConcurrentHashMap

internal class MockedeRisikovurderinger {
    private val forFødselsnummer = ConcurrentHashMap<String, Risikovurdering>()
    private val forFødselsnummerOgOrganisasjonsnummer = ConcurrentHashMap<Pair<String, String>, Risikovurdering>()

    fun leggTil(
        fødselsnummer: String,
        risikovurdering: Risikovurdering,
    ) {
        forFødselsnummer[fødselsnummer] = risikovurdering
    }

    fun leggTil(
        fødselsnummer: String,
        organisasjonsnummer: String,
        risikovurdering: Risikovurdering,
    ) {
        forFødselsnummerOgOrganisasjonsnummer[fødselsnummer to organisasjonsnummer] = risikovurdering
    }

    /** Svar for fnr + orgnr har forrang foran svar som kun er satt for fnr. */
    fun finn(
        fødselsnummer: String,
        organisasjonsnummer: String?,
    ): Risikovurdering? =
        organisasjonsnummer?.let { forFødselsnummerOgOrganisasjonsnummer[fødselsnummer to it] }
            ?: forFødselsnummer[fødselsnummer]

    fun fjern(fødselsnummer: String) {
        forFødselsnummer.remove(fødselsnummer)
        forFødselsnummerOgOrganisasjonsnummer.keys.removeIf { (fnr, _) -> fnr == fødselsnummer }
    }

    fun fjernAlle() {
        forFødselsnummer.clear()
        forFødselsnummerOgOrganisasjonsnummer.clear()
    }
}
