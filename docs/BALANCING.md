# Balancing – ALLE Zahlen sind Platzhalter

Es gab **keine** Spieltests zur Balance. Die Werte sind nur intern konsistent (sie sind im Integrationstest nachvollziehbar), nicht „gut“.
Flux-Einheit: Flux/Tick (Anzeige im Spiel ×60 = pro Sekunde).

## Konverter (`flux/ConverterProfile.java`)
| Typ | Durchsatz | Strom/Tick | Overdrive | risk (Ziel bei 100 % Last) | riseRate | coolRate | Kollaps ab Last |
|---|---|---|---|---|---|---|---|
| Stabilizer | 1,0 (60/s) | 0,9 | ×1,15 | 0,75 | 0,0012 | 0,0015 | nur mit Überlast (> ca. 105 % + Defizit) |
| Accelerator | 2,6 | 3,0 | ×1,30 | 1,05 | 0,0030 | 0,0012 | ≈ 97 % |
| Quantum | 6,5 | 9,0 | ×1,45 | 1,40 | 0,0060 | 0,0010 | ≈ 85 % |

Formel und Schwellen: `docs/FLUX-SYSTEM.md`. Erreichbarkeit jeder Stufe ist getestet (Unit- und Server-Tests).

## Modi (`modes/FluxModes.java`, `FluxBullets.java`)
| Modus | Material | Flux/Tick | Risiko (Zielwert) | Besonderheit |
|---|---|---|---|---|
| Rapid | Kupfer | 0,8 | 2 % | 8× Feuerrate, kurze Reichweite, Mini-Blitze |
| Rail | Graphit | 1,6 | 5 % | langsam, Durchschlag (Abzug nach Ziel-HP), große Reichweite |
| Incendiary | Kohle | 1,4 | 6 % | Flächenschaden + 4 s Brandzone |
| Smart | Silizium | 1,2 | 4 % | zielsuchend, bevorzugt schnelle/fliegende Ziele |
| Catastrophic | Blast Compound | 4,5 | 30 % | sehr großer Flächenschaden, 6 s Reload |
| Reality | Phase Fabric | 6,0 | 38 % | durchdringt Ziele/Gebäude, ignoriert Rüstung, verschiebt Ziele |
| Storm | Surge Alloy | 5,0 | 34 % | Kettenblitze, Elektro-Status, Schildprojektoren verlieren Energie |

Beobachtet im Headless-Test (alte Version, vor Fuser) (420 Ticks, 3 Konverter): Rapid 53 Schüsse, Smart 15, Incendiary 5, Storm 4, Rail 3, Reality 3, Catastrophic 1.
Zum Dauerbetrieb von Reality/Storm sind mehrere Quantum Converter nötig (6 Flux/Tick Bedarf bei 6,5 je Konverter).

Kosten der Blöcke stehen in `content/FluxBlocks.java` und sind ebenfalls ungeprüft. Overclock-Werte: `SkillableTurret` (Konstanten oben).
