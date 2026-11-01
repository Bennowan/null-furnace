package skillable.fx;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import skillable.flux.*;

import static arc.graphics.g2d.Draw.*;
import static arc.graphics.g2d.Lines.*;

/** Farben und Engine-Effekte (keine Shader). Intensitaet 0..1: blau -> violett -> weiss. */
public final class FluxEffects{
    private FluxEffects(){}

    public static final Color blue = Color.valueOf("4aa8ff"), purple = Color.valueOf("9a5cff"),
        white = Color.white, warn = Color.valueOf("ffd36b"), red = Color.valueOf("ff5a5a");

    private static final Color tmp = new Color();

    /** Farbe fuer eine Intensitaet 0..1 (blau niedrig, violett mittel, weiss kritisch). */
    public static Color colorFor(float intensity){
        float i = FluxMath.clamp01(intensity);
        if(i < 0.6f) return tmp.set(blue).lerp(purple, i / 0.6f);
        return tmp.set(purple).lerp(white, (i - 0.6f) / 0.4f);
    }

    /**
     * Visuelle Intensitaet aus ECHTEN Werten: Last treibt nur bis ~0.55 (violett),
     * Weiss entsteht ausschliesslich durch tatsaechliche Instabilitaet.
     */
    public static float intensity(float load, float instability){
        return FluxMath.clamp01(0.55f * FluxMath.clamp01(load) + 1.0f * Math.max(0f, instability - 0.35f) / 0.65f);
    }

    public static final Effect spark = new Effect(18f, e -> {
        color(blue, white, e.fin());
        stroke(e.fout() * 1.4f);
        for(int i = 0; i < 3; i++){
            float a = e.rotation + Mathf.randomSeed(e.id + i, -50f, 50f);
            lineAngle(e.x, e.y, a, 2f + e.fin() * 7f);
        }
    });

    public static final Effect leak = new Effect(26f, e -> {
        color(purple, white, e.fin());
        stroke(e.fout() * 1.6f);
        float a = Mathf.randomSeed(e.id, 360f);
        lineAngle(e.x + Angles.trnsx(a, e.fin() * 9f), e.y + Angles.trnsy(a, e.fin() * 9f), a, 3f + e.fout() * 5f);
        Fill.circle(e.x, e.y, e.fout() * 1.6f);
    });

    public static final Effect hitSmall = new Effect(14f, e -> {
        color(blue, white, e.fin());
        stroke(e.fout() * 1.2f);
        circle(e.x, e.y, 1f + e.fin() * 4f);
    });

    public static final Effect shootFlash = new Effect(12f, e -> {
        color(white, blue, e.fin());
        Drawf.tri(e.x, e.y, 4f * e.fout(), 14f * e.fout(), e.rotation);
        Drawf.tri(e.x, e.y, 4f * e.fout(), 4f * e.fout(), e.rotation + 180f);
    });

    public static final Effect blast = new Effect(40f, e -> {
        color(Color.valueOf("ffb35a"), Color.valueOf("ff5a1a"), e.fin());
        stroke(e.fout() * 3f);
        circle(e.x, e.y, e.fin() * 70f);
        Fill.circle(e.x, e.y, e.fout() * 18f);
    });

    public static final Effect blink = new Effect(26f, e -> {
        color(purple, white, e.fout());
        stroke(e.fout() * 2f);
        circle(e.x, e.y, e.fin() * 16f);
        circle(e.x, e.y, e.fin() * 9f);
    });

    public static final Effect stormHit = new Effect(24f, e -> {
        color(white, blue, e.fin());
        stroke(e.fout() * 2f);
        circle(e.x, e.y, e.fin() * 40f);
        for(int i = 0; i < 6; i++){
            lineAngle(e.x, e.y, i * 60f + e.id * 7f, e.fin() * 30f, e.fout() * 8f);
        }
    });

    /** Kollaps: grosser, aber kurzer Ring plus Funken. */
    public static final Effect collapse = new Effect(50f, e -> {
        color(white, purple, e.fin());
        stroke(e.fout() * 4f);
        circle(e.x, e.y, e.fin() * 44f);
        color(purple);
        stroke(e.fout() * 2f);
        circle(e.x, e.y, e.fin() * 26f);
    });

    public static final Effect empRing = new Effect(60f, e -> {
        color(blue, white, e.fout() * 0.5f);
        stroke(e.fout() * 2.5f);
        circle(e.x, e.y, e.fin() * 90f);
    });

    public static final Effect strike = new Effect(48f, e -> {
        color(white, purple, e.fin());
        stroke(e.fout() * 5f);
        circle(e.x, e.y, e.fin() * 130f);
        Fill.circle(e.x, e.y, e.fout() * 34f);
        for(int i = 0; i < 12; i++){
            lineAngle(e.x, e.y, i * 30f, e.fin() * 100f, e.fout() * 18f);
        }
    });

    /** Strahl vom Turm zum Overclock-Ziel (rotation = Laenge, data = Ziel ueber x2/y2 nicht verfuegbar -> Linie ueber lifetime). */
    public static final Effect beam = new Effect(24f, e -> {
        if(!(e.data instanceof arc.math.geom.Vec2 v)) return;
        color(white, purple, e.fin());
        stroke(e.fout() * 6f);
        line(e.x, e.y, v.x, v.y);
        stroke(e.fout() * 2.5f);
        color(white);
        line(e.x, e.y, v.x, v.y);
    });

    /** Fuser: Ergebnis fertig (Farbe = Item-Farbe ueber Effect.color). */
    public static final Effect craft = new Effect(34f, e -> {
        color(e.color, white, e.fout() * 0.6f);
        stroke(e.fout() * 2.5f);
        circle(e.x, e.y, 6f + e.fin() * 26f);
        Fill.circle(e.x, e.y, e.fout() * 6f);
        for(int i = 0; i < 8; i++) lineAngle(e.x, e.y, i * 45f + 10f, 6f + e.fin() * 22f, e.fout() * 6f);
    });

    /** Fuser: Charge fertig - bleibt komplett INNERHALB des 3x3-Blocks (Halbbreite 12 Einheiten). */
    public static final Effect fuserDone = new Effect(30f, e -> {
        color(e.color, white, e.fout() * 0.6f);
        stroke(e.fout() * 1.6f);
        circle(e.x, e.y, 3f + e.fin() * 7f);
        Fill.circle(e.x, e.y, e.fout() * 3.2f);
        for(int i = 0; i < 6; i++) lineAngle(e.x + Angles.trnsx(i * 60f, 3f), e.y + Angles.trnsy(i * 60f, 3f), i * 60f, 1f + e.fin() * 4f, e.fout() * 2.5f);
    });
}
