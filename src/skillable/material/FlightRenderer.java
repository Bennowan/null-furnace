package skillable.material;

/**
 * Zeichnet das Flugbild ({@link FlightPlan}) eines Geschosses. Zustandslos, allokationsfrei, alle Zufallswerte aus {@link Rnd}.
 * Die Spuren sind rein analytisch (keine gespeicherten Partikellisten) und koennen daher nicht wachsen oder Reste hinterlassen.
 */
public final class FlightRenderer{
    private FlightRenderer(){}

    static float cos(float deg){ return (float)Math.cos(Math.toRadians(deg)); }
    static float sin(float deg){ return (float)Math.sin(Math.toRadians(deg)); }

    /** lod 0 voll, 1 reduziert (<= 2 Schichten, weniger Partikel), 2 minimal (Kern und eine Spur). */
    public static void draw(Canvas c, FlightPlan p, float x, float y, float rot, float time, int id, int lod){
        float dx = cos(rot), dy = sin(rot), px = -dy, py = dx;
        float pulse = 1f + p.pulseAmp * sin(time * p.pulseHz * 360f + id * 40f);
        float flick = p.flicker > 0f ? Math.max(0.3f, 1f + p.flicker * (Rnd.f(id, (int)time) - 0.5f) * 2f) : 1f;
        float k = pulse * flick;
        float w = p.width * k, h = p.height * k;

        if(p.halo > 0f){
            c.additive(true);
            c.color(p.glow[0], p.glow[1], p.glow[2], 0.28f * flick);
            c.fillCircle(x, y, w * p.halo);
            c.additive(false);
        }

        //Spuren: Schichten mit eigener Laenge/Breite; bei Instabilitaet/Leitfaehigkeit/Feld geflochten
        int layers = lod >= 2 ? 1 : (lod == 1 ? Math.min(2, p.layers) : p.layers);
        int segs = lod >= 2 ? 3 : 5;
        for(int li = 0; li < layers; li++){
            float len = p.layerLen[li], amp = p.layerAmp[li];
            float ox = x, oy = y;
            for(int j = 1; j <= segs; j++){
                float q = j / (float)segs, d = q * len;
                float off = (float)Math.sin(time * 0.35f + j * 0.9f + li * 2.1f + id) * amp * q;
                float nx = x - dx * d + px * off, ny = y - dy * d + py * off;
                float a = (1f - q) * (li == 0 ? 0.7f : 0.45f);
                if(li == 0) c.color(p.glow[0], p.glow[1], p.glow[2], a); else c.color(p.front[0], p.front[1], p.front[2], a);
                c.additive(true);
                c.line(ox, oy, nx, ny, p.layerWidth[li] * (1f - 0.7f * q) + 0.3f);
                c.additive(false);
                ox = nx; oy = ny;
            }
        }

        //abgeloeste Partikel (Zustand nur ueber time/id)
        if(lod < 2 && p.shed != FlightPlan.SHED_NONE){
            int num = lod == 1 ? Math.max(1, p.shedCount / 2) : p.shedCount;
            for(int i = 0; i < num; i++){
                float u = (time * 0.05f * p.shedRate + Rnd.f(id, i)) % 1f;
                float back = u * (p.layerLen[0] + 8f), lat = Rnd.s(id, i + 20) * u * 5f, fo = 1f - u;
                float sx = x - dx * back + px * lat, sy = y - dy * back + py * lat;
                switch(p.shed){
                    case FlightPlan.SHED_EMBER -> {
                        c.additive(true);
                        c.color(1f, 0.55f + 0.3f * fo, 0.2f, fo);
                        c.fillCircle(sx, sy, 0.6f + 1.2f * fo);
                        c.additive(false);
                    }
                    case FlightPlan.SHED_SHARD -> {
                        c.color(p.front[0], p.front[1], p.front[2], fo);
                        c.tri(sx, sy, 2f, 3.5f, time * 12f * Rnd.s(id, i + 40) + i * 60f);
                    }
                    case FlightPlan.SHED_DROPLET -> {
                        c.color(p.front[0], p.front[1], p.front[2], 0.9f * fo);
                        c.fillCircle(sx, sy - 2f * u * u, 0.9f + 0.9f * fo);
                    }
                    case FlightPlan.SHED_SPARK -> {
                        c.additive(true);
                        c.color(1f, 1f, 1f, fo);
                        c.line(sx, sy, sx + px * Rnd.s(id, i + 60) * 3f, sy + py * Rnd.s(id, i + 60) * 3f, 0.8f);
                        c.additive(false);
                    }
                    default -> {
                        c.additive(true);
                        c.color(p.glow[0], p.glow[1], p.glow[2], fo);
                        c.ring(sx, sy, 1.2f + 1.5f * u, 0.7f);
                        c.additive(false);
                    }
                }
            }
        }

        //Kern je Form
        switch(p.form){
            case ShotSpec.SLUG -> {
                c.color(p.back[0], p.back[1], p.back[2], 1f);
                c.tri(x, y, w, h, rot);
                c.tri(x, y, w * 0.8f, h * 0.45f, rot + 180f);
                c.color(p.front[0], p.front[1], p.front[2], 1f);
                c.tri(x, y, w * 0.5f, h * 0.8f, rot);
            }
            case ShotSpec.BOLT -> {
                float len = h * 1.4f, sx = x - dx * len * 0.5f, sy = y - dy * len * 0.5f;
                for(int pass = 0; pass < 2; pass++){
                    if(pass == 0) c.color(p.glow[0], p.glow[1], p.glow[2], 1f); else c.color(1f, 1f, 1f, 1f);
                    c.additive(true);
                    float cx = sx, cy = sy;
                    for(int s = 1; s <= 5; s++){
                        float t = s / 5f, j = s < 5 ? Rnd.s(id * 31 + (int)(time / 2f), s) * (pass == 0 ? 3f : 2f) : 0f;
                        float nx = sx + dx * len * t + px * j, ny = sy + dy * len * t + py * j;
                        c.line(cx, cy, nx, ny, pass == 0 ? 2.4f : 1f);
                        cx = nx; cy = ny;
                    }
                    c.additive(false);
                }
                c.color(1f, 1f, 1f, 1f);
                c.fillCircle(x, y, 1.6f);
            }
            case ShotSpec.EMBER -> {
                c.additive(true);
                c.color(p.glow[0], p.glow[1], p.glow[2], 0.35f);
                c.fillCircle(x, y, w * 1.5f);
                c.additive(false);
                c.color(p.back[0], p.back[1], p.back[2], 1f);
                c.fillCircle(x, y, w);
                c.color(p.front[0], p.front[1], p.front[2], 1f);
                c.fillCircle(x, y, w * 0.5f);
            }
            case ShotSpec.SHELL -> {
                float r = w * 0.5f;
                c.color(p.back[0], p.back[1], p.back[2], 1f);
                c.poly(x, y, p.sides, r, rot + p.spin * time);
                c.color(p.front[0], p.front[1], p.front[2], 1f);
                c.poly(x, y, p.sides, r * 0.55f, rot + p.spin * time);
                c.color(p.glow[0], p.glow[1], p.glow[2], 1f);
                c.polyLine(x, y, p.sides, r * 1.1f, rot + p.spin * time, 1f);
            }
            default -> { //RIFT
                float spin = time * 6f + id * 40f;
                c.additive(true);
                c.color(p.glow[0], p.glow[1], p.glow[2], 0.5f);
                c.fillCircle(x, y, w * 1.1f);
                c.additive(false);
                c.color(p.back[0], p.back[1], p.back[2], 1f);
                c.poly(x, y, 4, w, spin);
                c.color(p.front[0], p.front[1], p.front[2], 1f);
                c.poly(x, y, 4, w * 0.55f, spin + 45f);
                c.color(p.glow[0], p.glow[1], p.glow[2], 1f);
                c.ring(x, y, w * 1.4f, 1.3f);
            }
        }

        if(lod >= 2) return;

        //Energiebaender (mehrphasige Projektile): gegenlaeufig rotierende Ringe in wechselnden Farben
        for(int b = 0; b < p.bands; b++){
            c.additive(true);
            if((b & 1) == 0) c.color(p.glow[0], p.glow[1], p.glow[2], 0.8f); else c.color(p.front[0], p.front[1], p.front[2], 0.8f);
            c.polyLine(x, y, 3 + b + (p.sides & 1), p.width * (1.5f + 0.6f * b), (b & 1) == 0 ? time * 7f : -time * 9f, 1f);
            c.additive(false);
        }
        if(p.fieldArcs > 0f){
            c.additive(true);
            c.color(p.glow[0], p.glow[1], p.glow[2], 0.7f);
            for(int i = 0; i < 2; i++) c.arc(x, y, p.width * 2.2f, 0.16f, time * 8f + i * 180f, 1.2f);
            c.additive(false);
        }
        //Entladungen entlang der Flugbahn (nur bei echter Blitzwirkung des Materials)
        if(lod == 0 && p.arcs > 0){
            int fl = (int)(time / 2f);
            c.additive(true);
            c.color(1f, 1f, 1f, 0.8f);
            for(int a = 0; a < p.arcs; a++){
                float ang = Rnd.f(id + fl * 13, a) * 360f, ox = x, oy = y;
                for(int sg = 0; sg < 3; sg++){
                    ang += Rnd.s(id + fl * 7, a * 5 + sg) * 50f;
                    float nx = ox + cos(ang) * p.arcLen / 3f, ny = oy + sin(ang) * p.arcLen / 3f;
                    c.line(ox, oy, nx, ny, 0.9f);
                    ox = nx; oy = ny;
                }
            }
            c.additive(false);
        }
    }
}
