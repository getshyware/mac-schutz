# MAC-Schutz – APK bauen und testen

## APK bauen (Android Studio)
1. ZIP entpacken, z. B. nach `Dokumente/MacSchutz`.
2. Android Studio öffnen → **File → Open** → den Ordner `MacSchutz` wählen (den Ordner, in dem `settings.gradle` liegt) → **OK**.
3. Falls gefragt wird „Trust Project?“ → **Trust Project**.
4. Warten, bis unten rechts die Fortschrittsanzeige weg ist (Gradle Sync, beim ersten Mal ein paar Minuten).
   - Fragt Android Studio nach fehlendem **SDK 36** → auf den blauen Link **Install** klicken.
   - Bietet es ein **AGP-Upgrade** an → einfach ignorieren / schließen.
5. Menü **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
6. Unten rechts erscheint „APK(s) generated successfully“ → auf **locate** klicken.
   Die Datei heißt `app-debug.apk` (liegt in `app/build/outputs/apk/debug/`).

## Auf dem Handy installieren
1. `app-debug.apk` aufs Handy bringen (USB-Kabel, Google Drive, Mail an dich selbst …).
2. Datei antippen → Installation aus unbekannter Quelle einmalig erlauben → **Installieren**.
   (Play Protect warnt evtl., weil die App unbekannt ist → „Trotzdem installieren“.)

## Testen (Android 12 oder neuer)
1. App öffnen → **Aktuelles WLAN übernehmen** → Standort-Erlaubnis erlauben.
2. WLAN-Passwort eintippen → **Schützen**.
3. **WLAN-Einstellungen öffnen** → dein WLAN → **Entfernen** (den alten, normalen Eintrag).
4. Kommt die Benachrichtigung „Vorgeschlagene WLANs zulassen?“ → **Ja / Zulassen**.
5. Das Handy verbindet sich von selbst wieder.
6. Prüfen: WLAN-Einstellungen → Zahnrad beim WLAN → MAC-Adresse notieren.
   WLAN aus- und wieder einschalten → die MAC-Adresse ist jetzt eine andere.

## Gut zu wissen
- Kein Root nötig. Kein Internetzugriff, keine Werbung, kein Tracking.
- Wird ein WLAN in der App entfernt, musst du dich danach normal neu mit dem WLAN verbinden.
- Wer die Frage „Vorgeschlagene WLANs zulassen?“ mit „Nein“ beantwortet hat:
  App deinstallieren, neu installieren, dann „Ja“ wählen.
- Wechselnde MAC-Adressen können in manchen Netzen stören (z. B. Router mit
  MAC-Filter, Kindersicherung am Router, Hotel-WLAN mit Anmeldeseite).
  Dann das WLAN in der App einfach wieder entfernen.
