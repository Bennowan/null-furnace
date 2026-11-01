package skillable.material;

/**
 * Lastbudget fuer Einschlagseffekte: viele gleichzeitige Reaktionen (mehrere Tuerme) werden automatisch vereinfacht
 * (Stufe 0 voll, 1 reduziert, 2 minimal). Die Last verfaellt exponentiell, so dass sich nach dem Gefecht wieder volle Qualitaet einstellt.
 */
public final class FxBudget{
    public static final float DECAY_PER_TICK = 0.97f, LEVEL1 = 120f, LEVEL2 = 300f;
    private float load, lastTick;

    /** Meldet einen neuen Effekt mit Kosten und liefert die zu verwendende Stufe. */
    public int spawn(int cost, float now){
        decay(now);
        int lod = load >= LEVEL2 ? 2 : (load >= LEVEL1 ? 1 : 0);
        //vereinfachte Effekte kosten weniger
        load += lod == 0 ? cost : (lod == 1 ? cost * 0.5f : cost * 0.2f);
        return lod;
    }

    /** Stufe ohne Neuzugang. */
    public int level(float now){
        decay(now);
        return load >= LEVEL2 ? 2 : (load >= LEVEL1 ? 1 : 0);
    }

    public float load(float now){ decay(now); return load; }

    private void decay(float now){
        float dt = now - lastTick;
        if(dt > 0f){ load *= (float)Math.pow(DECAY_PER_TICK, dt); lastTick = now; }
        else if(dt < 0f) lastTick = now; //Zeit zurueckgesetzt (neue Welt)
        if(load < 0.01f) load = 0f;
    }
}
