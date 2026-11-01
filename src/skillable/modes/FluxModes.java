package skillable.modes;

import arc.struct.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.entities.bullet.*;
import mindustry.type.*;
import skillable.content.*;

/** Registry aller sieben Material-Modi. Der Turm erlaubt immer nur EIN Material gleichzeitig. */
public final class FluxModes{
    private FluxModes(){}

    /** Normalstrom-Betrieb (nur Modi mit powerCapable): Anteile gegenueber dem Flux-Betrieb. */
    public static final float POWER_DAMAGE = 0.45f, POWER_RANGE = 0.6f, POWER_RATE = 0.30f;

    private static final ObjectMap<Item, FluxMode> byItem = new ObjectMap<>(), byErekirItem = new ObjectMap<>();
    public static final Seq<FluxMode> all = new Seq<>();

    /** Modus fuer ein Material; Erekir-Zusatzmaterialien zaehlen nur auf Erekir. */
    public static FluxMode get(Item item){
        if(item == null) return null;
        if(item instanceof skillable.synth.SynthItem si) return skillable.synth.DynamicModes.get(si.slot);
        FluxMode m = byItem.get(item);
        if(m != null) return m;
        return FluxPlanet.erekir() ? byErekirItem.get(item) : null;
    }

    public static boolean supports(Item item){
        return get(item) != null;
    }

    private static void add(FluxMode m){
        byItem.put(m.item, m);
        if(m.erekirItem != null) byErekirItem.put(m.erekirItem, m);
        all.add(m);
    }

    public static void load(){
        byItem.clear();
        byErekirItem.clear();
        all.clear();
        //Reihenfolge = Ausbaustufen. Erekir-Zusatzmaterial: beryllium/oxide/thorium (Kupfer/Kohle/Blast gibt es dort nicht)
        add(new FluxMode("rapid", Items.copper, 0.8f, 0.02f, 20, FluxBullets.rapid()).color("4aa8ff").erekir(Items.beryllium).powerCapable(POWER_DAMAGE, POWER_RANGE));
        add(new FluxMode("rail", Items.graphite, 1.6f, 0.05f, 6, FluxBullets.rail()).color("e8f0ff").powerCapable(POWER_DAMAGE, POWER_RANGE));
        add(new FluxMode("incendiary", Items.coal, 1.4f, 0.06f, 8, FluxBullets.incendiary()).color("ff8a3a").erekir(Items.oxide).powerCapable(POWER_DAMAGE, POWER_RANGE));
        add(new FluxMode("smart", Items.silicon, 1.2f, 0.04f, 10, FluxBullets.smart()).color("9a5cff").powerCapable(POWER_DAMAGE, POWER_RANGE));
        //Hochstufen-Modi funktionieren nur mit Flux
        add(new FluxMode("catastrophic", Items.blastCompound, 4.5f, 0.30f, 2, FluxBullets.catastrophic()).color("ff5a1a").erekir(Items.thorium));
        add(new FluxMode("reality", Items.phaseFabric, 6.0f, 0.38f, 4, FluxBullets.reality()).color("c58cff"));
        //Synthetische Materialien (Flux Fuser) - nur mit Flux
        add(new FluxMode("conductor", FluxItems.carbonConductor, 1.0f, 0.06f, 6, FluxBullets.conductor()).color("7fa8ff"));
        add(new FluxMode("ferrite", FluxItems.ferriteComposite, 1.5f, 0.10f, 4, FluxBullets.ferrite()).color("d89870"));
        add(new FluxMode("synaptic", FluxItems.synapticAlloy, 2.0f, 0.16f, 3, FluxBullets.synaptic()).color("a98bff"));
        add(new FluxMode("pyroglass", FluxItems.pyroglass, 2.4f, 0.20f, 4, FluxBullets.pyroglass()).color("ff7a45"));
        add(new FluxMode("storm", Items.surgeAlloy, 5.0f, 0.34f, 4, FluxBullets.storm()).color("7ae0ff"));
    }

    /** Fuer die Statistik-Anzeige des Blocks (beide Planeten). */
    public static ObjectMap<UnlockableContent, BulletType> statMap(){
        ObjectMap<UnlockableContent, BulletType> map = new ObjectMap<>();
        for(FluxMode m : all){
            map.put(m.item, m.bullet);
            if(m.erekirItem != null) map.put(m.erekirItem, m.bullet);
        }
        return map;
    }
}
