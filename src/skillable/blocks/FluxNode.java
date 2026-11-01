package skillable.blocks;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import arc.scene.ui.layout.*;
import skillable.fx.*;
import skillable.flux.*;

import static mindustry.Vars.*;

/** Verbindet Konverter, Verbraucher und andere Nodes zu einem Flux-Netz. */
public class FluxNode extends Block{
    /** Reichweite in Kacheln (vom Rand der Bloecke gemessen). */
    public float laserRange = 12f;
    public int maxLinks = 8;

    public FluxNode(String name){
        super(name);
        update = true;
        solid = true;
        configurable = true;
        consumesPower = false;
        outputsPower = false;
        hasPower = false;
        group = BlockGroup.power;
        envEnabled = Env.any;
        config(Integer.class, (NodeBuild b, Integer pos) -> b.toggleLink(pos));
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.powerRange, laserRange, StatUnit.blocks);
        stats.add(Stat.powerConnections, maxLinks, StatUnit.none);
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("flux-net", (NodeBuild e) -> new Bar(
            () -> e.network == null ? Core.bundle.get("flux.net.none")
                : Core.bundle.format("bar.flux-net", Strings.fixed(e.network.lastGranted * 60f, 1), Strings.fixed(e.network.lastCapacity * 60f, 1)),
            () -> FluxEffects.colorFor(e.intensity()).cpy(),
            () -> e.network == null ? 0f : FluxMath.safeDiv(e.network.lastGranted, Math.max(0.0001f, e.network.lastCapacity))));
        addBar("links", (NodeBuild e) -> new Bar(
            () -> Core.bundle.format("bar.flux-links", e.links.size, maxLinks),
            () -> Pal.items,
            () -> (float)e.links.size / maxLinks));
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        float wx = x * tilesize + offset, wy = y * tilesize + offset;
        Drawf.dashCircle(wx, wy, laserRange * tilesize, FluxEffects.blue);
        //Alle Flux-Gebaeude, die dieser Node beim Platzieren automatisch verbinden wuerde
        if(player != null){
            indexer.eachBlock(player.team(), wx, wy, (laserRange + 4f) * tilesize, b -> b instanceof FluxMember, b -> {
                float max = laserRange * tilesize + (size + b.block.size) * tilesize / 2f;
                boolean in = Mathf.dst(wx - b.x, wy - b.y) <= max;
                Drawf.square(b.x, b.y, b.block.size * tilesize / 2f + 2f, in ? FluxEffects.blue : FluxEffects.warn);
            });
        }
    }

    /** Hinweis beim Platzieren von Konvertern/Tuermen: zeigt Nodes in Reichweite (gruen) oder dass keiner da ist (rot). */
    public static void drawNodeHints(Block block, int tx, int ty){
        if(player == null) return;
        float wx = tx * tilesize + block.offset, wy = ty * tilesize + block.offset;
        final boolean[] found = {false};
        indexer.eachBlock(player.team(), wx, wy, 40f * tilesize, b -> b instanceof NodeBuild, b -> {
            FluxNode n = (FluxNode)b.block;
            float max = n.laserRange * tilesize + (block.size + b.block.size) * tilesize / 2f;
            if(Mathf.dst(wx - b.x, wy - b.y) <= max){
                found[0] = true;
                Drawf.square(b.x, b.y, b.block.size * tilesize / 2f + 2f, FluxEffects.blue);
                Lines.stroke(1f, FluxEffects.blue);
                Draw.alpha(0.6f);
                Lines.dashLine(wx, wy, b.x, b.y, 12);
                Draw.reset();
            }
        });
        if(!found[0]) Drawf.dashCircle(wx, wy, 12f * tilesize, FluxEffects.red);
    }

    /** Alle Nodes, die auf dieses Gebaeude verlinkt sind. */
    public static Seq<NodeBuild> nodesLinkedTo(Building target){
        Seq<NodeBuild> out = new Seq<>();
        indexer.eachBlock(target.team, target.x, target.y, 40f * tilesize, b -> b instanceof NodeBuild nb && nb.links.contains(target.pos()), b -> out.add((NodeBuild)b));
        return out;
    }

    /** Markiert beim Auswaehlen eines Flux-Gebaeudes die verbundenen Nodes. */
    public static void drawLinkedNodes(Building target){
        Seq<NodeBuild> nodes = nodesLinkedTo(target);
        for(NodeBuild n : nodes){
            Drawf.square(n.x, n.y, n.block.size * tilesize / 2f + 2f, FluxEffects.blue);
            Lines.stroke(1f, FluxEffects.blue);
            Draw.alpha(0.6f);
            Lines.dashLine(target.x, target.y, n.x, n.y, 12);
            Draw.reset();
        }
        if(nodes.isEmpty()) Drawf.dashCircle(target.x, target.y, 4f * tilesize, FluxEffects.red);
    }

    /** Ist 'other' von diesem Node aus erreichbar (Reichweite, Team, Typ)? */
    public boolean linkValid(Building self, Building other){
        if(other == null || other == self || !other.isValid()) return false;
        if(other.team != self.team) return false;
        if(!(other instanceof FluxMember)) return false;
        float max = laserRange * tilesize + (size + other.block.size) * tilesize / 2f;
        return self.within(other, max);
    }

    /** Beim Platzieren eines Konverters/Verbrauchers: naechsten freien Node in Reichweite verbinden. */
    public static void autoLinkTo(Building target){
        FluxNode bestBlock = null;
        NodeBuild best = null;
        float bestDst = Float.MAX_VALUE;
        for(Building b : indexerNodes(target)){
            NodeBuild nb = (NodeBuild)b;
            if(nb.links.size >= ((FluxNode)nb.block).maxLinks || nb.links.contains(target.pos())) continue;
            if(!((FluxNode)nb.block).linkValid(nb, target)) continue;
            float d = nb.dst2(target);
            if(d < bestDst){ bestDst = d; best = nb; }
        }
        if(best != null) best.configureAny(target.pos());
    }

    private static Seq<Building> indexerNodes(Building target){
        Seq<Building> out = new Seq<>();
        indexer.eachBlock(target.team, target.x, target.y, 40f * tilesize, b -> b instanceof NodeBuild, out::add);
        return out;
    }

    public class NodeBuild extends Building implements FluxLinker{
        public final IntSeq links = new IntSeq();
        public @Nullable FluxNetwork network;
        private boolean registered;

        @Override public IntSeq links(){ return links; }
        @Override public FluxNetwork fluxNetwork(){ return network; }
        @Override public void fluxNetwork(FluxNetwork n){ network = n; }

        public float intensity(){
            if(network == null) return 0f;
            float load = FluxMath.safeDiv(network.lastGranted, Math.max(0.0001f, network.lastCapacity));
            return network.offline ? 0f : FluxEffects.intensity(load, network.maxInstability);
        }

        public void toggleLink(int pos){
            int idx = links.indexOf(pos);
            if(idx >= 0){
                links.removeIndex(idx);
            }else{
                Building other = world.build(pos);
                if(links.size >= maxLinks || !linkValid(this, other)) return;
                links.add(pos);
            }
            FluxNetworkManager.get().markDirty();
        }

        @Override
        public void updateTile(){
            if(!registered){
                registered = true;
                FluxNetworkManager.get().register(this);
            }
        }

        @Override
        public void onRemoved(){
            super.onRemoved();
            FluxNetworkManager.get().unregister(this);
        }

        @Override
        public void placed(){
            super.placed();
            if(net.client()) return;
            //automatisch mit Flux-Gebaeuden in Reichweite verbinden
            Seq<Building> found = new Seq<>();
            indexer.eachBlock(team, x, y, (laserRange + 4f) * tilesize, b -> b != this && b instanceof FluxMember && linkValid(this, b), found::add);
            found.sort(b -> b.dst2(this));
            for(Building b : found){
                if(links.size >= maxLinks) break;
                if(b instanceof NodeBuild other && other.links.contains(pos())) continue;
                if(!links.contains(b.pos())) configureAny(b.pos());
            }
        }

        @Override
        public void display(Table table){
            super.display(table);
            table.row();
            table.table(t -> {
                t.left();
                t.label(() -> Core.bundle.get("flux.node.role")).wrap().width(190f).left().color(FluxEffects.blue).row();
                t.label(() -> network == null ? Core.bundle.get("flux.node.hint.nonet")
                    : Core.bundle.format("flux.node.info", network.sources.size(), network.consumers.size(), links.size)
                        + (network.sources.isEmpty() ? "\n" + Core.bundle.get("flux.node.hint.nosource") : "")
                        + (network.consumers.isEmpty() ? "\n" + Core.bundle.get("flux.node.hint.noconsumer") : ""))
                    .wrap().width(190f).left().color(Color.lightGray);
            }).growX().left();
        }

        @Override
        public void drawSelect(){
            super.drawSelect();
            Drawf.dashCircle(x, y, laserRange * tilesize, FluxEffects.blue);
        }

        @Override
        public boolean onConfigureBuildTapped(Building other){
            if(this == other){
                deselect();
                return false;
            }
            if(linkValid(this, other) || links.contains(other.pos())){
                configure(other.pos());
                return false;
            }
            return true;
        }

        @Override
        public void drawConfigure(){
            Drawf.dashCircle(x, y, laserRange * tilesize, FluxEffects.blue);
            for(int i = 0; i < links.size; i++){
                Building o = world.build(links.get(i));
                if(o != null) Drawf.square(o.x, o.y, o.block.size * tilesize / 2f + 1f, FluxEffects.purple);
            }
        }

        @Override
        public void draw(){
            super.draw();
            float z = Draw.z();
            Draw.z(Layer.power);
            float load = network == null ? 0f : FluxMath.safeDiv(network.lastGranted, Math.max(0.0001f, network.lastCapacity));
            float inst = network == null ? 0f : network.maxInstability;
            boolean down = network == null || network.offline;
            for(int i = 0; i < links.size; i++){
                Building o = world.build(links.get(i));
                if(o == null || !o.isValid()) continue;
                //Node-Node-Links nur einmal zeichnen
                if(o instanceof NodeBuild && o.pos() < pos()) continue;
                Tmp.v1.set(o.x - x, o.y - y).setLength(size * tilesize / 2f);
                Tmp.v2.set(x - o.x, y - o.y).setLength(o.block.size * tilesize / 2f);
                //Flussrichtung: Konverter -> Node -> Verbraucher
                boolean forward = !(o instanceof FluxSource);
                FluxLines.link(x + Tmp.v1.x, y + Tmp.v1.y, o.x + Tmp.v2.x, o.y + Tmp.v2.y, load, inst, down, forward, o.pos());
            }
            Draw.z(z);
            Draw.reset();
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.b(1);
            write.s(links.size);
            for(int i = 0; i < links.size; i++) write.i(links.get(i));
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            read.b();
            links.clear();
            int n = Math.min(read.s(), 64);
            for(int i = 0; i < n; i++){
                int p = read.i();
                if(p >= 0 && !links.contains(p) && links.size < maxLinks) links.add(p);
            }
        }
    }
}
