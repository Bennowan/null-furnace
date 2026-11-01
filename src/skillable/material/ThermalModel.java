package skillable.material;

/**
 * Temperaturmodell des Flux Fusers (rein rechnerisch, ohne Engine). Die Fuser-Temperatur ("Waerme", 0..1, ab 1,0 Durchreaktion) steigt durch
 * die Reaktionswaerme der laufenden Charge und sinkt durch passive Abstrahlung sowie durch Fuser-Kuehlbloecke. Wiederholte Chargen
 * summieren sich deshalb auf, bis der Fuser ueberhitzt - es sei denn, die Kuehlleistung reicht aus.
 */
public final class ThermalModel{
    private ThermalModel(){}

    /** Passive Abstrahlung pro Tick (immer wirksam). */
    public static final float PASSIVE = 0.0004f;
    /** Kuehlleistung pro Tick bei Kuehlmittelguete 1,0 und voller Versorgung. */
    public static final float COOL_BASE = 0.0036f;
    /** Mehr als dieser Wert wird nie abgefuehrt (extreme Chargen lassen sich nicht wegkuehlen). */
    public static final float MAX_COOLING = 0.006f;
    /** So viele Kuehlbloecke zaehlen je Fuser. */
    public static final int MAX_COOLERS = 4;
    /** Kuehlmittelverbrauch pro Tick bei voller Leistung. */
    public static final float LIQUID_PER_TICK = 0.1f;
    /** Automatischer Start nur, wenn heat + START_FACTOR * Spitze <= START_LIMIT. */
    public static final float START_FACTOR = 0.6f, START_LIMIT = 0.95f;
    /** Ab dieser Temperatur gilt der Fuser als ueberhitzt (Warnstufe). */
    public static final float OVERHEAT_AT = 0.8f;

    /**
     * Kuehlmittelguete aus Engine-Feldern: nur echte Kuehlfluessigkeiten (coolant), nicht brennbar, nicht warm.
     * Wasser (Waermekapazitaet .4, Temperatur .5) ~ 0,28; Kryofluid (.9, .25) ~ 0,86; Schlacke/Oel -> 0.
     */
    public static float coolantQuality(boolean coolant, float heatCapacity, float temperature, float flammability){
        if(!coolant || flammability > 0.1f || temperature > 0.55f || heatCapacity <= 0f) return 0f;
        return clamp(heatCapacity * (1.2f - temperature), 0f, 1.5f);
    }

    /** Kuehlleistung eines Blocks (Waermeeinheiten pro Tick); supply 0..1 = Anteil der benoetigten Kuehlmittelmenge. */
    public static float coolerRate(float quality, float supply){
        return COOL_BASE * quality * clamp(supply, 0f, 1f);
    }

    /** Gesamtkuehlung: Summe der ersten {@link #MAX_COOLERS} Raten, begrenzt auf {@link #MAX_COOLING}. */
    public static float totalCooling(float[] rates, int n){
        float sum = 0f;
        for(int i = 0; i < Math.min(n, MAX_COOLERS); i++) sum += Math.max(0f, rates[i]);
        return Math.min(MAX_COOLING, sum);
    }

    /** Ein Simulationsschritt der Temperatur ueber dt Ticks. */
    public static float step(float heat, float gainPerTick, float cooling, float dt){
        float h = heat + (gainPerTick - PASSIVE - Math.max(0f, cooling)) * dt;
        return h < 0f ? 0f : h;
    }

    /** Waermezuwachs pro Tick einer Charge mit effektiver Spitze peak und ticks Dauer bei voller Versorgung. */
    public static float gainPerTick(float peak, float ticks){
        return ticks <= 0f ? 0f : peak / ticks;
    }

    /** Darf eine Charge mit der Spitze peak bei der aktuellen Temperatur automatisch starten? */
    public static boolean startAllowed(float heat, float peak){
        return heat < OVERHEAT_AT && heat + START_FACTOR * peak <= START_LIMIT;
    }

    /** Bewusste Bestaetigung ("Jetzt starten") einer riskanten Charge: nur bei kaltem Fuser (Auto-Start ist dafuer gesperrt). */
    public static final float FORCE_START_HEAT = 0.35f;

    public static boolean forceAllowed(float heat){
        return heat <= FORCE_START_HEAT;
    }

    private static float clamp(float v, float lo, float hi){ return v < lo ? lo : (v > hi ? hi : v); }
}
