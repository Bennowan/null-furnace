package skillable.content;

import mindustry.content.*;

import static mindustry.Vars.*;

/** Erkennt, auf welchem Planeten gespielt wird (Serpulo / Erekir). */
public final class FluxPlanet{
    private FluxPlanet(){}

    public static boolean erekir(){
        if(state == null) return false;
        if(state.rules.planet == Planets.erekir) return true;
        return state.isCampaign() && state.getPlanet() == Planets.erekir;
    }
}
