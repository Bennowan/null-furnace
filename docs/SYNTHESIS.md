# Synthese-System (Flux Fuser)

Der Fuser hat zwei Betriebsarten, umschaltbar im Konfigurationsmenue: **Freie Synthese** (Standard fuer neue Bloecke) und **Klassik** (die vier Rezepte aus 0.3.0, damit
alte Welten und Items weiter funktionieren). Architektur und Begruendung: `MATERIAL-ARCHITECTURE.md`. Untersuchung des Fluessigkeits-/Partikelproblems: `FUSER-ANALYSE.md`.

## Freie Synthese - Bedienung
1. Items per Foerderband, Fluessigkeiten per Leitung zufuehren. Jede **neue Zutat belegt beim ersten Eintreffen einen von 11 Eingangsplaetzen** (3x4-Raster im Menue, 12. Feld = Ergebnis).
   1 bis 11 verschiedene Zutaten sind moeglich, nicht alle muessen belegt sein.
2. **Menge je Platz** (1-16 Teile, Standard 4) mit `-`/`+` einstellen: das ist das Mischungsverhaeltnis. 1 Teil = 1 Item bzw. 10 Einheiten Fluessigkeit.
3. Die Charge **startet automatisch, sobald jeder belegte Platz sein Limit erreicht hat**, oder per Knopf *Jetzt starten* mit dem, was gerade drin liegt.
4. Ungenutzte, nicht manuell eingestellte, leere Plaetze geben sich nach 10 s selbst frei (verirrte Items blockieren nichts); `x` gibt einen leeren Platz sofort frei.
5. Solange nichts laeuft, zeigt der Fuser eine **Vorschau** (Name, Klasse, Stabilitaet) der Mischung. Das Ergebnis geht an jedes angrenzende Foerderband oder direkt in einen Turm.
6. Flux wird nicht gespeichert: ohne ausreichend zugeteilten Flux (>= 50 % des Bedarfs) pausiert die Charge. Der Bedarf liegt bei 0,5-2,1 Flux/Tick (30-126/s); ein Stabilizer (1,0) reicht fuer einfache
   Mischungen, komplexe/harte Mischungen brauchen einen Accelerator (2,6).

## Wie aus der Zusammensetzung ein Material wird (`material/`)
**Identitaet.** Zutaten werden nach Schluessel sortiert und ihre Anteile auf 16 Teile quantisiert (Groesstrest, jede Zutat mind. 1 Teil). Signatur `c1;item:copper=12;item:lead=4` -> 64-Bit-Hash.
Reihenfolge, absolute Menge und Chargengroesse zaehlen nicht; 3:1 und 6:2 sind dasselbe Material. Ein bereits synthetisiertes Material kann als Zutat dienen (Schluessel `mat:<hash>`), so entstehen Ketten.

**Zutateneigenschaften** (alle 0..1, Temperatur -1..1) stammen aus den Engine-Feldern jedes Items/jeder Fluessigkeit (Entflammbarkeit, Explosivitaet, Radioaktivitaet, Ladung, Temperatur, Viskositaet,
Waermekapazitaet ...). Fuer bekannte Vanilla-Items ergaenzt eine kleine Konstantentabelle Dichte, Haerte, Leitfaehigkeit und Flux-Affinitaet (`IngredientTraits`) - **keine Rezeptliste**.
Modded Items werden vollstaendig aus ihren Engine-Feldern abgeleitet.

**Mischregeln** (Anteile s_i, Diversitaet D = 1 - sum s_i^2):
| Wert | Regel |
|---|---|
| Dichte | Mittel x (1 + 0,06 D) (dichtere Packung) |
| Haerte | Mittel x (1 + 0,30 D) (Legierungsbonus) |
| Leitfaehigkeit | Mittel x (0,35 + 0,65 x Perkolation): erst ab genug leitfaehigem Anteil (15-45 %) bildet sich ein leitendes Netz |
| Temperatur, Waermekapazitaet, Brennbarkeit, Explosivitaet, Strahlung, Flux-Affinitaet, Viskositaet, Farbe | gewichtetes Mittel |
| Reaktionswaerme | 1,10 x Temperaturunterschiede + 2,40 x (Brennstoff trifft Hitze) + 0,40 x Unterschiede der Reaktivitaet + 0,50 x Explosivitaet + 0,35 x Strahlung + 0,10 x Brennbarkeit |
| Spitzenwaerme | Reaktionswaerme / (0,30 + 0,95 x Waermekapazitaet + 0,55 x Kaelteanteil) - **Verduennung und Kuehlmittel senken sie** |

**Materialklasse** (Anzeige und Geschoss): kinetisch, elektrisch, thermisch, explosiv, Flux - jeweils aus den Mischwerten (z. B. elektrisch = Leitfaehigkeit, Flux = Flux-Affinitaet und Strahlung).
Primaer- und Sekundaerklasse bestimmen die Form der Wirkung (siehe unten); Hybride entstehen von selbst.

## Stabilitaet und gefaehrliche Reaktionen
Stufen nach Spitzenwaerme `P`: **stabil** < 0,55 <= **instabil** < 0,85 <= **kritisch** < 1,0 <= **durchgehend**. Die Stufe ist in Vorschau, Statuszeile und Item-Beschreibung sichtbar.
* Die **Temperatur** des Fusers (Balken "Temperatur", 0-100 %) steigt waehrend der Reaktion mit `Spitze x Fortschritt` und sinkt durch **passive Abstrahlung** (immer) und durch **Kuehlmaentel**. Wiederholte Chargen summieren sich deshalb auf
  (`ThermalModel`). Ab 100 % **laeuft die Reaktion durch**: die Charge geht verloren, es gibt eine echte Engine-Explosion (Staerke nach Chargengroesse, Brennbarkeit, Explosivitaet, Leitfaehigkeit), Schaden am Fuser,
  einen Schlag auf das Flux-Netz und **Verschleiss** (+35 %); danach faellt der Fuser in die Vorbereitung zurueck (nicht mehr scharf).
* Gegenmittel, die der Spieler wirklich hat: **Kuehlmaentel** (siehe unten), **Charge abbrechen** (Knopf), **Verduennen/Kuehlen in der Mischung** (z. B. Kryofluid als Zutat senkt die Spitze der Charge selbst), kleinere Chargen. Flux kappen haelt die Reaktion an, kuehlt aber nur langsam (passiv).
* **Verschleiss** (0-100 %, Balken) steigt nach instabilen/kritischen Chargen und Explosionen, faellt langsam wieder und verstaerkt die Reaktionswaerme um bis zu 50 %.
* Kritische Chargen liefern nur 55 % Ausbeute, instabile 85 %.
* Zufall wird **nicht** fuer die Entscheidung benutzt; das Ergebnis ist deterministisch aus Zusammensetzung, Verschleiss und Flux.
Beispiele (aus den Tests, nicht vorgeschrieben): Oel + Schlacke 1:1 laeuft durch (P ~ 2,1); dieselben Stoffe mit viel Kryofluid verduennt sind beherrschbar (P ~ 0,2); Kupfer + Wasser, Titan + Graphit + Sand, Silizium + Kupfer sind stabil.
In einer bewusst volatilen Zufallsstichprobe (20 000 Mischungen aus 22 Zutaten) waren 68 % stabil, 21 % instabil, 6 % kritisch, 5 % durchgehend.

## Kuehlung: der Fuser-Kuehlmantel (`blocks/FuserCooler`)
Ein eigener 2x2-Block (Kategorie *Produktion*, Serpulo- und Erekir-Variante), der **direkt an einen Flux Fuser** gesetzt und per Leitung mit **Wasser oder Kryofluid** versorgt wird.
* **Genau ein Ziel:** Beruehrt ein Mantel mehrere Fuser, kuehlt er nur den mit der kleinsten Position; das Ziel wird im Menue (und beim Platzieren als Vorschau) angezeigt. Ohne angrenzenden Fuser tut der Mantel nichts und wirkt nie auf andere Gebaeude.
* **Mehrfachkuehlung begrenzt:** Je Fuser zaehlen hoechstens **4** Kuehlmaentel (nach Position geordnet), die Gesamtkuehlung ist auf **36 %/s** gedeckelt (`ThermalModel.MAX_COOLING` = 0,006 Temperatur pro Tick). Weitere Maentel bleiben im Leerlauf.
* **Kuehlleistung aus echten Werten:** Guete = Waermekapazitaet x (1,2 - Temperatur) der Fluessigkeit; nur echte, nicht brennbare Kuehlfluessigkeiten werden angenommen (Wasser 0,28 -> ca. 6 %/s, Kryofluid 0,86 -> ca. 19 %/s je Mantel). Schlacke, Oel und andere werden abgewiesen.
* **Verbrauch nur bei Bedarf:** 0,1 Einheiten/Tick bei voller Leistung, und nur solange der Fuser warm ist (> 2 %). Ohne Kuehlmittel keine Wirkung.
* **Wasser und Kryofluid bleiben Synthesezutaten:** der Fuser nimmt sie weiter ueber seine Eingangsplaetze an; die Kuehlung ist ein getrenntes System mit eigener Zufuhr.
* **Grenzen:** eine einzelne extreme Charge (z. B. Spitze 3,0 in ~215 Ticks) uebersteigt jede Kuehlleistung und ueberhitzt auch mit voller Kuehlung; bei Dauerbelastung verzoegert volle Kuehlung die Explosion nur. Fuer mittlere Ueberlast reicht gute Kuehlung (im Test: dieselbe Charge explodiert ohne Kuehlung und mit einem Wasser-Mantel, ueberlebt zwei Kryofluid-Maentel).
* Die Kuehlung von **Stromgeneratoren** (Engine) wird nicht beruehrt: kein Engine-Code ausser den Kern-Bauarten wird veraendert, der Mantel wirkt nur auf Flux Fuser.

## Vorbereiten und automatischer Betrieb (Fuser, freie Synthese)
Das staendige Entfernen und Setzen von Flux Nodes ist nicht mehr noetig. Der Fuser hat zwei Betriebsarten, umschaltbar mit **Automatischer Betrieb**:
* **Vorbereiten (neue Fuser starten so):** Zutaten und Mengen in Ruhe einrichten, die Vorschau zeigt Name/Klasse/Stabilitaet - **nichts startet von selbst**, die Flux-Verbindung bleibt bestehen. Der Knopf *Jetzt starten* loest bewusst eine einzelne Charge aus.
* **Betrieb (scharf):** Jede vollstaendige Mischung wird automatisch verarbeitet - **ohne weiteren Befehl, Charge fuer Charge**. Voraussetzungen: alle belegten Plaetze voll, **Ruhezeit** (1 s nach der letzten Aenderung an Zutaten/Mengen), freier Ausgang, freier Materialplatz im Archiv, **genug Flux im Netz**, Temperatur im sicheren Bereich.
* **Laufende Charge ist gesperrt:** Sie hat ihre Zutaten und Zusammensetzung beim Start uebernommen; Aenderungen bereiten nur die naechste Mischung vor.
* **Temperaturgrenzen:** Auto-Start nur, wenn `Temperatur + 0,6 x Spitze <= 95 %` und die Temperatur unter 80 % liegt; **riskante Mischungen** (z. B. Oel + Schlacke) starten nie automatisch, sondern nur per *Jetzt starten* bei kaltem Fuser (<= 35 %). Nach einer Durchreaktion schaltet sich der Betrieb aus.
* **Zustandsanzeige:** VORBEREITUNG, RUHEZEIT, BEREIT, AKTIV, KUEHLT AB, UEBERHITZT (plus Hinweis *gekuehlt* mit Zahl der Maentel und Leistung), jeweils mit erklaerendem Text.
* Alte Spielstaende (Format v2) starten im Betrieb (so liefen sie vorher); neu gesetzte Fuser im Vorbereitungsmodus.

## Synthetische Materialien und der Kern (`synth/MaterialGuard`)
Synthetische Materialien nehmen **Kerne nicht an** (`acceptItem`, `acceptStack`, `getMaximumAccepted`, `handleItem`, `handleStack` der Kernbauart); damit sind Foerderbaender, Bruecken, Entlader, Fuser-Ausgabe,
Einheitenfracht und das manuelle Ablegen des Spielers gleichermassen abgedeckt, und mit dem Kern verbundene Behaelter/Tresore reichen die Pruefung weiter. Reguelaere Items und alle anderen Gebaeude
(unverbundene Behaelter, Turm, Fuser ...) sind nicht betroffen. Abgewiesenes Material bleibt dort, wo es ist (z. B. auf dem Band); bereits im Kern liegendes Material (aelterer Stand) bleibt erhalten und ist entnehmbar.
Kerne mit eigener Bauart anderer Mods werden nicht veraendert (Log-Hinweis).

## Munition im Turm (Phase 2, `ShotSpec` + `synth/MatBullet`)
Das Turmprofil (Flux pro Tick 0,6-6,0, Risiko 0,02-0,36, Schuesse pro Item 2-10) folgt aus dem Profil und liegt im Bereich der festen Modi. Die Klasse bestimmt Form und Bewegung:
| Klasse | Projektil | Bewegung | Wirkung |
|---|---|---|---|
| kinetisch | spitzer Penetrator | gerade; schwer = langsamer | Durchschlag und Panzerbruch ab Haerte, Rueckstoss nach Dichte, Splitter bei sproeden/dichten Mischungen |
| elektrisch | flackernder Lichtbogen | sehr schnell, schlaengelnd | bis 7 Kettenblitze, Elektrisierung, stoert Schutzschilde, mehr Gebaeudeschaden |
| thermisch | pulsierende Glutkugel | langsam, bremst ab | Flaechenfeuer, Brennen/Schmelzen, Nachbrennzone |
| explosiv | polygonale Granate | Artillerie (Bogen, nur Boden) | grosser Flaechenschaden, Splitterring, Druckwelle und Bildschirmwackeln |
| Flux | rotierende Raute mit Ring | Spiralbahn, leichte Zielsuche | durchdringt Gebaeude und Rüstung, bremst und **zieht** Ziele zum Einschlag |
Die **Sekundaerklasse** ergaenzt Module (z. B. kinetisch + elektrisch = durchschlagende Slugs mit Kettenblitzen). Kalte Mischungen (Kaelteanteil) frieren Ziele ein, leitfaehige stoeren Schilde unabhaengig von der Klasse.
Instabile Zusammensetzungen belasten das Flux-Netz **bei jedem Schuss** zusaetzlich. Nicht genutzt: Beschleunigung (`accel` bleibt 0); Bewegungsvielfalt entsteht aus Schlaengeln, Spirale, Zielsuche und Luftwiderstand.

**Performance:** pro Slot gibt es feste Geschoss-/Splitter-/Zonentypen (keine Neuerzeugung), 10 statische Effekte, Spuren <= 16, Splitter <= 12, Blitze <= 10.

## Kampfeffekte (Flug, Einschlag, Folgereaktionen)
Alle sichtbaren Effekte entstehen aus **reiner, getesteter Logik** (`material/`): `ReactionSignature` verdichtet Profil und Schusswerte zu Energie, Thermik, Elektrik, Instabilitaet, Sproedigkeit, Fluessigkeitsanteil, Feld, Dichte, Kaelte und Komplexitaet;
daraus bauen `FlightPlan` und `ImpactPlan` das Flugbild und den mehrstufigen Einschlag. Gezeichnet wird ueber eine kleine `Canvas`-Schnittstelle (Engine: `ArcCanvas`; Test: Aufzeichnungs-Canvas).
* **Flug:** pulsierender Kern (Frequenz/Amplitude nach Energie), **Flackern bei Instabilitaet**, Halo bei hoher Energie, **1-3 Spurschichten** (bei Leitfaehigkeit/Instabilitaet/Feld **geflochten**), abgeloeste Partikel nach Materialart
  (Glut, Splitter, Tropfen, Funken, Feldpartikel), **Energiebaender** bei komplexen Mischungen, Feldbogen bei Flux, Entladungen entlang der Bahn nur bei echter Blitzwirkung. Alles analytisch aus Zeit und Geschoss-ID - keine Partikellisten.
* **Einschlag in 4 Phasen** (jeder Einschlag ist EIN Effekt, der den Plan zeitlich abspielt): **Kontakt** (Blitz, Funkenstrahlen), **Primaerreaktion** (Schockring, Feuerball, verzweigte Entladungen, Splitterwolke, Implosion + Feldbogen, Tropfen/Dampf, Frostspitzen),
  **Folgereaktionen** (Echos), **Nachwirkung** (Glut, Rauch, Hitzezone). Einfache Materialien haben wenige Phasen, komplexe mehrere; die Zusammenstellung der Phasenarten unterscheidet die Reaktionsfamilien (nicht nur die Farbe).
* **Darstellung = Mechanik:** Der Schockring hat genau den Radius des echten Flaechenschadens; Blitze zeigen nie mehr Aeste als echte Blitze; die Hitzezone entspricht der echten Nachbrennzone.
* **Folgereaktionen sind echt:** Energiereiche, instabile Mischungen erzeugen bis zu 3 zeitversetzte **Echo-Geschosse** (`MatEcho`) mit echter Wirkung: Folgeexplosion (Flaechenschaden, 25 % der Primaerwirkung) oder Entladung (Blitze, bei Leitfaehigkeit + Instabilitaet).
  Sie loesen **einmal je Geschoss beim ersten Treffer** aus (auch bei Durchschlag); ohne ausgeloeste Echos werden auch keine Folgephasen gezeichnet.
* **Performance:** 3 statische Einschlags-Effekte (Dauerstufen 34/64/104 Ticks), keine Allokation pro Bild; Primitivgrenzen (Einschlag <= 170 / 100 / 60 je Stufe, Flug <= 75 / 50 / 16; gemessene Spitze 59 bzw. 43);
  **Lastbudget** (`FxBudget`) vereinfacht Einschlaege automatisch bei vielen gleichzeitigen Reaktionen (volle -> reduzierte -> minimale Stufe) und erholt sich danach; Flugbilder stufen sich nach der Zahl aktiver Geschosse ab.
* **Behobener Fehler:** Detonationen am Flugzeitende (Artillerie, Blitz-Geschosse) laufen in Mindustry 160.7 ueber `hit(Bullet, x, y, boolean)`; zuvor wurde nur die dreistellige Variante abgefangen, daher fehlten Effekt und Nachbrennzone bei Granaten und Boegen.

## Icons und Fuser-Grafik
Jedes Material erhaelt ein **aus dem Profil gezeichnetes 32x32-Icon** (reproduzierbar, `IconPainter`): Form nach Klasse (Kristall, Barren, Leiterplatte, Glutbrocken, Kapsel mit Warnband, Flux-Kristall mit Ring),
Einschluesse in den Farben der einzelnen Zutaten, Schichten bei hoher Diversitaet. Der Fuser zeigt Fluessigkeitsstand in der Mischfarbe, kreisende Zutatenpunkte, die Farbe des entstehenden Materials, Funken nach Reaktivitaet,
einen Warnring bei drohendem Durchgehen und einen kalten Ring bei aktiver Kuehlung - alles **innerhalb der Kammer**.

## Aufbau (Klassik-Modus)
* **Flux Fuser** (3×3, Kategorie *Produktion*): nimmt feste Zutaten (Förderband), Flüssigkeiten (Leitung) und **aktuell verfügbaren Flux** (Flux Node) an.
  Rezept wird im Block-Menü gewählt. Flux wird nicht gespeichert: Produktion läuft nur bei zugeteiltem Flux ≥ 50 % des Bedarfs, sonst **pausiert** sie (Fortschritt bleibt).
* **Rezepte** (`production/FluxRecipes.java`) – modular: ein Eintrag = Ergebnis, Zutaten (Serpulo/Erekir), flüssige Zutat, optionales Kühlmittel, Dauer, Flux, Risiko.
  Mehrstufige Ketten entstehen, indem ein Ergebnis Zutat eines weiteren Rezepts wird.
* **Ergebnis** verlässt den Fuser über jedes angrenzende Förderband **oder direkt in einen angrenzenden Turm**. Produktion und Munitionsverbrauch sind getrennte Systeme.

## Klassik-Rezepte (unveraendert aus 0.3.0)
| Ergebnis | Serpulo-Zutaten | Erekir-Zutaten | Flüssigkeit (Zutat) | Kühlmittel (optional) | Zeit | Flux/s | Risiko |
|---|---|---|---|---|---|---|---|
| Carbon Conductor | 2 Kupfer + 2 Kohle | 2 Beryllium + 2 Graphit | Wasser 6/s (Abschrecken) | Kryofluid: ×1,4 schneller, Risiko ×0,55 | 2 s | 54 | 5 % |
| Ferrite Composite | 3 Schrott + 2 Graphit | 3 Sand + 2 Graphit | Öl 4,8/s (Bindemittel) | Wasser: ×1,2, Risiko ×0,8 | 2,5 s | 72 | 8 % |
| Synaptic Alloy | 2 Silizium + 2 Kupfer | 2 Silizium + 2 Beryllium | Kryofluid 3,6/s (unterkühltes Gitter) | Wasser: ×1,2, Risiko ×0,65 | 3 s | 108 | 30 % |
| Pyroglass (×2) | 2 Kohle + 2 Silizium | 2 Sand + 2 Silizium | Schlacke 7,2/s (heißes Medium) | Wasser: ×1,2, Risiko ×0,8 | 2,5 s | 84 | 22 % |

## Flüssigkeiten mit echter Funktion
* **Zutat**: ohne die Flüssigkeit keine Produktion (Wasser, Öl, Kryofluid, Schlacke).
* **Kühlung der Reaktion** im Fuser: schneller und weniger Risiko für das Flux-Netz.
* **Kühlung der Konverter**: Wasser/Kryofluid senken den Instabilitäts-Zielwert (×0,80 / ×0,55).
* Es wurden keine neuen Flüssigkeitstypen hinzugefügt (kein klarer Nutzen).

## Synthetische Items und Munition (alle nur mit Flux)
| Item | Erscheinung | Turm-Verhalten | Flux/s | Risiko |
|---|---|---|---|---|
| Carbon Conductor | schwarze Stäbe mit Kupferringen | schnelle Schüsse, 4 Kettenblitze, durchschlägt 2 Ziele, +35 % Schaden an Gebäuden | 60 | 6 % |
| Ferrite Composite | rostbraune Sechseck-Platte | schwerer Panzerbrecher, ignoriert Rüstung, durchschlägt 2, 5 Splitter | 90 | 10 % |
| Synaptic Alloy | violettes Netzwerk-Gitter | starke Zielsuche, gibt im Flug Mini-Raketen ab | 120 | 16 % |
| Pyroglass | orange Kristallsplitter | Flächenschaden + Feuer, zerspringt in 8 durchschlagende, brennende Scherben | 144 | 20 % |
