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
- **Eingang umschalten**: HDMI 1–4 und „Quelle“
- **Weitere Tasten** (Menü, Info, Programmführer, Einstellungen, Farbtasten …) und ein Feld für
  beliebige Android-Tastencodes

## APK herunterladen

Die APK wird von GitHub Actions gebaut:

1. Im Repository auf **Actions → „APK bauen“** gehen
2. Den letzten erfolgreichen Lauf öffnen
3. Unten unter **Artifacts** `beamer-fernbedienung-apk` herunterladen und entpacken
4. `beamer-fernbedienung.apk` auf das Handy kopieren und installieren
   (Android fragt dabei nach der Erlaubnis, Apps aus unbekannten Quellen zu installieren)

Alternativ mit einem Tag ein Release erzeugen – die APK hängt dann direkt am Release:

```bash
git tag v1.0 && git push origin v1.0
```

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
| HDMI-Umschalten reagiert nicht | Nicht jeder Beamer nimmt die HDMI-Tastencodes an. Dann „Quelle“ benutzen und mit dem Steuerkreuz auswählen. |

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
| `ui/` | Oberfläche mit Jetpack Compose (Material 3) |

Das Protokoll ist nachgebaut nach den offenen Implementierungen
[tronikos/androidtvremote2](https://github.com/tronikos/androidtvremote2) und
[louis49/androidtv-remote](https://github.com/louis49/androidtv-remote).
