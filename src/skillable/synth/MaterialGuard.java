package skillable.synth;

import arc.util.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.*;

import static mindustry.Vars.*;

/**
 * Verhindert, dass synthetisierte Materialien in einen Kern gelangen. Betroffen sind ausschliesslich {@link SynthItem}-Traeger und
 * Kerne; reguelaere Items und alle anderen Gebaeude (Behaelter, Tresore, Foerderer ...) bleiben unveraendert.
 *
 * Alle Wege in den Kern laufen ueber die Annahmepruefung des Zielgebaeudes ({@code acceptItem}/{@code acceptStack}); angrenzende, mit dem Kern
 * verbundene Behaelter und Tresore reichen diese Pruefung an den Kern weiter. Deshalb genuegt es, die Kernbauart abzusichern:
 * Foerderband, Bruecke, Massentreiber, Entlader, Fuser-Ausgabe, Einheitenfracht und das manuelle Ablegen des Spielers sind damit gleichermassen
 * abgedeckt. Material, das bereits im Kern liegt (aelterer Spielstand), bleibt unveraendert und kann weiterhin entnommen werden.
 */
public final class MaterialGuard{
    private MaterialGuard(){}

    public static int guarded, skipped;

    /** Kern, der {@link SynthItem}s grundsaetzlich ablehnt. */
    public static class GuardedCoreBuild extends CoreBlock.CoreBuild{
        public GuardedCoreBuild(CoreBlock core){
            core.super();
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return !(item instanceof SynthItem) && super.acceptItem(source, item);
        }

        @Override
        public int getMaximumAccepted(Item item){
            return item instanceof SynthItem ? 0 : super.getMaximumAccepted(item);
        }

        @Override
        public int acceptStack(Item item, int amount, Teamc source){
            return item instanceof SynthItem ? 0 : super.acceptStack(item, amount, source);
        }

        @Override
        public void handleItem(Building source, Item item){
            if(item instanceof SynthItem) return; //Vertragsbruch eines Aufrufers (ohne acceptItem): nicht eintragen
            super.handleItem(source, item);
        }

        @Override
        public void handleStack(Item item, int amount, Teamc source){
            if(item instanceof SynthItem) return;
            super.handleStack(item, amount, source);
        }
    }

    /** Ersetzt die Bauart aller Standard-Kerne. Eigene Kern-Unterklassen anderer Mods bleiben unberuehrt (werden gemeldet). */
    public static void install(){
        guarded = 0; skipped = 0;
        for(Block b : content.blocks()){
            if(!(b instanceof CoreBlock core)) continue;
            Building probe = core.buildType.get();
            if(probe.getClass() != CoreBlock.CoreBuild.class){
                skipped++;
                Log.warn("[FluxProtocol] Kern '@' hat eine eigene Bauart (@) und wird nicht abgesichert.", core.name, probe.getClass().getName());
                continue;
            }
            core.buildType = () -> new GuardedCoreBuild(core);
            guarded++;
        }
        Log.info("[FluxProtocol] Materialsperre fuer @ Kernbauarten installiert (@ uebersprungen).", guarded, skipped);
    }
}
