# Architekturentscheidung: dynamische Materialidentität (Phase 1 + 2)

Stand: vor der Implementierung festgelegt, danach gegen den Code und die Tests abgeglichen (siehe `TESTING.md`).

## Randbedingungen der Mindustry-API (v160.7, am Bytecode geprüft)

| Einschränkung | Folge |
|---|---|
| `Item`-Typen sind Content: feste IDs (`short`), `ItemModule`/Förderbänder/Speicherstände/Content-Header referenzieren sie über ID bzw. Namen. Zur Laufzeit neu registrierte Items brechen Netzwerk-Sync und Spielstände. | **Keine** Items zur Laufzeit erzeugen. |
| Förderbänder, Sorter, Brücken, Kern, Entlader kennen nur `Item` + Menge, keine Zusatzdaten pro Item. | Identität muss im **Item-Typ** stecken oder die Transportlogik komplett ersetzt werden. |
| `BulletType` ist ebenfalls Content: Bullets werden im Netzwerk-Snapshot über die Content-ID referenziert. Lazy erzeugte Typen hätten auf Server und Client unterschiedliche IDs. | Alle Geschosstypen **beim Laden vorab** anlegen, danach nur umkonfigurieren. |
| `Effect`-Objekte erhalten beim Erzeugen eine fortlaufende ID (Netzwerk). | Nur wenige statische Effekte, parametrisiert über `Effect.data`/`color`. |
| `Rules.tags` (`StringMap`) wird mit dem Spielstand gespeichert und per `Call.setRules` an Clients gesendet. | Ideal für ein kleines, versioniertes Register ohne eigenes Chunk-Format. |
| `Building.moveLiquid` löst bei Flüssigkeiten, die der Zielblock nicht annimmt **und** die `Block.consumesLiquid()` nicht kennt, Dampf-/Feuer-Effekte und Dauerschaden aus. `consumesLiquid` liest nur `liquidFilter[]`. | Ursache der „offenen“ Fuser-Optik (Abschnitt 11), siehe unten. |

## Entscheidung

**Slot-Pool + weltgebundenes Register + deterministisches Profil.**

1. **48 vorab registrierte Träger-Items** (`SynthItem`, `fluxmat-00` … `fluxmat-47`). Sie sind echte Items: Förderbänder, Sorter, Brücken, Kern, Entlader und Speicherstände funktionieren unverändert, ohne dass etwas verloren gehen kann.
2. **Materialregister** (`MaterialRegistry`) ordnet jedem belegten Slot eine **Zusammensetzung** zu. Gespeichert in `state.rules.tags["flux-protocol.materials"]` (Zeilenformat mit Version und Prüfsumme je Zeile). Dadurch:
   * Spielstand speichern/laden bewahrt Slot → Zusammensetzung,
   * Multiplayer-Clients erhalten das Register mit den Regeln,
   * beschädigte/unbekannte Zeilen werden einzeln verworfen (Slot wird als „unbekanntes Material“ inert behandelt, nichts stürzt ab).
3. **Identität = Zusammensetzung, nicht Slot.** Die Zutaten werden auf 16 Teile quantisiert (Größtrest-Verfahren, jede Zutat ≥ 1 Teil, sortiert nach Schlüssel). Daraus entstehen Signatur und 64-Bit-Hash. Gleiche Zusammensetzung → gleicher Hash → gleiches Profil, unabhängig von Reihenfolge, Chargengröße und Welt. Die Quantisierung begrenzt die „Flut“ nahezu identischer Varianten, ohne eine Rezepttabelle zu brauchen.
4. **Profil = reine Funktion der Zusammensetzung** (`MaterialMath`). Zutateneigenschaften kommen aus den Engine-Feldern jedes Items/jeder Flüssigkeit (Entflammbarkeit, Explosivität, Ladung, Temperatur …) plus einer kleinen Tabelle physikalischer Konstanten für Vanilla-Items (Dichte, Härte, Leitfähigkeit). Modded Items funktionieren über die Ableitung aus den Engine-Feldern. Es gibt **keine Rezeptliste**.
5. **Slot-Wiederverwendung:** Ist das Register voll, wird nur ein Slot freigegeben, der (a) ≥ 60 s nicht genutzt wurde und (b) nirgends mehr im Spiel liegt (Scan aller Gebäude, Einheiten, Fuser-Ausgang, Turm-Slot). Gibt es keinen, pausiert der Fuser mit klarer Statusmeldung – es geht nie ein vorhandenes Material verloren.
6. **Geschosse:** Pro Slot existieren beim Laden vorab ein Haupt-, ein Splitter- und ein Zonen-`BulletType`. Bei Slot-Belegung werden sie anhand des Profils **in place umkonfiguriert** (`ProjectileFactory`). Form, Bewegung, Treffer- und Sondereffekte folgen aus Materialklasse und kontinuierlichen Werten.
7. **Turm:** `FluxModes.get(item)` liefert für Träger-Items ein dynamisches `FluxMode` aus dem Profil (Flux-Bedarf, Risiko, Schüsse pro Item, Geschoss). Der bestehende Flux-/Instabilitätspfad bleibt unverändert und wird dadurch automatisch gespeist.
8. **Fuser:** Freier Modus (Standard bei neuen Blöcken) + Klassik-Modus (die vier alten Rezepte, damit bestehende Welten und Items weiter funktionieren). Mehr dazu in `SYNTHESIS.md`.

## Verworfene Alternativen

* **Items zur Laufzeit registrieren** – bricht Speicherstände und Netzwerk-IDs.
* **Eigenes Transportsystem mit Datenpaketen** – verlangt Ersatz aller Förderblöcke; Mods/Vanilla-Logistik (Sorter, Kern, Brücken) wären nicht nutzbar.
* **Ein einziges Träger-Item + Zusatzdaten im Gebäude** – Förderbänder würden die Identität zwischen Gebäuden verlieren.
* **Unbegrenzt viele Varianten** – unmöglich mit festen Items; Quantisierung + Slot-Pool ist die ehrliche Obergrenze.

## Beim Testen gefundene und behobene Punkte
* `Groups.build` ist ein reiner Index (`Building.add()` ruft nur `addIndex`) und **nicht iterierbar** - die Slot-Schutzlogik iteriert deshalb ueber `TeamData.buildings` (Test: 47 belegte Slots werden erkannt).
* `ItemSelection` (Sortierer, Entlader, Itemquelle) filtert ueber `isHidden()`. `SynthItem.isHidden()` ist daher *dynamisch*: unbelegte Traeger sind verborgen, belegte waehlbar - sonst waeren synthetische Materialien nicht logistisch nutzbar.
* Ein Turm mit Material ohne Registereintrag warf eine NPE in `Turret.updateTile`; `hasAmmo()` verlangt jetzt einen gueltigen Modus, nach 3 s ohne Eintrag wird das Material kontrolliert verworfen.
* `Damage.dynamicExplosion` mit Team schont eigene Gebaeude; der Fuser nutzt die teamlose Variante plus gezielten Eigenschaden.

## Bekannte Grenzen (bewusst, siehe README)

* Max. 48 gleichzeitig existierende verschiedene synthetische Materialien pro Welt.
* Bis zu 60 s alte Items in Junction-/Brückenpuffern werden bei der Slot-Neuvergabe nicht erfasst; deshalb die 60-s-Sperre.
* Registerzeilen sind nur so lange gültig, wie dieselbe Formelversion gilt; bei Versionswechsel wird das Profil aus der Signatur neu berechnet (falls alle Zutaten auflösbar sind), sonst bleibt der gespeicherte Stand erhalten.
