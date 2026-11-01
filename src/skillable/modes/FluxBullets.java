package skillable.modes;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.pattern.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.blocks.defense.ForceProjector;
import mindustry.entities.units.*;
import skillable.fx.*;

import static mindustry.Vars.*;

/** Geschosstypen der sieben Modi und des Overclock-Schlags (nur echte Engine-Mechaniken). */
public final class FluxBullets{
    private FluxBullets(){}

    /**
     * Abgeschwaechte Kopie fuer den Normalstrom-Betrieb: weniger Schaden, kuerzere Reichweite.
     * (Die Feuerrate wird im Turm ueber POWER_RATE reduziert.)
     */
    public static BulletType weakened(BulletType b, float damageScale, float rangeScale){
        BulletType c = b.copy();
        c.damage *= damageScale;
        c.splashDamage *= damageScale;
        c.lightningDamage = c.lightningDamage < 0f ? c.lightningDamage : c.lightningDamage * damageScale;
        c.rangeChange = (b.rangeChange + 200f) * rangeScale - 200f; //Turm-Basisreichweite 200 skaliert
        if(c instanceof RailBulletType r){
            r.length *= rangeScale;
        }
        c.lifetime *= Math.max(0.7f, rangeScale);
        return c;
    }

    /** Kupfer / Rapid: sehr hohe Feuerrate, wenig Schaden, kurze Reichweite, kleine Blitzboegen. */
    public static BulletType rapid(){
        return new BasicBulletType(7.5f, 7f){{
            width = 5f; height = 10f;
            lifetime = 22f;
            frontColor = Color.white;
            backColor = FluxEffects.blue;
            reloadMultiplier = 8f;
            rangeChange = -50f;
            lightning = 1;
            lightningLength = 4;
            lightningDamage = 4f;
            lightningColor = FluxEffects.blue;
            hitEffect = FluxEffects.hitSmall;
            despawnEffect = FluxEffects.hitSmall;
            shootEffect = FluxEffects.shootFlash;
            smokeEffect = Fx.none;
            hitSound = Sounds.none;
            trailLength = 6;
            trailWidth = 1.2f;
            trailColor = FluxEffects.blue;
            ammoMultiplier = 1f;
        }};
    }

    /** Graphit / Rail: langsam, sehr hoher Einzelschaden, Durchschlag, hohe Genauigkeit. */
    public static BulletType rail(){
        return new RailBulletType(){{
            length = 330f;
            damage = 120f;
            pierceDamageFactor = 0.35f;
            reloadMultiplier = 0.35f;
            rangeChange = 140f;
            buildingDamageMultiplier = 0.7f;
            pierceEffect = FluxEffects.hitSmall;
            lineEffect = FluxEffects.spark;
            pointEffect = FluxEffects.spark;
            endEffect = FluxEffects.hitSmall;
            pointEffectSpace = 40f;
            shootEffect = FluxEffects.shootFlash;
            smokeEffect = Fx.none;
        }};
    }

    /** Kohle / Incendiary: Flaechenschaden plus zeitlich begrenzte Brandzone. */
    public static BulletType incendiary(){
        BulletType zone = new FireZoneBulletType(240f, 34f, 14f, 20f);
        return new ArtilleryBulletType(3f, 0f){{
            lifetime = 70f;
            width = height = 11f;
            collidesTiles = false;
            collides = false;
            collidesAir = false;
            scaleLife = true;
            splashDamage = 45f;
            splashDamageRadius = 42f;
            reloadMultiplier = 0.7f;
            rangeChange = 40f;
            makeFire = true;
            status = StatusEffects.burning;
            statusDuration = 60f * 4f;
            frontColor = Color.valueOf("ffd27a");
            backColor = Color.valueOf("ff6a2a");
            hitEffect = Fx.flakExplosion;
            shootEffect = FluxEffects.shootFlash;
            fragBullets = 0;
            //Brandzone entsteht an der Einschlagstelle
            despawnEffect = Fx.flakExplosion;
        }
            @Override
            public void despawned(Bullet b){
                super.despawned(b);
                if(b.absorbed) return;
                zone.create(b, b.team, b.x, b.y, 0f);
            }
        };
    }

    /** Silizium / Smart: lenkfaehige Raketen; bevorzugt schnelle und fliegende Ziele. */
    public static BulletType smart(){
        return new MissileBulletType(3.3f, 14f){{
            width = 7f; height = 11f;
            lifetime = 58f;
            homingPower = 0.12f;
            homingRange = 150f;
            splashDamage = 20f;
            splashDamageRadius = 28f;
            reloadMultiplier = 2.2f;
            rangeChange = 20f;
            collidesGround = true;
            collidesAir = true;
            frontColor = Color.white;
            backColor = FluxEffects.purple;
            trailColor = FluxEffects.purple;
            trailLength = 14;
            hitEffect = FluxEffects.hitSmall;
            shootEffect = FluxEffects.shootFlash;
            //Prioritaet: fliegend und schnell zuerst; kleiner Abstand als Tiebreak. Kleinerer Wert = frueher.
            unitSort = (u, x, y) -> -(u.type.speed + (u.isFlying() ? 6f : 0f)) + u.dst2(x, y) / 1_000_000f;
        }};
    }

    /** Blast Compound / Catastrophic: sehr hoher Flaechenschaden, grosse Effekte. */
    public static BulletType catastrophic(){
        return new ArtilleryBulletType(2.7f, 0f){{
            lifetime = 90f;
            width = height = 18f;
            collidesTiles = false;
            collides = false;
            collidesAir = false;
            scaleLife = true;
            splashDamage = 260f;
            splashDamageRadius = 95f;
            buildingDamageMultiplier = 0.8f;
            reloadMultiplier = 0.16f;
            rangeChange = 70f;
            makeFire = true;
            knockback = 2.2f;
            hitShake = 3f;
            frontColor = Color.white;
            backColor = Color.valueOf("ff5a1a");
            hitEffect = FluxEffects.blast;
            despawnEffect = FluxEffects.blast;
            shootEffect = FluxEffects.shootFlash;
        }};
    }

    /** Phase Fabric / Reality: Raumverzerrung, durchdringt Ziele und Gebaeude, ignoriert Panzerung. */
    public static BulletType reality(){
        return new BasicBulletType(8f, 90f){{
            width = 8f; height = 18f;
            lifetime = 38f;
            pierce = true;
            pierceCap = 8;
            pierceBuilding = true;
            pierceArmor = true;
            absorbable = false;
            hittable = false;
            reloadMultiplier = 0.45f;
            rangeChange = 60f;
            frontColor = Color.white;
            backColor = FluxEffects.purple;
            trailColor = FluxEffects.purple;
            trailLength = 18;
            trailWidth = 2.2f;
            hitEffect = FluxEffects.blink;
            despawnEffect = FluxEffects.blink;
            shootEffect = FluxEffects.blink;
            status = StatusEffects.slow;
            statusDuration = 90f;
        }
            @Override
            public void hitEntity(Bullet b, Hitboxc entity, float health){
                super.hitEntity(b, entity, health);
                //Raumverzerrung: Ziel wird entgegen der Schussrichtung verschoben
                if(entity instanceof Unit u && !u.isPlayer() && u.hittable()){
                    Tmp.v3.set(b.vel).nor().scl(-9f);
                    u.impulse(Tmp.v3.x * 8f, Tmp.v3.y * 8f);
                    FluxEffects.blink.at(u.x, u.y);
                }
            }

            @Override
            public void update(Bullet b){
                super.update(b);
                if(b.timer(2, 7f)) FluxEffects.blink.at(b.x, b.y);
            }
        };
    }

    /** Surge Alloy / Storm: Kettenblitze, EMP-Stoerung von Schildprojektoren, Elektro-Status. */
    public static BulletType storm(){
        return new BasicBulletType(9f, 40f){{
            width = 7f; height = 14f;
            lifetime = 30f;
            reloadMultiplier = 0.6f;
            rangeChange = 30f;
            lightning = 5;
            lightningLength = 14;
            lightningDamage = 32f;
            lightningColor = Color.white;
            splashDamage = 70f;
            splashDamageRadius = 70f;
            status = StatusEffects.electrified;
            statusDuration = 60f * 3f;
            frontColor = Color.white;
            backColor = FluxEffects.blue;
            trailColor = FluxEffects.blue;
            trailLength = 10;
            hitEffect = FluxEffects.stormHit;
            despawnEffect = FluxEffects.stormHit;
            shootEffect = FluxEffects.shootFlash;
        }
            @Override
            public void hit(Bullet b, float x, float y){
                super.hit(b, x, y);
                //EMP: feindliche Schildprojektoren in Reichweite verlieren Schildenergie
                indexer.eachBlock(null, x, y, 90f, o -> o.team != b.team && o instanceof ForceProjector.ForceBuild,
                    o -> ((ForceProjector.ForceBuild)o).buildup += 90f);
            }
        };
    }


    /** Carbon Conductor: sehr leitfaehig - schnelle Schuesse mit starken Kettenblitzen, durchschlaegt zwei Ziele, mehr Schaden an Gebaeuden. */
    public static BulletType conductor(){
        return new BasicBulletType(8.5f, 20f){{
            width = 5f; height = 13f;
            lifetime = 24f;
            reloadMultiplier = 1.6f;
            pierce = true;
            pierceCap = 2;
            buildingDamageMultiplier = 1.35f;
            lightning = 4;
            lightningLength = 12;
            lightningDamage = 16f;
            lightningColor = Color.valueOf("9fc0ff");
            status = StatusEffects.electrified;
            statusDuration = 90f;
            frontColor = Color.white;
            backColor = Color.valueOf("7fa8ff");
            trailColor = Color.valueOf("7fa8ff");
            trailLength = 8;
            hitEffect = FluxEffects.stormHit;
            shootEffect = FluxEffects.shootFlash;
        }};
    }

    /** Ferrite Composite: schwere Panzerbrecher-Slugs; ignorieren Panzerung, durchschlagen, zerfallen in Splitter. */
    public static BulletType ferrite(){
        BulletType shard = new BasicBulletType(3.2f, 14f){{
            width = 4f; height = 7f;
            lifetime = 16f;
            frontColor = Color.valueOf("ffd9b8");
            backColor = Color.valueOf("a8694a");
            hitEffect = Fx.hitBulletSmall;
        }};
        return new BasicBulletType(6.2f, 85f){{
            width = 9f; height = 19f;
            lifetime = 40f;
            reloadMultiplier = 0.55f;
            rangeChange = 40f;
            pierce = true;
            pierceCap = 2;
            pierceArmor = true;
            knockback = 3f;
            frontColor = Color.valueOf("ffd9b8");
            backColor = Color.valueOf("a8694a");
            fragBullet = shard;
            fragBullets = 5;
            fragRandomSpread = 80f;
            hitEffect = Fx.flakExplosion;
            shootEffect = FluxEffects.shootFlash;
        }};
    }

    /** Synaptic Alloy: lernende Schwarmraketen - starke Zielsuche, verteilen waehrend des Flugs Mini-Raketen. */
    public static BulletType synaptic(){
        BulletType mini = new MissileBulletType(3.2f, 8f){{
            width = 5f; height = 8f;
            lifetime = 42f;
            homingPower = 0.30f;
            homingRange = 220f;
            splashDamage = 10f;
            splashDamageRadius = 16f;
            backColor = Color.valueOf("c4b0ff");
            frontColor = Color.white;
            trailColor = Color.valueOf("c4b0ff");
            trailLength = 8;
            hitEffect = FluxEffects.hitSmall;
        }};
        return new MissileBulletType(3.4f, 16f){{
            width = 7f; height = 12f;
            lifetime = 70f;
            reloadMultiplier = 0.9f;
            rangeChange = 60f;
            homingPower = 0.28f;
            homingRange = 300f;
            splashDamage = 18f;
            splashDamageRadius = 26f;
            intervalBullet = mini;
            intervalBullets = 1;
            bulletInterval = 16f;
            intervalRandomSpread = 50f;
            backColor = Color.valueOf("a98bff");
            frontColor = Color.white;
            trailColor = Color.valueOf("a98bff");
            trailLength = 14;
            hitEffect = FluxEffects.hitSmall;
            shootEffect = FluxEffects.shootFlash;
        }};
    }

    /** Pyroglass: gluehende Glasgeschosse, die beim Aufprall in durchschlagende Scherben und Feuer zerspringen. */
    public static BulletType pyroglass(){
        BulletType shard = new BasicBulletType(4f, 12f){{
            width = 3f; height = 10f;
            lifetime = 22f;
            pierce = true;
            pierceCap = 3;
            status = StatusEffects.burning;
            statusDuration = 120f;
            frontColor = Color.white;
            backColor = Color.valueOf("ff9a55");
            hitEffect = Fx.hitBulletSmall;
        }};
        return new BasicBulletType(5.5f, 30f){{
            width = 8f; height = 14f;
            lifetime = 34f;
            reloadMultiplier = 0.8f;
            splashDamage = 24f;
            splashDamageRadius = 36f;
            makeFire = true;
            status = StatusEffects.burning;
            statusDuration = 180f;
            fragBullet = shard;
            fragBullets = 8;
            fragRandomSpread = 140f;
            frontColor = Color.valueOf("fff0d8");
            backColor = Color.valueOf("ff7a45");
            trailColor = Color.valueOf("ff7a45");
            trailLength = 10;
            hitEffect = Fx.flakExplosion;
            shootEffect = FluxEffects.shootFlash;
        }};
    }

    /** Overclock-Schlag: Punktschlag mit sehr hohem Flaechenschaden am Zielort. */
    public static BulletType overclockStrike(){
        return new BulletType(0.01f, 0f){{
            lifetime = 1f;
            instantDisappear = true;
            despawnHit = true;
            collides = false;
            hittable = false;
            absorbable = false;
            splashDamage = 1100f;
            splashDamageRadius = 120f;
            buildingDamageMultiplier = 0.9f;
            hitShake = 6f;
            status = StatusEffects.electrified;
            statusDuration = 60f * 4f;
            lightning = 10;
            lightningLength = 18;
            lightningDamage = 40f;
            lightningColor = Color.white;
            hitEffect = FluxEffects.strike;
            despawnEffect = FluxEffects.strike;
            hitSound = Sounds.explosionArtilleryShockBig;
        }};
    }

    /** Stationaere Schadenszone (Incendiary): periodischer Flaechenschaden + Feuer, zeitlich begrenzt. */
    public static class FireZoneBulletType extends BulletType{
        public final float radius, damagePerPulse;
        public final float interval;

        public FireZoneBulletType(float lifetime, float radius, float damagePerPulse, float interval){
            super(0f, 0f);
            this.lifetime = lifetime;
            this.radius = radius;
            this.damagePerPulse = damagePerPulse;
            this.interval = interval;
            collides = false;
            collidesTiles = false;
            hittable = false;
            absorbable = false;
            keepVelocity = false;
            hitEffect = Fx.none;
            despawnEffect = Fx.none;
            hitSound = Sounds.none;
        }

        @Override
        public void update(Bullet b){
            if(b.timer(1, interval)){
                Damage.damage(b.team, b.x, b.y, radius, damagePerPulse, false, true, true);
                Damage.status(b.team, b.x, b.y, radius, StatusEffects.burning, 90f, true, true);
                Fires.create(world.tileWorld(b.x + Mathf.range(radius * 0.6f), b.y + Mathf.range(radius * 0.6f)));
            }
        }

        @Override
        public void draw(Bullet b){
            float fade = Mathf.clamp(b.fout() * 3f);
            Draw.z(Layer.bullet - 1f);
            Draw.color(Color.valueOf("ff8a3a"), 0.18f * fade);
            Fill.circle(b.x, b.y, radius * (0.85f + 0.1f * Mathf.absin(Time.time, 7f, 1f)));
            Draw.color(Color.valueOf("ffd27a"), 0.5f * fade);
            Lines.stroke(1.2f);
            Lines.circle(b.x, b.y, radius * 0.8f);
            Draw.reset();
        }
    }
}
