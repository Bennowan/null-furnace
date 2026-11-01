package skillable.synth;

import arc.graphics.*;
import skillable.material.*;
import skillable.modes.*;

/** Dynamische {@link FluxMode}s der Material-Slots: gleiche Schnittstelle wie die festen Modi, daher laeuft die Flux-/Instabilitaetslogik des Turms unveraendert. */
public final class DynamicModes{
    private DynamicModes(){}

    private static final FluxMode[] modes = new FluxMode[MaterialRegistry.SLOTS];

    public static FluxMode get(int slot){
        MaterialRegistry.sync();
        return modes[slot];
    }

    static void rebuild(int slot, MaterialProfile p, SynthItem item){
        if(p == null){ modes[slot] = null; return; }
        FluxMode m = new FluxMode("fluxmat-" + slot, item, p.fluxPerTick, p.risk, p.shotsPerItem, ProjectileFactory.bullet(slot));
        m.color = new Color(Math.min(1f, p.r * 0.7f + 0.3f), Math.min(1f, p.g * 0.7f + 0.3f), Math.min(1f, p.b * 0.7f + 0.3f), 1f);
        m.shotKick = p.shotKick;
        modes[slot] = m;
    }
}
