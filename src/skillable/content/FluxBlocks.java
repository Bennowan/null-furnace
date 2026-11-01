package skillable.content;

import mindustry.content.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import skillable.blocks.*;
import skillable.flux.*;

import static mindustry.type.ItemStack.*;

/**
 * Registriert alle Bloecke des Mods - je einmal fuer Serpulo und einmal fuer Erekir (Suffix "-erekir"),
 * weil beide Planeten unterschiedliche Rohstoffe haben. Pro Planet ist nur die passende Variante im Baumenue sichtbar.
 * Kosten sind PLATZHALTER (docs/BALANCING.md).
 */
public final class FluxBlocks{
    private FluxBlocks(){}

    public static FluxNode fluxNode, fluxNodeErekir;
    public static FluxConverter stabilizer, accelerator, quantum, stabilizerErekir, acceleratorErekir, quantumErekir;
    public static SkillableTurret skillableTurret, skillableTurretErekir;
    public static FluxFuser fuser, fuserErekir;
    public static FuserCooler cooler, coolerErekir;

    private static final BuildVisibility serpuloOnly = new BuildVisibility(() -> !FluxPlanet.erekir());
    private static final BuildVisibility erekirOnly = new BuildVisibility(() -> FluxPlanet.erekir());

    private static void common(Block b, boolean erekir){
        b.alwaysUnlocked = true;
        b.buildVisibility = erekir ? erekirOnly : serpuloOnly;
        b.envEnabled = Env.any;
    }

    public static void load(){
        //---------------- Serpulo
        fluxNode = new FluxNode("flux-node"){{
            requirements(Category.power, with(Items.copper, 5, Items.lead, 8, Items.silicon, 4));
            size = 1; health = 70; laserRange = 12f; maxLinks = 8;
        }};
        stabilizer = new FluxConverter("flux-stabilizer", ConverterProfile.STABILIZER){{
            requirements(Category.power, with(Items.copper, 60, Items.lead, 50, Items.silicon, 40));
            size = 2; health = 380;
        }};
        accelerator = new FluxConverter("flux-accelerator", ConverterProfile.ACCELERATOR){{
            requirements(Category.power, with(Items.lead, 90, Items.silicon, 90, Items.titanium, 70));
            size = 2; health = 520;
        }};
        quantum = new FluxConverter("flux-quantum", ConverterProfile.QUANTUM){{
            requirements(Category.power, with(Items.silicon, 160, Items.titanium, 120, Items.plastanium, 60, Items.surgeAlloy, 40));
            size = 3; health = 1100;
        }};
        skillableTurret = new SkillableTurret("skillable-turret"){{
            requirements(Category.turret, with(Items.copper, 200, Items.graphite, 150, Items.silicon, 150, Items.titanium, 100));
            size = 3; health = 2400;
        }};

        //---------------- Erekir (nur dort vorhandene Rohstoffe)
        fluxNodeErekir = new FluxNode("flux-node-erekir"){{
            requirements(Category.power, with(Items.beryllium, 5, Items.graphite, 4, Items.silicon, 3));
            size = 1; health = 70; laserRange = 12f; maxLinks = 8;
        }};
        stabilizerErekir = new FluxConverter("flux-stabilizer-erekir", ConverterProfile.STABILIZER){{
            requirements(Category.power, with(Items.beryllium, 60, Items.graphite, 40, Items.silicon, 40));
            size = 2; health = 380;
        }};
        acceleratorErekir = new FluxConverter("flux-accelerator-erekir", ConverterProfile.ACCELERATOR){{
            requirements(Category.power, with(Items.silicon, 90, Items.tungsten, 60, Items.graphite, 60));
            size = 2; health = 520;
        }};
        quantumErekir = new FluxConverter("flux-quantum-erekir", ConverterProfile.QUANTUM){{
            requirements(Category.power, with(Items.silicon, 160, Items.tungsten, 120, Items.carbide, 40, Items.surgeAlloy, 40));
            size = 3; health = 1100;
        }};
        skillableTurretErekir = new SkillableTurret("skillable-turret-erekir"){{
            requirements(Category.turret, with(Items.beryllium, 200, Items.graphite, 150, Items.silicon, 150, Items.tungsten, 100));
            size = 3; health = 2400;
        }};

        fuser = new FluxFuser("flux-fuser", false){{
            requirements(Category.crafting, with(Items.lead, 120, Items.silicon, 150, Items.graphite, 100, Items.titanium, 80));
            size = 3; health = 1400;
        }};
        fuserErekir = new FluxFuser("flux-fuser-erekir", true){{
            requirements(Category.crafting, with(Items.beryllium, 150, Items.silicon, 150, Items.graphite, 100, Items.tungsten, 80));
            size = 3; health = 1400;
        }};

        cooler = new FuserCooler("flux-cooler", false){{
            requirements(Category.crafting, with(Items.metaglass, 40, Items.graphite, 40, Items.titanium, 30, Items.silicon, 20));
            size = 2; health = 400;
        }};
        coolerErekir = new FuserCooler("flux-cooler-erekir", true){{
            requirements(Category.crafting, with(Items.beryllium, 60, Items.graphite, 50, Items.silicon, 30, Items.tungsten, 20));
            size = 2; health = 400;
        }};
        for(Block b : new Block[]{fluxNode, stabilizer, accelerator, quantum, skillableTurret, fuser, cooler}) common(b, false);
        for(Block b : new Block[]{fluxNodeErekir, stabilizerErekir, acceleratorErekir, quantumErekir, skillableTurretErekir, fuserErekir, coolerErekir}) common(b, true);
    }
}
