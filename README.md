# Skillable Turret: Flux Protocol (Mindustry-Mod)

Java-Mod für **Mindustry v160.7** (Build gegen `dependencies.jar` v160.7, Java-17-Bytecode).

Inhalt: Flux Node, drei Konverter (Stabilizer, Accelerator, Quantum Converter), **Flux Fuser** (Synthese neuer Materialien),
Skillable Turret mit elf Munitions-Modi (7 Grundmaterialien + 4 synthetische), Instabilität/EMP/Netzkollaps, Overclock-Schlag.
Läuft auf Serpulo und Erekir. Details: `docs/` (neu: `docs/SYNTHESIS.md`).

## Bauen (Windows)

Voraussetzung: Internet (Gradle und die Mindustry-Abhängigkeit werden beim ersten Mal geladen). Dein `java` (Java 17) im PATH reicht.

1. Terminal im Projektordner öffnen (dort, wo `build.bat` liegt).
2. `build.bat`  → Ergebnis: `build\libs\flux-protocolDesktop.jar`
3. `install.bat` → kopiert die Datei nach `%AppData%\Mindustry\mods\flux-protocol.jar`
4. Mindustry neu starten. Unter *Mods* sollte „Skillable Turret: Flux Protocol“ erscheinen.

Ohne `install.bat`: Jar einfach selbst nach `%AppData%\Mindustry\mods` kopieren.

**Falls Gradle Probleme macht (Plan B ohne Gradle):** `build-manual.bat` → `build\manual\flux-protocol.jar`
(braucht ein JDK 17 mit `javac` und `jar` im PATH; `where javac` muss etwas finden).

**Bei Fehlern:** die komplette Ausgabe des Terminals kopieren und mir schicken. Bei Spielproblemen zusätzlich die Datei
`%AppData%\Mindustry\last_log.txt` schicken.

## Spielen / Testen

Der Mod schaltet seine Blöcke automatisch frei (`alwaysUnlocked`). Im Bau-Menü:
* Kategorie **Strom**: Flux Node, Flux Stabilizer, Flux Accelerator, Quantum Converter
* Kategorie **Türme**: Skillable Turret

Schnelltest in einer Sandbox-/Custom-Map:
1. Stromquelle (z. B. Solar/Verbrennungsgenerator oder im Sandbox die *Power Source*) **direkt neben oder per Strom-Node** an einen Konverter.
2. Flux Node in Reichweite (12 Kacheln) von Konverter **und** Turm platzieren. Beim Platzieren verbindet er sich automatisch;
   sonst Node anklicken und das Gebäude antippen (Link an/aus).
3. Kupfer in den Turm legen (per Förderband oder Hand). Den Turm anklicken → Statuszeile erklärt, warum er (nicht) schießt.
4. Ein Stabilizer liefert nur ~60 Flux/s. Starke Modi brauchen mehr Konverter (siehe `docs/BALANCING.md`).

Synthese-Schnelltest (freie Synthese, Standard):
1. Flux Fuser (Kategorie *Produktion*) neben den Turm setzen (er gibt das Ergebnis direkt an den Turm weiter, oder über ein Förderband) und per Flux Node mit einem **Accelerator** verbinden (komplexe Mischungen brauchen mehr als 1,0 Flux/Tick).
2. Zutaten per Förderband, Flüssigkeiten per Leitung zuführen. Jede neue Zutat belegt einen von 11 Plätzen; im Fuser-Menü (3×4-Raster) mit `-`/`+` die Menge je Platz = Mischungsverhältnis einstellen.
3. Sind alle belegten Plätze voll, startet die Charge (oder *Jetzt starten*). Die Statuszeile zeigt eine **Vorschau** mit Stabilität, danach den Fortschritt. Ohne genug Flux pausiert der Fuser.
4. **Gefahr:** Steigt die *Reaktionswärme* Richtung 100 %, läuft die Reaktion durch und der Fuser explodiert. Flux kappen lässt die Wärme sinken, *Charge abbrechen* lässt ab, Verdünnen/Kühlen (z. B. Kryofluid in der Mischung) verhindert es.
5. Das Ergebnis ist ein eigenes Material (Name, Icon, Beschreibung mit Zusammensetzung); es lässt sich per Förderband, Sortierer, Kern und Behälter bewegen und als Turm-Munition nutzen. Details: `docs/SYNTHESIS.md`.
6. Klassik-Rezepte der Version 0.3.0: im Fuser-Menü auf *Klassik-Rezepte* umschalten.
4. Instabilität: am Konverter steht der Zielwert samt Ursachen; Wasser/Kryofluid in den Konverter leiten senkt das Risiko.

Mindest-Erfüllung: Der Turm schießt nur, wenn mindestens 50 % des angeforderten Flux in diesem Moment zugeteilt sind.

## Tests

* `tools\run-tests.bat` – 48 Tests der Flux-Logik und 82 Tests der Materialsynthese, spielunabhängig (nur JDK nötig).
* `tools/run-integration.sh` – Entwickler-Werkzeug (Linux): lädt den Mod in einen echten Mindustry-v160.7-Headless-Server und prüft 292 Fälle (bisherige Funktionen).
* `tools/run-integration-materials.sh` – dasselbe für die freie Synthese (Register, Fuser, Transport, Turm, Speichern/Laden); mit `MATTEST_SECTIONS` teilbar.
  Ergebnisse und **ausdrücklich nicht getestete Teile (Grafik, Mehrspieler, Gradle)**: `docs/TESTING.md`.

## Projektstruktur

```
src/skillable/flux     Flux-Logik (Solver, Netz, Manager, Instabilität) – größtenteils ohne Engine-Abhängigkeit
src/skillable/blocks   FluxNode, FluxConverter, FluxFuser, SkillableTurret, OverclockTargeting
src/skillable/production Klassik-Rezepte (FluxRecipes) des Flux Fusers
src/skillable/material Materialsynthese ohne Engine-Abhängigkeit: Zusammensetzung, Profil, Schusswerte, Icon-Maler (voll unit-getestet)
src/skillable/synth    Engine-Anbindung: Träger-Items, Register in den Weltregeln, Geschosse, Effekte, dynamische Turm-Modi
src/skillable/modes    sieben Modus-Profile + Geschosse
src/skillable/fx       Effekte und Farben
src/skillable/content  Block-Registrierung (Kosten = Platzhalter)
assets/                Sprites (generiert: tools/make_sprites.py) und Übersetzungen (EN/DE)
test/ tools/ docs/     Tests, Werkzeuge, Dokumentation
```
