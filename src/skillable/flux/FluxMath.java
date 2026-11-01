package skillable.flux;

/** Absicherung aller Zahlenwerte gegen NaN, Infinity, negative und extreme Eingaben. */
public final class FluxMath{
    /** Obergrenze fuer jeden Flux-Wert (Flux/Tick). Verhindert Ueberlaeufe bei extremen Eingaben. */
    public static final float MAX_FLUX = 1_000_000f;

    private FluxMath(){}

    /** NaN/Infinity/negativ -> 0, sonst auf [0, MAX_FLUX] begrenzt. */
    public static float safe(float v){
        if(Float.isNaN(v) || v <= 0f) return 0f;
        if(v > MAX_FLUX) return MAX_FLUX;
        return v;
    }

    public static float clamp01(float v){
        if(Float.isNaN(v)) return 0f;
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static float safeDiv(float a, float b){
        if(b <= 0f || Float.isNaN(b) || Float.isNaN(a)) return 0f;
        return a / b;
    }
}
