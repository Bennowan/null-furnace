package skillable.synth;

import arc.graphics.*;
import mindustry.type.*;

/**
 * Traeger-Item fuer ein synthetisiertes Material. Es gibt genau {@link MaterialRegistry#SLOTS} davon, alle beim Laden registriert.
 * Welches Material ein Slot gerade darstellt, steht im {@link MaterialRegistry} (im Spielstand gespeichert).
 * Name, Farbe, Icon und Stoffwerte werden bei Belegung gesetzt ({@link MaterialVisuals}).
 */
public class SynthItem extends Item{
    public final int slot;

    public SynthItem(int slot){
        super(String.format("fluxmat-%02d", slot), Color.valueOf("8a93a8"));
        this.slot = slot;
        buildable = false;
        alwaysUnlocked = true;
        cost = 1f;
        fullOverride = "flux-protocol-material-base"; //neutrales Platzhalter-Sprite, bis das Icon generiert ist
    }

    /**
     * Unbelegte/beschaedigte Traeger bleiben aus Auswahllisten (Sortierer, Entlader, Itemquelle, Datenbank) ausgeblendet;
     * belegte Materialien sind dort mit ihrem generierten Icon waehlbar - Voraussetzung fuer sinnvolle Logistik.
     */
    @Override
    public boolean isHidden(){
        return MaterialRegistry.isUnknown(this);
    }
}
