package skillable;

import skillable.material.*;

import java.util.*;

import static skillable.MaterialFixtures.*;

/**
 * Spielunabhaengige Tests der freien Materialsynthese (Zusammensetzung, Profil, Gefahr, Geschosswerte, Icons).
 * Alles hier laeuft ohne Mindustry; Tests mit dem echten Spiel (Register, Fuser, Turm, Speichern) stehen im Selftest-Mod.
 */
public class MaterialTests{
    static int passed, failed;

    static void check(boolean cond, String name){
        if(cond){ passed++; System.out.println("  OK   " + name); }
        else{ failed++; System.out.println("  FAIL " + name); }
    }

    static Composition comp(IngredientDef[] defs, float... parts){ return Composition.of(Arrays.asList(defs), parts); }

    static boolean sameProfile(MaterialProfile a, MaterialProfile b){
        return a.hash == b.hash && Arrays.equals(a.toArray(), b.toArray()) && a.peakHeat == b.peakHeat && a.primary == b.primary
            && a.secondary == b.secondary && a.fluxPerTick == b.fluxPerTick && a.risk == b.risk && a.hazard == b.hazard;
    }

    public static void main(String[] args){
        System.out.println("[1] Zutatenzahl 1..11");
        {
            check(profile(copper, 3) != null && Composition.of(List.of(copper), new float[]{3}).count() == 1, "Eine einzelne Zutat ist gueltig");
            check(mix(copper, 2, coal, 2).count() == 2, "Zwei Zutaten");
            check(mix(copper, 1, coal, 1, lead, 1).count() == 3 && mix(copper, 1, coal, 1, lead, 1, sand, 1).count() == 4
                && mix(copper, 1, coal, 1, lead, 1, sand, 1, scrap, 1).count() == 5, "Drei, vier und fuenf Zutaten");
            IngredientDef[] eleven = {copper, lead, metaglass, graphite, sand, coal, titanium, scrap, silicon, water, cryo};
            float[] amt = new float[11]; Arrays.fill(amt, 2f);
            Composition c11 = comp(eleven, amt);
            check(c11 != null && c11.count() == 11, "Elf unterschiedliche Zutaten (8 Items + 3 ... gemischt mit Fluessigkeiten)");
            MaterialProfile p11 = MaterialProfile.of(c11);
            check(p11.ingredients == 11, "Profil kennt die Zutatenzahl 11");
            IngredientDef[] twelve = new IngredientDef[12];
            System.arraycopy(eleven, 0, twelve, 0, 11);
            twelve[11] = thorium;
            float[] a12 = new float[12]; Arrays.fill(a12, 1f);
            check(comp(twelve, a12) == null, "Zwoelf Zutaten werden abgelehnt (Maximum 11)");
            check(Composition.of(new ArrayList<>(), new float[0]) == null, "Leere Eingabe ergibt keine Zusammensetzung");
            check(comp(new IngredientDef[]{copper, coal}, 0f, -3f) == null, "Nur Nullmengen/negative Mengen ergeben nichts");
            check(comp(new IngredientDef[]{copper, coal}, Float.NaN, 2f).count() == 1, "NaN-Menge wird ignoriert");
        }

        System.out.println("[2] Feste Stoffe + Fluessigkeiten");
        {
            Composition c = mix(copper, 4, water, 40, oil, 20);
            MaterialProfile p = MaterialProfile.of(c);
            boolean hasLiquid = false, hasSolid = false;
            for(IngredientDef d : c.defs){ if(d.liquid) hasLiquid = true; else hasSolid = true; }
            check(hasLiquid && hasSolid, "Feste Zutat und Fluessigkeiten in einer Zusammensetzung");
            Composition more = mix(copper, 4, water, 80, oil, 20);
            check(!c.signature.equals(more.signature), "Fluessigkeitsmenge veraendert das Verhaeltnis und damit die Identitaet");
            check(p.viscosity < 1f, "Fluessigkeitsanteil geht in das Profil ein (Viskositaet < 1)");
            check(profile(copper, 4, water, 40).thermalMass != profile(copper, 4, cryo, 40).thermalMass, "Verschiedene Fluessigkeiten ergeben verschiedene Waermekapazitaet");
        }

        System.out.println("[3] Reihenfolge, Verhaeltnis, Reproduzierbarkeit");
        {
            Composition a = mix(copper, 3, coal, 1, water, 20);
            Composition b = mix(water, 20, copper, 3, coal, 1);
            Composition c = mix(coal, 1, water, 20, copper, 3);
            check(a.signature.equals(b.signature) && b.signature.equals(c.signature) && a.hash == c.hash, "Eingangsreihenfolge aendert Signatur/Hash nicht");
            check(sameProfile(MaterialProfile.of(a), MaterialProfile.of(c)), "Eingangsreihenfolge aendert das Profil nicht");
            check(mix(copper, 3, coal, 1).signature.equals(mix(copper, 6, coal, 2).signature), "Gleiches Verhaeltnis in anderer Chargengroesse = gleiche Identitaet");
            check(!mix(copper, 3, coal, 1).signature.equals(mix(copper, 1, coal, 3).signature), "Umgekehrtes Verhaeltnis = andere Identitaet");
            check(mix(copper, 3, coal, 1).parts == 4f && mix(copper, 6, coal, 2).parts == 8f, "Absolute Menge bleibt als Chargengroesse erhalten (nicht Teil der Identitaet)");
            MaterialProfile p1 = MaterialProfile.of(mix(copper, 3, coal, 1)), p2 = MaterialProfile.of(mix(copper, 3, coal, 1));
            check(sameProfile(p1, p2), "Wiederholte Berechnung liefert bitgleiches Profil");
            MaterialProfile q1 = MaterialProfile.of(mix(copper, 3, coal, 1)), q2 = MaterialProfile.of(mix(copper, 1, coal, 3));
            check(!Arrays.equals(q1.toArray(), q2.toArray()), "Unterschiedliche Verhaeltnisse ergeben unterschiedliche Eigenschaften");
        }

        System.out.println("[4] Quantisierung und Hash");
        {
            Composition extreme = mix(copper, 100, coal, 1, sand, 1);
            int sum = 0, min = 99;
            for(int w : extreme.weights){ sum += w; min = Math.min(min, w); }
            check(sum == Composition.QUANT && min >= 1, "Quantisierung: Summe = 16, jede Zutat mindestens 1 Teil (100:1:1)");
            Random rnd = new Random(7);
            Map<String, Long> seen = new HashMap<>();
            int dup = 0, clash = 0;
            for(int t = 0; t < 30000; t++){
                int k = 1 + rnd.nextInt(8);
                List<IngredientDef> d = new ArrayList<>(); float[] p = new float[k];
                for(int i = 0; i < k; i++){
                    d.add(rnd.nextFloat() < .25f ? LIQUIDS[rnd.nextInt(4)] : ITEMS[rnd.nextInt(ITEMS.length)]);
                    p[i] = 1 + rnd.nextInt(9);
                }
                Composition c = Composition.of(d, p);
                Long prev = seen.put(c.signature, c.hash);
                if(prev != null && prev != c.hash) clash++;
                if(prev != null) dup++;
            }
            Set<Long> hashes = new HashSet<>(seen.values());
            check(clash == 0, "Gleiche Signatur -> immer gleicher Hash");
            check(hashes.size() == seen.size(), "Verschiedene Signaturen -> verschiedene Hashes (" + seen.size() + " Zusammensetzungen, keine Kollision)");
            check(seen.size() < 30000 && dup > 0, "Quantisierung fasst aehnliche Mischungen zusammen (" + seen.size() + " statt 30000 Varianten)");
        }

        System.out.println("[5] Signatur: Speichern und Laden");
        {
            Map<String, IngredientDef> all = new HashMap<>();
            for(IngredientDef d : ITEMS) all.put(d.key, d);
            for(IngredientDef d : LIQUIDS) all.put(d.key, d);
            Composition c = mix(copper, 3, coal, 1, water, 20, surge, 2);
            Composition r = Composition.parse(c.signature, all::get);
            check(r != null && r.signature.equals(c.signature) && r.hash == c.hash, "Signatur laesst sich verlustfrei zuruecklesen");
            check(sameProfile(MaterialProfile.of(c), MaterialProfile.of(r)), "Zurueckgelesene Zusammensetzung ergibt dasselbe Profil");
            MaterialProfile p = MaterialProfile.of(c);
            MaterialProfile restored = new MaterialProfile(p.signature, p.hash, p.toArray());
            check(sameProfile(p, restored), "Gespeicherte Mischwerte stellen das Profil bitgleich wieder her");
            check(Composition.parse("c9;item:copper=8;item:coal=8", all::get) == null, "Unbekannte Formatversion wird abgelehnt");
            check(Composition.parse("c1;item:copper=8;item:coal=8", all::get) == null, "Nicht sortierte Zutaten werden abgelehnt");
            check(Composition.parse("c1;item:coal=8;item:copper=8", all::get) != null, "Korrekt sortierte Signatur wird akzeptiert");
            check(Composition.parse("c1;item:coal=8;item:coal=8", all::get) == null, "Doppelte Zutat wird abgelehnt");
            check(Composition.parse("c1;item:coal=8;item:copper=7", all::get) == null, "Gewichte ungleich 16 werden abgelehnt");
            check(Composition.parse("c1;item:copper=16;item:nichtda=0", all::get) == null, "Gewicht 0 wird abgelehnt");
            check(Composition.parse("c1;item:copper=8;item:unbekannt=8", all::get) == null, "Unbekannte Zutat -> kontrolliert abgelehnt, kein Absturz");
            check(Composition.parse("", all::get) == null && Composition.parse(null, all::get) == null, "Leere/null-Signatur wird abgelehnt");
            check(Composition.parse("c1;" + "x".repeat(700), all::get) == null, "Ueberlange Signatur wird abgelehnt");
            check(Composition.parse("c1;item:copper=abc;item:coal=8", all::get) == null, "Nichtnumerisches Gewicht wird abgelehnt");
            //Verschachtelung: ein Material als Zutat
            MaterialProfile base = MaterialProfile.of(mix(copper, 3, coal, 1));
            IngredientDef asIngredient = IngredientTraits.material(base);
            check(asIngredient.key.startsWith("mat:") && asIngredient.key.equals("mat:" + Long.toHexString(base.hash)), "Material als Zutat wird ueber seinen Hash identifiziert (nicht ueber den Slot)");
            MaterialProfile chained = MaterialProfile.of(comp(new IngredientDef[]{asIngredient, surge}, 2, 2));
            check(chained.conductivity > base.conductivity * 0.5f, "Mehrstufige Synthese: Eigenschaften des Zwischenprodukts gehen ein");
        }

        System.out.println("[6] Materialklassen");
        {
            check(profile(copper, 5, surge, 3).primary == MaterialProfile.CLASS_ELECTRIC, "Kupfer + Surge: elektrisch");
            check(profile(thorium, 3, phase, 3).primary == MaterialProfile.CLASS_FLUX, "Thorium + Phasengewebe: Flux");
            check(profile(blast, 5).primary == MaterialProfile.CLASS_EXPLOSIVE, "Sprengmittel: explosiv");
            check(profile(tungsten, 4, titanium, 2).primary == MaterialProfile.CLASS_KINETIC, "Wolfram + Titan: kinetisch");
            check(profile(pyratite, 4, slag, 30).primary == MaterialProfile.CLASS_THERMAL || profile(pyratite, 4, slag, 30).secondary == MaterialProfile.CLASS_THERMAL, "Pyratit + Schlacke: thermisch beteiligt");
            Set<Integer> classes = new HashSet<>();
            for(IngredientDef d : ITEMS) classes.add(profile(d, 4).primary);
            check(classes.size() >= 4, "Reine Zutaten decken mindestens vier Klassen ab (" + classes.size() + ")");
            MaterialProfile a = profile(copper, 6, sand, 2), b = profile(copper, 2, sand, 6);
            check(a.conductivity > b.conductivity, "Mehr Kupfer = hoehere Leitfaehigkeit");
            check(profile(copper, 4, lead, 4).conductivity > profile(copper, 2, sand, 6).conductivity, "Leitfaehige Netzwerke (Perkolation) leiten besser als verduennte");
            check(profile(tungsten, 4, carbide, 4).hardness > profile(sand, 8).hardness, "Harte Zutaten ergeben harte Materialien");
        }

        System.out.println("[7] Stabilitaet und gefaehrliche Reaktionen");
        {
            MaterialProfile os = profile(oil, 5, slag, 5);
            check(os.hazard == MaterialProfile.HAZARD_RUNAWAY, "Oel + Schlacke (Brennstoff trifft Hitze): durchgehende Reaktion (Spitze " + String.format("%.2f", os.peakHeat) + ")");
            MaterialProfile cooled = profile(oil, 5, slag, 5, cryo, 60);
            check(cooled.peakHeat < os.peakHeat * 0.4f && cooled.hazard < MaterialProfile.HAZARD_CRITICAL, "Dieselbe Mischung mit viel Kryofluid verduennt: Spitze faellt von " + String.format("%.2f", os.peakHeat) + " auf " + String.format("%.2f", cooled.peakHeat));
            check(profile(copper, 4, water, 40).hazard == MaterialProfile.HAZARD_STABLE, "Kupfer + Wasser: stabil");
            check(profile(titanium, 3, graphite, 2, sand, 1).hazard == MaterialProfile.HAZARD_STABLE, "Titan + Graphit + Sand: stabil");
            check(profile(silicon, 3, copper, 3).hazard == MaterialProfile.HAZARD_STABLE, "Silizium + Kupfer: stabil");
            check(profile(blast, 4, pyratite, 3).hazard >= MaterialProfile.HAZARD_CRITICAL, "Sprengmittel + Pyratit: kritisch oder durchgehend");
            check(profile(slag, 5, water, 5).peakHeat > profile(water, 5, cryo, 5).peakHeat, "Schlacke + Wasser reagiert staerker als Wasser + Kryofluid");
            //Verteilung ueber eine breite (bewusst volatilitaetslastige) Zufallsstichprobe
            Random rnd = new Random(42);
            int[] h = new int[4]; int n = 20000;
            for(int t = 0; t < n; t++){
                int k = 1 + rnd.nextInt(6);
                List<IngredientDef> d = new ArrayList<>(); float[] p = new float[k];
                for(int i = 0; i < k; i++){
                    d.add(rnd.nextFloat() < .25f ? LIQUIDS[rnd.nextInt(4)] : ITEMS[rnd.nextInt(ITEMS.length)]);
                    p[i] = 1 + rnd.nextInt(6);
                }
                h[MaterialProfile.of(Composition.of(d, p)).hazard]++;
            }
            float stable = h[0] / (float)n, unstable = h[1] / (float)n, critical = h[2] / (float)n, run = h[3] / (float)n;
            System.out.printf("       Verteilung: stabil %.1f%%, instabil %.1f%%, kritisch %.1f%%, durchgehend %.1f%%%n", stable * 100, unstable * 100, critical * 100, run * 100);
            check(stable + unstable >= 0.80f, "Grossteil der Zufallsmischungen ist nutzbar (stabil+instabil >= 80%)");
            check(run > 0.01f && run < 0.12f, "Nur ein kleiner Anteil laeuft durch (1-12%)");
            check(h[1] > 0 && h[2] > 0, "Alle Stufen (instabil, kritisch) kommen vor");
        }

        System.out.println("[8] Munition: Flux-Bedarf, Risiko, Schuesse");
        {
            List<MaterialProfile> ps = diverse();
            boolean ranges = true;
            for(MaterialProfile p : ps){
                if(p.fluxPerTick < 0.6f || p.fluxPerTick > 6f || p.risk < 0.02f || p.risk > 0.36f || p.shotsPerItem < 2 || p.shotsPerItem > 10) ranges = false;
            }
            check(ranges, "Flux-Bedarf, Risiko und Schuesse pro Item liegen im Spielbereich");
            MaterialProfile weak = profile(sand, 8), strong = profile(surge, 4, thorium, 3, phase, 2);
            check(strong.fluxPerTick > weak.fluxPerTick && strong.risk > weak.risk, "Staerkere Materialien brauchen mehr Flux und belasten das Netz staerker");
            check(strong.shotsPerItem <= weak.shotsPerItem, "Staerkere Materialien liefern weniger Schuesse je Item");
            MaterialProfile unstable = profile(oil, 3, coal, 2, slag, 1);
            check(unstable.shotKick > 0f && profile(copper, 4, water, 40).shotKick == 0f, "Nur instabile Zusammensetzungen belasten das Netz bei jedem Schuss");
            check(profile(phase, 8).fluxPerTick < profile(thorium, 8).fluxPerTick * 1.6f, "Hohe Flux-Affinitaet senkt den Flux-Bedarf");
        }

        System.out.println("[9] Schusswerte: Unterschiede jenseits von Farbe und Schaden");
        {
            List<MaterialProfile> ps = diverse();
            Set<Integer> forms = new HashSet<>();
            Set<String> keys = new HashSet<>();
            for(MaterialProfile p : ps){ ShotSpec s = ShotSpec.from(p); forms.add(s.form); keys.add(s.behaviourKey()); }
            check(forms.size() == 5, "Alle fuenf Projektilformen kommen vor (" + forms.size() + ")");
            check(keys.size() >= 9, "Mindestens neun verschiedene Verhaltensprofile in " + ps.size() + " Materialien (" + keys.size() + ")");
            ShotSpec slug = ShotSpec.from(profile(tungsten, 5, titanium, 3)), bolt = ShotSpec.from(profile(copper, 5, surge, 3)),
                ember = ShotSpec.from(profile(pyratite, 4, slag, 30)), shell = ShotSpec.from(profile(blast, 5)), rift = ShotSpec.from(profile(thorium, 3, phase, 3));
            check(slug.form == ShotSpec.SLUG && bolt.form == ShotSpec.BOLT && shell.form == ShotSpec.SHELL && rift.form == ShotSpec.RIFT, "Klasse bestimmt Form");
            check(bolt.lightning >= 3 && slug.lightning == 0 && bolt.shieldBreak > 0f, "Elektrisch: Kettenblitze und Schildstoerung; kinetisch: keine");
            check(slug.pierceCap >= 1 && slug.pierceArmor, "Kinetisch hart: durchschlagend, ruestungsbrechend");
            check(shell.artillery && !shell.air && shell.splashRadius > 40f && shell.frags >= 2, "Explosiv: Artillerie mit Flaechenschaden und Splittern");
            check(rift.pierceBuilding && rift.homingPower > 0f && rift.spiral > 0f && rift.displace > 0f, "Flux: durchdringt Gebaeude, leicht zielsuchend, Spiralbahn, zieht Ziele zum Einschlag");
            check(ember.makeFire && ember.burn > 0f && (ember.zoneRadius > 0f || ember.form != ShotSpec.EMBER), "Thermisch: Brandwirkung" + (ember.form == ShotSpec.EMBER ? " und Nachbrennzone" : ""));
            check(bolt.speed > slug.speed * 0.9f && shell.speed < slug.speed, "Bewegung: Boegen schnell, Granaten langsam (" + String.format("%.1f/%.1f/%.1f", bolt.speed, slug.speed, shell.speed) + ")");
            //Gleiche Klasse, andere Zusammensetzung -> andere Werte (nicht nur Farbe)
            ShotSpec a = ShotSpec.from(profile(copper, 6, surge, 2)), b = ShotSpec.from(profile(surge, 6, copper, 2, silicon, 2));
            check(a.form == b.form && (a.lightning != b.lightning || a.speed != b.speed || a.damage != b.damage || a.shieldBreak != b.shieldBreak), "Gleiche Klasse, andere Mischung: andere Werte");
            //Sekundaerklasse fuegt Module hinzu
            MaterialProfile hybrid = profile(tungsten, 4, surge, 3);
            ShotSpec hs = ShotSpec.from(hybrid);
            if(hybrid.secondary == MaterialProfile.CLASS_ELECTRIC && hybrid.primary == MaterialProfile.CLASS_KINETIC)
                check(hs.lightning >= 2 && hs.pierceCap >= 1, "Kinetik + Elektrik: durchschlagende Slugs mit Kettenblitzen");
            else
                check(hs.form == hybrid.primary, "Hybrid-Material (Primaer " + hybrid.primary + ", Sekundaer " + hybrid.secondary + ") bleibt konsistent");
            //Kaelte: frieren
            check(ShotSpec.from(profile(silicon, 3, cryo, 60)).freeze > 0f && ShotSpec.from(profile(copper, 5, surge, 3)).freeze == 0f, "Kalte Mischungen frieren Ziele, andere nicht");
            //Reichweite/Lebensdauer
            boolean reach = true;
            for(MaterialProfile p : ps){
                ShotSpec s = ShotSpec.from(p);
                float dist = 0, v = s.speed;
                for(int t = 0; t < (int)s.lifetime; t++){ dist += v; v = Math.max(0.2f, v * (1 - s.drag) + s.accel); }
                if(dist < s.range - 0.5f || s.lifetime < 5f || s.lifetime >= 1200f) reach = false;
            }
            check(reach, "Lebensdauer deckt die Reichweite ab (mit Luftwiderstand)");
            boolean sane = true;
            for(MaterialProfile p : ps){
                ShotSpec s = ShotSpec.from(p);
                if(!(s.speed > 0.5f && s.speed < 16f) || s.damage + s.splashDamage < 1f || s.frags > 12 || s.lightning > 10 || s.trailLength > 20 || s.zoneRadius > 80f) sane = false;
            }
            check(sane, "Alle Schusswerte bleiben in sinnvollen, performancefreundlichen Grenzen (Splitter <= 12, Blitze <= 10, Spur <= 20)");
        }

        System.out.println("[10] Icons");
        {
            List<Object[]> sets = new ArrayList<>();
            sets.add(new Object[]{copper, 3, coal, 2}); sets.add(new Object[]{titanium, 4, graphite, 2, sand, 1}); sets.add(new Object[]{copper, 5, surge, 3});
            sets.add(new Object[]{silicon, 3, copper, 3, cryo, 30}); sets.add(new Object[]{oil, 40, slag, 40}); sets.add(new Object[]{blast, 4, pyratite, 2});
            sets.add(new Object[]{thorium, 3, phase, 3}); sets.add(new Object[]{phase, 4, surge, 2, silicon, 2}); sets.add(new Object[]{coal, 4, spore, 2});
            sets.add(new Object[]{tungsten, 4, carbide, 2}); sets.add(new Object[]{lead, 3, metaglass, 3}); sets.add(new Object[]{sand, 3, scrap, 3, water, 20});
            Set<String> imgs = new HashSet<>();
            boolean deterministic = true, nonEmpty = true, border = true;
            for(Object[] s : sets){
                Composition c = mix(s);
                MaterialProfile p = MaterialProfile.of(c);
                int[] pal = new int[c.count()]; float[] sh = new float[c.count()];
                for(int i = 0; i < pal.length; i++){ pal[i] = ((int)(c.defs[i].r * 255) << 16) | ((int)(c.defs[i].g * 255) << 8) | (int)(c.defs[i].b * 255); sh[i] = c.share(i); }
                int[] a = IconPainter.paint(p, pal, sh), b = IconPainter.paint(p, pal, sh);
                if(!Arrays.equals(a, b)) deterministic = false;
                int opaque = 0;
                for(int v : a) if((v & 255) != 0) opaque++;
                if(opaque < 150 || opaque > 900) nonEmpty = false;
                for(int i = 0; i < IconPainter.SIZE; i++){
                    int n = IconPainter.SIZE;
                    if((a[i] & 255) != 0 || (a[n * (n - 1) + i] & 255) != 0 || (a[i * n] & 255) != 0 || (a[i * n + n - 1] & 255) != 0){ border = false; System.out.println("       Rand beruehrt: " + c.signature); break; }
                }
                imgs.add(Arrays.toString(a));
            }
            check(deterministic, "Icon ist reproduzierbar (zweimal gezeichnet = identisch)");
            check(imgs.size() == sets.size(), "Verschiedene Materialien haben verschiedene Icons (" + imgs.size() + "/" + sets.size() + ")");
            check(nonEmpty, "Icons sind weder leer noch uebervoll");
            check(border, "Icons ragen nicht in den Bildrand");
            MaterialProfile p = profile(copper, 3, coal, 2);
            int[] withoutIncl = IconPainter.paint(p, null, null);
            check(withoutIncl != null && withoutIncl.length == 1024, "Icon ist auch ohne Zutatenfarben zeichenbar (Zutat nicht mehr aufloesbar)");
        }

        System.out.println();
        System.out.println("Ergebnis: " + passed + " bestanden, " + failed + " fehlgeschlagen");
        System.exit(failed == 0 ? 0 : 1);
    }

    static List<MaterialProfile> diverse(){
        List<MaterialProfile> ps = new ArrayList<>();
        Object[][] sets = {
            {copper, 3, coal, 2}, {titanium, 4, graphite, 2, sand, 1}, {copper, 5, surge, 3}, {silicon, 3, copper, 3, cryo, 30},
            {oil, 40, slag, 40}, {blast, 4, pyratite, 2}, {thorium, 3, phase, 3}, {phase, 4, surge, 2, silicon, 2}, {coal, 4, spore, 2},
            {tungsten, 4, carbide, 2}, {lead, 3, metaglass, 3}, {sand, 3, scrap, 3, water, 20}, {tungsten, 5, titanium, 3}, {blast, 5},
            {pyratite, 4, slag, 30}, {surge, 4, thorium, 3, phase, 2}, {beryllium, 3, graphite, 3, titanium, 2, copper, 1, lead, 1},
            {slag, 30, silicon, 3}, {thorium, 5, oil, 20}, {surge, 4, titanium, 2, cryo, 20}, {copper, 4, water, 40}, {plastanium, 4, oil, 20}
        };
        for(Object[] s : sets) ps.add(MaterialProfile.of(mix(s)));
        return ps;
    }
}
