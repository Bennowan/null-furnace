package skillable.material;

/**
 * Konkrete Schusswerte eines synthetischen Materials - reine Funktion des {@link MaterialProfile} (ohne Engine, testbar).
 * Die PRIMAERE Klasse bestimmt Projektilform, Bewegung und Grundverhalten; die SEKUNDAERE Klasse fuegt Zusatzmodule hinzu
 * (z. B. Kinetik + Elektrik = durchschlagende Slugs mit Kettenblitzen). Alle Werte folgen kontinuierlich aus den Profilwerten.
 */
public final class ShotSpec{
    public static final int SLUG = 0, BOLT = 1, EMBER = 2, SHELL = 3, RIFT = 4;
    public static final String[] FORM_NAMES = {"slug", "bolt", "ember", "shell", "rift"};

    public int form, secondary, sides;
    public float speed, lifetime, range, damage, width, height, reloadMul, knockback, buildingMul;
    public int pierceCap;
    public boolean pierceBuilding, pierceArmor, ground = true, air = true, artillery;
    public float homingPower, homingRange, weaveMag, weaveScale, accel, drag, spiral;
    public int lightning, lightningLength;
    public float lightningDamage, shieldBreak;
    public float splashDamage, splashRadius;
    public boolean makeFire;
    public int frags;
    public float fragDamage, fragSpread;
    /** Statuseffekte in Ticks (0 = keiner). */
    public float burn, melt, freeze, electrify, slow;
    public float zoneRadius, zoneTime, zoneDamage;
    public float displace, hitShake;
    /**
     * Folgereaktionen (ECHOS): zeitversetzte, ECHTE Wirkung am Einschlag - Folgeexplosionen (Flaechenschaden) oder Entladungen (Blitze).
     * Die Darstellung in {@link ImpactPlan} verwendet exakt diese Werte (Anzahl, Verzoegerung, Radius, Versatz).
     */
    public int echoCount, echoLightning, echoLightningLength;
    public boolean echoArc;
    public float echoDelay, echoDelayStep, echoRadius, echoDamage, echoSpread;
    public int trailLength;
    public float trailWidth;
    /** Farben (RGB 0..1): Kern, Rand, Leuchten. */
    public float[] front = new float[3], back = new float[3], glow = new float[3];

    public static ShotSpec from(MaterialProfile p){
        ShotSpec s = new ShotSpec();
        float kin = p.classScore[MaterialProfile.CLASS_KINETIC], ele = p.classScore[MaterialProfile.CLASS_ELECTRIC],
            the = p.classScore[MaterialProfile.CLASS_THERMAL], exp = p.classScore[MaterialProfile.CLASS_EXPLOSIVE],
            flx = p.classScore[MaterialProfile.CLASS_FLUX];
        float D = p.density, H = p.hardness, pw = p.power;
        s.form = p.primary;
        s.secondary = p.secondary;
        s.sides = 3 + (p.seed & 3);
        s.buildingMul = 1f;
        s.trailWidth = 1.2f;
        float targetRange = 215f;

        switch(s.form){
            case SLUG -> {
                s.speed = 7.2f - 3.4f * D;
                s.damage = 16f + 90f * (float)Math.pow(0.5f * D + 0.5f * H, 1.2) * (0.6f + 0.4f * pw);
                s.width = 4f + 5f * D;
                s.height = 10f + 8f * H;
                s.pierceCap = H > 0.55f ? 1 + Math.round(3f * (H - 0.5f)) : 0;
                s.pierceArmor = H > 0.7f;
                s.knockback = 0.5f + 3f * D;
                s.frags = Math.round(5f * D * (1f - H));
                s.fragDamage = 8f + 14f * kin;
                s.fragSpread = 70f;
                s.reloadMul = 1.4f - 0.9f * D;
                s.buildingMul = 0.8f;
                s.trailLength = 6;
                targetRange = 215f + 30f * H;
            }
            case BOLT -> {
                s.speed = 7.5f + 4f * ele;
                s.damage = 10f + 40f * (0.4f * ele + 0.3f * pw);
                s.width = 4f; s.height = 12f;
                s.lightning = 2 + Math.round(5f * ele);
                s.lightningLength = 8 + Math.round(14f * ele);
                s.lightningDamage = 8f + 30f * ele * pw;
                s.electrify = 90f + 150f * ele;
                s.shieldBreak = 40f + 120f * ele;
                s.weaveMag = 1.2f + 2f * (1f - D);
                s.weaveScale = 4f;
                s.pierceCap = ele > 0.7f ? 1 : 0;
                s.reloadMul = 1.2f + 1.0f * ele;
                s.buildingMul = 1.3f;
                s.trailLength = 10;
                s.trailWidth = 1.0f;
                targetRange = 200f;
            }
            case EMBER -> {
                s.speed = 4.4f - 1.4f * D;
                s.drag = 0.004f;
                s.damage = 14f + 50f * the * pw;
                s.width = 7f + 5f * the; s.height = s.width;
                s.splashRadius = 20f + 30f * the;
                s.splashDamage = 20f + 90f * the * pw;
                s.makeFire = true;
                s.burn = 120f + 240f * the;
                s.melt = p.temp > 0.5f ? 90f + 120f * (p.temp - 0.5f) * 2f : 0f;
                s.zoneRadius = 26f + 30f * the;
                s.zoneTime = 120f + 180f * the;
                s.zoneDamage = 6f + 16f * the;
                s.reloadMul = 0.9f;
                s.trailLength = 14;
                s.trailWidth = 2.2f;
                s.accel = 0f;
                targetRange = 205f;
            }
            case SHELL -> {
                s.speed = 2.8f + 0.8f * D;
                s.artillery = true;
                s.air = false;
                s.damage = 0f;
                s.splashDamage = 90f + 330f * exp * pw;
                s.splashRadius = 40f + 70f * exp;
                s.knockback = 1f + 2.5f * exp;
                s.hitShake = 1f + 3f * exp;
                s.frags = 2 + Math.round(6f * exp);
                s.fragDamage = 10f + 24f * exp;
                s.fragSpread = 360f;
                s.makeFire = p.flammability > 0.3f;
                s.width = 12f + 7f * exp; s.height = s.width;
                s.buildingMul = 0.8f;
                s.reloadMul = 0.9f - 0.6f * exp;
                s.trailLength = 8;
                s.trailWidth = 2f;
                targetRange = 225f + 25f * exp;
            }
            default -> { //RIFT
                s.speed = 7.5f;
                s.pierceCap = 4 + Math.round(6f * flx);
                s.pierceBuilding = true;
                s.pierceArmor = true;
                s.damage = 30f + 90f * flx * pw;
                s.width = 8f + 3f * flx; s.height = 16f;
                s.slow = 60f + 120f * flx;
                s.displace = 4f + 8f * flx;
                s.homingPower = 0.02f + 0.05f * flx;
                s.homingRange = 120f;
                s.spiral = 2.2f + 2.4f * flx;
                s.reloadMul = 0.5f + 0.5f * (1f - flx);
                s.trailLength = 16;
                s.trailWidth = 2.2f;
                targetRange = 215f + 20f * flx;
            }
        }

        //Sekundaerklasse: Zusatzmodule (bestehende Werte werden nur ergaenzt/angehoben)
        switch(s.secondary){
            case MaterialProfile.CLASS_KINETIC -> {
                s.pierceCap = Math.max(s.pierceCap, 1 + (H > 0.5f ? 1 : 0));
                s.knockback += 0.6f * kin;
                s.damage += 8f * kin;
            }
            case MaterialProfile.CLASS_ELECTRIC -> {
                s.lightning = Math.max(s.lightning, 2 + Math.round(3f * ele));
                s.lightningLength = Math.max(s.lightningLength, 8 + Math.round(8f * ele));
                s.lightningDamage = Math.max(s.lightningDamage, 6f + 16f * ele);
                s.electrify = Math.max(s.electrify, 90f + 90f * ele);
                s.shieldBreak = Math.max(s.shieldBreak, 40f + 60f * ele);
                s.buildingMul = Math.max(s.buildingMul, 1.15f);
            }
            case MaterialProfile.CLASS_THERMAL -> {
                s.burn = Math.max(s.burn, 120f + 120f * the);
                s.makeFire = true;
                s.splashRadius = Math.max(s.splashRadius, 18f + 14f * the);
                s.splashDamage = Math.max(s.splashDamage, 12f + 40f * the);
            }
            case MaterialProfile.CLASS_EXPLOSIVE -> {
                s.splashRadius = Math.max(s.splashRadius, 26f + 26f * exp);
                s.splashDamage = Math.max(s.splashDamage, 30f + 120f * exp);
                s.hitShake = Math.max(s.hitShake, 1.2f);
            }
            case MaterialProfile.CLASS_FLUX -> {
                s.pierceArmor = true;
                s.slow = Math.max(s.slow, 45f + 60f * flx);
                s.homingPower = Math.max(s.homingPower, 0.03f * flx);
                s.homingRange = Math.max(s.homingRange, 90f);
            }
            default -> {}
        }
        //Kalte Mischungen frieren: Wirkung aus dem Kaelteanteil, unabhaengig von der Klasse
        if(p.cold > 0.15f) s.freeze = 60f + 240f * Math.min(1f, p.cold);
        //Leitfaehige Materialien stoeren Schilde auch ohne Elektro-Klasse
        if(p.conductivity > 0.6f) s.shieldBreak = Math.max(s.shieldBreak, 30f * p.conductivity);

        //Folgereaktionen: energiereiche, instabile Mischungen reagieren nach dem Einschlag weiter (wirkliche Folgeschaeden)
        float instab = Math.min(1f, p.peakHeat);
        if(pw > 0.45f && (instab > 0.3f || exp > 0.5f)){
            s.echoCount = 1 + (instab > 0.6f ? 1 : 0) + (pw > 0.8f && instab > 0.5f ? 1 : 0);
            s.echoArc = ele > 0.4f && instab > 0.4f;
            s.echoDelay = 12f;
            s.echoDelayStep = 7f;
            s.echoSpread = 12f + 14f * pw;
            s.echoRadius = 20f + 24f * pw;
            float basis = s.splashDamage > 0f ? s.splashDamage : 40f * pw + 20f;
            s.echoDamage = 0.25f * basis;
            if(s.echoArc){
                s.echoLightning = 2 + Math.round(2f * ele);
                s.echoLightningLength = 8 + Math.round(8f * ele);
            }
        }

        //Reichweite -> Lebensdauer (beruecksichtigt Beschleunigung und Luftwiderstand)
        s.range = targetRange;
        s.lifetime = lifetimeFor(targetRange, s.speed, s.accel, s.drag);
        s.reloadMul = Math.max(0.2f, s.reloadMul);

        //Farben aus der Zusammensetzung
        float[] c = {p.r, p.g, p.b};
        for(int i = 0; i < 3; i++){
            s.back[i] = c[i];
            s.front[i] = c[i] + (1f - c[i]) * 0.65f;
        }
        float[] tint = switch(s.form){
            case BOLT -> new float[]{0.55f, 0.75f, 1f};
            case EMBER -> new float[]{1f, 0.55f, 0.2f};
            case SHELL -> new float[]{1f, 0.78f, 0.35f};
            case RIFT -> new float[]{0.78f, 0.55f, 1f};
            default -> new float[]{1f, 1f, 1f};
        };
        for(int i = 0; i < 3; i++) s.glow[i] = 0.5f * c[i] + 0.5f * tint[i];
        return s;
    }

    /** Anzahl Ticks, bis ein Geschoss mit Anfangsgeschwindigkeit speed (mit accel/drag) die Strecke range zurueckgelegt hat. */
    public static float lifetimeFor(float range, float speed, float accel, float drag){
        float dist = 0f, v = Math.max(0.1f, speed);
        for(int t = 1; t < 1200; t++){
            dist += v;
            v = Math.max(0.2f, v * (1f - drag) + accel);
            if(dist >= range) return t;
        }
        return 1200f;
    }

    /** Fuer Tests: stabiler Fingerabdruck der gameplay-relevanten Werte (ohne Farben). */
    public String behaviourKey(){
        return form + "/" + secondary + "/" + pierceCap + "/" + pierceBuilding + "/" + pierceArmor + "/" + lightning + "/" + (int)(splashRadius / 10f)
            + "/" + frags + "/" + (zoneRadius > 0f) + "/" + (burn > 0f) + "/" + (freeze > 0f) + "/" + (slow > 0f) + "/" + (shieldBreak > 0f) + "/" + (displace > 0f) + "/" + echoCount + (echoArc ? "a" : "b");
    }
}
