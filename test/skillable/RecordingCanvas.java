package skillable;

import skillable.material.Canvas;

/** Testcanvas: zeichnet nichts, zaehlt Primitive und prueft Endlichkeit, Farbbereich und Reichweite um einen Ursprung. */
final class RecordingCanvas implements Canvas{
    float ox, oy, maxDist;
    int prims, bad, additiveCalls;
    float r = 1, g = 1, b = 1, a = 1;

    void reset(float ox, float oy){ this.ox = ox; this.oy = oy; maxDist = 0; prims = 0; bad = 0; additiveCalls = 0; }

    private void pt(float x, float y, float extra){
        if(Float.isNaN(x) || Float.isInfinite(x) || Float.isNaN(y) || Float.isInfinite(y) || Float.isNaN(extra) || Float.isInfinite(extra) || extra < 0f){ bad++; return; }
        float d = (float)Math.hypot(x - ox, y - oy) + extra;
        if(d > maxDist) maxDist = d;
    }

    private void check(){
        if(Float.isNaN(a) || a < -0.001f || a > 1.001f || Float.isNaN(r) || r < -0.001f || r > 1.001f || Float.isNaN(g) || g < -0.001f || g > 1.001f || Float.isNaN(b) || b < -0.001f || b > 1.001f) bad++;
    }

    @Override public void color(float r, float g, float b, float a){ this.r = r; this.g = g; this.b = b; this.a = a; }
    @Override public void additive(boolean on){ additiveCalls++; }
    @Override public void fillCircle(float x, float y, float rad){ prims++; check(); pt(x, y, rad); }
    @Override public void ring(float x, float y, float rad, float stroke){ prims++; check(); pt(x, y, rad + stroke * 0.5f); }
    @Override public void line(float x1, float y1, float x2, float y2, float stroke){ prims++; check(); pt(x1, y1, stroke * 0.5f); pt(x2, y2, stroke * 0.5f); }
    @Override public void tri(float x, float y, float w, float len, float rot){
        prims++; check();
        double rr = Math.toRadians(rot);
        pt(x, y, w * 0.5f); pt((float)(x + Math.cos(rr) * len), (float)(y + Math.sin(rr) * len), 0f);
    }
    @Override public void poly(float x, float y, int sides, float rad, float rot){ prims++; check(); if(sides < 3) bad++; pt(x, y, rad); }
    @Override public void polyLine(float x, float y, int sides, float rad, float rot, float stroke){ prims++; check(); if(sides < 3) bad++; pt(x, y, rad + stroke); }
    @Override public void arc(float x, float y, float rad, float frac, float rot, float stroke){ prims++; check(); pt(x, y, rad + stroke); }
}
