package skillable.modes;

import arc.graphics.*;
import mindustry.entities.bullet.*;
import mindustry.type.*;

/**
 * Profil eines Material-Modus. Alle Zahlen sind PLATZHALTER ohne Spieltests (docs/BALANCING.md).
 * Flux-Einheit: Flux pro Tick (Anzeige im Spiel: pro Sekunde = *60).
 */
public final class FluxMode{
    public final String id;
    /** Material auf Serpulo (und ueberall, wo es vorhanden ist). */
    public final Item item;
    /** Zusaetzliches Material, das NUR auf Erekir akzeptiert wird (null = keins). */
    public Item erekirItem;
    /** Durchsatz, den der Modus bei aktiver Zielerfassung anfordert (Flux/Tick). */
    public final float fluxPerTick;
    /** Beitrag zum Instabilitaets-Zielwert, solange der Modus tatsaechlich Flux erhaelt (0..~0,4). */
    public final float risk;
    /** Wie viele Schuesse eine Materialeinheit liefert. */
    public final int shotsPerItem;
    public final BulletType bullet;
    /** Abgeschwaechte Variante fuer den Normalstrom-Betrieb (null, wenn der Modus nur mit Flux funktioniert). */
    public BulletType powerBullet;
    /** Darf der Modus ohne Flux mit normalem Strom (stark abgeschwaecht) feuern? */
    public boolean powerCapable;
    /** Instabilitaets-Schub pro Schuss (nur dynamische Materialien mit instabiler Zusammensetzung). */
    public float shotKick;
    /** Anzeigefarbe (Balken, Glow). */
    public Color color = Color.valueOf("4aa8ff");

    public FluxMode(String id, Item item, float fluxPerTick, float risk, int shotsPerItem, BulletType bullet){
        this.id = id;
        this.item = item;
        this.fluxPerTick = fluxPerTick;
        this.risk = risk;
        this.shotsPerItem = Math.max(1, shotsPerItem);
        this.bullet = bullet;
    }

    public FluxMode color(String hex){
        this.color = Color.valueOf(hex);
        return this;
    }

    public FluxMode erekir(Item alt){
        this.erekirItem = alt;
        return this;
    }

    /** Modus kann auch mit Normalstrom feuern; erzeugt die abgeschwaechte Geschossvariante. */
    public FluxMode powerCapable(float damageScale, float rangeScale){
        this.powerCapable = true;
        this.powerBullet = FluxBullets.weakened(bullet, damageScale, rangeScale);
        return this;
    }
}
