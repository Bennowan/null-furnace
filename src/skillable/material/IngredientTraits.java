package skillable.material;

import java.util.*;

/**
 * Leitet Zutateneigenschaften aus rohen Engine-Werten ab (Item.flammability, Liquid.temperature ...).
 * Fuer bekannte Vanilla-Items gibt es eine kleine Tabelle physikalischer KONSTANTEN (Dichte, Haerte, Leitfaehigkeit,
 * Flux-Affinitaet) - das ist KEINE Rezeptliste. Unbekannte (modded) Items werden vollstaendig aus den Engine-Feldern abgeleitet.
 */
public final class IngredientTraits{
    private IngredientTraits(){}

    /** {Dichte, Haerte, Leitfaehigkeit, Flux-Affinitaet} */
    private static final Map<String, float[]> ITEM_CONST = new HashMap<>();
    private static final Map<String, float[]> LIQUID_CONST = new HashMap<>();
    static{
        put(ITEM_CONST, "copper", .60f, .30f, .95f, .05f);
        put(ITEM_CONST, "lead", .80f, .15f, .45f, .00f);
        put(ITEM_CONST, "metaglass", .40f, .60f, .00f, .00f);
        put(ITEM_CONST, "graphite", .35f, .40f, .60f, .10f);
        put(ITEM_CONST, "sand", .35f, .25f, .00f, .00f);
        put(ITEM_CONST, "coal", .30f, .15f, .20f, .00f);
        put(ITEM_CONST, "titanium", .45f, .80f, .25f, .05f);
        put(ITEM_CONST, "thorium", 1.0f, .85f, .10f, .55f);
        put(ITEM_CONST, "scrap", .50f, .30f, .35f, .00f);
        put(ITEM_CONST, "silicon", .35f, .55f, .60f, .20f);
        put(ITEM_CONST, "plastanium", .30f, .65f, .00f, .10f);
        put(ITEM_CONST, "phase-fabric", .25f, .35f, .40f, .95f);
        put(ITEM_CONST, "surge-alloy", .55f, .75f, .95f, .80f);
        put(ITEM_CONST, "spore-pod", .10f, .05f, .00f, .00f);
        put(ITEM_CONST, "blast-compound", .25f, .05f, .00f, .10f);
        put(ITEM_CONST, "pyratite", .25f, .05f, .00f, .05f);
        put(ITEM_CONST, "beryllium", .35f, .60f, .35f, .10f);
        put(ITEM_CONST, "tungsten", .95f, .95f, .40f, .15f);
        put(ITEM_CONST, "oxide", .40f, .55f, .00f, .20f);
        put(ITEM_CONST, "carbide", .55f, .95f, .10f, .10f);
        put(ITEM_CONST, "fissile-matter", .90f, .30f, .20f, .90f);
        put(ITEM_CONST, "dormant-cyst", .20f, .10f, .05f, .20f);

        //Fluessigkeiten: {Dichte, -, Leitfaehigkeit, Flux}
        put(LIQUID_CONST, "water", .30f, 0f, .15f, .00f);
        put(LIQUID_CONST, "slag", .75f, 0f, .35f, .05f);
        put(LIQUID_CONST, "oil", .28f, 0f, .00f, .00f);
        put(LIQUID_CONST, "cryofluid", .33f, 0f, .10f, .05f);
        put(LIQUID_CONST, "neoplasm", .50f, 0f, .20f, .30f);
        put(LIQUID_CONST, "arkycite", .45f, 0f, .10f, .25f);
        put(LIQUID_CONST, "ozone", .08f, 0f, .00f, .10f);
        put(LIQUID_CONST, "hydrogen", .05f, 0f, .00f, .00f);
        put(LIQUID_CONST, "nitrogen", .08f, 0f, .00f, .00f);
        put(LIQUID_CONST, "cyanogen", .12f, 0f, .05f, .10f);
    }

    private static void put(Map<String, float[]> m, String n, float a, float b, float c, float d){
        m.put(n, new float[]{a, b, c, d});
    }

    /** Feste Zutat aus rohen Engine-Werten. */
    public static IngredientDef item(String name, String label, float hardness, float cost, float flammability,
                                     float explosiveness, float radioactivity, float charge, float healthScaling, int rgb){
        float[] k = ITEM_CONST.get(name);
        float density, hard, cond, flux;
        if(k != null){
            density = k[0]; hard = k[1]; cond = k[2]; flux = k[3];
        }else{
            //Unbekanntes Item: aus Engine-Feldern ableiten
            density = 0.25f + 0.22f * cost + 0.05f * hardness;
            hard = 0.10f + 0.14f * hardness + 0.12f * cost + 1.2f * healthScaling;
            cond = 0.05f + 0.8f * charge;
            flux = 0.5f * charge + 0.45f * radioactivity;
        }
        float temp = 0.4f * flammability + 0.4f * radioactivity;
        float thermalMass = 0.2f + 0.6f * IngredientDef.c01(density);
        return new IngredientDef("item:" + name, label, false, density, hard, cond, temp, thermalMass,
            flammability, explosiveness, radioactivity, flux, 1f, ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
    }

    /** Fluessige Zutat. {@code temperature} wie Liquid.temperature (0..1, 0.5 = neutral). */
    public static IngredientDef liquid(String name, String label, float temperature, float viscosity, float flammability,
                                       float explosiveness, float heatCapacity, int rgb){
        float[] k = LIQUID_CONST.get(name);
        float density = k != null ? k[0] : 0.18f + 0.3f * viscosity;
        float cond = k != null ? k[2] : 0.05f;
        float flux = k != null ? k[3] : 0f;
        float temp = (temperature - 0.5f) * 2f;
        float thermalMass = 0.15f + 0.8f * heatCapacity;
        return new IngredientDef("liquid:" + name, label, true, density, 0f, cond, temp, thermalMass,
            flammability, explosiveness, 0f, flux, viscosity, ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
    }

    /** Ein bereits synthetisiertes Material als Zutat (mehrstufige Ketten). Schluessel nutzt den Hash, nicht den Slot. */
    public static IngredientDef material(MaterialProfile p){
        return new IngredientDef("mat:" + Long.toHexString(p.hash), "Material " + p.code(), false,
            p.density, p.hardness, p.conductivity, p.temp, p.thermalMass, p.flammability, p.explosiveness,
            p.radioactivity, p.flux, 1f, p.r, p.g, p.b);
    }
}
