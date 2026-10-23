package no.nav.helse.riskmock

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Når [svarMedAndreVedtaksperioder] er på, svarer mocken i tillegg ut "andreVedtaksperioder" i løsningen: én
 * risikovurdering for hver av de andre vedtaksperiodene i perioderMedSammeSkjæringstidspunkt. Når den er av, svarer
 * mocken akkurat som før.
 */
internal class Toggles(
    svarMedAndreVedtaksperioder: Boolean = false,
) {
    private val andreVedtaksperioder = AtomicBoolean(svarMedAndreVedtaksperioder)

    var svarMedAndreVedtaksperioder: Boolean
        get() = andreVedtaksperioder.get()
        set(value) = andreVedtaksperioder.set(value)

    companion object {
        const val SVAR_MED_ANDRE_VEDTAKSPERIODER_ENV = "SVAR_MED_ANDRE_VEDTAKSPERIODER"

        fun fraEnv(env: Map<String, String>) = Toggles(svarMedAndreVedtaksperioder = env[SVAR_MED_ANDRE_VEDTAKSPERIODER_ENV].toBoolean())
    }
}
