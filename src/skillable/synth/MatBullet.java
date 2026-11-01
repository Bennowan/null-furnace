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
import mindustry.world.blocks.defense.ForceProjector;
import skillable.material.*;

/**
 * Geschoss eines synthetischen Materials. Pro Slot existiert genau EINE Instanz (beim Laden angelegt, damit Content-IDs im
 * Multiplayer deterministisch bleiben); {@link ProjectileFactory} konfiguriert sie in place aus Profil und {@link ShotSpec}.
 *
 * Mechanik (Schaden, Kollision, Blitze, Splitter, Echos) und Darstellung sind getrennt: gezeichnet wird ausschliesslich ueber
 * {@link FlightPlan}/{@link ImpactPlan} (reine, getestete Logik); die Spielwirkung kommt aus den Engine-Feldern dieses Geschosses
 * und den {@link MatEcho}-Folgereaktionen.
 */
public class MatBullet extends BasicBulletType{
    public ShotSpec spec;
    public FlightPlan flight;
    public ImpactPlan plan;
    public MatZone zone;
    public MatEcho echo;
    public final Color cFront = new Color(), cBack = new Color(), cGlow = new Color();
    /** Diagnose: Anzahl der Treffer dieses Geschosstyps seit Spielstart (fuer Integrationstests). */
    public int hits;

    public MatBullet(){
        super(1f, 0f);
        hitEffect = Fx.none;
        despawnEffect = Fx.none;
        shootEffect = Fx.none;
        smokeEffect = Fx.none;
        hitSound = mindustry.gen.Sounds.none;
        despawnSound = mindustry.gen.Sounds.none;
        trailChance = 0f;
        lightning = 0;
    }

    //------------------------------------------------------------------ Lebenszyklus
    @Override
    public void init(Bullet b){
        super.init(b);
        if(spec != null) MatFx.shoot[spec.form].at(b.x, b.y, b.rotation(), cGlow, spec);
    }

    @Override
    public void update(Bullet b){
        super.update(b);
        if(spec == null) return;
        if(spec.form == ShotSpec.RIFT){ //Spiralbahn: Flugrichtung pendelt, pro Geschoss phasenverschoben
            b.vel.rotate(Mathf.cos(b.time + b.id * 3f, 5f, spec.spiral) * Time.delta);
        }
    }

    /**
     * Zentrale Treffermethode: in Mindustry 160.7 laufen Kollisionen UND Detonationen am Flugzeitende (Artillerie, Blitz-/Splitter-Geschosse
     * mit despawnHit) ueber diese vierstellige Variante. Die dreistellige {@code hit(b, x, y)} ruft sie nur auf; wer sie allein ueberschreibt,
     * verpasst alle Despawn-Detonationen.
     */
    @Override
    public void hit(Bullet b, float x, float y, boolean createFrags){
        super.hit(b, x, y, createFrags);
        if(spec == null || plan == null) return;
        //Folgereaktionen genau einmal je Geschoss, beim ersten Treffer (bei Durchschlag-Geschossen ist dann collided.size() == 1);
        //die Darstellung zeigt sie nur, wenn sie wirklich ausgeloest werden
        boolean echoes = spec.echoCount > 0 && echo != null && b.collided.size <= 1;
        hits++;
        MatFx.impact(plan, x, y, b.rotation(), cGlow, echoes);
        if(spec.form == ShotSpec.EMBER && zone != null && spec.zoneRadius > 0f) zone.create(b, x, y, 0f);
        if(spec.hitShake > 0f) Effect.shake(spec.hitShake, spec.hitShake, x, y);
        if(echoes){
            for(int i = 0; i < spec.echoCount; i++){
                float ex = x + ImpactPlan.echoX(i, plan.seed, spec.echoSpread, b.rotation());
                float ey = y + ImpactPlan.echoY(i, plan.seed, spec.echoSpread, b.rotation());
                float delay = spec.echoDelay + spec.echoDelayStep * i;
                echo.create(b.owner, b.team, ex, ey, 0f, -1f, 1f, delay / echo.lifetime, null);
            }
        }
    }

    @Override
    public void despawned(Bullet b){
        super.despawned(b);
        //Geschosse ohne Aufprall-Detonation verglimmen sichtbar statt einfach zu verschwinden
        if(spec != null && !despawnHit) MatFx.hit[spec.form].at(b.x, b.y, b.rotation(), cGlow, spec);
    }

    @Override
    public void hitEntity(Bullet b, Hitboxc entity, float health){
        super.hitEntity(b, entity, health);
        if(spec == null || !(entity instanceof Unit u)) return;
        if(spec.burn > 0f) u.apply(StatusEffects.burning, spec.burn);
        if(spec.melt > 0f) u.apply(StatusEffects.melting, spec.melt);
        if(spec.freeze > 0f) u.apply(StatusEffects.freezing, spec.freeze);
        if(spec.electrify > 0f) u.apply(StatusEffects.electrified, spec.electrify);
        if(spec.slow > 0f) u.apply(StatusEffects.slow, spec.slow);
        if(spec.displace > 0f) u.impulse(Tmp.v3.set(b.x, b.y).sub(u.x, u.y).nor().scl(spec.displace * 18f)); //zieht zum Einschlag
    }

    @Override
    public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
        super.hitTile(b, build, x, y, initialHealth, direct);
        if(spec != null && spec.shieldBreak > 0f && build instanceof ForceProjector.ForceBuild fb && build.team != b.team){
            fb.buildup += spec.shieldBreak; //leitfaehige Materialien stoeren Schutzschilde
        }
    }

    //------------------------------------------------------------------ Darstellung (reine Logik in FlightRenderer)
    @Override
    public void draw(Bullet b){
        if(flight == null) return;
        drawTrail(b);
        Draw.z(Layer.bullet + 0.01f);
        int n = Groups.bullet.size();
        FlightRenderer.draw(ArcCanvas.I, flight, b.x, b.y, b.rotation(), b.time, b.id, n > 600 ? 2 : (n > 300 ? 1 : 0));
        Draw.reset();
    }

    @Override
    public void drawLight(Bullet b){
        if(spec != null && (spec.form == ShotSpec.EMBER || spec.form == ShotSpec.BOLT || spec.form == ShotSpec.RIFT)) super.drawLight(b);
    }

    /** Uebernimmt die Werte des Spec in dieses Geschoss (und das Splitter-Geschoss). */
    public void configure(ShotSpec s, MaterialProfile p, BulletType fragType, float turretRange){
        this.spec = s;
        this.flight = FlightPlan.build(p, s);
        this.plan = ImpactPlan.build(p, s);
        cFront.set(s.front[0], s.front[1], s.front[2], 1f);
        cBack.set(s.back[0], s.back[1], s.back[2], 1f);
        cGlow.set(s.glow[0], s.glow[1], s.glow[2], 1f);
        frontColor = cFront; backColor = cBack; hitColor = cGlow; trailColor = cGlow; lightningColor = cGlow;
        speed = s.speed; lifetime = s.lifetime; damage = s.damage;
        width = s.width; height = s.height; hitSize = Math.max(3f, s.width * 0.8f);
        drag = s.drag; accel = s.accel;
        knockback = s.knockback; buildingDamageMultiplier = s.buildingMul;
        reloadMultiplier = s.reloadMul;
        rangeChange = s.range - turretRange;
        pierce = s.pierceCap > 0; pierceCap = s.pierceCap > 0 ? s.pierceCap : -1;
        pierceBuilding = s.pierceBuilding; pierceArmor = s.pierceArmor;
        collidesAir = s.air; collidesGround = true;
        collides = !s.artillery; collidesTiles = !s.artillery; scaleLife = s.artillery;
        despawnHit = s.artillery || s.form == ShotSpec.EMBER;
        hittable = s.form != ShotSpec.RIFT; absorbable = s.form != ShotSpec.RIFT;
        homingPower = s.homingPower; homingRange = s.homingRange;
        weaveMag = s.weaveMag; weaveScale = s.weaveScale > 0f ? s.weaveScale : 1f;
        lightning = s.lightning; lightningLength = s.lightningLength; lightningDamage = s.lightningDamage;
        splashDamage = s.splashDamage; splashDamageRadius = s.splashRadius;
        makeFire = s.makeFire;
        hitShake = 0f; //Schuettelwirkung wird in hit() ausgeloest
        fragBullets = s.frags; fragBullet = s.frags > 0 ? fragType : null;
        fragRandomSpread = s.fragSpread; fragVelocityMin = 0.5f; fragVelocityMax = 1.3f; fragLifeMin = 0.6f;
        trailLength = s.trailLength; trailWidth = s.trailWidth;
        status = StatusEffects.none;
        init();
    }

    /** Neutralisiert das Geschoss (Slot ohne Material). */
    public void clear(){
        spec = null; flight = null; plan = null;
        damage = 0f; splashDamage = 0f; lightning = 0; fragBullets = 0; fragBullet = null; trailLength = 0; rangeChange = 0f;
    }
}
