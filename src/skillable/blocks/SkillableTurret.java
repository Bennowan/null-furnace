package skillable.blocks;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.entities.part.*;
import mindustry.entities.part.DrawPart.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.*;
import mindustry.world.consumers.*;
import mindustry.world.draw.*;
import mindustry.world.meta.*;
import skillable.fx.*;
import skillable.flux.*;
import skillable.modes.*;

import static mindustry.Vars.*;

/**
 * Der Skillable Turret. Genau EIN Material-Slot, sieben Modi. Schiesst nur mit aktuell zugeteiltem Flux:
 * Es gibt keinen Flux-Vorrat im Turm. Der Overclock-Schlag ist ein einzelner, extrem teurer Schuss.
 */
public class SkillableTurret extends Turret{
    /** Mindest-Erfuellungsgrad (zugeteilt/angefordert), damit ueberhaupt geschossen werden darf. */
    public static final float MIN_SATISFACTION = 0.5f;
    /** Overclock: benoetigter Durchsatz in Flux/Tick im Moment des Schlags, Materialkosten, Abklingzeit. */
    public static final float OVERCLOCK_FLUX = 14f;
    public static final int OVERCLOCK_ITEMS = 4;
    public static final float OVERCLOCK_COOLDOWN = 60f * 40f;
    public static final float OVERCLOCK_ARM_TIME = 60f * 12f;
    public static final float OVERCLOCK_KICK = 0.55f;
    public static final float OVERCLOCK_SAFE_INSTABILITY = 0.40f;
    /** Normalstrom-Betrieb: Bedarf in Strom/Tick (= 2400/s, EXTREM viel). Ergibt nur POWER_RATE Feuerrate, POWER_RANGE Reichweite, POWER_DAMAGE Schaden. */
    public static final float POWER_USE = 40f;

    public final BulletType overclockStrike = skillable.modes.FluxBullets.overclockStrike();

    public SkillableTurret(String name){
        super(name);
        hasItems = true;
        itemCapacity = 20;
        hasPower = true;
        envEnabled = Env.any;
        configurable = true;
        saveConfig = false;
        acceptsItems = true;
        ammoPerShot = 1;
        range = 200f;
        rotateSpeed = 6f;
        shootCone = 10f;
        inaccuracy = 0f;
        reload = 60f; //Basis; die Modi skalieren ueber BulletType.reloadMultiplier
        recoil = 2f;
        shake = 0.6f;
        shootSound = Sounds.shootDuo;
        soundPitchMin = 1.2f;
        soundPitchMax = 1.5f;
        coolant = null;
        drawer = new FluxTurretDrawer();
        //Normalstrom: nur anfordern, wenn der Turm OHNE Flux feuern muss. Optional, damit der Flux-Betrieb nie vom Strom blockiert wird.
        consume(new ConsumePower(POWER_USE, 0f, false){
            @Override
            public float requestedPower(Building entity){
                return entity instanceof SkillableBuild b && b.wantsPower() ? usage : 0f;
            }
        }).optional(true, false);
        config(Integer.class, (SkillableBuild b, Integer code) -> b.handleCode(code));
    }

    @Override
    public void init(){
        super.init();
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        FluxNode.drawNodeHints(this, x, y);
    }

    /** Zeichnet Basis, Klingen und Kopf selbst (kein DrawTurret-Parts/Outline-System). */
    public static class FluxTurretDrawer extends DrawTurret{
        public TextureRegion blade;

        @Override
        public void load(Block block){
            super.load(block);
            blade = Core.atlas.find(block.name + "-blade");
        }

        @Override
        public void draw(Building build){
            Turret t = (Turret)build.block;
            TurretBuild tb = (TurretBuild)build;
            float z = Draw.z();
            Draw.color();
            Draw.blend();
            if(base.found()) Draw.rect(base, build.x, build.y);

            float rx = build.x + tb.recoilOffset.x, ry = build.y + tb.recoilOffset.y, rot = tb.drawrot();
            Draw.z(Layer.turret - 0.5f);
            Drawf.shadow(t.region, rx - t.elevation, ry - t.elevation, rot);

            Draw.z(Layer.turret);
            float w = tb.warmup();
            if(blade != null && blade.found()){
                float bw = blade.width * blade.scl() * Draw.xscl, bh = blade.height * blade.scl() * Draw.yscl;
                for(int side : Mathf.signs){
                    Tmp.v1.trns(rot, side * 5f * w, -1.5f * w);
                    Draw.rect(blade, rx + Tmp.v1.x, ry + Tmp.v1.y, bw * side, bh, rot + side * -12f * w);
                }
            }
            Draw.rect(t.region, rx, ry, rot);
            Draw.z(z);
            Draw.reset();
        }
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.ammo, StatValues.ammo(FluxModes.statMap()));
        stats.add(Stat.itemCapacity, itemCapacity, StatUnit.items);
    }

    @Override
    public void setBars(){
        super.setBars();
        removeBar("items");
        addBar("flux-supply", (SkillableBuild e) -> new Bar(
            () -> e.fluxBarText(),
            () -> FluxEffects.colorFor(FluxEffects.intensity(e.fluxBarFraction(), e.netInstability())).cpy(),
            () -> e.fluxBarFraction()));
        addBar("opmode", (SkillableBuild e) -> new Bar(
            () -> e.opModeText(),
            () -> e.opModeColor(),
            () -> e.opModeFraction()));
        addBar("instability", (SkillableBuild e) -> new Bar(
            () -> Core.bundle.format("bar.instability", (int)(e.netInstability() * 100f), e.stageText()),
            () -> e.stageColor(),
            () -> e.netInstability()));
        addBar("material", (SkillableBuild e) -> new Bar(
            () -> e.material == null ? Core.bundle.get("bar.flux-nomaterial")
                : Core.bundle.format("bar.flux-material", e.material.localizedName, e.materialCount + (e.shotBuffer > 0 ? 1 : 0), itemCapacity),
            () -> e.mode() == null ? Pal.items : e.mode().color,
            () -> e.material == null ? 0f : (float)e.materialCount / itemCapacity));
        addBar("overclock", (SkillableBuild e) -> new Bar(
            () -> e.overclockBarText(),
            () -> e.overclockArmed ? FluxEffects.warn : (e.overclockCooldown > 0f ? Pal.lightishGray : FluxEffects.purple),
            () -> e.overclockCooldown > 0f ? 1f - e.overclockCooldown / OVERCLOCK_COOLDOWN : 1f));
    }

    public class SkillableBuild extends TurretBuild implements FluxConsumer, skillable.synth.MaterialHolder{
        public @Nullable Item material;
        public int materialCount;
        /** Verbleibende Schuesse der bereits entnommenen Materialeinheit. */
        public int shotBuffer;

        /** Flux dieses Ticks (vom Netz zugeteilt, nie angesammelt) und der dafuer angeforderte Bedarf. */
        public float granted, lastDemand;
        public @Nullable FluxNetwork network;
        private boolean registered;

        public boolean overclockArmed;
        public float overclockArmTimer, overclockCooldown;
        private boolean mapTargetCache;
        private float mapTargetTimer;
        private String lastFailure = "";

        @Override
        public boolean holdsMaterial(Item item){
            return material == item && (materialCount > 0 || shotBuffer > 0);
        }

        private float unknownTicks;

        public @Nullable FluxMode mode(){
            return FluxModes.get(material);
        }

        //---- FluxMember / FluxConsumer
        @Override public FluxNetwork fluxNetwork(){ return network; }

        @Override
        public void fluxNetwork(FluxNetwork n){
            network = n;
            if(n == null){ granted = 0f; lastDemand = 0f; }
        }

        @Override
        public float fluxDemand(){
            float d;
            if(overclockArmed){
                d = OVERCLOCK_FLUX;
            }else{
                FluxMode m = mode();
                boolean wants = m != null && hasAmmo() && fluxAvailable() && (target != null || isShooting);
                d = wants ? m.fluxPerTick : 0f;
            }
            lastDemand = d;
            return d;
        }

        @Override public int fluxPriority(){ return overclockArmed ? 10 : 0; }

        @Override
        public void fluxGranted(float g){
            granted = FluxMath.safe(g); //ersetzt den Wert jedes Tick - nie addiert
        }

        @Override
        public float riskTarget(){
            FluxMode m = mode();
            if(m == null || overclockArmed || !fluxOk()) return 0f;
            return m.risk * satisfaction();
        }

        public float satisfaction(){
            return lastDemand <= 0f ? 0f : FluxMath.clamp01(granted / lastDemand);
        }

        public boolean fluxOk(){
            return lastDemand > 0f && granted >= lastDemand * MIN_SATISFACTION;
        }

        public float netInstability(){
            return network == null ? 0f : network.maxInstability;
        }

        //---- Material-Slot
        @Override
        public boolean acceptItem(Building source, Item item){
            if(!FluxModes.supports(item)) return false;
            if(material != null && material != item) return false;
            return materialCount < itemCapacity;
        }

        @Override
        public void handleItem(Building source, Item item){
            if(!acceptItem(source, item)) return;
            material = item;
            materialCount++;
        }

        @Override
        public int acceptStack(Item item, int amount, Teamc source){
            if(!acceptItem(this, item) || (source != null && source.team() != team)) return 0;
            return Math.min(itemCapacity - materialCount, amount);
        }

        @Override
        public void handleStack(Item item, int amount, Teamc source){
            if(!acceptItem(this, item)) return;
            noSleep();
            material = item;
            materialCount = Math.min(itemCapacity, materialCount + Math.max(0, amount));
        }

        @Override public int getMaximumAccepted(Item item){ return itemCapacity; }

        /** Bewusste Entladefunktion: gibt das Material an den Kern zurueck (Rest verfaellt). */
        public void unloadMaterial(){
            if(material == null) return;
            CoreBuild core = team.core();
            if(core != null && materialCount > 0){
                int free = Math.max(0, core.storageCapacity - core.items.get(material));
                core.items.add(material, Math.min(free, materialCount));
            }
            material = null;
            materialCount = 0;
            shotBuffer = 0;
            overclockArmed = false;
        }

        public void handleCode(int code){
            if(code == 1) requestOverclock();
            else if(code == 2) unloadMaterial();
        }

        //---- Turret-Hooks
        @Override
        public UnlockableContent getAmmoContent(){ return material; }

        @Override
        public float getAmmoFraction(){ return material == null ? 0f : (float)materialCount / itemCapacity; }

        @Override
        public boolean hasAmmo(){
            //Material ohne gueltigen Modus (beschaedigtes/ersetztes Materialregister) ist inert: nie als Munition behandeln
            return material != null && (materialCount > 0 || shotBuffer > 0) && mode() != null;
        }

        @Override
        public @Nullable BulletType peekAmmo(){
            FluxMode m = mode();
            if(m == null) return null;
            //ohne nutzbares Flux-Netz: abgeschwaechtes Geschoss (Normalstrom), sonst volle Staerke
            return m.powerCapable && !fluxAvailable() ? m.powerBullet : m.bullet;
        }

        @Override
        public BulletType useAmmo(){
            FluxMode m = mode();
            if(m == null) return null;
            if(cheating()) return m.bullet;
            if(shotBuffer <= 0 && materialCount > 0){
                materialCount--;
                shotBuffer = m.shotsPerItem;
            }
            if(shotBuffer > 0) shotBuffer--;
            //instabile Zusammensetzung: jeder Schuss belastet das Flux-Netz
            if(m.shotKick > 0f && network != null && !net.client()) network.kick(m.shotKick, 0.6f);
            if(materialCount <= 0 && shotBuffer <= 0){
                material = null; //Slot frei: Moduswechsel moeglich
            }
            return m.bullet;
        }

        /** Flux-Netz vorhanden, online und stark genug, den Modus zu versorgen? Dann wird NUR Flux genutzt (nie beides gleichzeitig). */
        public boolean fluxAvailable(){
            FluxMode m = mode();
            return m != null && network != null && !network.offline && network.lastCapacity >= m.fluxPerTick * MIN_SATISFACTION;
        }

        /** Aktiver Normalstrom-Betrieb moeglich (Modus erlaubt es, kein nutzbares Flux, Strom vorhanden)? */
        public boolean powerOk(){
            FluxMode m = mode();
            return m != null && m.powerCapable && !fluxAvailable() && power != null && power.status >= 0.05f;
        }

        /** Soll der Turm im Moment Normalstrom anfordern? */
        public boolean wantsPower(){
            FluxMode m = mode();
            return m != null && m.powerCapable && !overclockArmed && !fluxAvailable() && hasAmmo() && (target != null || isShooting);
        }

        @Override
        protected void updateShooting(){
            if(overclockArmed) return;
            if(fluxAvailable()){
                //Schuesse werden nur aus dem JETZT zugeteilten Flux finanziert
                if(!fluxOk()) return;
            }else if(!powerOk()){
                return;
            }
            super.updateShooting();
        }

        @Override
        protected float baseReloadSpeed(){
            if(overclockArmed) return 0f;
            if(fluxAvailable()) return fluxOk() ? satisfaction() : 0f;
            return powerOk() ? FluxModes.POWER_RATE * power.status : 0f;
        }

        //---- Overclock
        public boolean overclockReady(){
            return overclockCooldown <= 0f && material != null && materialCount >= OVERCLOCK_ITEMS && mapTargetCache
                && network != null && !network.offline && network.lastCapacity >= OVERCLOCK_FLUX;
        }

        public boolean overclockSafe(){
            return netInstability() < OVERCLOCK_SAFE_INSTABILITY;
        }

        public void requestOverclock(){
            if(net.client()) return; //serverseitig
            if(overclockArmed) return;
            mapTargetCache = OverclockTargeting.exists(team);
            if(!overclockReady()){
                lastFailure = overclockWhyNot();
                return;
            }
            overclockArmed = true;
            overclockArmTimer = OVERCLOCK_ARM_TIME;
            lastFailure = "";
        }

        private void tryOverclock(){
            if(!overclockArmed || net.client()) return;
            overclockArmTimer -= Time.delta;
            boolean enough = network != null && !network.offline && granted >= OVERCLOCK_FLUX * 0.999f && lastDemand >= OVERCLOCK_FLUX * 0.999f;
            if(enough){
                fireOverclock();
            }else if(overclockArmTimer <= 0f){
                overclockArmed = false;
                lastFailure = Core.bundle.get("flux.oc.fail.flux");
            }
        }

        private void fireOverclock(){
            overclockArmed = false;
            Posc t = OverclockTargeting.pick(team, x, y);
            if(t == null || materialCount < OVERCLOCK_ITEMS){
                lastFailure = Core.bundle.get("flux.oc.fail.target");
                return;
            }
            //Kosten: Material wird verbraucht
            materialCount -= OVERCLOCK_ITEMS;
            if(materialCount <= 0 && shotBuffer <= 0) material = null;
            overclockCooldown = OVERCLOCK_COOLDOWN;
            lastFailure = "";

            overclockStrike.create(this, team, t.getX(), t.getY(), 0f);
            FluxEffects.beam.at(x, y, 0f, new Vec2(t.getX(), t.getY()));
            Effect.shake(3f, 20f, x, y);
            Sounds.shootSmite.at(x, y, 0.9f, 1.2f);

            //Lastimpuls: hoher Instabilitaets-Schub, danach definierte Netzstoerung (EMP) und Erholung
            FluxNetwork n = network;
            if(n != null){
                n.kick(OVERCLOCK_KICK, 0.6f);
                if(!n.offline) n.collapse(0.5f);
            }
        }

        private String overclockWhyNot(){
            if(overclockCooldown > 0f) return Core.bundle.format("flux.oc.fail.cooldown", (int)Math.ceil(overclockCooldown / 60f));
            if(material == null || materialCount < OVERCLOCK_ITEMS) return Core.bundle.format("flux.oc.fail.material", OVERCLOCK_ITEMS);
            if(network == null) return Core.bundle.get("flux.status.nonet");
            if(network.offline) return Core.bundle.get("flux.status.offline");
            if(network.lastCapacity < OVERCLOCK_FLUX) return Core.bundle.format("flux.oc.fail.capacity", Strings.fixed(OVERCLOCK_FLUX * 60f, 0));
            if(!mapTargetCache) return Core.bundle.get("flux.oc.fail.target");
            return "";
        }

        public String overclockBarText(){
            if(overclockArmed) return Core.bundle.get("flux.oc.armed");
            if(overclockCooldown > 0f) return Core.bundle.format("flux.oc.cooldown", (int)Math.ceil(overclockCooldown / 60f));
            if(overclockReady()) return Core.bundle.get(overclockSafe() ? "flux.oc.ready" : "flux.oc.risky");
            return Core.bundle.get("flux.oc.unavailable");
        }

        //---- Status / UI
        public String stageText(){
            if(network != null && network.offline) return Core.bundle.get("flux.state.emp");
            if(network != null && network.recovering) return Core.bundle.get("flux.state.recovering");
            return switch(InstabilityModel.stage(netInstability())){
                case InstabilityModel.WARNING -> Core.bundle.get("flux.state.warning");
                case InstabilityModel.CRITICAL -> Core.bundle.get("flux.state.critical");
                default -> Core.bundle.get("flux.state.stable");
            };
        }

        public Color stageColor(){
            if(network != null && network.offline) return FluxEffects.red;
            return switch(InstabilityModel.stage(netInstability())){
                case InstabilityModel.WARNING -> FluxEffects.warn;
                case InstabilityModel.CRITICAL -> FluxEffects.red;
                default -> FluxEffects.blue;
            };
        }

        /** Balkentext: zeigt IMMER, wie viel Flux das Netz bereitstellen kann und wie viel der Turm gerade bekommt. */
        public String fluxBarText(){
            if(network == null) return Core.bundle.get("bar.flux-nonet");
            float cap = network.offline ? 0f : network.lastCapacity;
            if(lastDemand <= 0f) return Core.bundle.format("bar.flux-ready", Strings.fixed(cap * 60f, 1));
            return Core.bundle.format("bar.flux-supply", Strings.fixed(granted * 60f, 1), Strings.fixed(lastDemand * 60f, 1));
        }

        public float fluxBarFraction(){
            FluxMode m = mode();
            if(network == null || network.offline) return 0f;
            if(lastDemand > 0f) return satisfaction();
            return m == null ? FluxMath.clamp01(network.lastCapacity) : FluxMath.clamp01(network.lastCapacity / m.fluxPerTick);
        }

        public String opModeText(){
            if(material == null) return Core.bundle.get("flux.op.none");
            if(overclockArmed) return Core.bundle.get("flux.op.overclock");
            if(fluxAvailable()) return Core.bundle.get("flux.op.flux");
            FluxMode m = mode();
            if(m != null && m.powerCapable){
                return powerOk()
                    ? Core.bundle.format("flux.op.power", (int)(power.status * 100f))
                    : Core.bundle.get("flux.op.nopower");
            }
            return Core.bundle.get("flux.op.needflux");
        }

        public Color opModeColor(){
            if(material == null) return Pal.lightishGray;
            if(fluxAvailable() || overclockArmed) return FluxEffects.purple;
            return powerOk() ? Pal.accent : FluxEffects.warn;
        }

        public float opModeFraction(){
            if(fluxAvailable()) return 1f;
            return powerOk() ? power.status : 0f;
        }

        /** Erklaert, warum der Turm gerade nicht (oder doch) schiesst - mit Hinweis, was als Naechstes zu tun ist. */
        public String statusText(){
            if(!lastFailure.isEmpty() && !overclockArmed) return lastFailure;
            if(material == null) return Core.bundle.get("flux.status.nomaterial");
            if(network != null && network.offline) return Core.bundle.get("flux.status.offline");
            FluxMode m = mode();
            if(overclockArmed) return Core.bundle.get("flux.status.oc");
            if(fluxAvailable()){
                if(lastDemand > 0f && !fluxOk()) return Core.bundle.format("flux.status.lowflux", Strings.fixed(granted * 60f, 1), Strings.fixed(lastDemand * 60f, 1));
                return target == null ? Core.bundle.get("flux.status.idle") : Core.bundle.get("flux.status.firing");
            }
            //kein nutzbares Flux
            String why;
            if(network == null) why = Core.bundle.get("flux.status.nonet");
            else if(network.sources.isEmpty()) why = Core.bundle.get("flux.status.nosource");
            else if(network.lastNominal <= 0f) why = Core.bundle.get("flux.status.nopower");
            else why = Core.bundle.format("flux.status.weak", Strings.fixed(network.lastCapacity * 60f, 0), Strings.fixed(m.fluxPerTick * MIN_SATISFACTION * 60f, 0));
            if(m.powerCapable){
                if(powerOk()) return Core.bundle.format("flux.status.powermode", (int)(power.status * 100f), (int)(FluxModes.POWER_RATE * 100f), (int)(FluxModes.POWER_RANGE * 100f), (int)(FluxModes.POWER_DAMAGE * 100f)) + "\n" + why;
                return Core.bundle.format("flux.status.needpower", Strings.fixed(POWER_USE * 60f, 0)) + "\n" + why;
            }
            return Core.bundle.get("flux.status.needflux") + "\n" + why;
        }

        @Override
        public void display(Table table){
            super.display(table);
            table.row();
            table.table(t -> {
                t.left();
                t.label(() -> statusText()).wrap().width(190f).left().color(Color.lightGray);
            }).growX().left();
        }

        @Override
        public void buildConfiguration(Table table){
            table.defaults().size(170f, 40f).pad(2f);
            table.button(Core.bundle.get("flux.overclock"), Styles.defaultt, () -> configure(1))
                .update(b -> b.setDisabled(overclockCooldown > 0f || overclockArmed || material == null)).row();
            table.button(Core.bundle.get("flux.unload"), Styles.defaultt, () -> configure(2))
                .update(b -> b.setDisabled(material == null));
        }

        @Override
        public Object config(){
            return null;
        }

        @Override
        public void updateTile(){
            if(!registered){
                registered = true;
                FluxNetworkManager.get().register(this);
            }
            if(network == null){ granted = 0f; }
            //Unbekanntes Material (Registereintrag fehlt dauerhaft): nach kurzer Toleranz (Netzwerk-Sync) kontrolliert verwerfen
            if(material != null && mode() == null){
                if(!net.client() && (unknownTicks += Time.delta) > 180f){
                    material = null; materialCount = 0; shotBuffer = 0; unknownTicks = 0f;
                    Log.warn("[FluxProtocol] Turm verwirft Material ohne Registereintrag.");
                }
            }else unknownTicks = 0f;
            if(overclockCooldown > 0f) overclockCooldown = Math.max(0f, overclockCooldown - Time.delta);

            //Existenz mapweiter Ziele nur selten pruefen und nur, wenn der Turm geladen ist
            mapTargetTimer -= Time.delta;
            if(mapTargetTimer <= 0f){
                mapTargetTimer = 90f;
                mapTargetCache = material != null && OverclockTargeting.exists(team);
            }

            super.updateTile();
            tryOverclock();
        }

        @Override
        public void onRemoved(){
            super.onRemoved();
            FluxNetworkManager.get().unregister(this);
        }

        @Override
        public void placed(){
            super.placed();
            if(net.client()) return;
            FluxNode.autoLinkTo(this);
        }

        @Override
        public void drawSelect(){
            super.drawSelect();
            FluxNode.drawLinkedNodes(this);
        }

        @Override
        public void draw(){
            super.draw();
            float z0 = Draw.z();
            FluxMode m = mode();
            if(m == null){ Draw.z(z0); return; }
            float inten = FluxEffects.intensity(satisfaction(), netInstability());
            Draw.z(Layer.turret + 1f);
            Draw.blend(Blending.additive);
            Draw.color(m.color, 0.35f + 0.35f * satisfaction());
            Fill.circle(x + recoilOffset.x, y + recoilOffset.y, 3.2f + 1.6f * warmup());
            Draw.color(FluxEffects.colorFor(inten), 0.5f * warmup());
            Lines.stroke(1.2f);
            Lines.circle(x + recoilOffset.x, y + recoilOffset.y, 9f + 2f * Mathf.absin(Time.time, 9f, 1f));
            Draw.blend();
            Draw.reset();
            Draw.z(z0);
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.b(1);
            write.s(material == null ? -1 : material.id);
            write.i(Math.max(0, materialCount));
            write.i(Math.max(0, shotBuffer));
            write.f(overclockCooldown > 0.5f ? overclockCooldown : 0f);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            read.b();
            int id = read.s();
            Item it = id < 0 ? null : content.item(id);
            materialCount = Mathf.clamp(read.i(), 0, itemCapacity);
            shotBuffer = Math.max(0, read.i());
            overclockCooldown = Mathf.clamp(FluxMath.safe(read.f()), 0f, OVERCLOCK_COOLDOWN);
            material = FluxModes.supports(it) ? it : null;
            if(material == null){ materialCount = 0; shotBuffer = 0; }
            if(material != null && FluxModes.get(material) != null) shotBuffer = Math.min(shotBuffer, FluxModes.get(material).shotsPerItem);
            overclockArmed = false; //Uebergangszustand wird nie als aktiv gespeichert
        }
    }
}
