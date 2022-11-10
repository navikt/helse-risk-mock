# Riskmock
![Bygg og deploy](https://github.com/navikt/helse-sparkel-institusjonsopphold/workflows/Bygg%20og%20deploy/badge.svg)

## Beskrivelse
Risikovurdering er ikke fullverdig i testmiljøet fordi vi mocker dokumentene inn til Bømlo. Derfor har vi en mockapp
hvor vi kan styre svarene vi ønsker.

#### Bruk:
Appen holder på et in-memory map over ønsket svar på risikovurdering gitt et fødselsnummer. Dette kan endres med to apikall som nås via autoforward eller manuell port forward (boomer mode):
- `POST /reset`: Fjerner all eksisterende konfigurasjon
- `POST /reset-fnr/{fødselsnummer}`: Fjerner eksisterende konfigurasjon for gitt fødselsnummer
- `POST /risikovurdering/{fødselsnummer}`: Lagrer et ønsket svar på oppslag gitt et fødselsnummer.

Forventer en payload à la:
```json
{
    "kanGodkjennesAutomatisk": false,
    "funn": [
        {
            "kategori": [
                "8-4"
            ],
            "beskrivelse": "8-4 ikke ok",
            "kreverSupersaksbehandler": false
        }
    ],
    "kontrollertOk": [
        {
            "kategori": [
                "arbeid"
            ],
            "beskrivelse": "jobb ok"
        }
    ]
}
```

## Henvendelser
Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

### For NAV-ansatte
Interne henvendelser kan sendes via Slack i kanalen #område-helse.
