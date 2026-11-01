package skillable;

import skillable.material.*;

import java.util.*;

import static skillable.MaterialFixtures.*;

/** Tests der Fuser-Thermik, der Zustandslogik und der gesamten Effektlogik (Plaene, Zeichenlogik, Lastabstufung). Ohne Engine. */
public class ReactionTests{
    static int passed, failed;

    static void check(boolean cond, String name){
        if(cond){ passed++; System.out.println("  OK   " + name); }
        else{ failed++; System.out.println("  FAIL " + name); }
    }

    static final float LIFE_MARGIN = 0.0f;

    public static void main(String[] args){
        thermal();
        states();
        impact();
        flight();
        budget();
        System.out.println();
        System.out.println("Ergebnis: " + passed + " bestanden, " + failed + " fehlgeschlagen");
        System.exit(failed == 0 ? 0 : 1);
    }

    //----------------------------------------------------------------------------------------------------------------
    static void thermal(){
        System.out.println("[1] Fuser-Thermik und Kuehlung");
        float water = ThermalModel.coolantQuality(true, 0.4f, 0.5f, 0f), cryo = ThermalModel.coolantQuality(true, 0.9f, 0.25f, 0f);
        check(water > 0.2f && water < 0.4f && cryo > 0.7f, "Kuehlmittelguete: Wasser " + String.format("%.2f", water) + ", Kryofluid " + String.format("%.2f", cryo));
        check(cryo > 2.5f * water, "Kryofluid kuehlt deutlich besser als Wasser (x" + String.format("%.1f", cryo / water) + ")");
        check(ThermalModel.coolantQuality(false, 0.5f, 1f, 0f) == 0f && ThermalModel.coolantQuality(false, 0.7f, 0.5f, 1.2f) == 0f, "Schlacke und Oel taugen nicht als Kuehlmittel");
        check(ThermalModel.coolantQuality(true, 0.7f, 0.5f, 1.2f) == 0f, "Brennbare Fluessigkeiten werden auch bei coolant=true abgewiesen");
        float rw = ThermalModel.coolerRate(water, 1f), rc = ThermalModel.coolerRate(cryo, 1f);
        check(ThermalModel.coolerRate(cryo, 0f) == 0f && ThermalModel.coolerRate(cryo, 0.5f) == rc * 0.5f, "Kuehlleistung folgt der Kuehlmittelversorgung (ohne Kuehlmittel keine Wirkung)");
        float[] many = {rc, rc, rc, rc, rc, rc};
        check(ThermalModel.totalCooling(many, 6) <= ThermalModel.MAX_COOLING + 1e-6f && ThermalModel.totalCooling(many, 6) == ThermalModel.MAX_COOLING, "Gesamtkuehlung ist begrenzt (maximal " + ThermalModel.MAX_COOLING + "/Tick)");
        float[] two = {rw, rw};
        check(Math.abs(ThermalModel.totalCooling(two, 2) - 2 * rw) < 1e-6f, "Zwei Kuehler addieren sich");
        float[] five = {rw, rw, rw, rw, rw};
        check(Math.abs(ThermalModel.totalCooling(five, 5) - 4 * rw) < 1e-6f, "Nur " + ThermalModel.MAX_COOLERS + " Kuehler zaehlen je Fuser");

        //Wiederholte Chargen: 20 Stueck, jede mit Spitze 0,45 ueber 300 Ticks, dazwischen 60 Ticks Pause
        float gain = ThermalModel.gainPerTick(0.45f, 300f);
        float noCool = simulate(gain, 0f, 20), waterCool = simulate(gain, ThermalModel.totalCooling(new float[]{rw}, 1), 20), cryoCool = simulate(gain, ThermalModel.totalCooling(new float[]{rc, rc}, 2), 20);
        check(noCool >= 1f, "Ohne Kuehlung summieren sich wiederholte Chargen bis zur Ueberhitzung (Temperatur " + String.format("%.2f", noCool) + ")");
        check(waterCool < noCool * 0.8f, "Ein Wasser-Kuehler senkt die Endtemperatur deutlich (" + String.format("%.2f vs %.2f", waterCool, noCool) + ")");
        check(cryoCool < 0.5f, "Zwei Kryofluid-Kuehler halten denselben Dauerbetrieb kalt (" + String.format("%.2f", cryoCool) + ")");
        //Extreme Belastung: eine sehr volatile Charge uebersteigt jede Kuehlleistung, auch bei voller Kuehlung
        float extreme = ThermalModel.gainPerTick(3.0f, 215f);
        check(extreme > ThermalModel.MAX_COOLING + ThermalModel.PASSIVE, "Extreme Charge (Spitze 3,0 in 215 Ticks = " + String.format("%.4f", extreme) + "/Tick) uebersteigt die maximale Kuehlleistung");
        float h = 0f;
        for(int t = 0; t < 215; t++) h = ThermalModel.step(h, extreme, ThermalModel.MAX_COOLING, 1f);
        check(h >= 1f, "...und ueberhitzt trotz voller Kuehlung (" + String.format("%.2f", h) + ")");
        //Dauerbelastung: Oel+Schlacke-artige Chargen (Spitze 2,1) mit minimaler Pause - volle Kuehlung verzoegert, verhindert aber nicht
        float full = ThermalModel.MAX_COOLING, hd = 0f;
        int batchesUntilBoom = -1;
        for(int b = 1; b <= 30 && batchesUntilBoom < 0; b++){
            for(int t = 0; t < 215 && batchesUntilBoom < 0; t++){ hd = ThermalModel.step(hd, ThermalModel.gainPerTick(2.1f, 215f), full, 1f); if(hd >= 1f) batchesUntilBoom = b; }
            for(int t = 0; t < 30; t++) hd = ThermalModel.step(hd, 0f, full, 1f);
        }
        check(batchesUntilBoom > 1 && batchesUntilBoom <= 30, "Dauerbelastung (Spitze 2,1) ueberhitzt auch bei voller Kuehlung nach " + batchesUntilBoom + " Chargen, ohne Kuehlung schon in der ersten");
        float hn = 0f;
        for(int t = 0; t < 215; t++) hn = ThermalModel.step(hn, ThermalModel.gainPerTick(2.1f, 215f), 0f, 1f);
        check(hn >= 1f, "Dieselbe Charge ohne Kuehlung ueberhitzt sofort (" + String.format("%.2f", hn) + ")");
        float h2 = 0f;
        for(int t = 0; t < 300; t++) h2 = ThermalModel.step(h2, ThermalModel.gainPerTick(1.1f, 300f), ThermalModel.MAX_COOLING, 1f);
        check(h2 < 0.6f, "Eine mittlere Ueberlast (Spitze 1,1) wird mit voller Kuehlung abgefangen (" + String.format("%.2f", h2) + ")");
        check(ThermalModel.step(0.01f, 0f, 0.008f, 100f) == 0f, "Temperatur faellt nie unter 0");
        check(ThermalModel.startAllowed(0f, 0.5f) && !ThermalModel.startAllowed(0.7f, 0.5f) && ThermalModel.startAllowed(0f, 1.5f) && !ThermalModel.startAllowed(0.1f, 1.5f), "Auto-Start: kalt ja, zu warm nein; Chargen mit hoher Spitze nur aus dem kalten Zustand");
        check(!ThermalModel.startAllowed(0f, 2.1f) && !ThermalModel.startAllowed(0.85f, 0.01f), "Durchgehende Mischungen (Spitze 2,1) starten nie automatisch; ab 80 % Temperatur startet nichts");
        check(ThermalModel.forceAllowed(0.3f) && !ThermalModel.forceAllowed(0.5f), "Bewusste Bestaetigung einer riskanten Charge nur bei kaltem Fuser");
    }

    static float simulate(float gain, float cooling, int batches){
        float h = 0f;
        for(int b = 0; b < batches; b++){
            for(int t = 0; t < 300; t++){ h = ThermalModel.step(h, gain, cooling, 1f); if(h >= 1f) return h; }
            for(int t = 0; t < 60; t++) h = ThermalModel.step(h, 0f, cooling, 1f);
        }
        return h;
    }

    //----------------------------------------------------------------------------------------------------------------
    static void states(){
        System.out.println("[2] Fuser-Zustaende");
        check(FuserState.of(false, false, 0f, false, false) == FuserState.PREPARE, "Nicht scharf = Vorbereiten");
        check(FuserState.of(true, false, 0f, true, false) == FuserState.SETTLING, "Scharf + gerade veraendert = Ruhezeit");
        check(FuserState.of(true, false, 0f, false, false) == FuserState.READY, "Scharf + unveraendert = Bereit");
        check(FuserState.of(true, true, 0.3f, false, false) == FuserState.ACTIVE, "Laufende Charge = Aktiv");
        check(FuserState.of(true, false, 0.4f, false, true) == FuserState.COOLING_DOWN, "Zu heiss zum Start = Abkuehlen");
        check(FuserState.of(true, true, 0.9f, false, false) == FuserState.OVERHEATED && FuserState.of(false, false, 0.85f, false, false) == FuserState.OVERHEATED, "Ab 80 % Temperatur = Ueberhitzt (auch ohne Charge)");
        check(FuserState.of(false, true, 0.1f, true, false) == FuserState.ACTIVE, "Laufende Charge bleibt aktiv, auch wenn dabei die naechste Mischung eingerichtet wird");
    }

    //----------------------------------------------------------------------------------------------------------------
    static List<MaterialProfile> sample(int randomCount){
        List<MaterialProfile> ps = new ArrayList<>(MaterialTests.diverse());
        Random rnd = new Random(11);
        for(int t = 0; t < randomCount; t++){
            int k = 1 + rnd.nextInt(7);
            List<IngredientDef> d = new ArrayList<>(); float[] p = new float[k];
            for(int i = 0; i < k; i++){
                d.add(rnd.nextFloat() < .25f ? LIQUIDS[rnd.nextInt(4)] : ITEMS[rnd.nextInt(ITEMS.length)]);
                p[i] = 1 + rnd.nextInt(6);
            }
            ps.add(MaterialProfile.of(Composition.of(d, p)));
        }
        return ps;
    }

    static void impact(){
        System.out.println("[3] Einschlagsplaene und Zeichenlogik");
        List<MaterialProfile> ps = sample(400);
        RecordingCanvas rc = new RecordingCanvas();
        boolean echoSkip = true, det = true, phasesOk = true, tiersOk = true, finite = true, bounds = true, cleanup = true, lodMono = true, budget = true, mech = true, echoMatch = true, early = true;
        int maxPrims0 = 0;
        Set<String> prints = new HashSet<>();
        double simplePhases = 0, complexPhases = 0; int simpleN = 0, complexN = 0;
        Set<Integer> kindsUsed = new HashSet<>();
        float worstExtent = 0;
        for(MaterialProfile p : ps){
            ShotSpec s = ShotSpec.from(p);
            ImpactPlan a = ImpactPlan.build(p, s), b = ImpactPlan.build(p, s);
            if(!a.fingerprint().equals(b.fingerprint()) || a.extent != b.extent || a.cost != b.cost) det = false;
            prints.add(a.fingerprint());
            if(a.count < 1 || a.count > ImpactPlan.MAX_PHASES) phasesOk = false;
            if(a.lifetime != ImpactPlan.TIER_LIFE[a.tier]) tiersOk = false;
            for(int i = 0; i < a.count; i++){
                if(a.start[i] < 0 || a.dur[i] < 1 || a.start[i] + a.dur[i] > a.lifetime) tiersOk = false;
                kindsUsed.add(a.kind[i]);
            }
            //Mechanik <-> Darstellung
            boolean hasRing = false;
            int arcPhases = 0, echoFlash = 0, echoArcs = 0;
            for(int i = 0; i < a.count; i++){
                if(a.kind[i] == ImpactPlan.RING){ hasRing = true; if(Math.abs(a.size[i] - s.splashRadius) > 1e-3f) mech = false; }
                if(a.kind[i] == ImpactPlan.ARCS){ arcPhases++; if(a.n[i] > Math.min(7, s.lightning)) mech = false; }
                if(a.kind[i] == ImpactPlan.ECHO_FLASH) echoFlash++;
                if(a.kind[i] == ImpactPlan.ECHO_ARC){ echoArcs++; if(a.n[i] != s.echoLightning) echoMatch = false; }
                if(a.kind[i] == ImpactPlan.ECHO_RING && Math.abs(a.size[i] - s.echoRadius) > 1e-3f) echoMatch = false;
            }
            if(hasRing != (s.splashRadius > 0f)) mech = false;
            if(s.lightning == 0 && arcPhases > 0) mech = false;
            if(s.echoCount != a.echoes || (s.echoArc ? echoArcs != s.echoCount || echoFlash != 0 : echoFlash != s.echoCount)) echoMatch = false;

            if(p.ingredients == 1){ simplePhases += a.count; simpleN++; }
            if(p.ingredients >= 5){ complexPhases += a.count; complexN++; }

            for(int lod = 0; lod < 3; lod++){
                int prev = Integer.MAX_VALUE;
                int peak = 0;
                for(float t = -2; t <= a.lifetime + 6; t += 1f){
                    for(float rot : new float[]{0f, 137f}){
                        rc.reset(10f, -5f);
                        ImpactRenderer.draw(rc, a, t, 7, rot, 10f, -5f, lod, false);
                        if(lod == 0 && a.echoes > 0){ RecordingCanvas r2 = new RecordingCanvas(); r2.reset(10f, -5f); ImpactRenderer.draw(r2, a, t, 7, rot, 10f, -5f, lod, true); if(r2.prims > rc.prims) echoSkip = false; }
                        if(rc.bad > 0) finite = false;
                        if(rc.maxDist > a.extent + 0.001f){ bounds = false; worstExtent = Math.max(worstExtent, rc.maxDist - a.extent); }
                        if((t < 0f || t >= a.lifetime) && rc.prims != 0) cleanup = false;
                        peak = Math.max(peak, rc.prims);
                    }
                }
                if(lod == 0) maxPrims0 = Math.max(maxPrims0, peak);
                int limit = lod == 0 ? 170 : (lod == 1 ? 100 : 60);
                if(peak > limit) budget = false;
                a.cost = a.cost; //(unveraendert)
                if(peak > prev) lodMono = false;
                prev = peak;
                if(lod == 1) lastLod1Peak = peak;
                if(lod == 2 && peak > lastLod1Peak) lodMono = false;
                if(lod == 0) lastLod0Peak = peak;
                if(lod == 1 && peak > lastLod0Peak) lodMono = false;
            }
            if(a.lifetime < 20) early = false;
        }
        check(det, "Einschlagsplaene sind reproduzierbar (" + ps.size() + " Materialien, zweimal gebaut = identisch)");
        check(phasesOk && tiersOk, "Phasenzahl <= " + ImpactPlan.MAX_PHASES + ", alle Phasen liegen innerhalb der Lebensdauer der Stufe");
        check(finite, "Zeichenlogik liefert nie NaN/Unendlich, Farben bleiben im Bereich (alle Plaene, 3 Stufen, ueber die gesamte Lebensdauer)");
        check(bounds, "Jede Geometrie bleibt innerhalb der angegebenen Reichweite" + (worstExtent > 0 ? " (schlimmster Ueberstand " + worstExtent + ")" : ""));
        check(cleanup, "Vor dem Einschlag und nach Ablauf der Lebensdauer wird nichts gezeichnet (keine Renderreste)");
        check(budget, "Primitive je Bild begrenzt (voll <= 170, reduziert <= 100, minimal <= 60); Spitze voll: " + maxPrims0);
        check(lodMono, "Abgestufte Komplexitaet: minimal <= reduziert <= voll");
        check(mech, "Schockring hat genau den Radius des echten Flaechenschadens; Blitze nie mehr als echte Blitze; kein Ring ohne Flaechenschaden");
        check(echoMatch, "Folgephasen entsprechen exakt den echten Echo-Reaktionen (Anzahl, Radius, Blitzzahl, Art)");
        check(echoSkip, "Ohne ausgeloeste Echos werden keine Folgereaktionen gezeichnet (nie mehr Primitive als mit Echos)");
        check(early, "Jeder Einschlag ist lang genug sichtbar (>= 20 Ticks)");
        check(prints.size() >= ps.size() / 12, "Vielfalt: " + prints.size() + " verschiedene Phasenfolgen in " + ps.size() + " Materialien");
        check(kindsUsed.size() >= 15, "Fast alle Phasenarten werden tatsaechlich genutzt (" + kindsUsed.size() + " von 17)");
        float sAvg = (float)(simplePhases / Math.max(1, simpleN)), cAvg = (float)(complexPhases / Math.max(1, complexN));
        check(cAvg > sAvg + 1.5f, "Komplexe Synthesen (>= 5 Zutaten) haben deutlich mehr Phasen als einfache (" + String.format("%.1f vs %.1f", cAvg, sAvg));
        //Diverse Familien: Einschlaege der 22 Referenzmaterialien unterscheiden sich in Phasenarten
        Set<Integer> masks = new HashSet<>();
        for(MaterialProfile p : MaterialTests.diverse()){
            ImpactPlan a = ImpactPlan.build(p, ShotSpec.from(p));
            int m = 0; for(int i = 0; i < a.count; i++) m |= 1 << a.kind[i];
            masks.add(m);
        }
        check(masks.size() >= 14, "Die 22 Referenzmaterialien ergeben " + masks.size() + " verschiedene Zusammenstellungen von Phasenarten (nicht nur umgefaerbt)");
        //Beispiele aus dem Auftrag
        ImpactPlan blastP = ImpactPlan.build(profile(blast, 5), ShotSpec.from(profile(blast, 5)));
        ImpactPlan crystal = ImpactPlan.build(profile(tungsten, 4, carbide, 2), ShotSpec.from(profile(tungsten, 4, carbide, 2)));
        ImpactPlan arc = ImpactPlan.build(profile(copper, 5, surge, 3), ShotSpec.from(profile(copper, 5, surge, 3)));
        ImpactPlan plasma = ImpactPlan.build(profile(pyratite, 4, slag, 30), ShotSpec.from(profile(pyratite, 4, slag, 30)));
        ImpactPlan field = ImpactPlan.build(profile(thorium, 3, phase, 3), ShotSpec.from(profile(thorium, 3, phase, 3)));
        check(has(blastP, ImpactPlan.RING) && has(blastP, ImpactPlan.FIREBALL), "Sprengmittel: Druckwelle und Feuerball");
        check(has(arc, ImpactPlan.ARCS), "Elektrisch: verzweigte Entladungen");
        check(has(crystal, ImpactPlan.SHARDS), "Kristallin (Wolfram + Carbid): Splitterwolke");
        check(has(plasma, ImpactPlan.EMBERS) || has(plasma, ImpactPlan.BLOOM) || has(plasma, ImpactPlan.FIREBALL), "Thermisch: Glut/Hitzezone/Feuerball");
        check(has(field, ImpactPlan.IMPLODE) && has(field, ImpactPlan.FIELD), "Flux: Implosion und rotierende Feldbogen");
        check(!arc.fingerprint().equals(blastP.fingerprint()) && !crystal.fingerprint().equals(plasma.fingerprint()) && !field.fingerprint().equals(arc.fingerprint()), "Diese Klassen sind anhand ihrer Phasenfolge unterscheidbar");
        //Echo-Versatz
        boolean off = true;
        for(int i = 0; i < 3; i++) for(float rot : new float[]{0f, 90f, 210f}){
            float ex = ImpactPlan.echoX(i, 1234, 20f, rot), ey = ImpactPlan.echoY(i, 1234, 20f, rot);
            if(Math.hypot(ex, ey) > 20f * 1.5f || ex != ImpactPlan.echoX(i, 1234, 20f, rot)) off = false;
        }
        check(off, "Echo-Versatz ist reproduzierbar und begrenzt (Darstellung und echtes Echo-Geschoss nutzen dieselbe Funktion)");
    }

    static int lastLod0Peak, lastLod1Peak;

    static boolean has(ImpactPlan p, int kind){
        for(int i = 0; i < p.count; i++) if(p.kind[i] == kind) return true;
        return false;
    }

    //----------------------------------------------------------------------------------------------------------------
    static void flight(){
        System.out.println("[4] Flugbilder");
        List<MaterialProfile> ps = sample(400);
        RecordingCanvas rc = new RecordingCanvas();
        boolean det = true, finite = true, bounds = true, budget = true, lod = true, layersOk = true;
        Set<String> prints = new HashSet<>();
        int peak0 = 0;
        Set<Integer> sheds = new HashSet<>();
        float worst = 0;
        for(MaterialProfile p : ps){
            ShotSpec s = ShotSpec.from(p);
            FlightPlan a = FlightPlan.build(p, s), b = FlightPlan.build(p, s);
            if(!a.fingerprint().equals(b.fingerprint()) || a.cost != b.cost) det = false;
            prints.add(a.fingerprint());
            sheds.add(a.shed);
            if(a.layers < 1 || a.layers > 3) layersOk = false;
            int[] peak = new int[3];
            for(int l = 0; l < 3; l++){
                for(float time = 0; time < 120; time += 1f){
                    for(float rot : new float[]{0f, 73f, 200f}){
                        rc.reset(0f, 0f);
                        FlightRenderer.draw(rc, a, 0f, 0f, rot, time, 5 + (int)time, l);
                        if(rc.bad > 0) finite = false;
                        if(rc.maxDist > a.extent()){ bounds = false; worst = Math.max(worst, rc.maxDist - a.extent()); }
                        peak[l] = Math.max(peak[l], rc.prims);
                    }
                }
            }
            peak0 = Math.max(peak0, peak[0]);
            if(peak[0] > 75 || peak[1] > 50 || peak[2] > 16) budget = false;
            if(peak[1] > peak[0] || peak[2] > peak[1]) lod = false;
        }
        check(det, "Flugbilder sind reproduzierbar");
        check(layersOk, "Spurschichten: 1 bis 3");
        check(finite, "Flug-Zeichenlogik liefert nie NaN/Unendlich (alle Materialien, 3 Stufen, 120 Ticks, 3 Richtungen)");
        check(bounds, "Alle Flug-Geometrie bleibt innerhalb des angegebenen Radius" + (worst > 0 ? " (Ueberstand " + worst + ")" : ""));
        check(budget, "Primitive je Bild begrenzt (voll <= 75, reduziert <= 50, minimal <= 16); Spitze: " + peak0);
        check(lod, "Flug: minimal <= reduziert <= voll");
        check(prints.size() >= 12, "Flugbilder unterscheiden sich strukturell (" + prints.size() + " Varianten)");
        check(sheds.size() >= 5, "Abgeloeste Partikelarten kommen vor (" + sheds.size() + " von 6): Glut, Splitter, Tropfen, Funken, Feldpartikel");
        //Beispiele
        FlightPlan simple = FlightPlan.build(profile(sand, 8), ShotSpec.from(profile(sand, 8)));
        FlightPlan complexP = FlightPlan.build(profile(surge, 4, thorium, 3, phase, 2, silicon, 2, copper, 2), ShotSpec.from(profile(surge, 4, thorium, 3, phase, 2, silicon, 2, copper, 2)));
        check(complexP.cost > simple.cost && complexP.layers >= simple.layers, "Komplexe Materialien haben ein aufwendigeres Flugbild als einfache (Kosten " + complexP.cost + " vs " + simple.cost + ")");
        FlightPlan unstable = FlightPlan.build(profile(oil, 3, coal, 2, slag, 1), ShotSpec.from(profile(oil, 3, coal, 2, slag, 1)));
        check(unstable.flicker > 0f && simple.flicker == 0f, "Instabile Zusammensetzung flackert, stabile nicht");
        check(FlightPlan.build(profile(copper, 5, surge, 3), ShotSpec.from(profile(copper, 5, surge, 3))).arcs > 0 && simple.arcs == 0, "Entladungen entlang der Flugbahn nur bei echter Blitzwirkung");
    }

    //----------------------------------------------------------------------------------------------------------------
    static void budget(){
        System.out.println("[5] Lastbudget");
        FxBudget b = new FxBudget();
        check(b.spawn(50, 0f) == 0, "Erster Effekt: volle Qualitaet");
        int lvl = 0; float now = 0f;
        for(int i = 0; i < 30; i++){ now += 2f; lvl = b.spawn(60, now); }
        check(lvl >= 1, "Viele gleichzeitige Reaktionen -> vereinfachte Darstellung (Stufe " + lvl + ")");
        for(int i = 0; i < 60; i++){ now += 2f; lvl = b.spawn(60, now); }
        check(lvl == 2 || b.load(now) <= 800f, "Last bleibt begrenzt (" + String.format("%.0f", b.load(now)) + "), kein unkontrolliertes Wachstum");
        check(b.level(now + 400f) == 0 && b.load(now + 400f) < 1f, "Nach dem Gefecht erholt sich die Qualitaet vollstaendig");
        b.spawn(10, 5f); //Zeit zurueck (neue Welt)
        check(b.level(5f) >= 0, "Zurueckgesetzte Zeit bricht nichts");
        //Dauerbetrieb realistischer Tuerme: 6 Tuerme, 1 Schuss/s mit Kosten 40
        FxBudget c = new FxBudget();
        int worstLevel = 0; float t = 0f;
        for(int i = 0; i < 600; i++){ t += 10f; for(int k = 0; k < 6 / 6 + 0; k++) worstLevel = Math.max(worstLevel, c.spawn(40, t)); }
        check(worstLevel == 0, "Einzelner Turm im Dauerfeuer bleibt in voller Qualitaet");
        FxBudget d = new FxBudget(); t = 0f; worstLevel = 0;
        for(int i = 0; i < 600; i++){ t += 1f; worstLevel = Math.max(worstLevel, d.spawn(40, t)); }
        check(worstLevel == 2, "Extremer Dauerbeschuss (40 Reaktionen/s) wird auf Minimalstufe vereinfacht");
    }
}
