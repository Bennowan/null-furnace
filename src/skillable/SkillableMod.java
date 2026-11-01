package skillable;

import arc.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.mod.*;
import skillable.content.*;
import skillable.flux.*;
import skillable.fx.*;
import skillable.modes.*;
import skillable.production.*;

/** Einstiegspunkt: Skillable Turret - Flux Protocol. */
public class SkillableMod extends Mod{

    public SkillableMod(){
        Log.info("[Flux Protocol] Mod-Konstruktor geladen.");
        //Materialregister: nach jedem Laden/Netzwerk-Sync mit den Weltregeln abgleichen (Namen, Icons, Geschosse)
        arc.Events.on(mindustry.game.EventType.WorldLoadEvent.class, e -> skillable.synth.MaterialRegistry.reset());
        arc.Events.run(mindustry.game.EventType.Trigger.update, skillable.synth.MaterialRegistry::sync);
    }

    @Override
    public void init(){
        //Nach dem Laden aller Inhalte: synthetische Materialien duerfen nicht in Kerne gelangen
        skillable.synth.MaterialGuard.install();
    }

    @Override
    public void loadContent(){
        FluxItems.load();
        skillable.synth.ProjectileFactory.load();
        FluxRecipes.load();
        FluxModes.load();
        FluxBlocks.load();

        FluxNetworkManager mgr = FluxNetworkManager.get();
        mgr.onCollapseFx = SkillableMod::collapseEffects;
        mgr.init();
        Log.info("[Flux Protocol] Inhalte geladen: @ Modi, @ Rezepte.", FluxModes.all.size, FluxRecipes.all.size);
    }

    /** Kollaps-Rueckmeldung: Ring, EMP-Welle, zurueckhaltender Screenshake und Alarmton an jedem Konverter. */
    static void collapseEffects(FluxNetwork net){
        if(Vars_headless()) return;
        for(FluxSource s : net.sources){
            if(s instanceof Building b){
                FluxEffects.collapse.at(b.x, b.y);
                FluxEffects.empRing.at(b.x, b.y);
                Effect.shake(2.2f, 18f, b.x, b.y);
                Sounds.shootSmite.at(b.x, b.y, 0.6f, 0.8f);
            }
        }
    }

    private static boolean Vars_headless(){
        return mindustry.Vars.headless;
    }
}
