package skillable.material;

import static skillable.material.ImpactPlan.*;

/**
 * Zeichnet einen {@link ImpactPlan} zum Zeitpunkt t (Ticks seit dem Einschlag) auf ein {@link Canvas}. Zustandslos und allokationsfrei:
 * alle Zufallswerte kommen aus {@link Rnd}, alle Geometrie wird pro Bild neu berechnet. Nach {@code plan.lifetime} wird nichts mehr gezeichnet,
 * jede Geometrie bleibt innerhalb von {@code plan.extent}. {@code skipEcho}: Folgereaktionen weglassen (z. B. Treffer eines Durchschlag-Geschosses ohne Echo). {@code lod} 0 voll, 1 reduziert, 2 minimal (Kern der Reaktion bleibt erkennbar).
 */
public final class ImpactRenderer{
    private ImpactRenderer(){}

    static float ease(float u){ float v = 1f - u; return 1f - v * v * v; }
    static float cos(float deg){ return (float)Math.cos(Math.toRadians(deg)); }
    static float sin(float deg){ return (float)Math.sin(Math.toRadians(deg)); }

    public static void draw(Canvas c, ImpactPlan p, float t, int id, float rot, float x, float y, int lod, boolean skipEcho){
        if(!(t >= 0f) || t >= p.lifetime) return;
        int seed = p.seed + id * 31;
        for(int k = 0; k < p.count; k++){
            float local = t - p.start[k];
            if(local < 0f || local >= p.dur[k]) continue;
            int kind = p.kind[k];
            if(skipEcho && kind >= ECHO_FLASH) continue; //Folgereaktionen nur zeichnen, wenn die echten Echos wirklich ausgeloest wurden
            if(lod >= 2 && !(kind == FLASH || kind == RING || kind == FIREBALL || kind == ARCS || kind == IMPLODE
                || kind == ECHO_FLASH || kind == ECHO_RING || kind == ECHO_ARC)) continue;
            float u = local / p.dur[k], fo = 1f - u, e = ease(u), sz = p.size[k], in = p.inten[k];
            int num = p.n[k];
            if(lod >= 1) num = Math.max(num > 0 ? 1 : 0, num / 2);
            if(lod >= 2 && num > 2 && (kind == ARCS || kind == ECHO_ARC)) num = 2;
            switch(kind){
                case FLASH -> {
                    c.additive(true);
                    c.color(1f, 1f, 1f, 0.9f * fo);
                    c.fillCircle(x, y, sz * (0.35f + 0.65f * (float)Math.sqrt(u)) * fo + 0.5f);
                    c.color(p.glow[0], p.glow[1], p.glow[2], 0.7f * fo);
                    c.fillCircle(x, y, sz * 0.9f * e * fo + 0.5f);
                    if(lod == 0) for(int i = 0; i < 4; i++) c.line(x, y, x + cos(rot + i * 90f + 45f) * sz * fo, y + sin(rot + i * 90f + 45f) * sz * fo, 1.2f * fo + 0.3f);
                    c.additive(false);
                }
                case RING -> {
                    c.additive(true);
                    c.color(p.glow[0], p.glow[1], p.glow[2], 0.85f * fo * in);
                    c.ring(x, y, sz * e, 1f + 3f * fo * in);
                    if(lod == 0){
                        c.color(1f, 1f, 1f, 0.5f * fo);
                        c.ring(x, y, sz * e * 0.88f, 0.8f);
                    }
                    c.additive(false);
                }
                case FIREBALL -> {
                    float r = sz * (0.5f + 0.5f * e);
                    c.color(p.hot[0] + (p.glow[0] - p.hot[0]) * u, p.hot[1] + (p.glow[1] - p.hot[1]) * u, p.hot[2] + (p.glow[2] - p.hot[2]) * u, 0.75f * (float)Math.pow(fo, 0.8));
                    c.fillCircle(x, y, r);
                    if(lod == 0){
                        c.color(1f, 0.95f, 0.8f, 0.8f * fo);
                        c.fillCircle(x, y, r * 0.5f * fo);
                    }
                }
                case ARCS -> arcs(c, x, y, sz * e, num, seed, t, fo, p, 0f, 360f, 0);
                case SHARDS -> {
                    for(int i = 0; i < num; i++){
                        float ang = (i & 1) == 0 ? Rnd.f(seed, i) * 360f : rot + 180f + Rnd.s(seed, i) * 60f;
                        float d = sz * e * (0.5f + 0.5f * Rnd.f(seed, i + 50));
                        float px = x + cos(ang) * d, py = y + sin(ang) * d;
                        if(p.tag[k] == 1) c.color(p.hot[0], p.hot[1] + 0.3f * fo, p.hot[2], fo); else c.color(p.pale[0], p.pale[1], p.pale[2], fo);
                        c.tri(px, py, 2.5f * (fo + 0.2f), 4f + 3f * Rnd.f(seed, i + 80), ang + t * 14f * Rnd.s(seed, i + 20));
                    }
                }
                case STREAKS -> {
                    for(int i = 0; i < num; i++){
                        float ang = (i & 1) == 0 ? rot + 180f + Rnd.s(seed, i) * 55f : Rnd.f(seed, i) * 360f;
                        float r0 = sz * 0.15f * e, r1 = r0 + sz * 0.3f * fo + 2f * Rnd.f(seed, i + 9);
                        c.additive(true);
                        c.color(p.pale[0], p.pale[1], p.pale[2], fo * in);
                        c.line(x + cos(ang) * r0, y + sin(ang) * r0, x + cos(ang) * Math.min(sz, r0 + r1), y + sin(ang) * Math.min(sz, r0 + r1), 0.8f + fo);
                        c.additive(false);
                    }
                }
                case EMBERS -> {
                    c.additive(true);
                    for(int i = 0; i < num; i++){
                        float a = Rnd.f(seed, i) * 360f, r = sz * 0.55f * Rnd.f(seed, i + 30);
                        float px = x + cos(a) * r, py = y + sin(a) * r + e * sz * 0.4f;
                        c.color(p.hot[0], p.hot[1] * (1f - 0.5f * u), p.hot[2] * (1f - u), fo);
                        c.fillCircle(px, py, 0.6f + 1.6f * fo * Rnd.f(seed, i + 60));
                    }
                    c.additive(false);
                }
                case VAPOR, SMOKE -> {
                    boolean smoke = kind == SMOKE;
                    for(int i = 0; i < num; i++){
                        float a = Rnd.f(seed, i + 5) * 360f, r = sz * 0.3f * Rnd.f(seed, i + 15);
                        float px = x + cos(a) * r, py = y + sin(a) * r + e * sz * 0.4f;
                        if(smoke) c.color(0.25f, 0.25f, 0.27f, 0.32f * fo * in); else c.color(0.75f, 0.85f, 0.95f, 0.25f * fo * in);
                        c.fillCircle(px, py, sz * 0.28f * (0.5f + e));
                    }
                }
                case IMPLODE -> {
                    c.additive(true);
                    c.color(p.glow[0], p.glow[1], p.glow[2], 0.8f * fo);
                    float ro = sz * fo, ri = ro * 0.55f;
                    int lines = lod >= 1 ? 4 : num;
                    for(int i = 0; i < lines; i++){
                        float a = rot + i * (360f / lines);
                        c.line(x + cos(a) * ro, y + sin(a) * ro, x + cos(a) * ri, y + sin(a) * ri, 1f + u);
                    }
                    c.ring(x, y, ro, 1f + 2f * u);
                    c.additive(false);
                }
                case DROPS -> {
                    for(int i = 0; i < num; i++){
                        float a = rot + 180f + Rnd.s(seed, i) * 100f, d = sz * e * (0.4f + 0.6f * Rnd.f(seed, i + 40));
                        float px = x + cos(a) * d, py = y + sin(a) * d - 0.35f * sz * u * u;
                        c.color(p.pale[0], p.pale[1], p.pale[2], 0.9f * fo);
                        c.fillCircle(px, py, 1f + 1.4f * fo);
                        c.line(px, py, px - cos(a) * 2.5f, py - sin(a) * 2.5f, 0.8f);
                    }
                }
                case FROST -> {
                    float grow = Math.min(1f, u * 3f), fade = u > 0.6f ? (1f - u) / 0.4f : 1f;
                    for(int i = 0; i < num; i++){
                        float a = i * 360f / Math.max(1, num) + Rnd.s(seed, i) * 20f;
                        c.color(0.75f, 0.92f, 1f, 0.9f * fade);
                        c.tri(x, y, 3f, sz * grow * (0.5f + 0.5f * Rnd.f(seed, i + 70)), a);
                    }
                }
                case FIELD -> {
                    c.additive(true);
                    c.color(p.glow[0], p.glow[1], p.glow[2], 0.7f * fo * in);
                    for(int i = 0; i < num; i++) c.arc(x, y, sz * (0.45f + 0.55f * e), 0.18f, t * 9f + i * 120f, 1.3f);
                    c.additive(false);
                }
                case BLOOM -> {
                    c.additive(true);
                    c.color(p.hot[0], p.hot[1], p.hot[2], 0.22f * fo * in);
                    c.fillCircle(x, y, sz * (0.3f + 0.7f * e));
                    c.additive(false);
                }
                case ECHO_FLASH -> {
                    float ex = x + ImpactPlan.echoX(p.tag[k], p.seed, p.echoSpread, rot), ey = y + ImpactPlan.echoY(p.tag[k], p.seed, p.echoSpread, rot);
                    c.additive(true);
                    c.color(1f, 1f, 1f, 0.9f * fo);
                    c.fillCircle(ex, ey, sz * fo + 0.5f);
                    c.additive(false);
                }
                case ECHO_RING -> {
                    float ex = x + ImpactPlan.echoX(p.tag[k], p.seed, p.echoSpread, rot), ey = y + ImpactPlan.echoY(p.tag[k], p.seed, p.echoSpread, rot);
                    c.additive(true);
                    c.color(p.glow[0], p.glow[1], p.glow[2], 0.7f * fo);
                    c.ring(ex, ey, sz * e, 1f + 2f * fo);
                    c.additive(false);
                }
                default -> { //ECHO_ARC
                    float ex = x + ImpactPlan.echoX(p.tag[k], p.seed, p.echoSpread, rot), ey = y + ImpactPlan.echoY(p.tag[k], p.seed, p.echoSpread, rot);
                    arcs(c, ex, ey, sz * e, num, seed + 997 * (p.tag[k] + 1), t, fo, p, 0f, 360f, 1);
                }
            }
        }
    }

    /** Verzweigte, flackernde Entladungen vom Punkt (x,y); Laenge hoechstens reach. */
    static void arcs(Canvas c, float x, float y, float reach, int num, int seed, float t, float fo, ImpactPlan p, float from, float span, int salt){
        int flick = (int)(t / 2f);
        c.additive(true);
        for(int a = 0; a < num; a++){
            float ang = from + Rnd.f(seed, a * 7 + salt) * span, px = x, py = y, step = reach / 4f;
            c.color(1f, 1f, 1f, 0.5f * fo + 0.3f);
            for(int sgm = 0; sgm < 4; sgm++){
                ang += Rnd.s(seed + flick * 13, a * 11 + sgm) * 40f;
                float nx = px + cos(ang) * step, ny = py + sin(ang) * step;
                c.line(px, py, nx, ny, 1f + 0.8f * fo);
                px = nx; py = ny;
            }
        }
        c.color(p.glow[0], p.glow[1], p.glow[2], 0.6f * fo);
        c.ring(x, y, Math.min(reach * 0.35f, 6f) + 1f, 1f);
        c.additive(false);
    }
}
