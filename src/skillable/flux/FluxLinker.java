package skillable.flux;

import arc.struct.*;

/** Mitglied, das Verbindungen zu anderen Mitgliedern haelt (Flux Node). Links = Tile-Positionen. */
public interface FluxLinker extends FluxMember{
    IntSeq links();
}
