package skillable.synth;

import arc.struct.*;
import mindustry.ctype.*;
import mindustry.type.*;
import skillable.material.*;

import static mindustry.Vars.*;

/** Uebersetzt Engine-Items/-Fluessigkeiten in {@link IngredientDef}. Modded Inhalte werden aus ihren Engine-Feldern abgeleitet. */
public final class Ingredients{
    private Ingredients(){}

    private static final ObjectMap<UnlockableContent, IngredientDef> cache = new ObjectMap<>();

    /** Zutat zu einem Item; null, wenn das Item keine gueltige Zutat ist (unbekanntes/beschaedigtes Material). */
    public static IngredientDef of(Item item){
        if(item == null) return null;
        if(item instanceof SynthItem s){
            MaterialProfile p = MaterialRegistry.profile(s);
            return p == null ? null : IngredientTraits.material(p); //nicht cachen: Slot kann neu belegt werden
        }
        IngredientDef d = cache.get(item);
        if(d == null){
            d = IngredientTraits.item(item.name, item.localizedName, item.hardness, item.cost, item.flammability, item.explosiveness,
                item.radioactivity, item.charge, item.healthScaling, item.color.rgb888());
            cache.put(item, d);
        }
        return d;
    }

    public static IngredientDef of(Liquid liquid){
        if(liquid == null) return null;
        IngredientDef d = cache.get(liquid);
        if(d == null){
            d = IngredientTraits.liquid(liquid.name, liquid.localizedName, liquid.temperature, liquid.viscosity, liquid.flammability,
                liquid.explosiveness, liquid.heatCapacity, liquid.color.rgb888());
            cache.put(liquid, d);
        }
        return d;
    }

    /** Aufloesung eines gespeicherten Schluessels ("item:copper", "liquid:water", "mat:<hash>"); null = unbekannt. */
    public static IngredientDef resolve(String key){
        if(key == null) return null;
        try{
            if(key.startsWith("item:")){
                Item it = content.getByName(ContentType.item, key.substring(5));
                return it == null || it instanceof SynthItem ? null : of(it);
            }
            if(key.startsWith("liquid:")){
                Liquid l = content.getByName(ContentType.liquid, key.substring(7));
                return l == null ? null : of(l);
            }
            if(key.startsWith("mat:")){
                long h = Long.parseUnsignedLong(key.substring(4), 16);
                MaterialProfile p = MaterialRegistry.byHash(h);
                return p == null ? null : IngredientTraits.material(p);
            }
        }catch(RuntimeException e){
            return null;
        }
        return null;
    }

    /** Fluessigkeitsmenge -> Teile (10 Einheiten = 1 Teil). */
    public static final float LIQUID_UNITS_PER_PART = 10f;

    public static void clearCache(){ cache.clear(); }
}
