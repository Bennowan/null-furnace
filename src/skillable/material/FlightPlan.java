package skillable.material;

/**
 * Flugbild eines Materials: Kernform, pulsierender Kern, mehrschichtige/geflochtene Spuren, abgeloeste Partikel, Entladungen und
 * Energiebaender. Reine Funktion von {@link MaterialProfile} und {@link ShotSpec}; gezeichnet von {@link FlightRenderer}.
 */
public final class FlightPlan{
    public static final int SHED_NONE = 0, SHED_EMBER = 1, SHED_SHARD = 2, SHED_DROPLET = 3, SHED_SPARK = 4, SHED_MOTE = 5;
    public static final String[] SHED_NAMES = {"none", "ember", "shard", "droplet", "spark", "mote"};

    public int form, sides, seed, layers, shed, shedCount, arcs, bands, cost;
    public float width, height, pulseHz, pulseAmp, flicker, halo, spin, wobble, arcLen, shedRate, fieldArcs;
    public boolean braid;
    public final float[] layerLen = new float[3], layerAmp = new float[3], layerWidth = new float[3];
    public final float[] front = new float[3], back = new float[3], glow = new float[3];

    public static FlightPlan build(MaterialProfile p, ShotSpec s){
        ReactionSignature g = new ReactionSignature(p, s);
        FlightPlan f = new FlightPlan();
        f.form = s.form; f.sides = s.sides; f.seed = p.seed;
        f.width = s.width; f.height = s.height;
        System.arraycopy(s.front, 0, f.front, 0, 3);
        System.arraycopy(s.back, 0, f.back, 0, 3);
        System.arraycopy(s.glow, 0, f.glow, 0, 3);

        f.pulseHz = 0.05f + 0.10f * g.energy;
        f.pulseAmp = 0.08f + 0.20f * g.energy;
        f.flicker = g.instability > 0.3f ? 0.35f * g.instability : 0f;
        f.halo = g.energy > 0.3f ? 1.1f + 0.8f * g.energy : 0f;
        f.layers = 1 + (g.energy > 0.35f ? 1 : 0) + (g.complexity > 0.5f ? 1 : 0);
        f.braid = g.electric > 0.35f || g.instability > 0.5f || g.field > 0.4f;
        for(int i = 0; i < 3; i++){
            f.layerLen[i] = (8f + 14f * g.energy) * (1f + 0.45f * i);
            f.layerAmp[i] = (f.braid ? 1.5f + 2.5f * Math.max(g.instability, g.electric) : 0.6f) * (1f + 0.3f * i);
            f.layerWidth[i] = Math.max(0.8f, (s.form == ShotSpec.EMBER ? 2.6f : 1.8f) - 0.4f * i);
        }
        if(g.thermal > 0.4f) f.shed = SHED_EMBER;
        else if(g.brittle > 0.4f) f.shed = SHED_SHARD;
        else if(g.fluid > 0.35f) f.shed = SHED_DROPLET;
        else if(g.electric > 0.4f) f.shed = SHED_SPARK;
        else if(g.field > 0.35f) f.shed = SHED_MOTE;
        f.shedCount = f.shed == SHED_NONE ? 0 : 2 + Math.round(6f * g.complexity);
        f.shedRate = 0.6f + 0.8f * g.energy;
        f.arcs = s.lightning > 0 ? 1 + (s.lightning >= 5 ? 1 : 0) : 0;
        f.arcLen = 8f + 6f * g.electric;
        f.bands = g.complexity > 0.55f ? 2 : (g.complexity > 0.35f ? 1 : 0);
        f.fieldArcs = g.field > 0.35f ? 1f + g.field : 0f;
        f.spin = g.brittle > 0.35f ? 4f + 10f * g.brittle : 0f;
        f.wobble = 12f * g.instability;
        f.cost = f.layers * 6 + f.shedCount + f.arcs * 5 + f.bands * 2 + 8;
        return f;
    }

    /** Hoechster Abstand vom Geschossmittelpunkt, den die Zeichnung erreicht. */
    public float extent(){
        float len = Math.max(layerLen[0], Math.max(layerLen[1], layerLen[2]));
        return 14f + len * 1.3f + height * 1.5f + width * 3f + arcLen;
    }

    public String fingerprint(){
        return form + "/" + layers + "/" + (braid ? "b" : "s") + "/" + SHED_NAMES[shed] + "/" + arcs + "/" + bands + "/" + (halo > 0f ? "h" : "-") + "/"
            + (flicker > 0f ? "f" : "-") + "/" + (fieldArcs > 0f ? "F" : "-") + "/" + (spin > 0f ? "r" : "-");
    }
}
