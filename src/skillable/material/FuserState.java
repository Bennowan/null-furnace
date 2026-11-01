package skillable.material;

/** Betriebszustand des Flux Fusers (freie Synthese), rein aus Eingangsgroessen bestimmt - Grundlage fuer Anzeige und Tests. */
public final class FuserState{
    private FuserState(){}

    /** Zutaten und Mengen werden eingerichtet; es startet nichts von selbst. */
    public static final int PREPARE = 0;
    /** Betrieb ist scharf, aber die Mischung wurde gerade veraendert (Ruhezeit laeuft). */
    public static final int SETTLING = 1;
    /** Betrieb ist scharf und wartet auf Zutaten, Flux oder freien Ausgang. */
    public static final int READY = 2;
    /** Eine Charge laeuft. */
    public static final int ACTIVE = 3;
    /** Betrieb ist scharf, die Mischung waere komplett, aber der Fuser ist zu heiss fuer den Start. */
    public static final int COOLING_DOWN = 4;
    /** Temperatur im Warnbereich (oder gerade explodiert). */
    public static final int OVERHEATED = 5;

    public static final String[] KEYS = {"prepare", "settling", "ready", "active", "cooldown", "overheated"};

    public static int of(boolean armed, boolean active, float heat, boolean settling, boolean tooHot){
        if(heat >= ThermalModel.OVERHEAT_AT) return OVERHEATED;
        if(active) return ACTIVE;
        if(!armed) return PREPARE;
        if(tooHot) return COOLING_DOWN;
        if(settling) return SETTLING;
        return READY;
    }
}
