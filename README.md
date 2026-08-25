# Beamer Fernbedienung

Android-App, die einen Beamer mit **Google TV / Android TV (Chromecast built-in)** über das
WLAN fernsteuert – ohne Cloud, ohne Konto, direkt im lokalen Netz.

Die App spricht dasselbe Protokoll wie die offizielle Google-TV-Fernbedienung
(*Android TV Remote Control v2*, Ports 6466/6467). Sie muss also nichts am Beamer
installiert werden – der Dienst ist dort schon vorhanden.

## Funktionen

- **Suche im WLAN** – Geräte werden automatisch gefunden (mDNS), alternativ IP-Adresse eintippen
- **Koppeln mit 6-stelligem Code**, der auf dem Beamer erscheint (einmalig)
- **Steuerkreuz** mit OK, Zurück, Home, Suche – Pfeiltasten wiederholen beim Gedrückthalten
- **Lautstärke** lauter / leiser / stumm, wahlweise auch über die Lautstärkewippe des Handys
- **Wiedergabe**: Play/Pause, vor, zurück, spulen
- **Ein/Aus** (Standby)
- **App-Verknüpfungen**: YouTube, Netflix, Prime Video, Disney+, ARD, ZDF, Spotify, Play Store –
  eigene Verknüpfungen können hinzugefügt werden
- **HDMI-Eingang** mit eigenem Einrichtungs-Assistent (siehe unten)
- **Eigene Tasten**: Tastenfolgen aufnehmen und auf Knopfdruck abspielen
- **Direktzugriff über ADB** (optional): Bildschirme des Beamers direkt starten
- **Weitere Tasten** (Menü, Info, Programmführer, Einstellungen, Farbtasten …) und ein Feld für
  beliebige Android-Tastencodes

## APK herunterladen

Die APK wird von GitHub Actions gebaut:

1. Im Repository auf **Actions → „APK bauen“** gehen
2. Den letzten erfolgreichen Lauf öffnen
3. Unten unter **Artifacts** `beamer-fernbedienung-apk` herunterladen und entpacken
4. `beamer-fernbedienung.apk` auf das Handy kopieren und installieren
   (Android fragt dabei nach der Erlaubnis, Apps aus unbekannten Quellen zu installieren)

Bequemer fürs Handy ist ein Release mit Direktlink. Dafür einen Versions-Tag setzen:

```bash
git tag v1.0 && git push origin v1.0
```

Oder auf GitHub unter **Releases → Draft a new release**: bei „Choose a tag“ `v1.0`
eintippen, „Create new tag on publish“ wählen, als Target den Branch setzen und
veröffentlichen. Der Workflow baut die APK und hängt sie an das Release – der Link
lässt sich dann direkt auf dem Handy öffnen.

## Selbst bauen

```bash
./gradlew assembleRelease
# Ergebnis: app/build/outputs/apk/release/app-release.apk
```

Voraussetzungen: JDK 17 und ein Android SDK (z. B. über Android Studio). Mindestens Android 8.0
auf dem Handy.
Die APK ist mit dem Debug-Schlüssel signiert, damit sie ohne eigenen Keystore
installierbar ist – für den Play Store müsste ein eigener Signaturschlüssel eingerichtet werden.

## Einrichten

1. Beamer einschalten, Handy und Beamer im **gleichen WLAN**
2. App öffnen → der Beamer erscheint in der Liste
3. Auf **Koppeln** tippen → der Beamer zeigt einen 6-stelligen Code
4. Code eingeben → fertig. Ab jetzt verbindet sich die App beim Start automatisch.

## Wenn etwas nicht klappt

| Problem | Lösung |
| --- | --- |
| Beamer wird nicht gefunden | Gleiches WLAN? Manche Router blockieren mDNS zwischen Geräten („Client-Isolation“, Gäste-WLAN). Dann die IP-Adresse des Beamers direkt eintippen. |
| Koppeln schlägt fehl | Beamer muss eingeschaltet sein. Am Beamer unter *Einstellungen → Apps → Alle Apps anzeigen → Android TV Remote Service → Speicher → Daten löschen*, danach in der App *Kopplung zurücksetzen* und neu koppeln. |
| Verbindung bricht ab | Normal, wenn der Beamer in den Standby geht. Die App verbindet sich automatisch neu. |
| Eine App startet nicht | Die App muss auf dem Beamer installiert sein. Der Link lässt sich unter *Einstellungen → App-Verknüpfungen bearbeiten* anpassen. |
| HDMI-Umschalten reagiert nicht | Normal bei Beamern – siehe „HDMI-Eingang“ und „Eigene Tasten“ unten. |

## HDMI-Eingang

Ein Beamer mit Android TV ist kein Fernseher: Die HDMI-Tastencodes einer TV-Fernbedienung
(`KEYCODE_TV_INPUT_HDMI_1` und Verwandte) werden nur von echter TV-Firmware ausgewertet und
laufen auf Beamern und Streaming-Geräten ins Leere. Android TV öffnet einen Eingang stattdessen
über einen Link des TV-Input-Frameworks:

```
content://android.media.tv/passthrough/<Paket>/<Dienst>/<Kennung>
```

Paket, Dienst und Kennung hängen vom Chipsatz ab, zum Beispiel
`com.droidlogic.tvinput/.services.Hdmi1InputService/HW5` (Amlogic) oder
`com.mediatek.tvinput/.hdmi.HDMIInputService/HW5` (MediaTek). Über die Fernbedienungs-Verbindung
lässt sich das nicht abfragen, deshalb hat die App einen Assistenten:

**Fernbedienung → „HDMI einrichten“** probiert die bekannten Varianten durch – einzeln oder
automatisch. Sobald das HDMI-Bild auf der Leinwand erscheint, auf „Das war’s“ tippen; der Link
wird gespeichert und liegt danach als **HDMI**-Taste auf der Fernbedienung.

Kann der Beamer mit einem Link nichts anfangen, wirft sein Fernbedienungs-Dienst eine Ausnahme
und kappt die Verbindung. Die App verbindet sich deshalb nach jedem Versuch automatisch neu und
setzt die Suche fort. Genau dieses Verhalten ist zugleich die Trefferanzeige: **bleibt die
Verbindung nach einem Versuch bestehen, hat der Beamer den Link verstanden** – der Assistent
markiert solche Varianten und bietet sie direkt zum Speichern an.

Zwei Hilfen zur Eingrenzung:

- In den Einstellungen **„Laufende App anzeigen“** einschalten, neu verbinden und einmal mit der
  Original-Fernbedienung auf HDMI wechseln. Die App zeigt dann das zuständige Paket, und die
  passenden Varianten rutschen im Assistenten nach oben.
- Wer einen Rechner zur Hand hat, liest den exakten Link nach einem manuellen Wechsel aus:
  `adb shell dumpsys activity starter | grep passthrough` – und trägt ihn im Assistenten ein.

### Eigene Tasten (Tastenfolgen)

Bei vielen Beamern ist der HDMI-Eingang weder eine Taste noch ein Link, sondern nur ein Punkt in
der Oberfläche des Geräts – erreichbar allein über das Steuerkreuz. Für diesen Fall gibt es
**Eigene Tasten**: Die Navigation wird einmal aufgezeichnet und danach mit einem Tippen abgespielt.

Die Aufnahme startet automatisch auf dem Startbildschirm, damit der Ausgangspunkt beim Abspielen
derselbe ist. Das Tempo zwischen zwei Tasten lässt sich pro Folge einstellen (schnell / normal /
langsam), falls die Oberfläche des Beamers träge reagiert.

Das funktioniert für alles, was sich mit dem Steuerkreuz erreichen lässt – nicht nur für HDMI.

### Direktzugriff über ADB

Das Fernbedienungs-Protokoll kann nur fünf Dinge: Tasten senden, einen App-Link öffnen, Text
eintippen, Sprache übertragen, Lautstärke setzen. Es gibt keine Möglichkeit, eine bestimmte
Activity zu starten oder die App-Liste abzufragen. Genau das fehlt, wenn ein Bildschirm – etwa der
HDMI-Eingang – nur über die Oberfläche des Geräts erreichbar ist.

Android hat dafür eine offizielle Schnittstelle: **ADB**. Die App spricht das Protokoll selbst
(Bibliothek [`dadb`](https://github.com/mobile-dev-inc/dadb)) und braucht keinen Rechner.

Einrichtung am Beamer: *Einstellungen → Geräteeinstellungen → Info →* siebenmal auf **Build**
tippen, dann unter *Entwickleroptionen* **USB-Debugging** einschalten. Beim ersten Verbinden fragt
der Beamer auf der Leinwand nach Bestätigung des Schlüssels.

Danach unter **Einstellungen → Direktzugriff (ADB)**:

- **Laufenden Bildschirm auslesen** – den HDMI-Eingang am Beamer öffnen, hier tippen: Die App
  liest den Komponentennamen aus (`dumpsys`) und legt daraus eine Taste an, die ihn künftig direkt
  startet (`am start -n paket/activity`).
- **App-Liste auslesen** – alle installierten Apps; ein Tipp legt eine Starttaste an.
- **Eigener Befehl** – beliebiger Shell-Befehl mit Ausgabe.

Die angelegten Direktbefehle liegen als Tasten auf der Fernbedienung und arbeiten unabhängig von
der Fernbedienungs-Verbindung.

**Zur Sicherheit:** Solange USB-Debugging an ist, steht im WLAN ein Debug-Zugang offen. Fremde
Schlüssel muss der Beamer zwar bestätigen, aber der Port ist erreichbar. Wer das nicht dauerhaft
möchte, schaltet es nach dem Einrichten wieder aus – die aufgezeichneten Tastenfolgen laufen
weiter, nur die ADB-Tasten brauchen den Zugang.

Wegen ADB liegt die Mindestanforderung bei **Android 8.0**.

## Technischer Aufbau

| Datei | Inhalt |
| --- | --- |
| `proto/Proto.kt` | kleiner Protobuf-Encoder/Decoder plus Rahmenformat (Varint-Länge + Nachricht) |
| `net/ClientIdentity.kt` | einmalig erzeugtes RSA-Client-Zertifikat im PKCS12-Speicher der App |
| `net/Tls.kt` | TLS-Verbindung mit Client-Zertifikat; das selbstsignierte Zertifikat des Beamers wird akzeptiert, die Absicherung passiert über das Pairing-Geheimnis |
| `net/Pairing.kt` | Kopplung auf Port 6467 (Request → Options → Configuration → Secret) |
| `net/RemoteClient.kt` | Steuerverbindung auf Port 6466 (Konfiguration, Ping/Pong, Tasten, App-Links, Lautstärke) |
| `net/Discovery.kt` | Gerätesuche per mDNS (`_androidtvremote2._tcp`) |
| `RemoteViewModel.kt` | Verbindungsverwaltung inkl. automatischem Wiederverbinden |
| `data/HdmiInputs.kt` | bekannte Passthrough-Links der Chipsatz-Familien für den HDMI-Assistenten |
| `data/Macro.kt` | aufgezeichnete Tastenfolgen und ADB-Direktbefehle samt Speicherformat |
| `net/AdbSession.kt` | ADB über WLAN: Verbindung, Shell-Befehle, laufende Activity, App-Liste |
| `ui/` | Oberfläche mit Jetpack Compose (Material 3) |

### Protokolltest ohne Beamer

Unter `tools/protokolltest/` liegt ein End-zu-End-Test: Ein Mock-Beamer in Python spricht
das Protokoll mit den **Original-Protobuf-Dateien** und der offiziellen protobuf-Laufzeit,
der echte Kotlin-Client koppelt sich dagegen und sendet Tasten. Damit wird der
handgeschriebene Codec gegen eine unabhängige Implementierung geprüft:

```bash
tools/protokolltest/run.sh
```

Geprüft werden: TLS mit Client-Zertifikat, der komplette Pairing-Ablauf inklusive
SHA-256-Geheimnis, die Konfigurations- und Ping-Nachrichten sowie Tastendrücke,
HDMI-Umschaltung, App-Links und das Auslesen der Lautstärke.

Das Protokoll ist nachgebaut nach den offenen Implementierungen
[tronikos/androidtvremote2](https://github.com/tronikos/androidtvremote2) und
[louis49/androidtv-remote](https://github.com/louis49/androidtv-remote).
