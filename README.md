# Riskmock

![Bygg og deploy](https://github.com/navikt/helse-sparkel-institusjonsopphold/workflows/Bygg%20og%20deploy/badge.svg)

## Beskrivelse

Risikovurdering er ikke fullverdig i testmiljøet fordi vi mocker dokumentene inn til Team SAS. Derfor har vi en mockapp
hvor vi kan styre svarene vi ønsker.

#### Bruk:

Appen holder på et in-memory map over ønskede svar på risikovurdering for et fødselsnummer. Dette kan endres med API-kall via port forwarding:

-   `POST /reset`: Fjerner all eksisterende konfigurasjon
-   `POST /reset-fnr/{fødselsnummer}`: Fjerner eksisterende konfigurasjon for gitt fødselsnummer
-   `POST /risikovurdering/{fødselsnummer}`: Lagrer et ønsket svar på oppslag for et fødselsnummer.
-   `POST /risikovurdering/{fødselsnummer}/{orgnr}`: Lagrer et ønsket svar på oppslag for et fødselsnummer + orgnr.

Forventer en payload à la:

```json
{
    "kanGodkjennesAutomatisk": false,
    "funn": [
        {
            "kategori": [],
            "beskrivelse": "Nytt arbeidsforhold registrert (04.03.2024)"
        }
    ],
    "kontrollertOk": [
        {
            "beskrivelse": "Arbeidsgiver er konkurs eller under avvikling",
            "kategori": []
        },
        { "beskrivelse": "Nyregistrert rolle i næringslivet", "kategori": [] },
        {
            "beskrivelse": "Innmeldt inntekt fra ny(e) arbeidsgiver(e)",
            "kategori": []
        },
        {
            "beskrivelse": "Inntektsøkning på mer enn 5000 i annet arbeidsforhold",
            "kategori": []
        }
    ]
}
```

Eksempelvis:

```shell
k port-forward risk-mock-849df5786-5zzf9 8080:8080
curl localhost:8080/risikovurdering/15478308253 -H "Content-Type: application/json" -v -d '{"kanGodkjennesAutomatisk": false, "funn": [{"kategori": [], "beskrivelse": "Nytt arbeidsforhold registrert (04.03.2024)"}], "kontrollertOk": [{"beskrivelse": "Arbeidsgiver er konkurs eller under avvikling", "kategori": []}, {"beskrivelse": "Nyregistrert rolle i næringslivet", "kategori": []}, {"beskrivelse": "Innmeldt inntekt fra ny(e) arbeidsgiver(e)", "kategori": []}, {"beskrivelse": "Inntektsøkning på mer enn 5000 i annet arbeidsforhold", "kategori": []}]}'
```

### Risikovurdering av parallelle vedtaksperioder ("andreVedtaksperioder")

Spesialist sender med `perioderMedSammeSkjæringstidspunkt` i Risikovurdering-behovet. Når toggelen
`svarMedAndreVedtaksperioder` er på, svarer mocken i tillegg ut feltet `andreVedtaksperioder` i løsningen: én
risikovurdering per annen vedtaksperiode i `perioderMedSammeSkjæringstidspunkt` (egen vedtaksperiode er utelatt).

Svaret for hver periode slås opp på samme måte som for hovedperioden: først fnr + periodens orgnr, så kun fnr, og
ellers default OK (`kanGodkjennesAutomatisk: true`, ingen funn).

```json
"@løsning": {
  "Risikovurdering": {
    "kanGodkjennesAutomatisk": true, "funn": [], "kontrollertOk": [],
    "andreVedtaksperioder": [
      { "vedtaksperiodeId": "<uuid>", "kanGodkjennesAutomatisk": false, "funn": [ ... ], "kontrollertOk": [ ... ] }
    ]
  }
}
```

Når toggelen er av (default), svarer mocken akkurat som før, uten `andreVedtaksperioder`.

-   Startverdi settes med miljøvariabelen `SVAR_MED_ANDRE_VEDTAKSPERIODER` (`true`/`false`, default `false`).
-   `GET /toggle/andre-vedtaksperioder`: Viser gjeldende verdi.
-   `POST /toggle/andre-vedtaksperioder/på` eller `/av`: Skrur toggelen på/av uten redeploy (nullstilles ved restart).

```shell
curl -X POST localhost:8080/toggle/andre-vedtaksperioder/on
```

## Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

### For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen [#helseytelser](https://nav-it.slack.com/archives/CD1KVMPJ6).
