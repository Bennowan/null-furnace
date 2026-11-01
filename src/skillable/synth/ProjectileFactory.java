package skillable.synth;

import mindustry.content.*;
import mindustry.entities.bullet.*;
import skillable.material.*;

/** Legt pro Material-Slot beim Laden die Geschosstypen an und konfiguriert sie aus dem Profil. */
public final class ProjectileFactory{
    private ProjectileFactory(){}

    public static final float TURRET_RANGE = 200f;
    public static final MatBullet[] main = new MatBullet[MaterialRegistry.SLOTS];
    public static final BasicBulletType[] frag = new BasicBulletType[MaterialRegistry.SLOTS];
    public static final MatZone[] zone = new MatZone[MaterialRegistry.SLOTS];
    public static final MatEcho[] echo = new MatEcho[MaterialRegistry.SLOTS];

    /** Beim Laden aufrufen (Reihenfolge fest -> Content-IDs auf Server und Clients gleich). */
    public static void load(){
        for(int i = 0; i < MaterialRegistry.SLOTS; i++){
            frag[i] = new BasicBulletType(3.5f, 10f){{
                width = 4f; height = 6f; lifetime = 22f;
                hitEffect = Fx.hitBulletColor;
                despawnEffect = Fx.none;
                shootEffect = Fx.none;
                smokeEffect = Fx.none;
            }};
            zone[i] = new MatZone();
            echo[i] = new MatEcho();
            main[i] = new MatBullet();
            main[i].zone = zone[i];
            main[i].echo = echo[i];
            main[i].clear();
        }
    }

    public static MatBullet bullet(int slot){ return main[slot]; }

    /** Konfiguriert den Slot aus dem Profil (null = Slot leeren). */
    public static void configure(int slot, MaterialProfile p){
        if(main[slot] == null) return;
        if(p == null){ main[slot].clear(); zone[slot].spec = null; echo[slot].clear(); return; }
        ShotSpec s = ShotSpec.from(p);
        MatBullet b = main[slot];
        BasicBulletType f = frag[slot];
        f.damage = s.fragDamage;
        f.frontColor = b.cFront; f.backColor = b.cBack; f.hitColor = b.cGlow;
        f.speed = 3.5f;
        zone[slot].spec = s;
        zone[slot].lifetime = Math.max(1f, s.zoneTime);
        zone[slot].col.set(s.glow[0], s.glow[1], s.glow[2], 1f);
        echo[slot].configure(s);
        b.configure(s, p, f, TURRET_RANGE);
    }
}
