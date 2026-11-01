# Flux-System

## Regeln
* **Kapazität eines Konverters (pro Tick)** = `Nenndurchsatz × Stromzufriedenheit × Effizienz(Instabilität) × Erholungsfaktor`.
  Mit aktiviertem **Overdrive** darf er bis `× overdriveFactor` liefern (kostet mehr Instabilität und mehr Strom).
* **Bedarf**: Der Turm fordert nur Flux an, wenn er Material hat **und** ein Ziel/Schießwunsch besteht (oder Overclock scharf ist).
* **Verteilung**: höhere Priorität zuerst (Overclock = 10, normal = 0); gleiche Priorität proportional zum Bedarf.
* **Verfall**: Alles, was nicht zugeteilt wird, ist weg (`lastWasted` ist nur Anzeige). Kein Konverter, Node, Turm oder Netz hat ein Flux-Feld, das über Ticks wächst.
* **Schießen**: nur, wenn `zugeteilt ≥ 50 % × Bedarf`. Der Reload-Fortschritt wächst mit dem Erfüllungsgrad. (Der Reload-Zähler selbst ist Zeitfortschritt, kein Flux-Vorrat; zum Auslösen ist trotzdem aktueller Flux nötig.)
* **Stromsystem**: Konverter hängen als normale Verbraucher im Mindustry-Stromnetz. Ihr Verbrauch ist 0, solange kein Flux-Bedarf besteht,
  sonst `Nennverbrauch × max(0,3; Last)` – Overdrive/Überlast erhöht den Verbrauch also über das normale Stromsystem. Es werden **keine Batterien manipuliert**.

## Nodes
* Reichweite 12 Kacheln (Rand zu Rand), max. 8 Links, nur gleiches Team, nur Flux-Gebäude.
* Beim Platzieren automatische Verbindung; Umkonfigurieren durch Antippen des Ziels bei ausgewähltem Node.
* Netze entstehen nur über Node-Links (Union-Find). Zerstörte/entfernte Ziele werden beim nächsten Netzaufbau aus den Links gelöscht.
* Nicht verbundene Netze teilen weder Flux noch Instabilität (außer über die bewusste Kaskade, s. u.).

## Instabilität (Zielwert-Modell, seit 0.3.0)
**Warum sie vorher bei 0 blieb:** Die alte Formel hatte eine Abkühlung, die bei jeder Last mitlief. Bei Last unter ca. 90 % (Stabilizer) bzw. 80 % (Accelerator)
war der Netto-Wert negativ, die Instabilität wurde sofort wieder auf 0 gedrückt. Zusätzlich sank die gelieferte Menge mit steigender Instabilität und rechnete die Last „weg“.

**Jetzt:** Pro Konverter wird jeden Tick ein **Zielwert** berechnet:

`Ziel = ( risk · Last² + overdriveRisk · Überlastanteil + Modus-/Reaktionsrisiko + 0,25 · Defizit ) · Kühlfaktor`

* **Last** = geliefert + unversorgter Anteil des Bedarfs, bezogen auf den Nenndurchsatz (so kann sinkende Effizienz die Last nicht verschleiern).
* **Modus-/Reaktionsrisiko**: Beitrag der Verbraucher (Turm-Modus 2–38 %, Fuser-Rezept 5–30 %), nur solange sie tatsächlich Flux erhalten; geteilt durch die Zahl der Konverter.
* **Kühlfaktor**: Wasser ×0,80, Kryofluid ×0,55 (verbraucht 0,10/Tick bei Last).
* Die Instabilität läuft mit `riseRate` nach oben bzw. `coolRate` nach unten auf den Zielwert zu: **bei niedriger Last sinkt sie wieder**.

| Stufe | Wert | Folge |
|---|---|---|
| stabil | < 45 % | – |
| WARNUNG | ≥ 45 % | Durchsatz sinkt (bis −35 %), Funken/Lecks, Hinweis-Toast |
| KRITISCH | ≥ 75 % | Alarmton, Blitze auf den Leitungen, Toast |
| KOLLAPS | Ziel ≥ 100 % | alle Konverter des Netzes: 6 s EMP-Abschaltung, 8 s Erholung (50 → 100 %), Neustart bei 30 %; Kaskade auf Netze im Umkreis von 28 Kacheln (+25 %) |

Beispiele (Zielwert ohne Modus/Kühlung): Stabilizer 80 % Last → 48 % (Warnung), 100 % → 75 % (kritisch), Overdrive-Überlast → > 100 % (Kollaps).
Accelerator ab ca. 97 % Last Kollaps; Quantum ab ca. 85 %.
Die Anzeige am Konverter nennt den **Zielwert und seine Ursachen** (Last, Überlast, Modus-Risiko, Defizit, Kühlung).
Gespeichert werden Instabilität, Restzeit von Abschaltung/Erholung (nur wenn > 0,5 Ticks), Overdrive. Abgelaufene Zustände werden nicht gespeichert.

## Overclock-Schlag
Konfig-Button am Turm. Voraussetzungen: Material ≥ 4 Einheiten, Abklingzeit abgelaufen, gültiges mapweites Ziel (stärkste feindliche Einheit,
sonst nächstes feindliches Gebäude), Netzkapazität ≥ 14 Flux/Tick, Netz online. Dann wird der Turm „scharf“ (Flux-Anforderung mit Priorität 10).
Sobald **in einem Tick** 14 Flux/Tick zugeteilt sind, wird sofort geschossen: Material −4, Schlag am Zielort, Instabilitäts-Schub +55 %,
danach EMP-Netzstörung (3 s) + Erholung. Kein Aufladen über mehrere Sekunden; läuft ein Zeitfenster (12 s) ohne genug Flux ab, bricht er ab.
Abklingzeit 40 s verhindert Dauerfeuer.
