# Changelog

## 0.5.0 - Kuehlung, Reaktionssteuerung, Kern-Sperre, Kampfeffekte
* **Fuser-Kuehlmantel** (neuer Block, Serpulo + Erekir): kuehlt genau einen angrenzenden Fuser mit Wasser/Kryofluid, Verbrauch nur bei Bedarf, max. 4 wirksam je Fuser, gedeckelte Gesamtkuehlung; Menue zeigt Ziel, Status und Fuser-Temperatur; Platzier-Vorschau.
* **Fuser-Temperaturmodell:** passive Abstrahlung + Reaktionswaerme + Kuehlung (`ThermalModel`); wiederholte Chargen summieren sich bis zur Ueberhitzung. Warum Wasser/Kryofluid vorher nicht kuehlten: `docs/FUSER-ANALYSE.md`.
* **Vorbereiten und automatischer Betrieb:** Mischungen ohne Flux-Nodes zu entfernen einrichten; Betrieb schaltet Chargen automatisch (Ruhezeit, Flux, Temperatur, Ausgang); laufende Charge gesperrt; riskante Mischungen nur per Bestaetigung; Zustandsanzeige VORBEREITUNG/RUHEZEIT/BEREIT/AKTIV/KUEHLT AB/UEBERHITZT. Alte Staende laufen weiter automatisch.
* **Kern-Sperre:** synthetische Materialien gelangen auf keinem Weg in einen Kern (Band, Bruecke, Entlader, Fuser, Einheit, manuell, verbundene Behaelter); reguelaere Items unveraendert.
* **Kampfeffekte neu:** mehrschichtige, flackernde, geflochtene Flugbilder mit abgeloesten Partikeln und Energiebaendern; mehrstufiger Einschlag (Kontakt, Primaer, Folge, Nachwirkung) je Reaktionsfamilie; **echte Folgereaktionen** (Echo-Geschosse); Lastbudget mit abgestufter Komplexitaet; Zeichenlogik plattformunabhaengig und getestet.
* **Fehler behoben:** Einschlagslogik fehlte bei Detonationen am Flugzeitende (Granaten, Boegen; bestand seit 0.4.0); Echos bei Durchschlag; Build-Abbruch durch Umlaute in Kommentaren.
* **Veroeffentlichung:** `.gitignore` und `.gitattributes` neu, `PROJECT_RULES.md`, `tools/check_public.py` (Pruefung auf Pfade, private Daten, Artefakte, Autorenangabe), Autor ueberall **Bennowan**, lokaler Pfad aus der README entfernt.
* Tests: 67 neue Logik-Tests (Thermik, Zustaende, Effekte), Integrationstest um Kern-Sperre, Kuehlung, Betrieb und Direktschuss erweitert, Regression 292/292.

## 0.4.0 - Freie Materialsynthese und individuelle Munition
* **Flux Fuser: freie Synthese** mit 1-11 Zutaten (Items *und* Fluessigkeiten). Eingangsplaetze binden sich beim Eintreffen, das Mischungsverhaeltnis ist pro Platz einstellbar, automatische oder manuelle Charge, Vorschau mit Stabilitaet. Klassik-Rezepte bleiben als zweiter Modus.
* **Materialidentitaet, die Transport und Spielstand ueberlebt:** 48 Traeger-Items + weltgebundenes Register in den Weltregeln (mit Pruefsumme/Version); Identitaet = quantisierte Zusammensetzung. Details: `docs/MATERIAL-ARCHITECTURE.md`.
* **Materialprofil aus Zusammensetzung** (Dichte, Haerte, Leitfaehigkeit mit Perkolation, Temperatur, Reaktivitaet, Flux-Affinitaet ...), Klassen, Gefahrenstufen, **durchgehende Reaktionen mit echter Explosion**, Fuser-Verschleiss.
* **Turm:** Materialien sind echte Munition; fuenf Projektilformen mit eigener Bewegung, Treffer- und Sonderwirkung, Hybride aus Sekundaerklasse; Flux-Bedarf, Risiko und Schuss-Schub aus dem Profil.
* **Generierte Icons und Namen** je Material (reproduzierbar, mit Einschluessen in den Farben der Zutaten); Sortierer/Entlader koennen belegte Materialien waehlen.
* **Fuser-Fehler behoben:** fehlender `liquidFilter` liess die Engine Dampf/Feuer und Dauerschaden an der Fluessigkeits-Uebergabe erzeugen; ausserdem ragten Effekte ueber den Block. Siehe `docs/FUSER-ANALYSE.md`.
* **Weitere Fehler beim Testen gefunden und behoben:** Turm-Absturz bei Material ohne Registereintrag; Slot-Schutz iterierte eine nicht iterierbare Gruppe; Explosion schonte eigene Gebaeude.
* Alte Spielstaende (Fuser-Format v1) bleiben lesbar; neue Fuser starten im freien Modus.
* Tests: 82 neue Logik-Tests, ein neuer Headless-Integrationstest (189 Pruefungen in zwei Laeufen), Regression 292/292.

## 0.3.0
* **Instabilität repariert**: Zielwert-Modell statt Netto-Abkühlung (Ursache: Abkühlung glich realistische Lasten immer aus; sinkende Effizienz verschleierte die Last).
  Ursachen-Anzeige, Warn-Toasts, Kühlung mit Wasser/Kryofluid, Stufen erreichbar und getestet.
* **Flux Fuser** (Serpulo + Erekir): Rezepte mit festen Zutaten, Flüssigkeiten und momentanem Flux; Pause ohne Flux.
* Vier neue Items mit eigenen Grafiken/Beschreibungen: Carbon Conductor, Ferrite Composite, Synaptic Alloy, Pyroglass; vier neue Turm-Munitionsmodi.
* Konverter akzeptieren Kühlmittel (Wasser, Kryofluid).

## 0.2.0
* Alle sieben Material-Modi, Instabilität, EMP-Kollaps mit Kaskade, Overclock-Schlag, Statusanzeigen, Übersetzungen EN/DE.
* 35 Logik-Tests, 132 Headless-Integrationsprüfungen gegen Mindustry v160.7.

## 0.1.0
* Prototyp: Konverter, Flux Node, Skillable Turret mit Kupfer/Rapid, momentaner Flux ohne Speicher.
