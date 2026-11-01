package skillable.synth;

import mindustry.content.*;
import mindustry.entities.bullet.*;
import skillable.material.*;

/**
 * Echo-Reaktion: unsichtbares, ruhendes Geschoss, das nach kurzer Verzoegerung am Einschlagsort ECHTEN Schaden verursacht
 * (Folgeexplosion oder Entladung). Die Darstellung zeichnet {@link ImpactPlan}; dieses Geschoss liefert die tatsaechliche Wirkung.
 * Pro Slot existiert genau eine Instanz (vorab angelegt, deterministische IDs).
 */
public class MatEcho extends BulletType{
    public MatEcho(){
        super(0f, 0f);
        collides = false;
        collidesTiles = false;
        hittable = false;
        absorbable = false;
        keepVelocity = false;
        despawnHit = true;
        lifetime = 12f;
        hitEffect = Fx.none;
        despawnEffect = Fx.none;
        shootEffect = Fx.none;
        smokeEffect = Fx.none;
        hitSound = mindustry.gen.Sounds.none;
        despawnSound = mindustry.gen.Sounds.none;
        clear();
    }

    public void clear(){
        splashDamage = 0f; splashDamageRadius = 0f; lightning = 0; damage = 0f;
    }

    public void configure(ShotSpec s){
        clear();
        if(s.echoCount <= 0) return;
        buildingDamageMultiplier = 0.8f;
        if(s.echoArc){
            lightning = s.echoLightning;
            lightningLength = s.echoLightningLength;
            lightningDamage = s.echoDamage;
            lightningColor = new arc.graphics.Color(s.glow[0], s.glow[1], s.glow[2], 1f);
        }else{
            splashDamage = s.echoDamage;
            splashDamageRadius = s.echoRadius;
        }
        init();
    }
}
