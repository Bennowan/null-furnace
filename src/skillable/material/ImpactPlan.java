package skillable.material;

/**
 * Mehrstufiger Einschlagsplan eines Materials: Kontakt -> Primaerreaktion -> Folgereaktionen -> Nachwirkung.
 * Reine Funktion von {@link MaterialProfile} und {@link ShotSpec} (reproduzierbar, ohne Engine). Gezeichnet wird er von
 * {@link ImpactRenderer} in EINEM Effekt je Einschlag (keine Einzel-Effekte pro Phase).
 *
 * Ehrlichkeit gegenueber der Spielmechanik: der Schockring hat genau den Radius des echten Flaechenschadens, Blitze sind hoechstens so viele
 * wie echte Blitze, Folgereaktionen entsprechen den echten Echo-Geschossen (Anzahl, Verzoegerung, Radius, Versatz).
 */
public final class ImpactPlan{
    public static final int FLASH = 0, RING = 1, FIREBALL = 2, ARCS = 3, SHARDS = 4, STREAKS = 5, EMBERS = 6, VAPOR = 7, IMPLODE = 8,
        DROPS = 9, FROST = 10, FIELD = 11, BLOOM = 12, SMOKE = 13, ECHO_FLASH = 14, ECHO_RING = 15, ECHO_ARC = 16;
    public static final String[] KIND_NAMES = {"flash", "ring", "fireball", "arcs", "shards", "streaks", "embers", "vapor", "implode",
        "drops", "frost", "field", "bloom", "smoke", "echoFlash", "echoRing", "echoArc"};
    public static final int MAX_PHASES = 16;
    /** Effektlebensdauern der drei Stufen (je Stufe ein statischer Effekt). */
    public static final int[] TIER_LIFE = {34, 64, 104};
    private static final int[] WEIGHT = {6, 1, 3, 4, 1, 1, 1, 4, 10, 2, 1, 3, 2, 4, 6, 1, 4};

    public int count;
    public final int[] kind = new int[MAX_PHASES], start = new int[MAX_PHASES], dur = new int[MAX_PHASES], n = new int[MAX_PHASES], tag = new int[MAX_PHASES];
    public final float[] size = new float[MAX_PHASES], inten = new float[MAX_PHASES];
    public int lifetime, tier, cost, seed, form;
    public float extent;
    /** Farben: Leuchten, Hitze (orange), blass. */
    public final float[] glow = new float[3], hot = {1f, 0.55f, 0.2f}, pale = new float[3];
    public float ringRadius;
    public int arcs, echoes;
    public boolean echoArc;
    public float echoSpread, echoDelay, echoStep, echoRadius;

    private void add(int k, int st, int du, float sz, float in, int num, int tg){
        if(count >= MAX_PHASES) return;
        kind[count] = k; start[count] = st; dur[count] = Math.max(1, du); size[count] = sz; inten[count] = in; n[count] = num; tag[count] = tg;
        count++;
    }

    public static ImpactPlan build(MaterialProfile p, ShotSpec s){
        ReactionSignature g = new ReactionSignature(p, s);
        ImpactPlan ip = new ImpactPlan();
        ip.seed = p.seed;
        ip.form = s.form;
        for(int i = 0; i < 3; i++){
            ip.glow[i] = s.glow[i];
            ip.pale[i] = s.front[i] + (1f - s.front[i]) * 0.5f;
        }
        float E = g.energy;

        //Phase 1 - Kontakt
        ip.add(FLASH, 0, 5 + Math.round(4f * E), 6f + 14f * E, 0.6f + 0.4f * E, 0, 0);
        if(s.form == ShotSpec.SLUG || (g.brittle < 0.35f && g.density > 0.5f && s.splashRadius == 0f))
            ip.add(STREAKS, 0, 10, 8f + 12f * g.density, 0.6f + 0.4f * g.density, 4 + Math.round(5f * g.density), 0);

        //Phase 2 - Primaerreaktion (jede Komponente nur, wenn die Materialwerte oder die echte Wirkung sie tragen)
        if(s.splashRadius > 0f){
            ip.ringRadius = s.splashRadius;
            ip.add(RING, 2, 18 + Math.round(10f * E), s.splashRadius, 0.5f + 0.5f * E, 0, 0);
            if(g.thermal > 0.3f || s.form == ShotSpec.SHELL || g.instability > 0.5f)
                ip.add(FIREBALL, 1, 22 + Math.round(8f * E), s.splashRadius * 0.55f, 0.6f + 0.4f * Math.max(g.thermal, E), 0, 0);
        }
        if(s.lightning > 0){
            ip.arcs = Math.min(s.lightning, 7);
            ip.add(ARCS, 3, 16, Math.max(10f, s.lightningLength), 0.7f + 0.3f * g.electric, ip.arcs, 0);
        }
        if(g.brittle > 0.35f)
            ip.add(SHARDS, 2, 28, 10f + 16f * g.brittle, 0.6f + 0.4f * g.brittle, 6 + Math.round(8f * g.brittle), g.thermal > 0.4f ? 1 : 0);
        if(g.field > 0.3f || s.form == ShotSpec.RIFT){
            ip.add(IMPLODE, 0, 14, 16f + 16f * g.field, 0.7f, 8, 0);
            ip.add(FIELD, 8, 24, 14f + 14f * g.field, 0.6f + 0.4f * g.field, 3, 0);
        }
        if(g.fluid > 0.3f){
            ip.add(DROPS, 2, 26, 14f + 14f * g.fluid, 0.7f, 5 + Math.round(8f * g.fluid), 0);
            if(g.thermal > 0.2f) ip.add(VAPOR, 8, 36, 12f + 10f * g.fluid, 0.5f, 4, 0);
        }
        if(g.cold > 0.3f || s.freeze > 0f)
            ip.add(FROST, 3, 34, 10f + 12f * g.cold, 0.7f, 5 + Math.round(6f * g.cold), 0);

        //Phase 3 - Folgereaktionen: exakt die echten Echo-Geschosse
        ip.echoes = s.echoCount;
        ip.echoArc = s.echoArc;
        ip.echoSpread = s.echoSpread;
        ip.echoDelay = s.echoDelay;
        ip.echoStep = s.echoDelayStep;
        ip.echoRadius = s.echoRadius;
        for(int i = 0; i < s.echoCount; i++){
            int st = Math.round(s.echoDelay + s.echoDelayStep * i);
            if(s.echoArc) ip.add(ECHO_ARC, st, 14, Math.max(8f, s.echoLightningLength), 0.7f, s.echoLightning, i);
            else{
                ip.add(ECHO_FLASH, st, 6, 8f + 10f * E, 0.8f, 0, i);
                ip.add(ECHO_RING, st, 16, s.echoRadius, 0.6f, 0, i);
            }
        }

        //Phase 4 - Nachwirkung (nur bei komplexen oder deutlich thermischen Reaktionen)
        if(g.complexity >= 0.3f || g.thermal > 0.45f){
            if(g.thermal > 0.25f || E > 0.6f) ip.add(EMBERS, 24, 40, 10f + 14f * g.thermal, 0.7f, 4 + Math.round(6f * g.thermal), 0);
            if(s.splashRadius > 0f && (g.thermal > 0.3f || s.form == ShotSpec.SHELL)) ip.add(SMOKE, 26, 52, 10f + 10f * E, 0.5f, 4, 0);
            if(g.thermal > 0.5f || s.zoneRadius > 0f) ip.add(BLOOM, 10, 44, s.zoneRadius > 0f ? s.zoneRadius * 0.8f : 14f + 14f * g.thermal, 0.5f, 0, 0);
        }

        int end = 0;
        float ext = 0f;
        for(int i = 0; i < ip.count; i++){
            end = Math.max(end, ip.start[i] + ip.dur[i]);
            ip.cost += WEIGHT[ip.kind[i]] * (ip.n[i] > 0 ? Math.max(1, (ip.n[i] + 3) / 4) : 1);
            float e = ip.size[i] * 1.3f;
            if(ip.kind[i] >= ECHO_FLASH) e += s.echoSpread * 1.5f;
            ext = Math.max(ext, e);
        }
        ip.tier = end <= TIER_LIFE[0] ? 0 : (end <= TIER_LIFE[1] ? 1 : 2);
        ip.lifetime = TIER_LIFE[ip.tier];
        ip.extent = ext + 6f;
        return ip;
    }

    /** Versatz des Echos i in Welt-X relativ zum Einschlag; rot = Flugrichtung in Grad (identisch fuer Darstellung und echtes Echo-Geschoss). */
    public static float echoX(int i, int seed, float spread, float rot){
        float fwd = spread * (0.35f + 0.65f * Rnd.f(seed, 100 + i)), side = spread * Rnd.s(seed, 200 + i);
        double r = Math.toRadians(rot);
        return (float)(fwd * Math.cos(r) - side * Math.sin(r));
    }

    public static float echoY(int i, int seed, float spread, float rot){
        float fwd = spread * (0.35f + 0.65f * Rnd.f(seed, 100 + i)), side = spread * Rnd.s(seed, 200 + i);
        double r = Math.toRadians(rot);
        return (float)(fwd * Math.sin(r) + side * Math.cos(r));
    }

    /** Fuer Tests: Gestalt des Plans (Phasenfolge und grobe Groessen). */
    public String fingerprint(){
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < count; i++) sb.append(KIND_NAMES[kind[i]]).append('@').append(start[i]).append('x').append(n[i]).append('/').append(Math.round(size[i] / 4f)).append(';');
        return sb.toString();
    }

    /** Anzahl unterschiedlicher Phasenarten (Silhouettenvielfalt). */
    public int distinctKinds(){
        int mask = 0;
        for(int i = 0; i < count; i++) mask |= 1 << kind[i];
        return Integer.bitCount(mask);
    }
}
