package skillable.material;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Kanonische Zusammensetzung eines Materials: Zutaten nach Schluessel sortiert, Mengenverhaeltnisse auf
 * {@link #QUANT} Teile quantisiert (Groesstrest-Verfahren, jede Zutat mindestens 1 Teil).
 * Reihenfolge der Eingaenge, Chargengroesse und absolute Mengen beeinflussen die Identitaet NICHT - nur das Verhaeltnis.
 */
public final class Composition{
    public static final int QUANT = 16, MAX_INGREDIENTS = 11;
    public static final String VERSION = "c1";

    public final IngredientDef[] defs;
    public final int[] weights;
    /** Gesamte Einsatzmenge in "Teilen" (Item = 1, 10 Einheiten Fluessigkeit = 1). Nicht Teil der Identitaet. */
    public final float parts;
    public final String signature;
    public final long hash;

    private Composition(IngredientDef[] defs, int[] weights, float parts){
        this.defs = defs;
        this.weights = weights;
        this.parts = parts;
        StringBuilder sb = new StringBuilder(VERSION);
        for(int i = 0; i < defs.length; i++) sb.append(';').append(defs[i].key).append('=').append(weights[i]);
        this.signature = sb.toString();
        this.hash = hash64(signature);
    }

    /** FNV-1a, 64 Bit - stabil ueber Plattformen und Java-Versionen. */
    public static long hash64(String s){
        long h = 0xcbf29ce484222325L;
        for(byte b : s.getBytes(StandardCharsets.UTF_8)){
            h ^= (b & 0xff);
            h *= 0x100000001b3L;
        }
        return h;
    }

    /**
     * Baut eine Zusammensetzung. {@code parts[i]} ist die Einsatzmenge von {@code defs.get(i)} (in Teilen).
     * Gleiche Schluessel werden zusammengefasst. Liefert null bei leerer/ungueltiger Eingabe oder mehr als 11 Zutaten.
     */
    public static Composition of(List<IngredientDef> defs, float[] parts){
        if(defs == null || parts == null || defs.size() != parts.length) return null;
        TreeMap<String, Object[]> merged = new TreeMap<>();
        for(int i = 0; i < parts.length; i++){
            float p = parts[i];
            if(defs.get(i) == null || Float.isNaN(p) || Float.isInfinite(p) || p <= 0f) continue;
            Object[] e = merged.get(defs.get(i).key);
            if(e == null) merged.put(defs.get(i).key, new Object[]{defs.get(i), p});
            else e[1] = (Float)e[1] + p;
        }
        int n = merged.size();
        if(n == 0 || n > MAX_INGREDIENTS) return null;
        IngredientDef[] d = new IngredientDef[n];
        float[] amt = new float[n];
        float total = 0f;
        int idx = 0;
        for(Object[] e : merged.values()){
            d[idx] = (IngredientDef)e[0];
            amt[idx] = (Float)e[1];
            total += amt[idx];
            idx++;
        }
        if(!(total > 0f)) return null;

        //Groesstrest-Quantisierung auf QUANT Teile, jede Zutat >= 1
        int[] w = new int[n];
        double[] exact = new double[n];
        int sum = 0;
        for(int i = 0; i < n; i++){
            exact[i] = amt[i] / (double)total * QUANT;
            w[i] = Math.max(1, (int)Math.floor(exact[i]));
            sum += w[i];
        }
        while(sum < QUANT){
            int best = -1; double bestV = -1e9;
            for(int i = 0; i < n; i++){
                double v = exact[i] - w[i];
                if(v > bestV + 1e-12){ bestV = v; best = i; }
            }
            w[best]++; sum++;
        }
        while(sum > QUANT){
            int best = -1; double bestV = -1e9;
            for(int i = 0; i < n; i++){
                if(w[i] <= 1) continue;
                double v = w[i] - exact[i];
                if(v > bestV + 1e-12){ bestV = v; best = i; }
            }
            if(best < 0) break; //kann bei n <= 11 < 16 nicht passieren
            w[best]--; sum--;
        }
        return new Composition(d, w, total);
    }

    /** Aus gespeicherter Signatur wiederherstellen. {@code resolver} liefert die Zutat zum Schluessel (null = unbekannt). */
    public static Composition parse(String signature, java.util.function.Function<String, IngredientDef> resolver){
        if(signature == null || signature.length() > 600) return null;
        String[] parts = signature.split(";");
        if(parts.length < 2 || !parts[0].equals(VERSION) || parts.length - 1 > MAX_INGREDIENTS) return null;
        IngredientDef[] d = new IngredientDef[parts.length - 1];
        int[] w = new int[d.length];
        int sum = 0;
        String prev = null;
        for(int i = 1; i < parts.length; i++){
            int eq = parts[i].lastIndexOf('=');
            if(eq <= 0) return null;
            String key = parts[i].substring(0, eq);
            if(prev != null && key.compareTo(prev) <= 0) return null; //muss sortiert und eindeutig sein
            prev = key;
            int wi;
            try{ wi = Integer.parseInt(parts[i].substring(eq + 1)); }catch(NumberFormatException e){ return null; }
            if(wi < 1 || wi > QUANT) return null;
            IngredientDef def = resolver.apply(key);
            if(def == null) return null;
            d[i - 1] = def; w[i - 1] = wi; sum += wi;
        }
        if(sum != QUANT) return null;
        return new Composition(d, w, QUANT);
    }

    public int count(){ return defs.length; }
    public float share(int i){ return weights[i] / (float)QUANT; }
    public String code(){ return String.format("%04X", (int)(hash ^ (hash >>> 32)) & 0xffff); }
}
