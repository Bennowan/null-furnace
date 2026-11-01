package skillable.blocks;

import arc.util.*;
import mindustry.game.*;
import mindustry.gen.*;

import static mindustry.Vars.*;

/** Mapweite Zielwahl fuer den Overclock-Schlag. Wird nur auf Anforderung bzw. alle ~1,5 s (Cache) ausgefuehrt. */
public final class OverclockTargeting{
    private OverclockTargeting(){}

    /** Bestes Ziel: staerkste feindliche Einheit (hoechste Gesundheit); sonst naechstes feindliches Gebaeude. */
    public static @Nullable Posc pick(Team team, float x, float y){
        final Unit[] best = {null};
        final float[] bestHealth = {-1f};
        Groups.unit.each(u -> {
            if(u.team == team || u.team == Team.derelict || !u.isValid() || u.dead() || !u.targetable(team)) return;
            if(u.health > bestHealth[0]){
                bestHealth[0] = u.health;
                best[0] = u;
            }
        });
        if(best[0] != null) return best[0];
        float range = Math.max(world.width(), world.height()) * tilesize * 2f;
        return indexer.findEnemyTile(team, x, y, range, b -> b.isValid());
    }

    /** Guenstige Existenzpruefung (fuer die Anzeige "Overclock verfuegbar"). */
    public static boolean exists(Team team){
        final boolean[] found = {false};
        Groups.unit.each(u -> {
            if(!found[0] && u.team != team && u.team != Team.derelict && u.isValid() && !u.dead() && u.targetable(team)) found[0] = true;
        });
        if(found[0]) return true;
        return state.teams.present.contains(t -> t.team != team && t.team != Team.derelict && t.hasCore());
    }
}
