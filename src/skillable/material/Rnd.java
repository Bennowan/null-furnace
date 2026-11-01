package skillable.material;

/** Zustandslose, allokationsfreie Pseudozufallszahlen (Hash) fuer reproduzierbare Effekte. */
public final class Rnd{
    private Rnd(){}

    /** Gleichverteilt in [0,1). */
    public static float f(int seed, int i){
        int h = seed * 0x9E3779B1 + i * 0x85EBCA6B + 0x165667B1;
        h ^= h >>> 15; h *= 0x2C1B3C6D;
        h ^= h >>> 12; h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) / 16777216f;
    }

    /** Gleichverteilt in [-1,1). */
    public static float s(int seed, int i){ return f(seed, i) * 2f - 1f; }
}
