package skillable.synth;

import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import skillable.content.*;
import skillable.material.*;

import static mindustry.Vars.*;

/**
 * Weltgebundenes Register: Slot -> Zusammensetzung/Profil der {@link SynthItem}-Traeger.
 * Gespeichert in {@code state.rules.tags} (Spielstand + Multiplayer-Sync), zeilenweise mit Version und Pruefsumme.
 * Beschaedigte Zeilen werden einzeln verworfen; der Slot gilt dann als "unbekanntes Material" und bleibt inert.
 */
public final class MaterialRegistry{
    private MaterialRegistry(){}

    public static final int SLOTS = 48;
    public static final String TAG = "flux-protocol.materials";
    public static final String HEADER = "fm1:pv" + MaterialProfile.VERSION;
    /** Ticks, die ein Slot ungenutzt sein muss, bevor er neu vergeben werden darf (Puffer in Junctions/Bruecken). */
    public static final float REUSE_DELAY = 60f * 60f;

    static final class Entry{
        final String sig;
        final MaterialProfile profile;
        final Composition comp; //kann null sein (Zutat nicht mehr aufloesbar)
        float lastUsed;
        Entry(String sig, MaterialProfile profile, Composition comp){ this.sig = sig; this.profile = profile; this.comp = comp; this.lastUsed = Time.time; }
    }

    private static final Entry[] entries = new Entry[SLOTS];
    private static final boolean[] corrupt = new boolean[SLOTS];
    private static final ObjectIntMap<String> bySig = new ObjectIntMap<>();
    private static String seen = null;
    private static float syncedAt = -1f;
    private static Object syncedRules = null;
    /** Zaehler, der bei jeder Aenderung steigt (fuer Caches). */
    public static int revision = 0;

    //------------------------------------------------------------------ Abfragen
    public static MaterialProfile profile(SynthItem item){
        sync();
        Entry e = entries[item.slot];
        return e == null ? null : e.profile;
    }

    public static MaterialProfile profile(Item item){
        return item instanceof SynthItem s ? profile(s) : null;
    }

    public static boolean isUnknown(SynthItem item){ return profile(item) == null; }

    public static Composition composition(SynthItem item){
        sync();
        Entry e = entries[item.slot];
        return e == null ? null : e.comp;
    }

    public static MaterialProfile byHash(long hash){
        sync();
        for(Entry e : entries) if(e != null && e.profile.hash == hash) return e.profile;
        return null;
    }

    public static int used(){
        sync();
        int n = 0;
        for(Entry e : entries) if(e != null) n++;
        return n;
    }

    public static boolean isCorrupt(int slot){ return corrupt[slot]; }

    public static void touch(SynthItem item){
        Entry e = entries[item.slot];
        if(e != null) e.lastUsed = Time.time;
    }

    //------------------------------------------------------------------ Belegung
    /**
     * Liefert den Traeger fuer diese Zusammensetzung (bestehend oder neu belegt). null, wenn das Register voll ist und kein
     * Slot sicher freigegeben werden kann.
     */
    public static SynthItem obtain(Composition c){
        sync();
        if(c == null) return null;
        int slot = bySig.get(c.signature, -1);
        if(slot >= 0 && entries[slot] != null){
            entries[slot].lastUsed = Time.time;
            return FluxItems.material[slot];
        }
        slot = freeSlot();
        if(slot < 0) slot = reclaim();
        if(slot < 0) return null;
        MaterialProfile p = MaterialProfile.of(c);
        setEntry(slot, new Entry(c.signature, p, c));
        commit();
        apply(slot);
        return FluxItems.material[slot];
    }

    /** Kann eine Zusammensetzung aktuell aufgenommen werden (bestehend oder freier/freigebbarer Slot)? Aendert nichts. */
    public static boolean canObtain(Composition c){
        sync();
        if(c == null) return false;
        if(bySig.get(c.signature, -1) >= 0) return true;
        return freeSlot() >= 0 || reclaimCandidate() >= 0;
    }

    private static int freeSlot(){
        for(int i = 0; i < SLOTS; i++) if(entries[i] == null && !corrupt[i]) return i;
        return -1;
    }

    private static void setEntry(int slot, Entry e){
        if(entries[slot] != null) bySig.remove(entries[slot].sig);
        entries[slot] = e;
        corrupt[slot] = false;
        if(e != null) bySig.put(e.sig, slot);
        revision++;
    }

    private static int reclaimCandidate(){
        int best = -1;
        float bestT = Float.MAX_VALUE;
        for(int i = 0; i < SLOTS; i++){
            Entry e = entries[i];
            float last = e == null ? -1e9f : e.lastUsed;
            if(e != null && Time.time - last < REUSE_DELAY) continue;
            if(e == null && !corrupt[i]) continue;
            if(last < bestT && !inUse(FluxItems.material[i])){ bestT = last; best = i; }
        }
        return best;
    }

    private static int reclaim(){
        int slot = reclaimCandidate();
        if(slot < 0) return -1;
        Log.info("[FluxProtocol] Materialslot @ wird neu vergeben (ungenutzt, nirgends im Spiel vorhanden).", slot);
        setEntry(slot, null);
        return slot;
    }

    /** Liegt das Item noch irgendwo (Gebaeude, Einheit, Ausgabepuffer)? */
    public static boolean inUse(Item item){
        if(item == null) return false;
        //Groups.build indexiert Gebaeude nur (addIndex) und ist NICHT iterierbar -> pro Team ueber TeamData.buildings gehen
        boolean[] used = {false};
        for(Teams.TeamData data : state.teams.present){
            for(Building b : data.buildings){
                if(b.items != null && b.items.has(item)){ used[0] = true; break; }
                if(b instanceof MaterialHolder h && h.holdsMaterial(item)){ used[0] = true; break; }
            }
            if(used[0]) return true;
        }
        Groups.unit.each(u -> { if(!used[0] && u.stack != null && u.stack.item == item && u.stack.amount > 0) used[0] = true; });
        return used[0];
    }

    //------------------------------------------------------------------ Persistenz
    /** Gleicht den Speicher mit {@code state.rules.tags} ab (nach Laden, nach Netzwerk-Sync). Billig, wenn sich nichts geaendert hat. */
    public static void sync(){
        if(state == null || state.rules == null || state.rules.tags == null) return;
        if(syncedAt == Time.time && syncedRules == state.rules && seen != null) return;
        syncedAt = Time.time;
        syncedRules = state.rules;
        String tag = state.rules.tags.get(TAG);
        if(tag == null ? seen == null : tag.equals(seen)) return;
        reload(tag);
    }

    /** Erzwingt das Neuladen aus den Regeln (z. B. nach WorldLoad). */
    public static void reset(){
        seen = "\u0000";
        syncedAt = -1f;
        sync();
    }

    private static void reload(String tag){
        String[] oldSig = new String[SLOTS];
        boolean[] oldCorrupt = corrupt.clone();
        for(int i = 0; i < SLOTS; i++) oldSig[i] = entries[i] == null ? null : entries[i].sig;
        java.util.Arrays.fill(entries, null);
        java.util.Arrays.fill(corrupt, false);
        bySig.clear();
        seen = tag;
        int bad = 0;
        if(tag != null && !tag.isEmpty()){
            String[] lines = tag.split("\n");
            boolean headerOk = lines.length > 0 && lines[0].startsWith("fm1");
            boolean sameVersion = lines.length > 0 && lines[0].equals(HEADER);
            if(!headerOk){
                Log.warn("[FluxProtocol] Materialregister hat unbekanntes Format - ignoriert.");
            }else{
                for(int i = 1; i < lines.length; i++){
                    if(lines[i].isEmpty()) continue;
                    if(!parseLine(lines[i], sameVersion)) bad++;
                }
            }
        }
        if(bad > 0) Log.warn("[FluxProtocol] @ beschaedigte Registerzeile(n) verworfen; zugehoerige Materialien gelten als unbekannt.", bad);
        revision++;
        for(int i = 0; i < SLOTS; i++){
            String sig = entries[i] == null ? null : entries[i].sig;
            boolean changed = !java.util.Objects.equals(sig, oldSig[i]) || corrupt[i] != oldCorrupt[i];
            if(changed) apply(i);
        }
    }

    private static boolean parseLine(String line, boolean sameVersion){
        String[] f = line.split("\\|", -1);
        int slot = -1;
        try{
            if(f.length != 4) throw new IllegalArgumentException("Feldzahl");
            slot = Integer.parseInt(f[0]);
            if(slot < 0 || slot >= SLOTS) throw new IllegalArgumentException("Slot");
            String crcWanted = Long.toHexString(Composition.hash64(f[0] + "|" + f[1] + "|" + f[2]) & 0xffffffffL);
            if(!crcWanted.equals(f[3])) throw new IllegalArgumentException("Pruefsumme");
            if(entries[slot] != null) throw new IllegalArgumentException("doppelter Slot");
            String sig = f[1];
            String[] vs = f[2].split(",", -1);
            if(vs.length != MaterialProfile.VALUES) throw new IllegalArgumentException("Werteanzahl");
            float[] v = new float[MaterialProfile.VALUES];
            for(int i = 0; i < v.length; i++){
                int iv = Integer.parseInt(vs[i]);
                if(iv < -1000 || iv > 20000) throw new IllegalArgumentException("Wertebereich");
                v[i] = iv / 1000f;
            }
            if(!sig.startsWith(Composition.VERSION + ";")) throw new IllegalArgumentException("Signaturformat");
            long hash = Composition.hash64(sig);
            Composition comp = Composition.parse(sig, Ingredients::resolve);
            MaterialProfile p;
            if(!sameVersion && comp != null) p = MaterialProfile.of(comp); //Formelversion geaendert: neu rechnen, wenn moeglich
            else p = new MaterialProfile(sig, hash, v);
            setEntry(slot, new Entry(sig, p, comp));
            return true;
        }catch(RuntimeException e){
            if(slot >= 0 && slot < SLOTS && entries[slot] == null) corrupt[slot] = true;
            return false;
        }
    }

    private static void commit(){
        StringBuilder sb = new StringBuilder(HEADER);
        for(int i = 0; i < SLOTS; i++){
            Entry e = entries[i];
            if(e == null) continue;
            float[] v = e.profile.toArray();
            StringBuilder vs = new StringBuilder();
            for(int k = 0; k < v.length; k++){
                if(k > 0) vs.append(',');
                vs.append(Math.round(v[k] * 1000f));
            }
            String head = i + "|" + e.sig + "|" + vs;
            sb.append('\n').append(head).append('|').append(Long.toHexString(Composition.hash64(head) & 0xffffffffL));
        }
        String s = sb.toString();
        seen = s;
        state.rules.tags.put(TAG, s);
        if(net.server()) Call.setRules(state.rules);
    }

    /** Wendet Name/Farbe/Icon, Stoffwerte, Geschoss und Turmmodus fuer einen Slot an. */
    static void apply(int slot){
        SynthItem item = FluxItems.material == null ? null : FluxItems.material[slot];
        if(item == null) return;
        Entry e = entries[slot];
        MaterialVisuals.apply(item, e == null ? null : e.profile, e == null ? null : e.comp, corrupt[slot]);
        ProjectileFactory.configure(slot, e == null ? null : e.profile);
        DynamicModes.rebuild(slot, e == null ? null : e.profile, item);
    }
}
