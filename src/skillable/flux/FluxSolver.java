package skillable.flux;

/**
 * Reine Verteilungslogik (ohne Mindustry-Abhaengigkeit, daher unit-testbar).
 * Flux ist ein MOMENTANER Durchsatz: Es gibt keinen Zustand, der zwischen Aufrufen erhalten bleibt.
 */
public final class FluxSolver{
    private FluxSolver(){}

    /**
     * Verteilt einen momentanen Durchsatz auf Verbraucher.
     * Hoehere Prioritaet wird zuerst bedient; innerhalb gleicher Prioritaet proportional zum Bedarf.
     * @param supply verfuegbarer Durchsatz dieses Ticks
     * @param demand Bedarf pro Verbraucher (ungueltige Werte werden als 0 behandelt)
     * @param priority Prioritaet pro Verbraucher (groesser = wichtiger)
     * @param grants Ausgabe: zugeteilter Flux pro Verbraucher (gleiche Laenge wie demand)
     * @return tatsaechlich zugeteilte Summe. supply - Summe verfaellt (wird NICHT gespeichert).
     */
    public static float distribute(float supply, float[] demand, int[] priority, float[] grants){
        return distribute(supply, demand, priority, grants, demand.length, new boolean[demand.length]);
    }

    /** Variante ohne Allokation: nutzt nur die ersten n Eintraege und ein wiederverwendbares done-Array. */
    public static float distribute(float supply, float[] demand, int[] priority, float[] grants, int n, boolean[] done){
        float remaining = FluxMath.safe(supply);
        for(int i = 0; i < n; i++) grants[i] = 0f;
        if(remaining <= 0f || n == 0) return 0f;

        for(int i = 0; i < n; i++) done[i] = false;
        float total = 0f;
        for(int round = 0; round < n; round++){
            //hoechste noch nicht bediente Prioritaet bestimmen
            int best = Integer.MIN_VALUE;
            boolean any = false;
            for(int i = 0; i < n; i++){
                if(!done[i] && priority[i] > best){ best = priority[i]; any = true; }
            }
            if(!any) break;

            float groupDemand = 0f;
            for(int i = 0; i < n; i++){
                if(!done[i] && priority[i] == best) groupDemand += FluxMath.safe(demand[i]);
            }
            float give = Math.min(remaining, groupDemand);
            for(int i = 0; i < n; i++){
                if(!done[i] && priority[i] == best){
                    float d = FluxMath.safe(demand[i]);
                    grants[i] = groupDemand <= 0f ? 0f : give * (d / groupDemand);
                    done[i] = true;
                }
            }
            remaining -= give;
            total += give;
            if(remaining <= 0f) break;
        }
        return total;
    }
}
