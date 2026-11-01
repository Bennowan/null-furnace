package skillable.blocks;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;
import skillable.fx.*;
import skillable.flux.*;

import static mindustry.Vars.*;

/** Wandelt normalen Strom in momentanen Flux um. Speichert keinen Flux. */
public class FluxConverter extends Block{
    public final ConverterProfile profile;

    public FluxConverter(String name, ConverterProfile profile){
        super(name);
        this.profile = profile;
        update = true;
        solid = true;
        hasPower = true;
        connectedPower = true;
        configurable = true;
        saveConfig = false;
        hasLiquids = true;
        liquidCapacity = 20f;
        outputsLiquid = false;
        group = BlockGroup.power;
        envEnabled = Env.any;
        //Strombedarf haengt von der aktuellen Flux-Last ab (normales Mindustry-Stromsystem)
        consume(new ConsumePower(profile.powerUse, 0f, false){
            @Override
            public float requestedPower(Building entity){
                if(entity instanceof ConverterBuild c) return usage * c.powerFactor();
                return super.requestedPower(entity);
            }
        });
        config(Boolean.class, (ConverterBuild b, Boolean v) -> b.overdrive = v);
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.output, profile.throughput * 60f, StatUnit.perSecond);
        stats.add(Stat.maxEfficiency, (int)(profile.overdriveFactor * 100f), StatUnit.percent);
    }

    /** Kuehlmittel: Faktor auf den Instabilitaets-Zielwert und Verbrauch pro Tick bei Last. */
    public static float coolantValue(Liquid l){
        if(l == Liquids.cryofluid) return 0.55f;
        if(l == Liquids.water) return 0.80f;
        return 1f;
    }
    public static final float COOLANT_USE = 0.10f;

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        FluxNode.drawNodeHints(this, x, y);
    }

    @Override
    public void setBars(){
        super.setBars();
        float maxRate = profile.throughput * profile.overdriveFactor;
        addBar("flux-cap", (ConverterBuild e) -> new Bar(
            () -> Core.bundle.format("bar.flux-cap", Strings.fixed(e.nominalOutput() * 60f, 1)),
            () -> e.lockout > 0f ? FluxEffects.red : FluxEffects.blue,
            () -> FluxMath.clamp01(e.nominalOutput() / profile.throughput)));
        addBar("flux-out", (ConverterBuild e) -> new Bar(
            () -> Core.bundle.format("bar.flux-out", Strings.fixed(e.lastOut * 60f, 1)),
            () -> FluxEffects.colorFor(FluxEffects.intensity(e.lastLoad, e.instability)).cpy(),
            () -> FluxMath.clamp01(e.lastOut / maxRate)));
        addBar("coolant", (ConverterBuild e) -> new Bar(
            () -> e.liquids.currentAmount() > 0.01f ? Core.bundle.format("bar.flux-coolant", e.liquids.current().localizedName, (int)(coolantValue(e.liquids.current()) * 100f)) : Core.bundle.get("bar.flux-nocoolant"),
            () -> e.liquids.current() == null ? Pal.lightishGray : e.liquids.current().barColor(),
            () -> e.liquids.currentAmount() / liquidCapacity));
        addBar("instability", (ConverterBuild e) -> new Bar(
            () -> Core.bundle.format("bar.instability", (int)(e.instability * 100f), e.stateText()),
            () -> e.stateColor(),
            () -> e.instability));
    }

    public class ConverterBuild extends Building implements FluxSource{
        public float instability, lockout, recovery, lastOut, lastLoad;
        public boolean overdrive;
        public float coolFactor = 1f;
        public final InstabilityModel.Breakdown breakdown = new InstabilityModel.Breakdown();
        private int lastStage;
        private float toastTimer;
        public @Nullable FluxNetwork network;
        private boolean registered;
        private float alarmTimer;

        //---- FluxMember
        @Override public FluxNetwork fluxNetwork(){ return network; }
        @Override public void fluxNetwork(FluxNetwork n){ network = n; }

        //---- FluxSource
        @Override public ConverterProfile profile(){ return profile; }

        @Override
        public float nominalOutput(){
            if(lockout > 0f || power == null || !enabled) return 0f;
            return profile.throughput * FluxMath.clamp01(power.status)
                * InstabilityModel.efficiency(instability) * InstabilityModel.recoveryFactor(recovery);
        }

        @Override
        public float maxOutput(){
            float nom = nominalOutput();
            return overdrive ? nom * profile.overdriveFactor : nom;
        }

        @Override public boolean locked(){ return lockout > 0f; }
        @Override public boolean recovering(){ return lockout <= 0f && recovery > 0f; }
        @Override public float coolantFactor(){ return coolFactor; }
        @Override public void reportBreakdown(InstabilityModel.Breakdown b){ breakdown.set(b); }
        @Override public float instability(){ return instability; }
        @Override public void instability(float v){ instability = FluxMath.clamp01(v); }

        @Override
        public void reportOutput(float out, float load){
            lastOut = out;
            lastLoad = load;
        }

        @Override
        public void applyLockout(float ticks){
            lockout = Math.max(lockout, Math.min(FluxMath.safe(ticks), InstabilityModel.LOCKOUT_TICKS * 2f));
            recovery = InstabilityModel.RECOVERY_TICKS;
            instability = InstabilityModel.RESTART_INSTABILITY;
        }

        @Override
        public void tickState(float delta){
            if(lockout > 0f){
                lockout = Math.max(0f, lockout - delta);
            }else if(recovery > 0f){
                recovery = Math.max(0f, recovery - delta);
            }
        }

        /** Faktor fuer den Stromverbrauch: nur bei Flux-Bedarf, skaliert mit Last (Overdrive kostet mehr Strom). */
        public float powerFactor(){
            if(!shouldConsume()) return 0f;
            //Standby 25 % sobald im Netz, danach proportional zur Last (Overdrive kostet mehr)
            return Mathf.clamp(Math.max(0.25f, lastLoad), 0.25f, profile.overdriveFactor);
        }

        @Override
        public boolean shouldConsume(){
            return lockout <= 0f && network != null;
        }

        @Override
        public void updateTile(){
            if(!registered){
                registered = true;
                FluxNetworkManager.get().register(this);
            }
            //Kuehlmittel verbrauchen: nur bei tatsaechlicher Last, nur Wasser/Kryofluid
            Liquid cl = liquids.current();
            float use = COOLANT_USE * Time.delta * Mathf.clamp(lastLoad);
            if(cl != null && coolantValue(cl) < 1f && lastLoad > 0.05f && liquids.get(cl) >= use){
                liquids.remove(cl, use);
                coolFactor = coolantValue(cl);
            }else{
                coolFactor = 1f;
            }
            //Warnmeldung beim Uebergang in hoehere Stufen (nur Spieler-Team, mit Pause)
            toastTimer = Math.max(0f, toastTimer - Time.delta);
            int st = lockout > 0f ? InstabilityModel.COLLAPSE : InstabilityModel.stage(instability);
            if(!headless && st > lastStage && st >= InstabilityModel.WARNING && toastTimer <= 0f && team == player.team()){
                toastTimer = 60f * 8f;
                String key = st == InstabilityModel.WARNING ? "flux.toast.warning" : st == InstabilityModel.CRITICAL ? "flux.toast.critical" : "flux.toast.collapse";
                ui.hudfrag.showToast(Icon.warning, Core.bundle.format(key, (int)(instability * 100f)));
            }
            lastStage = st;
            //nur visuelles Feedback; die Simulation laeuft im FluxNetworkManager
            if(!headless){
                if(instability >= InstabilityModel.WARNING_AT && Mathf.chance(0.04 + instability * 0.12)){
                    FluxEffects.leak.at(x + Mathf.range(size * 3f), y + Mathf.range(size * 3f));
                }
                if(instability >= InstabilityModel.CRITICAL_AT && lockout <= 0f){
                    alarmTimer += Time.delta;
                    if(alarmTimer > 90f){
                        alarmTimer = 0f;
                        mindustry.gen.Sounds.beamPlasmaSmall.at(x, y, 1.7f, 0.35f);
                    }
                }else{
                    alarmTimer = 0f;
                }
            }
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            return coolantValue(liquid) < 1f && liquids.get(liquid) < liquidCapacity - 0.01f
                && (liquids.currentAmount() <= 0.01f || liquids.current() == liquid);
        }

        /** Ursachen der aktuellen Instabilitaet als Text. */
        public String causesText(){
            InstabilityModel.Breakdown b = breakdown;
            if(lockout > 0f) return Core.bundle.get("flux.cause.offline");
            return Core.bundle.format("flux.cause", (int)(b.target * 100f), (int)(b.load * 100f), (int)(b.loadRisk * 100f),
                (int)(b.overdriveRisk * 100f), (int)(b.modeRisk * 100f), (int)(b.deficitRisk * 100f), (int)(b.coolant * 100f));
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

        public String stateText(){
            if(lockout > 0f) return Core.bundle.get("flux.state.emp");
            if(recovery > 0f) return Core.bundle.get("flux.state.recovering");
            return switch(InstabilityModel.stage(instability)){
                case InstabilityModel.WARNING -> Core.bundle.get("flux.state.warning");
                case InstabilityModel.CRITICAL -> Core.bundle.get("flux.state.critical");
                default -> Core.bundle.get("flux.state.stable");
            };
        }

        /** Ausfuehrlicher Hinweis, warum der Konverter gerade (nicht) Flux liefert. */
        public String statusText(){
            if(lockout > 0f) return Core.bundle.get("flux.cstatus.emp");
            if(network == null || network.others.isEmpty() && network.consumers.isEmpty()) return Core.bundle.get("flux.cstatus.nonode");
            if(power == null || power.status < 0.02f) return Core.bundle.get("flux.cstatus.nopower");
            if(network.consumers.isEmpty()) return Core.bundle.get("flux.cstatus.noconsumer");
            if(recovery > 0f) return Core.bundle.get("flux.cstatus.recovering");
            if(lastOut <= 0.0001f) return Core.bundle.format("flux.cstatus.standby", Strings.fixed(nominalOutput() * 60f, 0));
            return Core.bundle.format("flux.cstatus.working", Strings.fixed(lastOut * 60f, 1));
        }

        @Override
        public void display(Table table){
            super.display(table);
            table.row();
            table.table(t -> {
                t.left();
                t.label(() -> Core.bundle.get("flux.conv.role")).wrap().width(190f).left().color(FluxEffects.purple).row();
                t.label(() -> statusText()).wrap().width(190f).left().color(Color.lightGray).row();
                t.label(() -> causesText()).wrap().width(190f).left().color(Color.gray);
            }).growX().left();
        }

        @Override
        public void drawSelect(){
            super.drawSelect();
            FluxNode.drawLinkedNodes(this);
        }

        public Color stateColor(){
            if(lockout > 0f) return FluxEffects.red;
            return switch(InstabilityModel.stage(instability)){
                case InstabilityModel.WARNING -> FluxEffects.warn;
                case InstabilityModel.CRITICAL -> FluxEffects.red;
                default -> FluxEffects.blue;
            };
        }

        @Override
        public void draw(){
            super.draw();
            float z0 = Draw.z();
            float inten = lockout > 0f ? 0f : FluxEffects.intensity(lastLoad, instability);
            float glow = lockout > 0f ? 0.08f : 0.25f + 0.5f * FluxMath.clamp01(lastLoad);
            if(lockout > 0f && Mathf.absin(6f, 1f) > 0.6f) glow = 0.4f;
            Draw.z(Layer.blockAdditive);
            Draw.blend(Blending.additive);
            Draw.color(lockout > 0f ? FluxEffects.red : FluxEffects.colorFor(inten), glow);
            Fill.circle(x, y, size * tilesize * 0.28f * (0.8f + 0.2f * Mathf.absin(Time.time, 8f, 1f)));
            Draw.blend();
            Draw.reset();
            Draw.z(z0);
        }

        @Override
        public void buildConfiguration(Table table){
            table.button(Core.bundle.get("flux.overdrive"), Styles.togglet, () -> configure(!overdrive))
                .size(150f, 40f).update(b -> b.setChecked(overdrive));
        }

        @Override
        public Object config(){
            return overdrive;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.b(1); //Mod-Revision
            write.f(FluxMath.clamp01(instability));
            //abgelaufene Uebergangszustaende nicht als aktiven Fehler speichern
            write.f(lockout > 0.5f ? lockout : 0f);
            write.f(recovery > 0.5f ? recovery : 0f);
            write.bool(overdrive);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            read.b();
            instability = FluxMath.clamp01(read.f());
            lockout = Mathf.clamp(FluxMath.safe(read.f()), 0f, InstabilityModel.LOCKOUT_TICKS * 2f);
            recovery = Mathf.clamp(FluxMath.safe(read.f()), 0f, InstabilityModel.RECOVERY_TICKS);
            overdrive = read.bool();
            if(lockout <= 0f && recovery <= 0f && instability >= InstabilityModel.COLLAPSE_AT){
                instability = InstabilityModel.RESTART_INSTABILITY;
            }
        }
    }
}
