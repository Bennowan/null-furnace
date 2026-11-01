package skillable.material;

/**
 * Zeichnet das Item-Icon (32x32, RGBA8888) eines Materials rein aus Profil und Zutatenfarben - ohne Engine und ohne Zufall:
 * dieselbe Zusammensetzung ergibt immer dasselbe Bild. Form folgt der Materialklasse, Oberflaeche/Einschluesse
 * folgen Haerte, Leitfaehigkeit, Diversitaet und den Farben der einzelnen Zutaten.
 */
public final class IconPainter{
    public static final int SIZE = 32;

    private final int[] px = new int[SIZE * SIZE];
    private int rng;

    private IconPainter(int seed){ rng = seed * 747796405 + 12345; }

    /**
     * @param palette RGB (0xRRGGBB) je Zutat, gleiche Reihenfolge wie shares (kann null sein)
     * @param shares  Anteile der Zutaten (kann null sein)
     */
    public static int[] paint(MaterialProfile p, int[] palette, float[] shares){
        IconPainter ip = new IconPainter(p.seed);
        int base = rgb(p.r, p.g, p.b);
        switch(p.primary){
            case MaterialProfile.CLASS_ELECTRIC -> ip.plate(p, base);
            case MaterialProfile.CLASS_THERMAL -> ip.rock(p, base);
            case MaterialProfile.CLASS_EXPLOSIVE -> ip.capsule(p, base);
            case MaterialProfile.CLASS_FLUX -> ip.crystal(p, base, true);
            default -> { if(p.form() == 0 || p.hardness > 0.5f) ip.crystal(p, base, false); else ip.ingot(p, base); }
        }
        if(palette != null && shares != null) ip.inclusions(palette, shares, p);
        if(p.diversity > 0.55f) ip.strata(base, p);
        if(p.secondary == MaterialProfile.CLASS_ELECTRIC && p.primary != MaterialProfile.CLASS_ELECTRIC) ip.traces(p, mul(lighten(base, .6f), 1f));
        if(p.secondary == MaterialProfile.CLASS_THERMAL && p.primary != MaterialProfile.CLASS_THERMAL) ip.cracks(p, 0xff9a3a, 3);
        ip.outline(mul(base, 0.28f));
        return ip.px;
    }

    //------------------------------------------------------------------ Grundformen
    private void ingot(MaterialProfile p, int base){
        //abgeschraegter Barren
        float[] xs = {5, 27, 24, 8}, ys = {20, 20, 11, 11};
        poly(xs, ys, base, 1.0f);
        poly(new float[]{5, 27, 28, 4}, new float[]{20, 20, 25, 25}, mul(base, 0.62f), 1f); //Front
        poly(new float[]{8, 24, 22, 10}, new float[]{11, 11, 8, 8}, lighten(base, 0.35f), 1f); //Oberseite
        line(9, 13, 23, 13, lighten(base, 0.5f));
        for(int i = 0; i < 3; i++) set(8 + next(14), 15 + next(4), mul(base, 0.8f));
    }

    private void crystal(MaterialProfile p, int base, boolean flux){
        int n = p.sides();
        float cx = 16, cy = 16, rad = flux ? 11 : 12;
        float[] xs = new float[n], ys = new float[n];
        float rot = (p.seed & 15) * 6.28318f / 16f;
        for(int i = 0; i < n; i++){
            float a = rot + i * 6.28318f / n;
            float rr = rad * (0.85f + 0.15f * ((p.seed >> (i + 2)) & 1));
            xs[i] = cx + (float)Math.cos(a) * rr; ys[i] = cy + (float)Math.sin(a) * rr;
        }
        for(int i = 0; i < n; i++){ //Facetten als Dreiecke zum Zentrum
            float shade = 0.55f + 0.7f * (0.5f + 0.5f * (float)Math.cos(rot + (i + 0.5f) * 6.28318f / n - 2.4f));
            poly(new float[]{cx, xs[i], xs[(i + 1) % n]}, new float[]{cy, ys[i], ys[(i + 1) % n]}, shade < 1f ? mul(base, shade) : lighten(base, shade - 1f), 1f);
        }
        if(flux){
            ring(cx, cy, rad + 2.2f, 0.9f, lighten(base, 0.6f));
            disc(cx, cy, 3.2f, 0xffffff);
            disc(cx, cy, 5f, lighten(base, 0.75f), 0.35f);
            for(int i = 0; i < 4; i++) set(3 + next(26), 3 + next(26), 0xffffff);
        }else{
            line(xs[0], ys[0], cx, cy, lighten(base, .55f));
        }
    }

    private void plate(MaterialProfile p, int base){
        poly(new float[]{4, 28, 28, 4}, new float[]{7, 7, 25, 25}, mul(base, 0.5f), 1f);
        poly(new float[]{5, 27, 27, 5}, new float[]{8, 8, 24, 24}, base, 1f);
        traces(p, lighten(base, 0.7f));
        for(int i = 0; i < 4; i++){ //Kontakte in den Ecken
            int x = i % 2 == 0 ? 7 : 25, y = i < 2 ? 10 : 22;
            disc(x, y, 1.6f, 0xe8d28a);
        }
    }

    private void rock(MaterialProfile p, int base){
        int n = 9;
        float[] xs = new float[n], ys = new float[n];
        for(int i = 0; i < n; i++){
            float a = i * 6.28318f / n;
            float rr = 9f + 5f * (((p.seed >> (i * 2)) & 3) / 3f);
            xs[i] = 16 + (float)Math.cos(a) * rr; ys[i] = 17 + (float)Math.sin(a) * rr * 0.9f;
        }
        poly(xs, ys, mul(base, 0.55f), 1f);
        float[] x2 = new float[n], y2 = new float[n];
        for(int i = 0; i < n; i++){ x2[i] = 16 + (xs[i] - 16) * 0.78f - 1; y2[i] = 16 + (ys[i] - 17) * 0.78f; }
        poly(x2, y2, base, 1f);
        cracks(p, 0xffb040, 3 + (p.seed & 3));
    }

    private void capsule(MaterialProfile p, int base){
        disc(16, 16, 10, mul(base, 0.5f));
        disc(15, 15, 9, base);
        disc(12, 12, 4f, lighten(base, 0.5f), 0.7f);
        //Warnband
        for(int x = 6; x < 26; x++) for(int y = 16; y < 19; y++) if(inside(x, y, 16, 16, 10) && ((x + y) / 3) % 2 == 0) set(x, y, mul(base, 0.35f));
        int spikes = 3 + (p.seed & 3);
        for(int i = 0; i < spikes; i++){
            float a = i * 6.28318f / spikes + 0.4f;
            line(16 + (float)Math.cos(a) * 10, 16 + (float)Math.sin(a) * 10, 16 + (float)Math.cos(a) * 12.5f, 16 + (float)Math.sin(a) * 12.5f, mul(base, 0.4f));
        }
    }

    //------------------------------------------------------------------ Oberflaeche
    private void traces(MaterialProfile p, int col){
        int branches = 3 + (p.seed & 3);
        for(int i = 0; i < branches; i++){
            int x = 8 + next(16), y = 9 + next(14);
            int dir = next(2);
            for(int s = 0; s < 3; s++){
                int len = 3 + next(6);
                int nx = dir == 0 ? clampi(x + (next(2) == 0 ? len : -len), 6, 26) : x;
                int ny = dir == 1 ? clampi(y + (next(2) == 0 ? len : -len), 9, 23) : y;
                line(x, y, nx, ny, col);
                x = nx; y = ny; dir = 1 - dir;
            }
            disc(x, y, 1.1f, 0xffffff);
        }
    }

    private void cracks(MaterialProfile p, int col, int count){
        for(int i = 0; i < count; i++){
            float x = 10 + next(12), y = 11 + next(12);
            for(int s = 0; s < 4; s++){
                float nx = x + (next(7) - 3), ny = y + (next(7) - 3);
                line(x, y, nx, ny, col);
                set(Math.round(x), Math.round(y), 0xffe090);
                x = nx; y = ny;
            }
        }
    }

    private void strata(int base, MaterialProfile p){
        for(int y = 9; y < 25; y += 4 + (p.seed >> 3 & 1)){
            for(int x = 5; x < 28; x++) if(get(x, y) != 0) set(x, y, mul(blend(get(x, y) >>> 8, base, 0.2f), 0.82f));
        }
    }

    private void inclusions(int[] palette, float[] shares, MaterialProfile p){
        for(int i = 0; i < palette.length; i++){
            int count = 1 + Math.round(shares[i] * 6f);
            if(shares[i] > 0.6f) count = 1; //Hauptbestandteil ist schon die Grundfarbe
            for(int k = 0; k < count; k++){
                int x = 7 + next(18), y = 8 + next(17);
                if(get(x, y) == 0) continue;
                disc(x, y, shares[i] < 0.2f ? 0.9f : 1.5f, palette[i] & 0xffffff);
                set(x, y, lighten(palette[i] & 0xffffff, 0.35f));
            }
        }
    }

    private void outline(int col){
        int[] copy = px.clone();
        for(int y = 0; y < SIZE; y++) for(int x = 0; x < SIZE; x++){
            if(copy[y * SIZE + x] != 0) continue;
            boolean near = false;
            for(int dy = -1; dy <= 1 && !near; dy++) for(int dx = -1; dx <= 1; dx++){
                int nx = x + dx, ny = y + dy;
                if(nx < 0 || ny < 0 || nx >= SIZE || ny >= SIZE) continue;
                if(copy[ny * SIZE + nx] != 0 && (dx == 0 || dy == 0)){ near = true; break; }
            }
            if(near) px[y * SIZE + x] = (col << 8) | 0xff;
        }
    }

    //------------------------------------------------------------------ Primitive (RGB in, RGBA8888 raus)
    private void set(int x, int y, int rgb){
        if(x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;
        px[y * SIZE + x] = (rgb << 8) | 0xff;
    }
    private void setA(int x, int y, int rgb, float a){
        if(x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;
        int old = px[y * SIZE + x];
        if(old == 0 || a >= 1f){ set(x, y, rgb); return; }
        set(x, y, blend(old >>> 8, rgb, a));
    }
    private int get(int x, int y){ return x < 0 || y < 0 || x >= SIZE || y >= SIZE ? 0 : px[y * SIZE + x]; }

    private void poly(float[] xs, float[] ys, int rgb, float a){
        for(int y = 0; y < SIZE; y++) for(int x = 0; x < SIZE; x++){
            if(contains(xs, ys, x + 0.5f, y + 0.5f)) setA(x, y, rgb, a);
        }
    }
    private static boolean contains(float[] xs, float[] ys, float px, float py){
        boolean in = false;
        for(int i = 0, j = xs.length - 1; i < xs.length; j = i++){
            if(((ys[i] > py) != (ys[j] > py)) && (px < (xs[j] - xs[i]) * (py - ys[i]) / (ys[j] - ys[i]) + xs[i])) in = !in;
        }
        return in;
    }
    private void disc(float cx, float cy, float r, int rgb){ disc(cx, cy, r, rgb, 1f); }
    private void disc(float cx, float cy, float r, int rgb, float a){
        for(int y = 0; y < SIZE; y++) for(int x = 0; x < SIZE; x++){
            float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
            if(dx * dx + dy * dy <= r * r) setA(x, y, rgb, a);
        }
    }
    private void ring(float cx, float cy, float r, float w, int rgb){
        for(int y = 0; y < SIZE; y++) for(int x = 0; x < SIZE; x++){
            float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
            float d = (float)Math.sqrt(dx * dx + dy * dy);
            if(Math.abs(d - r) <= w) setA(x, y, rgb, 1f);
        }
    }
    private static boolean inside(int x, int y, float cx, float cy, float r){
        float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
        return dx * dx + dy * dy <= r * r;
    }
    private void line(float x0, float y0, float x1, float y1, int rgb){
        int steps = (int)Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 2);
        for(int i = 0; i <= steps; i++){
            float t = i / (float)steps;
            set(Math.round(x0 + (x1 - x0) * t), Math.round(y0 + (y1 - y0) * t), rgb);
        }
    }

    private int next(int n){
        rng = rng * 1664525 + 1013904223;
        return ((rng >>> 8) & 0xffff) % n;
    }

    static int clampi(int v, int lo, int hi){ return v < lo ? lo : (v > hi ? hi : v); }
    static int rgb(float r, float g, float b){
        return (Math.round(clamp(r) * 255f) << 16) | (Math.round(clamp(g) * 255f) << 8) | Math.round(clamp(b) * 255f);
    }
    static float clamp(float v){ return v < 0 ? 0 : (v > 1 ? 1 : v); }
    static int mul(int rgb, float f){
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * f)), g = Math.min(255, Math.round(((rgb >> 8) & 255) * f)), b = Math.min(255, Math.round((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }
    static int lighten(int rgb, float f){ return blend(rgb, 0xffffff, f); }
    static int blend(int a, int b, float t){
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t),
            bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
