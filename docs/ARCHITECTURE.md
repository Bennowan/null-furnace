# Architektur

| Klasse | Aufgabe |
|---|---|
| `flux.FluxMath` | Absicherung: NaN/Infinity/negativ → 0, Obergrenze `MAX_FLUX` |
| `flux.ConverterProfile` | zentrale Parameter der drei Konvertertypen |
| `flux.FluxSolver` | Verteilung eines momentanen Durchsatzes (Priorität, proportional) – ohne Zustand |
| `flux.InstabilityModel` | Zielwert-Berechnung mit Ursachen (`Breakdown`), Annäherung, Stufen, Effizienz, Erholungsfaktor – reine Funktionen |
| `flux.FluxNetwork` | ein zusammenhängendes Netz; `update(delta)` rechnet pro Tick Angebot/Verteilung/Instabilität neu; `collapse`, `kick` |
| `flux.FluxNetworkManager` | Mitgliederliste, Aufbau der Netze (Union-Find) nur bei „dirty“, ein Netz-Update pro Spiel-Tick |
| `flux.FluxMember/Source/Consumer/Linker` | Schnittstellen, damit die Netzlogik ohne Mindustry testbar ist |
| `blocks.FluxNode` | Verbindungen (Reichweite, Team, Link-Limit), Speichern/Laden, Zeichnen |
| `blocks.FluxConverter` | Strom → Flux; Stromverbrauch über eigene `ConsumePower.requestedPower`; Zustände Sperre/Erholung |
| `blocks.FluxFuser` | Synthese: Rezept-Auswahl, Zutaten, Flüssigkeiten, Flux-Bedarf nur bei produzierbarem Rezept, Pause ohne Flux |
| `production.FluxRecipes/FluxRecipe` | modulare Rezeptdaten (Serpulo/Erekir-Zutaten) |
| `content.FluxItems` | die vier synthetischen Items |
| `blocks.SkillableTurret` | erbt `Turret`; Ein-Slot-Material, Flux-Gate für Schüsse, Overclock |
| `blocks.OverclockTargeting` | mapweite Zielwahl (nur auf Anforderung bzw. alle 90 Ticks als Existenzcache) |
| `modes.FluxModes/FluxMode/FluxBullets` | Profile und Geschosstypen der sieben Modi |
| `fx.FluxEffects` | Farben (blau→violett→weiß) und Engine-Effekte, keine Shader |

## Tick-Ablauf

1. `Trigger.update` → `FluxNetworkManager.update()` (einmal je Spiel-Tick; nicht im Pausen-/Game-Over-Zustand).
2. Bei „dirty“: Netze neu aufbauen, ungültige Links entfernen.
3. Je Netz: Konverter-Zustände zählen → Kapazität aus `Strom × Effizienz × Erholung` → Bedarf der Verbraucher einsammeln → Solver →
   `fluxGranted()` (ersetzt, addiert nie) → Instabilität je Konverter → ggf. Kollaps.
4. Danach aktualisieren sich die Gebäude (`updateTile`) und nutzen **nur** den eben zugeteilten Wert.

## Verworfene Ansätze

* **Flux als Item/Liquid/Tank**: widerspricht „nicht speicherbar“.
* **`ItemTurret`-Munitionsliste**: erlaubt gemischte Materialien; Ein-Slot-Regel nur mit eigener Logik sauber erzwingbar → `Turret` mit eigenem Slot.
* **`PowerTurret`/Strom direkt am Turm**: Der Turm soll bewusst keinen normalen Strom nutzen.
* **Eigenes Entity-/Update-System**: unnötig; Hook über `Trigger.update` genügt.
