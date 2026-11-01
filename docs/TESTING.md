# Tests und Abnahme

Ehrlicher Stand: **Der Mod wurde nie in der grafischen Spieloberfläche gestartet.** Geprüft wurde Logik (Unit-Tests) und das Verhalten
in einem echten **Mindustry-v160.7-Headless-Server** (lädt den Mod-Jar, führt echte Spiellogik aus). Nicht geprüft: alles, was Grafik/Eingabe braucht.

## Werkzeuge
* `tools/run-tests.bat|sh` – 48 Prüfungen (`test/skillable/FluxTests.java`), laufen ohne Mindustry.
* `tools/run-integration.sh` – baut den Mod, lädt ihn zusammen mit `tools/selftest` (Test-Mod) in `server-release.jar` v160.7:
  erzeugt eine Welt, platziert echte Blöcke, simuliert Ticks, spawnt Gegner. 132 Prüfungen, **alle bestanden**.

## Abnahmefälle (Auftrag Abschnitt 10)
| # | Fall | Status |
|---|---|---|
| 1 | Ohne Strom kein Flux | ✔ Unit + Integration (Stromquelle entfernt → Status 0, Turm 0 Flux, schießt nicht) |
| 2 | Ohne Flux-Verbindung kein Flux | ✔ Unit + Integration (Link getrennt → anderes Netz, 0 Flux, keine Schüsse) |
| 3 | Ohne ausreichenden Flux kein Schuss | ✔ Unit (Zuteilung < 50 %); im Spiel durch Gate in `updateShooting` und Reload-Faktor umgesetzt, Integration prüft nur den Voll-/Nullfall |
| 4 | Unverbrauchter Flux verfällt | ✔ Unit (10 s Leerlauf, danach nur aktueller Durchsatz) |
| 5 | Konverter/Nodes speichern keinen Flux | ✔ Unit (Feldprüfung + konstante Zuteilung über 1000 Ticks); Code-Review: kein Flux-Feld wächst |
| 6 | Getrennte Netze tauschen nichts | ✔ Unit + Integration |
| 7 | Konverterprofile | ✔ Unit (Durchsatz- und Instabilitätsreihenfolge) |
| 8 | Nur ein Material gleichzeitig | ✔ Integration (alle 6 anderen Materialien abgelehnt, Wechsel erst nach Entladen/Leerwerden) |
| 9 | Alle 7 Modi mit eigenem Verhalten | ✔ Integration: Schussrate, Schaden, Durchschlag (Rail/Reality), Brandzone, lenkende Raketen, Flächenschaden, Elektro/Kettenblitz. **Optik und Gefühl ungesehen** |
| 10 | Unterversorgung, Überlast, Kollaps, Wiederanlauf | ✔ Unit + Integration (Kollaps → EMP → Erholung → wieder Flux) |
| 11 | Overclock nur bei gültigen Bedingungen | ✔ Integration (ohne Material/Ziel/Abklingzeit/bei offenem Netz abgelehnt; gültig: Material −4, Treffer, EMP, Abklingzeit) |
| 12 | Speichern/Laden | ◐ Roundtrip über `write`/`read` der Gebäude getestet (Turm, Konverter, Node, abgelaufene Zustände nicht gespeichert). **Echte Spielstand-Datei nicht getestet** |
| 13 | Node entfernen hinterlässt keine ungültigen Links | ✔ Integration (Node entfernt, neuer Aufbau); ungültige Links werden beim Netzaufbau gelöscht |
| 14 | Rechenkosten | ◐ 500 Netze: ~0,4 ms/Tick (reine Logik). Kein Profiling im Spiel |
| 15 | Build enthält alle Ressourcen, installierbar | ◐ Jar (Klassen, Sprites, Bundles, `mod.hjson`, Icon) wird vom echten Server geladen. **Gradle-Build nicht ausgeführt** (Gradle war in der Testumgebung nicht ladbar); `build-manual.bat` und die Jar-Struktur wurden gleichwertig nachgebaut |

## Offene manuelle Prüfungen (bitte im Spiel)
1. Mod erscheint in der Mod-Liste, Blöcke und Sprites sichtbar, keine Fehlermeldung im Log.
2. Balken/Statuszeile am Turm und Konverter lesbar; Texte DE/EN.
3. Node-Linien: Farbe/Dicke ändern sich mit Last; bei EMP rot/gedimmt.
4. Warn-/Kritisch-Rückmeldung (Funken, Alarm) und Kollaps-Effekt wirken nicht übertrieben.
5. Overclock-Button, Strahl und Treffereffekt; Bildschirmshake zurückhaltend.
6. Spielstand speichern → Spiel verlassen → laden: Modus, Links, Instabilität erhalten.
7. Konverter-Strom über Strom-Nodes/Batterien in einem „echten“ Aufbau.
8. Performance mit vielen Netzen.

## Bekannte Risiken
* Render-Code (`draw`, Effekte, Teile `HaloPart`/`RegionPart`) ist nur kompiliert, nie gezeichnet worden.
* Sprites sind generierte Platzhalter.
* Mehrspieler nicht getestet.
* `Time.delta` wird auf 4 Ticks begrenzt; bei sehr niedrigen FPS läuft die Flux-Simulation dadurch langsamer als die Spielzeit.


## Nachtrag 0.3.0 (Instabilität, Fuser, Synthese)
**Unit-Tests (48, bestanden):** u. a. Stufen STABIL/WARNUNG/KRITISCH/KOLLAPS sind erreichbar, die Instabilität sinkt bei niedriger Last wieder,
Kühlung senkt den Zielwert, Reihenfolge Warnung < Kritisch < Kollaps mit mindestens 2 s Abstand.

**Server-Integrationstest (echtes Mindustry v160.7, ohne Grafik):**
* Instabilität steigt im Spiel messbar (Stufe WARNUNG nach ~380, KRITISCH ~630, KOLLAPS ~840 Ticks unter Überlast), EMP-Abschaltung, Erholung, Kryofluid-Kühlung wirkt.
* Fuser: nimmt nur Zutaten/Flüssigkeiten des Rezepts an, produziert nicht ohne Flüssigkeit, **pausiert ohne Flux-Verbindung**, stellt alle vier Items her
  (inkl. Kühlmittel-Pfad), speichert/lädt Rezept und Fortschritt, liefert das Ergebnis direkt an den Turm (Munition).
* Alle elf Modi feuern; Texte (Name/Beschreibung) für alle Blöcke, Items und Modi sind vorhanden.

**Weiterhin NICHT getestet (nur manuell möglich):** Grafik und Effekte (neue Item-Icons, Fuser-Sprite, Linien, Toasts, Ringe), Förderband-/Rohr-Anschluss
(im Test nur direkte Item-/Flüssigkeitsübergabe), Mehrspieler, Spielstand-Datei, Performance mit vielen Fusern, Gradle-Build der neuen Version (Gradle war hier nicht ladbar; zuvor bei Bennowan erfolgreich).


## Nachtrag 0.4.0 (freie Materialsynthese, Phase 1 + 2) - was wirklich ausgefuehrt wurde

Umgebung: Linux, **JDK 17.0.x** (`javac --release 17`), Mindustry **v160.7** (`dependencies.jar` und `server-release.jar` aus dem offiziellen Release, echter Headless-Server). Alle Zahlen stammen aus Laeufen gegen den ausgelieferten Stand.

| Ebene | Werkzeug | Ergebnis |
|---|---|---|
| Logik der Flux-Netze (unveraendert) | `tools/run-tests.sh` / `.bat` | **48 / 48** |
| Materialsynthese ohne Spiel (`material/`) | `tools/run-tests.sh` / `.bat` | **82 / 82** |
| Regression des bisherigen Mods im echten Server (Klassik-Fuser, Flux-Netz, Instabilitaet, 11 Turmmodi ...) | `tools/run-integration.sh` | **292 / 292** (identisch zur Baseline des unveraenderten Mods) |
| Neue Funktionen im echten Server | `tools/run-integration-materials.sh` | Lauf A (Register, Fuser, Gefahren): **88 / 88**; Lauf B (Turm, Leistung, Speichern/Laden): **101 / 101** (beide Laeufe enthalten dieselben Setup-Pruefungen) |

Der Material-Integrationstest ist in Abschnitte teilbar (`MATTEST_SECTIONS=C,E,F` bzw. `G,I`), weil ein Gesamtlauf ueber 5 Minuten dauert.

### Abnahmekriterien (Auftrag Abschnitt 13)
**Synthese**
* Eine Zutat (`c1;item:copper=16`), zwei Zutaten (Graphit 6 : Blei 2 -> Gewichte 12:4), fuenf/elf Zutaten, weniger als elf - Unit-Tests und echter Fuser. **Elf verschiedene Zutaten** (9 Items + 2 Fluessigkeiten) belegen alle Plaetze, eine zwoelfte wird abgelehnt, die Charge laeuft und liefert. Ebenfalls Pruefung: zwoelf Zutaten im Rechenkern abgelehnt.
* Feste Stoffe und Fluessigkeiten: Fluessigkeit ueber eine **echte Leitung**, wird als Fluessigkeit (nicht als Item) gefuehrt; reine Fluessigkeits-, Item-, Mischsynthesen.
* Mengenverhaeltnisse veraendern Identitaet und Eigenschaften; gleiches Verhaeltnis bei anderer Menge/Reihenfolge ergibt **denselben Traeger ohne neuen Slot** (Unit- und Integrationstest).
* Reproduzierbar: wiederholte Berechnung bitgleich; 30 000 Zufallsmischungen: keine Hash-Kollision, gleiche Signatur -> gleicher Hash; Quantisierung fasst aehnliche Mischungen zusammen (keine Flut).
* Keine Rezepttabelle: Eigenschaften aus Engine-Feldern; im Test arbeiten Items beider Welten und gemischte Fluessigkeiten ohne Sonderfall.

**Persistenz und Transport**
* Identitaet nach **echtem `SaveIO.save`/`SaveIO.load`** (Registerinhalt zuvor aus dem Speicher geloescht): gleiche Slots, gleiche Zusammensetzungen, Namen/Stoffwerte, Items auf Foerderband und im Behaelter, Turm-Munition samt Modus, Fuser-Plaetze mit Limits/Mengen, **Ausgabepuffer, laufende Charge mit Fortschritt**; die geladene Charge laeuft zu Ende (Flux-Netz wieder verbunden).
* Transport: Ergebnis laeuft ueber ein **echtes Foerderband** in den Turm; Turm erhaelt exakt dasselbe Material und seinen dynamischen Modus.
* Beschaedigte Daten: falsche Pruefsumme, Wert ausserhalb des Bereichs, Muell, ungueltiger Slot, doppelter Slot, unbekannte Formatversion, entfernte Zutat (gespeichertes Profil bleibt nutzbar) - kein Absturz, Slot wird inert und benannt, Turm/Fuser lehnen ihn ab.
* Abwaertskompatibilitaet: ein aus echten Fuser-Daten nachgebauter **Format-v1-Datenstrom** (Spielstand der Version 0.3.0) wird als Klassik-Fuser mit Rezept und Fortschritt gelesen.
* Slot-Wiederverwendung: 48 Materialien fuellen das Register; Voll -> nichts wird weggenommen; nach der Sperrzeit nur der **eine** Slot, dessen Item nirgends im Spiel liegt (47 in einem Behaelter geschuetzte Slots werden erkannt).

**Kampf und Reaktionen**
* Alle fuenf Klassen (aus echten Engine-Zutaten) feuern im echten Server, treffen und fliegen mit **ihrem eigenen Projektiltyp**; fuenf unterschiedliche Verhaltensprofile in den Geschosswerten (Blitze, Flaechenschaden, Artillerie, Durchdringung/Zielsuche/Unabschiessbarkeit, Brand/Bremsen, Schlaengeln, Geschwindigkeit 3,0-11,3).
* Flux-Bedarf beim Schiessen entspricht dem Profilwert; Instabilitaet steigt beim Schiessen (0,63-0,99 mit einem einzelnen Accelerator) und faellt zwischen den Faellen wieder unter 0,15; die Explosion eines durchgehenden Fusers hebt sie messbar an.
* Gefahr nachvollziehbar: Oel + Schlacke wird als durchgehend eingestuft, die Waerme steigt sichtbar, **ohne Flux faellt sie**, Abbrechen funktioniert, ungebremst **explodiert der Fuser** (Eigenschaden, Netzschlag, Verschleiss); dieselbe Mischung mit Kryofluid verduennt laeuft stabil durch.
* Leistung: 1152 gleichzeitige Geschosse (alle Formen, ohne Splitter) im Update **2,6 ms/Tick** in der Testumgebung; nach dem Auslaufen samt Splittern **kein Speicherwachstum** (1,8 MiB) und 0 uebrige Geschosse.

**Fuser und bestehende Mod:** siehe `FUSER-ANALYSE.md` (Ursache, Behebung, Reproduktion mit und ohne Fix). Klassik-Fuser, Flux-Gebaeude, Turm und Instabilitaet: 292/292 Regressionspruefungen.

### Instabilitaet (Abschnitt 10)
Die Instabilitaet wurde nicht neu entworfen, sondern ueberprueft und angebunden: das Zielwert-Modell aus 0.3.0 (48 Unit-Tests: Stufen erreichbar, Sinken bei Entlastung, Kuehlung) ist unveraendert gruen und im echten Server bestaetigt (Steigen unter Last, EMP, Erholung).
Neu angebunden: Fuser-Reaktionswaerme/Verschleiss (`riskTarget`), Materialrisiko im Turm, Schub pro Schuss bei instabilen Mischungen, Netzschlag bei Fuser-Explosion. Angezeigt wird der echte Wert (`network.maxInstability`), keine eigene Zahl.

### NICHT getestet / bekannte Luecken (ehrlich)
* **Keine Grafik:** Fuser-Zeichnung, Konfigurationsmenue (3x4-Raster, Knoepfe, Statustext), Geschossformen/-effekte, Treffereffekte, Item-Icons als **Textur im Spiel** (der Maler ist unit-getestet und als Kontaktbogen angesehen; Pixmap-/Texturaufbau in `MaterialVisuals` wurde nie ausgefuehrt). Alles ist kompiliert und wird nur logisch ausgefuehrt.
* **Kein Gradle-Lauf** und **kein Lauf der Windows-.bat-Skripte** (Linux-Umgebung). Der Quellcode kompiliert mit `javac --release 17` unter JDK 17; die Jar-Struktur wurde gleichwertig nachgebaut. `build.gradle.kts` und `build-manual.bat` wurden nur um UTF-8-Kodierung ergaenzt.
* **Mehrspieler:** Register-Sync ueber `Call.setRules` und feste Content-IDs sind so ausgelegt, aber nie mit einem echten Client getestet.
* Klassik-Fuser und neue Fuser-Optik wurden nicht von einem Menschen gesehen; die Behebung des Fluessigkeitsfehlers ist am Bytecode-Verhalten der Engine und per Reproduktion belegt, nicht per Bildschirm.
* Balancing (Flux-Bedarf, Ausbeute, Gefahrenschwellen) ist ein erster Entwurf; nur die Verteilung ueber Zufallsmischungen und Einzelfaelle wurde geprueft.
* Zwischenpuffer von Junctions/Bruecken zaehlen beim Schutz vor Slot-Neuvergabe nicht mit; deshalb die 60-s-Sperre.


## Nachtrag 0.5.0 (Kern-Sperre, Kuehlmantel, Vorbereiten/Betrieb, Kampfeffekte)

Umgebung wie oben: Linux, **JDK 17**, Mindustry **v160.7** (echter Headless-Server). Alle Zahlen stammen aus Laeufen gegen den ausgelieferten Quellstand.

| Ebene | Werkzeug | Ergebnis |
|---|---|---|
| Flux-Netze (unveraendert) | `tools/run-tests.sh` / `.bat` | **48 / 48** |
| Materialsynthese ohne Spiel | dito | **82 / 82** |
| Thermik, Zustaende, Effektlogik (neu) | dito | **67 / 67** |
| Regression des bisherigen Mods im echten Server | `tools/run-integration.sh` | **292 / 292** |
| Neue Funktionen im echten Server | `tools/run-integration-materials.sh` | Lauf A (`C,E,F`: Synthese, Gefahren): **89 / 89**; Lauf B (`C,G,I`: Turm, Leistung, Speichern/Laden): **117 / 117**; Lauf C (`C,K,H`: Kern-Sperre, Kuehlmantel, Vorbereiten/Betrieb, Direktschuss): **97 / 97** |

(`MATTEST_SECTIONS` waehlt Abschnitte; `C` ist immer noetig, jeder Lauf enthaelt die gemeinsamen Setup-Pruefungen.)

### Abnahmefaelle (Auftrag Abschnitt 6)
1. **Kern-Sperre:** Kern ist `GuardedCoreBuild`; `acceptItem`, `acceptStack` (manuelles Ablegen), `getMaximumAccepted` lehnen Material ab, reguelaere Items nicht; ein **Foerderband** liefert Material nicht in den Kern (alle 3 Stuecke bleiben auf dem Band), Kupfer vom Nachbarband kommt an; ein **mit dem Kern verbundener Behaelter** reicht die Sperre weiter; ein **Fuser neben dem Kern** gibt nichts ab (Ausgabepuffer bleibt); vorhandenes Material im Kern bleibt unveraendert; `handleItem`/`handleStack` ohne Pruefung tragen nichts ein; nur Kern-Bauarten wurden ersetzt. Nach echtem Speichern/Laden ist der Kern weiterhin abgesichert.
2. **Kuehlmantel beeinflusst die echte Temperatur:** ohne Kuehlmittel nur passive Abstrahlung (-0,08 in 200 Ticks), mit Wasser deutlich mehr, mit Kryofluid mehr als das Doppelte; kalter Fuser = kein Verbrauch; Verbrauch 0,1 Einheiten/Tick bei Bedarf.
3. **Ueberhitzung bleibt moeglich:** eine Ueberlast-Mischung (aus den echten Engine-Zutaten gesucht, Spitze 1,3-1,6, 14-16 Teile) **explodiert ohne Kuehlung und mit einem Wasser-Mantel und uebersteht zwei Kryofluid-Maentel**; die Unit-Tests belegen, dass eine einzelne extreme Charge (Spitze 3,0) auch bei maximaler Kuehlung explodiert und Dauerbelastung (Spitze 2,1) auch bei voller Kuehlung nach wenigen Chargen.
4. **Mehrere Fuser / Mehrfachkuehlung:** Beruehrt ein Mantel zwei Fuser, kuehlt er genau den mit der kleinsten Position (nur dieser faellt messbar, der andere nur passiv); fuenf Maentel an einem Fuser -> 5 angeschlossen, 4 wirksam, Gesamtkuehlung <= Obergrenze; Mantel ohne Fuser (nur Foerderband daneben) hat kein Ziel; ein angrenzender Generator wird nicht beeinflusst.
5. **Wasser und Kryofluid bleiben Zutaten** (Fuser nimmt sie neben dem Mantel weiter an); der Mantel akzeptiert nur echte Kuehlmittel (Schlacke/Oel nein).
6. **Stromgeneratoren:** Es wurde kein Engine-Code ausser den Kern-Bauarten angefasst (Test: kein anderer Block hat eine Bauart aus `MaterialGuard`); das **Verhalten** vanilla-Generatoren mit Kuehlmittel wurde **nicht** im Spiel nachgemessen.
7. **Vorbereiten/Betrieb:** eine komplette Mischung liegt 5 s im nicht scharfen Fuser, es startet nichts (Zustand VORBEREITUNG), die Flux-Verbindung bleibt dabei bestehen; Aenderungen verbrauchen keine Materialplaetze; nach dem Scharfschalten laeuft die Ruhezeit, jede weitere Aenderung verschiebt den Start; danach Start ohne weiteren Befehl; laufende Charge bleibt gesperrt (Zusammensetzung unveraendert trotz Aenderungen), die naechste verwendet die neue Mischung; **3 weitere Chargen im Dauerbetrieb ohne Eingriff an den Flux-Links**; bei 85 % Temperatur startet nichts (UEBERHITZT), nach dem Abkuehlen von selbst; ohne Flux verbraucht eine fertige Mischung nichts, mit Flux startet sie sofort; riskante Mischung (Oel + Schlacke) startet nie automatisch, nur nach Bestaetigung; Betriebszustand ueberlebt Speichern/Laden.
8. **Materialidentitaet/Synthese unveraendert:** Abschnitt A-F der bisherigen Tests laufen unveraendert durch (nur an die neue Startlogik angepasst); gleiche Mischung -> derselbe Traeger, kein neuer Slot.
9. **Kampfeffekte (Logik):** Plaene reproduzierbar; 422 Materialien x 3 Detailstufen x gesamte Lebensdauer x 2 Richtungen ohne NaN/Unendlich, Farben im Bereich, Geometrie in den angegebenen Grenzen, nach Ablauf wird nichts mehr gezeichnet; Primitive pro Bild begrenzt und abgestuft (Spitze Einschlag 59, Flug 43); Lastbudget vereinfacht und erholt sich; Schockring = echter Splash-Radius, Blitze <= echte Blitze, Folgephasen = echte Echos; komplexe Synthesen haben deutlich mehr Phasen (> einfache + 1,5), 14 verschiedene Phasenzusammenstellungen in 22 Referenzmaterialien.
10. **Kampfeffekte (Spiel):** alle fuenf Klassen treffen im Direktschuss und im Turm; **echte Echo-Geschosse** wurden beobachtet (Thermisch 37, Sprengmittel 30, Flux 32); Einschlagseffekte wurden angefordert (Lastbudget); Schadens-/Kollisionsmechanik bleibt korrekt (Treffer/Schaden je Klasse gemessen); 1152 gleichzeitige Geschosse 3,7 ms/Tick, kein Speicherwachstum, nach dem Ausklingen 0 Geschosse uebrig.

### Beim Testen gefundene und behobene Fehler
* **Einschlagslogik wurde bei Detonationen am Flugzeitende nie ausgefuehrt** (Artillerie und Blitz-Geschosse laufen in 160.7 ueber `hit(Bullet, x, y, boolean)`; es wurde nur die dreistellige Variante abgefangen). Bestand schon in 0.4.0 und erklaerte die unspektakulaeren Granaten-Einschlaege. Behoben.
* Echo-Reaktionen loesten bei Durchschlag-Geschossen nie aus, wenn nur ein Ziel getroffen wurde. Behoben (einmal je Geschoss beim ersten Treffer).
* Sehr volatile Mischungen (Spitze > 1,58) haetten mit der ersten Fassung der Startregel nie mehr starten koennen; jetzt nur noch per bewusster Bestaetigung bei kaltem Fuser.
* Zwei Kommentare mit Umlauten liessen den Build mit ASCII-Standardkodierung scheitern; der Quellcode ist wieder reines ASCII, `tools/check_public.py` prueft das kuenftig mit.

### NICHT getestet / bekannte Luecken (ehrlich)
* **Keine Grafik:** Die Zeichenlogik wurde gegen einen Aufzeichnungs-Canvas geprueft (keine ungueltigen Werte, Grenzen, Lebensdauer, Primitivzahlen), aber **nie auf einem Bildschirm gesehen**. Wie die Effekte tatsaechlich aussehen, Farbwahl und Wirkung im Gefecht muessen im Spiel beurteilt werden. Ebenso nicht gesehen: Fuser-Menue (Betriebsschalter, Statuszeilen), Kuehlmantel-Grafik im Spiel, Platzier-Vorschau des Kuehlmantels, Item-Icon-Texturen.
* **Nicht getestet:** Gradle-Build, die Windows-.bat-Skripte, Mehrspieler (Register-Sync, feste Content-IDs, Einschlags-Effekte auf Clients), Verhalten vanilla-Generatoren mit Kuehlmittel (nur: nicht veraendert).
* Balancing (Kuehlleistungen, Echo-Schaden 25 %, Schwellen der Startregel) ist ein erster Entwurf.
* Nicht-Standard-Kerne anderer Mods (eigene Bauart) werden nicht abgesichert; Material kann dort landen.
