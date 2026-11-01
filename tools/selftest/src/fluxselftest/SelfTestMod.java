package fluxselftest;

import arc.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.world.*;
import skillable.blocks.*;
import skillable.content.*;
import skillable.flux.*;
import skillable.production.*;

import java.io.*;
import java.util.*;

import static mindustry.Vars.*;

/** Laeuft NUR im Headless-Server. Prueft das echte Zusammenspiel mit der Engine und beendet danach das Spiel. */
public class SelfTestMod extends Mod{
    int pass, fail, tick, phase;
    Tile turretTile, convTile, nodeTile, powerTile;
    SkillableTurret.SkillableBuild turret;
    FluxConverter.ConverterBuild conv;
    FluxNode.NodeBuild node;
    Unit enemy;
    float grantedSum, fullRange;
    int powerShots;
    int firedBefore;
    boolean started;

    void check(boolean c, String name){
        if(c) pass++; else fail++;
        Log.info("[SELFTEST] @ @", c ? "OK  " : "FAIL", name);
    }

    public SelfTestMod(){
        Events.on(ServerLoadEvent.class, e -> start());
    }

    void start(){
        Log.info("[SELFTEST] Start");
        try{
            world.loadGenerator(80, 80, tiles -> tiles.each((x, y) -> tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air))));
            state.rules.infiniteResources = true;
            state.rules.waves = false;
            state.rules.waveTeam = Team.crux;
            state.rules.fog = false;
            state.set(mindustry.core.GameState.State.playing);
            logic.play();
            Events.fire(new WorldLoadEvent());
            powerTile = world.tile(31, 30); //direkt neben dem 2x2-Konverter (32..33, 30..31)
            world.tile(10, 10).setNet(Blocks.coreShard, Team.sharded, 0); //verhindert Game Over (pausiert sonst die Simulation)
            //Blocks setzen
            powerTile.setNet(Blocks.powerSource, Team.sharded, 0);
            convTile = world.tile(32, 30);
            convTile.setNet(FluxBlocks.stabilizer, Team.sharded, 0);
            nodeTile = world.tile(36, 30);
            nodeTile.setNet(FluxBlocks.fluxNode, Team.sharded, 0);
            turretTile = world.tile(41, 30);
            turretTile.setNet(FluxBlocks.skillableTurret, Team.sharded, 0);
            started = true;
            Events.run(Trigger.update, this::step);
        }catch(Throwable t){
            Log.err("[SELFTEST] Setup-Fehler", t);
            finish(1);
        }
    }

    float totalGranted;
    int ticksWithGrant;

    void step(){
        if(!started) return;
        tick++;
        if(turret != null) { totalGranted += turret.granted; if(turret.granted > 0) ticksWithGrant++; }
        switch(phase){
            case 0 -> { if(tick == 5){
                turret = (SkillableTurret.SkillableBuild)turretTile.build;
                conv = (FluxConverter.ConverterBuild)convTile.build;
                node = (FluxNode.NodeBuild)nodeTile.build;
                check(turret != null && conv != null && node != null, "Bloecke platziert");
                //Verbindungen manuell sicherstellen (placed() autolinkt nur bei Reihenfolge)
                if(!node.links.contains(conv.pos())) node.toggleLink(conv.pos());
                if(!node.links.contains(turret.pos())) node.toggleLink(turret.pos());
                check(node.links.size == 2, "Node hat 2 gueltige Links");
                phase = 1; tick = 0;
            }}
            case 1 -> { if(tick == 60){
                check(turret.network != null && turret.network == conv.network, "Turm und Konverter im selben Netz");
                check(turret.granted == 0f && turret.lastDemand == 0f, "Ohne Material/Ziel: kein Bedarf, kein Flux");
                check(!turret.acceptItem(null, Items.scrap), "Nicht unterstuetztes Material abgelehnt");
                turret.handleItem(null, Items.copper);
                check(turret.material == Items.copper && turret.materialCount == 1, "Kupfer akzeptiert");
                check(!turret.acceptItem(null, Items.graphite), "Zweites Material (Graphit) abgelehnt, solange Kupfer im Slot");
                for(int i = 0; i < 15; i++) turret.handleItem(null, Items.copper);
                enemy = UnitTypes.dagger.create(Team.crux);
                enemy.set(turret.x + 8 * 14, turret.y);
                enemy.add();
                enemy.health = enemy.maxHealth = 100000f;
                enemy.type.speed = 0f; //stehenbleiben
                phase = 2; tick = 0; totalGranted = 0; ticksWithGrant = 0;
                firedBefore = turret.materialCount * 100 + turret.shotBuffer;
            }}
            case 2 -> { if(tick == 5){
                //Ohne Strom? Powersource laeuft -> pruefen
            }
            if(tick == 300){
                int left = turret.materialCount * 100 + turret.shotBuffer;
                check(conv.power.status > 0.9f, "Konverter hat Strom (status " + conv.power.status + ")");
                check(ticksWithGrant > 100, "Turm erhaelt Flux mit Ziel in Reichweite (" + ticksWithGrant + " Ticks)");
                check(left < firedBefore, "Turm hat geschossen (Material " + firedBefore + " -> " + left + ")");
                check(Math.abs(turret.granted - turret.lastDemand) < 1e-3f || turret.granted <= conv.profile().throughput + 1e-3f, "Zuteilung <= Konverter-Durchsatz");
                check(enemy.health < enemy.maxHealth, "Ziel wurde getroffen (HP " + enemy.health + ")");
                //Strom entfernen
                powerTile.setNet(Blocks.air, Team.derelict, 0);
                phase = 3; tick = 0;
                firedBefore = turret.materialCount * 100 + turret.shotBuffer;
            }}
            case 3 -> { if(tick == 120){
                int left = turret.materialCount * 100 + turret.shotBuffer;
                check(conv.power.status < 0.05f, "Strom entfernt -> Konverter status " + conv.power.status);
                check(turret.granted < 1e-4f, "Ohne Strom kein Flux am Turm");
                //Es koennte bis zu ~60 Ticks (Stromgraph) noch Restschuesse gegeben haben, danach nicht mehr
                int mid = left;
                turret.set(turret.x, turret.y); // no-op
                Log.info("[SELFTEST] Materialstand nach Stromverlust: @", mid);
                firedBefore = mid;
                phase = 4; tick = 0;
            }}
            case 4 -> { if(tick == 120){
                int left = turret.materialCount * 100 + turret.shotBuffer;
                check(left == firedBefore, "Ohne Strom schiesst der Turm nicht (Material unveraendert " + left + ")");
                //Strom wieder an, aber Node-Verbindung zum Turm trennen
                powerTile.setNet(Blocks.powerSource, Team.sharded, 0);
                fullRange = ((SkillableTurret)turret.block).range + turret.mode().bullet.rangeChange;
                node.toggleLink(turret.pos());
                check(node.links.size == 1, "Link zum Turm getrennt");
                phase = 5; tick = 0;
                firedBefore = turret.materialCount * 100 + turret.shotBuffer;
            }}
            case 5 -> {
                if(tick == 200){
                    int left = turret.materialCount * 100 + turret.shotBuffer;
                    check(turret.network != conv.network, "Getrennte Netze nach Unlink");
                    check(turret.granted == 0f, "Turm ohne Verbindung erhaelt 0 Flux");
                    check(left == firedBefore, "Turm ohne Flux-Verbindung UND ohne Normalstrom schiesst nicht");
                    check(!turret.fluxAvailable(), "Ohne Netz: Flux nicht verfuegbar");
                    //Normalstrom direkt neben dem Turm (3x3 bei 40..42, 29..31) -> Stromquelle bei (39,30)
                    world.tile(39, 30).setNet(Blocks.powerSource, Team.sharded, 0);
                    shots0 = turret.totalShots;
                    enemy.set(turret.x, turret.y + 64f); //innerhalb der reduzierten Normalstrom-Reichweite
                }
                if(tick == 260){
                    check(turret.power.status > 0.9f, "Turm hat Normalstrom (" + turret.power.status + ")");
                    check(turret.powerOk() && !turret.fluxAvailable(), "Normalstrom-Betrieb aktiv, Flux nicht");
                    check(turret.range() < fullRange * 0.8f, "Normalstrom: Reichweite reduziert (" + turret.range() + " < " + fullRange + ")");
                    check(turret.granted == 0f, "Im Normalstrom-Betrieb kein Flux zugeteilt (nie beides)");
                    check(turret.block.consPower.requestedPower(turret) > 0f || turret.target == null, "Turm fordert im Normalstrom-Betrieb Strom an (" + turret.block.consPower.requestedPower(turret) + ")");
                    int shots = turret.totalShots - shots0;
                    powerShots = shots;
                    check(shots > 0, "Normalstrom: Turm schiesst (" + shots + " Schuesse in 260 Ticks)");
                    check(enemy.health < enemy.maxHealth, "Normalstrom-Schuesse treffen");
                    //Hochstufen-Modus ohne Flux: darf nicht feuern
                    turret.unloadMaterial();
                    for(int i = 0; i < 6; i++) turret.handleItem(null, Items.blastCompound);
                    shots0 = turret.totalShots;
                }
                if(tick == 380){
                    check(turret.mode() != null && !turret.mode().powerCapable, "Catastrophic ist nur mit Flux nutzbar");
                    check(turret.totalShots == shots0, "Catastrophic ohne Flux schiesst nicht");
                    turret.unloadMaterial();
                    for(int i = 0; i < 16; i++) turret.handleItem(null, Items.copper);
                    world.tile(39, 30).setNet(Blocks.air, Team.derelict, 0);
                    node.toggleLink(turret.pos());
                    phase = 6; tick = 0;
                }
            }
            case 6 -> { if(tick == 60){
                check(turret.network == conv.network, "Neu verbunden: gleiches Netz");
                check(turret.fluxAvailable() && !turret.powerOk(), "Wieder im Flux-Betrieb: Normalstrom-Betrieb aus");
                check(turret.block.consPower.requestedPower(turret) == 0f, "Im Flux-Betrieb kein Normalstrom-Bedarf (nie beides)");
                //Roundtrip Speichern/Laden
                try{
                    turret.overclockCooldown = 123f;
                    conv.instability = 0.5f; conv.lockout = 200f; conv.recovery = 300f; conv.overdrive = true;
                    byte[] a = save(turret), b = save(conv), c = save(node);
                    turret.materialCount = 0; turret.material = null; turret.overclockCooldown = 0;
                    conv.instability = 0; conv.lockout = 0; conv.recovery = 0; conv.overdrive = false;
                    int nlinks = node.links.size; node.links.clear();
                    load(turret, a); load(conv, b); load(node, c);
                    check(turret.material == Items.copper && turret.materialCount > 0, "Laden stellt Material-Modus wieder her");
                    check(Math.abs(turret.overclockCooldown - 123f) < 0.01f, "Overclock-Abklingzeit gespeichert");
                    check(Math.abs(conv.instability - 0.5f) < 1e-4f && conv.lockout > 199f && conv.recovery > 299f && conv.overdrive, "Konverter-Zustand gespeichert");
                    check(node.links.size == nlinks, "Node-Links gespeichert (" + node.links.size + ")");
                    conv.lockout = 0; conv.recovery = 0; conv.instability = 0; conv.overdrive = false;
                    byte[] d = save(conv);
                    conv.lockout = 0.2f; //abgelaufen -> darf nicht als aktiver Fehler gespeichert werden
                    d = save(conv); conv.lockout = 99f; load(conv, d);
                    check(conv.lockout == 0f, "Abgelaufene Abschaltung wird nicht als aktiv gespeichert");
                }catch(Throwable t){ Log.err(t); check(false, "Save/Load Exception " + t); }
                phase = 7; tick = 0;
            }}
            case 7 -> { if(tick == 5){
                //Node zerstoeren -> keine ungueltigen Verbindungen
                nodeTile.setNet(Blocks.air, Team.derelict, 0);
            }
            if(tick == 20){
                check(conv.network != turret.network || conv.network == null, "Nach Node-Entfernung getrennte Netze");
                //Kollaps provozieren: Overdrive + riesiger Bedarf nicht moeglich ohne Node -> direkt kicken
                phase = 8; tick = 0;
            }}
            case 8 -> { if(tick == 5){
                //neuer Node, Konverter in Netz, Kollaps ueber kick
                nodeTile.setNet(FluxBlocks.fluxNode, Team.sharded, 0);
                node = (FluxNode.NodeBuild)nodeTile.build;
                node.toggleLink(conv.pos());
                node.toggleLink(turret.pos());
            }
            if(tick == 30){
                FluxNetwork n = conv.network;
                check(n != null && n == turret.network, "Netz neu aufgebaut");
                n.kick(1.5f, 1f);
                check(n.offline && conv.lockout > 0f, "Kick fuehrt zum Kollaps/EMP");
                phase = 9; tick = 0;
            }}
            case 9 -> { if(tick == 100){
                check(turret.granted == 0f, "Waehrend EMP kein Flux am Turm");
            }
            if(tick == 450){
                check(conv.lockout <= 0f && conv.recovery > 0f, "Nach Abschaltung: Erholungsphase");
                phase = 10; tick = 0;
            }}
            case 10 -> { if(tick == 60){
                check(turret.granted > 0f, "Nach Wiederanlauf wieder Flux (" + turret.granted + ")");
                phase = 11; tick = 0;
            }}
            case 11 -> {
                if(tick == 1){
                    //Zusatzversorgung: zwei Quantenkonverter mit Overdrive, eigener Node
                    world.tile(52, 30).setNet(Blocks.powerSource, Team.sharded, 0);
                    world.tile(50, 30).setNet(FluxBlocks.quantum, Team.sharded, 0);
                    world.tile(52, 36).setNet(Blocks.powerSource, Team.sharded, 0);
                    world.tile(50, 36).setNet(FluxBlocks.quantum, Team.sharded, 0);
                    world.tile(46, 33).setNet(FluxBlocks.fluxNode, Team.sharded, 0);
                }
                if(tick == 5){
                    FluxNode.NodeBuild n2 = (FluxNode.NodeBuild)world.tile(46, 33).build;
                    q1 = (FluxConverter.ConverterBuild)world.tile(50, 30).build;
                    q2 = (FluxConverter.ConverterBuild)world.tile(50, 36).build;
                    n2.toggleLink(q1.pos()); n2.toggleLink(q2.pos()); n2.toggleLink(turret.pos());
                    q1.overdrive = true; q2.overdrive = true;
                    if(enemy != null) enemy.remove();
                    check(n2.links.size == 3, "Zweiter Node mit 3 Links");
                }
                if(tick == 560){
                    check(turret.network != null && turret.network.sources.size() == 3, "Netz mit 3 Konvertern (" + (turret.network == null ? -1 : turret.network.sources.size()) + ")");
                    check(q1.power.status > 0.9f && q2.power.status > 0.9f, "Quantenkonverter haben Strom");
                    check(turret.network.lastCapacity > 14f, "Netzkapazitaet > Overclock-Bedarf (" + turret.network.lastCapacity + ")");
                    phase = 12; tick = 0; modeIdx = 0;
                }
            }
            case 12 -> {
                var modes = skillable.modes.FluxModes.all;
                if(modeIdx >= modes.size){ phase = 13; tick = 0; return; }
                var m = modes.get(modeIdx);
                if(tick == 1){
                    turret.unloadMaterial();
                    check(turret.material == null, "[" + m.id + "] Slot leer nach Entladen -> Moduswechsel moeglich");
                    for(int i = 0; i < 20; i++) turret.handleItem(null, m.item);
                    check(turret.mode() == m, "[" + m.id + "] Modus durch Material gesetzt");
                    for(var other : skillable.modes.FluxModes.all) if(other != m) check(!turret.acceptItem(null, other.item), "[" + m.id + "] lehnt " + other.id + "-Material ab");
                    float tx = turret.x, ty = turret.y;
                    e1 = spawn(tx, ty + 96f); e2 = spawn(tx, ty + 96f + 40f); e3 = spawn(tx + 70f, ty + 96f);
                    //Rail: Durchschlag wird durch die Gesundheit des ersten Ziels abgezogen -> schwaches Ziel vorne
                    if(m.id.equals("rail")) e1.health = e1.maxHealth = 60f;
                    shots0 = turret.totalShots; sawZone = false; sawMissile = false; maxGrant = 0f; maxInstab = 0f;
                }
                if(tick > 1){
                    maxGrant = Math.max(maxGrant, turret.granted);
                    maxInstab = Math.max(maxInstab, turret.netInstability());
                    for(Bullet b : Groups.bullet){
                        if(b.type instanceof skillable.modes.FluxBullets.FireZoneBulletType) sawZone = true;
                        if(b.type instanceof mindustry.entities.bullet.MissileBulletType && b.type.homingPower > 0) sawMissile = true;
                    }
                }
                if(tick == 420){
                    int shots = turret.totalShots - shots0;
                    shotsPerMode.put(m.id, shots);
                    float d1 = e1.maxHealth - e1.health, d2 = e2.maxHealth - e2.health, d3 = e3.maxHealth - e3.health;
                    Log.info("[SELFTEST] [@] shots=@ maxGrant=@ dmg1=@ dmg2(hinter 1)=@ dmg3(neben 1)=@ instab=@", m.id, shots, maxGrant, d1, d2, d3, maxInstab);
                    check(shots > 0, "[" + m.id + "] feuert mit Flux (" + shots + " Schuesse)");
                    check(maxGrant >= m.fluxPerTick * 0.5f, "[" + m.id + "] erhaelt mindestens halben Bedarf (" + maxGrant + " >= " + m.fluxPerTick * 0.5f + ")");
                    check(d1 > 0f, "[" + m.id + "] verursacht Schaden");
                    switch(m.id){
                        case "rapid" -> {}
                        case "rail" -> check(d2 > 0f, "[rail] durchschlaegt: Ziel hinter dem ersten wird getroffen");
                        case "incendiary" -> check(sawZone, "[incendiary] erzeugt Brandzone");
                        case "smart" -> check(sawMissile, "[smart] feuert lenkfaehige Geschosse");
                        case "catastrophic" -> check(d3 > 0f || d2 > 0f, "[catastrophic] Flaechenschaden trifft Nachbarziele");
                        case "reality" -> check(d2 > 0f, "[reality] durchdringt Ziele");
                        case "conductor" -> check(d1 > 0f && (d2 > 0f || d3 > 0f || d1 > 20f), "[conductor] Kettenblitze/Durchschlag wirken");
                        case "ferrite" -> check(d1 > 60f, "[ferrite] schwerer Einzelschaden (" + d1 + ")");
                        case "synaptic" -> check(sawMissile, "[synaptic] feuert lenkende Schwarmraketen");
                        case "pyroglass" -> check(d2 > 0f || d3 > 0f || d1 > 30f, "[pyroglass] Splitter/Flaeche wirken");
                        case "storm" -> check(e1.hasEffect(StatusEffects.electrified) || d3 > 0f || d2 > 0f, "[storm] Elektro/Kettenblitz-Wirkung");
                        default -> {}
                    }
                    e1.remove(); e2.remove(); e3.remove();
                    modeIdx++; tick = 0;
                }
            }
            case 13 -> {
                if(tick == 1){
                    Log.info("[SELFTEST] Schuesse je Modus: @", shotsPerMode);
                    check(shotsPerMode.get("rapid") > shotsPerMode.get("rail") * 3, "Rapid feuert deutlich schneller als Rail");
                    check(shotsPerMode.get("catastrophic") < shotsPerMode.get("smart"), "Catastrophic feuert langsamer als Smart");
                    //Overclock vorbereiten
                    turret.unloadMaterial();
                    check(turret.overclockCooldown == 0f, "Overclock-Abklingzeit zurueckgesetzt");
                    check(turret.material == null, "Kein Material");
                    turret.requestOverclock();
                    check(!turret.overclockArmed, "Overclock ohne Material nicht moeglich");
                    for(int i = 0; i < 6; i++) turret.handleItem(null, Items.copper);
                    turret.requestOverclock();
                    check(!turret.overclockArmed, "Overclock ohne Ziel nicht moeglich");
                    e1 = spawn(turret.x + 3000f * 0 , turret.y + 8f * 60f); //weit weg (60 Kacheln) - mapweit
                    e1.health = e1.maxHealth = 100000f;
                    mat0 = turret.materialCount;
                    turret.requestOverclock();
                    check(turret.overclockArmed, "Overclock mit Material, Ziel und Netzkapazitaet scharf");
                }
                if(tick == 3){
                    check(!turret.overclockArmed && turret.overclockCooldown > 0f, "Overclock hat ausgeloest, Abklingzeit laeuft");
                    check(turret.materialCount == mat0 - SkillableTurret.OVERCLOCK_ITEMS, "Overclock verbraucht Material (" + mat0 + " -> " + turret.materialCount + ")");
                    check(e1.health < e1.maxHealth - 500f, "Mapweiter Schlag trifft entferntes Ziel (HP " + e1.health + ")");
                    check(turret.network.offline, "Netzstoerung (EMP) nach Overclock");
                    check(turret.granted == 0f, "Waehrend der Stoerung kein Flux");
                    turret.requestOverclock();
                    check(!turret.overclockArmed, "Zweiter Overclock sofort nicht moeglich (Abklingzeit/Netz offline)");
                }
                if(tick == 900){
                    check(!turret.network.offline, "Netz nach Stoerung wieder online");
                    //---- Erekir
                    var old = state.rules.planet;
                    check(!skillable.modes.FluxModes.supports(Items.beryllium), "Serpulo: Beryllium wird nicht akzeptiert");
                    check(FluxBlocks.fluxNode.buildVisibility.visible() && !FluxBlocks.fluxNodeErekir.buildVisibility.visible(), "Serpulo: nur Serpulo-Varianten sichtbar");
                    state.rules.planet = Planets.erekir;
                    check(skillable.modes.FluxModes.get(Items.beryllium) != null && skillable.modes.FluxModes.get(Items.beryllium).id.equals("rapid"), "Erekir: Beryllium = Rapid");
                    check(skillable.modes.FluxModes.get(Items.oxide).id.equals("incendiary"), "Erekir: Oxid = Incendiary");
                    check(skillable.modes.FluxModes.get(Items.thorium).id.equals("catastrophic"), "Erekir: Thorium = Catastrophic");
                    check(skillable.modes.FluxModes.get(Items.graphite).id.equals("rail") && skillable.modes.FluxModes.get(Items.surgeAlloy).id.equals("storm"), "Erekir: gemeinsame Materialien bleiben");
                    check(!FluxBlocks.fluxNode.buildVisibility.visible() && FluxBlocks.fluxNodeErekir.buildVisibility.visible(), "Erekir: nur Erekir-Varianten sichtbar");
                    boolean onlyErekirItems = true;
                    for(var blk : new mindustry.world.Block[]{FluxBlocks.fluxNodeErekir, FluxBlocks.stabilizerErekir, FluxBlocks.acceleratorErekir, FluxBlocks.quantumErekir, FluxBlocks.skillableTurretErekir}){
                        for(var st : blk.requirements) if(!Items.erekirItems.contains(st.item)){ onlyErekirItems = false; Log.info("[SELFTEST] Erekir-Block @ braucht Nicht-Erekir-Item @", blk.name, st.item.name); }
                    }
                    check(onlyErekirItems, "Erekir-Bloecke brauchen nur Erekir-Rohstoffe");
                    boolean onlySerpuloItems = true;
                    for(var blk : new mindustry.world.Block[]{FluxBlocks.fluxNode, FluxBlocks.stabilizer, FluxBlocks.accelerator, FluxBlocks.quantum, FluxBlocks.skillableTurret}){
                        for(var st : blk.requirements) if(!Items.serpuloItems.contains(st.item)) onlySerpuloItems = false;
                    }
                    check(onlySerpuloItems, "Serpulo-Bloecke brauchen nur Serpulo-Rohstoffe");
                    //Erekir-Variante platzieren und im Netz benutzen
                    world.tile(60, 60).setNet(Blocks.powerSource, Team.sharded, 0);
                    world.tile(61, 60).setNet(FluxBlocks.stabilizerErekir, Team.sharded, 0);
                    world.tile(65, 60).setNet(FluxBlocks.fluxNodeErekir, Team.sharded, 0);
                    world.tile(69, 60).setNet(FluxBlocks.skillableTurretErekir, Team.sharded, 0);
                    erekirPlaced = true;
                    phase = 14; tick = 0;
                    state.rules.planet = old;
                }
            }
            case 14 -> {
                if(tick == 10){
                    var et = (SkillableTurret.SkillableBuild)world.tile(69, 60).build;
                    var ec = (FluxConverter.ConverterBuild)world.tile(61, 60).build;
                    var en = (FluxNode.NodeBuild)world.tile(65, 60).build;
                    check(et != null && ec != null && en != null, "Erekir-Bloecke platziert");
                    if(!en.links.contains(ec.pos())) en.toggleLink(ec.pos());
                    if(!en.links.contains(et.pos())) en.toggleLink(et.pos());
                    check(en.links.size == 2, "Erekir-Node hat 2 Links");
                    et.handleItem(null, Items.graphite);
                }
                if(tick == 120){
                    var et = (SkillableTurret.SkillableBuild)world.tile(69, 60).build;
                    var ec = (FluxConverter.ConverterBuild)world.tile(61, 60).build;
                    check(et.network != null && et.network == ec.network, "Erekir: Turm und Konverter im selben Netz");
                    check(ec.power.status > 0.9f, "Erekir: Konverter hat Strom");
                    check(ec.nominalOutput() > 0.9f, "Erekir: Konverter liefert Flux-Kapazitaet (" + ec.nominalOutput() + ")");
                    check(et.fluxAvailable(), "Erekir: Turm sieht verfuegbaren Flux (Rail 1.6 * 0.5 <= 1.0)");
                    phase = 15; tick = 0;
                }
            }
            case 15 -> {
                if(tick == 1){
                    fuserTile = world.tile(44, 30);
                    fuserTile.setNet(FluxBlocks.fuser, Team.sharded, 0);
                }
                if(tick == 5){
                    fz = (FluxFuser.FuserBuild)fuserTile.build;
                    n2b = (FluxNode.NodeBuild)world.tile(46, 33).build;
                    check(fz != null, "Fuser platziert");
                    fz.setRecipe(0); //Regression: dieser Block prueft den KLASSIK-Modus (neue Fuser starten im freien Modus)
                    if(!n2b.links.contains(fz.pos())) n2b.toggleLink(fz.pos());
                    //Texte vollstaendig?
                    var missing = new ArrayList<String>();
                    for(var b : new mindustry.world.Block[]{FluxBlocks.fluxNode, FluxBlocks.stabilizer, FluxBlocks.accelerator, FluxBlocks.quantum, FluxBlocks.skillableTurret, FluxBlocks.fuser,
                        FluxBlocks.fluxNodeErekir, FluxBlocks.stabilizerErekir, FluxBlocks.acceleratorErekir, FluxBlocks.quantumErekir, FluxBlocks.skillableTurretErekir, FluxBlocks.fuserErekir}){
                        if(!Core.bundle.has("block." + b.name + ".name") || !Core.bundle.has("block." + b.name + ".description")) missing.add(b.name);
                    }
                    for(var it : new mindustry.type.Item[]{FluxItems.carbonConductor, FluxItems.ferriteComposite, FluxItems.synapticAlloy, FluxItems.pyroglass}){
                        if(!Core.bundle.has("item." + it.name + ".name") || !Core.bundle.has("item." + it.name + ".description")) missing.add(it.name);
                    }
                    for(var m : skillable.modes.FluxModes.all) if(!Core.bundle.has("mode." + m.id)) missing.add("mode." + m.id);
                    check(missing.isEmpty(), "Alle Bloecke/Items/Modi haben Name und Beschreibung " + missing);
                    var names = new HashSet<String>();
                    var ids = new HashSet<Integer>();
                    for(var it : new mindustry.type.Item[]{FluxItems.carbonConductor, FluxItems.ferriteComposite, FluxItems.synapticAlloy, FluxItems.pyroglass}){ names.add(it.name); ids.add((int)it.id); }
                    check(names.size() == 4 && ids.size() == 4, "Vier eigenstaendige Item-Typen");
                    check(skillable.production.FluxRecipes.all.size == 4, "Vier Rezepte registriert");
                }
                if(tick == 30){
                    check(fz.network != null && fz.network == turret.network, "Fuser im selben Flux-Netz wie Turm");
                    check(fz.recipe().id.equals("carbon-conductor"), "Standardrezept: Carbon Conductor");
                    check(fz.lastDemand == 0f && fz.progress == 0f, "Ohne Zutaten: kein Flux-Bedarf, kein Fortschritt");
                    check(!fz.acceptItem(null, Items.sand) && fz.acceptItem(null, Items.copper) && fz.acceptItem(null, Items.coal), "Fuser nimmt nur Zutaten des Rezepts an");
                    check(fz.acceptLiquid(null, Liquids.water) && fz.acceptLiquid(null, Liquids.cryofluid) && !fz.acceptLiquid(null, Liquids.oil), "Fuser nimmt nur Zutat- und Kuehlfluessigkeit an");
                    fz.items.add(Items.copper, 2); fz.items.add(Items.coal, 2);
                }
                if(tick == 120){
                    check(fz.progress == 0f && fz.lastDemand == 0f, "Ohne Fluessigkeit keine Produktion");
                    fz.liquids.add(Liquids.water, 20f);
                    n2b.toggleLink(fz.pos());
                }
                if(tick == 280){
                    check(fz.progress == 0f && fz.granted == 0f && fz.items.get(Items.copper) == 2, "Ohne Flux-Verbindung pausiert die Produktion");
                    n2b.toggleLink(fz.pos());
                    maxFzGrant = 0f;
                }
                if(tick > 280){ maxFzGrant = Math.max(maxFzGrant, fz.granted); sawFzWork |= fz.working; }
                if(tick == 560){
                    check(maxFzGrant > 0.5f && sawFzWork, "Mit Flux-Verbindung arbeitet der Fuser (Flux " + maxFzGrant + ")");
                    check(fz.items.get(FluxItems.carbonConductor) >= 1, "Carbon Conductor wurde hergestellt");
                    check(fz.items.get(Items.copper) == 0 && fz.items.get(Items.coal) == 0, "Zutaten wurden verbraucht");
                    check(fz.liquids.get(Liquids.water) < 20f, "Wasser wurde verbraucht");
                    check(fz.granted == 0f || fz.granted <= fz.lastDemand + 1e-3f, "Flux nur im Bedarf zugeteilt, nicht gespeichert");
                    try{
                        fz.setRecipe(2); fz.progress = 0.5f;
                        byte[] a = save(fz);
                        fz.progress = 0f; fz.setRecipe(0);
                        load(fz, a);
                        check(fz.recipeIdx == 2 && Math.abs(fz.progress - 0.5f) < 1e-4f, "Fuser: Rezept und Fortschritt gespeichert");
                    }catch(Throwable t){ Log.err(t); check(false, "Fuser Save/Load " + t); }
                    fz.setRecipe(0); fz.progress = 0f;
                    phase = 16; tick = 0; recIdx = 0;
                }
            }
            case 16 -> {
                var list = skillable.production.FluxRecipes.all;
                if(recIdx >= list.size){ phase = 17; tick = 0; return; }
                var r = list.get(recIdx);
                if(tick == 1){
                    if(turret.material != Items.copper){ turret.unloadMaterial(); turret.handleItem(null, Items.copper); } //Slot besetzt -> Ausgabe bleibt im Fuser
                    fz.setRecipe(recIdx);
                    fz.items.clear(); fz.liquids.clear(); fz.progress = 0f;
                    for(var st : r.inputs(false)) fz.items.add(st.item, st.amount * 3);
                    fz.liquids.add(r.liquid, 25f);
                    if(recIdx == 0) fz.liquids.add(r.coolant, 10f);
                    sawCool = false;
                }
                if(tick > 1) sawCool |= fz.cooling;
                if(tick == 420){
                    check(fz.recipe() == r, "[" + r.id + "] Rezept gewaehlt");
                    check(fz.items.get(r.output) >= r.outputAmount, "[" + r.id + "] stellt " + r.output.localizedName + " her (" + fz.items.get(r.output) + ")");
                    boolean consumed = true;
                    for(var st : r.inputs(false)) if(fz.items.get(st.item) > st.amount * 3 - st.amount) consumed = false;
                    check(consumed, "[" + r.id + "] verbraucht Zutaten");
                    if(recIdx == 0) check(sawCool, "[carbon-conductor] Kuehlmittel (Kryofluid) wird aktiv genutzt");
                    recIdx++; tick = 0;
                }
            }
            case 17 -> {
                if(tick == 1){
                    fz.items.clear(); fz.liquids.clear(); fz.setRecipe(0); fz.progress = 0f;
                    turret.unloadMaterial();
                    for(var st : skillable.production.FluxRecipes.all.get(0).inputs(false)) fz.items.add(st.item, st.amount * 3);
                    fz.liquids.add(Liquids.water, 25f);
                }
                if(tick == 500){
                    check(turret.material == FluxItems.carbonConductor && turret.materialCount >= 1, "Fuser liefert Carbon Conductor direkt als Munition an den Turm (" + turret.materialCount + ")");
                    check(turret.mode() != null && turret.mode().id.equals("conductor"), "Turm erkennt Conductor-Modus");
                    check(!turret.acceptItem(null, Items.copper), "Danach lehnt der Turm andere Materialien ab");
                    turret.unloadMaterial();
                    phase = 18; tick = 0;
                }
            }
            case 18 -> {
                if(tick == 1){
                    world.tile(9, 50).setNet(Blocks.powerSource, Team.sharded, 0);
                    world.tile(10, 50).setNet(FluxBlocks.stabilizer, Team.sharded, 0);
                    world.tile(14, 50).setNet(FluxBlocks.fluxNode, Team.sharded, 0);
                    world.tile(18, 50).setNet(FluxBlocks.fuser, Team.sharded, 0);
                }
                if(tick == 5){
                    conv2 = (FluxConverter.ConverterBuild)world.tile(10, 50).build;
                    fz2 = (FluxFuser.FuserBuild)world.tile(18, 50).build;
                    var nd = (FluxNode.NodeBuild)world.tile(14, 50).build;
                    nd.toggleLink(conv2.pos()); nd.toggleLink(fz2.pos());
                    fz2.setRecipe(1); //Ferrite: Bedarf 1.2 > Stabilizer-Kapazitaet 1.0 -> Ueberlast
                    check(conv2.acceptLiquid(null, Liquids.water) && conv2.acceptLiquid(null, Liquids.cryofluid) && !conv2.acceptLiquid(null, Liquids.oil), "Konverter nimmt nur Kuehlmittel an");
                }
                if(tick > 5){
                    fz2.items.set(Items.scrap, 9); fz2.items.set(Items.graphite, 6); fz2.liquids.set(Liquids.oil, 25f);
                    maxInst2 = Math.max(maxInst2, conv2.instability);
                    int st = conv2.lockout > 0f ? 3 : InstabilityModel.stage(conv2.instability);
                    if(st >= 0 && !stageSeen[st]){ stageSeen[st] = true; stageTick[st] = tick; Log.info("[SELFTEST] Instabilitaet erreicht Stufe @ nach @ Ticks (Wert @)", st, tick, conv2.instability); }
                    if(conv2.lockout > 0f) sawLock = true;
                    if(sawLock && conv2.lockout <= 0f && conv2.recovery > 0f) sawRec = true;
                    if(tick == 200){
                        check(conv2.instability > 0.05f, "Instabilitaet steigt unter Last tatsaechlich (" + conv2.instability + ")");
                        check(conv2.causesText().contains("%") && conv2.breakdown.target > 0f, "Ursachen-Anzeige zeigt Zielwert " + conv2.breakdown.target);
                    }
                    if(sawRec && !coolTested){
                        coolTested = true;
                        conv2.liquids.add(Liquids.cryofluid, 20f);
                        coolTick = tick;
                    }
                    if(coolTested && tick == coolTick + 90){
                        check(conv2.coolFactor < 0.6f, "Kryofluid-Kuehlung wirkt auf den Zielwert (Faktor " + conv2.coolFactor + ")");
                    }
                    if(coolTested && tick == coolTick + 100 || tick == 4500){
                        check(stageSeen[1] && stageSeen[2] && stageSeen[3], "Stufen WARNUNG, KRITISCH und KOLLAPS wurden alle erreicht");
                        check(stageTick[1] < stageTick[2] && stageTick[2] < stageTick[3], "Reihenfolge Warnung < Kritisch < Kollaps (" + stageTick[1] + "/" + stageTick[2] + "/" + stageTick[3] + ")");
                        check(sawLock && sawRec, "EMP-Abschaltung und Erholungsphase wurden durchlaufen");
                        check(conv2.lockout <= 0f, "Netz laeuft nach der Erholung wieder");
                        finish(fail == 0 ? 0 : 1);
                        phase = 99;
                    }
                }
            }
            default -> {}
        }
    }

    Tile fuserTile;
    FluxFuser.FuserBuild fz, fz2;
    FluxNode.NodeBuild n2b;
    FluxConverter.ConverterBuild conv2;
    float maxFzGrant, maxInst2;
    boolean sawFzWork, sawCool, sawLock, sawRec, coolTested;
    int recIdx, coolTick;
    final boolean[] stageSeen = new boolean[4];
    final int[] stageTick = new int[4];

    boolean erekirPlaced;
    FluxConverter.ConverterBuild q1, q2;
    Unit e1, e2, e3;
    int modeIdx, shots0, mat0;
    boolean sawZone, sawMissile;
    float maxGrant, maxInstab;
    final Map<String, Integer> shotsPerMode = new HashMap<>();

    Unit spawn(float x, float y){
        Unit u = UnitTypes.dagger.create(Team.crux);
        u.set(x, y);
        u.add();
        u.health = u.maxHealth = 100000f;
        u.type.speed = 0f;
        u.controller(new mindustry.entities.units.AIController(){
            @Override public void updateMovement(){}
            @Override public void updateTargeting(){}
        });
        return u;
    }

    byte[] save(Building b) throws IOException{
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Writes w = new Writes(new DataOutputStream(bos));
        b.write(w);
        return bos.toByteArray();
    }

    void load(Building b, byte[] data){
        b.read(new Reads(new DataInputStream(new ByteArrayInputStream(data))), b.version());
    }

    void finish(int code){
        Log.info("[SELFTEST] ERGEBNIS: @ bestanden, @ fehlgeschlagen", pass, fail);
        Core.app.post(() -> System.exit(code));
    }
}
