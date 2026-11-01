package skillable.material;

/**
 * Verdichtet das Materialprofil und die Schusswerte zu den Groessen, aus denen Flugbild und Einschlagreaktion entstehen.
 * Alle Werte stammen aus {@link MaterialProfile}/{@link ShotSpec} - es werden keine Eigenschaften erfunden.
 */
public final class ReactionSignature{
    /** 0..1 */
    public final float energy, thermal, electric, instability, brittle, fluid, field, density, cold, complexity;
    public final int ingredients;

    public ReactionSignature(MaterialProfile p, ShotSpec s){
        energy = c(p.power);
        thermal = c(p.classScore[MaterialProfile.CLASS_THERMAL]);
        electric = c(p.classScore[MaterialProfile.CLASS_ELECTRIC]);
        instability = c(p.peakHeat);
        //sproede: hart, fest (nicht fluessig), schlecht leitend (Metalle verformen sich statt zu splittern)
        brittle = c(p.hardness * (0.4f + 0.6f * p.viscosity) * (1f - 0.35f * p.conductivity));
        fluid = c((1f - p.viscosity) * 1.6f);
        field = c(p.classScore[MaterialProfile.CLASS_FLUX]);
        density = c(p.density);
        cold = c(p.cold * 2f);
        ingredients = p.ingredients;
        complexity = c(0.25f * (p.ingredients - 1) / 10f + 0.35f * p.diversity + 0.40f * p.power);
    }

    static float c(float v){ return v < 0f ? 0f : (v > 1f ? 1f : v); }
}
