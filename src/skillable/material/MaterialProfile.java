package skillable.material;

/**
 * Berechnetes Materialprofil. Reine Funktion der {@link Composition}: gleiche Zusammensetzung = gleiches Profil.
 * Die Mischwerte (siehe {@link #toArray()}) sind quantisiert (1/1000), damit gespeicherte und neu berechnete Profile
 * bitgleich sind. Alle abgeleiteten Werte entstehen ausschliesslich aus den Mischwerten.
 *
 * Alle Koeffizienten sind PLATZHALTER-Balancing; die Struktur (welche Eigenschaft woraus entsteht) ist nachvollziehbar
 * in docs/SYNTHESIS.md beschrieben.
 */
public final class MaterialProfile{
    public static final int VERSION = 1;
    public static final int VALUES = 17;

    public static final int CLASS_KINETIC = 0, CLASS_ELECTRIC = 1, CLASS_THERMAL = 2, CLASS_EXPLOSIVE = 3, CLASS_FLUX = 4;
    public static final int HAZARD_STABLE = 0, HAZARD_UNSTABLE = 1, HAZARD_CRITICAL = 2, HAZARD_RUNAWAY = 3;
    public static final float UNSTABLE_AT = 0.55f, CRITICAL_AT = 0.85f, RUNAWAY_AT = 1.0f;

    //---- Identitaet
    public final String signature;
    public final long hash;
    public final int ingredients;

    //---- Mischwerte (gespeichert)
    public final float density, hardness, conductivity, temp, thermalMass, flammability, explosiveness,
        radioactivity, flux, viscosity, r, g, b, diversity, heatRelease, cold;

    //---- abgeleitet
    /** Reaktionswaerme-Spitze bei voller Versorgung relativ zur Waermekapazitaet (>= 1 = durchgehende Reaktion). */
    public final float peakHeat;
    public final int hazard;
    public final float[] classScore = new float[5];
    public final int primary, secondary;
    public final float power;
    /** Munition: Flux/Tick beim Schiessen, Beitrag zum Instabilitaets-Zielwert, Schuesse pro Item, Instabilitaets-Schub pro Schuss. */
    public final float fluxPerTick, risk, shotKick;
    public final int shotsPerItem;
    /** Synthese: Flux/Tick, Ticks pro Charge bei voller Versorgung. */
    public final float synthFlux, synthTicksBase;
    /** Visueller Seed (aus dem Hash, reproduzierbar). */
    public final int seed;

    public MaterialProfile(String signature, long hash, float[] v){
        this.signature = signature;
        this.hash = hash;
        float[] q = new float[VALUES];
        for(int i = 0; i < VALUES; i++) q[i] = q(i < v.length ? v[i] : 0f);
        density = q[0]; hardness = q[1]; conductivity = q[2]; temp = Math.max(-1f, Math.min(1f, q[3]));
        thermalMass = q[4]; flammability = q[5]; explosiveness = q[6]; radioactivity = q[7]; flux = q[8];
        viscosity = q[9]; r = q[10]; g = q[11]; b = q[12]; diversity = q[13];
        heatRelease = Math.max(0f, q[14]); cold = q[15];
        ingredients = Math.max(1, Math.min(Composition.MAX_INGREDIENTS, Math.round(q[16] * 1000f)));
        seed = (int)(hash ^ (hash >>> 32));

        peakHeat = heatRelease / (0.30f + 0.95f * thermalMass + 0.55f * cold);
        hazard = hazardFor(peakHeat);

        float kin = c(0.5f * density + 0.5f * hardness);
        float ele = c(conductivity);
        float the = c(0.55f * Math.max(0f, temp) + 0.5f * flammability + 0.15f * radioactivity);
        float exp = c(1.4f * explosiveness + 0.5f * Math.max(0f, peakHeat - 0.3f));
        float flx = c(1.1f * flux + 0.5f * radioactivity);
        classScore[CLASS_KINETIC] = kin;
        classScore[CLASS_ELECTRIC] = ele;
        classScore[CLASS_THERMAL] = the;
        classScore[CLASS_EXPLOSIVE] = exp;
        classScore[CLASS_FLUX] = flx;
        int p = 0;
        for(int i = 1; i < 5; i++) if(classScore[i] > classScore[p] + 1e-6f) p = i;
        int s = -1;
        for(int i = 0; i < 5; i++){
            if(i == p) continue;
            if(s < 0 || classScore[i] > classScore[s] + 1e-6f) s = i;
        }
        primary = p;
        secondary = (s >= 0 && classScore[s] >= 0.55f * classScore[p] && classScore[s] >= 0.25f) ? s : -1;

        power = c(0.30f + 0.45f * classScore[p] + 0.25f * diversity);
        fluxPerTick = clamp((0.7f + 4.6f * (float)Math.pow(power, 1.4)) * (1.15f - 0.45f * flux), 0.6f, 6.0f);
        risk = clamp(0.02f + 0.20f * Math.min(1f, peakHeat) + 0.14f * power * power, 0.02f, 0.36f);
        shotsPerItem = Math.max(2, Math.min(10, Math.round(10f - 7f * power)));
        shotKick = peakHeat >= UNSTABLE_AT ? 0.012f * c((peakHeat - 0.5f) * 2f) : 0f;

        float complexity = c(0.25f * ingredients / 11f + 0.35f * hardness + 0.20f * diversity + 0.20f * density);
        synthFlux = 0.5f + 1.6f * complexity;
        synthTicksBase = 100f + 140f * complexity;
    }

    /** Berechnet das Profil aus einer Zusammensetzung. */
    public static MaterialProfile of(Composition c){
        int n = c.count();
        float[] s = new float[n];
        float simpson = 0f;
        for(int i = 0; i < n; i++){ s[i] = c.share(i); simpson += s[i] * s[i]; }
        float D = 1f - simpson;

        float dens = 0, hard = 0, cm = 0, phi = 0, temp = 0, tm = 0, fl = 0, ex = 0, ra = 0, fx = 0, vi = 0, rr = 0, gg = 0, bb = 0, cold = 0;
        for(int i = 0; i < n; i++){
            IngredientDef d = c.defs[i];
            dens += s[i] * d.density; hard += s[i] * d.hardness; cm += s[i] * d.conductivity;
            if(d.conductivity >= 0.4f) phi += s[i];
            temp += s[i] * d.temp; tm += s[i] * d.thermalMass; fl += s[i] * d.flammability; ex += s[i] * d.explosiveness;
            ra += s[i] * d.radioactivity; fx += s[i] * d.flux; vi += s[i] * d.viscosity;
            rr += s[i] * d.r; gg += s[i] * d.g; bb += s[i] * d.b;
            cold += s[i] * Math.max(0f, -d.temp);
        }
        float percolation = smooth(0.15f, 0.45f, phi);
        dens = dens * (1f + 0.06f * D);
        hard = hard * (1f + 0.30f * D);
        float cond = cm * (0.35f + 0.65f * percolation);

        //Reaktionswaerme aus Wechselwirkungen der Zutaten
        float mismatch = 0, ignition = 0, spread = 0;
        for(int i = 0; i < n; i++){
            IngredientDef a = c.defs[i];
            float ra_ = a.flammability + 1.5f * a.explosiveness + a.radioactivity;
            for(int j = 0; j < n; j++){
                if(i == j) continue;
                IngredientDef b2 = c.defs[j];
                ignition += s[i] * s[j] * a.flammability * Math.max(0f, b2.temp);
                if(j > i){
                    mismatch += s[i] * s[j] * Math.abs(a.temp - b2.temp);
                    float rb = b2.flammability + 1.5f * b2.explosiveness + b2.radioactivity;
                    spread += s[i] * s[j] * Math.abs(ra_ - rb);
                }
            }
        }
        float heat = HEAT_MISMATCH * mismatch + HEAT_IGNITION * ignition + HEAT_SPREAD * spread
            + HEAT_EXPLOSIVE * ex + HEAT_RADIO * ra + HEAT_FLAM * fl;

        return new MaterialProfile(c.signature, c.hash, new float[]{
            dens, hard, cond, temp, tm, fl, ex, ra, fx, vi, rr, gg, bb, D, heat, cold, n / 1000f});
    }

    //Heizkoeffizienten (Platzhalter, kalibriert in MaterialTests ueber eine Stichprobe sinnvoller Kombinationen)
    static final float HEAT_MISMATCH = 1.10f, HEAT_IGNITION = 2.40f, HEAT_SPREAD = 0.40f,
        HEAT_EXPLOSIVE = 0.50f, HEAT_RADIO = 0.35f, HEAT_FLAM = 0.10f;

    public static int hazardFor(float peak){
        if(peak >= RUNAWAY_AT) return HAZARD_RUNAWAY;
        if(peak >= CRITICAL_AT) return HAZARD_CRITICAL;
        if(peak >= UNSTABLE_AT) return HAZARD_UNSTABLE;
        return HAZARD_STABLE;
    }

    /** Gespeicherte Mischwerte (quantisiert, Reihenfolge fix). */
    public float[] toArray(){
        return new float[]{density, hardness, conductivity, temp, thermalMass, flammability, explosiveness,
            radioactivity, flux, viscosity, r, g, b, diversity, heatRelease, cold, ingredients / 1000f};
    }

    public String code(){ return String.format("%04X", seed & 0xffff); }

    /** Eckenzahl von Kristall-/Polygonformen (3..6), reproduzierbar aus dem Seed. */
    public int sides(){ return 3 + (seed & 3); }

    /** Form des Materials fuer Namen/Icons: 0 Kristall, 1 Legierung, 2 Schlamm, 3 Komposit. */
    public int form(){
        if(viscosity < 0.55f) return 2;
        if(hardness > 0.60f) return 0;
        if(conductivity > 0.50f) return 1;
        return 3;
    }

    /** Anteil, ab dem die Charge mit Abschlaegen rechnen muss. */
    public static float yieldFactor(int hazard){
        return switch(hazard){
            case HAZARD_UNSTABLE -> 0.85f;
            case HAZARD_CRITICAL -> 0.55f;
            default -> 1f;
        };
    }

    static float q(float v){ return Float.isNaN(v) || Float.isInfinite(v) ? 0f : Math.round(v * 1000f) / 1000f; }
    static float c(float v){ return v < 0f ? 0f : (v > 1f ? 1f : v); }
    static float clamp(float v, float lo, float hi){ return v < lo ? lo : (v > hi ? hi : v); }
    static float smooth(float e0, float e1, float x){
        float t = c((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }
}
