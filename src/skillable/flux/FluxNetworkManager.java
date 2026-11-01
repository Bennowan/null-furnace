package skillable.flux;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;

import java.util.*;

import static mindustry.Vars.*;

/**
 * Verwaltet alle Flux-Mitglieder der aktuellen Welt. Netze werden nur bei "dirty" neu aufgebaut
 * (Platzieren, Zerstoeren, Umkonfigurieren), pro Tick wird jedes Netz genau einmal simuliert.
 */
public final class FluxNetworkManager{
    private static final FluxNetworkManager instance = new FluxNetworkManager();
    public static FluxNetworkManager get(){ return instance; }

    /** Kaskade: Konverter anderer Netze innerhalb dieses Radius (in Welteinheiten) werden mit gestoert. */
    public static final float CASCADE_RADIUS = 28f * tilesize;
    public static final float CASCADE_KICK = 0.25f;

    private final ArrayList<FluxMember> members = new ArrayList<>();
    private final ArrayList<FluxNetwork> networks = new ArrayList<>();
    private boolean dirty = true;
    private float lastTime = -1f;

    /** Wird vom Mod gesetzt: Effekte/Sounds bei Kollaps. */
    public java.util.function.Consumer<FluxNetwork> onCollapseFx = n -> {};

    private FluxNetworkManager(){}

    public void init(){
        Events.on(WorldLoadEvent.class, e -> clear());
        Events.run(Trigger.update, this::update);
    }

    public void clear(){
        members.clear();
        networks.clear();
        dirty = true;
        lastTime = -1f;
    }

    public void markDirty(){ dirty = true; }

    public void register(FluxMember m){
        if(!members.contains(m)){
            members.add(m);
            dirty = true;
        }
    }

    public void unregister(FluxMember m){
        if(members.remove(m)){
            m.fluxNetwork(null);
            dirty = true;
        }
    }

    public int networkCount(){ return networks.size(); }
    public List<FluxNetwork> networks(){ return networks; }

    public void update(){
        if(state == null || !state.isGame() || state.isPaused()) return;
        //pro Spiel-Tick hoechstens einmal simulieren
        if(Time.time == lastTime) return;
        lastTime = Time.time;

        if(dirty) rebuild();
        float delta = Math.min(Time.delta, 4f);
        for(int i = 0; i < networks.size(); i++){
            networks.get(i).update(delta);
        }
    }

    /** Baut alle Netze aus den Node-Links neu auf (Union-Find). Entfernt ungueltige Links. */
    private void rebuild(){
        dirty = false;
        //ungueltige Mitglieder (entfernt/tot) aussortieren
        for(int i = members.size() - 1; i >= 0; i--){
            FluxMember m = members.get(i);
            if(m instanceof Building b && (!b.isValid() || b.tile == null || b.tile.build != b)){
                members.remove(i);
                m.fluxNetwork(null);
            }
        }

        int n = members.size();
        int[] parent = new int[n];
        for(int i = 0; i < n; i++) parent[i] = i;
        ObjectIntMap<Building> index = new ObjectIntMap<>();
        for(int i = 0; i < n; i++) index.put((Building)members.get(i), i);

        for(int i = 0; i < n; i++){
            if(!(members.get(i) instanceof FluxLinker linker)) continue;
            Building self = (Building)members.get(i);
            IntSeq links = linker.links();
            for(int k = links.size - 1; k >= 0; k--){
                Building other = world.build(links.get(k));
                boolean valid = other instanceof FluxMember && other != self && other.isValid() && other.team == self.team;
                if(!valid){
                    links.removeIndex(k); //kein Verweis auf zerstoerte/fremde Gebaeude behalten
                    continue;
                }
                if(index.containsKey(other)) union(parent, i, index.get(other));
            }
        }

        networks.clear();
        FluxNetwork[] byRoot = new FluxNetwork[n];
        for(int i = 0; i < n; i++){
            int r = find(parent, i);
            if(byRoot[r] == null){
                byRoot[r] = new FluxNetwork();
                byRoot[r].collapseHook = this::handleCollapse;
                networks.add(byRoot[r]);
            }
            byRoot[r].add(members.get(i));
        }
    }

    private static int find(int[] p, int i){
        while(p[i] != i){ p[i] = p[p[i]]; i = p[i]; }
        return i;
    }

    private static void union(int[] p, int a, int b){
        int ra = find(p, a), rb = find(p, b);
        if(ra != rb) p[ra] = rb;
    }

    private void handleCollapse(FluxNetwork net){
        onCollapseFx.accept(net);
        //Kaskade: benachbarte Netze erhalten einen Instabilitaets-Schub (koennen dadurch ebenfalls kollabieren)
        for(int i = 0; i < networks.size(); i++){
            FluxNetwork other = networks.get(i);
            if(other == net || other.offline) continue;
            if(near(net, other)) other.kick(CASCADE_KICK, 0.8f);
        }
    }

    private boolean near(FluxNetwork a, FluxNetwork b){
        for(int i = 0; i < a.sources.size(); i++){
            if(!(a.sources.get(i) instanceof Building ba)) continue;
            for(int k = 0; k < b.sources.size(); k++){
                if(!(b.sources.get(k) instanceof Building bb)) continue;
                if(ba.team == bb.team && ba.within(bb, CASCADE_RADIUS)) return true;
            }
        }
        return false;
    }
}
