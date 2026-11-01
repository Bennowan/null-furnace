package skillable.flux;

/** Alles, was an einem Flux-Netz haengen kann (Node, Konverter, Verbraucher). */
public interface FluxMember{
    FluxNetwork fluxNetwork();
    void fluxNetwork(FluxNetwork network);
}
