# punktgenau

Eine schlanke Android-App für Tischtennis-Spielerinnen und -Spieler in Deutschland. Sie zeigt TTR-Werte, rechnet TTR-Änderungen, zeigt Spieltage und Spielberichte und behält die Anmeldung dauerhaft.

Die App ist ein privates Projekt und **nicht mit mytischtennis.de verbunden**. Sie nutzt die öffentlichen Seiten und Daten der Website und das Konto der angemeldeten Person. Sie prüft keinen Abo-Status und umgeht keine Sperren oder Verifizierungen.

## Funktionen
- Anmeldung im eingebauten Browserfenster, die nach dem Neustart bleibt. Das Passwort wird nicht gespeichert.
- TTR-Rechner für mehrere Spiele, mit Änderung und Gegner aus der Meldeliste.
- TTR-Verlauf als Liste und Diagramm.
- Spielersuche, auch erweitert nach Geschlecht, TTR-Bereich und Verein.
- Liga: Tabelle, Spielplan, Bilanz, Aufstellung und TTR-Rangliste.
- Verein: Kontakt, Spiellokale mit Navigation, Mannschaften.
- Spieltag: Spiele der eigenen und gemerkten Mannschaften, mit Spielbericht.
- Spielbericht in der App, mit Einzeln, Sätzen und Summen.
- Wechselliste.

## Voraussetzungen
- JDK 17
- Android SDK mit Plattform 37 und Build-Tools 37.0.0
- Gradle 9.9.0

Den Pfad zum SDK trägst du in `android/local.properties` ein, z. B.:

```
sdk.dir=C\:/Pfad/zum/Android/Sdk
```

Die Datei gehört nicht ins Git und wird von Android Studio beim Öffnen des Projekts angelegt.

## Bauen und testen

```bash
cd android
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

Die Debug-APK liegt danach unter `android/app/build/outputs/apk/debug/app-debug.apk`.

Auf dem Gerät: USB-Debugging einschalten, das Gerät am Rechner autorisieren, dann

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## Aufbau
```
android/app/src/main/kotlin/app/punktgenau/
  domain/   TTR-Rechner (reines Kotlin)
  data/     Parser und Repositories (Profil, Spieler, Mannschaften, Spielbericht)
    session/  Cookie-Speicher, Verschlüsselung, Sitzungsverwaltung
  ui/       Compose-Screens
```

## Sicherheit und Datenschutz
- Das Passwort wird nie gespeichert. Die Anmeldung läuft im Browserfenster der Website.
- Die Sitzungs-Cookies liegen verschlüsselt (AES-GCM, Schlüssel im Android Keystore). Die Datensicherung ist abgeschaltet.
- Keine Analytics, keine Crash-Dienste, keine Werbung.
- Es werden nur Cookies von `mytischtennis.de` angenommen.

## Bekannte Grenzen
- Die Feldnamen der Website-Antworten sind nur am eigenen Konto geprüft. Ändert die Website ihr Format, zeigt die App eine "nicht lesbar"-Meldung.
- Spieltag und Spielbericht laden beim Öffnen bzw. über "Aktualisieren". Live-Stände gibt es noch nicht.
- Es gibt nur einen Debug-Build, ohne Release-Schlüssel.
- mySpieltag (Premium) ist nicht umgesetzt.

## Lizenz
Noch nicht festgelegt.
