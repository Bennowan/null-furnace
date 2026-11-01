package skillable.production;

import arc.struct.*;
import mindustry.content.*;
import mindustry.type.*;
import skillable.content.*;

import static mindustry.type.ItemStack.*;

/** Alle Rezepturen des Flux Fusers. Werte sind PLATZHALTER ohne Spieltests (docs/BALANCING.md). */
public final class FluxRecipes{
    private FluxRecipes(){}

    public static final Seq<FluxRecipe> all = new Seq<>();

    public static void load(){
        all.clear();
        //Kupfer + Kohle (+ Wasser zum Abschrecken) -> Carbon Conductor
        all.add(new FluxRecipe("carbon-conductor", FluxItems.carbonConductor, 1,
            with(Items.copper, 2, Items.coal, 2), with(Items.beryllium, 2, Items.graphite, 2),
            Liquids.water, 0.10f, Liquids.cryofluid, 120f, 0.9f, 0.05f, 1.4f, 0.55f));
        //Schrott + Graphit (+ Oel als Bindemittel) -> Ferrite Composite
        all.add(new FluxRecipe("ferrite-composite", FluxItems.ferriteComposite, 1,
            with(Items.scrap, 3, Items.graphite, 2), with(Items.sand, 3, Items.graphite, 2),
            Liquids.oil, 0.08f, Liquids.water, 150f, 1.2f, 0.08f, 1.2f, 0.80f));
        //Silizium + Kupfer (+ Kryofluid: unterkuehltes Gitter) -> Synaptic Alloy (riskant)
        all.add(new FluxRecipe("synaptic-alloy", FluxItems.synapticAlloy, 1,
            with(Items.silicon, 2, Items.copper, 2), with(Items.silicon, 2, Items.beryllium, 2),
            Liquids.cryofluid, 0.06f, Liquids.water, 180f, 1.8f, 0.30f, 1.2f, 0.65f));
        //Kohle + Silizium (+ Schlacke als heisses Medium) -> 2 Pyroglass
        all.add(new FluxRecipe("pyroglass", FluxItems.pyroglass, 2,
            with(Items.coal, 2, Items.silicon, 2), with(Items.sand, 2, Items.silicon, 2),
            Liquids.slag, 0.12f, Liquids.water, 150f, 1.4f, 0.22f, 1.2f, 0.80f));
    }
}
