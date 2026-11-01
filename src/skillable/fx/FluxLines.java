package skillable.fx;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.graphics.*;
import skillable.flux.*;

/**
 * Zeichnet Flux-Verbindungen wie die Strom-Laser des Spiels (Vanilla-Laser-Textur), plus
 * laufende Impulse in Flussrichtung (schneller bei mehr Last) und Instabilitaets-Blitze.
 */
public final class FluxLines{
    private FluxLines(){}

    /**
     * @param load 0..1 aktueller Anteil der Kapazitaet, der genutzt wird (steuert Dicke und Tempo)
     * @param instability 0..1 (steuert Farbe weiss und Blitze)
     * @param down Netz in EMP-Abschaltung oder ohne Betrieb -> rot/gedimmt/flackernd
     * @param forward true: Impulse laufen von (x1,y1) nach (x2,y2)
     */
    public static void link(float x1, float y1, float x2, float y2, float load, float instability,
                            boolean down, boolean forward, int seed){
        float len = Mathf.dst(x2 - x1, y2 - y1);
        if(len < 1f) return;
        float l = FluxMath.clamp01(load);
        Color c = down ? FluxEffects.red : FluxEffects.colorFor(FluxEffects.intensity(l, instability));
        float flicker = down ? 0.25f + 0.2f * Mathf.absin(Time.time + seed * 13f, 5f, 1f) : 1f;

        //Grundlaser im Stil der Strom-Laser
        TextureRegion laser = Core.atlas.find("laser"), end = Core.atlas.find("laser-end");
        Draw.color(c, (0.55f + 0.2f * l) * flicker);
        if(laser.found() && end.found()){
            Drawf.laser(laser, end, end, x1, y1, x2, y2, 0.55f + 0.45f * l, false);
        }else{
            Lines.stroke(1f + l);
            Lines.line(x1, y1, x2, y2);
        }
        if(down){ Draw.reset(); return; }

        //Laufende Impulse (additiv)
        Draw.blend(Blending.additive);
        int n = Mathf.clamp((int)(len / 20f), 2, 40);
        float base = Time.time / 60f * (0.35f + 1.4f * l);
        float dirAng = Mathf.angle(x2 - x1, y2 - y1);
        for(int i = 0; i < n; i++){
            float t = (base + i / (float)n) % 1f;
            if(!forward) t = 1f - t;
            float px = Mathf.lerp(x1, x2, t), py = Mathf.lerp(y1, y2, t);
            float fade = Mathf.sin(t * Mathf.PI); //weich ein- und ausblenden
            Draw.color(Color.white, c, 0.35f);
            Draw.alpha(0.35f + 0.65f * fade);
            float size = 1.1f + 1.9f * l;
            Drawf.tri(px, py, size * 1.6f, size * 2.6f, forward ? dirAng : dirAng + 180f);
            Draw.color(c, 0.25f * fade);
            Fill.circle(px, py, size * 2.2f);
        }

        //Instabilitaet: zuckende Blitze entlang der Leitung
        if(instability >= InstabilityModel.WARNING_AT){
            float strength = FluxMath.clamp01((instability - InstabilityModel.WARNING_AT) / (1f - InstabilityModel.WARNING_AT));
            int frame = (int)(Time.time / 3f);
            Draw.color(Color.white, c, 0.4f);
            Draw.alpha(0.5f + 0.5f * strength);
            Lines.stroke(0.8f + strength);
            int segs = Mathf.clamp((int)(len / 12f), 3, 30);
            float px = x1, py = y1;
            float nx = -(y2 - y1) / len, ny = (x2 - x1) / len;
            for(int i = 1; i <= segs; i++){
                float t = i / (float)segs;
                float off = i == segs ? 0f : Mathf.randomSeed(seed * 977L + frame * 31L + i, -1f, 1f) * (2f + 5f * strength);
                float qx = Mathf.lerp(x1, x2, t) + nx * off, qy = Mathf.lerp(y1, y2, t) + ny * off;
                if(Mathf.randomSeed(seed * 131L + frame * 7L + i, 0f, 1f) < 0.35f + 0.5f * strength) Lines.line(px, py, qx, qy);
                px = qx; py = qy;
            }
        }
        Draw.blend();
        Draw.reset();
    }
}
