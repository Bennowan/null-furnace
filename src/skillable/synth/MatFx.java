package skillable.synth;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import mindustry.entities.*;
import mindustry.graphics.*;
import skillable.material.*;

import static arc.graphics.g2d.Draw.*;
import static arc.graphics.g2d.Lines.*;

/**
 * Wiederverwendbare Treffer- und Muendungseffekte, EIN statisches Objekt je Projektilform (Effekt-IDs bleiben deterministisch).
 * Parametrisiert ueber {@code e.color} (Leuchtfarbe) und {@code e.data} ({@link ShotSpec}) - keine Allokation pro Material.
 */
public final class MatFx{
    private MatFx(){}

    public static final Effect[] hit = new Effect[5], shoot = new Effect[5];
    /** Mehrstufiger Einschlag in drei Dauerstufen (je ein statischer Effekt; Inhalt kommt aus dem ImpactPlan in e.data). */
    public static final Effect[] impact = new Effect[3];
    /** Lastbudget: bei vielen gleichzeitigen Reaktionen wird die Darstellung automatisch vereinfacht. */
    public static final FxBudget budget = new FxBudget();

    static float size(Effect.EffectContainer e){
        return e.data instanceof ShotSpec s ? Math.max(s.width, s.splashRadius * 0.25f) : 8f;
    }

    static{
        for(int i = 0; i < impact.length; i++){
            impact[i] = new Effect(ImpactPlan.TIER_LIFE[i], 260f, e -> {
                if(!(e.data instanceof ImpactPlan p)) return;
                //Rotation codiert Richtung (0..360), Stufe (je 360) und "ohne Echos" (ab 3 x 360)
                int v = (int)(e.rotation / 360f);
                float rot = e.rotation - 360f * v;
                ImpactRenderer.draw(ArcCanvas.I, p, e.time, e.id, rot, e.x, e.y, v % 3, v >= 3);
                Draw.reset();
            });
        }
        //SLUG: kurze, harte Funken und ein Schlagblitz
        hit[ShotSpec.SLUG] = new Effect(16f, e -> {
            color(Color.white, e.color, e.fin());
            stroke(e.fout() * 1.6f);
            Angles.randLenVectors(e.id, 7, 3f + 16f * e.finpow(), e.rotation + 180f, 55f, (x, y) -> lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 1f + e.fout() * 4f));
            Fill.circle(e.x, e.y, e.fout() * 2.6f);
        });
        //BOLT: gezackte Entladungsbogen
        hit[ShotSpec.BOLT] = new Effect(22f, e -> {
            color(e.color, Color.white, e.fout());
            stroke(0.8f + e.fout() * 1.2f);
            Mathf.rand.setSeed(e.id);
            for(int i = 0; i < 4; i++){
                float ang = Mathf.rand.random(360f), px = e.x, py = e.y, len = (6f + 12f * e.finpow());
                for(int s = 0; s < 4; s++){
                    ang += Mathf.rand.range(40f);
                    float nx = px + Angles.trnsx(ang, len / 4f), ny = py + Angles.trnsy(ang, len / 4f);
                    line(px, py, nx, ny);
                    px = nx; py = ny;
                }
            }
            circle(e.x, e.y, 3f + 7f * e.finpow());
        });
        //EMBER: Feuerring und aufsteigende Glut
        hit[ShotSpec.EMBER] = new Effect(34f, e -> {
            color(Color.white, e.color, e.fin() * 0.8f);
            Draw.alpha(e.fout());
            stroke(e.fout() * 2.4f);
            circle(e.x, e.y, 5f + size(e) * 1.2f * e.finpow());
            color(Color.valueOf("ffcf6a"), Color.valueOf("ff5a1a"), e.fin());
            Angles.randLenVectors(e.id, 8, 4f + size(e) * e.finpow(), (x, y) -> Fill.circle(e.x + x, e.y + y + e.fin() * 6f, 0.8f + e.fout() * 2.2f));
        });
        //SHELL: Druckwelle, Feuerball und Truemmer
        hit[ShotSpec.SHELL] = new Effect(42f, e -> {
            float r = e.data instanceof ShotSpec s ? s.splashRadius : 50f;
            color(Color.valueOf("ffd27a"), Color.valueOf("ff5a1a"), e.fin());
            Fill.circle(e.x, e.y, r * 0.35f * e.fout(Interp.pow3Out));
            color(e.color);
            stroke(e.fout() * 3.5f);
            circle(e.x, e.y, r * e.fin(Interp.pow3Out));
            color(Color.gray);
            Angles.randLenVectors(e.id, 9, r * 0.9f * e.finpow(), (x, y) -> lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 1f + e.fout() * 5f));
        });
        //RIFT: Implosion mit Rautensplittern
        hit[ShotSpec.RIFT] = new Effect(30f, e -> {
            color(e.color, Color.white, e.fout() * 0.6f);
            stroke(e.fout() * 2f);
            circle(e.x, e.y, 16f * e.fout(Interp.pow2In) + 2f);
            for(int i = 0; i < 4; i++){
                float a = e.rotation + i * 90f + e.fin() * 40f, d = 14f * e.fout(Interp.pow2In);
                Drawf.tri(e.x + Angles.trnsx(a, d), e.y + Angles.trnsy(a, d), 3f * e.fout() + 1f, 6f * e.fout() + 2f, a + 180f);
            }
        });

        shoot[ShotSpec.SLUG] = new Effect(10f, e -> {
            color(Color.white, e.color, e.fin());
            stroke(e.fout() * 1.4f);
            Angles.randLenVectors(e.id, 4, 2f + 10f * e.finpow(), e.rotation, 25f, (x, y) -> lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fout() * 4f));
        });
        shoot[ShotSpec.BOLT] = new Effect(12f, e -> {
            color(e.color, Color.white, e.fout());
            stroke(e.fout() * 1.6f);
            for(int i = 0; i < 3; i++) lineAngle(e.x, e.y, e.rotation + (i - 1) * 28f, 4f + 8f * e.finpow());
        });
        shoot[ShotSpec.EMBER] = new Effect(18f, e -> {
            color(Color.valueOf("ffcf6a"), e.color, e.fin());
            Angles.randLenVectors(e.id, 5, 2f + 9f * e.finpow(), e.rotation, 30f, (x, y) -> Fill.circle(e.x + x, e.y + y, 0.6f + e.fout() * 2f));
        });
        shoot[ShotSpec.SHELL] = new Effect(22f, e -> {
            color(Color.gray, Color.darkGray, e.fin());
            Draw.alpha(0.6f * e.fout());
            Angles.randLenVectors(e.id, 5, 3f + 12f * e.finpow(), e.rotation, 40f, (x, y) -> Fill.circle(e.x + x, e.y + y, 1.5f + e.fout() * 3f));
        });
        shoot[ShotSpec.RIFT] = new Effect(16f, e -> {
            color(e.color, Color.white, e.fout());
            stroke(e.fout() * 1.8f);
            circle(e.x + Angles.trnsx(e.rotation, 4f), e.y + Angles.trnsy(e.rotation, 4f), 2f + 7f * e.finpow());
        });
    }

    /** Spielt den Einschlag eines Materials. {@code withEchoes}: die echten Folgereaktionen wurden ausgeloest. */
    public static void impact(ImpactPlan plan, float x, float y, float rot, Color color, boolean withEchoes){
        int lod = budget.spawn(plan.cost, arc.util.Time.time);
        impact[plan.tier].at(x, y, rot + 360f * (lod + (withEchoes ? 0 : 3)), color, plan);
    }
}
