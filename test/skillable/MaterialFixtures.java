package skillable;

import skillable.material.*;

import java.util.*;

/** Vanilla-aehnliche Zutatenwerte fuer Tests ohne Engine (entsprechen den Engine-Feldern von Mindustry v7). */
final class MaterialFixtures{
    private MaterialFixtures(){}

    static IngredientDef item(String n, float hard, float cost, float fl, float ex, float ra, float ch, float hs, int rgb){
        return IngredientTraits.item(n, n, hard, cost, fl, ex, ra, ch, hs, rgb);
    }
    static IngredientDef liq(String n, float temp, float visc, float fl, float ex, float hc, int rgb){
        return IngredientTraits.liquid(n, n, temp, visc, fl, ex, hc, rgb);
    }

    static final IngredientDef copper = item("copper", 1, .5f, 0, 0, 0, 0, 0, 0xd99d73);
    static final IngredientDef lead = item("lead", 1, .7f, 0, 0, 0, 0, 0, 0x8c7fa9);
    static final IngredientDef metaglass = item("metaglass", 0, 1.5f, 0, 0, 0, 0, 0, 0xebeef5);
    static final IngredientDef graphite = item("graphite", 0, 1f, 0, 0, 0, 0, 0, 0xb2c6d2);
    static final IngredientDef sand = item("sand", 0, 1f, 0, 0, 0, 0, 0, 0xf7cba4);
    static final IngredientDef coal = item("coal", 2, 1f, 1f, .2f, 0, 0, 0, 0x272727);
    static final IngredientDef titanium = item("titanium", 3, 1f, 0, 0, 0, 0, 0, 0x8da1e3);
    static final IngredientDef thorium = item("thorium", 4, 1.1f, 0, .2f, 1f, 0, .1f, 0xf9a3c7);
    static final IngredientDef scrap = item("scrap", 0, .5f, 0, 0, 0, 0, 0, 0x777777);
    static final IngredientDef silicon = item("silicon", 0, .8f, 0, 0, 0, 0, .8f, 0x53565c);
    static final IngredientDef plastanium = item("plastanium", 0, 1.3f, .1f, .2f, 0, 0, .1f, 0xcbd97f);
    static final IngredientDef phase = item("phase-fabric", 0, 1.3f, 0, 0, .6f, 0, .5f, 0xf4ba6e);
    static final IngredientDef surge = item("surge-alloy", 0, 1.2f, 0, 0, 0, .75f, .75f, 0xf3e979);
    static final IngredientDef spore = item("spore-pod", 0, 1f, 1.15f, 0, 0, 0, 0, 0x7457ce);
    static final IngredientDef blast = item("blast-compound", 0, 1f, .4f, 1.2f, 0, 0, 0, 0xff795e);
    static final IngredientDef pyratite = item("pyratite", 0, 1f, 1.4f, .4f, 0, 0, 0, 0xffaa5f);
    static final IngredientDef beryllium = item("beryllium", 3, 1f, 0, 0, 0, 0, 0, 0x3a8f64);
    static final IngredientDef tungsten = item("tungsten", 5, 1.5f, 0, 0, 0, 0, 0, 0x768a9a);

    static final IngredientDef water = liq("water", .5f, .5f, 0, 0, .4f, 0x596ab8);
    static final IngredientDef slag = liq("slag", 1f, .7f, 0, 0, .5f, 0xffa166);
    static final IngredientDef oil = liq("oil", .5f, .75f, 1.2f, 1.2f, .7f, 0x313131);
    static final IngredientDef cryo = liq("cryofluid", .25f, .5f, 0, 0, .9f, 0x6ecdec);

    static final IngredientDef[] ITEMS = {copper, lead, metaglass, graphite, sand, coal, titanium, thorium, scrap, silicon,
        plastanium, phase, surge, spore, blast, pyratite, beryllium, tungsten};
    static final IngredientDef[] LIQUIDS = {water, slag, oil, cryo};

    static final IngredientDef carbide = item("carbide", 0, 1.3f, 0, 0, 0, 0, .3f, 0x89769a);

    static Composition mix(Object... args){
        List<IngredientDef> d = new ArrayList<>();
        float[] p = new float[args.length / 2];
        for(int i = 0; i < args.length; i += 2){
            d.add((IngredientDef)args[i]);
            p[i / 2] = ((Number)args[i + 1]).floatValue();
        }
        return Composition.of(d, p);
    }

    static MaterialProfile profile(Object... args){ return MaterialProfile.of(mix(args)); }
}
