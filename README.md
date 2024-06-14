# Riskmock
![Bygg og deploy](https://github.com/navikt/helse-sparkel-institusjonsopphold/workflows/Bygg%20og%20deploy/badge.svg)

## Beskrivelse
Risikovurdering er ikke fullverdig i testmiljøet fordi vi mocker dokumentene inn til Bømlo. Derfor har vi en mockapp
hvor vi kan styre svarene vi ønsker.

#### Bruk:
Appen holder på et in-memory map over ønskede svar på risikovurdering for et fødselsnummer. Dette kan endres med API-kall via port forwarding:
- `POST /reset`: Fjerner all eksisterende konfigurasjon
- `POST /reset-fnr/{fødselsnummer}`: Fjerner eksisterende konfigurasjon for gitt fødselsnummer
- `POST /risikovurdering/{fødselsnummer}`: Lagrer et ønsket svar på oppslag for et fødselsnummer.

Forventer en payload à la:
```json
{
    "kanGodkjennesAutomatisk": false,
    "funn": [
        {
            "kategori": [],
            "beskrivelse": "Nytt arbeidsforhold registrert (04.03.2024)",
            "kreverSupersaksbehandler": true
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

Eksempelvis:
```shell
k port-forward risk-mock-849df5786-5zzf9 8080:8080
curl localhost:8080/risikovurdering/15478308253 -H "Content-Type: application/json" -v -d '{"kanGodkjennesAutomatisk": false, "funn": [{"kategori": [], "beskrivelse": "Nytt arbeidsforhold registrert (04.03.2024)", "kreverSupersaksbehandler": true}], "kontrollertOk": [{"beskrivelse": "Arbeidsgiver er konkurs eller under avvikling", "kategori": []}, {"beskrivelse": "Nyregistrert rolle i næringslivet", "kategori": []}, {"beskrivelse": "Innmeldt inntekt fra ny(e) arbeidsgiver(e)", "kategori": []}, {"beskrivelse": "Inntektsøkning på mer enn 5000 i annet arbeidsforhold", "kategori": []}]}'
```

## Henvendelser
Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

### For NAV-ansatte
Interne henvendelser kan sendes via Slack i kanalen #område-helse.
