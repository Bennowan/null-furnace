package skillable.flux;

/** Erzeuger von momentanem Flux (Konverter). Haelt KEINEN Flux-Vorrat, nur Instabilitaets-Zustand. */
public interface FluxSource extends FluxMember{
    ConverterProfile profile();
    /** Flux/Tick bei normalem Betrieb (Strom * Effizienz * Erholung). 0 bei Abschaltung oder ohne Strom. */
    float nominalOutput();
    /** Flux/Tick inklusive erlaubtem Overdrive. Immer >= nominalOutput(). */
    float maxOutput();
    boolean locked();
    boolean recovering();
    float instability();
    void instability(float value);
    /** Kuehlfaktor aus Kuehlfluessigkeit (1 = keine Kuehlung, <1 senkt den Instabilitaets-Zielwert). */
    float coolantFactor();
    /** Aufschluesselung der Instabilitaets-Ursachen dieses Ticks (nur Anzeige). */
    void reportBreakdown(InstabilityModel.Breakdown b);
    /** Wird jeden Tick vom Netz mit dem tatsaechlichen Durchsatz gemeldet (nur Anzeige/Last). */
    void reportOutput(float out, float load);
    /** EMP-Abschaltung starten (setzt Instabilitaet auf Wiederanlauf-Wert, startet Erholung danach). */
    void applyLockout(float ticks);
    /** Zaehlt Abschalt-/Erholungsphase herunter. */
    void tickState(float delta);
}
