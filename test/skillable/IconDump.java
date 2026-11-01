package skillable;

import skillable.material.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/** Entwickler-Werkzeug: schreibt einen Kontaktbogen der Material-Icons (build/icons.png). */
public class IconDump{
    public static void main(String[] a) throws Exception{
        List<Object[]> sets = new ArrayList<>();
        sets.add(new Object[]{MaterialFixtures.copper, 3, MaterialFixtures.coal, 2});
        sets.add(new Object[]{MaterialFixtures.titanium, 4, MaterialFixtures.graphite, 2, MaterialFixtures.sand, 1});
        sets.add(new Object[]{MaterialFixtures.copper, 5, MaterialFixtures.surge, 3});
        sets.add(new Object[]{MaterialFixtures.silicon, 3, MaterialFixtures.copper, 3, MaterialFixtures.cryo, 30});
        sets.add(new Object[]{MaterialFixtures.oil, 40, MaterialFixtures.slag, 40});
        sets.add(new Object[]{MaterialFixtures.blast, 4, MaterialFixtures.pyratite, 2});
        sets.add(new Object[]{MaterialFixtures.thorium, 3, MaterialFixtures.phase, 3});
        sets.add(new Object[]{MaterialFixtures.phase, 4, MaterialFixtures.surge, 2, MaterialFixtures.silicon, 2});
        sets.add(new Object[]{MaterialFixtures.coal, 4, MaterialFixtures.spore, 2});
        sets.add(new Object[]{MaterialFixtures.tungsten, 4, MaterialFixtures.carbide, 2});
        sets.add(new Object[]{MaterialFixtures.lead, 3, MaterialFixtures.metaglass, 3});
        sets.add(new Object[]{MaterialFixtures.sand, 3, MaterialFixtures.scrap, 3, MaterialFixtures.water, 20});
        sets.add(new Object[]{MaterialFixtures.beryllium, 3, MaterialFixtures.graphite, 3, MaterialFixtures.titanium, 2, MaterialFixtures.copper, 1, MaterialFixtures.lead, 1});
        sets.add(new Object[]{MaterialFixtures.slag, 30, MaterialFixtures.silicon, 3});
        sets.add(new Object[]{MaterialFixtures.thorium, 5, MaterialFixtures.oil, 20});
        sets.add(new Object[]{MaterialFixtures.surge, 4, MaterialFixtures.titanium, 2, MaterialFixtures.cryo, 20});
        int cols = 8, scale = 6;
        int rows = (sets.size() + cols - 1) / cols;
        BufferedImage img = new BufferedImage(cols * (IconPainter.SIZE * scale + 8) + 8, rows * (IconPainter.SIZE * scale + 8) + 8, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(0x2b2f3a)); g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for(int i = 0; i < sets.size(); i++){
            Composition c = MaterialFixtures.mix(sets.get(i));
            MaterialProfile p = MaterialProfile.of(c);
            int[] pal = new int[c.count()]; float[] sh = new float[c.count()];
            for(int k = 0; k < pal.length; k++){
                IngredientDef d = c.defs[k];
                pal[k] = (Math.round(d.r * 255) << 16) | (Math.round(d.g * 255) << 8) | Math.round(d.b * 255);
                sh[k] = c.share(k);
            }
            int[] px = IconPainter.paint(p, pal, sh);
            int ox = 8 + (i % cols) * (IconPainter.SIZE * scale + 8), oy = 8 + (i / cols) * (IconPainter.SIZE * scale + 8);
            for(int y = 0; y < IconPainter.SIZE; y++) for(int x = 0; x < IconPainter.SIZE; x++){
                int v = px[y * IconPainter.SIZE + x];
                if((v & 255) == 0) continue;
                int argb = (0xff << 24) | (v >>> 8);
                for(int dy = 0; dy < scale; dy++) for(int dx = 0; dx < scale; dx++) img.setRGB(ox + x * scale + dx, oy + y * scale + dy, argb);
            }
            System.out.printf("%2d prim=%d sec=%d form=%d %s%n", i, p.primary, p.secondary, p.form(), c.signature);
        }
        ImageIO.write(img, "png", new File("build/icons.png"));
    }
}
