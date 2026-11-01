package skillable.flux;

/**
 * Reine Instabilitaetsmathematik (unit-testbar). Instabilitaet liegt immer in [0, 1].
 *
 * Modell: Jeder Tick wird ein ZIELWERT berechnet:
 *   ziel = ( risk * last^2  +  overdriveRisk * ueberlastanteil  +  modusRisiko  +  0,25 * defizit ) * kuehlfaktor
 * Die Instabilitaet laeuft mit riseRate nach oben bzw. coolRate nach unten auf den Zielwert zu.
 * Liegt der Zielwert bei >= 1, kollabiert das Netz. Bei niedriger Last faellt der Zielwert und die Instabilitaet sinkt wieder.
 */
public final class InstabilityModel{
    private InstabilityModel(){}

    public static final int STABLE = 0, WARNING = 1, CRITICAL = 2, COLLAPSE = 3;
    public static final float WARNING_AT = 0.45f, CRITICAL_AT = 0.75f, COLLAPSE_AT = 1f;

    /** Wiederanlauf: Instabilitaet nach einem Kollaps. */
    public static final float RESTART_INSTABILITY = 0.30f;
    /** Dauer der EMP-Abschaltung und der Erholungsphase in Ticks. */
    public static final float LOCKOUT_TICKS = 60f * 6f, RECOVERY_TICKS = 60f * 8f;
    /** Anteil des unversorgten Bedarfs, der in den Zielwert eingeht. */
    public static final float DEFICIT_WEIGHT = 0.25f;

    /** Aufschluesselung der Ursachen fuer die Anzeige (alles Anteile am Zielwert, 0..). */
    public static final class Breakdown{
        public float load, loadRisk, overdriveRisk, modeRisk, deficitRisk, coolant = 1f, target;

        public void set(Breakdown o){
            load = o.load; loadRisk = o.loadRisk; overdriveRisk = o.overdriveRisk; modeRisk = o.modeRisk;
            deficitRisk = o.deficitRisk; coolant = o.coolant; target = o.target;
        }
    }

    public static int stage(float instability){
        float i = FluxMath.clamp01(instability);
        if(i >= COLLAPSE_AT) return COLLAPSE;
        if(i >= CRITICAL_AT) return CRITICAL;
        if(i >= WARNING_AT) return WARNING;
        return STABLE;
    }

    /**
     * Zielwert der Instabilitaet (kann > 1 sein = fuehrt zum Kollaps).
     * @param load Anteil des Nenndurchsatzes, der tatsaechlich geliefert wird (0..overdriveFactor)
     * @param modeRisk Risiko der aktiven Verbraucher (Modi, Reaktionen), auf diesen Konverter umgelegt
     * @param deficit Anteil des Bedarfs, der nicht gedeckt wurde (0..1)
     * @param coolantFactor 1 = keine Kuehlung, < 1 senkt den Zielwert (Wasser/Kryofluid)
     * @param out optional: Aufschluesselung
     */
    public static float target(ConverterProfile p, float load, float modeRisk, float deficit, float coolantFactor, Breakdown out){
        float l = FluxMath.safe(load);
        float base = Math.min(l, 1f);
        float over = Math.max(0f, l - 1f) / Math.max(0.0001f, p.overdriveFactor - 1f);
        float loadRisk = p.risk * base * base;
        float overRisk = p.overdriveRisk * FluxMath.clamp01(over);
        float mode = FluxMath.safe(modeRisk);
        float def = DEFICIT_WEIGHT * FluxMath.clamp01(deficit);
        float cool = coolantFactor <= 0f || Float.isNaN(coolantFactor) ? 1f : Math.min(1f, coolantFactor);
        float t = (loadRisk + overRisk + mode + def) * cool;
        if(out != null){
            out.load = l; out.loadRisk = loadRisk; out.overdriveRisk = overRisk; out.modeRisk = mode;
            out.deficitRisk = def; out.coolant = cool; out.target = t;
        }
        return t;
    }

    /** Bewegt die Instabilitaet um hoechstens riseRate/coolRate (pro Tick, mit delta skaliert) auf den Zielwert zu. */
    public static float advance(float instability, float target, ConverterProfile p, float delta){
        float i = FluxMath.clamp01(instability);
        float d = FluxMath.safe(delta);
        float t = Float.isNaN(target) ? 0f : Math.max(0f, target);
        if(i < t) i = Math.min(t, i + p.riseRate * d);
        else if(i > t) i = Math.max(t, i - p.coolRate * d);
        return FluxMath.clamp01(i < 1f && t >= 1f && i >= 0.9999f ? 1f : i);
    }

    /** Wirkungsgrad-Faktor: ab WARNING nimmt der Durchsatz ab (max. -35 % kurz vor dem Kollaps). */
    public static float efficiency(float instability){
        float i = FluxMath.clamp01(instability);
        if(i <= WARNING_AT) return 1f;
        float t = (i - WARNING_AT) / (1f - WARNING_AT);
        return 1f - 0.35f * t;
    }

    /** Faktor waehrend der Erholungsphase (rampt linear von 0.5 auf 1.0). */
    public static float recoveryFactor(float recoveryTicksLeft){
        float t = FluxMath.clamp01(recoveryTicksLeft / RECOVERY_TICKS);
        return 1f - 0.5f * t;
    }
}
