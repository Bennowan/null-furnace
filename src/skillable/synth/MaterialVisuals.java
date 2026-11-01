package skillable.synth;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.util.*;
import skillable.material.*;

import static mindustry.Vars.*;

/** Setzt Name, Farbe, Beschreibung, Stoffwerte und das generierte Icon eines Traeger-Items anhand des Materialprofils. */
public final class MaterialVisuals{
    private MaterialVisuals(){}

    private static final Texture[] textures = new Texture[MaterialRegistry.SLOTS];
    private static final TextureRegion[] baseRegion = new TextureRegion[MaterialRegistry.SLOTS];

    static String text(String key, String fallback){
        return Core.bundle == null ? fallback : Core.bundle.get(key, fallback);
    }

    public static String className(int cls){
        return text("material.class." + cls, new String[]{"kinetic", "electric", "thermal", "explosive", "flux"}[cls]);
    }

    public static String formName(int form){
        return text("material.form." + form, new String[]{"Crystal", "Alloy", "Slurry", "Composite"}[form]);
    }

    public static String hazardName(int hazard){
        return text("material.hazard." + hazard, new String[]{"stable", "unstable", "critical", "runaway"}[hazard]);
    }

    public static String displayName(MaterialProfile p){
        String fmt = text("material.name", "{0} {1} ({2})");
        return Strings.format(fmt.replace("{0}", "@").replace("{1}", "@").replace("{2}", "@"), formName(p.form()), p.code(), className(p.primary));
    }

    static void apply(SynthItem item, MaterialProfile p, Composition comp, boolean corrupt){
        if(p == null){
            item.localizedName = text(corrupt ? "material.corrupt" : "material.unknown", corrupt ? "Damaged material" : "Unassigned material");
            item.description = text("material.unknown.description", "This material slot has no composition. It is inert and cannot be used.");
            item.color = new Color(0.45f, 0.47f, 0.52f);
            item.flammability = item.explosiveness = item.radioactivity = item.charge = 0f;
            resetIcon(item);
            return;
        }
        item.localizedName = displayName(p);
        item.color = new Color(p.r, p.g, p.b);
        item.flammability = p.flammability;
        item.explosiveness = p.explosiveness;
        item.radioactivity = p.radioactivity;
        item.charge = p.conductivity * 0.8f;
        item.description = describe(p, comp);
        if(!headless) buildIcon(item, p, comp);
    }

    static String describe(MaterialProfile p, Composition comp){
        StringBuilder sb = new StringBuilder();
        if(comp != null){
            sb.append(text("material.composition", "Composition")).append(": ");
            for(int i = 0; i < comp.count(); i++){
                if(i > 0) sb.append(", ");
                sb.append(comp.defs[i].label).append(' ').append(Math.round(comp.share(i) * 100f)).append('%');
            }
            sb.append('\n');
        }
        sb.append(text("material.profile", "Class")).append(": ").append(className(p.primary));
        if(p.secondary >= 0) sb.append(" / ").append(className(p.secondary));
        sb.append("  |  ").append(text("material.stability", "Stability")).append(": ").append(hazardName(p.hazard));
        sb.append('\n').append(text("material.ammo", "Turret ammo")).append(": ")
            .append(Strings.fixed(p.fluxPerTick, 1)).append(" flux/tick, ").append(p.shotsPerItem).append(' ').append(text("material.shots", "shots per item"));
        return sb.toString();
    }

    private static void resetIcon(SynthItem item){
        if(headless) return;
        Core.app.post(() -> {
            if(textures[item.slot] != null){ textures[item.slot].dispose(); textures[item.slot] = null; }
            if(baseRegion[item.slot] != null){ item.fullIcon = item.uiIcon = baseRegion[item.slot]; }
        });
    }

    private static void buildIcon(SynthItem item, MaterialProfile p, Composition comp){
        int[] pal = null; float[] sh = null;
        if(comp != null){
            pal = new int[comp.count()]; sh = new float[comp.count()];
            for(int i = 0; i < pal.length; i++){
                IngredientDef d = comp.defs[i];
                pal[i] = (Math.round(d.r * 255f) << 16) | (Math.round(d.g * 255f) << 8) | Math.round(d.b * 255f);
                sh[i] = comp.share(i);
            }
        }
        int[] px = IconPainter.paint(p, pal, sh);
        Core.app.post(() -> {
            try{
                if(baseRegion[item.slot] == null) baseRegion[item.slot] = item.fullIcon;
                Pixmap pm = new Pixmap(IconPainter.SIZE, IconPainter.SIZE);
                for(int y = 0; y < IconPainter.SIZE; y++) for(int x = 0; x < IconPainter.SIZE; x++) pm.setRaw(x, y, px[y * IconPainter.SIZE + x]);
                Texture t = new Texture(pm);
                pm.dispose();
                t.setFilter(Texture.TextureFilter.nearest);
                if(textures[item.slot] != null) textures[item.slot].dispose();
                textures[item.slot] = t;
                TextureRegion r = new TextureRegion(t);
                item.fullIcon = r;
                item.uiIcon = r;
            }catch(Throwable e){
                Log.err("[FluxProtocol] Icon konnte nicht erzeugt werden", e);
            }
        });
    }
}
