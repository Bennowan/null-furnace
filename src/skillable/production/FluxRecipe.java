package skillable.production;

import mindustry.type.*;

/**
 * Eine Fuser-Rezeptur. Modular: neue Rezepte werden nur in FluxRecipes eingetragen; spaetere mehrstufige Ketten
 * entstehen, indem ein Ergebnis-Item eines Rezepts Zutat eines anderen ist.
 */
public final class FluxRecipe{
    public final String id;
    public final Item output;
    public final int outputAmount;
    /** Zutaten auf Serpulo. */
    public final ItemStack[] inputs;
    /** Zutaten auf Erekir (dort fehlen Kupfer, Kohle, Schrott). Gleiches Ergebnis. */
    public final ItemStack[] erekirInputs;
    /** Fluessige Zutat, Verbrauch pro Tick waehrend der Produktion. */
    public final Liquid liquid;
    public final float liquidPerTick;
    /** Optionales Kuehlmittel: schneller und weniger riskant, solange vorhanden. null = keins. */
    public final Liquid coolant;
    public static final float COOLANT_PER_TICK = 0.05f;
    /** Ticks pro Durchlauf bei voller Flux-Versorgung. */
    public final float craftTime;
    /** Flux/Tick waehrend der Produktion. */
    public final float fluxPerTick;
    /** Beitrag zum Instabilitaets-Zielwert, solange produziert wird. */
    public final float risk;
    /** Mit Kuehlmittel: Geschwindigkeit x, Risiko x. */
    public final float coolSpeed, coolRisk;

    public FluxRecipe(String id, Item output, int outputAmount, ItemStack[] inputs, ItemStack[] erekirInputs,
                      Liquid liquid, float liquidPerTick, Liquid coolant, float craftTime, float fluxPerTick,
                      float risk, float coolSpeed, float coolRisk){
        this.id = id;
        this.output = output;
        this.outputAmount = outputAmount;
        this.inputs = inputs;
        this.erekirInputs = erekirInputs;
        this.liquid = liquid;
        this.liquidPerTick = liquidPerTick;
        this.coolant = coolant;
        this.craftTime = craftTime;
        this.fluxPerTick = fluxPerTick;
        this.risk = risk;
        this.coolSpeed = coolSpeed;
        this.coolRisk = coolRisk;
    }

    public ItemStack[] inputs(boolean erekir){
        return erekir && erekirInputs != null ? erekirInputs : inputs;
    }

    /** Maximale Lagermenge je Zutat im Fuser. */
    public int capacity(ItemStack s){
        return Math.max(8, s.amount * 4);
    }
}
