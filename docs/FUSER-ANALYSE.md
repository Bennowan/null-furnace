# Analyse: Fuser wirkt "offen" (Partikel trotz Fluessigkeit)

## Beobachtung (Auftrag Abschnitt 11)
Bei Fluessigkeitszufuhr erscheinen am Fuser Partikel, als wuerde etwas durch eine offene Stelle fliegen, waehrend die Oberflaeche zeigt, dass die Fluessigkeit im Block ankommt.

## Untersuchung
1. **Blockdefinition** (`FluxFuser`): `solid = true`, `update = true`, `hasLiquids = true`, 3x3, `outputsLiquid = false`. Der Block ist geschlossen und kollidiert korrekt - **kein Fehler in Belegung/Kollision**.
2. **Sprite** `flux-fuser.png` (96x96): 752 nicht deckende Pixel, ausschliesslich der 2-Pixel-Rahmen am Rand. Kein Loch in der Mitte - **kein Texturfehler**.
3. **Eigene Effekte:** `FluxEffects.spark` setzte Funken bis +-9 Einheiten mit Linien ueber den Blockrand (Halbbreite 12); der Abschlusseffekt `craft` hatte Ringe bis 32 Einheiten Radius. Diese Effekte ragten aus dem Block - **Darstellungsfehler (Layer/Position)**.
4. **Engine-Verhalten (Bytecode von `Building.moveLiquid`, v160.7):** Nimmt der Zielblock eine Fluessigkeit nicht an und liefert `Block.consumesLiquid(liquid)` false, gilt sie als Fremdstoff:
   ist die *aktuelle* Fluessigkeit des Ziels eine andere reaktive (>10 % Fuellstand), spielt die Engine `Fx.steam`/`Fx.fire` **am Mittelpunkt zwischen Leitung und Block** ab, entfernt Fluessigkeit aus der Leitung
   und fuegt beiden Gebaeuden Dauerschaden zu. `consumesLiquid` liest nur `liquidFilter[]`, das ausschliesslich `ConsumeLiquid.apply` setzt. Der Fuser registrierte **keinen** Fluessigkeits-Consumer.
   Ergebnis: sobald ein Tank voll war (`acceptLiquid` false) oder eine nicht passende Fluessigkeit anlag, entstanden Dampf-/Feuerpartikel genau an der Kante, **waehrend die Anzeige gleichzeitig Fluessigkeit im Block zeigte** - exakt die Beobachtung.

## Urteil
Zwei getrennte Ursachen: (a) **echter Implementierungsfehler** (fehlender `liquidFilter`), der zusaetzlich Schaden anrichtete, und (b) **Darstellungsfehler** (Effekte ausserhalb der Kammer). Die Reaktionsregeln der Synthese waren nicht beteiligt und wurden deshalb nicht angefasst.

## Behebung
* `FluxFuser.init()`: `liquidFilter` fuer alle Fluessigkeiten setzen (der Fuser kennt jede Fluessigkeit als moegliche Zutat).
* Fuser-Grafik komplett in `draw()` innerhalb der Kammer (Radius <= 0,26 x Blockgroesse, Warnring <= 0,45); neuer Abschlusseffekt `FluxEffects.fuserDone` bleibt im Block; `spark`/`craft` werden vom Fuser nicht mehr benutzt.

## Nachweis (Integrationstest im echten Server)
* Alle Fluessigkeiten sind `consumesLiquid`.
* Reproduktion: voller Schlacketank + Wasser als aktuelle Fluessigkeit, Leitung schiebt Schlacke -> **mit** Filter kein Schaden/keine Entnahme; **ohne** Filter (kuenstlich entfernt) reproduziert die Engine die Reaktion (Fuser-HP faellt).
* Wasser ueber eine echte Leitung in den Fuser: bindet einen Fluessigkeitsplatz, landet nicht im Item-Inventar, kein Schaden.
**Nicht geprueft:** das tatsaechliche Aussehen (kein Grafikbetrieb in der Testumgebung).

## Nachtrag 0.5.0: Warum Wasser und Kryofluid den Fuser bisher nicht kuehlten
**Befund (Code-Analyse):**
1. In der freien Synthese sind Wasser und Kryofluid **Zutaten**: sie gehen in die Zusammensetzung ein und senken dort die Spitzenwaerme der *Charge* (Waermekapazitaet, Kaelteanteil). Mit der Temperatur des **Fusers** selbst waren sie nie verbunden.
2. Die Fuser-Temperatur sank nur durch eine einzige Abstrahlung, und zwar nur in Pausen (kein Flux); ein Kuehlpfad existierte nicht. Wiederholte Chargen heizten den Fuser (zusammen mit dem Verschleiss) deshalb auf, ohne dass der Spieler etwas dagegen tun konnte.
3. Die Fluessigkeitsverarbeitung selbst war nicht fehlerhaft (siehe oben: nur der fehlende `liquidFilter`, der Dampf/Feuer an der Uebergabe ausloeste, bereits behoben). Es fehlte also kein Wert, sondern die **Mechanik**.
**Behebung:** eigenes Temperaturmodell (`ThermalModel`: passive Abstrahlung, Zuwachs aus der laufenden Charge, Kuehlleistung) und der Fuser-Kuehlmantel als echter, getrennter Kuehlpfad. Wasser/Kryofluid bleiben Zutaten; als Kuehlmittel wirken sie nur ueber den Mantel.
**Nachweis:** Integrationstest im echten Server (ohne Kuehlmittel -0,08 Temperatur in 200 Ticks, mit Wasser deutlich mehr, mit Kryofluid mehr als das Doppelte; dieselbe Ueberlast-Charge explodiert ohne Kuehlung, ueberlebt zwei Kryofluid-Maentel).
