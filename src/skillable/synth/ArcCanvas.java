package skillable.synth;

import arc.graphics.*;
import arc.graphics.g2d.*;
import mindustry.graphics.*;
import skillable.material.*;

/** Zeichnet die plattformunabhaengige Effektlogik mit Arc/Mindustry. */
public final class ArcCanvas implements Canvas{
    public static final ArcCanvas I = new ArcCanvas();

    private ArcCanvas(){}

    @Override public void color(float r, float g, float b, float a){ Draw.color(r, g, b, a); }
    @Override public void additive(boolean on){ if(on) Draw.blend(Blending.additive); else Draw.blend(); }
    @Override public void fillCircle(float x, float y, float r){ Fill.circle(x, y, r); }
    @Override public void ring(float x, float y, float r, float stroke){ Lines.stroke(stroke); Lines.circle(x, y, r); }
    @Override public void line(float x1, float y1, float x2, float y2, float stroke){ Lines.stroke(stroke); Lines.line(x1, y1, x2, y2); }
    @Override public void tri(float x, float y, float width, float length, float rot){ Drawf.tri(x, y, width, length, rot); }
    @Override public void poly(float x, float y, int sides, float r, float rot){ Fill.poly(x, y, sides, r, rot); }
    @Override public void polyLine(float x, float y, int sides, float r, float rot, float stroke){ Lines.stroke(stroke); Lines.poly(x, y, sides, r, rot); }
    @Override public void arc(float x, float y, float r, float frac, float rot, float stroke){ Lines.stroke(stroke); Lines.arc(x, y, r, frac, rot); }
}
