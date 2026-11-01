package skillable.blocks;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import skillable.fx.*;
import skillable.material.*;

import static mindustry.Vars.*;

/**
 * Kuehlmantel fuer den Flux Fuser. Er kuehlt GENAUSO EINEN angrenzenden Fuser: den mit der kleinsten Position (deterministisch, im Menue
 * angezeigt). Je Fuser zaehlen hoechstens {@link ThermalModel#MAX_COOLERS} Kuehlmantel (nach Position geordnet); weitere bleiben im Leerlauf.
 *
 * Der Kuehlmantel verbraucht nur dann Kuehlmittel, wenn der Fuser warm ist. Kuehlmittelguete und Leistung kommen aus
 * {@link ThermalModel} (Waermekapazitaet und Temperatur der Fluessigkeit); der Fuser wendet die Leistung auf seine Temperatur an.
 * Die Kuehlung von Stromgeneratoren (Engine) wird nicht beruehrt, andere Gebaeude werden nie beeinflusst.
 */
public class FuserCooler extends Block{
    public final boolean erekir;

    public FuserCooler(String name, boolean erekir){
        super(name);
        this.erekir = erekir;
        update = true;
        solid = true;
        sync = true;
        hasLiquids = true;
        hasItems = false;
        outputsLiquid = false;
        configurable = false;
        group = BlockGroup.none;
        envEnabled = Env.any;
        liquidCapacity = 30f;
    }

    /** Kuehlmittelguete einer Fluessigkeit (0 = ungeeignet). */
    public static float quality(Liquid l){
        return l == null ? 0f : ThermalModel.coolantQuality(l.coolant, l.heatCapacity, l.temperature, l.flammability);
    }

    @Override
    public void init(){
        super.init();
        //Nur echte Kuehlmittel zaehlen als "verbrauchte" Fluessigkeit; alles andere bleibt Fremdstoff (Engine-Verhalten wie bei jedem Block)
        if(liquidFilter == null) liquidFilter = new boolean[content.liquids().size];
        for(Liquid l : content.liquids()) liquidFilter[l.id] = quality(l) > 0f;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.output, t -> {
            t.row();
            t.add(Core.bundle.get("stat.cooler-text")).wrap().width(320f).left().padBottom(4f).row();
            for(Liquid l : content.liquids()){
                float q = quality(l);
                if(q <= 0f) continue;
                t.add(l.localizedName + ": " + Strings.fixed(ThermalModel.coolerRate(q, 1f) * 60f * 100f, 1) + " %/s").left().row();
            }
            t.add(Core.bundle.format("stat.cooler-limit", ThermalModel.MAX_COOLERS, Strings.fixed(ThermalModel.MAX_COOLING * 60f * 100f, 0))).left().padTop(4f).row();
        });
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("cooler-power", (CoolerBuild e) -> new Bar(
            () -> Core.bundle.format("bar.cooler-power", Strings.fixed(e.power * 60f * 100f, 1)),
            () -> Color.valueOf("7fdcff").cpy(), () -> Mathf.clamp(e.power / (ThermalModel.COOL_BASE * 0.9f))));
        addBar("cooler-target", (CoolerBuild e) -> new Bar(
            () -> e.target == null ? Core.bundle.get("bar.cooler-notarget") : Core.bundle.format("bar.cooler-target", (int)(e.target.heat * 100f)),
            () -> Tmp.c1.set(Color.valueOf("ffb35a")).lerp(Color.valueOf("ff3a2a"), e.target == null ? 0f : Mathf.clamp(e.target.heat)).cpy(),
            () -> e.target == null ? 0f : Mathf.clamp(e.target.heat)));
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        //Vorschau: welcher Fuser wuerde gekuehlt?
        FluxFuser.FuserBuild t = findTarget(x, y);
        if(t != null){
            Drawf.square(t.x, t.y, t.block.size * tilesize / 2f + 2f, Color.valueOf("7fdcff"));
            Drawf.line(Color.valueOf("7fdcff"), x * tilesize + offset, y * tilesize + offset, t.x, t.y);
        }else{
            drawPlaceText(Core.bundle.get("cooler.place.hint"), x, y, false);
        }
    }

    /** Angrenzender Fuser (kleinste Position) fuer einen Kuehlmantel, der bei (x,y) stuende. */
    @Nullable FluxFuser.FuserBuild findTarget(int x, int y){
        int off = -(size - 1) / 2;
        FluxFuser.FuserBuild best = null;
        for(int i = 0; i < size; i++){
            int[][] nb = {{-1, i}, {size, i}, {i, -1}, {i, size}}; //nur direkt angrenzende Kacheln (keine Ecken)
            for(int[] d : nb){
                Building b = world.build(x + off + d[0], y + off + d[1]);
                if(b instanceof FluxFuser.FuserBuild f && f.team == player.team() && (best == null || f.pos() < best.pos())) best = f;
            }
        }
        return best;
    }

    public class CoolerBuild extends Building{
        public @Nullable FluxFuser.FuserBuild target;
        /** Aktuelle Kuehlleistung (Waerme pro Tick), die der Fuser anwendet. */
        public float power, supply, glow;
        public boolean active;

        @Override
        public void onProximityUpdate(){
            super.onProximityUpdate();
            resolveTarget();
        }

        void resolveTarget(){
            FluxFuser.FuserBuild best = null;
            for(int i = 0; i < proximity.size; i++){
                if(proximity.get(i) instanceof FluxFuser.FuserBuild f && f.team == team && (best == null || f.pos() < best.pos())) best = f;
            }
            target = best;
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            if(quality(liquid) <= 0f) return false;
            Liquid cur = liquids.current();
            return (cur == liquid || liquids.currentAmount() < 0.2f) && liquids.get(liquid) < liquidCapacity - 0.01f;
        }

        @Override
        public void updateTile(){
            if(target != null && !target.isValid()){ target = null; resolveTarget(); }
            power = 0f; supply = 0f; active = false;
            FluxFuser.FuserBuild t = target;
            if(t == null || !t.coolable() || t.rankOf(this) >= ThermalModel.MAX_COOLERS){ glow = Mathf.lerpDelta(glow, 0f, 0.05f); return; }
            active = true;
            if(!t.needsCooling()){ glow = Mathf.lerpDelta(glow, 0f, 0.05f); return; } //kalt: kein Verbrauch
            Liquid l = liquids.current();
            float q = quality(l);
            if(l == null || q <= 0f){ glow = Mathf.lerpDelta(glow, 0f, 0.05f); return; }
            float need = ThermalModel.LIQUID_PER_TICK * Time.delta;
            float used = Math.min(need, liquids.get(l));
            if(used > 0f){
                liquids.remove(l, used);
                supply = used / need;
                power = ThermalModel.coolerRate(q, supply);
            }
            glow = Mathf.lerpDelta(glow, power > 0f ? 1f : 0f, 0.08f);
        }

        public String statusText(){
            if(target == null) return Core.bundle.get("cooler.status.notarget");
            if(!target.coolable()) return Core.bundle.get("cooler.status.classic");
            if(target.rankOf(this) >= ThermalModel.MAX_COOLERS) return Core.bundle.format("cooler.status.surplus", ThermalModel.MAX_COOLERS);
            if(power > 0f) return Core.bundle.format("cooler.status.cooling", (int)target.tile.x, (int)target.tile.y);
            if(!target.needsCooling()) return Core.bundle.format("cooler.status.standby", (int)target.tile.x, (int)target.tile.y);
            return Core.bundle.get("cooler.status.nocoolant");
        }

        @Override
        public void display(Table table){
            super.display(table);
            table.row();
            table.table(t -> {
                t.left();
                t.label(() -> Core.bundle.get("cooler.role")).wrap().width(190f).left().color(FluxEffects.purple).row();
                t.label(() -> statusText()).wrap().width(190f).left().color(Color.lightGray);
            }).growX().left();
        }

        @Override
        public void drawSelect(){
            super.drawSelect();
            if(target != null){
                Drawf.square(target.x, target.y, target.block.size * tilesize / 2f + 2f, Color.valueOf("7fdcff"));
                Drawf.line(Color.valueOf("7fdcff"), x, y, target.x, target.y);
            }
        }

        @Override
        public void draw(){
            super.draw();
            Liquid l = liquids.current();
            if(l != null && liquids.currentAmount() > 0.5f){
                Draw.z(Layer.block + 0.1f);
                Draw.color(l.color, 0.35f + 0.4f * Mathf.clamp(liquids.currentAmount() / liquidCapacity));
                Fill.rect(x, y, size * tilesize * 0.34f, size * tilesize * 0.34f);
            }
            if(glow > 0.02f){
                Draw.z(Layer.blockAdditive);
                Draw.blend(Blending.additive);
                Draw.color(Color.valueOf("9fe8ff"), 0.25f * glow * (0.75f + 0.25f * Mathf.absin(Time.time, 6f, 1f)));
                Fill.rect(x, y, size * tilesize * 0.5f, size * tilesize * 0.5f);
                Draw.blend();
            }
            Draw.reset();
        }
    }
}
