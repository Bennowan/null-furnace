package skillable.material;

/**
 * Minimale Zeichenflaeche fuer die Effektlogik. In der Engine zeichnet {@code synth/ArcCanvas} mit Arc; im Test zeichnet
 * ein Aufzeichnungs-Canvas mit, so dass die Zeichenlogik (Anzahl, Grenzen, Endlichkeit) ohne Grafikkarte pruefbar ist.
 * Winkel in Grad, Laengen in Welteinheiten.
 */
public interface Canvas{
    void color(float r, float g, float b, float a);
    void additive(boolean on);
    void fillCircle(float x, float y, float r);
    void ring(float x, float y, float r, float stroke);
    void line(float x1, float y1, float x2, float y2, float stroke);
    /** Dreieck mit Grundlinie {@code width} bei (x,y), Spitze nach {@code rot}, Laenge {@code length}. */
    void tri(float x, float y, float width, float length, float rot);
    void poly(float x, float y, int sides, float r, float rot);
    void polyLine(float x, float y, int sides, float r, float rot, float stroke);
    /** Kreisbogen, frac = Anteil des Vollkreises. */
    void arc(float x, float y, float r, float frac, float rot, float stroke);
}
