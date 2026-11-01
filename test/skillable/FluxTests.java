package skillable;

import skillable.flux.*;

import java.util.*;

/**
 * Spielunabhaengige Tests der Flux-Logik (kein JUnit noetig). Start: tools/run-tests.bat bzw. tools/run-tests.sh
 * Deckt die Abnahmefaelle 1-7, 10 und Teile von 14 ab. Faelle, die das laufende Spiel brauchen
 * (8, 9, 11-13, 15), stehen in docs/TESTING.md als offen bzw. im Headless-Integrationstest.
 */
public class FluxTests{
    static int passed, failed;

    static void check(boolean cond, String name){
        if(cond){ passed++; System.out.println("  OK   " + name); }
        else{ failed++; System.out.println("  FAIL " + name); }
    }

    static boolean near(float a, float b){ return Math.abs(a - b) < 1e-4f; }

    //---------- Fakes
    static class FakeSource implements FluxSource{
        final ConverterProfile profile;
        float power = 1f, instability, lockout, recovery, lastOut, lastLoad, coolant = 1f;
        InstabilityModel.Breakdown last = new InstabilityModel.Breakdown();
        boolean overdrive;
        FluxNetwork net;
        FakeSource(ConverterProfile p){ profile = p; }
        public FluxNetwork fluxNetwork(){ return net; }
        public void fluxNetwork(FluxNetwork n){ net = n; }
        public ConverterProfile profile(){ return profile; }
        public float nominalOutput(){
            if(lockout > 0) return 0f;
            return profile.throughput * FluxMath.clamp01(power) * InstabilityModel.efficiency(instability) * InstabilityModel.recoveryFactor(recovery);
        }
        public float maxOutput(){ return overdrive ? nominalOutput() * profile.overdriveFactor : nominalOutput(); }
        public float coolantFactor(){ return coolant; }
        public void reportBreakdown(InstabilityModel.Breakdown b){ last.set(b); }
        public boolean locked(){ return lockout > 0; }
        public boolean recovering(){ return lockout <= 0 && recovery > 0; }
        public float instability(){ return instability; }
        public void instability(float v){ instability = FluxMath.clamp01(v); }
        public void reportOutput(float out, float load){ lastOut = out; lastLoad = load; }
        public void applyLockout(float t){ lockout = t; recovery = InstabilityModel.RECOVERY_TICKS; instability = InstabilityModel.RESTART_INSTABILITY; }
        public void tickState(float d){
            if(lockout > 0) lockout = Math.max(0, lockout - d);
            else if(recovery > 0) recovery = Math.max(0, recovery - d);
        }
    }

    static class FakeConsumer implements FluxConsumer{
        float demand, granted, risk;
        int prio;
        FluxNetwork net;
        FakeConsumer(float demand, int prio){ this.demand = demand; this.prio = prio; }
        public FluxNetwork fluxNetwork(){ return net; }
        public void fluxNetwork(FluxNetwork n){ net = n; }
        public float fluxDemand(){ return demand; }
        public int fluxPriority(){ return prio; }
        public void fluxGranted(float g){ granted = g; }
        public float riskTarget(){ return risk; }
    }

    static FluxNetwork net(FluxMember... members){
        FluxNetwork n = new FluxNetwork();
        for(FluxMember m : members) n.add(m);
        return n;
    }

    //---------- Tests
    static void test1_noPowerNoFlux(){
        System.out.println("[1] Ohne normalen Strom wird kein Flux erzeugt");
        FakeSource s = new FakeSource(ConverterProfile.STABILIZER); s.power = 0f;
        FakeConsumer c = new FakeConsumer(0.8f, 0);
        FluxNetwork n = net(s, c);
        n.update(1f);
        check(near(n.lastCapacity, 0f), "Kapazitaet 0 ohne Strom");
        check(near(c.granted, 0f), "Verbraucher erhaelt 0");
    }

    static void test2_noLinkNoFlux(){
        System.out.println("[2] Ohne gueltige Flux-Verbindung erhaelt der Turm keinen Flux");
        FakeSource s = new FakeSource(ConverterProfile.STABILIZER);
        FakeConsumer lonely = new FakeConsumer(0.8f, 0);
        FluxNetwork a = net(s), b = net(lonely);
        a.update(1f); b.update(1f);
        check(near(lonely.granted, 0f), "Verbraucher in eigenem Netz ohne Konverter erhaelt 0");
    }

    static void test3_insufficientFlux(){
        System.out.println("[3] Ohne ausreichend aktuellen Flux wird nicht voll versorgt");
        FakeSource s = new FakeSource(ConverterProfile.STABILIZER); //1.0 Flux/Tick
        FakeConsumer c = new FakeConsumer(4.5f, 0);
        FluxNetwork n = net(s, c);
        n.update(1f);
        check(c.granted < 4.5f * 0.5f, "Zuteilung unter Mindest-Erfuellungsgrad (0.5)");
        check(near(c.granted, 1.0f), "Zuteilung = verfuegbarer Durchsatz");
    }

    static void test4_unusedFluxDecays(){
        System.out.println("[4] Unverbrauchter Flux wird nicht im naechsten Tick wieder verfuegbar");
        FakeSource s = new FakeSource(ConverterProfile.STABILIZER);
        FakeConsumer c = new FakeConsumer(0f, 0);
        FluxNetwork n = net(s, c);
        for(int i = 0; i < 600; i++) n.update(1f); //10 s Leerlauf
        check(n.lastWasted > 0f, "Leerlauf: Kapazitaet verfaellt (wasted > 0)");
        c.demand = 100f; //riesiger Bedarf nach langem Leerlauf
        n.update(1f);
        check(near(c.granted, ConverterProfile.STABILIZER.throughput), "Nach Leerlauf nur aktueller Durchsatz, kein Vorrat");
        s.power = 0f;
        n.update(1f);
        check(near(c.granted, 0f), "Ohne Strom im naechsten Tick 0, nichts uebrig geblieben");
    }

    static void test5_noStorage(){
        System.out.println("[5] Konverter und Netz speichern keine Flux-Menge");
        boolean storesAny = false;
        for(Class<?> k : new Class<?>[]{FluxNetwork.class, FluxSolver.class, ConverterProfile.class}){
            for(var f : k.getDeclaredFields()){
                String nm = f.getName().toLowerCase();
                if(nm.contains("stored") || nm.contains("buffer") || nm.contains("battery") || nm.contains("accum")) storesAny = true;
            }
        }
        check(!storesAny, "Keine Speicher-Felder in Netz/Solver/Profil");
        FluxNetwork n = net(new FakeSource(ConverterProfile.QUANTUM), new FakeConsumer(1f, 0));
        float first = 0f;
        for(int i = 0; i < 1000; i++){ n.update(1f); if(i == 0) first = n.lastGranted; }
        check(near(first, n.lastGranted), "Zuteilung bleibt ueber 1000 Ticks konstant (waechst nicht an)");
    }

    static void test6_separateNetworks(){
        System.out.println("[6] Getrennte Netzwerke tauschen keinen Flux und keine Stabilitaet");
        FakeSource a = new FakeSource(ConverterProfile.STABILIZER), b = new FakeSource(ConverterProfile.STABILIZER);
        FakeConsumer ca = new FakeConsumer(5f, 0), cb = new FakeConsumer(0.2f, 0);
        FluxNetwork na = net(a, ca), nb = net(b, cb);
        for(int i = 0; i < 300; i++){ na.update(1f); nb.update(1f); }
        check(near(ca.granted, 1.0f), "Netz A begrenzt auf eigene Kapazitaet");
        check(near(cb.granted, 0.2f), "Netz B voll versorgt");
        check(a.instability > b.instability, "Instabilitaet nur im ueberlasteten Netz A");
    }

    static void test7_profiles(){
        System.out.println("[7] Konverterprofile beeinflussen Durchsatz und Instabilitaet");
        ConverterProfile[] ps = {ConverterProfile.STABILIZER, ConverterProfile.ACCELERATOR, ConverterProfile.QUANTUM};
        check(ps[0].throughput < ps[1].throughput && ps[1].throughput < ps[2].throughput, "Durchsatz Stabilizer < Accelerator < Quantum");
        float[] t = new float[3];
        for(int k = 0; k < 3; k++) t[k] = InstabilityModel.target(ps[k], 0.9f, 0f, 0f, 1f, null);
        check(t[0] < t[1] && t[1] < t[2], "Zielwert bei gleicher relativer Last: Stabilizer < Accelerator < Quantum (" + t[0] + ", " + t[1] + ", " + t[2] + ")");
        float[] inst = new float[3];
        for(int k = 0; k < 3; k++){
            FakeSource s = new FakeSource(ps[k]);
            FakeConsumer c = new FakeConsumer(ps[k].throughput * 0.6f, 0);
            FluxNetwork n = net(s, c);
            for(int i = 0; i < 1200; i++) n.update(1f);
            inst[k] = s.instability;
        }
        check(inst[0] > 0f && inst[0] < inst[1] && inst[1] < inst[2], "Simulation (Last 60 %): Instabilitaet steigt je nach Typ (" + inst[0] + ", " + inst[1] + ", " + inst[2] + ")");
    }

    static FakeSource runLoad(ConverterProfile p, float load, boolean overdrive, float risk, float coolant, int ticks){
        FakeSource s = new FakeSource(p); s.overdrive = overdrive; s.coolant = coolant;
        FakeConsumer c = new FakeConsumer(p.throughput * load, 0); c.risk = risk;
        FluxNetwork n = net(s, c);
        for(int i = 0; i < ticks && !n.offline; i++) n.update(1f);
        return s;
    }

    static void testInstabilityStages(){
        System.out.println("[Instabilitaet] Jede Stufe ist tatsaechlich erreichbar, sie steigt UND sinkt");
        ConverterProfile st = ConverterProfile.STABILIZER;
        FakeSource low = runLoad(st, 0.30f, false, 0f, 1f, 3000);
        check(InstabilityModel.stage(low.instability) == InstabilityModel.STABLE && low.instability > 0f, "Stabilizer bei 30 % Last: stabil, aber Wert > 0 (" + low.instability + ")");
        FakeSource warn = runLoad(st, 0.80f, false, 0f, 1f, 3000);
        check(InstabilityModel.stage(warn.instability) == InstabilityModel.WARNING, "Stabilizer bei 80 % Last: WARNUNG (" + warn.instability + ")");
        FakeSource crit = runLoad(st, 1.00f, false, 0f, 1f, 3000);
        check(InstabilityModel.stage(crit.instability) == InstabilityModel.CRITICAL, "Stabilizer bei 100 % Last: KRITISCH (" + crit.instability + ")");
        FakeSource col = runLoad(st, 1.15f, true, 0f, 1f, 6000);
        check(col.lockout > 0f, "Stabilizer mit Overdrive-Ueberlast: Kollaps");
        FakeSource acc = runLoad(ConverterProfile.ACCELERATOR, 1.0f, false, 0f, 1f, 6000);
        check(acc.lockout > 0f, "Accelerator bei Volllast kollabiert (Zielwert > 1)");
        FakeSource q = runLoad(ConverterProfile.QUANTUM, 0.90f, false, 0f, 1f, 3000);
        check(q.lockout > 0f, "Quantum bei 90 % Last kollabiert (hohes Risiko)");
        FakeSource modeRisk = runLoad(st, 0.50f, false, 0.30f, 1f, 3000);
        check(InstabilityModel.stage(modeRisk.instability) == InstabilityModel.WARNING, "Riskanter Modus (+30 %) bei 50 % Last: WARNUNG (" + modeRisk.instability + ")");
        FakeSource cooled = runLoad(st, 0.80f, false, 0f, 0.80f, 3000);
        check(cooled.instability < warn.instability && InstabilityModel.stage(cooled.instability) == InstabilityModel.STABLE, "Wasserkuehlung (x0,8) senkt WARNUNG auf stabil (" + cooled.instability + ")");
        //Sinken bei niedriger Last
        FakeSource s = new FakeSource(st);
        FakeConsumer c = new FakeConsumer(0.8f, 0);
        FluxNetwork n = net(s, c);
        for(int i = 0; i < 2000; i++) n.update(1f);
        float peak = s.instability;
        c.demand = 0.1f;
        for(int i = 0; i < 2000; i++) n.update(1f);
        check(peak >= InstabilityModel.WARNING_AT && s.instability < InstabilityModel.WARNING_AT, "Bei niedriger Last sinkt die Instabilitaet wieder (" + peak + " -> " + s.instability + ")");
        //Ursachen werden gemeldet
        FakeSource b = runLoad(st, 0.8f, false, 0.1f, 1f, 100);
        check(b.last.loadRisk > 0f && b.last.modeRisk > 0f && b.last.target > 0f, "Aufschluesselung der Ursachen vorhanden");
        //Zeit bis zum Kollaps bei Ueberlast ist fuer Warnungen ausreichend
        FakeSource ss = new FakeSource(ConverterProfile.ACCELERATOR);
        FakeConsumer cc = new FakeConsumer(2.6f, 0);
        FluxNetwork nn = net(ss, cc);
        int tWarn = -1, tCrit = -1, tCol = -1;
        for(int i = 0; i < 6000 && tCol < 0; i++){
            nn.update(1f);
            if(tWarn < 0 && ss.instability >= InstabilityModel.WARNING_AT) tWarn = i;
            if(tCrit < 0 && ss.instability >= InstabilityModel.CRITICAL_AT) tCrit = i;
            if(nn.offline) tCol = i;
        }
        check(tWarn > 0 && tCrit > tWarn && tCol > tCrit, "Reihenfolge Warnung < Kritisch < Kollaps (" + tWarn + " < " + tCrit + " < " + tCol + " Ticks)");
        check(tCol - tWarn >= 120, "Mindestens 2 s zwischen Warnung und Kollaps (" + (tCol - tWarn) + " Ticks)");
    }

    static void test10_collapseAndRecovery(){
        System.out.println("[10] Unterversorgung, Ueberlastung, Kollaps und Wiederanlauf");
        FakeSource s = new FakeSource(ConverterProfile.QUANTUM);
        s.overdrive = true;
        FakeConsumer c = new FakeConsumer(100f, 0);
        FluxNetwork n = net(s, c);
        int[] stages = new int[4];
        int collapsedAt = -1;
        final int[] hooks = {0};
        n.collapseHook = x -> hooks[0]++;
        for(int i = 0; i < 5000 && collapsedAt < 0; i++){
            n.update(1f);
            stages[Math.min(3, n.stage)]++;
            if(n.offline) collapsedAt = i;
        }
        check(collapsedAt > 0, "Dauerhafte Ueberlast fuehrt zum Kollaps (nach " + collapsedAt + " Ticks)");
        check(stages[InstabilityModel.WARNING] > 0 && stages[InstabilityModel.CRITICAL] > 0, "Warn- und kritische Stufe werden VOR dem Kollaps durchlaufen");
        check(hooks[0] == 1, "Kollaps-Hook genau einmal");
        check(near(c.granted, 0f) && n.offline, "Waehrend EMP-Abschaltung kein Flux");
        int ticks = 0;
        while(n.offline && ticks < 100000){ c.demand = 0f; n.update(1f); ticks++; }
        check(Math.abs(ticks - InstabilityModel.LOCKOUT_TICKS) <= 2, "Abschaltung endet nach LOCKOUT_TICKS (" + ticks + ")");
        check(n.recovering, "Danach Erholungsphase");
        check(s.instability <= InstabilityModel.RESTART_INSTABILITY + 1e-3f, "Wiederanlauf mit definierter Start-Instabilitaet");
        c.demand = 0.5f;
        n.update(1f);
        check(c.granted > 0f && c.granted < 0.5f + 1e-4f, "Wiederanlauf: wieder Flux, anfangs reduziert");
        float early = c.granted;
        c.demand = 100f;
        n.update(1f);
        float cap1 = n.lastNominal;
        for(int i = 0; i < (int)InstabilityModel.RECOVERY_TICKS + 5; i++){ c.demand = 0f; n.update(1f); }
        c.demand = 100f; n.update(1f);
        check(n.lastNominal > cap1, "Kapazitaet waechst waehrend der Erholung (" + cap1 + " -> " + n.lastNominal + ")");
        check(!n.recovering, "Erholung abgeschlossen");
    }

    static void test14_cost(){
        System.out.println("[14] Rechenaufwand vieler Netze");
        List<FluxNetwork> all = new ArrayList<>();
        for(int i = 0; i < 500; i++){
            FluxNetwork n = net(new FakeSource(ConverterProfile.STABILIZER), new FakeSource(ConverterProfile.ACCELERATOR),
                new FakeConsumer(1.2f, 0), new FakeConsumer(0.8f, 1), new FakeConsumer(2f, 0));
            all.add(n);
        }
        for(int i = 0; i < 50; i++) for(FluxNetwork n : all) n.update(1f); //Warmup
        long t0 = System.nanoTime();
        int ticks = 600;
        for(int i = 0; i < ticks; i++) for(FluxNetwork n : all) n.update(1f);
        double msPerTick = (System.nanoTime() - t0) / 1e6 / ticks;
        System.out.printf("       500 Netze: %.3f ms pro Tick%n", msPerTick);
        check(msPerTick < 5.0, "500 Netze (2 Konverter, 3 Verbraucher) unter 5 ms/Tick");
    }

    static void testSolver(){
        System.out.println("[Solver] Verteilung, Prioritaeten, Absicherung");
        float[] g = new float[3];
        float tot = FluxSolver.distribute(10f, new float[]{4f, 4f, 4f}, new int[]{0, 0, 0}, g);
        check(near(tot, 10f) && near(g[0], 10f / 3f), "Gleiche Prioritaet: proportionale Verteilung");
        tot = FluxSolver.distribute(5f, new float[]{4f, 4f, 4f}, new int[]{0, 5, 0}, g);
        check(near(g[1], 4f) && near(g[0], 0.5f) && near(g[2], 0.5f), "Hoehere Prioritaet wird zuerst voll bedient");
        tot = FluxSolver.distribute(Float.NaN, new float[]{1f}, new int[]{0}, new float[1]);
        check(near(tot, 0f), "NaN-Angebot -> 0");
        float[] g1 = new float[2];
        tot = FluxSolver.distribute(5f, new float[]{Float.NaN, -3f}, new int[]{0, 0}, g1);
        check(near(tot, 0f) && near(g1[0], 0f) && near(g1[1], 0f), "NaN/negativer Bedarf -> 0");
        tot = FluxSolver.distribute(Float.POSITIVE_INFINITY, new float[]{1f}, new int[]{0}, new float[1]);
        check(tot <= 1f + 1e-4f, "Unendliches Angebot ueberlaeuft nicht");
        check(FluxMath.safe(Float.POSITIVE_INFINITY) == FluxMath.MAX_FLUX, "safe(Infinity) begrenzt");
        check(FluxMath.safe(-1f) == 0f && FluxMath.safe(Float.NaN) == 0f, "safe(neg/NaN) = 0");
    }

    static void testPriorityOverclock(){
        System.out.println("[Prio] Overclock-Anforderung hat Vorrang, ohne Vorrat");
        FakeSource s = new FakeSource(ConverterProfile.QUANTUM);
        s.overdrive = true;
        FakeConsumer oc = new FakeConsumer(5f, 10), normal = new FakeConsumer(5f, 0);
        FluxNetwork n = net(s, oc, normal);
        n.update(1f);
        check(oc.granted > normal.granted, "Hoehere Prioritaet bekommt mehr");
        check(near(oc.granted + normal.granted, n.lastCapacity) || oc.granted + normal.granted <= n.lastCapacity + 1e-3f, "Summe <= Kapazitaet");
    }

    public static void main(String[] args){
        testSolver();
        testInstabilityStages();
        test1_noPowerNoFlux();
        test2_noLinkNoFlux();
        test3_insufficientFlux();
        test4_unusedFluxDecays();
        test5_noStorage();
        test6_separateNetworks();
        test7_profiles();
        test10_collapseAndRecovery();
        testPriorityOverclock();
        test14_cost();
        System.out.println();
        System.out.println("Ergebnis: " + passed + " bestanden, " + failed + " fehlgeschlagen");
        if(failed > 0) System.exit(1);
    }
}
