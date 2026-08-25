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

Voraussetzungen: JDK 17 und ein Android SDK (z. B. über Android Studio).
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
| HDMI-Umschalten reagiert nicht | Normal bei Beamern – siehe „HDMI-Eingang“ unten. |

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
automatisch alle 2,5 Sekunden. Sobald das HDMI-Bild auf der Leinwand erscheint, auf „Das war’s“
tippen; der Link wird gespeichert und liegt danach als **HDMI**-Taste auf der Fernbedienung.

Zwei Hilfen zur Eingrenzung:

- In den Einstellungen **„Laufende App anzeigen“** einschalten, neu verbinden und einmal mit der
  Original-Fernbedienung auf HDMI wechseln. Die App zeigt dann das zuständige Paket, und die
  passenden Varianten rutschen im Assistenten nach oben.
- Wer einen Rechner zur Hand hat, liest den exakten Link nach einem manuellen Wechsel aus:
  `adb shell dumpsys activity starter | grep passthrough` – und trägt ihn im Assistenten ein.

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
