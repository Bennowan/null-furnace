package skillable.blocks;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import skillable.content.*;
import skillable.fx.*;
import skillable.flux.*;
import skillable.material.*;
import skillable.production.*;
import skillable.synth.*;

import static mindustry.Vars.*;

/**
 * Flux Fuser - zwei Betriebsarten:
 *
 * FREI (Standard): Bis zu 11 verschiedene Zutaten (Items UND Fluessigkeiten) werden per Foerderband/Leitung zugefuehrt und
 * binden beim ersten Eintreffen einen Eingangsplatz. Das Mengenverhaeltnis ergibt sich aus den Mengen je Charge
 * (einstellbar pro Platz, 1..16 Teile). Aus Zusammensetzung und Verhaeltnis wird ein Materialprofil berechnet; das Ergebnis ist
 * ein Traeger-Item des {@link MaterialRegistry} (bleibt auf Foerderbaendern, im Kern und in Spielstaenden erhalten).
 *
 * KLASSIK: die vier urspruenglichen Rezepte (alte Welten und Items bleiben nutzbar).
 *
 * Flux ist nie gespeichert: ohne ausreichenden Flux pausiert die Charge. Reaktionswaerme steigt waehrend der Reaktion und
 * entweicht nur, wenn sie pausiert - eine durchgehende Reaktion (Waerme >= 1) loest eine echte Explosion aus.
 */
public class FluxFuser extends Block{
    public static final float MIN_SATISFACTION = 0.5f;
    public static final Stat recipesStat = new Stat("flux-recipes", StatCat.crafting);
    public static final Stat freeStat = new Stat("flux-free-synthesis", StatCat.crafting);

    public static final int SLOTS = 11, DEFAULT_LIMIT = 4, MAX_LIMIT = 16, OUT_CAP = 24, IDLE_RELEASE = 600;
    public static final float STRESS_DECAY = 0.00005f;
    /** Ruhezeit nach der letzten Aenderung der Mischung, bevor eine Charge starten darf. */
    public static final int SETTLE_TICKS = 60;
    //Befehle (Point2.x = cmd << 4 | slot, Point2.y = Wert)
    public static final int CMD_LIMIT = 1, CMD_RELEASE = 2, CMD_START = 3, CMD_ABORT = 4, CMD_MODE = 5, CMD_ARM = 6;

    public final boolean erekir;

    public FluxFuser(String name, boolean erekir){
        super(name);
        this.erekir = erekir;
        update = true;
        solid = true;
        sync = true;
        hasItems = true;
        hasLiquids = true;
        hasPower = false;
        configurable = true;
        saveConfig = true;
        itemCapacity = MAX_LIMIT;
        liquidCapacity = MAX_LIMIT * Ingredients.LIQUID_UNITS_PER_PART;
        group = BlockGroup.none;
        envEnabled = Env.any;
        outputsLiquid = false;
        config(Integer.class, (FuserBuild b, Integer i) -> b.setRecipe(i));
        config(Point2.class, (FuserBuild b, Point2 p) -> b.command(p.x >> 4, p.x & 15, p.y));
    }

    @Override
    public void init(){
        super.init();
        //TECHNISCHE URSACHE der "offenen" Optik: Block.consumesLiquid() liest nur liquidFilter[]. Ohne Eintrag behandelt
        //Building.moveLiquid jede nicht angenommene Fluessigkeit als Fremdstoff und spielt Dampf-/Feuereffekte samt Dauerschaden
        //an der Uebergabestelle ab. Der Fuser kennt jede Fluessigkeit als moegliche Zutat -> alle eintragen.
        if(liquidFilter == null) liquidFilter = new boolean[content.liquids().size];
        for(Liquid l : content.liquids()) liquidFilter[l.id] = true;
    }

    public Seq<FluxRecipe> recipes(){
        return FluxRecipes.all;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(freeStat, t -> {
            t.row();
            t.add(Core.bundle.get("stat.flux-free-text")).wrap().width(320f).left().padBottom(6f).row();
        });
        stats.add(recipesStat, t -> {
            t.row();
            for(FluxRecipe r : recipes()){
                StringBuilder in = new StringBuilder();
                for(ItemStack s : r.inputs(erekir)) in.append(s.amount).append("x ").append(s.item.localizedName).append(" + ");
                in.append(r.liquid.localizedName).append(" ").append(Strings.fixed(r.liquidPerTick * 60f, 1)).append("/s");
                t.add("[accent]" + r.output.localizedName + "[] x" + r.outputAmount + "  <-  " + in
                    + "\n[lightgray]" + Core.bundle.format("stat.flux-recipe-line", Strings.fixed(r.fluxPerTick * 60f, 0),
                    Strings.fixed(r.craftTime / 60f, 1), r.coolant.localizedName)).left().padBottom(6f).row();
            }
        });
    }

    @Override
    public void setBars(){
        super.setBars();
        removeBar("liquid");
        addBar("progress", (FuserBuild e) -> new Bar(
            () -> Core.bundle.format("bar.fuser-progress", (int)(e.progress * 100f)),
            () -> Pal.accent, () -> e.progress));
        addBar("flux-supply", (FuserBuild e) -> new Bar(
            () -> e.network == null ? Core.bundle.get("bar.flux-nonet")
                : e.lastDemand > 0f ? Core.bundle.format("bar.flux-supply", Strings.fixed(e.granted * 60f, 1), Strings.fixed(e.lastDemand * 60f, 1))
                : Core.bundle.format("bar.flux-ready", Strings.fixed(e.network.lastCapacity * 60f, 1)),
            () -> FluxEffects.colorFor(FluxEffects.intensity(e.satisfaction(), e.network == null ? 0f : e.network.maxInstability)).cpy(),
            () -> e.lastDemand > 0f ? e.satisfaction() : (e.network == null ? 0f : FluxMath.clamp01(e.network.lastCapacity))));
        //Bar 1: Klassik = Fluessigkeit des Rezepts, Frei = belegte Eingangsplaetze
        addBar("liq-ingredient", (FuserBuild e) -> e.classic
            ? new Bar(() -> Core.bundle.format("bar.fuser-liquid", e.recipe().liquid.localizedName), () -> e.recipe().liquid.barColor(), () -> e.liquids.get(e.recipe().liquid) / liquidCapacity)
            : new Bar(() -> Core.bundle.format("bar.fuser-slots", e.boundCount(), SLOTS), () -> Pal.accent, () -> e.boundCount() / (float)SLOTS));
        //Bar 2: Klassik = Kuehlmittel, Frei = Reaktionswaerme
        addBar("liq-coolant", (FuserBuild e) -> e.classic
            ? new Bar(() -> Core.bundle.format("bar.fuser-coolant", e.recipe().coolant.localizedName), () -> e.recipe().coolant.barColor(), () -> e.liquids.get(e.recipe().coolant) / liquidCapacity)
            : new Bar(() -> Core.bundle.format("bar.fuser-heat", (int)(e.heat * 100f)), () -> Tmp.c1.set(Color.valueOf("ffb35a")).lerp(Color.valueOf("ff3a2a"), Mathf.clamp(e.heat)).cpy(), () -> Mathf.clamp(e.heat)));
        addBar("fuser-cool", (FuserBuild e) -> new Bar(
            () -> e.classic ? "-" : Core.bundle.format("bar.fuser-cool", e.coolersActive, Strings.fixed(e.coolRate * 60f * 100f, 1)),
            () -> Color.valueOf("7fdcff").cpy(), () -> e.classic ? 0f : Mathf.clamp(e.coolRate / ThermalModel.MAX_COOLING)));
        addBar("fuser-stress", (FuserBuild e) -> new Bar(
            () -> Core.bundle.format("bar.fuser-stress", (int)(e.stress * 100f)),
            () -> Pal.remove, () -> e.classic ? 0f : Mathf.clamp(e.stress)));
        addBar("instability", (FuserBuild e) -> new Bar(
            () -> Core.bundle.format("bar.instability", (int)((e.network == null ? 0f : e.network.maxInstability) * 100f),
                Core.bundle.get(e.network != null && e.network.offline ? "flux.state.emp" : "flux.state.stable")),
            () -> FluxEffects.blue, () -> e.network == null ? 0f : e.network.maxInstability));
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        FluxNode.drawNodeHints(this, x, y);
    }

    public class FuserBuild extends Building implements FluxConsumer, MaterialHolder{
        //---- gemeinsam
        public boolean classic;
        public int recipeIdx;
        public float progress, granted, lastDemand, warmup, craftFlash;
        public boolean cooling, working;
        public @Nullable FluxNetwork network;
        private boolean registered;
        private float dumpTimer;

        //---- freie Synthese
        public final byte[] kind = new byte[SLOTS];      //0 leer, 1 Item, 2 Fluessigkeit
        public final short[] cid = new short[SLOTS];
        public final byte[] limit = new byte[SLOTS];
        public final boolean[] pinned = new boolean[SLOTS];
        private final float[] idle = new float[SLOTS];
        public boolean active, forceStart;
        public float heat, stress, batchParts;
        public int batchSlot = -1, outSlot = -1, outAmount;
        public String batchSig;
        public boolean archiveFull, outBlocked;
        private @Nullable MaterialProfile preview;
        private float previewTimer;
        private int cdumpOut;
        private float eventFlash;
        /** Betrieb scharf: Chargen starten automatisch. Aus = Vorbereiten (nichts startet von selbst). */
        public boolean armed;
        /** Verbleibende Ruhezeit nach der letzten Aenderung der Mischung. */
        public float settle;
        /** Aktuelle Kuehlleistung (Waerme/Tick), Zahl angeschlossener und wirksamer Kuehlmaentel. */
        public float coolRate;
        public int coolersAttached, coolersActive;
        public boolean tooHot, waitFlux, risky;
        private final float[] coolRates = new float[8];

        //================================================================== Hilfen
        public FluxRecipe recipe(){
            Seq<FluxRecipe> list = recipes();
            return list.get(Mathf.clamp(recipeIdx, 0, list.size - 1));
        }

        public void setRecipe(int i){
            if(i < 0){
                if(classic){ classic = false; progress = 0f; }
                return;
            }
            if(i >= recipes().size) return;
            if(!classic){ classic = true; active = false; batchSlot = -1; progress = 0f; heat = 0f; }
            if(i == recipeIdx) return;
            recipeIdx = i;
            progress = 0f;
        }

        public @Nullable Item itemAt(int s){ return kind[s] == 1 ? content.item(cid[s]) : null; }
        public @Nullable Liquid liquidAt(int s){ return kind[s] == 2 ? content.liquid(cid[s]) : null; }

        public int slotOfItem(Item it){
            for(int i = 0; i < SLOTS; i++) if(kind[i] == 1 && cid[i] == it.id) return i;
            return -1;
        }

        public int slotOfLiquid(Liquid l){
            for(int i = 0; i < SLOTS; i++) if(kind[i] == 2 && cid[i] == l.id) return i;
            return -1;
        }

        int freeSlot(){
            for(int i = 0; i < SLOTS; i++) if(kind[i] == 0) return i;
            return -1;
        }

        public int boundCount(){
            int n = 0;
            for(int i = 0; i < SLOTS; i++) if(kind[i] != 0) n++;
            return n;
        }

        /** Menge im Platz, in Teilen (Item = 1, 10 Einheiten Fluessigkeit = 1). */
        public float partsAt(int s){
            if(kind[s] == 1) return items.get(itemAt(s));
            if(kind[s] == 2) return liquids.get(liquidAt(s)) / Ingredients.LIQUID_UNITS_PER_PART;
            return 0f;
        }

        public float capAt(int s){ return limit[s]; }

        void bind(int s, short id, int type){
            kind[s] = (byte)type;
            cid[s] = id;
            limit[s] = DEFAULT_LIMIT;
            pinned[s] = false;
            idle[s] = 0f;
        }

        void unbind(int s){
            kind[s] = 0; cid[s] = 0; limit[s] = 0; pinned[s] = false; idle[s] = 0f;
        }

        /** Baut die Zusammensetzung aus dem, was gerade in den Plaetzen liegt (bis zum Limit). */
        @Nullable Composition currentComposition(boolean capToLimit){
            Seq<IngredientDef> defs = new Seq<>();
            FloatSeq parts = new FloatSeq();
            for(int i = 0; i < SLOTS; i++){
                if(kind[i] == 0) continue;
                float p = partsAt(i);
                if(capToLimit) p = Math.min(p, limit[i]);
                if(p <= 0.01f) continue;
                IngredientDef d = kind[i] == 1 ? Ingredients.of(itemAt(i)) : Ingredients.of(liquidAt(i));
                if(d == null) continue;
                defs.add(d);
                parts.add(p);
            }
            if(defs.isEmpty()) return null;
            return Composition.of(java.util.Arrays.asList(defs.toArray(IngredientDef.class)), parts.toArray());
        }

        public @Nullable MaterialProfile batchProfile(){
            if(!active || batchSlot < 0) return null;
            MaterialProfile p = MaterialRegistry.profile(FluxItems.material[batchSlot]);
            return p != null && (batchSig == null || batchSig.equals(p.signature)) ? p : null;
        }

        public float effectivePeak(MaterialProfile p){ return p.peakHeat * (1f + 0.5f * stress); }

        public float batchTicks(MaterialProfile p){ return p.synthTicksBase * (0.7f + 0.08f * Math.min(batchParts, 40f)); }

        @Override
        public boolean holdsMaterial(Item item){
            if(item == null) return false;
            if(outAmount > 0 && outSlot >= 0 && FluxItems.material[outSlot] == item) return true;
            return active && batchSlot >= 0 && FluxItems.material[batchSlot] == item;
        }

        //================================================================== Befehle
        public void command(int cmd, int slot, int value){
            if(cmd == CMD_MODE){ setRecipe(value == 1 ? Math.max(0, recipeIdx) : -1); return; }
            if(classic) return;
            switch(cmd){
                case CMD_LIMIT -> {
                    if(slot < 0 || slot >= SLOTS || kind[slot] == 0) return;
                    limit[slot] = (byte)Mathf.clamp(value, 1, MAX_LIMIT);
                    pinned[slot] = true;
                    settle = SETTLE_TICKS;
                }
                case CMD_RELEASE -> {
                    if(slot < 0 || slot >= SLOTS || kind[slot] == 0) return;
                    if(partsAt(slot) > 0.01f) return; //nur leere Plaetze freigeben
                    unbind(slot);
                    settle = SETTLE_TICKS;
                }
                case CMD_ARM -> armed = value != 0;
                case CMD_START -> forceStart = true;
                case CMD_ABORT -> abortBatch();
                default -> {}
            }
        }

        void abortBatch(){
            if(!active) { forceStart = false; return; }
            active = false;
            progress = 0f;
            batchSlot = -1; batchSig = null;
            eventFlash = 1f;
            if(!headless) FluxEffects.fuserDone.at(x, y, 0f, Color.gray);
        }

        //================================================================== Flux
        @Override public FluxNetwork fluxNetwork(){ return network; }

        @Override
        public void fluxNetwork(FluxNetwork n){
            network = n;
            if(n == null){ granted = 0f; lastDemand = 0f; }
        }

        @Override
        public float fluxDemand(){
            if(classic) lastDemand = canCraft(recipe()) ? recipe().fluxPerTick : 0f;
            else{
                MaterialProfile p = batchProfile();
                lastDemand = p == null ? 0f : p.synthFlux;
            }
            return lastDemand;
        }

        @Override public int fluxPriority(){ return 0; }
        @Override public void fluxGranted(float g){ granted = FluxMath.safe(g); }

        @Override
        public float riskTarget(){
            if(!working) return 0f;
            if(classic){
                FluxRecipe r = recipe();
                return r.risk * (cooling ? r.coolRisk : 1f) * satisfaction();
            }
            MaterialProfile p = batchProfile();
            if(p == null) return 0f;
            return (0.03f + 0.18f * Mathf.clamp(effectivePeak(p))) * satisfaction();
        }

        public float satisfaction(){
            return lastDemand <= 0f ? 0f : FluxMath.clamp01(granted / lastDemand);
        }

        public boolean fluxOk(){
            return lastDemand > 0f && granted >= lastDemand * MIN_SATISFACTION;
        }

        //================================================================== Klassik-Produktion (unveraendert)
        public boolean hasInputs(FluxRecipe r){
            for(ItemStack s : r.inputs(erekir)) if(items.get(s.item) < s.amount) return false;
            return true;
        }

        public boolean outputFree(FluxRecipe r){
            return items.get(r.output) + r.outputAmount <= itemCapacity;
        }

        public boolean canCraft(FluxRecipe r){
            return hasInputs(r) && outputFree(r) && liquids.get(r.liquid) >= Math.max(0.02f, r.liquidPerTick * 0.5f);
        }

        //================================================================== Annahme
        @Override
        public boolean acceptItem(Building source, Item item){
            if(classic){
                for(ItemStack s : recipe().inputs(erekir)){
                    if(s.item == item) return items.get(item) < recipe().capacity(s);
                }
                return false;
            }
            if(Ingredients.of(item) == null) return false; //unbekanntes/beschaedigtes Material
            int s = slotOfItem(item);
            if(s >= 0) return items.get(item) < limit[s];
            return freeSlot() >= 0 && DEFAULT_LIMIT > items.get(item);
        }

        @Override
        public void handleItem(Building source, Item item){
            super.handleItem(source, item);
            if(classic) return;
            settle = SETTLE_TICKS;
            int s = slotOfItem(item);
            if(s < 0){
                s = freeSlot();
                if(s >= 0) bind(s, item.id, 1);
            }
            if(s >= 0) idle[s] = 0f;
        }

        @Override
        public int getMaximumAccepted(Item item){
            if(classic){
                for(ItemStack s : recipe().inputs(erekir)) if(s.item == item) return recipe().capacity(s);
                return 0;
            }
            int s = slotOfItem(item);
            return s >= 0 ? limit[s] : DEFAULT_LIMIT;
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            if(classic){
                FluxRecipe r = recipe();
                return (liquid == r.liquid || liquid == r.coolant) && liquids.get(liquid) < liquidCapacity - 0.01f;
            }
            if(Ingredients.of(liquid) == null) return false;
            int s = slotOfLiquid(liquid);
            float cap = (s >= 0 ? limit[s] : DEFAULT_LIMIT) * Ingredients.LIQUID_UNITS_PER_PART;
            if(s < 0 && freeSlot() < 0) return false;
            return liquids.get(liquid) < cap - 0.01f;
        }

        @Override
        public void handleLiquid(Building source, Liquid liquid, float amount){
            super.handleLiquid(source, liquid, amount);
            if(classic) return;
            settle = SETTLE_TICKS;
            int s = slotOfLiquid(liquid);
            if(s < 0){
                s = freeSlot();
                if(s >= 0) bind(s, liquid.id, 2);
            }
            if(s >= 0) idle[s] = 0f;
        }

        //================================================================== Update
        @Override
        public void updateTile(){
            if(!registered){
                registered = true;
                FluxNetworkManager.get().register(this);
            }
            if(classic) updateClassic();
            else updateFree();
            warmup = Mathf.lerpDelta(warmup, working ? 1f : 0f, 0.05f);
            craftFlash = Math.max(0f, craftFlash - Time.delta / 20f);
            eventFlash = Math.max(0f, eventFlash - Time.delta / 30f);
        }

        void updateClassic(){
            FluxRecipe r = recipe();
            boolean can = canCraft(r);
            working = false;
            cooling = false;
            if(can && fluxOk()){
                working = true;
                float rate = satisfaction();
                float use = r.liquidPerTick * Time.delta * rate;
                if(liquids.get(r.liquid) >= use){
                    liquids.remove(r.liquid, use);
                    float cu = FluxRecipe.COOLANT_PER_TICK * Time.delta * rate;
                    if(liquids.get(r.coolant) >= cu){
                        liquids.remove(r.coolant, cu);
                        cooling = true;
                        rate *= r.coolSpeed;
                    }
                    progress += Time.delta / r.craftTime * rate;
                    if(progress >= 1f){
                        progress = 0f;
                        for(ItemStack s : r.inputs(erekir)) items.remove(s.item, s.amount);
                        items.add(r.output, r.outputAmount);
                        craftFlash = 1f;
                        if(!headless) FluxEffects.fuserDone.at(x, y, 0f, r.output.color);
                    }
                }else{
                    working = false;
                }
            }
            //Ergebnis und nicht benoetigte Items weitergeben
            dumpTimer += Time.delta;
            if(dumpTimer >= 5f){
                dumpTimer = 0f;
                if(items.get(r.output) > 0) dump(r.output);
                for(Item it : content.items()){
                    if(it == r.output || items.get(it) <= 0) continue;
                    boolean needed = false;
                    for(ItemStack s : r.inputs(erekir)) if(s.item == it) needed = true;
                    if(!needed) dump(it); //Reste einer frueheren Rezeptur
                }
            }
        }

        //---- Temperatur, Kuehlung, Zustand
        public boolean coolable(){ return !classic; }

        public boolean needsCooling(){ return !classic && heat > 0.02f; }

        /** Rang eines angeschlossenen Kuehlmantels (nach Position geordnet); nur die ersten MAX_COOLERS wirken. */
        public int rankOf(Building cooler){
            int r = 0;
            for(int i = 0; i < proximity.size; i++){
                if(proximity.get(i) instanceof FuserCooler.CoolerBuild c && c.target == this && c != cooler && c.pos() < cooler.pos()) r++;
            }
            return r;
        }

        void updateCooling(){
            int n = 0;
            coolersAttached = 0; coolersActive = 0;
            for(int i = 0; i < proximity.size; i++){
                if(proximity.get(i) instanceof FuserCooler.CoolerBuild c && c.target == this){
                    coolersAttached++;
                    if(c.active){ coolersActive++; if(n < coolRates.length) coolRates[n++] = c.power; }
                }
            }
            coolRate = ThermalModel.totalCooling(coolRates, n);
        }

        public int state(){
            return FuserState.of(armed, active, heat, settle > 0f, tooHot);
        }

        void updateFree(){
            working = false;
            cooling = false;
            //Plaetze: ungenutzte, nicht angepinnte, leere Plaetze geben sich nach einer Weile frei (verirrte Items blockieren nichts)
            for(int i = 0; i < SLOTS; i++){
                if(kind[i] == 0) continue;
                if(partsAt(i) > 0.01f) idle[i] = 0f;
                else if(!pinned[i] && (idle[i] += Time.delta) > IDLE_RELEASE) unbind(i);
            }
            //Reste aus dem Klassik-Betrieb (nicht zugeordnete Items) weitergeben
            dumpTimer += Time.delta;
            if(dumpTimer >= 5f){
                dumpTimer = 0f;
                if(!net.client()){
                    for(Item it : content.items()){
                        if(items.get(it) > 0 && slotOfItem(it) < 0) dump(it);
                    }
                    dumpOutput();
                }
            }
            if(!net.client() && !active) tryStart();

            MaterialProfile p = batchProfile();
            if(active && p == null && !net.client()){
                active = false; batchSlot = -1; progress = 0f; batchSig = null; //Register-Eintrag fehlt: Charge verworfen
            }
            updateCooling();
            if(settle > 0f) settle = Math.max(0f, settle - Time.delta);
            if(active && p != null){
                float gain = 0f;
                if(fluxOk()){
                    working = true;
                    float rate = satisfaction();
                    float ticks = batchTicks(p);
                    progress += Time.delta / ticks * rate;
                    gain = ThermalModel.gainPerTick(effectivePeak(p) * rate, ticks);
                }
                heat = ThermalModel.step(heat, gain, coolRate, Time.delta);
                if(!net.client()){
                    if(heat >= 1f) runaway(p);
                    else if(progress >= 1f) complete(p);
                }
            }else{
                heat = ThermalModel.step(heat, 0f, coolRate, Time.delta);
            }
            stress = Math.max(0f, stress - STRESS_DECAY * Time.delta);

            //Vorschau fuer die Anzeige (Server und Client)
            previewTimer += Time.delta;
            if(previewTimer > 20f){
                previewTimer = 0f;
                Composition c = active ? null : currentComposition(true);
                preview = c == null ? null : MaterialProfile.of(c);
            }
            if(!headless && working && heat > 0.6f && Mathf.chance(0.05 * (heat - 0.5f))) eventFlash = Math.max(eventFlash, 0.4f);
        }

        void tryStart(){
            archiveFull = false;
            outBlocked = false;
            tooHot = false;
            waitFlux = false;
            risky = false;
            boolean any = false, allFull = true;
            for(int i = 0; i < SLOTS; i++){
                if(kind[i] == 0) continue;
                float have = partsAt(i);
                if(have > 0.01f) any = true;
                if(have < limit[i] - (kind[i] == 2 ? 0.05f : 0.01f)) allFull = false;
            }
            if(!any) { forceStart = false; return; }
            if(!armed && !forceStart) return;                //Vorbereiten: nichts startet von selbst
            if(!(allFull || forceStart)) return;
            if(settle > 0f && !forceStart) return;           //Mischung wurde gerade veraendert: erst zur Ruhe kommen lassen
            Composition c = currentComposition(true);
            if(c == null){ forceStart = false; return; }
            MaterialProfile pv = MaterialProfile.of(c);
            //Auto-Start nur bei sicherer Mischung und kuehlem Fuser. Riskante Chargen (Spitze zu hoch) starten nie von selbst,
            //sondern nur nach bewusster Bestaetigung ("Jetzt starten") bei kaltem Fuser.
            boolean safe = ThermalModel.startAllowed(heat, effectivePeak(pv));
            if(forceStart ? !ThermalModel.forceAllowed(heat) : !safe){
                tooHot = heat > 0.2f;
                risky = !safe && heat <= 0.2f;
                return;
            }
            if(network == null || network.lastCapacity < pv.synthFlux * MIN_SATISFACTION){ waitFlux = true; return; } //Zutaten nicht verbrauchen, solange kein Flux da ist
            if(!MaterialRegistry.canObtain(c)){ archiveFull = true; return; }
            //Ausgang muss frei sein oder dasselbe Material tragen
            int existing = -1;
            SynthItem existingItem = null;
            if(outAmount > 0){
                existingItem = outSlot >= 0 ? FluxItems.material[outSlot] : null;
            }
            SynthItem it = MaterialRegistry.obtain(c);
            if(it == null){ archiveFull = true; return; }
            if(outAmount > 0 && (existingItem != it || outAmount > OUT_CAP / 2)){ outBlocked = true; return; }
            //Zutaten in die Reaktionskammer uebernehmen
            for(int i = 0; i < SLOTS; i++){
                if(kind[i] == 0) continue;
                float take = Math.min(partsAt(i), limit[i]);
                if(take <= 0.01f) continue;
                if(kind[i] == 1) items.remove(itemAt(i), Math.round(take));
                else liquids.remove(liquidAt(i), take * Ingredients.LIQUID_UNITS_PER_PART);
            }
            active = true;
            forceStart = false;
            progress = 0f;
            batchSlot = it.slot;
            batchSig = c.signature;
            batchParts = c.parts;
            MaterialRegistry.touch(it);
        }

        void complete(MaterialProfile p){
            float peak = effectivePeak(p);
            int hazard = MaterialProfile.hazardFor(peak);
            float yf = MaterialProfile.yieldFactor(hazard);
            int amount = Mathf.clamp(Math.round(batchParts * 0.5f * yf), 1, 12);
            outSlot = batchSlot;
            outAmount += amount;
            active = false;
            progress = 0f;
            batchSlot = -1; batchSig = null;
            stress = Math.min(1f, stress + (hazard == MaterialProfile.HAZARD_CRITICAL ? 0.12f : hazard == MaterialProfile.HAZARD_UNSTABLE ? 0.04f : 0f));
            craftFlash = 1f;
            if(!headless){
                SynthItem out = FluxItems.material[outSlot];
                FluxEffects.fuserDone.at(x, y, 0f, out.color);
            }
        }

        /** Durchgehende Reaktion: Waerme >= 1. Echte Explosion nach Menge und Stoffwerten der Charge. */
        void runaway(MaterialProfile p){
            float parts = Math.max(1f, batchParts);
            float rad = size * tilesize * 0.5f + 6f * Mathf.sqrt(parts);
            //Engine-Explosion ohne Teamschutz (7-Parameter-Variante, wie bei zerstoerten Behaeltern): trifft auch eigene Gebaeude
            Damage.dynamicExplosion(x, y, p.flammability * parts * 0.8f, p.explosiveness * parts * 0.9f + 0.3f * parts * Math.min(1f, Math.max(0f, p.peakHeat - 0.8f)),
                p.conductivity * parts * 6f, rad, true);
            //Der Fuser selbst traegt den Hauptteil: Schaden waechst mit Chargengroesse und Ueberschreitung der Schwelle
            damage(maxHealth * Mathf.clamp(0.12f + 0.012f * parts + 0.15f * Math.max(0f, effectivePeak(p) - 1f), 0.1f, 0.6f));
            if(network != null) network.kick(0.18f + 0.02f * Math.min(parts, 20f), 0.6f);
            stress = Math.min(1f, stress + 0.35f);
            active = false;
            progress = 0f;
            batchSlot = -1; batchSig = null;
            heat = 0.25f;
            armed = false; //nach einer Durchreaktion zurueck in die Vorbereitung
            eventFlash = 1f;
            if(!headless){
                FluxEffects.blast.at(x, y);
                Effect.shake(3f, 20f, x, y);
            }
        }

        void dumpOutput(){
            if(outAmount <= 0 || outSlot < 0) return;
            Item out = FluxItems.material[outSlot];
            for(int i = 0; i < proximity.size; i++){
                Building next = proximity.get((i + cdumpOut) % proximity.size);
                if(next.team == team && next.acceptItem(this, out)){
                    next.handleItem(this, out);
                    outAmount--;
                    cdumpOut = (cdumpOut + i + 1) % Math.max(1, proximity.size);
                    if(outAmount <= 0){ outAmount = 0; outSlot = -1; }
                    return;
                }
            }
        }

        //================================================================== Anzeige
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

        public String statusText(){
            if(classic) return classicStatus();
            if(network != null && network.offline) return Core.bundle.get("flux.status.offline");
            int st = state();
            String head = "[accent]" + Core.bundle.get("fuser.state." + FuserState.KEYS[st]) + "[]"
                + (coolersActive > 0 ? "  [#7fdcff]" + Core.bundle.format("fuser.cooled", coolersActive, Strings.fixed(coolRate * 60f * 100f, 1)) + "[]" : "") + "\n";
            MaterialProfile p = batchProfile();
            if(active && p != null){
                String name = MaterialVisuals.displayName(p);
                if(heat >= ThermalModel.OVERHEAT_AT) return head + Core.bundle.format("flux.fuser.runaway", name, (int)(heat * 100f));
                if(network == null) return head + Core.bundle.get("flux.status.nonet");
                if(!fluxOk()) return head + Core.bundle.format("flux.fuser.paused", Strings.fixed(granted * 60f, 1), Strings.fixed(lastDemand * 60f, 1));
                return head + Core.bundle.format("flux.fuser.synth", name, MaterialVisuals.hazardName(MaterialProfile.hazardFor(effectivePeak(p))))
                    + (preview == null ? "" : "\n" + Core.bundle.get("fuser.next.prep"));
            }
            if(archiveFull) return head + Core.bundle.format("flux.fuser.archivefull", MaterialRegistry.used(), MaterialRegistry.SLOTS);
            if(outBlocked) return head + Core.bundle.get("flux.fuser.outblocked");
            int bound = boundCount();
            if(bound == 0) return head + Core.bundle.get(armed ? "flux.fuser.feed" : "fuser.hint.prepare");
            StringBuilder miss = new StringBuilder();
            for(int i = 0; i < SLOTS; i++){
                if(kind[i] == 0) continue;
                if(partsAt(i) < limit[i] - 0.05f){
                    String n = kind[i] == 1 ? itemAt(i).localizedName : liquidAt(i).localizedName;
                    miss.append(n).append(' ').append((int)Math.ceil(limit[i] - partsAt(i))).append("  ");
                }
            }
            String pv = preview == null ? "" : "\n" + Core.bundle.format("flux.fuser.preview", MaterialVisuals.displayName(preview), MaterialVisuals.hazardName(preview.hazard));
            if(st == FuserState.PREPARE) return head + Core.bundle.get("fuser.hint.prepare") + pv;
            if(st == FuserState.OVERHEATED) return head + Core.bundle.format("fuser.hint.overheated", (int)(heat * 100f)) + pv;
            if(st == FuserState.COOLING_DOWN) return head + Core.bundle.format("fuser.hint.cooldown", (int)(heat * 100f)) + pv;
            if(risky) return head + Core.bundle.get("fuser.hint.risky") + pv;
            if(waitFlux) return head + Core.bundle.get("fuser.hint.waitflux") + pv;
            if(st == FuserState.SETTLING) return head + Core.bundle.format("fuser.hint.settling", (int)Math.ceil(settle / 60f)) + pv;
            if(miss.length() > 0) return head + Core.bundle.format("flux.fuser.waiting", miss.toString().trim()) + pv;
            return head + Core.bundle.get("flux.fuser.ready") + pv;
        }

        String classicStatus(){
            FluxRecipe r = recipe();
            if(network != null && network.offline) return Core.bundle.get("flux.status.offline");
            if(!hasInputs(r)){
                StringBuilder sb = new StringBuilder();
                for(ItemStack s : r.inputs(erekir)) if(items.get(s.item) < s.amount) sb.append(s.amount).append("x ").append(s.item.localizedName).append(' ');
                return Core.bundle.format("flux.fuser.missing", sb.toString().trim());
            }
            if(!outputFree(r)) return Core.bundle.get("flux.fuser.full");
            if(liquids.get(r.liquid) < Math.max(0.02f, r.liquidPerTick * 0.5f)) return Core.bundle.format("flux.fuser.noliquid", r.liquid.localizedName);
            if(network == null) return Core.bundle.get("flux.status.nonet");
            if(network.sources.isEmpty()) return Core.bundle.get("flux.status.nosource");
            if(network.lastNominal <= 0f) return Core.bundle.get("flux.status.nopower");
            if(!fluxOk()) return Core.bundle.format("flux.fuser.paused", Strings.fixed(granted * 60f, 1), Strings.fixed(lastDemand * 60f, 1));
            return Core.bundle.format(cooling ? "flux.fuser.workingcool" : "flux.fuser.working", r.output.localizedName);
        }

        @Override
        public void display(Table table){
            super.display(table);
            table.row();
            table.table(t -> {
                t.left();
                t.label(() -> Core.bundle.get("flux.fuser.role")).wrap().width(190f).left().color(FluxEffects.purple).row();
                t.label(() -> statusText()).wrap().width(190f).left().color(Color.lightGray);
            }).growX().left();
        }

        //================================================================== Oberflaeche
        private boolean uiClassic;

        @Override
        public void buildConfiguration(Table table){
            Table inner = new Table();
            table.add(inner);
            uiClassic = classic;
            buildMode(inner);
            inner.update(() -> {
                if(uiClassic != classic){
                    uiClassic = classic;
                    inner.clearChildren();
                    buildMode(inner);
                }
            });
        }

        void buildMode(Table t){
            t.table(m -> {
                m.button(Core.bundle.get("fuser.mode.free"), Styles.togglet, () -> configure(new Point2(CMD_MODE << 4, 0))).size(120f, 34f).update(b -> b.setChecked(!classic));
                m.button(Core.bundle.get("fuser.mode.classic"), Styles.togglet, () -> configure(new Point2(CMD_MODE << 4, 1))).size(120f, 34f).update(b -> b.setChecked(classic));
            }).padBottom(4f).row();
            if(classic) buildClassic(t);
            else buildFree(t);
        }

        void buildClassic(Table table){
            Seq<FluxRecipe> list = recipes();
            table.table(r -> {
                for(int i = 0; i < list.size; i++){
                    final int idx = i;
                    Item out = list.get(i).output;
                    r.button(new TextureRegionDrawable(out.uiIcon), Styles.clearTogglei, 40f, () -> configure(idx))
                        .size(50f).tooltip(out.localizedName).update(b -> b.setChecked(recipeIdx == idx));
                }
            });
        }

        void buildFree(Table t){
            //3x4-Raster: 11 Eingangsplaetze + Ergebnis
            t.table(grid -> {
                for(int i = 0; i < 12; i++){
                    final int idx = i;
                    if(idx < SLOTS) slotCell(grid, idx);
                    else outCell(grid);
                    if(i % 3 == 2) grid.row();
                }
            }).row();
            t.table(b -> {
                b.button(Core.bundle.get("fuser.arm"), Styles.togglet, () -> configure(new Point2(CMD_ARM << 4, armed ? 0 : 1))).size(230f, 34f)
                    .update(x -> x.setChecked(armed)).tooltip(Core.bundle.get("fuser.arm.tip"));
            }).padTop(4f).row();
            t.table(b -> {
                b.button(Core.bundle.get("fuser.start"), Styles.defaultt, () -> configure(new Point2(CMD_START << 4, 0))).size(110f, 34f)
                    .disabled(x -> active).tooltip(Core.bundle.get("fuser.start.tip"));
                b.button(Core.bundle.get("fuser.abort"), Styles.defaultt, () -> configure(new Point2(CMD_ABORT << 4, 0))).size(110f, 34f)
                    .disabled(x -> !active).tooltip(Core.bundle.get("fuser.abort.tip"));
            }).padTop(4f).row();
            t.label(() -> statusText()).wrap().width(250f).left().color(Color.lightGray).padTop(4f);
        }

        void slotCell(Table g, int i){
            g.table(Styles.grayPanel, c -> {
                c.defaults().pad(1f);
                Image img = new Image(Tex.clear);
                img.update(() -> {
                    TextureRegion r = kind[i] == 1 ? itemAt(i).uiIcon : kind[i] == 2 ? liquidAt(i).uiIcon : null;
                    img.setDrawable(r == null ? Tex.clear : new TextureRegionDrawable(r));
                });
                c.add(img).size(30f).row();
                c.label(() -> kind[i] == 0 ? "-" : (kind[i] == 1 ? items.get(itemAt(i)) + "" : Strings.fixed(partsAt(i), 1)) + "/" + limit[i])
                    .color(Color.lightGray).fontScale(0.85f).row();
                c.table(b -> {
                    b.button("-", Styles.cleart, () -> configure(new Point2(CMD_LIMIT << 4 | i, limit[i] - 1))).size(22f).disabled(x -> kind[i] == 0);
                    b.button("+", Styles.cleart, () -> configure(new Point2(CMD_LIMIT << 4 | i, limit[i] + 1))).size(22f).disabled(x -> kind[i] == 0);
                    b.button("x", Styles.cleart, () -> configure(new Point2(CMD_RELEASE << 4 | i, 0))).size(22f).disabled(x -> kind[i] == 0 || partsAt(i) > 0.01f);
                });
            }).size(84f, 102f).pad(2f);
        }

        void outCell(Table g){
            g.table(Styles.grayPanel, c -> {
                Image img = new Image(Tex.clear);
                img.update(() -> {
                    SynthItem it = outAmount > 0 && outSlot >= 0 ? FluxItems.material[outSlot] : active && batchSlot >= 0 ? FluxItems.material[batchSlot] : null;
                    img.setDrawable(it == null ? Tex.clear : new TextureRegionDrawable(it.uiIcon));
                });
                c.add(img).size(36f).row();
                c.label(() -> outAmount > 0 ? "x" + outAmount : active ? (int)(progress * 100f) + "%" : "-").color(Pal.accent).row();
                c.label(() -> {
                    SynthItem it = outAmount > 0 && outSlot >= 0 ? FluxItems.material[outSlot] : active && batchSlot >= 0 ? FluxItems.material[batchSlot] : null;
                    return it == null ? "" : it.localizedName;
                }).wrap().width(78f).fontScale(0.7f).color(Color.lightGray);
            }).size(84f, 102f).pad(2f);
        }

        @Override
        public Object config(){ return classic ? (Object)recipeIdx : (Object)(-1); }

        @Override
        public void drawSelect(){
            super.drawSelect();
            FluxNode.drawLinkedNodes(this);
        }

        //================================================================== Zeichnen (alles innerhalb der Kammer)
        @Override
        public void draw(){
            super.draw();
            float z0 = Draw.z();
            float R = size * tilesize * 0.26f; //Kammerradius (< Blockhalbbreite)
            float inten = FluxEffects.intensity(satisfaction(), network == null ? 0f : network.maxInstability);

            //Fluessigkeitsstand in der Kammer: Mischfarbe aller enthaltenen Fluessigkeiten
            float tot = 0f, lr = 0f, lg = 0f, lb = 0f;
            for(Liquid l : content.liquids()){
                float a = liquids.get(l);
                if(a <= 0.05f) continue;
                tot += a; lr += l.color.r * a; lg += l.color.g * a; lb += l.color.b * a;
            }
            Draw.z(Layer.block + 0.1f);
            if(tot > 0.05f){
                Draw.color(lr / tot, lg / tot, lb / tot, 0.75f);
                Fill.circle(x, y, R * (0.35f + 0.55f * Mathf.clamp(tot / (liquidCapacity * 0.5f))));
            }

            Color batchColor = classic ? recipe().output.color
                : (active && batchSlot >= 0 ? FluxItems.material[batchSlot].color : Color.white);
            Draw.z(Layer.blockAdditive);
            Draw.blend(Blending.additive);
            //Kern: Farbe des entstehenden Materials, Helligkeit nach Aktivitaet; Hitze faerbt ueber Rot nach Weiss
            float hot = classic ? 0f : Mathf.clamp(heat);
            Tmp.c1.set(batchColor).lerp(Color.valueOf("ff5a2a"), hot * 0.8f).lerp(Color.white, Mathf.clamp(hot - 0.7f) * 2f);
            Draw.color(Tmp.c1, 0.15f + 0.45f * warmup + 0.4f * craftFlash);
            Fill.circle(x, y, R * 0.72f);
            //rotierender Flux-Ring, Tempo nach Aktivitaet
            Draw.color(FluxEffects.colorFor(inten), 0.2f + 0.6f * warmup);
            Lines.stroke(1.4f);
            Lines.circle(x, y, R * 1.15f);
            float rot = Time.time * (1f + 4f * warmup);
            for(int i = 0; i < 3; i++) Lines.arc(x, y, R * 1.45f, 0.18f + 0.22f * progress, rot + i * 120f);

            if(!classic){
                //Zutaten kreisen als Punkte in ihrer Farbe (Anzahl = Menge), bei laufender Charge schneller und enger
                int n = 0;
                for(int i = 0; i < SLOTS; i++) if(kind[i] != 0 && partsAt(i) > 0.05f) n++;
                int k = 0;
                for(int i = 0; i < SLOTS; i++){
                    if(kind[i] == 0 || partsAt(i) <= 0.05f) continue;
                    Color c = kind[i] == 1 ? itemAt(i).color : liquidAt(i).color;
                    float fill = Mathf.clamp(partsAt(i) / Math.max(1f, limit[i]));
                    float a = Time.time * (active ? 3.2f : 0.8f) + k * 360f / Math.max(1, n);
                    float rr = R * (active ? 0.5f : 0.9f) * (0.8f + 0.2f * Mathf.sin(Time.time * 0.1f + i));
                    Draw.color(c, 0.45f + 0.5f * fill);
                    Fill.circle(x + Angles.trnsx(a, rr), y + Angles.trnsy(a, rr), 0.7f + 0.9f * fill);
                    k++;
                }
                //Funken: gestaltet sich nach Reaktivitaet, beschraenkt auf die Kammer
                if(active && warmup > 0.3f){
                    MaterialProfile p = batchProfile();
                    int sparks = p == null ? 2 : 2 + Math.round(4f * Mathf.clamp(p.peakHeat));
                    Draw.color(Color.white, Tmp.c1, 0.5f);
                    Lines.stroke(0.7f);
                    for(int i = 0; i < sparks; i++){
                        float a = Time.time * 4f + i * 360f / sparks + Mathf.randomSeed(i + (int)(Time.time / 4f) * 7, 30f);
                        float r0 = R * 0.3f, r1 = Math.min(R * 0.95f, r0 + 1.5f + 2.5f * Mathf.absin(Time.time + i * 5f, 6f, 1f));
                        Lines.line(x + Angles.trnsx(a, r0), y + Angles.trnsy(a, r0), x + Angles.trnsx(a, r1), y + Angles.trnsy(a, r1));
                    }
                }
                //Warnring bei drohender Durchreaktion
                if(heat > 0.6f || eventFlash > 0f){
                    float w = Mathf.clamp((heat - 0.6f) * 2.5f) * (0.6f + 0.4f * Mathf.absin(Time.time, 4f, 1f)) + eventFlash;
                    Draw.color(Color.valueOf("ff3a2a"), Mathf.clamp(w) * 0.8f);
                    Lines.stroke(1.6f);
                    Lines.circle(x, y, R * 1.7f);
                }
            }
            if(!classic && coolRate > 0f){ //gekuehlt: duenner kalter Ring in der Kammer
                Draw.color(Color.valueOf("9fe8ff"), 0.25f + 0.25f * Mathf.absin(Time.time, 8f, 1f));
                Lines.stroke(1f);
                Lines.circle(x, y, R * 1.3f);
            }
            Draw.blend();
            Draw.reset();
            Draw.z(z0);
        }

        //================================================================== Speichern (Version 2; Version 1 = alter Klassik-Stand)
        @Override
        public void write(Writes write){
            super.write(write);
            write.b(3);
            write.bool(classic);
            write.b(recipeIdx);
            write.f(Mathf.clamp(progress, 0f, 1f));
            for(int i = 0; i < SLOTS; i++){
                write.b(kind[i]); write.s(cid[i]); write.b(limit[i]); write.bool(pinned[i]);
            }
            write.bool(active);
            write.f(heat); write.f(stress); write.f(batchParts);
            write.b(batchSlot);
            write.bool(batchSig != null);
            if(batchSig != null) write.str(batchSig);
            write.b(outSlot);
            write.s(outAmount);
            write.bool(armed);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            int ver = read.b();
            if(ver <= 1){
                //alter Spielstand: reiner Klassik-Fuser
                classic = true;
                recipeIdx = Mathf.clamp(read.b(), 0, Math.max(0, recipes().size - 1));
                progress = Mathf.clamp(FluxMath.safe(read.f()), 0f, 1f);
                return;
            }
            classic = read.bool();
            recipeIdx = Mathf.clamp(read.b(), 0, Math.max(0, recipes().size - 1));
            progress = Mathf.clamp(FluxMath.safe(read.f()), 0f, 1f);
            for(int i = 0; i < SLOTS; i++){
                byte k = read.b(); short id = read.s(); byte lim = read.b(); boolean pin = read.bool();
                boolean valid = (k == 1 && content.item(id) != null) || (k == 2 && content.liquid(id) != null);
                if(valid){
                    kind[i] = k; cid[i] = id; limit[i] = (byte)Mathf.clamp(lim, 1, MAX_LIMIT); pinned[i] = pin;
                }else unbind(i);
            }
            active = read.bool();
            heat = Mathf.clamp(FluxMath.safe(read.f()), 0f, 1.5f);
            stress = Mathf.clamp(FluxMath.safe(read.f()), 0f, 1f);
            batchParts = Mathf.clamp(FluxMath.safe(read.f()), 0f, 200f);
            int bs = read.b();
            batchSlot = bs >= 0 && bs < MaterialRegistry.SLOTS ? bs : -1;
            batchSig = read.bool() ? read.str() : null;
            int os = read.b();
            outSlot = os >= 0 && os < MaterialRegistry.SLOTS ? os : -1;
            outAmount = Mathf.clamp(read.s(), 0, OUT_CAP);
            if(outSlot < 0) outAmount = 0;
            armed = ver >= 3 ? read.bool() : true; //aeltere Staende liefen automatisch weiter
            if(batchSlot < 0) active = false;
        }
    }
}
