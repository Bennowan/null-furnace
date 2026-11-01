package skillable.synth;

import mindustry.type.*;

/** Gebaeude, die synthetische Materialien AUSSERHALB ihres ItemModule halten (Fuser-Ausgang, Turm-Slot ...). Verhindert Slot-Neuvergabe. */
public interface MaterialHolder{
    boolean holdsMaterial(Item item);
}
