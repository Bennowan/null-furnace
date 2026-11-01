package skillable.material;

/**
 * Eigenschaften EINER Zutat (Item oder Fluessigkeit) als reine Zahlen - ohne Engine-Abhaengigkeit.
 * Alle Werte 0..1, ausser {@code temp} (-1 kalt .. +1 heiss). {@code key} ist die stabile Identitaet
 * ("item:copper", "liquid:water", "mat:&lt;hash&gt;"); {@code label} ist nur Anzeige.
 */
public final class IngredientDef{
    public final String key, label;
    public final boolean liquid;
    public final float density, hardness, conductivity, temp, thermalMass, flammability, explosiveness,
        radioactivity, flux, viscosity, r, g, b;

    public IngredientDef(String key, String label, boolean liquid,
                         float density, float hardness, float conductivity, float temp, float thermalMass,
                         float flammability, float explosiveness, float radioactivity, float flux, float viscosity,
                         float r, float g, float b){
        this.key = key;
        this.label = label;
        this.liquid = liquid;
        this.density = c01(density);
        this.hardness = c01(hardness);
        this.conductivity = c01(conductivity);
        this.temp = Math.max(-1f, Math.min(1f, safe(temp)));
        this.thermalMass = c01(thermalMass);
        this.flammability = c01(flammability);
        this.explosiveness = c01(explosiveness);
        this.radioactivity = c01(radioactivity);
        this.flux = c01(flux);
        this.viscosity = c01(viscosity);
        this.r = c01(r);
        this.g = c01(g);
        this.b = c01(b);
    }

    static float safe(float v){ return Float.isNaN(v) || Float.isInfinite(v) ? 0f : v; }
    static float c01(float v){ v = safe(v); return v < 0f ? 0f : (v > 1f ? 1f : v); }

    @Override public String toString(){ return key; }
}
