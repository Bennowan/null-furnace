package skillable.synth;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import skillable.material.*;

import static mindustry.Vars.*;

/** Nachbrennende Zone eines thermischen Materials. Radius, Dauer, Schaden und Status kommen aus dem {@link ShotSpec}. */
public class MatZone extends BulletType{
    public ShotSpec spec;
    public final Color col = new Color(1f, 0.55f, 0.2f);

    public MatZone(){
        super(0f, 0f);
        collides = false;
        collidesTiles = false;
        hittable = false;
        absorbable = false;
        keepVelocity = false;
        hitEffect = Fx.none;
        despawnEffect = Fx.none;
        hitSound = mindustry.gen.Sounds.none;
        lifetime = 1f;
    }

    @Override
    public void update(Bullet b){
        if(spec == null || spec.zoneRadius <= 0f) return;
        if(b.timer(1, 15f)){
            Damage.damage(b.team, b.x, b.y, spec.zoneRadius, spec.zoneDamage, false, true, true);
            Damage.status(b.team, b.x, b.y, spec.zoneRadius, spec.melt > 0f ? StatusEffects.melting : StatusEffects.burning, Math.max(60f, spec.burn * 0.5f), true, true);
            if(spec.makeFire) Fires.create(world.tileWorld(b.x + Mathf.range(spec.zoneRadius * 0.6f), b.y + Mathf.range(spec.zoneRadius * 0.6f)));
        }
    }

    @Override
    public void draw(Bullet b){
        if(spec == null) return;
        float fade = Mathf.clamp(b.fout() * 3f);
        float r = spec.zoneRadius;
        Draw.z(Layer.bullet - 1f);
        Draw.color(col, 0.18f * fade);
        Fill.circle(b.x, b.y, r * (0.85f + 0.1f * Mathf.absin(Time.time, 7f, 1f)));
        Draw.color(Color.white, col, 0.5f);
        Draw.alpha(0.5f * fade);
        Lines.stroke(1.2f);
        Lines.circle(b.x, b.y, r * 0.8f);
        Draw.reset();
    }
}
