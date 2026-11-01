package skillable.content;

import arc.graphics.*;
import mindustry.type.*;
import skillable.synth.*;

/** Die vier synthetischen Materialien. Jedes hat eigene Grafik, Beschreibung, Rezept (siehe FluxRecipes) und eigenes Schussverhalten. */
public final class FluxItems{
    private FluxItems(){}

    public static Item carbonConductor, ferriteComposite, synapticAlloy, pyroglass;
    /** Traeger-Items der frei synthetisierten Materialien (siehe MaterialRegistry). */
    public static SynthItem[] material;

    public static void load(){
        material = new SynthItem[MaterialRegistry.SLOTS];
        for(int i = 0; i < material.length; i++) material[i] = new SynthItem(i);
        carbonConductor = new Item("carbon-conductor", Color.valueOf("3a4150")){{
            cost = 1.4f;
            charge = 0.8f;
        }};
        ferriteComposite = new Item("ferrite-composite", Color.valueOf("a8694a")){{
            cost = 1.6f;
            healthScaling = 0.1f;
        }};
        synapticAlloy = new Item("synaptic-alloy", Color.valueOf("a98bff")){{
            cost = 2.1f;
            charge = 1.2f;
        }};
        pyroglass = new Item("pyroglass", Color.valueOf("ff7a45")){{
            cost = 1.5f;
            flammability = 0.6f;
            explosiveness = 0.1f;
        }};
    }
}
