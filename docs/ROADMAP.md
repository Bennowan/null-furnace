# Roadmap / Stand

Meilensteine laut Auftrag: A ✔, B ✔, C ✔, D ✔ (technisch, alle 7 Modi laufen im Headless-Test), E ✔ (Logik getestet),
F ◐ (Effekte/UI **implementiert, aber nie visuell gesehen**), G ◐ (Tests siehe `TESTING.md`; Gradle-Build und Spieltest durch den Autor (Bennowan) ausstehend).

Offen / Ideen:
1. Visuelle Abnahme im echten Spiel (Sprites, Effekte, Balken, Statuszeile, Node-Linien) und Korrekturen.
2. Balancing per Spieltest; Kosten und Forschung (aktuell `alwaysUnlocked`, kein Eintrag im Tech-Baum).
3. Eigene Sounds (aktuell vorhandene Spiel-Sounds als Platzhalter), eigene Sprites statt generierter Pixelgrafik.
4. Mehrspieler: Logik läuft serverseitig, aber Overclock-Status/Netzwerte werden nicht eigens an Clients synchronisiert → ungetestet.
5. Android-Build (`gradlew deploy` braucht Android-SDK/d8) – nicht versucht.
6. Netz-Visualisierung beim Platzieren (Vorschau-Linien), Logik-Sensoren (LAccess) für Flux-Werte.

## Stand 0.3.0 (Phase „Synthese“)
Erledigt: Instabilität repariert, Flux Fuser, Flüssigkeiten als Zutat/Kühlung, vier synthetische Items, Munition im Turm, Erekir-Varianten.
Offen: Spielgefühl/Balancing, visuelle Abnahme aller neuen Grafiken und Effekte, Rezept-Anzeige als echte Item-Icons im Stat-Menü (aktuell Text),
mehrstufige Ketten (Struktur vorhanden), Fuser-Sound, Tech-Baum-Integration.

## Stand 0.4.0 (Phasen "Freie Synthese" und "Individuelle Munition")
Erledigt und im echten Server getestet: Register/Persistenz, Synthese mit 1-11 Zutaten, Fluessigkeiten, Transport, Turm mit fuenf Materialklassen, Gefahrenreaktionen, Fuser-Fehler, Speichern/Laden.
Offen: **visuelle Abnahme** (Fuser-Zeichnung, Menue, Geschosse, Icons als Textur), Balancing, Mehrspieler, Gradle-/Windows-Build durch den Autor (Bennowan), eigener Sound; Slot-Obergrenze 48 (Erweiterung durch weitere Traeger moeglich, Namen werden angehaengt).
Ausdruecklich nicht Teil: ein eigener Planet (spaetere Erweiterung).
