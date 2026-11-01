package skillable.flux;

import java.util.ArrayList;

/**
 * Ein zusammenhaengendes Flux-Netz. Enthaelt KEINEN Flux-Speicher: update() berechnet pro Tick
 * Angebot und Verteilung neu; nicht verbrauchter Flux verfaellt sofort ("wasted").
 * Rein in Java ohne Mindustry-Typen, damit die Logik unit-testbar ist.
 */
public class FluxNetwork{
    public final ArrayList<FluxSource> sources = new ArrayList<>();
    public final ArrayList<FluxConsumer> consumers = new ArrayList<>();
    public final ArrayList<FluxMember> others = new ArrayList<>();

    /** Nur Anzeigewerte des LETZTEN Ticks - niemals Eingabe fuer den naechsten Tick. */
    public float lastCapacity, lastNominal, lastDemand, lastGranted, lastWasted;
    public int stage;
    public float maxInstability;
    public boolean offline, recovering;
    /** Wird bei Kollaps aufgerufen (Effekte, Kaskade). Darf null sein. */
    public java.util.function.Consumer<FluxNetwork> collapseHook;

    private final InstabilityModel.Breakdown scratch = new InstabilityModel.Breakdown();
    private float[] demand = new float[8], grants = new float[8];
    private int[] prio = new int[8];
    private boolean[] done = new boolean[8];

    public void add(FluxMember m){
        m.fluxNetwork(this);
        if(m instanceof FluxSource s) sources.add(s);
        else if(m instanceof FluxConsumer c) consumers.add(c);
        else others.add(m);
    }

    private void ensure(int n){
        if(demand.length >= n) return;
        int len = Math.max(n, demand.length * 2);
        demand = new float[len]; grants = new float[len]; prio = new int[len]; done = new boolean[len];
    }

    public float demandNow(){ return lastDemand; }

    /** Ein Simulationsschritt. */
    public void update(float delta){
        int ns = sources.size(), nc = consumers.size();
        offline = false; recovering = false;
        for(int i = 0; i < ns; i++){
            FluxSource s = sources.get(i);
            s.tickState(delta);
            if(s.locked()) offline = true;
            if(s.recovering()) recovering = true;
        }

        float nominal = 0f, max = 0f;
        if(!offline){
            for(int i = 0; i < ns; i++){
                FluxSource s = sources.get(i);
                float nom = FluxMath.safe(s.nominalOutput());
                float mx = Math.max(nom, FluxMath.safe(s.maxOutput()));
                nominal += nom; max += mx;
            }
        }
        nominal = FluxMath.safe(nominal);
        max = FluxMath.safe(max);

        ensure(nc);
        float totalDemand = 0f;
        for(int i = 0; i < nc; i++){
            FluxConsumer c = consumers.get(i);
            demand[i] = FluxMath.safe(c.fluxDemand());
            prio[i] = c.fluxPriority();
            totalDemand += demand[i];
        }
        totalDemand = FluxMath.safe(totalDemand);

        float granted = nc == 0 ? 0f : FluxSolver.distribute(max, demand, prio, grants, nc, done);
        for(int i = 0; i < nc; i++) consumers.get(i).fluxGranted(nc == 0 ? 0f : grants[i]);

        lastCapacity = max; lastNominal = nominal; lastDemand = totalDemand; lastGranted = granted;
        lastWasted = Math.max(0f, max - granted); //verfaellt - wird NICHT gespeichert

        float usedNominal = Math.min(granted, nominal);
        float extra = Math.max(0f, granted - nominal);
        float extraCap = Math.max(0f, max - nominal);
        float modeRisk = 0f;
        for(int i = 0; i < nc; i++) modeRisk += FluxMath.safe(consumers.get(i).riskTarget());
        modeRisk = ns > 0 ? modeRisk / ns : 0f;
        float deficit = totalDemand > 0f ? FluxMath.clamp01((totalDemand - granted) / totalDemand) : 0f;

        boolean collapse = false;
        float maxInst = 0f;
        for(int i = 0; i < ns; i++){
            FluxSource s = sources.get(i);
            float out = 0f;
            if(!offline){
                float nom = FluxMath.safe(s.nominalOutput());
                float mx = Math.max(nom, FluxMath.safe(s.maxOutput()));
                if(nominal > 0f) out += usedNominal * nom / nominal;
                if(extraCap > 0f) out += extra * (mx - nom) / extraCap;
            }
            out = FluxMath.safe(out);
            float rated = Math.max(0.0001f, s.profile().throughput);
            //Auslastung = geliefert + unversorgter Anteil am Bedarf (sonst wuerde sinkende Effizienz die Last "wegrechnen"
            //und die Instabilitaet kuenstlich begrenzen)
            float nomShare = nominal > 0f ? FluxMath.safe(s.nominalOutput()) / nominal : 0f;
            float load = FluxMath.safe(out + (totalDemand - granted) * nomShare) / rated;
            s.reportOutput(out, load);
            float target = InstabilityModel.target(s.profile(), load, out > 0f ? modeRisk : 0f,
                out > 0f ? deficit : 0f, s.coolantFactor(), scratch);
            s.reportBreakdown(scratch);
            float inst = InstabilityModel.advance(s.instability(), target, s.profile(), delta);
            s.instability(inst);
            if(!offline && inst >= InstabilityModel.COLLAPSE_AT) collapse = true;
            maxInst = Math.max(maxInst, s.instability());
        }
        maxInstability = maxInst;
        stage = offline ? InstabilityModel.COLLAPSE : InstabilityModel.stage(maxInst);

        if(collapse) collapse(1f);
    }

    /** Vollstaendiger Netz-Kollaps: alle Konverter werden fuer die EMP-Phase abgeschaltet. */
    public void collapse(float lockoutFactor){
        float ticks = InstabilityModel.LOCKOUT_TICKS * Math.max(0.1f, lockoutFactor);
        for(int i = 0; i < sources.size(); i++) sources.get(i).applyLockout(ticks);
        offline = true;
        for(int i = 0; i < consumers.size(); i++) consumers.get(i).fluxGranted(0f);
        if(collapseHook != null) collapseHook.accept(this);
    }

    /** Instabilitaets-Schub (Overclock-Schuss, Kaskade). Fuehrt ggf. sofort zum Kollaps. */
    public void kick(float amount, float lockoutFactorIfCollapse){
        boolean collapse = false;
        for(int i = 0; i < sources.size(); i++){
            FluxSource s = sources.get(i);
            if(s.locked()) continue;
            s.instability(FluxMath.clamp01(s.instability() + FluxMath.safe(amount)));
            if(s.instability() >= InstabilityModel.COLLAPSE_AT) collapse = true;
        }
        if(collapse) collapse(lockoutFactorIfCollapse);
    }

    public boolean isEmpty(){
        return sources.isEmpty() && consumers.isEmpty() && others.isEmpty();
    }
}
