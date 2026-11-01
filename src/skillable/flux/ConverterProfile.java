package skillable.flux;

/**
 * Zentrale Parameter der drei Konvertertypen. Alle Werte sind PLATZHALTER ohne Spieltests
 * (siehe docs/BALANCING.md). Einheit Flux: "Flux pro Tick" bei voller Stromversorgung.
 *
 * Instabilitaet folgt einem ZIELWERT-Modell (siehe InstabilityModel): Aus Last, Ueberlast, Modus-Risiko und
 * Defizit ergibt sich ein Zielwert; die Instabilitaet bewegt sich mit riseRate nach oben bzw. coolRate nach unten dorthin.
 */
public final class ConverterProfile{
    public final String name;
    /** Nenn-Durchsatz in Flux/Tick bei 100% Stromversorgung. */
    public final float throughput;
    /** Nenn-Stromverbrauch in Stromeinheiten/Tick (60 * Wert = Anzeige /s). */
    public final float powerUse;
    /** Faktor, um den Durchsatz im Overdrive-Betrieb hoeher sein darf (>=1). */
    public final float overdriveFactor;
    /** Zielwert der Instabilitaet bei 100 % Last (ohne Ueberlast, Modus-Risiko, Defizit, Kuehlung). */
    public final float risk;
    /** Maximale Zunahme der Instabilitaet pro Tick, wenn der Zielwert darueber liegt. */
    public final float riseRate;
    /** Maximale Abnahme pro Tick, wenn der Zielwert darunter liegt. */
    public final float coolRate;
    /** Zusaetzlicher Zielwert bei voller Overdrive-Ueberlast (Last = overdriveFactor). */
    public final float overdriveRisk;

    public ConverterProfile(String name, float throughput, float powerUse, float overdriveFactor,
                            float risk, float riseRate, float coolRate, float overdriveRisk){
        this.name = name;
        this.throughput = FluxMath.safe(throughput);
        this.powerUse = FluxMath.safe(powerUse);
        this.overdriveFactor = Math.max(1.001f, FluxMath.safe(overdriveFactor));
        this.risk = FluxMath.safe(risk);
        this.riseRate = FluxMath.safe(riseRate);
        this.coolRate = FluxMath.safe(coolRate);
        this.overdriveRisk = FluxMath.safe(overdriveRisk);
    }

    //Stabilizer: zuverlaessig, effizient, niedrigstes Risiko
    public static final ConverterProfile STABILIZER = new ConverterProfile("stabilizer",
        1.0f, 0.9f, 1.15f, 0.75f, 0.0012f, 0.0015f, 0.30f);
    //Accelerator: mehr Durchsatz, deutlich hoeheres Risiko; Volllast kippt das Netz
    public static final ConverterProfile ACCELERATOR = new ConverterProfile("accelerator",
        2.6f, 3.0f, 1.30f, 1.05f, 0.0030f, 0.0012f, 0.35f);
    //Quantum: High-End, teuer, erhebliches Risiko
    public static final ConverterProfile QUANTUM = new ConverterProfile("quantum",
        6.5f, 9.0f, 1.45f, 1.40f, 0.0060f, 0.0010f, 0.40f);
}
