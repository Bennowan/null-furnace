package fluxselftest;

import arc.*;
import arc.files.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.world.*;
import skillable.blocks.*;
import skillable.content.*;
import skillable.flux.*;
import skillable.material.*;
import skillable.modes.*;
import skillable.synth.*;

import java.util.*;
import java.util.function.*;

import static mindustry.Vars.*;

/**
 * Headless-Integrationstest der freien Materialsynthese (Phase 1 + 2) im ECHTEN Mindustry-Server v160.7.
 * Prueft Register, Persistenz, Fuser (1/2/11 Zutaten, Fluessigkeiten), Transport ueber Foerderband, Turm mit allen Materialklassen,
 * Gefahrenreaktionen, den Fuser-Fluessigkeitsfehler, Leistung und echtes Speichern/Laden (SaveIO).
 */
public class MaterialSelfTest extends Mod{
    int pass, fail, totalTicks;
    boolean started;
    final ArrayDeque<Task> queue = new ArrayDeque<>();

    interface Task{ boolean run(); }

    void check(boolean c, String name){
        if(c) pass++; else fail++;
        Log.info("[MATTEST] @ @", c ? "OK  " : "FAIL", name);
    }

    void info(String s){ Log.info("[MATTEST]      @", s); }

    void add(Task t){ queue.add(t); }
    void now(Runnable r){ add(() -> { try{ r.run(); }catch(Throwable t){ Log.err("[MATTEST] Ausnahme", t); check(false, "Ausnahme: " + t); } return true; }); }
    void waitUntil(String name, BooleanSupplier c, int max){
        int[] t = {0};
        add(() -> {
            boolean ok;
            try{ ok = c.getAsBoolean(); }catch(Throwable e){ Log.err("[MATTEST] Ausnahme", e); check(false, name + " (Ausnahme " + e + ")"); return true; }
            if(ok){ check(true, name); return true; }
            if(++t[0] > max){ check(false, name + " (Zeitlimit " + max + " Ticks)"); return true; }
            return false;
        });
    }
    void delay(int n){ int[] t = {0}; add(() -> ++t[0] >= n); }

    public MaterialSelfTest(){
        Events.on(ServerLoadEvent.class, e -> start());
    }

    // ------------------------------------------------------------------ Hilfen
    IngredientDef di(Item i){ return Ingredients.of(i); }
    IngredientDef dl(Liquid l){ return Ingredients.of(l); }

    Composition comp(Object... pairs){
        List<IngredientDef> d = new ArrayList<>();
        float[] p = new float[pairs.length / 2];
        for(int i = 0; i < pairs.length; i += 2){
            d.add(pairs[i] instanceof Item it ? di(it) : dl((Liquid)pairs[i]));
            p[i / 2] = ((Number)pairs[i + 1]).floatValue();
        }
        return Composition.of(d, p);
    }

    int feed(FluxFuser.FuserBuild f, Item it, int n){
        int ok = 0;
        for(int i = 0; i < n; i++){ if(f.acceptItem(null, it)){ f.handleItem(null, it); ok++; } }
        return ok;
    }

    float feedLiquid(FluxFuser.FuserBuild f, Liquid l, float units){
        float given = 0f;
        for(int i = 0; i < 400 && given < units; i++){
            if(!f.acceptLiquid(null, l)) break;
            float a = Math.min(2f, units - given);
            f.handleLiquid(null, l, a);
            given += a;
        }
        return given;
    }

    void setLimit(FluxFuser.FuserBuild f, Item it, int lim){
        int s = f.slotOfItem(it);
        f.command(FluxFuser.CMD_LIMIT, s, lim);
    }

    void setLimit(FluxFuser.FuserBuild f, Liquid l, int lim){
        int s = f.slotOfLiquid(l);
        f.command(FluxFuser.CMD_LIMIT, s, lim);
    }

    void releaseAll(FluxFuser.FuserBuild f){
        for(int i = 0; i < FluxFuser.SLOTS; i++) if(f.kind[i] != 0){
            f.items.clear(); f.liquids.clear();
            f.command(FluxFuser.CMD_RELEASE, i, 0);
        }
    }

    void wipeFuser(FluxFuser.FuserBuild f){
        f.items.clear(); f.liquids.clear();
        for(int i = 0; i < FluxFuser.SLOTS; i++) f.command(FluxFuser.CMD_RELEASE, i, 0);
        f.active = false; f.batchSlot = -1; f.batchSig = null; f.progress = 0f; f.heat = 0f; f.stress = 0f; f.settle = 0f; f.outAmount = 0; f.outSlot = -1; f.forceStart = false;
    }

    void resetRegistry(){
        state.rules.tags.remove(MaterialRegistry.TAG);
        MaterialRegistry.reset();
    }

    // ------------------------------------------------------------------ Welt
    Tile tmpFuserT, coreT, nodeT, accT, powT, fuserT, fuserBT, turretT, node2T;
    FluxNode.NodeBuild node, node2;
    FluxConverter.ConverterBuild acc;
    FluxFuser.FuserBuild fa, fb;
    SkillableTurret.SkillableBuild turret;
    Unit enemy;
    SynthItem sCopper, sTwo, sEleven, sWater;
    long snapHash;

    void start(){
        Log.info("[MATTEST] Start");
        try{
            world.loadGenerator(90, 90, tiles -> tiles.each((x, y) -> tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air))));
            state.rules.infiniteResources = true;
            state.rules.waves = false;
            state.rules.waveTeam = Team.crux;
            state.rules.fog = false;
            state.set(mindustry.core.GameState.State.playing);
            logic.play();
            Events.fire(new WorldLoadEvent());
            world.tile(10, 10).setNet(Blocks.coreShard, Team.sharded, 0);
            script();
            started = true;
            Events.run(Trigger.update, this::step);
        }catch(Throwable t){
            Log.err("[MATTEST] Setup-Fehler", t);
            finish(1);
        }
    }

    void step(){
        if(!started) return;
        if(++totalTicks > 40000){ check(false, "Gesamtzeitlimit"); finish(1); return; }
        Task t = queue.peek();
        if(t == null){ finish(fail == 0 ? 0 : 1); return; }
        if(t.run()) queue.poll();
    }

    void finish(int code){
        started = false;
        Log.info("[MATTEST] ERGEBNIS: @ bestanden, @ fehlgeschlagen", pass, fail);
        Core.app.post(() -> System.exit(code));
    }

    // ------------------------------------------------------------------ Ablauf
    final Set<String> sections = new HashSet<>(Arrays.asList(System.getenv().getOrDefault("MATTEST_SECTIONS", "A,B,C,D,E,F,G,I").split(",")));
    boolean want(String s){ return sections.contains(s); }

    void script(){
        //============ A. Inhalte und Zutaten
        now(() -> {
            check(FluxItems.material != null && FluxItems.material.length == MaterialRegistry.SLOTS && MaterialRegistry.SLOTS == 48, "48 Traeger-Items beim Laden registriert");
            boolean all = true;
            for(int i = 0; i < 48; i++){
                Item it = content.getByName(mindustry.ctype.ContentType.item, String.format("flux-protocol-fluxmat-%02d", i));
                if(it != FluxItems.material[i] || !it.isHidden()) all = false;
            }
            check(all, "Traeger-Items sind ueber den Content-Namen auffindbar; unbelegte sind in Auswahllisten ausgeblendet");
            check(ProjectileFactory.main[47] != null && ProjectileFactory.frag[0] != null && ProjectileFactory.zone[5] != null, "Geschosstypen pro Slot beim Laden vorab angelegt");
            check(Ingredients.of(Items.copper).conductivity > 0.8f && Ingredients.of(Items.coal).flammability > 0.9f, "Zutateneigenschaften aus der Engine: Kupfer leitet, Kohle brennt");
            check(Ingredients.of(Liquids.slag).temp > 0.9f && Ingredients.of(Liquids.cryofluid).temp < -0.4f && Ingredients.of(Liquids.oil).flammability > 0.9f, "Fluessigkeitswerte aus der Engine: Schlacke heiss, Kryofluid kalt, Oel brennbar");
            check(Ingredients.resolve("item:copper") == Ingredients.of(Items.copper) && Ingredients.resolve("liquid:water") != null && Ingredients.resolve("item:gibtesnicht") == null && Ingredients.resolve("quatsch") == null, "Zutatenschluessel werden aufgeloest, Unbekanntes gibt null");
            check(Ingredients.of(FluxItems.material[0]) == null, "Unbelegter Traeger ist keine gueltige Zutat");
        });

        //============ B. Register: Identitaet, Persistenz, Beschaedigung, Wiederverwendung
        now(() -> {
            resetRegistry();
            Composition c1 = comp(Items.copper, 3, Items.coal, 1, Liquids.water, 20);
            Composition c2 = comp(Liquids.water, 20, Items.coal, 1, Items.copper, 3);
            SynthItem a = MaterialRegistry.obtain(c1), b = MaterialRegistry.obtain(c2);
            check(a != null && a == b, "Gleiche Zusammensetzung (andere Reihenfolge) -> derselbe Traeger");
            SynthItem c = MaterialRegistry.obtain(comp(Items.copper, 1, Items.coal, 3, Liquids.water, 20));
            check(c != null && c != a, "Anderes Mengenverhaeltnis -> anderer Traeger");
            SynthItem scaled = MaterialRegistry.obtain(comp(Items.copper, 6, Items.coal, 2, Liquids.water, 40));
            check(scaled == a, "Gleiches Verhaeltnis, doppelte Menge -> derselbe Traeger");
            MaterialProfile p = MaterialRegistry.profile(a);
            check(p != null && p.signature.equals(c1.signature) && MaterialRegistry.composition(a) != null, "Profil und Zusammensetzung sind abrufbar");
            check(a.localizedName.contains(p.code()) && !a.localizedName.equals(c.localizedName), "Name enthaelt die Materialkennung und unterscheidet Materialien: " + a.localizedName);
            check(Math.abs(a.flammability - p.flammability) < 1e-4f && Math.abs(a.charge - p.conductivity * 0.8f) < 1e-4f, "Engine-Stoffwerte des Items folgen dem Profil (Brand-/Explosionsverhalten in Behaeltern)");
            check(a.description != null && a.description.contains("%"), "Beschreibung nennt die Zusammensetzung");
            check(!a.isHidden() && FluxItems.material[47].isHidden(), "Belegtes Material ist in Sortierer/Entlader-Auswahl sichtbar, unbelegte bleiben verborgen");
            String tag = state.rules.tags.get(MaterialRegistry.TAG);
            check(tag != null && tag.startsWith("fm1") && tag.split("\n").length == 3, "Register steht in den Weltregeln (Kopfzeile + 2 Materialien)");
            //Speichern/Laden der Regeln simulieren
            float[] before = p.toArray();
            int slotA = a.slot;
            state.rules.tags.remove(MaterialRegistry.TAG);
            MaterialRegistry.reset();
            check(MaterialRegistry.profile(a) == null && MaterialRegistry.used() == 0, "Ohne Registereintrag ist der Traeger 'unbelegt'");
            check(a.localizedName.equals(Core.bundle.get("material.unknown")), "Unbelegter Traeger zeigt neutralen Namen");
            state.rules.tags.put(MaterialRegistry.TAG, tag);
            MaterialRegistry.reset();
            MaterialProfile q = MaterialRegistry.profile(FluxItems.material[slotA]);
            check(q != null && Arrays.equals(before, q.toArray()) && q.hash == p.hash, "Registerinhalt aus den Regeln wiederhergestellt: Slot, Profil bitgleich");
            check(MaterialRegistry.obtain(c1) == FluxItems.material[slotA], "Nach dem Wiederherstellen liefert dieselbe Zusammensetzung denselben Slot");
        });
        now(() -> {
            //Beschaedigte Daten
            String good = state.rules.tags.get(MaterialRegistry.TAG);
            String[] lines = good.split("\n");
            String valid = lines[1];
            String[] f = valid.split("\\|");
            int slotValid = Integer.parseInt(f[0]);
            int slotBad = (slotValid + 7) % 48, slotBad2 = (slotValid + 9) % 48, slotNew = (slotValid + 11) % 48;
            String badCrc = slotBad + "|" + f[1] + "|" + f[2] + "|deadbeef";
            String badRange = slotBad2 + "|" + f[1] + "|" + f[2].replaceFirst("^-?\\d+", "999999");
            badRange = badRange + "|" + Long.toHexString(Composition.hash64(badRange) & 0xffffffffL);
            //gueltig, aber Zutat unbekannt (z. B. Mod entfernt) -> gespeicherter Stand bleibt nutzbar
            String unkSig = "c1;item:entferntes-mod-item=8;item:copper=8";
            String unkHead = slotNew + "|" + unkSig + "|" + f[2];
            String unk = unkHead + "|" + Long.toHexString(Composition.hash64(unkHead) & 0xffffffffL);
            String tag = String.join("\n", lines[0], valid, badCrc, badRange, "garbage", "99|x|y|z", valid, unk);
            state.rules.tags.put(MaterialRegistry.TAG, tag);
            try{
                MaterialRegistry.reset();
                check(true, "Beschaedigtes Register wird ohne Ausnahme gelesen");
            }catch(Throwable t){ check(false, "Beschaedigtes Register warf " + t); }
            check(MaterialRegistry.profile(FluxItems.material[slotValid]) != null, "Gueltige Zeile bleibt erhalten");
            check(MaterialRegistry.profile(FluxItems.material[slotBad]) == null && MaterialRegistry.isCorrupt(slotBad), "Falsche Pruefsumme -> Slot als beschaedigt markiert, inert");
            check(MaterialRegistry.profile(FluxItems.material[slotBad2]) == null && MaterialRegistry.isCorrupt(slotBad2), "Wert ausserhalb des Bereichs -> verworfen");
            check(FluxItems.material[slotBad].localizedName.equals(Core.bundle.get("material.corrupt")), "Beschaedigter Slot ist als solcher benannt");
            MaterialProfile kept = MaterialRegistry.profile(FluxItems.material[slotNew]);
            check(kept != null && MaterialRegistry.composition(FluxItems.material[slotNew]) == null, "Unbekannte Zutat: gespeichertes Profil bleibt, Zusammensetzung nicht aufloesbar");
            Item bad = FluxItems.material[slotBad];
            check(!FluxModes.supports(bad) && FluxModes.get(bad) == null, "Turm erkennt beschaedigtes Material nicht als Munition");
            check(Ingredients.of(bad) == null, "Beschaedigtes Material ist keine Zutat");
            state.rules.tags.put(MaterialRegistry.TAG, "xyz-unbekanntes-format\n0|a|b|c");
            try{ MaterialRegistry.reset(); check(MaterialRegistry.used() == 0, "Unbekannte Registerversion wird ignoriert, kein Absturz"); }
            catch(Throwable t){ check(false, "Unbekanntes Format warf " + t); }
            resetRegistry();
        });
        now(() -> {
            //Slot-Wiederverwendung
            resetRegistry();
            Random rnd = new Random(3);
            Item[] pool = {Items.copper, Items.lead, Items.sand, Items.coal, Items.titanium, Items.scrap, Items.graphite, Items.silicon, Items.metaglass, Items.thorium, Items.pyratite, Items.sporePod};
            Set<String> sigs = new HashSet<>();
            while(MaterialRegistry.used() < 48){
                int k = 2 + rnd.nextInt(3);
                Object[] args = new Object[k * 2];
                for(int i = 0; i < k; i++){ args[i * 2] = pool[rnd.nextInt(pool.length)]; args[i * 2 + 1] = 1 + rnd.nextInt(6); }
                Composition c = comp(args);
                if(sigs.add(c.signature)) MaterialRegistry.obtain(c);
            }
            check(MaterialRegistry.used() == 48, "Register laesst sich bis 48 verschiedene Materialien fuellen");
            Composition extra = comp(Items.lead, 1, Items.thorium, 5, Items.sporePod, 2);
            check(!MaterialRegistry.canObtain(extra) && MaterialRegistry.obtain(extra) == null, "Voll und alles frisch genutzt: kein Slot wird weggenommen");
            Time.time += MaterialRegistry.REUSE_DELAY + 10f;
            //47 Slots in einem Behaelter 'in Benutzung' halten
            Building cont = world.tile(20, 20).build;
            world.tile(20, 20).setNet(Blocks.container, Team.sharded, 0);
            cont = world.tile(20, 20).build;
            final Building cf = cont;
            int free = 17;
            for(int i = 0; i < 48; i++) if(i != free) cont.items.add(FluxItems.material[i], 1);
            String oldSig = MaterialRegistry.composition(FluxItems.material[free]) == null ? null : MaterialRegistry.composition(FluxItems.material[free]).signature;
            int prot = 0; for(int i = 0; i < 48; i++) if(MaterialRegistry.inUse(FluxItems.material[i])) prot++;
            info("im Spiel vorhandene Slots laut inUse(): " + prot + " (erwartet 47); Behaelter hat " + cont.items.total() + " Items, Slot0 im Behaelter: " + cont.items.get(FluxItems.material[0]) + "");
            SynthItem got = MaterialRegistry.obtain(extra);
            check(got != null && got.slot == free, "Neue Vergabe nimmt nur den ungenutzten Slot (" + (got == null ? "null" : got.slot) + "), nicht die 47 im Spiel vorhandenen");
            check(oldSig != null && MaterialRegistry.profile(got) != null && !MaterialRegistry.profile(got).signature.equals(oldSig), "Der Slot gehoert nun zur neuen Zusammensetzung");
            cont.items.clear();
            world.tile(20, 20).setNet(Blocks.air, Team.derelict, 0);
            resetRegistry();
        });

        //============ C. Netz, Fuser, Turm aufbauen
        now(() -> {
            powT = world.tile(31, 40);
            powT.setNet(Blocks.powerSource, Team.sharded, 0);
            accT = world.tile(32, 40);
            accT.setNet(FluxBlocks.accelerator, Team.sharded, 0);
            nodeT = world.tile(36, 42);
            nodeT.setNet(FluxBlocks.fluxNode, Team.sharded, 0);
            fuserT = world.tile(40, 40);
            fuserT.setNet(FluxBlocks.fuser, Team.sharded, 0);
            turretT = world.tile(49, 40);
            turretT.setNet(FluxBlocks.skillableTurret, Team.sharded, 0);
            for(int x = 42; x <= 47; x++) world.tile(x, 40).setNet(Blocks.conveyor, Team.sharded, 0); //Fuser -> Turm
            fuserBT = world.tile(40, 52);
            fuserBT.setNet(FluxBlocks.fuser, Team.sharded, 0);
        });
        delay(5);
        now(() -> {
            node = (FluxNode.NodeBuild)nodeT.build; acc = (FluxConverter.ConverterBuild)accT.build;
            fa = (FluxFuser.FuserBuild)fuserT.build; fb = (FluxFuser.FuserBuild)fuserBT.build; turret = (SkillableTurret.SkillableBuild)turretT.build;
            for(Building b : new Building[]{acc, fa, fb, turret}) if(!node.links.contains(b.pos())) node.toggleLink(b.pos());
            check(fa != null && fb != null && turret != null && acc != null && node != null, "Netz, zwei Fuser, Turm und Foerderband platziert");
            check(!fa.classic && !fb.classic, "Neue Fuser starten im freien Modus");
        });
        delay(60);
        waitUntil("Fuser, Turm und Konverter im selben Flux-Netz", () -> fa.network != null && fa.network == acc.network && turret.network == acc.network && fb.network == acc.network, 200);

        //============ D. Technischer Fuser-Fehler (Abschnitt 11): Fluessigkeitsreaktion an der Uebergabestelle
        now(() -> {
            boolean allFilter = true;
            for(Liquid l : content.liquids()) if(!fa.block.consumesLiquid(l)) allFilter = false;
            check(allFilter, "Fuser kennt jede Fluessigkeit als moegliche Zutat (liquidFilter)");
            //Reproduktion: voller Slag-Tank + Wasser als 'aktuelle' Fluessigkeit; Leitung schiebt Schlacke -> Engine-Reaktion
            Tile ct = world.tile(40, 37); //direkt unter dem Fuser (39..41, 39..41) -> y=38
            ct = world.tile(40, 38);
            ct.setNet(Blocks.conduit, Team.sharded, 1); //Rotation 1 = nach oben, in den Fuser
            Building conduit = ct.build;
            wipeFuser(fa);
            fa.liquids.clear();
            fa.liquids.add(Liquids.slag, 40f);   // = Obergrenze bei Limit 4
            fa.handleLiquid(null, Liquids.water, 40f); //Wasser ist nun die 'aktuelle' Fluessigkeit (> 10 % Fuellstand)
            conduit.liquids.clear();
            conduit.liquids.add(Liquids.slag, conduit.block.liquidCapacity);
            float hp = fa.health, condSlag = conduit.liquids.get(Liquids.slag);
            for(int i = 0; i < 40; i++) conduit.moveLiquid(fa, Liquids.slag);
            check(fa.health >= hp - 0.01f && conduit.liquids.get(Liquids.slag) >= condSlag - 0.01f, "Mit liquidFilter: keine Dampf-/Brandreaktion und kein Schaden an der Uebergabe");
            //Gegenprobe: ohne Filter reproduziert sich der urspruengliche Fehler
            boolean old = fa.block.liquidFilter[Liquids.slag.id];
            fa.block.liquidFilter[Liquids.slag.id] = false;
            conduit.liquids.clear();
            conduit.liquids.add(Liquids.slag, conduit.block.liquidCapacity);
            float hp2 = fa.health, cs2 = conduit.liquids.get(Liquids.slag);
            for(int i = 0; i < 40; i++) conduit.moveLiquid(fa, Liquids.slag);
            boolean reproduced = fa.health < hp2 - 0.01f || conduit.liquids.get(Liquids.slag) < cs2 - 0.01f;
            fa.block.liquidFilter[Liquids.slag.id] = old;
            check(reproduced, "Gegenprobe: OHNE liquidFilter loest die Engine die Reaktion aus (Fehler reproduziert; HP " + hp2 + " -> " + fa.health + ")");
            ct.setNet(Blocks.air, Team.derelict, 0);
            wipeFuser(fa);
        });

        //============ D2. Abwaertskompatibilitaet: alter Fuser-Spielstand (Format Version 1)
        now(() -> {
            try{
                wipeFuser(fa);
                fa.setRecipe(2);              //Klassik-Modus, Rezept 2
                fa.progress = 0.37f;
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                fa.write(new arc.util.io.Writes(new java.io.DataOutputStream(bos)));
                byte[] v2 = bos.toByteArray();
                //Der Version-3-Teil ohne Chargen-Signatur ist genau 81 Bytes lang; davor steht der unveraenderte Engine-Teil (Items/Fluessigkeiten ...)
                int base = v2.length - 81;
                java.io.ByteArrayOutputStream v1 = new java.io.ByteArrayOutputStream();
                v1.write(v2, 0, base);
                java.io.DataOutputStream d = new java.io.DataOutputStream(v1);
                d.writeByte(1); d.writeByte(2); d.writeFloat(0.37f);
                tmpFuserT = world.tile(30, 60);
                tmpFuserT.setNet(FluxBlocks.fuser, Team.sharded, 0);
                FluxFuser.FuserBuild old = (FluxFuser.FuserBuild)tmpFuserT.build;
                old.read(new arc.util.io.Reads(new java.io.DataInputStream(new java.io.ByteArrayInputStream(v1.toByteArray()))), (byte)0);
                check(old.classic && old.recipeIdx == 2 && Math.abs(old.progress - 0.37f) < 1e-4f, "Alter Spielstand (Fuser-Format v1) wird als Klassik-Fuser mit Rezept und Fortschritt gelesen");
                check(!old.active && old.boundCount() == 0 && old.outAmount == 0, "Alter Spielstand: freie Synthese startet sauber leer");
                tmpFuserT.setNet(Blocks.air, Team.derelict, 0);
                wipeFuser(fa);
                fa.setRecipe(-1);
            }catch(Throwable t){ Log.err("[MATTEST] v1", t); check(false, "Format-v1-Test: " + t); }
        });

        if(want("E")) sectionE();
        if(want("F")) sectionF();
        if(want("K")) sectionK();
        if(want("H")) sectionH();
        if(want("G")) now(() -> { //Restmaterial darf nicht ueber das Foerderband in den Turm laufen (er wuerde sofort schiessen)
            wipeFuser(fa);
            world.tile(42, 40).setNet(Blocks.air, Team.derelict, 0);
            turret.material = null; turret.materialCount = 0; turret.shotBuffer = 0;
        });
        if(want("G")) fireCases();      //G + H (Leistung)
        if(want("I")) saveLoadTests();  //I
    }

    void sectionE(){
        now(() -> fa.command(FluxFuser.CMD_ARM, 0, 1)); //automatischer Betrieb (neue Fuser starten im Vorbereitungsmodus)
        //============ E. Synthese Phase 1
        //E1: eine Zutat
        now(() -> {
            wipeFuser(fa);
            check(feed(fa, Items.copper, 1) == 1 && fa.slotOfItem(Items.copper) >= 0, "Erste Zutat belegt einen Eingangsplatz");
            check(fa.limit[fa.slotOfItem(Items.copper)] == FluxFuser.DEFAULT_LIMIT, "Standardmenge je Platz: " + FluxFuser.DEFAULT_LIMIT);
            check(feed(fa, Items.copper, 10) == 3 && !fa.acceptItem(null, Items.copper), "Platz nimmt genau bis zum Limit auf, danach wird abgelehnt");
        });
        waitUntil("Eine einzelne Zutat startet eine Synthese (nach der Ruhezeit)", () -> fa.active && fa.batchSlot >= 0, 240);
        waitUntil("Einzel-Synthese liefert ein Ergebnis", () -> fa.outAmount > 0 || turret.material != null, 1500);
        now(() -> {
            MaterialProfile p = null;
            for(int i = 0; i < 48; i++) if(MaterialRegistry.profile(FluxItems.material[i]) != null) p = MaterialRegistry.profile(FluxItems.material[i]);
            check(p != null && p.ingredients == 1 && p.signature.equals("c1;item:copper=16"), "Ergebnis hat die Zusammensetzung 'nur Kupfer': " + (p == null ? "-" : p.signature));
            for(SynthItem s : FluxItems.material) if(MaterialRegistry.profile(s) != null && MaterialRegistry.profile(s).signature.equals("c1;item:copper=16")) sCopper = s;
        });
        //E2: Transport per Foerderband -> Turm, Identitaet bleibt
        waitUntil("Ergebnis laeuft ueber das Foerderband in den Turm", () -> turret.material != null && turret.materialCount + turret.shotBuffer > 0, 1500);
        now(() -> {
            check(turret.material == sCopper, "Turm erhaelt exakt dasselbe Material (Identitaet nach dem Foerderband erhalten)");
            FluxMode m = turret.mode();
            check(m != null && m.bullet == ProjectileFactory.main[sCopper.slot] && m.item == sCopper, "Turm nutzt den dynamischen Modus dieses Materials");
            MaterialProfile p = MaterialRegistry.profile(sCopper);
            check(m != null && Math.abs(m.fluxPerTick - p.fluxPerTick) < 1e-5f && Math.abs(m.risk - p.risk) < 1e-5f && m.shotsPerItem == p.shotsPerItem, "Flux-Bedarf, Risiko und Schuesse pro Item stammen aus dem Profil");
        });

        //E3: zwei Zutaten mit Verhaeltnis 3:1 (Reihenfolge-Unabhaengigkeit)
        now(() -> {
            turret.material = null; turret.materialCount = 0; turret.shotBuffer = 0;
            wipeFuser(fa);
            feed(fa, Items.graphite, 1); setLimit(fa, Items.graphite, 6);
            feed(fa, Items.lead, 1); setLimit(fa, Items.lead, 2);
            check(feed(fa, Items.graphite, 10) == 5 && feed(fa, Items.lead, 10) == 1, "Mengen je Platz einstellbar (Graphit 6, Blei 2)");
        });
        waitUntil("Zwei Zutaten starten automatisch, wenn beide Plaetze voll sind", () -> fa.active, 120);
        now(() -> {
            sTwo = FluxItems.material[fa.batchSlot];
            check(fa.batchSig.equals("c1;item:graphite=12;item:lead=4"), "Verhaeltnis 6:2 ergibt Gewichte 12:4 -> " + fa.batchSig);
        });
        waitUntil("Zwei-Zutaten-Synthese liefert", () -> fa.outAmount > 0 || turret.material == sTwo, 1500);
        now(() -> {
            //Dieselbe Mischung in umgekehrter Zufuhr-Reihenfolge und anderer Menge (3:1 statt 6:2) -> derselbe Traeger, kein neuer Slot
            turret.material = null; turret.materialCount = 0; turret.shotBuffer = 0;
            fa.outAmount = 0; fa.outSlot = -1;
            wipeFuser(fa);
            checkAfter = MaterialRegistry.used();
            feed(fa, Items.lead, 1); setLimit(fa, Items.lead, 1);
            feed(fa, Items.graphite, 1); setLimit(fa, Items.graphite, 3);
            feed(fa, Items.graphite, 5); feed(fa, Items.lead, 5);
        });
        waitUntil("Umgekehrte Zufuhr, halbe Menge: Charge startet", () -> fa.active, 120);
        now(() -> {
            check(fa.batchSlot == sTwo.slot && MaterialRegistry.used() == checkAfter, "Gleiches Verhaeltnis -> gleicher Traeger (Slot " + sTwo.slot + "), kein neuer Slot belegt");
        });
        waitUntil("Charge abgeschlossen", () -> !fa.active, 1500);

        //E4: Fluessigkeit ueber eine ECHTE Leitung, Fluessigkeit-only Synthese
        now(() -> {
            fa.outAmount = 0; fa.outSlot = -1;
            wipeFuser(fa);
            Tile ct = world.tile(40, 38);
            ct.setNet(Blocks.conduit, Team.sharded, 1);
            conduitB = ct.build;
            conduitB.liquids.clear();
            conduitB.liquids.add(Liquids.water, conduitB.block.liquidCapacity);
            hpBefore = fa.health;
        });
        waitUntil("Wasser fliesst ueber eine echte Leitung in den Fuser", () -> fa.liquids.get(Liquids.water) > 5f, 300);
        now(() -> {
            check(fa.slotOfLiquid(Liquids.water) >= 0 && fa.kind[fa.slotOfLiquid(Liquids.water)] == 2, "Fluessigkeit belegt einen Fluessigkeits-Platz (nicht als Item behandelt)");
            check(fa.health >= hpBefore - 0.01f, "Leitung -> Fuser: keine Dampf-/Brandschaeden an der Uebergabe");
            check(fa.items.total() == 0, "Fluessigkeit landet nicht im Item-Inventar");
            conduitB.liquids.clear();
            world.tile(40, 38).setNet(Blocks.air, Team.derelict, 0);
            feedLiquid(fa, Liquids.water, 60f); //bis zum Limit auffuellen
        });
        waitUntil("Reines Fluessigkeits-Material startet (Wasser bis zum Limit)", () -> fa.active, 600);
        now(() -> {
            sWater = FluxItems.material[fa.batchSlot];
            check(MaterialRegistry.profile(sWater).signature.equals("c1;liquid:water=16") && fa.liquids.get(Liquids.water) < 15f, "Fluessigkeitsmenge wurde in die Charge uebernommen");
        });
        waitUntil("Fluessigkeits-Synthese liefert", () -> fa.outAmount > 0 || turret.material == sWater, 1500);

        //E5: feste Stoffe + Fluessigkeiten gemischt
        now(() -> {
            turret.material = null; turret.materialCount = 0; turret.shotBuffer = 0; fa.outAmount = 0; fa.outSlot = -1;
            wipeFuser(fa);
            feed(fa, Items.copper, 1); setLimit(fa, Items.copper, 2);
            feedLiquid(fa, Liquids.water, 1f); setLimit(fa, Liquids.water, 2);
            feedLiquid(fa, Liquids.cryofluid, 1f); setLimit(fa, Liquids.cryofluid, 1);
            feed(fa, Items.copper, 5); feedLiquid(fa, Liquids.water, 60f); feedLiquid(fa, Liquids.cryofluid, 60f);
        });
        waitUntil("Feste Zutat + zwei Fluessigkeiten: Charge startet", () -> fa.active, 300);
        now(() -> check(fa.batchSig.equals("c1;item:copper=8;liquid:cryofluid=4;liquid:water=4") || fa.batchSig.startsWith("c1;item:copper="), "Mischung aus Item und Fluessigkeiten -> " + fa.batchSig));
        waitUntil("Gemischte Charge abgeschlossen", () -> !fa.active, 1500);

        //E6: ELF Zutaten
        now(() -> {
            fa.outAmount = 0; fa.outSlot = -1;
            wipeFuser(fa);
            Item[] items = {Items.copper, Items.lead, Items.sand, Items.titanium, Items.graphite, Items.silicon, Items.scrap, Items.metaglass, Items.beryllium};
            for(Item i : items){ feed(fa, i, 1); setLimit(fa, i, 1); }
            feedLiquid(fa, Liquids.water, 1f); setLimit(fa, Liquids.water, 1);
            feedLiquid(fa, Liquids.cryofluid, 1f); setLimit(fa, Liquids.cryofluid, 1);
            check(fa.boundCount() == 11, "Elf verschiedene Zutaten belegen alle Eingangsplaetze");
            check(!fa.acceptItem(null, Items.thorium) && !fa.acceptLiquid(null, Liquids.oil), "Zwoelfte Zutat (Item oder Fluessigkeit) wird abgelehnt");
            for(Item i : items) feed(fa, i, 2);
            feedLiquid(fa, Liquids.water, 30f); feedLiquid(fa, Liquids.cryofluid, 30f);
        });
        waitUntil("Elf Zutaten: Charge startet", () -> fa.active, 300);
        now(() -> {
            MaterialProfile p = MaterialRegistry.profile(FluxItems.material[fa.batchSlot]);
            sEleven = FluxItems.material[fa.batchSlot];
            check(p.ingredients == 11 && p.signature.split(";").length == 12, "Elf-Zutaten-Material hat alle 11 Anteile im Profil (" + p.hazard + " = " + MaterialVisuals.hazardName(p.hazard) + ")");
        });
        waitUntil("Elf-Zutaten-Synthese liefert (Flux wird dafuer zugeteilt)", () -> fa.outAmount > 0 || turret.material == sEleven, 2500);

        //E7: ein Item gleichzeitig aus dem Fuser ueber Foerderband in den Turm - Identitaet fuer alle Ergebnisse
        now(() -> {
            turret.material = null; turret.materialCount = 0; turret.shotBuffer = 0;
            check(turret.acceptItem(null, sEleven) && FluxModes.get(sEleven) != null, "Turm nimmt auch das Elf-Zutaten-Material als Munition an");
        });

    }

    void sectionF(){
        now(() -> fb.command(FluxFuser.CMD_ARM, 0, 1));
        //============ F. Gefahrenreaktionen
        // F1: Verduennung verhindert die Durchreaktion
        now(() -> {
            wipeFuser(fb);
            feedLiquid(fb, Liquids.oil, 1f); setLimit(fb, Liquids.oil, 2);
            feedLiquid(fb, Liquids.slag, 1f); setLimit(fb, Liquids.slag, 2);
            feedLiquid(fb, Liquids.cryofluid, 1f); setLimit(fb, Liquids.cryofluid, 16);
            feedLiquid(fb, Liquids.oil, 60f); feedLiquid(fb, Liquids.slag, 60f); feedLiquid(fb, Liquids.cryofluid, 200f);
            predictedDiluted = Float.NaN;
        });
        waitUntil("Oel + Schlacke stark mit Kryofluid verduennt: Charge startet", () -> fb.active, 300);
        now(() -> {
            MaterialProfile p = fb.batchProfile();
            predictedDiluted = p.peakHeat;
            check(p.hazard <= MaterialProfile.HAZARD_UNSTABLE, "Verduennte Oel-Schlacke-Mischung ist beherrschbar (Spitze " + String.format("%.2f", p.peakHeat) + ")");
        });
        waitUntil("Verduennte Charge laeuft ohne Explosion durch", () -> !fb.active && fb.outAmount + (fb.outSlot >= 0 ? 0 : 0) >= 0 && fb.stress < 0.3f, 3000);
        now(() -> check(fb.isValid() && fb.health >= fb.maxHealth - 1f, "Fuser unbeschaedigt nach stabiler Charge"));

        // F2: Durchgehende Reaktion, Kuehlung durch Pause (Flux kappen), Abbruch
        now(() -> {
            fb.outAmount = 0; fb.outSlot = -1;
            wipeFuser(fb);
            feedLiquid(fb, Liquids.oil, 1f); setLimit(fb, Liquids.oil, 4);
            feedLiquid(fb, Liquids.slag, 1f); setLimit(fb, Liquids.slag, 4);
            feedLiquid(fb, Liquids.oil, 60f); feedLiquid(fb, Liquids.slag, 60f);
        });
        delay(150);
        now(() -> {
            check(!fb.active && fb.risky && fb.state() == skillable.material.FuserState.READY, "Riskante Mischung (Oel + Schlacke) startet nie automatisch, auch nicht im Betrieb (" + fb.statusText().replace('\n', ' ') + ")");
            fb.command(FluxFuser.CMD_START, 0, 0); //bewusste Bestaetigung
        });
        waitUntil("Oel + Schlacke 1:1: Charge startet nach Bestaetigung", () -> fb.active, 300);
        now(() -> {
            MaterialProfile p = fb.batchProfile();
            check(p.hazard == MaterialProfile.HAZARD_RUNAWAY, "Oel + Schlacke wird als durchgehende Reaktion eingestuft (Spitze " + String.format("%.2f", p.peakHeat) + ")");
        });
        waitUntil("Waerme steigt waehrend der Reaktion (nachvollziehbar sichtbar)", () -> fb.heat > 0.4f, 600);
        now(() -> {
            check(fb.statusText().length() > 0, "Statustext waehrend der Reaktion: " + fb.statusText().replace('\n', ' '));
            node.toggleLink(fb.pos()); //Flux kappen
            heatMark = fb.heat;
        });
        delay(90);
        now(() -> {
            check(!fb.working && fb.heat < heatMark - 0.02f && fb.active, "Ohne Flux pausiert die Charge und die Waerme faellt langsam (nur passive Abstrahlung) (" + String.format("%.2f -> %.2f", heatMark, fb.heat) + ")");
            fb.command(FluxFuser.CMD_ABORT, 0, 0);
            check(!fb.active, "Abbrechen laesst die Charge ab");
            check(fb.isValid() && fb.health >= fb.maxHealth - 1f, "Kein Schaden durch Pausieren und Abbrechen");
        });
        // F3: ungebremst -> Explosion
        now(() -> {
            node.toggleLink(fb.pos());
            wipeFuser(fb);
            feedLiquid(fb, Liquids.oil, 1f); setLimit(fb, Liquids.oil, 4);
            feedLiquid(fb, Liquids.slag, 1f); setLimit(fb, Liquids.slag, 4);
            feedLiquid(fb, Liquids.oil, 60f); feedLiquid(fb, Liquids.slag, 60f);
            fb.command(FluxFuser.CMD_START, 0, 0);
            instabBefore = acc.network == null ? 0f : acc.network.maxInstability;
            maxInstab = 0f;
            wallT = world.tile(44, 52);
            wallT.setNet(Blocks.copperWall, Team.sharded, 0);
            wallHp = wallT.build.health;
        });
        waitUntil("Ungebremst: Reaktion laeuft durch und explodiert", () -> { if(acc.network != null) maxInstab = Math.max(maxInstab, acc.network.maxInstability); return !fb.isValid() || (fb.stress > 0.3f && !fb.active); }, 3000);
        now(() -> {
            check(!fb.isValid() || fb.health < fb.maxHealth - 1f, "Explosion beschaedigt den Fuser (" + (fb.isValid() ? fb.health + "/" + fb.maxHealth : "zerstoert") + ")");
            check(maxInstab > instabBefore + 0.05f, "Explosion belastet das Flux-Netz (Instabilitaet " + String.format("%.2f -> %.2f", instabBefore, maxInstab) + ")");
            info("Wand in 4 Kacheln Entfernung: " + (wallT.build == null ? "zerstoert" : wallT.build.health + "/" + wallHp));
        });
        now(() -> { //Fuser B neu aufbauen fuer spaetere Tests
            if(!fb.isValid()){ fuserBT.setNet(FluxBlocks.fuser, Team.sharded, 0); }
        });
        delay(5);
        now(() -> { fb = (FluxFuser.FuserBuild)fuserBT.build; if(!node.links.contains(fb.pos())) node.toggleLink(fb.pos()); });

        //============ G. Turm: jede Materialklasse feuert anders
    }

    float predictedDiluted, heatMark, instabBefore, maxInstab, wallHp, hpBefore;
    int checkAfter;
    Building conduitB;
    Tile wallT;

    // ------------------------------------------------------------------ Turm-Tests (dynamisch eingehaengt)
    static class Case{
        String name; int form; Object[] args; Case(String n, int f, Object... a){ name = n; form = f; args = a; }
    }

    final Map<String, Object[]> results = new LinkedHashMap<>();

    void fireCases(){
        Case[] cases = {
            new Case("kinetisch (Wolfram+Titan)", ShotSpec.SLUG, Items.tungsten, 5, Items.titanium, 3),
            new Case("elektrisch (Kupfer+Surge)", ShotSpec.BOLT, Items.copper, 5, Items.surgeAlloy, 3),
            new Case("thermisch (Pyratit+Schlacke)", ShotSpec.EMBER, Items.pyratite, 4, Liquids.slag, 30),
            new Case("explosiv (Sprengmittel)", ShotSpec.SHELL, Items.blastCompound, 5),
            new Case("Flux (Thorium+Phasengewebe)", ShotSpec.RIFT, Items.thorium, 3, Items.phaseFabric, 3),
        };
        for(Case c : cases){
            final int[] waited = {0};
            add(() -> { //Instabilitaet der vorigen Tests abklingen lassen: ein kollabiertes/instabiles Netz liefert korrekt keinen Flux
                boolean calm = acc.network != null && !acc.network.offline && acc.network.maxInstability < 0.15f && !acc.locked();
                if(calm || ++waited[0] > 4000){ if(!calm) info("Netz nicht abgeklungen (Instabilitaet " + (acc.network == null ? -1 : acc.network.maxInstability) + ")"); return true; }
                return false;
            });
            final SynthItem[] item = {null};
            add(() -> { //Gegner erst jetzt (Netz ist ruhig) und nur fuer diesen Fall
                if(enemy != null) enemy.remove();
                enemy = UnitTypes.dagger.create(Team.crux);
                enemy.set(turret.x + 8 * 13, turret.y);
                enemy.add();
                enemy.health = enemy.maxHealth = 1_000_000f;
                enemy.type.speed = 0f;
                return true;
            });
            final int[] shots0 = {0};
            final float[] hp0 = {0f};
            final float[] maxInstabCase = {0f};
            final float[] maxDemand = {0f};
            final Set<BulletType> types = new HashSet<>();
            final int[] peakBullets = {0};
            final int[] echoSeen = {0};
            final float[] maxLoad = {0f};
            add(() -> {
                item[0] = MaterialRegistry.obtain(comp(c.args));
                turret.material = null; turret.materialCount = 0; turret.shotBuffer = 0;
                check(item[0] != null && turret.acceptItem(null, item[0]), "[" + c.name + "] Turm akzeptiert das Material");
                for(int i = 0; i < 12; i++) if(turret.acceptItem(null, item[0])) turret.handleItem(null, item[0]);
                shots0[0] = turret.totalShots; hp0[0] = enemy.health;
                if(acc.network != null) acc.network.maxInstability = Math.max(0f, acc.network.maxInstability);
                return true;
            });
            for(int t = 0; t < 300; t++) add(() -> {
                for(Bullet b : Groups.bullet){ if(b.type == ProjectileFactory.main[item[0].slot]) types.add(b.type); if(b.type == ProjectileFactory.echo[item[0].slot]) echoSeen[0]++; }
                maxLoad[0] = Math.max(maxLoad[0], MatFx.budget.load(Time.time));
                int n = 0; for(Bullet b : Groups.bullet) if(b.team == Team.sharded) n++;
                peakBullets[0] = Math.max(peakBullets[0], n);
                if(turret.network != null) maxInstabCase[0] = Math.max(maxInstabCase[0], turret.network.maxInstability);
                maxDemand[0] = Math.max(maxDemand[0], turret.lastDemand);
                return true;
            });
            add(() -> {
                MaterialProfile p = MaterialRegistry.profile(item[0]);
                MatBullet bt = ProjectileFactory.main[item[0].slot];
                int shots = turret.totalShots - shots0[0];
                check(shots > 0, "[" + c.name + "] feuert tatsaechlich (" + shots + " Schuesse)");
                check(bt.spec != null && bt.spec.form == c.form && p.primary == (c.form == ShotSpec.SLUG ? 0 : c.form == ShotSpec.BOLT ? 1 : c.form == ShotSpec.EMBER ? 2 : c.form == ShotSpec.SHELL ? 3 : 4), "[" + c.name + "] Projektilform " + ShotSpec.FORM_NAMES[c.form] + " aus der Materialklasse");
                check(types.contains(bt), "[" + c.name + "] das Projektil mit dem Typ DIESES Materials flog in der Welt");
                check(enemy.health < hp0[0], "[" + c.name + "] trifft das Ziel (" + (int)(hp0[0] - enemy.health) + " Schaden)");
                check(Math.abs(maxDemand[0] - p.fluxPerTick) < 0.01f || maxDemand[0] > 0.5f * p.fluxPerTick, "[" + c.name + "] Flux-Bedarf beim Schiessen = Profilwert (" + String.format("%.2f", maxDemand[0]) + " von " + String.format("%.2f", p.fluxPerTick) + ")");
                check(bt.plan != null && bt.flight != null && bt.plan.echoes == bt.spec.echoCount, "[" + c.name + "] Flug- und Einschlagsplan sind konfiguriert (" + bt.plan.count + " Phasen, " + bt.spec.echoCount + " echte Folgereaktionen, " + bt.flight.layers + " Spurschichten)");
                results.put(c.name, new Object[]{bt, p, shots, (int)(hp0[0] - enemy.health), maxInstabCase[0], peakBullets[0]});
                info(String.format("%s: Schuesse=%d Schaden=%d Instabilitaet=%.2f Projektile(max)=%d Hazard=%s lightning=%d frag=%d splash=%.0f pierce=%d homing=%.2f weave=%.1f speed=%.1f",
                    c.name, shots, (int)(hp0[0] - enemy.health), maxInstabCase[0], peakBullets[0], MaterialVisuals.hazardName(p.hazard), bt.lightning, bt.fragBullets, bt.splashDamageRadius, bt.pierceCap, bt.homingPower, bt.weaveMag, bt.speed));
                turret.material = null; turret.materialCount = 0; turret.shotBuffer = 0;
                if(enemy != null){ enemy.remove(); enemy = null; }
                return true;
            });
            for(int t = 0; t < 240; t++) add(() -> { //Geschosse/Zonen/Echos auslaufen lassen und weiter messen (Einschlag kann spaet im Fenster liegen)
                for(Bullet b : Groups.bullet) if(b.type == ProjectileFactory.echo[item[0].slot]) echoSeen[0]++;
                maxLoad[0] = Math.max(maxLoad[0], MatFx.budget.load(Time.time));
                return true;
            });
            add(() -> {
                MatBullet bt = ProjectileFactory.main[item[0].slot];
                check(bt.spec.echoCount == 0 || echoSeen[0] > 0, "[" + c.name + "] echte Folgereaktionen wurden ausgeloest (" + bt.spec.echoCount + " geplant, " + echoSeen[0] + " Echo-Geschosse beobachtet)");
                info(c.name + ": Treffer-Aufrufe=" + bt.hits + ", Lastbudget=" + String.format("%.1f", maxLoad[0]) + ", Echos=" + echoSeen[0]);
                check(bt.hits == 0 || maxLoad[0] > 0f, "[" + c.name + "] Einschlagseffekte wurden angefordert (" + bt.hits + " Treffer-Aufrufe, Lastbudget " + String.format("%.0f", maxLoad[0]) + ")");
                return true;
            });
        }
        add(() -> {
            //Vergleich der Klassen: wirklich verschieden, nicht nur Farbe/Schaden
            Set<String> keys = new HashSet<>();
            Set<Boolean> artillery = new HashSet<>();
            for(Object[] r : results.values()){ MatBullet bt = (MatBullet)r[0]; keys.add(bt.spec.behaviourKey()); artillery.add(bt.spec.artillery); }
            check(keys.size() == 5, "Fuenf Materialklassen ergeben fuenf unterschiedliche Verhaltensprofile in der Engine (" + keys.size() + ")");
            MatBullet slug = (MatBullet)results.get("kinetisch (Wolfram+Titan)")[0], bolt = (MatBullet)results.get("elektrisch (Kupfer+Surge)")[0],
                ember = (MatBullet)results.get("thermisch (Pyratit+Schlacke)")[0], shell = (MatBullet)results.get("explosiv (Sprengmittel)")[0],
                rift = (MatBullet)results.get("Flux (Thorium+Phasengewebe)")[0];
            check(bolt.lightning > 0 && slug.lightning == 0 && shell.lightning == 0, "Nur elektrische Munition erzeugt Kettenblitze");
            check(shell.splashDamageRadius > 40f && slug.splashDamageRadius == 0f, "Nur explosive Munition hat grossen Flaechenschaden");
            check(!shell.collides && shell.scaleLife && slug.collides, "Granaten fliegen als Artillerie (kollidieren nicht im Flug), Slugs nicht");
            check(rift.pierceBuilding && rift.pierceCap >= 4 && rift.homingPower > 0f && !rift.hittable, "Flux-Geschosse durchdringen Gebaeude, folgen leicht dem Ziel und sind nicht abschiessbar");
            check(ember.makeFire && ember.despawnHit && ember.drag > 0f, "Thermische Geschosse zuenden und bremsen ab");
            check(bolt.weaveMag > 0f && slug.weaveMag == 0f, "Bewegungsmuster: Boegen schlaengeln, Slugs fliegen gerade");
            check(bolt.speed > shell.speed * 2f, "Geschwindigkeiten unterscheiden sich stark (Bogen " + String.format("%.1f", bolt.speed) + " vs Granate " + String.format("%.1f", shell.speed) + ")");
            float instabShell = (Float)results.get("explosiv (Sprengmittel)")[4], instabSlug = (Float)results.get("kinetisch (Wolfram+Titan)")[4];
            info("Instabilitaet: explosiv " + instabShell + ", kinetisch " + instabSlug);
            check(instabShell >= instabSlug - 0.001f, "Explosive (instabile) Munition belastet das Netz nicht weniger als stabile");
            return true;
        });
        // Leistung + Speichern/Laden
        add(() -> { perfTest(); return true; });
        delay(400);
        add(() -> { perfMemory(); return true; });
        delay(5);
        add(() -> { perfCleanup(); return true; });
    }

    void perfTest(){
        //Rein rechnerisch: Konfiguration aller 48 Slots
        long t0 = Time.nanos();
        Random rnd = new Random(1);
        resetRegistry();
        Item[] pool = {Items.copper, Items.lead, Items.sand, Items.coal, Items.titanium, Items.scrap, Items.graphite, Items.silicon, Items.metaglass, Items.thorium, Items.pyratite, Items.sporePod, Items.surgeAlloy, Items.phaseFabric, Items.blastCompound};
        Set<String> seen = new HashSet<>();
        while(MaterialRegistry.used() < 48){
            Object[] args = new Object[6];
            for(int i = 0; i < 3; i++){ args[i * 2] = pool[rnd.nextInt(pool.length)]; args[i * 2 + 1] = 1 + rnd.nextInt(7); }
            Composition c = comp(args);
            if(seen.add(c.signature)) MaterialRegistry.obtain(c);
        }
        double ms = (Time.nanos() - t0) / 1e6;
        check(ms < 2000, "48 Materialien berechnen, Icons/Geschosse konfigurieren: " + String.format("%.0f ms", ms));
        System.gc();
        memBase = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        //Viele Geschosse gleichzeitig
        int forms[] = new int[5];
        List<Bullet> list = new ArrayList<>();
        for(int round = 0; round < 3; round++) for(int i = 0; i < 48; i++){
            MatBullet bt = ProjectileFactory.main[i];
            if(bt.spec == null) continue;
            if(round == 0) forms[bt.spec.form]++;
            for(int k = 0; k < 8; k++) list.add(bt.create(null, Team.sharded, turret.x + 20, turret.y + 40 + k * 3, 90f + k * 10f));
        }
        int n = Groups.bullet.size();
        info("Geschosse nach Erzeugung: " + n);
        long t1 = Time.nanos();
        for(int i = 0; i < 30; i++) Groups.bullet.update();
        double perTick = (Time.nanos() - t1) / 1e6 / 30.0;
        info("Formen ueber 48 Materialien: " + Arrays.toString(forms));
        check(perTick < 12.0, "Update von " + n + " gleichzeitigen Geschossen (alle Formen): " + String.format("%.2f ms pro Tick", perTick));
        info("Geschosse nach 30 Updates (inkl. entstandener Splitter): " + Groups.bullet.size());
    }

    long memBase;

    void perfMemory(){
        //Geschosse laufen natuerlich aus (Splitter, Blitze, Zonen entstehen und vergehen wie im Spiel)
        Groups.bullet.update();
        System.gc();
        long mem1 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        check(mem1 - memBase < 30L * 1024 * 1024, "Kein unkontrolliertes Speicherwachstum nach dem Burst aus 3 x 384 Geschossen samt Splittern (" + (mem1 - memBase) / 1024 + " KiB)");
        resetRegistry();
    }

    void perfCleanup(){
        check(Groups.bullet.size() <= 5, "Alle Geschosse, Splitter und Zonen sind nach ihrer Lebensdauer verschwunden (" + Groups.bullet.size() + " uebrig)");
    }

    // ------------------------------------------------------------------ Speichern / Laden
    String savedSigA, savedSigOut;
    float savedProgress;
    int savedOutSlot, savedOut, savedLimit0, savedConvCount, savedContCount, savedTurretCount;
    long savedHashes;
    SynthItem sSave1, sSave2;

    void saveLoadTests(){
        now(() -> {
            //Zustand fuer den Spielstand aufbauen
            if(enemy != null){ enemy.remove(); enemy = null; }
            turret.material = null; turret.materialCount = 0; turret.shotBuffer = 0;
            wipeFuser(fa); wipeFuser(fb);
            fa.command(FluxFuser.CMD_ARM, 0, 1);
            fa.outAmount = 0; fa.outSlot = -1;
            world.tile(42, 40).setNet(Blocks.air, Team.derelict, 0); //kein Abnehmer: Ausgabepuffer bleibt gefuellt
            //Foerderband-Sackgasse mit Material
            for(int x = 59; x <= 64; x++) world.tile(x, 20).setNet(Blocks.conveyor, Team.sharded, 0);
            world.tile(65, 20).setNet(Blocks.copperWall, Team.sharded, 0);
            world.tile(70, 20).setNet(Blocks.container, Team.sharded, 0);
            sSave1 = MaterialRegistry.obtain(comp(Items.copper, 3, Items.lead, 1));
            sSave2 = MaterialRegistry.obtain(comp(Items.titanium, 2, Liquids.water, 20, Items.coal, 1));
            check(sSave1 != null && sSave2 != null && sSave1 != sSave2, "Zwei Materialien fuer den Spielstand vorbereitet");
        });
        delay(3);
        now(() -> {
            Building c0 = world.tile(60, 20).build, c3 = world.tile(63, 20).build, c4 = world.tile(64, 20).build;
            for(int i = 0; i < 3; i++) if(c4.acceptItem(c3, sSave1)) c4.handleItem(c3, sSave1);
            if(c0.acceptItem(world.tile(59, 20).build == null ? c3 : world.tile(59, 20).build, sSave2)) c0.handleItem(world.tile(59, 20).build == null ? c3 : world.tile(59, 20).build, sSave2);
            world.tile(70, 20).build.items.add(sSave2, 7);
            turret.handleItem(null, sSave1); turret.handleItem(null, sSave1);
            //erste Charge desselben Materials, wird zum Ausgabepuffer
            feed(fa, Items.graphite, 1); setLimit(fa, Items.graphite, 2);
            feed(fa, Items.lead, 1); setLimit(fa, Items.lead, 2);
            feed(fa, Items.graphite, 3); feed(fa, Items.lead, 3);
        });
        waitUntil("Erste Charge liegt im Ausgabepuffer (kein Abnehmer)", () -> fa.outAmount > 0 && !fa.active, 1500);
        now(() -> {
            feed(fa, Items.graphite, 3); feed(fa, Items.lead, 3);
        });
        waitUntil("Zweite Charge laeuft mit Fortschritt (vor dem Speichern)", () -> fa.active && fa.progress > 0.15f && fa.progress < 0.8f, 600);
        now(() -> {
            feed(fa, Items.sand, 1); setLimit(fa, Items.sand, 5); feed(fa, Items.sand, 2);
            for(Building b : new Building[]{world.tile(59, 20).build, world.tile(60, 20).build, world.tile(61, 20).build, world.tile(62, 20).build, world.tile(63, 20).build, world.tile(64, 20).build}) savedConvCount += b.items.get(sSave1) + b.items.get(sSave2);
            savedContCount = world.tile(70, 20).build.items.get(sSave2);
            savedTurretCount = turret.materialCount;
            savedOut = fa.outAmount; savedOutSlot = fa.outSlot;
            savedLimit0 = fa.limit[fa.slotOfItem(Items.sand)];
            savedSigA = fa.batchSig; savedProgress = fa.progress;
            check(savedConvCount >= 3 && savedOut > 0, "Vor dem Speichern: " + savedConvCount + " Materialien auf dem Foerderband, " + savedOut + " im Fuser-Ausgang");
            savedHashes = 0;
            for(SynthItem s : FluxItems.material){ MaterialProfile p = MaterialRegistry.profile(s); if(p != null) savedHashes ^= p.hash * (s.slot + 1); }
            Fi f = new Fi("/tmp/flux-matselftest.msav");
            try{
                SaveIO.save(f);
                check(f.exists() && f.length() > 1000, "Spielstand per SaveIO geschrieben (" + f.length() + " Bytes)");
            }catch(Throwable t){ Log.err("[MATTEST] save", t); check(false, "SaveIO.save: " + t); }
            //Speicher des Registers bewusst verwerfen: es darf nur aus der Datei zurueckkommen
            state.rules.tags.remove(MaterialRegistry.TAG);
            MaterialRegistry.reset();
            check(MaterialRegistry.used() == 0, "Register im Speicher geleert (nur die Datei kennt die Materialien)");
        });
        now(() -> {
            try{
                SaveIO.load(new Fi("/tmp/flux-matselftest.msav"));
                state.set(mindustry.core.GameState.State.playing);
                check(true, "Spielstand per SaveIO geladen");
            }catch(Throwable t){ Log.err("[MATTEST] load", t); check(false, "SaveIO.load: " + t); }
        });
        delay(10);
        now(() -> {
            long h = 0;
            for(SynthItem s : FluxItems.material){ MaterialProfile p = MaterialRegistry.profile(s); if(p != null) h ^= p.hash * (s.slot + 1); }
            check(h == savedHashes && MaterialRegistry.used() >= 2, "Nach dem Laden: dieselben Slots mit denselben Zusammensetzungen (" + MaterialRegistry.used() + " Materialien)");
            check(MaterialRegistry.profile(sSave1) != null && MaterialRegistry.profile(sSave1).signature.equals("c1;item:copper=12;item:lead=4"), "Material 1 nach dem Laden: " + (MaterialRegistry.profile(sSave1) == null ? "FEHLT" : MaterialRegistry.profile(sSave1).signature));
            check(sSave1.localizedName.contains(MaterialRegistry.profile(sSave1).code()), "Name/Eigenschaften nach dem Laden wiederhergestellt: " + sSave1.localizedName);
            int cnt = 0;
            for(int x = 59; x <= 64; x++){ Building b = world.tile(x, 20).build; if(b != null) cnt += b.items.get(sSave1) + b.items.get(sSave2); }
            check(cnt == savedConvCount, "Materialien auf dem Foerderband nach dem Laden erhalten (" + cnt + "/" + savedConvCount + ")");
            check(world.tile(70, 20).build.items.get(sSave2) == savedContCount, "Materialien im Behaelter erhalten (" + savedContCount + ")");
            SkillableTurret.SkillableBuild t2 = (SkillableTurret.SkillableBuild)world.tile(49, 40).build;
            check(t2.material == sSave1 && t2.materialCount == savedTurretCount && t2.mode() != null && t2.mode().bullet == ProjectileFactory.main[sSave1.slot], "Turm hat sein Material nach dem Laden und nutzt den Modus (" + t2.materialCount + ")");
            FluxFuser.FuserBuild f2 = (FluxFuser.FuserBuild)world.tile(40, 40).build;
            check(f2 != null && f2.armed && world.tile(10, 10).build instanceof MaterialGuard.GuardedCoreBuild, "Betriebszustand (automatisch) und Kern-Absicherung nach dem Laden erhalten");
            check(f2 != null && !f2.classic && f2.active && savedSigA.equals(f2.batchSig) && f2.progress >= savedProgress - 0.02f && f2.progress < savedProgress + 0.4f, "Laufende Charge nach dem Laden: gleiche Zusammensetzung, Fortschritt laeuft von " + (f2 == null ? 0 : f2.progress) + " (vorher " + savedProgress + ")");
            check(f2 != null && f2.outSlot == savedOutSlot && f2.outAmount == savedOut, "Ausgabepuffer des Fusers nach dem Laden erhalten (" + savedOut + " Stueck)");
            check(f2 != null && f2.slotOfItem(Items.sand) >= 0 && f2.limit[f2.slotOfItem(Items.sand)] == savedLimit0 && f2.items.get(Items.sand) == 3, "Eingangsplaetze samt Limit und Menge erhalten");
            fa = f2; turret = t2;
        });
        waitUntil("Nach dem Laden schliesst die gespeicherte Charge ab (Flux-Netz wieder verbunden)", () -> {
            FluxFuser.FuserBuild f = (FluxFuser.FuserBuild)world.tile(40, 40).build;
            return f != null && !f.active && f.outAmount > savedOut;
        }, 3000);
    }

    void wipeFuserKeepOut(FluxFuser.FuserBuild f){
        int o = f.outAmount, s = f.outSlot;
        wipeFuser(f);
        f.outAmount = o; f.outSlot = s;
    }

    // ------------------------------------------------------------------ K: Kern-Sperre, Kuehlblock, Vorbereiten/Betrieb
    void runTicks(int n, Runnable r){
        int[] t = {0};
        add(() -> { r.run(); return ++t[0] >= n; });
    }

    float[] mark = new float[8];
    Object[] heavy;
    FluxFuser.FuserBuild zf, xf, yf;
    FuserCooler.CoolerBuild coolW;

    void feedMix(FluxFuser.FuserBuild f, Object[] args){
        for(int i = 0; i < args.length; i += 2){
            int n = ((Number)args[i + 1]).intValue();
            if(args[i] instanceof Item it){ feed(f, it, 1); setLimit(f, it, n); feed(f, it, n); }
            else{ Liquid l = (Liquid)args[i]; feedLiquid(f, l, 1f); setLimit(f, l, n); feedLiquid(f, l, n * 10f); }
        }
    }

    void sectionK(){
        //============ K1. Synthetische Materialien duerfen nicht in den Kern
        now(() -> {
            Building core = world.tile(10, 10).build;
            check(core instanceof MaterialGuard.GuardedCoreBuild && MaterialGuard.guarded >= 3, "Kerne sind gegen synthetische Materialien abgesichert (" + MaterialGuard.guarded + " Kernbauarten, " + MaterialGuard.skipped + " uebersprungen)");
            resetRegistry();
            SynthItem m = MaterialRegistry.obtain(comp(Items.copper, 3, Items.lead, 1));
            sCore = m;
            Unit u = UnitTypes.dagger.create(Team.sharded);
            check(!core.acceptItem(null, m) && core.acceptStack(m, 10, u) == 0 && core.getMaximumAccepted(m) == 0, "Kern lehnt Material ab: acceptItem, acceptStack (manuelles Ablegen), Maximalmenge");
            check(core.acceptItem(null, Items.copper) && core.acceptStack(Items.copper, 10, u) > 0, "Regulaere Items werden vom Kern weiter angenommen");
            core.items.add(m, 5);
            check(core.items.get(m) == 5, "Schon vorhandenes Material im Kern bleibt unveraendert");
            core.handleItem(null, m); core.handleStack(m, 3, u);
            check(core.items.get(m) == 5, "Direkte handleItem/handleStack-Aufrufe tragen kein Material ein");
            core.items.remove(m, 5);
            //Foerderband in den Kern
            for(int y = 12; y <= 17; y++){ world.tile(10, y).setNet(Blocks.conveyor, Team.sharded, 3); world.tile(9, y).setNet(Blocks.conveyor, Team.sharded, 3); }
            //Behaelter am Kern (verbunden) und Fuser am Kern
            world.tile(12, 10).setNet(Blocks.container, Team.sharded, 0);
            world.tile(7, 10).setNet(FluxBlocks.fuser, Team.sharded, 0);
        });
        delay(5);
        now(() -> {
            Building core = world.tile(10, 10).build;
            Building cont = world.tile(12, 10).build;
            boolean linked = cont instanceof mindustry.world.blocks.storage.StorageBlock.StorageBuild sb && sb.linkedCore == core;
            check(linked, "Behaelter neben dem Kern ist mit dem Kern verbunden (teilt dessen Inventar)");
            check(!cont.acceptItem(null, sCore) && cont.acceptItem(null, Items.copper), "Verbundener Behaelter reicht die Sperre weiter (kein Umweg ueber den Behaelter), regulaere Items gehen durch");
            Building top = world.tile(10, 16).build, topC = world.tile(9, 16).build, up = world.tile(10, 17).build, upC = world.tile(9, 17).build;
            //Material auf die Bandstrecke geben (vom vorgelagerten Band aus); Kupfer auf die zweite
            copperBefore = core.items.get(Items.copper);
            fuserCore = (FluxFuser.FuserBuild)world.tile(7, 10).build;
            fuserCore.outSlot = sCore.slot; fuserCore.outAmount = 3;
        });
        fedM = 0; fedC = 0;
        runTicks(60, () -> { //je Tick hoechstens ein Item pro Band (Foerderband verlangt Abstand)
            Building top = world.tile(10, 16).build, topC = world.tile(9, 16).build, up = world.tile(10, 17).build, upC = world.tile(9, 17).build;
            if(fedM < 3 && top.acceptItem(up, sCore)){ top.handleItem(up, sCore); fedM++; }
            if(fedC < 3 && topC.acceptItem(upC, Items.copper)){ topC.handleItem(upC, Items.copper); fedC++; }
        });
        delay(180);
        now(() -> {
            Building core = world.tile(10, 10).build;
            check(fedM == 3 && fedC == 3, "Testaufbau: 3 Material- und 3 Kupfer-Items auf die Baender gegeben");
            check(core.items.get(sCore) == 0, "Foerderband liefert Material nicht in den Kern (Kern: " + core.items.get(sCore) + ")");
            int onBelt = 0;
            for(int y = 12; y <= 16; y++) onBelt += world.tile(10, y).build.items.get(sCore);
            check(onBelt == 3, "Das abgewiesene Material bleibt auf dem Band, nichts verschwindet (" + onBelt + " von 3)");
            check(core.items.get(Items.copper) > copperBefore, "Regulaeres Kupfer vom Nachbarband kommt weiter im Kern an (+" + (core.items.get(Items.copper) - copperBefore) + ")");
            check(fuserCore.outAmount == 3 && core.items.get(sCore) == 0, "Fuser neben dem Kern gibt sein Ergebnis nicht an den Kern ab (Ausgangspuffer bleibt " + fuserCore.outAmount + ")");
            //aufraeumen
            for(int y = 12; y <= 17; y++){ world.tile(10, y).setNet(Blocks.air, Team.derelict, 0); world.tile(9, y).setNet(Blocks.air, Team.derelict, 0); }
            world.tile(12, 10).setNet(Blocks.air, Team.derelict, 0);
            world.tile(7, 10).setNet(Blocks.air, Team.derelict, 0);
            //Nur Kerne wurden angefasst
            int fremd = 0;
            for(mindustry.world.Block b : content.blocks()){
                if(b instanceof mindustry.world.blocks.storage.CoreBlock) continue;
                Building probe = b.buildType.get();
                if(probe.getClass().getName().startsWith("skillable.synth.MaterialGuard")) fremd++;
            }
            check(fremd == 0, "Die Sperre betrifft keine anderen Bloecke (nur Kerne)");
        });

        //============ K2. Kuehlblock
        now(() -> {
            world.tile(70, 70).setNet(FluxBlocks.fuser, Team.sharded, 0);
            world.tile(67, 70).setNet(FluxBlocks.cooler, Team.sharded, 0);
            world.tile(60, 60).setNet(FluxBlocks.fuser, Team.sharded, 0);
            world.tile(65, 60).setNet(FluxBlocks.fuser, Team.sharded, 0);
            world.tile(62, 60).setNet(FluxBlocks.cooler, Team.sharded, 0);
            world.tile(62, 62).setNet(Blocks.combustionGenerator, Team.sharded, 0);
            world.tile(80, 80).setNet(FluxBlocks.cooler, Team.sharded, 0);
            world.tile(80, 82).setNet(Blocks.conveyor, Team.sharded, 0);
        });
        delay(5);
        now(() -> {
            zf = (FluxFuser.FuserBuild)world.tile(70, 70).build;
            xf = (FluxFuser.FuserBuild)world.tile(60, 60).build; yf = (FluxFuser.FuserBuild)world.tile(65, 60).build;
            coolW = (FuserCooler.CoolerBuild)world.tile(67, 70).build;
            FuserCooler.CoolerBuild between = (FuserCooler.CoolerBuild)world.tile(62, 60).build, lonely = (FuserCooler.CoolerBuild)world.tile(80, 80).build;
            check(coolW.target == zf, "Kuehlmantel erkennt den angrenzenden Fuser als Ziel");
            check(between.target == xf && xf.pos() < yf.pos(), "Beruehrt ein Kuehlmantel zwei Fuser, kuehlt er genau einen: den mit der kleinsten Position");
            check(lonely.target == null, "Kuehlmantel ohne Fuser (nur Foerderband daneben) hat kein Ziel");
            //Kuehlmittel-Eignung
            boolean ok = true;
            for(Liquid l : content.liquids()) if(coolW.acceptLiquid(null, l) != (FuserCooler.quality(l) > 0f)) ok = false;
            check(ok && FuserCooler.quality(Liquids.water) > 0f && FuserCooler.quality(Liquids.cryofluid) > FuserCooler.quality(Liquids.water) * 2.5f && FuserCooler.quality(Liquids.slag) == 0f && FuserCooler.quality(Liquids.oil) == 0f,
                "Nur echte Kuehlmittel werden angenommen: Wasser ja, Kryofluid ja (staerker), Schlacke/Oel nein");
            check(zf.acceptLiquid(null, Liquids.water) && zf.acceptLiquid(null, Liquids.cryofluid), "Wasser und Kryofluid bleiben Synthesezutaten des Fusers (trotz angrenzendem Kuehlmantel)");
            //kein Verbrauch, solange der Fuser kalt ist
            zf.heat = 0f; coolW.liquids.clear(); coolW.liquids.add(Liquids.water, 30f);
            mark[0] = coolW.liquids.get(Liquids.water);
        });
        runTicks(100, () -> {});
        now(() -> {
            check(Math.abs(coolW.liquids.get(Liquids.water) - mark[0]) < 0.01f && coolW.power == 0f, "Kalter Fuser: kein Kuehlmittelverbrauch");
            zf.heat = 0.6f; zf.active = false; mark[1] = zf.heat; coolW.liquids.clear();
        });
        runTicks(200, () -> {}); //ohne Kuehlmittel
        now(() -> {
            mark[2] = mark[1] - zf.heat; //Abfall nur durch passive Abstrahlung
            check(mark[2] > 0.06f && mark[2] < 0.10f, "Ohne Kuehlmittel faellt die Temperatur nur durch passive Abstrahlung (-" + String.format("%.3f", mark[2]) + " in 200 Ticks)");
            zf.heat = 0.6f; coolW.liquids.clear();
        });
        runTicks(200, () -> { if(coolW.acceptLiquid(null, Liquids.water)) coolW.handleLiquid(null, Liquids.water, 5f); }); //Wasser
        now(() -> {
            mark[3] = 0.6f - zf.heat;
            check(mark[3] > mark[2] * 2.5f, "Mit Wasser kuehlt der Mantel messbar staerker (-" + String.format("%.3f", mark[3]) + " statt -" + String.format("%.3f", mark[2]) + ")");
            zf.heat = 0.95f; coolW.liquids.clear();
        });
        now(() -> { coolW.liquids.add(Liquids.cryofluid, 20f); zf.heat = 0.95f; mark[7] = 20f; });
        runTicks(100, () -> {}); //ohne Nachfuellen
        now(() -> {
            float used = mark[7] - coolW.liquids.get(Liquids.cryofluid);
            check(used > 8f && used < 11f, "Kuehlmittelverbrauch folgt der Kuehlung: 0,1 Einheiten pro Tick (" + String.format("%.1f", used) + " in 100 Ticks)");
            zf.heat = 0.95f; coolW.liquids.clear();
        });
        runTicks(200, () -> { if(coolW.acceptLiquid(null, Liquids.cryofluid)) coolW.handleLiquid(null, Liquids.cryofluid, 5f); }); //Kryofluid
        now(() -> {
            mark[4] = 0.95f - zf.heat;
            check(mark[4] > mark[3] * 2f, "Kryofluid kuehlt deutlich staerker als Wasser (-" + String.format("%.3f", mark[4]) + " statt -" + String.format("%.3f", mark[3]) + ")");
            //zwei Fuser am selben Mantel
            FuserCooler.CoolerBuild between = (FuserCooler.CoolerBuild)world.tile(62, 60).build;
            xf.heat = 0.6f; yf.heat = 0.6f; xf.active = false; yf.active = false;
            mark[5] = xf.heat; mark[6] = yf.heat;
            between.liquids.clear();
        });
        runTicks(150, () -> { FuserCooler.CoolerBuild c = (FuserCooler.CoolerBuild)world.tile(62, 60).build; if(c.acceptLiquid(null, Liquids.water)) c.handleLiquid(null, Liquids.water, 5f); });
        now(() -> {
            float dx = mark[5] - xf.heat, dy = mark[6] - yf.heat;
            check(dx > dy * 3f && dy < 0.07f, "Zwei Fuser am selben Kuehlmantel: nur der Zielfuser wird gekuehlt (-" + String.format("%.3f", dx) + " gegen -" + String.format("%.3f", dy) + " passiv)");
            check(world.tile(62, 62).build.liquids == null || world.tile(62, 62).build.liquids.currentAmount() < 0.01f, "Angrenzender Generator wird nicht beeinflusst");
            //Obergrenze: fuenf Kuehlmaentel an einem Fuser
            world.tile(67, 68).setNet(FluxBlocks.cooler, Team.sharded, 0);
            world.tile(70, 67).setNet(FluxBlocks.cooler, Team.sharded, 0);
            world.tile(72, 70).setNet(FluxBlocks.cooler, Team.sharded, 0);
            world.tile(72, 68).setNet(FluxBlocks.cooler, Team.sharded, 0);
            zf.heat = 0.8f;
        });
        delay(3);
        runTicks(30, () -> {
            for(Point2 pt : new Point2[]{new Point2(67, 70), new Point2(67, 68), new Point2(70, 67), new Point2(72, 70), new Point2(72, 68)}){
                FuserCooler.CoolerBuild c = (FuserCooler.CoolerBuild)world.tile(pt.x, pt.y).build;
                if(c.acceptLiquid(null, Liquids.water)) c.handleLiquid(null, Liquids.water, 10f);
            }
        });
        now(() -> {
            check(zf.coolersAttached == 5 && zf.coolersActive == ThermalModel.MAX_COOLERS, "Fuenf Kuehlmaentel angeschlossen, nur " + ThermalModel.MAX_COOLERS + " wirksam (" + zf.coolersAttached + "/" + zf.coolersActive + ")");
            check(zf.coolRate <= ThermalModel.MAX_COOLING + 1e-6f && zf.coolRate > 0f, "Gesamtkuehlung bleibt begrenzt (" + String.format("%.4f", zf.coolRate) + " <= " + ThermalModel.MAX_COOLING + ")");
            findHeavy();
        });

        //============ K2b. Kuehlung gegen Ueberhitzung (echter Dauer-/Ueberlastfall am Netz-Fuser fb)
        overheatScenario("ohne Kuehlung", 0, null, true);
        overheatScenario("1 Wasser-Kuehlmantel", 1, Liquids.water, true);
        overheatScenario("2 Kryofluid-Kuehlmaentel", 2, Liquids.cryofluid, false);

        //============ K3. Vorbereiten / automatischer Betrieb
        now(() -> {
            wipeFuser(fa);
            fa.command(FluxFuser.CMD_ARM, 0, 0);
            fa.outAmount = 0; fa.outSlot = -1;
            linksBefore = node.links.size;
            feed(fa, Items.graphite, 1); setLimit(fa, Items.graphite, 2); feed(fa, Items.graphite, 1);
            feed(fa, Items.lead, 1); setLimit(fa, Items.lead, 2); feed(fa, Items.lead, 1);
        });
        delay(300);
        now(() -> {
            check(!fa.active && fa.state() == skillable.material.FuserState.PREPARE && fa.items.get(Items.graphite) == 2 && fa.items.get(Items.lead) == 2,
                "Vorbereiten: komplette Mischung liegt 5 s im Fuser, es startet nichts (Zustand VORBEREITUNG)");
            check(fa.network != null && !fa.network.sources.isEmpty(), "Waehrend der Vorbereitung bleibt die Flux-Verbindung bestehen (kein Node-Entfernen noetig)");
            int used = MaterialRegistry.used();
            usedBefore = used;
            fa.command(FluxFuser.CMD_LIMIT, fa.slotOfItem(Items.graphite), 3); //Aenderung waehrend der Vorbereitung
            fa.command(FluxFuser.CMD_LIMIT, fa.slotOfItem(Items.graphite), 2);
            check(MaterialRegistry.used() == used, "Aendern der Mengen verbraucht keine Materialplaetze (Identitaet unberuehrt)");
            fa.command(FluxFuser.CMD_ARM, 0, 1);
        });
        delay(20);
        now(() -> {
            check(!fa.active && fa.settle > 0f && fa.state() == skillable.material.FuserState.SETTLING, "Nach dem Scharfschalten und einer Aenderung laeuft zuerst die Ruhezeit (Zustand RUHEZEIT, " + (int)fa.settle + " Ticks)");
            fa.command(FluxFuser.CMD_LIMIT, fa.slotOfItem(Items.graphite), 2); //erneute Aenderung setzt die Ruhezeit zurueck
        });
        delay(40);
        now(() -> check(!fa.active, "Jede weitere Aenderung verschiebt den Start (nach 40 Ticks immer noch nicht gestartet)"));
        waitUntil("Danach startet die Verarbeitung automatisch", () -> fa.active, 200);
        now(() -> {
            sigA = fa.batchSig;
            check(fa.state() == skillable.material.FuserState.ACTIVE, "Zustand AKTIV waehrend der Charge");
            //Mischung waehrend der laufenden Reaktion aendern
            fa.command(FluxFuser.CMD_LIMIT, fa.slotOfItem(Items.graphite), 4);
            feed(fa, Items.graphite, 4); feed(fa, Items.lead, 2);
        });
        delay(20);
        now(() -> check(fa.active && fa.batchSig.equals(sigA), "Laufende Charge bleibt gesperrt: Aenderungen bereiten nur die naechste Mischung vor (" + fa.batchSig + ")"));
        waitUntil("Charge abgeschlossen", () -> !fa.active && fa.outAmount > 0, 1500);
        waitUntil("Die naechste Charge startet automatisch ohne weiteren Befehl", () -> fa.active, 400);
        now(() -> {
            check(!fa.batchSig.equals(sigA), "...und verwendet die neu vorbereitete Mischung (" + fa.batchSig + ")");
            batches = 0;
        });
        //Wiederholte Reaktionen im Dauerbetrieb ohne Eingriff am Netz
        add(() -> {
            if(fa.outAmount > 0 && !fa.active){ batches++; fa.outAmount = 0; fa.outSlot = -1; }
            if(!fa.active){ feed(fa, Items.graphite, 4); feed(fa, Items.lead, 4); }
            return batches >= 3 || ++batchTicks > 6000;
        });
        now(() -> {
            check(batches >= 3, "Wiederholte Reaktionen laufen im Dauerbetrieb automatisch (" + batches + " weitere Chargen, ohne Flux Nodes anzufassen)");
            check(node.links.size == linksBefore, "Die Flux-Links blieben dabei unveraendert (" + node.links.size + " Links)");
            //Temperaturgrenze
            wipeFuser(fa);
            fa.command(FluxFuser.CMD_ARM, 0, 1);
            fa.outAmount = 0; fa.outSlot = -1;
            feed(fa, Items.graphite, 1); setLimit(fa, Items.graphite, 2); feed(fa, Items.graphite, 1);
            fa.heat = 0.85f;
        });
        runTicks(100, () -> fa.heat = 0.85f);
        now(() -> {
            check(!fa.active && fa.state() == skillable.material.FuserState.OVERHEATED, "Ab 80 % Temperatur startet keine neue Charge (Zustand UEBERHITZT)");
            fa.heat = 0.1f;
        });
        waitUntil("Nach dem Abkuehlen startet die Charge von selbst", () -> fa.active, 300);
        now(() -> {
            //Ohne Flux
            fa.command(FluxFuser.CMD_ABORT, 0, 0);
            wipeFuser(fa);
            node.toggleLink(fa.pos());
            feed(fa, Items.graphite, 1); setLimit(fa, Items.graphite, 2); feed(fa, Items.graphite, 1);
        });
        delay(200);
        now(() -> {
            check(!fa.active && (fa.network == null || fa.network.sources.isEmpty()) && fa.items.get(Items.graphite) == 2, "Ohne Flux-Verbindung verbraucht eine fertige Mischung ihre Zutaten nicht und startet nicht");
            node.toggleLink(fa.pos());
        });
        waitUntil("Mit Flux startet die wartende Mischung sofort", () -> fa.active, 400);
        now(() -> { fa.command(FluxFuser.CMD_ABORT, 0, 0); wipeFuser(fa); fa.command(FluxFuser.CMD_ARM, 0, 0); });
    }

    int fedM, fedC;
    int linksBefore, usedBefore, batches, batchTicks;
    String sigA;
    SynthItem sCore;
    int copperBefore;
    FluxFuser.FuserBuild fuserCore;

    /** Sucht eine Mischung mit Spitze 1,3-1,6 und 14-16 Teilen (Dauer ~300 Ticks), damit Kuehlung den Ausgang entscheidet. */
    void findHeavy(){
        Object[] pool = {Liquids.oil, Liquids.slag, Liquids.water, Items.coal, Items.pyratite, Items.blastCompound, Items.sporePod, Items.sand, Items.copper};
        for(int a = 0; a < pool.length; a++) for(int b = a + 1; b < pool.length; b++) for(int c = b + 1; c < pool.length; c++)
            for(int pa = 1; pa <= 8; pa++) for(int pb = 1; pb <= 8; pb++) for(int pc = 1; pc <= 8; pc++){
                int sum = pa + pb + pc;
                if(sum < 14 || sum > 16) continue;
                float peak = MaterialProfile.of(comp(pool[a], pa, pool[b], pb, pool[c], pc)).peakHeat;
                if(peak >= 1.3f && peak <= 1.6f){
                    heavy = new Object[]{pool[a], pa, pool[b], pb, pool[c], pc};
                    MaterialProfile p = MaterialProfile.of(comp(heavy));
                    info("Ueberlast-Mischung: " + Arrays.toString(heavy) + " Spitze " + peak + ", Dauer ~" + (int)(p.synthTicksBase * (0.7f + 0.08f * sum)) + " Ticks");
                    return;
                }
            }
        check(false, "Keine Ueberlast-Mischung gefunden");
    }

    float maxHeatSeen;

    void overheatScenario(String name, int coolers, Liquid coolant, boolean expectBoom){
        now(() -> {
            if(heavy == null){ check(false, "[" + name + "] keine Mischung"); return; }
            if(!fb.isValid()){ fuserBT.setNet(FluxBlocks.fuser, Team.sharded, 0); }
        });
        delay(3);
        now(() -> {
            fb = (FluxFuser.FuserBuild)fuserBT.build;
            if(!node.links.contains(fb.pos())) node.toggleLink(fb.pos());
            wipeFuser(fb);
            for(int i = 0; i < 2; i++) world.tile(37, 52 - 2 * i).setNet(i < coolers ? FluxBlocks.cooler : Blocks.air, i < coolers ? Team.sharded : Team.derelict, 0);
            feedMix(fb, heavy);
            fb.command(FluxFuser.CMD_START, 0, 0);
            maxHeatSeen = 0f;
        });
        waitUntil("[" + name + "] Ueberlast-Charge startet (Flux im Netz)", () -> fb.active, 500);
        add(() -> {
            if(coolant != null) for(int i = 0; i < coolers; i++){
                Building c = world.tile(37, 52 - 2 * i).build;
                if(c != null && c.acceptLiquid(null, coolant)) c.handleLiquid(null, coolant, 10f);
            }
            maxHeatSeen = Math.max(maxHeatSeen, fb.heat);
            return !fb.isValid() || !fb.active;
        });
        now(() -> {
            boolean boom = !fb.isValid() || (fb.stress > 0.3f && !fb.armed);
            check(boom == expectBoom, "[" + name + "] " + (expectBoom ? "ueberhitzt und explodiert" : "uebersteht dieselbe Charge") + " (Spitzentemperatur " + String.format("%.2f", maxHeatSeen) + ")");
            if(!expectBoom) check(fb.outAmount > 0 && fb.isValid(), "[" + name + "] liefert das Ergebnis");
        });
    }

    // ------------------------------------------------------------------ H: Direktschuss (Mechanik unabhaengig vom Turm)
    void sectionH(){
        Object[][] cases = {
            {"Slug (kinetisch)", new Object[]{Items.tungsten, 5, Items.titanium, 3}},
            {"Bogen (elektrisch)", new Object[]{Items.copper, 5, Items.surgeAlloy, 3}},
            {"Glut (thermisch)", new Object[]{Items.pyratite, 4, Liquids.slag, 30}},
            {"Granate (explosiv)", new Object[]{Items.blastCompound, 5}},
            {"Raute (Flux)", new Object[]{Items.thorium, 3, Items.phaseFabric, 3}},
        };
        for(Object[] cs : cases){
            final String name = (String)cs[0];
            final SynthItem[] item = {null};
            final float[] hp0 = {0f};
            final int[] hits0 = {0};
            add(() -> {
                item[0] = MaterialRegistry.obtain(comp((Object[])cs[1]));
                if(enemy != null) enemy.remove();
                enemy = UnitTypes.dagger.create(Team.crux);
                enemy.set(turret.x + 104f, turret.y);
                enemy.add();
                enemy.health = enemy.maxHealth = 1_000_000f;
                enemy.type.speed = 0f;
                MatBullet bt = ProjectileFactory.main[item[0].slot];
                hp0[0] = enemy.health; hits0[0] = bt.hits;
                float dist = Mathf.dst(turret.x, turret.y, enemy.x, enemy.y);
                float lifeScl = bt.scaleLife ? Mathf.clamp(dist / bt.range, 0.1f, 1f) : 1f;
                for(int i = 0; i < 3; i++) bt.create(turret, Team.sharded, turret.x, turret.y, 0f, -1f, 1f, lifeScl, null);
                return true;
            });
            for(int t = 0; t < 150; t++) add(() -> true);
            add(() -> {
                MatBullet bt = ProjectileFactory.main[item[0].slot];
                info(name + ": speed=" + bt.speed + " lifetime=" + bt.lifetime + " range=" + bt.range + " weaveMag=" + bt.weaveMag + " scaleLife=" + bt.scaleLife + " collides=" + bt.collides + " despawnHit=" + bt.despawnHit);
                check(bt.hits - hits0[0] >= 1, "[" + name + "] Direktschuss (3 Geschosse auf 13 Kacheln) erzeugt Treffer (" + (bt.hits - hits0[0]) + " Treffer-Aufrufe, " + (int)(hp0[0] - enemy.health) + " Schaden)");
                enemy.remove(); enemy = null;
                return true;
            });
        }
    }
}
